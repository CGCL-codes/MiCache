;buildInfoPackage: chisel3, version: 3.1.8, scalaVersion: 2.11.12, sbtVersion: 1.1.1, builtAtString: 2019-07-08 17:44:42.884, builtAtMillis: 1562607882884
circuit AXILiteConfig : 
  module ElasticBufferRegExport : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, regs : {valid : UInt<1>, bits : UInt<4>}[2], readyReg : UInt<1>}
    
    reg outerRegData : UInt<4>, clock @[AXILiteCfg.scala 33:31]
    reg outerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 34:36]
    reg innerRegData : UInt<4>, clock @[AXILiteCfg.scala 35:31]
    reg innerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 36:36]
    reg readyReg : UInt<1>, clock @[AXILiteCfg.scala 37:27]
    node _T_39 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 39:24]
    when _T_39 : @[AXILiteCfg.scala 40:9]
      outerRegData <= io.in.bits @[AXILiteCfg.scala 41:30]
      innerRegData <= outerRegData @[AXILiteCfg.scala 42:30]
      outerRegValid <= io.in.valid @[AXILiteCfg.scala 43:31]
      node _T_40 = not(io.out.valid) @[AXILiteCfg.scala 44:67]
      node _T_41 = or(io.out.ready, _T_40) @[AXILiteCfg.scala 44:65]
      node _T_42 = not(_T_41) @[AXILiteCfg.scala 44:50]
      node _T_43 = and(outerRegValid, _T_42) @[AXILiteCfg.scala 44:48]
      innerRegValid <= _T_43 @[AXILiteCfg.scala 44:31]
      skip @[AXILiteCfg.scala 40:9]
    node _T_45 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 46:37]
    node _T_46 = mux(_T_45, outerRegData, innerRegData) @[AXILiteCfg.scala 46:27]
    io.out.bits <= _T_46 @[AXILiteCfg.scala 46:21]
    node _T_48 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 47:38]
    node _T_49 = mux(_T_48, outerRegValid, innerRegValid) @[AXILiteCfg.scala 47:28]
    io.out.valid <= _T_49 @[AXILiteCfg.scala 47:22]
    node _T_50 = not(io.out.valid) @[AXILiteCfg.scala 48:36]
    node _T_51 = or(io.out.ready, _T_50) @[AXILiteCfg.scala 48:34]
    readyReg <= _T_51 @[AXILiteCfg.scala 48:18]
    io.in.ready <= readyReg @[AXILiteCfg.scala 49:21]
    io.regs[0].bits <= outerRegData @[AXILiteCfg.scala 50:25]
    io.regs[0].valid <= outerRegValid @[AXILiteCfg.scala 51:26]
    io.regs[1].bits <= innerRegData @[AXILiteCfg.scala 52:25]
    io.regs[1].valid <= innerRegValid @[AXILiteCfg.scala 53:26]
    io.readyReg <= readyReg @[AXILiteCfg.scala 54:21]
    
  module ElasticBuffer : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}}
    
    inst fullBuffer of ElasticBufferRegExport @[AXILiteCfg.scala 20:32]
    fullBuffer.clock <= clock
    fullBuffer.reset <= reset
    fullBuffer.io.in.bits <= io.in.bits @[AXILiteCfg.scala 21:26]
    fullBuffer.io.in.valid <= io.in.valid @[AXILiteCfg.scala 21:26]
    io.in.ready <= fullBuffer.io.in.ready @[AXILiteCfg.scala 21:26]
    io.out.bits <= fullBuffer.io.out.bits @[AXILiteCfg.scala 22:16]
    io.out.valid <= fullBuffer.io.out.valid @[AXILiteCfg.scala 22:16]
    fullBuffer.io.out.ready <= io.out.ready @[AXILiteCfg.scala 22:16]
    
  module ElasticBufferRegExport_1 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, regs : {valid : UInt<1>, bits : UInt<4>}[2], readyReg : UInt<1>}
    
    reg outerRegData : UInt<4>, clock @[AXILiteCfg.scala 33:31]
    reg outerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 34:36]
    reg innerRegData : UInt<4>, clock @[AXILiteCfg.scala 35:31]
    reg innerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 36:36]
    reg readyReg : UInt<1>, clock @[AXILiteCfg.scala 37:27]
    node _T_39 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 39:24]
    when _T_39 : @[AXILiteCfg.scala 40:9]
      outerRegData <= io.in.bits @[AXILiteCfg.scala 41:30]
      innerRegData <= outerRegData @[AXILiteCfg.scala 42:30]
      outerRegValid <= io.in.valid @[AXILiteCfg.scala 43:31]
      node _T_40 = not(io.out.valid) @[AXILiteCfg.scala 44:67]
      node _T_41 = or(io.out.ready, _T_40) @[AXILiteCfg.scala 44:65]
      node _T_42 = not(_T_41) @[AXILiteCfg.scala 44:50]
      node _T_43 = and(outerRegValid, _T_42) @[AXILiteCfg.scala 44:48]
      innerRegValid <= _T_43 @[AXILiteCfg.scala 44:31]
      skip @[AXILiteCfg.scala 40:9]
    node _T_45 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 46:37]
    node _T_46 = mux(_T_45, outerRegData, innerRegData) @[AXILiteCfg.scala 46:27]
    io.out.bits <= _T_46 @[AXILiteCfg.scala 46:21]
    node _T_48 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 47:38]
    node _T_49 = mux(_T_48, outerRegValid, innerRegValid) @[AXILiteCfg.scala 47:28]
    io.out.valid <= _T_49 @[AXILiteCfg.scala 47:22]
    node _T_50 = not(io.out.valid) @[AXILiteCfg.scala 48:36]
    node _T_51 = or(io.out.ready, _T_50) @[AXILiteCfg.scala 48:34]
    readyReg <= _T_51 @[AXILiteCfg.scala 48:18]
    io.in.ready <= readyReg @[AXILiteCfg.scala 49:21]
    io.regs[0].bits <= outerRegData @[AXILiteCfg.scala 50:25]
    io.regs[0].valid <= outerRegValid @[AXILiteCfg.scala 51:26]
    io.regs[1].bits <= innerRegData @[AXILiteCfg.scala 52:25]
    io.regs[1].valid <= innerRegValid @[AXILiteCfg.scala 53:26]
    io.readyReg <= readyReg @[AXILiteCfg.scala 54:21]
    
  module ElasticBuffer_1 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}}
    
    inst fullBuffer of ElasticBufferRegExport_1 @[AXILiteCfg.scala 20:32]
    fullBuffer.clock <= clock
    fullBuffer.reset <= reset
    fullBuffer.io.in.bits <= io.in.bits @[AXILiteCfg.scala 21:26]
    fullBuffer.io.in.valid <= io.in.valid @[AXILiteCfg.scala 21:26]
    io.in.ready <= fullBuffer.io.in.ready @[AXILiteCfg.scala 21:26]
    io.out.bits <= fullBuffer.io.out.bits @[AXILiteCfg.scala 22:16]
    io.out.valid <= fullBuffer.io.out.valid @[AXILiteCfg.scala 22:16]
    fullBuffer.io.out.ready <= io.out.ready @[AXILiteCfg.scala 22:16]
    
  module ElasticBufferRegExport_2 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<32>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<32>}, regs : {valid : UInt<1>, bits : UInt<32>}[2], readyReg : UInt<1>}
    
    reg outerRegData : UInt<32>, clock @[AXILiteCfg.scala 33:31]
    reg outerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 34:36]
    reg innerRegData : UInt<32>, clock @[AXILiteCfg.scala 35:31]
    reg innerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 36:36]
    reg readyReg : UInt<1>, clock @[AXILiteCfg.scala 37:27]
    node _T_39 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 39:24]
    when _T_39 : @[AXILiteCfg.scala 40:9]
      outerRegData <= io.in.bits @[AXILiteCfg.scala 41:30]
      innerRegData <= outerRegData @[AXILiteCfg.scala 42:30]
      outerRegValid <= io.in.valid @[AXILiteCfg.scala 43:31]
      node _T_40 = not(io.out.valid) @[AXILiteCfg.scala 44:67]
      node _T_41 = or(io.out.ready, _T_40) @[AXILiteCfg.scala 44:65]
      node _T_42 = not(_T_41) @[AXILiteCfg.scala 44:50]
      node _T_43 = and(outerRegValid, _T_42) @[AXILiteCfg.scala 44:48]
      innerRegValid <= _T_43 @[AXILiteCfg.scala 44:31]
      skip @[AXILiteCfg.scala 40:9]
    node _T_45 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 46:37]
    node _T_46 = mux(_T_45, outerRegData, innerRegData) @[AXILiteCfg.scala 46:27]
    io.out.bits <= _T_46 @[AXILiteCfg.scala 46:21]
    node _T_48 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 47:38]
    node _T_49 = mux(_T_48, outerRegValid, innerRegValid) @[AXILiteCfg.scala 47:28]
    io.out.valid <= _T_49 @[AXILiteCfg.scala 47:22]
    node _T_50 = not(io.out.valid) @[AXILiteCfg.scala 48:36]
    node _T_51 = or(io.out.ready, _T_50) @[AXILiteCfg.scala 48:34]
    readyReg <= _T_51 @[AXILiteCfg.scala 48:18]
    io.in.ready <= readyReg @[AXILiteCfg.scala 49:21]
    io.regs[0].bits <= outerRegData @[AXILiteCfg.scala 50:25]
    io.regs[0].valid <= outerRegValid @[AXILiteCfg.scala 51:26]
    io.regs[1].bits <= innerRegData @[AXILiteCfg.scala 52:25]
    io.regs[1].valid <= innerRegValid @[AXILiteCfg.scala 53:26]
    io.readyReg <= readyReg @[AXILiteCfg.scala 54:21]
    
  module ElasticBuffer_2 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<32>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<32>}}
    
    inst fullBuffer of ElasticBufferRegExport_2 @[AXILiteCfg.scala 20:32]
    fullBuffer.clock <= clock
    fullBuffer.reset <= reset
    fullBuffer.io.in.bits <= io.in.bits @[AXILiteCfg.scala 21:26]
    fullBuffer.io.in.valid <= io.in.valid @[AXILiteCfg.scala 21:26]
    io.in.ready <= fullBuffer.io.in.ready @[AXILiteCfg.scala 21:26]
    io.out.bits <= fullBuffer.io.out.bits @[AXILiteCfg.scala 22:16]
    io.out.valid <= fullBuffer.io.out.valid @[AXILiteCfg.scala 22:16]
    fullBuffer.io.out.ready <= io.out.ready @[AXILiteCfg.scala 22:16]
    
  module ElasticBufferRegExport_3 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, regs : {valid : UInt<1>, bits : UInt<4>}[2], readyReg : UInt<1>}
    
    reg outerRegData : UInt<4>, clock @[AXILiteCfg.scala 33:31]
    reg outerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 34:36]
    reg innerRegData : UInt<4>, clock @[AXILiteCfg.scala 35:31]
    reg innerRegValid : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 36:36]
    reg readyReg : UInt<1>, clock @[AXILiteCfg.scala 37:27]
    node _T_39 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 39:24]
    when _T_39 : @[AXILiteCfg.scala 40:9]
      outerRegData <= io.in.bits @[AXILiteCfg.scala 41:30]
      innerRegData <= outerRegData @[AXILiteCfg.scala 42:30]
      outerRegValid <= io.in.valid @[AXILiteCfg.scala 43:31]
      node _T_40 = not(io.out.valid) @[AXILiteCfg.scala 44:67]
      node _T_41 = or(io.out.ready, _T_40) @[AXILiteCfg.scala 44:65]
      node _T_42 = not(_T_41) @[AXILiteCfg.scala 44:50]
      node _T_43 = and(outerRegValid, _T_42) @[AXILiteCfg.scala 44:48]
      innerRegValid <= _T_43 @[AXILiteCfg.scala 44:31]
      skip @[AXILiteCfg.scala 40:9]
    node _T_45 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 46:37]
    node _T_46 = mux(_T_45, outerRegData, innerRegData) @[AXILiteCfg.scala 46:27]
    io.out.bits <= _T_46 @[AXILiteCfg.scala 46:21]
    node _T_48 = eq(readyReg, UInt<1>("h01")) @[AXILiteCfg.scala 47:38]
    node _T_49 = mux(_T_48, outerRegValid, innerRegValid) @[AXILiteCfg.scala 47:28]
    io.out.valid <= _T_49 @[AXILiteCfg.scala 47:22]
    node _T_50 = not(io.out.valid) @[AXILiteCfg.scala 48:36]
    node _T_51 = or(io.out.ready, _T_50) @[AXILiteCfg.scala 48:34]
    readyReg <= _T_51 @[AXILiteCfg.scala 48:18]
    io.in.ready <= readyReg @[AXILiteCfg.scala 49:21]
    io.regs[0].bits <= outerRegData @[AXILiteCfg.scala 50:25]
    io.regs[0].valid <= outerRegValid @[AXILiteCfg.scala 51:26]
    io.regs[1].bits <= innerRegData @[AXILiteCfg.scala 52:25]
    io.regs[1].valid <= innerRegValid @[AXILiteCfg.scala 53:26]
    io.readyReg <= readyReg @[AXILiteCfg.scala 54:21]
    
  module ElasticBuffer_3 : 
    input clock : Clock
    input reset : UInt<1>
    output io : {flip in : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}, out : {flip ready : UInt<1>, valid : UInt<1>, bits : UInt<4>}}
    
    inst fullBuffer of ElasticBufferRegExport_3 @[AXILiteCfg.scala 20:32]
    fullBuffer.clock <= clock
    fullBuffer.reset <= reset
    fullBuffer.io.in.bits <= io.in.bits @[AXILiteCfg.scala 21:26]
    fullBuffer.io.in.valid <= io.in.valid @[AXILiteCfg.scala 21:26]
    io.in.ready <= fullBuffer.io.in.ready @[AXILiteCfg.scala 21:26]
    io.out.bits <= fullBuffer.io.out.bits @[AXILiteCfg.scala 22:16]
    io.out.valid <= fullBuffer.io.out.valid @[AXILiteCfg.scala 22:16]
    fullBuffer.io.out.ready <= io.out.ready @[AXILiteCfg.scala 22:16]
    
  module AXILiteConfig : 
    input clock : Clock
    input reset : UInt<1>
    output io : {axiLiteCfg : {flip ARADDR : UInt<6>, flip ARVALID : UInt<1>, ARREADY : UInt<1>, RDATA : UInt<32>, RRESP : UInt<2>, RVALID : UInt<1>, flip RREADY : UInt<1>, flip AWADDR : UInt<6>, flip AWVALID : UInt<1>, AWREADY : UInt<1>, flip WDATA : UInt<32>, flip WVALID : UInt<1>, WREADY : UInt<1>, flip WSTRB : UInt<4>, BRESP : UInt<2>, BVALID : UInt<1>, flip BREADY : UInt<1>}, traceNum : UInt<32>, flip cycleCnt : UInt<32>, start : UInt<1>, flip end : UInt<1>}
    
    reg start : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 73:28]
    io.start <= start @[AXILiteCfg.scala 74:18]
    reg traceNumReg : UInt<32>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 75:34]
    inst ElasticBuffer of ElasticBuffer @[AXILiteCfg.scala 83:30]
    ElasticBuffer.clock <= clock
    ElasticBuffer.reset <= reset
    node _T_50 = bits(io.axiLiteCfg.ARADDR, 5, 2) @[AXILiteCfg.scala 84:54]
    ElasticBuffer.io.in.bits <= _T_50 @[AXILiteCfg.scala 84:31]
    ElasticBuffer.io.in.valid <= io.axiLiteCfg.ARVALID @[AXILiteCfg.scala 85:31]
    io.axiLiteCfg.ARREADY <= ElasticBuffer.io.in.ready @[AXILiteCfg.scala 86:31]
    node _T_53 = eq(UInt<4>("h08"), ElasticBuffer.io.out.bits) @[Mux.scala 46:19]
    node _T_54 = mux(_T_53, io.cycleCnt, io.end) @[Mux.scala 46:16]
    node _T_55 = eq(UInt<3>("h04"), ElasticBuffer.io.out.bits) @[Mux.scala 46:19]
    node _T_56 = mux(_T_55, traceNumReg, _T_54) @[Mux.scala 46:16]
    io.axiLiteCfg.RDATA <= _T_56 @[AXILiteCfg.scala 88:30]
    io.axiLiteCfg.RVALID <= ElasticBuffer.io.out.valid @[AXILiteCfg.scala 89:30]
    ElasticBuffer.io.out.ready <= io.axiLiteCfg.RREADY @[AXILiteCfg.scala 90:30]
    io.axiLiteCfg.RRESP <= UInt<1>("h00") @[AXILiteCfg.scala 92:29]
    inst ElasticBuffer_1 of ElasticBuffer_1 @[AXILiteCfg.scala 95:30]
    ElasticBuffer_1.clock <= clock
    ElasticBuffer_1.reset <= reset
    inst ElasticBuffer_2 of ElasticBuffer_2 @[AXILiteCfg.scala 96:30]
    ElasticBuffer_2.clock <= clock
    ElasticBuffer_2.reset <= reset
    inst ElasticBuffer_3 of ElasticBuffer_3 @[AXILiteCfg.scala 97:30]
    ElasticBuffer_3.clock <= clock
    ElasticBuffer_3.reset <= reset
    node _T_61 = bits(io.axiLiteCfg.AWADDR, 5, 2) @[AXILiteCfg.scala 99:54]
    ElasticBuffer_1.io.in.bits <= _T_61 @[AXILiteCfg.scala 99:31]
    ElasticBuffer_1.io.in.valid <= io.axiLiteCfg.AWVALID @[AXILiteCfg.scala 100:31]
    io.axiLiteCfg.AWREADY <= ElasticBuffer_1.io.in.ready @[AXILiteCfg.scala 101:31]
    ElasticBuffer_2.io.in.bits <= io.axiLiteCfg.WDATA @[AXILiteCfg.scala 103:30]
    ElasticBuffer_2.io.in.valid <= io.axiLiteCfg.WVALID @[AXILiteCfg.scala 104:30]
    ElasticBuffer_3.io.in.bits <= io.axiLiteCfg.WSTRB @[AXILiteCfg.scala 105:30]
    ElasticBuffer_3.io.in.valid <= io.axiLiteCfg.WVALID @[AXILiteCfg.scala 106:30]
    node _T_62 = and(ElasticBuffer_2.io.in.ready, ElasticBuffer_3.io.in.ready) @[AXILiteCfg.scala 107:51]
    io.axiLiteCfg.WREADY <= _T_62 @[AXILiteCfg.scala 107:30]
    node _T_63 = not(io.axiLiteCfg.BVALID) @[AXILiteCfg.scala 109:52]
    node _T_64 = and(ElasticBuffer_2.io.out.valid, _T_63) @[AXILiteCfg.scala 109:50]
    ElasticBuffer_1.io.out.ready <= _T_64 @[AXILiteCfg.scala 109:28]
    node _T_65 = not(io.axiLiteCfg.BVALID) @[AXILiteCfg.scala 110:52]
    node _T_66 = and(ElasticBuffer_1.io.out.valid, _T_65) @[AXILiteCfg.scala 110:50]
    ElasticBuffer_2.io.out.ready <= _T_66 @[AXILiteCfg.scala 110:28]
    node _T_67 = not(io.axiLiteCfg.BVALID) @[AXILiteCfg.scala 111:52]
    node _T_68 = and(ElasticBuffer_1.io.out.valid, _T_67) @[AXILiteCfg.scala 111:50]
    ElasticBuffer_3.io.out.ready <= _T_68 @[AXILiteCfg.scala 111:28]
    node _T_69 = and(ElasticBuffer_1.io.out.valid, ElasticBuffer_2.io.out.valid) @[AXILiteCfg.scala 113:54]
    node _T_70 = and(_T_69, ElasticBuffer_3.io.out.valid) @[AXILiteCfg.scala 113:75]
    node _T_71 = not(io.axiLiteCfg.BVALID) @[AXILiteCfg.scala 113:98]
    node wrAddrDataAvailable = and(_T_70, _T_71) @[AXILiteCfg.scala 113:96]
    reg bvalidReg : UInt<1>, clock with : (reset => (reset, UInt<1>("h00"))) @[AXILiteCfg.scala 115:32]
    io.axiLiteCfg.BRESP <= UInt<1>("h00") @[AXILiteCfg.scala 116:30]
    io.axiLiteCfg.BVALID <= bvalidReg @[AXILiteCfg.scala 117:30]
    node _T_75 = not(ElasticBuffer_3.io.out.bits) @[AXILiteCfg.scala 119:56]
    node _T_77 = eq(_T_75, UInt<1>("h00")) @[AXILiteCfg.scala 119:56]
    node _T_78 = and(wrAddrDataAvailable, _T_77) @[AXILiteCfg.scala 119:35]
    when _T_78 : @[AXILiteCfg.scala 119:62]
      node _T_80 = eq(ElasticBuffer_1.io.out.bits, UInt<1>("h00")) @[AXILiteCfg.scala 120:41]
      when _T_80 : @[AXILiteCfg.scala 120:50]
        node _T_81 = bits(ElasticBuffer_2.io.out.bits, 0, 0) @[AXILiteCfg.scala 121:51]
        start <= _T_81 @[AXILiteCfg.scala 121:31]
        skip @[AXILiteCfg.scala 120:50]
      else : @[AXILiteCfg.scala 122:56]
        node _T_83 = eq(ElasticBuffer_1.io.out.bits, UInt<3>("h04")) @[AXILiteCfg.scala 122:47]
        when _T_83 : @[AXILiteCfg.scala 122:56]
          traceNumReg <= ElasticBuffer_2.io.out.bits @[AXILiteCfg.scala 123:37]
          skip @[AXILiteCfg.scala 122:56]
      skip @[AXILiteCfg.scala 119:62]
    when wrAddrDataAvailable : @[AXILiteCfg.scala 126:36]
      bvalidReg <= UInt<1>("h01") @[AXILiteCfg.scala 127:27]
      skip @[AXILiteCfg.scala 126:36]
    else : @[AXILiteCfg.scala 128:56]
      node _T_85 = and(bvalidReg, io.axiLiteCfg.BREADY) @[AXILiteCfg.scala 128:32]
      when _T_85 : @[AXILiteCfg.scala 128:56]
        bvalidReg <= UInt<1>("h00") @[AXILiteCfg.scala 129:27]
        skip @[AXILiteCfg.scala 128:56]
    io.traceNum <= traceNumReg @[AXILiteCfg.scala 132:21]
    
