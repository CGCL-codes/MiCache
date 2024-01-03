source "params.tcl"

# Check file required for this script exists
proc checkRequiredFiles { origin_dir} {
  set status true
  set files [list \
   "$origin_dir/constraint.xdc" \
  ]
  foreach ifile $files {
    if { ![file isfile $ifile] } {
      puts " Could not find local file $ifile "
      set status false
    }
  }

  set paths [list \
   [file normalize "$origin_dir/../ip"] \
   [file normalize "$origin_dir/../../spmv"] \
  ]
  foreach ipath $paths {
    if { ![file isdirectory $ipath] } {
      puts " Could not access $ipath "
      set status false
    }
  }

  return $status
}
# Set the reference directory for source file relative paths (by default the value is script directory path)
set origin_dir "."

# Use origin directory path location variable, if specified in the tcl shell
if { [info exists ::origin_dir_loc] } {
  set origin_dir $::origin_dir_loc
}

# Set the project name
set _xil_proj_name_ "u280_cache_test"

# Use project name variable, if specified in the tcl shell
if { [info exists ::user_project_name] } {
  set _xil_proj_name_ $::user_project_name
}

variable script_file
set script_file "u280_cache_test.tcl"

# Help information for this script
proc print_help {} {
  variable script_file
  puts "\nDescription:"
  puts "Recreate a Vivado project from this script. The created project will be"
  puts "functionally equivalent to the original project for which this script was"
  puts "generated. The script contains commands for creating a project, filesets,"
  puts "runs, adding/importing sources and setting properties on various objects.\n"
  puts "Syntax:"
  puts "$script_file"
  puts "$script_file -tclargs \[--origin_dir <path>\]"
  puts "$script_file -tclargs \[--project_name <name>\]"
  puts "$script_file -tclargs \[--help\]\n"
  puts "Usage:"
  puts "Name                   Description"
  puts "-------------------------------------------------------------------------"
  puts "\[--origin_dir <path>\]  Determine source file paths wrt this path. Default"
  puts "                       origin_dir path value is \".\", otherwise, the value"
  puts "                       that was set with the \"-paths_relative_to\" switch"
  puts "                       when this script was generated.\n"
  puts "\[--project_name <name>\] Create project with the specified name. Default"
  puts "                       name is the name of the project from where this"
  puts "                       script was generated.\n"
  puts "\[--help\]               Print help information for this script"
  puts "-------------------------------------------------------------------------\n"
  exit 0
}

if { $::argc > 0 } {
  for {set i 0} {$i < $::argc} {incr i} {
    set option [string trim [lindex $::argv $i]]
    switch -regexp -- $option {
      "--origin_dir"   { incr i; set origin_dir [lindex $::argv $i] }
      "--project_name" { incr i; set _xil_proj_name_ [lindex $::argv $i] }
      "--help"         { print_help }
      default {
        if { [regexp {^-} $option] } {
          puts "ERROR: Unknown option '$option' specified, please type '$script_file -tclargs --help' for usage info.\n"
          return 1
        }
      }
    }
  }
}

# Check for paths and files needed for project creation
set validate_required 0
if { $validate_required } {
  if { [checkRequiredFiles $origin_dir] } {
    puts "Tcl file $script_file is valid. All files required for project creation is accesable. "
  } else {
    puts "Tcl file $script_file is not valid. Not all files required for project creation is accesable. "
    return
  }
}

# Create project
create_project ${_xil_proj_name_} ./${_xil_proj_name_} -part xcu280-fsvh2892-2L-e

# Set the directory path for the new project
set proj_dir [get_property directory [current_project]]

# Set project properties
set obj [current_project]
set_property -name "board_part" -value "xilinx.com:au280:part0:1.1" -objects $obj
set_property -name "default_lib" -value "xil_defaultlib" -objects $obj
set_property -name "enable_vhdl_2008" -value "1" -objects $obj
set_property -name "ip_cache_permissions" -value "read write" -objects $obj
set_property -name "ip_output_repo" -value "$proj_dir/${_xil_proj_name_}.cache/ip" -objects $obj
set_property -name "mem.enable_memory_map_generation" -value "1" -objects $obj
set_property -name "platform.board_id" -value "au280" -objects $obj
set_property -name "sim.central_dir" -value "$proj_dir/${_xil_proj_name_}.ip_user_files" -objects $obj
set_property -name "sim.ip.auto_export_scripts" -value "1" -objects $obj
set_property -name "simulator_language" -value "Mixed" -objects $obj
set_property -name "webtalk.activehdl_export_sim" -value "3" -objects $obj
set_property -name "webtalk.ies_export_sim" -value "3" -objects $obj
set_property -name "webtalk.modelsim_export_sim" -value "3" -objects $obj
set_property -name "webtalk.questa_export_sim" -value "3" -objects $obj
set_property -name "webtalk.riviera_export_sim" -value "3" -objects $obj
set_property -name "webtalk.vcs_export_sim" -value "3" -objects $obj
set_property -name "webtalk.xsim_export_sim" -value "3" -objects $obj
set_property -name "xpm_libraries" -value "XPM_CDC XPM_FIFO XPM_MEMORY" -objects $obj

# Create 'sources_1' fileset (if not found)
if {[string equal [get_filesets -quiet sources_1] ""]} {
  create_fileset -srcset sources_1
}

# Set IP repository paths
set obj [get_filesets sources_1]
if { $obj != {} } {
   set_property "ip_repo_paths" "[file normalize "$origin_dir/../ip"] [file normalize "$origin_dir/../../spmv"]" $obj

   # Rebuild user ip_repo's index before adding any source files
   update_ip_catalog -rebuild
}

# Set 'sources_1' fileset object
set obj [get_filesets sources_1]
# Set 'sources_1' fileset file properties for remote files
# None

# Set 'sources_1' fileset file properties for local files
# None

# Set 'sources_1' fileset properties
set obj [get_filesets sources_1]
set_property -name "top" -value "design_1_wrapper" -objects $obj

# Create 'constrs_1' fileset (if not found)
if {[string equal [get_filesets -quiet constrs_1] ""]} {
  create_fileset -constrset constrs_1
}

# Set 'constrs_1' fileset object
set obj [get_filesets constrs_1]

# Add/Import constrs file and set constrs file properties
set file "[file normalize "$origin_dir/constraint.xdc"]"
set file_imported [import_files -fileset constrs_1 [list $file]]
set file "constraint.xdc"
set file_obj [get_files -of_objects [get_filesets constrs_1] [list "*$file"]]
set_property -name "file_type" -value "XDC" -objects $file_obj

# Set 'constrs_1' fileset properties
set obj [get_filesets constrs_1]
set_property -name "target_constrs_file" -value "[get_files *constraint.xdc]" -objects $obj
set_property -name "target_ucf" -value "[get_files *constraint.xdc]" -objects $obj

# Create 'sim_1' fileset (if not found)
if {[string equal [get_filesets -quiet sim_1] ""]} {
  create_fileset -simset sim_1
}

# Set 'sim_1' fileset object
set obj [get_filesets sim_1]
# Empty (no sources present)

# Set 'sim_1' fileset properties
set obj [get_filesets sim_1]
set_property -name "hbs.configure_design_for_hier_access" -value "1" -objects $obj
set_property -name "top" -value "design_1_wrapper" -objects $obj
set_property -name "top_lib" -value "xil_defaultlib" -objects $obj

# Set 'utils_1' fileset object
set obj [get_filesets utils_1]
# Empty (no sources present)

# Set 'utils_1' fileset properties
set obj [get_filesets utils_1]


# Adding sources referenced in BDs, if not already added


# Proc to create BD design_1
proc cr_bd_design_1 { parentCell } {
  global cache_name
  # CHANGE DESIGN NAME HERE
  set design_name design_1

  common::send_gid_msg -ssname BD::TCL -id 2010 -severity "INFO" "Currently there is no design <$design_name> in project, so creating one..."

  create_bd_design $design_name

  set bCheckIPsPassed 1
  ##################################################################
  # CHECK IPs
  ##################################################################
  set bCheckIPs 1
  if { $bCheckIPs == 1 } {
     set list_check_ips "\ 
  CGCL:MiCache:${cache_name}:1.0\
  xilinx.com:ip:clk_wiz:6.0\
  xilinx.com:ip:ddr4:2.2\
  xilinx.com:ip:hbm:1.0\
  xilinx.com:ip:proc_sys_reset:5.0\
  xilinx.com:ip:qdma:4.0\
  xilinx.com:ip:util_vector_logic:2.0\
  xilinx.com:ip:smartconnect:1.0\
  xilinx.com:ip:util_ds_buf:2.1\
  xilinx.com:ip:xlconstant:1.1\
  user.org:user:FullyPipelinedSpMV:1.3\
  xilinx.com:ip:axi_dma:7.1\
  "

   set list_ips_missing ""
   common::send_gid_msg -ssname BD::TCL -id 2011 -severity "INFO" "Checking if the following IPs exist in the project's IP catalog: $list_check_ips ."

   foreach ip_vlnv $list_check_ips {
      set ip_obj [get_ipdefs -all $ip_vlnv]
      if { $ip_obj eq "" } {
         lappend list_ips_missing $ip_vlnv
      }
   }

   if { $list_ips_missing ne "" } {
      catch {common::send_gid_msg -ssname BD::TCL -id 2012 -severity "ERROR" "The following IPs are not found in the IP Catalog:\n  $list_ips_missing\n\nResolution: Please add the repository containing the IP(s) to the project." }
      set bCheckIPsPassed 0
   }

  }

  if { $bCheckIPsPassed != 1 } {
    common::send_gid_msg -ssname BD::TCL -id 2023 -severity "WARNING" "Will not continue with creation of design due to the error(s) above."
    return 3
  }

  
# Hierarchical cell: hier_3
proc create_hier_cell_hier_3 { parentCell nameHier } {

  variable script_folder

  if { $parentCell eq "" || $nameHier eq "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2092 -severity "ERROR" "create_hier_cell_hier_3() - Empty argument(s)!"}
     return
  }

  # Get object for parentCell
  set parentObj [get_bd_cells $parentCell]
  if { $parentObj == "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2090 -severity "ERROR" "Unable to find parent cell <$parentCell>!"}
     return
  }

  # Make sure parentObj is hier blk
  set parentType [get_property TYPE $parentObj]
  if { $parentType ne "hier" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2091 -severity "ERROR" "Parent <$parentObj> has TYPE = <$parentType>. Expected to be <hier>."}
     return
  }

  # Save current instance; Restore later
  set oldCurInst [current_bd_instance .]

  # Set parent object as current
  current_bd_instance $parentObj

  # Create cell and set as current instance
  set hier_obj [create_bd_cell -type hier $nameHier]
  current_bd_instance $hier_obj

  # Create interface pins
  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S1

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S2

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_S2MM

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE1

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE2

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE3

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 m_axi_vect

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 s_axi_AXILiteS


  # Create pins
  create_bd_pin -dir I -type rst axi_resetn
  create_bd_pin -dir O done
  create_bd_pin -dir I -type clk m_axi_mm2s_aclk
  create_bd_pin -dir O running
  create_bd_pin -dir I running_all

  # Create instance: FullyPipelinedSpMV_0, and set properties
  set FullyPipelinedSpMV_0 [ create_bd_cell -type ip -vlnv user.org:user:FullyPipelinedSpMV:1.3 FullyPipelinedSpMV_0 ]

  # Create instance: axi_dma_0, and set properties
  set axi_dma_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_0 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_m_axi_s2mm_data_width {32} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_s2mm_burst_size {16} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_0

  # Create instance: axi_dma_1, and set properties
  set axi_dma_1 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_1 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_1

  # Create instance: axi_dma_2, and set properties
  set axi_dma_2 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_2 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_2

  # Create instance: axi_dma_3, and set properties
  set axi_dma_3 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_3 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s {0} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {1} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {32} \
   CONFIG.c_m_axi_s2mm_data_width {512} \
   CONFIG.c_mm2s_burst_size {16} \
   CONFIG.c_s2mm_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_3

  # Create interface connections
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_0_m_axi_vect [get_bd_intf_pins m_axi_vect] [get_bd_intf_pins FullyPipelinedSpMV_0/m_axi_vect]
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_0_output_stream [get_bd_intf_pins FullyPipelinedSpMV_0/output_stream] [get_bd_intf_pins axi_dma_3/S_AXIS_S2MM]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/rowptr_stream] [get_bd_intf_pins axi_dma_0/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S] [get_bd_intf_pins axi_dma_0/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/col_ind_stream] [get_bd_intf_pins axi_dma_1/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S1] [get_bd_intf_pins axi_dma_1/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/val_stream] [get_bd_intf_pins axi_dma_2/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S2] [get_bd_intf_pins axi_dma_2/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_3_M_AXI_S2MM [get_bd_intf_pins M_AXI_S2MM] [get_bd_intf_pins axi_dma_3/M_AXI_S2MM]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M00_AXI [get_bd_intf_pins S_AXI_LITE] [get_bd_intf_pins axi_dma_0/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M01_AXI [get_bd_intf_pins S_AXI_LITE1] [get_bd_intf_pins axi_dma_1/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M02_AXI [get_bd_intf_pins S_AXI_LITE2] [get_bd_intf_pins axi_dma_2/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M06_AXI [get_bd_intf_pins S_AXI_LITE3] [get_bd_intf_pins axi_dma_3/S_AXI_LITE]
  connect_bd_intf_net -intf_net s_axi_AXILiteS_1 [get_bd_intf_pins s_axi_AXILiteS] [get_bd_intf_pins FullyPipelinedSpMV_0/s_axi_AXILiteS]

  # Create port connections
  connect_bd_net -net FullyPipelinedSpMV_0_done [get_bd_pins done] [get_bd_pins FullyPipelinedSpMV_0/done]
  connect_bd_net -net FullyPipelinedSpMV_0_running [get_bd_pins running] [get_bd_pins FullyPipelinedSpMV_0/running]
  connect_bd_net -net clk_wiz_2_clk_out1 [get_bd_pins m_axi_mm2s_aclk] [get_bd_pins FullyPipelinedSpMV_0/ap_clk] [get_bd_pins axi_dma_0/m_axi_mm2s_aclk] [get_bd_pins axi_dma_0/s_axi_lite_aclk] [get_bd_pins axi_dma_1/m_axi_mm2s_aclk] [get_bd_pins axi_dma_1/s_axi_lite_aclk] [get_bd_pins axi_dma_2/m_axi_mm2s_aclk] [get_bd_pins axi_dma_2/s_axi_lite_aclk] [get_bd_pins axi_dma_3/m_axi_s2mm_aclk] [get_bd_pins axi_dma_3/s_axi_lite_aclk]
  connect_bd_net -net rst_clk_wiz_1_100M_peripheral_aresetn [get_bd_pins axi_resetn] [get_bd_pins FullyPipelinedSpMV_0/ap_rst_n] [get_bd_pins axi_dma_0/axi_resetn] [get_bd_pins axi_dma_1/axi_resetn] [get_bd_pins axi_dma_2/axi_resetn] [get_bd_pins axi_dma_3/axi_resetn]
  connect_bd_net -net running_all_1 [get_bd_pins running_all] [get_bd_pins FullyPipelinedSpMV_0/running_all]

  # Restore current instance
  current_bd_instance $oldCurInst
}
  
# Hierarchical cell: hier_2
proc create_hier_cell_hier_2 { parentCell nameHier } {

  variable script_folder

  if { $parentCell eq "" || $nameHier eq "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2092 -severity "ERROR" "create_hier_cell_hier_2() - Empty argument(s)!"}
     return
  }

  # Get object for parentCell
  set parentObj [get_bd_cells $parentCell]
  if { $parentObj == "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2090 -severity "ERROR" "Unable to find parent cell <$parentCell>!"}
     return
  }

  # Make sure parentObj is hier blk
  set parentType [get_property TYPE $parentObj]
  if { $parentType ne "hier" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2091 -severity "ERROR" "Parent <$parentObj> has TYPE = <$parentType>. Expected to be <hier>."}
     return
  }

  # Save current instance; Restore later
  set oldCurInst [current_bd_instance .]

  # Set parent object as current
  current_bd_instance $parentObj

  # Create cell and set as current instance
  set hier_obj [create_bd_cell -type hier $nameHier]
  current_bd_instance $hier_obj

  # Create interface pins
  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S1

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S2

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_S2MM

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE1

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE2

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE3

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 m_axi_vect

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 s_axi_AXILiteS


  # Create pins
  create_bd_pin -dir I -type rst axi_resetn
  create_bd_pin -dir O done
  create_bd_pin -dir I -type clk m_axi_mm2s_aclk
  create_bd_pin -dir O running
  create_bd_pin -dir I running_all

  # Create instance: FullyPipelinedSpMV_0, and set properties
  set FullyPipelinedSpMV_0 [ create_bd_cell -type ip -vlnv user.org:user:FullyPipelinedSpMV:1.3 FullyPipelinedSpMV_0 ]

  # Create instance: axi_dma_0, and set properties
  set axi_dma_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_0 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_m_axi_s2mm_data_width {32} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_s2mm_burst_size {16} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_0

  # Create instance: axi_dma_1, and set properties
  set axi_dma_1 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_1 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_1

  # Create instance: axi_dma_2, and set properties
  set axi_dma_2 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_2 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_2

  # Create instance: axi_dma_3, and set properties
  set axi_dma_3 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_3 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s {0} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {1} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {32} \
   CONFIG.c_m_axi_s2mm_data_width {512} \
   CONFIG.c_mm2s_burst_size {16} \
   CONFIG.c_s2mm_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_3

  # Create interface connections
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_0_m_axi_vect [get_bd_intf_pins m_axi_vect] [get_bd_intf_pins FullyPipelinedSpMV_0/m_axi_vect]
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_0_output_stream [get_bd_intf_pins FullyPipelinedSpMV_0/output_stream] [get_bd_intf_pins axi_dma_3/S_AXIS_S2MM]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/rowptr_stream] [get_bd_intf_pins axi_dma_0/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S] [get_bd_intf_pins axi_dma_0/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/col_ind_stream] [get_bd_intf_pins axi_dma_1/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S1] [get_bd_intf_pins axi_dma_1/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/val_stream] [get_bd_intf_pins axi_dma_2/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S2] [get_bd_intf_pins axi_dma_2/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_3_M_AXI_S2MM [get_bd_intf_pins M_AXI_S2MM] [get_bd_intf_pins axi_dma_3/M_AXI_S2MM]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M00_AXI [get_bd_intf_pins S_AXI_LITE] [get_bd_intf_pins axi_dma_0/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M01_AXI [get_bd_intf_pins S_AXI_LITE1] [get_bd_intf_pins axi_dma_1/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M02_AXI [get_bd_intf_pins S_AXI_LITE2] [get_bd_intf_pins axi_dma_2/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M06_AXI [get_bd_intf_pins S_AXI_LITE3] [get_bd_intf_pins axi_dma_3/S_AXI_LITE]
  connect_bd_intf_net -intf_net s_axi_AXILiteS_1 [get_bd_intf_pins s_axi_AXILiteS] [get_bd_intf_pins FullyPipelinedSpMV_0/s_axi_AXILiteS]

  # Create port connections
  connect_bd_net -net FullyPipelinedSpMV_0_done [get_bd_pins done] [get_bd_pins FullyPipelinedSpMV_0/done]
  connect_bd_net -net FullyPipelinedSpMV_0_running [get_bd_pins running] [get_bd_pins FullyPipelinedSpMV_0/running]
  connect_bd_net -net clk_wiz_2_clk_out1 [get_bd_pins m_axi_mm2s_aclk] [get_bd_pins FullyPipelinedSpMV_0/ap_clk] [get_bd_pins axi_dma_0/m_axi_mm2s_aclk] [get_bd_pins axi_dma_0/s_axi_lite_aclk] [get_bd_pins axi_dma_1/m_axi_mm2s_aclk] [get_bd_pins axi_dma_1/s_axi_lite_aclk] [get_bd_pins axi_dma_2/m_axi_mm2s_aclk] [get_bd_pins axi_dma_2/s_axi_lite_aclk] [get_bd_pins axi_dma_3/m_axi_s2mm_aclk] [get_bd_pins axi_dma_3/s_axi_lite_aclk]
  connect_bd_net -net rst_clk_wiz_1_100M_peripheral_aresetn [get_bd_pins axi_resetn] [get_bd_pins FullyPipelinedSpMV_0/ap_rst_n] [get_bd_pins axi_dma_0/axi_resetn] [get_bd_pins axi_dma_1/axi_resetn] [get_bd_pins axi_dma_2/axi_resetn] [get_bd_pins axi_dma_3/axi_resetn]
  connect_bd_net -net running_all_1 [get_bd_pins running_all] [get_bd_pins FullyPipelinedSpMV_0/running_all]

  # Restore current instance
  current_bd_instance $oldCurInst
}
  
# Hierarchical cell: hier_1
proc create_hier_cell_hier_1 { parentCell nameHier } {

  variable script_folder

  if { $parentCell eq "" || $nameHier eq "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2092 -severity "ERROR" "create_hier_cell_hier_1() - Empty argument(s)!"}
     return
  }

  # Get object for parentCell
  set parentObj [get_bd_cells $parentCell]
  if { $parentObj == "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2090 -severity "ERROR" "Unable to find parent cell <$parentCell>!"}
     return
  }

  # Make sure parentObj is hier blk
  set parentType [get_property TYPE $parentObj]
  if { $parentType ne "hier" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2091 -severity "ERROR" "Parent <$parentObj> has TYPE = <$parentType>. Expected to be <hier>."}
     return
  }

  # Save current instance; Restore later
  set oldCurInst [current_bd_instance .]

  # Set parent object as current
  current_bd_instance $parentObj

  # Create cell and set as current instance
  set hier_obj [create_bd_cell -type hier $nameHier]
  current_bd_instance $hier_obj

  # Create interface pins
  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S1

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S2

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_S2MM

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE1

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE2

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE3

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 m_axi_vect

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 s_axi_AXILiteS


  # Create pins
  create_bd_pin -dir I -type rst axi_resetn
  create_bd_pin -dir O done
  create_bd_pin -dir I -type clk m_axi_mm2s_aclk
  create_bd_pin -dir O running
  create_bd_pin -dir I running_all

  # Create instance: FullyPipelinedSpMV_0, and set properties
  set FullyPipelinedSpMV_0 [ create_bd_cell -type ip -vlnv user.org:user:FullyPipelinedSpMV:1.3 FullyPipelinedSpMV_0 ]

  # Create instance: axi_dma_0, and set properties
  set axi_dma_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_0 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_m_axi_s2mm_data_width {32} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_s2mm_burst_size {16} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_0

  # Create instance: axi_dma_1, and set properties
  set axi_dma_1 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_1 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_1

  # Create instance: axi_dma_2, and set properties
  set axi_dma_2 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_2 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_2

  # Create instance: axi_dma_3, and set properties
  set axi_dma_3 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_3 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s {0} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {1} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {32} \
   CONFIG.c_m_axi_s2mm_data_width {512} \
   CONFIG.c_mm2s_burst_size {16} \
   CONFIG.c_s2mm_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_3

  # Create interface connections
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_0_m_axi_vect [get_bd_intf_pins m_axi_vect] [get_bd_intf_pins FullyPipelinedSpMV_0/m_axi_vect]
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_0_output_stream [get_bd_intf_pins FullyPipelinedSpMV_0/output_stream] [get_bd_intf_pins axi_dma_3/S_AXIS_S2MM]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/rowptr_stream] [get_bd_intf_pins axi_dma_0/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S] [get_bd_intf_pins axi_dma_0/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/col_ind_stream] [get_bd_intf_pins axi_dma_1/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S1] [get_bd_intf_pins axi_dma_1/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/val_stream] [get_bd_intf_pins axi_dma_2/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S2] [get_bd_intf_pins axi_dma_2/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_3_M_AXI_S2MM [get_bd_intf_pins M_AXI_S2MM] [get_bd_intf_pins axi_dma_3/M_AXI_S2MM]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M00_AXI [get_bd_intf_pins S_AXI_LITE] [get_bd_intf_pins axi_dma_0/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M01_AXI [get_bd_intf_pins S_AXI_LITE1] [get_bd_intf_pins axi_dma_1/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M02_AXI [get_bd_intf_pins S_AXI_LITE2] [get_bd_intf_pins axi_dma_2/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M06_AXI [get_bd_intf_pins S_AXI_LITE3] [get_bd_intf_pins axi_dma_3/S_AXI_LITE]
  connect_bd_intf_net -intf_net s_axi_AXILiteS_1 [get_bd_intf_pins s_axi_AXILiteS] [get_bd_intf_pins FullyPipelinedSpMV_0/s_axi_AXILiteS]

  # Create port connections
  connect_bd_net -net FullyPipelinedSpMV_0_done [get_bd_pins done] [get_bd_pins FullyPipelinedSpMV_0/done]
  connect_bd_net -net FullyPipelinedSpMV_0_running [get_bd_pins running] [get_bd_pins FullyPipelinedSpMV_0/running]
  connect_bd_net -net clk_wiz_2_clk_out1 [get_bd_pins m_axi_mm2s_aclk] [get_bd_pins FullyPipelinedSpMV_0/ap_clk] [get_bd_pins axi_dma_0/m_axi_mm2s_aclk] [get_bd_pins axi_dma_0/s_axi_lite_aclk] [get_bd_pins axi_dma_1/m_axi_mm2s_aclk] [get_bd_pins axi_dma_1/s_axi_lite_aclk] [get_bd_pins axi_dma_2/m_axi_mm2s_aclk] [get_bd_pins axi_dma_2/s_axi_lite_aclk] [get_bd_pins axi_dma_3/m_axi_s2mm_aclk] [get_bd_pins axi_dma_3/s_axi_lite_aclk]
  connect_bd_net -net rst_clk_wiz_1_100M_peripheral_aresetn [get_bd_pins axi_resetn] [get_bd_pins FullyPipelinedSpMV_0/ap_rst_n] [get_bd_pins axi_dma_0/axi_resetn] [get_bd_pins axi_dma_1/axi_resetn] [get_bd_pins axi_dma_2/axi_resetn] [get_bd_pins axi_dma_3/axi_resetn]
  connect_bd_net -net running_all_1 [get_bd_pins running_all] [get_bd_pins FullyPipelinedSpMV_0/running_all]

  # Restore current instance
  current_bd_instance $oldCurInst
}
  
# Hierarchical cell: hier_0
proc create_hier_cell_hier_0 { parentCell nameHier } {

  variable script_folder

  if { $parentCell eq "" || $nameHier eq "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2092 -severity "ERROR" "create_hier_cell_hier_0() - Empty argument(s)!"}
     return
  }

  # Get object for parentCell
  set parentObj [get_bd_cells $parentCell]
  if { $parentObj == "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2090 -severity "ERROR" "Unable to find parent cell <$parentCell>!"}
     return
  }

  # Make sure parentObj is hier blk
  set parentType [get_property TYPE $parentObj]
  if { $parentType ne "hier" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2091 -severity "ERROR" "Parent <$parentObj> has TYPE = <$parentType>. Expected to be <hier>."}
     return
  }

  # Save current instance; Restore later
  set oldCurInst [current_bd_instance .]

  # Set parent object as current
  current_bd_instance $parentObj

  # Create cell and set as current instance
  set hier_obj [create_bd_cell -type hier $nameHier]
  current_bd_instance $hier_obj

  # Create interface pins
  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S1

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_MM2S2

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 M_AXI_S2MM

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE1

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE2

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 S_AXI_LITE3

  create_bd_intf_pin -mode Master -vlnv xilinx.com:interface:aximm_rtl:1.0 m_axi_vect

  create_bd_intf_pin -mode Slave -vlnv xilinx.com:interface:aximm_rtl:1.0 s_axi_AXILiteS


  # Create pins
  create_bd_pin -dir I -type rst axi_resetn
  create_bd_pin -dir O done
  create_bd_pin -dir I -type clk m_axi_mm2s_aclk
  create_bd_pin -dir O running
  create_bd_pin -dir I running_all

  # Create instance: FullyPipelinedSpMV_0, and set properties
  set FullyPipelinedSpMV_0 [ create_bd_cell -type ip -vlnv user.org:user:FullyPipelinedSpMV:1.3 FullyPipelinedSpMV_0 ]

  # Create instance: axi_dma_0, and set properties
  set axi_dma_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_0 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_m_axi_s2mm_data_width {32} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_s2mm_burst_size {16} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_0

  # Create instance: axi_dma_1, and set properties
  set axi_dma_1 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_1 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_1

  # Create instance: axi_dma_2, and set properties
  set axi_dma_2 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_2 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {512} \
   CONFIG.c_mm2s_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_2

  # Create instance: axi_dma_3, and set properties
  set axi_dma_3 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_dma:7.1 axi_dma_3 ]
  set_property -dict [ list \
   CONFIG.c_addr_width {34} \
   CONFIG.c_include_mm2s {0} \
   CONFIG.c_include_mm2s_dre {0} \
   CONFIG.c_include_s2mm {1} \
   CONFIG.c_include_s2mm_dre {0} \
   CONFIG.c_include_sg {0} \
   CONFIG.c_m_axi_mm2s_data_width {32} \
   CONFIG.c_m_axi_s2mm_data_width {512} \
   CONFIG.c_mm2s_burst_size {16} \
   CONFIG.c_s2mm_burst_size {2} \
   CONFIG.c_sg_include_stscntrl_strm {0} \
   CONFIG.c_sg_length_width {26} \
   CONFIG.c_single_interface {0} \
 ] $axi_dma_3

  # Create interface connections
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_1_m_axi_vect [get_bd_intf_pins m_axi_vect] [get_bd_intf_pins FullyPipelinedSpMV_0/m_axi_vect]
  connect_bd_intf_net -intf_net FullyPipelinedSpMV_1_output_stream [get_bd_intf_pins FullyPipelinedSpMV_0/output_stream] [get_bd_intf_pins axi_dma_3/S_AXIS_S2MM]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/rowptr_stream] [get_bd_intf_pins axi_dma_0/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_0_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S] [get_bd_intf_pins axi_dma_0/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/col_ind_stream] [get_bd_intf_pins axi_dma_1/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_1_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S1] [get_bd_intf_pins axi_dma_1/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXIS_MM2S [get_bd_intf_pins FullyPipelinedSpMV_0/val_stream] [get_bd_intf_pins axi_dma_2/M_AXIS_MM2S]
  connect_bd_intf_net -intf_net axi_dma_2_M_AXI_MM2S [get_bd_intf_pins M_AXI_MM2S2] [get_bd_intf_pins axi_dma_2/M_AXI_MM2S]
  connect_bd_intf_net -intf_net axi_dma_3_M_AXI_S2MM [get_bd_intf_pins M_AXI_S2MM] [get_bd_intf_pins axi_dma_3/M_AXI_S2MM]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M00_AXI [get_bd_intf_pins S_AXI_LITE] [get_bd_intf_pins axi_dma_0/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M01_AXI [get_bd_intf_pins S_AXI_LITE1] [get_bd_intf_pins axi_dma_1/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M02_AXI [get_bd_intf_pins S_AXI_LITE2] [get_bd_intf_pins axi_dma_2/S_AXI_LITE]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M06_AXI [get_bd_intf_pins S_AXI_LITE3] [get_bd_intf_pins axi_dma_3/S_AXI_LITE]
  connect_bd_intf_net -intf_net s_axi_AXILiteS_1 [get_bd_intf_pins s_axi_AXILiteS] [get_bd_intf_pins FullyPipelinedSpMV_0/s_axi_AXILiteS]

  # Create port connections
  connect_bd_net -net FullyPipelinedSpMV_0_done [get_bd_pins done] [get_bd_pins FullyPipelinedSpMV_0/done]
  connect_bd_net -net FullyPipelinedSpMV_0_running [get_bd_pins running] [get_bd_pins FullyPipelinedSpMV_0/running]
  connect_bd_net -net clk_wiz_2_clk_out1 [get_bd_pins m_axi_mm2s_aclk] [get_bd_pins FullyPipelinedSpMV_0/ap_clk] [get_bd_pins axi_dma_0/m_axi_mm2s_aclk] [get_bd_pins axi_dma_0/s_axi_lite_aclk] [get_bd_pins axi_dma_1/m_axi_mm2s_aclk] [get_bd_pins axi_dma_1/s_axi_lite_aclk] [get_bd_pins axi_dma_2/m_axi_mm2s_aclk] [get_bd_pins axi_dma_2/s_axi_lite_aclk] [get_bd_pins axi_dma_3/m_axi_s2mm_aclk] [get_bd_pins axi_dma_3/s_axi_lite_aclk]
  connect_bd_net -net rst_clk_wiz_1_100M_peripheral_aresetn [get_bd_pins axi_resetn] [get_bd_pins FullyPipelinedSpMV_0/ap_rst_n] [get_bd_pins axi_dma_0/axi_resetn] [get_bd_pins axi_dma_1/axi_resetn] [get_bd_pins axi_dma_2/axi_resetn] [get_bd_pins axi_dma_3/axi_resetn]
  connect_bd_net -net running_all_1 [get_bd_pins running_all] [get_bd_pins FullyPipelinedSpMV_0/running_all]

  # Restore current instance
  current_bd_instance $oldCurInst
}
  variable script_folder

  if { $parentCell eq "" } {
     set parentCell [get_bd_cells /]
  }

  # Get object for parentCell
  set parentObj [get_bd_cells $parentCell]
  if { $parentObj == "" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2090 -severity "ERROR" "Unable to find parent cell <$parentCell>!"}
     return
  }

  # Make sure parentObj is hier blk
  set parentType [get_property TYPE $parentObj]
  if { $parentType ne "hier" } {
     catch {common::send_gid_msg -ssname BD::TCL -id 2091 -severity "ERROR" "Parent <$parentObj> has TYPE = <$parentType>. Expected to be <hier>."}
     return
  }

  # Save current instance; Restore later
  set oldCurInst [current_bd_instance .]

  # Set parent object as current
  current_bd_instance $parentObj


  # Create interface ports
  set ddr4_sdram_c1 [ create_bd_intf_port -mode Master -vlnv xilinx.com:interface:ddr4_rtl:1.0 ddr4_sdram_c1 ]

  set pci_express_x16 [ create_bd_intf_port -mode Master -vlnv xilinx.com:interface:pcie_7x_mgt_rtl:1.0 pci_express_x16 ]

  set pcie_refclk [ create_bd_intf_port -mode Slave -vlnv xilinx.com:interface:diff_clock_rtl:1.0 pcie_refclk ]
  set_property -dict [ list \
   CONFIG.FREQ_HZ {100000000} \
   ] $pcie_refclk

  set sysclk0 [ create_bd_intf_port -mode Slave -vlnv xilinx.com:interface:diff_clock_rtl:1.0 sysclk0 ]
  set_property -dict [ list \
   CONFIG.FREQ_HZ {100000000} \
   ] $sysclk0


  # Create ports
  set pcie_perstn [ create_bd_port -dir I -type rst pcie_perstn ]
  set_property -dict [ list \
   CONFIG.POLARITY {ACTIVE_LOW} \
 ] $pcie_perstn
  set resetn [ create_bd_port -dir I -type rst resetn ]
  set_property -dict [ list \
   CONFIG.POLARITY {ACTIVE_LOW} \
 ] $resetn

  # Create instance: MiCache_0, and set properties
  set MiCache_0 [ create_bd_cell -type ip -vlnv CGCL:MiCache:${cache_name}:1.0 MiCache_0 ]

  # Create instance: axi_interconnect_0, and set properties
  set axi_interconnect_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_interconnect:2.1 axi_interconnect_0 ]
  set_property -dict [ list \
   CONFIG.NUM_MI {1} \
   CONFIG.S00_HAS_REGSLICE {4} \
 ] $axi_interconnect_0

  # Create instance: axi_interconnect_ddr, and set properties
  set axi_interconnect_ddr [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_interconnect:2.1 axi_interconnect_ddr ]
  set_property -dict [ list \
   CONFIG.ENABLE_ADVANCED_OPTIONS {0} \
   CONFIG.NUM_MI {1} \
   CONFIG.NUM_SI {16} \
 ] $axi_interconnect_ddr

  # Create instance: clk_wiz_1, and set properties
  set clk_wiz_1 [ create_bd_cell -type ip -vlnv xilinx.com:ip:clk_wiz:6.0 clk_wiz_1 ]
  set_property -dict [ list \
   CONFIG.CLKIN1_JITTER_PS {33.330000000000005} \
   CONFIG.CLKIN2_JITTER_PS {100.0} \
   CONFIG.CLKOUT1_DRIVES {BUFG} \
   CONFIG.CLKOUT1_JITTER {98.047} \
   CONFIG.CLKOUT1_PHASE_ERROR {73.261} \
   CONFIG.CLKOUT1_REQUESTED_OUT_FREQ {100.000} \
   CONFIG.CLKOUT2_DRIVES {Buffer} \
   CONFIG.CLKOUT2_JITTER {83.632} \
   CONFIG.CLKOUT2_PHASE_ERROR {73.261} \
   CONFIG.CLKOUT2_REQUESTED_OUT_FREQ {225.000} \
   CONFIG.CLKOUT2_USED {true} \
   CONFIG.CLKOUT3_DRIVES {BUFG} \
   CONFIG.CLKOUT3_JITTER {73.020} \
   CONFIG.CLKOUT3_PHASE_ERROR {73.261} \
   CONFIG.CLKOUT3_REQUESTED_OUT_FREQ {450} \
   CONFIG.CLKOUT3_USED {true} \
   CONFIG.CLKOUT4_DRIVES {Buffer} \
   CONFIG.CLKOUT5_DRIVES {Buffer} \
   CONFIG.CLKOUT6_DRIVES {Buffer} \
   CONFIG.CLKOUT7_DRIVES {Buffer} \
   CONFIG.CLK_IN1_BOARD_INTERFACE {Custom} \
   CONFIG.CLK_IN2_BOARD_INTERFACE {Custom} \
   CONFIG.FEEDBACK_SOURCE {FDBK_AUTO} \
   CONFIG.MMCM_BANDWIDTH {OPTIMIZED} \
   CONFIG.MMCM_CLKFBOUT_MULT_F {4.500} \
   CONFIG.MMCM_CLKIN1_PERIOD {3.333} \
   CONFIG.MMCM_CLKIN2_PERIOD {10.0} \
   CONFIG.MMCM_CLKOUT0_DIVIDE_F {13.500} \
   CONFIG.MMCM_CLKOUT1_DIVIDE {6} \
   CONFIG.MMCM_CLKOUT2_DIVIDE {3} \
   CONFIG.MMCM_COMPENSATION {AUTO} \
   CONFIG.MMCM_DIVCLK_DIVIDE {1} \
   CONFIG.NUM_OUT_CLKS {3} \
   CONFIG.PRIMITIVE {MMCM} \
   CONFIG.PRIM_SOURCE {Single_ended_clock_capable_pin} \
   CONFIG.RESET_BOARD_INTERFACE {Custom} \
   CONFIG.RESET_PORT {reset} \
   CONFIG.RESET_TYPE {ACTIVE_HIGH} \
   CONFIG.SECONDARY_SOURCE {Single_ended_clock_capable_pin} \
   CONFIG.USE_BOARD_FLOW {true} \
   CONFIG.USE_INCLK_SWITCHOVER {false} \
 ] $clk_wiz_1

  # Create instance: ddr4_0, and set properties
  set ddr4_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:ddr4:2.2 ddr4_0 ]
  set_property -dict [ list \
   CONFIG.ADDN_UI_CLKOUT1_FREQ_HZ {100} \
   CONFIG.C0.DDR4_AUTO_AP_COL_A3 {true} \
   CONFIG.C0.DDR4_AxiAddressWidth {34} \
   CONFIG.C0.DDR4_AxiDataWidth {512} \
   CONFIG.C0.DDR4_CLKFBOUT_MULT {15} \
   CONFIG.C0.DDR4_CLKOUT0_DIVIDE {5} \
   CONFIG.C0.DDR4_CasLatency {17} \
   CONFIG.C0.DDR4_CasWriteLatency {12} \
   CONFIG.C0.DDR4_DataMask {NONE} \
   CONFIG.C0.DDR4_DataWidth {72} \
   CONFIG.C0.DDR4_EN_PARITY {true} \
   CONFIG.C0.DDR4_Ecc {true} \
   CONFIG.C0.DDR4_InputClockPeriod {9996} \
   CONFIG.C0.DDR4_Mem_Add_Map {ROW_COLUMN_BANK_INTLV} \
   CONFIG.C0.DDR4_MemoryPart {MTA18ASF2G72PZ-2G3} \
   CONFIG.C0.DDR4_MemoryType {RDIMMs} \
   CONFIG.C0.DDR4_TimePeriod {833} \
   CONFIG.C0_CLOCK_BOARD_INTERFACE {sysclk1} \
   CONFIG.C0_DDR4_BOARD_INTERFACE {ddr4_sdram_c1} \
   CONFIG.RESET_BOARD_INTERFACE {resetn} \
 ] $ddr4_0

  # Create instance: hbm_0, and set properties
  set hbm_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:hbm:1.0 hbm_0 ]
  set_property -dict [ list \
   CONFIG.USER_APB_EN {false} \
   CONFIG.USER_CLK_SEL_LIST0 {AXI_00_ACLK} \
   CONFIG.USER_CLK_SEL_LIST1 {AXI_23_ACLK} \
   CONFIG.USER_HBM_CP_1 {3} \
   CONFIG.USER_HBM_DENSITY {4GB} \
   CONFIG.USER_HBM_FBDIV_1 {5} \
   CONFIG.USER_HBM_HEX_CP_RES_1 {0x0000B300} \
   CONFIG.USER_HBM_HEX_FBDIV_CLKOUTDIV_1 {0x00000142} \
   CONFIG.USER_HBM_HEX_LOCK_FB_REF_DLY_1 {0x00000a0a} \
   CONFIG.USER_HBM_LOCK_FB_DLY_1 {10} \
   CONFIG.USER_HBM_LOCK_REF_DLY_1 {10} \
   CONFIG.USER_HBM_RES_1 {11} \
   CONFIG.USER_HBM_STACK {1} \
   CONFIG.USER_MC0_EN_DATA_MASK {true} \
   CONFIG.USER_MC10_EN_DATA_MASK {true} \
   CONFIG.USER_MC11_EN_DATA_MASK {true} \
   CONFIG.USER_MC12_EN_DATA_MASK {true} \
   CONFIG.USER_MC13_EN_DATA_MASK {true} \
   CONFIG.USER_MC14_EN_DATA_MASK {true} \
   CONFIG.USER_MC15_EN_DATA_MASK {true} \
   CONFIG.USER_MC1_EN_DATA_MASK {true} \
   CONFIG.USER_MC2_EN_DATA_MASK {true} \
   CONFIG.USER_MC3_EN_DATA_MASK {true} \
   CONFIG.USER_MC4_EN_DATA_MASK {true} \
   CONFIG.USER_MC5_EN_DATA_MASK {true} \
   CONFIG.USER_MC6_EN_DATA_MASK {true} \
   CONFIG.USER_MC7_EN_DATA_MASK {true} \
   CONFIG.USER_MC8_EN_DATA_MASK {true} \
   CONFIG.USER_MC9_EN_DATA_MASK {true} \
   CONFIG.USER_MC_ENABLE_08 {FALSE} \
   CONFIG.USER_MC_ENABLE_09 {FALSE} \
   CONFIG.USER_MC_ENABLE_10 {FALSE} \
   CONFIG.USER_MC_ENABLE_11 {FALSE} \
   CONFIG.USER_MC_ENABLE_12 {FALSE} \
   CONFIG.USER_MC_ENABLE_13 {FALSE} \
   CONFIG.USER_MC_ENABLE_14 {FALSE} \
   CONFIG.USER_MC_ENABLE_15 {FALSE} \
   CONFIG.USER_MC_ENABLE_APB_01 {FALSE} \
   CONFIG.USER_MEMORY_DISPLAY {4096} \
   CONFIG.USER_PHY_ENABLE_08 {FALSE} \
   CONFIG.USER_PHY_ENABLE_09 {FALSE} \
   CONFIG.USER_PHY_ENABLE_10 {FALSE} \
   CONFIG.USER_PHY_ENABLE_11 {FALSE} \
   CONFIG.USER_PHY_ENABLE_12 {FALSE} \
   CONFIG.USER_PHY_ENABLE_13 {FALSE} \
   CONFIG.USER_PHY_ENABLE_14 {FALSE} \
   CONFIG.USER_PHY_ENABLE_15 {FALSE} \
   CONFIG.USER_SAXI_01 {false} \
   CONFIG.USER_SAXI_02 {false} \
   CONFIG.USER_SAXI_03 {true} \
   CONFIG.USER_SAXI_04 {false} \
   CONFIG.USER_SAXI_05 {false} \
   CONFIG.USER_SAXI_06 {false} \
   CONFIG.USER_SAXI_07 {false} \
   CONFIG.USER_SAXI_08 {false} \
   CONFIG.USER_SAXI_09 {false} \
   CONFIG.USER_SAXI_10 {false} \
   CONFIG.USER_SAXI_11 {false} \
   CONFIG.USER_SAXI_12 {false} \
   CONFIG.USER_SAXI_13 {false} \
   CONFIG.USER_SAXI_14 {false} \
   CONFIG.USER_SAXI_15 {false} \
   CONFIG.USER_SWITCH_ENABLE_01 {FALSE} \
 ] $hbm_0

  # Create instance: hier_0
  create_hier_cell_hier_0 [current_bd_instance .] hier_0

  # Create instance: hier_1
  create_hier_cell_hier_1 [current_bd_instance .] hier_1

  # Create instance: hier_2
  create_hier_cell_hier_2 [current_bd_instance .] hier_2

  # Create instance: hier_3
  create_hier_cell_hier_3 [current_bd_instance .] hier_3

  # Create instance: proc_sys_reset_100M, and set properties
  set proc_sys_reset_100M [ create_bd_cell -type ip -vlnv xilinx.com:ip:proc_sys_reset:5.0 proc_sys_reset_100M ]

  # Create instance: proc_sys_reset_450M, and set properties
  set proc_sys_reset_450M [ create_bd_cell -type ip -vlnv xilinx.com:ip:proc_sys_reset:5.0 proc_sys_reset_450M ]

  # Create instance: qdma_0, and set properties
  set qdma_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:qdma:4.0 qdma_0 ]
  set_property -dict [ list \
   CONFIG.MAILBOX_ENABLE {true} \
   CONFIG.PCIE_BOARD_INTERFACE {pci_express_x16} \
   CONFIG.PF0_SRIOV_FIRST_VF_OFFSET {4} \
   CONFIG.PF1_INTERRUPT_PIN {INTA} \
   CONFIG.PF1_SRIOV_CAP_INITIAL_VF {4} \
   CONFIG.PF1_SRIOV_FIRST_VF_OFFSET {7} \
   CONFIG.PF2_INTERRUPT_PIN {INTA} \
   CONFIG.PF2_SRIOV_CAP_INITIAL_VF {4} \
   CONFIG.PF2_SRIOV_FIRST_VF_OFFSET {10} \
   CONFIG.PF3_INTERRUPT_PIN {INTA} \
   CONFIG.PF3_SRIOV_CAP_INITIAL_VF {4} \
   CONFIG.PF3_SRIOV_FIRST_VF_OFFSET {13} \
   CONFIG.SRIOV_CAP_ENABLE {true} \
   CONFIG.SRIOV_FIRST_VF_OFFSET {4} \
   CONFIG.SYS_RST_N_BOARD_INTERFACE {pcie_perstn} \
   CONFIG.axilite_master_en {false} \
   CONFIG.axisten_if_enable_msg_route {27FFF} \
   CONFIG.barlite_mb_pf0 {1} \
   CONFIG.dma_intf_sel_qdma {AXI_MM} \
   CONFIG.en_axi_master_if {true} \
   CONFIG.en_axi_st_qdma {false} \
   CONFIG.en_bridge_slv {false} \
   CONFIG.en_gt_selection {true} \
   CONFIG.flr_enable {true} \
   CONFIG.mode_selection {Advanced} \
   CONFIG.pf0_ari_enabled {true} \
   CONFIG.pf0_bar0_prefetchable_qdma {true} \
   CONFIG.pf0_bar2_64bit_qdma {false} \
   CONFIG.pf0_bar2_enabled_qdma {false} \
   CONFIG.pf0_msix_cap_pba_offset {00010050} \
   CONFIG.pf0_msix_cap_table_offset {00010040} \
   CONFIG.pf1_bar0_prefetchable_qdma {true} \
   CONFIG.pf1_bar2_64bit_qdma {false} \
   CONFIG.pf1_bar2_enabled_qdma {false} \
   CONFIG.pf2_bar0_prefetchable_qdma {true} \
   CONFIG.pf2_bar2_64bit_qdma {false} \
   CONFIG.pf2_bar2_enabled_qdma {false} \
   CONFIG.pf3_bar0_prefetchable_qdma {true} \
   CONFIG.pf3_bar2_64bit_qdma {false} \
   CONFIG.pf3_bar2_enabled_qdma {false} \
   CONFIG.testname {mm} \
   CONFIG.vdm_en {false} \
   CONFIG.xdma_wnum_rids {16} \
 ] $qdma_0

  # Create instance: qdma_0_axi_periph, and set properties
  set qdma_0_axi_periph [ create_bd_cell -type ip -vlnv xilinx.com:ip:axi_interconnect:2.1 qdma_0_axi_periph ]
  set_property -dict [ list \
   CONFIG.NUM_MI {24} \
 ] $qdma_0_axi_periph

  # Create instance: resetn_inv_0, and set properties
  set resetn_inv_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:util_vector_logic:2.0 resetn_inv_0 ]
  set_property -dict [ list \
   CONFIG.C_OPERATION {not} \
   CONFIG.C_SIZE {1} \
   CONFIG.LOGO_FILE {data/sym_notgate.png} \
 ] $resetn_inv_0

  # Create instance: resetn_inv_1, and set properties
  set resetn_inv_1 [ create_bd_cell -type ip -vlnv xilinx.com:ip:util_vector_logic:2.0 resetn_inv_1 ]
  set_property -dict [ list \
   CONFIG.C_OPERATION {not} \
   CONFIG.C_SIZE {1} \
   CONFIG.LOGO_FILE {data/sym_notgate.png} \
 ] $resetn_inv_1

  # Create instance: rst_clk_wiz_1_225M, and set properties
  set rst_clk_wiz_1_225M [ create_bd_cell -type ip -vlnv xilinx.com:ip:proc_sys_reset:5.0 rst_clk_wiz_1_225M ]
  set_property -dict [ list \
   CONFIG.C_AUX_RESET_HIGH {1} \
   CONFIG.C_EXT_RST_WIDTH {1} \
 ] $rst_clk_wiz_1_225M

  # Create instance: rst_ddr4_0_300M, and set properties
  set rst_ddr4_0_300M [ create_bd_cell -type ip -vlnv xilinx.com:ip:proc_sys_reset:5.0 rst_ddr4_0_300M ]

  # Create instance: smartconnect_ddr, and set properties
  set smartconnect_ddr [ create_bd_cell -type ip -vlnv xilinx.com:ip:smartconnect:1.0 smartconnect_ddr ]
  set_property -dict [ list \
   CONFIG.NUM_SI {2} \
 ] $smartconnect_ddr

  # Create instance: util_ds_buf, and set properties
  set util_ds_buf [ create_bd_cell -type ip -vlnv xilinx.com:ip:util_ds_buf:2.1 util_ds_buf ]
  set_property -dict [ list \
   CONFIG.C_BUF_TYPE {IBUFDSGTE} \
   CONFIG.DIFF_CLK_IN_BOARD_INTERFACE {pcie_refclk} \
   CONFIG.USE_BOARD_FLOW {true} \
 ] $util_ds_buf

  # Create instance: xlconstant_0, and set properties
  set xlconstant_0 [ create_bd_cell -type ip -vlnv xilinx.com:ip:xlconstant:1.1 xlconstant_0 ]

  # Create interface connections
  connect_bd_intf_net -intf_net MiCache_0_io_out_0 [get_bd_intf_pins MiCache_0/io_out_0] [get_bd_intf_pins axi_interconnect_0/S00_AXI]
  connect_bd_intf_net -intf_net S05_AXI_1 [get_bd_intf_pins axi_interconnect_ddr/S05_AXI] [get_bd_intf_pins hier_0/M_AXI_MM2S1]
  connect_bd_intf_net -intf_net S07_AXI_1 [get_bd_intf_pins axi_interconnect_ddr/S07_AXI] [get_bd_intf_pins hier_3/M_AXI_MM2S1]
  connect_bd_intf_net -intf_net S_AXI_LITE1_1 [get_bd_intf_pins hier_3/S_AXI_LITE1] [get_bd_intf_pins qdma_0_axi_periph/M20_AXI]
  connect_bd_intf_net -intf_net S_AXI_LITE2_1 [get_bd_intf_pins hier_1/S_AXI_LITE2] [get_bd_intf_pins qdma_0_axi_periph/M11_AXI]
  connect_bd_intf_net -intf_net S_AXI_LITE_1 [get_bd_intf_pins hier_1/S_AXI_LITE] [get_bd_intf_pins qdma_0_axi_periph/M09_AXI]
  connect_bd_intf_net -intf_net axi_interconnect_0_M00_AXI [get_bd_intf_pins axi_interconnect_ddr/M00_AXI] [get_bd_intf_pins smartconnect_ddr/S00_AXI]
  connect_bd_intf_net -intf_net axi_interconnect_1_M00_AXI [get_bd_intf_pins axi_interconnect_0/M00_AXI] [get_bd_intf_pins hbm_0/SAXI_00]
  connect_bd_intf_net -intf_net ddr4_0_C0_DDR4 [get_bd_intf_ports ddr4_sdram_c1] [get_bd_intf_pins ddr4_0/C0_DDR4]
  connect_bd_intf_net -intf_net hier_0_M_AXI_MM2S [get_bd_intf_pins axi_interconnect_ddr/S01_AXI] [get_bd_intf_pins hier_0/M_AXI_MM2S]
  connect_bd_intf_net -intf_net hier_0_M_AXI_MM2S2 [get_bd_intf_pins axi_interconnect_ddr/S09_AXI] [get_bd_intf_pins hier_0/M_AXI_MM2S2]
  connect_bd_intf_net -intf_net hier_0_M_AXI_S2MM [get_bd_intf_pins axi_interconnect_ddr/S13_AXI] [get_bd_intf_pins hier_0/M_AXI_S2MM]
  connect_bd_intf_net -intf_net hier_0_m_axi_vect [get_bd_intf_pins MiCache_0/io_in_0] [get_bd_intf_pins hier_0/m_axi_vect]
  connect_bd_intf_net -intf_net hier_1_M_AXI_MM2S [get_bd_intf_pins axi_interconnect_ddr/S00_AXI] [get_bd_intf_pins hier_1/M_AXI_MM2S]
  connect_bd_intf_net -intf_net hier_1_M_AXI_MM2S1 [get_bd_intf_pins axi_interconnect_ddr/S04_AXI] [get_bd_intf_pins hier_1/M_AXI_MM2S1]
  connect_bd_intf_net -intf_net hier_1_M_AXI_MM2S2 [get_bd_intf_pins axi_interconnect_ddr/S08_AXI] [get_bd_intf_pins hier_1/M_AXI_MM2S2]
  connect_bd_intf_net -intf_net hier_1_M_AXI_S2MM [get_bd_intf_pins axi_interconnect_ddr/S12_AXI] [get_bd_intf_pins hier_1/M_AXI_S2MM]
  connect_bd_intf_net -intf_net hier_1_m_axi_vect [get_bd_intf_pins MiCache_0/io_in_1] [get_bd_intf_pins hier_1/m_axi_vect]
  connect_bd_intf_net -intf_net hier_2_M_AXI_MM2S [get_bd_intf_pins axi_interconnect_ddr/S02_AXI] [get_bd_intf_pins hier_2/M_AXI_MM2S]
  connect_bd_intf_net -intf_net hier_2_M_AXI_MM2S1 [get_bd_intf_pins axi_interconnect_ddr/S06_AXI] [get_bd_intf_pins hier_2/M_AXI_MM2S1]
  connect_bd_intf_net -intf_net hier_2_M_AXI_MM2S2 [get_bd_intf_pins axi_interconnect_ddr/S10_AXI] [get_bd_intf_pins hier_2/M_AXI_MM2S2]
  connect_bd_intf_net -intf_net hier_2_M_AXI_S2MM [get_bd_intf_pins axi_interconnect_ddr/S14_AXI] [get_bd_intf_pins hier_2/M_AXI_S2MM]
  connect_bd_intf_net -intf_net hier_2_m_axi_vect [get_bd_intf_pins MiCache_0/io_in_2] [get_bd_intf_pins hier_2/m_axi_vect]
  connect_bd_intf_net -intf_net hier_3_M_AXI_MM2S [get_bd_intf_pins axi_interconnect_ddr/S03_AXI] [get_bd_intf_pins hier_3/M_AXI_MM2S]
  connect_bd_intf_net -intf_net hier_3_M_AXI_MM2S2 [get_bd_intf_pins axi_interconnect_ddr/S11_AXI] [get_bd_intf_pins hier_3/M_AXI_MM2S2]
  connect_bd_intf_net -intf_net hier_3_M_AXI_S2MM [get_bd_intf_pins axi_interconnect_ddr/S15_AXI] [get_bd_intf_pins hier_3/M_AXI_S2MM]
  connect_bd_intf_net -intf_net hier_3_m_axi_vect [get_bd_intf_pins MiCache_0/io_in_3] [get_bd_intf_pins hier_3/m_axi_vect]
  connect_bd_intf_net -intf_net pcie_refclk_1 [get_bd_intf_ports pcie_refclk] [get_bd_intf_pins util_ds_buf/CLK_IN_D]
  connect_bd_intf_net -intf_net qdma_0_M_AXI [get_bd_intf_pins qdma_0/M_AXI] [get_bd_intf_pins qdma_0_axi_periph/S00_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M00_AXI [get_bd_intf_pins hier_0/S_AXI_LITE] [get_bd_intf_pins qdma_0_axi_periph/M00_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M01_AXI [get_bd_intf_pins hier_0/S_AXI_LITE1] [get_bd_intf_pins qdma_0_axi_periph/M01_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M02_AXI [get_bd_intf_pins hier_0/S_AXI_LITE2] [get_bd_intf_pins qdma_0_axi_periph/M02_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M03_AXI [get_bd_intf_pins MiCache_0/io_axiProfiling] [get_bd_intf_pins qdma_0_axi_periph/M03_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M04_AXI [get_bd_intf_pins hier_0/s_axi_AXILiteS] [get_bd_intf_pins qdma_0_axi_periph/M04_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M05_AXI [get_bd_intf_pins hbm_0/SAXI_03] [get_bd_intf_pins qdma_0_axi_periph/M05_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M06_AXI [get_bd_intf_pins hier_0/S_AXI_LITE3] [get_bd_intf_pins qdma_0_axi_periph/M06_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M07_AXI [get_bd_intf_pins qdma_0_axi_periph/M07_AXI] [get_bd_intf_pins smartconnect_ddr/S01_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M08_AXI [get_bd_intf_pins ddr4_0/C0_DDR4_S_AXI_CTRL] [get_bd_intf_pins qdma_0_axi_periph/M08_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M10_AXI [get_bd_intf_pins hier_1/S_AXI_LITE1] [get_bd_intf_pins qdma_0_axi_periph/M10_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M13_AXI [get_bd_intf_pins hier_1/S_AXI_LITE3] [get_bd_intf_pins qdma_0_axi_periph/M13_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M14_AXI [get_bd_intf_pins hier_2/S_AXI_LITE] [get_bd_intf_pins qdma_0_axi_periph/M14_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M15_AXI [get_bd_intf_pins hier_2/S_AXI_LITE1] [get_bd_intf_pins qdma_0_axi_periph/M15_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M16_AXI [get_bd_intf_pins hier_2/S_AXI_LITE2] [get_bd_intf_pins qdma_0_axi_periph/M16_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M17_AXI [get_bd_intf_pins hier_2/s_axi_AXILiteS] [get_bd_intf_pins qdma_0_axi_periph/M17_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M18_AXI [get_bd_intf_pins hier_2/S_AXI_LITE3] [get_bd_intf_pins qdma_0_axi_periph/M18_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M19_AXI [get_bd_intf_pins hier_3/S_AXI_LITE] [get_bd_intf_pins qdma_0_axi_periph/M19_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M21_AXI [get_bd_intf_pins hier_3/S_AXI_LITE2] [get_bd_intf_pins qdma_0_axi_periph/M21_AXI]
  connect_bd_intf_net -intf_net qdma_0_axi_periph_M23_AXI [get_bd_intf_pins hier_3/S_AXI_LITE3] [get_bd_intf_pins qdma_0_axi_periph/M23_AXI]
  connect_bd_intf_net -intf_net qdma_0_pcie_mgt [get_bd_intf_ports pci_express_x16] [get_bd_intf_pins qdma_0/pcie_mgt]
  connect_bd_intf_net -intf_net s_axi_AXILiteS_1 [get_bd_intf_pins hier_1/s_axi_AXILiteS] [get_bd_intf_pins qdma_0_axi_periph/M12_AXI]
  connect_bd_intf_net -intf_net s_axi_AXILiteS_2 [get_bd_intf_pins hier_3/s_axi_AXILiteS] [get_bd_intf_pins qdma_0_axi_periph/M22_AXI]
  connect_bd_intf_net -intf_net smartconnect_1_M00_AXI [get_bd_intf_pins ddr4_0/C0_DDR4_S_AXI] [get_bd_intf_pins smartconnect_ddr/M00_AXI]
  connect_bd_intf_net -intf_net sysclk0_1 [get_bd_intf_ports sysclk0] [get_bd_intf_pins ddr4_0/C0_SYS_CLK]

  # Create port connections
  connect_bd_net -net MiCache_0_io_reset_out [get_bd_pins MiCache_0/io_reset_out] [get_bd_pins rst_clk_wiz_1_225M/aux_reset_in]
  connect_bd_net -net MiCache_0_io_pe_all_running [get_bd_pins MiCache_0/io_pe_all_running] [get_bd_pins hier_0/running_all] [get_bd_pins hier_1/running_all] [get_bd_pins hier_2/running_all] [get_bd_pins hier_3/running_all]
  connect_bd_net -net clk_wiz_1_clk_out2 [get_bd_pins clk_wiz_1/clk_out1] [get_bd_pins hbm_0/APB_0_PCLK] [get_bd_pins hbm_0/HBM_REF_CLK_0] [get_bd_pins proc_sys_reset_100M/slowest_sync_clk]
  connect_bd_net -net clk_wiz_1_clk_out3 [get_bd_pins axi_interconnect_0/M00_ACLK] [get_bd_pins clk_wiz_1/clk_out3] [get_bd_pins hbm_0/AXI_00_ACLK] [get_bd_pins proc_sys_reset_450M/slowest_sync_clk]
  connect_bd_net -net clk_wiz_1_locked [get_bd_pins clk_wiz_1/locked] [get_bd_pins rst_clk_wiz_1_225M/dcm_locked]
  connect_bd_net -net clk_wiz_2_clk_out1 [get_bd_pins MiCache_0/clock] [get_bd_pins axi_interconnect_0/ACLK] [get_bd_pins axi_interconnect_0/S00_ACLK] [get_bd_pins axi_interconnect_ddr/ACLK] [get_bd_pins axi_interconnect_ddr/S00_ACLK] [get_bd_pins axi_interconnect_ddr/S01_ACLK] [get_bd_pins axi_interconnect_ddr/S02_ACLK] [get_bd_pins axi_interconnect_ddr/S03_ACLK] [get_bd_pins axi_interconnect_ddr/S04_ACLK] [get_bd_pins axi_interconnect_ddr/S05_ACLK] [get_bd_pins axi_interconnect_ddr/S06_ACLK] [get_bd_pins axi_interconnect_ddr/S07_ACLK] [get_bd_pins axi_interconnect_ddr/S08_ACLK] [get_bd_pins axi_interconnect_ddr/S09_ACLK] [get_bd_pins axi_interconnect_ddr/S10_ACLK] [get_bd_pins axi_interconnect_ddr/S11_ACLK] [get_bd_pins axi_interconnect_ddr/S12_ACLK] [get_bd_pins axi_interconnect_ddr/S13_ACLK] [get_bd_pins axi_interconnect_ddr/S14_ACLK] [get_bd_pins axi_interconnect_ddr/S15_ACLK] [get_bd_pins clk_wiz_1/clk_out2] [get_bd_pins hbm_0/AXI_03_ACLK] [get_bd_pins hier_0/m_axi_mm2s_aclk] [get_bd_pins hier_1/m_axi_mm2s_aclk] [get_bd_pins hier_2/m_axi_mm2s_aclk] [get_bd_pins hier_3/m_axi_mm2s_aclk] [get_bd_pins qdma_0_axi_periph/ACLK] [get_bd_pins qdma_0_axi_periph/M00_ACLK] [get_bd_pins qdma_0_axi_periph/M01_ACLK] [get_bd_pins qdma_0_axi_periph/M02_ACLK] [get_bd_pins qdma_0_axi_periph/M03_ACLK] [get_bd_pins qdma_0_axi_periph/M04_ACLK] [get_bd_pins qdma_0_axi_periph/M05_ACLK] [get_bd_pins qdma_0_axi_periph/M06_ACLK] [get_bd_pins qdma_0_axi_periph/M09_ACLK] [get_bd_pins qdma_0_axi_periph/M10_ACLK] [get_bd_pins qdma_0_axi_periph/M11_ACLK] [get_bd_pins qdma_0_axi_periph/M12_ACLK] [get_bd_pins qdma_0_axi_periph/M13_ACLK] [get_bd_pins qdma_0_axi_periph/M14_ACLK] [get_bd_pins qdma_0_axi_periph/M15_ACLK] [get_bd_pins qdma_0_axi_periph/M16_ACLK] [get_bd_pins qdma_0_axi_periph/M17_ACLK] [get_bd_pins qdma_0_axi_periph/M18_ACLK] [get_bd_pins qdma_0_axi_periph/M19_ACLK] [get_bd_pins qdma_0_axi_periph/M20_ACLK] [get_bd_pins qdma_0_axi_periph/M21_ACLK] [get_bd_pins qdma_0_axi_periph/M22_ACLK] [get_bd_pins qdma_0_axi_periph/M23_ACLK] [get_bd_pins rst_clk_wiz_1_225M/slowest_sync_clk]
  connect_bd_net -net ddr4_0_c0_ddr4_ui_clk [get_bd_pins axi_interconnect_ddr/M00_ACLK] [get_bd_pins clk_wiz_1/clk_in1] [get_bd_pins ddr4_0/c0_ddr4_ui_clk] [get_bd_pins qdma_0_axi_periph/M07_ACLK] [get_bd_pins qdma_0_axi_periph/M08_ACLK] [get_bd_pins rst_ddr4_0_300M/slowest_sync_clk] [get_bd_pins smartconnect_ddr/aclk]
  connect_bd_net -net ddr4_0_c0_ddr4_ui_clk_sync_rst [get_bd_pins clk_wiz_1/reset] [get_bd_pins ddr4_0/c0_ddr4_ui_clk_sync_rst] [get_bd_pins resetn_inv_1/Op1] [get_bd_pins rst_ddr4_0_300M/ext_reset_in]
  connect_bd_net -net hier_0_done [get_bd_pins MiCache_0/io_pe_done_0] [get_bd_pins hier_0/done]
  connect_bd_net -net hier_0_running [get_bd_pins MiCache_0/io_pe_running_0] [get_bd_pins hier_0/running]
  connect_bd_net -net hier_1_done [get_bd_pins MiCache_0/io_pe_done_1] [get_bd_pins hier_1/done]
  connect_bd_net -net hier_1_running [get_bd_pins MiCache_0/io_pe_running_1] [get_bd_pins hier_1/running]
  connect_bd_net -net hier_2_done [get_bd_pins MiCache_0/io_pe_done_2] [get_bd_pins hier_2/done]
  connect_bd_net -net hier_2_running [get_bd_pins MiCache_0/io_pe_running_2] [get_bd_pins hier_2/running]
  connect_bd_net -net hier_3_done [get_bd_pins MiCache_0/io_pe_done_3] [get_bd_pins hier_3/done]
  connect_bd_net -net hier_3_running [get_bd_pins MiCache_0/io_pe_running_3] [get_bd_pins hier_3/running]
  connect_bd_net -net pcie_perstn_1 [get_bd_ports pcie_perstn] [get_bd_pins qdma_0/sys_rst_n]
  connect_bd_net -net proc_sys_reset_100M_peripheral_aresetn [get_bd_pins hbm_0/APB_0_PRESET_N] [get_bd_pins proc_sys_reset_100M/peripheral_aresetn]
  connect_bd_net -net proc_sys_reset_450M_peripheral_aresetn [get_bd_pins axi_interconnect_0/M00_ARESETN] [get_bd_pins hbm_0/AXI_00_ARESET_N] [get_bd_pins proc_sys_reset_450M/peripheral_aresetn]
  connect_bd_net -net qdma_0_axi_aclk [get_bd_pins qdma_0/axi_aclk] [get_bd_pins qdma_0_axi_periph/S00_ACLK]
  connect_bd_net -net qdma_0_axi_aresetn [get_bd_pins qdma_0/axi_aresetn] [get_bd_pins qdma_0_axi_periph/S00_ARESETN]
  connect_bd_net -net resetn_1 [get_bd_ports resetn] [get_bd_pins resetn_inv_0/Op1]
  connect_bd_net -net resetn_inv_0_Res [get_bd_pins ddr4_0/sys_rst] [get_bd_pins resetn_inv_0/Res]
  connect_bd_net -net resetn_inv_1_Res [get_bd_pins proc_sys_reset_100M/ext_reset_in] [get_bd_pins proc_sys_reset_450M/ext_reset_in] [get_bd_pins resetn_inv_1/Res] [get_bd_pins rst_clk_wiz_1_225M/ext_reset_in]
  connect_bd_net -net rst_clk_wiz_1_100M_interconnect_aresetn [get_bd_pins qdma_0_axi_periph/ARESETN] [get_bd_pins qdma_0_axi_periph/M00_ARESETN] [get_bd_pins qdma_0_axi_periph/M01_ARESETN] [get_bd_pins qdma_0_axi_periph/M02_ARESETN] [get_bd_pins qdma_0_axi_periph/M03_ARESETN] [get_bd_pins qdma_0_axi_periph/M04_ARESETN] [get_bd_pins qdma_0_axi_periph/M05_ARESETN] [get_bd_pins qdma_0_axi_periph/M06_ARESETN] [get_bd_pins qdma_0_axi_periph/M09_ARESETN] [get_bd_pins qdma_0_axi_periph/M10_ARESETN] [get_bd_pins qdma_0_axi_periph/M11_ARESETN] [get_bd_pins qdma_0_axi_periph/M12_ARESETN] [get_bd_pins qdma_0_axi_periph/M13_ARESETN] [get_bd_pins qdma_0_axi_periph/M14_ARESETN] [get_bd_pins qdma_0_axi_periph/M15_ARESETN] [get_bd_pins qdma_0_axi_periph/M16_ARESETN] [get_bd_pins qdma_0_axi_periph/M17_ARESETN] [get_bd_pins qdma_0_axi_periph/M18_ARESETN] [get_bd_pins qdma_0_axi_periph/M19_ARESETN] [get_bd_pins qdma_0_axi_periph/M20_ARESETN] [get_bd_pins qdma_0_axi_periph/M21_ARESETN] [get_bd_pins qdma_0_axi_periph/M22_ARESETN] [get_bd_pins qdma_0_axi_periph/M23_ARESETN] [get_bd_pins rst_clk_wiz_1_225M/interconnect_aresetn]
  connect_bd_net -net rst_clk_wiz_1_100M_peripheral_aresetn [get_bd_pins axi_interconnect_0/ARESETN] [get_bd_pins axi_interconnect_0/S00_ARESETN] [get_bd_pins axi_interconnect_ddr/ARESETN] [get_bd_pins axi_interconnect_ddr/S00_ARESETN] [get_bd_pins axi_interconnect_ddr/S01_ARESETN] [get_bd_pins axi_interconnect_ddr/S02_ARESETN] [get_bd_pins axi_interconnect_ddr/S03_ARESETN] [get_bd_pins axi_interconnect_ddr/S04_ARESETN] [get_bd_pins axi_interconnect_ddr/S05_ARESETN] [get_bd_pins axi_interconnect_ddr/S06_ARESETN] [get_bd_pins axi_interconnect_ddr/S07_ARESETN] [get_bd_pins axi_interconnect_ddr/S08_ARESETN] [get_bd_pins axi_interconnect_ddr/S09_ARESETN] [get_bd_pins axi_interconnect_ddr/S10_ARESETN] [get_bd_pins axi_interconnect_ddr/S11_ARESETN] [get_bd_pins axi_interconnect_ddr/S12_ARESETN] [get_bd_pins axi_interconnect_ddr/S13_ARESETN] [get_bd_pins axi_interconnect_ddr/S14_ARESETN] [get_bd_pins axi_interconnect_ddr/S15_ARESETN] [get_bd_pins hbm_0/AXI_03_ARESET_N] [get_bd_pins hier_0/axi_resetn] [get_bd_pins hier_1/axi_resetn] [get_bd_pins hier_2/axi_resetn] [get_bd_pins hier_3/axi_resetn] [get_bd_pins rst_clk_wiz_1_225M/peripheral_aresetn]
  connect_bd_net -net rst_clk_wiz_1_225M_peripheral_reset [get_bd_pins MiCache_0/reset] [get_bd_pins rst_clk_wiz_1_225M/peripheral_reset]
  connect_bd_net -net rst_ddr4_0_300M_peripheral_aresetn [get_bd_pins axi_interconnect_ddr/M00_ARESETN] [get_bd_pins ddr4_0/c0_ddr4_aresetn] [get_bd_pins qdma_0_axi_periph/M07_ARESETN] [get_bd_pins qdma_0_axi_periph/M08_ARESETN] [get_bd_pins rst_ddr4_0_300M/peripheral_aresetn] [get_bd_pins smartconnect_ddr/aresetn]
  connect_bd_net -net util_ds_buf_IBUF_DS_ODIV2 [get_bd_pins qdma_0/sys_clk] [get_bd_pins util_ds_buf/IBUF_DS_ODIV2]
  connect_bd_net -net util_ds_buf_IBUF_OUT [get_bd_pins qdma_0/sys_clk_gt] [get_bd_pins util_ds_buf/IBUF_OUT]
  connect_bd_net -net xlconstant_0_dout [get_bd_pins qdma_0/qsts_out_rdy] [get_bd_pins qdma_0/tm_dsc_sts_rdy] [get_bd_pins xlconstant_0/dout]

  # Create address segments
  assign_bd_address -offset 0x00000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM00] -force
  assign_bd_address -offset 0x10000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM01] -force
  assign_bd_address -offset 0x20000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM02] -force
  assign_bd_address -offset 0x30000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM03] -force
  assign_bd_address -offset 0x40000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM04] -force
  assign_bd_address -offset 0x50000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM05] -force
  assign_bd_address -offset 0x60000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM06] -force
  assign_bd_address -offset 0x70000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM07] -force
  assign_bd_address -offset 0x80000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM08] -force
  assign_bd_address -offset 0x90000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM09] -force
  assign_bd_address -offset 0xA0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM10] -force
  assign_bd_address -offset 0xB0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM11] -force
  assign_bd_address -offset 0xC0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM12] -force
  assign_bd_address -offset 0xD0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM13] -force
  assign_bd_address -offset 0xE0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM14] -force
  assign_bd_address -offset 0xF0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces MiCache_0/io_out_0] [get_bd_addr_segs hbm_0/SAXI_00/HBM_MEM15] -force
  assign_bd_address -offset 0x000100000000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs MiCache_0/io_axiProfiling/reg0] -force
  assign_bd_address -offset 0x000100010000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_0/FullyPipelinedSpMV_0/s_axi_AXILiteS/reg0] -force
  assign_bd_address -offset 0x000100020000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_1/FullyPipelinedSpMV_0/s_axi_AXILiteS/reg0] -force
  assign_bd_address -offset 0x000100030000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_2/FullyPipelinedSpMV_0/s_axi_AXILiteS/reg0] -force
  assign_bd_address -offset 0x000100040000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_3/FullyPipelinedSpMV_0/s_axi_AXILiteS/reg0] -force
  assign_bd_address -offset 0x000100050000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_0/axi_dma_0/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100090000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_1/axi_dma_0/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x0001000D0000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_2/axi_dma_0/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100110000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_3/axi_dma_0/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100060000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_0/axi_dma_1/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x0001000A0000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_1/axi_dma_1/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x0001000E0000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_2/axi_dma_1/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100120000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_3/axi_dma_1/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100070000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_0/axi_dma_2/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x0001000B0000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_1/axi_dma_2/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x0001000F0000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_2/axi_dma_2/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100130000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_3/axi_dma_2/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100080000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_0/axi_dma_3/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x0001000C0000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_1/axi_dma_3/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100100000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_2/axi_dma_3/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000100140000 -range 0x00010000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hier_3/axi_dma_3/S_AXI_LITE/Reg] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000100200000 -range 0x00100000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP_CTRL/C0_REG] -force
  assign_bd_address -offset 0x00000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM00] -force
  assign_bd_address -offset 0x10000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM01] -force
  assign_bd_address -offset 0x20000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM02] -force
  assign_bd_address -offset 0x30000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM03] -force
  assign_bd_address -offset 0x40000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM04] -force
  assign_bd_address -offset 0x50000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM05] -force
  assign_bd_address -offset 0x60000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM06] -force
  assign_bd_address -offset 0x70000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM07] -force
  assign_bd_address -offset 0x80000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM08] -force
  assign_bd_address -offset 0x90000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM09] -force
  assign_bd_address -offset 0xA0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM10] -force
  assign_bd_address -offset 0xB0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM11] -force
  assign_bd_address -offset 0xC0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM12] -force
  assign_bd_address -offset 0xD0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM13] -force
  assign_bd_address -offset 0xE0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM14] -force
  assign_bd_address -offset 0xF0000000 -range 0x10000000 -target_address_space [get_bd_addr_spaces qdma_0/M_AXI] [get_bd_addr_segs hbm_0/SAXI_03/HBM_MEM15] -force
  assign_bd_address -offset 0x00000000 -range 0x000100000000 -target_address_space [get_bd_addr_spaces hier_0/FullyPipelinedSpMV_0/m_axi_vect] [get_bd_addr_segs MiCache_0/io_in_0/reg0] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_0/axi_dma_0/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_0/axi_dma_1/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_0/axi_dma_2/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_0/axi_dma_3/Data_S2MM] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x00000000 -range 0x000100000000 -target_address_space [get_bd_addr_spaces hier_1/FullyPipelinedSpMV_0/m_axi_vect] [get_bd_addr_segs MiCache_0/io_in_1/reg0] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_1/axi_dma_0/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_1/axi_dma_1/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_1/axi_dma_2/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_1/axi_dma_3/Data_S2MM] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x00000000 -range 0x000100000000 -target_address_space [get_bd_addr_spaces hier_2/FullyPipelinedSpMV_0/m_axi_vect] [get_bd_addr_segs MiCache_0/io_in_2/reg0] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_2/axi_dma_0/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_2/axi_dma_1/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_2/axi_dma_2/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_2/axi_dma_3/Data_S2MM] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x00000000 -range 0x000100000000 -target_address_space [get_bd_addr_spaces hier_3/FullyPipelinedSpMV_0/m_axi_vect] [get_bd_addr_segs MiCache_0/io_in_3/reg0] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_3/axi_dma_0/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_3/axi_dma_1/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_3/axi_dma_2/Data_MM2S] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force
  assign_bd_address -offset 0x000200000000 -range 0x000200000000 -target_address_space [get_bd_addr_spaces hier_3/axi_dma_3/Data_S2MM] [get_bd_addr_segs ddr4_0/C0_DDR4_MEMORY_MAP/C0_DDR4_ADDRESS_BLOCK] -force

  # Perform GUI Layout
  regenerate_bd_layout -layout_string {
   "ActiveEmotionalView":"Default View",
   "Addressing View_Layers":"/rst_clk_wiz_1_100M_interconnect_aresetn:false|/qdma_0_axi_aclk:false|/rst_clk_wiz_1_100M_peripheral_aresetn:false|/clk_wiz_1_clk_out2:false|/rst_ddr4_0_300M_peripheral_aresetn:false|/ddr4_0_c0_ddr4_ui_clk_sync_rst:false|/pcie_perstn_1:false|/util_ds_buf_IBUF_OUT:false|/qdma_0_axi_aresetn:false|/clk_wiz_2_clk_out1:false|/util_ds_buf_IBUF_DS_ODIV2:false|/rst_clk_wiz_1_100M_peripheral_reset:false|/ddr4_0_c0_ddr4_ui_clk:false|/resetn_1:false|",
   "Addressing View_ScaleFactor":"0.647083",
   "Addressing View_TopLeft":"-100,-190",
   "Color Coded_ScaleFactor":"0.39935",
   "Color Coded_TopLeft":"-133,-135",
   "Default View_Layers":"/rst_clk_wiz_1_100M_interconnect_aresetn:true|/qdma_0_axi_aclk:true|/rst_clk_wiz_1_100M_peripheral_aresetn:true|/clk_wiz_1_clk_out2:true|/rst_ddr4_0_300M_peripheral_aresetn:true|/ddr4_0_c0_ddr4_ui_clk_sync_rst:true|/pcie_perstn_1:true|/util_ds_buf_IBUF_OUT:true|/qdma_0_axi_aresetn:true|/clk_wiz_2_clk_out1:true|/util_ds_buf_IBUF_DS_ODIV2:true|/rst_clk_wiz_1_100M_peripheral_reset:true|/ddr4_0_c0_ddr4_ui_clk:true|/resetn_1:true|/rst_clk_wiz_1_150M_peripheral_reset:true|",
   "Default View_ScaleFactor":"0.204054",
   "Default View_TopLeft":"-1240,10",
   "Display-PortTypeClock":"true",
   "Display-PortTypeOthers":"true",
   "Display-PortTypeReset":"true",
   "ExpandedHierarchyInLayout":"",
   "Grouping and No Loops_ExpandedHierarchyInLayout":"",
   "Grouping and No Loops_Layout":"# # String gsaved with Nlview 7.0r4  2019-12-20 bk=1.5203 VDI=41 GEI=36 GUI=JA:9.0 TLS
#  -string -flagsOSRD
preplace port pci_express_x16 -pg 1 -lvl 8 -x 3100 -y 320 -defaultsOSRD
preplace port pcie_refclk -pg 1 -lvl 0 -x 0 -y 320 -defaultsOSRD
preplace port sysclk0 -pg 1 -lvl 0 -x 0 -y 880 -defaultsOSRD
preplace port ddr4_sdram_c1 -pg 1 -lvl 8 -x 3100 -y 880 -defaultsOSRD
preplace port pcie_perstn -pg 1 -lvl 0 -x 0 -y 420 -defaultsOSRD
preplace port resetn -pg 1 -lvl 0 -x 0 -y 1100 -defaultsOSRD
preplace inst qdma_0 -pg 1 -lvl 2 -x 520 -y 320 -swap {0 40 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 1 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 89 86 88 90 85 87} -defaultsOSRD -pinBusDir group_1 left -pinBusY group_1 0L -pinDir M_AXI right -pinY M_AXI 20R -pinDir pcie_mgt right -pinY pcie_mgt 0R -pinDir usr_irq left -pinY usr_irq 20L -pinDir dsc_crdt_in left -pinY dsc_crdt_in 40L -pinDir tm_dsc_sts right -pinY tm_dsc_sts 40R -pinDir tm_dsc_sts.tm_dsc_sts_rdy right -pinY tm_dsc_sts.tm_dsc_sts_rdy 60R -pinDir usr_flr left -pinY usr_flr 60L -pinDir qsts_out right -pinY qsts_out 80R -pinDir qsts_out.qsts_out_rdy right -pinY qsts_out.qsts_out_rdy 100R -pinDir sys_rst_n left -pinY sys_rst_n 100L -pinDir user_lnk_up right -pinY user_lnk_up 120R -pinDir axi_aclk right -pinY axi_aclk 160R -pinDir axi_aresetn right -pinY axi_aresetn 180R -pinDir soft_reset_n left -pinY soft_reset_n 80L -pinDir phy_ready right -pinY phy_ready 140R
preplace inst util_ds_buf -pg 1 -lvl 1 -x 150 -y 320 -defaultsOSRD -pinBusDir group_1 right -pinBusY group_1 0R -pinDir CLK_IN_D left -pinY CLK_IN_D 0L
preplace inst qdma_0_axi_periph -pg 1 -lvl 3 -x 990 -y 1000 -swap {5 0 4 3 2 1 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 81 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 45 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99 100 101 102 103 104 105 106 150 108 109 110 111 112 113 114 115 116 117 118 119 120 121 122 123 124 125 126 127 128 129 130 131 132 133 134 135 136 137 138 139 140 141 142 143 144 145 146 147 148 149 107 151 152 153 154 155 156 157 158 159 160 161 162 163 164 165 166 169 192 167 168 170 193 171 194 172 195 173 196 174 197 175 198 176 199 217 215 218 216 177 200 178 201 179 202 180 203 181 204 182 205 183 206 184 207 185 208 186 209 187 210 188 211 189 212 190 213 191 214} -defaultsOSRD -pinBusDir group_1 right -pinBusY group_1 100R -pinBusDir group_2 right -pinBusY group_2 0R -pinBusDir group_3 right -pinBusY group_3 80R -pinBusDir group_4 right -pinBusY group_4 60R -pinBusDir group_5 right -pinBusY group_5 40R -pinBusDir group_6 right -pinBusY group_6 20R -pinDir S00_AXI left -pinY S00_AXI 0L -pinDir M03_AXI right -pinY M03_AXI 160R -pinDir M04_AXI right -pinY M04_AXI 140R -pinDir M05_AXI right -pinY M05_AXI 120R -pinDir M07_AXI right -pinY M07_AXI 200R -pinDir M08_AXI right -pinY M08_AXI 180R -pinDir ACLK left -pinY ACLK 60L -pinDir ARESETN left -pinY ARESETN 520L -pinDir S00_ACLK left -pinY S00_ACLK 20L -pinDir S00_ARESETN left -pinY S00_ARESETN 40L -pinDir M00_ACLK left -pinY M00_ACLK 80L -pinDir M00_ARESETN left -pinY M00_ARESETN 540L -pinDir M01_ACLK left -pinY M01_ACLK 100L -pinDir M01_ARESETN left -pinY M01_ARESETN 560L -pinDir M02_ACLK left -pinY M02_ACLK 120L -pinDir M02_ARESETN left -pinY M02_ARESETN 580L -pinDir M03_ACLK left -pinY M03_ACLK 140L -pinDir M03_ARESETN left -pinY M03_ARESETN 600L -pinDir M04_ACLK left -pinY M04_ACLK 160L -pinDir M04_ARESETN left -pinY M04_ARESETN 620L -pinDir M05_ACLK left -pinY M05_ACLK 180L -pinDir M05_ARESETN left -pinY M05_ARESETN 640L -pinDir M06_ACLK left -pinY M06_ACLK 200L -pinDir M06_ARESETN left -pinY M06_ARESETN 660L -pinDir M07_ACLK left -pinY M07_ACLK 1020L -pinDir M07_ARESETN left -pinY M07_ARESETN 980L -pinDir M08_ACLK left -pinY M08_ACLK 1040L -pinDir M08_ARESETN left -pinY M08_ARESETN 1000L -pinDir M09_ACLK left -pinY M09_ACLK 220L -pinDir M09_ARESETN left -pinY M09_ARESETN 680L -pinDir M10_ACLK left -pinY M10_ACLK 240L -pinDir M10_ARESETN left -pinY M10_ARESETN 700L -pinDir M11_ACLK left -pinY M11_ACLK 260L -pinDir M11_ARESETN left -pinY M11_ARESETN 720L -pinDir M12_ACLK left -pinY M12_ACLK 280L -pinDir M12_ARESETN left -pinY M12_ARESETN 740L -pinDir M13_ACLK left -pinY M13_ACLK 300L -pinDir M13_ARESETN left -pinY M13_ARESETN 760L -pinDir M14_ACLK left -pinY M14_ACLK 320L -pinDir M14_ARESETN left -pinY M14_ARESETN 780L -pinDir M15_ACLK left -pinY M15_ACLK 340L -pinDir M15_ARESETN left -pinY M15_ARESETN 800L -pinDir M16_ACLK left -pinY M16_ACLK 360L -pinDir M16_ARESETN left -pinY M16_ARESETN 820L -pinDir M17_ACLK left -pinY M17_ACLK 380L -pinDir M17_ARESETN left -pinY M17_ARESETN 840L -pinDir M18_ACLK left -pinY M18_ACLK 400L -pinDir M18_ARESETN left -pinY M18_ARESETN 860L -pinDir M19_ACLK left -pinY M19_ACLK 420L -pinDir M19_ARESETN left -pinY M19_ARESETN 880L -pinDir M20_ACLK left -pinY M20_ACLK 440L -pinDir M20_ARESETN left -pinY M20_ARESETN 900L -pinDir M21_ACLK left -pinY M21_ACLK 460L -pinDir M21_ARESETN left -pinY M21_ARESETN 920L -pinDir M22_ACLK left -pinY M22_ACLK 480L -pinDir M22_ARESETN left -pinY M22_ARESETN 940L -pinDir M23_ACLK left -pinY M23_ACLK 500L -pinDir M23_ARESETN left -pinY M23_ARESETN 960L
preplace inst xlconstant_0 -pg 1 -lvl 2 -x 520 -y 600 -defaultsOSRD -pinBusDir dout right -pinBusY dout 0R
preplace inst hbm_0 -pg 1 -lvl 7 -x 2850 -y 60 -swap {30 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 0 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 61 64 68 60 63 66 67 62 65 69 70 71 72 73} -defaultsOSRD -pinDir SAXI_00 left -pinY SAXI_00 20L -pinDir SAXI_04 left -pinY SAXI_04 0L -pinDir HBM_REF_CLK_0 left -pinY HBM_REF_CLK_0 60L -pinDir AXI_00_ACLK left -pinY AXI_00_ACLK 120L -pinDir AXI_00_ARESET_N left -pinY AXI_00_ARESET_N 200L -pinBusDir AXI_00_WDATA_PARITY left -pinBusY AXI_00_WDATA_PARITY 40L -pinDir AXI_04_ACLK left -pinY AXI_04_ACLK 100L -pinDir AXI_04_ARESET_N left -pinY AXI_04_ARESET_N 160L -pinBusDir AXI_04_WDATA_PARITY left -pinBusY AXI_04_WDATA_PARITY 180L -pinDir APB_0_PCLK left -pinY APB_0_PCLK 80L -pinDir APB_0_PRESET_N left -pinY APB_0_PRESET_N 140L -pinBusDir AXI_00_RDATA_PARITY right -pinBusY AXI_00_RDATA_PARITY 0R -pinBusDir AXI_04_RDATA_PARITY right -pinBusY AXI_04_RDATA_PARITY 20R -pinDir apb_complete_0 right -pinY apb_complete_0 40R -pinDir DRAM_0_STAT_CATTRIP right -pinY DRAM_0_STAT_CATTRIP 60R -pinBusDir DRAM_0_STAT_TEMP right -pinBusY DRAM_0_STAT_TEMP 80R
preplace inst clk_wiz_1 -pg 1 -lvl 1 -x 150 -y 740 -swap {3 0 1 2 4} -defaultsOSRD -pinDir reset right -pinY reset 60R -pinDir clk_out1 right -pinY clk_out1 0R -pinDir clk_out2 right -pinY clk_out2 20R -pinDir locked right -pinY locked 40R -pinDir clk_in1 right -pinY clk_in1 80R
preplace inst rst_clk_wiz_1_150M -pg 1 -lvl 2 -x 520 -y 720 -swap {2 0 1 4 3 6 7 5 9 8} -defaultsOSRD -pinDir slowest_sync_clk left -pinY slowest_sync_clk 40L -pinDir ext_reset_in left -pinY ext_reset_in 0L -pinDir aux_reset_in left -pinY aux_reset_in 20L -pinDir mb_debug_sys_rst left -pinY mb_debug_sys_rst 80L -pinDir dcm_locked left -pinY dcm_locked 60L -pinDir mb_reset right -pinY mb_reset 20R -pinBusDir bus_struct_reset right -pinBusY bus_struct_reset 40R -pinBusDir peripheral_reset right -pinBusY peripheral_reset 0R -pinBusDir interconnect_aresetn right -pinBusY interconnect_aresetn 80R -pinBusDir peripheral_aresetn right -pinBusY peripheral_aresetn 60R
preplace inst hier_0 -pg 1 -lvl 4 -x 1570 -y 540 -swap {35 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 36 0} -defaultsOSRD -pinBusDir group_1 left -pinBusY group_1 40L -pinBusDir group_2 right -pinBusY group_2 40R -pinDir m_axi_vect right -pinY m_axi_vect 60R -pinDir s_axi_AXILiteS left -pinY s_axi_AXILiteS 20L -pinDir m_axi_mm2s_aclk left -pinY m_axi_mm2s_aclk 60L -pinDir axi_resetn left -pinY axi_resetn 0L
preplace inst ddr4_0 -pg 1 -lvl 7 -x 2850 -y 880 -swap {0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 81 76 78 79 80 77} -defaultsOSRD -pinDir C0_SYS_CLK left -pinY C0_SYS_CLK 0L -pinDir C0_DDR4 right -pinY C0_DDR4 0R -pinDir C0_DDR4_S_AXI_CTRL left -pinY C0_DDR4_S_AXI_CTRL 20L -pinDir C0_DDR4_S_AXI left -pinY C0_DDR4_S_AXI 200L -pinDir c0_init_calib_complete right -pinY c0_init_calib_complete 20R -pinDir dbg_clk right -pinY dbg_clk 40R -pinBusDir dbg_bus right -pinBusY dbg_bus 60R -pinDir c0_ddr4_ui_clk left -pinY c0_ddr4_ui_clk 400L -pinDir c0_ddr4_ui_clk_sync_rst left -pinY c0_ddr4_ui_clk_sync_rst 220L -pinDir c0_ddr4_aresetn left -pinY c0_ddr4_aresetn 260L -pinDir c0_ddr4_interrupt right -pinY c0_ddr4_interrupt 80R -pinDir addn_ui_clkout1 right -pinY addn_ui_clkout1 100R -pinDir sys_rst left -pinY sys_rst 240L
preplace inst rst_ddr4_0_300M -pg 1 -lvl 2 -x 520 -y 940 -swap {1 0 2 3 4 5 6 7 8 9} -defaultsOSRD -pinDir slowest_sync_clk left -pinY slowest_sync_clk 20L -pinDir ext_reset_in left -pinY ext_reset_in 0L -pinDir aux_reset_in left -pinY aux_reset_in 40L -pinDir mb_debug_sys_rst left -pinY mb_debug_sys_rst 60L -pinDir dcm_locked left -pinY dcm_locked 80L -pinDir mb_reset right -pinY mb_reset 0R -pinBusDir bus_struct_reset right -pinBusY bus_struct_reset 20R -pinBusDir peripheral_reset right -pinBusY peripheral_reset 40R -pinBusDir interconnect_aresetn right -pinBusY interconnect_aresetn 60R -pinBusDir peripheral_aresetn right -pinBusY peripheral_aresetn 80R
preplace inst resetn_inv_0 -pg 1 -lvl 6 -x 2410 -y 960 -defaultsOSRD -pinBusDir Op1 left -pinBusY Op1 0L -pinBusDir Res right -pinBusY Res 0R
preplace inst axi_interconnect_0 -pg 1 -lvl 5 -x 2090 -y 1180 -swap {54 71 67 0 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 58 2 68 42 75 56 72 51 66 47 57 46 62 44 60 43 59 76 70 63 55 45 65 48 64 1 73 52 77 50 69 3 49 74 61 53} -defaultsOSRD -pinBusDir group_1 left -pinBusY group_1 320L -pinBusDir group_2 left -pinBusY group_2 660L -pinBusDir group_3 left -pinBusY group_3 580L -pinBusDir group_4 left -pinBusY group_4 0L -pinDir M00_AXI right -pinY M00_AXI 0R -pinDir ACLK left -pinY ACLK 400L -pinDir ARESETN left -pinY ARESETN 40L -pinDir S00_ACLK left -pinY S00_ACLK 600L -pinDir S00_ARESETN left -pinY S00_ARESETN 80L -pinDir M00_ACLK left -pinY M00_ACLK 740L -pinDir M00_ARESETN left -pinY M00_ARESETN 360L -pinDir S01_ACLK left -pinY S01_ACLK 680L -pinDir S01_ARESETN left -pinY S01_ARESETN 260L -pinDir S02_ACLK left -pinY S02_ACLK 560L -pinDir S02_ARESETN left -pinY S02_ARESETN 180L -pinDir S03_ACLK left -pinY S03_ACLK 380L -pinDir S03_ARESETN left -pinY S03_ARESETN 160L -pinDir S04_ACLK left -pinY S04_ACLK 480L -pinDir S04_ARESETN left -pinY S04_ARESETN 120L -pinDir S05_ACLK left -pinY S05_ACLK 440L -pinDir S05_ARESETN left -pinY S05_ARESETN 100L -pinDir S06_ACLK left -pinY S06_ACLK 420L -pinDir S06_ARESETN left -pinY S06_ARESETN 760L -pinDir S07_ACLK left -pinY S07_ACLK 640L -pinDir S07_ARESETN left -pinY S07_ARESETN 500L -pinDir S08_ACLK left -pinY S08_ACLK 340L -pinDir S08_ARESETN left -pinY S08_ARESETN 140L -pinDir S09_ACLK left -pinY S09_ACLK 540L -pinDir S09_ARESETN left -pinY S09_ARESETN 200L -pinDir S10_ACLK left -pinY S10_ACLK 520L -pinDir S10_ARESETN left -pinY S10_ARESETN 20L -pinDir S11_ACLK left -pinY S11_ACLK 700L -pinDir S11_ARESETN left -pinY S11_ARESETN 280L -pinDir S12_ACLK left -pinY S12_ACLK 780L -pinDir S12_ARESETN left -pinY S12_ARESETN 240L -pinDir S13_ACLK left -pinY S13_ACLK 620L -pinDir S13_ARESETN left -pinY S13_ARESETN 60L -pinDir S14_ACLK left -pinY S14_ACLK 220L -pinDir S14_ARESETN left -pinY S14_ARESETN 720L -pinDir S15_ACLK left -pinY S15_ACLK 460L -pinDir S15_ARESETN left -pinY S15_ARESETN 300L
preplace inst resetn_inv_1 -pg 1 -lvl 1 -x 150 -y 620 -swap {1 0} -defaultsOSRD -pinBusDir Op1 right -pinBusY Op1 20R -pinBusDir Res right -pinBusY Res 0R
preplace inst smartconnect_0 -pg 1 -lvl 6 -x 2410 -y 380 -defaultsOSRD -pinDir S00_AXI left -pinY S00_AXI 110L -pinDir M00_AXI right -pinY M00_AXI 0R -pinDir aclk left -pinY aclk 130L -pinDir aresetn left -pinY aresetn 150L
preplace inst hier_1 -pg 1 -lvl 4 -x 1570 -y 730 -swap {0 18 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 19 1} -defaultsOSRD -pinBusDir group_1 right -pinBusY group_1 0R -pinBusDir group_2 left -pinBusY group_2 40L -pinBusDir group_3 left -pinBusY group_3 20L -pinDir m_axi_vect right -pinY m_axi_vect 20R -pinDir m_axi_mm2s_aclk left -pinY m_axi_mm2s_aclk 60L -pinDir axi_resetn left -pinY axi_resetn 0L
preplace inst smartconnect_1 -pg 1 -lvl 6 -x 2410 -y 1080 -swap {46 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 0 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99 100 101 102 103 104 105 106 107 108 109 110 111 112 113 114 115 116 117 118 119 120 121 122 123 124 125 126 127 128 129 130 131 132 133 134 135 136 137 138 139} -defaultsOSRD -pinDir S00_AXI left -pinY S00_AXI 100L -pinDir S01_AXI left -pinY S01_AXI 0L -pinDir M00_AXI right -pinY M00_AXI 0R -pinDir aclk left -pinY aclk 120L -pinDir aresetn left -pinY aresetn 140L
preplace inst hier_2 -pg 1 -lvl 4 -x 1570 -y 1330 -swap {17 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 18 0} -defaultsOSRD -pinBusDir group_1 left -pinBusY group_1 20L -pinBusDir group_2 right -pinBusY group_2 0R -pinDir m_axi_vect right -pinY m_axi_vect 20R -pinDir m_axi_mm2s_aclk left -pinY m_axi_mm2s_aclk 40L -pinDir axi_resetn left -pinY axi_resetn 0L
preplace inst hier_3 -pg 1 -lvl 4 -x 1570 -y 1140 -swap {0 18 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 19 1} -defaultsOSRD -pinBusDir group_1 right -pinBusY group_1 0R -pinBusDir group_2 left -pinBusY group_2 40L -pinBusDir group_3 left -pinBusY group_3 20L -pinDir m_axi_vect right -pinY m_axi_vect 20R -pinDir m_axi_mm2s_aclk left -pinY m_axi_mm2s_aclk 60L -pinDir axi_resetn left -pinY axi_resetn 0L
preplace netloc netgroup_1 1 3 1 1140 1100n
preplace netloc netgroup_2 1 4 1 1860 1140n
preplace netloc netgroup_3 1 3 1 1180 580n
preplace netloc netgroup_4 1 3 1 1340 1080n
preplace netloc netgroup_5 1 3 1 1360 1060n
preplace netloc netgroup_6 1 4 1 1760 730n
preplace netloc netgroup_7 1 4 1 1720 1330n
preplace netloc netgroup_8 1 4 1 1940 580n
preplace netloc netgroup_9 1 3 1 1400 770n
preplace netloc netgroup_10 1 3 1 1220 750n
preplace netloc netgroup_11 1 1 1 N 320
preplace netloc pcie_perstn_1 1 0 2 NJ 420 NJ
preplace netloc xlconstant_0_dout 1 2 1 740 380n
preplace netloc qdma_0_axi_aclk 1 2 1 820 480n
preplace netloc qdma_0_axi_aresetn 1 2 1 780 500n
preplace netloc rst_clk_wiz_1_100M_interconnect_aresetn 1 2 1 760 800n
preplace netloc clk_wiz_1_clk_out2 1 1 6 280 260 NJ 260 NJ 260 NJ 260 NJ 260 2560
preplace netloc rst_clk_wiz_1_100M_peripheral_aresetn 1 2 5 N 780 1380 840 1920 840 2260 300 2620
preplace netloc clk_wiz_2_clk_out1 1 1 6 320 660 740 2100 1420 1270 1880 820 2240 280 2600
preplace netloc resetn_1 1 0 6 NJ 1100 NJ 1100 700J 900 1300J 920 NJ 920 2240J
preplace netloc clk_wiz_1_locked 1 1 1 N 780
preplace netloc rst_clk_wiz_1_100M_peripheral_reset 1 2 3 760 670 NJ 670 1780J
preplace netloc ddr4_0_c0_ddr4_ui_clk 1 1 6 280 1080 720 920 1280J 940 1740 1100 2240 1280 NJ
preplace netloc ddr4_0_c0_ddr4_ui_clk_sync_rst 1 1 6 300 860 NJ 860 NJ 860 NJ 860 NJ 860 2620J
preplace netloc rst_ddr4_0_300M_peripheral_aresetn 1 2 5 800 940 1240J 1250 1900 1120 2260 1020 2560
preplace netloc resetn_inv_0_Res 1 6 1 2580J 960n
preplace netloc resetn_inv_1_Res 1 1 1 340J 620n
preplace netloc FullyPipelinedSpMV_0_m_axi_vect 1 4 1 N 600
preplace netloc qdma_0_axi_periph_M04_AXI 1 3 1 1200 560n
preplace netloc qdma_0_M_AXI 1 2 1 840 340n
preplace netloc pcie_refclk_1 1 0 1 NJ 320
preplace netloc qdma_0_pcie_mgt 1 2 6 NJ 320 NJ 320 NJ 320 NJ 320 NJ 320 NJ
preplace netloc qdma_0_axi_periph_M03_AXI 1 3 2 1260 650 NJ
preplace netloc qdma_0_axi_periph_M08_AXI 1 3 4 1320 900 NJ 900 NJ 900 NJ
preplace netloc qdma_0_axi_periph_M05_AXI 1 3 4 1160 60 NJ 60 NJ 60 NJ
preplace netloc sysclk0_1 1 0 7 NJ 880 NJ 880 NJ 880 NJ 880 NJ 880 NJ 880 NJ
preplace netloc ddr4_0_C0_DDR4 1 7 1 NJ 880
preplace netloc smartconnect_0_M00_AXI 1 6 1 2580 80n
preplace netloc hier_1_m_axi_vect 1 4 1 1800 680n
preplace netloc smartconnect_1_M00_AXI 1 6 1 N 1080
preplace netloc axi_interconnect_0_M00_AXI 1 5 1 N 1180
preplace netloc qdma_0_axi_periph_M07_AXI 1 3 3 1400 1080 NJ 1080 NJ
preplace netloc hier_3_m_axi_vect 1 4 1 1820 700n
preplace netloc hier_2_m_axi_vect 1 4 1 1840 720n
levelinfo -pg 1 0 150 520 990 1570 2090 2410 2850 3100
pagesize -pg 1 -db -bbox -sgen -140 0 3270 2110
",
   "Grouping and No Loops_ScaleFactor":"0.389573",
   "Grouping and No Loops_TopLeft":"-480,0",
   "Interfaces View_Layers":"/rst_clk_wiz_1_100M_interconnect_aresetn:false|/qdma_0_axi_aclk:false|/rst_clk_wiz_1_100M_peripheral_aresetn:false|/clk_wiz_1_clk_out2:false|/rst_ddr4_0_300M_peripheral_aresetn:false|/ddr4_0_c0_ddr4_ui_clk_sync_rst:false|/pcie_perstn_1:false|/util_ds_buf_IBUF_OUT:false|/qdma_0_axi_aresetn:false|/clk_wiz_2_clk_out1:false|/util_ds_buf_IBUF_DS_ODIV2:false|/rst_clk_wiz_1_100M_peripheral_reset:false|/ddr4_0_c0_ddr4_ui_clk:false|/resetn_1:false|/rst_clk_wiz_1_150M_peripheral_reset:false|",
   "Interfaces View_ScaleFactor":"0.630478",
   "Interfaces View_TopLeft":"-125,0",
   "No Loops_ScaleFactor":"0.314943",
   "No Loops_TopLeft":"-597,0",
   "Reduced Jogs_Layers":"/rst_clk_wiz_1_100M_interconnect_aresetn:true|/qdma_0_axi_aclk:true|/rst_clk_wiz_1_100M_peripheral_aresetn:true|/clk_wiz_1_clk_out2:true|/rst_ddr4_0_300M_peripheral_aresetn:true|/ddr4_0_c0_ddr4_ui_clk_sync_rst:true|/pcie_perstn_1:true|/util_ds_buf_IBUF_OUT:true|/qdma_0_axi_aresetn:true|/clk_wiz_2_clk_out1:true|/util_ds_buf_IBUF_DS_ODIV2:true|/rst_clk_wiz_1_100M_peripheral_reset:true|/ddr4_0_c0_ddr4_ui_clk:true|/resetn_1:true|",
   "Reduced Jogs_ScaleFactor":"0.332794",
   "Reduced Jogs_TopLeft":"-532,0",
   "guistr":"# # String gsaved with Nlview 7.0r4  2019-12-20 bk=1.5203 VDI=41 GEI=36 GUI=JA:10.0 TLS
#  -string -flagsOSRD
preplace port pci_express_x16 -pg 1 -lvl 8 -x 3260 -y 1900 -defaultsOSRD
preplace port pcie_refclk -pg 1 -lvl 0 -x 0 -y 2080 -defaultsOSRD
preplace port sysclk0 -pg 1 -lvl 0 -x 0 -y 1190 -defaultsOSRD
preplace port ddr4_sdram_c1 -pg 1 -lvl 8 -x 3260 -y 1170 -defaultsOSRD
preplace port pcie_perstn -pg 1 -lvl 0 -x 0 -y 2010 -defaultsOSRD
preplace port resetn -pg 1 -lvl 0 -x 0 -y 1210 -defaultsOSRD
preplace inst xlconstant_0 -pg 1 -lvl 2 -x 570 -y 2160 -defaultsOSRD
preplace inst rst_clk_wiz_1_225M -pg 1 -lvl 2 -x 570 -y 1400 -defaultsOSRD
preplace inst hier_0 -pg 1 -lvl 4 -x 1440 -y 300 -defaultsOSRD
preplace inst rst_ddr4_0_300M -pg 1 -lvl 2 -x 570 -y 1070 -defaultsOSRD
preplace inst hier_1 -pg 1 -lvl 4 -x 1440 -y 550 -defaultsOSRD
preplace inst hier_2 -pg 1 -lvl 4 -x 1440 -y 1370 -defaultsOSRD
preplace inst hier_3 -pg 1 -lvl 4 -x 1440 -y 800 -defaultsOSRD
preplace inst proc_sys_reset_450M -pg 1 -lvl 5 -x 2050 -y 1800 -defaultsOSRD
preplace inst proc_sys_reset_100M -pg 1 -lvl 6 -x 2520 -y 1700 -defaultsOSRD
preplace inst axi_interconnect_ddr -pg 1 -lvl 5 -x 2050 -y 610 -defaultsOSRD
preplace inst clk_wiz_1 -pg 1 -lvl 1 -x 190 -y 1520 -defaultsOSRD
preplace inst ddr4_0 -pg 1 -lvl 7 -x 3000 -y 1240 -defaultsOSRD
preplace inst hbm_0 -pg 1 -lvl 7 -x 3000 -y 1530 -defaultsOSRD
preplace inst qdma_0 -pg 1 -lvl 2 -x 570 -y 1970 -defaultsOSRD
preplace inst qdma_0_axi_periph -pg 1 -lvl 3 -x 970 -y 580 -defaultsOSRD
preplace inst resetn_inv_0 -pg 1 -lvl 6 -x 2520 -y 1280 -defaultsOSRD
preplace inst resetn_inv_1 -pg 1 -lvl 1 -x 190 -y 1390 -defaultsOSRD
preplace inst smartconnect_ddr -pg 1 -lvl 6 -x 2520 -y 640 -defaultsOSRD
preplace inst util_ds_buf -pg 1 -lvl 1 -x 190 -y 2080 -defaultsOSRD
preplace inst axi_interconnect_0 -pg 1 -lvl 6 -x 2520 -y 1470 -defaultsOSRD
preplace inst MiCache_0 -pg 1 -lvl 5 -x 2050 -y 1490 -defaultsOSRD
preplace netloc pcie_perstn_1 1 0 2 NJ 2010 NJ
preplace netloc util_ds_buf_IBUF_OUT 1 1 1 380 1990n
preplace netloc util_ds_buf_IBUF_DS_ODIV2 1 1 1 360 1970n
preplace netloc xlconstant_0_dout 1 2 1 740 1940n
preplace netloc qdma_0_axi_aclk 1 2 1 770 120n
preplace netloc qdma_0_axi_aresetn 1 2 1 790 140n
preplace netloc rst_clk_wiz_1_100M_interconnect_aresetn 1 2 1 750 100n
preplace netloc clk_wiz_1_clk_out2 1 1 6 340J 1180 NJ 1180 NJ 1180 1750J 1250 2260 1340 2700
preplace netloc rst_clk_wiz_1_100M_peripheral_aresetn 1 2 5 N 1440 1180 150 1850 30 2290 1220 2720J
preplace netloc clk_wiz_2_clk_out1 1 1 6 360 1190 760 1190 1210 160 1710 1280 2270 1350 2710
preplace netloc resetn_1 1 0 6 NJ 1210 NJ 1210 780J 1200 NJ 1200 1660J 1260 2280J
preplace netloc clk_wiz_1_locked 1 1 1 370 1440n
preplace netloc ddr4_0_c0_ddr4_ui_clk 1 0 8 20 1030 390 970 800 1160 NJ 1160 1660 40 2300 730 NJ 730 3210
preplace netloc ddr4_0_c0_ddr4_ui_clk_sync_rst 1 0 8 30 1220 380 1220 NJ 1220 1250J 1190 NJ 1190 2220J 1110 NJ 1110 3200
preplace netloc rst_ddr4_0_300M_peripheral_aresetn 1 2 5 810 1210 NJ 1210 1760 1200 2260 1180 2750J
preplace netloc resetn_inv_0_Res 1 6 1 NJ 1280
preplace netloc resetn_inv_1_Res 1 1 5 380 1500 NJ 1500 NJ 1500 1580 1690 2310
preplace netloc clk_wiz_1_clk_out3 1 1 6 N 1530 NJ 1530 NJ 1530 1600 1290 2250 1590 2750J
preplace netloc proc_sys_reset_450M_peripheral_aresetn 1 5 2 2290 1600 2760J
preplace netloc proc_sys_reset_100M_peripheral_aresetn 1 6 1 2730 1630n
preplace netloc MiCache_0_io_pe_all_running 1 3 3 1270 1240 NJ 1240 2220
preplace netloc rst_clk_wiz_1_225M_peripheral_reset 1 2 3 760 1430 1120J 1490 1580J
preplace netloc hier_0_running 1 4 1 1780 340n
preplace netloc hier_0_done 1 4 1 1670 360n
preplace netloc hier_1_running 1 4 1 1730 590n
preplace netloc hier_1_done 1 4 1 1650 610n
preplace netloc hier_2_running 1 4 1 1610 1430n
preplace netloc hier_2_done 1 4 1 1590 1410n
preplace netloc hier_3_running 1 4 1 1630 860n
preplace netloc hier_3_done 1 4 1 1620 840n
preplace netloc MiCache_0_io_reset_out 1 1 5 390 1170 NJ 1170 NJ 1170 1770J 1230 2230
preplace netloc qdma_0_axi_periph_M05_AXI 1 3 4 1140 170 1840J 1180 2230J 1190 2730J
preplace netloc sysclk0_1 1 0 7 NJ 1190 350J 1230 NJ 1230 1260J 1220 NJ 1220 2280J 1200 NJ
preplace netloc qdma_0_pcie_mgt 1 2 6 NJ 1900 NJ 1900 NJ 1900 NJ 1900 NJ 1900 NJ
preplace netloc hier_2_M_AXI_MM2S2 1 4 1 1690 300n
preplace netloc qdma_0_axi_periph_M02_AXI 1 3 1 1150 270n
preplace netloc hier_3_M_AXI_MM2S 1 4 1 1720 160n
preplace netloc s_axi_AXILiteS_2 1 3 1 N 790
preplace netloc s_axi_AXILiteS_1 1 3 1 1250 540n
preplace netloc qdma_0_axi_periph_M06_AXI 1 3 1 1170 310n
preplace netloc hier_1_M_AXI_MM2S 1 4 1 1600 100n
preplace netloc S_AXI_LITE1_1 1 3 1 N 750
preplace netloc qdma_0_axi_periph_M14_AXI 1 3 1 1200 630n
preplace netloc hier_0_M_AXI_S2MM 1 4 1 1680 300n
preplace netloc qdma_0_axi_periph_M18_AXI 1 3 1 1110 710n
preplace netloc qdma_0_axi_periph_M23_AXI 1 3 1 N 810
preplace netloc qdma_0_axi_periph_M13_AXI 1 3 1 1260 560n
preplace netloc qdma_0_M_AXI 1 2 1 740 60n
preplace netloc qdma_0_axi_periph_M16_AXI 1 3 1 1130 670n
preplace netloc qdma_0_axi_periph_M17_AXI 1 3 1 1120 690n
preplace netloc S05_AXI_1 1 4 1 1620 200n
preplace netloc S_AXI_LITE2_1 1 3 1 1240 520n
preplace netloc qdma_0_axi_periph_M19_AXI 1 3 1 N 730
preplace netloc hier_2_M_AXI_S2MM 1 4 1 1580 380n
preplace netloc qdma_0_axi_periph_M21_AXI 1 3 1 N 770
preplace netloc qdma_0_axi_periph_M04_AXI 1 3 1 1160 290n
preplace netloc S_AXI_LITE_1 1 3 1 1200 480n
preplace netloc qdma_0_axi_periph_M00_AXI 1 3 1 1110 230n
preplace netloc hier_3_M_AXI_S2MM 1 4 1 1830 400n
preplace netloc hier_0_M_AXI_MM2S 1 4 1 1590 120n
preplace netloc qdma_0_axi_periph_M01_AXI 1 3 1 1130 250n
preplace netloc qdma_0_axi_periph_M07_AXI 1 3 3 1120J 20 NJ 20 2310
preplace netloc qdma_0_axi_periph_M10_AXI 1 3 1 1220 500n
preplace netloc hier_1_m_axi_vect 1 4 1 1820 570n
preplace netloc hier_3_m_axi_vect 1 4 1 1680 820n
preplace netloc MiCache_0_io_out_0 1 5 1 2240 1410n
preplace netloc qdma_0_axi_periph_M03_AXI 1 3 2 1220 420 1700J
preplace netloc hier_0_m_axi_vect 1 4 1 1590 320n
preplace netloc hier_2_m_axi_vect 1 4 1 1640 1390n
preplace netloc qdma_0_axi_periph_M15_AXI 1 3 1 1190 650n
preplace netloc pcie_refclk_1 1 0 1 NJ 2080
preplace netloc axi_interconnect_0_M00_AXI 1 5 1 N 610
preplace netloc hier_1_M_AXI_S2MM 1 4 1 1790 340n
preplace netloc hier_1_M_AXI_MM2S1 1 4 1 1650 180n
preplace netloc hier_1_M_AXI_MM2S2 1 4 1 1730 260n
preplace netloc ddr4_0_C0_DDR4 1 7 1 NJ 1170
preplace netloc qdma_0_axi_periph_M08_AXI 1 3 4 1230J 1110 1810J 1210 NJ 1210 2740
preplace netloc hier_2_M_AXI_MM2S 1 4 1 1740 140n
preplace netloc axi_interconnect_1_M00_AXI 1 6 1 2690 1430n
preplace netloc smartconnect_1_M00_AXI 1 6 1 2760 640n
preplace netloc hier_2_M_AXI_MM2S1 1 4 1 1800 220n
preplace netloc hier_0_M_AXI_MM2S2 1 4 1 N 280
preplace netloc hier_3_M_AXI_MM2S2 1 4 1 1620 320n
preplace netloc S07_AXI_1 1 4 1 1760 240n
levelinfo -pg 1 0 190 570 970 1440 2050 2520 3000 3260
pagesize -pg 1 -db -bbox -sgen -130 0 3420 2220
"
}

  # Restore current instance
  current_bd_instance $oldCurInst

  validate_bd_design
  save_bd_design
  # close_bd_design $design_name 
}
# End of cr_bd_design_1()
cr_bd_design_1 ""
set_property REGISTERED_WITH_MANAGER "1" [get_files design_1.bd ] 
set_property SYNTH_CHECKPOINT_MODE "Hierarchical" [get_files design_1.bd ] 

#call make_wrapper to create wrapper files
set wrapper_path [make_wrapper -fileset sources_1 -files [ get_files -norecurse design_1.bd] -top]
add_files -norecurse -fileset sources_1 $wrapper_path

# Create 'synth_1' run (if not found)
if {[string equal [get_runs -quiet synth_1] ""]} {
    create_run -name synth_1 -part xcu280-fsvh2892-2L-e -flow {Vivado Synthesis 2020} -strategy "Vivado Synthesis Defaults" -report_strategy {No Reports} -constrset constrs_1
} else {
  set_property strategy "Vivado Synthesis Defaults" [get_runs synth_1]
  set_property flow "Vivado Synthesis 2020" [get_runs synth_1]
}
set obj [get_runs synth_1]
set_property set_report_strategy_name 1 $obj
set_property report_strategy {Vivado Synthesis Default Reports} $obj
set_property set_report_strategy_name 0 $obj
# Create 'synth_1_synth_report_utilization_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs synth_1] synth_1_synth_report_utilization_0] "" ] } {
  create_report_config -report_name synth_1_synth_report_utilization_0 -report_type report_utilization:1.0 -steps synth_design -runs synth_1
}
set obj [get_report_configs -of_objects [get_runs synth_1] synth_1_synth_report_utilization_0]
if { $obj != "" } {

}
set obj [get_runs synth_1]
set_property -name "strategy" -value "Vivado Synthesis Defaults" -objects $obj

# set the current synth run
current_run -synthesis [get_runs synth_1]

# Create 'impl_1' run (if not found)
if {[string equal [get_runs -quiet impl_1] ""]} {
    create_run -name impl_1 -part xcu280-fsvh2892-2L-e -flow {Vivado Implementation 2020} -strategy "Vivado Implementation Defaults" -report_strategy {No Reports} -constrset constrs_1 -parent_run synth_1
} else {
  set_property strategy "Vivado Implementation Defaults" [get_runs impl_1]
  set_property flow "Vivado Implementation 2020" [get_runs impl_1]
}
set obj [get_runs impl_1]
set_property set_report_strategy_name 1 $obj
set_property report_strategy {Vivado Implementation Default Reports} $obj
set_property set_report_strategy_name 0 $obj
# Create 'impl_1_init_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_init_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_init_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps init_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_init_report_timing_summary_0]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj
set_property -name "options.max_paths" -value "10" -objects $obj

}
# Create 'impl_1_opt_report_drc_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_opt_report_drc_0] "" ] } {
  create_report_config -report_name impl_1_opt_report_drc_0 -report_type report_drc:1.0 -steps opt_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_opt_report_drc_0]
if { $obj != "" } {

}
# Create 'impl_1_opt_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_opt_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_opt_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps opt_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_opt_report_timing_summary_0]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj
set_property -name "options.max_paths" -value "10" -objects $obj

}
# Create 'impl_1_power_opt_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_power_opt_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_power_opt_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps power_opt_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_power_opt_report_timing_summary_0]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj
set_property -name "options.max_paths" -value "10" -objects $obj

}
# Create 'impl_1_place_report_io_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_io_0] "" ] } {
  create_report_config -report_name impl_1_place_report_io_0 -report_type report_io:1.0 -steps place_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_io_0]
if { $obj != "" } {

}
# Create 'impl_1_place_report_utilization_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_utilization_0] "" ] } {
  create_report_config -report_name impl_1_place_report_utilization_0 -report_type report_utilization:1.0 -steps place_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_utilization_0]
if { $obj != "" } {

}
# Create 'impl_1_place_report_control_sets_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_control_sets_0] "" ] } {
  create_report_config -report_name impl_1_place_report_control_sets_0 -report_type report_control_sets:1.0 -steps place_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_control_sets_0]
if { $obj != "" } {
set_property -name "options.verbose" -value "1" -objects $obj

}
# Create 'impl_1_place_report_incremental_reuse_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_incremental_reuse_0] "" ] } {
  create_report_config -report_name impl_1_place_report_incremental_reuse_0 -report_type report_incremental_reuse:1.0 -steps place_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_incremental_reuse_0]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj

}
# Create 'impl_1_place_report_incremental_reuse_1' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_incremental_reuse_1] "" ] } {
  create_report_config -report_name impl_1_place_report_incremental_reuse_1 -report_type report_incremental_reuse:1.0 -steps place_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_incremental_reuse_1]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj

}
# Create 'impl_1_place_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_place_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps place_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_place_report_timing_summary_0]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj
set_property -name "options.max_paths" -value "10" -objects $obj

}
# Create 'impl_1_post_place_power_opt_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_post_place_power_opt_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_post_place_power_opt_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps post_place_power_opt_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_post_place_power_opt_report_timing_summary_0]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj
set_property -name "options.max_paths" -value "10" -objects $obj

}
# Create 'impl_1_phys_opt_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_phys_opt_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_phys_opt_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps phys_opt_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_phys_opt_report_timing_summary_0]
if { $obj != "" } {
set_property -name "is_enabled" -value "0" -objects $obj
set_property -name "options.max_paths" -value "10" -objects $obj

}
# Create 'impl_1_route_report_drc_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_drc_0] "" ] } {
  create_report_config -report_name impl_1_route_report_drc_0 -report_type report_drc:1.0 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_drc_0]
if { $obj != "" } {

}
# Create 'impl_1_route_report_methodology_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_methodology_0] "" ] } {
  create_report_config -report_name impl_1_route_report_methodology_0 -report_type report_methodology:1.0 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_methodology_0]
if { $obj != "" } {

}
# Create 'impl_1_route_report_power_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_power_0] "" ] } {
  create_report_config -report_name impl_1_route_report_power_0 -report_type report_power:1.0 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_power_0]
if { $obj != "" } {

}
# Create 'impl_1_route_report_route_status_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_route_status_0] "" ] } {
  create_report_config -report_name impl_1_route_report_route_status_0 -report_type report_route_status:1.0 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_route_status_0]
if { $obj != "" } {

}
# Create 'impl_1_route_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_route_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_timing_summary_0]
if { $obj != "" } {
set_property -name "options.max_paths" -value "10" -objects $obj

}
# Create 'impl_1_route_report_incremental_reuse_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_incremental_reuse_0] "" ] } {
  create_report_config -report_name impl_1_route_report_incremental_reuse_0 -report_type report_incremental_reuse:1.0 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_incremental_reuse_0]
if { $obj != "" } {

}
# Create 'impl_1_route_report_clock_utilization_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_clock_utilization_0] "" ] } {
  create_report_config -report_name impl_1_route_report_clock_utilization_0 -report_type report_clock_utilization:1.0 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_clock_utilization_0]
if { $obj != "" } {

}
# Create 'impl_1_route_report_bus_skew_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_bus_skew_0] "" ] } {
  create_report_config -report_name impl_1_route_report_bus_skew_0 -report_type report_bus_skew:1.1 -steps route_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_route_report_bus_skew_0]
if { $obj != "" } {
set_property -name "options.warn_on_violation" -value "1" -objects $obj

}
# Create 'impl_1_post_route_phys_opt_report_timing_summary_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_post_route_phys_opt_report_timing_summary_0] "" ] } {
  create_report_config -report_name impl_1_post_route_phys_opt_report_timing_summary_0 -report_type report_timing_summary:1.0 -steps post_route_phys_opt_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_post_route_phys_opt_report_timing_summary_0]
if { $obj != "" } {
set_property -name "options.max_paths" -value "10" -objects $obj
set_property -name "options.warn_on_violation" -value "1" -objects $obj

}
# Create 'impl_1_post_route_phys_opt_report_bus_skew_0' report (if not found)
if { [ string equal [get_report_configs -of_objects [get_runs impl_1] impl_1_post_route_phys_opt_report_bus_skew_0] "" ] } {
  create_report_config -report_name impl_1_post_route_phys_opt_report_bus_skew_0 -report_type report_bus_skew:1.1 -steps post_route_phys_opt_design -runs impl_1
}
set obj [get_report_configs -of_objects [get_runs impl_1] impl_1_post_route_phys_opt_report_bus_skew_0]
if { $obj != "" } {
set_property -name "options.warn_on_violation" -value "1" -objects $obj

}
set obj [get_runs impl_1]
set_property -name "strategy" -value "Vivado Implementation Defaults" -objects $obj
set_property -name "steps.write_bitstream.args.readback_file" -value "0" -objects $obj
set_property -name "steps.write_bitstream.args.verbose" -value "0" -objects $obj

# set the current impl run
current_run -implementation [get_runs impl_1]

puts "INFO: Project created:${_xil_proj_name_}"
# Create 'drc_1' gadget (if not found)
if {[string equal [get_dashboard_gadgets  [ list "drc_1" ] ] ""]} {
create_dashboard_gadget -name {drc_1} -type drc
}
set obj [get_dashboard_gadgets [ list "drc_1" ] ]
set_property -name "reports" -value "impl_1#impl_1_route_report_drc_0" -objects $obj

# Create 'methodology_1' gadget (if not found)
if {[string equal [get_dashboard_gadgets  [ list "methodology_1" ] ] ""]} {
create_dashboard_gadget -name {methodology_1} -type methodology
}
set obj [get_dashboard_gadgets [ list "methodology_1" ] ]
set_property -name "reports" -value "impl_1#impl_1_route_report_methodology_0" -objects $obj

# Create 'power_1' gadget (if not found)
if {[string equal [get_dashboard_gadgets  [ list "power_1" ] ] ""]} {
create_dashboard_gadget -name {power_1} -type power
}
set obj [get_dashboard_gadgets [ list "power_1" ] ]
set_property -name "reports" -value "impl_1#impl_1_route_report_power_0" -objects $obj

# Create 'timing_1' gadget (if not found)
if {[string equal [get_dashboard_gadgets  [ list "timing_1" ] ] ""]} {
create_dashboard_gadget -name {timing_1} -type timing
}
set obj [get_dashboard_gadgets [ list "timing_1" ] ]
set_property -name "reports" -value "impl_1#impl_1_route_report_timing_summary_0" -objects $obj

# Create 'utilization_1' gadget (if not found)
if {[string equal [get_dashboard_gadgets  [ list "utilization_1" ] ] ""]} {
create_dashboard_gadget -name {utilization_1} -type utilization
}
set obj [get_dashboard_gadgets [ list "utilization_1" ] ]
set_property -name "reports" -value "synth_1#synth_1_synth_report_utilization_0" -objects $obj
set_property -name "run.step" -value "synth_design" -objects $obj
set_property -name "run.type" -value "synthesis" -objects $obj

# Create 'utilization_2' gadget (if not found)
if {[string equal [get_dashboard_gadgets  [ list "utilization_2" ] ] ""]} {
create_dashboard_gadget -name {utilization_2} -type utilization
}
set obj [get_dashboard_gadgets [ list "utilization_2" ] ]
set_property -name "reports" -value "impl_1#impl_1_place_report_utilization_0" -objects $obj

move_dashboard_gadget -name {utilization_1} -row 0 -col 0
move_dashboard_gadget -name {power_1} -row 1 -col 0
move_dashboard_gadget -name {drc_1} -row 2 -col 0
move_dashboard_gadget -name {timing_1} -row 0 -col 1
move_dashboard_gadget -name {utilization_2} -row 1 -col 1
move_dashboard_gadget -name {methodology_1} -row 2 -col 1

launch_runs impl_1 -to_step write_bitstream -jobs 16