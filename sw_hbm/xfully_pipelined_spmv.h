/*
 * xfully_pipelined_spmv.h
 *
 *  Created on: Jul 3, 2018
 *      Author: asiatici
 */

#ifndef SRC_XFULLY_PIPELINED_SPMV_H_
#define SRC_XFULLY_PIPELINED_SPMV_H_

#include <stdint.h>

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

static inline uint32_t XSpmv_mult_axis_ReadReg(uint64_t base, uint32_t offset) {
    return *(volatile uint32_t *)(base + offset);
}

static inline void XSpmv_mult_axis_WriteReg(uint64_t base, uint32_t offset, uint32_t data) {
    *(volatile uint32_t *)(base + offset) = data;
}

uint32_t XSpmv_mult_axis_IsIdle(uint64_t base) {
    uint32_t data;

    data = XSpmv_mult_axis_ReadReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_AP_CTRL);
#ifdef SPLIT_INPUT_VECTORS
    return data & 0x1;
#else
    return (data >> 2) & 0x1;
#endif
}

void XSpmv_mult_axis_Set_val_size(uint64_t base, uint32_t data) {
    XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_VAL_SIZE_DATA, data);
}

void XSpmv_mult_axis_Set_output_size(uint64_t base, uint32_t data) {
    XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_OUTPUT_SIZE_DATA, data);
}

void XSpmv_mult_axis_Set_vect_mem(uint64_t base, uint32_t data) {
    XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_VECT_MEM_DATA, data);
}

void XSpmv_mult_axis_Start(uint64_t base) {
    uint32_t data;
    data = XSpmv_mult_axis_ReadReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_AP_CTRL) & 0x80;
    XSpmv_mult_axis_WriteReg(base, XSPMV_MULT_AXIS_AXILITES_ADDR_AP_CTRL, data | 0x01);
}

#endif /* SRC_XFULLY_PIPELINED_SPMV_H_ */
