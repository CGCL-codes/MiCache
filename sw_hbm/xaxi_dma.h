#ifndef XAXI_DMA_H
#define XAXI_DMA_H

// Only support direct-mode and after-mmap.

#include <stdint.h>

// Registers offset.
#define MM2S_DMACR		0x00	// MM2S DMA Control register
#define MM2S_DMASR		0x04	// MM2S DMA Status register
#define MM2S_SA			0x18	// MM2S Source Address. Lower 32 bits of address.
#define MM2S_SA_MSB		0x1c	// MM2S Source Address. Upper 32 bits of address.
#define MM2S_LENGTH		0x28	// MM2S Transfer Length (Bytes)

#define S2MM_DMACR		0x30	// S2MM DMA Control registe
#define S2MM_DMASR		0x34	// S2MM DMA Status register
#define S2MM_DA			0x48	// S2MM Destination Address. Lower 32 bit address.
#define S2MM_DA_MSB		0x4c	// S2MM Destination Address. Upper 32 bit address.
#define S2MM_LENGTH		0x58	// S2MM Buffer Length (Bytes)

#define XAXIDMA_DMA_TO_DEVICE	0
#define XAXIDMA_DEVICE_TO_DMA	1

static inline uint32_t XAXI_DMA_ReadReg(uint64_t dma_base, uint32_t offset) {
	return *(volatile uint32_t *)(dma_base + offset);
}

static inline void XAXI_DMA_WriteReg(uint64_t dma_base, uint32_t offset, uint32_t data) {
	*(volatile uint32_t *)(dma_base + offset) = data;
}

uint32_t XAXI_DMA_Busy(uint64_t dma_base, int direction)
{
	uint32_t status;
	status = XAXI_DMA_ReadReg(dma_base, (direction == XAXIDMA_DMA_TO_DEVICE) ? MM2S_DMASR : S2MM_DMASR);
	return (status & 0x3) == 0;
}

int XAXI_DMA_SimpleTransfer(uint64_t dma_base, uint64_t addr, uint32_t length, int direction)
{
	// if (XAXI_DMA_Busy(dma_base, direction))
	// 	return -1;
	if (direction == XAXIDMA_DMA_TO_DEVICE) {
		uint32_t ctrl = XAXI_DMA_ReadReg(dma_base, MM2S_DMACR);
		XAXI_DMA_WriteReg(dma_base, MM2S_DMACR, ctrl | 0x1);
		XAXI_DMA_WriteReg(dma_base, MM2S_SA, addr & 0xffffffff);
		XAXI_DMA_WriteReg(dma_base, MM2S_SA_MSB, addr >> 32);
		XAXI_DMA_WriteReg(dma_base, MM2S_LENGTH, length);		// This field should be written after Control register start.
	} else {
		uint32_t ctrl = XAXI_DMA_ReadReg(dma_base, S2MM_DMACR);
		XAXI_DMA_WriteReg(dma_base, S2MM_DMACR, ctrl | 0x1);
		XAXI_DMA_WriteReg(dma_base, S2MM_DA, addr & 0xffffffff);
		XAXI_DMA_WriteReg(dma_base, S2MM_DA_MSB, addr >> 32);
		XAXI_DMA_WriteReg(dma_base, S2MM_LENGTH, length);
	}
	return 0;
}

void XAXI_DMA_Enable(uint64_t dma_base, int direction)
{
	if (direction == XAXIDMA_DMA_TO_DEVICE) {
		uint32_t ctrl = XAXI_DMA_ReadReg(dma_base, MM2S_DMACR);
		XAXI_DMA_WriteReg(dma_base, MM2S_DMACR, ctrl | 0x1);
	} else {
		uint32_t ctrl = XAXI_DMA_ReadReg(dma_base, S2MM_DMACR);
		XAXI_DMA_WriteReg(dma_base, S2MM_DMACR, ctrl | 0x1);
	}
}

void XAXI_DMA_SetAddrLength(uint64_t dma_base, uint64_t addr, uint32_t length, int direction)
{
	if (direction == XAXIDMA_DMA_TO_DEVICE) {
		XAXI_DMA_WriteReg(dma_base, MM2S_SA, addr & 0xffffffff);
		XAXI_DMA_WriteReg(dma_base, MM2S_SA_MSB, addr >> 32);
		XAXI_DMA_WriteReg(dma_base, MM2S_LENGTH, length);
	} else {
		XAXI_DMA_WriteReg(dma_base, S2MM_DA, addr & 0xffffffff);
		XAXI_DMA_WriteReg(dma_base, S2MM_DA_MSB, addr >> 32);
		XAXI_DMA_WriteReg(dma_base, S2MM_LENGTH, length);
	}
}

#endif
