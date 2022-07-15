module ElasticBufferRegExportAXISAndLenStream( // @[:@3.2]
  input        clock, // @[:@4.4]
  input        reset, // @[:@5.4]
  output       io_in_ready, // @[:@6.4]
  input        io_in_valid, // @[:@6.4]
  input  [1:0] io_in_bits, // @[:@6.4]
  input        io_out_ready, // @[:@6.4]
  output       io_out_valid, // @[:@6.4]
  output [1:0] io_out_bits // @[:@6.4]
);
  reg [1:0] outerRegData; // @[AXISAndLenStream.scala 34:27:@8.4]
  reg [31:0] _RAND_0;
  reg  outerRegValid; // @[AXISAndLenStream.scala 35:32:@9.4]
  reg [31:0] _RAND_1;
  reg [1:0] innerRegData; // @[AXISAndLenStream.scala 36:27:@10.4]
  reg [31:0] _RAND_2;
  reg  innerRegValid; // @[AXISAndLenStream.scala 37:32:@11.4]
  reg [31:0] _RAND_3;
  reg  readyReg; // @[AXISAndLenStream.scala 38:23:@12.4]
  reg [31:0] _RAND_4;
  wire  _T_40; // @[AXISAndLenStream.scala 45:59:@18.6]
  wire  _T_41; // @[AXISAndLenStream.scala 45:57:@19.6]
  wire  _T_42; // @[AXISAndLenStream.scala 45:42:@20.6]
  wire  _T_43; // @[AXISAndLenStream.scala 45:40:@21.6]
  wire  _GEN_2; // @[AXISAndLenStream.scala 41:5:@14.4]
  wire  _GEN_3; // @[AXISAndLenStream.scala 41:5:@14.4]
  assign _T_40 = ~ io_out_valid; // @[AXISAndLenStream.scala 45:59:@18.6]
  assign _T_41 = io_out_ready | _T_40; // @[AXISAndLenStream.scala 45:57:@19.6]
  assign _T_42 = ~ _T_41; // @[AXISAndLenStream.scala 45:42:@20.6]
  assign _T_43 = outerRegValid & _T_42; // @[AXISAndLenStream.scala 45:40:@21.6]
  assign _GEN_2 = readyReg ? io_in_valid : outerRegValid; // @[AXISAndLenStream.scala 41:5:@14.4]
  assign _GEN_3 = readyReg ? _T_43 : innerRegValid; // @[AXISAndLenStream.scala 41:5:@14.4]
  assign io_in_ready = readyReg; // @[AXISAndLenStream.scala 50:17:@33.4]
  assign io_out_valid = readyReg ? outerRegValid : innerRegValid; // @[AXISAndLenStream.scala 48:18:@29.4]
  assign io_out_bits = readyReg ? outerRegData : innerRegData; // @[AXISAndLenStream.scala 47:17:@26.4]
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
  outerRegData = _RAND_0[1:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {1{`RANDOM}};
  outerRegValid = _RAND_1[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_2 = {1{`RANDOM}};
  innerRegData = _RAND_2[1:0];
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
module ElasticBufferAXISAndLenStream( // @[:@40.2]
  input        clock, // @[:@41.4]
  input        reset, // @[:@42.4]
  output       io_in_ready, // @[:@43.4]
  input        io_in_valid, // @[:@43.4]
  input  [1:0] io_in_bits, // @[:@43.4]
  input        io_out_ready, // @[:@43.4]
  output       io_out_valid, // @[:@43.4]
  output [1:0] io_out_bits // @[:@43.4]
);
  wire  fullBuffer_clock; // @[AXISAndLenStream.scala 21:28:@45.4]
  wire  fullBuffer_reset; // @[AXISAndLenStream.scala 21:28:@45.4]
  wire  fullBuffer_io_in_ready; // @[AXISAndLenStream.scala 21:28:@45.4]
  wire  fullBuffer_io_in_valid; // @[AXISAndLenStream.scala 21:28:@45.4]
  wire [1:0] fullBuffer_io_in_bits; // @[AXISAndLenStream.scala 21:28:@45.4]
  wire  fullBuffer_io_out_ready; // @[AXISAndLenStream.scala 21:28:@45.4]
  wire  fullBuffer_io_out_valid; // @[AXISAndLenStream.scala 21:28:@45.4]
  wire [1:0] fullBuffer_io_out_bits; // @[AXISAndLenStream.scala 21:28:@45.4]
  ElasticBufferRegExportAXISAndLenStream fullBuffer ( // @[AXISAndLenStream.scala 21:28:@45.4]
    .clock(fullBuffer_clock),
    .reset(fullBuffer_reset),
    .io_in_ready(fullBuffer_io_in_ready),
    .io_in_valid(fullBuffer_io_in_valid),
    .io_in_bits(fullBuffer_io_in_bits),
    .io_out_ready(fullBuffer_io_out_ready),
    .io_out_valid(fullBuffer_io_out_valid),
    .io_out_bits(fullBuffer_io_out_bits)
  );
  assign io_in_ready = fullBuffer_io_in_ready; // @[AXISAndLenStream.scala 22:22:@50.4]
  assign io_out_valid = fullBuffer_io_out_valid; // @[AXISAndLenStream.scala 23:12:@52.4]
  assign io_out_bits = fullBuffer_io_out_bits; // @[AXISAndLenStream.scala 23:12:@51.4]
  assign fullBuffer_clock = clock; // @[:@46.4]
  assign fullBuffer_reset = reset; // @[:@47.4]
  assign fullBuffer_io_in_valid = io_in_valid; // @[AXISAndLenStream.scala 22:22:@49.4]
  assign fullBuffer_io_in_bits = io_in_bits; // @[AXISAndLenStream.scala 22:22:@48.4]
  assign fullBuffer_io_out_ready = io_out_ready; // @[AXISAndLenStream.scala 23:12:@53.4]
endmodule
module ElasticBufferRegExportAXISAndLenStream_2( // @[:@107.2]
  input         clock, // @[:@108.4]
  input         reset, // @[:@109.4]
  output        io_in_ready, // @[:@110.4]
  input         io_in_valid, // @[:@110.4]
  input  [31:0] io_in_bits, // @[:@110.4]
  input         io_out_ready, // @[:@110.4]
  output        io_out_valid, // @[:@110.4]
  output [31:0] io_out_bits // @[:@110.4]
);
  reg [31:0] outerRegData; // @[AXISAndLenStream.scala 34:27:@112.4]
  reg [31:0] _RAND_0;
  reg  outerRegValid; // @[AXISAndLenStream.scala 35:32:@113.4]
  reg [31:0] _RAND_1;
  reg [31:0] innerRegData; // @[AXISAndLenStream.scala 36:27:@114.4]
  reg [31:0] _RAND_2;
  reg  innerRegValid; // @[AXISAndLenStream.scala 37:32:@115.4]
  reg [31:0] _RAND_3;
  reg  readyReg; // @[AXISAndLenStream.scala 38:23:@116.4]
  reg [31:0] _RAND_4;
  wire  _T_40; // @[AXISAndLenStream.scala 45:59:@122.6]
  wire  _T_41; // @[AXISAndLenStream.scala 45:57:@123.6]
  wire  _T_42; // @[AXISAndLenStream.scala 45:42:@124.6]
  wire  _T_43; // @[AXISAndLenStream.scala 45:40:@125.6]
  wire  _GEN_2; // @[AXISAndLenStream.scala 41:5:@118.4]
  wire  _GEN_3; // @[AXISAndLenStream.scala 41:5:@118.4]
  assign _T_40 = ~ io_out_valid; // @[AXISAndLenStream.scala 45:59:@122.6]
  assign _T_41 = io_out_ready | _T_40; // @[AXISAndLenStream.scala 45:57:@123.6]
  assign _T_42 = ~ _T_41; // @[AXISAndLenStream.scala 45:42:@124.6]
  assign _T_43 = outerRegValid & _T_42; // @[AXISAndLenStream.scala 45:40:@125.6]
  assign _GEN_2 = readyReg ? io_in_valid : outerRegValid; // @[AXISAndLenStream.scala 41:5:@118.4]
  assign _GEN_3 = readyReg ? _T_43 : innerRegValid; // @[AXISAndLenStream.scala 41:5:@118.4]
  assign io_in_ready = readyReg; // @[AXISAndLenStream.scala 50:17:@137.4]
  assign io_out_valid = readyReg ? outerRegValid : innerRegValid; // @[AXISAndLenStream.scala 48:18:@133.4]
  assign io_out_bits = readyReg ? outerRegData : innerRegData; // @[AXISAndLenStream.scala 47:17:@130.4]
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
module ElasticBufferAXISAndLenStream_2( // @[:@144.2]
  input         clock, // @[:@145.4]
  input         reset, // @[:@146.4]
  output        io_in_ready, // @[:@147.4]
  input         io_in_valid, // @[:@147.4]
  input  [31:0] io_in_bits, // @[:@147.4]
  input         io_out_ready, // @[:@147.4]
  output        io_out_valid, // @[:@147.4]
  output [31:0] io_out_bits // @[:@147.4]
);
  wire  fullBuffer_clock; // @[AXISAndLenStream.scala 21:28:@149.4]
  wire  fullBuffer_reset; // @[AXISAndLenStream.scala 21:28:@149.4]
  wire  fullBuffer_io_in_ready; // @[AXISAndLenStream.scala 21:28:@149.4]
  wire  fullBuffer_io_in_valid; // @[AXISAndLenStream.scala 21:28:@149.4]
  wire [31:0] fullBuffer_io_in_bits; // @[AXISAndLenStream.scala 21:28:@149.4]
  wire  fullBuffer_io_out_ready; // @[AXISAndLenStream.scala 21:28:@149.4]
  wire  fullBuffer_io_out_valid; // @[AXISAndLenStream.scala 21:28:@149.4]
  wire [31:0] fullBuffer_io_out_bits; // @[AXISAndLenStream.scala 21:28:@149.4]
  ElasticBufferRegExportAXISAndLenStream_2 fullBuffer ( // @[AXISAndLenStream.scala 21:28:@149.4]
    .clock(fullBuffer_clock),
    .reset(fullBuffer_reset),
    .io_in_ready(fullBuffer_io_in_ready),
    .io_in_valid(fullBuffer_io_in_valid),
    .io_in_bits(fullBuffer_io_in_bits),
    .io_out_ready(fullBuffer_io_out_ready),
    .io_out_valid(fullBuffer_io_out_valid),
    .io_out_bits(fullBuffer_io_out_bits)
  );
  assign io_in_ready = fullBuffer_io_in_ready; // @[AXISAndLenStream.scala 22:22:@154.4]
  assign io_out_valid = fullBuffer_io_out_valid; // @[AXISAndLenStream.scala 23:12:@156.4]
  assign io_out_bits = fullBuffer_io_out_bits; // @[AXISAndLenStream.scala 23:12:@155.4]
  assign fullBuffer_clock = clock; // @[:@150.4]
  assign fullBuffer_reset = reset; // @[:@151.4]
  assign fullBuffer_io_in_valid = io_in_valid; // @[AXISAndLenStream.scala 22:22:@153.4]
  assign fullBuffer_io_in_bits = io_in_bits; // @[AXISAndLenStream.scala 22:22:@152.4]
  assign fullBuffer_io_out_ready = io_out_ready; // @[AXISAndLenStream.scala 23:12:@157.4]
endmodule
module ElasticBufferRegExportAXISAndLenStream_3( // @[:@159.2]
  input        clock, // @[:@160.4]
  input        reset, // @[:@161.4]
  output       io_in_ready, // @[:@162.4]
  input        io_in_valid, // @[:@162.4]
  input  [3:0] io_in_bits, // @[:@162.4]
  input        io_out_ready, // @[:@162.4]
  output       io_out_valid, // @[:@162.4]
  output [3:0] io_out_bits // @[:@162.4]
);
  reg [3:0] outerRegData; // @[AXISAndLenStream.scala 34:27:@164.4]
  reg [31:0] _RAND_0;
  reg  outerRegValid; // @[AXISAndLenStream.scala 35:32:@165.4]
  reg [31:0] _RAND_1;
  reg [3:0] innerRegData; // @[AXISAndLenStream.scala 36:27:@166.4]
  reg [31:0] _RAND_2;
  reg  innerRegValid; // @[AXISAndLenStream.scala 37:32:@167.4]
  reg [31:0] _RAND_3;
  reg  readyReg; // @[AXISAndLenStream.scala 38:23:@168.4]
  reg [31:0] _RAND_4;
  wire  _T_40; // @[AXISAndLenStream.scala 45:59:@174.6]
  wire  _T_41; // @[AXISAndLenStream.scala 45:57:@175.6]
  wire  _T_42; // @[AXISAndLenStream.scala 45:42:@176.6]
  wire  _T_43; // @[AXISAndLenStream.scala 45:40:@177.6]
  wire  _GEN_2; // @[AXISAndLenStream.scala 41:5:@170.4]
  wire  _GEN_3; // @[AXISAndLenStream.scala 41:5:@170.4]
  assign _T_40 = ~ io_out_valid; // @[AXISAndLenStream.scala 45:59:@174.6]
  assign _T_41 = io_out_ready | _T_40; // @[AXISAndLenStream.scala 45:57:@175.6]
  assign _T_42 = ~ _T_41; // @[AXISAndLenStream.scala 45:42:@176.6]
  assign _T_43 = outerRegValid & _T_42; // @[AXISAndLenStream.scala 45:40:@177.6]
  assign _GEN_2 = readyReg ? io_in_valid : outerRegValid; // @[AXISAndLenStream.scala 41:5:@170.4]
  assign _GEN_3 = readyReg ? _T_43 : innerRegValid; // @[AXISAndLenStream.scala 41:5:@170.4]
  assign io_in_ready = readyReg; // @[AXISAndLenStream.scala 50:17:@189.4]
  assign io_out_valid = readyReg ? outerRegValid : innerRegValid; // @[AXISAndLenStream.scala 48:18:@185.4]
  assign io_out_bits = readyReg ? outerRegData : innerRegData; // @[AXISAndLenStream.scala 47:17:@182.4]
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
module ElasticBufferAXISAndLenStream_3( // @[:@196.2]
  input        clock, // @[:@197.4]
  input        reset, // @[:@198.4]
  output       io_in_ready, // @[:@199.4]
  input        io_in_valid, // @[:@199.4]
  input  [3:0] io_in_bits, // @[:@199.4]
  input        io_out_ready, // @[:@199.4]
  output       io_out_valid, // @[:@199.4]
  output [3:0] io_out_bits // @[:@199.4]
);
  wire  fullBuffer_clock; // @[AXISAndLenStream.scala 21:28:@201.4]
  wire  fullBuffer_reset; // @[AXISAndLenStream.scala 21:28:@201.4]
  wire  fullBuffer_io_in_ready; // @[AXISAndLenStream.scala 21:28:@201.4]
  wire  fullBuffer_io_in_valid; // @[AXISAndLenStream.scala 21:28:@201.4]
  wire [3:0] fullBuffer_io_in_bits; // @[AXISAndLenStream.scala 21:28:@201.4]
  wire  fullBuffer_io_out_ready; // @[AXISAndLenStream.scala 21:28:@201.4]
  wire  fullBuffer_io_out_valid; // @[AXISAndLenStream.scala 21:28:@201.4]
  wire [3:0] fullBuffer_io_out_bits; // @[AXISAndLenStream.scala 21:28:@201.4]
  ElasticBufferRegExportAXISAndLenStream_3 fullBuffer ( // @[AXISAndLenStream.scala 21:28:@201.4]
    .clock(fullBuffer_clock),
    .reset(fullBuffer_reset),
    .io_in_ready(fullBuffer_io_in_ready),
    .io_in_valid(fullBuffer_io_in_valid),
    .io_in_bits(fullBuffer_io_in_bits),
    .io_out_ready(fullBuffer_io_out_ready),
    .io_out_valid(fullBuffer_io_out_valid),
    .io_out_bits(fullBuffer_io_out_bits)
  );
  assign io_in_ready = fullBuffer_io_in_ready; // @[AXISAndLenStream.scala 22:22:@206.4]
  assign io_out_valid = fullBuffer_io_out_valid; // @[AXISAndLenStream.scala 23:12:@208.4]
  assign io_out_bits = fullBuffer_io_out_bits; // @[AXISAndLenStream.scala 23:12:@207.4]
  assign fullBuffer_clock = clock; // @[:@202.4]
  assign fullBuffer_reset = reset; // @[:@203.4]
  assign fullBuffer_io_in_valid = io_in_valid; // @[AXISAndLenStream.scala 22:22:@205.4]
  assign fullBuffer_io_in_bits = io_in_bits; // @[AXISAndLenStream.scala 22:22:@204.4]
  assign fullBuffer_io_out_ready = io_out_ready; // @[AXISAndLenStream.scala 23:12:@209.4]
endmodule
module AXISAndLenStream( // @[:@211.2]
  input         clock, // @[:@212.4]
  input         reset, // @[:@213.4]
  output        io_rdAddr_ready, // @[:@214.4]
  input         io_rdAddr_valid, // @[:@214.4]
  input  [1:0]  io_rdAddr_bits, // @[:@214.4]
  input         io_rdData_ready, // @[:@214.4]
  output        io_rdData_valid, // @[:@214.4]
  output [31:0] io_rdData_bits, // @[:@214.4]
  output        io_wrAddr_ready, // @[:@214.4]
  input         io_wrAddr_valid, // @[:@214.4]
  input  [1:0]  io_wrAddr_bits, // @[:@214.4]
  output        io_wrData_ready, // @[:@214.4]
  input         io_wrData_valid, // @[:@214.4]
  input  [31:0] io_wrData_bits, // @[:@214.4]
  output        io_wrAck, // @[:@214.4]
  output        io_offset_valid, // @[:@214.4]
  output [31:0] io_offset_bits, // @[:@214.4]
  output        io_nnz_valid, // @[:@214.4]
  output [31:0] io_nnz_bits, // @[:@214.4]
  output        io_outputSize_valid, // @[:@214.4]
  output [31:0] io_outputSize_bits, // @[:@214.4]
  output        io_running, // @[:@214.4]
  input         io_done, // @[:@214.4]
  output        io_rowPtrStream_ready, // @[:@214.4]
  input         io_rowPtrStream_valid, // @[:@214.4]
  input  [31:0] io_rowPtrStream_bits, // @[:@214.4]
  input         io_lenStream_ready, // @[:@214.4]
  output        io_lenStream_valid, // @[:@214.4]
  output [31:0] io_lenStream_bits, // @[:@214.4]
  output        io_wstrb_ready, // @[:@214.4]
  input         io_wstrb_valid, // @[:@214.4]
  input  [3:0]  io_wstrb_bits // @[:@214.4]
);
  wire  ElasticBufferAXISAndLenStream_clock; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire  ElasticBufferAXISAndLenStream_reset; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire  ElasticBufferAXISAndLenStream_io_in_ready; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire  ElasticBufferAXISAndLenStream_io_in_valid; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire [1:0] ElasticBufferAXISAndLenStream_io_in_bits; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire  ElasticBufferAXISAndLenStream_io_out_ready; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire  ElasticBufferAXISAndLenStream_io_out_valid; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire [1:0] ElasticBufferAXISAndLenStream_io_out_bits; // @[AXISAndLenStream.scala 8:23:@221.4]
  wire  ElasticBufferAXISAndLenStream_1_clock; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire  ElasticBufferAXISAndLenStream_1_reset; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire  ElasticBufferAXISAndLenStream_1_io_in_ready; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire  ElasticBufferAXISAndLenStream_1_io_in_valid; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire [1:0] ElasticBufferAXISAndLenStream_1_io_in_bits; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire  ElasticBufferAXISAndLenStream_1_io_out_ready; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire  ElasticBufferAXISAndLenStream_1_io_out_valid; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire [1:0] ElasticBufferAXISAndLenStream_1_io_out_bits; // @[AXISAndLenStream.scala 8:23:@237.4]
  wire  ElasticBufferAXISAndLenStream_2_clock; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire  ElasticBufferAXISAndLenStream_2_reset; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire  ElasticBufferAXISAndLenStream_2_io_in_ready; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire  ElasticBufferAXISAndLenStream_2_io_in_valid; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire [31:0] ElasticBufferAXISAndLenStream_2_io_in_bits; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire  ElasticBufferAXISAndLenStream_2_io_out_ready; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire  ElasticBufferAXISAndLenStream_2_io_out_valid; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire [31:0] ElasticBufferAXISAndLenStream_2_io_out_bits; // @[AXISAndLenStream.scala 8:23:@243.4]
  wire  ElasticBufferAXISAndLenStream_3_clock; // @[AXISAndLenStream.scala 8:23:@249.4]
  wire  ElasticBufferAXISAndLenStream_3_reset; // @[AXISAndLenStream.scala 8:23:@249.4]
  wire  ElasticBufferAXISAndLenStream_3_io_in_ready; // @[AXISAndLenStream.scala 8:23:@249.4]
  wire  ElasticBufferAXISAndLenStream_3_io_in_valid; // @[AXISAndLenStream.scala 8:23:@249.4]
  wire [3:0] ElasticBufferAXISAndLenStream_3_io_in_bits; // @[AXISAndLenStream.scala 8:23:@249.4]
  wire  ElasticBufferAXISAndLenStream_3_io_out_ready; // @[AXISAndLenStream.scala 8:23:@249.4]
  wire  ElasticBufferAXISAndLenStream_3_io_out_valid; // @[AXISAndLenStream.scala 8:23:@249.4]
  wire [3:0] ElasticBufferAXISAndLenStream_3_io_out_bits; // @[AXISAndLenStream.scala 8:23:@249.4]
  reg  state; // @[AXISAndLenStream.scala 80:24:@216.4]
  reg [31:0] _RAND_0;
  reg [31:0] regs_0; // @[AXISAndLenStream.scala 83:19:@219.4]
  reg [31:0] _RAND_1;
  reg [31:0] regs_1; // @[AXISAndLenStream.scala 83:19:@219.4]
  reg [31:0] _RAND_2;
  reg [31:0] regs_2; // @[AXISAndLenStream.scala 83:19:@219.4]
  reg [31:0] _RAND_3;
  wire  _T_107; // @[AXISAndLenStream.scala 88:54:@228.4]
  wire  _T_111; // @[Mux.scala 46:19:@229.4]
  wire [31:0] _T_112; // @[Mux.scala 46:16:@230.4]
  wire  _T_113; // @[Mux.scala 46:19:@231.4]
  wire [31:0] _T_114; // @[Mux.scala 46:16:@232.4]
  wire  _T_115; // @[Mux.scala 46:19:@233.4]
  wire  _T_120; // @[AXISAndLenStream.scala 94:46:@255.4]
  wire  wrAddrDataAvailable; // @[AXISAndLenStream.scala 94:63:@256.4]
  wire  _T_124; // @[AXISAndLenStream.scala 102:26:@263.6]
  wire [3:0] _T_125; // @[AXISAndLenStream.scala 102:50:@264.6]
  wire  _T_127; // @[AXISAndLenStream.scala 102:50:@265.6]
  wire  _T_128; // @[AXISAndLenStream.scala 102:34:@266.6]
  wire  _T_129; // @[AXISAndLenStream.scala 103:27:@268.8]
  wire  _GEN_1; // @[AXISAndLenStream.scala 102:56:@267.6]
  wire  start; // @[AXISAndLenStream.scala 101:31:@262.4]
  wire  _T_135; // @[AXISAndLenStream.scala 111:49:@276.4]
  wire  _T_136; // @[AXISAndLenStream.scala 111:32:@277.4]
  wire  _T_140; // @[AXISAndLenStream.scala 111:58:@280.4]
  wire  _T_142; // @[AXISAndLenStream.scala 111:49:@284.4]
  wire  _T_143; // @[AXISAndLenStream.scala 111:32:@285.4]
  wire  _T_147; // @[AXISAndLenStream.scala 111:58:@288.4]
  wire  _T_149; // @[AXISAndLenStream.scala 111:49:@292.4]
  wire  _T_150; // @[AXISAndLenStream.scala 111:32:@293.4]
  wire  _T_154; // @[AXISAndLenStream.scala 111:58:@296.4]
  wire  _T_159; // @[Conditional.scala 37:30:@307.4]
  wire  _GEN_7; // @[AXISAndLenStream.scala 127:21:@309.6]
  wire  _GEN_9; // @[AXISAndLenStream.scala 136:23:@320.8]
  wire  _GEN_11; // @[Conditional.scala 39:67:@318.6]
  wire  _GEN_12; // @[Conditional.scala 40:58:@308.4]
  reg  delayedRowPtr_0_valid; // @[AXISAndLenStream.scala 145:32:@332.4]
  reg [31:0] _RAND_4;
  reg [31:0] delayedRowPtr_0_bits; // @[AXISAndLenStream.scala 145:32:@332.4]
  reg [31:0] _RAND_5;
  reg  delayedRowPtr_1_valid; // @[AXISAndLenStream.scala 145:32:@332.4]
  reg [31:0] _RAND_6;
  reg [31:0] delayedRowPtr_1_bits; // @[AXISAndLenStream.scala 145:32:@332.4]
  reg [31:0] _RAND_7;
  wire  _T_227; // @[AXISAndLenStream.scala 148:51:@333.4]
  wire  _T_228; // @[AXISAndLenStream.scala 148:49:@334.4]
  wire  _T_229; // @[AXISAndLenStream.scala 148:77:@335.4]
  wire  _T_230; // @[AXISAndLenStream.scala 148:75:@336.4]
  wire [31:0] _GEN_15; // @[AXISAndLenStream.scala 151:89:@346.6]
  wire  _GEN_16; // @[AXISAndLenStream.scala 151:89:@346.6]
  wire  _GEN_17; // @[AXISAndLenStream.scala 149:19:@338.4]
  wire [31:0] _GEN_18; // @[AXISAndLenStream.scala 149:19:@338.4]
  wire  _T_238; // @[AXISAndLenStream.scala 157:36:@355.6]
  wire [31:0] _GEN_19; // @[AXISAndLenStream.scala 157:63:@356.6]
  wire  _GEN_20; // @[AXISAndLenStream.scala 157:63:@356.6]
  wire  _GEN_21; // @[AXISAndLenStream.scala 155:19:@350.4]
  wire [31:0] _GEN_22; // @[AXISAndLenStream.scala 155:19:@350.4]
  wire [32:0] _T_240; // @[AXISAndLenStream.scala 161:49:@362.4]
  wire [32:0] _T_241; // @[AXISAndLenStream.scala 161:49:@363.4]
  ElasticBufferAXISAndLenStream ElasticBufferAXISAndLenStream ( // @[AXISAndLenStream.scala 8:23:@221.4]
    .clock(ElasticBufferAXISAndLenStream_clock),
    .reset(ElasticBufferAXISAndLenStream_reset),
    .io_in_ready(ElasticBufferAXISAndLenStream_io_in_ready),
    .io_in_valid(ElasticBufferAXISAndLenStream_io_in_valid),
    .io_in_bits(ElasticBufferAXISAndLenStream_io_in_bits),
    .io_out_ready(ElasticBufferAXISAndLenStream_io_out_ready),
    .io_out_valid(ElasticBufferAXISAndLenStream_io_out_valid),
    .io_out_bits(ElasticBufferAXISAndLenStream_io_out_bits)
  );
  ElasticBufferAXISAndLenStream ElasticBufferAXISAndLenStream_1 ( // @[AXISAndLenStream.scala 8:23:@237.4]
    .clock(ElasticBufferAXISAndLenStream_1_clock),
    .reset(ElasticBufferAXISAndLenStream_1_reset),
    .io_in_ready(ElasticBufferAXISAndLenStream_1_io_in_ready),
    .io_in_valid(ElasticBufferAXISAndLenStream_1_io_in_valid),
    .io_in_bits(ElasticBufferAXISAndLenStream_1_io_in_bits),
    .io_out_ready(ElasticBufferAXISAndLenStream_1_io_out_ready),
    .io_out_valid(ElasticBufferAXISAndLenStream_1_io_out_valid),
    .io_out_bits(ElasticBufferAXISAndLenStream_1_io_out_bits)
  );
  ElasticBufferAXISAndLenStream_2 ElasticBufferAXISAndLenStream_2 ( // @[AXISAndLenStream.scala 8:23:@243.4]
    .clock(ElasticBufferAXISAndLenStream_2_clock),
    .reset(ElasticBufferAXISAndLenStream_2_reset),
    .io_in_ready(ElasticBufferAXISAndLenStream_2_io_in_ready),
    .io_in_valid(ElasticBufferAXISAndLenStream_2_io_in_valid),
    .io_in_bits(ElasticBufferAXISAndLenStream_2_io_in_bits),
    .io_out_ready(ElasticBufferAXISAndLenStream_2_io_out_ready),
    .io_out_valid(ElasticBufferAXISAndLenStream_2_io_out_valid),
    .io_out_bits(ElasticBufferAXISAndLenStream_2_io_out_bits)
  );
  ElasticBufferAXISAndLenStream_3 ElasticBufferAXISAndLenStream_3 ( // @[AXISAndLenStream.scala 8:23:@249.4]
    .clock(ElasticBufferAXISAndLenStream_3_clock),
    .reset(ElasticBufferAXISAndLenStream_3_reset),
    .io_in_ready(ElasticBufferAXISAndLenStream_3_io_in_ready),
    .io_in_valid(ElasticBufferAXISAndLenStream_3_io_in_valid),
    .io_in_bits(ElasticBufferAXISAndLenStream_3_io_in_bits),
    .io_out_ready(ElasticBufferAXISAndLenStream_3_io_out_ready),
    .io_out_valid(ElasticBufferAXISAndLenStream_3_io_out_valid),
    .io_out_bits(ElasticBufferAXISAndLenStream_3_io_out_bits)
  );
  assign _T_107 = state == 1'h0; // @[AXISAndLenStream.scala 88:54:@228.4]
  assign _T_111 = 2'h3 == ElasticBufferAXISAndLenStream_io_out_bits; // @[Mux.scala 46:19:@229.4]
  assign _T_112 = _T_111 ? regs_2 : {{31'd0}, _T_107}; // @[Mux.scala 46:16:@230.4]
  assign _T_113 = 2'h2 == ElasticBufferAXISAndLenStream_io_out_bits; // @[Mux.scala 46:19:@231.4]
  assign _T_114 = _T_113 ? regs_1 : _T_112; // @[Mux.scala 46:16:@232.4]
  assign _T_115 = 2'h1 == ElasticBufferAXISAndLenStream_io_out_bits; // @[Mux.scala 46:19:@233.4]
  assign _T_120 = ElasticBufferAXISAndLenStream_1_io_out_valid & ElasticBufferAXISAndLenStream_2_io_out_valid; // @[AXISAndLenStream.scala 94:46:@255.4]
  assign wrAddrDataAvailable = _T_120 & ElasticBufferAXISAndLenStream_3_io_out_valid; // @[AXISAndLenStream.scala 94:63:@256.4]
  assign _T_124 = ElasticBufferAXISAndLenStream_1_io_out_bits == 2'h0; // @[AXISAndLenStream.scala 102:26:@263.6]
  assign _T_125 = ~ ElasticBufferAXISAndLenStream_3_io_out_bits; // @[AXISAndLenStream.scala 102:50:@264.6]
  assign _T_127 = _T_125 == 4'h0; // @[AXISAndLenStream.scala 102:50:@265.6]
  assign _T_128 = _T_124 & _T_127; // @[AXISAndLenStream.scala 102:34:@266.6]
  assign _T_129 = ElasticBufferAXISAndLenStream_2_io_out_bits[0]; // @[AXISAndLenStream.scala 103:27:@268.8]
  assign _GEN_1 = _T_128 ? _T_129 : 1'h0; // @[AXISAndLenStream.scala 102:56:@267.6]
  assign start = wrAddrDataAvailable ? _GEN_1 : 1'h0; // @[AXISAndLenStream.scala 101:31:@262.4]
  assign _T_135 = ElasticBufferAXISAndLenStream_1_io_out_bits == 2'h1; // @[AXISAndLenStream.scala 111:49:@276.4]
  assign _T_136 = wrAddrDataAvailable & _T_135; // @[AXISAndLenStream.scala 111:32:@277.4]
  assign _T_140 = _T_136 & _T_127; // @[AXISAndLenStream.scala 111:58:@280.4]
  assign _T_142 = ElasticBufferAXISAndLenStream_1_io_out_bits == 2'h2; // @[AXISAndLenStream.scala 111:49:@284.4]
  assign _T_143 = wrAddrDataAvailable & _T_142; // @[AXISAndLenStream.scala 111:32:@285.4]
  assign _T_147 = _T_143 & _T_127; // @[AXISAndLenStream.scala 111:58:@288.4]
  assign _T_149 = ElasticBufferAXISAndLenStream_1_io_out_bits == 2'h3; // @[AXISAndLenStream.scala 111:49:@292.4]
  assign _T_150 = wrAddrDataAvailable & _T_149; // @[AXISAndLenStream.scala 111:32:@293.4]
  assign _T_154 = _T_150 & _T_127; // @[AXISAndLenStream.scala 111:58:@296.4]
  assign _T_159 = 1'h0 == state; // @[Conditional.scala 37:30:@307.4]
  assign _GEN_7 = start ? 1'h1 : state; // @[AXISAndLenStream.scala 127:21:@309.6]
  assign _GEN_9 = io_done ? 1'h0 : state; // @[AXISAndLenStream.scala 136:23:@320.8]
  assign _GEN_11 = state ? _GEN_9 : state; // @[Conditional.scala 39:67:@318.6]
  assign _GEN_12 = _T_159 ? _GEN_7 : _GEN_11; // @[Conditional.scala 40:58:@308.4]
  assign _T_227 = ~ delayedRowPtr_0_valid; // @[AXISAndLenStream.scala 148:51:@333.4]
  assign _T_228 = io_lenStream_ready | _T_227; // @[AXISAndLenStream.scala 148:49:@334.4]
  assign _T_229 = ~ delayedRowPtr_1_valid; // @[AXISAndLenStream.scala 148:77:@335.4]
  assign _T_230 = _T_228 | _T_229; // @[AXISAndLenStream.scala 148:75:@336.4]
  assign _GEN_15 = _T_230 ? io_rowPtrStream_bits : delayedRowPtr_0_bits; // @[AXISAndLenStream.scala 151:89:@346.6]
  assign _GEN_16 = _T_230 ? io_rowPtrStream_valid : delayedRowPtr_0_valid; // @[AXISAndLenStream.scala 151:89:@346.6]
  assign _GEN_17 = io_done ? 1'h0 : _GEN_16; // @[AXISAndLenStream.scala 149:19:@338.4]
  assign _GEN_18 = io_done ? delayedRowPtr_0_bits : _GEN_15; // @[AXISAndLenStream.scala 149:19:@338.4]
  assign _T_238 = io_lenStream_ready | _T_229; // @[AXISAndLenStream.scala 157:36:@355.6]
  assign _GEN_19 = _T_238 ? delayedRowPtr_0_bits : delayedRowPtr_1_bits; // @[AXISAndLenStream.scala 157:63:@356.6]
  assign _GEN_20 = _T_238 ? delayedRowPtr_0_valid : delayedRowPtr_1_valid; // @[AXISAndLenStream.scala 157:63:@356.6]
  assign _GEN_21 = io_done ? 1'h0 : _GEN_20; // @[AXISAndLenStream.scala 155:19:@350.4]
  assign _GEN_22 = io_done ? delayedRowPtr_1_bits : _GEN_19; // @[AXISAndLenStream.scala 155:19:@350.4]
  assign _T_240 = delayedRowPtr_0_bits - delayedRowPtr_1_bits; // @[AXISAndLenStream.scala 161:49:@362.4]
  assign _T_241 = $unsigned(_T_240); // @[AXISAndLenStream.scala 161:49:@363.4]
  assign io_rdAddr_ready = ElasticBufferAXISAndLenStream_io_in_ready; // @[AXISAndLenStream.scala 9:17:@226.4]
  assign io_rdData_valid = ElasticBufferAXISAndLenStream_io_out_valid; // @[AXISAndLenStream.scala 89:21:@236.4]
  assign io_rdData_bits = _T_115 ? regs_0 : _T_114; // @[AXISAndLenStream.scala 88:20:@235.4]
  assign io_wrAddr_ready = ElasticBufferAXISAndLenStream_1_io_in_ready; // @[AXISAndLenStream.scala 9:17:@242.4]
  assign io_wrData_ready = ElasticBufferAXISAndLenStream_2_io_in_ready; // @[AXISAndLenStream.scala 9:17:@248.4]
  assign io_wrAck = _T_120 & ElasticBufferAXISAndLenStream_3_io_out_valid; // @[AXISAndLenStream.scala 100:14:@261.4 AXISAndLenStream.scala 107:16:@274.6]
  assign io_offset_valid = _T_159 ? start : 1'h0; // @[AXISAndLenStream.scala 121:21:@304.4 AXISAndLenStream.scala 129:27:@311.8]
  assign io_offset_bits = regs_2; // @[AXISAndLenStream.scala 118:24:@302.4]
  assign io_nnz_valid = _T_159 ? start : 1'h0; // @[AXISAndLenStream.scala 122:18:@305.4 AXISAndLenStream.scala 130:24:@312.8]
  assign io_nnz_bits = regs_0; // @[AXISAndLenStream.scala 116:24:@300.4]
  assign io_outputSize_valid = _T_159 ? start : 1'h0; // @[AXISAndLenStream.scala 123:25:@306.4 AXISAndLenStream.scala 131:31:@313.8]
  assign io_outputSize_bits = regs_1; // @[AXISAndLenStream.scala 117:24:@301.4]
  assign io_running = _T_159 ? 1'h0 : state; // @[AXISAndLenStream.scala 81:16:@218.4 AXISAndLenStream.scala 120:16:@303.4 AXISAndLenStream.scala 135:20:@319.8]
  assign io_rowPtrStream_ready = _T_228 | _T_229; // @[AXISAndLenStream.scala 148:27:@337.4]
  assign io_lenStream_valid = delayedRowPtr_1_valid & delayedRowPtr_0_valid; // @[AXISAndLenStream.scala 160:24:@361.4]
  assign io_lenStream_bits = _T_241[31:0]; // @[AXISAndLenStream.scala 161:24:@365.4]
  assign io_wstrb_ready = ElasticBufferAXISAndLenStream_3_io_in_ready; // @[AXISAndLenStream.scala 9:17:@254.4]
  assign ElasticBufferAXISAndLenStream_clock = clock; // @[:@222.4]
  assign ElasticBufferAXISAndLenStream_reset = reset; // @[:@223.4]
  assign ElasticBufferAXISAndLenStream_io_in_valid = io_rdAddr_valid; // @[AXISAndLenStream.scala 9:17:@225.4]
  assign ElasticBufferAXISAndLenStream_io_in_bits = io_rdAddr_bits; // @[AXISAndLenStream.scala 9:17:@224.4]
  assign ElasticBufferAXISAndLenStream_io_out_ready = io_rdData_ready; // @[AXISAndLenStream.scala 87:20:@227.4]
  assign ElasticBufferAXISAndLenStream_1_clock = clock; // @[:@238.4]
  assign ElasticBufferAXISAndLenStream_1_reset = reset; // @[:@239.4]
  assign ElasticBufferAXISAndLenStream_1_io_in_valid = io_wrAddr_valid; // @[AXISAndLenStream.scala 9:17:@241.4]
  assign ElasticBufferAXISAndLenStream_1_io_in_bits = io_wrAddr_bits; // @[AXISAndLenStream.scala 9:17:@240.4]
  assign ElasticBufferAXISAndLenStream_1_io_out_ready = ElasticBufferAXISAndLenStream_2_io_out_valid; // @[AXISAndLenStream.scala 95:20:@257.4]
  assign ElasticBufferAXISAndLenStream_2_clock = clock; // @[:@244.4]
  assign ElasticBufferAXISAndLenStream_2_reset = reset; // @[:@245.4]
  assign ElasticBufferAXISAndLenStream_2_io_in_valid = io_wrData_valid; // @[AXISAndLenStream.scala 9:17:@247.4]
  assign ElasticBufferAXISAndLenStream_2_io_in_bits = io_wrData_bits; // @[AXISAndLenStream.scala 9:17:@246.4]
  assign ElasticBufferAXISAndLenStream_2_io_out_ready = ElasticBufferAXISAndLenStream_1_io_out_valid; // @[AXISAndLenStream.scala 96:20:@258.4]
  assign ElasticBufferAXISAndLenStream_3_clock = clock; // @[:@250.4]
  assign ElasticBufferAXISAndLenStream_3_reset = reset; // @[:@251.4]
  assign ElasticBufferAXISAndLenStream_3_io_in_valid = io_wstrb_valid; // @[AXISAndLenStream.scala 9:17:@253.4]
  assign ElasticBufferAXISAndLenStream_3_io_in_bits = io_wstrb_bits; // @[AXISAndLenStream.scala 9:17:@252.4]
  assign ElasticBufferAXISAndLenStream_3_io_out_ready = ElasticBufferAXISAndLenStream_1_io_out_valid; // @[AXISAndLenStream.scala 97:19:@259.4]
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
  state = _RAND_0[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_1 = {1{`RANDOM}};
  regs_0 = _RAND_1[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_2 = {1{`RANDOM}};
  regs_1 = _RAND_2[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_3 = {1{`RANDOM}};
  regs_2 = _RAND_3[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_4 = {1{`RANDOM}};
  delayedRowPtr_0_valid = _RAND_4[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_5 = {1{`RANDOM}};
  delayedRowPtr_0_bits = _RAND_5[31:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_6 = {1{`RANDOM}};
  delayedRowPtr_1_valid = _RAND_6[0:0];
  `endif // RANDOMIZE_REG_INIT
  `ifdef RANDOMIZE_REG_INIT
  _RAND_7 = {1{`RANDOM}};
  delayedRowPtr_1_bits = _RAND_7[31:0];
  `endif // RANDOMIZE_REG_INIT
  end
`endif // RANDOMIZE
  always @(posedge clock) begin
    if (reset) begin
      state <= 1'h0;
    end else begin
      if (_T_159) begin
        if (start) begin
          state <= 1'h1;
        end
      end else begin
        if (state) begin
          if (io_done) begin
            state <= 1'h0;
          end
        end
      end
    end
    if (_T_140) begin
      regs_0 <= ElasticBufferAXISAndLenStream_2_io_out_bits;
    end
    if (_T_147) begin
      regs_1 <= ElasticBufferAXISAndLenStream_2_io_out_bits;
    end
    if (_T_154) begin
      regs_2 <= ElasticBufferAXISAndLenStream_2_io_out_bits;
    end
    if (reset) begin
      delayedRowPtr_0_valid <= 1'h0;
    end else begin
      if (io_done) begin
        delayedRowPtr_0_valid <= 1'h0;
      end else begin
        if (_T_230) begin
          delayedRowPtr_0_valid <= io_rowPtrStream_valid;
        end
      end
    end
    if (reset) begin
      delayedRowPtr_0_bits <= 32'h0;
    end else begin
      if (!(io_done)) begin
        if (_T_230) begin
          delayedRowPtr_0_bits <= io_rowPtrStream_bits;
        end
      end
    end
    if (reset) begin
      delayedRowPtr_1_valid <= 1'h0;
    end else begin
      if (io_done) begin
        delayedRowPtr_1_valid <= 1'h0;
      end else begin
        if (_T_238) begin
          delayedRowPtr_1_valid <= delayedRowPtr_0_valid;
        end
      end
    end
    if (reset) begin
      delayedRowPtr_1_bits <= 32'h0;
    end else begin
      if (!(io_done)) begin
        if (_T_238) begin
          delayedRowPtr_1_bits <= delayedRowPtr_0_bits;
        end
      end
    end
  end
endmodule
