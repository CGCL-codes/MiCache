# MiCache: An MSHR-inclusive non-blocking cache design for FPGAs
This repository contains the full source code of MiCache, an MSHR-inclusive nonblocking cache architecture, where cache entries and MSHR entries share the same storage spaces to support the dynamic requirements of MSHRs during the executions of applications.  

We implement MiCache by revising the [code](https://github.com/m-asiatici/MSHR-rich) of [MSHR-rich Cache Design](https://dl.acm.org/doi/10.1145/3289602.3293901) \(published in FPGA 2019\).   
The major changes are on modules `Cache`, `MSHR`, and `SubentryBuffer` (in file [InCacheMSHR.scala](/src/main/scala/reqhandler/cuckoo/InCacheMSHR.scala)).

However, there is still a bug in the current implementation:  
>When an MSHR in the stash is frequently operated, this bug may cause the subentry counter or the content of some of the subentries to have wrong value, which leads to the corresponding requests from the PE not being responded correctly. This further leads to the sliding window of the out-of-order memory accessor in the PE not being able to advance due to the unanswered requests, thus causing the system frozen.

When configured with 4 cache-MSHR banks with 64 KB capacity each, this bug occurs with approximately a 90% probability on ljournal-2008, and 50% on other tested matrices. When configured with 4 cache-MSHR banks with 128 KB capacity each, this bug occurs with approximately a 50% probability on ljournal-2008, and 15% on other tested matrices.  
On other configurations, this bug hardly occurs.

## Requirements
The compiling environments are the same as in [MSHR-rich](https://github.com/m-asiatici/MSHR-rich).
+ Chisel 3
+ sbt
+ Python3
+ Xilinx Vivado
+ Xilinx QDMA driver

We tested our design with the environments listed below:
+ Xilinx Alevo U280 board
+ Ubuntu 18.04
+ Vivado 2020.2
+ QDMA driver 2020.2

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

Run the following commands to compile the host program.
```bash
$ cd sw
$ make inclusive
# The QDMA driver must be loaded before executing the test.
$ sudo ./spmvtest [QDMA_DEVICE_PATH] [BENCHMARK_MATRIX_PATH]
```
The format of the matrices is the same as in [MSHR-rich](https://github.com/m-asiatici/MSHR-rich).
