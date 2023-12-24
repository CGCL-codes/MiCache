#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>
#include <limits.h>
#include "def.h"
#include "params.h"

#define MEMORY_SPAN					(1 << ADDR_BITS)

#define NUM_INPUTS					4
#define NUM_MEMPORT					1
#define ROB_DEPTH					0
#ifndef PARAMS_H
#define MSHR_HASH_TABLES			4
#define MSHR_PER_HASH_TABLE			1024
#define CACHE_WAYS					4
#define CACHE_SIZE					262144
#define CACHE_SIZE_REDUCTION_WIDTH	3
#define NUM_REQ_HANDLERS			4
#endif
#define CACHE_SIZE_REDUCTION_VALUES	(1 << CACHE_SIZE_REDUCTION_WIDTH)
#define CACHELINE_SIZE				64
#define REGS_PER_REQ_HANDLER		512
#define REGS_PER_REQ_HANDLER_MODULE	256

// #define CACHE_RECV_REQS_OFFSET						(0)
// #define CACHE_HITS_OFFSET							(1)
// #define CACHE_CYCLES_OUT_MISSES_STALL_OFFSET		(2)
// #define CACHE_CYCLES_OUT_DATA_STALL_OFFSET			(3)
#define MSHR_CURRENTLY_USED_OFFSET					(0)
#define MSHR_MAX_USED_OFFSET						(1)
#define SUBE_MAX_USED_OFFSET						(2)
#define MSHR_COLLISION_TRIGGER_COUNT_OFFSET			(3)
#define MSHR_CYCLES_IN_COLLISION_OFFSET				(4)
#define MSHR_STALL_TRIGGER_COUNT_OFFSET				(5)
#define MSHR_CYCLES_IN_STALL_OFFSET					(6)
#define MSHR_ACCEPTED_ALLOCS_OFFSET					(7)
#define MSHR_ACCEPTED_DEALLOCS_OFFSET				(8)
#define MSHR_CYCLES_ALLOCS_STALLED_OFFSET			(9)
#define MSHR_CYCLES_DEALLOCS_STALLED_OFFSET			(10)
#define MSHR_ENQUEUED_MEM_REQS_OFFSET				(11)
#define CACHE_HITS_OFFSET							(12)
#define MSHR_ACCUM_USED_MSHR_OFFSET					(13)
#define SUBE_FULL_STALL_OFFSET						(14)
#define RESP_GEN_ACCEPTED_INPUTS_OFFSET				(REGS_PER_REQ_HANDLER_MODULE)
#define RESP_GEN_RESP_SENT_OUT_OFFSET				(REGS_PER_REQ_HANDLER_MODULE + 1)
#define RESP_GEN_CYCLES_OUT_NOT_READY_OFFSET		(REGS_PER_REQ_HANDLER_MODULE + 2)
#define ROB_RECEIVED_REQS							(0)
#define ROB_RECEIVED_RESP							(1)
#define ROB_CURR_USED_ENTRIES						(2)
#define ROB_MAX_USED_ENTRIES						(3)
#define ROB_SENT_RESP								(4)
#define ROB_CYCLES_FULL_STALLED						(5)
#define ROB_CYCLES_REQS_IN_STALLED					(6)
#define ROB_CYCLES_REQS_OUT_STALLED					(7)
#define ROB_CYCLES_RESP_OUT_STALLED					(8)

static uint64_t _fpgamshr_base;

uint64_t FPGAMSHR_Read_reg(uint32_t offset) {
	uint64_t data;
	if (qdma_read(_fpgamshr_base + offset * sizeof(uint64_t), &data, sizeof(data)) < 0) {
		perror("FPGAMSHR_Read_reg");
		exit(-1);
	}
	return data;
}

void FPGAMSHR_Write_reg(uint32_t offset, uint64_t data) {
	// printf("FPGAMSHR_Write_reg: _fpgamshr_base=%p\n\r", (void*)(_fpgamshr_base));
	// printf("FPGAMSHR_Write_reg: addr=%p, data=%u, &data=%p\n\r", (void*)(_fpgamshr_base + offset), data, &data);
	if (qdma_write(_fpgamshr_base + offset, &data, sizeof(data)) < 0) {
		perror("FPGAMSHR_Write_reg");
		fprintf(stderr, "Fail to write at 0x%lx\n", _fpgamshr_base + offset);
		exit(-1);
	}
}

void FPGAMSHR_Set_base(uint64_t base) {
	// printf("FPGAMSHR_Set_base: base=%p\n\r", (void*)base);
	_fpgamshr_base = base;
	// printf("FPGAMSHR_Set_base: _fpgamshr_base=%p\n\r", (void*)(_fpgamshr_base));
}

uint64_t FPGAMSHR_Get_base() {
	return _fpgamshr_base;
}

void FPGAMSHR_Clear_stats() {
	// *(volatile uint32_t *)fpgamshr_base = 1;
	FPGAMSHR_Write_reg(0, 1);
}

void FPGAMSHR_Profiling_snapshot() {
	// *(volatile uint32_t *)fpgamshr_base = 2;
	FPGAMSHR_Write_reg(0, 2);
}

void FPGAMSHR_Invalidate_cache() {
	// *(volatile uint32_t*)fpgamshr_base = 4;
	FPGAMSHR_Write_reg(0, 4);
}

void FPGAMSHR_Enable_cache() {
	// *(volatile uint32_t*)fpgamshr_base = 8;
	FPGAMSHR_Write_reg(0, 8);
}

void FPGAMSHR_Disable_cache() {
	// *(volatile uint32_t*)fpgamshr_base = 16;
	FPGAMSHR_Write_reg(0, 16);
}

void FPGAMSHR_SetCacheDivider(uint64_t div) {
	// *(volatile uint32_t*)(fpgamshr_base + 1) = div;
	FPGAMSHR_Write_reg(8, div);
}

void FPGAMSHR_SetMaxMSHR(uint64_t mshr) {
	// *(volatile uint32_t*)(fpgamshr_base + 2) = mshr;
	FPGAMSHR_Write_reg(16, mshr);
}

void FPGAMSHR_Reset() {
	FPGAMSHR_Write_reg(24, 8);
	sleep(1);
}

void FPGAMSHR_Get_stats_log(const char *benchname) {
	// get snapshot
	FPGAMSHR_Profiling_snapshot();

	time_t now;
	time(&now);
	struct tm *t = localtime(&now);
	char filename[256];
	snprintf(filename, sizeof(filename), "%s_%02d%02d%02d%02d%02d.csv", benchname, t->tm_mon + 1, t->tm_mday, t->tm_hour, t->tm_min, t->tm_sec);
	FILE *flog = fopen(filename, "w");
	int i;
#if FPGAMSHR_EXISTS
	uint64_t stats_mshr[NUM_REQ_HANDLERS][18];
	// uint64_t stats_subentry[NUM_REQ_HANDLERS][13];
	uint64_t stats_respgen[NUM_REQ_HANDLERS][3];

	for (i = 0; i < NUM_REQ_HANDLERS; i++) {
		uint64_t handler_offset = i * REGS_PER_REQ_HANDLER;
		if (qdma_read(_fpgamshr_base + handler_offset * sizeof(uint64_t),
			stats_mshr[i], sizeof(stats_mshr[i])) < 0) {
			perror("FPGAMSHR read MSHR statistic");
		}
		if (qdma_read(_fpgamshr_base + (handler_offset + RESP_GEN_ACCEPTED_INPUTS_OFFSET) * sizeof(uint64_t),
			stats_respgen[i], sizeof(stats_respgen[i])) < 0) {
			perror("FPGAMSHR read respgen statistic");
		}
	}
#endif

	uint64_t stats_input[NUM_INPUTS][9];
	uint64_t misc_statistic[1 + NUM_MEMPORT * 3];

	for (i = 0; i < NUM_INPUTS; i++) {
		if (qdma_read(_fpgamshr_base + (NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER * sizeof(uint64_t),
			stats_input[i], sizeof(stats_input[i])) < 0) {
			perror("FPGAMSHR read input statistic");
		}
	}
	if (qdma_read(_fpgamshr_base + (NUM_INPUTS + NUM_REQ_HANDLERS) * REGS_PER_REQ_HANDLER * sizeof(uint64_t),
		misc_statistic, sizeof(misc_statistic)) < 0) {
		perror("FPGAMSHR read misc statistic");
	}

	// output to log files
#if FPGAMSHR_EXISTS

	fprintf(flog, "\n\nMSHR");
	const char *items_mshr[] = {
		"currently used MSHR",
		"max used MSHR",
		"max used subentry",
	#if MSHR_HASH_TABLES > 0
		"collison trigger count",
		"cycles spent handling collisons",
	#else // MSHR_HASH_TABLES > 0
		"cycles MSHR full",
		"cycles LdBuf full",
	#endif // MSHR_HASH_TABLES > 0
		"stall trigger count",
		"cycles spent stalling",
		"accepted allocs count",
		"accepted deallocs count",
		"cycles allocs stall",
		"cycles deallocs stall",
		"enqueued mem reqs count",
		"cache hit count",
		"subentry full count",
		"accum used MSHR",
		"cycles subentry full stall",
		"deallocs retry count",
		"ctrlSignal"
	};
	for (i = 0; i < sizeof(stats_mshr[0])/sizeof(stats_mshr[0][0]); i++) {
		fprintf(flog, "\n%s", items_mshr[i]);
		for (int j = 0; j < NUM_REQ_HANDLERS; j++) {
			fprintf(flog, ",%lu", stats_mshr[j][i]);
		}
	}

	fprintf(flog, "\n\nResponse Generator");
	const char *items_respgen[] = {
		"accepted inputs count",
		"responses sent out count",
		"cycles out not ready"
	};
	for (i = 0; i < sizeof(stats_respgen[0])/sizeof(stats_respgen[0][0]); i++) {
		fprintf(flog, "\n%s", items_respgen[i]);
		for (int j = 0; j < NUM_REQ_HANDLERS; j++) {
			fprintf(flog, ",%lu", stats_respgen[j][i]);
		}
	}
#endif // MSHR_PER_HASH_TABLE > 0

	fprintf(flog, "\n\nROB Input");
	const char *items_input[] = {
		"received requests",
		"received responses",
		"currently used entries",
		"max used entries",
		"sent responses",
		"cycles full stall",
		"cycles reqs in stall",
		"cycles reqs out stall",
		// "cycles resp in stall",
		"cycles resp out stall"
	};
	for (i = 0; i < sizeof(stats_input[0])/sizeof(stats_input[0][0]); i++) {
		fprintf(flog, "\n%s", items_input[i]);
		for (int j = 0; j < NUM_INPUTS; j++) {
			fprintf(flog, ",%lu", stats_input[j][i]);
		}
	}

	fprintf(flog, "\n\ntotal cycles,%lu\n", misc_statistic[0]);

	// const char *items_mem[] = {
	// 	"cycles not ready",
	// 	"sent requests",
	// 	"received responses"
	// };
	// for (i = 0; i < sizeof(items_mem)/sizeof(items_mem[0]); i++) {
	// 	fprintf(flog, "\n%s", items_mem[i]);
	// 	for (int j = 0; j < NUM_MEMPORT; j++) {
	// 		fprintf(flog, ",%lu", misc_statistic[1 + j + i * NUM_MEMPORT]);
	// 	}
	// }
	fprintf(flog, "\nMemory Interface\nPC#,cycles not ready,sent requests,received responses");
	for (i = 0; i < NUM_MEMPORT; i++) {
		fprintf(flog, "\n%d", i);
		for (int j = 0; j < 3; j++) {
			fprintf(flog, ",%lu", misc_statistic[1 + i + j * NUM_MEMPORT]);
		}
	}
	fprintf(flog, "\n");
	fclose(flog);
}

#define MAX_FPGAMSHR_RUNTIME_LOG_NUM 10000
static uint64_t fpgamshr_runtime_log[MAX_FPGAMSHR_RUNTIME_LOG_NUM][NUM_REQ_HANDLERS][18+5];
static uint64_t fpgamshr_runtime_log2[MAX_FPGAMSHR_RUNTIME_LOG_NUM][1 + NUM_MEMPORT * 3];
static int fpgamshr_runtime_log_idx = 0;

void FPGAMSHR_Get_runtime_log() {
	if (fpgamshr_runtime_log_idx >= MAX_FPGAMSHR_RUNTIME_LOG_NUM)
		return;

	FPGAMSHR_Profiling_snapshot();

	for (int i = 0; i < NUM_REQ_HANDLERS; i++) {
		uint64_t handler_offset = i * REGS_PER_REQ_HANDLER;
		if (qdma_read(_fpgamshr_base + handler_offset * sizeof(uint64_t),
			fpgamshr_runtime_log[fpgamshr_runtime_log_idx][i], sizeof(fpgamshr_runtime_log[fpgamshr_runtime_log_idx][i])) < 0) {
			perror("FPGAMSHR read MSHR statistic");
		}
	}
	if (qdma_read(_fpgamshr_base + (NUM_INPUTS + NUM_REQ_HANDLERS) * REGS_PER_REQ_HANDLER * sizeof(uint64_t),
		fpgamshr_runtime_log2[fpgamshr_runtime_log_idx], sizeof(fpgamshr_runtime_log2[fpgamshr_runtime_log_idx])) < 0) {
		perror("FPGAMSHR read misc statistic");
	}
	fpgamshr_runtime_log_idx++;
}

void FPGAMSHR_Output_runtime_log(const char *benchname) {
	time_t now;
	time(&now);
	struct tm *t = localtime(&now);
	char filename[256];
	snprintf(filename, sizeof(filename), "runtime_%s_%02d%02d%02d%02d%02d.csv", benchname, t->tm_mon + 1, t->tm_mday, t->tm_hour, t->tm_min, t->tm_sec);
	FILE *flog = fopen(filename, "w");

	printf("Get %d log(s)\n", fpgamshr_runtime_log_idx);

	const char *items_mshr[] = {
		"currently used MSHR",
		"max used MSHR",
		"max used subentry",
	#if MSHR_HASH_TABLES > 0
		"collison trigger count",
		"cycles spent handling collisons",
	#else // MSHR_HASH_TABLES > 0
		"cycles MSHR full",
		"cycles LdBuf full",
	#endif // MSHR_HASH_TABLES > 0
		"stall trigger count",
		"cycles spent stalling",
		"accepted allocs count",
		"accepted deallocs count",
		"cycles allocs stall",
		"cycles deallocs stall",
		"enqueued mem reqs count",
		"cache hit count",
		"subentry full count",
		"accum used MSHR",
		"cycles subentry full stall",
		"deallocs retry count",
		"ctrlSignal",
		">=5",
		">=10",
		">=15",
		">=20",
		">=25"
	};

	fprintf(flog, "cycles");
	for (int i = 0; i < sizeof(fpgamshr_runtime_log[0][0])/sizeof(fpgamshr_runtime_log[0][0][0]); i++) {
		fprintf(flog, ",%s", items_mshr[i]);
	}

	for (int i = 0; i < fpgamshr_runtime_log_idx; i++) {
		fprintf(flog, "\n%lu", fpgamshr_runtime_log2[i][0]);
		for (int j = 0; j < sizeof(fpgamshr_runtime_log[0][0])/sizeof(fpgamshr_runtime_log[0][0][0]); j++) {
			fprintf(flog, ",%lu", fpgamshr_runtime_log[i][0][j]);
		}
	}
	fprintf(flog, "\n");
	fclose(flog);
	fpgamshr_runtime_log_idx = 0;
}

uint64_t FPGAMSHR_Get_extMemCyclesNotReady() {
	return FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + NUM_INPUTS) * REGS_PER_REQ_HANDLER + 1);
}

uint64_t FPGAMSHR_Get_totalCycles() {
	return FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + NUM_INPUTS) * REGS_PER_REQ_HANDLER);
}

void print_profiling_reg(uint32_t regOffset) {
	double mean = 0;
	for (int i = 0; i < NUM_REQ_HANDLERS; i++) {
		uint64_t val = FPGAMSHR_Read_reg(regOffset + i * REGS_PER_REQ_HANDLER);
		double delta = val - mean;
		mean += delta / (i + 1);
	}
	printf("%-8lu", (uint64_t)mean);
}

void print_rob_profiling_reg(uint32_t regOffset) {
	double mean = 0;
	for (int i = 0; i < NUM_INPUTS; i++) {
		uint64_t val =FPGAMSHR_Read_reg(regOffset + (NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER);
		double delta = val - mean;
		mean += delta / (i + 1);
	}
	printf("%-8lu", (uint64_t)mean);
}
