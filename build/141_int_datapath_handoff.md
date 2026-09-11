# #141 INT 族数据通路缺陷 — 用户手动修复交接笔记

> 只读取证产出。不含裁定、不做实施设计（范围已收窄）。
> 主题：INT8 输入数据路 lane 错位 + INT32/INT4 输入关联缺口。
> 全部行号经本轮静态复核（Chisel 源码 + 金锚 HiveDebugModel + 归档回归日志 124_full_regression_final.log）；未跑 sbt/仿真（让资源给 Rhys #130）。

## 1. 根因链（INT8：地址路密集 1B vs 数据路 lane 低 8 位）

### 1a. 地址路（密集 1 字节/元素，#124 已裁定正确）
- HiveCoreCalcConfig.scala L234-235：INT8 → aRowAddressOffset = k×1
- HiveCoreCalcConfig.scala L284-285：loadW=0 INT8 → bRowAddressOffset = n×1
- HiveCoreCalcConfig.scala L268：aColTileAddressOffset = totalN×elemsPerLane×memElemBytes = 16×1×1 = 16
- HiveCoreCalcConfig.scala L338：bColTileAddressOffset 同式 = 16
- HiveCoreCalcConfig.scala L87-99 memElemBytesOf（INT8→1）、L105-113 elemsPerLaneOf（INT8→1）

### 1b. 数据路（lane 契约：每 16-bit lane 只取低 8 位符号扩展）
- HiveWorkUnit.scala L48-59 HiveCvtOpInt：L55 `pad = io.in(15,0)`；L57-58 INT8 → `Cat(Fill(8,pad(7)), pad(7,0))`，即每 lane 只消费低字节、高字节被丢弃

### 1c. 一拍一行搬运 + 定宽 16-bit 步长切片
- HiveCoreConfig.scala L102/L105：aExtW=bExtW=totalN×16=256 bit=32 字节/拍（格式无关，恒定）
- HiveCoreDmaRdOnly.scala L217-218：`bufPush.payload := dmaExtRdIF.rsp.payload.data`（256b 直推），一行一拍
- HiveCoreExecutor.scala L213-214（A 切片）：`hiveAIn(i) := aPop.payload(aEffW*(i+1)-1, aEffW*i)`，aEffW=16
- HiveCoreExecutor.scala L372-374（B 垂直/psumIn）：`hivePsumIn(i) := bPop.payload(bW*(i+1)-1, bW*i)`
- HiveCoreExecutor.scala L409-414（B 水平 loadW/aIn）：`hiveAIn(i) := bPop.payload(bW*(i+1)-1, bW*i)`

### 1d. 金锚内存布局（密集 1B，权威张量语义）
- HiveDebugModel.scala L543-561 buildMemory：`storeElem(A_BASE + mm*aRowOff + k*aEb, code, aEb)`，INT8 aEb=1 ⇒ 逐字节密集
- HiveDebugModel.scala L564 beatAtA = `mem.loadBits(addr, aExtW=256)` ⇒ DMA 每拍读 32 字节

### 1e. 错位机理（半拍）
INT8 一个 kTile 行 = 16 元素 × 1B = 16 字节 = 128 bit = **半拍**；但 DMA 一拍固定读 32 字节、Executor 按固定 16-bit 步长切 16 lane。于是 lane[i] = beat 的 byte[2i..2i+1]，HiveCvtOpInt 只取低字节 ⇒ **PE i 拿到 byte[2i] 而非语义要求的 byte[i]**。奇数字节整体丢弃、lane[8..15] 落到相邻 kTile 数据 ⇒ 全乱。这正是 G1 出现 hw C(0,8..15) 与 C(0,16..23) 逐元素相同的「8 元素组重复」、以及 mismatch=509/512 的直接来源。地址路本身与金锚逐拍对账全绿（缺陷纯在数据路 lane 分配，不在地址）。

## 2. 影响面清单（死结论：不波及 FP）

判据：一拍 32 字节能否恰好装下「一个 kTile 行 = totalN(16) 元素 × elemsPerLane × memElemBytes」，且每元素落在自己 lane 的低有效位。

| 格式 | elems/lane | 字节/元素 | 一行 kTile | 对齐? | 受影响? |
|---|---|---|---|---|---|
| FP16 / BF16 | 1 | 2 | 16×2=32B=满拍 | lane[i]=elem[i] ✓ | 否 |
| INT16 | 1 | 2 | 32B=满拍 | ✓ | 否 |
| MX8 (E4M3/E5M2) | 2 | 1 | 16lane×2elem×1B=32B | 双字节都被 HiveCvtOpMx 消费（HiveWorkUnit L400-418：io.a(7,0) 与 io.a(15,8)）✓ | 否 |
| **INT8** | 1 | 1 | **16×1=16B=半拍** | lane[i]=byte[2i] ✗ | **是** |
| INT32(输入) | 1 | 4 | 16×4=64B=2拍 | 见 §5 | 是(另一类) |
| INT4(输入) | — | 0.5 | — | 金锚已拒 | 是(另一类) |

### 2a. 死结论（用户最关心：是否波及 FP）
**不波及 FP。** 缺陷只发生在 INT8（以及 INT32/INT4 输入，属独立缺口）。FP16/BF16 与所有 MX 浮点通路零波及——它们 elemsPerLane×memElemBytes=2，一行 16 元素恰占满 32B 一拍，lane[i] 天然等于 elem[i]，HiveCvtOpFp（HiveWorkUnit L66-78）/ HiveCvtOpMx（L92-118）的消费口径与内存密集布局自洽，不存在 INT8 的半拍错位。
证据：
- 归档回归 build/124_full_regression_final.log：多处「C 对比 … mismatch=0」（如 L40/L76/L112/L332/L406），基线 83 跑 80 绿；仅 3 红为 QuantSpec blk32-over-totalN（errCode 域，#140）与 dualMX K=64 blk16（#127），均与 INT8 数据路无关。
- #124 恒等性证明（HiveCoreCalcConfig.scala L256-264、L320-327）已列 MX8/FP16/BF16/INT16 的 colTile 新旧同值=32，改动只在 INT8/INT32 两个此前零覆盖区间生效。
- 结构面：整数/浮点/MX 三条累加通路在 HiveWorkUnit 内物理分离（sumInt L358-370 / sumFp L376-389 / sumMx L398-432），INT8 的 lane 错位只污染 sumInt 支路的输入切片，不触碰 FP/MX 支路。

## 3. 候选插入点（不裁定，交用户选）
紧凑重排目标：INT8 时把 beat 低 128b 的 byte[i] 送到 lane[i] 的低 8 位（高 8 位随意，HiveCvtOpInt 只取低字节）。

- (a) Scratchpad 出口（HiveCoreScratchpad.scala aPop L64 / bPop L75 payload）：改动集中在存储边界；但 Scratchpad 已被 P2 定位为「纯存储」（见文件头 L50-51 注释），在此加格式感知重排违背其职责、且需新引入 fmt 端口。
- (b) Executor 入口（HiveCoreExecutor.scala 三处切片 L213-214 / L372-374 / L409-414）：改动量最小、最贴近既有 aEffW/bW 定宽步长假设；可先按 fmt 生成一个 repacked payload（INT8 时 byte[i]→16b lane[i]）再走原切片。风险：三处都要覆盖（A、B 垂直、B 水平 loadW），且门控必须精确 `== INT8`（切勿用 DataFormat.is1B，它含 MX8 会误伤）。
- (c) HiveCvtOpInt 内部取位（HiveWorkUnit.scala L48-59）：单靠此处无法修——该模块每实例只见 1 个 16-bit lane、无跨 lane 视野；INT8 的奇数元素落在相邻 lane 的高字节，per-lane 取位物理上取不到 byte[i]。仅改此处不能把 byte[i] 还原到 lane[i]。

## 4. 修复红线
- 任何改法必须对 FP16/BF16/INT16/MX8 **零影响**：这些格式 elemsPerLane×memElemBytes=2 ⇒ 一行 16 元素恰占满 32B 一拍 ⇒ lane[i] 天然=elem[i]；重排逻辑必须被 fmt 门控短路（gate 精确等于 INT8，非 INT8 走原路径 bit-exact）。
- 金锚 elemsPerLane(INT8)=1（HiveDebugModel.scala L69）**已裁定正确、不要动**；buildMemory 密集 1B 布局是权威张量语义，**禁止**改成 2B/lane 填充来迁就坏 RTL（本项目 dmaC 金锚反向拟合惨痛教训）。
- #124 刚落地的 memElemBytesOf/elemsPerLaneOf（HiveCoreCalcConfig.scala L87-113）与 aColTileAddressOffset(INT8)=16、bColTileAddressOffset(INT8)=16（L268/L338）**已裁定正确、不要动**。地址路无需改；缺陷纯在数据路 lane 分配。

## 5. 关联缺口（只登记事实，不设计）
- HiveWorkUnit.scala L442 `isInt = io.aFmt===INT8 || io.aFmt===INT16` 缺 INT32/INT4 ⇒ 二者作输入时 L443 Mux 落到 sumFp 浮点累加器（HiveCvtOpFp 把 INT32 当 fp16 解码），端到端不可用。注意：即便把 INT32/INT4 加进 isInt 也修不好——数据路本身不支持。
- INT32 输入：一个 kTile 行 = 16×4B = 64B = **2 拍**，且 32-bit 元素 > 16-bit lane（aEffW=bW=16），单元素装不进一个 lane，是 INT8「半拍」的**镜像问题（2 拍 + 元素超 lane 宽）**。地址路 #124 已给 colTile=64，但数据路 / lane 宽度 / 拍数全不支持。
- INT4 输入：半字节，memElemBytes=0，金锚 validate（HiveDebugModel.scala L254-257）已 require 拒绝作输入；colTile 退化为 0。（INT4 作**输出**是另一回事，已单独支持，勿混淆。）
- HiveEnum.scala L16 `isInt = INT8||INT16||INT4` 与 HiveWorkUnit L442 局部 isInt（仅 INT8||INT16）口径不一致，属文档/一致性瑕疵。
- supportedFmts（HiveCoreConfig.scala L47）默认只含 FP16/BF16/INT16/INT8/MXE4M3/MXE5M2，INT32/INT4/FP32 本就不在其中——收口（errCode 拒绝非法输入格式）按用户指令推迟到 #140。

## 6. 护栏恢复提醒
#124 新增护栏 G1/G2（INT8→INT32）当前 C 数据 mismatch 断言被**刻意弱化为「只打印不硬断言」**（为把既存数据路缺陷与地址改动隔离）。用户手动修完 INT8 数据路后，应把 G1/G2 的 C 数据断言**恢复为硬断言**，否则修复没有牙齿。当前基线：G1 mismatch=509/512、G2 mismatch=254/256（六通道地址逐拍对账已全绿，说明地址路 OK、待修的纯是数据路）。
