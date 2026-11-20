# WorkerBee PE 阵列优化实施计划：C 立项 + INT MAC 宽度修正 — 2026-11-19

> **状态**：WP-A + WP-B 均已落地并验证（2026-11-20）；实施记录见 §7.1/§7.2。Q1 裁定 = 选项 2 + block 饱和到 max。
> **日期**：2026-11-19。后续 opt 计划一律带日期标题、置于 `doc/opt/`。
> **范围**：只动 PE 阵列计算逻辑（MAC 内部加法器）+ INT MAC 宽度缺陷修正；
> 不碰 DMA / feeder / Executor / sequencer / 寄存器映射 / 写回通路 / `StreamFifo` / `WbCell.scala`。
> **目标格式集**（用户裁定）：{BF16, MXE4M3, MXE5M2, MXE2M1, INT8, INT4}。
> **依据**：评估与证明见 [pe_array_ce_plan.md](../pe_array_ce_plan.md)（C/E 实测、§8.2 等价证明、§8.4.1 F1–F3 取证、§6.1.1 溢出语义）；
> 调研背景见 [ws_tree_reduction_plan.md](../ws_tree_reduction_plan.md)。本文不重复评估，只列执行。
> **口径**：面积 = yosys + Nangate45 预综合，**非物理面积、非功耗**；本机无 STA，**不给 Fmax**。

---

## 0. 摘要

| 工作包 | 内容 | 收益 | 依赖 |
|---|---|---|---|
| **WP-A** | P7：`WbFusionMacInt` 四积中间和 16-bit → 19-bit（修单点溢出缺陷） | 修缺陷；面积增量待实测（预计 <100 µm²/PE） | 无（独立，先做） |
| **WP-B** | P2：C 方案 = MX4 块内整数前端 `Mx4BlockInt` + 3 槽 lazy 加法器 `FpAddLazy3` | **−6.19%**（Q1 选 2 + block 饱和到 max ≈ **−5.96%**） | Q1 裁定 + 金锚重推 |

**E 方案关闭**（6 格式并集 flat +7.21% / pair +3.84%，节点数下界支配，见 pe_array_ce_plan §5）。

**已结前置取证**（pe_array_ce_plan §8.4.1）：
- F1：C 在 in-spec 域与现状**数值相等但非逐位**（2100 SAT 分解证明 + 逐位失配见证）⟹ **金锚须重推**。
- F2：scale∈{0,1}/E1 低域/underflow 现有 266 绿**零覆盖** ⟹ 须补定向用例。
- F3：饱和域签核量化流**不可达**（amax≥2.98e38）；属软件流保证非硬件契约。
- F4/F5（死硬件红利核实 + 整 PE 兑现率）**须 WP-B 实施后做**。

**待用户裁定**（实施前必须拍）：
1. **Q1 饱和域**：建议**选项 2（声明规格变更）+ block 级饱和到 max**（§6.1.1，+16.2 µm²/PE，≈−5.96%）。
   选项 1（逐元素饱和复刻）−3.37% 贵 12×；Inf 破坏有限值累加器不可取。
2. **`FpAddNarrow` 处置**：保留为精度契约参考（被 `FpAddNarrowSpec`/`OptSpec` 测试）vs 连同 spec 删除。建议保留。
3. **饱和 sticky / 保守阈值 / 累加溢出 E3 对齐**（§6.1.1 三裁定项）：建议非 sticky 可接受、阈值保守可接受、E3 暂不动。

---

## 1. WP-A：INT MAC 中间和宽度修正（P7，独立先做）

### 1.1 缺陷
[WbFusionMacInt.scala:76-77](../../scala/main/WbFusionMacInt.scala) 四积和按 Chisel 语义应 19-bit，
但交付 RTL [Wb_WbFusionMacInt.sv:65](../../verilog/Wb_WbFusionMacInt.sv) 发射成 16-bit（先 mod 2¹⁶ 再符号扩展）。
**唯一触发点**：INT8 且 `a=b=0x8080`（四字节全 0x80），和 = 32768 溢 16-bit 上界 32767，偏差 −65536。
INT4 模式无错（和 ∈[−224,256]）。yosys SAT 反例已证（pe_array_ce_plan §7.3）。

### 1.2 改动（1 文件）
| 文件 | 改动 |
|---|---|
| `src/workerbee/scala/main/WbFusionMacInt.scala` | `val sumFull = r0.pad(19) + r1.pad(19) + r2.pad(19) + r3.pad(19) + io.cReg.asSInt`（显式钉位宽，不给 firtool 收窄机会） |

**零 diff 面**：除 `Wb_WbFusionMacInt.sv` 外所有模块 MD5 不变。

### 1.3 验收
1. **SAT 全域复验**：`sim/ws-tree-20261118/intmac_equiv.ys`（修后 RTL vs `gold_intmac.sv` 全宽金锚 miter，
   `proc` 必须在 `miter` 前）prove trigger=0 全域 SUCCESS。**不能只看仿真绿**。
2. **定向用例**：`WbFusionMacIntSpec` 新增 INT8 `a=b=0x8080`，`cReg ∈ {0, 1, 0xFFFFFFFFFF}`；金锚由张量语义推导（本就正确，不改 `WbDebugModel`）。
3. **面积实测**：修后 `Wb_WbFusionMacInt` 单模块 + 整 PE yosys 对照，记录增量。
4. **精度对齐（非全量回归）**：WP-A 纯数据通路、不涉控制层，按用户裁定**不跑全量 20 suite 回归**；
   验收 = `WbFusionMacIntSpec` 定向绿 + SAT 全域等价 + spec 精度对齐。spec §5.2.3 本就描述正确张量语义
   （全精度乘加后按累加位宽截低位、溢出绕回），P7 是 RTL 实现缺陷而非规格缺陷 ⟹ spec 散文无需改；
   可执行规格（测试金锚）原拟合缺陷 RTL，已订正为全精度 BigInt 求和后截 `cEffW`。

---

## 2. WP-B：C 方案（P2）

### 2.1 前置（实施前）
- Q1 裁定（§0）。若选 2 + satmax：`Mx4BlockInt` 用 P<<sh 前端 + block 溢出饱和码 `{sgn,254,31'h7FFFFF80}`
  （归一化为有限 fp32max，RNE 对齐见 §6.1.1）；若选 1：改 n 形式 + `n_eff` 饱和 mux。
- **金锚重推方案确认**（P9）：C 非逐位兼容，MX4 多拍累加截断点改变。金锚一律由张量语义推导，
  量化分叉界（绝对界 ≤ n_lossy×窗口LSB 沿用 FpAddNarrow 契约）。**禁止以 RTL 行为拟合**。

### 2.2 文件 × 改动点（详见 pe_array_ce_plan §8.1）
| # | 文件 | 改动 | 类型 |
|---|---|---|---|
| 1 | `scala/main/Mx4BlockInt.scala` | 新增。P<<sh 整数前端 + subnormal 等效尾数 + underflow 冲刷 + tiny 守卫 +（Q1 选 2）block 饱和到 max | 新增 |
| 2 | `scala/main/FpAddLazy3.scala` | 新增。3 槽全 lazy-40 解包，W=19，MagW=W+2，kGrow∈{0,1,2}，平衡树 | 新增 |
| 3 | `scala/main/WbFusionMacFp.scala` | `if(!useNarrowAdd)` 包住 fp4Mul0/1 + 4×FpCvtMx4ToMid + r2/r3（默认路不例化）；例化 Mx4BlockInt；r0/r1 打包 lazy；槽选择 s1/s2；useNarrowAdd=true 例化 FpAddLazy3；connectFiveSlotAdder 拆 3/5 槽 helper | 改 |
| 4 | `scala/main/FpAddNarrow.scala` | **不改不删**（精度契约参考 + 被 spec 测试；默认 RTL 不再例化）。处置待裁定 | 不动 |
| 5 | `scala/test/Mx4BlockIntSpec.scala` | 新增。穷举 256 nibble 对 × scale 域（underflow/饱和/tiny），金锚复用 verify_mx4_all.py 真值链 | 新增 |
| 6 | `scala/test/FpAddLazy3Spec.scala` | 新增。3 槽 / 退化 2 槽 / 零槽 / 完全相消 / expOut 钳 254 / 与 FpAddNarrow 掩零后逐位对拍 | 新增 |
| 7 | `scala/test/WbFusionMacFpNarrowSpec.scala`、`WbFusionMacFpBf16Spec.scala` | 新增 MX4 subnormal/underflow/tiny-scale + 多拍累加分叉定向用例 | 改 |
| 8 | `doc/spec.md` | 记录 5→3 槽、MX4 整数前端、Q1 裁定、tiny/溢出域契约、非逐位兼容声明 | 改 |

**零 diff 面**：`WbFusionMacInt`（WP-A 单独改）/ `WbWorkUnit` / `WbComb` / `WbWorker` / `WbWorkerOsM1` /
`WorkerBeeExecutor` / `WbOsM1Feeder` / sequencer / 全部 DMA / `WorkerBeeRegIf` / 寄存器映射 / `StreamFifo` / `WbCell.scala`。

### 2.3 实施步骤
- **S1**：新增 `Mx4BlockInt` + `Mx4BlockIntSpec`（穷举金锚），单 suite 绿（`-J-Xmx6G`）。
- **S2**：新增 `FpAddLazy3` + `FpAddLazy3Spec`（含与 FpAddNarrow 掩零逐位对拍），单 suite 绿。
- **S3**：改 `WbFusionMacFp`（条件例化 + 槽选择 + helper 拆分），`WbFusionMacFpNarrowSpec`/`Bf16Spec` 对拍基线绿。
- **S4**：补 MX4 边界域 + 多拍累加定向用例（F2 零覆盖域）；金锚重推落地。
- **S5**：默认 RTL 重发射 + **F4**（确认 FpMulMx4/FpCvtMx4ToMid 实例消失，编译期 if 不例化而非悬空）+ **MD5 零 diff 审计**（§2.2 零 diff 面逐字节不变）。
- **S6**：**F5** 整 PE yosys 对照（与 1.6707 mm² / satmax 1.6975 对账，±1.77% 噪声内）+ **定向精度回归**
  （`Mx4BlockIntSpec`/`FpAddLazy3Spec`/`WbFusionMacFpNarrowSpec`/`WbFusionMacFpBf16Spec`/`WbFusionMacIntSpec`，`-J-Xmx6G`）。
  WP-B 纯数据通路、不涉控制层，按用户裁定**不跑全量 20 suite 回归**。
- **S7**：spec.md 同步 + 本文回写实施记录 + memory。

### 2.4 验收
1. F4：默认 RTL 无 `Wb_FpAddNarrow` / `Wb_FpMulMx4` / `Wb_FpCvtMx4ToMid` 实例；新增 `Wb_Mx4BlockInt` / `Wb_FpAddLazy3`。
2. MD5：零 diff 面逐字节不变；只 `Wb_WbFusionMacFp.sv` 变。
3. F5：整 PE 面积收益落 −6.19%（选 1 −3.37% / 选 2+satmax ≈−5.96%），噪声内对账。
4. **定向精度回归绿**（MAC 族 suite，非全量）；新增定向用例覆盖 F2 零覆盖域 + P9 多拍分叉。
5. 金锚由张量语义推导，非逐位兼容已在 spec 声明。

---

## 3. 验证与回归纪律
- **范围裁定（用户）**：WP-A/WP-B 均为纯数据通路改动、不涉控制层 ⟹ **不跑全量 20 suite 回归**，
  只对齐 spec 与 RTL 精度：定向 MAC 族 suite（`-J-Xmx6G`）+ SAT 全域等价 + MD5 零 diff 审计 + spec 精度对齐。
- 单 suite `-J-Xmx6G` + 绝对 tmpdir。一次一个 sbt 进程；后台 run 期间不编辑源码（markdown 除外）。
- 新增用例消息中文；硬件 assert/require 消息英文 ASCII。参数化用例名不得撞车。
- 金锚一律张量语义推导，**禁止拟合 RTL**。
- SAT miter：`proc` 在 `miter` 前；本机 Verilator 独立二进制被宿主 SIGKILL ⟹ 用 yosys SAT。
- 面积对照须**同批同 e_nodes.v**（追加模块改 abc 输入序 ⟹ 跨批漂移，见 §6.1.1 实测注）。

## 4. 风险
| 风险 | 缓解 |
|---|---|
| firtool 保留悬空实例 ⟹ 拿不到死硬件红利 | 编译期 if 不例化；F4 核实 |
| MX4 多拍累加与现状分叉（P9） | 金锚张量语义重推 + 量化分叉界 + 多拍定向用例 |
| tiny/E1/溢出域行为变更 | F1 已取证；补 scale∈{0,1}/全零块/溢出定向用例 |
| Q1 选 2 后硬件契约层任意 scale | spec 声明 out-of-contract + block 饱和到 max（非静默钳位）；可选极端 scale 守护 |
| 面积收益被 abc 噪声吞 | 6.19% ≫ 1.77% 噪声底；同批对照 + 整 PE 口径 |
| WP-A 加法链增宽面积超预期 | 实测；预计 <100 µm²/PE |

## 5. 交付面
- 只发**单一默认配置一组 verilog**（对照目录与子模块发射入口已删）。
- `build/` 仅报告/日志；`sim/` 仅临时仿真文件。已删锚文件/取证文件不恢复。
- git 写须用户明确授权；按关注点拆分（WP-A 一条、WP-B 一条、doc 一条）。

## 6. 依据与证据索引
- 评估/证明/取证：[pe_array_ce_plan.md](../pe_array_ce_plan.md) §3（对比表）/§6.1.1（溢出语义）/§7（P7）/§8.1（文件清单）/§8.2（等价证明）/§8.4.1（F1–F3）。
- 调研背景：[ws_tree_reduction_plan.md](../ws_tree_reduction_plan.md)。
- 测量/证明脚本与日志：`sim/ws-tree-20261118/`（summary10/16/17/19/20、f1_*、verify_satmax、intmac_*）。

---

## 7. 实施记录

### 7.1 WP-A（P7 INT MAC 中间和增宽）—— 已落地 2026-11-20
| 项 | 结果 |
|---|---|
| 源码 | [WbFusionMacInt.scala:78](../../scala/main/WbFusionMacInt.scala) `r0.pad(19)+r1.pad(19)+r2.pad(19)+r3.pad(19)+io.cReg.asSInt`；发射 RTL `_sumFull_T_10[18:0]`（19-bit），旧 HEAD 为 `_sumFull_T_6[15:0]`（16-bit） |
| 金锚订正 | `WbFusionMacIntSpec` 的 `goldenInt8/goldenInt4` 原按缺陷 RTL 把中间和 `truncSInt(...,16)`（拟合 RTL，违金锚纪律），已改为全精度 BigInt 求和后截 `cEffW=40`；删除无用 `truncSInt` |
| 定向用例 | 新增 P7 触发块 `a=b=0x8080`、`cReg∈{0,1,−1,549755813887}`，显式锚 `truncToCEffW(32768+cSigned)`；`WbFusionMacIntSpec` **9/9 绿**（INT8 25 + INT4 25 + wrap 7 + guard 3 + 单格式 20） |
| SAT 全域 | `intmac_equiv_merge.ys`（修后 RTL vs `gold_intmac.sv` 全宽金锚）：`miter` 后加 `opt_merge` 合并 gate/gold 同构 fmt 寄存器与 mux ⟹ 43886 vars/128104 clauses，`-seq 3 -set-init-zero` **prove trigger=0 SUCCESS（no model）**。旧 `intmac_equiv.ys`（merge 在 miter 前）8 独立乘法器 59704 vars 不可解，已弃用 |
| 面积 | 单模块 nangate45：旧 1689.366 → 新 1714.902 µm²，**+25.536 µm²（+1.51%）**，与「<100 µm²/PE」预期一致；整 PE 占比微小（FP 路主导），未单独重测 |
| spec 对齐 | spec §5.2.3 本就描述正确张量语义（全精度乘加后截低位、溢出绕回），P7 属 RTL 实现缺陷非规格缺陷 ⟹ 散文无需改；可执行规格（测试金锚）已订正 |
| 零 diff | 仅 `Wb_WbFusionMacInt.sv` 变；其余模块 MD5 待 WP-B 重发射后统一审计 |
| 回归范围 | 按用户裁定纯数据通路不涉控制层 ⟹ **未跑全量 20 suite**，仅定向 `WbFusionMacIntSpec` |

### 7.2 WP-B（P2 C 方案：Mx4BlockInt + FpAddLazy3 + 条件例化）—— 已落地 2026-11-20

**Q1 裁定**：选项 2（声明规格变更）+ block 级饱和到 max。`FpAddNarrow` 保留为精度契约参考（仍被 `FpAddNarrowSpec`/`OptSpec` 测试），默认 RTL 不再例化。非 sticky、保守阈值、E3 不动。

| 项 | 结果 |
|---|---|
| 新增源码 | [Mx4BlockInt.scala](../../scala/main/Mx4BlockInt.scala)：纯组合 MX4 整数前端，P<<sh 形式（`maE=ma&(e2!=0)` 处理 subnormal、`ua/ub` underflow 冲刷、`tiny=eSum<121` 守卫、`ovf=eRaw>254` block 饱和码 `{sgn,254,31'h7FFFFF80}`）；[FpAddLazy3.scala](../../scala/main/FpAddLazy3.scala)：3 槽全 lazy-40，`W=19`、`MagW=W+2`、`kGrow∈{0,1,2}`、平衡树 `tA=fC+&fA`/`S=tA+&fB`、`expOut=clamp(E+k,254)` |
| 集成 | [WbFusionMacFp.scala](../../scala/main/WbFusionMacFp.scala)：`if(!useNarrowAdd)` 包住 fp4Mul0/1 + 4×FpCvtMx4ToMid + r2/r3（默认路不例化）；true 分支**先例化 FpAddLazy3**（其 `require(cEffW>=40)` 须在 `fp32ToLazy40` 的 `(cEffW−40)` 位宽算术之前触发，否则非法 cEffW 先撞 Chisel 负位宽错而非规格护栏）；`fp32ToLazy40` 含隐含 1（`Cat(p31,e,1.U(1.W),m,0.U(7.W))`）保证与 FpAddNarrow 掩零逐位等价；槽选择 `s1=Mux(isMx4,blk,lazyR0)`、`s2=Mux(isMx4,0,lazyR1)`；FpCvtMx4ToMx8Mid（lane0/lane1）保留 |
| 金锚（P9） | 一律张量语义推导，**禁拟合 RTL**。`Mx4BlockIntSpec` 穷举 256 单 lane nibble 对 × 81 scale 对 = 20736 + 多 lane 结构化/随机 12464 + 边界锚，**3/3 绿**；`FpAddLazy3Spec` 整数 3 槽模型 + 与 FpAddNarrow 掩零逐位对拍 W=19/16/22 各 1362 例 = **4086 bit-exact，2/2 绿** |
| 定向用例（F2） | `WbFusionMacFpNarrowSpec` 补 MX4 tiny-scale/underflow 冲刷真零 + 溢出饱和有限 ≈fp32max（`expF==254 && mantF∈[0x7FFFE0,0x7FFFFF]`，单拍 fp32 归一化比较）**7/7 绿**；`WbFusionMacFpBf16Spec` cW=39 护栏改断言 `cEffW(39)` 子串 **23/23 绿**；`WbFusionMacFpSpec`（pin useNarrowAdd=false 五槽基线）不受影响 |
| S5 MD5 零 diff | 零 diff 面 35 模块逐字节不变；**removed** `Wb_FpAddNarrow`/`Wb_FpMulMx4`/`Wb_FpCvtMx4ToMid`，**added** `Wb_Mx4BlockInt`/`Wb_FpAddLazy3`，**changed** 仅 `Wb_WbFusionMacFp.sv`（预期 C 方案）+ `Wb_WorkerBeeRegIf.sv`。RegIf 经可逆对照实验（备份 C 方案 MAC→还原 HEAD 5 槽 MAC→重发射→还原 C 方案→重发射）证明：剥去全部 `//` 注释后逻辑 diff = **0**，唯一差异是 `<stdin>:NNNNN` FIRRTL 全局流定位注释行号（27288→20888，因 MAC 子模块集尺寸变化而平移），源码 git 未改 ⟹ 逻辑逐位不变，属良性例外 |
| F4 死硬件 | C 的 WbWorker 闭包 = 10 模块，无 FpAddNarrow/FpMulMx4/FpCvtMx4ToMid；交付 MAC dead-ref grep = **0**；FpCvtMx4ToMx8Mid ×4 保留。编译期 if 不例化（非悬空），firtool 死硬件红利兑现 |
| S6 F5 面积 | **真实重发射 RTL**（非代理）整 PE yosys+nangate45：基线（HEAD 5 槽 MAC + 当前 WP-A IntMac + 未改 PE 模块）阵列 **1.8014 mm²** → C **1.6805 mm²**，**−6.71%**（IntMac 两侧同 1714.902 ⟹ 纯 WP-B）。单模块 MAC 3995.054→3604.832（−9.77%）/ WorkUnit 5734.694→5252.702 / Worker 7018.942→6557.698 / OsM1 7304.094→6665.694。对账：真实 C vs 代理 1.6707 **+0.59%**、vs satmax 1.6975 **−1.00%**、真实基线 vs 代理 1.7810 **+1.15%**，均在 ±1.77% 噪声内；收益符号与量级确认（略优于代理 −6.19%）。详见 [build/wpB_f5_area.md](../../../../build/wpB_f5_area.md) |
| S6 定向回归 | `Mx4BlockIntSpec FpAddLazy3Spec WbFusionMacFpNarrowSpec WbFusionMacFpBf16Spec WbFusionMacIntSpec` 一次 sbt（`-J-Xmx6G`）：**44/44 绿，5 suite completed/0 aborted，SBT_EXIT=0**。按用户裁定纯数据通路不涉控制层 ⟹ **未跑全量 20 suite** |
| S7 spec 同步 | [spec.md](../spec.md) **v5.1.0**（次版本：兼容细化，既有非逐位契约/格式集/寄存器/端口不变）：§5.2.5 默认归约器改述三槽 lazy-40 `{C,S1,S2}`（W=19）+ MX4 整数前端 + 三段精度契约（J1/J2≤2^-18/J3）+ MXFP8/BF16 逐位等价、MXFP4 多拍非逐位声明；§5.2.6 新增 MX4 underflow/tiny-scale/块溢出饱和边界语义；修订记录 + 首页版本/日期同步 |
| 溢出精度 nuance | satmax 码 `{254,0x7FFFFF80}` 经 FpAddLazy3 W=19 窗口截断 mant31→`0x7FFFF000`，归一化为 fp32 `0x7F7FFFE0`（fp32max−31ulp，相对误差在 2^-18 内），**非逐位 fp32max**。计划 §6.1.1「恰好归一化为 fp32max」指前端直出；累加器窗口会截断。语义目标（有限、≈max、非 Inf、非 sticky）成立，spec 已按窗口精度内如实表述 |