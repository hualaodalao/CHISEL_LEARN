# HiveCore 硬件规格

## 1. 文档说明

### 1.1 目的

本文定义 HiveCore 的软件可见行为、计算语义、存储布局、接口协议和配置约束。本文只描述当前 RTL 已实现的功能，不记录开发过程，也不承诺尚未实现的功能。

### 1.2 范围

本文覆盖：

- HiveCore GEMM 阵列及其执行顺序；
- INT、FP8、MXFP8、MXFP4 数据格式；
- A、B、C、scale、bias 和 requant 参数的存储布局；
- 命令、响应、DMA、状态、中断和性能事件接口；
- 寄存器映射、运行期错误检查及软件编程模型。

本文不定义 Queen 指令编码、外部 Scheduler、ForagerBee、LLC、CPU cache 管理实现或系统物理地址映射。

### 1.3 规范性用语

- “必须”表示违反该要求会导致命令被拒绝、结果未定义或与当前 RTL 不一致。
- “应”表示集成或软件应遵循的推荐约束。
- “默认配置”指未显式覆盖 `HiveCoreConfig` 参数时的配置。
- `T` 表示阵列输出列数 `totalN = arrayN × clusterM`；默认 `T=16`。
- `Nlogical` 表示软件需要的有效列数，`Nprog` 表示写入 N 寄存器并按 T 补齐后的列数；除明确写出 `Nlogical` 外，后续公式中的 `N` 均指编程值 `Nprog`。
- 所有地址均为字节地址。
- 除非另有说明，多字节数据采用低地址存放低有效字节的布局。

## 2. HiveCore 概述

HiveCore 是面向矩阵乘法和量化推理的 weight-stationary GEMM 单元，计算：

```text
C = Epilogue(A × B + Bias)
```

A 的逻辑形状为 `M×K`，B 的有效形状为 `K×Nlogical`。由于硬件要求 N 维按阵列宽度补齐，实际编程和存储形状为 B=`K×Nprog`、C=`M×Nprog`。阵列以 K 和 N 作为空间轴，以 M 作为时间轴；每个 `(N tile, K pass)` 装载一次权重，并连续处理全部 M 行。

当前默认输入能力为：

- INT8、INT4；
- MXFP8 E4M3、MXFP8 E5M2；
- MXFP4 E2M1；
- 非 MX FP8 E4M3、FP8 E5M2。

当前实现还提供 INT32、INT16、FP32、FP16、BF16 和 MX 格式的输出转换路径。FP8 不支持作为 C 输出格式。

### 2.1 默认编译期配置

| 参数 | 默认值 | 含义 |
|---|---:|---|
| `arrayN` | 8 | 每个 cluster 的 N 方向宽度 |
| `clusterM` | 2 | cluster 数 |
| `totalN` | 16 | `arrayN × clusterM` |
| `aW` | 16 bit | 每个 K-lane 的 A 输入打包宽度 |
| `bW` | 16 bit | 每个 PE 的 B 权重打包宽度 |
| `cW` | 0 | 自动派生累加宽度 |
| `aBufferDepth` | 64 | A FIFO 深度 |
| `bBufferDepth` | 64 | B FIFO 深度 |
| `cBufferDepth` | 64 | C/partial-sum FIFO 深度 |
| `addrWidth` | 32 | DMA 地址宽度 |
| `scaleBufferDepth` | 64 | scale FIFO 深度 |
| `cDmaExtW` | 256 bit | C 写 DMA 数据宽度 |

默认 `supportedFmts` 为 `INT8、INT4、MXE4M3、MXE5M2、MXE2M1、FP8E4M3、FP8E5M2`。

### 2.2 编译期约束

配置必须满足：

- `arrayN ≥ 4`，`clusterM ≥ 1`；
- `aBufferDepth > 0`，`bBufferDepth > 0`；
- `cBufferDepth ≥ 2T`；
- `0 < addrWidth ≤ 32`；
- `scaleBufferDepth > 0`；
- `cDmaExtW` 为 2 的幂，位于 `[8T, 32T]`，并整除 `32T`；
- 含 MX 格式时必须满足 `T=16`、`aW≥16`、`bW≥16`；
- 含 FP8 时必须同时生成 MX 数据通路；
- MX 数据通路必须同时包含至少一种 MXFP8（E4M3 或 E5M2）和 MXFP4 E2M1；fp8-only 与 fp4-only 的 MX 编译配置均不合法；
- 浮点/MX 累加有效宽度不得小于 40 bit；
- 整数累加有效宽度不得小于 `aW+bW`。

当前发布配置均包含 MX 硬件。纯无 MX 的参数化实例不属于本文保证的交付配置。

## 3. 计算架构

### 3.1 阵列组织

阵列包含 `T×T` 个 PE：

- x 方向对应 K-lane；
- y 方向对应输出 N 列；
- A 数据及 ScaleA 沿 y 方向传播；
- partial sum 沿 x 方向归约；
- B 权重锁存在 PE 内。

默认 `T=16`。每个阵列有效计算拍同时产生 T 个输出列的部分结果。

### 3.2 循环顺序

硬件执行顺序固定为：

```text
for nt in N tiles:
  for kt in K passes:
    load B tile
    for m in 0 until M:
      consume one packed A row beat
```

因此外层循环为 N，中层为 K，M 作为每个权重窗口内的连续时间流。

```text
nTiles = ceil(N / T)
kPerBeat = T × elemsPerLane(aFmt)
kTiles = K / kPerBeat       // 当前默认输入格式要求整除
```

其中：

| A 格式 | `elemsPerLane` | 默认 T=16 时 `kPerBeat` |
|---|---:|---:|
| INT4、MXFP4 | 4 | 64 |
| INT8、MXFP8、FP8 | 2 | 32 |
| 其它单元素格式 | 1 | 16 |

M 不在 Executor 内再次切 tile。单次命令必须满足：

```text
M ≤ cBufferDepth - T
```

默认 `cBufferDepth=64、T=16` 时，单命令 `M≤48`。软件可通过多条命令对更大的 M 分块。

### 3.3 权重驻留

每个 `(nt,kt)` 先消费 T 个 B 拍完成权重装载，然后连续消费 M 个 A 拍。相同权重在该窗口内供全部 M 行复用。

- `loadWMode=0`：B 通过垂直 partial-sum 链装载；
- `loadWMode=1`：B 通过水平 A 链移位装载，并在最后一拍锁存。

MX、FP8 以及正向 INT W4A8 必须使用 `loadWMode=1`。

### 3.4 多 K Pass 累加

- `kt=0`：partial-sum 基底为 0，或在开启 bias 时为对应输出列的 bias；
- `kt>0`：上一 K pass 的结果从 C FIFO 回灌；
- 只有最后一个 K pass 的结果进入输出转换和 C DMA；
- 中间 K pass 结果只在内部 C FIFO 中周转，不写回外部存储器。

### 3.5 N 维补齐

硬件以完整 T 列为单位读取 B 和写回 C，不对最后一个 N tile 自动缩窄 byte strobe。C 行步长又直接由编程值 `N` 计算，因此仅在缓冲区末尾追加空间不能阻止最后一个 tile 跨入下一行。

软件必须把写入寄存器的 `N` 向上补齐为 T 的整数倍：

```text
Nprog = ceil(Nlogical / T) × T
```

A×B、C、bias、requant 参数和相关 scale 布局均按 `Nprog` 构造，逻辑列数 `Nlogical` 由软件在 HiveCore 之外保存。补齐列必须具有可访问的 B/参数数据，C 缓冲区也必须按每行 `Nprog` 个元素分配。

## 4. 数值格式

### 4.1 格式编码

| 编码 | 格式 | 元素宽度 | 默认 A/B 支持 | C 输出路径 |
|---:|---|---:|:---:|:---:|
| 0 | FP32 | 32 | 否 | 是 |
| 1 | FP16 | 16 | 否 | 是 |
| 2 | BF16 | 16 | 否 | 是 |
| 3 | MXFP8 E4M3 | 8 | 是 | 是 |
| 4 | MXFP8 E5M2 | 8 | 是 | 是 |
| 5 | MXFP4 E2M1 | 4 | 是 | 是 |
| 6 | INT32 | 32 | 否 | 是 |
| 7 | INT16 | 16 | 否 | 是 |
| 8 | INT8 | 8 | 是 | 是 |
| 9 | INT4 | 4 | 是 | 是 |
| 10 | FP8 E4M3 | 8 | 是 | 否 |
| 11 | FP8 E5M2 | 8 | 是 | 否 |

“默认 A/B 支持”同时受编译期 `supportedFmts` 约束。软件不得使用未生成的数据通路。

### 4.2 Lane 打包

每个 A/B lane 宽度默认为 16 bit：

- 8-bit 格式：bit `[7:0]` 为较小 K，bit `[15:8]` 为较大 K；
- 4-bit 格式：四个 nibble 按 bit `[3:0]、[7:4]、[11:8]、[15:12]` 对应递增 K；
- INT4 使用 4-bit 二补码；
- MXFP4 使用 E2M1 编码。

在外部字节流中，4-bit 元素 `2p` 位于低 nibble，元素 `2p+1` 位于高 nibble。

### 4.3 合法 A/B 组合

A 和 B 必须属于同一计算族：

- 整数与非整数不得混合；
- MX 与非 MX 不得混合；
- FP8 与非 FP8 不得混合。

当前合法组合包括：

| A | B | 状态 |
|---|---|---|
| INT8 | INT8 | 支持 |
| INT4 | INT4 | 支持 |
| INT8 | INT4 | 支持，正向 INT W4A8 |
| INT4 | INT8 | 不支持 |
| MXFP8 E4M3/E5M2 | MXFP8 E4M3/E5M2 | 支持，E4M3/E5M2 可交叉 |
| MXFP4 E2M1 | MXFP4 E2M1 | 支持 |
| MXFP8 E4M3/E5M2 | MXFP4 E2M1 | 支持，正向 MX W4A8 |
| MXFP4 E2M1 | MXFP8 E4M3/E5M2 | 不支持 |
| FP8 E4M3/E5M2 | FP8 E4M3/E5M2 | 支持，E4M3/E5M2 可交叉 |

正向 W4A8 的 A 为 8-bit 激活、B 为 4-bit 权重。

### 4.4 输出族约束

- 整数计算必须输出整数族格式；
- 非整数计算必须输出非整数族格式；
- FP8 不能作为 C 输出格式；
- MX 输出会同时产生 C 数据和 C scale；
- 非 MX 输出不得依赖 C scale。

### 4.5 整数计算

INT8 和 INT4 输入均先符号扩展，再做二补码乘加。整数累加按配置的累加位宽保留低位，溢出绕回。

未开启 requant 时：

- INT32、INT16、INT8、INT4 均先从累加槽取低 32 位形成 `acc32`；`reluOn=1` 时将负 `acc32` 钳为零，再按 C 格式截取低位；
- 不执行饱和；
- `rnd` 字段不影响该路径。

开启 requant 时只允许输出 INT8 或 INT4，结果执行舍入、加 zero-point 和有符号饱和。

### 4.6 FP8 与 MX 计算

非 MX FP8 在输入端复用 MXFP8 数据通路，硬件注入 E8M0 scale `127` 作为单位缩放。FP8 运行仍必须设置 `loadWMode=1`，但不从 ScaleA/ScaleB 存储器读取输入 scale。

MX 输入使用 E8M0 block scale。MXFP8 乘法器有效尾数宽度为 4 bit，MXFP4 为 2 bit。MX 乘法截断位结构上为零，乘法阶段不产生额外舍入。

内部浮点/MX 累加采用 40-bit lazy 表示：

```text
{sign[1], exponent[8], mantissa[31]}
```

默认归约器为窄化多操作数加法器，不应视为 IEEE-754 逐操作 bit-exact 累加。规格只保证硬件定义的归约结果和输出舍入，不保证与软件逐项 FP32 累加完全一致。

### 4.7 特殊值

当前转换采用以下硬件语义：

- MXFP8 和 FP8 次正规输入冲刷为零；
- MXFP4 E2M1 的 `±0.5` 次正规值保留，且该格式没有 Inf/NaN 编码；
- MXFP8/FP8 的 A、B 元素在有效计算拍出现相应 Inf/NaN 保留码会触发硬件断言；
- FP16/BF16（若编译启用）及非整数 FP32 bias 不提供 IEEE Inf/NaN 语义，且 bias 不触发上述断言；软件必须提供有限值；
- E8M0 输入 scale 的 `0xFF` 是 NaN 保留码，软件不得使用；RTL 不对此值产生配置错误或断言；
- 有限输入导致的指数上溢饱和到最大有限指数；
- MX 输出上溢饱和，下溢通常冲刷为零；E2M1 输出额外支持次正规生成。

MXFP4 E2M1 的 `±0.5` 是受支持输入。MXFP8/FP8 元素的 Inf/NaN 保留码、FP16/BF16 输入的 Inf/NaN（若编译启用）、非有限 FP32 bias 以及 E8M0 `0xFF` 不属于受支持输入域；关闭断言后对这些不受支持值的退化结果不构成规范行为。

### 4.8 舍入模式

| 编码 | 模式 | 含义 |
|---:|---|---|
| 0 | RNE | round to nearest, ties to even |
| 1 | RTZ | round toward zero |
| 2 | RDN | round toward negative infinity |
| 3 | RUP | round toward positive infinity |
| 4 | RMM | round to nearest, ties to max magnitude |
| 5–7 | 保留 | 软件不得使用 |

该字段用于浮点/MX 输出转换。整数非 requant 路径忽略该字段。

## 5. MX Scale 规格

### 5.1 Block Size

A、B、C 分别配置 block size：

| 编码 | Block Size |
|---:|---:|
| 0 | 16 elements |
| 1 | 32 elements |

该表表示字段可编码值。A/B 可使用 16 或 32；默认 `T=16` 时，C block 不能大于 T，因此 C 只允许 16，配置 C block=32 会触发 errCode 0。旧的 8/64 档位不可编码。

### 5.2 输入 Scale 编码

每个 MX block 对应一个 8-bit E8M0 scale。ScaleA 和 ScaleB 各使用独立的 32-bit DMA 读口。

Executor 对一拍内各 lane 使用：

```text
scaleIndex = floor(lane × step / blockSize)
```

其中：

- MXFP8：`step=2`；
- MXFP4：`step=4`；
- 正向 MX W4A8 的 B：按 A 的窗口节奏使用 `step=2`。

### 5.3 ScaleA 布局

ScaleA 逻辑形状为：

```text
M × (K / blockA)
```

地址为：

```text
scaleAAddr(m, kt) = aScaleBase
                    + m × (K / blockA)
                    + kt × (kPerBeatA / blockA)
```

每个元素占 1 byte。

### 5.4 ScaleB 布局

ScaleB 逻辑形状为：

```text
N × (K / blockB)
```

B 以 N-major 行组织。定义：

```text
kWindowBEffective = kPerBeatB                  // 同宽 A/B
kWindowBEffective = kPerBeatA                  // 正向 MX W4A8
```

地址为：

```text
scaleBAddr(n, kt) = bScaleBase
                    + n × (K / blockB)
                    + kt × (kWindowBEffective / blockB)
```

默认 `T=16` 的正向 MX W4A8 中 `kWindowBEffective=32`，因此 block16 的步长为 2 byte，block32 的步长为 1 byte。该运行还要求 `K%64=0` 且 `B_ADDR%32=0`。

### 5.5 ScaleC 布局

MX 输出的 ScaleC 逻辑形状为：

```text
M × ceil(Nprog / blockC)
```

地址为：

```text
scaleCAddr(m, nt) = cScaleBase
                    + m × ceil(Nprog / blockC)
                    + floor(nt × T / blockC)
```

每个输出 block 产生一个 E8M0 scale byte。零 block 输出 scale 0；非零 block 根据 block 最大幅值计算并限制在有效 E8M0 范围内。

### 5.6 非对齐 Scale 读取

ScaleA/ScaleB DMA 支持任意字节地址的 32-bit 逻辑读取。非对齐请求由硬件拆成相邻对齐物理读，并在内部拼接。因此软件不需要把每次逻辑 scale 地址强制对齐到 4 byte，但必须保证两个对齐物理读覆盖的地址均可访问。

## 6. 张量内存布局

### 6.1 A 矩阵

A 按 `M×K` row-major 稠密存储：

```text
A[m][k]
```

每个 N tile 都会重新读取对应 A 数据；HiveCore 内部不跨 N tile 缓存 A。

### 6.2 B 矩阵

#### `loadWMode=0`

B 按补齐后的 `K×Nprog` row-major 存储。硬件按 K/N tile 地址跳转读取。

#### `loadWMode=1`

B 必须预转置为 `Nprog×K` row-major：

```text
BT[n][k] = B[k][n]
```

MX、FP8 和正向 INT W4A8 必须采用此布局。

### 6.3 C 矩阵

C 按 `M×Nprog` row-major 写回，其中 `Nprog` 必须是 T 的整数倍。硬件不会缩窄末 tile；软件只消费每行前 `Nlogical` 列。

### 6.4 Bias

Bias 为每个补齐后的输出列提供一个 32-bit 值，共 Nprog 项：

```text
bias[n]
```

每个 N tile 读取 T 个连续 32-bit 字：

```text
biasAddr(nt, i) = biasBase + nt × T × 4 + i × 4
```

整数计算把 bias 解释为有符号 INT32；非整数计算把相同 32-bit 字解释为 FP32。

### 6.5 Requant 乘数

Requant 乘数 `M[n]` 为每个输出列一个有符号 32-bit 定点乘数：

```text
rqMAddr(nt, i) = rqMBase + nt × T × 4 + i × 4
```

bias 和 rqM 共用 ScaleA DMA 物理读口；两者同时启用时，硬件先读取 bias，再读取 rqM。

## 7. 顶层接口

### 7.1 命令接口

`cmd` 为带 ready/valid 握手的输入流。Payload：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `op` | 枚举 | WRITE1、WRITE2、EXECUTE、READ |
| `reg0Addr` | 5 | 第一个寄存器索引 |
| `reg1Addr` | 5 | 第二个寄存器索引 |
| `data0` | 32 | 第一个写数据 |
| `data1` | 32 | 第二个写数据 |
| `pc` | 32 | tracing PC |
| `tag` | 8 | tracing tag |

操作码编码：

| 编码 | 操作 |
|---:|---|
| 0 | WRITE1 |
| 1 | WRITE2 |
| 2 | EXECUTE |
| 3 | READ |

寄存器地址是寄存器索引，不是 MMIO byte offset。

`cmd.ready` 只受未消费响应反压，不会因执行事务在途或 `status.busy=1` 自动阻止新命令。EXECUTE 被接受后，CalcConfig 尚需经过配置阶段，此时 `status.busy` 仍可能为 0。因此，从 EXECUTE 被接受起，直到完成或配置错误已被观察且 `busy=0`，软件只能发送 READ；不得发送 WRITE 或新的 EXECUTE。违约命令可能被接受、被忽略或破坏当前任务，硬件不保证报错。

### 7.2 普通响应接口

`resp` 为带 ready/valid 握手的输出流：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `data` | 32 | READ 返回值，其它命令通常为 0 |
| `err` | 1 | 命令接口级错误 |

当前所有命令响应的 `resp.err` 恒为 0。WRITE 和 EXECUTE 的响应只确认命令已被接口接受；非法运行配置和 DMA 故障不会通过该字段返回。EXECUTE 响应立即返回，不表示 GEMM 已完成；完成与配置错误必须通过 `irq`、STATUS 或 busy 变化观察。

### 7.3 Tracing 响应

`respForTracing` 是无反压 Flow，包含：

| 字段 | 宽度 | 含义 |
|---|---:|---|
| `state` | 2 | 0=start，1=done，3=configuration error |
| `pc` | 32 | EXECUTE 命令携带的 PC |
| `tag` | 8 | EXECUTE 命令携带的 tag |

当前 tracing 状态在首次 EXECUTE 后不会返回初始态。因此只有复位后的第一次执行保证产生配对的 start 与 done/configuration-error 事件；后续执行的 tracing 行为不属于可靠接口契约。软件不得用 tracing 代替 STATUS/irq 完成检测。

### 7.4 DMA 接口

读 DMA 接口：

```text
req  : Core -> Memory, ready/valid, {addr}
rsp  : Memory -> Core, ready/valid, {data, err}
```

写 DMA 接口：

```text
req  : Core -> Memory, ready/valid, {addr, data, strb}
rsp  : Memory -> Core, ready/valid, {err}
```

默认配置端口：

| 接口 | 方向 | 数据宽度 | 用途 |
|---|---|---:|---|
| `dmaA` | 读 | 256 bit | A 数据 |
| `dmaB` | 读 | 256 bit | B 数据 |
| `dmaC` | 写 | 256 bit | C 数据 |
| `dmaScaleA` | 读 | 32 bit | ScaleA、bias、rqM |
| `dmaScaleB` | 读 | 32 bit | ScaleB |
| `dmaCScale` | 写 | 8 bit | ScaleC |

Scale 端口只在编译期包含 MX 硬件时存在。

### 7.5 状态接口

`status` 输出：

| 字段 | 含义 |
|---|---|
| `busy` | Executor 不在 IDLE |
| `done` | 至少完成过一次执行，复位前保持 |
| `err` | 当前/最近一次执行错误状态 |
| `progress` | 已完成的 `(N tile, K pass)` 数 |
| `aOccupancy` | A FIFO 占用 |
| `bOccupancy` | B FIFO 占用 |
| `cOccupancy` | C FIFO 占用 |

`progress` 不表示 M 行数，也不表示写回 byte 数。

### 7.6 中断

`irq` 为电平信号：

```text
irq = doneSinceStart OR errFlag
```

- 一次执行完成或发生错误后置高；
- 下一条 EXECUTE 被接受时清除本次事件状态；
- `status.done` 不随下一条 EXECUTE 清除，而是保持到复位。

软件处理重复执行时不得仅等待 `status.done==1`，应观察 `irq` 的新一轮变化，或观察 busy 从 1 返回 0。

## 8. 寄存器规格

### 8.1 寄存器索引总表

| 索引 | 名称 | 访问 | 含义 |
|---:|---|---|---|
| `0x00` | M | RW | M 维度 |
| `0x01` | N_K | RW | N、K 维度 |
| `0x02` | A_ADDR | RW | A 基地址 |
| `0x03` | B_ADDR | RW | B 基地址 |
| `0x04` | C_ADDR | RW | C 基地址 |
| `0x05` | CONTROL | RW | 格式和控制位 |
| `0x06` | RESERVED | — | 读 0，写无效 |
| `0x07` | RESERVED | — | 读 0，写无效 |
| `0x08` | STATUS | RO | 状态和错误码 |
| `0x09` | SCALE_A_ADDR | RW | ScaleA 基地址 |
| `0x0A` | SCALE_B_ADDR | RW | ScaleB 基地址 |
| `0x0B` | SCALE_C_ADDR | RW | ScaleC 基地址 |
| `0x0C` | BIAS_ADDR | RW | Bias 基地址 |
| `0x0D` | RQ_M_ADDR | RW | Requant 乘数基地址 |
| `0x0E` | RQ_PARAM | RW | Zero-point 和 shift |

### 8.2 M 与 N/K

`M`：

| Bits | 字段 |
|---|---|
| `[15:0]` | M |
| `[31:16]` | 读 0 |

`N_K`：

| Bits | 字段 |
|---|---|
| `[15:0]` | N |
| `[31:16]` | K |

M、N、K 均为 16-bit 无符号值。

### 8.3 CONTROL

| Bits | 字段 | 含义 |
|---|---|---|
| `[3:0]` | `aFmt` | A 格式 |
| `[7:4]` | `bFmt` | B 格式 |
| `[11:8]` | `cFmt` | C 格式 |
| `[12]` | `aAddrMode` | 0=MAT，1=TILE |
| `[13]` | `bAddrMode` | 0=MAT，1=TILE |
| `[14]` | `cAddrMode` | 0=MAT，1=TILE |
| `[15]` | `reluOn` | ReLU 使能 |
| `[18:16]` | `rnd` | 舍入模式 |
| `[19]` | Reserved | 写入被忽略 |
| `[20]` | `warmdown` | 当前数据通路未消费，仅保留读写 |
| `[21]` | `warmup` | 当前数据通路未消费，仅保留读写 |
| `[22]` | `aMxBlockSize` | 0=16，1=32 |
| `[23]` | Reserved | 写入被忽略 |
| `[24]` | `bMxBlockSize` | 0=16，1=32 |
| `[25]` | Reserved | 写入被忽略 |
| `[26]` | `cMxBlockSize` | 0=16，1=32 |
| `[27]` | Reserved | 写入被忽略 |
| `[28]` | `loadWMode` | B 装载模式 |
| `[29]` | `requantOn` | Requant 使能 |
| `[30]` | `biasOn` | Bias 使能 |
| `[31]` | Reserved | 写入被忽略 |

`AddrMode` 编码定义为 MAT/TILE，但当前主数据通路没有消费这三个字段；软件应写 0，不能依赖 TILE 模式改变 DMA 地址行为。

### 8.4 STATUS

| Bits | 字段 | 含义 |
|---|---|---|
| `[0]` | `busy` | Executor 非 IDLE |
| `[1]` | `done` | 粘滞完成标志 |
| `[2]` | `err` | 错误标志 |
| `[6:3]` | `errCode` | 配置错误码 |
| `[15:7]` | Reserved | 0 |
| `[31:16]` | `progress` | 已完成 `(N,K)` pass 数 |

STATUS 写入无效。

### 8.5 RQ_PARAM

| Bits | 字段 | 含义 |
|---|---|---|
| `[5:0]` | `shift` | 右移位数 |
| `[15:6]` | Reserved | 0 |
| `[31:16]` | `zeroPoint` | 有符号 16-bit zero-point |

## 9. DMA 行为

### 9.1 A 读取

A DMA 遍历顺序为 N tile、K pass、M 行。每个 N tile 都重新读取完整 A。逻辑地址为：

```text
A_BASE + kt × aKStride + m × aRowStride
```

具体 stride 根据格式的元素位宽和 `kPerBeat` 派生。

### 9.2 B 读取

B DMA 遍历 N tile 和 K pass。每个窗口读取 T 个逻辑拍并按阵列装载顺序送入 Executor。

W4A8 使用 stretch 读取：一个 32-byte 对齐物理字包含两个 16-byte 4-bit 半片，硬件根据 K 窗口选择低半或高半。软件必须满足对应的 B 基址对齐要求。

### 9.3 非对齐 Gather

A、B、ScaleA 和 ScaleB 读 DMA 支持非对齐逻辑地址：

1. 将逻辑地址向下对齐到端口字宽；
2. 必要时读取当前字和下一字；
3. 拼接并右移，形成从原逻辑地址开始的完整逻辑拍。

每个逻辑拍只占用一个目标 FIFO credit。外部存储必须允许硬件访问 gather 覆盖的两个对齐字。32-bit scale 读取还可能触及当前计算不使用的相邻字节，这些字节也必须可安全读取。

### 9.4 读侧 Credit

读请求发出时即预留 FIFO credit；响应到达并进入 FIFO 后，直到该项被消费才返还 credit。读接口没有 transaction ID，gather/stretch 状态机按请求顺序配对响应。外部必须严格按请求顺序返回读响应；每个已接受的读请求必须且只能返回一个响应，不得产生无请求响应。

### 9.5 C 写回

C DMA 按 N tile、M 行写回。默认 `cDmaExtW=256`：

| C 格式位宽 | 每个 T 列行的有效字节 | 写拍数 |
|---:|---:|---:|
| 4 | 8 | 1 |
| 8 | 16 | 1 |
| 16 | 32 | 1 |
| 32 | 64 | 2 |

`data` 和 `strb` 使用同一个 `addr mod dmaBytes` 左移量。C 基地址必须按 `cDmaExtW/8` 对齐；默认要求 32-byte 对齐。

写请求可被 `req.ready` 反压。硬件在一整行最后一拍被接受后才弹出对应 C FIFO 项。执行完成还会等待全部 C 写响应返回。外部必须为每个已接受的写请求返回且只返回一个响应，不得产生无请求响应。

### 9.6 ScaleC 写回

ScaleC 通过独立 8-bit 写接口输出。当前实现的 ScaleC FIFO/在途控制深度为 2，但 FIFO pop 的 ready 未被“在途已满”条件门控。集成环境必须保证在 `bufPop.valid` 有效时未完成 ScaleC 写请求数不会达到 2；否则当前 RTL 可能在没有真正发出请求时弹出并丢失一个 scale。最安全的集成方式是对 ScaleC 写请求提供无停顿 ready，并在零或一拍内返回响应。

### 9.7 DMA 错误

DMA 错误不是 fail-fast：通道会锁存响应错误并继续排空，已经发出的事务不会撤销，C 也可能已部分写入。顶层只在 Executor done 拍采样各 DMA 错误状态；A/B/Scale 读 DMA 又会在各自返回 IDLE 时清除本地错误，因此部分读错误可能在顶层采样前丢失。

当前实现不能保证所有读 DMA 错误都反映到 STATUS.err/irq。被采样到的 DMA 错误不会分配专用 errCode；`errCode` 只描述配置检查。系统集成不得把 STATUS.err 当作完整的内存故障检测机制。

## 10. 执行流程

### 10.1 状态机

Executor 包含五个状态：

```text
IDLE -> DRAIN -> LOAD_B -> LOAD_A -> NEXT_NK_TILE -> ... -> DRAIN -> IDLE
```

#### IDLE

- 清除运行游标；
- 计算 `nTiles` 和 `kTiles`；
- 接受有效 EXECUTE 后 flush A/B/C 工作 FIFO；
- 设置首个窗口待执行并进入 DRAIN。

#### DRAIN

- 等待上一波前排空；
- 等待 B FIFO 至少有 T 拍；
- MX B 还要求 ScaleB FIFO 至少有 T 拍；
- 满足条件后进入 LOAD_B；
- 最终窗口完成后，等待阵列输出无效且 C DMA 空闲，再产生 done 脉冲。

#### LOAD_B

- 每次 `bPop.fire` 消费一个 B 拍；
- 连续 T 个有效拍完成权重装载；
- 最后一拍锁存权重并进入 LOAD_A。

B 装载节拍不得在一个窗口中断裂；入口预蓄门控用于保证完整窗口供数。

#### LOAD_A

- `aPop.fire` 时阵列消费一个 M 行拍；
- `curM` 只在真实 fire 时递增；
- 消费第 M 个有效拍后进入 NEXT_NK_TILE。

#### NEXT_NK_TILE

- 有下一 K pass：保存 partial sum，K 游标加一；
- 当前 N tile 的最后 K pass：打开最终写回门；
- 有下一 N tile：N 游标加一；
- 无下一 tile：转入最终排空。

### 10.2 完成行为

完成条件为：

```text
无下一窗口
AND 阵列无有效输出
AND C DMA 不忙
```

满足时：

- Executor `done` 输出一拍脉冲；
- `status.done` 置位并保持到复位；
- `irq` 置高；
- 若这是复位后的首次执行，tracing 输出 done 事件；
- Executor 返回 IDLE。

### 10.3 配置错误行为

EXECUTE 时先检查运行期配置。若配置非法：

- 不启动正常 GEMM；
- STATUS.err 置位；
- STATUS.errCode 给出最高优先级错误；
- irq 置高；
- 若这是复位后的首次执行，tracing 输出 configuration error。

## 11. Epilogue

### 11.1 执行顺序

逻辑顺序为：

```text
accWide = A × B + bias                          // 内部累加宽度
acc32   = signed(accWide mod 2^32)              // INT epilogue 固定先取低32位
relu32  = max(acc32, 0)                         // reluOn，否则等于acc32
q       = round(relu32 × M / 2^shift) + zeroPoint
out     = saturate(q)                           // requantOn
out     = truncate(acc32/relu32)                // requantOn=0 的整数输出
```

非整数输出不经过 `acc32`、ReLU 或 requant。

### 11.2 Bias

Bias 每个输出列一个值，只在 `kt=0` 注入。后续 K pass 回灌的 partial sum 已包含 bias，因此不会重复相加。

- 整数路径：bias 按 INT32 符号扩展；
- 非整数路径：bias 按 FP32 解码后转为内部 lazy 格式；FP32 指数为 0 的值按零处理。

`biasOn=1` 时：

- `BIAS_ADDR` 必须 4-byte 对齐；
- 编译配置必须包含用于参数取数的 MX/scale DMA 硬件。

### 11.3 ReLU

ReLU 只作用于整数输出路径。硬件先把宽累加槽截为有符号 `acc32`，再在 requant 或最终整数截断前把负 `acc32` 钳为零。非整数输出忽略 `reluOn`。

### 11.4 Requant

Requant 只允许 INT8 或 INT4 输出。其输入是已经截为低 32 位并按 `reluOn` 可选钳零的 `acc32/relu32`，不是完整宽累加值。每个输出列使用独立有符号 32-bit 乘数 `M[n]`，全命令共享 `shift` 和有符号 16-bit `zeroPoint`。

当 `shift>0` 时：

```text
product = relu32 × M[n]
rounded = ((product >> (shift-1)) + 1) >> 1
```

随后：

```text
shifted = rounded + zeroPoint
```

最后饱和到：

- INT8：`[-128, 127]`；
- INT4：`[-8, 7]`。

当 `shift=0` 时不执行右移舍入。

`requantOn=1` 时：

- C 格式必须为 INT8 或 INT4；
- `RQ_M_ADDR` 必须 4-byte 对齐；
- 编译配置必须包含参数取数 DMA。

## 12. 配置检查与错误码

### 12.1 错误码表

| errCode | 判据摘要 |
|---:|---|
| 0 | MX block size 大于对应 K 窗口，或 C block size 大于 T |
| 1 | 冻结，无生产者 |
| 2 | 冻结，无生产者 |
| 3 | 不支持的格式组合，包括 MX 单边、反向 W4A8、不支持的 FP8 组合或 FP8 输出 |
| 4 | MX/FP8 未设置 `loadWMode=1` |
| 5 | A 或 B 使用 FP32 |
| 6 | 无 RTL 生产者 |
| 7 | `M > cBufferDepth-T` |
| 8 | K 粒度、K=0 或正向 W4A8 的 K/B 对齐违约 |
| 9 | 保留，无生产者 |
| 10 | C 基地址未按 C DMA 字宽对齐 |
| 11 | 计算域与 C 输出的整数/非整数族不一致 |
| 12 | Bias 配置非法 |
| 13 | Requant 配置非法 |
| 14 | 保留，无生产者 |
| 15 | 保留，无生产者 |

### 12.2 主要判据

#### errCode 0

- MX A block 大于 A 的 `kPerBeat`；
- MX B block 大于 B 的 `kPerBeat`；
- MX C block 大于 T。

#### errCode 3

包括但不限于：

- A/B 只有一侧为 MX；
- A=MXFP4、B=MXFP8；
- A=INT4、B=INT8；
- FP8 与非 FP8 混合；
- C 使用 FP8。

#### errCode 8

- INT8、MXFP8、FP8：`K` 必须为 `2T` 的整数倍；
- INT4、MXFP4：`K` 必须为 `4T` 的整数倍；
- `K=0` 非法；
- 正向 W4A8 按 4-bit B 的几何要求执行，并附加 B 地址对齐检查；
- 正向 INT W4A8 必须设置 `loadWMode=1`。

#### errCode 12

- `biasOn=1` 且 BIAS_ADDR 非 4-byte 对齐；
- `biasOn=1` 但编译配置不含参数取数硬件。

#### errCode 13

- `requantOn=1` 且 C 不是 INT8/INT4；
- RQ_M_ADDR 非 4-byte 对齐；
- 编译配置不含参数取数硬件。

### 12.3 错误优先级

多个错误同时成立时，RTL 的最终优先级从高到低为：

```text
3(late format guards)
> 11
> 8
> 13
> 12
> 10
> 7
> 5
> 4
> 3(MX one-sided)
> 0
```

软件不得假设 errCode 会报告最先出现或数值最小的错误。

### 12.4 非错误码约束

以下条件不会自动产生专用 errCode，软件仍必须满足：

- `M≥1`、`N≥1`；M/N 为 0 会使内部减一计数回绕，可能越界运行或挂死；
- 编程值 `N=Nprog` 且为 T 的整数倍；
- A/B/C 和参数缓冲区实际可访问范围；
- EXECUTE 在途时（包括 `busy` 尚未拉高的配置阶段）禁止 WRITE/EXECUTE；
- 保留字段写 0；
- 使用编译配置真正生成的 A/B 格式；
- 外部 DMA 严格同序并对每个请求返回一个且仅一个响应。

## 13. 性能事件

`perf` 只输出组合事件电平，不包含硬件计数器。外部 PMU 应在每个时钟采样并累加。

| 事件 | 定义 |
|---|---|
| `dmaA` | A DMA `req.valid` |
| `dmaB` | B DMA `req.valid` |
| `dmaC` | C DMA `req.valid` |
| `scaleA` | ScaleA 物理口 `req.valid`，包含 bias/rqM 取数 |
| `scaleB` | ScaleB DMA `req.valid` |
| `scaleC` | ScaleC DMA `req.valid` |
| `array` | 阵列输入有效，即 LOAD_A 的 `aPop.fire` |
| `stallA` | LOAD_A 中没有发生 `aPop.fire` |
| `stallB` | LOAD_B 中没有 `bPop.fire`，或预蓄 B/ScaleB 不足 |
| `epilogue` | bias/rqM 取数活动，或输出转换输入/输出/scale 有效 |
| `busy` | Executor 非 IDLE |

DMA 事件使用 `req.valid` 而不是 `req.fire`。当外部 `ready=0` 时，同一请求可令事件连续多个周期为 1；外部 PMU 若要统计事务数，应额外结合对应 ready 信号。

## 14. 软件编程模型

### 14.1 基本流程

1. 校验 `M≥1、Nlogical≥1、K≥1`，计算 `Nprog=ceil(Nlogical/T)×T`；
2. 按 `Nprog` 准备 A、B、C 和可选 scale/bias/requant 缓冲区，并完成 B 转置、4-bit 打包和补齐列；
3. 写入 M/Nprog/K、A/B/C 地址和 CONTROL；
4. MX 运行写入 ScaleA/ScaleB/ScaleC 地址；
5. bias 运行写入 BIAS_ADDR；
6. requant 运行写入 RQ_M_ADDR 和 RQ_PARAM；
7. 提交 EXECUTE 并接收立即 ACK；
8. 等待 irq，或观察 busy 从 1 变为 0；
9. 读取 STATUS；若 err=1，按 errCode 诊断配置错误；
10. 确认输出存储和 cache 一致性后消费 C。

### 14.2 重复执行

`status.done` 在第一次成功完成后保持到复位，不能用来区分后续执行。重复执行建议：

1. 确认上一条命令已结束；
2. 提交下一条 EXECUTE；该动作清除事件 irq 状态；
3. 观察 irq 再次拉高，或观察 busy 的新一轮 `0→1→0`；
4. 读取 STATUS.err 和 errCode。

### 14.3 地址与缓冲区要求

软件必须保证：

- C 基地址按 `cDmaExtW/8` 对齐；
- bias/rqM 基地址按 4 byte 对齐；
- 正向 W4A8 的 B 基地址满足错误检查要求；
- gather 可能访问的相邻对齐字均位于合法存储区；
- B、C、bias、rqM 和 scale 均按 `Nprog` 提供完整补齐列；
- EXECUTE 在途时（包括 `busy` 尚未拉高的配置阶段）不修改配置寄存器、不提交新的 EXECUTE；
- 输出写回完成前，不回收或覆盖任何输入、参数和输出缓冲区。

### 14.4 Cache 与一致性

HiveCore 接口本身不定义 cache coherence。若系统 DMA 与 CPU cache 不一致，软件必须在 EXECUTE 前 clean 输入和参数缓冲区，并在完成后 invalidate 输出缓冲区；具体操作由 SoC 平台定义。

## 附录 A：关键公式

```text
T            = arrayN × clusterM
nTiles       = ceil(N / T)
elemsPerLane = 4 for INT4/MXFP4
               2 for INT8/MXFP8/FP8
               1 otherwise
kPerBeat     = T × elemsPerLane
kTiles       = K / kPerBeat
Mmax         = cBufferDepth - T
```

MX 输入 scale：

```text
ScaleA[m, kt] = SA_BASE + m×(K/blockA) + kt×(kPerBeatA/blockA)
ScaleB[n, kt] = SB_BASE + n×(K/blockB) + kt×(kWindowBEffective/blockB)
scaleIndex    = floor(lane×step/block)

kWindowBEffective = kPerBeatA for forward MX W4A8
                    kPerBeatB otherwise
```

MX 输出 scale：

```text
ScaleC[m, nt] = SC_BASE + m×ceil(Nprog/blockC) + floor(nt×T/blockC)
```

## 附录 B：当前实现限制摘要

- 当前发布配置固定 MX 阵列几何 `T=16`；
- 单命令 M 受 C buffer 容量限制；
- `M/N` 必须大于 0，但 RTL 没有专用错误码；
- 编程值 N 必须补齐为 T 的整数倍，硬件不自动缩窄末 tile；
- FP8 不能直接作为 C 输出；
- 默认配置的 C block 只允许 16；
- W4A8 只支持 A8×B4，不支持 A4×B8；
- `AddrMode`、`warmup`、`warmdown` 当前仅可读写，不改变数据通路；
- EXECUTE 在途时（包括配置阶段）写寄存器或重复 EXECUTE 不受硬件阻止；
- EXECUTE 普通响应为立即 ACK，且 `resp.err` 恒为 0；
- `status.done` 只能由复位清除；
- tracing 只保证复位后的首次执行；
- 部分读 DMA 错误可能在顶层采样前丢失；
- perf DMA 事件统计 valid 周期，不等价于事务数；
- ScaleC 写响应不得无限延迟。
