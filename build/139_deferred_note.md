# #139 归档：取消（deferred），不修复

> 状态：**已取消 / 遗留（deferred）**。本文件是 build/ 下的笔记，**不是源码改动**。
> #139 的阶段 A（只读取证）在收到停止指令时中止，阶段 B 未启动。
> 全程**未修改任何 RTL / 测试 / 金锚 / 注释**，**未运行 sbt**（资源留给 #130 Rhys、#141 Rhea）。
> 以下内容按指令**转抄归档**，未做新的推导或新增取证。

---

## ① 用户裁定原文要点

- **非整数对齐问题当前不考虑：软件层已限制输入必须对齐。**
- 该项作为**遗留问题以后再说**，本轮不做任何修复。
- 配套的最高优先级口径：**只关心正常配置**；errCode / 非法配置校验一律推迟到 #140。

裁定结果：#139 **不修 RTL、不加校验、不改金锚、不动注释**。将来若重启，需重新取得授权。

---

## ② Vance 在 #124 报告 §5 已给出的取证结论（转抄）

### 2.1 残余偏差一般式

#124 把 `HiveCoreCalcConfig` 的 a/bColTileAddressOffset 从编译期常数 `totalN*(w/8)`
改为运行期式 `totalN × elemsPerLane × memElemBytes`（新增 `memElemBytesOf` / `elemsPerLaneOf`
两个解码函数，逐字镜像金锚 `HiveDebugModel.memElemBytes` L62-66 / `elemsPerLane` L69）。

改后证明：n-tile 跳转偏差 Δ(nt,kt) 的 **kt 维分量恒归零**，但 **nt 维第一项仍非零**：

- **一般式：term1 = nt × bEb × pad**
  其中 `pad = ceil(k/bKpb)×bKpb − k` = 把 K 补齐到 bKpb 整数倍的填充量。
- **触发条件：loadWMode=1 ∧ 非 MX ∧ K % bKpb ≠ 0 ∧ nTiles ≥ 2**
  - loadW=0 时 Δ≡0（已代数验证）；
  - MX 用整除 `k/bKpb` ⇒ pad=0 ⇒ 恒不触发。

### 2.2 最小可复现配置

| 项 | 值 |
|---|---|
| m | 16 |
| n | 32 |
| k | 24 |
| aFmt / bFmt | FP16(1) |
| cFmt | FP32(0) |
| addrMode | MAT(0) |
| rnd | RNE(0) |
| **loadWMode** | **true(1)** |
| **strictAddr** | **true（必需，否则缺陷隐形）** |
| aMxBlkSel / bMxBlkSel | 2(M32)，非 MX 下硬件不消费的占位 |
| cMxBlkSel | 0(M8)，同上占位 |

### 2.3 偏差量与越界事实（nt=1，nTiles=2）

- `term1 = +16 字节`。
- HW（`lastBTileStartAddr` 累加模型）：
  - nt=1, kt=0 块基址 = `0x1005E0`；语义正确值 = `0x1005D0`
  - nt=1, kt=1 块基址 = `0x100600`；语义正确值 = `0x1005F0`
- 受影响拍 = dmaB beat#32..#63，**首现 beat#32**。
- **HW 最高地址 `0x100600` 已越出 B 区**：loadW=1 下 B 为 N×K = 32×24×2 = 1536 字节
  ⇒ 合法区间 `[0x100000, 0x100600)`。

### 2.4 金锚侧取证结论：金锚未与 RTL 同源共享该错，**金锚可作权威**

- 金锚 `expBAddrSeq`（`HiveDebugModel` L467）用**语义正确的闭式**：
  `B_BASE + nt*bNTileOff + (totalN-1)*bRowOff + kt*bKTileOff`
- RTL `HiveCoreDmaRdOnly` 用 **`lastBTileStartAddr` 累加模型**
  （loadW=1 支 = `base + (nt+1)*(T-1)*R + (nt*kT+kt)*C`）
- ⇒ 两者**不同源**，金锚可作权威。这**不是** dmaC 那种「金锚按坏 RTL 反推闭式」的假绿模式
  （#132/#133 的教训），所以加一条 `strictAddr=true` 的护栏就能抓到。

### 2.5 零覆盖已确认

全仓 loadW=1 的活跃用例 K ∈ {16, 32, 64} 全是 bKpb 整数倍 ⇒ pad=0，
故该缺陷**从未被覆盖到**。

### 2.6 相关留痕位置（供将来定位，勿在此重开）

- `HiveCoreDmaRdOnly.scala` 头注释【#124 ① 补记】★ 残余偏差段（约 L94-97）
- `HiveCoreCalcConfig.scala` bColTileAddressOffset 注释「误差解析（#132 推导式）」nt 维分量段（约 L328-334）
- `HiveDebugModel.scala` bKTileOff 注释「仍存在的残余偏差（#124 ① 未覆盖、已由 #139 承接）」（约 L412-414）
- `HiveCoreParamDebugSpec.scala` Section E【E2】★ 残余段（约 L73-75）与 detect() 内对应注释（约 L298-299）

> 注：上述四处留痕在 #139 取消后**保持原样不动**（停止指令明确「注释一律不动」）。
> 其中「已由 #139 承接」的措辞现已被本裁定覆盖为「遗留、以后再说」，
> 将来重启时一并订正，本轮不做文字改动。

---

## ③ 将来若要重启，第一步该做什么

**第一步：先查末 kTile 的 pad 元素在数据路上是否被掩码 / 门控。**

具体检查点（这是判断「修 RTL」还是「记文档 + 推迟到 #140 加校验」的前置裁定）：

1. 非 MX 路径 `kTiles = ceil(k/totalN)`，说明硬件**形式上**允许 K 有余数。但要查清：
   末 kTile 的那 `pad` 个越界元素在数据路上**是否被掩码/门控**——
   - Executor 的 `beatsPerRow` / valid 窗口 / k 边界门控；
   - Scratchpad 的 push 计数。
2. **若没有掩码** ⇒ pad 元素会进 MAC 累加 ⇒ **计算结果本身就是错的**
   ⇒ 那么 `K % bKpb ≠ 0` 事实上不可用，`term1` 只是这个更大问题的一个**副作用**
   （此时应走「文档留痕 + #140 加校验拒绝」，而不是单改地址生成）。
3. 附带需查：`supportedFmts` / `validate` / 头注释 / 金锚 `HiveDebugModel`
   有没有任何地方声明过 K 的对齐约束。
4. 附带需查：git 历史（`672fb37` 及其前后），看这个累加模型是重构引入的还是原生的；
   参照 #133 对 dmaC 的做法（把累积式改成绝对式）判断 RdOnly 是否适用同一范式。

**若裁定为「是正常配置」才进入修复**：把 `HiveCoreDmaRdOnly` 的 n-tile 跳转从
`lastBTileStartAddr` 累加模型改成**绝对式**（参照 #133 对 dmaC `cAddrSeqDense` 的成功范式）；
护栏用例 **nTiles 必须 ≥3**（二阶项在 nt=1 时最小，只验 nt=1 会漏掉「只修对第一轮」的假修复），
K 取非 bKpb 整数倍、loadWMode=1、strictAddr=true，并保留一条 K 为整数倍的用例证明零退化。
**金锚不动**（已取证金锚是权威侧）。

---

## 中止时的进度快照（供将来接续，非新增取证）

阶段 A 已读到源码、尚未形成书面裁定即被叫停。已打开的文件：
`HiveCoreDmaRdOnly.scala`、`HiveCoreCalcConfig.scala`、`HiveCoreExecutor.scala`、
`HiveCoreScratchpad.scala`、`HiveDebugModel.scala`（validate / Plan / buildMemory / golden）、
`HiveCoreParamDebugSpec.scala`（Section E）、`672fb37^:HiveCoreDma.scala`（git show 到 /tmp）。

**无任何文件被写入或修改**（本 build/ 笔记除外）。
