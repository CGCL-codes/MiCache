module ElasticBufferRegExport( // @[:@3.2]
  input        clock, // @[:@4.4]
  input        reset, // @[:@5.4]
  output       io_in_ready, // @[:@6.4]
  input        io_in_valid, // @[:@6.4]
  input  [3:0] io_in_bits, // @[:@6.4]
  input        io_out_ready, // @[:@6.4]
  output       io_out_valid, // @[:@6.4]
  output [3:0] io_out_bits // @[:@6.4]
);
  reg [3:0] outerRegData; // @[AXILiteCfg.scala 33:31:@8.4]
  reg [31:0] _RAND_0;
  reg  outerRegValid; // @[AXILiteCfg.scala 34:36:@9.4]
  reg [31:0] _RAND_1;
  reg [3:0] innerRegData; // @[AXILiteCfg.scala 35:31:@10.4]
  reg [31:0] _RAND_2;
  reg  innerRegValid; // @[AXILiteCfg.scala 36:36:@11.4]
  reg [31:0] _RAND_3;
  reg  readyReg; // @[AXILiteCfg.scala 37:27:@12.4]
  reg [31:0] _RAND_4;
  wire  _T_40; // @[AXILiteCfg.scala 44:67:@18.6]
  wire  _T_41; // @[AXILiteCfg.scala 44:65:@19.6]
  wire  _T_42; // @[AXILiteCfg.scala 44:50:@20.6]
  wire  _T_43; // @[AXILiteCfg.scala 44:48:@21.6]
  wire  _GEN_2; // @[AXILiteCfg.scala 40:9:@14.4]
  wire  _GEN_3; // @[AXILiteCfg.scala 40:9:@14.4]
  assign _T_40 = ~ io_out_valid; // @[AXILiteCfg.scala 44:67:@18.6]
  assign _T_41 = io_out_ready | _T_40; // @[AXILiteCfg.scala 44:65:@19.6]
  assign _T_42 = ~ _T_41; // @[AXILiteCfg.scala 44:50:@20.6]
  assign _T_43 = outerRegValid & _T_42; // @[AXILiteCfg.scala 44:48:@21.6]
  assign _GEN_2 = readyReg ? io_in_valid : outerRegValid; // @[AXILiteCfg.scala 40:9:@14.4]
  assign _GEN_3 = readyReg ? _T_43 : innerRegValid; // @[AXILiteCfg.scala 40:9:@14.4]
  assign io_in_ready = readyReg; // @[AXILiteCfg.scala 49:21:@33.4]
  assign io_out_valid = readyReg ? outerRegValid : innerRegValid; // @[AXILiteCfg.scala 47:22:@29.4]
  assign io_out_bits = readyReg ? outerRegData : innerRegData; // @[AXILiteCfg.scala 46:21:@26.4]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  outerRegData = _RAND_0[3:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {1{`RANDOM}};
  outerRegValid = _RAND_1[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_2 = {1{`RANDOM}};
  innerRegData = _RAND_2[3:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_3 = {1{`RANDOM}};
  innerRegValid = _RAND_3[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_4 = {1{`RANDOM}};
  readyReg = _RAND_4[0:0];
  `endif // RANDOMIZE_REG_INIT
  end
`endif // RANDOMIZE
  always @(posedge clock) begin
    if (readyReg) begin
      outerRegData <= io_in_bits;
    end
    if (reset) begin
      outerRegValid <= 1'h0;
    end else begin
      if (readyReg) begin
        outerRegValid <= io_in_valid;
      end
    end
    if (readyReg) begin
      innerRegData <= outerRegData;
    end
    if (reset) begin
      innerRegValid <= 1'h0;
    end else begin
      if (readyReg) begin
        innerRegValid <= _T_43;
      end
    end
    readyReg <= io_out_ready | _T_40;
  end
endmodule
module ElasticBuffer( // @[:@40.2]
  input        clock, // @[:@41.4]
  input        reset, // @[:@42.4]
  output       io_in_ready, // @[:@43.4]
  input        io_in_valid, // @[:@43.4]
  input  [3:0] io_in_bits, // @[:@43.4]
  input        io_out_ready, // @[:@43.4]
  output       io_out_valid, // @[:@43.4]
  output [3:0] io_out_bits // @[:@43.4]
);
  wire  fullBuffer_clock; // @[AXILiteCfg.scala 20:32:@45.4]
  wire  fullBuffer_reset; // @[AXILiteCfg.scala 20:32:@45.4]
  wire  fullBuffer_io_in_ready; // @[AXILiteCfg.scala 20:32:@45.4]
  wire  fullBuffer_io_in_valid; // @[AXILiteCfg.scala 20:32:@45.4]
  wire [3:0] fullBuffer_io_in_bits; // @[AXILiteCfg.scala 20:32:@45.4]
  wire  fullBuffer_io_out_ready; // @[AXILiteCfg.scala 20:32:@45.4]
  wire  fullBuffer_io_out_valid; // @[AXILiteCfg.scala 20:32:@45.4]
  wire [3:0] fullBuffer_io_out_bits; // @[AXILiteCfg.scala 20:32:@45.4]
  ElasticBufferRegExport fullBuffer ( // @[AXILiteCfg.scala 20:32:@45.4]
    .clock(fullBuffer_clock),
    .reset(fullBuffer_reset),
    .io_in_ready(fullBuffer_io_in_ready),
    .io_in_valid(fullBuffer_io_in_valid),
    .io_in_bits(fullBuffer_io_in_bits),
    .io_out_ready(fullBuffer_io_out_ready),
    .io_out_valid(fullBuffer_io_out_valid),
    .io_out_bits(fullBuffer_io_out_bits)
  );
  assign io_in_ready = fullBuffer_io_in_ready; // @[AXILiteCfg.scala 21:26:@50.4]
  assign io_out_valid = fullBuffer_io_out_valid; // @[AXILiteCfg.scala 22:16:@52.4]
  assign io_out_bits = fullBuffer_io_out_bits; // @[AXILiteCfg.scala 22:16:@51.4]
  assign fullBuffer_clock = clock; // @[:@46.4]
  assign fullBuffer_reset = reset; // @[:@47.4]
  assign fullBuffer_io_in_valid = io_in_valid; // @[AXILiteCfg.scala 21:26:@49.4]
  assign fullBuffer_io_in_bits = io_in_bits; // @[AXILiteCfg.scala 21:26:@48.4]
  assign fullBuffer_io_out_ready = io_out_ready; // @[AXILiteCfg.scala 22:16:@53.4]
endmodule
module ElasticBufferRegExport_2( // @[:@107.2]
  input         clock, // @[:@108.4]
  input         reset, // @[:@109.4]
  output        io_in_ready, // @[:@110.4]
  input         io_in_valid, // @[:@110.4]
  input  [31:0] io_in_bits, // @[:@110.4]
  input         io_out_ready, // @[:@110.4]
  output        io_out_valid, // @[:@110.4]
  output [31:0] io_out_bits // @[:@110.4]
);
  reg [31:0] outerRegData; // @[AXILiteCfg.scala 33:31:@112.4]
  reg [31:0] _RAND_0;
  reg  outerRegValid; // @[AXILiteCfg.scala 34:36:@113.4]
  reg [31:0] _RAND_1;
  reg [31:0] innerRegData; // @[AXILiteCfg.scala 35:31:@114.4]
  reg [31:0] _RAND_2;
  reg  innerRegValid; // @[AXILiteCfg.scala 36:36:@115.4]
  reg [31:0] _RAND_3;
  reg  readyReg; // @[AXILiteCfg.scala 37:27:@116.4]
  reg [31:0] _RAND_4;
  wire  _T_40; // @[AXILiteCfg.scala 44:67:@122.6]
  wire  _T_41; // @[AXILiteCfg.scala 44:65:@123.6]
  wire  _T_42; // @[AXILiteCfg.scala 44:50:@124.6]
  wire  _T_43; // @[AXILiteCfg.scala 44:48:@125.6]
  wire  _GEN_2; // @[AXILiteCfg.scala 40:9:@118.4]
  wire  _GEN_3; // @[AXILiteCfg.scala 40:9:@118.4]
  assign _T_40 = ~ io_out_valid; // @[AXILiteCfg.scala 44:67:@122.6]
  assign _T_41 = io_out_ready | _T_40; // @[AXILiteCfg.scala 44:65:@123.6]
  assign _T_42 = ~ _T_41; // @[AXILiteCfg.scala 44:50:@124.6]
  assign _T_43 = outerRegValid & _T_42; // @[AXILiteCfg.scala 44:48:@125.6]
  assign _GEN_2 = readyReg ? io_in_valid : outerRegValid; // @[AXILiteCfg.scala 40:9:@118.4]
  assign _GEN_3 = readyReg ? _T_43 : innerRegValid; // @[AXILiteCfg.scala 40:9:@118.4]
  assign io_in_ready = readyReg; // @[AXILiteCfg.scala 49:21:@137.4]
  assign io_out_valid = readyReg ? outerRegValid : innerRegValid; // @[AXILiteCfg.scala 47:22:@133.4]
  assign io_out_bits = readyReg ? outerRegData : innerRegData; // @[AXILiteCfg.scala 46:21:@130.4]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  outerRegData = _RAND_0[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {1{`RANDOM}};
  outerRegValid = _RAND_1[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_2 = {1{`RANDOM}};
  innerRegData = _RAND_2[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_3 = {1{`RANDOM}};
  innerRegValid = _RAND_3[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_4 = {1{`RANDOM}};
  readyReg = _RAND_4[0:0];
  `endif // RANDOMIZE_REG_INIT
  end
`endif // RANDOMIZE
  always @(posedge clock) begin
    if (readyReg) begin
      outerRegData <= io_in_bits;
    end
    if (reset) begin
      outerRegValid <= 1'h0;
    end else begin
      if (readyReg) begin
        outerRegValid <= io_in_valid;
      end
    end
    if (readyReg) begin
      innerRegData <= outerRegData;
    end
    if (reset) begin
      innerRegValid <= 1'h0;
    end else begin
      if (readyReg) begin
        innerRegValid <= _T_43;
      end
    end
    readyReg <= io_out_ready | _T_40;
  end
endmodule
module ElasticBuffer_2( // @[:@144.2]
  input         clock, // @[:@145.4]
  input         reset, // @[:@146.4]
  output        io_in_ready, // @[:@147.4]
  input         io_in_valid, // @[:@147.4]
  input  [31:0] io_in_bits, // @[:@147.4]
  input         io_out_ready, // @[:@147.4]
  output        io_out_valid, // @[:@147.4]
  output [31:0] io_out_bits // @[:@147.4]
);
  wire  fullBuffer_clock; // @[AXILiteCfg.scala 20:32:@149.4]
  wire  fullBuffer_reset; // @[AXILiteCfg.scala 20:32:@149.4]
  wire  fullBuffer_io_in_ready; // @[AXILiteCfg.scala 20:32:@149.4]
  wire  fullBuffer_io_in_valid; // @[AXILiteCfg.scala 20:32:@149.4]
  wire [31:0] fullBuffer_io_in_bits; // @[AXILiteCfg.scala 20:32:@149.4]
  wire  fullBuffer_io_out_ready; // @[AXILiteCfg.scala 20:32:@149.4]
  wire  fullBuffer_io_out_valid; // @[AXILiteCfg.scala 20:32:@149.4]
  wire [31:0] fullBuffer_io_out_bits; // @[AXILiteCfg.scala 20:32:@149.4]
  ElasticBufferRegExport_2 fullBuffer ( // @[AXILiteCfg.scala 20:32:@149.4]
    .clock(fullBuffer_clock),
    .reset(fullBuffer_reset),
    .io_in_ready(fullBuffer_io_in_ready),
    .io_in_valid(fullBuffer_io_in_valid),
    .io_in_bits(fullBuffer_io_in_bits),
    .io_out_ready(fullBuffer_io_out_ready),
    .io_out_valid(fullBuffer_io_out_valid),
    .io_out_bits(fullBuffer_io_out_bits)
  );
  assign io_in_ready = fullBuffer_io_in_ready; // @[AXILiteCfg.scala 21:26:@154.4]
  assign io_out_valid = fullBuffer_io_out_valid; // @[AXILiteCfg.scala 22:16:@156.4]
  assign io_out_bits = fullBuffer_io_out_bits; // @[AXILiteCfg.scala 22:16:@155.4]
  assign fullBuffer_clock = clock; // @[:@150.4]
  assign fullBuffer_reset = reset; // @[:@151.4]
  assign fullBuffer_io_in_valid = io_in_valid; // @[AXILiteCfg.scala 21:26:@153.4]
  assign fullBuffer_io_in_bits = io_in_bits; // @[AXILiteCfg.scala 21:26:@152.4]
  assign fullBuffer_io_out_ready = io_out_ready; // @[AXILiteCfg.scala 22:16:@157.4]
endmodule
module AXILiteConfig( // @[:@211.2]
  input         clock, // @[:@212.4]
  input         reset, // @[:@213.4]
  input  [5:0]  io_axiLiteCfg_ARADDR, // @[:@214.4]
  input         io_axiLiteCfg_ARVALID, // @[:@214.4]
  output        io_axiLiteCfg_ARREADY, // @[:@214.4]
  output [31:0] io_axiLiteCfg_RDATA, // @[:@214.4]
  output [1:0]  io_axiLiteCfg_RRESP, // @[:@214.4]
  output        io_axiLiteCfg_RVALID, // @[:@214.4]
  input         io_axiLiteCfg_RREADY, // @[:@214.4]
  input  [5:0]  io_axiLiteCfg_AWADDR, // @[:@214.4]
  input         io_axiLiteCfg_AWVALID, // @[:@214.4]
  output        io_axiLiteCfg_AWREADY, // @[:@214.4]
  input  [31:0] io_axiLiteCfg_WDATA, // @[:@214.4]
  input         io_axiLiteCfg_WVALID, // @[:@214.4]
  output        io_axiLiteCfg_WREADY, // @[:@214.4]
  input  [3:0]  io_axiLiteCfg_WSTRB, // @[:@214.4]
  output [1:0]  io_axiLiteCfg_BRESP, // @[:@214.4]
  output        io_axiLiteCfg_BVALID, // @[:@214.4]
  input         io_axiLiteCfg_BREADY, // @[:@214.4]
  output [31:0] io_traceNum, // @[:@214.4]
  input  [31:0] io_cycleCnt, // @[:@214.4]
  output        io_start, // @[:@214.4]
  input         io_end // @[:@214.4]
);
  wire  ElasticBuffer_clock; // @[AXILiteCfg.scala 83:30:@219.4]
  wire  ElasticBuffer_reset; // @[AXILiteCfg.scala 83:30:@219.4]
  wire  ElasticBuffer_io_in_ready; // @[AXILiteCfg.scala 83:30:@219.4]
  wire  ElasticBuffer_io_in_valid; // @[AXILiteCfg.scala 83:30:@219.4]
  wire [3:0] ElasticBuffer_io_in_bits; // @[AXILiteCfg.scala 83:30:@219.4]
  wire  ElasticBuffer_io_out_ready; // @[AXILiteCfg.scala 83:30:@219.4]
  wire  ElasticBuffer_io_out_valid; // @[AXILiteCfg.scala 83:30:@219.4]
  wire [3:0] ElasticBuffer_io_out_bits; // @[AXILiteCfg.scala 83:30:@219.4]
  wire  ElasticBuffer_1_clock; // @[AXILiteCfg.scala 95:30:@234.4]
  wire  ElasticBuffer_1_reset; // @[AXILiteCfg.scala 95:30:@234.4]
  wire  ElasticBuffer_1_io_in_ready; // @[AXILiteCfg.scala 95:30:@234.4]
  wire  ElasticBuffer_1_io_in_valid; // @[AXILiteCfg.scala 95:30:@234.4]
  wire [3:0] ElasticBuffer_1_io_in_bits; // @[AXILiteCfg.scala 95:30:@234.4]
  wire  ElasticBuffer_1_io_out_ready; // @[AXILiteCfg.scala 95:30:@234.4]
  wire  ElasticBuffer_1_io_out_valid; // @[AXILiteCfg.scala 95:30:@234.4]
  wire [3:0] ElasticBuffer_1_io_out_bits; // @[AXILiteCfg.scala 95:30:@234.4]
  wire  ElasticBuffer_2_clock; // @[AXILiteCfg.scala 96:30:@237.4]
  wire  ElasticBuffer_2_reset; // @[AXILiteCfg.scala 96:30:@237.4]
  wire  ElasticBuffer_2_io_in_ready; // @[AXILiteCfg.scala 96:30:@237.4]
  wire  ElasticBuffer_2_io_in_valid; // @[AXILiteCfg.scala 96:30:@237.4]
  wire [31:0] ElasticBuffer_2_io_in_bits; // @[AXILiteCfg.scala 96:30:@237.4]
  wire  ElasticBuffer_2_io_out_ready; // @[AXILiteCfg.scala 96:30:@237.4]
  wire  ElasticBuffer_2_io_out_valid; // @[AXILiteCfg.scala 96:30:@237.4]
  wire [31:0] ElasticBuffer_2_io_out_bits; // @[AXILiteCfg.scala 96:30:@237.4]
  wire  ElasticBuffer_3_clock; // @[AXILiteCfg.scala 97:30:@240.4]
  wire  ElasticBuffer_3_reset; // @[AXILiteCfg.scala 97:30:@240.4]
  wire  ElasticBuffer_3_io_in_ready; // @[AXILiteCfg.scala 97:30:@240.4]
  wire  ElasticBuffer_3_io_in_valid; // @[AXILiteCfg.scala 97:30:@240.4]
  wire [3:0] ElasticBuffer_3_io_in_bits; // @[AXILiteCfg.scala 97:30:@240.4]
  wire  ElasticBuffer_3_io_out_ready; // @[AXILiteCfg.scala 97:30:@240.4]
  wire  ElasticBuffer_3_io_out_valid; // @[AXILiteCfg.scala 97:30:@240.4]
  wire [3:0] ElasticBuffer_3_io_out_bits; // @[AXILiteCfg.scala 97:30:@240.4]
  reg  start; // @[AXILiteCfg.scala 73:28:@216.4]
  reg [31:0] _RAND_0;
  reg [31:0] traceNumReg; // @[AXILiteCfg.scala 75:34:@218.4]
  reg [31:0] _RAND_1;
  wire  _T_53; // @[Mux.scala 46:19:@226.4]
  wire [31:0] _T_54; // @[Mux.scala 46:16:@227.4]
  wire  _T_55; // @[Mux.scala 46:19:@228.4]
  wire  _T_63; // @[AXILiteCfg.scala 109:52:@253.4]
  wire  _T_69; // @[AXILiteCfg.scala 113:54:@262.4]
  wire  _T_70; // @[AXILiteCfg.scala 113:75:@263.4]
  wire  wrAddrDataAvailable; // @[AXILiteCfg.scala 113:96:@265.4]
  reg  bvalidReg; // @[AXILiteCfg.scala 115:32:@266.4]
  reg [31:0] _RAND_2;
  wire [3:0] _T_75; // @[AXILiteCfg.scala 119:56:@269.4]
  wire  _T_77; // @[AXILiteCfg.scala 119:56:@270.4]
  wire  _T_78; // @[AXILiteCfg.scala 119:35:@271.4]
  wire  _T_80; // @[AXILiteCfg.scala 120:41:@273.6]
  wire  _T_81; // @[AXILiteCfg.scala 121:51:@275.8]
  wire  _T_83; // @[AXILiteCfg.scala 122:47:@279.8]
  wire [31:0] _GEN_0; // @[AXILiteCfg.scala 122:56:@280.8]
  wire  _GEN_1; // @[AXILiteCfg.scala 120:50:@274.6]
  wire [31:0] _GEN_2; // @[AXILiteCfg.scala 120:50:@274.6]
  wire  _GEN_3; // @[AXILiteCfg.scala 119:62:@272.4]
  wire [31:0] _GEN_4; // @[AXILiteCfg.scala 119:62:@272.4]
  wire  _T_85; // @[AXILiteCfg.scala 128:32:@288.6]
  wire  _GEN_5; // @[AXILiteCfg.scala 128:56:@289.6]
  wire  _GEN_6; // @[AXILiteCfg.scala 126:36:@284.4]
  ElasticBuffer ElasticBuffer ( // @[AXILiteCfg.scala 83:30:@219.4]
    .clock(ElasticBuffer_clock),
    .reset(ElasticBuffer_reset),
    .io_in_ready(ElasticBuffer_io_in_ready),
    .io_in_valid(ElasticBuffer_io_in_valid),
    .io_in_bits(ElasticBuffer_io_in_bits),
    .io_out_ready(ElasticBuffer_io_out_ready),
    .io_out_valid(ElasticBuffer_io_out_valid),
    .io_out_bits(ElasticBuffer_io_out_bits)
  );
  ElasticBuffer ElasticBuffer_1 ( // @[AXILiteCfg.scala 95:30:@234.4]
    .clock(ElasticBuffer_1_clock),
    .reset(ElasticBuffer_1_reset),
    .io_in_ready(ElasticBuffer_1_io_in_ready),
    .io_in_valid(ElasticBuffer_1_io_in_valid),
    .io_in_bits(ElasticBuffer_1_io_in_bits),
    .io_out_ready(ElasticBuffer_1_io_out_ready),
    .io_out_valid(ElasticBuffer_1_io_out_valid),
    .io_out_bits(ElasticBuffer_1_io_out_bits)
  );
  ElasticBuffer_2 ElasticBuffer_2 ( // @[AXILiteCfg.scala 96:30:@237.4]
    .clock(ElasticBuffer_2_clock),
    .reset(ElasticBuffer_2_reset),
    .io_in_ready(ElasticBuffer_2_io_in_ready),
    .io_in_valid(ElasticBuffer_2_io_in_valid),
    .io_in_bits(ElasticBuffer_2_io_in_bits),
    .io_out_ready(ElasticBuffer_2_io_out_ready),
    .io_out_valid(ElasticBuffer_2_io_out_valid),
    .io_out_bits(ElasticBuffer_2_io_out_bits)
  );
  ElasticBuffer ElasticBuffer_3 ( // @[AXILiteCfg.scala 97:30:@240.4]
    .clock(ElasticBuffer_3_clock),
    .reset(ElasticBuffer_3_reset),
    .io_in_ready(ElasticBuffer_3_io_in_ready),
    .io_in_valid(ElasticBuffer_3_io_in_valid),
    .io_in_bits(ElasticBuffer_3_io_in_bits),
    .io_out_ready(ElasticBuffer_3_io_out_ready),
    .io_out_valid(ElasticBuffer_3_io_out_valid),
    .io_out_bits(ElasticBuffer_3_io_out_bits)
  );
  assign _T_53 = 4'h8 == ElasticBuffer_io_out_bits; // @[Mux.scala 46:19:@226.4]
  assign _T_54 = _T_53 ? io_cycleCnt : {{31'd0}, io_end}; // @[Mux.scala 46:16:@227.4]
  assign _T_55 = 4'h4 == ElasticBuffer_io_out_bits; // @[Mux.scala 46:19:@228.4]
  assign _T_63 = ~ io_axiLiteCfg_BVALID; // @[AXILiteCfg.scala 109:52:@253.4]
  assign _T_69 = ElasticBuffer_1_io_out_valid & ElasticBuffer_2_io_out_valid; // @[AXILiteCfg.scala 113:54:@262.4]
  assign _T_70 = _T_69 & ElasticBuffer_3_io_out_valid; // @[AXILiteCfg.scala 113:75:@263.4]
  assign wrAddrDataAvailable = _T_70 & _T_63; // @[AXILiteCfg.scala 113:96:@265.4]
  assign _T_75 = ~ ElasticBuffer_3_io_out_bits; // @[AXILiteCfg.scala 119:56:@269.4]
  assign _T_77 = _T_75 == 4'h0; // @[AXILiteCfg.scala 119:56:@270.4]
  assign _T_78 = wrAddrDataAvailable & _T_77; // @[AXILiteCfg.scala 119:35:@271.4]
  assign _T_80 = ElasticBuffer_1_io_out_bits == 4'h0; // @[AXILiteCfg.scala 120:41:@273.6]
  assign _T_81 = ElasticBuffer_2_io_out_bits[0]; // @[AXILiteCfg.scala 121:51:@275.8]
  assign _T_83 = ElasticBuffer_1_io_out_bits == 4'h4; // @[AXILiteCfg.scala 122:47:@279.8]
  assign _GEN_0 = _T_83 ? ElasticBuffer_2_io_out_bits : traceNumReg; // @[AXILiteCfg.scala 122:56:@280.8]
  assign _GEN_1 = _T_80 ? _T_81 : start; // @[AXILiteCfg.scala 120:50:@274.6]
  assign _GEN_2 = _T_80 ? traceNumReg : _GEN_0; // @[AXILiteCfg.scala 120:50:@274.6]
  assign _GEN_3 = _T_78 ? _GEN_1 : start; // @[AXILiteCfg.scala 119:62:@272.4]
  assign _GEN_4 = _T_78 ? _GEN_2 : traceNumReg; // @[AXILiteCfg.scala 119:62:@272.4]
  assign _T_85 = bvalidReg & io_axiLiteCfg_BREADY; // @[AXILiteCfg.scala 128:32:@288.6]
  assign _GEN_5 = _T_85 ? 1'h0 : bvalidReg; // @[AXILiteCfg.scala 128:56:@289.6]
  assign _GEN_6 = wrAddrDataAvailable ? 1'h1 : _GEN_5; // @[AXILiteCfg.scala 126:36:@284.4]
  assign io_axiLiteCfg_ARREADY = ElasticBuffer_io_in_ready; // @[AXILiteCfg.scala 86:31:@225.4]
  assign io_axiLiteCfg_RDATA = _T_55 ? traceNumReg : _T_54; // @[AXILiteCfg.scala 88:30:@230.4]
  assign io_axiLiteCfg_RRESP = 2'h0; // @[AXILiteCfg.scala 92:29:@233.4]
  assign io_axiLiteCfg_RVALID = ElasticBuffer_io_out_valid; // @[AXILiteCfg.scala 89:30:@231.4]
  assign io_axiLiteCfg_AWREADY = ElasticBuffer_1_io_in_ready; // @[AXILiteCfg.scala 101:31:@246.4]
  assign io_axiLiteCfg_WREADY = ElasticBuffer_2_io_in_ready & ElasticBuffer_3_io_in_ready; // @[AXILiteCfg.scala 107:30:@252.4]
  assign io_axiLiteCfg_BRESP = 2'h0; // @[AXILiteCfg.scala 116:30:@267.4]
  assign io_axiLiteCfg_BVALID = bvalidReg; // @[AXILiteCfg.scala 117:30:@268.4]
  assign io_traceNum = traceNumReg; // @[AXILiteCfg.scala 132:21:@292.4]
  assign io_start = start; // @[AXILiteCfg.scala 74:18:@217.4]
  assign ElasticBuffer_clock = clock; // @[:@220.4]
  assign ElasticBuffer_reset = reset; // @[:@221.4]
  assign ElasticBuffer_io_in_valid = io_axiLiteCfg_ARVALID; // @[AXILiteCfg.scala 85:31:@224.4]
  assign ElasticBuffer_io_in_bits = io_axiLiteCfg_ARADDR[5:2]; // @[AXILiteCfg.scala 84:31:@223.4]
  assign ElasticBuffer_io_out_ready = io_axiLiteCfg_RREADY; // @[AXILiteCfg.scala 90:30:@232.4]
  assign ElasticBuffer_1_clock = clock; // @[:@235.4]
  assign ElasticBuffer_1_reset = reset; // @[:@236.4]
  assign ElasticBuffer_1_io_in_valid = io_axiLiteCfg_AWVALID; // @[AXILiteCfg.scala 100:31:@245.4]
  assign ElasticBuffer_1_io_in_bits = io_axiLiteCfg_AWADDR[5:2]; // @[AXILiteCfg.scala 99:31:@244.4]
  assign ElasticBuffer_1_io_out_ready = ElasticBuffer_2_io_out_valid & _T_63; // @[AXILiteCfg.scala 109:28:@255.4]
  assign ElasticBuffer_2_clock = clock; // @[:@238.4]
  assign ElasticBuffer_2_reset = reset; // @[:@239.4]
  assign ElasticBuffer_2_io_in_valid = io_axiLiteCfg_WVALID; // @[AXILiteCfg.scala 104:30:@248.4]
  assign ElasticBuffer_2_io_in_bits = io_axiLiteCfg_WDATA; // @[AXILiteCfg.scala 103:30:@247.4]
  assign ElasticBuffer_2_io_out_ready = ElasticBuffer_1_io_out_valid & _T_63; // @[AXILiteCfg.scala 110:28:@258.4]
  assign ElasticBuffer_3_clock = clock; // @[:@241.4]
  assign ElasticBuffer_3_reset = reset; // @[:@242.4]
  assign ElasticBuffer_3_io_in_valid = io_axiLiteCfg_WVALID; // @[AXILiteCfg.scala 106:30:@250.4]
  assign ElasticBuffer_3_io_in_bits = io_axiLiteCfg_WSTRB; // @[AXILiteCfg.scala 105:30:@249.4]
  assign ElasticBuffer_3_io_out_ready = ElasticBuffer_1_io_out_valid & _T_63; // @[AXILiteCfg.scala 111:28:@261.4]
`ifdef RANDOMIZE_GARBAGE_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_INVALID_ASSIGN
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_REG_INIT
`define RANDOMIZE
`endif
`ifdef RANDOMIZE_MEM_INIT
`define RANDOMIZE
`endif
`ifndef RANDOM
`define RANDOM $random
`endif
`ifdef RANDOMIZE
  integer initvar;
  initial begin
    `ifdef INIT_RANDOM
      `INIT_RANDOM
    `endif
    `ifndef VERILATOR
      #0.002 begin end
    `endif
  `ifdef RANDOMIZE_REG_INIT
  _RAND_0 = {1{`RANDOM}};
  start = _RAND_0[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {1{`RANDOM}};
  traceNumReg = _RAND_1[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_2 = {1{`RANDOM}};
  bvalidReg = _RAND_2[0:0];
  `endif // RANDOMIZE_REG_INIT
  end
`endif // RANDOMIZE
  always @(posedge clock) begin
    if (reset) begin
      start <= 1'h0;
    end else begin
      if (_T_78) begin
        if (_T_80) begin
          start <= _T_81;
        end
      end
    end
    if (reset) begin
      traceNumReg <= 32'h0;
    end else begin
      if (_T_78) begin
        if (!(_T_80)) begin
          if (_T_83) begin
            traceNumReg <= ElasticBuffer_2_io_out_bits;
          end
        end
      end
    end
    if (reset) begin
      bvalidReg <= 1'h0;
    end else begin
      if (wrAddrDataAvailable) begin
        bvalidReg <= 1'h1;
      end else begin
        if (_T_85) begin
          bvalidReg <= 1'h0;
        end
      end
    end
  end
endmodule
