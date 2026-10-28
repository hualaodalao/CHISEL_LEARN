# WorkerBee gem5 性能模型规格

版本：v0.1.2（P0 实施中）  
日期：2026-10-28  
状态：P0 idle 骨架、基线工具与 RTL 边沿单测已实现；边沿运行受宿主启动问题阻塞，命令/GEMM/DMA 模型仍待实现，P0 尚未验收。

## 1. 目的、依据与规范等级

本规格定义 WorkerBee GEMM 的 gem5 timing 模型，用于分析 kernel 延迟、计算/供数停顿、DMA流量及存储竞争。目录与实施步骤见 [实施规划](plan.md)。

硬件基线：[WorkerBee 硬件规格](../doc/spec.md) v4.0.0，2026-10-26。编写时源码 HEAD 为 `7be63b713f813314a9e021aec00d1ff17576a485`；该 commit 不替代文件内容 hash，实际模型验收必须记录参与构建的源码与RTL hash。

依据优先级：

1. 软件可见接口、格式与合法输入以硬件规格为契约。
2. 硬件规格未固定的内部周期、FIFO透传和仲裁状态以同配置源码、重新发射RTL及对拍证据补充。
3. 规格与源码冲突必须登记并裁定，不静默选一方，不通过模型修改硬件能力。
4. 内存、互连、CPU和调度为外部环境假设，不能作为GEMM硬件保证。

“必须”为实现和验收条件；“第一版不支持”不代表硬件不支持；“待校准”表示尚无可发布的精确数值。

## 2. 模型范围与精度

### 2.1 第一版范围

- 单个 WorkerBee 实例；系统可组合多个实例，各自只有一个执行上下文。
- 默认硬件配置、当前合法输入格式组合和输出格式对应的时序/流量路径。
- 四路寄存器命令、单响应、配置检查、状态与IRQ。
- 执行 FSM、FIFO/credit、波前、内部部分和回灌、输出转换占用。
- A/B/C/ScaleA/ScaleB/ScaleC六路DMA及ScaleA共享参数取数。
- 真实gem5 timing内存事务、确定性校准内存、性能统计与事件日志。

### 2.2 第一版明确不做

- 完整GEMM数值计算、MX lazy累加bit-exact或网络精度验证。
- CPU指令执行、NPU ISA译码、MMIO地址映射、Linux驱动与真实runtime。
- 外部scheduler、外部DMA、向量算子或卷积im2col硬件。
- 新数据流、跨命令权重保持、自动硬件M切块/N尾缩窄。
- 详细功耗、面积或工艺频率预测。
- 执行中checkpoint恢复、事务撤销或在途reset回滚。

### 2.3 精度承诺

| 维度 | 验收要求 |
|---|---|
| 命令与寄存器 | 可见行为和关键握手逐拍一致 |
| 地址与流量 | 同配置同输入的物理请求序列、大小、strobe和响应数完全一致 |
| 确定性校准时序 | 核心校准集合逐拍一致，周期误差为0；这是验收目标，尚未实现 |
| 实际存储环境 | 时序由明确的gem5存储模型决定；没有RTL同环境证据时不得标为RTL精确 |
| 数值 | timing-only不承诺C或ScaleC值，data_valid=false |
| 硅片性能 | 没有实际硬件和系统校准前不承诺误差百分比 |

不能以“总周期误差小于5%”放宽寄存器错误、流量错误、提前完成或数据丢失。较大kernel的误差统计必须给出适用配置和样本，不作为跳过基础精确对拍的理由。

## 3. 模型架构与配置边界

DUT为一个 `ClockedObject`，内部状态类统一推进；DMA使用固定命名的gem5 `RequestPort`。四路Stream命令由测试driver提供拍级输入，不先伪装成MMIO写。

P0 Python 类型 `WorkerBeeGemm` 绑定 `gem5::workerbee::WorkerBee`，头文件使用 `workerbee/gem5/model/...` 限定路径。六个 gem5 端口为 `dma_a`、`dma_b`、`dma_c`、`dma_scale_a`、`dma_scale_b`、`dma_scale_c`。当前仅实例化和连接端口，不发请求，不提供命令输入；P1–P3 才实现相应行为，idle smoke 不能作为时序模型验收。

### 3.1 初始硬件profile

| 项目 | 值/规则 |
|---|---|
| arrayN / clusterM / T | 8 / 2 / 16，T派生而非独立配置 |
| A/B lane宽 | 各16 bit |
| A/B DMA宽 | 各T×16=256 bit |
| C DMA宽 | 256 bit |
| ScaleA/B / ScaleC宽 | 32 / 8 bit |
| A/B/C FIFO深度 | 各64项 |
| 输入scale FIFO深度 | 64项 |
| 命令lane / response slots | 固定4 / 4，不设count参数 |
| cvt行保持 / ScaleC FIFO | 固定1行 / 2项，不设可选深度 |
| addrWidth / M,N,K | 32 bit / 各16 bit |
| 输入格式集合 | INT8、INT4、MXE4M3、MXE5M2、MXE2M1、FP8E4M3、FP8E5M2 |
| cEffW与流水时序 | 由同配置源码派生，P0冻结证据，不提供自由latency旋钮 |

硬件变体只在新增同配置RTL校准后登记为profile。不能将FIFO深度、支持格式或阵列宽度随意修改后仍沿用默认配置的精度标识。

### 3.2 外部环境参数

GEMM、SRAM、互连和DRAM各自使用明确clock domain。频率为实验输入，不是模型预测。容量、端口连接、共享仲裁、服务宽度/延迟、ready模式和随机seed属于环境参数。

不能新增 `gemm_latency`、`stall_penalty`、`loadW_cycles` 或 shape专用拟合表。结构时序是模型实现事实；系统带宽和延迟才由环境选择。

## 4. 时钟、事件与生命周期

### 4.1 拍级状态语义

定义cycle c为一组输入和拍前状态被采样的边界。每拍必须：

1. 固定本拍driver输入、可见的内存响应和拍前寄存器/FIFO状态。
2. 基于同一拍前状态评估组合ready/valid、仲裁、事件和握手。
3. 记录fire及perf电平；同一接口每拍至多一次fire。
4. 一次性提交所有寄存器/FIFO下一状态。

START、同拍done或IRQ_CLEAR不得先改变另一lane的检查/读快照。具有实际组合通路的ready/valid必须遵循RTL；不能一律用“新push下一拍才可消费”代替FIFO透传行为。

内存回调不能按C++调用顺序直接修改已采样状态。同tick响应、采样前/后到达和跨clock domain同步规则在P0确定并做边界测试；不能对所有响应无条件增加一拍，也不能让同一个响应被两拍消费。

### 4.2 事件压缩

第一版逐拍实现。后续只允许跳过不会改变外部可见握手、FIFO边界、资源竞争或状态转移结果的区间。等待内存、credit不足、输出反压等情况下，不得一次预约整个kernel完成。

跳拍后必须按区间长度精确累积valid周期、busy周期与占用统计；保持与逐拍版本相同的关键事件序列。两个版本不作为不同精度模式交付。

### 4.3 reset与drain

- 初始reset清配置、pending、start、inFlight、RAW和ERROR_CODE；MASK=0x7FF。
- 命令响应pending但没有执行/DMA在途时，可以测试reset丢弃该批响应。
- 第一版执行中reset只作“不支持运行域”诊断，不能宣称已撤销外部事务。
- 结束仿真必须等DUT和适配器实际排空；不得删除尚在途事件后报告完成。
- gem5 drain不得等同于GEMM done，checkpoint暂不承诺。

## 5. 命令、寄存器和异常契约

### 5.1 命令格式

固定四lane，各lane字段与硬件一致：op[2]、reg0Addr[6]、reg1Addr[6]、data0/data1[32]、pc[32]、tag[8]。响应为data[32]、err[1]、laneId[2]。

op编码：0=REG_WRITE1，1=REG_WRITE2，2=REG_READ，3拒绝。地址是寄存器索引，不是byte offset；不新增执行opcode。

### 5.2 批接收与仲裁

必须完整实现硬件规格第6.1–6.2章，特别是：

- pending为空才接收本拍任意稀疏lane集合；最多4条。
- 读快照、写检查、inFlight保护全部使用拍前值。
- WRITE2任一字段非法则整条拒绝、无副作用、无仲裁资格。
- 合法写按目标地址独立仲裁，lane3>2>1>0；落败字段不执行，ACK仍err=0。
- 同lane普通同址slot1胜；CLEAR同lane OR、跨lane只用最高lane；CLEAR=0仍有目标。
- START只由合法非零项参与；零值no-op；一批最多产生一次下一拍start，使用最高非零lane的pc/tag及全部winning配置。
- 接受拍写提交不等待ACK；读不旁路同拍写。
- 响应下一拍起按lane递增串行返回；背压保持payload；最后响应fire当拍不能接新批。

### 5.3 寄存器索引

| 索引 | 内容 |
|---|---|
| 0x00 / 0x01 | M / N_K |
| 0x02–0x04 | A/B/C_ADDR |
| 0x05 | CONTROL |
| 0x06–0x08 | SCALE_A/B/C_ADDR |
| 0x09 / 0x0A / 0x0B | BIAS_ADDR / RQ_M_ADDR / RQ_PARAM |
| 0x10 | START |
| 0x1E | STATUS |
| 0x30–0x34 | RAW / MASK / MASKED / CLEAR / ERROR_CODE |

字段、保留位、RO拒绝和reserved no-op直接遵循硬件规格第8章。AddrMode、warmup和warmdown只能保持既有读写语义，不能引入寻址或性能行为。

### 5.4 配置与输入边界

合法任务要求M≥1、Nprog≥1且Nprog%T=0、K符合格式粒度、M≤64−16=48，地址与可访问范围符合硬件规格。Nlogical仅是外部元数据。

对硬件已有错误分支，必须按CalcConfig判据和优先级产生真实配置错误；ERROR_CODE=0也是有效错误码。M/N为0、未补齐N等无专用硬件错误的输入，由测试环境判为不受支持，不能伪造errCode。

### 5.5 IRQ、完成与诊断

- RAW低11bit、MASK bit=1屏蔽；MASK不阻止RAW捕获。
- RAW_next=(RAW & ~winningClear) | newEvents；fresh event优先。
- 首个未清配置错误码、同拍CLEAR+新错误规则与硬件一致。
- DMA响应err只在rsp.fire形成对应事件；Bias/RqM按响应owner分类。
- DMA错误不fail-fast；继续等待正常排空，不能把IRQ置位当完成。
- START不隐式清旧事件；trace driver必须执行合法的重复任务流程。
- 硬件tracing仅保证reset后的首次配对。模型可有多任务debug日志，但必须标为仿真日志，不能扩展respForTracing契约。

模型/输入/环境故障与硬件IRQ分离。结果error包含 `kind/code/message/cycle/task_id/port`；kind区分hardware、input、environment、model。模型断言或unsupported环境不得转成硬件IRQ后继续发布有效性能结果。

## 6. 执行几何、FIFO与流水

### 6.1 几何

```text
T = 16
nTiles = Nprog / T
pA = 2 for INT8/MXFP8/FP8, 4 for INT4/MXFP4
kPerBeat = T * pA
kTiles = K / kPerBeat
```

正向W4A8依8-bit A节奏，kPerBeat=32，但K仍要求%64=0；不能按4-bit B将计算吞吐翻倍。

顺序固定为N tile→K pass→装载T个B拍→消费M个A拍。每个窗口重新装载权重；每个N tile重新读取A。kernel拆M为多条命令时会再次执行装载与DMA，不承诺跨命令硬件复用。

### 6.2 FIFO与token

A/B/ScaleA/ScaleB保存逻辑拍token；C保存原始部分和行token。token至少关联nt/kt/m及有效列身份；不以数值0表示invalid。

必须区分FIFO中的已返回项、请求预占/保留项、物理在途、gather半拍和cvt持有行。不同计数不能共用一个“queue length”。

首K pass使用零/bias基底；后续从C FIFO回灌；只有末K pass进入cvt和外部写回。不能将中间K pass记为外部C读写。

### 6.3 执行状态

| 状态 | 行为与转移依据 |
|---|---|
| IDLE | 等start/配置阶段；有效执行初始化 |
| DRAIN | 结构倒数；B≥T、MX另需ScaleB≥T；最终等待真实完成 |
| LOAD_B | 严格连续T次bPop.fire；保留loadW模式对应传播关系 |
| LOAD_A | aPop.fire推进m；受C空间/回灌写门、A/ScaleA、bias/rqM就绪影响 |
| NEXT_NK_TILE | 递进K/N、部分和/写回门、旧块释放和参数更新 |

不能通过不定长stall的loadW装载窗口“补足T拍”；违反连续供数说明预蓄或模型结构有误。stallA不仅是A DMA等待，还可能由scale、参数或C资源造成。

### 6.4 结构延迟

CalcConfig C0/C1、A/ScaleA/psum skew、PE有效流水、输出deskew、窗口倒数及cvt持行必须依源码推导。

当前源码给出默认整行有效流水约束的推导候选：T−1行skew+T级有效流水=31拍；这是待边沿对拍确认的结构候选，不是可配置 `array_latency=31`，也不能直接当START到C写回的延迟。

同样，Executor出现的15/31倒数赋值不能仅取最大常数应用所有窗口；必须包含实际状态覆盖关系。禁止将历史注释中的“连续2T拍无valid超时退出”实现为新完成逻辑。

完成必须匹配顶层实际条件：无下一窗口、对应阵列输出状态满足排空、C数据DMA和启用的ScaleC DMA均idle。CalcConfig拒绝路径单独结束，不伪造done。

## 7. DMA与gem5存储适配

### 7.1 六个端口

| 模型端口 | 硬件端口 | 物理传输字节 | 方向 |
|---|---|---:|---|
| dma_a | dmaA | 32 | 读 |
| dma_b | dmaB | 32 | 读 |
| dma_c | dmaC | 32 | 写、带strobe |
| dma_scale_a | dmaScaleA | 4 | 读；ScaleA/Bias/RqM共享 |
| dma_scale_b | dmaScaleB | 4 | 读 |
| dma_scale_c | dmaCScale | 1 | 写 |

每口请求/响应各至多一拍一次；各口可并行。端口峰值不等价于独享DRAM带宽。

### 7.2 扫描、地址与真实流量

成功合法任务、无额外重试计数时的逻辑项数：

```text
A logical beats = nTiles * kTiles * M
B logical beats = nTiles * kTiles * T
C output rows   = nTiles * M
```

A地址为A_BASE+kt×aKStride+m×aRowStride。B mode0为K×Nprog布局，mode1为预转置Nprog×K；精确跳转、字内偏移与FSM阶段沿CalcConfig/RdOnly实现，不按线性整矩阵memcpy替代。

每个逻辑读地址需明确记录对应的物理请求；下游cache合并/命中不能使DUT层请求凭空消失。

### 7.3 Gather、stretch与credit

- 普通对齐逻辑拍：一物理读、一逻辑push。
- 非对齐gather：先等此前物理在途排空，再lo请求/响应、hi请求/响应、拼接为一次push。不能将两读默认并行发出。
- 相邻逻辑拍不复用上一字缓存；当前gather只保存本次lo字。
- stretch：每个16-byte逻辑半片仍发一个32-byte对齐物理读，选择低/高半片；相邻窗口相同源字也会重读。它不是“一物理读产生两个逻辑拍”。
- credit直到消费者pop才归还；响应到达不会返还credit。gather具体扣减节点按第13章冻结，不能产生超容量或重复push。
- 不把计数器位宽当作任意可配置outstanding能力。

### 7.4 请求retry、顺序与包生命周期

`sendTimingReq`失败不算请求接受、不推进地址或credit；保持同一请求直到收到retry并成功发出。retry次数和valid等待周期分别统计，不能重复计算事务或重新生成地址。

硬件读口没有transaction ID，必须同请求接受顺序返回。若下游可能乱序，适配器需显式保存序号及已返回包，按顺序向DUT交付；每口每拍最多交付一响应。缓冲有界，容量与容量不足产生的反压必须体现在环境manifest；它不是免费增加的硬件FIFO。支持乱序重排前，只可使用已证明同序的内存环境。

Packet层完成不自动等于DUT的rsp.fire。未能在本拍交付的响应由适配器持有，不丢包、不重复响应。包拆分/重组、请求取消与所有权依固定gem5版本API实现并测试。

性能测量仅用timing模式。gem5 functional访问可用于初始化测试存储，但不能旁路正在建模的DMA；atomic模式不得用于发布本模型性能结果。

### 7.5 C数据写

默认每T列行的有效字节/请求拍数：4-bit=8/1，8-bit=16/1，16-bit=32/1，32-bit=64/2。4/8-bit不得跨行合拍。C基址按32B对齐，row/tile偏移和strobe仍按完整Nprog推导。

- 原始C FIFO→cvt单行保持→C DMA分拍；原始FIFO与cvt行不能合并为同一资源。
- cvt接受行时弹原始FIFO，C最后一拍请求接受后释放数据输出持行；MX行还须交付对应scale。
- data和strobe使用同源地址lane偏移；gem5 byte-enable必须保持未选中字节不被写改。
- 保存原始RTL req.addr供对拍；映射gem5写包时区分逻辑首地址与总线对齐字基址，按strobe对应lane写入，不能把已移位data再按非对齐req.addr偏移一次。包拆分只属于外部适配，不改DUT层一拍计数；memory侧包/字节统计另列。
- 不能通过新增读改写补足masked write，除非实际系统就有该转换且成本单独建模。
- doneBlock是末行末请求接受，不是全部写响应完成；最终结束须等待响应。
- 当前C写没有独立发射credit上限。必须保留真实计数语义，不能擅加限流。默认计数宽度可能回绕的环境需诊断为模型未覆盖，见第13章。

### 7.6 ScaleA、bias、rqM

ScaleA和ScaleB分别读取32-bit物理字；scale语义字节数与物理读取量分开统计。scale非对齐也走gather，相邻字节必须可访问。

ScaleA物理口存在WAIT旧响应排空、Bias取T项、RqM取T项和返回ScaleA的owner阶段；Bias与RqM同时启用时先Bias后RqM。每相可以流水发读，但不能与ScaleA同时把它当独立端口使用。

参数每N tile重取、跨K pass复用；rqM staging/提交要保留，避免下一tile更新影响旧输出行。A与ScaleA有效消费锁步，非MX FP8使用单位scale、不读输入scale。

scale布局、block16/32及MX W4A8的ScaleB步长按硬件规格第7.6章；不能以端口4字节宽度代替逻辑scale步长。

### 7.7 ScaleC集成限制

ScaleC顶层FIFO固定深2。现行写模块pop.ready未包含在途已满门控；模型不得静默改成正确的无限背压队列。

第一版支持满足硬件第9.6章约束的环境：最初用无停顿ready、零/一GEMM拍内响应的local SRAM服务。实际运行逐拍检查危险条件；违规即标environment错误、data_valid=false、performance_valid=false，不生成新的硬件errCode。

若系统需缓冲后写DRAM，必须显式建模缓冲容量、接受/响应语义、写可见性、最终排空和资源竞争；未定义该系统前不把ScaleC直接接高延迟DRAM，也不宣称SRAM ACK等于DRAM持久完成。

## 8. 输出转换的时序语义

cvt只持有一行；输入接受时同时锁存格式和参数。当前源码为行寄存后组合转换，输出有效边沿由校准确认，不额外添加“浮点计算周期”。

非量化行在数据输出fire后释放；量化行还要求同一行scale交付到ScaleC FIFO，dataDone/scaleSent避免重复发送。释放拍能否背靠背接下一行按源码组合ready执行，不人为插空拍。

Bias、ReLU和requant虽不在第一版计算数值，但其参数读取、门控、锁存及输出释放必须体现；不能把整个epilogue视为无成本。

## 9. timing-only数据与workload契约

### 9.1 数值输出标识

第一版内存请求具有真实地址、大小、strobe与响应时序；C/ScaleC写payload只能是明确标记的占位数据。manifest和每个任务结果必须包含 `model_kind="timing_only"`、`data_valid=false`。

- 不能把占位数据供真实CPU算子、Softmax、分支或动态shape计算使用。
- 静态网络性能轨迹可在下一kernel复用同地址，但调度/地址/形状来自外部描述，不能从占位输出推导。
- 不支持数据相关压缩、稀疏、动态路由或数值异常验证；需要这些能力时另立数值模型或输入真实功能结果。
- 进行RTL时序对拍时，测试环境生成满足硬件有限值等要求的数据；timing-only不复制数值assert，也不保证检测所有非法数值输入。

### 9.2 原始命令trace

采用JSON Lines，首行header含schema_version=1、hardware_profile、source_hash、clock_period_ticks、model_kind。所有地址/寄存器data以十六进制字符串表示，cycle为非负整数，时间单位为GEMM cycle。

记录类型：

| type | 必须字段 | 语义 |
|---|---|---|
| batch | earliest_cycle、task_id、commands | 每lane至多一条；到时持稳，fire后撤销；commands显式携带lane/op及硬件payload |
| resp_ready | cycle、value | 独立的绝对时间表；从指定cycle起保持ready，初值由header指定 |
| wait_completion | task_id | 先检查START ACK，已启动则等新任务done/配置拒绝及在途结束；不模拟CPU轮询成本 |
| task_begin | earliest_cycle、task_id、kernel_id | 在本task首批输入前设置仿真标记；可携带logical_shape={m,n,k} |
| task_end | earliest_cycle、task_id、kernel_id | 等本task全部ACK及wait解除后结束标记；不产生硬件命令 |

除resp_ready独立时间表外，记录顺序执行；标记不强制耗费一个硬件周期，task_begin可与首批输入同cycle。批次接受后下一批仍按实际ready等待。结束/错误状态在cycle c采样后，wait最早于c+1解除；task_end在满足earliest_cycle及前序条件的第一个边界生效。

每task最多安排一次实际GEMM启动，task_id仅是仿真关联元数据。性能trace必须提供补齐前logical_shape；原始协议测试可省略，但useful_ops/padding/有效吞吐必须输出null，不能把Nprog/Kprog猜成逻辑形状。programmed_shape从实际winning配置捕获，不由元数据覆盖。

示例batch记录，所有payload字段完整：

```json
{"type":"batch","earliest_cycle":0,"task_id":"g0","commands":[{"lane":0,"op":0,"reg0Addr":"0x00","reg1Addr":"0x00","data0":"0x00000001","data1":"0x00000000","pc":"0x00000000","tag":"0x00"}]}
```

trace parser先校验schema、字段位宽、lane唯一性和时间顺序。合法性负向硬件命令保留供DUT拒绝；parser不代替DUT进行寄存器仲裁或生成ACK。未知版本拒绝，不提供兼容猜测。

批接收和响应ready时间表分离；driver不能因读取下一batch而越过pending。wait_completion先收齐对应START批次ACK：若所有非零START候选被接口拒绝，输出input类not_started诊断并结束该等待，不等不会出现的done或CalcConfig事件；零START同样不算已启动。已成功启动的任务，即使早期DMA错误也必须继续等待实际结束。负向协议用例可以验证这些诊断，但不能输出有效GEMM吞吐。

任务间等待通过观察实际状态/IRQ完成，不写入预估完成cycle。一般性能driver的等待属于理想调度条件，必须标明；ISA/CPU开销以后由实际前端提供。

### 9.3 kernel描述与降低

kernel输入包含：kernel_id、Mlogical/Nlogical/Klogical、a/b/c格式、loadWMode、block、epilogue参数、各地址和存储区描述。

转换器负责：Nprog/Kprog合法补齐及零padding描述、M≤48拆命令、B转置/4-bit打包的布局契约、参数地址、IRQ清除、配置写、START和等待。每task保留补齐前logical_shape，M为该task的逻辑行数；programmed_shape记录实际M/Nprog/Kprog。不能在未指定存储布局时只改K数值；所有padding读写必须对应实际可访问空间。

转换得到的轨迹反映真实重复装载和A重读。B重排、量化、外部搬运及补齐准备开销若未模拟必须在报告excluded_costs列出；仅GEMM命令执行时间不能称完整kernel时间。

## 10. 性能统计与输出格式

### 10.1 与RTL同口径的事件

累计 `dmaA/B/C、scaleA/B/C、array、stallA、stallB、epilogue、busy`，严格沿硬件perf定义。DMA计req.valid周期，不计事务。stallB的DRAIN项只计结构倒数归零后等待B/ScaleB预蓄，不能把固定排空全部当B供数停顿。

### 10.2 模型附加统计

- START接受、有效execute、末A消费、末C请求、末C响应、done、RAW可见的cycle。
- 各FSM状态周期；command/response背压周期；接口拒绝、配置拒绝与DMA错误计数。
- 每口accepted requests、responses、retry次数、valid等待周期、物理字节、有效strobe字节。
- FIFO占用最大/均值/直方图，预占项、在途峰值、gather两读次数、stretch半片次数。
- A等待原因：A、scale、bias/rqM、C资源/写门；原因可重叠，不能求和当总停顿。状态周期分布单独提供互斥口径。
- kernel拆命令数、padding量、重复读取流量、系统资源竞争及任务间空闲。
- 宿主wall time、GEMM cycles/s和事件数；构建时间不混入运行速度。

公式与单位：

```text
array_feed_fraction = A_fire_cycles / executor_busy_cycles
scheduled_ops = 2 * M * Nprog * Kprog
useful_ops = 2 * M * Nlogical * Klogical
throughput_ops_per_s = useful_ops / simulated_interval_seconds
C physical_bytes = accepted_C_requests * 32
C useful_bytes = sum(popcount(strb))
```

kernel由多task组成时按实际任务累加。2按一次乘法和一次加法计；计算拍占比不是“每个PE一直忙”的测量值。逻辑shape缺失时有效工作量与吞吐为null，硬件调度工作量仍可统计。

时间区间统一为边界间的半开区间：

- task区间=[task_begin生效边界, task_end生效边界)，包含配置、IRQ操作、ACK/响应反压与earliest_cycle等待。
- GEMM区间=[START接受边界, 本次结束状态可见后的首个边界)，不将软件旧IRQ清除/配置开销混入，但包含配置检查和最终排空。
- kernel区间=[首task_begin, 末task_end)，包含task之间空闲；各task GEMM区间之和另列，不能冒充kernel总延迟。
- 系统或网络区间还须加入明确模拟的外部操作。已排除的准备、调度或算子成本不通过改变计时标记隐藏。

吞吐及字节率必须指定以上哪一个区间作分母；simulated_interval_seconds由相应tick边界计算。失败/未启动任务不生成有效吞吐。

### 10.3 输出

每次运行至少生成：

- `manifest.json`：模型/gem5/RTL版本与hash、profile、clock、内存连接/容量/重排缓冲、trace hash、seed、精度域和排除开销。
- `stats.txt`：gem5原生统计。
- `result.json`：schema_version、model_kind、data_valid、performance_valid、任务/kernel结果、errors。
- `events.jsonl`：对拍模式的逐事件cycle/tick、port、task、nt/kt/m、地址、strobe、状态与握手；正常大规模运行可关闭，不得影响行为。

多实例统计使用instance_id。所有工具的结构化结果统一JSON；错误结构按第5.5章，不将输出日志中的“done”字符串当完成凭据。

## 11. 系统组合、ISA与网络扩展边界

1. 第一版trace环境没有CPU/OS开销；它不是SE或Full System运行真实程序。
2. local SRAM是外部可寻址资源，不是内部64项FIFO的别名。
3. 初始系统采用显式SRAM管理，不默认增加LLC/coherence；Host一致性由未来平台契约定义。
4. 多实例共享SRAM/互连/DRAM时必须模拟真实竞争，不能乘单实例峰值估算。
5. 未来NPU ISA前端将指令译码、依赖与发射转为现有命令；不得为了适配预先新增WorkerBee执行opcode。
6. YOLO需卷积映射及激活/resize/concat/后处理；语言模型需attention/KV/Norm/激活/Softmax等成本。未覆盖算子必须列明，不能记0后宣称端到端性能。
7. prefill/decode分开测量，明确batch、输入/输出token、上下文长度和KV布局。
8. 功能模型及实际runtime进入后才可称“真实推理跑通”；性能轨迹闭环不能证明精度。

## 12. 验证矩阵与发布门槛

| 类别 | 最低覆盖 |
|---|---|
| 四路接口 | 单/四lane、lane1+3稀疏、混合读写、同址冲突、WRITE2整体拒绝/部分胜出、START零/多候选、读snapshot、pending反压/reset |
| IRQ与错误 | 各分源、MASK、CLEAR/new同拍、ERROR_CODE首码/零码、早期DMA错误、旧事件重复启动 |
| 几何 | M=1/2/48及超限；N=16/32/48；单/多K pass；M/N零及非法N输入诊断 |
| 格式 | INT8/INT4、MX8各交叉、MX4、FP8各交叉、正向INT/MX W4A8；所有合法C位宽、非法反向混配/FP8输出 |
| Gather | A/B字内1/末byte偏移；scale偏移1/2/3；对齐/非对齐交错；lo/hi错误；物理在途先排空 |
| Stretch | 低/高半片、相同源字重读、多K窗口、B/K非法对齐 |
| Epilogue | bias/rqM各自/同时、多N tile、参数慢响应、非MX不读scale、MX锁步 |
| 写回 | 1/2拍行、部分strobe、N tile/行地址、末拍反压、doneBlock/最终done分离 |
| 存储 | 零/一/多拍响应、有限ready窗口、请求retry、可支持的乱序返回重排、六口共享竞争 |
| ScaleC | FIFO满边界、C/scale分离释放、合法快速响应、违规环境诊断 |
| workload | M拆命令、N/Kpadding、重复权重/A流量、有效/无效统计、确定性复现 |

发布第一版必须：

- P0关键歧义关闭；默认配置及支持格式全部登记状态，未完成项不能标supported。
- 确定性集合命令/DMA/关键流水事件对拍通过；地址/大小/strobe/响应数误差为0。
- 正常任务不重复消费、不越FIFO、不提前done；不合法环境不能生成有效结果。
- 同seed同输入结果可复现；关闭debug不影响结果。
- 任何已承诺的硬件profile改变均重新校准。
- 性能报告没有将占位数据、拟合周期或未覆盖算子当真实推理结果。

对实际系统环境分别记录：模型机制已校准、内存/适配假设、与RTL可对比的范围；不能因模块单测通过就称整机已验证。

## 13. 编码前需要冻结的证据与歧义

以下是P0任务，不表示已裁定硬件变更。

| 编号 | 当前证据与问题 | 处理要求 |
|---|---|---|
| G5-U01 | 硬件规格9.4称行末拍才弹C FIFO；[顶层](../scala/main/WorkerBee.scala) 269–276行显示原始C FIFO在cvt输入fire时弹，末拍释放的是cvt持有行 | 建议澄清规格中的资源名称；裁定前不能合并两种占用模型；不擅自改原规格 |
| G5-U02 | 硬件规格9.3称请求即预留credit；[读DMA](../scala/main/WorkerBeeDmaRdOnly.scala) 330–361行的计数在finalReqFire扣减，gather lo/hi还有阶段隐式预占 | 明确物理请求、逻辑项预占和ostCredit三个口径；验证lo/hi同拍响应及pop组合边界，再冻结模型 |
| G5-U03 | [C写DMA](../scala/main/WorkerBeeDmaWrOnly.scala) 392、444–472、580行只有在途计数、没有发射限额；默认计数宽度并非流量上限 | 确认合法外存响应域与计数回绕风险；不能以新增限流器掩盖，也不能将回绕导致的早done标有效 |
| G5-U04 | 有效流水、FIFO透传、CalcConfig、窗口倒数覆盖和cvt释放拍没有已冻结的对拍时序表 | 逐源码推导、同配置RTL边沿验证；31拍候选和15/31倒数不直接发布为总延迟 |
| G5-U05 | gem5端口响应可乱序/跨tick、byte-enable与零延迟回调有版本相关行为 | 固定gem5版本；确认API、同序适配和采样边界，先通过协议测试 |
| G5-U06 | ScaleC高延迟/反压受到硬件9.6限制 | 第一版限定合法local SRAM服务；新增缓冲系统另行定义，不直接视为支持DRAM |

U01/U02影响性能结构，裁定原则为先澄清而非自动改RTL。未裁定时可以做P1接口骨架研究，不能冻结受影响的GEMM性能实现或误差承诺。

2026-10-28 已补充默认配置下的 8 项 RTL 子模块边沿单测：FIFO、CalcConfig、cvt、阵列波前/skew/deskew、executor 窗口、读 credit/gather/stretch。首轮 FIFO 与 CalcConfig 通过；完整集合虽已编译并发射 RTL，但 8 项均在宿主仿真启动时失败，未进入测试体，因此不构成时序通过或 RTL 回归证据。系统日志记录 AMFI 签名问题，Java 启动的子进程在动态加载器入口阻塞；未修改安全策略或签名来绕过检查。

`calibrate.py` 只有在所选 suite 全部测试通过、采样非空、源码前后相同且基线/RTL/采样/XML 完整性检查通过后才生成 `calibration.json`。子模块报告即使全绿也必须保留 `edge_calibrated=false`、`p0_accepted=false`、`performance_valid=false`、`data_valid=false`；不能替代顶层写响应排空、ScaleA 参数 owner、ScaleC 服务域或 gem5 协议验证。G5-U01–U06 本次均不关闭。

## 14. 版本管理

模型规格与硬件规格分别版本化。接口/trace不兼容改动升主版本；新增兼容支持域升次版本；澄清文字升修订号。草案阶段仍须更新状态，不能将“计划支持”改写为“已实现”而无测试证据。

实现版本每次发布记录：支持profile、格式与环境范围、验证集合、已知限制和与硬件基线的对应关系。源码更名、模型统计扩展和系统环境变动也须检查trace与报告可比性。
