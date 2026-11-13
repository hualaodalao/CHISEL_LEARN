# scaleB A 级改动 plan：跳 gather + 消费端字节索引加偏移

- **状态**：待确认（本文件是 plan，未动任何 `src/main`）
- **日期**：2026-11-11
- **前置基线**：20 suite / **238 test 全绿**（47 分 01 秒，`-J-Xmx10G`），零 RTL 改动下取得
- **范围**：只动 scaleB 读通路与其字节选择。B 数据 / A / scaleA / C 通路、阵列、feeder 配对、errCode、寄存器映射全部不动。

---

## 1. 目标与收益

去掉 scaleB 非对齐拍 gather 的**第二次物理读**——它填的字节本窗口永不消费。

| 格式 / blk | 读/kTile 现状 → A 级 | predPort | 拍/kTile 现状 → A 级 | 吞吐 | scaleB 物理字节 |
|---|---|---|---|---|---|
| MX8×MX8 / MX8×MX4 blk16 | 24 → **16** | 24 → 16 | 25.07 → **≈17** | **1.47×** | 96 → 64 B（−33%） |
| MX8×MX4 blk32 | 28 → **16** | 28 → 16 | 29.08 → **≈17** | **1.71×** | 112 → 64 B（−43%） |
| MX4×MX4 blk32 | 24 → **16** | 24 → 16 | 25.11 → **≈17** | **1.48×** | 96 → 64 B（−33%） |
| MX4×MX4 blk16 | 16 → 16 | 16 | 不变 | **1.00×** | 64 B（不变） |
| BF16 / INT 族 | 无 scale 通路 | — | 不变 | 1.00× | — |

A 级后 `predPort = max(16, B 16, A 1, sB 16) = 16` = 阵列下界 ⟹ **吞吐收益到此封顶**，再降读数（B 级）只省字节。

## 2. 机制与代码依据

两条消费路径用**同一个字节选择公式**，最大字节索引 = `kPerBeat/blk − 1`：

| 路径 | 出处 | 公式 |
|---|---|---|
| OS | `WbOsM1Sequencer.scala:138-139` | `(word >> (((lane*step)/blk)*8))(7,0)` |
| WS | `WorkerBeeExecutor.scala:195-197` | `idx = (lane*scaleBStep)/scaleBBlk`，同式 |

`lane ∈ [0,15]`、`step ∈ {2,4}`、`blk ∈ {16,32}` ⟹ 每拍实际消费 `demand = 16*step/blk ∈ {1,2,4}` 个**低位**字节。

逻辑地址步进 = `aKLoadNum/blk` = demand（`WorkerBeeCalcConfig.scala:622-623`），demand 整除 4 ⟹ **有用字节永不跨 4 B 边界**，单个对齐字即可满足。gather 的 hi 读（`WorkerBeeDmaRdOnly.scala:307`）只填 `idx ≥ demand` 的字节，永不被选中。

**改法**：DMA 恒读 `alignedBase`、原样推 `rsp.data`（**不移位、不补零**，payload 是真实对齐 4 B 内存字）；消费端把字节索引加上该字的偏移 `off`。

⚠️ **开关语义不是「让访问对齐」**：DMA 现在发出的 `req.addr` 本来就 100% 对齐——`:194` `unaligned = byteOff =/= 0`，`:305-308` 非对齐拍取 `alignedBase`、hi 读取 `alignedBase + portBytes`，对齐拍取 `curAddr`（此时 `byteOff` 恒 0）。gather 的存在本身就是为了保证总线对齐。故参数命名为 **`noGather`**，语义 = 「本通道每逻辑拍只需一个对齐端口字，消费端自行按偏移选字节 ⟹ 抑制 hi 读」。

**为什么必须是实例级开关、不能全局删 gather**：gather 的前提是「整个 `pushW` 都被消费」，A/B 数据满足且逻辑拍会跨字 ⟹ 第二读必需。而「只用低 demand 字节」是消费侧知识，DMA 从 addr/len/stride 推不出来。同文件 `stretch` 已是同一模式的先例（`:194` 强制 `unaligned=false` + `:304` 取 `alignedBase`），但它是运行期信号且绑 fp4→fp8 LUT 加宽，**不复用**（避免碰已验证的 W4A8 通路）。

### 2.1 为什么不能「不加参数、直接无条件改」（已查证，有现成反例）

**反例**：`WorkerBeeOsM1PerfSpec.scala:141` 的「BF16 非对齐 N48 K64」用例故意取 `aBase = A_BASE+3`、`bBase = 0x01000000+5`（`:128-130`），纯 BF16、无 stretch，走的正是 gather 两读 + 移位重组，且 `strictAddr=true` 对账 golden C。**无条件改成「恒读 alignedBase、原样推」会直接打红该用例。**

| 口径 | 事实 | 出处 |
|---|---|---|
| A/B **步进** | 恒 32B 对齐（errCode 8 的 K 粒度 ⟹ rowOff/colOff 均为 32 倍数） | `CalcConfig:270-320` |
| A/B **基址** | **无对齐守护**，非对齐合法 ⟹ 非对齐拍来自基址而非步进 | errCode 8 仅混配臂守 `bAddr%32`（`CalcConfig:300-306`） |
| scale **基址+步进** | 两者都不保证对齐，且**明确不设 errCode** | `CalcConfig:588-596`「No errCode guards this」 |

⟹ 删 gather = 推翻 2026-10-12 裁定（硬件自主 gather、**软件张量布局不变**、明确否决「新增对齐违约 errCode 拒绝」）。

**唯一真能「不加参数」的替代**：用现成编译期量 `pushW` 区分（scale 实例 = `cfg.scaleBW` = 32，数据实例 = `totalN*aEffW`/`totalN*bW` ≥ 64）。**不采纳**：① 隐式魔数，`totalN`/`bW` 一改就误判数据实例；② 会连带关掉 `aDmaScale` 的 gather ⟹ 必须同时给 scaleA 消费端加 off（见 §7），改动面反而更大。

**`noGather` 不是可配置选项**：与既有 `bufWidth`/`bufDepth` 同类，是**实例级接线常量**，不进寄存器映射、不暴露给软件。同一模块被 4 个读实例复用而契约不同，差异只能在例化处表达。

## 3. 关键边界：off 不是「每窗口一个常数」

**plan 阶段查证发现的坑**：列步进 `sbRowOff = K/blk`（`WorkerBeeCalcConfig.scala:610-615`）**不一定 4 B 对齐**。
`K=32, blk=16` ⟹ `sbRowOff = 2` ⟹ 同一窗口内各列偏移不同。errCode 8 只要求 `K % kPerBeat == 0`（MX8 为 32），故 **K=32/96/160 合法且 QuickSpec 大量使用**（`WorkerBeeQuickSpec.scala:58,64,74,75,81,214,459`）。

⟹ off 必须**按列/按 lane** 推导，不能只由 kt 决定。

**已验证的闭式**（`j` / `lane` 是编译期常量，`kTiles`、`kt` 两侧都已有）：

```
demand = (T*step)/blk ∈ {1,2,4}
q      = (j*kTiles + kt) & 3            // OS 升序
q      = ((T-1-lane)*kTiles + kt) & 3   // WS 降序
off    = demand==4 ? 0 : demand==2 ? (q[0] << 1) : q[1:0]
idx    = (lane*step)/blk + off
```

推导依据：`sbRowOff = K/blk = kTiles*demand`，故 `off = (demand*(j*kTiles + kt)) mod 4`。
`demand ∈ {1,2,4}` ⟹ 退化为 2 bit mux + 常量乘，**无除法器、无桶形移位器**。

**越界不变式**：`idx_max = demand−1`，`off ≤ 4−demand` ⟹ `idx + off ≤ 3`，恒在字内。

**验证**：对 `K ∈ [32,2048] 步长 32 × step ∈{2,4} × blk ∈{16,32} × {OS,WS}` 共 **384 组合法组合**，逐 (kt, j) 比对模型闭式地址的真实偏移：**失配 0 处，`idx+off` 恒 ≤3**。

## 4. 改动清单

| # | 文件 | 改动点 | 零 diff 保证 |
|---|---|---|---|
| **M1** | `WorkerBeeDmaRdOnly.scala` | 新增构造参数 `noGather: Boolean = false`；`:194` `unaligned` 在 `noGather` 时取 `false.B`；`:304` `reqAddrDefault` 在 `noGather` 时取 `alignedBase` | `noGather` 是**编译期常量**，false 时两表达式化简回原式 ⟹ aDma / bDma / cDma / scaleA **发射 RTL 逐位不变** |
| **M2** | `WorkerBee.scala:38` | `bDmaScale` 实例传 `noGather = true` | 仅一行；`aDmaScale`(:37) 不动 |
| **M3** | `WbOsM1Sequencer.scala:138-142` | `selByte` 增 `off` 形参；B 侧按 §3 公式传 `off(j)`（用 `feeder.io.headKt(j)`、`io.kTiles`、`io.scaleBStep`、`io.scaleBBlk`）；**A 侧 `io.aScaleByte`(:140) 传 off=0** | `scaleOne` 臂恒 127 不变；A 侧 scaleA DMA 未改 ⟹ 必须保持原式 |
| **M4** | `WorkerBeeExecutor.scala:195-197` | `idx` 加 `off`（WS 降序用 `T-1-lane`；`curKTile`:119、`kTiles`:117 均在 :195 之前声明，无前向引用） | **`:169-177` 的 scaleA 块不动**；`fp8Run` 恒 127 臂不动 |
| **M5** | `WbDebugModel.scala:767` | `.flatMap(gatherPhys(_, 4L))` → `.map(a => a - a % 4L)` | **C 金锚完全不动**（张量语义未变，只有访问序列变） |
| **M6** | `WorkerBeePhase0Spec.scala` | A2/A4 组断言：`sbPerKTile` 24/28→16、`sbAmp` 3.0/7.0→2.0/4.0、MX8 系 `cycPerKTile` 25/29→≈17 | 判据按新访问序列推导，**不拟合实测** |

**push 通路零改动**（已核）：`unaligned=false` ⟹ `gPhase` 恒 0 ⟹ `alignedRspPush = rsp.valid`、`bufPush.payload = rsp.payload.data`（`:314-329`）。**不需要移位、不需要 off FIFO、不需要改 credit/inflight**。

**不受影响**（已核）：`WorkerBeeOsM1PerfSpec.scala:565` 的硬编码断言是数组逻辑计数 `advances/commits/lowerBound`，与 scale 读数无关。

## 5. 必须保持的不变式

1. **1 逻辑拍 = 1 读 = 1 推**，16 拍/kTile 不变 ⟹ `scaleBOcc >= totalN` 预蓄门控（`Executor:656`）与 `bPop/scaleBPop` lockstep 断言（`Executor:424`）都不受影响。
2. A 级只**加快**供数（读数 24→16），不减速、不改节拍 ⟹ 不触发 loadW 预取节奏不变式的风险面。
3. **选中字节逐一相同** ⟹ C 张量值不变，OS 与 WS 两路皆然。
4. `errCode` 全部不变；`scaleOne`（纯 FP8 注入 127）路径不变。
5. **WS 同步受益、功能不变**：WS 本就阵列界（33+m），吞吐不变，只省字节。

## 6. 验收判据

1. **既有 238 test 不得转红**（新增用例则总数增加）。复现：
   ```
   CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
     sbt -J-Xmx10G -J-Djava.io.tmpdir=build/tmp "testOnly <20 个 suite>"
   ```
   判完成看 `Total number of tests run` 与 `Suites: completed 20` 两行。
2. `sB 读/kTile`：MX8-blk16 **24→16**、MX8-blk32 **28→16**、MX4-blk32 **24→16**、MX4-blk16 **16→16**。
3. MX8 系拍/kTile **25.07 / 29.08 → ≈17**；MX4-blk32 **25.11 → ≈17**；BF16 / INT / MX4-blk16 **不变**。
4. `addrBadCnt == 0`（逐拍地址对账），C `elemMismatch == 0`。
5. **非 scale 实例发射 RTL MD5 不变**（aDma/bDma/cDma/scaleA）；默认 RTL 重发射审计。
6. 边界用例必须绿：**K=32 与 K=96 的 MX8/MX4 × blk16/blk32 × OS/WS**（§3 的 off 按列变化区）。

## 7. 非目标（本次不做）

- **B 级**（scaleB 同字复用，16→8 读）：吞吐 +0、字节 −5.2%，走 pop 侧路线，列入优化清单待 rank2 机制就绪后接上。
- **rank2**（B 数据去重，512→256 B/kTile、−41.7%）：同样走 pop 侧路线，收益是 B 级的 8 倍，优先。
- **scaleA 同类优化**：已查证 scaleA **同病**——`WbDebugModel.scala:723` 同样 `gatherPhys(_, 4L)`，`saColOff = aKLoadNum/blk` 同样是 demand，`WbOsM1Sequencer.scala:140` 的 `aScaleByte` 同样只选低 demand 字节 ⟹ 推导 1.5 读/kTile = 6 B（demand=2 时半数拍非对齐）。但 sA 对 `predPort` 无影响（1.5 ≪ 16）⟹ **吞吐 +0、字节仅 −0.3%**，本次不纳入；留到 rank2/2b 重做 scale 通路时一起。`noGather` 取通用名正是为了届时 `aDmaScale` 直接传 true 即可复用（bias 流共用该端口但地址已由 errCode 12 保证对齐，`alignedBase == curAddr` ⟹ 无副作用）。
- blk 默认档位（rank 0a）、扩列（rank 3）、WS 权重双缓冲（rank 4）。

## 8. 风险与回退

| 风险 | 处置 |
|---|---|
| off 公式在某个未扫描到的合法组合上失配 | §3 已扫 384 组；验收 6 强制覆盖 K=32/96 边界；失配会直接表现为 C `elemMismatch` |
| 误改 scaleA 臂导致 A 侧 scale 错位 | M3/M4 显式要求 A 侧 off=0 / 块不动；回归的 MX 用例会抓 |
| `noGather` 未做成编译期常量 ⟹ 其他实例 RTL 漂移 | 验收 5 用 MD5 审计强制 |
| 改动面膨胀 | 本 plan 共 6 处、其中 RTL 4 处；若实施中超出此范围即停下重新对齐 |

**回退**：M1 的 `noGather` 默认 false + M2 一行 ⟹ 把 M2 改回即恢复原行为；M3/M4 的 off 项在 off=0 时与原式等价。

---

## 9. 实施修订（2026-11-12，编码期查证后对本 plan 的三处修正）

### 9.1 契约缺口：scale 基址无对齐守护 ⟹ 快路径须加运行期门控

§2 原写「payload 永远是真实对齐 4B 内存字、契约不放宽」，但 off 闭式隐含假设了 `base ≡ 0 mod 4`。查证：`WorkerBeeCalcConfig` 中**搜不到任何 ScaleAddr 对齐守护**（errCode 12 只守 biasAddr、errCode 8 只在 W4A8 混配臂守 bAddr%32）。基址非 4B 对齐时 `L&3` 不再是 demand 的倍数，有用字节**真的会跨字**，单读无法恢复 ⟹ 按原 plan 直接改会静默算错。

**修正**：`noGather` 仍是编译期常量，但快路径再门控一个运行期位——
```
gatherEn = if (noGather) io.reg_baseAddr(offBits-1,0) =/= 0.U else true.B
unaligned = (原判据) && gatherEn
```
`reg_baseAddr` 是 DMA **已有输入**（scaleB 接 `regFile.bScaleAddr`）⟹ 不需新端口。基址非对齐时自动退回 gather；消费端 off 同步归 0（新增 `scaleBAligned` 位：Executor 由 `regFile.bScaleAddr(1,0)===0` 导出，经 `osScaleBAligned` 送 Sequencer）。**软件张量布局契约完全不变**，其他实例 RTL 仍逐位不变。

### 9.2 ★OS 的 kt 必须取 `headKt(j)`，不能取列 0 的 `tileKt`

§3 的 `q = (j*kTiles + kt) & 3` 未指明 kt 的来源，实施时先用了 Sequencer 的 `tileKt` ⟹ **首轮仿真 4 个 MX8 用例失败**（MX4-blk16 因 off 恒 0 全绿）。

根因：**OS 阵列第 j 列的 token 比列 0 延迟 j 拍**（`WbWorkerOsM1.scala:15-17`，`osConsume = osM1 && osAdvance && validIn`，而 validIn 取自左邻寄存输出）⟹ 各列槽头当前窗口号不同。这正是 feeder 要 per-column 存 `headKt`/`lanes` 的原因。失配列恰为「非 16 倍数列」（j=0 的列全对）与该机制一一对应。

**修正**：`ktLocal = feeder.io.headKt(j) - aKt + tileKt`。headKt 是跨 N tile 全局计数，`aKt - tileKt = nt*kTiles` 即本 tile 基号；只需 mod 4 ⟹ 低位加减即可，**无除法器**。

### 9.3 WS 的列号是 `tileInterCnt`，且 `kTiles` 寄存器存 count−1

- WS 的 `lane` 是 **K-lane**（只决定字内 idx），不是权重列号。本拍 scaleB 字的权重列号 = `tileInterCnt`（自 `totalN-1` 逐拍递减，与 bPop/scaleBPop lockstep）⟹ off 与 lane 无关，全 16 lane 共用一个 off。
- `WorkerBeeExecutor.scala:353` `kTiles := newKTiles - 1.U` ⟹ 公式里须用 `kTiles + 1.U`（`io.osKTiles` 已是 +1 后的计数，OS 侧直接用）。

### 9.4 实测（Phase0Spec 8/8 全绿，N=512 M=1 OS-M1 理想存储）

| 用例 | cycles 前→后 | 拍/kTile | sB读/kTile | sbAmp | stallB | 加速 |
|---|---|---|---|---|---|---|
| MX8×MX8 blk16 | 12837→**8769** | 25.07→**17.13** | 24→16 | 3→2 | 4065→**0** | **1.464×** |
| MX8×MX4 blk16 K512 | 12837→**8769** | 25.07→**17.13** | 24→16 | 3→2 | 4065→**0** | **1.464×** |
| MX8×MX4 blk32 | 14889→**8769** | 29.08→**17.13** | 28→16 | 7→4 | 6113→**0** | **1.698×** |
| MX4×MX4 blk32 | 6429→**4683** | 25.11→**18.29** | 24→16 | 3→2 | 1761→**0** | **1.373×** |
| MX4 blk16 / BF16 / INT / WS 全组 | 不变 | 不变 | 不变 | 不变 | 不变 | 1.00× |

⚠️ **§1 收益表的 MX4-blk32「1.48×」须订正为 1.373×**：K512 时 aKTiles=8<15，落在 0.8 归因的阵列尾波区，下界是 18.29 而非 17。MX8 系不受影响（aKTiles≥16）。
**MX8 系现与 INT8×INT4 同为 8769 拍 / 14.95 GMAC/s**，印证「A 级后 MX 追平 INT、0b 冗余收益归零」。

### 9.5 验收 6 的边界用例原不存在，已补

现有 OS MX 用例只有 K=64/128（均 K%64==0 ⟹ sbRowOff 是 4B 倍数 ⟹ off 逐列变化区**完全未被覆盖**）。已在 `WorkerBeeQuickSpec` 补 6 条：MX8×MX5M2，K∈{32,96} × bBlk∈{16,32} × {OS,WS}（K=32→kTiles=1 只验 j 项；K=96→kTiles=3 验 j×kt 交叉）。MX8 的 errCode 8 只要求 K%32==0 故合法；MX4 要求 K%64==0 不适用。

### 9.6 最终验证与重发射审计（2026-11-12）

**全量回归 244/244 全绿**（20 suite、0 aborted；= 原 238 + 新增 6 条）。日志 `build/regression-alevel.log`。
中途一次红：M5 漏改 **MxSpec 自带的第二套 scaleB 期望序列**（`WorkerBeeSimCaseMxSpec.scala:443`，不走 WbDebugModel）⟹ 6 个用例红在 `DMA-scaleB beat #N addr mismatch`，其余 19 suite 全绿。两处都改成 `map(a => a - a%4)` 后全绿。

**默认 RTL 已重发射**（`src/workerbee/verilog/`，71 文件）。RdOnly 由 2 变体分裂为 3：`RdOnly`→aDma+bDma、`RdOnly_2`→aDmaScale、`RdOnly_3`→bDmaScale（新）。

⚠️ **验收 5「非 scale 实例发射 RTL MD5 不变」这条判据本身不成立，已改用结构归一化比对**：模块名嵌在文件内，dedup 分裂又使编号位移 ⟹ 裸 MD5 必变。把内部标识符按首现序规范化（保留 `io_*` 端口名与 Verilog 关键字）后：

| 模块 | pre 内部名数 | post 内部名数 | 归一化哈希 | 判定 |
|---|---|---|---|---|
| 数据 DMA（aDma/bDma） | 229 | 229 | **完全相同** | **逻辑逐位不变**，差异纯为改名 |
| scaleA | 102（与 scaleB 共享） | 80 | 不同 | **常量特化**：少了 `io_isA`/`io_regFile_loadWMode`/`io_regFile_osM1` 三个端口（例化处 tie 死，不再共享模块后被传播）。逻辑不变 |
| scaleB | 同上 | 100 | 新模块 | 少 `io_isA`/`io_regFile_m`，同理特化 + noGather 快路径 |

gather 机构（`gPhase`/`loReg`/`offReg`/`inflCnt`）在三个变体中**都仍在** ⟹ §9.1 的基址非对齐回退路径确实保留。

**Feeder RTL 增大是真实代价、不是 bug**：`headKt_1..15` 与 30 个 `slots_*_kt`（16 bit）原先被 firtool 死代码消除（只有列 0 的 assert 用到），§9.2 改用全部 16 列的 `headKt(j)` 后必须保留 ⟹ 约 **+480 flop**。这是 per-column off 路线的固有成本。