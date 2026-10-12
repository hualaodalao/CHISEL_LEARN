# Queen扩展指令集及DSA软硬接口约束

## DSA设备寄存器读写扩展 custom-0(7'b0001011)
**custom-0 "funct3 2‘b00" 扩展**
该扩展指令为两个RS寄存器，无RD，目的为写入目标DSA设备2个寄存器
*Note：可写寄存器地址最多为5bits 32个*
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits | dsaType      |  00:gemm 01:dma 10:dma 11:dma  |
| inst[29:25] |5bits | rs1Addr      | dsa设备的第一个寄存器地址  |    
| inst[24:20] |5bits | rs2 | cpu寄存器地址：从cpu rs2中取数写入到dsa的rs2Addr中 |
| inst[19:15] |5bits | rs1 | cpu寄存器地址：从cpu rs1中取数写入到dsa的rs1Addr中 |
| inst[14:14] |1bits | macroeop | 触发dsa执行指示，当该标记为1的时候dsa解析器会触发dsa开始运行|
| inst[13:12] |2bits | func3 | 该类型固定为2'b00 |
| inst[11:7] |5bits | rs2Addr | dsa设备的第一个寄存器地址  |
| inst[6:0] | 7bits | opcode  | custom-0，固定为0001011 |

---
**custom-0 "funct3 2’b01" 扩展**
该扩展指令为1个RS寄存器，无RD，目的为写入目标DSA设备1个寄存器
*Note：可写寄存器地址最多为10bits 1024个*
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits | dsaType      |  00:gemm 01:dma 10:dma 11:dma  |
| inst[29:20] |10bits | rs1Addr      | dsa设备的第一个寄存器地址  |    
| inst[19:15] |5bits | rs1 | cpu寄存器地址：从cpu rs1中取数写入到dsa的rs1Addr中 |
| inst[14:14] |1bits | macroeop | 触发dsa执行指示，当该标记为1的时候dsa解析器会触发dsa开始运行|
| inst[13:12] |2bits | func3 | 该类型固定为2'b01 |
| inst[11:7] |5bits | rsv | 保留 |
| inst[6:0] | 7bits | opcode  | custom-0，固定为0001011 |
---
**custom-0 "funct3 2‘b10" 扩展**
该扩展指令为1个RD寄存器，无RS，目的为读取目标DSA设备1个寄存器
*Note：可写寄存器地址最多为10bits 1024个*
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits | dsaType      |  00:gemm 01:dma 10:dma 11:dma  |
| inst[29:20] |10bits | rdAddr      | dsa设备的第一个寄存器地址  |    
| inst[19:15] |5bits | rd | cpu寄存器地址：从dsa rdAddr中读数放入到cpu rd中寄存器中 |
| inst[14:14] |1bits | rsv | 保留|
| inst[13:12] |2bits | func3 | 该类型固定为2'b10 |
| inst[11:7]  |5bits | rsv | 保留 |
| inst[6:0]   |7bits | opcode  | custom-0，固定为0001011 |
---


## :warning: Queen Scheduler+Tracing Tag指令扩展 custom-0(7'b0001011) funct3 (2'b11) 
**type(2'b10) cmd(4'b0000)**
该指令为queen scheduler fence指令，fence指令为强制前序所有通过shceduler的指令执行完毕且所有dsa设备进入idle状态，fence指令无val需求
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits  | type    | 固定为2‘b10  |
| inst[29:14] |16bits | val     | cmd携带命令   |    
| inst[13:12] |2bits  | func3   | 该类型固定为2'b11 |
| inst[11:8]  |4bits  | cmd     | 该类型固定为4‘b0000|
| inst[7:7]   |1bits  | macroeop| macro eop标记 |
| inst[6:0]   |7bits  | opcode  | custom-0，固定为0001011 |
---
**type(2'b10) cmd(4'b0001)**
该指令为queen scheduler wait指令，wait指令为等待预期的事件状态
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits  | type    | 固定为2‘b10  |
| inst[29:14] |16bits | val     | cmd携带命令的payload值   |    
| inst[13:12] |2bits  | func3   | 该类型固定为2'b11 |
| inst[11:8]  |4bits  | cmd     | 该类型固定为4‘b0001 |
| inst[7:7]   |1bits  | macroEop    | macro eop标记 |
| inst[6:0]   |7bits  | opcode  | custom-0，固定为0001011 |

wait指令为等待事件，需要携带val指定等待资源：
 - val(xxxxxxxxxxxx0000) : 等待gemm执行进入idle
 - val(xxxxxxxxxxxx0001) ：等待dma执行进入idle
 - val(xxxxxxxxxxxx0010) : 等待rvv进入idle
 - val(xxxxxxxxxxxx1zzz) : 等待硬同步资源锁z释放，z当前为最多8个硬件锁资源
---

**type(2'b10) cmd(4'b0010)**
该指令为queen scheduler pooling cmp fence指令, 从内存中读出一个数字如果与cmp值相等则继续执行否则fence后续指令继续pooling，硬件默认每8 cycle 发起一个读命令
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits  | type    | 固定为2‘b10  |
| inst[29:14] |16bits | val     | cmd携带命令的payload值   |    
| inst[13:12] |2bits  | func3   | 该类型固定为2'b11 |
| inst[11:8]  |4bits  | cmd     | 该类型固定为4‘b0001 |
| inst[7:7]   |1bits  | macroEop    | macro eop标记 |
| inst[6:0]   |7bits  | opcode  | custom-0，固定为0001011 |

该指令需要4个ISA组成一个macro指令
- macro1 - val(16 bits) 表示pooling地址低16bit
- macro2 - val(16 bits) 表示pooling地址高16bit
- macro3 - val(16 bits) 表示pooling预期值低16bit
- macro4 - val(16 bits) 表示pooling预期值高16bit

**type(2'b10) cmd(4'b1000)**
该指令为queen scheduler atomic store 4B的ADD/CLR/SET指令, 发出一个atomic store
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits  | type    | 固定为2‘b10  |
| inst[29:14] |16bits | val     | cmd携带命令的payload值   |    
| inst[13:12] |2bits  | func3   | 该类型固定为2'b11 |
| inst[11:8]  |4bits  | cmd     | 该类型固定为4‘b0001 |
| inst[7:7]   |1bits  | macroEop    | macro eop标记 |
| inst[6:0]   |7bits  | opcode  | custom-0，固定为0001011 |

该指令需要3个ISA组成一个macro指令
- macro1 - val(3:0 bits) 表示store atomic store命令， xx00:ADD，xx01:CLR，xx10:SET
- marco1 - val(15:8 bits) 表示store atomic add的值
- macro2 - val(16 bits) 表示store地址低16bit
- macro3 - val(16 bits) 表示store地址高16bit
 
---
**custom-0 "funct3 2‘b11 type 2‘b11 " 扩展**
该扩展指令为tracing的tag指令，仅用于tracing, tag值会写入到tracing包内
| Field       | Bits    | Name | Description     |
| :---        | :--- |    :----   |          :--- |
| inst[31:30] |2bits | type   |  固定为2‘b11  |
| inst[29:14] |16bits | tag      | tracing自定义tag  |    
| inst[13:12] |2bits | func3 | 该类型固定为2'b11 |
| inst[11:8]  |4bits | rsv | 保留 |
| inst[7:7]   |1bits | monitor_point | 0: tag指令在进入schedule处即给到tracing接口（指令接收） 1: tag指令随marco流入dsa设备在dsa设备处给到tracing接口（“实际派发”）
| inst[6:0]   |7bits | opcode  | custom-0，固定为0001011 |


---