# An in-cache MSHR (or called MSHR-inclusive cache) design (Chisel version)

This is based on the MSHR-rich MOMS from [FPGA'19: Stop Crying Over Your Cache Miss Rate](https://github.com/m-asiatici/MSHR-rich). We simply merge the cache buffer and MSHR buffer to share the storage resource for the MSHR-rich design. The effect remains unknown so far, but intuitively some resource savings are foreseeable.
The current implement only verifies the functionality of the in-cache MSHR storage and doesn't consider any optimization (such as the timing problem).

## Usage
To build the vivado IP, run:
```bash
make ip cfg=CONFIG_FILE
```
The example configuration files are in `cfg/`. Read the FPGA'19 paper for configuration details. The output IPs are in `output/ip/`.

To generate verilog file, run:
```bash
make verilog cfg=CONFIG_FILE
```

See the original [README](./README_origin.md) for other detail information.