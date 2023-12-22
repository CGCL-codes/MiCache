# MiCache: An MSHR-inclusive non-blocking cache design for FPGAs
This repository contains the full source code of MiCache, an MSHR-inclusive nonblocking cache architecture, where cache entries and MSHR entries share the same storage spaces to support the dynamic requirements of MSHRs during the executions of applications.  

We implement MiCache by revising the [code](https://github.com/m-asiatici/MSHR-rich) of [MSHR-rich Cache Design](https://dl.acm.org/doi/10.1145/3289602.3293901) \(published in FPGA 2019\).   
The major changes are on modules `Cache`, `MSHR`, and `SubentryBuffer` (in file [InCacheMSHR.scala](/src/main/scala/reqhandler/cuckoo/InCacheMSHR.scala)).

However, there is still a bug in the current implementation, and it only occurs on `ljournal-2008`: when MiCache is configured with 4 cache banks (256KB in total), there will be about 5% chance for the system to be stuck on Xilinx U280. We suspect it is related to the concurrency control logic of accessing the stash. We will try to fix this bug in the following week.

In instances where the bug does not occur, our system works properly, producing correct outputs identical to those computed by the CPU. Besides, the bug dose not affect the performance of the normal runs, and the performance data reported are obtained from these normal runs. 

## Requirements
The compiling environments are the same as in [MSHR-rich](https://github.com/m-asiatici/MSHR-rich).
+ Chisel 3
+ sbt
+ Python3
+ Xilinx Vivado

We tested our design with the environments listed below:
+ Xilinx Alevo U280 board
+ Ubuntu 18.04
+ Xilinx Vivado 2020.2
+ Xilinx QDMA driver 2020.2

## Usage
To build the vivado IP, run:
```bash
$ make ip cfg=CONFIG_FILE
```
To generate verilog file, run:
```bash
$ make verilog cfg=CONFIG_FILE
```
The configuration files of our evaluations in the paper are in `cfg/`. The output IPs are in `output/ip/`.

(TODO) To generate the example vivado project, run:
```bash
# TODO
```

Run the following commands to compile the host program `spmvtest` for evaluations on U280. 
We use the Xilinx QDMA IP and to transfer data and control signals between the host and the U280 FPGA through PCIe. This host program is not working on FPGAs like ZYNQ.
```bash
$ cd sw
$ make inclusive
# The QDMA driver must be loaded before executing the test.
$ sudo ./spmvtest [QDMA_DEVICE_PATH] [BENCHMARK_MATRIX_PATH]
```
The format of the matrices is the same as in [MSHR-rich](https://github.com/m-asiatici/MSHR-rich).
