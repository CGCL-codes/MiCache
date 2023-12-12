# MiCache: An MSHR-inclusive non-blocking cache design for FPGAs

MiCache is developed by revising the Chisel codes of [MSHR-rich](https://github.com/m-asiatici/MSHR-rich). We replace the original `Cache`, `MSHR` and `SubentryBuffer` modules with our proposed MSHR-inclusive architecture (the major codes are in [InCacheMSHR.scala](/src/main/scala/reqhandler/cuckoo/InCacheMSHR.scala)). Based on the key idea of building a large number of MSHRs, we implements MSHRs and cache lines in shared storage spaces, and allow the entries to switch between these two forms. By doing so, MiCache is able to support the dynamic requirements of MSHRs during the executions of applications.

However, there are still a few unknown bugs in our codes which may result in failures in some of the tests on FPGAs. We are trying our best to locate the bugs.

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