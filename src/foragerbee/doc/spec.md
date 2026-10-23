# ForagerBee DMA 硬件规格

文档版本：`v1.0.0`

修订日期：`2026-10-23`

## 版本控制

### 文档版本规则

文档采用 `v主版本.次版本.修订号`：

- 主版本：外部可见规格契约发生不兼容变更；
- 次版本：增加兼容的功能描述或规格章节，不改变既有功能契约；
- 修订号：修正文案、排版或引用，不改变功能契约。

版本号仅标识本文档修订，不代表 RTL 发布版本、命令编码版本或芯片型号。每次发布文档修订必须同步更新首页版本/日期及下表，日期使用 `YYYY-MM-DD`；若修改影响功能契约，还应记录受影响的 Feature ID。Feature ID 按第 3.1 节保持稳定。

### 修订记录

从 v1.0.0 开始显式记录文档版本；此前未编号草稿不追溯编造版本号或发布日期。

| 版本号 | 日期 | 修改内容 | 相关 Feature ID |
|---|---|---|---|
| v1.0.0 | 2026-10-23 | 按通用硬件规格模板建立首个编号基线：重组为 14 个编号章节与附录，新增带稳定 ID 的功能清单；技术契约沿用此前未编号草稿，并与当前 RTL 核对 | FB-F001–FB-F026 |

## 1. 文档说明

### 1.1 目的

本文定义 ForagerBee 多引擎张量 DMA 的外部可见行为，包括命令语义、数据格式、存储布局、调度与并发、总线协议、完成与错误、配置约束和集成义务。

本文以当前 RTL 为准描述已实现能力。尚无 RTL 实现的设计方向统一标注“**规划中/非当前 RTL 契约**”，汇总于第 14 章，不构成当前交付承诺。功能清单见第 3 章，版本规则与修订记录见文首“版本控制”章节。

### 1.2 范围

本文覆盖：

- ForagerBee 顶层及其内部子模块（链式控制器、Tile 展开器、调度器、引擎、格式转换、MX 量化、IM2COL 地址生成、Scatter/Gather 控制、写端口仲裁）的外部可见行为；
- 结构化命令 `FbCmd`、完成回执 `FbDone` 及 `FbStreamBus` 读写协议；
- COPY、TRANSPOSE、PERMUTE、IM2COL、SCATTER、GATHER、TILE2LINEAR、LINEAR2TILE 及其组合功能；
- 链式描述符内存布局、atomic 完成标志、全局转换参数活线；
- Raw DW 变长命令编码的设计方向（规划中/非当前 RTL 契约）。

本文不定义 HiveCore、LLC、Queen、Gingercat 或系统互连/存储器的实现，只定义 ForagerBee 依赖的接口契约（见第 2.2 节和第 13.1 节）。

### 1.3 术语与规范性用语

“必须/不得”表示强制契约：违反时命令被拒绝、结果未定义或与当前 RTL 不一致，也包括为规避当前 RTL 缺陷而对集成和软件提出的约束；“应”表示推荐要求；“可”表示可选能力。“不适用”表示模块确实无该项；“待确认”表示信息或验证尚未完成。“规划中/非当前 RTL 契约”表示尚无 RTL 实现，不能作为当前硬件能力使用。源码注释与实际逻辑不一致时以实际逻辑为准，例如当前 `maxDims` 实现上限为 5，而非部分旧注释中的 4。

| 术语/符号 | 定义 | 单位或适用范围 |
|---|---|---|
| beat | 总线上一次 `dataWidth` 位数据传输 | 拍 |
| `beatBytes` | `dataWidth/8`，必须为 2 的幂 | 字节；默认 16 |
| burst | 一次 `rd` 或 `wr` 请求及其数据，共 `len+1` 个 beat | beat |
| 引擎/通道 | 一个 `FbEngine` 实例，编号 `p ∈ [0, numPorts)`，与 `bus[p]` 一一对应 | 默认 2 个 |
| dim0 | 最内层连续维；数据行沿 dim0 展开为连续 burst | — |
| `outerIdx[d]` | 外层维 `d`（`d≥1`）的当前遍历计数 | 元素 |
| `rowBytes(n, fmt)` | `ceil(n × elemBits(fmt) / 8)` | 字节 |
| byte stride | 当前 `FbCmd` 中 stride 的单位 | 字节，`addressWidth` 位 |
| element stride | Raw DW 草案中 stride 的单位 | 元素，16 位（规划中） |
| in-flight 命令 | 已派发到引擎、对应引擎 `done` 尚未握手的命令 | 每通道至多 1 条 |
| HOL | 队首阻塞（head-of-line blocking） | 调度器 |
| 静默期 | `busy==0 && occupancy==0`，且启用链式时整条链最终 `done` 已返回 | — |
| 默认配置 | 未覆盖 `ForagerBeeConfig` 参数时的配置 | 见第 8.1 节 |

除非另有说明，所有地址均为字节地址，多字节数据按 little-endian 存放。

### 1.4 参考资料与适用基线

| 资料 | 版本或路径 | 用途 |
|---|---|---|
| 顶层集成 | `src/foragerbee/scala/main/ForagerBee.scala` | 端口、子模块连接、done 汇聚、`busy` 定义 |
| 编译期配置 | `src/foragerbee/scala/main/ForagerBeeConfig.scala` | 参数、默认值、`require` 约束 |
| 总线与命令定义 | `src/foragerbee/scala/main/FbStreamBus.scala` | `FbStreamBus`、`FbCmd`、`FbDone`、枚举编码 |
| 调度器 | `src/foragerbee/scala/main/FbDispatcher.scala` | 命令 FIFO、hazard 范围模型、通道选择 |
| 引擎 | `src/foragerbee/scala/main/FbEngine.scala` | 执行状态机、命令校验、地址生成、写通路、atomic |
| 链式控制器 | `src/foragerbee/scala/main/FbChainController.scala` | 描述符布局 `FbDescLayout`、链执行与最终 done |
| Tile 展开器 | `src/foragerbee/scala/main/FbTile2LinearExpander.scala` | T2L/L2T 到 4D COPY 的展开 |
| 转置缓冲 | `src/foragerbee/scala/main/FbTransposeBuffer.scala` | TRANSPOSE tile 缓冲 |
| 格式转换 | `src/foragerbee/scala/main/FbFormatConverter.scala` | 普通格式转换数值语义 |
| MX 量化 | `src/foragerbee/scala/main/FbMxQuantizer.scala` | MX 量化与 E8M0 scale |
| 写端口仲裁 | `src/foragerbee/scala/main/FbWrPortArbiter.scala` | 共享写端口与 owner 路由 |
| IM2COL 地址生成 | `src/foragerbee/scala/main/FbIm2colAddrGen.scala` | IM2COL 遍历与 padding |
| SG 控制 | `src/foragerbee/scala/main/FbScatterGatherCtrl.scala` | 地址表 fetch 与条目格式 |
| 回归测试 | `src/foragerbee/scala/test/ForagerBeeSpec.scala` | 已验证行为；测试存在不扩展 RTL 契约 |
| 规格模板 | `src/doc/spec_template.md` | 本文章节结构 |

## 2. 模块概述

### 2.1 定位与职责

ForagerBee 是多引擎张量 DMA。它从单一命令入口接收结构化命令 `FbCmd`，按命令描述的多维形状、stride、裁剪、padding、格式转换等语义，在存储系统中读取源数据并写到目的位置，完成后通过 `done` 返回带 tag 的完成回执。

命令先经过可选链式控制器和 Tile 语法糖展开器，再进入 FIFO 与调度器；调度器把队首命令派发给一个空闲且具备相应能力的引擎。每个引擎具有独立读通道、数据队列和执行状态机，可并行执行不同命令。

当前 RTL 实现 COPY、TRANSPOSE、PERMUTE、IM2COL、SCATTER、GATHER、TILE2LINEAR 和 LINEAR2TILE。TILE2LINEAR/LINEAR2TILE 在调度前被组合展开为 4D COPY，不是引擎内独立数据通路。

### 2.2 系统边界

| 外部对象 | 与本模块的交互 | 外部承担的责任 |
|---|---|---|
| 命令源（软件或上游控制器） | 经 `cmd` 提交 `FbCmd`；经 `done` 消费完成回执；读 `busy`/`occupancy` | 填写合法字段并清零未用字段；按 tag 关联完成；对 hazard 盲区串行化（第 5.2.2 节）；启用链式时 `done.ready` 恒为 1 |
| 数据存储系统（`bus[p]`） | 每引擎一组读/写 Stream | 每个读请求返回恰好 `len+1` 拍；每个写 burst 恰好一次响应；支持 byte strobe；共享写模式下同端口响应保序；按需处理 4 KiB 边界 |
| 描述符存储（`chainBus`，可选） | 读取链式后续描述符 | 描述符读取必须可靠，fetch 错误不会被上报 |
| 转换参数源（`cvtScale`/`cvtZeroPoint`，可选） | 全局组合活线 | 仅在静默期更新；不使用时驱动 0 |
| 时钟与复位 | 单一隐式时钟和复位 | 见第 9 章 |

### 2.3 总体框图

```text
                 clock / reset（单时钟域）
                          |
cmd ──> [FbChainController]* ──> FbTile2LinearExpander ──> FbDispatcher(FIFO + hazard)
          |        ^                                         |-> FbEngine[0]   <──> bus[0]
          v        |                                         |-> ...
       chainBus*   |                                         `-> FbEngine[N-1] <──> bus[N-1]
                   |                                                  |
done <─────────────┴──── round-robin done 仲裁 <────────────────────────┘

cvtScale* / cvtZeroPoint* ──> 所有引擎的格式转换器
* 为可选：chainBus/链式控制器由 enableChaining 生成；cvt* 由 enableConversion 生成。
enableSharedWritePorts=true 时，引擎写半部经 FbWrPortArbiter 映射到 bus[p].wr/wrResp。
```

## 3. 功能清单（Feature List）

### 3.1 Feature ID 规则

Feature ID 使用 `FB-Fnnn`，是功能项的稳定标识，不是寄存器地址、指令操作码或错误码。新增功能使用未分配编号；既有 ID 不因章节移动或文案调整而改变，也不得复用。若功能被移除，后续版本保留该 ID 并标注“已移除”。

只有“已实现”行构成当前能力索引。“已实现”不表示任意参数化配置均可用，也不消除对应章节的输入域、布局、时序或集成约束。“未实现”行不构成支持承诺。实现状态不等于验证结论，验证缺口见第 14 章。

### 3.2 功能清单

| Feature ID | 功能名称 | 实现状态 | 适用条件与主要约束 | 契约章节 |
|---|---|---|---|---|
| FB-F001 | 多引擎命令派发 | 已实现 | 单 FIFO 队首调度；`numPorts` 为 1..4；每通道至多 1 条 in-flight 命令 | 4.2、5.2.1 |
| FB-F002 | ND COPY | 已实现 | 最多 `maxDims` 维；源/目的独立 byte stride；基址与行字节按 beat 对齐 | 5.1.1、7.3.2 |
| FB-F003 | 源子块裁剪 | 已实现 | `srcStartIdx` 仅偏移源侧；适用 COPY/PERMUTE/TRANSPOSE；非零裁剪不在 hazard 范围内 | 5.1.2、5.2.2 |
| FB-F004 | Zero-padding | 已实现 | 仅 COPY；仅 dim0/dim1；dim0 padding 字节数为完整 beat；与 INT4、MX 互斥 | 5.1.3 |
| FB-F005 | TRANSPOSE | 已实现 | `dimCount=2`；需通道能力；同格式；`tileSize×tileSize` 分块；边缘使用 strobe | 5.1.4 |
| FB-F006 | PERMUTE | 已实现 | 源外层 stride 按 `permVec` 选择；dim0 仍为连续维；需通道能力；同格式、无 padding | 5.1.5 |
| FB-F007 | IM2COL | 已实现 | 需通道能力；仅 `dW=1` 且水平窗口不越界时正确；可普通转换，不支持 INT4/MX | 5.1.6 |
| FB-F008 | SCATTER/GATHER | 已实现 | 需通道能力且 `dataWidth ≥ addressWidth+16`；条目长度非零且按 beat 对齐；同格式 | 5.1.7、7.2.5 |
| FB-F009 | TILE2LINEAR/LINEAR2TILE | 已实现 | 调度前展开为 4D COPY；字节粒度同格式；无运行时 tile 顺序选择 | 5.1.8、7.3.5 |
| FB-F010 | 普通格式转换 | 已实现 | `enableConversion=true`；FP32/FP16/BF16/INT8 间；仅 COPY 与 IM2COL | 5.1.9、5.3.2 |
| FB-F011 | INT4 同格式 COPY | 已实现 | 仅 COPY 且两侧 INT4；`shape(0)`、`srcStartIdx(0)` 为偶数；无 padding | 5.1.10 |
| FB-F012 | MX 量化 | 已实现 | `enableConversion=true`；FP32/FP16/BF16 → MXE4M3/MXE5M2；仅 COPY；E8M0 scale 侧写 | 5.1.11、5.3.3、7.3.4 |
| FB-F013 | 全局转换参数活线 | 已实现 | `enableConversion=true`；所有引擎共享；不快照；仅静默期可更新 | 8.2.2 |
| FB-F014 | 地址 hazard 检查 | 已实现 | RAW/WAR/WAW 保守包络；MX scale 为第三写区；存在已知漏检场景 | 5.2.2 |
| FB-F015 | 通道能力裁剪与退化派发 | 已实现 | 每通道独立配置 TRANSPOSE/PERMUTE/IM2COL/SG 能力；无能力通道时退化派发并报错 | 5.2.1、8.1 |
| FB-F016 | 阻塞/非阻塞写 | 已实现 | `writeOutstandingDepth=1` 阻塞，>1 非阻塞；`done` 等待写响应排空 | 5.2.4、9.3 |
| FB-F017 | 共享写端口仲裁 | 已实现 | `enableSharedWritePorts=true`；burst 级 round-robin；同端口响应必须保序 | 5.2.4 |
| FB-F018 | 链式描述符 | 已实现 | `enableChaining=true`；后续节点仅 COPY/TRANSPOSE/PERMUTE；最终 `done` 不可反压 | 5.1.13、7.2.6 |
| FB-F019 | Atomic 完成标志 | 已实现 | 数据阶段无错误时写 32 bit 值 1；地址 4 字节对齐；写区不在 hazard 范围内 | 5.1.12 |
| FB-F020 | 完成回执与错误聚合 | 已实现 | 回执带 tag；单一 `err` 位；完成顺序不保证等于提交顺序 | 5.2.3、10 |
| FB-F021 | Raw DW 变长命令解码 | 未实现 | 规划中/非当前 RTL 契约 | 14.3 |
| FB-F022 | tileOrder 字段与独立 T2T opcode | 未实现 | 规划中；同尺寸 tile 顺序互转当前可用 4D COPY 表达 | 5.1.8、14.2 |
| FB-F023 | MX 反量化 | 未实现 | `srcFmt` 为 MX 的转换命令当前报错 | 5.1.11、14.2 |
| FB-F024 | INT32/INT16/INT4 跨格式转换 | 未实现 | 当前报错；同格式搬运可用 | 5.1.9、14.2 |
| FB-F025 | 4 KiB 边界自动拆分 | 未实现 | 当前不拆分，由软件保证 | 7.3.3、14.2 |
| FB-F026 | 细分错误码 | 未实现 | 当前只有单一 `err` 位 | 10.1、14.2 |

功能表仅用于索引，详细契约只在对应章节定义。不支持项与实现限制汇总于第 14 章。

## 4. 架构说明

### 4.1 子模块划分

| 子模块或逻辑块 | 职责 | 相互依赖 |
|---|---|---|
| `FbChainController`（`enableChaining`） | 链首命令透传；`chainMode=1` 时等待节点完成并从 `nextDescAddr` fetch 下一描述符；汇总最终 `done` | 输入 `cmd`，输出到 Tile 展开器；读 `chainBus`；拦截 done 仲裁输出 |
| `FbTile2LinearExpander` | 把 T2L/L2T 组合展开为 4D COPY，其余命令透传 | 输入来自链式控制器或 `cmd`，输出到调度器 |
| `FbDispatcher` | 命令 FIFO（深度 `queueDepth`）；队首 hazard 检查；能力匹配与通道选择 | 输出到各引擎命令口；读取各引擎 `busy` 与 `done.fire` |
| `FbEngine` × `numPorts` | 命令校验、地址生成、读写状态机、数据队列、写通路、atomic 完成标志 | 连接 `bus[p]`（或经写端口仲裁器） |
| `FbTransposeBuffer`（通道具备 TRANSPOSE 能力） | `tileSize×tileSize` 寄存器阵列，行序写入、列序读出 | 引擎内部 |
| `FbFormatConverter`（`enableConversion`，每引擎） | 普通格式转换 | 引擎读写数据之间；读取 `cvt*` 活线 |
| `FbMxQuantizer`（`enableConversion`，每引擎） | MX 块量化与 E8M0 scale 计算 | 引擎内部 |
| `FbIm2colAddrGen`（通道具备 IM2COL 能力） | IM2COL 遍历与垂直 padding 判定 | 引擎内部 |
| `FbScatterGatherCtrl`（通道具备 SG 能力且宽度满足） | SG 地址表逐条 fetch | 复用引擎读通道 |
| `FbWrPortArbiter`（`enableSharedWritePorts`） | 写 burst 级 round-robin 仲裁与 owner FIFO 响应路由 | 引擎写半部 ↔ `bus[p].wr/wrResp` |
| done 仲裁器 | transaction-lock round-robin 汇聚各引擎 `done` | 输出到 `done` 或链式控制器 |

### 4.2 数据通路与控制通路

控制通路：命令在 `cmd` 握手后进入链式控制器（若生成）和 Tile 展开器，然后进入调度器 FIFO。调度器只检查队首命令；当其与所有 in-flight 命令无地址冲突且存在可用通道时派发。派发时锁存该命令的源、目的及 MX scale 地址包络，直到该通道 `done.fire`。引擎完成后其 `done` 经 round-robin 仲裁汇出。

数据通路：每个引擎独立发出读请求，读数据进入引擎数据队列（深度 `resolvedDataQueueDepth`），经可选转置缓冲、格式转换或 MX 量化后形成写 burst。读通道始终为引擎专用；写通道在默认配置下直连，在共享写模式下经写端口仲裁器映射到物理写端口。

### 4.3 内部资源组织

| 资源 | 用途 | 所有权 |
|---|---|---|
| 命令 FIFO | 缓存已接收未派发的命令，`occupancy` 反映其占用 | 调度器 |
| in-flight 地址包络表 | 每通道一组 src/dst/scale 起止地址及有效位 | 调度器，派发时写入，`done.fire` 时释放 |
| 引擎数据队列 | 读写解耦；读请求按 credit 预留空间 | 每引擎独占 |
| 转置 tile 缓冲 | `tileSize×tileSize` 个 32 bit 元素槽 | 每个具备 TRANSPOSE 能力的引擎独占 |
| MX block 缓冲 | `max(1, 64×32/dataWidth)` 个 beat，按最大块 64×FP32 计 | 每引擎独占 |
| MX scale 累积寄存器 | 一个 beat 宽的 scale 字节累积 | 每引擎独占 |
| 写 owner FIFO | 每物理写端口深度 `writeOutstandingDepth`，记录响应归属 | 写端口仲裁器 |
| 链式描述符寄存器 | `descBeats` 个 beat | 链式控制器 |

容量数值见第 8.1 节和第 12.1 节。

## 5. 功能行为规范

### 5.1 功能契约

本节各子节的错误条件均以 `done.err=1` 上报，汇总见第 10.1 节。所有命令均须满足第 7.3 节的维度、地址和对齐约定。

#### 5.1.1 ND COPY

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F002 |
| 前置条件 | `1 ≤ dimCount ≤ maxDims`；活动维 `shape` 非零；`srcAddr`、`dstAddr` 按 beat 对齐；`rowBytes(shape(0), srcFmt)` 为完整 beat |
| 输入与触发 | 命令派发到引擎后开始 |
| 处理语义 | 按 dim1 最快、随后 dim2.. 的外层进位顺序遍历；每行把 dim0 展开为连续读写 burst；源与目的使用独立 stride（地址公式见第 7.3.2 节） |
| 输出与完成 | 全部写响应返回（及可选 atomic 写完成）后发出 `done` |
| 边界情形 | 行内源 burst 长度取行剩余、`maxBurstLen` 与数据队列 credit 的最小值；格式压缩/扩展时按转换比调整；`srcStride(0)`/`dstStride(0)` 不参与 COPY 地址生成 |
| 异常关联 | 第 10.1 节：维数、shape、对齐、格式错误；总线错误 |

COPY 可组合源裁剪、zero-padding、普通格式转换、MX 量化和 atomic 完成标志，但必须同时满足各功能约束。

#### 5.1.2 源子块裁剪

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F003 |
| 前置条件 | 适用 COPY、PERMUTE、TRANSPOSE；裁剪后的源访问不得越出源缓冲 |
| 输入与触发 | `srcStartIdx[d]` 随命令给出 |
| 处理语义 | 仅偏移源侧：dim0 偏移 `rowBytes(srcStartIdx[0], srcFmt)`，外层偏移 `srcStartIdx[d] × srcStride[d]`；目的侧从 `dstAddr` 开始 |
| 输出与完成 | 同所属操作 |
| 边界情形 | INT4 时 `srcStartIdx(0)` 必须为偶数；实际源行地址必须满足下游对齐要求 |
| 异常关联 | 非零 `srcStartIdx` 不进入调度器 hazard 范围（第 5.2.2 节），软件必须串行化可能重叠的命令 |

#### 5.1.3 Zero-padding

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F004 |
| 前置条件 | 仅 COPY；`rowBytes(padBefore[0], dstFmt)` 与 `rowBytes(padAfter[0], dstFmt)` 各自为完整 beat；不得与 INT4、MX 量化同用 |
| 输入与触发 | `padBefore[0..1]`、`padAfter[0..1]` 非零 |
| 处理语义 | `padBefore/After[0]` 在每个数据行前后写零；`padBefore/After[1]` 增加整行零行；`dimCount>2` 时各外层切片重复该行为 |
| 输出与完成 | 零值以全 strobe 写出，属于目的写区 |
| 边界情形 | padding 只作用于 dim0/dim1 |
| 异常关联 | padding 用于非 COPY 或 dim0 padding 不为完整 beat 时报错 |

#### 5.1.4 TRANSPOSE

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F005 |
| 前置条件 | `dimCount=2`；派发通道具备 TRANSPOSE 能力；`srcFmt==dstFmt`；非 INT4 |
| 输入与触发 | 令 `M=shape(0)`、`N=shape(1)` |
| 处理语义 | 以 `tileSize×tileSize` 缓冲按 tile 读取源矩阵并写出转置矩阵；源行地址主要使用 `srcStride(1)`；转置输出第 `j` 行地址为 `dstAddr + j × dstStride(0)`，行内沿原行维连续写 |
| 输出与完成 | 每 tile 先填充再排空；全部写响应返回后发出 `done` |
| 边界情形 | 源行字节数可不是完整 beat；边缘 tile 通过有效行列数与部分 strobe 处理；软件仍须保证实际源行读请求地址满足下游对齐要求 |
| 异常关联 | 维数不为 2、通道无能力、格式不同时报错；目的包络不可靠（第 5.2.2 节） |

#### 5.1.5 PERMUTE

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F006 |
| 前置条件 | 派发通道具备 PERMUTE 能力；活动 `permVec[d] < dimCount`；同格式；无 padding |
| 输入与触发 | `permVec[maxDims]` 随命令给出 |
| 处理语义 | 与 COPY 共用读写通路，只在源外层地址计算时以 `srcStride[permVec[d]]` 替代 `srcStride[d]`；dim0 仍为连续维，`permVec[0]` 不参与当前行地址计算；目的侧仍按 `dstStride[d]` |
| 输出与完成 | 同 COPY |
| 边界情形 | RTL 不检查 `permVec` 是否互不重复；若需要真正的排列，软件必须保证其为双射。当前 PERMUTE 不是可搬动连续维的通用重排器 |
| 异常关联 | 通道无能力、映射越界、跨格式或带 padding 时报错 |

#### 5.1.6 IM2COL

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F007 |
| 前置条件 | 派发通道具备 IM2COL 能力；`kH/kW/sH/sW` 非零；`dW=1` 且每个非垂直 padding micro-row 的整个水平窗口位于输入行内；非 INT4、非 MX |
| 输入与触发 | `im2colKernel/Stride/Pad/Dilation`、`im2colInShape=[C,H,W]`；`shape(0)`、`shape(1)` 分别填源、目的内存保守 byte span |
| 处理语义 | 输出遍历顺序为 `oh → ow → c → kh → kw`，`kw` 构成一次 micro-row；垂直越界 micro-row 写零 |
| 输出与完成 | 同 COPY |
| 边界情形 | 引擎未使用地址生成器的 `burstable` 信号，总把 micro-row 当作连续数据读取，因此水平 padding、水平越界窗口与 `dW≠1` 不是当前 RTL 契约 |
| 异常关联 | 能力、非零参数、转换 burst 约束不满足时报错 |

输出尺寸为：

```text
outH = floor((H + 2*pH - dH*(kH-1) - 1) / sH) + 1
outW = floor((W + 2*pW - dW*(kW-1) - 1) / sW) + 1
```

IM2COL 可使用普通格式转换。附加约束：

- 压缩时 micro-row 源 beat 数必须是转换比的整数倍，且折算后至少 1 beat；
- 跨格式命令采用保守上界检查：无论实际转换比和方向，均要求 `4 × sourceMicroRowBeats ≤ maxBurstLen`；
- 同格式路径不按 `maxBurstLen` 拆分或拒绝完整 micro-row；若下游把 `maxBurstLen` 当作硬上限，软件必须保证 `sourceMicroRowBeats ≤ maxBurstLen`；
- 源 micro-row beat 数必须不超过 `resolvedDataQueueDepth`；读请求只检查 credit 非零，不按剩余 credit 拆分 micro-row；
- 调度器把 `shape(0)`、`shape(1)` 解释为 byte span，它们在此不表示常规张量 shape，软件必须填入真实范围。

#### 5.1.7 SCATTER/GATHER

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F008 |
| 前置条件 | 派发通道具备 SG 能力且 `dataWidth ≥ addressWidth+16`；`sgListAddr` 按 beat 对齐；`sgEntryCount` 非零；同格式 |
| 输入与触发 | 地址表格式见第 7.2.5 节；每条目 fetch 一个 beat 后搬运该条目数据 |
| 处理语义 | GATHER 从各条目地址读取，按条目顺序连续写到 `dstAddr`；SCATTER 从 `srcAddr` 连续读取，按条目顺序写到各条目地址 |
| 输出与完成 | 所有条目处理完且写响应排空后发出 `done` |
| 边界情形 | `lenBytes` 向上取整为 beat 数，数据写使用全 strobe；不校验条目地址、长度和地址表 fetch 读错误 |
| 异常关联 | 能力、entry count、表对齐不满足时报错；其余条目约束为软件义务 |

软件必须保证：

- 每个条目 `lenBytes > 0`，否则状态机会停滞；
- 条目地址按 beat 对齐；
- `lenBytes` 是 `beatBytes` 的整数倍，否则会多搬运并覆盖尾部字节；
- `ceil(lenBytes/beatBytes)` 能由 `log2Up(maxBurstLen+1)` bit 表示，否则会在当前寄存器中截断；
- 地址表 fetch 和数据区域合法、可访问。

调度器固定把源范围解释为 `[srcAddr, srcAddr+shape(0))`，目的范围解释为 `[dstAddr, dstAddr+shape(1))`。若软件依赖硬件冲突保护，必须满足以下 base/span 包络契约：

- GATHER：`srcAddr` 不大于所有条目源地址，`shape(0)` 覆盖所有条目访问区间；
- SCATTER：`dstAddr` 不大于所有条目目的地址，`shape(1)` 覆盖所有条目访问区间；
- 另一侧线性区也必须由对应 base/span 完整覆盖；
- span 字段只有 16 bit，无法表达完整包络时必须串行化相关命令；
- `sgListAddr` 地址表本身不参与 hazard 检查，命令完成前不得被其他主机修改或覆盖。

即使 `dimCount=1`，软件也必须填好 `shape(0)` 与 `shape(1)`。

#### 5.1.8 TILE2LINEAR、LINEAR2TILE 与 tile 顺序互转

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F009（已实现）；FB-F022（未实现） |
| 前置条件 | `t2lMatCols`、`t2lTileRows`、`t2lTileCols`、`t2lNumTileRows`、`t2lNumTileCols` 均非零；字节粒度格式；`srcFmt==dstFmt`；非 INT4，不转换 |
| 输入与触发 | `op` 为 TILE2LINEAR 或 LINEAR2TILE |
| 处理语义 | 调度前组合展开为 4D COPY，stride 公式见第 7.3.5 节；TILE2LINEAR 以 tiled stride 作源、linear stride 作目的，LINEAR2TILE 相反 |
| 输出与完成 | 同 COPY |
| 边界情形 | 软件必须自行保证 Mt/Nt 与矩阵和 tile 尺寸一致 |
| 异常关联 | 参数为零、INT4 或跨格式时报错 |

当前 `FbCmd` 没有 `tileOrder` 字段，T2L/L2T 不接受运行时 row-major/column-major 选择，也没有独立 T2T opcode。相同 tile 尺寸下 row-major/column-major 的 tile 顺序互转可由普通 4D COPY 配合独立 `srcStride/dstStride` 表达。不同 tile 尺寸的重分块必须由软件发出 T2L 后接 L2T 两条命令，并使用中间线性缓冲。

#### 5.1.9 普通格式转换

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F010（已实现）；FB-F024（未实现） |
| 前置条件 | `enableConversion=true` 且 `srcFmt≠dstFmt`；两侧均属 FP32、FP16、BF16、INT8；操作为 COPY 或 IM2COL |
| 输入与触发 | 格式由 `srcFmt/dstFmt` 选择；INT8 相关路径使用全局 `cvt*` 活线（第 8.2.2 节） |
| 处理语义 | FP16/BF16/INT8 组合经 FP32 中间表示；数值语义见第 5.3.2 节 |
| 输出与完成 | 同所属操作 |
| 边界情形 | COPY 压缩时源行 beat 数必须是压缩比的整数倍，且 `maxBurstLen` 与 `resolvedDataQueueDepth` 均不小于压缩比；COPY 扩展时 `maxBurstLen` 必须不小于扩展比。否则分段向下对齐可能得到 0 beat 并永久停滞，当前 RTL 未对这些组合做完整命令级报错 |
| 异常关联 | TRANSPOSE、PERMUTE、SCATTER、GATHER 与 Tile 语法糖跨格式时报错；涉及 INT32、INT16、INT4 的跨格式转换报错；`enableConversion=false` 时 `srcFmt≠dstFmt` 报错 |

#### 5.1.10 INT4 同格式 COPY

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F011 |
| 前置条件 | 仅 COPY 且 `srcFmt==dstFmt==INT4`；`shape(0)` 为偶数；`srcStartIdx(0)` 为偶数；源行字节数为完整 beat |
| 输入与触发 | 同 COPY |
| 处理语义 | 按 `rowBytes(n, INT4)=ceil(n×4/8)` 计算行字节；stride 仍为字节 stride，RTL 不提供半字节地址 |
| 输出与完成 | 同 COPY |
| 边界情形 | 不得使用 padding、TRANSPOSE、PERMUTE、IM2COL、SCATTER/GATHER 或 T2L/L2T |
| 异常关联 | 违反上述约束时报错 |

#### 5.1.11 MX 量化

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F012（已实现）；FB-F023（未实现） |
| 前置条件 | `enableConversion=true`；`op=COPY`；`srcFmt` 为 FP32、FP16 或 BF16；`scaleBlockSize` 为 0/1/2；`shape(0)` 为块大小整数倍；单块源 beat 数不超过 `maxBurstLen` 与 `resolvedDataQueueDepth`；`scaleAddr` 按 beat 对齐；无 padding |
| 输入与触发 | `dstFmt` 为 MXE4M3 或 MXE5M2 即激活，无独立量化使能位 |
| 处理语义 | 每块输出一个 E8M0 scale 字节，scale 布局见第 7.3.4 节；数值语义见第 5.3.3 节；收集、量化、排空三阶段执行，scale 写与数据写共享写通道 |
| 输出与完成 | 数据与 scale 写响应全部返回后发出 `done` |
| 边界情形 | 数据区与 scale 区应由软件保证不重叠；`enableConversion=false` 时没有量化器，`srcFmt==dstFmt==MX*` 按 1 byte/element 做裸字节 COPY |
| 异常关联 | `enableConversion=true` 构建中 `srcFmt` 为 MX 的命令（含 MX→MX）报错；其余前置条件不满足时报错 |

#### 5.1.12 Atomic 完成标志

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F019 |
| 前置条件 | `atomicEnable=1`；`atomicStoreAddr` 4 字节对齐（不要求 beat 对齐） |
| 输入与触发 | 命令数据阶段结束且无错误，所有数据写响应已排空 |
| 处理语义 | 在 `done` 前发出一个单拍写 burst：`atomic=1`、`sof=eof=1`、`len=0`；`addr` 为 `atomicStoreAddr` 向下对齐的 beat 地址；向目标 4 字节 lane 写入 little-endian 32 bit 值 1，`strb` 只使能该 4 字节 |
| 输出与完成 | atomic 写响应返回后发出 `done` |
| 边界情形 | 命令已出错时不发 atomic 写 |
| 异常关联 | 地址未 4 字节对齐时报错；atomic 写响应报错时最终 `done.err=1`；atomic 写区不参与 hazard 检查（第 5.2.2 节） |

`atomic` 只是总线属性，是否具备原子/排序语义由下游实现。

#### 5.1.13 链式描述符执行

| 契约项 | 说明 |
|---|---|
| 功能 ID | FB-F018 |
| 前置条件 | `enableChaining=true`；集成方令 `done.ready` 恒为 1；描述符布局见第 7.2.6 节 |
| 输入与触发 | 链首从 `cmd` 接收且 `chainMode=1` |
| 处理语义 | 控制器等待当前节点完成，再经 `chainBus` 从 `nextDescAddr` 读取下一描述符并注入；节点严格串行；链首为完整 `FbCmd` 直通，后续节点受描述符格式限制 |
| 输出与完成 | 遇 `nextDescAddr=0` 或节点错误即结束，只返回一次最终 `done`：tag 为链首 tag，`chainLen` 为已执行节点数 |
| 边界情形 | 终止节点完成时控制器只在该拍组合拉高 `done.valid`，随后立即返回透传状态，不等待 `done.fire`；`done.ready=0` 时最终回执可能丢失 |
| 异常关联 | 节点错误终止链并上报；描述符 fetch 的 `rdData.err` 不被采集；后续节点 MX 自守见第 7.2.6 节 |

### 5.2 执行顺序与并发行为

#### 5.2.1 命令派发与通道能力

调度器是单 FIFO、只检查队首的 HOL 调度器。派发规则：

1. 队首命令按第 5.2.2 节的保守地址模型与所有 in-flight 命令检查 RAW、WAR 和 WAW；
2. 无冲突时，优先选择编号最小的空闲且具备该操作能力的通道；
3. 若没有任何通道具备所需能力，则退化派发到任一空闲通道，由引擎返回 `err=1`，而不是永久阻塞。

通道空闲指引擎不 busy 且该通道无 in-flight 记录。每通道 in-flight 状态保持到对应引擎 `done.fire`，因此非链路径下游反压 `done` 会保持引擎 busy 与冲突锁定。冲突或能力等待会阻塞后续所有命令。

#### 5.2.2 地址冲突检查

调度器为每条命令估计源、目的两个地址包络，MX 量化命令另有 scale 写区作为第三区间：

- 普通 ND 范围按外层各维 `(shape[d]-1) × stride[d]` 之和加 dim0 行字节估计；
- PERMUTE 源范围使用重映射后的 stride；
- 目的范围使用 `dstFmt` 粒度；
- IM2COL 和 SG 使用命令提供的 `shape(0)`/`shape(1)` byte span；
- MX scale 区间仅在派发时确为量化命令时记录，非 MX 命令清除该区间。

新命令与每个 in-flight 命令检查：RAW（新读区 × 在途 dst/scale 写区）、WAR（新 dst/scale 写区 × 在途读区）、WAW（dst/scale 写区两两组合）。

当前模型存在以下漏检场景，软件必须串行化可能重叠的命令或隔离相应区域：

- 非零 `srcStartIdx` 未进入范围计算，真实源访问可能超出所记录上界；
- TRANSPOSE 实际目的地址沿 `dstStride(0)` 推进，但调度器按普通 ND 的 `dstStride(1)` 模型估计，目的包络不可靠；
- SG 只有在第 5.1.7 节 base/span 契约成立时才有有效包络，地址表自身不参与检查；
- atomic 4 字节写区不参与检查；
- 链式描述符所在内存不参与检查；
- 地址范围乘加最终截断到 `addressWidth`，会回绕的范围不得提交。

因此当前包络既可能产生假冲突，也可能漏检，不得视为完整内存一致性机制。

#### 5.2.3 完成回执汇聚

多个引擎的 `done` 经 transaction-lock round-robin 仲裁汇出。完成顺序不保证等于提交顺序，软件必须用 tag 关联。启用链式时，引擎完成先进入链式控制器，链式命令只在链结束时对外返回一次回执。

#### 5.2.4 写通路模式

阻塞写（`writeOutstandingDepth=1`）：引擎在每个 burst EOF 后必须等待该 burst 的 `wrResp` 才推进。

非阻塞写（`writeOutstandingDepth>1`）：引擎在 EOF 握手后即可推进，最多保持配置深度的写 burst 在途；最终 `done`（及 atomic 写）必须等待所有数据写响应排空。读侧仍只有一个请求在途。

共享写端口（`enableSharedWritePorts=true`）：

- 读通道仍为引擎一对一直连；
- 物理端口数据锁从 SOF 保持到 EOF，burst 中途不得切换 owner；
- 每物理端口使用深度为 `writeOutstandingDepth` 的 owner FIFO，从 SOF 保持归属到响应；
- 同一物理端口的响应必须按 burst 发出顺序返回，因为 `FbWriteResp` 没有 ID；
- 有在途响应的引擎绑定到原物理端口，直至其在途计数归零；
- 仲裁器不增加物理端口数，物理写端口数仍等于 `numPorts`。

直连非阻塞模式下，每引擎独享响应通道，只按计数回收，不要求跨 burst 响应顺序。

### 5.3 边界与数值行为

#### 5.3.1 地址运算

所有地址加法和 stride 乘加最终截断到 `addressWidth`，RTL 不检查溢出或回绕。软件必须保证地址范围不回绕，并用比 `addressWidth` 更宽的整数做提交前检查。

#### 5.3.2 普通格式转换数值

- FP32 → FP16/BF16 使用实现中的 RNE 位级舍入；FP16/BF16 → FP32 为位级扩展。不得把该简化实现等同于完整 IEEE 异常、次正规与 NaN 处理器。
- FP32 → INT8 使用 `cvtScale` 低 5 bit `shift` 做 2 的幂缩放，再加 `cvtZeroPoint` 低 8 bit 有符号 zero point，并饱和到 [-128, 127]。整数化为移位截取，不得依赖注释中未实现的通用实数 scale 或完整舍入行为。
- INT8 → FP32 计算意图为 `(int8 - zeroPoint) << shift`。当前测试锁定了差值非负的情形；负差值的完整数值语义为规划中/非当前 RTL 契约。

#### 5.3.3 MX 量化数值

MX 量化采用块内最大真指数确定 E8M0 scale，尾数 RNE 舍入，下溢清零，上溢饱和。E4M3 遇 NaN 输出 NaN 编码；E5M2 遇 NaN 饱和到最大正常数。

## 6. 接口规范

### 6.1 接口与信号总表

方向以 ForagerBee 为参照。所有接口位于同一时钟域。

| 接口/信号 | 方向 | 宽度或类型 | 时钟域 | 含义与有效条件 |
|---|---|---|---|---|
| `cmd` | 输入 Stream | `FbCmd`（第 7.2.3 节） | 主时钟 | 单入口命令流；`valid && ready` 时命令被接收，可排队 |
| `done` | 输出 Stream | `FbDone`（第 7.2.4 节） | 主时钟 | 完成回执；非链路径可反压；链式最终回执要求 `ready` 恒为 1 |
| `occupancy` | 输出 | `log2Up(queueDepth+1)` | 主时钟 | 调度 FIFO 当前占用，不含正在执行的命令 |
| `busy` | 输出 | 1 | 主时钟 | 任一引擎不在 IDLE 时为 1；不反映 FIFO、链式 fetch 或链控制器状态 |
| `bus[p]` | 主接口 | `FbStreamBus` × `numPorts` | 主时钟 | 每引擎一组读写接口；共享写模式只重映射写半部 |
| `bus[p].rd` | 输出 Stream | `addr[addressWidth]`、`len[8]` | 主时钟 | 读请求，beat 数 `len+1` |
| `bus[p].rdData` | 输入 Stream | `data[dataWidth]`、`sof`、`eof`、`err` | 主时钟 | 读数据流 |
| `bus[p].wr` | 输出 Stream | `addr`、`len[8]`、`data`、`strb[beatBytes]`、`sof`、`eof`、`atomic` | 主时钟 | 写 burst 数据流 |
| `bus[p].wrResp` | 输入 Stream | `err` | 主时钟 | 每个写 burst 一次响应 |
| `chainBus` | 可选主接口 | `FbStreamBus` | 主时钟 | 仅 `enableChaining=true` 存在；当前只使用读通道 |
| `cvtScale` | 可选输入 | 32 | 主时钟 | 仅 `enableConversion=true` 存在；低 5 bit 为移位量 |
| `cvtZeroPoint` | 可选输入 | 32 | 主时钟 | 仅 `enableConversion=true` 存在；低 8 bit 为有符号 zero point |

### 6.2 协议与握手

#### 6.2.1 通用 Stream

所有 Stream 使用 `valid/ready` 握手，传输在 `valid && ready` 时发生。发送方在握手前应保持 `valid` 与 payload 稳定。链式最终完成回执是例外，见第 6.2.4 节。

#### 6.2.2 读通道

| 通道 | Payload | 契约 |
|---|---|---|
| `rd` | `addr[addressWidth]`、`len[8]` | 一次请求；实际 beat 数为 `len+1` |
| `rdData` | `data[dataWidth]`、`sof`、`eof`、`err` | 下游返回数据流，标记首末拍 |

引擎按内部计数消费 beat，不验证 `sof/eof` 与请求长度是否一致。下游必须对每个请求返回恰好 `len+1` 拍，并正确给出首末标记。每引擎最多一个读请求在途。

#### 6.2.3 写通道

| 字段 | 宽度 | 契约 |
|---|---:|---|
| `addr` | `addressWidth` | 仅 `sof=1` 首拍有效 |
| `len` | 8 | 仅首拍有效；burst beat 数为 `len+1` |
| `data` | `dataWidth` | 每拍有效数据 |
| `strb` | `beatBytes` | 每字节写使能 |
| `sof`、`eof` | 各 1 | burst 首末拍；单拍 burst 时均为 1 |
| `atomic` | 1 | 普通写必须为 0；atomic 完成标志写为 1 |

每个写 burst 必须恰好返回一次 `wrResp{err}`。普通 COPY/IM2COL/SG 与 padding 写通常使用全 strobe；TRANSPOSE 边缘/非对齐输出、MX scale 和 atomic 标志使用部分 strobe，下游必须正确支持 byte strobe。

#### 6.2.4 完成回执

非链完成回执在握手前保持，对应引擎保持 busy。链式最终完成回执只在一拍内组合有效，不等待握手；启用链式命令时集成方必须令 `done.ready` 恒为 1（第 5.1.13 节）。

### 6.3 接口依赖与互斥

- `chainBus` 当前只发 `rd/rdData`，其 `wr.valid=0`、`wrResp.ready=0`。
- 共享写模式下，引擎写半部与物理 `bus[p].wr/wrResp` 不再一一对应，响应路由规则见第 5.2.4 节；读半部仍一一对应。
- `cvtScale`/`cvtZeroPoint` 直接组合连接到所有引擎转换器，更新约束见第 8.2.2 节。
- SG 地址表 fetch 复用所属引擎的 `bus[p].rd/rdData`。
- 事务时序、在途数量与反压见第 9.3 节。

## 7. 数据表示规范

### 7.1 类型、位宽与单位

| 数据项 | 位宽/类型 | 符号与编码 | 单位或数值域 |
|---|---|---|---|
| 地址（`srcAddr`、`dstAddr`、`scaleAddr` 等） | `addressWidth` | 无符号 | 字节地址；运算截断到 `addressWidth` |
| `shape[d]` | 16 | 无符号 | 元素数；活动维非零，非活动维应写 0；IM2COL/SG 另作 byte span |
| `srcStride[d]`、`dstStride[d]` | `addressWidth` | 无符号 | 字节 stride |
| `srcStartIdx[d]` | 16 | 无符号 | 元素 |
| `dimCount` | 3 | 无符号 | `1..maxDims` |
| `tag` | 8 | 不透明 | 软件定义，完成时原样返回 |
| `op` | 3 | `FbOp` 枚举 | 第 7.2.1 节 |
| `srcFmt`、`dstFmt` | 4 | `FbDataFmt` 枚举 | 第 7.2.2 节 |
| 总线 `len` | 8 | 无符号 | beat 数减 1 |
| `chainLen` | 16 | 无符号 | 已执行链节点数 |

### 7.2 编码与打包

#### 7.2.1 操作码

`FbOp` 为 3 bit ChiselEnum，声明顺序即编码。

| 编码 | 操作 |
|---:|---|
| 0 | COPY |
| 1 | TRANSPOSE |
| 2 | PERMUTE |
| 3 | IM2COL |
| 4 | SCATTER |
| 5 | GATHER |
| 6 | TILE2LINEAR |
| 7 | LINEAR2TILE |

#### 7.2.2 数据格式编码

`srcFmt/dstFmt` 均为 4 bit，编码为软件可见契约。

| 编码 | 名称 | 元素位数 | `bytesOfCeil` | 当前用途 |
|---:|---|---:|---:|---|
| 0 | FP32 | 32 | 4 | 搬运、转换、MX 源 |
| 1 | FP16 | 16 | 2 | 搬运、转换、MX 源 |
| 2 | BF16 | 16 | 2 | 搬运、转换、MX 源 |
| 3 | INT8 | 8 | 1 | 搬运、与浮点转换 |
| 4 | MXE4M3 | 8 | 1 | MX 量化目的；无转换构建可作裸字节格式 |
| 5 | MXE5M2 | 8 | 1 | 同上 |
| 6 | 保留 | - | - | 非法 |
| 7 | 历史保留 | - | - | 非法，不得复用 |
| 8 | INT32 | 32 | 4 | 同格式搬运；跨格式算术未实现 |
| 9 | INT16 | 16 | 2 | 同格式搬运；跨格式算术未实现 |
| 10 | INT4 | 4 | 1（地址计算辅助值） | 受限的同格式 COPY |
| 11..15 | 保留 | - | - | 非法 |

非法编码必须产生 `done.err=1`，不得进入转换器。

#### 7.2.3 `FbCmd` 字段

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `op` | 3 | 操作码 |
| `tag` | 8 | 软件标签；链模式最终返回链首标签 |
| `dimCount` | 3 | 活动维数 |
| `shape[maxDims]` | 每项 16 | 元素数；IM2COL/SG 时 `shape(0/1)` 为 byte span |
| `srcStride[maxDims]` | 每项 `addressWidth` | 源 byte stride |
| `dstStride[maxDims]` | 每项 `addressWidth` | 目的 byte stride；MX scale 外层行也复用 |
| `srcStartIdx[maxDims]` | 每项 16 | 源裁剪起始索引 |
| `t2lMatCols` | 16 | T2L/L2T 矩阵列数 N |
| `t2lTileRows` | 16 | tile 行数 Tm |
| `t2lTileCols` | 16 | tile 列数 Tn |
| `t2lNumTileRows` | 16 | tile 行块数 Mt |
| `t2lNumTileCols` | 16 | tile 列块数 Nt |
| `srcAddr` | `addressWidth` | 源基址 |
| `dstAddr` | `addressWidth` | 目的基址 |
| `permVec[maxDims]` | 每项 3 | PERMUTE 源 stride 索引映射 |
| `padBefore[2]` | 每项 16 | dim0/dim1 前置零填充 |
| `padAfter[2]` | 每项 16 | dim0/dim1 后置零填充 |
| `nextDescAddr` | `addressWidth` | 下一链式描述符地址，0 表示结束 |
| `chainMode` | 1 | 链模式请求 |
| `srcFmt`、`dstFmt` | 各 4 | 数据格式 |
| `im2colKernel[2]` | 每项 8 | kH、kW |
| `im2colStride[2]` | 每项 8 | sH、sW |
| `im2colPad[2]` | 每项 8 | pH、pW（对称） |
| `im2colDilation[2]` | 每项 8 | dH、dW |
| `im2colInShape[3]` | 每项 16 | C、H、W |
| `sgListAddr` | `addressWidth` | SG 地址表基址 |
| `sgEntryCount` | 16 | SG 条目数 |
| `scaleAddr` | `addressWidth` | MX E8M0 scale 写基址 |
| `scaleBlockSize` | 2 | 0/1/2 → 16/32/64 元素 |
| `atomicEnable` | 1 | 成功后写完成标志 |
| `atomicStoreAddr` | `addressWidth` | 32 bit 完成标志地址，必须 4 字节对齐 |

未用于当前操作的字段应写 0。`FbCmd` 是结构化 Bundle，不定义 raw 位序；当前没有 `tileOrder` 字段。`FbSharedCmd` 是未使用的 3×32 bit 占位 Bundle，不是已实现的命令编码。

#### 7.2.4 `FbDone`

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `tag` | 8 | 命令 tag；链命令为链首 tag |
| `err` | 1 | 聚合错误位 |
| `chainLen` | 16 | `enableChaining=true` 时：非链命令为 1，链命令为已执行节点数；`enableChaining=false` 时未定义，软件不得读取 |

#### 7.2.5 SG 地址表条目

每条目占一个完整 beat：

```text
bits[addressWidth-1:0]              = addr
bits[addressWidth+15:addressWidth]  = lenBytes
其余高位                              = 忽略
entry[i] address                    = sgListAddr + i * beatBytes
```

#### 7.2.6 链式描述符布局

描述符由若干 `dataWidth` beat 组成，beat 顺序为 little-endian：beat 0 承载最低位。字段从低位起紧密排列：

```text
op(2), tag(8), dimCount(3), shape(maxDims*16),
srcStride(maxDims*addressWidth), dstStride(maxDims*addressWidth),
srcAddr, dstAddr, permVec(maxDims*3),
padBefore(32), padAfter(32), nextDescAddr, chainMode(1),
srcFmt(4), dstFmt(4)
```

```text
totalBits = 2+8+3 + maxDims*16 + 2*maxDims*addressWidth
          + 2*addressWidth + maxDims*3 + 32+32
          + addressWidth + 1 + 4+4

descBeats = chainDescBeats>0 ? chainDescBeats : ceil(totalBits/dataWidth)
```

常用值：`maxDims=5, addressWidth=32` 时为 597 bit，128 bit 总线 5 beat、256 bit 总线 3 beat；`maxDims=4, addressWidth=32` 时为 514 bit，128 bit 总线 5 beat。

后续节点格式限制：

- 描述符 `op` 只有 2 bit，只能编码 COPY/TRANSPOSE/PERMUTE；
- 不序列化 `srcStartIdx`、Tile、IM2COL、SG、MX scale 和 atomic 字段，注入节点对应字段均为 0；
- 链首为完整 `FbCmd` 直通，不受上述限制；
- `enableConversion=true` 时，后续描述符若 `dstFmt` 为 MX，会被强制改为非法 `dimCount=0` 并以错误终止链，防止向地址 0 写 scale；
- `enableConversion=false` 时允许链式 MX 同格式裸字节 COPY；
- `chainDescBeats` 若手工覆盖，必须足以容纳布局，RTL 不检查覆盖值是否过小。

### 7.3 存储布局与地址约定

#### 7.3.1 维度约定

- dim0 为最内层连续维，`dimCount` 可为 `1..maxDims`（默认最大 5）。
- 所有活动维 `shape` 必须非零。
- 普通 COPY 中 `stride(0)` 是信息字段，地址生成不使用它；dim0 由连续 burst 展开。

#### 7.3.2 COPY 与 PERMUTE 行地址

```text
srcRow = srcAddr
       + rowBytes(srcStartIdx[0], srcFmt)
       + Σ (outerIdx[d] + srcStartIdx[d]) * srcStride[d]

dstRow = dstAddr + Σ outerIdx[d] * dstStride[d]
                 d=1..dimCount-1
```

PERMUTE 源侧把 `srcStride[d]` 替换为 `srcStride[permVec[d]]`，目的侧不变。

#### 7.3.3 对齐与边界

- `srcAddr`、`dstAddr` 必须按 beat 对齐，该检查对所有操作生效。
- 除 TRANSPOSE 外，`rowBytes(shape(0), srcFmt)` 必须为完整 beat。
- RTL 只检查基址，不逐行检查 stride 生成的地址；软件必须保证所有实际 burst 起始地址满足系统总线要求。
- 当前 RTL 不按 4 KiB 边界拆分 burst。若下游禁止跨 4 KiB，软件必须拆分命令或安排地址与行长，使每个 burst 不跨界。

#### 7.3.4 MX scale 布局

scale 行地址为：

```text
scaleAddr + Σ outerIdx[d] * dstStride[d],  d=1..dimCount-1
```

行内 scale 字节依次存放，每行 `shape(0)/blockSize` 个字节，尾部用部分 `strb` 写出。

#### 7.3.5 TILE2LINEAR/LINEAR2TILE 展开

令 `N=t2lMatCols`、`Tm=t2lTileRows`、`Tn=t2lTileCols`、`Mt=t2lNumTileRows`、`Nt=t2lNumTileCols`、`e=bytesOfCeil(srcFmt)`：

```text
shape         = [Tn, Tm, Nt, Mt]
tiled stride  = [e, N*e, Tn*e, Tm*N*e]
linear stride = [e, Tn*e, Tm*Tn*e, Nt*Tm*Tn*e]
```

TILE2LINEAR：源用 tiled stride，目的用 linear stride；LINEAR2TILE：反之。

## 8. 配置与寄存器规范

### 8.1 编译期参数

| 参数 | 默认值 | 合法范围/依赖 | 影响 |
|---|---:|---|---|
| `numPorts` | 2 | 1..4 | 引擎数与 `bus` 数 |
| `queueDepth` | 8 | ≥1 | 命令 FIFO 深度 |
| `dataQueueDepth` | -1 | ≤0 时解析为 `max(maxBurstLen, 4)`；否则 ≥1 | 引擎数据队列深度 `resolvedDataQueueDepth` |
| `maxDims` | 5 | 配置类检查 1..5；顶层始终实例化访问维度 0..3 的 Tile 展开器，完整顶层实际只可使用 4..5 | `FbCmd` 数组长度、链式描述符位数 |
| `tileSize` | 8 | ≥2 | TRANSPOSE 缓冲边长 |
| `maxBurstLen` | 64 | 1..256 beat（总线 `len` 为 8 bit） | burst 上限与若干命令约束 |
| `dataWidth` | 128 | 配置类只检查 ≥32；`beatBytes` 必须为 2 的幂 | 总线数据宽度 |
| `addressWidth` | 32 | ≥12 | 地址与 stride 宽度 |
| `channelTranspose` | 空 | 空时每通道为 `true`；非空时长度等于 `numPorts` | 是否生成转置缓冲 |
| `channelPermute` | 空 | 同上 | 通道 PERMUTE 能力 |
| `channelIm2col` | 空 | 同上 | 是否生成 IM2COL 地址生成器 |
| `channelScatterGather` | 空 | 同上；另需 `dataWidth ≥ addressWidth+16` | 是否生成 SG 控制器 |
| `enableChaining` | false | — | 是否生成链式控制器与 `chainBus` |
| `chainDescBeats` | 0 | 0 为自动；正值必须足以容纳布局 | 描述符 beat 数 |
| `enableConversion` | false | 见下 | 是否生成格式转换器、MX 量化器及 `cvt*` 端口 |
| `converterPipeStages` | 1 | — | 当前传入转换器但不改变其结构，不得据此假设额外流水级 |
| `enableSharedWritePorts` | false | — | 是否生成写端口仲裁器 |
| `writeOutstandingDepth` | 1 | ≥1 | 1 为阻塞写，>1 为非阻塞写 |

额外约束：

- 所有构建把 `log2Up(beatBytes)` 用于除法、地址步进和对齐掩码，因此 `beatBytes` 必须是 2 的幂，即有效 `dataWidth` 为 32、64、128、256…；配置类未完整检查这一条件。
- `enableConversion=true` 时，`FbFormatConverter` 要求 `dataWidth` 为 32 的整数倍，`FbMxQuantizer` 要求 `dataWidth ≥ 32` 且为 2 的幂。
- 通道 SG 能力为真但 `dataWidth < addressWidth+16` 时不生成 SG 硬件，该通道 SG 命令返回错误。
- `portSelWidth = max(1, log2Up(numPorts))` 是内部值，不是顶层端口。

### 8.2 运行期配置

#### 8.2.1 命令字段

运行期配置以命令字段形式随 `FbCmd` 提供，在派发到引擎时被引擎采用，命令之间互不影响。软件不能修改已提交命令。

#### 8.2.2 全局转换参数活线

`cvtScale` 和 `cvtZeroPoint`（FB-F013）是直接组合连接到所有引擎转换器的全局活线，不是命令字段，不在命令接收或派发时快照。

软件更新任一参数前必须处于静默期：

```text
busy == 0 && occupancy == 0
```

若启用链式命令，还必须等待整条链的最终 `done`，因为链式描述符 fetch 间隙可能出现 `busy==0 && occupancy==0` 而链仍未结束。命令排队或执行期间改变活线不会触发错误，但会静默改变 INT8 转换结果。

- `cvtScale` 仅低 5 bit 生效，解释为移位量 `shift`，高 27 bit 应写 0；它不是 IEEE FP32 数。
- `cvtZeroPoint` 仅低 8 bit 生效，按有符号整数解释，高 24 bit 应写 0。
- 未使用转换时应驱动 0，不得悬空。
- MX 量化器不使用这两个端口；MX scale 由块内数据计算。

### 8.3 寄存器映射

不适用。ForagerBee 不含 MMIO 寄存器；命令以结构化 `FbCmd` Stream 输入，状态以 `busy`、`occupancy` 端口输出。Raw DW 命令编码为规划中，见第 14.3 节。

## 9. 时序与复位规范

### 9.1 时钟与跨域

ForagerBee 为 Chisel `Module`，使用单一隐式时钟，所有端口和内部逻辑在同一时钟域上升沿采样。内部不含跨时钟域逻辑；若命令源、存储系统或 `cvt*` 来自其他时钟域，跨域同步由集成方负责。

### 9.2 复位与初始化

ForagerBee 使用隐式复位，复位类型由集成顶层决定（独立发射时为 Chisel 默认的同步高有效复位）。复位后：引擎处于 IDLE，命令 FIFO 为空，in-flight 包络表清空，链式控制器处于透传状态。

复位撤销在途总线事务的行为未定义，待确认；集成方应在静默期且下游无在途事务时施加复位，并在复位期间保持下游接口一致复位。

### 9.3 事务时序与反压

- 读：每引擎最多一个读请求在途。读请求在数据队列 credit 非零时发出，并一次性预留整个 burst 的 credit（IM2COL micro-row 例外，见第 5.1.6 节）。
- 写：阻塞模式下每 burst 等待响应；非阻塞模式下最多 `writeOutstandingDepth` 个 burst 在途，`done` 等待全部响应排空（第 5.2.4 节）。
- 写端口共享时，同一物理端口响应必须按 burst 发出顺序返回。
- `rdData` 或 `wr` 被反压时引擎停止推进，不丢弃数据。
- `done` 反压：非链路径下，回执保持有效，对应通道保持 busy 与冲突锁定；链式最终回执不支持反压（第 6.2.4 节）。
- 命令 FIFO 满时 `cmd.ready=0`；队首冲突或无空闲通道时 FIFO 不出队。

## 10. 异常处理规范

### 10.1 异常检测与上报

所有可检测异常通过 `done.err=1` 上报，没有错误码（细分错误码 FB-F026 未实现）。

| 异常项 | 检测条件 | 上报途径/编码 | 执行影响 | 清除或恢复 |
|---|---|---|---|---|
| 维数/shape 非法 | `dimCount` 为 0 或大于 `maxDims`；任一活动 shape 为 0 | `done.err` | 不发普通数据读写，不发 atomic 写 | 随回执消费；下一命令独立 |
| TRANSPOSE 约束 | 维数不为 2；通道无能力；跨格式 | `done.err` | 同上 | 同上 |
| PERMUTE 约束 | 通道无能力；活动映射越界；跨格式或带 padding | `done.err` | 同上 | 同上 |
| 对齐 | 基址非 beat 对齐；非 TRANSPOSE 源行字节数不为完整 beat | `done.err` | 同上 | 同上 |
| padding | 用于非 COPY；dim0 padding 字节数不为完整 beat | `done.err` | 同上 | 同上 |
| IM2COL | 通道无能力；kernel/stride 为零；转换 burst 约束不满足 | `done.err` | 同上 | 同上 |
| SG | 通道无能力或无 SG 硬件；entry count 为 0；表地址未对齐 | `done.err` | 同上 | 同上 |
| 格式 | 非法编码；转换组合未实现；无转换硬件但格式不同 | `done.err` | 同上 | 同上 |
| INT4/MX/atomic | 第 5.1.10–5.1.12 节约束不满足 | `done.err` | 同上 | 同上 |
| T2L/L2T | 参数为零；INT4 或跨格式 | `done.err` | 同上 | 同上 |
| 数据读错误 | 正常数据读 `rdData.err=1` | `done.err` | 通常继续完成既定传输后上报；不发 atomic 写 | 同上 |
| 数据写错误 | `wrResp.err=1` | `done.err` | 同上 | 同上 |
| atomic 写错误 | atomic 写响应 `err=1` | `done.err` | 标志写可能未生效 | 同上 |
| 链节点错误 | 任一节点 `err=1`，或后续节点 MX 自守 | 最终 `done.err` | 链终止，不 fetch 后续节点 | 同上 |
| SG 地址表 fetch 错误 | `rdData.err=1` | **不检测** | 错误数据被当作条目使用 | 存储系统必须保证可靠 |
| 链描述符 fetch 错误 | `rdData.err=1` | **不检测** | 错误数据被当作描述符注入 | 同上 |
| SG 条目非法 | `lenBytes=0`、非 beat 对齐、超出计数宽度 | **不检测** | 停滞、越界写或截断 | 软件义务（第 5.1.7 节） |
| COPY 转换分段为 0 | 第 5.1.9 节压缩/扩展约束不满足 | **不检测** | 可能永久停滞 | 软件与配置义务 |
| hazard 漏检 | 第 5.2.2 节所列场景 | **不检测** | 数据竞争 | 软件串行化 |
| 地址回绕 | 地址或范围超出 `addressWidth` | **不检测** | 回绕访问 | 软件宽位检查 |

### 10.2 优先级与恢复

错误只有一个聚合 Bool，不区分优先级。命令级校验失败时引擎不发普通数据读写，也不发 atomic 成功标志。传输中的读写错误被聚合，当前状态机通常继续完成既定传输后再报错，因此收到 `err=1` 时，软件必须把目标数据视为可能部分更新。硬件不提供回滚或重试；错误不粘滞，不影响后续命令。链式命令出错时链在出错节点终止，已执行节点的写入不回滚。

## 11. 状态与观测规范

### 11.1 状态输出

| 状态 | 属性 | 含义 | 更新时机 |
|---|---|---|---|
| `busy` | 电平 | 任一引擎不在 IDLE | 组合反映引擎状态；不含 FIFO、链式 fetch 和链控制器状态 |
| `occupancy` | 电平 | 调度 FIFO 已占用项数 | 每拍更新；不含执行中的命令 |
| `done` | Stream | 完成回执（第 7.2.4 节） | 命令或链完成时 |

判定静默期必须联合使用 `busy`、`occupancy` 与链式最终 `done`（第 8.2.2 节）。

### 11.2 中断、性能事件与调试

不适用。ForagerBee 不含中断输出、内建性能计数器、性能事件端口或调试端口。完成通知通过 `done` Stream 或 atomic 完成标志轮询实现。

## 12. 性能与资源规格

### 12.1 结构与容量

| 结构 | 数值/表达式 | 默认配置 |
|---|---|---|
| 并行引擎数 | `numPorts` | 2 |
| 命令 FIFO 深度 | `queueDepth` | 8 |
| 引擎数据队列深度 | `resolvedDataQueueDepth` | 64 beat |
| 每引擎读请求在途数 | 1 | 1 |
| 每引擎写 burst 在途数 | `writeOutstandingDepth` | 1 |
| 转置 tile 缓冲 | `tileSize × tileSize` × 32 bit | 8×8 |
| MX block 缓冲 | `max(1, 2048/dataWidth)` beat | 16 beat |
| 链式描述符 | `descBeats` beat | 5 beat（`maxDims=5`、`dataWidth=128`） |

### 12.2 延迟与吞吐

当前不提供固定周期数保证，以下为结构性口径。

| 指标 | 数值/表达式 | 属性 | 成立条件与统计口径 |
|---|---|---|---|
| 单引擎峰值数据带宽 | `dataWidth` bit/拍 | 设计目标上限 | 读写无反压，行长足够、burst 未被 credit 或转换比截短 |
| 并行度 | 至多 `numPorts` 条命令同时执行 | 设计保证 | 命令间无冲突、通道能力匹配 |
| burst 长度 | ≤ `maxBurstLen`，并受行剩余、credit、转换比、MX 块边界限制 | 设计保证（IM2COL 同格式除外，见第 5.1.6 节） | `dataQueueDepth < maxBurstLen` 时按 credit 拆分 |
| TRANSPOSE | 每 tile 先填充再排空 | 结构性 | 无双缓冲重叠 |
| SG | 每条目先读一个描述符 beat 再搬运 | 结构性 | 无描述符预取 |
| MX 量化 | 收集、量化、排空三阶段 | 结构性 | scale 写与数据写共享写通道 |
| 写响应延迟 | 阻塞模式每 burst 暴露；非阻塞模式最多隐藏 `writeOutstandingDepth` 个 | 结构性 | `done` 始终等待排空 |
| 调度 | FIFO 队首策略 | 结构性 | 冲突或能力等待造成 HOL blocking |

`converterPipeStages` 当前不改变硬件，不得计入吞吐模型。共享写仲裁不增加物理写端口数。

### 12.3 面积、功耗与测量依据

未提供实测值。面积与功耗随 `numPorts`、`dataWidth`、`tileSize`、通道能力向量、`enableConversion` 与 `writeOutstandingDepth` 变化，待综合评估后补充。

## 13. 集成与使用规范

### 13.1 外部环境要求

- 存储系统必须对每个读请求返回恰好 `len+1` 拍并正确标记 `sof/eof`，对每个写 burst 返回恰好一次响应，并支持 byte strobe。
- 共享写模式下，同一物理端口的写响应必须按 burst 发出顺序返回。
- 若下游禁止跨 4 KiB，软件必须保证 burst 不跨界（第 7.3.3 节）。
- `atomic` 写属性的原子/排序语义由下游实现。
- 启用链式命令时，`done.ready` 必须恒为 1。
- `cvt*` 不使用时驱动 0。
- 链式描述符与 SG 地址表读取必须可靠，其读错误不被上报。
- `enableChaining=false` 时不得读取 `done.chainLen`。

### 13.2 使用流程

1. 从配置获取 `beatBytes`、能力向量和可选功能。
2. 按数据格式计算源/目的行字节数，构造活动 shape，清零未用字段。
3. 以字节为单位填写 `FbCmd` stride，并验证所有实际 burst 地址、行长、padding 和侧带区（MX scale、atomic、SG 表、链描述符）。
4. 对 IM2COL/SG 填写调度器要求的源/目的 byte span；范围不得回绕，并遵守 SG base/span 包络契约。
5. 对非零 `srcStartIdx`、TRANSPOSE 目的区、无法完整包络的 SG、atomic 地址等 hazard 盲区，由软件串行化可能重叠的命令。
6. 若使用全局 `cvt*`，先等待最终 chain `done`（如有），再等 `busy=0 && occupancy=0`，然后更新低有效位。
7. 使用 tag 建立 outstanding 表，不依赖完成顺序。
8. 只有 `cmd.valid && cmd.ready` 后命令才算提交；非链完成在 `done.valid && done.ready` 后消费。启用链式命令时持续拉高 `done.ready`。
9. 收到 `err=1` 时把目标数据视为可能部分更新；此前已有错误时不会写出 atomic 成功标志。
10. 若用 atomic 轮询，atomic 地址必须与所有并发访问区隔离，并保证下游对 `atomic` 属性和写响应的处理符合系统要求。
11. 需要 tile 重分块时，同尺寸优先生成等价 4D COPY；尺寸变化时使用 T2L、中间缓冲、L2T。

软件应在提交前实现与第 10.1 节一致的静态校验，并对地址乘加使用比 `addressWidth` 更宽的整数检查溢出。

### 13.3 使用示例

示例均基于默认配置（`dataWidth=128`，`beatBytes=16`，`addressWidth=32`）。

**示例 1：FP32 子块提取。** 源为 64×64 FP32 行主序矩阵（行跨度 256 B），提取从第 4 行、第 8 列开始的 8 行×16 列子块，紧密写到目的缓冲（行跨度 64 B）：

```text
op=COPY, dimCount=2, srcFmt=dstFmt=FP32
shape       = [16, 8]
srcStartIdx = [8, 4]
srcStride   = [4, 256]     dstStride = [4, 64]
srcAddr, dstAddr 按 16 B 对齐
```

dim0 行字节数为 `16×4=64 B`（4 beat，满足完整 beat）；源行首为 `srcAddr + 32 + (outerIdx+4)×256`，均按 16 B 对齐。由于 `srcStartIdx` 非零，该命令的真实源访问不在调度器包络内，软件必须把它与可能写入该源区的命令串行化。

**示例 2：INT4 行长边界。** `op=COPY`、`srcFmt=dstFmt=INT4`、`dimCount=1`：`shape(0)=32` 时行字节数为 16 B（1 beat），命令合法；`shape(0)=31` 为奇数，返回 `done.err=1`；`shape(0)=16` 时行字节数为 8 B，不是完整 beat，返回 `done.err=1`。

## 14. 约束与实现限制

### 14.1 支持边界与已知限制

| 项目 | 限制及影响 | 集成/使用义务 | 契约引用 |
|---|---|---|---|
| 链式最终 `done` | 不支持反压，`ready=0` 时回执可能丢失 | 链模式下 `done.ready` 恒为 1 | 5.1.13、6.2.4 |
| hazard 覆盖 | 非零 `srcStartIdx`、TRANSPOSE 目的区、不满足包络的 SG、atomic 写区、链描述符区不检查 | 软件串行化或隔离 | 5.2.2 |
| `maxDims` | 完整顶层实际只可用 4..5 | 配置取 4 或 5 | 8.1 |
| `beatBytes` | 必须为 2 的幂，配置类未完整检查 | 只选 32/64/128/256… 位 | 8.1 |
| COPY 转换分段 | 压缩/扩展比约束不满足时可能停滞，未报错 | 软件与配置预先保证 | 5.1.9 |
| IM2COL | 仅 `dW=1` 且水平窗口不越界时正确；跨格式要求 `4×srcMicroRowBeats ≤ maxBurstLen`；同格式不按 `maxBurstLen` 拆分 | 按实际约束生成命令 | 5.1.6 |
| SG 条目 | `lenBytes` 必须非零、按 beat 对齐、不超过 burst 计数宽度；不校验 | 软件校验 | 5.1.7 |
| fetch 错误 | SG 表与链描述符 fetch 的 `rdData.err` 不聚合 | 存储系统保证可靠 | 10.1 |
| `chainLen` | 非链构建中未定义 | 不读取 | 7.2.4 |
| 4 KiB 边界 | 不拆分 burst | 软件保证 | 7.3.3 |
| `converterPipeStages` | 不影响硬件 | 不计入时序或吞吐模型 | 8.1、12.2 |
| `busy` | 不含 FIFO 与链式 fetch | 联合 `occupancy` 与链 `done` 判断静默期 | 11.1 |
| 地址运算 | 截断到 `addressWidth`，不检查回绕 | 软件宽位检查 | 5.3.1 |
| INT8 → FP32 | 负差值数值语义未完整锁定 | 不依赖负差值结果 | 5.3.2 |
| PERMUTE | 不检查 `permVec` 双射；dim0 固定为连续维 | 软件保证 | 5.1.5 |

### 14.2 不支持能力与待确认事项

以下为未实现或规划中事项，不构成当前交付承诺：

- Raw DW 命令解码（FB-F021），设计方向见第 14.3 节；
- `tileOrder` 字段与独立 T2T opcode（FB-F022），以及单命令不同尺寸 tile 重分块；
- MX 反量化（FB-F023），以及链式后续节点的 MX scale 字段；
- INT32、INT16、INT4 跨格式算术转换（FB-F024），以及负差值 INT8 反量化的完整语义；
- 4 KiB 边界自动拆分（FB-F025）；
- 细分错误码（FB-F026）；
- IM2COL 水平 padding、水平越界窗口与 `dW≠1`；
- 待确认：复位撤销在途总线事务的行为（第 9.2 节）；面积与功耗实测（第 12.3 节）。

### 14.3 Raw DW 变长命令编码草案（规划中/非当前 RTL 契约）

当前 RTL 直接接收结构化 `FbCmd`，没有 Raw DW decoder。后续 Raw DW 方向为：

- 指令采用变长 32 bit DW，不同 op 可有不同扩展段和总长度；
- 最多描述 4 维；
- 连续布局不携带 stride，头部携带独立的 `src-stride-present` 与 `dst-stride-present` 模式；
- 显式 stride 为 16 bit element stride，不是 byte stride。

概念解析顺序可以是：公共头、地址/shape 基本段、按 present 模式出现的源/目的 stride 扩展、op 专用扩展。**字段位分配、present 模式编码、地址压缩方式、各 op 最终 DW 数量均未定，不得作为软件 ABI。**

Raw decoder 必须完成到当前 `FbCmd` 的语义转换：

1. 把最多 4 维扩展到编译期 `maxDims` 数组，未用项清零；
2. 连续侧由 shape 和对应元素位宽推导 stride；
3. 16 bit element stride 分别按源/目的元素粒度转换为 `addressWidth` bit byte stride，并检查乘法溢出；
4. INT4 element stride 若不能精确映射为整字节，必须拒绝，不能截断；
5. 生成或校验 op 专用字段、对齐、格式和 atomic 约束；
6. 若 Raw ABI 需要 tile 顺序等当前 `FbCmd` 不存在的语义，必须先定义展开规则或扩展结构化命令，不得假定当前 RTL 已有相应字段。

Raw decoder、字段表、版本号和错误细分均为规划中。在此之前不得生成声称兼容当前 RTL 的二进制命令流。

## 附录：补充资料

### A.1 常用公式

```text
beatBytes      = dataWidth / 8
burstBeats     = len + 1
rowBytes       = ceil(shape(0) * elemBits / 8)
COPY srcRow    = srcAddr + rowBytes(srcStartIdx[0], srcFmt)
               + Σ(outerIdx[d] + srcStartIdx[d]) * srcStride[d]
COPY dstRow    = dstAddr + Σ outerIdx[d] * dstStride[d]
MX scales/row  = shape(0) / blockSize
resolvedDataQueueDepth = dataQueueDepth > 0 ? dataQueueDepth : max(maxBurstLen, 4)
```

### A.2 当前 RTL 与规划项对照

| 主题 | 当前 RTL | 规划中/非当前 RTL 契约 |
|---|---|---|
| 结构化 `FbCmd` | 已实现，最多 5 维 | — |
| Raw DW decoder | 未实现 | 变长、最多 4 维、16 bit element stride、present 模式 |
| T2L/L2T | 已实现为 4D COPY 语法糖 | 独立 T2T opcode、`tileOrder` 字段 |
| 同尺寸 tile 顺序互转 | 可用 4D COPY 表达 | 专用编码未定 |
| 不同尺寸重分块 | 软件两命令 T2L+L2T | 单命令重分块 |
| 普通转换 | FP32/FP16/BF16/INT8 的受限实现 | INT16/INT32/INT4 算术、负差值 INT8 反量化语义 |
| MX | 浮点到 MX 量化 | MX 反量化、链式后续节点 MX scale 字段 |
| 错误 | 单一 `err` 位 | 细分错误码 |
| 4 KiB 边界 | 不拆分 | 自动边界拆分 |
