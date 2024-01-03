# MiCache: An MSHR-inclusive non-blocking cache design for FPGAs
This repository contains the full source code of MiCache, an MSHR-inclusive nonblocking cache architecture, where cache entries and MSHR entries share the same storage spaces to support the dynamic requirements of MSHRs during the executions of applications.  

We implement MiCache by revising the [code](https://github.com/m-asiatici/MSHR-rich) of [MSHR-rich Cache Design](https://dl.acm.org/doi/10.1145/3289602.3293901) \(published in FPGA 2019\).   
The major changes are on modules `Cache`, `MSHR`, and `SubentryBuffer` (in file [InCacheMSHR.scala](/src/main/scala/reqhandler/cuckoo/InCacheMSHR.scala)).

## Requirements
The compiling environments are the same as in [MSHR-rich](https://github.com/m-asiatici/MSHR-rich).
+ [Chisel 3](https://github.com/chipsalliance/chisel)
+ [sbt](https://www.scala-sbt.org/release/docs/Installing-sbt-on-Linux.html)
+ Python3
+ Xilinx Vivado

We tested our design with the environments listed below:
+ Xilinx Alevo U280 board
+ Ubuntu 18.04
+ Xilinx Vivado 2020.2
+ [Xilinx QDMA driver 2020.2](https://github.com/Xilinx/dma_ip_drivers/tree/2020.2)

## Usage
### Build Hardware
Make sure that `sbt` and `vivado` are installed and included in `PATH`.

To build the MiCache vivado IP, run:
```bash
$ make ip cfg=CONFIG_FILE
```
Or to generate verilog file, run:
```bash
$ make verilog cfg=CONFIG_FILE
```
After the MiCache IP is built, run the following commands to generate the example vivado project and the bitstream file (it might take a few hours):
```bash
$ make project cfg=CONFIG_FILE
$ cd output/vivado
$ vivado -source genprj.tcl
```

The configuration files of our evaluations are in `cfg/`. The output IPs are in `output/ip/`, and the output project is in `output/vivado/`.

### Xilinx QDMA
The Xilinx QDMA driver is required (the installation guide is [here](https://xilinx.github.io/dma_ip_drivers/master/QDMA/linux-kernel/html/build.html)), since we use the Xilinx QDMA IP to transfer data and control signals between the host and the U280 FPGA through PCIe. After the bitstream is programmed into the FPGA and the QDMA driver module is loaded, rescan the PCIe bus and configure the QDMA driver by running the followings:

```bash
# Root may be required.
# Assuming the PCIe device node is 0000:01:00.0, for example.

# Rescan the PCIe bus.
$ echo 1 > /sys/bus/pci/devices/0000:01:00.0/remove
$ echo 1 > /sys/bus/pci/rescan

# Configure the QDMA driver.
$ echo 256 > /sys/bus/pci/devices/0000:01:00.0/qdma/qmax
$ dma-ctl qdma01000 q add idx 0 mode mm dir bi
$ dma-ctl qdma01000 q start idx 0 dir bi
```
Then the QDMA device can be found in path `/dev/qdma01000-MM-0`.

When finishing the evaluations, clean the QDMA configurations:

```bash
# Stop the QDMA driver after all evaluations are done.
dma-ctl qdma01000 q stop idx 0 dir bi
dma-ctl qdma01000 q del idx 0 dir bi
```

### Input Matrix
The matrices are in MatrixMarket format and can be downloaded from [SuiteSparse](https://sparse.tamu.edu/). The `util/mm_matrix_to_csr.py` Python script converts a matrix in MatrixMarket format (`.mtx`) to the binary format for the evaluations. For example:
```bash
mkdir matrices
cd matrices
# Assuming a MatrixMarket file 'example-matrix.mtx' is downloaded into 'matrices/'
python3 ../util/mm_matrix_to_csr.py -a 1..4 -i -s -v example-matrix.mtx
```
The output matrix in binary format will be stored in folder `matrices/example-matrix`.

### Run Evaluations
Run the following commands to compile the host program `spmvtest` for evaluations on U280:
```bash
$ cd output/sw
$ make
# The QDMA driver must be loaded before executing the test.
# Usage: sudo ./spmvtest [QDMA_DEVICE_PATH] [MATRIX_FOLDER_PATH]
# For example:
$ sudo ./spmvtest /dev/qdma01000-MM-0 ../../matrices/example-matrix
```
The evaluation results are stored in the output `.csv` files.
