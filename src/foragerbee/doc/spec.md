# ForagerBee DMA 规格

> 状态：当前 RTL 契约与后续接口草案并存。除明确标注“规划中/非当前 RTL 契约”的内容外，本文以 `src/foragerbee/scala/main/` 和 `src/foragerbee/scala/test/ForagerBeeSpec.scala` 的当前实现为准。

## 1. 文档范围

本文只规定 ForagerBee DMA 的命令、数据、调度、总线和软件可见行为，不规定 HiveCore 或其他模块。

本文使用以下措辞：

- **必须/不得/应**：当前 RTL 的规范性契约，或为避免当前 RTL 错误行为而对集成、软件提出的约束。
- **规划中/非当前 RTL 契约**：尚无 RTL 实现，不能作为当前硬件能力使用。
- 注释与实现不一致时，以实际逻辑为准。例如当前 `maxDims` 实现上限为 5，而不是部分旧注释中的 4；当前普通数据突发没有 4 KiB 边界拆分逻辑。

主要事实源为：

- `src/foragerbee/scala/main/ForagerBee.scala`
- `src/foragerbee/scala/main/ForagerBeeConfig.scala`
- `src/foragerbee/scala/main/FbStreamBus.scala`
- `src/foragerbee/scala/main/FbDispatcher.scala`
- `src/foragerbee/scala/main/FbEngine.scala`
- `src/foragerbee/scala/main/FbChainController.scala`
- `src/foragerbee/scala/main/FbTile2LinearExpander.scala`
- `src/foragerbee/scala/main/FbFormatConverter.scala`
- `src/foragerbee/scala/main/FbMxQuantizer.scala`
- `src/foragerbee/scala/main/FbWrPortArbiter.scala`
- `src/foragerbee/scala/main/FbIm2colAddrGen.scala`
- `src/foragerbee/scala/main/FbScatterGatherCtrl.scala`
- `src/foragerbee/scala/test/ForagerBeeSpec.scala`

## 2. 模块概述

ForagerBee 是多引擎张量 DMA。单一命令入口先经过可选链式控制器和 Tile 语法糖展开器，再进入 FIFO 与调度器；调度器把队首命令派发给一个空闲且具备相应能力的引擎。每个引擎具有独立读通道、数据队列和执行状态机。

```text
cmd -> [ChainController] -> TileExpander -> Dispatcher/FIFO
                                             |-> Engine[0] -> bus[0]
                                             |-> ...
                                             `-> Engine[N-1] -> bus[N-1]
Engine done -> round-robin arbiter -> [ChainController] -> done
```

当前 RTL 实现以下操作：COPY、TRANSPOSE、PERMUTE、IM2COL、SCATTER、GATHER、TILE2LINEAR 和 LINEAR2TILE。TILE2LINEAR/LINEAR2TILE 在调度前被组合展开为 4D COPY，并不是引擎内独立数据通路。

## 3. 编译期配置

`ForagerBeeConfig` 的当前参数如下。

| 参数 | 默认值 | 当前约束和含义 |
|---|---:|---|
| `numPorts` | 2 | 1..4；引擎数与顶层 `bus` 数 |
| `queueDepth` | 8 | >=1；命令 FIFO 深度 |
| `dataQueueDepth` | -1 | <=0 时解析为 `max(maxBurstLen,4)`；否则必须 >=1 |
| `maxDims` | 5 | 配置类检查 1..5；但顶层始终实例化访问维度 0..3 的 Tile 展开器，因此当前完整 ForagerBee 顶层实际只可使用 4..5 |
| `tileSize` | 8 | >=2；TRANSPOSE 缓冲边长 |
| `maxBurstLen` | 64 | 1..256 beat；总线 `len` 为 8 bit |
| `dataWidth` | 128 | 配置层只检查 >=32；`beatBytes=dataWidth/8` |
| `addressWidth` | 32 | >=12 |
| `channelTranspose` | 空 | 空时每通道均为 `true`；非空时长度必须等于 `numPorts` |
| `channelPermute` | 空 | 同上 |
| `enableChaining` | false | 是否生成 `chainBus` 和链式控制器 |
| `chainDescBeats` | 0 | 0 表示由布局位数自动向上取整；正值直接覆盖 |
| `enableConversion` | false | 是否生成格式转换器和 MX 量化器，以及顶层 `cvt*` 输入 |
| `converterPipeStages` | 1 | 当前传入转换器但未改变其实现结构；不得据此假设额外流水级 |
| `channelIm2col` | 空 | 空时每通道均为 `true` |
| `channelScatterGather` | 空 | 空时每通道均为 `true` |
| `enableSharedWritePorts` | false | 是否生成写端口共享仲裁器；读通道仍一对一 |
| `writeOutstandingDepth` | 1 | >=1；1 为阻塞写，>1 为非阻塞多 outstanding 写 |

额外的实际 elaboration/功能约束：

- 所有构建都把 `log2Up(beatBytes)` 用作除法、地址步进和对齐掩码，因此 `beatBytes=dataWidth/8` 必须是 2 的幂，即有效 `dataWidth` 应为 32、64、128、256…；配置类当前未完整检查这一条件。
- `enableConversion=true` 时还会实例化 `FbFormatConverter` 和 `FbMxQuantizer`；前者要求 `dataWidth` 为 32 的整数倍，后者要求 `dataWidth>=32` 且为 2 的幂。
- Scatter/Gather 硬件仅在通道能力为真且 `dataWidth >= addressWidth + 16` 时生成；否则该操作必须返回错误。
- `portSelWidth=max(1,log2Up(numPorts))`，但该值当前不是顶层端口。

## 4. 顶层接口

所有 Stream 均使用 `valid/ready` 握手，传输发生条件为 `valid && ready`。链式最终完成回执当前存在第 13 章所述的反压限制，不能按一般 Stream 保持规则使用。

| 端口 | 方向 | 宽度/类型 | 契约 |
|---|---|---|---|
| `cmd` | 输入 Stream | `FbCmd` | 单入口命令流；命令在握手后可排队 |
| `occupancy` | 输出 | `log2Up(queueDepth+1)` | 调度 FIFO 当前占用，不含正在执行的命令 |
| `done` | 输出 Stream | `FbDone` | 非链路径可反压；链式最终回执要求 `ready` 恒为 1 |
| `busy` | 输出 | 1 bit | 任一引擎不在 IDLE 时为 1；不表示 FIFO、链式 fetch 或链控制器状态 |
| `bus[p]` | 主接口 | `FbStreamBus` | 每引擎一个读写接口；共享写模式只重映射其写半部 |
| `chainBus` | 可选主接口 | `FbStreamBus` | 仅 `enableChaining=true` 存在；当前只使用读通道 |
| `cvtScale` | 可选输入 | 32 bit | 仅 `enableConversion=true` 存在 |
| `cvtZeroPoint` | 可选输入 | 32 bit | 仅 `enableConversion=true` 存在 |

### 4.1 全局转换参数活线

`cvtScale` 和 `cvtZeroPoint` 是直接组合连接到所有引擎转换器的**全局活线**，不是命令字段，也不会在命令接收或派发时快照。

软件在更新任一参数前必须满足：

```text
busy == 0 && occupancy == 0
```

若启用了链式命令，软件还必须等待整条链的最终 `done`，因为链式描述符 fetch 间隙可能出现 `busy==0 && occupancy==0`，但链仍未结束。命令排队或执行期间改变活线不会触发错误，却会静默改变 INT8 转换结果。

- `cvtScale` 仅低 5 bit 生效，解释为移位量 `shift`；高 27 bit 应写 0。它不是 IEEE FP32 数。
- `cvtZeroPoint` 仅低 8 bit 生效，并按有符号整数解释；高 24 bit 应写 0。
- 未使用转换时应驱动 0，不得悬空。
- MX 量化器不使用这两个端口；MX scale 由块内数据计算。

## 5. 数据格式

### 5.1 编码

`srcFmt/dstFmt` 均为 4 bit。编码是软件可见契约。

| 编码 | 名称 | 元素位数 | `bytesOfCeil` | 当前用途 |
|---:|---|---:|---:|---|
| 0 | FP32 | 32 | 4 | 搬运、转换、MX 源 |
| 1 | FP16 | 16 | 2 | 搬运、转换、MX 源 |
| 2 | BF16 | 16 | 2 | 搬运、转换、MX 源 |
| 3 | INT8 | 8 | 1 | 搬运、与浮点转换 |
| 4 | MXE4M3 | 8 | 1 | MX 量化目的；copy-only 构建可作裸字节格式 |
| 5 | MXE5M2 | 8 | 1 | 同上 |
| 6 | 保留 | - | - | 非法 |
| 7 | 历史保留 | - | - | 非法，不得复用 |
| 8 | INT32 | 32 | 4 | 同格式搬运；跨格式算术未实现 |
| 9 | INT16 | 16 | 2 | 同格式搬运；跨格式算术未实现 |
| 10 | INT4 | 4 | 1（地址计算辅助值） | 受限的同格式 COPY |
| 11..15 | 保留 | - | - | 非法 |

非法编码必须产生 `done.err=1`，不得进入转换器。

行字节数按下式计算，INT4 也适用：

```text
rowBytes(n, fmt) = ceil(n * elemBits(fmt) / 8)
```

### 5.2 普通格式转换

仅 `enableConversion=true` 且 `srcFmt!=dstFmt` 时激活普通转换。当前算术支持 FP32、FP16、BF16、INT8 之间的路径，FP16/BF16/INT8 组合经 FP32 中间表示。COPY 和 IM2COL 可使用普通转换；TRANSPOSE、PERMUTE、SCATTER、GATHER 和 Tile 语法糖不得跨格式。

当前数值语义及限制：

- FP32 到 FP16/BF16 使用实现中的 RNE 位级舍入；FP16/BF16 到 FP32 为位级扩展。不得把该简化实现等同于完整 IEEE 异常、次正规与 NaN 处理器。
- FP32 到 INT8 使用低 5 bit `shift` 做 2 的幂缩放，再加低 8 bit zero point 并饱和到 [-128,127]。当前逻辑的整数化是移位截取，不得依赖注释中未实现的通用实数 scale 或完整舍入行为。
- INT8 到 FP32 计算意图为 `(int8-zeroPoint)<<shift`。当前测试锁定了差值非负的情形；当前位级实现未提供对所有负差值的完整软件数值契约，负差值依赖应视为**规划中/非当前 RTL 契约**。
- 任何涉及 INT32、INT16 或 INT4 的跨格式转换必须报错；同格式搬运可以使用 INT32/INT16。
- `enableConversion=false` 时必须有 `srcFmt==dstFmt`。
- COPY 压缩时，源行 beat 数必须是压缩比的整数倍，且 `maxBurstLen` 与 `resolvedDataQueueDepth` 都必须不小于压缩比；否则当前分段向下对齐可能得到 0 beat并永久停滞。COPY 扩展时，`maxBurstLen` 必须不小于扩展比。当前 RTL 未对这些 COPY 组合做完整命令级报错，软件和配置必须预先保证。

### 5.3 INT4

当前 INT4 必须同时满足：

- 仅允许 COPY，且 `srcFmt==dstFmt==INT4`；
- `shape(0)` 必须为偶数；
- `srcStartIdx(0)` 必须为偶数；
- 源行字节数必须是完整 beat；
- 不得使用 padding、TRANSPOSE、PERMUTE、IM2COL、SCATTER/GATHER 或 T2L/L2T；
- stride 仍是字节 stride。RTL 不提供半字节地址。

### 5.4 MX 量化

`enableConversion=true` 时，只要 `dstFmt` 为 MXE4M3/MXE5M2 就激活 MX 量化，不存在独立量化使能位。命令必须满足：

- `op=COPY`；
- `srcFmt` 为 FP32、FP16 或 BF16；
- `scaleBlockSize` 编码 0/1/2，分别表示 16/32/64 个元素；
- `shape(0)` 是块大小的整数倍；
- 单块源 beat 数不超过 `maxBurstLen` 和 `resolvedDataQueueDepth`；
- `scaleAddr` 按 beat 对齐；
- 不使用 padding。

每块输出一个 E8M0 scale 字节。scale 行地址为：

```text
scaleAddr + Σ outerIdx[d] * dstStride[d], d=1..dimCount-1
```

行内 scale 字节依次存放，尾部用 `strb` 部分写。数据区与 scale 区应由软件保证不重叠。

当前硬件不支持 MX 反量化。`srcFmt` 为 MX 的命令在 `enableConversion=true` 构建中必须报错，包括 MX 到 MX。`enableConversion=false` 时没有量化器，`srcFmt==dstFmt==MX*` 可按 1 byte/element 做裸字节 COPY。

MX 量化采用块内最大真指数、E8M0 scale、尾数 RNE、下溢清零和上溢饱和。E4M3 NaN 输出 NaN 编码；E5M2 NaN 饱和到最大正常数。

## 6. 地址与张量模型

### 6.1 维度约定

- `dim0` 是最内层连续维，`dimCount` 当前可为 1..`maxDims`，默认最大 5。
- `shape[d]` 是 16 bit 元素数；所有活动维必须非零，非活动维应写 0。
- `srcStride[d]`、`dstStride[d]` 均为 `addressWidth` bit **byte stride**。
- 普通 COPY 中 stride(0) 是信息字段，地址生成不使用它；dim0 由连续 burst 展开。
- 所有地址加法最终截断到 `addressWidth`，RTL不检查溢出或回绕。软件必须保证地址范围不回绕。

COPY 的源行首和目的行首为：

```text
srcRow = srcAddr
       + rowBytes(srcStartIdx[0], srcFmt)
       + Σ (outerIdx[d] + srcStartIdx[d]) * srcStride[d]

dstRow = dstAddr + Σ outerIdx[d] * dstStride[d]
                 d=1..dimCount-1
```

PERMUTE 的源侧把上式中的 `srcStride[d]` 替换为 `srcStride[permVec[d]]`；dim0 仍为连续维，`permVec[0]` 不参与当前行地址计算。目的侧仍按 `dstStride[d]`。因此当前 PERMUTE 不是任意“搬动连续维”的通用重排器。

### 6.2 对齐

- `srcAddr` 和 `dstAddr` 必须按 beat 对齐；该检查对所有操作生效。
- 除 TRANSPOSE 外，`rowBytes(shape(0),srcFmt)` 必须为完整 beat。
- TRANSPOSE 允许源行长度不是完整 beat，并在写侧使用 `strb`；但软件仍应保证每个实际源行读请求地址符合下游对齐要求。
- RTL只检查基址，不逐行检查 stride 生成地址；软件必须保证所有实际 burst 起始地址满足系统总线要求。
- 当前 RTL 不按 4 KiB 边界拆 burst。若下游禁止跨 4 KiB，软件必须拆分命令或安排地址/行长使每个 burst 不跨界。

### 6.3 裁剪与 padding

`srcStartIdx[d]` 仅偏移源侧，支持 COPY、PERMUTE 和 TRANSPOSE 的源子块起点。目的侧从 `dstAddr` 开始。

Zero-padding 仅允许 COPY：

- `padBefore/After[0]` 在每个数据行前后写零；对应目的字节数必须各自为完整 beat。
- `padBefore/After[1]` 增加整行零行。
- `dimCount>2` 时 padding 仍只作用于 dim0/dim1，各外层切片重复该行为。
- INT4 与 padding 互斥。

## 7. 命令通用定义

### 7.1 操作码

`FbOp` 当前为 3 bit ChiselEnum，声明顺序即编码。

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

### 7.2 `FbCmd` 字段

| 字段 | 宽度 | 通用含义 |
|---|---:|---|
| `op` | 3 | 操作码 |
| `tag` | 8 | 软件标签，完成时原样返回；链模式最终返回链首标签 |
| `dimCount` | 3 | 活动维数 |
| `shape[maxDims]` | 每项16 | 元素数；IM2COL/SG 另有 bbox 特殊用途 |
| `srcStride[maxDims]` | 每项 addressWidth | 源 byte stride |
| `dstStride[maxDims]` | 每项 addressWidth | 目的 byte stride；MX scale 外层行也复用 |
| `srcStartIdx[maxDims]` | 每项16 | 源裁剪起始索引 |
| `t2lMatCols` | 16 | T2L/L2T 的矩阵列数 N |
| `t2lTileRows` | 16 | tile 行数 Tm |
| `t2lTileCols` | 16 | tile 列数 Tn |
| `t2lNumTileRows` | 16 | tile 行块数 Mt |
| `t2lNumTileCols` | 16 | tile 列块数 Nt |
| `srcAddr` | addressWidth | 源基址 |
| `dstAddr` | addressWidth | 目的基址 |
| `permVec[maxDims]` | 每项3 | PERMUTE 的源 stride 索引映射 |
| `padBefore[2]` | 每项16 | dim0/dim1 前置零填充 |
| `padAfter[2]` | 每项16 | dim0/dim1 后置零填充 |
| `nextDescAddr` | addressWidth | 下一链式描述符地址，0 表示结束 |
| `chainMode` | 1 | 链模式请求 |
| `srcFmt`,`dstFmt` | 各4 | 数据格式 |
| `im2colKernel[2]` | 每项8 | kH、kW |
| `im2colStride[2]` | 每项8 | sH、sW |
| `im2colPad[2]` | 每项8 | pH、pW，对称参数 |
| `im2colDilation[2]` | 每项8 | dH、dW |
| `im2colInShape[3]` | 每项16 | C、H、W |
| `sgListAddr` | addressWidth | SG 地址表基址 |
| `sgEntryCount` | 16 | SG 条目数 |
| `scaleAddr` | addressWidth | MX E8M0 scale 写基址 |
| `scaleBlockSize` | 2 | 0/1/2 -> 16/32/64 |
| `atomicEnable` | 1 | 成功后写完成标志 |
| `atomicStoreAddr` | addressWidth | 32 bit 完成标志地址，必须 4-byte 对齐 |

未用于当前操作的字段应写 0。当前命令接口没有 `tileOrder` 字段。

## 8. 各操作定义

### 8.1 COPY

COPY 必须按 dim1 最快、随后 dim2.. 的外层进位顺序遍历，每行把 dim0 展开为连续读写 burst。源和目的可使用独立 stride。COPY 可组合源裁剪、zero-padding、普通格式转换、MX 量化和 atomic 完成标志，但必须同时满足各功能约束。

行内源 burst 长度受行剩余、`maxBurstLen` 和 data queue credit 限制；格式压缩/扩展时还必须按转换比调整。

### 8.2 TRANSPOSE

TRANSPOSE 必须有 `dimCount=2`，且只能派发到支持转置的通道。令 `M=shape(0)`、`N=shape(1)`，当前实现以 `tileSize x tileSize` 缓冲按 tile 读取源矩阵并写出转置矩阵。

- 源行地址主要使用 `srcStride(1)`。
- 目的转置输出第 `j` 行使用 `dstAddr + j*dstStride(0)`，行内沿原行维连续写。
- `srcFmt` 必须等于 `dstFmt`；INT4 不支持。
- 边缘 tile 通过有效行列数和 `strb` 处理。

### 8.3 PERMUTE

PERMUTE 与 COPY 共用读写通路，只在源外层地址计算时使用 `permVec[d]` 选择 stride。活动项必须 `<dimCount`。RTL不检查 `permVec` 是否互不重复；若软件需要真正的排列，必须自行保证其为双射。PERMUTE 不支持 padding或跨格式转换，且通道必须具备 PERMUTE 能力。

### 8.4 IM2COL

IM2COL 输出遍历顺序为：`oh -> ow -> c -> kh -> kw`，其中 kw 构成一次 micro-row。输出尺寸为：

```text
outH = floor((H + 2*pH - dH*(kH-1) - 1) / sH) + 1
outW = floor((W + 2*pW - dW*(kW-1) - 1) / sW) + 1
```

`kH/kW/sH/sW` 必须非零。垂直越界 micro-row 写零。当前引擎没有使用地址生成器的 `burstable` 信号，而是总把 micro-row 当连续数据读取；因此当前正确使用范围必须满足 `dW=1` 且每个非垂直 padding micro-row 的整个水平窗口均在输入行内。特别是水平 padding/水平越界窗口不是当前 RTL 契约。

IM2COL 可使用普通格式转换，但不得使用 INT4 或 MX 量化。压缩时 micro-row 源 beat 数必须是转换比的整数倍且折算后至少 1 beat。当前跨格式命令采用保守上界检查：无论实际转换比和方向，均要求 `4 * sourceMicroRowBeats <= maxBurstLen`；软件必须按这一实际限制生成命令。同格式路径不会按 `maxBurstLen` 拆分或拒绝完整 micro-row，因此若下游把 `maxBurstLen` 当作硬上限，软件还必须保证同格式 `sourceMicroRowBeats <= maxBurstLen`。源 micro-row beat 数也必须不超过 `resolvedDataQueueDepth`；当前读请求只检查 credit 非零，不会按剩余 credit 拆分该 micro-row。

调度器对 IM2COL 特殊解释 `shape(0)` 为源内存保守 byte span、`shape(1)` 为目的内存保守 byte span。软件必须填入真实范围；这两个字段在此不表示常规张量 shape。

### 8.5 SCATTER/GATHER

地址表每条目占一个完整 beat：

```text
bits[addressWidth-1:0]                 = addr
bits[addressWidth+15:addressWidth]     = lenBytes
其余高位                                  = 忽略
entry[i] address                       = sgListAddr + i*beatBytes
```

`sgListAddr` 必须按 beat 对齐，`sgEntryCount` 必须非零。GATHER 从各条目地址读取，按条目顺序连续写到 `dstAddr`；SCATTER 从 `srcAddr` 连续读取，按条目顺序写到各条目地址。

当前控制器把 `lenBytes` 向上取整到 beat 数，数据写使用全 strobe；它不校验条目地址、长度或读数据错误。因此软件必须保证：

- 每个条目 `lenBytes>0`，否则当前状态机会停滞；
- 条目地址按 beat 对齐；
- `lenBytes` 是 beatBytes 的整数倍，否则会多搬运并覆盖尾部字节；
- `ceil(lenBytes/beatBytes)` 必须能由 `log2Up(maxBurstLen+1)` bit 表示；更大的条目 beat 数会在当前寄存器中截断；
- 地址表 fetch 和数据区域合法、可访问。

SCATTER/GATHER 不支持格式转换。调度器固定把源范围解释为 `[srcAddr,srcAddr+shape(0))`，把目的范围解释为 `[dstAddr,dstAddr+shape(1))`。因此软件若依赖硬件冲突保护，必须满足：

- GATHER：`srcAddr` 必须不大于所有条目源地址，`shape(0)` 必须覆盖所有条目访问区间；
- SCATTER：`dstAddr` 必须不大于所有条目目的地址，`shape(1)` 必须覆盖所有条目访问区间；
- 另一侧的线性区也必须由对应 base/span 完整覆盖；
- span 字段只有 16 bit，无法表达完整包络时必须串行化相关命令；
- `sgListAddr` 描述符表本身不参与 hazard 检查，在命令完成前不得被其他主机修改或覆盖。

即使 `dimCount=1`，软件也必须填好 `shape(0/1)`。若上述 base/span 契约不成立，硬件可能漏检 SG 与并发命令之间的冲突。

### 8.6 TILE2LINEAR、LINEAR2TILE 与 tile-to-tile

当前有 TILE2LINEAR 和 LINEAR2TILE 两个语法糖。两者展开为：

```text
shape = [Tn, Tm, Nt, Mt]
tiled stride = [e, N*e, Tn*e, Tm*N*e]
linear stride = [e, Tn*e, Tm*Tn*e, Nt*Tm*Tn*e]
```

其中 `e=bytesOfCeil(srcFmt)`。TILE2LINEAR 使用 tiled stride 作源、linear stride 作目的；LINEAR2TILE 相反。`N/Tm/Tn/Mt/Nt` 均必须非零，软件必须自行保证 Mt/Nt 与矩阵和 tile 尺寸一致。两者要求字节粒度、`srcFmt==dstFmt`，不支持 INT4 或转换。

必须明确：

- 当前 `FbCmd` 没有 `tileOrder` 字段，T2L/L2T 不接受运行时 row-major/column-major 选择。
- 相同 tile 尺寸下，row-major/column-major 的 tile 顺序互转可由普通 **4D COPY** 配合独立 `srcStride/dstStride` 表达。
- 当前没有独立 T2T opcode。
- 不同 tile 尺寸的重分块通常必须由软件发出 T2L 后接 L2T 两条命令，并使用中间线性缓冲。

### 8.7 Atomic 完成标志

`atomicEnable=1` 且命令数据阶段无错误时，引擎必须在 `done` 前发出一个单拍写 burst：

- `atomic=1`、`sof=eof=1`、`len=0`；
- 向 `atomicStoreAddr` 所在 4-byte lane 写入 little-endian 32-bit 值 1；
- `addr` 为该地址向下对齐后的 beat 地址，`strb` 只使能目标 4 字节；
- `atomicStoreAddr` 必须 4-byte 对齐，但不要求 beat 对齐。

若命令已出错，不得发 atomic 写。atomic 写自身响应报错时，最终 `done.err` 必须为 1。调度器不跟踪 `atomicStoreAddr`，因此该 4-byte 写区不受 RAW/WAR/WAW 硬件保护；软件必须确保它与所有并发命令的源、目的、MX scale、SG/链描述符区及其他 atomic 地址不重叠，或串行提交相关命令。

## 9. Raw DW 变长命令编码（规划中/非当前 RTL 契约）

当前 RTL直接接收结构化 `FbCmd`，没有 Raw DW decoder。`FbSharedCmd` 只是未使用的 3x32-bit 占位 Bundle，不能视为已实现命令编码。

后续 Raw DW 方向为设计草案：

- 指令采用变长 DW；
- 最多描述 4 维；
- 连续布局不携带 stride；
- 显式 stride 是 16-bit **element stride**，不是 byte stride；
- 头部携带独立的 `src-stride-present` 与 `dst-stride-present` 模式；
- 不同 op 可有不同扩展段和不同总长度。

概念解析顺序可以是：公共头、地址/shape 基本段、按 present 模式出现的源/目的 stride 扩展、op 专用扩展。**字段位分配、present 模式编码、地址压缩方式、各 op 最终 DW 数量均未定，不得作为软件 ABI。**

Raw decoder 必须完成到当前 `FbCmd` 的语义转换：

1. Raw 最多 4 维必须扩展到编译期 `maxDims` 数组，未用项清零；当前 `FbCmd` 最多支持 5 维。
2. 连续侧必须由 shape 和对应元素位宽推导 stride。
3. 16-bit element stride 必须分别按源/目的元素粒度转换为 `addressWidth` bit byte stride，并检查乘法溢出。
4. 当前 `FbCmd` 的 stride 是 byte stride；INT4 element stride 若不能精确映射为整字节，decoder 必须拒绝，不能截断。
5. decoder 必须生成或校验当前结构化字段中的 op 专用信息、对齐、格式和 atomic 约束。
6. 若 Raw ABI 需要 TILE 顺序或其他当前 `FbCmd` 不存在的语义，必须先定义展开规则或扩展结构化命令；不得假定当前 RTL已有相应字段。

Raw decoder、字段表、版本号和错误细分均为规划中。

## 10. 调度与并发

调度器是单 FIFO、只检查队首的 HOL 调度器。派发规则：

1. 队首命令按当前保守地址模型与所有 in-flight 命令检查 RAW、WAR 和 WAW；
2. 优先选择编号最小的空闲且具备操作能力的通道；
3. 若没有任何通道具备能力，则可退化派发到任一空闲通道，由引擎返回 `err=1`，而不是永久阻塞。

普通 ND 范围按外层各维 `(shape[d]-1)*stride[d]` 之和加 dim0 行字节估计；PERMUTE 源范围使用重映射 stride；目的范围使用 `dstFmt` 粒度。IM2COL 和 SG 使用命令提供的 `shape(0/1)` byte span。MX scale 作为独立第三写区参与冲突检查。

当前 hazard 模型存在以下限制，软件必须规避：

- 非零 `srcStartIdx` 未进入调度范围计算，真实源访问可能超出所记录上界；此类命令与可能重叠的命令必须串行化。
- TRANSPOSE 实际目的地址沿 `dstStride(0)` 推进，但调度器仍按普通 ND 的 `dstStride(1)` 模型估计，目的包络不可靠；可能重叠的 TRANSPOSE/其他命令必须串行化。
- SG 只有在第 8.5 节规定的 base/span 契约成立时才具有有效包络；地址表自身不参与检查。
- atomic 4-byte 写区不参与检查，必须由软件隔离或串行化。
- 地址范围乘加最终截断到 `addressWidth`；会回绕的范围不得提交。

因此当前 bounding box 可能产生假冲突，也可能在上述场景漏检，不能把它视为完整内存一致性机制。

每通道 in-flight 状态保持到对应引擎的 `done.fire`。因此非链路径下游反压 `done` 会保持引擎 busy 与冲突锁定。多个引擎完成通过 transaction-lock round-robin 仲裁汇流，完成顺序不保证等于提交顺序；软件必须用 tag 关联。

## 11. DMA Stream Bus

`FbStreamBus` 含四条独立 Stream。

### 11.1 读

| 通道 | Payload | 契约 |
|---|---|---|
| `rd` | `addr[addressWidth]`, `len[8]` | 一次请求；实际 beat 数=`len+1` |
| `rdData` | `data[dataWidth]`, `sof`, `eof`, `err` | 从设备返回数据流；首/末拍标记 |

当前引擎按内部计数消费 beat，不验证 `sof/eof` 与请求长度是否一致。下游必须返回恰好 `len+1` 拍，并正确给出首末标记。当前每引擎最多一个读请求在途。

### 11.2 写

`wr` payload：

| 字段 | 宽度 | 契约 |
|---|---:|---|
| `addr` | addressWidth | 仅 `sof=1` 首拍有效 |
| `len` | 8 | 仅首拍有效；burst beat 数=`len+1` |
| `data` | dataWidth | 每拍有效数据 |
| `strb` | beatBytes | 每字节写使能 |
| `sof`,`eof` | 各1 | burst 首末拍；单拍时均为1 |
| `atomic` | 1 | 普通写必须为0；atomic 标志写为1 |

每个写 burst 必须恰好返回一次 `wrResp{err}`。`atomic` 只是总线属性，是否具备原子/排序语义由下游实现；ForagerBee 本身只保证标志写在本命令数据写响应排空后发出。

链式 `chainBus` 当前只发 `rd/rdData`；其 `wr.valid=0`，`wrResp.ready=0`。

## 12. 写通路

### 12.1 阻塞写

`writeOutstandingDepth=1` 时，引擎在每个 burst EOF 后必须等待该 burst 的 `wrResp` 才推进。共享写端口仲裁器从 SOF 锁定物理端口直到响应返回，burst 中途不得切换 owner。

### 12.2 非阻塞写

`writeOutstandingDepth>1` 时，引擎在 EOF 握手后即可推进，最多保持配置深度的写 burst 在途；最终 `done` 必须等待所有响应排空。读侧仍只有一个请求在途。

共享写模式下：

- 物理端口的数据锁从 SOF 保持到 EOF；
- 每个端口使用深度为 `writeOutstandingDepth` 的 owner FIFO，从 SOF 保持归属到响应；
- 同一物理端口的响应必须按 burst 发出顺序返回，因为 `FbWriteResp` 没有 ID；
- 有在途响应的引擎绑定到原物理端口，直至其在途计数归零。

直连非阻塞模式下，每引擎独享响应通道，只按计数回收，不要求跨 burst 响应顺序。

### 12.3 部分写

普通 COPY/IM2COL/SG 和 padding 写通常使用全 strobe；TRANSPOSE 边缘/非对齐输出、MX scale 和 atomic 标志使用部分 strobe。下游必须正确支持 byte strobe。

## 13. 链式描述符

仅 `enableChaining=true` 时生成链式控制器和 `chainBus`。链首从 `cmd` 接收；当 `chainMode=1` 时，控制器等待当前节点完成，再从 `nextDescAddr` 读取下一描述符。节点严格串行，遇到 `nextDescAddr=0` 或节点错误即结束。

**当前链式完成回执不支持反压。** 终止节点完成时，控制器只在该拍组合拉高 `done.valid`，随后立即返回透传状态，并不会等待 `done.fire`。因此启用链式命令时，集成方必须令顶层 `done.ready` 恒为 1；否则最终完成回执可能丢失。非链命令的常规 done 路径仍可反压。

描述符由若干 `dataWidth` beat 组成，beat 顺序为 little-endian：beat 0 承载最低位。字段紧密排列，顺序为：

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

当前常用值：`maxDims=5,addressWidth=32` 时为 597 bit，128-bit 总线 5 beat、256-bit 总线 3 beat；`maxDims=4,addressWidth=32` 时为 514 bit，128-bit 总线 5 beat。

当前链式格式限制必须视为契约：

- 描述符 `op` 只有 2 bit，只能编码 COPY/TRANSPOSE/PERMUTE；其他 op 不可编码。
- 不序列化 `srcStartIdx`、Tile、IM2COL、SG、MX scale 和 atomic 字段，注入节点对应字段均为 0。
- 链首是完整 `FbCmd` 直通，因此链首仍可使用完整字段；上述限制针对内存中的后续节点。
- `enableConversion=true` 时，后续描述符若 `dstFmt` 为 MX，会被强制转成非法 `dimCount=0` 并以错误终止链，防止向地址 0 写 scale。
- `enableConversion=false` 时允许链式 MX 同格式裸字节 COPY。
- chain descriptor fetch 的 `rdData.err` 当前未被控制器采集；存储系统必须保证描述符读取可靠。
- `chainDescBeats` 若手工覆盖，必须足以容纳布局；RTL不检查覆盖值是否过小。

## 14. 完成与错误

`FbDone` 字段为：`tag[7:0]`、`err`、`chainLen[15:0]`。

- 普通引擎完成返回命令 tag 和聚合错误位。
- `enableChaining=true` 时，非链命令 `chainLen=1`；链命令最终只返回一次完成，tag 为链首 tag，`chainLen` 为已执行节点数。
- `enableChaining=false` 时，引擎当前没有给 `chainLen` 赋确定值；软件不得读取该字段。只有 tag/err 是当前有效契约。
- 非链完成回执在握手前保持，引擎保持 busy；链式最终完成存在第 13 章的反压缺陷，要求 `done.ready=1`。

以下任一命令级条件必须置 `err=1`：

- `dimCount` 为 0 或大于 `maxDims`，或任一活动 shape 为 0；
- TRANSPOSE 维数不为 2或通道无能力；PERMUTE 通道无能力或活动映射越界；
- 基址不按 beat 对齐；非 TRANSPOSE 源行字节数不为完整 beat；
- padding 用于非 COPY，或 dim0 padding 字节数不为完整 beat；
- IM2COL 能力/非零参数/转换 burst 约束不满足；
- SG 能力、entry count 或 list 对齐不满足；
- 格式编码非法、转换组合未实现、无转换硬件却格式不同；
- INT4、MX 或 atomic 地址约束不满足；
- 总线正常数据读或写响应报告错误。

错误只有聚合 Bool，没有错误码。非法命令不应发普通数据读写；若启用 atomic，也不得发 atomic 成功标志。传输中的读/写错误会被聚合，但当前状态机通常继续完成既定传输后再报错。SG 描述符 fetch 和链描述符 fetch 的 `rdData.err` 当前不会进入最终错误位。

## 15. 性能

性能应按配置和操作估算，不保证固定周期数。

- 理论每拍数据宽度为 `dataWidth`；命令最多可并行占用 `numPorts` 个引擎。
- COPY burst 上限为 `maxBurstLen`，同时受行剩余、data queue credit、格式转换比和 MX 块边界限制。
- `dataQueueDepth<maxBurstLen` 时 burst 会按 credit 拆分。
- TRANSPOSE 每 tile 先填充再排空，没有填充/排空双缓冲重叠。
- SG V1 无描述符预取；每条目先读一个描述符 beat，再搬运数据。
- MX 量化为收集、量化、排空三阶段，scale 写与数据写共享写通道。
- 阻塞写每 burst 等响应；非阻塞写可把最多 `writeOutstandingDepth` 个响应延迟隐藏在执行之后，但 `done` 仍等待排空。
- 共享写仲裁按 burst round-robin；它不增加物理端口数，当前物理写端口数仍等于 `numPorts`。
- 调度为 FIFO 队首策略，冲突或能力等待会造成 HOL blocking。
- `converterPipeStages` 当前不改变硬件，不得计入吞吐模型。
- 当前没有 4 KiB 边界拆分；不能把地址宽度 >=12 的配置检查解释为已有该能力。

## 16. 软件编程模型

软件提交命令应遵循：

1. 从配置获取 `beatBytes`、能力向量和可选功能。
2. 按数据格式计算源/目的行字节数，构造活动 shape；清零未用字段。
3. 以 byte 为单位填写当前 `FbCmd` stride，并验证所有实际 burst 地址、行长、padding 和特殊侧带区。
4. 对 IM2COL/SG 填写调度器要求的源/目的 byte span；范围不得回绕，并遵守 SG base/span 包络约定。
5. 对非零 `srcStartIdx`、TRANSPOSE 目的区、无法完整包络的 SG、atomic 地址等 hazard 盲区，由软件串行化可能重叠的命令。
6. 若使用全局 `cvt*`，先等最终 chain done（如有），再等 `busy=0 && occupancy=0`，然后更新低有效位。
7. 使用 tag 建立 outstanding 表；不得依赖完成顺序。
8. 只有 `cmd.valid && cmd.ready` 后命令才算提交；非链完成只在 `done.valid && done.ready` 后消费。启用链式命令时必须持续拉高 `done.ready`。
9. 收到 `err=1` 时，软件必须把目标数据视为可能部分更新；atomic 成功标志不会在此前已有错误时写出。
10. 若用 atomic 轮询，atomic 地址必须与所有并发访问区隔离，并保证下游对 `atomic` 属性和写响应的处理符合系统要求。
11. 对需要 tile 重分块的工作，软件应优先生成等价 4D COPY；尺寸变化时使用 T2L、中间缓冲、L2T。

建议软件在提交前实现与第 14 章一致的静态校验，并对地址乘加使用比 `addressWidth` 更宽的整数检查溢出。

## 17. 验证要求

RTL回归必须至少覆盖：

- 1D 到 5D COPY、独立 stride、源裁剪、burst/queue credit 拆分；
- TRANSPOSE 完整 tile、边缘 tile、长行分段、不同字节粒度；
- PERMUTE 2D/3D/5D、非法映射和非双射软件检查；
- dim0/dim1 padding、外层切片、对齐错误；
- T2L/L2T 对称、非方 tile、非法参数、INT4/跨格式自守，以及 4D tile-order 参考模型；
- IM2COL 基本、垂直 padding、stride、多通道、格式扩展/压缩及非法 micro-row；水平 padding/dW 非1应作为已知非契约负向场景；
- SG gather/scatter、变长但 beat 对齐条目、能力关闭、空表，以及零长度条目不提交的断言；
- 所有格式编码、非法编码、INT4 约束、普通转换正反向、全局活线分发到每个通道；
- MX E4M3/E5M2、块大小、全零块、scale 地址/冲突、非法源、反量化拒绝；
- RAW/WAR/WAW、目的格式粒度 bbox、MX scale 第三区间、多引擎并发与 done 反压；
- 阻塞/非阻塞写、共享端口 owner/响应顺序、错误响应、最终排空；
- atomic 非 beat 对齐但 4-byte 对齐地址、数据错误抑制、atomic 响应错误；
- 链长度、错误截断、v3 布局位数、后续节点字段限制、MX 自守；
- elaboration 参数边界和可选端口/模块裁剪。

参考模型必须按 byte strobe 更新内存，并检查每个 burst 的 `len+1`、SOF/EOF、响应恰一次以及普通写 `atomic=0`。断言应覆盖共享写端口无 owner 时不得接收响应、owner FIFO 不为空才可接收响应。

当前 `ForagerBeeSpec.scala` 已覆盖上述大部分主路径和错误路径，但测试存在并不扩展 RTL 契约；未实现能力必须保持负向测试或明确标为规划中。

## 18. 附录

### A. 常用公式

```text
beatBytes = dataWidth / 8
burstBeats = len + 1
rowBytes = ceil(shape(0) * elemBits / 8)
COPY source row = srcAddr + srcStart dim0 byte offset
                + Σ(outerIdx[d]+srcStartIdx[d])*srcStride[d]
COPY destination row = dstAddr + Σ outerIdx[d]*dstStride[d]
MX scales per row = shape(0) / blockSize
```

### B. 当前 RTL 与规划项边界

| 主题 | 当前 RTL | 规划中/非当前 RTL 契约 |
|---|---|---|
| 结构化 `FbCmd` | 已实现，最多5维 | - |
| Raw DW decoder | 未实现 | 变长、最多4维、16-bit element stride、present 模式 |
| T2L/L2T | 已实现为4D COPY语法糖 | 独立 T2T opcode、`tileOrder` 字段 |
| 同尺寸 tile 顺序互转 | 可用4D COPY表达 | 专用编码未定 |
| 不同尺寸重分块 | 软件两命令 T2L+L2T | 单命令重分块未实现 |
| 普通转换 | FP32/FP16/BF16/INT8 的受限实现 | INT16/INT32/INT4 算术、完整负 INT8 反量化语义 |
| MX | 浮点到 MX 量化 | MX 反量化、链式后续节点 MX scale 字段 |
| 错误 | 单一 `err` Bool | 细分错误码 |
| 4 KiB 边界 | 不拆分 | 自动边界拆分 |

### C. 已知集成注意事项

- `busy` 不包含命令 FIFO 和链式 fetch；必须与 `occupancy`、最终 chain done 联合判断静默期。
- 链式最终 `done` 当前不能反压，链模式下必须令 `done.ready=1`。
- 非链构建的 `done.chainLen` 当前未定义。
- SG 与链描述符 fetch 错误未聚合。
- 调度器不完整覆盖非零 `srcStartIdx`、TRANSPOSE 目的范围、SG 任意条目地址和 atomic 写区；软件必须补充 hazard 管理。
- 地址范围和乘法会截断，软件必须先做宽位检查。
- 当前 Raw DW 章节只是设计方向，不得生成声称兼容当前 RTL 的二进制命令流。
