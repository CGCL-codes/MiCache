#ifndef DEF_H
#define DEF_H

#include <stdint.h>
#include <unistd.h>

#define ADDR_BITS		33
#define MEM_BASE_ADDR	0x00000000
#define DDR_BASE_ADDR	0x200000000UL
#define HBM_BASE_ADDR	0x00000000U
#define FPGAMSHR_EXISTS	1
#define NUM_SPMV		4

int qdmafd;

int qdma_read(uint64_t addr, void *data, size_t size) {
	// if (lseek(qdmafd, addr, SEEK_SET) < 0 ||
		// read(qdmafd, data, size) < 0)
	if (pread(qdmafd, data, size, addr) < 0)
	{
		// perror("qdma_read");
		return -1;
	}
	return 0;
}

int qdma_write(uint64_t addr, void *data, size_t size) {
	// if (lseek(qdmafd, addr, SEEK_SET) < 0 ||
	// 	write(qdmafd, data, size) < 0)
	if (pwrite(qdmafd, data, size, addr) < 0)
	{
		// perror("qdma_write");
		return -1;
	}
	return 0;
}

#endif
