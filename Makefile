cfg =

ip:
ifeq ($(cfg),)
	@echo "No config file input"
	@echo "Usage: make ip cfg=CONFIG_FILE"
else
	sbt "Test / runMain fpgamshr.main.FPGAMSHRIpBuilder $(cfg)"
endif

verilog:
ifeq ($(cfg),)
	@echo "No config file input"
	@echo "Usage: make verilog cfg=CONFIG_FILE"
else
	sbt "Test / runMain fpgamshr.main.FPGAMSHRVerilog $(cfg)"
endif

clean:
	rm -rf project target

.PHONY: ip verilog clean
