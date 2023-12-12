#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>
#include <limits.h>
#include "def.h"

#define MEMORY_SPAN					(1 << ADDR_BITS)

#define NUM_INPUTS					4
#define NUM_MEMPORT					1
#define ROB_DEPTH					0
#define MSHR_HASH_TABLES			4
#define MSHR_PER_HASH_TABLE			(1024 * 1)
#define SE_BUF_ENTRIES_PER_ROW		3
#define SE_BUF_ROWS					2048
#define CACHE_WAYS					4
#define CACHE_SIZE					(262144 * 1)
#define CACHE_SIZE_REDUCTION_WIDTH	3
#define CACHE_SIZE_REDUCTION_VALUES	(1 << CACHE_SIZE_REDUCTION_WIDTH)
#define CACHELINE_SIZE				64
#if FPGAMSHR_EXISTS
// #define NUM_REQ_HANDLERS			64
#define NUM_REQ_HANDLERS			4
#else
#define NUM_REQ_HANDLERS			0
#endif
#define REGS_PER_REQ_HANDLER		512
#define REGS_PER_REQ_HANDLER_MODULE	128

#define CACHE_RECV_REQS_OFFSET						(0)
#define CACHE_HITS_OFFSET							(1)
#define CACHE_CYCLES_OUT_MISSES_STALL_OFFSET		(2)
#define CACHE_CYCLES_OUT_DATA_STALL_OFFSET			(3)
#define MSHR_CURRENTLY_USED_OFFSET					(REGS_PER_REQ_HANDLER_MODULE)
#define MSHR_MAX_USED_OFFSET						(REGS_PER_REQ_HANDLER_MODULE + 1)
#define MSHR_COLLISION_TRIGGER_COUNT_OFFSET			(REGS_PER_REQ_HANDLER_MODULE + 2)
#define TRAD_MSHR_CYCLES_MSHR_FULL					(REGS_PER_REQ_HANDLER_MODULE + 2)
#define MSHR_CYCLES_IN_COLLISION_OFFSET				(REGS_PER_REQ_HANDLER_MODULE + 3)
#define TRAD_MSHR_CYCLES_SE_BUF_FULL				(REGS_PER_REQ_HANDLER_MODULE + 3)
#define MSHR_STALL_TRIGGER_COUNT_OFFSET				(REGS_PER_REQ_HANDLER_MODULE + 4)
#define MSHR_CYCLES_IN_STALL_OFFSET					(REGS_PER_REQ_HANDLER_MODULE + 5)
#define MSHR_ACCEPTED_ALLOCS_OFFSET					(REGS_PER_REQ_HANDLER_MODULE + 6)
#define MSHR_ACCEPTED_DEALLOCS_OFFSET				(REGS_PER_REQ_HANDLER_MODULE + 7)
#define MSHR_CYCLES_ALLOCS_STALLED_OFFSET			(REGS_PER_REQ_HANDLER_MODULE + 8)
#define MSHR_CYCLES_DEALLOCS_STALLED_OFFSET			(REGS_PER_REQ_HANDLER_MODULE + 9)
#define MSHR_ENQUEUED_MEM_REQS_OFFSET				(REGS_PER_REQ_HANDLER_MODULE + 10)
#define MSHR_CYCLES_OUT_SE_BUF_NOT_READY_OFFSET		(REGS_PER_REQ_HANDLER_MODULE + 11)
#define MSHR_ACCUM_USED_MSHR_OFFSET					(REGS_PER_REQ_HANDLER_MODULE + 12)
#define SE_BUF_MAX_USED_OFFSET						(2 * REGS_PER_REQ_HANDLER_MODULE + 1)
#define SE_BUF_MAX_USED_ROWS_OFFSET					(2 * REGS_PER_REQ_HANDLER_MODULE + 3)
#define SE_BUF_MAX_ROWS_WITH_NEXT_PTR_OFFSET		(2 * REGS_PER_REQ_HANDLER_MODULE + 5)
#define SE_BUF_CYCLES_IN_FW_STALL_OFFSET			(2 * REGS_PER_REQ_HANDLER_MODULE + 6)
#define SE_BUF_CYCLES_RESP_GEN_STALL_OFFSET			(2 * REGS_PER_REQ_HANDLER_MODULE + 7)
#define SE_BUF_CYCLES_WRITE_PIPELINE_STALL_OFFSET	(2 * REGS_PER_REQ_HANDLER_MODULE + 8)
#define SE_BUF_CYCLES_VALID_NEXT_PTR_STALL_OFFSET	(2 * REGS_PER_REQ_HANDLER_MODULE + 9)
#define SE_BUF_ACCUM_USED_ENTRIES_OFFSET			(2 * REGS_PER_REQ_HANDLER_MODULE + 10)
#define SE_BUF_ACCUM_USED_ROWS_OFFSET				(2 * REGS_PER_REQ_HANDLER_MODULE + 11)
#define RESP_GEN_ACCEPTED_INPUTS_OFFSET				(3 * REGS_PER_REQ_HANDLER_MODULE)
#define RESP_GEN_RESP_SENT_OUT_OFFSET				(3 * REGS_PER_REQ_HANDLER_MODULE + 1)
#define RESP_GEN_CYCLES_OUT_NOT_READY_OFFSET		(3 * REGS_PER_REQ_HANDLER_MODULE + 2)
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

void FPGAMSHR_SetMshrDivider(uint64_t div) {
	// *(volatile uint32_t*)(fpgamshr_base + 1) = div;
	FPGAMSHR_Write_reg(16, div);
}

void FPGAMSHR_SetMaxMSHR(uint64_t mshr) {
	// *(volatile uint32_t*)(fpgamshr_base + 2) = mshr;
	FPGAMSHR_Write_reg(24, mshr);
}

void FPGAMSHR_SetMaxSubentryRow(uint64_t subRowNum) {
	FPGAMSHR_Write_reg(32, subRowNum);
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
	uint64_t stats_cache[NUM_REQ_HANDLERS][6];
	uint64_t stats_mshr[NUM_REQ_HANDLERS][14];
	uint64_t stats_subentry[NUM_REQ_HANDLERS][13];
	uint64_t stats_respgen[NUM_REQ_HANDLERS][3];

	for (i = 0; i < NUM_REQ_HANDLERS; i++) {
		uint64_t handler_offset = i * REGS_PER_REQ_HANDLER;
		if (qdma_read(_fpgamshr_base + handler_offset * sizeof(uint64_t),
			stats_cache[i], sizeof(stats_cache[i])) < 0) {
			perror("FPGAMSHR read cache statistic");
		}
		if (qdma_read(_fpgamshr_base + (handler_offset + REGS_PER_REQ_HANDLER_MODULE) * sizeof(uint64_t),
			stats_mshr[i], sizeof(stats_mshr[i])) < 0) {
			perror("FPGAMSHR read mshr statistic");
		}
		if (qdma_read(_fpgamshr_base + (handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE) * sizeof(uint64_t),
			stats_subentry[i], sizeof(stats_subentry[i])) < 0) {
			perror("FPGAMSHR read subentry statistic");
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
	for (i = 0; i < NUM_REQ_HANDLERS; i++) {
		fprintf(flog, ",%d", i);
	}
	fprintf(flog, "\n\nCache");
	const char *items_cache[] = {
		"received requests",
		"hits",
		"cycles out misses stall",
		"cycles out data stall",
		"cycles hits on out misses stall",
		"cycles pipeline stalls",
	};
	for (i = 0; i < sizeof(stats_cache[0])/sizeof(stats_cache[0][0]); i++) {
		fprintf(flog, "\n%s", items_cache[i]);
		for (int j = 0; j < NUM_REQ_HANDLERS; j++) {
			fprintf(flog, ",%lu", stats_cache[j][i]);
		}
		if (i == 1) {
			fprintf(flog, "\nhit rate");
			for (int j = 0; j < NUM_REQ_HANDLERS; j++) {
				fprintf(flog, ",%lf", (double)stats_cache[j][1]/stats_cache[j][0]);
			}
		}
	}

#if MSHR_PER_HASH_TABLE > 0
	fprintf(flog, "\n\nMSHR");
	const char *items_mshr[] = {
		"currently used MSHR",
		"max used MSHR",
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
		"cycles out LdBuf not ready",
		"accum used MSHR",
		"cycles allocs stall LdBuf"
	};
	for (i = 0; i < sizeof(stats_mshr[0])/sizeof(stats_mshr[0][0]); i++) {
		fprintf(flog, "\n%s", items_mshr[i]);
		for (int j = 0; j < NUM_REQ_HANDLERS; j++) {
			fprintf(flog, ",%lu", stats_mshr[j][i]);
		}
	}

	fprintf(flog, "\n\nSubentry buffer");
	const char *items_subentry[] = {
		"currently used entries",
		"max used entries",
		"currently used rows",
		"max used rows",
		"currently rows with NextRowPtr valid",
		"max rows with NextRowPtr valid",
		"cycles RespGen stall",
		"cycles write pipeline stall",
		"cycles valid NextPtr input stall",
		"NextPtr cache hits",
		"accum used entries",
		"accum used rows",
		"cycles FRQ stop alloc"
	};
	for (i = 0; i < sizeof(stats_subentry[0])/sizeof(stats_subentry[0][0]); i++) {
		fprintf(flog, "\n%s", items_subentry[i]);
		for (int j = 0; j < NUM_REQ_HANDLERS; j++) {
			fprintf(flog, ",%lu", stats_subentry[j][i]);
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
#endif // FPGAMSHR_EXISTS

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
static uint64_t fpgamshr_runtime_log_cache[MAX_FPGAMSHR_RUNTIME_LOG_NUM][NUM_REQ_HANDLERS][4];
static uint64_t fpgamshr_runtime_log_mshr[MAX_FPGAMSHR_RUNTIME_LOG_NUM][NUM_REQ_HANDLERS][14];
static uint64_t fpgamshr_runtime_log_misc[MAX_FPGAMSHR_RUNTIME_LOG_NUM][1 + NUM_MEMPORT * 3];
static int fpgamshr_runtime_log_idx = 0;

void FPGAMSHR_Get_runtime_log() {
	if (fpgamshr_runtime_log_idx >= MAX_FPGAMSHR_RUNTIME_LOG_NUM)
		return;

	FPGAMSHR_Profiling_snapshot();

	for (int i = 0; i < NUM_REQ_HANDLERS; i++) {
	// for (int i = 0; i < 1; i++) {
		uint64_t handler_offset = i * REGS_PER_REQ_HANDLER;
		if (qdma_read(_fpgamshr_base + handler_offset * sizeof(uint64_t),
			fpgamshr_runtime_log_cache[fpgamshr_runtime_log_idx][i], sizeof(fpgamshr_runtime_log_cache[fpgamshr_runtime_log_idx][i])) < 0) {
			perror("FPGAMSHR read cache statistic");
		}
		if (qdma_read(_fpgamshr_base + (handler_offset + REGS_PER_REQ_HANDLER_MODULE) * sizeof(uint64_t),
			fpgamshr_runtime_log_mshr[fpgamshr_runtime_log_idx][i], sizeof(fpgamshr_runtime_log_mshr[fpgamshr_runtime_log_idx][i])) < 0) {
			perror("FPGAMSHR read MSHR statistic");
		}
	}
	if (qdma_read(_fpgamshr_base + (NUM_INPUTS + NUM_REQ_HANDLERS) * REGS_PER_REQ_HANDLER * sizeof(uint64_t),
		fpgamshr_runtime_log_misc[fpgamshr_runtime_log_idx], sizeof(fpgamshr_runtime_log_misc[fpgamshr_runtime_log_idx])) < 0) {
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

	const char *items_cache[] = {
		"received requests",
		"hits",
		// "cycles out misses stall",
		// "cycles out data stall",
	};
	const char *items_mshr[] = {
		"currently used MSHR",
		"max used MSHR",
		"collison trigger count",
		"cycles spent handling collisons",
		"stall trigger count",
		"cycles spent stalling",
		"accepted allocs count",
		"accepted deallocs count",
		"cycles allocs stall",
		"cycles deallocs stall",
		"enqueued mem reqs count",
		"cycles out LdBuf not ready",
		"accum used MSHR",
		// "cycles allocs stall LdBuf"
	};

	fprintf(flog, "cycles");
	for (int i = 0; i < sizeof(items_cache)/sizeof(items_cache[0]); i++) {
		fprintf(flog, ",%s", items_cache[i]);
	}
	fprintf(flog, ",");
	for (int i = 0; i < sizeof(items_mshr)/sizeof(items_mshr[0]); i++) {
		fprintf(flog, ",%s", items_mshr[i]);
	}

	for (int i = 0; i < fpgamshr_runtime_log_idx; i++) {
		fprintf(flog, "\n%lu", fpgamshr_runtime_log_misc[i][0]);
		for (int j = 0; j < sizeof(items_cache)/sizeof(items_cache[0]); j++) {
			uint64_t sum = 0;
			for (int k = 0; k < NUM_REQ_HANDLERS; k++) {
				sum += fpgamshr_runtime_log_cache[i][k][j];
			}
			// fprintf(flog, ",%lu", fpgamshr_runtime_log_cache[i][0][j]);
			fprintf(flog, ",%lu", sum);
		}
		fprintf(flog, ",");
		for (int j = 0; j < sizeof(items_mshr)/sizeof(items_mshr[0]); j++) {
			uint64_t sum = 0;
			for (int k = 0; k < NUM_REQ_HANDLERS; k++) {
				sum += fpgamshr_runtime_log_mshr[i][k][j];
			}
			// fprintf(flog, ",%lu", fpgamshr_runtime_log_mshr[i][0][j]);
			fprintf(flog, ",%lu", sum);
		}
	}
	fprintf(flog, "\n");
	fclose(flog);
	fpgamshr_runtime_log_idx = 0;
}

/*
void FPGAMSHR_Get_stats_pretty() {
	// get snapshot
	FPGAMSHR_Profiling_snapshot();
	int i;
	for (i = 0; i < NUM_REQ_HANDLERS; i++) {
		printf("Bank %d\n\r", i);
		uint64_t handler_offset = i * REGS_PER_REQ_HANDLER;

		// uint64_t recv_reqs = FPGAMSHR_Read_reg(handler_offset + CACHE_RECV_REQS_OFFSET);
		// uint64_t hits = FPGAMSHR_Read_reg(handler_offset + CACHE_HITS_OFFSET);
		uint64_t cache_statistic[4];
		if (qdma_read(_fpgamshr_base + handler_offset * sizeof(uint64_t), cache_statistic, sizeof(cache_statistic)) < 0) {
			perror("FPGAMSHR read cache statistic");
		}
		uint64_t recv_reqs = cache_statistic[0];
		uint64_t hits = cache_statistic[1];
		printf("Cache: received requests: %lu\n\r", recv_reqs);
		printf("Cache: hits: %lu (hit rate=%f)\n\r", hits, (float) hits / recv_reqs);
		// printf("Cache: cyclesOutMissesStall: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + CACHE_CYCLES_OUT_MISSES_STALL_OFFSET));
		// printf("Cache: cyclesOutDataStall: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + CACHE_CYCLES_OUT_DATA_STALL_OFFSET));
		printf("Cache: cyclesOutMissesStall: %lu\n\r", cache_statistic[2]);
		printf("Cache: cyclesOutDataStall: %lu\n\r", cache_statistic[3]);

		uint64_t mshr_statistic[13];
		if (qdma_read(_fpgamshr_base + (handler_offset + REGS_PER_REQ_HANDLER_MODULE) * sizeof(uint64_t), mshr_statistic, sizeof(mshr_statistic)) < 0) {
			perror("FPGAMSHR read mshr statistic");
		}
	#if MSHR_PER_HASH_TABLE > 0
		printf("MSHR: currentlyUsedMSHR: %lu\n\r", mshr_statistic[0]);
		printf("MSHR: maxUsedMSHR: %lu\n\r", mshr_statistic[1]);
		// printf("MSHR: currentlyUsedMSHR: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_CURRENTLY_USED_OFFSET));
		// printf("MSHR: maxUsedMSHR: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_MAX_USED_OFFSET));
	#if MSHR_HASH_TABLES > 0
		printf("MSHR: collisonTriggerCount: %lu\n\r", mshr_statistic[2]);
		printf("MSHR: cyclesSpentHandlingCollisons: %lu\n\r", mshr_statistic[3]);
		// printf("MSHR: collisonTriggerCount: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_COLLISION_TRIGGER_COUNT_OFFSET));
		// printf("MSHR: cyclesSpentHandlingCollisons: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_CYCLES_IN_COLLISION_OFFSET));
	#else // MSHR_HASH_TABLES > 0
		printf("MSHR: cyclesMSHRFull: %lu\n\r", mshr_statistic[2]);
		printf("MSHR: cyclesLdBufFull: %lu\n\r", mshr_statistic[3]);
		// printf("MSHR: cyclesMSHRFull: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + TRAD_MSHR_CYCLES_MSHR_FULL));
		// printf("MSHR: cyclesLdBufFull: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + TRAD_MSHR_CYCLES_SE_BUF_FULL));
	#endif // MSHR_HASH_TABLES > 0
		printf("MSHR: stallTriggerCount: %lu\n\r", mshr_statistic[4]);
		printf("MSHR: cyclesSpentStalling: %lu\n\r", mshr_statistic[5]);
		printf("MSHR: acceptedAllocsCount: %lu\n\r", mshr_statistic[6]);
		printf("MSHR: acceptedDeallocsCount: %lu\n\r", mshr_statistic[7]);
		printf("MSHR: cyclesAllocsStalled: %lu\n\r", mshr_statistic[8]);
		printf("MSHR: cyclesDeallocsStalled: %lu\n\r", mshr_statistic[9]);
		printf("MSHR: enqueuedMemReqsCount: %lu\n\r", mshr_statistic[10]);
		printf("MSHR: cyclesOutLdBufNotReady: %lu\n\r", mshr_statistic[11]);
		printf("MSHR: accumUsedMSHR: %lu\n\r", mshr_statistic[12]);
		// printf("MSHR: stallTriggerCount: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_STALL_TRIGGER_COUNT_OFFSET));
		// printf("MSHR: cyclesSpentStalling: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_CYCLES_IN_STALL_OFFSET));
		// printf("MSHR: acceptedAllocsCount: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_ACCEPTED_ALLOCS_OFFSET));
		// printf("MSHR: acceptedDeallocsCount: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_ACCEPTED_DEALLOCS_OFFSET));
		// printf("MSHR: cyclesAllocsStalled: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_CYCLES_ALLOCS_STALLED_OFFSET));
		// printf("MSHR: cyclesDeallocsStalled: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_CYCLES_DEALLOCS_STALLED_OFFSET));
		// printf("MSHR: enqueuedMemReqsCount: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_ENQUEUED_MEM_REQS_OFFSET));
		// printf("MSHR: cyclesOutLdBufNotReady: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_CYCLES_OUT_SE_BUF_NOT_READY_OFFSET));
		// printf("MSHR: accumUsedMSHR: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + MSHR_ACCUM_USED_MSHR_OFFSET));
		uint64_t subentry_statistic[13];
		if (qdma_read(_fpgamshr_base + (handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE) * sizeof(uint64_t), subentry_statistic, sizeof(subentry_statistic)) < 0) {
			perror("FPGAMSHR read subentry statistic");
		}
		printf("Subentry buffer: snapshotUsedEntries: %lu\n\r", subentry_statistic[0]);
		printf("Subentry buffer: maxUsedEntries: %lu\n\r", subentry_statistic[1]);
		printf("Subentry buffer: currentlyUsedRows: %lu\n\r", subentry_statistic[2]);
		printf("Subentry buffer: maxUsedRows: %lu\n\r", subentry_statistic[3]);
		printf("Subentry buffer: snapshotRowsWithNextRowPtrValid: %lu\n\r", subentry_statistic[4]);
		printf("Subentry buffer: maxRowsWithNextRowPtrValid: %lu\n\r", subentry_statistic[5]);
		printf("Subentry buffer: cyclesRespGenStall: %lu\n\r", subentry_statistic[6]);
		printf("Subentry buffer: cyclesWritePipelineStall: %lu\n\r", subentry_statistic[7]);
		printf("Subentry buffer: cyclesValidNextPtrInputStall: %lu\n\r", subentry_statistic[8]);
		printf("Subentry buffer: nextPtrCacheHits: %lu\n\r", subentry_statistic[9]);
		printf("Subentry buffer: accumUsedEntries: %lu\n\r", subentry_statistic[10]);
		printf("Subentry buffer: accumUsedRows: %lu\n\r", subentry_statistic[11]);
		printf("Subentry buffer: cyclesFrqStopAlloc: %lu\n\r", subentry_statistic[12]);
		uint64_t respgen_statistic[3];
		if (qdma_read(_fpgamshr_base + (handler_offset + RESP_GEN_ACCEPTED_INPUTS_OFFSET) * sizeof(uint64_t), respgen_statistic, sizeof(respgen_statistic)) < 0) {
			perror("FPGAMSHR read respgen statistic");
		}
		printf("RespGen: acceptedInputsCount: %lu\n\r", respgen_statistic[0]);
		printf("RespGen: responsesSentOutCount: %lu\n\r", respgen_statistic[1]);
		printf("RespGen: cyclesOutNotReady: %lu\n\r", respgen_statistic[2]);
		// printf("Subentry buffer: snapshotUsedEntries: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE));
		// printf("Subentry buffer: maxUsedEntries: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 1));
		// printf("Subentry buffer: currentlyUsedRows: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 2));
		// printf("Subentry buffer: maxUsedRows: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 3));
		// printf("Subentry buffer: snapshotRowsWithNextRowPtrValid: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 4));
		// printf("Subentry buffer: maxRowsWithNextRowPtrValid: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 5));
		// printf("Subentry buffer: cyclesRespGenStall: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 6));
		// printf("Subentry buffer: cyclesWritePipelineStall: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 7));
		// printf("Subentry buffer: cyclesValidNextPtrInputStall: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 8));
		// printf("Subentry buffer: nextPtrCacheHits: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 9));
		// printf("Subentry buffer: accumUsedEntries: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 10));
		// printf("Subentry buffer: accumUsedRows: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + 2 * REGS_PER_REQ_HANDLER_MODULE + 11));
		// printf("RespGen: acceptedInputsCount: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + RESP_GEN_ACCEPTED_INPUTS_OFFSET));
		// printf("RespGen: responsesSentOutCount: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + RESP_GEN_RESP_SENT_OUT_OFFSET));
		// printf("RespGen: cyclesOutNotReady: %lu\n\r", FPGAMSHR_Read_reg(handler_offset + RESP_GEN_CYCLES_OUT_NOT_READY_OFFSET));
	#endif // MSHR_PER_HASH_TABLE > 0
	}
	for (i = 0; i < NUM_INPUTS; i++) {
		uint64_t input_statistic[9];
		if (qdma_read(_fpgamshr_base + (NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER * sizeof(uint64_t), input_statistic, sizeof(input_statistic)) < 0) {
			perror("FPGAMSHR read input statistic");
		}
		printf("Input %d\n\r", i);
		printf("ROB: receivedRequests: %lu\n\r", input_statistic[0]);
		printf("ROB: receivedResponses: %lu\n\r", input_statistic[1]);
		printf("ROB: currentlyUsedEntries: %lu\n\r", input_statistic[2]);
		printf("ROB: maxUsedEntries: %lu\n\r", input_statistic[3]);
		printf("ROB: sentResponses: %lu\n\r", input_statistic[4]);
		printf("ROB: cyclesFullStalled: %lu\n\r", input_statistic[5]);
		printf("ROB: cyclesReqsInStalled: %lu\n\r", input_statistic[6]);
		printf("ROB: cyclesReqsOutStalled: %lu\n\r", input_statistic[7]);
		printf("ROB: cyclesRespOutStalled: %lu\n\r", input_statistic[8]);
		// printf("ROB: receivedRequests: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER));
		// printf("ROB: receivedResponses: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 1));
		// printf("ROB: currentlyUsedEntries: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 2));
		// printf("ROB: maxUsedEntries: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 3));
		// printf("ROB: sentResponses: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 4));
		// printf("ROB: cyclesFullStalled: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 5));
		// printf("ROB: cyclesReqsInStalled: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 6));
		// printf("ROB: cyclesReqsOutStalled: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 7));
		// printf("ROB: cyclesRespOutStalled: %lu\n\r", FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + i) * REGS_PER_REQ_HANDLER + 8));
	}
	uint64_t misc_statistic[2];
	if (qdma_read(_fpgamshr_base + (NUM_INPUTS + NUM_REQ_HANDLERS) * REGS_PER_REQ_HANDLER * sizeof(uint64_t), misc_statistic, sizeof(misc_statistic)) < 0) {
		perror("FPGAMSHR read misc statistic");
	}
	printf("Total cycles: %lu\n\r", misc_statistic[0]);
	printf("extMem not ready: %lu\n\r", misc_statistic[1]);
	// volatile uint64_t cycles = FPGAMSHR_Read_reg((NUM_INPUTS + NUM_REQ_HANDLERS) * REGS_PER_REQ_HANDLER);
	// printf("Total cycles: %lu\n\r", cycles);
	// printf("extMem not ready: %lu\n\r", FPGAMSHR_Read_reg((NUM_INPUTS + NUM_REQ_HANDLERS) * REGS_PER_REQ_HANDLER + 1));
}
*/

uint64_t FPGAMSHR_Get_extMemCyclesNotReady() {
	return FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + NUM_INPUTS) * REGS_PER_REQ_HANDLER + 1);
}

uint64_t FPGAMSHR_Get_totalCycles() {
	return FPGAMSHR_Read_reg((NUM_REQ_HANDLERS + NUM_INPUTS) * REGS_PER_REQ_HANDLER);
}

void FPGAMSHR_Get_stats_header() {
	printf("extMemNotReady ");
	printf("receivedRequests ");
	printf("hits ");
	printf("numMemRequests ");
	printf("cyclesOutMissesStall ");
#if MSHR_PER_HASH_TABLE > 0
	printf("maxUsedMSHR ");
#if MSHR_HASH_TABLES > 0
	printf("cyclesInCollision ");
	printf("cyclesInStall ");
#else // MSHR_HASH_TABLES > 0
	printf("cyclesMSHRFull ");
	printf("cyclesLdBufFull ");
#endif // MSHR_HASH_TABLES > 0
	printf("cyclesOutLdBufNotReady ");
	printf("maxUsedLdBufEntries ");
	printf("maxUsedLdBufRows ");
	printf("maxRowsWithNextRowPtrValid ");
	printf("cyclesRespGenStall ");
	printf("cyclesWritePipelineStall ");
	printf("cyclesValidNextPtrInputStall ");
	printf("nextPtrCacheHits ");
	printf("respGenCyclesOutNotReady ");
	printf("robMaxUsedEntries ");
	printf("robCyclesFullStalled ");
	printf("robReqOutStalled ");
	printf("robRespOutStalled ");
	printf("accumUsedMSHR ");
	printf("accumUsedSubentries ");
	printf("accumUsedRows\n\r");
#endif // MSHR_PER_HASH_TABLE > 0
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

void FPGAMSHR_Get_stats_row() {
	FPGAMSHR_Profiling_snapshot();
	printf("%lu ", FPGAMSHR_Get_extMemCyclesNotReady());
	print_profiling_reg(CACHE_RECV_REQS_OFFSET);
	print_profiling_reg(CACHE_HITS_OFFSET);
	print_profiling_reg(MSHR_ACCEPTED_DEALLOCS_OFFSET);
	print_profiling_reg(CACHE_CYCLES_OUT_MISSES_STALL_OFFSET);
#if MSHR_PER_HASH_TABLE > 0
	print_profiling_reg(MSHR_MAX_USED_OFFSET);
#if MSHR_HASH_TABLES > 0
	print_profiling_reg(MSHR_CYCLES_IN_COLLISION_OFFSET);
	print_profiling_reg(MSHR_CYCLES_IN_STALL_OFFSET);
#else // MSHR_HASH_TABLES > 0
	print_profiling_reg(TRAD_MSHR_CYCLES_MSHR_FULL);
	print_profiling_reg(TRAD_MSHR_CYCLES_SE_BUF_FULL);
#endif // MSHR_HASH_TABLES > 0
	print_profiling_reg(MSHR_CYCLES_OUT_SE_BUF_NOT_READY_OFFSET);
	print_profiling_reg(SE_BUF_MAX_USED_OFFSET);
	print_profiling_reg(SE_BUF_MAX_USED_ROWS_OFFSET);
	print_profiling_reg(SE_BUF_MAX_ROWS_WITH_NEXT_PTR_OFFSET);
	print_profiling_reg(SE_BUF_CYCLES_IN_FW_STALL_OFFSET);
	print_profiling_reg(SE_BUF_CYCLES_RESP_GEN_STALL_OFFSET);
	print_profiling_reg(SE_BUF_CYCLES_WRITE_PIPELINE_STALL_OFFSET);
	print_profiling_reg(SE_BUF_CYCLES_VALID_NEXT_PTR_STALL_OFFSET);
	print_profiling_reg(RESP_GEN_CYCLES_OUT_NOT_READY_OFFSET);
	print_rob_profiling_reg(ROB_MAX_USED_ENTRIES);
	print_rob_profiling_reg(ROB_CYCLES_FULL_STALLED);
	print_rob_profiling_reg(ROB_CYCLES_REQS_OUT_STALLED);
	print_rob_profiling_reg(ROB_CYCLES_RESP_OUT_STALLED);
	print_profiling_reg(MSHR_ACCUM_USED_MSHR_OFFSET);
	print_profiling_reg(SE_BUF_ACCUM_USED_ENTRIES_OFFSET);
	print_profiling_reg(SE_BUF_ACCUM_USED_ROWS_OFFSET);
#endif // MSHR_PER_HASH_TABLE > 0	
	printf("\n");
	// fflush(stdout);
}
