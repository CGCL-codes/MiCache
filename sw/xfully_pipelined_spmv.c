/*
 * xfully_pipelined_spmv.h
 *
 *  Created on: Jul 3, 2018
 *      Author: asiatici
 */

#ifndef SRC_XFULLY_PIPELINED_SPMV_H_
#define SRC_XFULLY_PIPELINED_SPMV_H_

#include "def.h"

#define SPLIT_INPUT_VECTORS

#define XSPMV_MULT_AXIS_AXILITES_ADDR_AP_CTRL          0x00
#define XSPMV_MULT_AXIS_AXILITES_ADDR_GIE              0x04
#define XSPMV_MULT_AXIS_AXILITES_ADDR_IER              0x08
#define XSPMV_MULT_AXIS_AXILITES_ADDR_ISR              0x0c
#define XSPMV_MULT_AXIS_AXILITES_BITS_VAL_SIZE_DATA    32
#define XSPMV_MULT_AXIS_AXILITES_BITS_OUTPUT_SIZE_DATA 32
#define XSPMV_MULT_AXIS_AXILITES_BITS_VECT_MEM_DATA    32
#ifdef SPLIT_INPUT_VECTORS
#define XSPMV_MULT_AXIS_AXILITES_ADDR_VAL_SIZE_DATA    0x4
#define XSPMV_MULT_AXIS_AXILITES_ADDR_OUTPUT_SIZE_DATA 0x8
#define XSPMV_MULT_AXIS_AXILITES_ADDR_VECT_MEM_DATA    0xC
#else
#define XSPMV_MULT_AXIS_AXILITES_ADDR_VAL_SIZE_DATA    0x10
#define XSPMV_MULT_AXIS_AXILITES_ADDR_OUTPUT_SIZE_DATA 0x18
#define XSPMV_MULT_AXIS_AXILITES_ADDR_VECT_MEM_DATA    0x20
#endif

static uint32_t XSpmv_mult_axis_ReadReg(uint64_t base, uint32_t offset) {
	// return *(volatile uint32_t *)(base + offset);
	uint32_t data;
	if (qdma_read(base + offset, &data, sizeof(data)) < 0) {
		perror("XSpmv_mult_axis_ReadReg");
		exit(-1);
	}
	return data;
}

static void XSpmv_mult_axis_WriteReg(uint64_t base, uint32_t offset, uint32_t data) {
	// printf("spmv write: addr=0x%lx, data=%u\n\r", base + offset, data);
	if (qdma_write(base + offset, &data, sizeof(data)) < 0) {
		perror("XSpmv_mult_axis_WriteReg");
		exit(-1);
	}
	// *(volatile uint32_t *)(base + offset) = data;
}

uint32_t XSpmv_mult_axis_IsIdle(uint64_t base) {
	uint32_t data[4];
	if (qdma_read(base, data, sizeof(data[0])) < 0) {
		perror("XSpmv_mult_axis_IsIdle");
		return -1;
	}
	// data = XSpmv_mult_axis_ReadReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_AP_CTRL);
#ifdef SPLIT_INPUT_VECTORS
	return data[0] & 0x1;
#else
	return (data >> 2) & 0x1;
#endif
}

void XSpmv_mult_axis_Set_val_size(uint64_t base, uint32_t data) {
	XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_VAL_SIZE_DATA, data);
	// printf("XSpmv_mult_axis_Set_val_size=%u against %u\n", XSpmv_mult_axis_ReadReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_VAL_SIZE_DATA), data);
}

void XSpmv_mult_axis_Set_output_size(uint64_t base, uint32_t data) {
	XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_OUTPUT_SIZE_DATA, data);
	// printf("XSpmv_mult_axis_Set_output_size=%u against %u\n", XSpmv_mult_axis_ReadReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_OUTPUT_SIZE_DATA), data);
}

void XSpmv_mult_axis_Set_vect_mem(uint64_t base, uint32_t data) {
	XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_VECT_MEM_DATA, data);
	// printf("XSpmv_mult_axis_Set_vect_mem=%u against %u\n", XSpmv_mult_axis_ReadReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_VECT_MEM_DATA), data);
}

int XSpmv_mult_axis_Set_args(uint64_t base, uint32_t val_size, uint32_t out_size, uint32_t vect_mem) {
	uint32_t data[4];
	data[0] = 0;
	data[1] = val_size;
	data[2] = out_size;
	data[3] = vect_mem;
	if (qdma_write(base, data, sizeof(data)) < 0) {
		perror("XSpmv_mult_axis_Set_args");
		return -1;
	}
	return 0;
}

int XSpmv_mult_axis_Get_args(uint64_t base) {
	uint32_t data[4] = {0};
	for (int i = 0; i < sizeof(data)/sizeof(data[0]); i++) {
		if (qdma_read(base + i * sizeof(data[i]), &data[i], sizeof(data[i])) < 0) {
			perror("XSpmv_mult_axis_Get_args");
			return -1;
		}
	}
	printf("\nstate=%d, val_size=%u, output_size=%u, vect_mem=0x%x\n", data[0], data[1], data[2], data[3]);
	return 0;
}

void XSpmv_mult_axis_Start(uint64_t base) {
	uint32_t data = 1;
	XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_AP_CTRL, data);
}

#endif /* SRC_XFULLY_PIPELINED_SPMV_H_ */
