/* True dual port bram. One port is no change with 1 cycle delay for reads,
 * the other is read first with no cycles delay for both reads and writes.
 */

module XilinxTDPReadFirstByteWriteBRAM #(
	parameter RAM_WIDTH = 18,               // Specify RAM data width
	parameter RAM_DEPTH = 1024,             // Specify RAM depth (number of entries)
	parameter BYTE_WIDTH = RAM_WIDTH,
	parameter INIT_FILE = ""                // Specify name/location of RAM initialization file if using one (leave blank if not)
) (
	input [clogb2(RAM_DEPTH-1)-1:0] addra,  // Port A address bus, width determined from RAM_DEPTH
	input [clogb2(RAM_DEPTH-1)-1:0] addrb,  // Port B address bus, width determined from RAM_DEPTH
	input [RAM_WIDTH-1:0] dina,             // Port A RAM input data
	input [RAM_WIDTH-1:0] dinb,             // Port B RAM input data
	input clock,                            // Clock
	input [(RAM_WIDTH/BYTE_WIDTH)-1:0] wea, // Port A write enable
	input [(RAM_WIDTH/BYTE_WIDTH)-1:0] web, // Port B write enable
	input ena,                              // Port A RAM Enable, for additional power savings, disable port when not in use
	input enb,                              // Port B RAM Enable, for additional power savings, disable port when not in use
	input reset,                            // Port A and B output reset (does not affect memory contents)
	input regcea,                           // Port A output register enable
	input regceb,                           // Port B output register enable
	output [RAM_WIDTH-1:0] douta,           // Port A RAM output data
	output [RAM_WIDTH-1:0] doutb            // Port B RAM output data
);

	reg [RAM_WIDTH-1:0] bram [RAM_DEPTH-1:0];
	reg [RAM_WIDTH-1:0] ram_data_a = {RAM_WIDTH{1'b0}};
	reg [RAM_WIDTH-1:0] ram_data_b = {RAM_WIDTH{1'b0}};

	// init
	generate
		if (INIT_FILE != "") begin: use_init_file
			initial
				$readmemh(INIT_FILE, bram, 0, RAM_DEPTH-1);
		end else begin: init_bram_to_zero
			integer ram_index;
			initial
				for (ram_index = 0; ram_index < RAM_DEPTH; ram_index = ram_index + 1)
					bram[ram_index] = {RAM_WIDTH{1'b0}};
		end
	endgenerate

	// port A
	for (genvar i = 0; i < RAM_WIDTH / BYTE_WIDTH; i = i + 1) begin
		always @(posedge clock) begin
			if (ena) begin
				if (wea[i])
					bram[addra][(i+1)*BYTE_WIDTH-1:i*BYTE_WIDTH] <= dina[(i+1)*BYTE_WIDTH-1:i*BYTE_WIDTH];
			end
		end
	end

	always @(posedge clock) begin
		if (ena) begin
			ram_data_a <= bram[addra];
		end
	end

	reg [RAM_WIDTH-1:0] douta_reg = {RAM_WIDTH{1'b0}};
	assign douta = douta_reg;

	always @(posedge clock) begin
		if (reset)
			douta_reg <= {RAM_WIDTH{1'b0}};
		else if (regcea)
			douta_reg <= ram_data_a;
	end

	// port B
	for (genvar i=0; i<RAM_WIDTH/BYTE_WIDTH; i=i+1) begin
		always @(posedge clock) begin
			if (enb) begin
				if (web[i])
					bram[addrb][(i+1)*BYTE_WIDTH-1:i*BYTE_WIDTH] <= dinb[(i+1)*BYTE_WIDTH-1:i*BYTE_WIDTH];
			end
		end
	end

	always @(posedge clock) begin
		if (enb) begin
			ram_data_b <= bram[addrb];
		end
	end

	reg [RAM_WIDTH-1:0] doutb_reg = {RAM_WIDTH{1'b0}};
	assign doutb = doutb_reg;

	always @(posedge clock) begin
		if (reset)
			doutb_reg <= {RAM_WIDTH{1'b0}};
		else if (regceb)
			doutb_reg <= ram_data_b;
	end

	//  The following function calculates the address width based on specified RAM depth
	function integer clogb2;
		input integer depth;
			for (clogb2=0; depth>0; clogb2=clogb2+1)
				depth = depth >> 1;
	endfunction

endmodule
