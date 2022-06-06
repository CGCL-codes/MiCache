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
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <sys/sendfile.h>
#include <sys/mman.h>

#include "xfully_pipelined_spmv.h"
#include "xaxi_dma.h"
#include "fpgamshr.h"
#include "inttypes.h"

uint32_t cols;
uint32_t nnz[NUM_SPMV];
uint32_t rows[NUM_SPMV];
uint32_t nout[NUM_SPMV];

#define KB(x)	((x) * 1024)
#define MB(x)	((x) * 1024 * 1024)
#define GB(x)	((x) * 1024 * 1024 * 1024)

uint64_t vect_mem = HBM_BASE_ADDR;
uint64_t rowptr_mem[NUM_SPMV] = {
	HBM_BASE_ADDR + GB(1)
};
uint64_t col_mem[NUM_SPMV] = {
	HBM_BASE_ADDR + GB(1) + MB(256)
};
uint64_t val_mem[NUM_SPMV] = {
	HBM_BASE_ADDR + GB(1) + MB(256 * 2)
};
uint64_t output_mem[NUM_SPMV] = {
	HBM_BASE_ADDR + GB(1) + MB(256 * 3)
};

float* host_output_mem[NUM_SPMV] = { NULL };
float* ref_output_mem[NUM_SPMV] = { NULL };

//#define NUM_BENCHMARKS 14
//char* benchmarks[NUM_BENCHMARKS] = {"pds-80", "amazon-2", "cnr-2000", "dblp-201", "enron", "eu-2005", "flickr",
//		"in-2004", "internet","ljournal", "rail4284", "webbase-", "youtube", "bcspwr01"};

uint64_t spmv_bases[NUM_SPMV];
uint64_t row_dma_bases[NUM_SPMV];
uint64_t col_dma_bases[NUM_SPMV];
uint64_t val_dma_bases[NUM_SPMV];
extern volatile uint64_t *fpgamshr_base;

void init_dma(void)
{
	for (int i = 0; i < NUM_SPMV; i++) {
		XAXI_DMA_Enable(row_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Enable(row_dma_bases[i], XAXIDMA_DEVICE_TO_DMA);
		XAXI_DMA_Enable(col_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
		XAXI_DMA_Enable(val_dma_bases[i], XAXIDMA_DMA_TO_DEVICE);
	}
}

#define MAX_TRANSFER_SIZE_BYTES ((1 << 14) - sizeof(uint64_t))
#define MIN(a, b) ((a) < (b) ? (a) : (b))

int test_spmv_mult_axis(int num_spmv)
{
	typedef struct {
		uint32_t next_start_addr;
		uint32_t bytes_left;
	} dma_state_t;

	typedef struct {
		dma_state_t val;
		dma_state_t col;
		dma_state_t rowptr;
		dma_state_t output;
	} spmv_dma_state_t;

	spmv_dma_state_t dma_state[num_spmv];

	int i;

	for (i = 0; i < num_spmv; i++) {
		dma_state[i].val.next_start_addr = val_mem[i];
		dma_state[i].val.bytes_left = nnz[i] * sizeof(float);
		dma_state[i].col.next_start_addr = col_mem[i];
		dma_state[i].col.bytes_left = nnz[i] * sizeof(unsigned);

		dma_state[i].rowptr.next_start_addr = rowptr_mem[i];
		dma_state[i].rowptr.bytes_left = (rows[i] + 1)*sizeof(int);

		dma_state[i].output.next_start_addr = output_mem[i];
		dma_state[i].output.bytes_left = rows[i] * sizeof(float);

		XSpmv_mult_axis_Set_val_size(spmv_bases[i], nnz[i]);
		XSpmv_mult_axis_Set_output_size(spmv_bases[i], rows[i]);
		XSpmv_mult_axis_Set_vect_mem(spmv_bases[i], vect_mem);

		debug_dma_printf("i=%d, curr_nnz=%d, curr_row_num=%d\n\r", i, nnz[i], rows[i]);
	}

	for (i = 0; i < num_spmv; i++)
	{
		uint32_t bytes_to_send = MIN(dma_state[i].val.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes from addr 0x%08X on val[%d]\n\r", bytes_to_send, dma_state[i].val.next_start_addr, i);
		XAXI_DMA_SetAddrLength(val_dma_bases[i], dma_state[i].val.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE);
		dma_state[i].val.next_start_addr += bytes_to_send;
		dma_state[i].val.bytes_left -= bytes_to_send;

		bytes_to_send = MIN(dma_state[i].col.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes from addr 0x%08X on col[%d]\n\r", bytes_to_send, dma_state[i].col.next_start_addr, i);
		XAXI_DMA_SetAddrLength(col_dma_bases[i], dma_state[i].col.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE);
		dma_state[i].col.next_start_addr += bytes_to_send;
		dma_state[i].col.bytes_left -= bytes_to_send;

		bytes_to_send = MIN(dma_state[i].rowptr.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes from addr 0x%08X on rowptr[%d]\n\r", bytes_to_send, dma_state[i].rowptr.next_start_addr, i);
		XAXI_DMA_SetAddrLength(row_dma_bases[i], dma_state[i].rowptr.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE);
		dma_state[i].rowptr.next_start_addr += bytes_to_send;
		dma_state[i].rowptr.bytes_left -= bytes_to_send;

		bytes_to_send = MIN(dma_state[i].output.bytes_left, MAX_TRANSFER_SIZE_BYTES);
		debug_dma_printf("Sending %d bytes to addr 0x%08X from output[%d]\n\r", bytes_to_send, dma_state[i].output.next_start_addr, i);
		XAXI_DMA_SetAddrLength(row_dma_bases[i], dma_state[i].output.next_start_addr, bytes_to_send, XAXIDMA_DEVICE_TO_DMA);
		dma_state[i].output.next_start_addr += bytes_to_send;
		dma_state[i].output.bytes_left -= bytes_to_send;
	}

    for (i = 0; i < num_spmv; i++) {
		XSpmv_mult_axis_Start(spmv_bases[i]);
	}

    int any_transfers_pending = 1;
    while (any_transfers_pending)
	{
    	any_transfers_pending = 0;
		for (i = 0; i < num_spmv; i++)
		{
			if (dma_state[i].val.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(val_dma_bases[i], XAXIDMA_DMA_TO_DEVICE)) {
					uint32_t bytes_to_send = MIN(dma_state[i].val.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					debug_dma_printf("Sending %d bytes from addr 0x%08X on val[%d]\n\r", bytes_to_send, dma_state[i].val.next_start_addr, i);
					XAXI_DMA_SetAddrLength(val_dma_bases[i], dma_state[i].val.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE);
					dma_state[i].val.next_start_addr += bytes_to_send;
					dma_state[i].val.bytes_left -= bytes_to_send;
				}
			}
			if (dma_state[i].col.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(col_dma_bases[i], XAXIDMA_DMA_TO_DEVICE)) {
					uint32_t bytes_to_send = MIN(dma_state[i].col.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					debug_dma_printf("Sending %d bytes from addr 0x%08X on col[%d]\n\r", bytes_to_send, dma_state[i].col.next_start_addr, i);
					XAXI_DMA_SetAddrLength(col_dma_bases[i], dma_state[i].col.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE);
					dma_state[i].col.next_start_addr += bytes_to_send;
					dma_state[i].col.bytes_left -= bytes_to_send;
				}
			}
			if (dma_state[i].rowptr.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(row_dma_bases[i], XAXIDMA_DMA_TO_DEVICE)) {
					uint32_t bytes_to_send = MIN(dma_state[i].rowptr.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					debug_dma_printf("Sending %d bytes from addr 0x%08X on rowptr[%d]\n\r", bytes_to_send, dma_state[i].rowptr.next_start_addr, i);
					XAXI_DMA_SetAddrLength(row_dma_bases[i], dma_state[i].rowptr.next_start_addr, bytes_to_send, XAXIDMA_DMA_TO_DEVICE);
					dma_state[i].rowptr.next_start_addr += bytes_to_send;
					dma_state[i].rowptr.bytes_left -= bytes_to_send;
				}
			}
			if (dma_state[i].output.bytes_left > 0) {
				any_transfers_pending = 1;
				if (!XAXI_DMA_Busy(row_dma_bases[i], XAXIDMA_DEVICE_TO_DMA)) {
					uint32_t bytes_to_send = MIN(dma_state[i].output.bytes_left, MAX_TRANSFER_SIZE_BYTES);
					debug_dma_printf("Sending %d bytes to addr 0x%08X from output[%d]\n\r", bytes_to_send, dma_state[i].output.next_start_addr, i);
					XAXI_DMA_SetAddrLength(row_dma_bases[i], dma_state[i].output.next_start_addr, bytes_to_send, XAXIDMA_DEVICE_TO_DMA);
					dma_state[i].output.next_start_addr += bytes_to_send;
					dma_state[i].output.bytes_left -= bytes_to_send;
				}
			}
		}
    }

	int all_idle = 0;
	while (!all_idle) {
		all_idle = 1;
		for (i = 0; i < num_spmv; i++) {
			all_idle &= XSpmv_mult_axis_IsIdle(spmv_bases[i]);
		}
	}

    all_idle = 0;
    while (!all_idle) {
    	all_idle = 1;
		for (i = 0; i < num_spmv; i++) {
			all_idle &= !XAXI_DMA_Busy(row_dma_bases[i], XAXIDMA_DEVICE_TO_DMA);
		}
    }

    return 0;
}

int fetch_result(int fpga_fd, int nspmv)
{
	for (int i = 0; i < nspmv; i++) {
		if (lseek(fpga_fd, output_mem[i], SEEK_SET) < 0 ||
			read(fpga_fd, host_output_mem[i], nout[i] * sizeof(float)) < 0)
		{
			fprintf(stderr, "fail to fetch output of %d spmv\n\r", i);
			return -1;
		}
	}
	return 0;
}

void compare_result(int nspmv)
{
	printf("Result verification: \n\r");
	int i, acc;
	for (acc = 0; acc < nspmv; acc++)
	{
		for (i = 0; i < rows[acc]; i++) {
			//printf("%d: %lf %lf\n\r", i, output_mem[i], ref_output_mem[i]);
			if ((ref_output_mem[acc][i] > 1e-5) &&
				(((((int32_t*)ref_output_mem[acc])[i] - ((int32_t*)host_output_mem[acc])[i]) > 167772) ||
				(((int32_t*)host_output_mem[acc])[i] - ((int32_t*)ref_output_mem[acc])[i]) > 167772))
			{
				printf("%d %d: %lf %lf\n\r", acc, i, host_output_mem[acc][i], ref_output_mem[acc][i]);
				break;
			}
		}
		if (i == rows[acc]) {
			printf("spmv %d pass\n\r", acc);
		} else {
			printf("spmv %d fail\n\r", acc);
		}
	}
}

int load_vec(int fpga_fd, uint64_t fpga_addr, const char *vec_file, uint32_t *pvec_sz, size_t elem_sz)
{
	int vec_fd = open(vec_file, O_RDONLY);
	if (vec_fd < 0) {
		fprintf(stderr, "unable to open [%s]\n\r", vec_file);
		return -1;
	}

	uint32_t vec_sz;
	if (read(vec_fd, &vec_sz, sizeof(vec_sz)) < 0) {
		fprintf(stderr, "fail to read from [%s]\n\r", vec_file);
		close(vec_fd);
		return -1;
	}
	*pvec_sz = vec_sz;

	if (lseek(fpga_fd, fpga_addr, SEEK_SET) < 0) {
		fprintf(stderr, "fail to seek FPGA address at %lu\n\r", fpga_addr);
		close(vec_fd);
		return -1;
	}

	int res = 0;
	if (sendfile(vec_fd, fpga_fd, NULL, vec_sz * elem_sz) < 0) {
		fprintf(stderr, "fail to sendfile to FPGA device\n\r");
		res = -1;
	}

	close(vec_fd);
	return res;
}

int load_data(int fpga_fd, const char* folder_name, int nspmv)
{
	char full_file_name[64];

	if (strlen(folder_name) > 8) {
		fprintf(stderr, "folder name %s breaks the limit of 8 characters\n\r", folder_name);
		return -1;
	}

	sprintf(full_file_name, "%s/%d/%s.vec", folder_name, nspmv, folder_name);
	if (load_vec(fpga_fd, vect_mem, full_file_name, &cols, sizeof(float)) < 0)
		return -1;
	for (int i = 0; i < nspmv; i++) {
		sprintf(full_file_name, "%s/%d/%d.val", folder_name, nspmv, i);
		if (load_vec(fpga_fd, val_mem[i], full_file_name, &nnz[i], sizeof(float)) < 0)
			return -1;
		sprintf(full_file_name, "%s/%d/%d.col", folder_name, nspmv, i);
		if (load_vec(fpga_fd, col_mem[i], full_file_name, &nnz[i], sizeof(float)) < 0)
			return -1;

		sprintf(full_file_name, "%s/%d/%d.row", folder_name, nspmv, i);
		if (load_vec(fpga_fd, rowptr_mem[i], full_file_name, &rows[i], sizeof(float)) < 0)
			return -1;
		rows[i]--; // rowptr size is rows + 1

		sprintf(full_file_name, "%s/%d/%d.exp", folder_name, nspmv, i);
		int fd = open(full_file_name, O_RDONLY);
		if (fd < 0) {
			fprintf(stderr, "unable to open [%s]\n\r", full_file_name);
			return -1;
		}
		if (read(fd, &nout[i], sizeof(nout[i])) < 0) {
			fprintf(stderr, "fail to read from [%s]\n\r", full_file_name);
			close(fd);
			return -1;
		}
		host_output_mem[i] = (float *)malloc(nout[i] * sizeof(float));
		ref_output_mem[i] = (float *)malloc(nout[i] * sizeof(float));
		if (host_output_mem == NULL || ref_output_mem == NULL) {
			fprintf(stderr, "fail to malloc output memory\n\r");
			close(fd);
			return -1;
		}
		int res = read(fd, ref_output_mem, nout[i] * sizeof(float));
		close(fd);
		if (res < 0)
			return -1;
	}
	return 0;
}

#define FPGAMSHR_BASEADDR	0x04A10000
#define FPGAMSHR_SPACESIZE	((NUM_INPUTS + NUM_REQ_HANDLERS) * (REGS_PER_REQ_HANDLER + REGS_PER_REQ_HANDLER_MODULE) * sizeof(uint64_t))
uint64_t spmv_addrs[NUM_SPMV] = {
	0x04A00000
};
uint64_t row_dma_addrs[NUM_SPMV] = {
	0x01E20000
};
uint64_t col_dma_addrs[NUM_SPMV] = {
	0x01E00000
};
uint64_t val_dma_addrs[NUM_SPMV] = {
	0x01E10000
};

int map_devs(int fpga_fd, int nspmv)
{
	fpgamshr_base = mmap(NULL, FPGAMSHR_SPACESIZE, PROT_READ|PROT_WRITE, MAP_SHARED, fpga_fd, FPGAMSHR_BASEADDR);
	if (fpgamshr_base == MAP_FAILED) {
		return -1;
	}

	for (int i = 0; i < nspmv; i++) {
		if ((spmv_bases[i] = (uint64_t)mmap(NULL, 0x1000, PROT_READ|PROT_WRITE, MAP_SHARED, fpga_fd, spmv_addrs[i])) == (uint64_t)MAP_FAILED ||
			(row_dma_bases[i] = (uint64_t)mmap(NULL, 0x1000, PROT_READ|PROT_WRITE, MAP_SHARED, fpga_fd, row_dma_addrs[i])) == (uint64_t)MAP_FAILED ||
			(col_dma_bases[i] = (uint64_t)mmap(NULL, 0x1000, PROT_READ|PROT_WRITE, MAP_SHARED, fpga_fd, col_dma_addrs[i])) == (uint64_t)MAP_FAILED ||
			(val_dma_bases[i] = (uint64_t)mmap(NULL, 0x1000, PROT_READ|PROT_WRITE, MAP_SHARED, fpga_fd, val_dma_addrs[i])) == (uint64_t)MAP_FAILED)
			return -1;
	}
	return 0;
}

void unmap_devs(int fpga_fd, int nspmv)
{
	munmap((void*)fpgamshr_base, FPGAMSHR_SPACESIZE);
	fpgamshr_base = NULL;
	for (int i = 0; i < nspmv; i++) {
		munmap((void*)spmv_bases[i], 0x1000);
		munmap((void*)row_dma_bases[i], 0x1000);
		munmap((void*)col_dma_bases[i], 0x1000);
		munmap((void*)val_dma_bases[i], 0x1000);
		spmv_bases[i] = row_dma_bases[i] = col_dma_bases[i] = val_dma_bases[i] = 0;
	}
}

/**
 * this QDMA_DEV_PATH BENCH_NAME
 */
int main(int argc, char *argv[])
{
	if (argc < 3) {
		fprintf(stderr, "args too less!\n\rbin QDMA_DEV_PATH BENCH_NAME\n\r");
		return -1;
	}

	int fpga_fd = open(argv[1], O_RDWR);
	if (fpga_fd < 0) {
		fprintf(stderr, "unable to open device %s, %d.\n\r", argv[1], fpga_fd);
		return -1;
	}
	if (map_devs(fpga_fd, NUM_SPMV) < 0) {
		fprintf(stderr, "unable to map devices\n\r");
		return -1;
	}

	char *benchmark = argv[2];
	int num_spmv, cache_divider, benchmark_idx, mshr_count_idx;

#if FPGAMSHR_EXISTS
	printf("benchmark numAcc robDepth mshrHashTables mshrPerHashTable ldBufPerRow ldBufRows cacheWays cacheSize totalCycles ");
#else
	printf("benchmark numAcc cacheSize totalCycles ");
#endif
	init_dma();
	FPGAMSHR_Get_stats_header();

	// for (num_spmv = 1; num_spmv <= NUM_SPMV; num_spmv++) {
		
		// debug_data_read_printf("Reading data...\r\n");
		if (load_data(fpga_fd, benchmark, NUM_SPMV) < 0) {
			fprintf(stderr, "fail to load data %s into FPGA\n\r", benchmark);
			return -1;
		}
		// debug_data_read_printf("...done\r\n");

	#if FPGAMSHR_EXISTS
		// we iterate over all possible cache size values
		// effective_cache_size = total_cache_size / (2 ^ cache_divider)
		for (cache_divider = 0; cache_divider <= CACHE_SIZE_REDUCTION_VALUES; cache_divider++) {
			FPGAMSHR_Clear_stats();
			FPGAMSHR_Invalidate_cache();
			if (cache_divider == CACHE_SIZE_REDUCTION_VALUES) {
				FPGAMSHR_Disable_cache();
			} else {
				FPGAMSHR_Enable_cache();
				FPGAMSHR_SetCacheDivider(cache_divider);
			}
			// run parameters
			printf("\r\n%s %d %d %d %d %d %d %d %d ", benchmark, NUM_SPMV,
				ROB_DEPTH, MSHR_HASH_TABLES, MSHR_PER_HASH_TABLE, SE_BUF_ENTRIES_PER_ROW, SE_BUF_ROWS, CACHE_WAYS,
				cache_divider == CACHE_SIZE_REDUCTION_VALUES ? 0 : CACHE_SIZE >> cache_divider);
			FPGAMSHR_Get_stats_row();
			if (test_spmv_mult_axis(NUM_SPMV) != 0)
				return -1;
			fetch_result(fpga_fd, NUM_SPMV);
			compare_result(NUM_SPMV);
		}
	#else
		cache_divider = CACHE_SIZE_REDUCTION_VALUES + 1;
		printf("\r\n%s %d 0 ", benchmark, NUM_SPMV);
		if (test_spmv_mult_axis(NUM_SPMV) != 0)
			return -1;
		fetch_result(fpga_fd, NUM_SPMV);
		compare_result(NUM_SPMV);
	#endif

		// Uncomment to get a full dump of the internal performance registers
		// FPGAMSHR_Get_stats_pretty();
		for (int i = 0; i < NUM_SPMV; i++) {
			free(ref_output_mem[i]);
			ref_output_mem[i] = NULL;
			free(host_output_mem[i]);
			host_output_mem[i] = NULL;
		}
	// }
	unmap_devs(fpga_fd, NUM_SPMV);
	close(fpga_fd);
    return 0;
}
