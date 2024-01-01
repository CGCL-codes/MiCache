#define DEBUG_DATA_READ 0
#define DEBUG_DMA 0
#define DEBUG_CHECK_RESULTS 0

// ## removes the preceding comma when there are 0 arguments in __VA_ARGS__
#define debug_printf(fmt, debug_cond, ...) do { if (debug_cond) printf(fmt, ## __VA_ARGS__); } while (0)
#define debug_data_read_printf(fmt, ...) debug_printf(fmt, DEBUG_DATA_READ, ## __VA_ARGS__)
#define debug_dma_printf(fmt, ...) debug_printf(fmt, DEBUG_DMA, ## __VA_ARGS__)
#define debug_check_results_printf(fmt, ...) debug_printf(fmt, DEBUG_CHECK_RESULTS, ## __VA_ARGS__)

#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <libgen.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/time.h>

#include "def.h"
#include "xfully_pipelined_spmv.c"
// #define GET_RUNTIME_LOG
// #define MSHR_INCLUSIVE
#ifdef MSHR_INCLUSIVE
#include "mshrinclusive.c"
#else
#include "fpgamshr.c"
#endif
#include "xaxi_dma.c"

uint32_t cols;
uint32_t nnz[NUM_SPMV];
uint32_t rows[NUM_SPMV];
uint32_t nout[NUM_SPMV];

#define KB(x)	((uint64_t)(x) * 1024)
#define MB(x)	((uint64_t)(x) * 1024 * 1024)
#define GB(x)	((uint64_t)(x) * 1024 * 1024 * 1024)

uint64_t vect_mem = HBM_BASE_ADDR;
uint64_t vect_mem_host = HBM_BASE_ADDR;
uint64_t rowptr_mem[NUM_SPMV] = {
	DDR_BASE_ADDR,
	DDR_BASE_ADDR + MB(512),
	DDR_BASE_ADDR + GB(1),
	DDR_BASE_ADDR + GB(1) + MB(512)
};
uint64_t col_mem[NUM_SPMV] = {
	DDR_BASE_ADDR + GB(2),
	DDR_BASE_ADDR + GB(2) + MB(512),
	DDR_BASE_ADDR + GB(2) + GB(1),
	DDR_BASE_ADDR + GB(2) + GB(1) + MB(512)
};
uint64_t val_mem[NUM_SPMV] = {
	DDR_BASE_ADDR + GB(4),
	DDR_BASE_ADDR + GB(4) + MB(512),
	DDR_BASE_ADDR + GB(4) + GB(1),
	DDR_BASE_ADDR + GB(4) + GB(1) + MB(512)
};
uint64_t output_mem[NUM_SPMV] = {
	DDR_BASE_ADDR + GB(6),
	DDR_BASE_ADDR + GB(6) + MB(512),
	DDR_BASE_ADDR + GB(6) + GB(1),
	DDR_BASE_ADDR + GB(6) + GB(1) + MB(512)
};

float* host_output_mem[NUM_SPMV] = { NULL };
float* ref_output_mem[NUM_SPMV] = { NULL };

uint64_t spmv_bases[NUM_SPMV] = {
	0x100010000,
	0x100020000,
	0x100030000,
	0x100040000
};
uint64_t row_dma_bases[NUM_SPMV] = {
	0x100050000,
	0x100090000,
	0x1000d0000,
	0x100110000
};
uint64_t col_dma_bases[NUM_SPMV] = {
	0x100060000,
	0x1000a0000,
	0x1000e0000,
	0x100120000
};
uint64_t val_dma_bases[NUM_SPMV] = {
	0x100070000,
	0x1000b0000,
	0x1000f0000,
	0x100130000
};
uint64_t out_dma_bases[NUM_SPMV] = {
	0x100080000,
	0x1000c0000,
	0x100100000,
	0x100140000
};

#if NUM_REQ_HANDLERS <= 4
uint64_t fpgamshr_base = 0x100000000;
#else
uint64_t fpgamshr_base = 0x100160000;
#endif

// #ifdef NUM_REQ_HANDLERS
// #undef NUM_REQ_HANDLERS
// #define NUM_REQ_HANDLERS 4
// #endif

static void measure(struct timeval *start, struct timeval *end, uint64_t *sec, int *msec)
{
	*sec = end->tv_sec - start->tv_sec;
	*msec = (end->tv_usec - start->tv_usec) / 1000;
	if (*msec < 0) {
		*msec += 1000;
		*sec -= 1;
	}
}

void init_dma(int nspmv)
{
	for (int i = 0; i < nspmv; i++) {
		// printf("reset DMA\n");
		XAXI_DMA_Reset(row_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Reset(col_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Reset(val_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Reset(out_dma_bases[i], XAXIDMA_DEVICE_TO_DMA);

		// printf("enable DMA\n");
		XAXI_DMA_Enable(row_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Enable(col_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Enable(val_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Enable(out_dma_bases[i], XAXIDMA_DEVICE_TO_DMA);
	}
}

#define DMA_TRANSFER_BITWIDTH	26
#define MAX_TRANSFER_SIZE_BYTES ((1 << DMA_TRANSFER_BITWIDTH) - 64)
#define MIN(a, b) ((a) < (b) ? (a) : (b))

int test_spmv_mult_axis(int num_spmv, const char *logname)
{
	typedef struct {
		uint64_t next_start_addr;
		uint64_t bytes_left;
	} dma_state_t;

	typedef struct {
		dma_state_t rowptr;
		dma_state_t col;
		dma_state_t val;
		dma_state_t output;
	} spmv_dma_state_t;

	spmv_dma_state_t dma_state[num_spmv];

	struct timeval t1, t2;
	uint64_t sec;
	int msec;

	int i;

	for (i = 0; i < num_spmv; i++) {
		dma_state[i].col.next_start_addr = col_mem[i];
		dma_state[i].col.bytes_left = nnz[i] * sizeof(unsigned);
		dma_state[i].val.next_start_addr = val_mem[i];
		dma_state[i].val.bytes_left = nnz[i] * sizeof(float);

		dma_state[i].rowptr.next_start_addr = rowptr_mem[i];
		dma_state[i].rowptr.bytes_left = (rows[i] + 1) * sizeof(unsigned);

		dma_state[i].output.next_start_addr = output_mem[i];
		dma_state[i].output.bytes_left = nout[i] * sizeof(float);

		XSpmv_mult_axis_Set_val_size(spmv_bases[i], nnz[i]);
		XSpmv_mult_axis_Set_output_size(spmv_bases[i], nout[i]);
		XSpmv_mult_axis_Set_vect_mem(spmv_bases[i], vect_mem);
		// if (XSpmv_mult_axis_Set_args(spmv_bases[i], nnz[i], nout[i], vect_mem) < 0) {
		// 	return -1;
		// }
		// XSpmv_mult_axis_Get_args(spmv_bases[i]);

		debug_dma_printf("\ni=%d, curr_nnz=%u, curr_row_num=%u, curr_nout_num=%u\n", i, nnz[i], rows[i], nout[i]);
		debug_dma_printf("col bytes %lu, val bytes %lu, row bytes %lu, out bytes %lu\n",
						dma_state[i].col.bytes_left,
						dma_state[i].val.bytes_left,
						dma_state[i].rowptr.bytes_left,
						dma_state[i].output.bytes_left);
	}

	uint32_t status, ctrl;
	// status = XAXI_DMA_ReadReg(row_dma_bases[0], MM2S_DMASR);
	// ctrl = XAXI_DMA_ReadReg(row_dma_bases[0], MM2S_DMACR);
	// printf("row_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
	// status = XAXI_DMA_ReadReg(col_dma_bases[0], MM2S_DMASR);
	// ctrl = XAXI_DMA_ReadReg(col_dma_bases[0], MM2S_DMACR);
	// printf("col_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
	// status = XAXI_DMA_ReadReg(val_dma_bases[0], MM2S_DMASR);
	// ctrl = XAXI_DMA_ReadReg(val_dma_bases[0], MM2S_DMACR);
	// printf("val_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
	// status = XAXI_DMA_ReadReg(out_dma_bases[0], S2MM_DMASR);
	// ctrl = XAXI_DMA_ReadReg(out_dma_bases[0], S2MM_DMACR);
	// printf("out_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);

	for (i = 0; i < num_spmv; i++)
	{
		uint32_t bytes_to_send;
		bytes_to_send = MIN(dma_state[i].rowptr.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes from addr 0x%lX on rowptr[%d]\n", bytes_to_send, dma_state[i].rowptr.next_start_addr, i);
		if (XAXI_DMA_SetAddrLength(row_dma_bases[i], dma_state[i].rowptr.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE) < 0) {
			return -1;
		}
		dma_state[i].rowptr.next_start_addr += bytes_to_send;
		dma_state[i].rowptr.bytes_left -= bytes_to_send;
		
		bytes_to_send = MIN(dma_state[i].col.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes from addr 0x%lX on col[%d]\n", bytes_to_send, dma_state[i].col.next_start_addr, i);
		if (XAXI_DMA_SetAddrLength(col_dma_bases[i], dma_state[i].col.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE) < 0) {
			return -1;
		}
		dma_state[i].col.next_start_addr += bytes_to_send;
		dma_state[i].col.bytes_left -= bytes_to_send;

		bytes_to_send = MIN(dma_state[i].val.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes from addr 0x%lX on val[%d]\n", bytes_to_send, dma_state[i].val.next_start_addr, i);
		if (XAXI_DMA_SetAddrLength(val_dma_bases[i], dma_state[i].val.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE) < 0) {
			return -1;
		}
		dma_state[i].val.next_start_addr += bytes_to_send;
		dma_state[i].val.bytes_left -= bytes_to_send;

		bytes_to_send = MIN(dma_state[i].output.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes to addr 0x%lX from output[%d]\n", bytes_to_send, dma_state[i].output.next_start_addr, i);
		if (XAXI_DMA_SetAddrLength(out_dma_bases[i], dma_state[i].output.next_start_addr, bytes_to_send, XAXIDMA_DEVICE_TO_DMA) < 0) {
			return -1;
		}
		dma_state[i].output.next_start_addr += bytes_to_send;
		dma_state[i].output.bytes_left -= bytes_to_send;
	}

	gettimeofday(&t1, NULL);
	// FPGAMSHR_Clear_stats();
    for (i = 0; i < num_spmv; i++) {
		XSpmv_mult_axis_Start(spmv_bases[i]);
		// XSpmv_mult_axis_Start(spmv_bases[i], nnz[i], nout[i], vect_mem);
	}

	int count = 0;
    int any_transfers_pending = 1;
    while (any_transfers_pending)
	{
    	any_transfers_pending = 0;
		count++;
		#if DEBUG_DMA == 1
		if ((count & 0x3fff) == 0) {
			FPGAMSHR_Get_stats_log(logname);
			status = XAXI_DMA_ReadReg(row_dma_bases[0], MM2S_DMASR);
			ctrl = XAXI_DMA_ReadReg(row_dma_bases[0], MM2S_DMACR);
			printf("count %d:\nrow_dma: status 0x%x, ctrl 0x%x\n", count, status, ctrl);
			status = XAXI_DMA_ReadReg(col_dma_bases[0], MM2S_DMASR);
			ctrl = XAXI_DMA_ReadReg(col_dma_bases[0], MM2S_DMACR);
			printf("col_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
			status = XAXI_DMA_ReadReg(val_dma_bases[0], MM2S_DMASR);
			ctrl = XAXI_DMA_ReadReg(val_dma_bases[0], MM2S_DMACR);
			printf("val_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
			status = XAXI_DMA_ReadReg(out_dma_bases[0], S2MM_DMASR);
			ctrl = XAXI_DMA_ReadReg(out_dma_bases[0], S2MM_DMACR);
			printf("out_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
		}
		#endif
		#ifdef MSHR_INCLUSIVE
		if ((count & 0x2fff) == 0) {
			printf("%d: SpMV not done\n", count);
			// FPGAMSHR_Get_stats_log(logname);
		}
		if (count > 0x7fff) {
			return -1;
		}
		#endif
		#ifdef GET_RUNTIME_LOG
		FPGAMSHR_Get_runtime_log();
		#endif
		for (i = 0; i < num_spmv; i++)
		{
			if (dma_state[i].rowptr.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(row_dma_bases[i], XAXIDMA_DMA_TO_DEVICE)) {
					uint32_t bytes_to_send = MIN(dma_state[i].rowptr.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					// debug_dma_printf("Sending %d bytes from addr 0x%lX on rowptr[%d]\n", bytes_to_send, dma_state[i].rowptr.next_start_addr, i);
					if (XAXI_DMA_SetAddrLength(row_dma_bases[i], dma_state[i].rowptr.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE) < 0) {
						return -1;
					}
					dma_state[i].rowptr.next_start_addr += bytes_to_send;
					dma_state[i].rowptr.bytes_left -= bytes_to_send;
				}
			}
			if (dma_state[i].col.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(col_dma_bases[i], XAXIDMA_DMA_TO_DEVICE)) {
					uint32_t bytes_to_send = MIN(dma_state[i].col.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					// debug_dma_printf("Sending %d bytes from addr 0x%lX on col[%d]\n", bytes_to_send, dma_state[i].col.next_start_addr, i);
					if (XAXI_DMA_SetAddrLength(col_dma_bases[i], dma_state[i].col.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE) < 0) {
						return -1;
					}
					dma_state[i].col.next_start_addr += bytes_to_send;
					dma_state[i].col.bytes_left -= bytes_to_send;
				}
			}
			if (dma_state[i].val.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(val_dma_bases[i], XAXIDMA_DMA_TO_DEVICE)) {
					uint32_t bytes_to_send = MIN(dma_state[i].val.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					// debug_dma_printf("Sending %d bytes from addr 0x%lX on val[%d]\n", bytes_to_send, dma_state[i].val.next_start_addr, i);
					if (XAXI_DMA_SetAddrLength(val_dma_bases[i], dma_state[i].val.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE) < 0) {
						return -1;
					}
					dma_state[i].val.next_start_addr += bytes_to_send;
					dma_state[i].val.bytes_left -= bytes_to_send;
				}
			}
			if (dma_state[i].output.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(out_dma_bases[i], XAXIDMA_DEVICE_TO_DMA)) {
					uint32_t bytes_to_send = MIN(dma_state[i].output.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					// debug_dma_printf("Sending %d bytes to addr 0x%lX from output[%d]\n", bytes_to_send, dma_state[i].output.next_start_addr, i);
					if (XAXI_DMA_SetAddrLength(out_dma_bases[i], dma_state[i].output.next_start_addr, bytes_to_send, XAXIDMA_DEVICE_TO_DMA) < 0) {
						return -1;
					}
					dma_state[i].output.next_start_addr += bytes_to_send;
					dma_state[i].output.bytes_left -= bytes_to_send;
				}
			}
		}
    }

	// #if DEBUG_DMA == 1
	// printf("REACH HERE 1, count=%d\n", count);
	// status = XAXI_DMA_ReadReg(row_dma_bases[0], MM2S_DMASR);
	// ctrl = XAXI_DMA_ReadReg(row_dma_bases[0], MM2S_DMACR);
	// printf("row_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
	// status = XAXI_DMA_ReadReg(col_dma_bases[0], MM2S_DMASR);
	// ctrl = XAXI_DMA_ReadReg(col_dma_bases[0], MM2S_DMACR);
	// printf("col_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
	// status = XAXI_DMA_ReadReg(val_dma_bases[0], MM2S_DMASR);
	// ctrl = XAXI_DMA_ReadReg(val_dma_bases[0], MM2S_DMACR);
	// printf("val_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
	// status = XAXI_DMA_ReadReg(out_dma_bases[0], S2MM_DMASR);
	// ctrl = XAXI_DMA_ReadReg(out_dma_bases[0], S2MM_DMACR);
	// printf("out_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
	// #endif

	count = 0;
	int all_idle = 0;
	int all_stat = 0;
	while (!all_idle) {
		count++;
		#if DEBUG_DMA == 1
		if ((count & 0x3fff) == 0) {
			FPGAMSHR_Get_stats_log(logname);
			printf("spmv stat: 0x%04x\n", all_stat);
			for (int j = 0; j < num_spmv; j++) {
				status = XAXI_DMA_ReadReg(row_dma_bases[j], MM2S_DMASR);
				ctrl = XAXI_DMA_ReadReg(row_dma_bases[j], MM2S_DMACR);
				printf("row_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
				status = XAXI_DMA_ReadReg(col_dma_bases[j], MM2S_DMASR);
				ctrl = XAXI_DMA_ReadReg(col_dma_bases[j], MM2S_DMACR);
				printf("col_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
				status = XAXI_DMA_ReadReg(val_dma_bases[j], MM2S_DMASR);
				ctrl = XAXI_DMA_ReadReg(val_dma_bases[j], MM2S_DMACR);
				printf("val_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
				status = XAXI_DMA_ReadReg(out_dma_bases[j], S2MM_DMASR);
				ctrl = XAXI_DMA_ReadReg(out_dma_bases[j], S2MM_DMACR);
				printf("out_dma: status 0x%x, ctrl 0x%x\n", status, ctrl);
			}
		}
		#endif
		#ifdef MSHR_INCLUSIVE
		if ((count & 0x2fff) == 0) {
			// printf("%d: SpMV not done\n", count);
			FPGAMSHR_Get_stats_log(logname);
		}
		if (count > 0x7fff) {
			return -1;
		}
		#endif
		#ifdef GET_RUNTIME_LOG
		FPGAMSHR_Get_runtime_log();
		#endif
		all_idle = 1;
		for (i = 0; i < num_spmv; i++) {
			// all_idle &= XSpmv_mult_axis_IsIdle(spmv_bases[i]);
			int res = XSpmv_mult_axis_IsIdle(spmv_bases[i]);
			all_idle &= res;
			all_stat |= res << i;
		}
	}

	FPGAMSHR_Get_stats_log(logname);
	#ifdef GET_RUNTIME_LOG
	FPGAMSHR_Get_runtime_log();
	FPGAMSHR_Output_runtime_log(logname);
	#endif

	gettimeofday(&t2, NULL);
	measure(&t1, &t2, &sec, &msec);
	printf("  cost %lu s %d ms\n", sec, msec);

	count = 0;
    all_idle = 0;
    while (!all_idle) {
		count++;
		// if (count > 10000)
		// 	break;
    	all_idle = 1;
		for (i = 0; i < num_spmv; i++) {
			all_idle &= !XAXI_DMA_Busy(out_dma_bases[i], XAXIDMA_DEVICE_TO_DMA);
		}
    }
	printf("DONE\n");
    return 0;
}

int fetch_result(int nspmv)
{
	for (int i = 0; i < nspmv; i++) {
		if (qdma_read(output_mem[i], host_output_mem[i], nout[i] * sizeof(float)) < 0) {
			fprintf(stderr, "fail to fetch output of %d spmv\n", i);
			return -1;
		}
	}
	return 0;
}

void compare_result(int nspmv)
{
	printf("Result verification: \n");
	int i, acc;
	for (acc = 0; acc < nspmv; acc++)
	{
		for (i = 0; i < rows[acc]; i++) {
			//printf("%d: %lf %lf\n", i, output_mem[i], ref_output_mem[i]);
			if ((ref_output_mem[acc][i] > 1e-5) &&
				(((((int32_t*)ref_output_mem[acc])[i] - ((int32_t*)host_output_mem[acc])[i]) > 167772) ||
				(((int32_t*)host_output_mem[acc])[i] - ((int32_t*)ref_output_mem[acc])[i]) > 167772)) {
			// float diff = ref_output_mem[acc][i] - host_output_mem[acc][i];
			// if (diff > 1e-3 || diff < -1e-3) {
				printf("%d %d: %f %f\n", acc, i, host_output_mem[acc][i], ref_output_mem[acc][i]);
				break;
			}
		}
		if (i == rows[acc]) {
			printf("spmv %d pass\n", acc);
		} else {
			printf("spmv %d fail\n", acc);
		}
	}
}

#define HBM_CHANNEL_SIZE  MB(256)
#define HBM_CHANNEL_NUM   16

struct hbm_data_config {
	// input
	uint32_t channel_num;
	// output
	uint32_t elem_num;
	uint32_t elem_num_per_pc[HBM_CHANNEL_NUM];
	uint32_t elem_num_prefix_sum[HBM_CHANNEL_NUM + 1 + NUM_REQ_HANDLERS];	// plus 1 for convenience
};


int load_vec_hbm(uint64_t hbm_addr, const char *vec_file, struct hbm_data_config *config)
{
	uint32_t const nchannel = config->channel_num;
	if (nchannel > 16 || /*nchannel < NUM_REQ_HANDLERS ||*/ (nchannel & (nchannel - 1)) != 0) {
		fprintf(stderr, "HBM channel number must be power of 2 and less than 16\n");
		return -1;
	}

	int vec_fd = open(vec_file, O_RDONLY);
	if (vec_fd < 0) {
		fprintf(stderr, "unable to open %s\n", vec_file);
		return -1;
	}

	int res = -1;
	char *buf = NULL;
	char *vec_mem = MAP_FAILED;
	uint32_t nelem;
	struct stat st;
	if (fstat(vec_fd, &st) < 0) {
		fprintf(stderr, "fail to stat %s\n", vec_file);
		goto out;
	}
	if (st.st_size % sizeof(float)) {
		fprintf(stderr, "funny file size that unaligned to data size: %lu to %lu\n", st.st_size, sizeof(float));
		goto out;
	}
	if (st.st_size > nchannel * HBM_CHANNEL_SIZE) {
		fprintf(stderr, "file size exceed capacity of %d channel(s): %lu \n", nchannel, st.st_size);
		goto out;
	}
	nelem = st.st_size / sizeof(float);
	config->elem_num = nelem;
	
	vec_mem = mmap(NULL, st.st_size, PROT_READ, MAP_SHARED, vec_fd, 0);
	if (vec_mem == MAP_FAILED) {
		fprintf(stderr, "fail to mmap %s\n", vec_file);
		goto out;
	}

	uint32_t nstrip = st.st_size / CACHELINE_SIZE;
	if (st.st_size % CACHELINE_SIZE)
		nstrip += 1;
	// division
	uint32_t nstrip_per_pc = nstrip / nchannel;
	uint32_t nleft = nstrip - nstrip_per_pc * nchannel;
	for (int i = 0; i < nchannel; i++) {
		config->elem_num_per_pc[i] = nstrip_per_pc;		// treat "elem" as "strip"
		if (nleft > 0) {
			nleft--;
			config->elem_num_per_pc[i]++;
		}
	}
	// for (int i = 0; i < nchannel; i++)
	// 	config->elem_num_prefix_sum[i] = 0;				// treat "elem" as "strip of this kind"
	// for (int i = NUM_REQ_HANDLERS; i < nchannel + NUM_REQ_HANDLERS; i++)
	// 	config->elem_num_prefix_sum[i] = config->elem_num_prefix_sum[i - NUM_REQ_HANDLERS] + config->elem_num_per_pc[i - NUM_REQ_HANDLERS];

	buf = (char *)malloc((nstrip_per_pc + 1) * CACHELINE_SIZE);
	if (buf == NULL) {
		perror("load vec mem");
		goto out;
	}

	for (int i = 0; i < nchannel; i++) {
		for (int j = 0; j < config->elem_num_per_pc[i]; j++) {
			memcpy(buf + j * CACHELINE_SIZE, vec_mem + (i + j * nchannel) * CACHELINE_SIZE, CACHELINE_SIZE);
		}
		if (qdma_write(hbm_addr + i * HBM_CHANNEL_SIZE, buf, config->elem_num_per_pc[i] * CACHELINE_SIZE) < 0) {
			perror("write vec to HBM");
			goto out;
		}
	}

	res = 0;
out:
	if (vec_mem != MAP_FAILED)
		munmap(vec_mem, st.st_size);
	if (buf != NULL)
		free(buf);
	close(vec_fd);
	return res;
}

int col_preprocess(char *buf, uint32_t buf_sz, void *args)
{
	struct hbm_data_config const *cfg = args;
	uint32_t const nchannel = cfg->channel_num;
	if (nchannel == 0)
		return 0;

	uint32_t *col_data = (uint32_t *)buf;
	uint32_t col_num = buf_sz / sizeof(uint32_t);
	// uint32_t const max_per_channel = HBM_CHANNEL_SIZE / sizeof(uint32_t);
	uint32_t const nelem_per_strip = CACHELINE_SIZE / sizeof(uint32_t);

	for (int i = 0; i < col_num; i++) {
		int strip_no = col_data[i] / nelem_per_strip;
		int strip_ch_no = strip_no % nchannel;
		int strip_ch_offset = strip_no / nchannel;
		col_data[i] = (strip_ch_no * (HBM_CHANNEL_SIZE / sizeof(uint32_t))) + 
						strip_ch_offset * nelem_per_strip + 
						(col_data[i] % nelem_per_strip);
	}
	return 0;
}

int load_vec(uint64_t fpga_addr, const char *vec_file, uint32_t *pvec_sz, size_t elem_sz,
			int (*preprocess)(char*, uint32_t, void *), void *args)
{
	int vec_fd = open(vec_file, O_RDONLY);
	if (vec_fd < 0) {
		fprintf(stderr, "unable to open %s\n", vec_file);
		return -1;
	}

	int res = -1;
	char *buf = NULL;
	uint32_t vec_sz;
	struct stat st;
	if (fstat(vec_fd, &st) < 0) {
		fprintf(stderr, "fail to stat %s\n", vec_file);
		goto out;
	}
	if (st.st_size % elem_sz) {
		fprintf(stderr, "funny file size that unaligned to data size: %lu to %lu\n", st.st_size, elem_sz);
		goto out;
	}
	vec_sz = st.st_size / elem_sz;
	*pvec_sz = vec_sz;
	
	off_t chunk_size = MIN(st.st_size, MB(8));

	buf = (char *)malloc(chunk_size);
	if (buf == NULL) {
		perror("load vec mem");
		goto out;
	}
	for (off_t off = 0; off < st.st_size; off += chunk_size) {
		chunk_size = MIN(st.st_size - off, MB(8));
		// printf("addr=0x%lx, chunk_size=0x%lx\n", fpga_addr + off, chunk_size);
		if (read(vec_fd, buf, chunk_size) < 0) {
			perror("read vec file");
			goto out;
		}
		if (preprocess) {
			preprocess(buf, chunk_size, args);
		}
		if (qdma_write(fpga_addr + off, buf, chunk_size) < 0) {
			perror("write vec to FPGA");
			fprintf(stderr, "fail to write at addr 0x%lx with %ld bytes\n", fpga_addr + off, chunk_size);
			goto out;
		}
	}

	res = 0;
out:
	if (buf != NULL)
		free(buf);
	close(vec_fd);
	return res;
}

int load_data(const char* folder_name, int nspmv, uint32_t nchannel)
{
	char full_file_name[256];
	if (strlen(folder_name) > 64) {
		fprintf(stderr, "folder name %s breaks the limit of 64 characters\n", folder_name);
		return -1;
	}
	const char *bench_name = strrchr(folder_name, '/');
	if (bench_name == NULL)
		bench_name = folder_name;
	else
		bench_name++;
	if (*bench_name == '\0') {
		fprintf(stderr, "bad folder name %s ends with '\\'\n", folder_name);
		return -1;
	}

	struct hbm_data_config hdc;
	hdc.channel_num = nchannel;
	sprintf(full_file_name, "%s/%d/%s.vec", folder_name, nspmv, bench_name);
	if (nchannel == 0) {
		if (load_vec(vect_mem_host, full_file_name, &cols, sizeof(float), NULL, NULL) < 0)
			return -1;
	} else {
		if (load_vec_hbm(vect_mem, full_file_name, &hdc) < 0)
			return -1;
		cols = hdc.elem_num;
	}

	for (int i = 0; i < nspmv; i++) {
		sprintf(full_file_name, "%s/%d/%d.val", folder_name, nspmv, i);
		if (load_vec(val_mem[i], full_file_name, &nnz[i], sizeof(float), NULL, NULL) < 0)
			return -1;
		sprintf(full_file_name, "%s/%d/%d.col", folder_name, nspmv, i);
		if (load_vec(col_mem[i], full_file_name, &nnz[i], sizeof(float), col_preprocess, &hdc) < 0)
			return -1;

		sprintf(full_file_name, "%s/%d/%d.row", folder_name, nspmv, i);
		if (load_vec(rowptr_mem[i], full_file_name, &rows[i], sizeof(float), NULL, NULL) < 0)
			return -1;
		rows[i]--; // rowptr size is rows + 1

		sprintf(full_file_name, "%s/%d/%d.exp", folder_name, nspmv, i);
		int fd = open(full_file_name, O_RDONLY);
		if (fd < 0) {
			fprintf(stderr, "unable to open [%s]\n", full_file_name);
			return -1;
		}
		struct stat st;
		if (fstat(fd, &st) < 0) {
			fprintf(stderr, "fail to stat %s\n", full_file_name);
			close(fd);
			return -1;
		}
		if (st.st_size % sizeof(float)) {
			fprintf(stderr, "funny file size that unaligned to data size: %lu to %lu\n", st.st_size, sizeof(float));
			return -1;
		}
		nout[i] = st.st_size / sizeof(float);
		host_output_mem[i] = (float *)malloc(st.st_size);
		ref_output_mem[i] = (float *)malloc(st.st_size);
		if (host_output_mem[i] == NULL || ref_output_mem[i] == NULL) {
			fprintf(stderr, "fail to malloc output memory\n");
			close(fd);
			return -1;
		}
		int res = read(fd, ref_output_mem[i], st.st_size);
		close(fd);
		if (res < 0)
			return -1;
	}
	return 0;
}


/**
 * USAGE:
 * $ ./spmvtest QDMA_DEV_PATH BENCH_MATRIX_PATH
 */
int main(int argc, char *argv[])
{
	if (argc < 3) {
		fprintf(stderr, "args too less!\nbin QDMA_DEV_PATH BENCH_NAME\n");
		return -1;
	}

	extern int qdmafd;
	qdmafd = open(argv[1], O_RDWR);
	if (qdmafd < 0) {
		fprintf(stderr, "unable to open device %s\n", argv[1]);
		perror("qdma open");
		return -1;
	}

	char *benchmark = argv[2];
	char *benchname = basename(benchmark);
	if (benchname == NULL)
		benchname = benchmark;
	char logname[256];
	
	int num_spmv;
	int cache_divider = 0;
	int MSHR_divider = 0;
	int subRow_divider = 0;
	int mshr_count_idx;
	uint32_t num_hbm_channel = 1;
	uint32_t total_cache_size = (CACHE_SIZE / 1024) * NUM_REQ_HANDLERS; // KB
	uint32_t total_MSHR_number = MSHR_PER_HASH_TABLE * MSHR_HASH_TABLES * NUM_REQ_HANDLERS;
	num_spmv = NUM_SPMV;

	FPGAMSHR_Set_base(fpgamshr_base);

	printf("init DMA\n");
	#ifdef MSHR_INCLUSIVE
	FPGAMSHR_Reset();
	#endif
	init_dma(num_spmv);
	printf("DMA init done\n");

	// for (num_hbm_channel = 1; num_hbm_channel <= 16; num_hbm_channel <<= 1)
	{	
		debug_data_read_printf("Reading data...\n");
		// if (load_data(benchmark, num_spmv, num_hbm_channel) < 0) {
		if (load_data(benchmark, num_spmv, 0) < 0) {
			fprintf(stderr, "fail to load data %s into FPGA\n", benchmark);
			// continue;
		}
		debug_data_read_printf("...done\n");
		printf("Data stat:\ncols: %u\n", cols);
		for (int i = 0; i < num_spmv; i++) {
			printf("spmv %d: nnz=%u, rows=%u, nout=%u\n", i, nnz[i], rows[i], nout[i]);
		}

	#if FPGAMSHR_EXISTS
		// we iterate over all possible cache size values
		// effective_cache_size = total_cache_size / (2 ^ cache_divider)
		for (cache_divider = 0; cache_divider < CACHE_SIZE_REDUCTION_VALUES; cache_divider++) {
		// for (MSHR_divider = 2; MSHR_divider < 3; MSHR_divider++) { // for mshr-rich test
			#ifndef MSHR_INCLUSIVE
			// MSHR_divider = cache_divider;
			subRow_divider = MSHR_divider;
			#else
			MSHR_divider = cache_divider;
			#endif
			uint32_t cache_size = cache_divider == CACHE_SIZE_REDUCTION_VALUES ? 0 : total_cache_size / (1 << cache_divider);
			uint32_t MSHR_num = total_MSHR_number / (1 << MSHR_divider);
			uint32_t subRow_num = (total_MSHR_number / NUM_REQ_HANDLERS) / (1 << (subRow_divider));
			printf("------------ Test Info ------------\n%s\nSpMV: %d\nHBM channels: %d\nCache size: %uKB\nMSHR number: %u\nSubentry row number: %u\n-----------------------------------\n",
					benchname, num_spmv, num_hbm_channel, cache_size, MSHR_num, subRow_num);
			snprintf(logname, sizeof(logname), "%s_%dpe_%upc_%uKB_%uMSHR",
						benchname, num_spmv, num_hbm_channel, cache_size, MSHR_num);

			FPGAMSHR_Clear_stats();
			FPGAMSHR_Invalidate_cache();
			if (cache_divider == CACHE_SIZE_REDUCTION_VALUES) {
				FPGAMSHR_Disable_cache();
			} else {
				FPGAMSHR_Enable_cache();
				FPGAMSHR_SetCacheDivider(cache_divider);
				#ifndef MSHR_INCLUSIVE
				FPGAMSHR_SetMshrDivider(MSHR_divider);
				FPGAMSHR_SetMaxSubentryRow(subRow_num);
				#endif
			}

			if (test_spmv_mult_axis(num_spmv, logname) != 0) {
				return -1;
			}
			fetch_result(num_spmv);
			compare_result(num_spmv);
		// }
		}
	#else
		printf("------------ Test Info ------------\n%s\nSpMV: %d\nHBM channels: %d\nNo MSHR\n-----------------------------------\n",
					benchname, num_spmv, num_hbm_channel);
		snprintf(logname, sizeof(logname), "%s_%dpe_%upc_none", benchname, num_spmv, num_hbm_channel);
		FPGAMSHR_Clear_stats();
		if (test_spmv_mult_axis(num_spmv, logname) != 0)
			return -1;
		fetch_result(num_spmv);
		compare_result(num_spmv);
	#endif

		// Uncomment to get a full dump of the internal performance registers
		// FPGAMSHR_Get_stats_pretty();
		for (int i = 0; i < num_spmv; i++) {
			free(ref_output_mem[i]);
			ref_output_mem[i] = NULL;
			free(host_output_mem[i]);
			host_output_mem[i] = NULL;
		}
	}
	close(qdmafd);
    return 0;
}
