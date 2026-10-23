# ForagerBee DMA 硬件规格

文档版本：`v2.0.0`

修订日期：`2026-10-23`

## 版本控制

### 文档版本规则

采用 `v主版本.次版本.修订号`：主版本表示外部契约不兼容变更，次版本表示兼容新增，修订号表示不改变契约的勘误。文档版本不等于 RTL 发布版本、命令编码版本或芯片型号。发布时同步更新首页、日期和修订记录；影响契约的修改记录 Feature ID，ID 不因章节移动而变化或被复用。

### 修订记录

仅记录可确认的修订，不追溯编造此前草稿版本。

| 版本号 | 日期 | 修改内容 | 相关 Feature ID |
|---|---|---|---|
| v2.0.0 | 2026-10-23 | **ABI break**：删除 chaining、atomic、全局转换活线及 INT8 shift 量化/反量化；新增 `idle`、`padValue`，按重写后的 Engine、字节适配器更新非对齐、尾部、IM2COL、SG、MX 与写响应语义；保留数值限制和验证缺口 | FB-F002–FB-F020、FB-F027–FB-F030 |
| v1.0.0 | 2026-10-23 | 首个编号基线：按通用模板建立版本控制、14 章和稳定功能清单 | FB-F001–FB-F026 |

## 1. 文档说明

### 1.1 目的

定义 ForagerBee 的结构化命令、张量搬运、数据格式、调度、总线、完成/错误、配置和使用义务。本文按**当前主源码实际逻辑**编写，不沿用旧 Engine 的整拍对齐、整拍转换比例或跨行 EOF 推进假设。实现状态不是验证结论；编译和新增字节通路 RTL 仿真已通过；保留功能的全量回归状态见 14.2。

### 1.2 范围

覆盖 ForagerBee 顶层、Tile 展开器、Dispatcher、Engine、字节读写适配器、浮点转换、MX 量化、转置缓冲、IM2COL、SG 和共享写仲裁。只定义本模块与命令源、存储系统、时钟复位的接口，不声明 Hive 集成、Queen 控制/解码或任何外部 NPU 的已实现连接。

当前入口直接接收 `FbCmd`，没有 Raw DW decoder、链式控制器/描述符总线、atomic 完成写或全局 `cvt*` IO。规划项统一标注“**规划中/非当前 RTL 契约**”，不是交付承诺。

### 1.3 术语与规范性用语

“必须/不得”表示合法输入或环境强制要求；“应”表示推荐；“可”表示可选能力。“不适用”表示确无该项；“待确认”表示信息或验证未完成。源码旧注释与逻辑不一致时以逻辑为准。

| 术语/符号 | 定义 | 单位/范围 |
|---|---|---|
| beat、`BB` | 一次 `dataWidth` 位总线数据；`BB=beatBytes=dataWidth/8` | 字节；默认 16 |
| burst | 一次读请求或 SOF 到 EOF 的写事务，含 `len+1` beat | 1..`maxBurstLen` beat |
| logical transfer | 一次字节 reader/writer 启动；可以包含多个物理 burst | 一行、一个 pad 段、SG 条目、IM2COL micro-row/元素、MX 块或 scale 段 |
| 物理覆盖读区 | 逻辑源区向下/向上对齐到 beat 的区间 | 包含首尾额外读取字节 |
| dim0 | 最内连续维；COPY 不使用 `stride(0)` 寻址 | 元素 |
| `rowBytes(n,f)` | `ceil(n*elemBits(f)/8)` | 字节 |
| in-flight 命令 | 已派发且对应引擎 `done` 尚未握手 | 每引擎至多 1 条 |
| HOL | 单 FIFO 队首阻塞 | 调度限制 |
| 完全空闲 | `idle = !busy && occupancy==0` | 已接受命令、执行和待消费回执均排空 |
| element stride | Raw DW 草案中的 16 bit 元素步长 | 规划中；当前命令使用 byte stride |

地址均为字节地址，多字节元素和 beat 内低地址字节使用 little-endian。

### 1.4 参考资料与适用基线

以下路径相对仓库根目录；基线是本次核对的当前主源码，不是旧生成 RTL 或注释中的历史版本。

| 资料 | 路径 | 用途 |
|---|---|---|
| 顶层/配置/命令 | `src/foragerbee/scala/main/ForagerBee.scala`、`ForagerBeeConfig.scala` | IO、默认值、枚举、`FbCmd/FbDone` |
| 总线 | `src/foragerbee/scala/main/FbStreamBus.scala` | 四通道、`FbWriteBeat` |
| 调度/执行 | `src/foragerbee/scala/main/FbDispatcher.scala`、`FbEngine.scala` | 包络、状态机、校验与完成 |
| 字节/转换 | `src/foragerbee/scala/main/FbByteTransfer.scala`、`FbFormatConverter.scala`、`FbMxQuantizer.scala` | 物理对齐、有效字节、数值逻辑 |
| 专用操作 | `src/foragerbee/scala/main/FbTransposeBuffer.scala`、`FbIm2colAddrGen.scala`、`FbScatterGatherCtrl.scala`、`FbTile2LinearExpander.scala` | 操作语义 |
| 仲裁/导出 | `src/foragerbee/scala/main/FbWrPortArbiter.scala`、`ForagerBeeElaborate.scala` | 写响应路由、导出配置 |
| 测试源码 | `src/foragerbee/scala/test/ForagerBeeSpec.scala`、`ForagerBeeByteSpec.scala` | 回归意图；存在测试不代表已通过 |
| 模板 | `src/doc/spec_template.md` | 版本控制及 14 章结构 |

## 2. 模块概述

### 2.1 定位与职责

ForagerBee 是多引擎张量 DMA，经单一 `cmd` Stream 接收命令，按 shape、独立 stride、裁剪、填充和格式语义读源写目的，经 `done` 返回 tag 和错误位。当前操作为 COPY、TRANSPOSE、PERMUTE、IM2COL、SCATTER、GATHER、TILE2LINEAR、LINEAR2TILE；后两项先组合展开为 4D COPY。

每引擎独立读通道、队列和状态机；写半部可一对一直连或共享仲裁。模块没有软件命令解码器、CSR、中断或外部系统集成控制。

### 2.2 系统边界

| 外部对象 | 交互 | 外部责任 |
|---|---|---|
| 命令源 | 提交 `FbCmd`、消费 `FbDone`，观察 `busy/occupancy/idle` | 合法输入、未用字段清零、按 tag 关联、hazard 盲区串行化 |
| 存储系统 | `bus[p]` 四条 Stream；SG 表复用读通道 | 覆盖 beat 可读、byte strobe 正确、请求/响应数量准确；共享写响应保序 |
| 时钟/复位 | 单一隐式时钟与复位 | 跨域在外部处理；复位前排空事务 |

### 2.3 总体框图

```text
                      clock / reset（单时钟域）
cmd -> FbTile2LinearExpander -> FbDispatcher（FIFO + bbox hazard）
                                      |-> FbEngine[0]   <-> bus[0]
                                      |-> ...
                                      `-> FbEngine[N-1] <-> bus[N-1]
done <- transaction-lock round-robin <- 各引擎 done
busy = OR(engine.busy)；occupancy = FIFO 占用；idle = !busy && occupancy==0

引擎普通搬运：ByteReader -> [FormatConverter/旁路] -> ByteWriter
MX：ByteReader -> MxQuantizer -> ByteWriter；scale 累积后侧写
TRANSPOSE：ByteReader -> 原 tile buffer -> 专用带 strobe 写通路
可选 FbWrPortArbiter 仅映射写半部，读半部始终独占。
```

## 3. 功能清单（Feature List）

### 3.1 Feature ID 规则

使用稳定 `FB-Fnnn`，不等于 opcode/地址/错误码。既有 ID 不重排、不复用；移除功能保留 ID 并标“已移除”。只有“已实现”行构成当前能力索引，且受输入域与配置限制；实现状态不代表已通过仿真。

### 3.2 功能清单

| Feature ID | 功能名称 | 实现状态 | 适用条件与主要约束 | 契约章节 |
|---|---|---|---|---|
| FB-F001 | 多引擎命令派发 | 已实现 | 1..4 引擎，单 FIFO/HOL，每引擎一条命令 | 5.2.1 |
| FB-F002 | ND COPY | 已实现 | 独立 byte stride，逻辑地址/行长不要求整 beat | 5.1.1、7.3 |
| FB-F003 | 源子块裁剪 | 已实现 | COPY/PERMUTE/TRANSPOSE；dispatcher 未计裁剪偏移 | 5.1.2、5.2.2 |
| FB-F004 | Zero-padding | 已实现 | COPY dim0/dim1；`padValue=0`；现在可子 beat 填充 | 5.1.3 |
| FB-F005 | TRANSPOSE | 已实现 | 2D、同格式、非 INT4；保留原 tile buffer | 5.1.4 |
| FB-F006 | PERMUTE | 已实现 | 仅源外层 stride 重映射，dim0 连续；同格式，无 padding | 5.1.5 |
| FB-F007 | IM2COL | 已实现 | CHW 输入；字节粒度同格式或浮点转换 | 5.1.6 |
| FB-F008 | SCATTER/GATHER | 已实现 | 表 beat 对齐，数据任意正字节长度；同格式、非 INT4 | 5.1.7、7.2.5 |
| FB-F009 | TILE2LINEAR/LINEAR2TILE | 已实现 | 组合展开 4D COPY，字节粒度同格式 | 5.1.8、7.3.5 |
| FB-F010 | 普通浮点格式转换 | 已实现 | 仅 FP32/FP16/BF16 之间，COPY/IM2COL，`enableConversion=true` | 5.1.9、5.3.2 |
| FB-F011 | INT4 同格式 COPY | 已实现 | 行元素数/源 dim0 裁剪为偶数，无 padding；可短字节行 | 5.1.10 |
| FB-F012 | MX 量化 | 已实现 | 浮点 -> MX，仅 COPY；逐块字节归一化；数值限制未修复 | 5.1.11、5.3.3 |
| FB-F013 | 全局转换参数活线 | **已移除** | `cvtScale/cvtZeroPoint` IO 删除；INT8 shift 转换同时删除 | 7.2.6 |
| FB-F014 | 地址 hazard 检查 | 已实现 | RAW/WAR/WAW 与 MX scale；不是完整 hazard 覆盖 | 5.2.2 |
| FB-F015 | 能力裁剪与退化派发 | 已实现 | 每通道能力；无能力通道时派发并报错 | 5.2.1、8.1 |
| FB-F016 | 阻塞/非阻塞写 | 已实现 | 单 transfer 内多 burst；末尾排空全部响应，不承诺跨普通行重叠 | 5.2.4 |
| FB-F017 | 共享写端口仲裁 | 已实现 | burst 仲裁，共享端口响应保序；物理端口数不增加 | 5.2.4 |
| FB-F018 | 链式描述符 | **已移除** | 控制器、配置、命令链字段和 `chainBus` 删除 | 7.2.6 |
| FB-F019 | Atomic 完成标志 | **已移除** | 命令 atomic 字段和 `FbWriteBeat.atomic` 删除 | 7.2.6 |
| FB-F020 | 完成与错误聚合 | 已实现 | `tag+err`；支持反压，完成可乱序 | 5.2.3、10 |
| FB-F021 | Raw DW 变长解码 | 未实现 | 规划中/非当前 RTL 契约 | A.2 |
| FB-F022 | tileOrder/独立 T2T | 未实现 | 同尺寸互转可用 4D COPY 表达 | 5.1.8、A.2 |
| FB-F023 | MX 反量化 | 未实现 | 转换构建拒绝 MX 源 | 5.1.11、A.2 |
| FB-F024 | INT32/INT16/INT4 跨格式转换 | 未实现 | 编码保留，同格式搬运可用 | 5.1.9、7.2.2 |
| FB-F025 | 4 KiB 自动拆分 | 未实现 | 当前不检查/拆分该边界 | 7.3.3、A.2 |
| FB-F026 | 细分错误码 | 未实现 | 当前只有 `err` Bool | 10、A.2 |
| FB-F027 | 完全空闲状态 `idle` | 已实现 | FIFO、执行和 done 反压均计入；不以 `cmd.valid` 门控 | 11.1 |
| FB-F028 | 物理 beat 对齐/逻辑非对齐及尾部 | 已实现 | 字节 reader/writer、精确转换字节计数；源覆盖 beat 必须可访问 | 5.1.12、7.3.3 |
| FB-F029 | IM2COL 水平边界与 dilation | 已实现 | `dW=1` 整窗有效则 burst，否则逐 kw fallback；无效元素不读源 | 5.1.6 |
| FB-F030 | 可编程 `padValue` | 已实现 | 低目的元素位重复，转换后填充，不对 padValue 做数值转换 | 5.1.3、5.1.6 |

功能表仅索引；详细合法域、限制和验证状态见对应章节及第 14 章。

## 4. 架构说明

### 4.1 子模块划分

| 子模块 | 职责/依赖 |
|---|---|
| `FbTile2LinearExpander` | 无状态组合展开/透传，连接 cmd 与 dispatcher；无隐藏命令队列 |
| `FbDispatcher` | 命令 FIFO、每通道 in-flight 包络、能力选择，`done.fire` 释放记录 |
| `FbEngine` | 锁存命令、校验、逐 transfer 控制、专用操作、聚合错误与完成 |
| `FbByteReader` | beat 对齐覆盖读、物理队列 credit、首部裁除/尾部截取，输出连续低字节 packet |
| `FbByteWriter` | packet 字节打包、目的偏移/strobe、分 burst、local outstanding 与响应排空 |
| `FbFormatConverter`（可选） | 浮点位级转换和字节计数压缩/扩展，同格式旁路 |
| `FbMxQuantizer`（可选） | 原块缓存/最大指数/量化；引擎负责块边界和 scale 累积写出 |
| `FbTransposeBuffer`（能力条件） | 原 `tileSize×tileSize` 元素槽；reader 归一化源行后输入 |
| `FbIm2colAddrGen`（能力条件） | CHW 几何、micro-row 地址、整窗 burst 判定/逐元素边界 |
| `FbScatterGatherCtrl`（能力及位宽条件） | 复用读口逐条 fetch，一个 beat 一个描述符，无预取 |
| `FbWrPortArbiter`（可选） | 写数据选择与响应 owner 路由，无数据 FIFO |
| done 仲裁 | transaction-lock round-robin，反压保持选中的回执 |

### 4.2 数据通路与控制通路

命令握手后进入 FIFO，队首无冲突且有合适空闲通道时派发并锁存地址包络。引擎按操作生成逻辑 transfer；每个普通 transfer 启动 reader/writer，并在其完成后才推进外层计数。最终 `done.fire` 才使引擎回 IDLE、释放 in-flight。

**所有 COPY/PERMUTE 数据行均使用 streaming ByteReader -> 可选 converter/旁路 -> ByteWriter**，不是旧整行收集方案。reader 输出 `data/bytes/last`；转换器用有效字节计算元素数和目的字节数，writer 只写这些有效字节。padding 不读源，直接给 writer；MX 每块先字节归一化再进入原 quantizer。TRANSPOSE 只替换源读适配，保留 tile buffer 和专用输出 strobe 写逻辑。

### 4.3 内部资源组织

每引擎独占物理读队列、双 beat reader/writer 字节储存、命令/遍历计数和错误寄存器。具备转置能力时生成 32 bit 元素 tile 槽；开启转换时生成 converter 缓冲、MX block 缓冲和 `BB` 个 scale 字节累积槽。Dispatcher 的 src/dst/scale 包络和有效位为每通道独立资源；共享写 NB 模式每物理端口另有 owner FIFO。容量见 12.1。

## 5. 功能行为规范

### 5.1 功能契约

所有操作在引擎 `cmdIn.fire` 后启动，命令 tag 原样返回；共用合法维数、格式、地址和总线条件，错误见第 10 章。普通完成必须等待传输及写响应排空，而不是最后一次写 EOF。

#### 5.1.1 ND COPY（FB-F002）

| 契约项 | 说明 |
|---|---|
| 前置条件 | `1<=dimCount<=maxDims`，活动 shape 非零；格式组合合法；不要求逻辑基址、stride、行字节整 beat |
| 处理语义 | dim1 最快，随后 dim2..进位；dim0 连续；源/目的独立 byte stride；地址见 7.3.2 |
| 输出与完成 | 每数据行精确读 `rowBytes(shape(0),srcFmt)` 逻辑字节、写对应目的字节；行 transfer 完成后推进 |
| 边界 | 行可短于 beat 或有尾部；物理 burst 按剩余、配置和读 credit 拆分；可裁剪、padding、浮点转换或 MX，但须符合各自互斥条件 |
| 异常关联 | 命令校验或 transfer 地址越界时报 `err`；源物理覆盖 beat 可读是环境义务 |

#### 5.1.2 源裁剪（FB-F003）

COPY/PERMUTE/TRANSPOSE 用 `srcStartIdx` 偏移源，目的从 `dstAddr` 开始。dim0 加 `rowBytes(srcStartIdx(0),srcFmt)`，外层加起始索引乘源 stride；PERMUTE 使用重映射后的 stride。INT4 dim0 起始索引必须偶数，无半字节地址。裁剪后逻辑地址可非 beat 对齐；完成与所属操作相同。源缓冲合法性由软件保证，dispatcher **仍未加入裁剪偏移**，相关并发必须串行化（5.2.2）。

#### 5.1.3 Padding 与 padValue（FB-F004、FB-F030）

| 契约项 | 说明 |
|---|---|
| 前置条件 | COPY，仅 dim0/dim1；不与 INT4 或 MX 同用；未用 pad 数填 0 |
| 输入 | `padBefore/After[0]` 为每行前后元素数，`[1]` 为前后整行数；`padValue[31:0]` 为目的元素位型 |
| 处理语义 | 有效行按前 pad 段、数据行、后 pad 段执行；pad 行写完整目的行。dimCount>2 时每外层切片重复 |
| 输出 | 取 `padValue` 低 `elemBits(dstFmt)` bit，little-endian 重复；FP16/INT16 用低 16 bit，FP32/INT32 用 32 bit，INT8 用低 8 bit；高位忽略 |
| 完成/边界 | 每段精确字节写，首尾 strobe 保留邻字节；段/行等待 writer.done；padValue=0 即 zero-padding，长度不必整 beat |
| 异常关联 | 非 COPY 带 `padBefore/After`、INT4/MX padding 被拒；目的越界时报错 |

padding 位于**数据转换之后的目的表示域**，padValue 不经转换器、不做浮点/整数算术变换。IM2COL 用自己的 `im2colPad` 几何和同一 padValue 位型；不得用 COPY 的 padBefore/After 描述 IM2COL。对于 1D COPY，dim1 pad 必须为零，否则命令校验报 err；需要整行 padding 时使用 dimCount>=2。

#### 5.1.4 TRANSPOSE（FB-F005）

限定 `dimCount=2`、有转置能力、同格式且非 INT4。令 `M=shape(0)`、`N=shape(1)`、`e=bytesOfCeil(srcFmt)`：

```text
src(r,c) = srcAddr + (r+srcStartIdx(1))*srcStride(1)
                    + rowBytes(srcStartIdx(0),srcFmt) + c*e
dst(c,r) = dstAddr + c*dstStride(0) + r*e
```

按 `tileSize×tileSize` tile 先填充后列序排空，余行/余列由有效行列数控制。源行由 ByteReader 覆盖读取、去除首偏移并截尾，**归一化源行 packet 输入既有 tile buffer**；每个列 tile 会重新读取相关完整源行，不是只读所需列。源/目的可非 beat 对齐；输出由 tile buffer 产生字节偏移和 strobe，专用写通路将物理地址向下对齐并按 maxBurstLen 拆分。

完成等待全部读写和专用写 outstanding 排空。错误包括能力/维数/格式与源覆盖读区、目的有效区越界。dispatcher 目的模型未使用实际 `dstStride(0)`，不能依赖其完整保护。极大 tile 的内部有限计数/打包宽度仍有参数化限制，见 14.1。

#### 5.1.5 PERMUTE（FB-F006）

须有 PERMUTE 能力、同格式、无 padding，活动 `permVec[d]<dimCount`。共用 COPY 的 streaming 字节通路，仅源外层 stride 改为 `srcStride[permVec[d]]`；目的仍用 `dstStride[d]`，dim0 始终连续，`permVec[0]` 不参与行地址。可非对齐和带裁剪，完成同 COPY。RTL 不检查映射互不重复；要表达真正排列，软件须保证双射。它不是可移动连续维的通用置换器；越界映射、跨格式或能力缺失时报错。

#### 5.1.6 IM2COL（FB-F007、FB-F029、FB-F030）

| 契约项 | 说明 |
|---|---|
| 前置条件 | 有 IM2COL 能力；C/H/W、kH/kW、sH/sW、dH/dW 非零；有效核不大于 padded 输入；outH/outW 为 1..65535；非 INT4/MX |
| 输入 | `im2colInShape=[C,H,W]`，kernel/stride/pad/dilation 分别 `[H,W]`；pad 为上下/左右对称 |
| 处理语义 | CHW 紧密源布局，输出展开顺序 `[oh,ow,c,kh,kw]`，kw 最快；不使用普通 ND stride/裁剪 |
| 整窗路径 | ih 有效、`dW=1` 且整个水平窗口有效：kW 元素连续 transfer，可按 reader/writer 的物理 burst 限制拆分 |
| fallback | 水平越界或 `dW!=1`：逐 kw 检查/搬运，每个元素一个 logical transfer；有效元素读源，可浮点转换；无效元素直接写 padValue，**不发源读请求** |
| 垂直 padding | ih 越界则整 kW micro-row 填充，不读源；不要求 dW=1 |
| 完成/异常 | 每 transfer 等待 writer.done，完成 kw 后推进 kh/c/ow/oh；全部响应排空才 done；几何/地址/格式错误上报 |

```text
outH = floor((H + 2*pH - dH*(kH-1) - 1)/sH) + 1
outW = floor((W + 2*pW - dW*(kW-1) - 1)/sW) + 1
ih = oh*sH - pH + kh*dH
iw = ow*sW - pW + kw*dW
src = srcAddr + ((c*H + ih)*W + iw)*srcElemBytes       （仅坐标有效时使用）
dst = dstAddr + ((((oh*outW+ow)*C+c)*kH+kh)*kW+kw)*dstElemBytes
```

转换以完整逻辑元素数计算源/目的字节，支持短 micro-row、奇数元素和尾部；没有旧 `4*sourceMicroRowBeats<=maxBurstLen` 或压缩整 beat 比例约束。**Dispatcher 仍使用软件 `shape(0)`/`shape(1)` 的源/目的 byte span，不从上述生成几何推导包络**；必须填写实际范围，即使 dimCount=1。Engine 的几何和地址检查不能替代这项软件 hazard 义务。

#### 5.1.7 SCATTER/GATHER（FB-F008）

| 契约项 | 说明 |
|---|---|
| 前置条件 | 有 SG 能力且 `dataWidth>=addressWidth+16`；sgListAddr beat 对齐；sgEntryCount=1..65535；同格式、非 INT4 |
| 输入/触发 | 一个 beat 一个 `(addr,lenBytes)` 条目（7.2.5），复用读口逐条 fetch |
| 处理 | GATHER：条目源 -> 连续目的；SCATTER：连续源 -> 条目目的；线性 offset 按 **lenBytes 原值相加**，不按 beat 取整 |
| 字节域 | 数据地址任意字节对齐，`lenBytes=1..65535` 均可，甚至不为元素宽度整数倍；同格式裸字节搬运，无数值转换 |
| 完成 | 条目 transfer 分多个 burst，writer.done 后才 fetch/处理下一条；最后响应排空才 done |
| 异常 | 零长度条目返回 err，不再永久停滞；表/数据越界、表读取及数据读写错误聚合为 err |

SG 表地址/整体表范围在命令入口检查；数据条目范围在取到条目后检查，出错前条目可能已更新目的。地址表 fetch 错误会置 err，但当前不立即中止或丢弃该条目，软件必须将最终错误结果视为部分更新。

Dispatcher 仅估计 `[srcAddr,srcAddr+shape(0))`、`[dstAddr,dstAddr+shape(1))`，不解析条目或保护 sgListAddr：GATHER 的 srcAddr 必须不大于所有条目源地址，shape(0) 覆盖其并集包络；SCATTER 的 dstAddr/shape(1) 同理覆盖条目目的；另一线性侧也须覆盖真实字节区。shape 为 16 bit，无法表达包络时须串行化。表在命令完成前不得被并发覆盖；源首尾物理覆盖读区也必须可读。

#### 5.1.8 TILE2LINEAR/LINEAR2TILE（FB-F009；FB-F022 未实现）

五个 t2l 参数均非零、字节粒度同格式、非 INT4。上游组合展开为 4D COPY（7.3.5）；非法参数/格式通过 dimCount=0 送引擎报错。沿用 COPY 的非对齐、尾部及完成规则。参数不一致、非完整 tile 的矩阵边缘不会自动推导/裁剪，软件必须确保遍历区域有效。其他未用命令字段须清零，展开器默认透传它们，不会自动清掉裁剪/padding。

当前没有 `tileOrder` 字段或独立 T2T opcode。同尺寸 tile 顺序互转可由 4D COPY 配独立 stride 表达；不同尺寸重分块使用软件 T2L -> 中间线性缓冲 -> L2T，两条命令必须按依赖排序。单命令重分块属规划。

#### 5.1.9 普通浮点转换（FB-F010；FB-F024 未实现）

仅开启 enableConversion 且 COPY/IM2COL 的 FP32/FP16/BF16 跨格式转换合法；FP16/BF16 组合经 FP32 中间表示。同格式包括 INT8/INT16/INT32 为位型旁路，不执行算术。任何 INT8 与浮点之间、INT16/INT32/INT4 跨格式转换当前均拒绝；**INT8 shift 量化/反量化已删除，不是待恢复功能**。

输入/输出有效字节和 last 随流传递：32->16 的逻辑目的字节是源字节一半，16->32 为两倍，16->16 不变。压缩可累积子 beat，扩展可拆成多个输出 packet；最后不足 beat 时精确输出尾部。逻辑数据始终为完整元素；浮点短行/奇数元素无需整 beat 比例，也不要求 burst/queue 至少等于转换比。数值公式及受限次正规/NaN 行为见 5.3.2，完成与所属操作相同。

#### 5.1.10 INT4 COPY（FB-F011）

仅 `op=COPY`、`srcFmt=dstFmt=INT4`、shape(0) 和 srcStartIdx(0) 偶数、无 padding。相邻元素连续打包，低半字节为先行元素；行字节数 shape(0)/2，可短于 beat，首尾精确 strobe。stride 单位仍是字节，无半字节地址。违反条件时报错；不支持 INT4 TRANSPOSE/PERMUTE/IM2COL/SG/Tile 操作或算术转换。

#### 5.1.11 MX 量化（FB-F012；FB-F023 未实现）

| 契约项 | 说明 |
|---|---|
| 前置条件 | enableConversion=true、COPY，源 FP32/FP16/BF16，目的 MXE4M3/MXE5M2；scaleBlockSize 为 0/1/2 -> 16/32/64 元素；shape(0) 整块，无 padding，scaleAddr beat 对齐 |
| 激活 | 由 dstFmt 属 MX 派生，无独立 MX 使能位 |
| 数据路径 | 每块 ByteReader 精确提取 blockSize*srcElemBytes，字节归一化后给原 quantizer；原 collect/quant/drain 阶段不变；输出 blockSize 个字节经 ByteWriter |
| 分段/scale | 单块读写可跨多个物理 burst；不再要求单块 beat 数不大于 maxBurstLen/queue。每块一个 scale，累积 BB 字节满时或行末写出，末段精确 strobe |
| 完成/边界 | 每数据块/scale transfer 等待 writer.done；scale 行复用 dstStride 外层偏移（7.3.4）；所有数据/scale 响应返回才 done |
| 异常 | 非浮点源、非法块、非整块行、padding/scaleAddr 不对齐被拒；数据/scale 地址越界与总线错误上报 |

逻辑源/目的数据地址和 scale 外层行地址可非 beat 对齐；仅 scaleAddr 基址仍须 beat 对齐。软件必须隔离同命令数据区、源区和 scale 区，硬件不检查命令内重叠。enableConversion=true 时 **所有 MX 源（含 MX->MX）拒绝**；false 时同格式 MX 编码可作 1 byte/element 裸搬运，也可用 INT8 编码搬运等宽 MX 字节。同时修正 FP32 块最大指数归约的槽掩码，其他数值限制见 5.3.3。

#### 5.1.12 字节适配（FB-F028）

reader 从 alignDown(src,BB) 读取 ceil((src%BB+byteCount)/BB) 个物理 beat，去除首偏移/尾部额外字节，输出低地址到高地址的连续低字节 packet。writer 对齐目的物理地址，以初始偏移和精确剩余计数产生 strobe，不覆盖逻辑目的区外的字节。两侧 burst 独立分段；转换后的有效字节不是读 burst beat 数的简单取整。

reader credit 是**空队列槽减全部已预留但未返回物理 beat**；rd.fire 一次预留整个物理 burst，物理队列出队归还 credit。请求长度寄存后不因随后 credit 变化而变化，最多一个读请求在途。writer local outstanding 在 EOF 增加、响应握手减少，同拍 EOF/response 抵消；不会把未完成 burst 当作可响应事务。适配器 start 只在自身 idle 接受，不是 abort；零字节适配器 transfer 可直接完成，但命令活动 shape/SG 长度的零值仍按命令规则拒绝。

### 5.2 执行顺序与并发行为

#### 5.2.1 调度与能力（FB-F001、FB-F015）

单 FIFO 只检查队首。无地址冲突时选编号最小的空闲、能力匹配通道；空闲需 `!engine.busy && !infValid`。无任何通道具备该操作能力时退化派发到任意空闲通道，由 Engine 报错，不永久等能力。SG 若能力配置为真但位宽不够，仍可能被选中而由 Engine 拒绝。

队首冲突或能力等待阻塞后续命令，非全局公平调度。in-flight 保持到引擎 done.fire；done 反压期间引擎仍 busy，hazard 不释放。

#### 5.2.2 地址包络与盲区（FB-F014）

普通源逻辑上界是 srcAddr + Σ活动外层 `(shape[d]-1)*srcStride[d]` + 源行字节；PERMUTE 用重映射 stride。**源起点向下、终点向上按 BB 对齐**，以覆盖物理额外读字节。目的按 dstFmt 和 padding 后 shape/外层跨度估计；IM2COL/SG 固定用软件 shape(0/1) byte span。MX scale 为独立第三写区，按 dstStride 外层跨度加整 beat 向上取整的 scale 行字节估计，仅量化命令有效。

范围乘加保留宽中间结果，in-flight 起止寄存器为 **addressWidth+1 bit**；端点超过 `2^addressWidth` 时 clamp 到该上限，避免旧截断回绕缩小包络。这是调度包络饱和，不是合法化越界命令。目的范围描述有效写字节，并非对所有目的 strobe 都强制扩大到完整 beat。

新读区与在途 dst/scale 检查 RAW，新 dst/scale 与在途读区检查 WAR，两个写区组合检查 WAW。已存在的盲区仍须软件串行化/隔离：

- **crop**：srcStartIdx 未计入源包络。
- **transpose**：目的按普通 dstStride(1) 模型，不按实际 dstStride(0)。
- **SG descriptor**：sgListAddr 表读取不参与 hazard；散列数据依赖软件 base/span 包络，16 bit span 可能不够。
- **unions/命令内别名**：不逐条构造稀疏访问并集，也不检查同一命令 src/dst/scale 或不同条目自身重叠；无通用 memmove/原地转换保证。
- **IM2COL 软件包络**：shape(0/1) 与真实生成几何没有自动一致性校验；填错就可能漏检。

因此会有假冲突和漏检，不能声明 universal hazard coverage 或系统内存一致性；外部主机访问也不在 dispatcher 检查域内。

#### 5.2.3 完成回执（FB-F020）

done 以 transaction-lock round-robin 汇聚；不同引擎可乱序完成，用 tag 关联。全部路径支持反压，选中回执 valid/payload 在握手前保持；引擎保留命令、busy 和包络锁定。`done` 只有 tag/err，不再有 chainLen；没有 atomic 写替代通知。写响应“完成”的系统可见性/一致性强度由下游协议决定。

#### 5.2.4 写响应与共享仲裁（FB-F016、FB-F017）

`writeOutstandingDepth=1` 为阻塞：一次 burst EOF 后等待响应才可开下一 burst。>1 时 **同一个 logical transfer 内**可继续下一个 burst，最多配置深度的已完成 burst 等响应。ByteWriter 计数局部于当前 transfer，末尾等所有响应才拉 done。

**普通 COPY/PERMUTE 数据行、pad 段/行、SG 条目、IM2COL transfer、MX 数据块与 scale 段均等 writer.done 才启动下一 transfer**。不能沿用旧“所有行均 EOF 即推进”的描述，也不能承诺跨这些边界隐藏写延迟。TRANSPOSE 有独立 outstanding：NB 在列输出 EOF 后可推进，命令 done 仍等待全部响应；阻塞模式等待响应。

共享写模式不改变读口：

- depth=1：端口归属锁保持到 wrResp；depth>1：数据锁 SOF->EOF，owner FIFO SOF->response。
- NB 每物理端口 owner FIFO 深度为 writeOutstandingDepth，同端口响应**必须按 burst 发出顺序**返回（响应无 ID）。
- 有在途 owner 的引擎绑定原物理端口，计数清零才解绑；burst 中间不得换 owner。
- 物理写端口数仍为 numPorts，仲裁器为组合选择器，不增加缓存/带宽。

直连每引擎独享响应口，只计数聚合错误，不要求跨 burst 返回顺序。下游不得在对应 EOF 之前返回响应；字节适配器支持 EOF/response 同拍计数，**共享仲裁的 owner 记录必须已建立**，不能把直连的零延迟支持推广为所有仲裁模式均可首个单拍 burst 同拍响应。

### 5.3 边界与数值行为

#### 5.3.1 地址与计数

Engine 在启动 ordinary/MX/SG/pad transfer 时用宽地址检查目的有效字节终点和源物理覆盖终点，超过 `L=2^addressWidth` 报 err；TRANSPOSE 检查源覆盖读终点及输出有效终点，IM2COL 另有几何总 span 检查。端点恰等于 L 可表示，不能先截断再比较。Dispatcher 采用宽位累计/端点 clamp（5.2.2），不是旧回绕包络。

但**不是所有表达式都有统一溢出检测**：Tile 展开 stride 乘积最终截断到 addressWidth，专用地址生成/内部计数也有有限宽度。软件必须宽整数预检所有展开 stride、地址及首尾覆盖 beat；不得依赖 clamp 把越界/回绕命令变成合法输入。logical transfer 字节计数为 24 bit，SG 总线性 offset 为 addressWidth+1 bit，SG 单条长度仍 16 bit。

#### 5.3.2 浮点转换公式与限制

正常值采用位级 RNE（round-to-nearest-even），无异常标志或完整 IEEE 运算环境：

```text
FP16 -> FP32：正常指数 e32=e16+112，尾数 m32=m16<<13，符号不变。
              e16=31 映射 e32=255；e16=0 保持 e32=0（不规格化次正规）。
BF16 -> FP32：raw32=raw16<<16。
FP32 -> FP16：q=m32>>13；up=bit12 && (bits11:0!=0 || q.bit0)。
              尾数 RNE 进位加入指数，e16=e32+carry-112。
              e32<113（含 e32=0）保号清零；e32>142 或上边界进位输出 Inf。
FP32 -> BF16：upper=raw32>>16；up=bit15 && (bits14:0!=0 || bit16)。
              raw16=(upper+up) 的低 16 bit。
FP16 <-> BF16：先扩成 FP32，再按目的规则缩小。
```

FP32->FP16 的 Inf 保留，NaN 使用 exp=31、置非零尾数并取部分 payload；不承诺完整 payload/quiet/signaling 策略。FP32->BF16 为整上半字加 RNE，没有独立 NaN 保护，某些 NaN payload 可舍入为 Inf；FP16 次正规扩展不是数学上正确的正规化转换，FP32->FP16 不生成次正规。正常数、signed zero、Inf 的规则可用；次正规及 NaN 须按上述受限实现处理，不声明全 IEEE 一致性。本轮没有数值算法修复。

#### 5.3.3 MX 数值与既有限制

原 quantizer 意图按块归约最大真指数 m（零/次正规/NaN 不贡献，Inf 贡献 127，无贡献初值 -128），scale 字节 `max(0,127+m)` 取低 8 bit。每元素 `newExp=trueExp-m+dstBias+RNEcarry`，E4M3/E5M2 bias 为 7/15、尾数为 3/2 bit；进位后指数<=0 保号 FTZ，超限/Inf 饱和，E4M3 NaN 输出 sign|0x7F，E5M2 NaN 饱和。FP16/BF16 源先位级扩为 FP32，次正规按零处理。

**字节路径更新不等于 MX numerical correctness 修复**。当前需保留以下数值边界：

- FP32 归约使用 16 bit 槽、仅偶数槽贡献；有效范围按 `slot/2<take` 判断，覆盖整拍 FP32 元素，不再遗漏后半拍最大指数。
- 当前 E4M3 饱和值编码为 sign|`0x77`（exp=14、mant=7，通常 bias=7 解码为 240），不是旧注释声称的 448；E5M2 为 sign|`0x7B`（57344）。不能宣称完整标准 MX/OCP 数值一致性。
- scale=0 与全零/极小块、Inf/NaN 组合、RNE/FTZ 边界以及量化输出握手仍需独立数值回归；测试参考模型或注释一致不能替代 RTL 证明。

MX 反量化未实现；本轮对原 quantizer 不作额外数值正确性承诺，专项修复列 A.2。

## 6. 接口规范

### 6.1 接口与信号总表

方向以 ForagerBee 为参照，全部同一时钟域。

| 接口/信号 | 方向 | 类型/宽度 | 含义与有效条件 |
|---|---|---|---|
| `cmd` | 输入 Stream | FbCmd | valid&&ready 接受，可排队；不是 Raw 二进制入口 |
| `done` | 输出 Stream | FbDone | tag/err，全部路径可反压 |
| `occupancy` | 输出 | log2Up(queueDepth+1) | FIFO 占用，不含执行或待消费回执 |
| `busy` | 输出 | 1 | OR(engine.state!=IDLE)，包含 done 反压，不含 FIFO |
| `idle` | 输出 | 1 | !busy && occupancy==0，无 cmd.valid 门控 |
| `bus[p]` | 主接口 | FbStreamBus × numPorts | 读独占，写可重映射 |
| `bus[p].rd` | 输出 Stream | addr[addressWidth]、len[8] | 对齐物理地址，len+1 beat |
| `bus[p].rdData` | 输入 Stream | data[dataWidth]、sof/eof/err 各 1 | 读数据及错误 |
| `bus[p].wr` | 输出 Stream | addr、len[8]、data、strb[BB]、sof/eof | 地址/长度仅 SOF 有效；无 atomic 字段 |
| `bus[p].wrResp` | 输入 Stream | err[1] | 每 burst 一次响应 |

chainBus、cvtScale、cvtZeroPoint 已不存在，不需绑零或保留占位端口。

### 6.2 协议与握手

所有 Stream 在 valid&&ready 时传输，发送方等待握手时保持 valid 和有效 payload 稳定。读请求返回恰好 len+1 拍并正确给 sof/eof；reader 按内部计数消费，不验证这些标记一致性，每引擎至多一个读请求在途。

写首拍 sof=1 带对齐 addr、len 和数据，其后地址/长度被忽略，物理第 i 拍对应首地址+i*BB；末拍 eof=1，单拍 sof/eof 均为 1。strb 每 bit 对应一个字节，普通短行、非对齐、pad、SG、MX 数据/scale 和 TRANSPOSE 均可能部分 strobe，不能只在转置路径支持掩码。

下游每写 burst 恰好一次响应，不得提前于 EOF；数量、路由和响应顺序见 5.2.4。done.valid 表示所有本命令读写排空，握手前保持；done.ready 可长期反压，但会阻止该引擎再接收命令。

### 6.3 接口依赖与互斥

SG 描述符 fetch 与 data reader 串行复用所属读口。转换器与 quantizer 由引擎按 transfer 模式选择，pad/scale 无源读；TRANSPOSE 输出为专用写通路，不与 ByteWriter 同时发送。共享写只映射写半部；不修改命令入口、读口或 idle 公式。

## 7. 数据表示规范

### 7.1 类型、位宽与单位

| 数据项 | 宽度/类型 | 单位与域 |
|---|---|---|
| 地址/stride | addressWidth，无符号 | byte address/byte stride；提交前检查宽位乘加 |
| shape/srcStartIdx | 每维 16，无符号 | 元素；IM2COL/SG shape(0/1) 为 byte span |
| dimCount | 3，无符号 | 1..maxDims，TRANSPOSE=2，Tile 展开=4 |
| padBefore/After | 每项 16，无符号 | dim0 元素数、dim1 行数 |
| padValue | 32，位型 | 仅低目的元素位有效，无数值转换 |
| op、srcFmt/dstFmt | 3、各 4 bit 枚举 | 编码见 7.2 |
| tag/err | 8/1 | 不透明标签/错误 Bool |
| 总线 len | 8 | beat 数减 1 |
| FbBytePacket | data[dataWidth]、bytes[log2Ceil(BB+1)]、last | 有效低字节数 1..BB，last 为该 transfer 最后 packet；内部接口 |

### 7.2 编码与打包

#### 7.2.1 操作码

FbOp 3 bit，声明顺序稳定：

| 编码 | 操作 |
|---|---|
| 0 | COPY |
| 1 | TRANSPOSE |
| 2 | PERMUTE |
| 3 | IM2COL |
| 4 | SCATTER |
| 5 | GATHER |
| 6 | TILE2LINEAR |
| 7 | LINEAR2TILE |

#### 7.2.2 数据格式

现有编码保持不变，不因删除 INT8 转换而删除 INT8 搬运格式。

| 编码 | 名称 | elemBits / bytesOfCeil | 当前用途 |
|---|---|---|---|
| 0 | FP32 | 32 / 4 | 搬运、浮点转换、MX 源 |
| 1 | FP16 | 16 / 2 | 同上 |
| 2 | BF16 | 16 / 2 | 同上 |
| 3 | INT8 | 8 / 1 | 同格式搬运；**无 shift 量化/反量化** |
| 4 | MXE4M3 | 8 / 1 | 量化目的；无转换构建可同格式裸搬运 |
| 5 | MXE5M2 | 8 / 1 | 同上 |
| 6 | 保留 | — | 非法，不复用 |
| 7 | 历史 NONE 保留 | — | 非法，永久不复用 |
| 8 | INT32 | 32 / 4 | 同格式搬运，算术转换未实现 |
| 9 | INT16 | 16 / 2 | 同格式搬运，算术转换未实现 |
| 10 | INT4 | 4 / 1（辅助值） | 受限 COPY，实际行字节按 elemBits 计算 |
| 11..15 | 保留 | — | 非法 |

保留/非法码报 done.err，不允许作为 converter bypass 哨兵。

#### 7.2.3 FbCmd 字段

| 字段 | 宽度 | 含义 |
|---|---|---|
| op、tag、dimCount | 3、8、3 | 操作/标签/活动维数 |
| shape[maxDims] | 每项 16 | 普通元素数；IM2COL/SG shape(0/1) 源/目的 byte span |
| srcStride/dstStride[maxDims] | 每项 addressWidth | 源/目的 byte stride；MX scale 外层复用 dstStride |
| srcStartIdx[maxDims] | 每项 16 | 源裁剪索引 |
| t2lMatCols、t2lTileRows、t2lTileCols | 各 16 | N、Tm、Tn |
| t2lNumTileRows、t2lNumTileCols | 各 16 | Mt、Nt |
| srcAddr、dstAddr | 各 addressWidth | 逻辑基址，可非 beat 对齐 |
| permVec[maxDims] | 每项 3 | 源外层 stride 索引 |
| padBefore[2]、padAfter[2] | 每项 16 | COPY dim0/dim1 填充数 |
| padValue | 32 | COPY/IM2COL 目的域填充值位型 |
| srcFmt、dstFmt | 各 4 | 格式枚举 |
| im2colKernel/Stride/Pad/Dilation[2] | 每项 8 | kH/kW、sH/sW、pH/pW、dH/dW |
| im2colInShape[3] | 每项 16 | C/H/W |
| sgListAddr、sgEntryCount | addressWidth、16 | beat 对齐表基址、条目数 |
| scaleAddr、scaleBlockSize | addressWidth、2 | MX scale 基址，0/1/2 -> 块 16/32/64 |

未用字段应清零；IM2COL/SG 即使 dimCount=1 仍应写 shape(1) 目的 span。FbCmd 是结构化 Bundle，不定义 raw 位序或内存序列化 ABI。`FbSharedCmd` 仍是未引用的 3×32 bit 占位 Bundle，不是 decoder 或已实现 Raw 编码。

#### 7.2.4 FbDone / FbWriteBeat

FbDone 只有 tag[8] 和 err[1]。FbWriteBeat 只有 addr[addressWidth]、len[8]、data[dataWidth]、strb[BB]、sof/eof 各 1；含义见第 6 章。删除字段不会保留“读取无效值”的兼容槽。

#### 7.2.5 SG 条目

```text
每条目一个完整 beat：
bits[addressWidth-1:0]             = addr
bits[addressWidth+15:addressWidth] = lenBytes（1..65535；0 报错）
其余高位忽略；entry[i] 地址 = sgListAddr + i*BB。
```

#### 7.2.6 v2.0.0 ABI 删除清单

| 载体 | 删除项 |
|---|---|
| FbCmd | nextDescAddr、chainMode、atomicEnable、atomicStoreAddr |
| FbDone | chainLen |
| FbWriteBeat | atomic |
| ForagerBeeConfig | enableChaining、chainDescBeats |
| 顶层 IO/实现 | chainBus、cvtScale、cvtZeroPoint、FbChainController、INT8 shift 量化/反量化逻辑 |

新增 FbCmd.padValue 和顶层 idle；FB-F010 缩窄为浮点转换。旧链式描述符内存位布局已失效，不能提交或按旧 Bundle/端口打包。软件/上游须按新字段重新适配；不承诺恢复已移除 INT8 shift 或全局活线。

### 7.3 存储布局与地址约定

#### 7.3.1 维度

dim0 连续，普通活动 shape 非零；COPY/PERMUTE 不使用 stride(0) 生成 dim0 地址。maxDims 配置 1..5，但完整顶层因无条件实例化 4D 展开器实际需 4 或 5（8.1）。

#### 7.3.2 COPY/PERMUTE 行地址

```text
无 padding：
srcRow = srcAddr + rowBytes(srcStartIdx(0),srcFmt)
         + Σ[d=1..dimCount-1](outerIdx[d]+srcStartIdx[d])*srcStride[d]
dstRow = dstAddr + Σ[d=1..dimCount-1]outerIdx[d]*dstStride[d]

有 padding：rowCursor 遍历 sourceRows+padBefore(1)+padAfter(1)，
sourceRows = dimCount>1 ? shape(1) : 1。
数据行源 dim1 索引 = rowCursor-padBefore(1)+srcStartIdx(1)，
目的 dim1 索引 = rowCursor，数据起点另加 padBefore(0)*dstElemBytes。
整行目的字节 = (padBefore(0)+shape(0)+padAfter(0))*dstElemBytes。
```

PERMUTE 把源各外层 srcStride[d] 替为 srcStride[permVec[d]]；目的和遍历计数不变。TRANSPOSE、IM2COL、SG 分别使用 5.1.4/6/7 专用地址语义。

#### 7.3.3 对齐、覆盖读与 4 KiB

```text
logical source = [S,S+N)
physical read  = [alignDown(S,BB),alignUp(S+N,BB))
physical write beats 从 alignDown(D,BB) 开始，strb 仅覆盖 [D,D+M)。
```

所有实际 rd 和 wr SOF 地址必须 beat 对齐，硬件负责字节适配；逻辑源/目的地址、逐行 stride、短行和尾部无需 beat 对齐（SG 表/MX scale 基址例外）。源首尾额外覆盖字节虽不进入结果，仍会被物理读取：**覆盖 beat 全部必须可访问、无读取副作用，不得用于 MMIO 或不可读保护边界**。目的未使能 strobe 字节保持不变，依赖下游正确 byte-write。

当前不按 4 KiB 边界自动拆分，没有该边界的独立错误检查；maxBurstLen 不是 4 KiB 保证。下游若禁止跨界，软件须对包含首尾对齐扩张的每个 burst 预检并拆分命令，或由外部总线桥合法拆分，不能声称 ForagerBee 内部已有该能力。

地址范围计算须先宽位累计、再源 start 下取整/end 上取整，端点至少 addressWidth+1 bit，不允许绕回地址 0；软件也须检查展开 stride 不发生截断。硬件包络端点 clamp 与 transfer 越界错误的区别见 5.2.2/5.3.1。

#### 7.3.4 MX scale 布局

```text
scaleRow = scaleAddr + Σ[d=1..dimCount-1]outerIdx[d]*dstStride[d]
每行 scale 字节数 = shape(0)/blockSize。
第 b 块 scale 在 scaleRow+b；累积 BB 个或到行末后写出。
```

scale 基址 beat 对齐但外层行可偏移；writer 精确首尾 strobe。scale 行 stride 不是专用紧密 scale stride，软件必须显式分配足够跨度且不与数据区重叠。

#### 7.3.5 Tile 展开

令 N=t2lMatCols、Tm=t2lTileRows、Tn=t2lTileCols、Mt=t2lNumTileRows、Nt=t2lNumTileCols、e=bytesOfCeil(srcFmt)：

```text
shape         = [Tn,Tm,Nt,Mt]
tiled stride  = [e,N*e,Tn*e,Tm*N*e]
linear stride = [e,Tn*e,Tm*Tn*e,Nt*Tm*Tn*e]
TILE2LINEAR：源 tiled、目的 linear；LINEAR2TILE：反之。
```

遍历 dim1/2/3 即 tile 内行/tile 列/tile 行；软件保证 N、tile 数、访问大小一致，并宽位验证所有最终 stride，展开器不会检查乘积截断。

## 8. 配置与寄存器规范

### 8.1 编译期参数

| 参数 | 默认值 | 合法范围/影响 |
|---|---|---|
| numPorts | 2 | 1..4；引擎/物理端口数 |
| queueDepth | 8 | >=1；命令 FIFO |
| dataQueueDepth | -1 | <=0 自动 max(maxBurstLen,4)，否则 >=1；物理读队列 |
| maxDims | 5 | 配置 require 1..5；**完整顶层实际 4/5**，展开器固定访问 0..3；独立 Engine 目前亦访问 shape(1)，实际需 >=2 |
| tileSize | 8 | >=2；tileSize² 个 32 bit 槽；大参数另有内部宽度限制 |
| maxBurstLen | 64 | 1..256 beat；读写全部路径的物理 burst 上限 |
| dataWidth | 128 | **2 的幂且 >=32，配置 require 已检查**；BB=dataWidth/8 |
| addressWidth | 32 | >=12；不代表已实现 4 KiB split |
| channelTranspose/Permute/Im2col/ScatterGather | 空 | 空时每通道 true，非空长度=numPorts；各能力独立 |
| enableConversion | false | 生成 float converter 和 MX quantizer；不生成 cvt* IO |
| converterPipeStages | 1 | 当前传入但不改变转换结构；不能当作有效流水深度 |
| enableSharedWritePorts | false | 生成写仲裁器 |
| writeOutstandingDepth | 1 | >=1，1 阻塞、>1 单 transfer 内 NB |

SG 硬件额外要求 dataWidth>=addressWidth+16，否则该通道 SG 命令报错。转换器另要求 dataWidth 为 32 的整数倍、MX 要求 >=32 的 2 次幂，配置已涵盖。maxDims 的 4D expander 限制没有在配置 require 完整检查，不能只看配置通过就声明完整顶层支持 1..3。

上述为 ForagerBeeConfig 默认值。当前 ForagerBeeElaborate 导出入口采用 numPorts=2、queueDepth=4、maxDims=5、tileSize=16、dataWidth=256、maxBurstLen=64、enableConversion=true，transpose=[true,false]、permute=[true,true]、im2col=[false,true]、SG=[true,true]；另选择共享写、depth=4、两者组合。导出配置/入口存在不表示这些配置仿真或 PPA 已验证。

### 8.2 运行期配置

所有运行参数由 FbCmd 随命令传递，在引擎派发握手锁存；无全局活线、不需要静默期修改 cvt 参数。padValue 为每命令字段，不能修改已接受命令。idle 只是观测，不限制 cmd.valid 或命令连续提交。

### 8.3 寄存器映射

不适用：没有 MMIO/CSR 寄存器接口和软件 raw decoder。命令/状态通过 Stream 与电平输出；未来 CSR/驱动属 A.2 规划。

## 9. 时序与复位规范

### 9.1 时钟与跨域

单隐式主时钟，上升沿采样；内部无 CDC 或显式 power/clock gating。跨域命令、总线、状态消费由外部负责，不引入已删除 cvt* 的跨域义务。

### 9.2 复位与初始化

Chisel 隐式复位类型由集成环境决定，独立默认同步高有效。复位后 Engine IDLE、FIFO/包络有效位清空、adapter busy/outstanding 清零，idle 为真；缓冲未初始化数据不能在有效传输前使用。

**复位撤销已接受总线事务未定义/待确认**，不能当作 abort。外部应停止提交、消费 done、等 idle 且下游排空后共同复位。异常停滞时的系统级恢复不是本模块已实现能力。

### 9.3 事务时序与反压

读请求稳定后握手，预留全部物理 beat credit，接收 len+1 拍；读数据、写数据和 packet 均可反压，不丢弃有效字节。写最后 EOF 不是 transfer 完成，writer 等全部响应后 done；Engine 才推进普通行/段/条目。TRANSPOSE 专用规则见 5.2.4。

FIFO 满则 cmd.ready=0，队首等待不出队。done 反压保持 busy/in-flight；回执握手后才重用该引擎。没有额外 cmd.valid gate：空闲时 valid 可以为 1，命令被接受后的状态在时钟沿更新；idle 可在接受新命令的该沿之前仍为 1。

## 10. 异常处理规范

### 10.1 检测与上报（FB-F020、FB-F026）

当前仅 done.err，无细分错误码、异常中断或回滚。

| 异常项 | 检测/上报 | 执行影响 | 清除/恢复 |
|---|---|---|---|
| dimCount/shape | 0、超 maxDims 或活动 shape=0 -> err | 命令校验拒绝，不发数据传输 | 消费回执，下一命令独立 |
| 能力/TRANSPOSE/PERMUTE | 能力缺失、transpose 非 2D、perm 映射越界、非法格式/padding -> err | 同上 | 同上 |
| 格式/INT4/MX | 保留码、未支持跨格式、MX 源、奇数 INT4、MX 块/pad/scale 基址不合法 -> err | 同上 | 同上 |
| IM2COL 几何 | 零参数、有效核超过 padded 输入、输出维>65535、几何总地址越界 -> err | 同上 | 同上 |
| SG 表 | 无硬件、count=0、表不对齐/表范围越界 -> err | 同上 | 同上 |
| Tile 输入 | 参数为零、INT4 或跨格式，由展开器置 dimCount=0 -> err | 同上 | 同上 |
| transfer 地址 | 源物理覆盖终点/目的有效终点超地址域 -> err | 终止后续 transfer，可能此前已写 | 结果视为部分更新 |
| SG 零长度 | 条目 curLen=0 -> err | 终止，不搬此条目，之前写不回滚 | 同上 |
| 数据读/写响应 | rdData.err、wrResp.err 聚合 -> err | 通常继续既定传输后上报 | 消费回执；无自动重试 |
| SG 表读错误 | **现已聚合** fetch rdData.err -> err | 仍可解析/搬运条目，不保证立即停止 | 同上 |
| 总线标记/数量违例 | 不完整检测；共享响应空 owner 有仿真 assert | 可能停滞/错误；assert 不是 done 错误码 | 环境必须保证协议 |
| hazard 盲区/命令内别名 | 不检测 | 数据竞争或覆盖 | 软件串行化/隔离 |
| Tile stride 截断/部分专用算术 | 无统一溢出错误覆盖 | 可能访问错误地址 | 软件宽位检查 |
| 浮点/MX 数值限制 | 无数值异常码 | 位级结果受 5.3 限制 | 不把 err=0 当作标准数值正确证明 |

逻辑 src/dst 非 beat 对齐、短行和任意正 SG len **不是错误**；物理 bus 地址不对齐是实现/协议违例，不再由旧基址对齐校验拒绝逻辑命令。

### 10.2 优先级与恢复

错误在单命令内粘滞 OR 聚合，无错误码优先级；转置读完成与写响应错误同拍时保留错误。入口校验失败只返回 err；运行时错误可能在已有写入之后发现，排空既定总线工作后完成，没有撤销/回滚/重试。软件收到 err 时必须视目的与 scale 可能部分更新。DONE 阶段复位局部转置缓冲，避免地址拒绝污染下一条命令；后续命令重新锁存/清理错误状态。

## 11. 状态与观测规范

### 11.1 状态输出（FB-F027）

| 状态 | 属性 | 精确定义 |
|---|---|---|
| busy | 组合电平 | 任一 Engine state!=IDLE；含执行、响应等待、DONE/回执反压 |
| occupancy | 电平计数 | Dispatcher FIFO 当前条目数，不含 in-flight |
| idle | 组合电平 | **`!io.busy && io.occupancy===0`**；已接受工作及回执完全排空 |
| done | Stream | 所属命令传输/响应结束，握手消费 tag/err |

idle 不额外与 `!cmd.valid` 相与，也不是 cmd.ready 门控。尚未握手的上游命令不算已接受工作；若要建立不会再进入新命令的静默窗口，命令源还须自己停止提交。只看 busy 可能忽略 FIFO；done 被反压时 busy=1、idle=0。此定义仅涵盖 ForagerBee，不证明外部系统所有主机/存储均空闲。

### 11.2 中断、性能事件与调试

不适用：没有中断输出、内建性能计数器/事件、调试端口或 atomic 轮询通知。完成只有 done Stream。仿真内部信号、测试统计和仲裁 assert 不是产品观测 ABI。

## 12. 性能与资源规格

### 12.1 结构与容量

| 结构 | 表达式 | 默认配置 |
|---|---|---|
| 并行 Engine / 命令 FIFO | numPorts / queueDepth | 2 / 8 |
| 每引擎物理读队列 | resolvedDataQueueDepth beat | 64 beat |
| reader/writer 字节储存 | 各 2*dataWidth bit；writer 另有 2*BB strobe | 各 32 B 数据 |
| 单引擎读请求在途 | 1 | 1 |
| 写完成 burst 等响应 | <=writeOutstandingDepth | 1 |
| 转置缓冲 | tileSize²*32 bit | 8×8×32 bit |
| MX block 缓冲 | max(1,2048/dataWidth) beat | 16 beat |
| MX scale 累积 | BB 个字节 | 16 B |
| 共享 NB owner FIFO | 每物理端口 writeOutstandingDepth | 默认不生成 |

### 12.2 延迟与吞吐

| 指标 | 数值/表达式 | 属性与条件 |
|---|---|---|
| 并行命令 | 至多 numPorts | 结构上限；无冲突、能力匹配 |
| burst 长度 | <=maxBurstLen | 物理剩余、读 credit、transfer 边界共同限制 |
| 峰值物理总线宽度 | dataWidth bit/端口/拍 | 接口上限，**不是持续有效载荷吞吐保证** |
| 延迟/有效字节吞吐 | 待测量 | 包括首尾额外读、packet 装卸、转换、写响应和调度气泡 |
| NB 写 | 同 transfer 内最多配置深度等响应 | 普通行/段/SG 条目边界仍等全部响应 |
| TRANSPOSE | tile fill 后 drain | 无双 tile 缓冲，源行按列 tile 重读 |
| IM2COL fallback | 每 kw 独立 transfer | dW!=1/边界情况请求多，局部复用未实现 |
| SG / MX | 逐条 fetch / collect-quant-drain | 无 SG 预取；MX scale 与数据共享写口 |

reader/writer 储存追加与输出刻意互斥，转换/转置/MX 也有各自节奏，不能据接口宽度宣称逐拍满带宽。converterPipeStages 不改变结构；不宣称此次重写的 throughput 或 RTL 与旧阻塞路径逐字相同/零回归。共享仲裁不增加物理端口数量，实际性能须带负载/反压/起止口径测量。

### 12.3 面积、功耗与测量依据

未提供实测值；没有已验证的资源/频率/功耗收益。须记录 RTL 基线、配置、工具、工艺、频率、负载及有效字节/总周期统计，综合和功耗测量后再补充。局部 NPU 数据复用、power gating 及资源性能评估见 A.2，不是当前能力。

## 13. 集成与使用规范

### 13.1 外部环境要求

存储系统支持对齐 beat 请求和 byte strobe，每读恰好 len+1 拍、每写恰好一次响应，共享写保序且不提前响应。源首尾覆盖 beat 均可访问且无副作用；不可把字节 reader 用在 MMIO。4 KiB 禁跨界由软件/外部桥承担。软件管理外部一致性、可见性、地址权限及未覆盖的竞争；本文不声明与 Hive 或其他系统模块已集成。

### 13.2 使用流程

1. 获取实际 cfg、BB、能力和 enableConversion；完整顶层选 maxDims=4/5。
2. 填合法格式/活动 shape，清零未用字段；源/目的 stride 均为字节，padValue 已是目的位型。
3. 宽位算所有逻辑地址、展开 stride、源覆盖 beat；保证缓冲/SG 表/scale 可访问、不回绕，按下游要求处理 4 KiB。
4. IM2COL/SG 填 shape(0/1) 实际源/目的 byte span；无法表达或 crop/transpose/SG 表/并集盲区可能冲突时软件串行化；同命令别名区隔离。
5. 用 tag 跟踪 outstanding，valid&&ready 才算提交；不要求等 idle 才提交，也不依赖完成顺序。
6. done.valid&&done.ready 消费 tag/err；失败结果可能部分更新，无 atomic/chain 补充通知。
7. 所有命令完成后 idle 反映本模块排空；复位/改变系统资源前还须停上游并排空下游。

### 13.3 使用示例

默认 BB=16，以下为契约示例，不表示仿真已经通过。

**例 1：跨 beat 短 COPY。** INT8 同格式、dimCount=1、shape(0)=5，srcAddr=0x100F、dstAddr=0x2003：源物理读 `[0x1000,0x1020)`，仅 5 个逻辑字节写到 `[0x2003,0x2008)`，目的 strobe=0x00F8，其他字节不改。源两个覆盖 beat 均须可读。

**例 2：浮点压缩与目的域 padding。** enableConversion=true、COPY、shape=[5]、FP32->FP16，padBefore(0)=1、padAfter(0)=2、padValue=0xA5A53C00：读 20 个逻辑源字节，转换写 10 B；前写一个、后写两个 0x3C00 FP16 位型，共 16 B，padValue 高 16 bit 不用。非对齐目的可跨两个物理 beat，未使能字节保持。

**例 3：INT4 边界。** 同格式 COPY、shape(0)=10、srcStartIdx(0)=2：裁剪偏移 1 B，搬 5 B，短行合法；shape(0)=9 或起始索引奇数则 err。软件串行化 crop 源 hazard。

**例 4：SG 精确长度。** 两条 GATHER len=3、5，第二条目的起点为 dstAddr+3，不是 dstAddr+16；任一零长度条目报错。描述符仍每条一个完整 beat。

## 14. 约束与实现限制

### 14.1 支持边界与已知限制

| 项目 | 当前限制/影响 | 使用义务 | 引用 |
|---|---|---|---|
| dispatcher 盲区 | crop、transpose dst、SG 表/不完整 span、稀疏 unions、命令内别名 | 软件串行化/区域隔离，不作完整 hazard 声明 | 5.2.2 |
| IM2COL bbox | 仍用软件 shape(0/1) byte span，不用生成几何 | 填真实范围，16 bit 不够时串行化 | 5.1.6 |
| 源额外读 | 非对齐/尾部读覆盖整个 beat | 全部可读、无副作用，非 MMIO | 7.3.3 |
| 4 KiB | 不自动 split/检测 | 软件或外部桥处理 | 7.3.3 |
| 写重叠边界 | NB 仅同 transfer 多 burst；普通行/pad/SG 等 done | 不沿用旧跨行 EOF 推进/吞吐假设 | 5.2.4 |
| maxDims | 完整顶层 4/5，配置仅检查 1..5 | 区分独立 Engine 与完整顶层 | 8.1 |
| 极大 tile 参数 | beatsPerCol/输出 beat 计数仅 8 bit，打包移位位宽有限；无完整 require | 保证每列物理 beat<=255、有效打包偏移可表示；未验证参数不用 | 5.1.4、8.1 |
| 算术/别名 | Tile stride 截断，无统一全地址检查或 memmove 保证 | 宽位预检且隔离重叠 | 5.3.1 |
| 浮点/MX 数值 | 次正规/NaN 受限、MX 编码问题仍待专项处理 | 按受限位型契约，不宣称标准数值正确 | 5.3.2/3 |
| PERMUTE/Tile | 连续维固定、不检查双射/矩阵参数一致 | 软件构造正确布局 | 5.1.5/8 |
| 协议/复位 | 标记/响应数量非全覆盖，复位 abort 未定义 | 合法总线、排空后复位 | 6、9 |
| 性能 | converterPipeStages 无结构作用，无测量结果 | 不承诺满带宽/RTL 相同/PPA 收益 | 12 |

### 14.2 不支持能力与待确认事项

- **已移除**：FB-F013 全局转换活线及 INT8 shift 转换、FB-F018 chaining、FB-F019 atomic。不是规划恢复项，旧字段/描述符 ABI 不可用。
- **未实现/规划中**：Raw DW decoder、tileOrder/T2T/单命令 reblock、MX dequant、INT16/INT32/INT4 算术转换、4 KiB split、细分错误码；详见 A.2。
- **待确认**：在途复位恢复、极端参数化、数值完整正确性、资源/功耗/性能与公平性界限。

**当前验证状态（2026-10-23）**：本轮主源码和测试编译已通过；ForagerBeeSpec 完整保留功能回归 146/146 通过，ForagerBeeByteSpec 的 6/6 个 RTL 仿真用例通过。后者覆盖 4 组顶层配置的 92 条命令及 2 组独立转换路径的 8 次传输。此前偶发的缺失 simulation-log.txt 问题未复现于最终完整回归，根因仍未确定；不能把更换持久化仿真目录视为根因证明。

已通过回归覆盖普通/共享、阻塞/NB、128/256 bit、短行/非对齐、转换 packet bytes/last、SG 零长度、IM2COL 水平/dilation、非零 padValue、转置边缘与地址错误恢复、MX scale、delayed-response/done 反压，以及转置读完成与写响应错误同拍聚合；字节回归另检查 sentinel/邻字节和写 strobe。MX 数值完整性、hazard 盲区及极端配置仍有专项缺口。

## 附录：补充资料

### A.1 常用公式

```text
BB = dataWidth/8；burstBeats=len+1；rowBytes=ceil(n*elemBits/8)
physicalReadBeats=ceil((srcAddr%BB+sourceBytes)/BB)
physicalWriteBeats=ceil((dstAddr%BB+destinationBytes)/BB)
L=2^addressWidth；区间终点保留至少 addressWidth+1 bit，源覆盖读终点<=L。
resolvedDataQueueDepth=dataQueueDepth>0 ? dataQueueDepth : max(maxBurstLen,4)
MX blockSize=16/32/64；scalesPerRow=shape(0)/blockSize
idle=!busy && occupancy==0（无 cmd.valid gate）
```

### A.2 其他待优化项

以下用 `FB-OPTnnn` 作**可查询的行动项编号**，不是新增已实现 Feature ID。所有项均为规划中/非当前 RTL 契约；只列候选工作与验收方向，不构成排期或交付承诺。已删除 INT8 shift/global live IO 不在恢复承诺中。

| 行动项 | 主题/关联 ID | 下一步与验收方向 |
|---|---|---|
| FB-OPT001 | error 细分（FB-F026） | 定义兼容错误载体、分类/优先级/失败阶段，覆盖非法命令、descriptor/data/bus 错误与部分更新语义 |
| FB-OPT002 | Raw DW 变长（FB-F021） | 规划变长 32 bit DW、最多 4 维、源/目的独立 stride-present flag，显式 stride 为 16 bit **element stride**；连续侧省略 stride，由 decoder 推导，再转当前 byte stride；定义版本/位域/长度/保留码并测非法长度和乘法溢出。INT4 不能精确转字节时拒绝；当前没有 decoder，位分配/各 op DW 数未定，不可当软件 ABI |
| FB-OPT003 | tileOrder/T2T（FB-F022） | 定义运行时 tile 顺序字段、独立 T2T 编码/展开和边缘语义；与同尺寸 4D COPY 对照验证 |
| FB-OPT004 | reblock/MX dequant（FB-F023） | 设计单命令不同 tile 尺寸重分块、scale 读布局和 MX 反量化；与当前软件 T2L+缓冲+L2T 区分，验证分块/scale 边界 |
| FB-OPT005 | numerical correctness | FP32 max 槽掩码已修正；后续处理 E4M3 规范/饱和编码及握手边界；确定 E8M0 极值、RNE/FTZ/Inf/NaN，补独立 oracle；浮点次正规/NaN 策略和 INT16/INT32/INT4 算术（FB-F024）另行定义，不把本轮字节路径改动算作数值修复 |
| FB-OPT006 | 4 KiB split（FB-F025） | 对 reader、writer、TRANSPOSE、SG fetch 加物理边界限制，检查非对齐覆盖扩张、len/credit/strobe 和边界地址 |
| FB-OPT007 | complete hazard coverage | 纳入 crop、TRANSPOSE 实际 dst、SG descriptor/数据并集和命令内 src/dst/scale；IM2COL 从几何推包络并校验软件 span；定义别名/外部一致性边界，测试 RAW/WAR/WAW、宽位溢出及假冲突 |
| FB-OPT008 | head-of-line / RR fairness | 评估多队列、队首旁路或可配置 RR；量化当前最小编号派发和写端口 RR 的饥饿/等待，定义适用负载与公平性验收 |
| FB-OPT009 | multiple read outstanding / coalescing | 设计读事务 ID/返回关联、physical-credit 预留、重排及小 transfer 合并；兼顾 tail、SG 预取和 writer transfer 间响应语义 |
| FB-OPT010 | dW fallback performance | 合并 IM2COL 有效水平片段、复用覆盖 beat/减少逐 kw 启动；以 dW>1/边界窗口请求数、有效字节/周期量化，保持无效 pad 不读源 |
| FB-OPT011 | local NPU data reuse | 探索本地窗口/tile 双缓冲、复用转置重读和 IM2COL 重复输入；先定义局部接口/所有权，不假定已连接任何 Hive/NPU |
| FB-OPT012 | power gating / resource-perf measurement | 评估独立引擎/能力单元时钟或电源门控、隔离/唤醒与 idle 排空；综合测面积/Fmax/功耗和有效吞吐，记录配置、工艺、工具、负载 |
| FB-OPT013 | CSR / software driver future | 定义可选 CSR/软件提交、tag 生命周期、错误恢复、地址/缓存管理及 Raw ABI 版本；当前只有结构化 Stream，不宣称驱动/系统集成已交付 |

### A.3 发布前检查与待验证

- 14 章、版本日期和稳定 ID 对齐；FB-F013/018/019 保留为已移除，新增 FB-F027–030；操作/格式编码未重排。
- 当前字段、字节有效数、物理对齐、padValue、响应排空与 idle 均以主源码为准，规划与删除项不混入当前功能。
- ForagerBeeSpec 完整回归 146/146、ForagerBeeByteSpec 6/6 已通过，结果已记录到 14.2。
- 已通过回归检查 sentinel/邻字节不改、短行/跨 beat、bytes/last 稳定、SG 表/零长度错误、IM2COL pad 无源读、MX 行末 scale、共享响应路由、错误同拍聚合和 done 反压；数值/盲区/PPA 另有专项缺口。
