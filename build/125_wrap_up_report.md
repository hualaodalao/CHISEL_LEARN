# #125 收口清理报告（不跑回归）

> 执行人：Troy ｜ 日期：2026-09-11 ｜ 状态：**部分完成（按 leader 裁定 C 收口）**
> 用户明确指示：**不跑全量回归**。本轮替代安全网原定为「重发射双产物 + 逐字 diff」，
> 但因并发编辑冻结（见 §0.2），该安全网**本轮未能执行**，改为遗留待办交接（见 §3）。

---

## 0. 执行摘要与未完成项声明

### 0.1 完成项一览
| 步骤 | 内容 | 状态 |
|---|---|---|
| 第0步 | 确认 sbt 空闲 + 清点 Rhys #130 遗留 | ✅ 完成 |
| 第1步 | Test/compile 基线 + 双产物快照到 `build/125_baseline_verilog*` | ✅ 完成 |
| 第2步 | 死码/stale 注释清理（删 2 个死寄存器 + 订正 6 处注释 + 裁定保留 runQuantGemm） | ✅ 完成 |
| 第3步 | 重发射双产物 + 逐字 diff 零功能变更证明 | ⛔ **未完成 → 遗留待办（§3）** |
| 第4步 | .gitignore 裁定 + 文本修改（已 git check-ignore 验证） | ✅ 完成 |
| 第5步 | 金锚 bW/8 同源化裁定 + 4 推迟项 + #130 留痕 + doc 概要 | ✅ 完成（登记/裁定，未实施） |

### 0.2 ★ 本轮未完成项（显著声明，勿在收口中消失）★
> **未完成项 = 双产物（`src/hive/verilog` 与 `src/hive/verilog_mx`）最终重发射 + 零功能变更证明（逐字 diff）。**
> **原因 = 并发编辑冻结。** 开工期间检测到另一进程（很可能是用户本人在 IDE 手写）正在**实时**向
> `src/hive/scala/main/HiveCoreDmaWrOnly.scala` 写入新功能（DMA `strb`/字节移位/`req.ready` 回接），
> 文件 mtime 每几秒推进、内容在我两次读取之间发生变化。对该移动靶重发射会：①捕获对方半成品 RTL；
> ②使 diff 混入**不属于我**的功能差异，令「零功能变更证明」失效。经回报，leader/用户裁定 **(C)：
> 冻结该文件、本轮取消重发射、改为遗留待办交接**。
> **接手人 = 用户。** 待 `HiveCoreDmaWrOnly.scala` mtime 稳定后，按 §3 的交接说明执行重发射与 diff。
>
> **风险声明：本轮结束时 `src/hive/verilog*` 的已发射产物相对当前源码是 stale 的，零功能变更证明未完成。**
> 下游任何直接取用仓内 `.sv` / `HiveCore_rtl.zip` 的流程（综合/交付/网表级仿真）在重发射前取到的是旧产物。
> 对 svsim/ChiselSim 仿真回归零影响（它们从 Scala 源码在 `build/chiselsim/` 现场 elaborate）。

---

## 1. 并发编辑事件取证时间线（冻结依据）

`src/hive/scala/main/HiveCoreDmaWrOnly.scala` 全程处于被并发写入状态，客观证据：

| 时刻 | 事件 | 证据 |
|---|---|---|
| 17:37:07 | sbt 编译该文件**失败** | 报错 L251 `val dmaMaxStrb = (1.U << log2Up(..)).U`（`.U` 作用于 UInt）、L257 `% dmaBytes`（Int/UInt 类型错）——**坏版本** |
| 17:38:24 | 我 Read 同区域 | 已变**好版本** `(1 << log2Up(..)).U`、`% dmaBytes.U`；mtime=17:38:22（sbt 之后） |
| 17:39:16→17:39:24 | 8 秒内两次快照 | mtime 17:39:04→17:39:21；L258 由 `<< (addrShiftBytes * 8)` 变 `<< (addrShiftBytes << 3)`，并插入空行、data/strb 赋值行位移 |
| 17:42:47 / 17:44:22 / 17:45:32 | mtime 持续推进 | `find -mmin -3` 仅命中此一个文件——定向并发编辑 |
| 17:45:40 | 后台编译器重建 class | `target/.../HiveCoreDmaWrOnly.class` mtime=17:45:40 > source 17:45:32，说明其最新状态被后台（bloop/并发 sbt）编译通过 |

- git diff 证实 L249-259 的 `dmaMaxStrb / dmaBytes / addrShiftBytes / strb / dmaExtWrIF.req.ready` 整块为**新加入功能代码**（HEAD 无、非我所写），含新输出驱动 `io.dmaExtWrIF.req.payload.strb := dmaMaxStrb << addrShiftBytes`；`dmaExtWrIF` 由 `new` 改为 `Wire(new ..)`。
- **裁定：** 该文件本轮冻结，不写入、不回退（任何 revert/checkout 都会摧毁对方在途工作）。

---

## 2. 第0/1步：前置确认与基线快照

### 2.1 第0步 — sbt 空闲 + #130 遗留清点
- **sbt 空闲确认：** `ps -Ao pid,etime,command | grep -Ei "sbt|verilator|clang"` 唯一命中是一个运行 15 天的
  `scala.meta.metals.Main`（Metals/bloop LSP 守护，classpath 里含 "sbt" 字样 jar 名而被 grep 命中），
  **非** sbt/verilator/clang 构建实例。判定 sbt 空闲，未强杀任何进程。
- **Rhys #130 遗留：** git status 有 3 个 untracked 测试文件（`HiveCoreParamDebugSpec.scala`、
  `HiveCoreQuickSpec.scala`、`HiveOutCvtSpec.scala`）+ untracked 目录 `src/hive/doc/`。经回报，leader 裁定
  **三者均为前序任务正常交付物**（分别为 #120/#126、#131、#129/#137 产物，`HiveOutCvtSpec` 属禁动区），
  **不归档、不移动、不改**；判据改为纯客观的 `sbt Test/compile` 结果。
- **#130 那 5 个被删测试文件的处置：** 已被（Rhys 或前序）归档到 `build/t130/` 下、**重命名为 `.scala.txt`**
  （Fp32Test、SystolicArrayTest、HiveCoreOnesProbeSpec、HiveCoreMultiPassSpec、HiveCoreRank1ProbeSpec、
  SystolicArrayDebugSpec、HiveCoreWeightMapProbeSpec 共 7 个），`.txt` 后缀使其不参与编译。
  **结论：工作树无编译不过的半成品 .scala，无需我再归档任何东西。**
- **Test/compile 基线：** 首次 `sbt Test/compile` 返回 `[success]`（0s，增量缓存命中），工作树可编译。

### 2.2 第1步 — 双产物基线快照
- 快照命令：`cp -R src/hive/verilog/. build/125_baseline_verilog/`、`cp -R src/hive/verilog_mx/. build/125_baseline_verilog_mx/`
  （含 `.sv`/`.zip`/`filelist.f` 及 `verification/` 子树，供后续完整逐字 diff）。
- 文件数核对（与任务口径一致）：
  - `src/hive/verilog`：**51 个 .sv + filelist.f = 52 文件，+ HiveCore_rtl.zip**（共 53 个磁盘文件）。
  - `src/hive/verilog_mx`：**46 个 .sv + filelist.f = 47 文件，+ HiveCore_mx_rtl.zip**（共 48 个磁盘文件）。
  - 两目录 `Hive_HiveCoreCalcConfig.sv` **快照时逐字一致**（`diff` 返回 IDENTICAL）。
- 这两个基线目录即 §3 重发射待办的 **diff 对照基准**。

---

## 3. ★第3步：重发射待办交接（本轮未执行，移交用户）★

> 本节是替代回归的安全网的**执行说明**。本轮因冻结未跑，接手人（用户）待文件稳定后照此执行。

### 3.1 前置条件（务必先满足）
1. `src/hive/scala/main/HiveCoreDmaWrOnly.scala` **mtime 稳定**（连续 ≥1~2 分钟无变化，`find src -name '*.scala' -mmin -3` 不再命中它）。
2. sbt 空闲（无并发 sbt/verilator 实例；注意后台 bloop/metals 守护不算）。
3. 并发编辑者的 DMA `strb` 新功能已落地到可编译状态（`sbt Test/compile` 通过）。

### 3.2 精确命令
```bash
cd /Users/wangliutailong/Qoder_Proj/Chisel_Learn
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt -J-Xmx6G "runMain HiveCoreElaborate"
```
（`HiveCoreElaborate` 会先各自清空 `src/hive/verilog` 与 `src/hive/verilog_mx` 目录内容，再 elaborate 两套配置并重新打包 zip。）

### 3.3 校验清单
- [ ] `src/hive/verilog` 刷新为 **52 文件 + HiveCore_rtl.zip**；`src/hive/verilog_mx` 刷新为 **47 文件 + HiveCore_mx_rtl.zip**。
- [ ] 两目录 `Hive_HiveCoreCalcConfig.sv` **逐字一致**：
      `diff src/hive/verilog/Hive_HiveCoreCalcConfig.sv src/hive/verilog_mx/Hive_HiveCoreCalcConfig.sv`
- [ ] **逐文件 diff 新产物 vs 基线快照**（基线 = 第1步存的 `build/125_baseline_verilog*`）：
```bash
diff -r build/125_baseline_verilog    src/hive/verilog
diff -r build/125_baseline_verilog_mx src/hive/verilog_mx
```
      （zip 为二进制，逐字 diff 无意义，可跳过或比对解包内容；重点比对全部 `.sv`。）

### 3.4 ★diff 结果的分离归因（关键）★
届时 diff **预期会同时出现两类差异，必须分开归因**：
1. **我的死码清理（§4.1）：** 删除 `lineData`/`lineValid` 两个死寄存器 + 相关注释订正。
   **预期为零功能差异**——两者从未被读取（`lineData` 甚至从未被赋值），firtool 本就会 DCE 掉，
   故对应 `.sv` **应逐字一致或仅注释差异**。若 `Hive_HiveCoreDmaWrOnly.sv` 出现**非注释**差异且能定位到
   lineData/lineValid，则说明我的清理动了功能 → **回退该项清理**（但因文件已冻结，回退须与并发编辑者协调）。
2. **并发编辑者的 strb 新功能（§1）：** `dmaMaxStrb`/`addrShiftBytes`/`strb`/`req.ready` 回接 + `Wire(new ..)`。
   **预期有真实功能差异**，集中在 `Hive_HiveCoreDmaWrOnly.sv`（新增 strb 驱动、data 移位逻辑）。
   **这些差异不属于 #125，不应归因于本轮清理**，是对方在途功能开发的产物。

> 归因方法：`Hive_HiveCoreDmaWrOnly.sv` 的差异逐条对照——凡涉及 `strb`/`addrShiftBytes`/`dmaMaxStrb`/data 移位/`req.ready` 的归对方；
> 凡涉及原 `lineData`/`lineValid` 寄存器的归我（且应为空/仅注释）。其余 51（verilog）/46（verilog_mx）个 `.sv` 若无差异，则进一步佐证我的清理零功能影响。

---

## 4. 第2步：死代码与 stale 注释清理

### 4.1 【单列节·leader 要求】HiveCoreDmaWrOnly 死寄存器删除 + 与 strb 逻辑的交织说明

**删除项（在文件被冻结前已落地，现保持原样不动）：**

| 信号 | 原声明 | 处置 | 不可达性论证 |
|---|---|---|---|
| `lineData` | `val lineData = Reg(UInt(popW.W))` | **删除声明** | 全仓 grep：除声明外**无任何赋值、无任何读取**（其余命中全为注释）。undriven + unread 的死寄存器，firtool DCE 后不进 `.sv`。 |
| `lineValid` | `val lineValid = RegInit(false.B)` | **删除声明 + 删除 `startReload()` 内的死写入 `lineValid := false.B`** | 全仓 grep：唯一写入是 `startReload()` 里 `:= false.B`，**无任何读取点**。写入无可观测效果（死寄存器的死写入）。 |
| `beatCnt` | `val beatCnt = RegInit(0.U(beatCntW.W))` | **保留（活信号）** | 参与 `lastBeat` 判据、拍内地址偏移 `curAddr + beatCnt*cBeatAddrStep` 与数据移位 `>> (beatCnt*cDmaExtW)`。**不可删。** |

- 论证依据来源：这两处死寄存器由 **#136-KS6 显式登记并明确「删除属 #125 死码清理裁定范围」**（原注释 L270、L386）。
  #122「禁动区声明」亦写明「不恢复 lineData/lineValid（行寄存职责已归 cvt，全设计唯一一份）」——删除与 #122 意图一致（删除 ≠ 接回数据路）。
- 删除的连带安全性：`popW`（仍被 L230 `bufPop` Stream 位宽使用）、`beatCntW`（仍被 `beatCnt` 使用）均未变成未用 val，无新增编译警告。
- **零功能变更的验证方式：** 本应由 §3 的重发射 diff 证明（预期 `Hive_HiveCoreDmaWrOnly.sv` 对该删除零差异）。**因冻结，该验证本轮未完成，移交 §3。**

**与并发编辑者 strb 新功能的交织关系（给对方/用户的知情说明）：**
- 我的删除位置：寄存器声明区（原 L287-289，现 `beatCnt` 在 L301）、`startReload()`（原 L390 死写入）、及文件头/中段 5 处注释块。
- 对方 strb 新功能位置：`Internal IF` 区（L249-259：`dmaExtWrIF`/`dmaMaxStrb`/`dmaBytes`/`addrShiftBytes`/`strb`/`req.ready`）。
- **两者在文件内物理位置相邻但不重叠**：我的删除在 `beatCnt`/`startReload` 与注释，对方的新增在 `dmaExtWrIF` 内部 IF 驱动。
  截至冻结时（17:45:40 后台编译通过），**两者共存且编译通过**，未见语义冲突。
- ⚠️ 提示接手人：对方 strb 逻辑里 `dmaExtWrIF` 已从 `new` 改为 `Wire(new ..)`，并新增 `dmaExtWrIF.req.ready := io.dmaExtWrIF.req.ready` 回接——
  这会改变 `Hive_HiveCoreDmaWrOnly.sv`，重发射 diff 时**务必把这部分差异归对方、不要误判为我的清理破坏了功能**。

### 4.2 裁定保留（不删）：runQuantGemm 死函数 + 其内死 val
- **对象：** `HiveCoreSimCaseQuantSpec.scala` 的 `private def runQuantGemm`（L235 起，约 465 行）+ 其内 `cColOff`/`beatStep` 两个死 val。
- **确认死码（不可达性成立）：** 全部 `it should` 调用者（L65-97）位于 **L58–L128 的 `/* */` 块注释内**，无任何活跃调用；现役量化用例走 `runMxQuantGemm`（L177/184/218）。L490 原注释亦自陈「本函数为历史死代码」。
- **裁定：保留不删。** 理由（已就地写入 QuantSpec L231、L370 注释留痕）：
  1. 函数体内装有 **#136-KW2「死码复活硬闸」**（require：复活即因 beat 口径分裂而在金锚构造期高声报错）+ L58 块注释的复活重写清单，构成「**有意保留、复活即报错**」的归档设计；删函数会连带删掉该保护闸。
  2. 函数体保存了 legacy beat 口径（`beatsPerRow = totalN/elemE`）的金锚推导链，具取证/历史对照价值。
  3. **本轮无重发射 diff 兜底、且禁跑回归**——测试树语义变更无任何客观安全网可验证；对拿不准的删除一律不做（符合「宁可少删」口径）。
- 就地留痕：QuantSpec L227-231、L367-371 已更新为「#125 裁定：保留不删」，避免后续审计重复flag。

### 4.3 登记不删（拿不准 / 属禁动区 / 属他任务域）
| 疑似项 | 位置 | 不删原因 |
|---|---|---|
| 「已由 #139 承接」措辞（4 处留痕） | `HiveCoreDmaRdOnly.scala` L94、`HiveCoreCalcConfig.scala` L334、`HiveDebugModel.scala` L412-414、`HiveCoreParamDebugSpec.scala` L73-75/L298-299 | #139 已取消，措辞过期，但：①属**任务状态**陈旧而非**RTL 行为**陈旧（不在本轮 stale 注释严格范围）；②紧邻禁动区（金锚 bKTileOff、护栏 G1/G2/G3）；③ `build/139_deferred_note.md` L83-85 明确「本轮不做文字改动，将来重启时一并订正」。**登记，不动。** |
| `runQuantGemm` 整体删除 | QuantSpec | 见 §4.2，裁定保留。 |
| RdOnly n-tile 累加模型 `lastBTileStartAddr` | `HiveCoreDmaRdOnly.scala` | 属 #139 功能缺陷域（K 非 bKpb 整数倍漂移），非死码，禁动。见 §5B。 |

### 4.4 本轮实际订正的 stale 注释清单（均为注释，零功能影响）
> 注：以下 6 处中，位于 `HiveCoreDmaWrOnly.scala` 的 5 处属冻结文件——它们在冻结**之前**已随死寄存器删除一并订正落地，冻结后未再改动。

| # | 文件 | 原失真注释 | 订正为 |
|---|---|---|---|
| 1 | HiveCoreDmaWrOnly L164-175（反压策略头） | 「本模块的 lineData/lineValid **已成死寄存器**（登记见下方，删除属 #125 范围）」 | 「原有 lineData/lineValid 死寄存器**已由 #125 删除**」 |
| 2 | HiveCoreDmaWrOnly L270-291（#136-KS6 登记段） | 标题「死寄存器**登记（只登记不动）**」+ 三行声明描述 | 标题「死寄存器**清理（#125 已执行删除）**」+ 补不可达论证，`beatCnt` 标注「活，保留」 |
| 3 | HiveCoreDmaWrOnly L305-312（P2 ActiveRowSplit 段） | 「上方 lineData/lineValid **已是死寄存器**，三行声明里只有 beatCnt 是活的」 | 「原有两个死寄存器**已由 #125 删除**，此处只保留活的 beatCnt」 |
| 4 | HiveCoreDmaWrOnly L328（拍推进注释） | 「末拍**清行锁存**并回零拍计数」（本模块已无行锁存可清） | 「末拍回零拍计数（本模块**无行锁存可清**）」 |
| 5 | HiveCoreDmaWrOnly L361-392（startReload 注释 + 死写入） | 「lineValid 确在本函数被写 false.B…删除属 #125 范围」+ 实际死写入 `lineValid := false.B` | 改过去式说明该死写入「已由 #125 连同声明删除」，**并删除该死写入语句** |
| 6 | QuantSpec L227-231 / L367-371 | 「是否整体删除属 #125 裁定范围，本任务不动」 | 「**#125 裁定：保留不删**」+ 三条理由 |

- **未触碰的 #124/#137/#133 已订正项**（按禁改要求）：HiveCore.scala L162-169 偏移语义速查块、HiveOutCvt 的 5 处 #137 订正、#133 已清理的死引用与 6+ 处注释——本轮均未重复改、未回退。

---

## 5. 第4/5步：.gitignore 裁定与登记项

### 5.A 第4步 — .gitignore 裁定（仅改文本，未执行任何 git add/rm/commit）

**裁定原则：** 可再生产物忽略；不可再生的取证/裁定记录入库。

| 类别 | 具体 | 裁定 | 理由 |
|---|---|---|---|
| 可再生产物 | verilator 中间产物、`build/chiselsim/**`、回归日志（`*_regression*.log`/`baseline_*.log`）、基线快照（`build/125_baseline_*`）、`build/t130/` 归档 | **忽略** | 重跑即可复现、体积大、diff 噪声 |
| 可再生产物 | FST/VCD 波形（`*.fst`、`sim/`） | **忽略** | 每次仿真重生成、二进制噪声 diff |
| 交付产物 | `src/hive/verilog*` 下 101 个 `.sv` + `HiveCore_rtl.zip`/`HiveCore_mx_rtl.zip` | **保持跟踪（不忽略）** | 沿用 #118 S3 裁定：有意入库的交付物，下游综合/交付直接取用；`git check-ignore` 退出码 1、`git ls-files` 全部 tracked |
| 取证/裁定记录 | `build/124_arch/`（pre672/old 源码快照 + calcconfig_rtl_diff + pre/mid CalcConfig.sv）、`build/124_causal_revert*.log`、`build/139_deferred_note.md`、`build/141_int_datapath_handoff.md`、`build/125_wrap_up_report.md` | **应入库** | **不可再生**：因果实验日志与旧版源码快照是一次性破坏性实验（回退代码验因果）的唯一证据、无法重跑复现；推迟项笔记与收口报告是后续审计权威依据，丢失即致重复发现/重复推导 |

**已执行的 .gitignore 文本修改（3 处）：**
1. **删除死行 `.src/**/verilog*`**（原 L28）：前导点使其从未匹配真实路径 `src/…`（`git check-ignore` 退出码 1、101 个 .sv 与 zip 全部 tracked），且意图与仓库约定相反，属死配置（沿用 #118 S3 裁定）。原位置改为一段说明性注释，防止后人误加同类忽略规则。
2. **`build/` → `build/*` + 取证记录 negation**：因 git 无法在「父目录被整体排除」时重新纳入其中文件，改为排除到直接子级，再用 `!build/124_arch/`、`!build/124_causal_revert.log`、`!build/124_causal_revert2.log`、`!build/139_deferred_note.md`、`!build/141_int_datapath_handoff.md`、`!build/125_wrap_up_report.md` 重纳取证记录。
3. **新增全局 `*.fst`**：显式声明波形永不入库（兜底 sim/ 之外的误提交）。

**git check-ignore 验证结果（全部符合裁定）：**
- 取证记录（124_arch/pre672_Executor.scala、124_causal_revert.log、139/141 笔记、125 报告）：exit=1（**可跟踪** ✓）
- 可再生产物（chiselsim、125_baseline_*、124_full_regression_final.log、t130/*.txt）：exit=0（**已忽略** ✓）
- 交付产物（Hive_HiveCore.sv、两个 zip）：exit=1（**可跟踪/未被忽略** ✓）

**⚠️ 待批 git 动作（#125 只裁定不执行，需用户批准后方能动）：**
- 3 个**已被跟踪**的 FST 波形应脱离版本控制（裁定为可再生产物）：
  `git rm --cached sim/loadw/hivecore_loadw_sim.fst sim/mx/hivecore_mx_multipass_sim.fst sim/mx/hivecore_mx_sim.fst`
  （`sim/` 与 `*.fst` 规则只能阻止**未来**新增，对已 tracked 文件无效，必须 `rm --cached` 才真正脱离。）
- 取证记录现已被 .gitignore 放行（变为可跟踪），但**是否 `git add` 入库由用户决定**（我未执行 add）。
- 次要观察：`sim/` 下 11 个 matrix `.txt`（A/B/C_matrix 及 _float 变体）目前亦被跟踪，属仿真 I/O dump（原则上也可再生）。任务未点名，**仅登记观察**，建议用户一并裁定是否 `rm --cached`；本轮不动。

### 5.B 第5步 — 四个推迟项 + #130 终止留痕（登记，避免后续审计重复发现）

> 留痕位置：以本报告为准（权威）；`build/139_deferred_note.md`、`build/141_int_datapath_handoff.md` 为对应详情笔记（已被 .gitignore 放行入库）。
> 按 §4.3 裁定，本轮**不在源码内新增 scattered 注释**（避免触碰禁动区、且 #139 笔记已声明其留痕措辞本轮不动）。

| 项 | 状态 | 摘要 | 详情/权威出处 |
|---|---|---|---|
| **#140** | 推迟 | errCode 0/1/2 未分立、V2/V5 未恢复；非法配置/格式校验收口推迟至此。QuantSpec blk32-over-totalN 既存红属此域 | 任务描述 + `important_decision_experience`「HiveCore errCode 裁定：三路 blk 违约合并为 0 是权威语义」 |
| **#141** | 推迟（用户手动修） | INT8 lane 半拍解包错位（数据路，非地址路）+ `isInt` 判据缺 INT32/INT4（HiveWorkUnit L442 仅 INT8‖INT16，与 HiveEnum L16 口径不一致）。地址路 #124 已裁定正确、勿动；护栏 G1/G2 的 C 数据断言当前**刻意弱化为只打印**，修完数据路后须恢复硬断言 | `build/141_int_datapath_handoff.md`（全量根因链 + 候选插入点 + 修复红线） |
| **#138** | 推迟（用户裁定取消） | INT4 输出端到端未落地（金锚半字节数据模型 + INT4×偶N 正向 E2E + MxSpec controlWord 复核）。**RTL 支路 #137 已实现且有单元覆盖**，仅缺端到端 | 任务 #138（cancelled）；#137 已落地 HiveOutCvt INT4 臂 |
| **#139** | 推迟（用户裁定取消） | K 非 bKpb 整数倍时 `lastBTileStartAddr` 累加模型的 n-tile 跳转残余偏差（一般式 term1 = nt×bEb×pad）。**软件层保证输入对齐故当前不可触发**；全仓 loadW=1 活跃用例 K∈{16,32,64} 均 bKpb 整数倍 ⇒ pad=0 ⇒ 零覆盖。金锚为权威侧（非同源共享该错） | `build/139_deferred_note.md`（含最小复现配置 m16/n32/k24、越界事实、重启第一步） |
| **#130** | 终止（用户裁定） | 恢复 672fb37 删除的 Fp32.add / HiveCell 单元覆盖被终止。那 5 个被删测试文件已归档为 `build/t130/*.scala.txt`（不参与编译）。Rhys 两次未回交接清单 | 任务 #130（cancelled）+ §2.1 |

### 5.C 第5步 — 金锚硬编码 `bW/8` 列偏移：登记 + 同源化裁定（本轮不实施）

**登记（5 个测试文件的金锚仍独立硬编码 `cfg.bW/8`（=2）计算列偏移，行号经本轮 grep 复核）：**
- `HiveCoreSimCase.scala`：`bColTileOff = totalN*(cfg.bW/8)` 于 **L155 / L672 / L1052**（另 aRowOff/bRowOff 多处同族）。
- `HiveCoreSimCaseQuantSpec.scala`：`bColTileOff` **L330 / L800**、`aColOff` **L1142**、`kSliceStep = totalN*(cfg.bW/8)` **L1166**。
- `HiveCoreSimCaseMxSpec.scala`：`aColOff` **L242**、`kSliceStep` **L271**。
- `HiveCoreSimCaseLoadWSpec.scala`：`bColTileOff` **L121**、`kSliceStep` **L122**（+ 头注释 L7-11/L111-116 同族）。

**隐形机制（为何长期不红）：** 这些用例所用格式（FP16/BF16/MX8/INT16）的 `elemsPerLane × memElemBytes = 2`，恰等于 `bW/8 = 16/8 = 2`，
故金锚旧式 `totalN*(bW/8)` 与 RTL #124 新式 `totalN × elemsPerLane × memElemBytes` **数值恒等**，永不报红。
但 INT8（`elemsPerLane×memElemBytes=1`）/INT32（`=4`）下二者不等——正因无任何用例覆盖 INT8/INT32 列偏移，该地址缺陷长期隐形（与 #141 数据路缺陷叠加）。

**裁定：本轮不同源化、不实施。** 依据：
1. **金锚必须保持独立推导，不得变成对 RTL 的反向拟合。** 本项目有惨痛教训（memory「金锚以 RTL 行为为拟合目标导致 dmaC 地址断言恒真假绿」）：一旦金锚直接调用/复制 RTL 表达式，断言退化为恒真，失去发现缺陷的能力。
2. **同源化若要做，只能以「共享语义常量」而非「共享 RTL 表达式」的形式实现**：即抽取一张 `格式 → (elemsPerLane, memElemBytes)` 的**语义真值表**（独立于 RTL 与金锚双方的权威常量源），RTL 与金锚各自**独立**消费该表并独立推导列偏移。绝不能让金锚去 `import`/调用 `HiveCoreCalcConfig.aColTileAddressOffset` 之类的 RTL 侧表达式。
3. **当前收益近零、风险非零：** 在 INT8 数据路（#141）修好前，INT8 端到端本就跑不过，单独同源化地址金锚**不增加任何抓错牙齿**；反而改动 5 个文件的金锚推导有引入回归的风险，且本轮无重发射 diff/无回归兜底。
4. **建议的正确顺序（留给用户）：** 先修 #141 数据路 → 补 INT8/INT32 列偏移的 `strictAddr=true` 护栏用例（此时旧 `bW/8` 硬编码会**变红**，暴露缺陷）→ 再评估是否引入「共享语义常量」表做同源化。**先让缺陷可见，再谈去重。**

### 5.D src/hive/doc/ 未跟踪目录内容概要（leader 要求记一笔）
- `src/hive/doc/`（untracked 目录）仅含 1 个文件 `problem.md`（239 B，今日 17:19 创建，非我创建）。
- 内容为**用户手记的待办问题**（原文概要）：
  > 「1. 在 16×16 fp16 算力矩阵下，当前运行 a/b 的 blocksize 如果是 16 是不对的，因为一行理论是两个 scale 系数，但当前仍只读一个，TB 也认为是对的需要修改。（暂定不支持，后续修改）  2.（空）」
- 性质：用户自己的问题备忘录，**不属 #125 清理范围，未触碰**。仅登记其存在与内容概要。该问题（MX scale blocksize=16 一行应读两个 scale 系数、当前只读一个且 TB 误判为对）与 #141/MX 通路相关，**建议后续单独立项**，本轮不展开。

---

## 6. 收尾 Test/compile 结果（leader 第7条）
- `sbt -J-Xmx6G "Test/compile"`（17:44:48）返回 **`[success]`**（Total time 0s，zinc 增量缓存命中）。
- **我改动过的文件编译安全性：**
  - `HiveCoreSimCaseQuantSpec.scala`：**纯注释**改动，test-class 于 17:37:52 编译产出，编译安全。
  - `HiveCoreDmaWrOnly.scala`（冻结文件）：我的改动 = 删 2 个未用 val + 删 1 处死写入 + 注释；已 grep 确认无任何代码引用这两个 val，删除**在构造上不可能破坏编译**。该文件当前（含对方 strb 新代码）由后台编译器于 **17:45:40 重建 class（> source 17:45:32）**，表明其最新状态编译通过。
- **报错归属：** 本轮唯一一次编译失败（17:37:07）报错落在 `HiveCoreDmaWrOnly.scala` L251/L257，**属并发编辑者当时的半成品 strb 代码，非我责任、按冻结规则不修**；该坏版本随后已被并发编辑者自行修好（17:38 起即为好版本）。
- 说明：0s 缓存命中不能 100% 代表对冻结文件最新态的从头重编译；对该文件的确定性编译验证随重发射一并移交 §3（文件稳定后执行）。

---

## 7. 交付物清单
| 交付物 | 路径 | 说明 |
|---|---|---|
| 本报告 | `build/125_wrap_up_report.md` | 收口权威记录（已被 .gitignore 放行入库） |
| 双产物基线快照 | `build/125_baseline_verilog/`、`build/125_baseline_verilog_mx/` | §3 重发射 diff 的对照基准（可再生，保持忽略） |
| 死寄存器清理 | `src/hive/scala/main/HiveCoreDmaWrOnly.scala` | 删 lineData/lineValid + 6 处注释订正（冻结文件，冻结前落地） |
| runQuantGemm 裁定留痕 | `src/hive/scala/test/HiveCoreSimCaseQuantSpec.scala` | 注释级：裁定保留不删 |
| .gitignore 裁定 | `.gitignore` | 删死行 + build/* 重纳取证记录 + *.fst（未执行任何 git add/rm/commit） |

## 8. 移交用户的待办（汇总）
1. **【主】重发射 + 零功能变更证明**（§3）：待 `HiveCoreDmaWrOnly.scala` 稳定后执行，diff 须分离归因我的清理 vs 对方 strb 功能。
2. **【待批 git 动作】**（§5.A）：3 个 tracked `.fst` 的 `git rm --cached`；取证记录是否 `git add` 入库；11 个 matrix `.txt` 是否一并处置。
3. **【推迟项】**（§5.B）：#140/#141/#138/#139 按各自笔记在将来重启；#130 已终止、5 文件已归档 `build/t130/`。
4. **【金锚同源化】**（§5.C）：先修 #141 → 补 INT8/INT32 列偏移 strictAddr 护栏 → 再评估「共享语义常量」式同源化。
5. **【MX blocksize 问题】**（§5.D）：`src/hive/doc/problem.md` 记的 scale blocksize=16 只读一个系数问题，建议单独立项。
