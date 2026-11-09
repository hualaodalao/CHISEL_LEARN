# WorkerBee M=1 输出驻留模式实施计划：方案 2

状态：S1–S12 已完成；正式规格 v4.2.0、定向/全量回归及唯一默认 RTL 结构审计通过。

更新日期：2026-11-09。

需求来源：用户方案 2，以及对 B DMA、列缓存、独立首行 PE、阵列层级和计划表达形式的修订。

范围：WorkerBee GEMM；实施边界按第 10 章，正式行为契约见 spec.md。实际周期账与测试命令见 build/s11_acceptance.txt，不执行 git 写操作。

## 1. 方案结论

采用 **A 非广播、沿第 0 行向右脉动；B 按 N×K 转置布局升序读取，分发到每列两个窗口槽的 ping-pong buffer；每个窗口槽保存一拍 256-bit B 和配对的 32-bit scale word，读端再按 16-bit packed 字逐拍供给本列 PE；首行各列本地累加，全 K 完成后统一交付**。

设计结论如下：

1. OS 的 B 数据及 scaleB DMA 块内均按输出列升序读取；WS 保持既有降序。物理列 j 对应逻辑列 nt×16+j，取消额外的 B/C/bias 列反转。
2. 每列不是“深 16、字宽 16-bit”的元素 FIFO，而是**深 2、窗口宽 256-bit 的 FIFO**。一个槽就是该列一个 K 窗口的全部 16 个 packed 字；lane 指针在当前槽内每次取 16 bit，消费完第 16 个字后才弹出整个窗口。另一个槽可同时预取下一窗口。
3. scaleB 缓存与 B 窗口同样是每列深 2。每槽保存同一列、同一 K 窗口的完整 32-bit scale word；消费时按 lane、g 和 blockB 选择其中的有效 E8M0 字节。优先把 B 与 scale 合并成一个窗口 Bundle 原子入队，避免两套 ping-pong 的槽号失配。
4. 首行另写专用双模式 PE 模块，与原 WS WbWorker 区分；替换首行原有 16 个 PE，每个只保留一份 WbWorkUnit。其余 240 个使用原模块，总 MAC 数仍为 256。
5. WbComb 直接例化 T×T PE，不再通过 2×2 WbCell 拼接。保留当前 WS 的寄存边界、skew/deskew 和控制取点，不借扁平化改变 WS 行为；不顺带删除 WbCell。

正常 Run 阶段采用用户要求的保守门控：**A 有数、16 列当前窗口均有效，才统一推进；MX 时 scale 已包含在同一有效窗口中**。必须有一个末尾例外：全 K 的最后 A 已注入后，不再要求入口有新 A，已经完成的列也不再要求当前窗口有效，否则无法排完右侧尾波。

不将外部带宽作为否决依据。容量、逻辑推进拍和端到端实测周期分别核算，不预先承诺 PPA 或速度提升。

## 2. 模式与形状契约

| 项目 | 拟议约定 |
| --- | --- |
| 模式位 | CONTROL[31] 新增 osM1，显式开启，不根据 M 自动切换 |
| 形状 | M=1、loadWMode=1；保持既有 K 粒度和 N 补齐契约 |
| A | 原 M×K 行主序，无需转置；捕获一拍后依次发送完整 packed 字 |
| B | Nprog×K 行主序，即 BT[n][k]=B[k][n]；OS 块内自然列序 |
| 空间轴 | 仅全局第 0 行参与 OS，列对应输出 N；K 在时间上推进 |
| B 工作集 | T 路窗口 FIFO，每路深 2、每槽 T×bW；默认 16 路×2槽×256 bit |
| scaleB 工作集 | 与 B 窗口同槽配对；每槽保存完整 scaleBW，默认 32 bit |
| 槽内消费 | lane 指针每个有效 MAC 取一个 bW 字；默认 16 次后弹出当前窗口 |
| 累加 | 本地 psum 反馈跨全部 K 窗口保持，不回灌中间 C |
| 输出 | 每个 N tile 最终一个含 T 个 cEffW 字的 C 行，默认 16×40 bit |
| 格式 | 既有合法 BF16、INT、MX、纯 FP8、W4A8 与 epilogue 契约保持 |
| 外部接口 | 端口宽度、张量布局和 DMA credit 不变；仅增加 OS B/scaleB 地址遍历分支 |
| 非 OS | 原 WS 功能、加载节奏与数值路径保持 |
| 模式错误 | 拟使用 errCode=14，拒绝 osM1 下 M≠1 或 loadWMode=0 |

T=cfg.totalN，g=本次运行每个 packed 字覆盖的 K 元素数，W=K/(T×g)。默认 T=16；深度从既有几何和最大 scale 数派生，不新增任意深度参数、寄存器槽位或 hasOsM1 开关。

OS 不新增 K 尾块处理：所有合法格式的 K%kPerBeat==0 已由既有 errCode 8 强制（K 粒度契约），窗口与 lane 语义只在整除前提下成立；实施不得另造窗口级尾块逻辑。

错误码 14 放在既有判决之前；已有格式、K、输出、bias/requant 等违约保持原优先级并可覆盖它。软件镜像和多错误组合测试同步。

### 2.1 N 补齐沿用现有契约

依据 [spec.md:783](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/doc/spec.md#L783)：

```text
Nprog = ceil(Nlogical / T) × T
寄存器 N = Nprog
```

B/C/bias/rqM/相关 scale 按 Nprog 分配，软件只消费前 Nlogical 列。逻辑 N=1/17 的测试分别编程 Nprog=16/32；不实现末 tile 自动缩窄 byte strobe。padding 与 gather 覆盖的相邻物理字必须可访问，不能用测试存储缺省返回零证明真实越界安全。

## 3. 当前源码约束与修改落点

| 位置 | 已核实行为 | 修改方向 |
| --- | --- | --- |
| [WorkerBeeRegIf.scala:69](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeRegIf.scala#L69) | CONTROL bit31 空闲 | osM1 读写，保留 inFlight 写保护 |
| [WbInterface.scala:111](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WbInterface.scala#L111) | 执行配置 Bundle | 增加模式位，不新增参数寄存器 |
| [WorkerBeeCalcConfig.scala:162](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeCalcConfig.scala#L162) | last-connect 错误判决 | 增加模式守护，保持现有地址步长计算 |
| [WorkerBeeDmaRdOnly.scala:301](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeDmaRdOnly.scala#L301) | B 从最高行起步，块内减 rowStep | OS 修改起点、递增和 K/N 跳转，不能只把减法改加法 |
| [WorkerBee.scala:420](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBee.scala#L420) | scaleB 同样复用 RdOnly，isA=false | 与 B 数据使用同一个 OS 升序判据 |
| [WorkerBeeScratchpad.scala:45](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeScratchpad.scala#L45) | B 是单队首宽 FIFO | 保留上游预取，每个 256-bit head 整拍写入目标列的一个窗口槽 |
| [WorkerBeeConfig.scala:140](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeConfig.scala#L140) | scaleBW 默认 32 bit，可携带最多 4 个 E8M0 字节 | 整个 scale word 与对应 B 窗口进入同一槽，不拆为字节 FIFO |
| [WbWorker.scala:147](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WbWorker.scala#L147) | 每 PE 一份纯组合 WbWorkUnit | 原模块保持；另写首行双模式 PE，共用一份算术 |
| [WbCell.scala:95](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WbCell.scala#L95) | 内部 n×n PE，横纵链直接连接 | WbComb 内移到全局坐标接线；不删除独立模块 |
| [WbComb.scala:89](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WbComb.scala#L89) | 2×2 WbCell 层级 | 改为 T×T 直接例化，首行专用，其余原 WS PE |
| [WbComb.scala:236](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WbComb.scala#L236) | A 跨 Cell 没有额外边界寄存拍 | 扁平化不增减 A 延迟；OS 第 j 列延迟 j 个推进拍 |
| [WorkerBeeExecutor.scala:244](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeExecutor.scala#L244) | wbValidOut 当前直接触发 C push | OS 最终 commit 一次，不沿用每拍推送 |
| [WorkerBeeRtlArraySpec.scala:34](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/gem5/tests/WorkerBeeRtlArraySpec.scala#L34) | 测试探针访问 arrays(...).pes(...) | 随扁平结构更新测试探针，不加兼容层恢复旧层级 |

拟新增 feeder、双槽列窗口 FIFO、首行专用 PE 及局部测试模块均尚未创建。知识卡中的旧模块名、旧寄存器数与多套 RTL 描述不作为本计划依据。

## 4. 列 ping-pong 窗口数据流

### 4.1 A 串行化

原 A FIFO 每拍 T×aEffW。捕获后按 lane=0…T−1 依次发送一个完整 aEffW 字：

- BF16：g=1，一个 16-bit 字覆盖一个 K 元素。
- INT8/MXFP8/纯 FP8：g=2，一个字覆盖两个 K 元素。
- 同格式 INT4/MXFP4：g=4，一个字覆盖四个 K 元素。
- W4A8：B 使用既有 DMA stretch 展开结果，按 A 的 g=2 消费，不另做标量拆分。

g 取 DataFormat 的权威映射及既有 W4A8 口径，不另立格式常数。A 与 scaleA 原子捕获；末字消费与下一 A 拍捕获可同拍，旧字消费旧 scale，新状态优先。A 预取在阵列停顿时仍可进行，只要 A 捕获寄存器有空间。

### 4.2 OS B 与 scaleB DMA 改为升序

物理列 j=0…T−1 对应 logicalN=nt×T+j。以 B 转置矩阵的字节行步长 R、K 窗口字节步长 S 表示，独立地址契约为：

```text
B逻辑拍地址(nt, kt, j) = baseB + (nt×T+j)×R + kt×S
B到达拍0 → 列0，固定输出 nt×T+0 的连续 K 数据
B到达拍1 → 列1，固定输出 nt×T+1 的连续 K 数据
...
B到达拍T−1 → 列T−1
```

遍历仍为 nt 外层、kt 中层、j 内层。实施时使用已有 rowOffset/colTileOffset，不改变布局：

- 首块第一列从 base 开始，不再补 (T−1)×R。
- 块内逻辑 finalReqFire 后地址加 R；gather 的中间物理读不推进逻辑列计数。
- 切 K 窗口：当前第一列锚点加 S，再从 j=0 扫描。
- 切 N tile：锚点取 base+(nt+1)×T×R，kt/j 复位；不把末 K 的偏移带入下一 tile。
- OS start/重启在相关状态下同源初始化起点、计数与 credit；不顺带重构 WS 异常流程。

scaleB 使用同样的公式，分别替换 base、R、S：Rscale=K/blockB，Sscale=T×g/blockB，单位都是字节。[WorkerBeeCalcConfig.scala:599](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeCalcConfig.scala#L599) 已有对应步长，W4A8 覆盖为 A 窗口口径。

只对 !isA && osM1 的合法模式启用新遍历。WS 降序、A 升序、stretch/gather、响应配对和一逻辑拍一 credit 契约保持。W4A8 逻辑片地址仍先转换成对齐物理读及半字选择，地址测试必须区分这两层。

B、scaleB、bias、rqM 和 C 均使用自然逻辑列序。C 仍沿用低列号低位的原接口打包；原 Cat(reverse) 是位拼接次序，不是额外语义反转。

### 4.3 每列深 2 的窗口 FIFO

上游一个 B 逻辑拍属于固定输出列，包含该列一个 K 窗口的全部 T 个 packed 字。默认 T=16、bW=16，因此一个窗口恰为 256 bit。每列维护两个窗口槽：

```text
第 j 列窗口FIFO（depth=2）

head槽：B[255:0] + scaleB[31:0] + 窗口标识
          │
          ├─ lane=0  读取 B[15:0]
          ├─ lane=1  读取 B[31:16]
          ├─ ...
          └─ lane=15 读取 B[255:240]，随后弹出整个head槽

next槽：可在head消费期间预取下一K窗口
```

深 2 指两个**窗口**，不是两个 16-bit 元素。写端每次只写一个完整槽，读端在当前槽内通过 lane 指针选择 bW 位；lane 只随本列真实 Consume 前进，暂停时保持。lane=T−1 被消费后，窗口 FIFO 才 pop，next 槽自动成为新 head，lane 复位为 0。

同拍消费旧 head 的 lane=T−1 并写入一个新窗口时，MAC 必须读取旧 head；时钟边沿后 next/新入槽再按 FIFO 次序生效。深 2 允许当前窗口消费与下一窗口预取重叠，不需要在“最后一个 16-bit 字”层面实现 T 项批量写口，也不需要第二套完整 N tile bank。

上游分发顺序仍是 kt 外、j 内：第 kt×T+j 个 B head 写入第 j 列的一个空窗口槽。只有目标列窗口 FIFO 未满时才 pop 上游 B；真实入队后才递增 j，j=T−1 后切 kt。未来 N tile 数据继续留在原上游 FIFO，不能在每个 N tile flush 已预取数据。

### 4.4 scaleB 与 B 共用窗口槽

scaleB 上游每个逻辑拍物理宽度是 scaleBW，默认 32 bit，已经包含该输出列、该 K 窗口最多 4 个 E8M0 字节。因此 scale 也只需两个窗口槽，不需要另建深 4 的字节 FIFO。

| 输入窗口 | g | blockB | scale word 中有效字节 | 槽内选择 |
| --- | ---: | ---: | ---: | --- |
| MX8 或 MX W4A8 | 2 | 16 | 2 | lane 0–7 取 byte0，lane 8–15 取 byte1 |
| MX8 或 MX W4A8 | 2 | 32 | 1 | lane 0–15 取 byte0 |
| 同格式 MX4 | 4 | 16 | 4 | 每 4 个 lane 切换一个 byte |
| 同格式 MX4 | 4 | 32 | 2 | 每 8 个 lane 切换一个 byte |

消费时直接从当前槽的 scale word 选择 `byteIdx=floor(lane×g/blockB)`；scale 不单独 pop，只在整个 B 窗口 pop 时一起释放。高于有效字节数的物理读回位保持 don't-care，不参与选择，也不能被解释为下一窗口的 scale。

B 与 scaleB 两个上游宽 FIFO 可以独立预取，但 MX 分发必须等两侧 head 都有效，再把 `{B window, scale word, kt}` 原子写入同一个目标列槽并同时 pop 两侧。优先实现为一个深 2 的 Bundle FIFO，而不是两套依靠隐含同步的 ping-pong；这样 valid、head/tail 和槽号只有一份。非 MX 槽不等待 scale DMA；纯 FP8 使用常数 127，BF16/INT 忽略 scale 字段。

### 4.5 ping-pong 的能力边界

两个窗口槽提供的是**消费与预取解耦**：head 可向 PE 逐字供数，next 同时接收下一 K 窗口。它不是双 N tile bank，也不改变外部 B FIFO 的深度和 DMA credit。

原 B FIFO 仍作预取，默认 64×256 bit。[WorkerBeeScratchpad.scala:61-63](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WorkerBeeScratchpad.scala#L61) 的 pop.fire 表示一个完整窗口已转移到某列槽，并返还 DMA 一拍 credit；MX scaleB 的 pop.fire 与其原子配对并独立返还 scale DMA credit。

不增大 bBufferDepth，不改 utils 的 StreamFifo，不合并上游存储所有权。若 DMA 长期平均供数低于阵列消费，两个槽只能延后 stall，不能消除速率差；这仍由周期实测统计。

## 5. 全阵列停顿、启动与末尾排空

### 5.1 Run：严格全 16 路门控

开始计算前等待全部 T 路 B 就绪及入口 A 就绪；MX 同时要求所需 scale 就绪。首个 A 进入列 0，其后每次逻辑推进向右传一列，尚未到达的列保持 invalid，不提前 pop B。

```text
RunAdvance = AHeadValid
          && AND_j(WindowFifo[j].headValid)
Consume[j] = RunAdvance && LocalAToken[j].valid
```

MX 的 scale word 已和 B 保存在同一 head 槽，所以不再有第二个运行期 scale 非空条件。这里 AHeadValid 指已捕获的当前 packed 字，不要求外部 DMA 正在同拍响应。临时 A 不足时不注入 bubble 推进旧波前，而是按用户要求整行暂停；A/B/scale 窗口的预取和 next 槽装入仍可继续，不被 RunAdvance 禁止。

RunAdvance=0 时 A、valid、scaleA、结束标记、各列 lane 指针和 psum 全部保持；不能只拉低入口 valid，也不能让旧 token 重复 MAC。每列 lane 只随本列 Consume 前进，不能要求 16 路每次都弹出窗口，否则启动波前会丢 B。

### 5.2 K 窗口边界：head/next 无缝切换或整行停顿

列 0 会比列 j 提前 j 个推进拍耗尽当前 head。若该列 next 已有效，消费 lane=T−1 后下拍直接切到 next，不需要等该列先变空再装载；分发器可以在 head 消费期间继续填充空闲的 next 槽。

如果任一列在需要新窗口时没有有效 head，Run 全行停顿；上游分发仍可补入其空槽。全 16 路 headValid 比仅检查有效 token 所在列更保守，但采用此要求，不暗中改成后者。两个窗口槽只提供有限预取，不保证消除 DMA 控制空泡或长期平均供数不足。

组合方向固定为：已寄存 head/valid → RunAdvance → Consume/lane递增 → 窗口末项pop → enqueue ready。RunAdvance 不依赖本拍新窗口入队 fire，避免组合环和未声明旁路。

### 5.3 Tail：两项终止豁免不可省略

假设最后一窗口 16 个 A 字已经注入，列消费数量依次为：

```text
列0…列15：16、15、14、…、1
```

此时列 0 的 B 已为空，入口也没有新 A，但列 15 还有 15 个有效 token 未消费。如果继续要求“全部 B 非空且入口 A 有数”，即使所有外部输入已经正常到齐，也会永久停住。

因此尾阶段使用：

```text
TailAdvance = 尚有在途有效A token
           && AND_j(ColumnDone[j] || WindowFifo[j].headValid)
```

ColumnDone 只在本列真实消费全 K 最后 token 后置位；不能在普通 K 窗口末尾置位。Tail 不再索取新入口 A，推进 invalid 入口及已有 token；完成列不再递增 lane 或弹出窗口。所有列最后结果写入 psumReg 后的下一拍才能统一 commit。

Tail 仅发生在完整 K 末尾一次，不在每个 K 窗口 drain。该例外不放宽 Run 的全路门控，而是确保有限输入任务能终止。

### 5.4 已完成的计划层时序核验

使用独立离散时序模型，从张量坐标生成 A/B token 和 block scale 期望值；并非从 RTL 出口反推金锚。模型覆盖：

- 1/2/4/64 个 K 窗口，以及 scale word 内 0/1/2/4 个有效字节。
- A、B、scale 独立随机供数延迟；每列最多两个窗口槽，窗口内逐 lane 消费。
- B 与 scale 原子成槽、head/next 切换、FIFO 满反压和 Tail 排空。
- 512 组新的 ping-pong 随机案例全部完成；每列消费数为 K/g，窗口/scale 配对正确，末尾槽全部释放。
- 连续源下逻辑计算与尾波推进数仍为 K/g+15；DMA 平均供数不足产生的真实时钟 stall 另计。
- 此前 1,024 组元素 FIFO 模型结果已被本轮窗口模型取代，不作为当前结构验收依据；无 Tail 豁免仍可重现终止卡死。

这是有限状态激励下的协议可行性核验，不是形式证明，也不是 Chisel/Verilator 回归。实现故事仍须覆盖真实 ready/valid、寄存器边界及 DMA 反压。

## 6. 独立首行 PE 与扁平阵列

### 6.1 首行专用模块，不额外增加 16 份 MAC

拟新增首行双模式 PE 模块 WbWorkerOsM1，与原 WbWorker 分离：

- 全局 row=0：T 个 WbWorkerOsM1。
- row=1…T−1：T×(T−1) 个原 WbWorker。
- 每个首行专用 PE 只例化一份 WbWorkUnit；不把一个原 WbWorker 加一份 OS WbWorkUnit 包在 wrapper 中。

专用模块在 WS 下保留原权重、psum、加载、valid、格式和 scale 寄存语义；OS 下选择流式 B、直供 scaleB 与本地 psum 反馈，并使 A/valid/scaleA/last 同源冻结。提供明确的 OS B、seed、advance 和 token 端口，不复用 WS loadV/loadW 暗示 OS 初始化。

clear、seed、consume 互斥，clear 优先。seed 用完整 cEffW bias/零基底；计算中 B 为 bW。OS 配置在 Setup 从本次执行静态配置准备好，包括纯 FP8/W4A8 原 remap，再进行 seed；不让配置初始化清掉已经写入的 bias。

原 WbWorker 源码不加入 OS 分支；但其首行替代模块的 WS 等价性必须逐拍验证，独立模块并不自动代表零回归风险。OS 下其他行的 valid、纵向加载和 MAC 更新禁用。

### 6.2 WbComb 直接 T×T 例化

当前 WbCell 的跨边界只是连接 PE 的寄存输出，没有物理必须的额外寄存级。直接按全局 i/j 接线可行，也更直接表达“只有 row=0 特殊”。层级删除本身不减少 PE，不证明面积或 Fmax 改善。

采用两步实施：先把全 WS 阵列扁平化并验证不变，再替换首行、加入 OS 路由。阵列改为 arrayN × arrayM 平面 PE 网格：clusterM 参数与簇概念整体删除，totalN = arrayN，`require(arrayN == arrayM)` 守护方形契约。

WS 必须保留的细节：

- A/valid/scaleA/scaleB/loadW 横链与 psum/validV/loadV 纵链逐 PE 寄存边界。
- 输入行 skew、psum 输入 skew 及加载旁路、输出列 deskew 的原使能条件。
- clear/loadH/loadVLock/loadWLock 原广播语义。
- [WbComb.scala:204](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WbComb.scala#L204) 的 fmt/bFmt/rnd 原簇末标量取点；不能未经验证改成所有行只从本行左 PE 取配置。
- [WbComb.scala:274](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/WbComb.scala#L274) 输出数据/水平 valid 和最终 validOutV 的实际取点；不能按注释直觉重写 deskew。

OS 绕过 WS 的 B/psum skew 与底行 deskew，首行自然列序结果在全列完成后统一读取；A 横链延迟仍为 j 个逻辑推进拍，旧第 7/8 列边界不再是模块边界，也不新增一拍。

WbCell 可保留给独立调用和测试，但不再位于 WbComb 计算路径。不恢复旧 arrays 作为兼容 wrapper；依赖旧层级的测试探针直接更新为全局坐标。

### 6.3 工作集与数值边界

| 默认新增供数状态 | 逻辑容量 |
| --- | ---: |
| 16 列×2 槽×256 bit B 窗口 | 1,024 B |
| 16 列×2 槽×32 bit scaleB word | 128 B |
| 一拍 A 数据 | 32 B |
| 一拍 scaleA | 4 B |
| 小计 | 1,188 B |

另计每列两位槽有效状态、head/tail、4-bit lane 指针、窗口标识、last 和完成状态。若 B 与 scale 用同一个 Bundle FIFO，上表仍按字段分别列容量。原上游 FIFO 与每 PE 累加器仍存在；1,188 B 不是总存储面积或综合面积。

复用 WbWorkUnit 算术，不修改乘法器/FpAdd。local-feedback 与 WS 空间归约/跨窗口回灌的浮点数值顺序仍须核查，不预设逐位等价。[FpAddNarrow.scala:78](file:///Users/wangliutailong/Qoder_Proj/Chisel_Learn/src/workerbee/scala/main/FpAddNarrow.scala#L78) 默认归约窗口 W=19，lazy40 不等于 40-bit 全精度运算。

## 7. Executor 与最终提交

1. **Setup/Prime**：执行原任务边界 flush、模式锁存与 bias/rqM 取数；清空每列两个窗口槽、lane 指针、波前和完成状态，准备配置及 seed，至少预蓄所有列的首窗口；第二槽随后可继续预取。
2. **Run**：严格 A+全 T 列 head 槽有效才推进。跨 K 只做 head/next 切换和继续累加，不执行 WS 逐 K drain、cPop 回灌或中间 cPush。
3. **Tail**：最后 A 已注入，按第 5.3 节继续推进在途 token，逐列标记完成。
4. **Commit**：全部列最后 psumReg 更新后的下一拍，按自然列序提交一个 C 行；不能接受时保持结果，禁止重复 push。
5. **Wait-C/Next-N**：沿用 cStoreGate/cPeekDone 边界；准备下一个 N tile 的参数和 seed。任务末尾仍等待 C/scaleC DMA 排空。

每 N tile 不 flush 上游 A/B/scale FIFO。下一 tile 已预取的数据不能无重发地丢弃；本地列窗口槽与波前仅在本 tile 消费完毕、结果交付之后重置。

bias、rqM 和 C 不再额外反转。rqM 仍沿用 staging 整组提交；rqMLoaded 置位不等于 rqMRegs 同拍已更新，首块及跨 N tile 均要等真正提交。保留现有输出转换、scaleC、requant/ReLU 和 DMA 行锁存边界，不在 C DMA 添加第二组行锁存。

progress 按已完成 N/K 工作定义；perf.array 表示至少一个 PE 真正消费，包括尾波。stallA/B 区分正常 Run 缺 A 与 B/scale 供数导致的暂停，不把已完成列空队列计为 Tail stall。活动 Bool 不是 packed MAC 数或 16 列满载计数。

## 8. 周期账：保留理想下界，新增 Prime/refill 测量

令 L=K/g。首个 A 在列 0 消费后，第 j 列延迟 j 个逻辑推进拍；每列有效消费 L 次。无供数暂停时，计算和最终尾波为 L+T−1 拍，下一拍才可统一 commit。

BF16，A=1×1024、B=1024×2048、T=16：

```text
N tiles                  = 128
每 N tile K 窗口          = 64
每 N tile计算+最终尾波    = 1024+15 = 1039 个逻辑推进拍
全部计算+尾波             = 128×1039 = 132,992 拍
每 tile独占一拍commit时   = 133,120 拍，仍不含其他开销
```

新增全 16 路门控意味着首发前要装齐首窗口；连续供数每时钟装一列时，需要 16 次 B/scale 成对分发。它属于 Prime，不包含在以上首次 MAC 后的 1039 拍中；能否与配置/seed/上一 tile 写回重叠必须测量，不固定宣称额外零拍。

原 DMA 每 T 个逻辑 B 拍间仍有 sNEXT_COL 控制空泡；gather/scale、A 不足、批量 refill 和 C 等待也会增加真实时钟。保守全路门控可以暂停在途波前，因此实际尾波可能超过 15 个真实时钟，但无停顿时仍只有一次 15 拍逻辑尾延迟。

WS 基线必须按当前状态机和 loadWMode=1 同转置布局复算，不能套用 loadWMode=0 的结果。当前 sNEXT_NK_TILE 将 drainNextCnt 设为 T−1，稳态每 kTile 为 16 拍 LOAD_B + 1 拍 LOAD_A + 1 拍 NEXT_NK_TILE + 16 拍 DRAIN = 34 拍；sNEXT_COL 是 DMA 事件，可与计算重叠，不能再次加入互斥状态和。S11 连续源端到端实测 OS=139,288、WS=278,566（约 2.00×），其完整采样条件、逻辑下界和互斥/重叠周期账见 build/s11_acceptance.txt。验收按 Setup、Prime、RunAdvance、各列有效 MAC、Run stall、Tail、commit、C 等待分别对账，不把 133K 当端到端性能承诺；实测数字不进入正式硬件规格。

## 9. 验证与非功能边界

- 金锚由张量坐标、格式、block 与舍入契约独立推导；地址测试同时断言请求地址与数据，禁止拟合 RTL 行为。
- WS 扁平化和首行替代分别做逐拍回归，再测 OS；不将两类改动混在一次失败中定位。
- 覆盖 M1 单/多 K、单/多 N tile、所有合法格式、MX block16/32、多系数、不同列 scale、自然列序 bias/rqM、OS/WS 切换与输出反压。
- 定向停顿放在首填充、窗口 FIFO 空/满、head/next 切换、scale byte 选择边界、旧窗口末 lane 与新窗口同拍入队，以及最后 A/各列最后消费处；输入恢复后必须完成。
- 整数保持截断绕回及 requant 饱和例外；浮点差异按实际归约/精度契约解释，不任意放宽容差。
- 不包含自动 N 尾缩窄、跨 N 全量 A 缓存、FIFO 存储合并、DMA 去气泡、无关模块重构或 PPA 定论。
- 不修改 ForagerBee/LLC/Gingercat/utils/.gitignore，不恢复已删文件，不删除 WbCell，不执行 git 写。
- 实施后的 RTL 仅通过既有入口交付默认一套；临时日志按 build/sim 原边界处理，验收后清理调试探针。当前没有 OS RTL、仿真或综合结果。

## 10. 实施步骤与依赖图

本章以文字故事和图形说明实施顺序，不再维护 JSON。每个故事限定为一次 coding-mode 可完成的边界；依赖允许并行不代表允许多个写代理同时修改同一文件。

### 10.1 数据通路图

```text
B DMA ──> 原B宽FIFO(256b) ───────┐
                                  ├─> 自然列序分发器 ─> column[j] window FIFO(depth=2)
scaleB DMA ─> 原scaleB FIFO(32b) ┘                         │
                                                             ├─ head.B[16×16b]
                                                             ├─ head.scale[32b]
                                                             ├─ head.kt / valid
                                                             └─ next slot
                                                                    │
                                                     lane index 0...15
                                                                    │
A FIFO ─> A窗口寄存器 ─> A字串行化 ─> 首行横向token ───────┼─> WbWorkerOsM1[j]
                                                                    │
                                                       本地psum反馈 / done
                                                                    │
                                              全列完成 ─> 单次C行commit
```

MX 模式下分发器必须同时拿到 B 与 scaleB head，原子生成一个窗口槽；非 MX 只写 B 字段。PE 每次消费当前槽的一个 16-bit packed 字，scale 则从同槽 32-bit word 中按 lane 选择。

### 10.2 执行状态图

```text
Setup
  │  锁存模式、清空2槽、准备fmt/rnd/bias/rqM
  ▼
Prime
  │  16列首窗口全部有效；next槽可继续预取
  ▼
Run ───────────────┐
  │ A有数且16列head有效 │ 缺数据：整行冻结，分发器继续补槽
  │ 每列按本地token消费 │
  └──────────────────┘
  │ 最后A已注入
  ▼
Tail
  │ 不再要求新A；done列豁免空head；排完在途token
  ▼
Commit
  │ 全列psum更新后的下一拍提交一次自然列序C行
  ▼
Wait-C / Next-N ──> Prime 或 Done
```

### 10.3 故事依赖图

```text
S1 控制契约 ─┬─> S2 升序DMA ────────────────────────────────────────────┐
             ├─> S4 首行专用PE ─┐                                        │
             └─> S5 双槽窗口FIFO ├─> S6 推进协议 ─┐                       │
                                 └───────────────┤                       │
S3 扁平WS阵列 ───────────────────────────────────┴─> S7 阵列集成 ─┐      │
                                                                 ├─> S8 BF16端到端
                                                                       │
                                                                       ▼
                                                                 S9 全格式/scale
                                                                       │
                                                                       ▼
                                                                 S10 epilogue
                                                                       │
                                                                       ▼
                                                                 S11 周期测量
                                                                       │
                                                                       ▼
                                                                 S12 回归收口
```

### 10.4 可执行故事

每个故事都按“入口条件 → 有序执行步骤 → 验证与完成标准 → 范围边界”组织。执行者只在入口条件满足后开始；任一完成标准未满足时，不进入下游故事。

#### S1：建立 OS-M1 控制契约

**目标：** CONTROL[31] 显式选择 OS-M1，非法形状在计算开始前被拒绝。

**入口条件：** 无代码依赖；以当前寄存器映射、errCode 优先级和 osM1=0 行为为基线。

**执行步骤：**

1. 在 `WbInterface.scala` 的 `WorkerBeeRegister` 增加 `osM1: Bool`，不增加编译期 Config 字段，也不改变其他字段类型。
2. 在 `WorkerBeeRegIf.scala` 的 CONTROL 写路径接收 bit31，在读路径把 bit31 放回原最高位；沿用现有命令级 inFlight 写保护和寄存器复位方式。
3. 在 `WorkerBeeCalcConfig.scala` 建立 `osM1 && (m =/= 1 || !loadWMode)` 的 errCode 14 分支；放在既有判决之前，使原格式、K、输出及 epilogue 错误仍按当前 last-connect 优先级覆盖它。
4. 在 `scala/test/WbDebugModel.scala` 同步寄存器参数、CONTROL 组字和 `hwErrCode` 镜像，避免测试模型与 RTL 对模式位或错误优先级产生分叉。
5. 在 `scala/test/WorkerBeeRegIfSpec.scala` 增加 bit31 写入、读回、reset 和 inFlight 禁写用例；在 `scala/test/WorkerBeeQuickSpec.scala` 增加 M≠1、loadWMode=0、合法 M=1 及与既有错误叠加的配置用例。

**验证与完成标准：** 两个目标 suite 通过；osM1=0 的寄存器与错误码用例结果不变；合法 osM1 只通过配置检查，尚不触发 OS 阵列行为。

**范围边界：** 不新增寄存器槽位、不改变 N 补齐契约、不加入自动模式选择。**依赖：** 无。

#### S2：实现 OS B 与 scaleB 升序 DMA

**目标：** OS 的 B 和 scaleB 都按 nt、kt、自然列 j 升序输出，WS 继续降序。

**入口条件：** S1 已完成，`io.regFile.osM1` 可在 DMA 中稳定读取；地址金锚使用本报告第 4.2 节闭式公式。

**执行步骤：**

1. 在 `WorkerBeeDmaRdOnly.scala` 定义运行期 `osB = !io.isA && io.regFile.osM1`，只用它选择 B 类遍历；A 通道与 WS B 通道保留原分支。
2. 为 OS B 维护当前 N tile 的首列锚点：start/restart 时为 base；同一 N tile 切 K 窗口时在锚点上加 `reg_colTileOffset`；切 N tile 时回到 `base + (nextNt×T)×reg_rowOffset` 的 K0 起点。OS 分支不复用 loadW=1 的 lastBTileStartAddr 累加模型（该模型有文档化 Δ_nt 偏差，见 DmaRdOnly 头部注释），每 N tile 锚点由闭式直接重算。
3. 修改 sIDLE、sTRANSFER 中途重启和 sDONE 重启的同源初始化，确保 `curAddr`、列计数、K/N 计数、credit、gather 相位及窗口锚点不会沿用上一次任务状态。
4. 在 OS B 的 `finalReqFire` 上执行 `curAddr += rowStep`；lo gather 物理读不前移逻辑列，hi/final 读才推进，与现有 credit 口径一致。
5. 在 sNEXT_COL 分别实现“同 tile 下一 K 窗口”和“下一 N tile 的 K0”跳转；数据 B 与 scaleB 共用同一模块和模式判据，但各自使用顶层传入的 row/col 步长。
6. 在 `gem5/tests/WorkerBeeRtlDmaSpec.scala` 或新增专用 RdOnly 局部用例中，由张量坐标独立生成地址序列，覆盖多 K/N、block16/32、W4A8 半字选择、非对齐 gather、请求反压、延迟响应及三种 start 状态。

**验证与完成标准：** 每个 OS 请求地址与闭式公式一致，B 和 scaleB 的逻辑列顺序一致；现有 WS DMA、A DMA、gather、stretch 和 error suite 全部保持通过。

**范围边界：** 不去除 sNEXT_COL 空泡、不重写 credit/响应配对、不修改外部张量布局。**依赖：** S1。

#### S3：先将纯 WS 阵列扁平化（arrayN × arrayM，去除 clusterM 概念）

**目标：** `WbComb` 直接例化 `arrayN × arrayM` 个原 `WbWorker` 平面网格（无簇层级），WS 的逐拍行为不变。

**入口条件：** 无功能依赖；先记录当前 `WorkerBeeRtlArraySpec` 的关键 skew、load、valid 和 deskew 波形/断言作为结构重构基线。

**执行步骤：**

1. 在 `WbComb.scala` 用全局 `pes(i)(j)` 代替 2×2 `arrays(ci)(cj)`，总实例数固定为 `arrayN × arrayM`（16×16=256）；此故事所有位置仍使用原 `WbWorker`。
2. 移除 clusterM 参数与簇概念：`WorkerBeeConfig` 改 `arrayN=16`（行/K-lane 轴）+ `arrayM=16`（列/输出 N 轴），`totalN=arrayN`，`require(arrayN == arrayM)` 守护方形契约；`WorkerBee.scala`、测试侧构造点与 gem5 `hardware.py` 同步。
3. 逐项迁移 clear/loadH/loadVLock/loadWLock 广播、A/valid/scale/loadW 横链（j 向）和 psum/validV/loadV 纵链（i 向）。
4. 保留原输入 valid/A/scaleA skew、B/psum skew、加载旁路和输出 deskew 的寄存器数量及使能条件；不依据注释简化真实代码中的取点。
5. fmt/bFmt/rnd 统一行内链传播（旧跨簇取点 (7,7) 与行内链值恒等，逐拍行为不变）；最终 validOut 取点 `pes(arrayN-1)(arrayM-1).io.validOutV`。
6. 更新 `gem5/tests/WorkerBeeRtlArraySpec.scala` 中访问 `arrays(...).pes(...)` 的探针为 `pes(row)(col)`，保持同一组坐标和同一拍期望。
7. 跑阵列局部测试和现有 WorkerBee Quick/LoadW/MX/INT 针对性用例，并发射一次临时 RTL 仅做结构计数，确认仍为 256 个 PE 算术单元。

**验证与完成标准：** 所有 WS 数值和逐拍断言通过；第 7/8 列与第 7/8 行边界没有新增延迟；生成结构中 `WbWorkUnit` 数量不变（256）、`WbWorker` 实例 256、无 `WbCell` 实例。

**范围边界：** 不删除 `WbCell.scala`（保留文件、不再例化），不加入 OS 接口，方形约束 `arrayN == arrayM`（矩形待列侧常量解耦）。**依赖：** 无。

#### S4：新增首行双模式 PE

**目标：** 独立 `WbWorkerOsM1` 同时保持 WS 兼容和 OS 本地反馈，不增加第二份 MAC。

**入口条件：** S1 已完成；原 `WbWorker` 和 `WbWorkUnit` 的 WS 时序作为对照真源。

**执行步骤：**

1. 新增 `scala/main/WbWorkerOsM1.scala`，复制首行所需的 WS 状态与接口语义，并增加 `osM1`、`osB`、`osScaleB`、`osSeed`、`osAdvance`、`osTokenValid` 和 `osLast` 等明确 OS 端口。
2. 模块内部只例化一个 `WbWorkUnit`：WS 选择 `wReg` 和外部 `psumIn`，OS 选择流式 B 和本地 `psumReg`；fmt/bFmt/rnd 仍来自本次执行静态配置。
3. 把状态更新优先级固定为 clear > seed > OS consume/WS load-compute > hold；seed 写完整 cEffW 基底且不执行 MAC，OS 停顿时不更新 psum、A、scaleA、valid 或 last。
4. OS 的 A/scaleA/token 右传寄存器只在 `osAdvance` 时更新；WS 分支保留原 `loadWIn | validIn`、RegNext valid/格式和纵向加载传播行为。
5. 新增 `scala/test/WbOsM1PeSpec.scala`：覆盖零/bias seed、连续本地反馈、暂停多拍恢复、last 标记、clear 竞争；同一激励下把 osM1=0 的输出逐拍与原 `WbWorker` 对比。

**验证与完成标准：** OS 每个 consume 只累加一次，暂停不重复累加；WS 对照逐拍一致；发射结构显示专用 PE 内只有一个 `WbWorkUnit`。

**范围边界：** 不修改原 `WbWorker.scala` 的 OS 行为，不额外并排例化 MAC，不接完整阵列。**依赖：** S1。

#### S5：实现 16 路双槽窗口 FIFO

**目标：** 每列用两个窗口槽重叠当前消费和下一窗口预取，B 与 scale 永不错槽。

**入口条件：** S1 已完成；窗口 Bundle 和握手遵循本报告第 4.3–4.5 节。

**执行步骤：**

1. 新增 `scala/main/WbOsM1Feeder.scala`，定义私有窗口 Bundle：`bWindow(T×bW)`、`scaleWord(scaleBW)`、K 窗口标识和 valid；每列实现两个槽、head/tail 或 ping/pong 选择位及 4-bit lane 指针。
2. 接入原 B 宽 FIFO head：分发索引按 `kt×T+j` 自然列序推进，目标列未满才允许 B pop；一个 pop 只写一个完整 256-bit 窗口槽。
3. MX 模式要求 B 与 scaleB 两个上游 head 同时有效、目标列有空槽时才同步 pop，并原子写入同一个 Bundle；任一侧缺失时两侧都不 fire。非 MX 不拉高 scaleB ready。
4. 读端组合选择 head 槽的 `bWindow(lane)`，scale 选择留给后续格式故事；本故事先输出完整 scaleWord 和 lane。只有本列 consume 才递增 lane，lane=T−1 时弹出整个 head 并把 next 切为新 head。
5. 明确同拍“旧窗口最后 lane consume + 新窗口 enqueue”的优先级：组合输出读取旧 head，边沿同时 pop/enqueue 后 count 保持，下一拍新 head 可见；禁止未声明的同拍 bypass。
6. 新增 `scala/test/WbOsM1FeederSpec.scala`，覆盖 0/1/2 槽占用、满反压、head/next 顺序、16 次消费、同拍 pop+push、B/scale 独立延迟、列间交错和 N tile 边界。

**验证与完成标准：** 每个输入窗口恰被目标列消费 16 次且顺序正确；MX 的 B/scale 窗口标识始终一致；任意暂停后可恢复，槽计数不溢出或下溢。

**范围边界：** 不修改 utils `StreamFifo`，不增加上游 bBufferDepth，不实现 A/阵列推进。**依赖：** S1。

#### S6：实现 A 串行化与 Run/Tail 推进协议

**目标：** Run 严格按 A 与全列 head 有效统一推进，Tail 在没有新 A 时仍可终止。

**入口条件：** S4、S5 的局部测试通过；专用 PE 和窗口 feeder 已有稳定的 consume/advance 接口。

**执行步骤：**

1. 在 feeder 中增加 A 窗口寄存器、A lane、scaleA word、全 K token 计数和 last 生成；只有捕获槽为空时 pop 原 A FIFO，一拍数据按 lane 0…T−1 输出完整 aEffW 字。
2. 实现 `RunAdvance = AHeadValid && AND_j(WindowHeadValid[j])`；RunAdvance=0 时冻结 A lane、首行 token、各列 B lane 和 PE 本地累加，但允许 B/scale 上游继续填 next 槽。
3. 对每列生成 `Consume[j] = Advance && LocalTokenValid[j]`，只让真正持有 A token 的列前移窗口 lane；启动填充阶段不能让尚无 token 的右侧列提前消费 B。
4. 最后 A 字注入后切 Tail：入口不再要求 AHeadValid，使用 `ColumnDone[j] || WindowHeadValid[j]` 判定可推进；本列真实消费全 K last 后置 done 并停止继续消费。
5. 当全部 done 后延迟一拍产生 resultsReady，保证最后一次 `psumReg` 更新先完成；同时断言所有 A token 已排空且不存在重复 done。
6. 扩展 `scala/test/WbOsM1FeederSpec.scala` 或新增首行链用例，把 512 组模型场景落为定向与随机测试：Prime、窗口切换、A/B/scale 随机停顿、最后 A、逐列 done、Tail 和最终槽释放。

**验证与完成标准：** 每列 consume 数等于 K/g，全部数据/scale 窗口按 kt 配对；任意有限停顿解除后任务终止；无 Tail 豁免版本的卡死场景在正式实现中通过。

**范围边界：** 不接 Executor/C 写回，不把严格全列门控放宽为“仅有效列 ready”。**依赖：** S4、S5。

#### S7：在扁平阵列替换首行并接入 OS

**目标：** 同一 256-PE 阵列支持首行 OS 与原 WS，两种数据和结果出口分明。

**入口条件：** S3 的纯 WS 扁平回归通过，S6 的首行链协议通过。

**执行步骤：**

1. 在 `WbComb.scala` 将全局 row0 的 T 个实例替换为 `WbWorkerOsM1`，row1…T−1 保持原 `WbWorker`；建立统一的首行输出 Vec，避免在下游复制模式判断。
2. WS 模式把首行专用 PE 接回 S3 已验证的横纵链、加载和格式信号；OS 模式接 feeder 的 per-column B/scale、A/token、seed、advance 和 consume。
3. OS 模式显式禁止其他 240 个 PE 的 valid/load 更新，旁路 WS 的 b/psum skew 与底行 deskew；结果直接取首行 T 个本地 psum。
4. 把 per-column token valid、consume、done/resultsReady 返回 feeder/控制侧；确保第 j 列延迟恰为 j 个 Advance，原第 7/8 列位置不产生特殊拍。
5. 新增 `scala/test/WbOsM1ArraySpec.scala`，覆盖自然列 B、跨 7/8 列、整行暂停、head/next 切换、Tail 和结果采样；同一 WbComb 继续运行完整 WS 阵列用例。
6. 发射局部 RTL 或检查 FIRRTL/SV 实例，统计 T 个专用 PE、T×(T−1) 个普通 PE和总计 T×T 个 `WbWorkUnit`。

**验证与完成标准：** OS 首行结果与独立首行模型一致，WS 全阵列回归不变；总算术实例不增加，OS 下非首行无有效 MAC。

**范围边界：** 不接 DMA/Executor 生命周期，不删除 WbCell。**依赖：** S3、S6。

#### S8：接通 BF16 OS 任务生命周期

**目标：** BF16 从现有 DMA 输入到 C 写回端到端运行，每 N tile 只提交一个最终 C 行。

**入口条件：** S2 升序 DMA 与 S7 阵列集成各自通过；先只启用 BF16、bias/requant/scaleC 关闭。

**执行步骤：**

1. 在 `WorkerBeeExecutor.scala` 为 osM1 增加 Setup、Prime、Run、Tail、Commit、Wait-C 状态或等价分支；进入任务时锁存模式，避免执行中 CONTROL 改写影响当前任务。
2. Setup 清空本地窗口/波前/完成状态并准备 fmt/bFmt/rnd；Prime 等待 16 列首窗口和一个 A 窗口可用，再发 seed/启动信号。
3. Run 只驱动 feeder/阵列 Advance，不执行 WS 的 loadW/loadV、逐 K cPop 回灌、逐 K drain 或中间 wbValidOut→cPush 路径。
4. Tail 等 resultsReady；Commit 把首行自然列序 psum 按原 Cat 打包后由 executor 产生唯一一次 `cPush`，经 cFifo 由 cStoreGate/cPeekDone 门控的 `cPop` 喂给 cvt（复用 WS 的 cFifo→cPop→cvt 路径，不加第二套行锁存）；`cPush` 遵守既有 valid&ready 断言，下游未 ready 时保持结果和 valid 不丢弃。
5. Wait-C 沿用 cStoreGate/cPeekDone 后进入下一 N tile Prime；不 flush A/B/scale 上游 FIFO。最后一个 N tile 继续等待 C DMA busy 清零后 done。
6. 在 `scala/test/WorkerBeeQuickSpec.scala` 增加 BF16 M=1 的单/多 K、单/多 N、A/B/C 反压、连续 OS 任务和 OS→WS→OS 切换用例；同时断言每 N tile 恰一次 C 行提交。

**验证与完成标准：** BF16 张量结果与独立 GEMM 金锚一致；无中间 C push、无 per-K drain、无 N tile 数据丢弃；连续任务无残留状态。

**范围边界：** 本故事不接 MX/packed/bias/requant，不优化 DMA 空泡。**依赖：** S2、S7。

#### S9：扩展 packed 与全部合法 scale 格式

**目标：** INT8/INT4、MX8/MX4、纯 FP8 和 W4A8 均沿既有格式契约运行。

**入口条件：** S8 的 BF16 生命周期稳定；现有格式合法性与 W4A8 stretch 规则保持权威。

**执行步骤：**

1. feeder/Executor 的 g 统一调用 DataFormat 权威映射；INT8/MX8/FP8 每字 2 个元素，INT4/MX4 每字 4 个元素，BF16 每字 1 个元素，W4A8 按 A 的 g=2。
2. A 窗口捕获时保存 scaleA word，随 A lane/token 选择 `floor(lane×g/blockA)` 的 E8M0 字节并横向传播。
3. 在当前 B 窗口槽中按 `floor(lane×g/blockB)` 选择 scaleB 字节；窗口切换同步切 scaleWord，暂停时 lane 与 scale 选择共同保持。
4. 纯 FP8 沿用顶层格式 remap并注入 scale 127，不启动或等待 scale DMA；BF16/INT 忽略槽内 scale 字段。
5. W4A8 使用 bDma stretch 后的 256-bit 展开窗口，K/scale 步进都按 A 窗口；不恢复 passPhase 或连续推两拍方案。
6. 在 `scala/test/WorkerBeeSimCaseMxSpec.scala`、`scala/test/WorkerBeeSimCaseIntSpec.scala` 和 `scala/test/WorkerBeeSimCaseLoadWSpec.scala` 增加 block16/32、1/2/4 有效 scale 字节、列间不同 scale、多 K/N、随机停顿及所有合法组合；金锚从张量和格式语义生成。

**验证与完成标准：** 各格式每列 consume 数、K 覆盖和 scale byte 选择正确；非法组合仍由原错误码拒绝；浮点差异按既有窄化契约解释而非任意放宽容差。

**范围边界：** 不新增格式、不修改 WbWorkUnit/FpAdd 算术、不解释 scale word 无效高字节。本故事为全计划最大单故事（5 个格式族 × 3 个 suite），实施时可按 INT 族与 MX/FP8/W4A8 族分两次 coding-mode 落地，不改变对外依赖与验证标准。**依赖：** S8。

#### S10：接入自然列序 epilogue 与补齐写回

**目标：** bias、rqM、requant、ReLU 和 C 写回均保持自然列序及既有量化语义。

**入口条件：** S9 全输入格式通过；基础 OS 输出已按列 0…T−1 自然排序。

**执行步骤：**

1. 在 Executor 的 Setup/Prime 阶段沿用现有 biasFetch/rqMFetch，按当前 N tile 的自然列序收齐 16 项；不添加物理列反转。
2. seed 时对 INT 使用现有符号扩展、对浮点使用现有 fp32→lazy 转换；每列只 seed 一次，seed 完成后才能启动首个 MAC。
3. 保留 rqM staging/armed 提交：首 tile 和跨 N tile 都等 rqMRegs 真正更新后才允许对应结果进入 cvt，不能把 rqMLoaded 当作同拍已提交。
4. Commit 复用原 WbOutCvt、requant、ReLU、scaleC 和 cStoreGate/cPeekDone；保持低逻辑列位于原接口低位的拼接规则，不在 C DMA 增加第二套行锁存。
5. 在 INT/MX/Quick 用例中加入每列不同 bias/rqM、至少两个 N tile、cPush ready 停顿、scaleC 反压、INT4/INT8 requant 饱和及 ReLU 开关。
6. 增加 Nlogical=1/17、Nprog=16/32 用例，显式分配可访问的 B/参数/scale padding，检查完整写回范围和软件可见前 Nlogical 列。

**验证与完成标准：** 多 tile 的 bias/rqM 不串列，C 自然列序正确；输出反压不重复 commit；requant 饱和与非 requant 绕回保持原契约。

**范围边界：** 不实现自动 N 尾缩窄、不改变 C DMA byte strobe 或输出格式集合。**依赖：** S9。

#### S11：定义观测并测量 Prime 与 stall

**目标：** 给出可复算的真实周期账，分清逻辑推进下界和供数停顿。

**入口条件：** S10 功能完整且数值回归稳定，避免性能采样掩盖功能错误。

**执行步骤：**

1. 在 Executor/WorkerBee 现有 perf Bool 上定义 OS 语义：array 只在至少一个 PE 真实 consume 时为高，Tail consume 计入；done 列空 head 不记作 B stall。
2. 测试侧增加计数器而非扩展通用硬件 PMU，分别记录 Setup、Prime、RunAdvance、每列 consume、缺 A、缺 B/scale 窗口、Tail、Commit、C 等待和总周期。
3. 为每个 N tile 输出守恒检查：每列 consume=K/g、总 Run/Tail Advance 与 token 模型一致、commit=1；计数不满足时测试直接失败。
4. 测量 BF16 M=1、K=1024、N=2048 的连续源 OS 和相同 WS 基线，分别列出逻辑下界、首窗口 Prime、DMA sNEXT_COL 空泡和 C 写回。
5. 再测 gather/scale 延迟和随机 ready，观察双槽吸收的短期抖动及长期平均供数缺口；不把 132,992 或 133,120 拍直接写成端到端保证。
6. 将可复算周期表及测试命令记录到本功能验收输出；不把临时波形或计数探针写入正式规格。

**验证与完成标准：** 周期分项之和等于总周期，MAC/advance/commit 守恒；连续源和低速源结果可由同一计数定义复算。

**范围边界：** 不修改 DMA 节奏、不扩展泛用 PMU、不做 PPA 结论。**依赖：** S10。

#### S12：回归、规格与默认 RTL 收口

**目标：** 交付经过完整回归的 OS 功能、同步规格和默认一套 RTL。

**入口条件：** S11 功能与周期验收通过，所有临时问题已归零，没有未解释的数值或停顿差异。

**执行步骤：**

1. 汇总并顺序运行 `WbOsM1PeSpec`、`WbOsM1FeederSpec`、`WbOsM1ArraySpec`、`WorkerBeeRegIfSpec`、`WorkerBeeQuickSpec`、LoadW/INT/MX/DMA/Error 相关 suite；一次只运行一个 sbt 进程。
2. 运行 `sbt -J-Xmx6G "Test / compile"`，确认不是无效的 `Test:compile` 别名；再运行 `sbt -J-Xmx6G test` 全量回归。执行环境前置沿用已验证模板：`-Djava.io.tmpdir=<repo>/build/tmp` 与 `CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib`（Verilator FST writer 依赖 lz4，见 WbSimCommon/QuickSpec 头部注释）。若仿真进程未启动且缺整套日志，先区分宿主 EDR 问题，不误报 RTL 回归。
3. 更新 `spec.md`：CONTROL[31]、M=1/loadWMode=1 守护、B/scaleB 升序地址、每列双槽窗口、Run/Tail、自然列序、最终一次 commit 和 Nprog 契约。
4. 通过 `sbt "runMain WorkerBeeElaborate"` 使用唯一默认 `WorkerBeeConfig()` 重发射一套 RTL；不创建对照配置目录或恢复已删除发射入口。
5. 审计生成结构与接口：总计 256 个 `WbWorkUnit`、首行 16 个专用 PE、16×2 个窗口槽、CONTROL[31] 读回、OS/WS 两路结果选择及无意外 Config/端口扩张。
6. 清理临时调试探针和 sim 临时波形；build 仅保留允许的报告日志。检查变化只位于 WorkerBee 授权源码、测试、spec 和默认 RTL，不触碰其他模块。

**验证与完成标准：** 针对性测试、编译、全量回归、规格审阅和默认 RTL 结构审计全部通过；无调试残留、无第二套 RTL、无未解释差异。

**范围边界：** 不删除 `WbCell`，不恢复已删文件，不修改 ForagerBee/LLC/Gingercat/utils/.gitignore，不执行 git stage/commit/push。**依赖：** S11。

以上故事定义实施与验收边界，S1–S12 已完成。S12 的 OS 定向 31/31、按既定模块边界的全量回归 248/248、默认 RTL 发射与结构审计均通过；正式行为契约见 spec.md v4.2.0，验收命令、日志和结构清单见 build/s12_acceptance.txt。osFmtKeep 广播优化继续使用软件值 0 的基线，不追加优化范围。
