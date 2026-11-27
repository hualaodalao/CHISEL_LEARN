# Loom 互联生成套件架构规范 v1.1

> **口径更新（S6，2026-11-25）**：用户配置与生成管线已切换为 CHI 原生口径（`loom/v2`）。
> 本文的 v1.1 正文按历史保留：其中 **MNet/SNet、四通道（rreq/wdata/rdata/wresp）、
> burst/beat 信用、(src,id)、native/AxiLite4/TL-UL 协议壳、banked switch** 等概念已退役，
> 不再是用户口径（对应实现隔离于 `src/loom/legacy/`）。现行权威规格：
> [ir_gen_spec.md](ir_gen_spec.md)（IR v2 / 门禁 / GEN 管线 / CLI）与
> [protocol_spec.md](protocol_spec.md)（CHI 硬件）。节点角色（RN/SN/HN/MN）+ NodeID、
> SAM、REQ/RSP/DAT/SNP 通道类、QoS(4b)→VC、三级流控为现行术语。
> 方法论（IR→门禁→CDG 无环证明→发射→形式化/签核）不变。

> 状态：v1.1 发布（2026-11-20，历史正文） | 发布记录见 [release_notes_v1.0.md](release_notes_v1.0.md)、[release_notes_v1.1.md](release_notes_v1.1.md) | 范围：可重构、可自动化、构造性无死锁的片内互联生成套件
> 上游调研：DEEP_RESEARCH_selfdeveloped_interconnect.md（已归档，见 git 历史）

## 0. 范围、裁定与术语

本规范定义一套**生成器**而非一条总线：输入是配置（IR），输出是 RTL、测试台、形式化 harness、文档与估算报告。生成器本身不发明协议语义，只负责在受约束的参数空间内组合已验证的构件。

已裁定：

- 名称：**Loom（织机）**——配置是织法，生成物是 fabric；CLI 为 `loom`，模块前缀 `Lm`。
- 分组策略：**默认 `flat`、`banks = 1`，生成器不自动分组**；是否 banked 或拆成多 Router 由用户按实际情况显式选择，生成器只输出时序风险告警。
- 交付形态：套件架构规范（本文）。
- 技术底座：配置 IR（YAML/JSON）+ Chisel 后端，后端可替换。
- 无死锁策略：构造性保证为主，形式化检查兜底；拓扑不设白名单，但任意 Router 图必须通过通道依赖图（CDG）无环的静态证明，否则只能走 Unsafe。
- 图形化范围：参数表单 + 拓扑预览 + 带宽/延迟/面积估算报告；图形界面只读写 IR。

术语：

| 术语 | 含义 |
|---|---|
| Endpoint（端点） | 挂在 fabric 上的主/从设备，可以是原生 Stream 口，也可以是 AXI4/AxiLite4/TL-UL 协议壳 |
| XP（Crosspoint） | 交换节点内部的一个交叉点：输入队列 + 路由 + 输出仲裁 + 信用管理 |
| MNet | 主设备侧网络：每主一个 NI，负责 SrcID 分配、返回槽、排序标签、按类注入准入 |
| SNet | 从设备侧网络：每从一个 NI，负责地址窗口译码、响应槽、未映射/越界错误的应答 |
| Router | 可组合交换单元：N 入 M 出，按 VC 排队与路由；Router 之间可同步或异步连接 |
| Pipe | 可插入任意链路的 1–N 级流水/缓冲，用于物理拉远；其占用必须计入信用在途 |
| Partition | 可独立发射的顶层切分；边界只能是链路边界且必须寄存 |
| Class（流量类） | CTL / LAT / BULK 三类之一，各自持有互不相交的 VC 集合 |
| Gate（死锁门） | 构造性无死锁必须满足的静态条件，见第 5 章 |
| Safe mode | 只接受满足全部 Gate 的配置；默认模式 |
| Unsafe mode | 允许越界配置，但必须通过形式化属性集才能发射 RTL |
| IR | 唯一真源；CLI 与 GUI 都只读写 IR |

---

## 1. 设计目标与非目标

### 1.1 目标

1. **可重构**：拓扑、端口数、位宽、VC 数、信用深度、时钟域、协议壳、可选特性均由 IR 决定，改配置不改代码。
2. **可自动化**：一条命令从 IR 产出 RTL + TB + formal harness + 报告；同一 IR 必须产出逐位一致的 RTL；可直接嵌入 CI 矩阵。
3. **构造性无死锁**：任何通过静态检查的配置在协议层面可证无死锁；形式化只作兜底与实现校验，而不是唯一防线。
4. **可检查**：每个配置产出可复现的检查证据（Gate 通过表、形式化属性结果、估算报告）。
5. **图形化配置**：非专家可用表单与拓扑预览完成 80% 的配置；专家可退回文本 IR。
6. **可分区**：同一配置可按子树或时钟域切成多个可独立发射的 top，顶层只做连线与验证；切分不得改变协议语义与死锁结论。

### 1.2 非目标

- 不做缓存一致性协议生成（snoop filter/MESI 不在本套件范围；一致性留在 CPU 簇边界，由外部 IP 或独立项目承担）。
- 不做物理设计、时序收敛、DFT。
- 不追求"任意图拓扑无需证明"。任意 Router 图可以配置，但必须通过 CDG 无环检查；单级 crossbar、树、维序 mesh 只是已验证模板。
- 不生成驱动/运行时；只生成硬件与验证资产。
- 估算报告不是签核数据。

---

## 2. 不变量（不可配置）

以下八条写死在生成器中，IR 不得关闭（Unsafe mode 也不能违反 I1–I5、I7–I8）：

| 编号 | 不变量 |
|---|---|
| I1 | 每个被接受的请求最终恰好得到一个响应（成功或错误），不允许无响应、重复响应 |
| I2 | 响应是终态，不得触发新的请求 |
| I3 | 请求注入前必须原子地预留源端返回槽与目的端响应槽 |
| I4 | 同地址 RAW/WAW 保序；标记 `ordered` 的同源同目的事务保序；其余可乱序 |
| I5 | 信用不得透支；跨时钟域信用回归按保守往返计数 |
| I6 | Safe mode 下不得绕过 Gate 检查；Unsafe 必须通过形式化属性集 |
| I7 | IR 是唯一真源；GUI、CLI、CI 不得各自维护配置副本 |
| I8 | 输出确定性：同一 IR 的 RTL 文件树与内容哈希一致 |

I3 是整套构造性证明的支点：它把"请求等响应缓冲"这一状态从事务图中删除，而不是靠调大缓冲去规避。

---

## 3. 总体架构

### 3.1 生成器分层

五层，自上而下单向依赖：

```
┌──────────────────────────────────────────────┐
│ L5 前端：表单 / 拓扑预览 / 估算报告（只读写 IR） │
├──────────────────────────────────────────────┤
│ L4 驱动：CLI、CI 矩阵、缓存、产物打包            │
├──────────────────────────────────────────────┤
│ L3 验证闭环：TB 生成、断言包、formal harness    │
├──────────────────────────────────────────────┤
│ L2 生成后端：IR → FabricGraph → Chisel 参数     │
├──────────────────────────────────────────────┤
│ L1 配置层：IR schema、规范化、静态检查          │
├──────────────────────────────────────────────┤
│ L0 构件库：Router/Pipe/信用/CDC/协议壳（手写、已验） │
└──────────────────────────────────────────────┘
```

关键约束：**上层不得绕过下层**。GUI 只能产出 IR；IR 只能经 L1 校验后进入 L2；L2 只能调用 L0 中已签核的构件，不允许为某个配置临时手写 RTL。

### 3.2 Fabric 结构：MNet / Router / SNet / Pipe

生成器内部使用的 fabric 结构不是"一个 crossbar"，而是四类可组合单元：

```
master ─ LmShell ─ NI ─┐
master ─ LmShell ─ NI ─┤ MNet ── Router ══(async)══ Router ── SNet ── NI ─ LmShell ─ slave
master ─ LmShell ─ NI ─┘            │                         │
                                  Pipe                      Pipe
                                 (可选)                    (可选)
```

- **MNet（主侧网络）**：聚合所有主设备 NI。负责 SrcID 分配、源端返回槽、`ordered` 标签、按流量类做注入准入。只暴露主设备方向的端口。
- **SNet（从侧网络）**：分发到所有从设备 NI。负责地址窗口译码、目的端响应槽、未映射/越界访问的合法错误响应（I1 要求必须回答）。只暴露从设备方向的端口。
- **Router（交换单元）**：拓扑的拼接单位，外壳由 Ingress、SwitchCore（crossbar）、Egress 三段组成；Router 之间通过 `RouterLink` 连接，链路可同步或异步。细节见 3.3。
- **Pipe（流水级）**：可插入任意链路，1–N 级寄存器/缓冲，用于把物理位置拉远。Pipe 必须是 per-VC 保序 FIFO，其占用的槽位计入信用在途（否则在途低估会直接导致死锁）。

单级 crossbar 时，MNet/SNet 退化为**零延迟的端口分组**，不增加跳数；只有多 Router 时它们才成为真实模块。MNet/SNet 是**物理共享端口**：挂在其上的多个端点共享该端口带宽，每个网络每拍每方向只允许一个事务。RTL 仍为每个端点实例化 NI 与端口（内部扇出），由 MNet/SNet 令牌仲裁器落实共享语义；Router 的 `in/out` 与估算按网络数计。

> 注：MNet/SNet 是结构划分，不是协议层。任何跨侧事务的语义、排序与错误规则与第 5 章完全一致；划分只影响模块边界，不影响证明。

### 3.3 Router 内部：crossbar 是 switch core

Router 不是黑盒，内部固定三段：

```
in0 ─ Ingress(每 VC 队列) ─┐
in1 ─ Ingress(每 VC 队列) ─┤  SwitchCore   ├─ Egress(每口 VC 仲裁) ─ out0
in2 ─ Ingress(每 VC 队列) ─┘  (crossbar)   └─ Egress(每口 VC 仲裁) ─ out1
        ↑ 入口 Pipe            ↑ 级间 Pipe        ↑ 出口 Pipe
```

- **Ingress**：每输入端口每 VC 一个队列，负责 VC 分配与反压。
- **SwitchCore（crossbar）**：把输入 VC 连到输出端口，有两种形态：
  - `flat`：单级全交叉。经验阈值 ≤8×8 @256b，随目标频率与工艺调整。
  - `banked`（v1.0）：输出分 G 组，每组内仍为 `N×(M/G)`，每个输出后加 1 级寄存；不新增仲裁、不改变路由，交叉点保持 `N×M`。收益是分组布线与出口寄存（延迟 +1 拍），时序代理在估算中体现。Clos 式两级（`N×M/G + G×M`）会把中间级暴露给 CDG，留作后续选项。
- **Egress**：每输出端口按 VC 仲裁（WRR + 老化）并管理信用。

**时序预算**：默认 `switch.kind = flat`、`banks = 1`，生成器不做任何自动分组或自动拆分。`timing: {maxFanin, maxCombDepth}` 只用于风险提示：估算超过阈值时在 `checks.json` / `estimate.json` 输出 `LM-W-TIMING` 警告，并附带建议 banks 数与 banked 出口寄存带来的 +1 拍延迟；是否采纳由用户显式写回 IR。Pipe 可插在入口、级间、出口三处，每处 0–2 级，同样由用户显式配置。

**一条硬约束**：级间分解必须是**纯流水**——只加寄存器，不引入新的仲裁或独立缓冲。若某级引入独立仲裁/缓冲，它就已经是一个 Router，必须作为节点进入 CDG 检查；否则会把未检查的依赖环藏进 crossbar 内部，构造性证明失效。

> 因此"超大 crossbar"有两条合法出路：① banked 纯流水（对外仍是一个 Router，延迟 +1 拍、交叉点不变，靠分组布线改善时序）；② 拆成多个 Router 用 Link 连接（对外可见，进入 CDG 与 floorplan）。**两条都由用户选择，生成器不替用户决定**；默认保持 flat，只在报告里提示风险。对时序敏感的配置，推荐 ②，因为可见性对物理实现更有利。

---

## 4. 配置模型（IR）

### 4.1 结构

IR 分为八段，全部为必填或显式默认：

| 段 | 内容 | 约束 |
|---|---|---|
| `meta` | name、schemaVersion、protocolVersion、seed | `name` 参与模块前缀与输出路径 |
| `clockDomains` | 域 id、频率、复位 | v1 最多 4 个域 |
| `endpoints` | 角色、协议、位宽、地址窗口、outstanding、所属时钟域、流量类、挂接点 | 地址宽度全 fabric 统一，默认 40 |
| `fabric` | MNet/SNet 列表、Router 图（router/link）、Pipe 插入点、可选预置模板 | 任意 Router 图须过 CDG；link 显式声明 sync/async |
| `qos` | 类名、VC 数、队列深度、仲裁、权重 | 恰好三类：ctl/lat/bulk |
| `flow` / `deadlock` | 信用方案、最大包长、Gate 列表、formalFallback | Safe mode 强制全部 Gate |
| `partitions` | 自动按域/子树或手工列出成员；分区顶层、总顶层、验证顶层 | 切分只在链路边界 |
| `features` / `outputs` | 多播、原子、poison、ECC；产物开关与目录 | 特性关闭时不得生成对应端口 |

### 4.2 规范化

IR 在任何处理前先规范化为 canonical JSON：键按字典序、端点与链路按 id 排序、省略默认值、数值统一为十进制。规范化后的 SHA-256 即 **config hash**，用于输出路径、缓存键与回归基线。

没有这一步就无法满足 I8；仓库已有前车之鉴——Scala `HashSet` 迭代序不稳定会导致 firtool 结构去重失效，所有集合在发射前必须转有序 `Seq`。

### 4.3 可重构维度

| 维度 | 取值范围（v1） | 影响 |
|---|---|---|
| 拓扑 | 预置 crossbar / 树 / 维序 mesh，或任意 Router 图（须 CDG 无环） | 面积、跳数、Gate G1 |
| Router 基数 | 1–16 入 / 1–16 出 | 交叉点、面积 |
| Router 内部 | **默认 flat / banks=1**；banked 需显式开启（banks 2–8）；入口/级间/出口 Pipe 0–2 级；时序预算 maxFanin/maxCombDepth 仅告警 | 时序收敛、面积、延迟 |
| Pipe | 每链路 0–8 级 | 延迟、面积、物理可达距离 |
| 分区 | 1–8 个分区 top + 总顶层 + 验证顶层 | 单模块规模、后端并行度 |
| 端口 | 1–32 主、1–32 从 | Router 交叉点数量 |
| 数据位宽 | 32/64/128/256/512 bit | 链路面积、每拍事务数 |
| VC | 每类 1–4 个，深度 2–32 | 面积、HOL、吞吐 |
| 信用 | 每 VC 2–64 | 吞吐、返回槽数量 |
| 时钟域 | 1–4，链路级 CDC 或 Router 级 CDC | 延迟、面积 |
| 协议壳 | native Stream / AXI4 / AxiLite4 / TL-UL | 端点适配逻辑 |
| 特性 | 多播组（≤8）、近 home 原子、poison、ECC | 可选状态机与验证负担 |

---

## 5. 构造性无死锁

### 5.1 状态模型

事务分请求阶段与响应阶段。资源集合为：每跳每 VC 的缓冲槽、目的端响应槽、源端返回槽、链路信用。若事务 t1 所需资源被 t2 持有，则记等待边 t1 → t2。死锁即等待图中存在非空强连通分量且无外部推进。

### 5.2 九道 Gate

| Gate | 条件 | 检查方式 |
|---|---|---|
| G1 | 路由确定性且通道依赖图（CDG）无环：预置模板天然满足；任意 Router 图由工具构造 CDG 并做环检测；banked 纯流水级不计入 CDG，含独立仲裁的级按 Router 展开 | CDG 构造 + 环检测（不是拓扑白名单） |
| G2 | 请求与响应使用不相交的 VC/物理资源集 | VC 分配表 |
| G3 | 请求注入时原子预留源端返回槽与目的端响应槽；预留失败则在源端阻塞 | 端点参数 + 信用模型 |
| G4 | 响应为终态，不产生请求 | 端点协议壳的类型系统 |
| G5 | 三类流量各自持有互不相交的 VC 集合 | QoS 段 |
| G6 | 信用守恒：不超发、仅在消费后归还；**Pipe 与异步 FIFO 的占用计入在途**；CDC 信用按往返保守计数 | 信用配置 + 形式化 P3/P7 |
| G7 | 控制类消息单 flit，store-and-forward | 包长参数 |
| G8 | 仲裁公平：WRR + 老化或 RR；不允许饥饿 | 仲裁配置 |
| G9 | Pipe/异步边界透明：每 VC 独立 FIFO、保序、不跨 VC 合并；边界两侧信用闭环 | Pipe 配置 + 形式化 P3 |

### 5.3 定理与证明骨架

**定理**：满足 G1–G9 的配置，其协议层事务系统无死锁、无饿死。

证明骨架：

1. G1 使请求通道依赖图无环（Dally–Seitz 条件），故请求阶段不存在循环等待。
2. G2 与 G4 使等待图不跨越请求/响应平面，且不存在"响应等待请求"的边。
3. G3 使请求阶段永远不会等待响应槽——该等待状态不可达，而不是"很少发生"。
4. G5 与 G7 消除类间隐藏依赖与控制类的包级 HOL。
5. G6 与 G8 保证持有信用的下游在有限步内消费，等待链必然终止于一个可推进的最下游事务。
6. G9 保证在任意合法位置插入 Pipe 或异步边界不改变上述依赖结构：边界是每 VC 保序 FIFO，信用在边界两侧闭环，等待图仍然无环。
7. 于是等待图是"跨跳 + 最终消费"的有向无环图；若仍有事务不可推进，则存在一个不被任何人持有的资源被等待，矛盾。

**边界**：这是协议层证明。实现层仍可能因隐藏共享缓冲、信用归还乱序、CDC 指针错误而破坏前提，因此形式化兜底不可省略（第 6 章）。

### 5.4 Router 图与预置模板

预置模板天然满足 G1，可直接使用：

- **T1 单级 crossbar**：只有一个 Router，任意 M×N，默认内部 flat；用户可显式开启 banked 纯流水，或拆成多 Router（见 3.3）。
- **T2 树状分层 crossbar**：由 Router 组成的树，上行/下行链路显式声明（`tree_3r` 为最小模板）。
- **T3 维序 mesh**：Router 声明 `pos {x,y}` 与 `routing: "xy"`；CDG 按转向模型裁剪（X→Y 允许，Y→X 与 180° 回转禁止），路由表按 X 先 Y 后推导（`mesh_2x2_xy`）。

任意 Router 图：工具从 IR 构造"节点 = 有向通道（link）"的通道依赖图。在每个 Router 上，入通道可等待任意出通道，唯一排除的是 U 转（回到刚离开的节点）；自环 link 计为真实依赖环。声明 `routing: "xy"` 的 Router 额外按转向模型裁剪依赖。工具做确定性环检测，有环则拒绝（LM-G1），并打印环上的通道链。因此不再限制形状：带逃生通道的单向环、不规则树、多级 hub 都可以，只要 CDG 无环。Router 内部的 banked 分解若为纯流水，不进入 CDG；若某一级含独立仲裁或缓冲，则该级按 Router 展开后再做环检测。自适应路由仍不开放；若配置显式声明 VC 升级方案，则只能走 Unsafe 并跑完整形式化。

路由表由工具推导而非手写：router 名与 link 均排序后建图，从每个 router 做 BFS 求到各目的 router 的最短路径，等距下一跳按名字排序取第一个，从而得到确定性 `routeSlave` / `routeMaster`；`routing: "xy"` 的 Router 改用 X 先 Y 后（响应方向 Y 先 X 后）的确定性下一跳。硬约束只有两条——有 master 的 router 必须能到达所有 slave，有 slave 的 router 必须能到达所有 master；叶分支之间的不可达不视为配置错误。多 Router 顶层 `LmNetworkTop` 仍只接同步链路；async 链路只出现在分区 wrapper 的切分边上：`LmWrapperTop` 按分区时钟域给出 `domainClk/domainReset` 端口，跨域切分边自动插入双向 `LmAsyncLink`（详见 15 章）。

---

## 6. 形式化兜底

### 6.1 抽象模型

形式化不证明数据正确性，只证明控制语义。抽象方式：每个事务一个 token，数据载荷与位宽折叠，队列用计数器建模，保留 VC、信用、响应槽与路由。该抽象把状态空间压到可在分钟级收敛的规模。

### 6.2 属性集

| 编号 | 属性 | 说明 |
|---|---|---|
| P1 | RESP_TOTAL | 被接受的请求最终恰好一个响应 |
| P2 | NO_ORPHAN | 不存在无匹配请求的响应 |
| P3 | CREDIT_SAFE | outstanding ≤ 信用池；无下溢 |
| P4 | ORDER | 同址 RAW/WAW；`ordered` 同源同目的保序 |
| P5 | PROGRESS | 公平环境下 `GF(req) ⇒ GF(grant)` |
| P6 | NO_LOSS | 拍数等于 len+1，无丢失/重复 |
| P7 | CDC_CREDIT | 跨域信用永不过发 |
| P8 | ERROR_TOTAL | 译码错误与 poison 也必须被回答 |
| P9 | PARTITION_EQUIV | 分区组合后的可观测事务序列与未分区模型一致 |

### 6.3 方法

先 k-归纳，深度取 `2 × 最大跳数 × 每 VC 信用`；不收敛则转 IC3/PDR。规模超限时按 Router 或 Partition 做 assume-guarantee 分解：先证每个 Router 的局部不变量与链路信用守恒，再组合出全局进展性。分区场景下，各分区独立证明后再证明边界契约（S1–S4，见第 9 章）。该路线与 DVCon 的层次化形式化 + progress checking 一致，也是 mesh 规模唯一可关闭的路径。

### 6.4 配置空间覆盖

Safe mode 的配置空间是有限的（拓扑模板或 Router 图 × 端口区间 × 位宽 × VC × 信用 × Pipe × 域数 × 分区数）。CI 维护"代表元矩阵"：每个等价类至少一个配置跑完整形式化，等价类内抽查。Unsafe 配置不进入矩阵，必须单独签核。

### 6.5 当前实现（S8）

`src/loom/formal/token_8x8.sv`：8 主 8 出的 token 模型，每事务一个 token，输出端口忙 2 拍后返回一个响应；断言覆盖 P1（限时应答）、P2（无孤响应/不重复）、P3（pending 期间不再接收、只接受本口的 grant）与路由安全（grant 的目的地与端口一致）。`src/loom/formal/token_rr8.sv`：单输出 8 路 RR 模型，覆盖 P5 的有界进展。`token_response.sv`：请求/响应槽计数模型，断言"accepted − responded = 在途数"与"响应只对应活跃槽"，覆盖 P6/P8。`token_cdc.sv`：跨域信用计数模型，断言 `used ≤ depth`，并在接收侧按信用假设约束环境，覆盖 P7。`token_cdc_buf.sv`（v1.1）：async 边界数据缓冲模型，对端指针经 0–2 级单调同步器可见，断言真实占用 ≤ depth 且弹出的数据恰为最老序号，覆盖 G9 的数据面。门禁 `p1_p5_8x8.sh`（安全 depth 12、RR depth 20）与 `p6_p8.sh`（depth 12）均用 yosys-sat；`loom formal` 一次跑完五个模型。

实测 sby 0.71 + z3 在 8×8 模型 depth 40 上 10 分钟不收敛，因此采用有界签核；P6–P8 已在 S17 以独立 token 模型覆盖，P9 以 wrapper 事务 trace 与 `loom diff` 覆盖。P4（ordered 保序，依赖尚未实现的 `ordered` 标签）与深归纳/IC3 仍留待后续。v1.1 起 async 分区边界不再被拒绝：G9 由 `token_cdc_buf`（数据面）与 `token_cdc`（信用面）两个抽象模型覆盖，物理 FIFO 由 `LmCdcSpec` 的双时钟用例覆盖。

---

## 7. 生成流水线

```
IR ──规范化──► canonical JSON ──► config hash
   ──► C0–C3 检查（schema → 结构 → Gate → 资源上界）
   ──► L2 FabricGraph（MNet/SNet 分解 → Router 图 → 分区切分 → Pipe 插入）
   ──► 估算模型 ──► report.json（超预算则 fail）
   ──► Chisel 参数对象 ──► L0 构件组合 ──► RTL
   ──► TB + 断言包 + formal harness + 文档
   ──► 产物目录 generated/loom/<name>/<hash8>/
```

每一步的中间产物都落盘（`canonical.json`、`graph.json`、`checks.json`、`estimate.json`），因此任何一次发射都可复现、可审计、可 diff。

### 7.1 静态检查分级

| 级别 | 内容 | 失败后果 |
|---|---|---|
| C0 | schema、必填、取值范围、引用完整性 | 退出码 2 |
| C1 | 结构合法性：地址窗口不重叠、端口数、位宽匹配、时钟域可达 | 退出码 3 |
| C2 | Gate G1–G9 逐条 | Safe mode 退出码 3；Unsafe 转入形式化 |
| C3 | 资源上界：Router 交叉点、缓冲总量、信用总量、扇出 | 退出码 3 |

### 7.2 确定性发射

- 所有集合先排序；模块名由 `name + hash8 + 路径` 决定。
- 生成器不读环境变量、不读时间戳、不依赖文件系统遍历顺序。
- 同一 IR 连续两次发射，RTL 目录树的逐文件哈希必须一致（验收项 A2）。

---

## 8. RTL 构件库（L0）

### 8.1 模块清单

| 模块 | 职责 | 复用现状 |
|---|---|---|
| `LmTop` / `LmVerifyTop` | 总顶层（连线 + 分区例化）与验证顶层（暴露边界端口、可替换 stub） | 单 Router `LmTop` 已实现；`LmVerifyTop` 待做 |
| `LmNetworkTop` | 多 Router 网络顶层：NI + Router + RouterLink 拼接 | 已实现（S7，仅同步链路；异步配置改由分区 wrapper 发射） |
| `LmPartitionTop` | 分区顶层，稳定端口契约 + 独立 filelist | 已实现（S10 + v1.1）：同步/异步边界 + ≥1 级 Pipe + ports.json |
| `LmWrapperTop` | 组合全部分区的总顶层，按链路 id 连接边界 | 已实现（S11），同步链路 |
| `LmPartitionStub` | 与被替换分区同端口契约的 stub，逐请求返回错误 | 已实现（S11，S4）：每接受一个请求恰好一个 err 响应 |
| `LmMNet` | 主侧聚合：NI、SrcID、返回槽、ordered 标签、注入准入 | 以 `LmMasterNi` 实现（S7） |
| `LmSNet` | 从侧分发：NI、地址窗口译码、响应槽、未映射错误应答 | 以 `LmSlaveNi` 实现响应槽与 qos 剥离（S7）；地址窗口待补 |
| `LmRouter` / `LmNetRouter` | Router 外壳：Ingress + SwitchCore + Egress | `LmNetRouter` 已实现（S7）：路由表 + 每口每类仲裁，无内部缓冲 |
| `LmSwitchCore` | crossbar：flat 或 banked；banked 为输出分组 + 每输出 1 级寄存 | 已实现（S15）：flat 默认；banked 不新增仲裁，延迟 +1 拍 |
| `LmIngress` / `LmEgress` | 每口 VC 队列；出口 VC 仲裁与信用 | 单 Router 每口一个类 VC 队列；网络 Router 每口 × 类一个（S8） |
| `LmRouterLink` | Router 间链路：sync/async、位宽、Pipe 级数 | 已实现 sync/async 独立构件（S7），async 已过双时钟 1000 项用例 |
| `LmPipe` | 1–N 级寄存器/缓冲，per-VC 透明保序 | 已实现 0–4 级（S7） |
| `LmPort` | 入/出口：切片、反压、错误检查 | 新建 |
| `LmVcQueue` | 每 VC 的同步 FIFO | 已实现（S8），深度取类深度 |
| `LmArbiter` | WRR + 老化；严格优先级仅作二类降级 | `LmRrArbiter`/`LmRrArbiterN`/`LmWrrArbiter` 已实现（S3/S4/S7）；S8 起准入与出口两级都用类权重 |
| `LmCreditMgr` | 信用发放/归还、预留原子性 | 已实现（S3） |
| `LmRespPool` | 源端返回槽与目的端响应槽 | 以 `LmCreditMgr` + `LmSlotPool` 实现，S7 上移到 NI |
| `LmWidthAdapter` | 宽窄转换 | 已实现 2:1/1:2 beat 适配（S6） |
| `LmAsyncFifo` / `LmCdcCredit` | 跨域数据与信用 | 已实现 gray 指针 FIFO 与保守信用（S6） |
| `LmMcast` | 多播复制与响应聚合 | 可选 |
| `LmErrLog` / `LmPerfMon` | 错误记录、带宽/延迟计数器 | 新建 |
| `LmShellNative` | 原生四通道（REQ/WDATA/RSP/RDATA） | 已实现（S9），burst 拆分/响应归并 |
| `LmShellAxi4` / `AxiLite4` / `TlUl` | 协议壳 | 已实现（S9）：AXI4 INCR 子集、AxiLite4 单拍、TL-UL Get/Put 单拍；未复用仓内 Axi4 bundle（可选信号多，壳内用最小子集） |

### 8.2 已知缺口（必须先补）

仓库 `Stream` 库明确未移植 CDC FIFO、`StreamWidthAdapter` 与 formal API；Loom 侧已在 S6 补上 `LmAsyncFifo`、`LmCdcCredit`、`LmAsyncLink` 和 `LmWidthAdapter`，formal API 仍待补。`Stream` 的 `valid/ready` 语义与 `IMasterSlave` 方向约定直接沿用，避免与现有模块不一致。多 Router 顶层（`LmNetworkTop`）保持单时钟；async 链路只在分区 wrapper 上接入（v1.1 S1/S2），每域独立复位，不做跨域复位握手。

### 8.3 协议语义冻结

协议语义（通道定义、字段、错误码、状态机）单独版本化为 `protocolVersion`，与 `schemaVersion`、`rtlLibVersion` 组成兼容矩阵。生成器只组合，不改语义；语义变更必须走独立评审，不能由某个配置的临时需求驱动。

---

## 9. 分区与多 Top 生成

### 9.1 目的

把一个大 fabric 切成多个可独立发射的顶层，解决三个工程问题：单模块规模（综合/布线/仿真时间）、后端并行与增量构建、以及"物理顶层"与"验证顶层"分离。总顶层只做连线与例化，不承载业务逻辑。

### 9.2 切分规则

- **只在链路边界切**：NI↔link、Router↔link、link↔NI。禁止切在 VC 队列、信用池或 Router 内部。
- **每条跨分区链路必须寄存**：Pipe ≥ 1 级或异步 FIFO；跨时钟域必须 async。
- **职责归属不变**：地址译码始终在 SNet，SrcID 分配始终在 MNet，分区不得把两者搬到对方侧。
- **stub 契约**：验证顶层把某分区替换为 stub 时，stub 必须广告相同的 credit、返回相同数量的响应、保持相同的 per-VC 顺序。

### 9.3 IR 形态

```yaml
partitions:
  mode: auto            # auto | manual
  by: clock-domain      # clock-domain | subtree | manual
  tops:
    - {name: compute, clock: npu, members: [mnet_cmp, r_cmp, snet_cmp], kind: partition}
    - {name: system,  clock: soc, members: [mnet_sys, r_sys, snet_sys], kind: partition}
    - {name: top,     kind: wrapper}
    - {name: verify,  kind: verify, stubs: [system]}
```

`mode: auto` 时由工具按 `by` 生成候选切分，写入 `canonical.json` 供评审确认；**自动切分的结果必须回写 IR**，不允许只存在于工具内存中（I7）。

### 9.4 产物与命名

| 产物 | 命名 | 内容 |
|---|---|---|
| 分区顶层 | `Lm_<name>_<partition>_top` | RTL + `ports.json` + filelist + 独立 hash |
| 总顶层 | `Lm_<name>_top` | 例化全部分区，自动插入边界 Pipe/CDC |
| 验证顶层 | `Lm_<name>_verify_top` | 暴露边界端口，可替换 stub，供 UVM/FPGA 使用 |

分区 hash 独立计算，实现增量构建；总顶层 hash 汇总所有分区 hash 与边界配置。任一分区变化只重发该分区与总顶层。

### 9.5 分区保持条件 S1–S4

| 编号 | 条件 |
|---|---|
| S1 | 切分只发生在链路边界 |
| S2 | 边界寄存，无组合路径穿越分区 |
| S3 | 信用闭环跨边界保持；Pipe/异步 FIFO 占用计入在途 |
| S4 | stub 与被替换分区具有相同的信用与响应契约 |

满足 S1–S4 时，分区不改变第 5 章的无死锁结论，形式化可按分区独立证明后组合；否则该配置只能 Unsafe 并跑完整形式化。

当前实现（S10/S11）：S1 由成员归属与 attachment 共置检查保证；S2 由 `boundaryPipe ≥ 1` 与 `LmPartitionTop` 的边界 Pipe 保证；S3 由"切分边只允许 Router-Router、信用与响应槽全部位于 NI"保证；S4 由 `LmPartitionStub` 的"每请求恰好一个错误响应"契约与定向用例保证。P9 用单分区/双分区 wrapper 的事务 trace 比对；S12 起 `loom diff` 可在配置级对照未分区/分区版本的端点、Router 与链路契约（分区差异只提示）。

> 反例：把某个 VC 队列切到两个 top（S1 违反）会让信用池跨边界分裂，两侧各自认为在途为 0，直接透支；把边界做成组合直连（S2 违反）会让"分区"只存在于文件里，规模没有下降。

---

## 10. 验证与签核

每个配置自动产出四类资产：

1. **定向 TB**：最小连通性、单笔读写、错误响应、跨域往返。
2. **随机 TB**：按类流量模型生成请求，scoreboard 按 I1/I4 检查；覆盖注入、背压、信用边界。
3. **断言包**：协议合法性、信用守恒、响应唯一性、地址译码全覆盖；作为仿真与形式化共用。
4. **formal harness**：P1–P9，附抽象说明与收敛深度。

**分区级**：每个 partition top 与 stub 对端独立仿真与形式化；verify top 做全系统联调；P9 检查分区组合后与未分区模型的事务级等价。

签核报告包含：Gate 通过表、形式化属性结果与运行时、估算 vs 仿真对照、分区边界检查（S1–S4）、未覆盖项。任一项缺失即视为未签核。

---

## 11. 图形化前端

前端只做三件事：编辑 IR、预览拓扑、展示估算。**不生成 RTL，不维护第二份配置。**

- **结构编辑器**：按硬件层次组织，`partition → router → mnet/snet`。分区是一等对象，Router 只能在分区内创建，MNet/SNet 只能挂在 Router 下；删除父级会级联清理子级与引用。
- **时钟先行**：Clocks 区块维护时钟域列表；分区、Router、MNet/SNet 的时钟下拉只列出已创建的时钟，不允许自由输入。修改分区时钟会级联到其成员，端点时钟跟随其挂接网络。
- **连接**：同一 Router 下的 MNet/SNet 到 Router 的附接链路自动生成（默认全连接语义），用户只需显式创建 Router→Router 链路；端点挂接与链路两端只能从已存在的对象中选择。端点面板跟随选中对象过滤，网络行可就地新增端点；分区、Router、网络、链路均支持就地删除（二次确认）。
- **路由模式**：Router 检查器可选 `routing: any | xy`；选 `xy` 时必须给出 `pos {x,y}`，链路只能连接曼哈顿邻居，G1 按 XY 转向模型裁剪 CDG（2×2 方形环即该特例）。闭合环在 `any` 模式下仍按 G1 拒绝。
- **地址映射**：slave 可声明 `base`/`size`（缺省时按 IR 顺序各占 4 KB，保持旧配置哈希不变）。C0 校验对齐、2 的幂、容量；C1 校验窗口不重叠。RTL 一律按 `base <= addr <= end` 译码，未命中窗口的地址落到 slave 0；端点数量不要求是 2 的幂（主/从数可为任意 ≥1）。面板可直接编辑 base/size，`auto 4K` 清除字段回到派生映射。
- **宽度**：设计级 `addrWidth ∈ {16,32,48,64}`、`dataWidth ∈ {32,64,128,512,1024}`，C0 使用同一集合校验；端点与链路宽度随设计宽度同步。
- **连接矩阵**：行是 initiator、列是 target，格子是该方向在 Router 图上的最小跳数，`—` 表示不可达；只读视图，不改变路由表。
- **拓扑预览**：MNet / Router / SNet 三层展开；全部 Router 带 `pos` 时按网格摆放到对应坐标（MNet 左列、SNet 右列），否则按最长路径分层自动布局；Router 高亮、CDG 是否无环直接标注；v1.1 起节点按时钟域着色、async 链路画虚线，并在图例中列出时钟域与分区（`name@clock`）。
- **工作目录**：`loom serve --work <dir>` 或首次打开页面时选择目录；每次结构编辑 600 ms 去抖后把 IR 写入 `<dir>/loom_draft.json`（临时文件 + 原子替换），刷新或重启后自动恢复草稿。最近使用的目录记在 `~/.loom/serve_workspace`，未设置目录时会弹出选择框。
- **估算面板**：带宽与延迟代理合计、crosspoint 数、每条链路容量，附假设与"估算，非签核"。
- **导入/导出**：示例列表、IR 双向、Canonicalize 与 Export；导出使用服务端 canonical JSON。
- **实现形态**：`src/loom/gui` 为 Space Grotesk / JetBrains Mono 装配的零构建静态资源（`index.html` + `style.css` + `app.js`），由 `loom serve` 托管；每次结构编辑先规整 IR 再调用 `/api/evaluate`（250 ms 去抖 + 请求序号防止旧响应覆盖），门禁与估算全部走 `LoomApi`，与 CLI 共用 `Checks`/`Gates`/`Reports`，浏览器不做 elaboration、不复制规则。

---

## 12. CLI、集成与产物

### 12.1 命令

```
loom check    --config <file> [--out <dir>]          # C0-C3，退出码 0/2/3
loom estimate --config <file> --out <dir>            # 估算报告
loom emit     --config <file> --out <dir> [--cache] [--formal]
loom formal   --config <file> --out <dir>            # P1-P3/P5-P8 有界证明 + G9
loom diff     --a <file|dir> --b <file|dir>          # 配置契约或报告基线对照（P9/CG5）
loom serve    --dir <dir> [--port <n>] [--examples <dir>] [--work <dir>]  # GUI + 静态预览 + 草稿工作目录
loom blocks   --out <dir>                            # 单独发射 CDC/宽度构件
```

`check / estimate / emit / formal / diff / serve / blocks` 均已实现（S12）。`emit` 支持 `--cache`（manifest 命中则跳过重发射）与 `--formal`（同一次调用完成有界证明）；`--partition` / `--top` 的子集发射随 S13 之后的 GUI 与多 top 工作补齐。

### 12.2 仓库集成

- 新增源码目录：`src/loom/scala/main`、`src/loom/scala/test`，在 `build.sbt` 增加 unmanagedSourceDirectories。
- 发射沿用 `ChiselStage.emitSystemVerilogFile` + 模块前缀 + zip 打包的既有套路，产物放 `generated/loom/`。
- 复用 `src/utils/Stream`、`src/utils/bus/amba4`、`src/utils/bus/Tilelink`；不在 loom 内复制一份总线基础库。
- 与现有模块解耦：loom 不 import 任何具体引擎（WorkerBee/ForagerBee/LLC/Queen），只依赖 utils。

### 12.3 产物

```
generated/loom/<name>/
├── checks.json / estimate.json / graph.json
├── LmTop.sv 或 LmNetworkTop.sv + 子模块 .sv + filelist.f
├── verification/    # firtool 发射的 SVA 层（assert/assume/cover）
├── partitions/<name>/
│   ├── LmPartitionTop.sv
│   ├── ports.json   # 端口契约 + partitionHash
│   └── stub/        # 同端口契约的分区 stub
├── tops/            # LmWrapperTop.sv（含分区时；多时钟配置带 domainClk/domainReset）
├── sram/            # LmSramSp_<D>x<W>.sv + filelist.f（≥1024 bit 的队列宏形态，规划用）
├── tb/              # <Top>_tb.sv 骨架 + assertions.f
├── formal/          # formal.json + token 模型日志（loom formal / emit --formal）
├── signoff.md
└── manifest.json
```

产物语义：

- `manifest.json` 记录相对路径与逐文件 SHA-256；`emit --cache` 在 configHash、工具版本与全部哈希一致时跳过发射。manifest 不包含 `formal/` 的运行日志（日志依赖求解耗时），`signoff.md` 的证明结论由 `formal.json` 给出。
- `signoff.md` 由 `emit` 生成，`formal` 只刷新其中的证明段与产物计数：门禁表、告警、RTL/断言/TB 计数、P1–P3/P5–P8 与 G9 结果及局限。
- `tb/<Top>_tb.sv` 从发射后的端口表反解方向与位宽：复位后所有输入置零、时钟 10 ns、`$finish` 收尾；同一端口声明的延续行继承位宽，跨方向不继承。骨架供填空，不自动生成激励。
- `perf` 端口除按端点的 returnSlots/respSlots 外，还给出 `requests`/`responses`/`errors` 计数与 `errSticky`；错误地址未记录（响应无地址字段）。
- `lint.sh` 用 Verilator 对代表元做 elaboration lint（含 wrapper、分区 top 与 CDC 构件），error 即失败。
- `loom serve` 只读发射目录，拒绝规范化后越出根目录的请求；不做 elaboration，避免前后端逻辑漂移。`/` 托管 GUI，`/api/evaluate` 接收 IR 文本并返回门禁、估算、拓扑与 canonical JSON；页面改动只落在 IR 上。

---

## 13. 估算模型

估算必须是**可解释的解析模型**，不是黑盒：

- **带宽**：按端点流量矩阵求每条链路负载；`ρ = offered / (width × freq)`；瓶颈取最大 ρ。
- **延迟**：`hops × (Router 固定延迟 2 拍 + banked 出口寄存 1 拍 + 串行化拍数) + Σ Pipe 级数 + 异步边界往返 + 排队延迟`；Pipe 与 async 级数由 IR 显式给出，模型不猜；排队用 M/D/1 近似并给出区间。
- **面积**：`areaProxy = 16,694.5 + 3,264.5 × crosspoints + 4,290 × bankedRouters`（µm²，nangate45/yosys 0.71 由 2×2、4×4 flat 与 4×4 banked 三点拟合）。系数把 VC 队列与 NI 一并折入 crosspoint 项；不含 SRAM、时钟树与布线。`estimate.json` 输出 `areaProxyUm2` 与按贡献排序的 `areaSensitivity`；系数随 RTL 版本重测并冻结。
- **SRAM 拆分（v1.1）**：深度×宽度 ≥ 1024 bit 的 VC 队列与 async FIFO 计入 `sramBits`，按 0.6 µm²/bit 占位系数给出 `sramAreaProxyUm2`；`totalAreaProxyUm2 = areaProxyUm2 + sramAreaProxyUm2`。该系数未经存储器编译器标定，只用于预算对比；`loom emit` 同时按所需 (depth, width) 生成 `sram/LmSramSp_<D>x<W>.sv` 行为模型，**不替换** RTL 中的推断存储器。
- **物理**：每条链路可声明 `distanceUm` 与物理分组；模型按"每 500 µm / 目标频率 1 级"给出**建议** Pipe 级数，但实际级数必须显式回写 IR，否则不发射。
- **能耗**：`bits × hops × pJ/bit/hop`，以公开的先进节点 NoC 数据作为量级锚点，明确标注为代理指标。

报告必须同时给出假设、输入、区间与"最敏感的三个参数"；只给单点数字的估算视为无效。

---

## 14. 分期与验收

详细任务分解、排期、人月与首个 sprint 清单见 [implementation_plan.md](implementation_plan.md)。

| 阶段 | 交付 | 出口条件 |
|---|---|---|
| M0 底座 | IR schema + 规范化 + C0/C1 + MNet/SNet + 单 Router（≤4×4）+ 1 级 Pipe + native 壳 + 冒烟 TB | 4×4 配置可仿真 |
| M1 MVP | VC/QoS、信用预留、CDC、多 Router + Pipe、CDG 检查、P1–P3/P5 有界证明 | G1–G3/G5–G8 可判；4×4/8×8 有界收敛；G4 随 M2 协议壳 |
| M2 可用 | 表单 + 拓扑预览 + 估算；CLI 全命令；CI 矩阵；分区/多 top；文档生成 | 新配置 10 分钟；同 IR 哈希一致；分区 P9 通过 |
| M3 扩展 | 树/维序 mesh、P6–P8、面积校准、加固与发布（P9 已在 M2 完成） | 已过门（2026-11-19）：树/网格通过，估算与面积代理冻结，v1.0 发布 |
| v1.1 | async 分区 + 多时钟 wrapper、AXI4 ID、SRAM block/面积拆分、G9 | 已过门（2026-11-20）：双时钟分区仿真、92 用例、5 个形式化模型、CG0–CG7 全绿 |

验收项：

- A1：从 YAML 到 RTL + TB + 报告 ≤ 10 分钟（单级 16 端口）。
- A2：同一 IR 两次发射，RTL 逐文件哈希一致。
- A3：Safe mode 配置 100% 通过 G1–G9；Unsafe 配置 100% 跑完 P1–P9 才可发射。
- A4：GUI 往返（IR → GUI → IR）后 canonical JSON 不变。
- A5：生成物零手工修改；任何手工补丁视为配置缺陷。
- A6：分区组合后 P9 通过，切分前后事务级等价。
- A7：任意 Router 图在 C2 中 100% 给出 CDG 判定（无环放行 / 有环并打印环链）。

---

## 15. v1.1 能力扩展

v1.1 在不改协议语义与 schema 的前提下补上四块工程能力。

### 15.1 async 分区与多时钟 wrapper

- **分区时钟**：每个分区一个时钟；S1 额外要求分区内每个成员（Router/MNet/SNet）的时钟等于分区时钟，且端点的 `clock` 与其 attachment 网络一致。
- **async 只在切分边上**：分区内部的 async 链路被拒绝（同一分区共用一个时钟，跨域无意义）；async 链路必须连接不同时钟域。
- **wrapper**：`LmWrapperTop` 在单域时保持原端口；多域时增加 `domainClk/domainReset`（按时钟 id 排序，每域一项），每个分区在 `withClockAndReset` 下例化。sync 切分边直连；async 切分边每个方向插入一个 `LmAsyncLink`，源侧用源域时钟，`dstClk` 用目的域时钟。各域复位独立，不做跨域复位握手。
- **顶层选择**：含 async 切分边的配置不再发射单时钟 `LmNetworkTop`，wrapper 成为主顶层；TB 骨架与 manifest 覆盖该变化。
- **验证**：`chain_split_async`（core 1 GHz / slow 600 MHz）在 ChiselSim 中按 2:1 时钟比完成读写往返；CI 增加该配置的确定性、静态门与 Verilator lint。

### 15.2 AXI4 事务 ID

- IR 顶层新增 `axiIdWidth`（默认 4，允许 1–8，C0 校验），由 `NetworkParams.tagWidth` 贯穿全部 bundle。
- `LmReq`/`LmRsp`/`LmNetReq` 携带 `id`；NI、Router、Pipe、分区边界与 stub 全链路透传，不参与路由。
- AXI4 壳在 AW/AR 接收时锁存 ID，B/R 回填；`LmShellCore` 在响应返回时断言 `rsp.id == burstId`，作为同 (src,id) 保序契约的运行时检查。native/AxiLite4/TL-UL 前端固定填 0。
- 本版不实现按 ID 重排；同 (src,id) 的响应顺序由"单路径、不重排"结构保证。

### 15.3 SRAM block 与面积拆分

- 深度×宽度 ≥ 1024 bit 的 VC 队列与 async FIFO 视为宏级存储，`estimate.json` 给出 `sramBits`、`sramBlocks`、`sramAreaProxyUm2` 与 `totalAreaProxyUm2`；较小队列仍按寄存器计入逻辑代理。
- `loom emit` 为每个所需 `(depth, width)` 生成 `sram/LmSramSp_<D>x<W>.sv` 行为模型与 `sram/filelist.f`，纳入 manifest 哈希；**不修改** RTL 中的推断存储器。
- 0.6 µm²/bit 为占位系数，未经存储器编译器标定，报告假设中显式标注。

### 15.4 G9 形式化

`token_cdc_buf.sv` 用单调 0–2 级指针同步器抽象对端视图，证明真实占用不超 depth 且弹出数据恰为最老序号；与 `token_cdc.sv`（信用面）共同覆盖 G9 的数据面与信用面。`loom formal` 一次跑五个模型（depth 12/20）。

---

## 16. 风险与开放问题

1. **构造性类过窄**：用户可能绕过 Gate 手改 RTL。缓解：Unsafe 模式必须形式化通过，且产物带显著标记；CI 扫描生成物哈希。
2. **形式化状态爆炸**：树/网格规模上升后平地证明必然失败。缓解：token 抽象 + assume-guarantee 分解；把"证明预算"写进配置。
3. **CDC 信用保守导致吞吐损失**：往返保守计数会降低跨域带宽。需在 M1 实测并给出可调参数，但不得以牺牲 P7 为代价。
4. **估算误导**：解析模型可能高估有效带宽。缓解：M3 用仿真标定，报告强制给区间与敏感参数。
5. **前端漂移**：GUI 自行生成 RTL 或缓存配置会产生双真源。架构上禁止，评审上拦截。
6. **协议语义蔓延**：为单个配置加特例是最大的长期风险。语义变更必须走独立版本与评审。
7. **Pipe 占用未计入信用**：会造成在途低估并直接死锁。缓解：G6/G9 + P3，且 Pipe 深度与信用同源计算。
8. **分区切错位置**：切在 VC 队列或信用池内部会使两侧各自认为在途为 0。缓解：S1–S4 + C3 端口契约检查 + P9。
9. **MNet/SNet 无谓跳数**：单级 crossbar 下若把两侧做成真实模块会白增延迟。缓解：单级时退化为零延迟端口分组，估算与 RTL 同时体现。
10. **未决**：多播的响应聚合语义（组内各回一次 vs 最慢者聚合）；近 home 原子在 Router 图中的 home 映射；Unsafe 模式的签核责任人；自动分区的默认策略是否应按综合规模而非时钟域。
11. **已决（2026-11-18）**：默认 `flat` 不分组；banked 与多 Router 拆分由用户显式选择，生成器只输出 `LM-W-TIMING` 告警，不自动改配置。
12. **已决（2026-11-20）**：MNet/SNet 采用**物理共享端口**语义。每个网络每拍每方向最多一个事务：单 Router 与多 Router 路径、分区 top 均插入请求/响应令牌仲裁；Router 的 `in = MNet 数`、`out = SNet 数`，估算 crosspoints 按网络数计。RTL 内部仍按端点实例化 NI 与端口，属于实现细节。

---

## 附录 A：IR 示例

```yaml
schema: loom/v1
meta: {name: edge_npu_fabric, protocolVersion: 1.0, seed: 0}
clockDomains:
  - {id: soc, freqMHz: 1200}
  - {id: npu, freqMHz: 1000}
endpoints:
  - {id: cpu,   role: master, protocol: axi4,   dataWidth: 512, addrWidth: 48, outstanding: 32, clock: soc, qos: ctl,  attach: mnet_sys}
  - {id: gemm0, role: master, protocol: native, dataWidth: 512, addrWidth: 48, outstanding: 16, clock: npu, qos: bulk, attach: mnet_cmp}
  - {id: dma0,  role: master, protocol: native, dataWidth: 512, addrWidth: 48, outstanding: 8,  clock: npu, qos: bulk, attach: mnet_cmp}
  - {id: llc,   role: slave,  protocol: axi4,   dataWidth: 512, addrWidth: 48, clock: soc, attach: snet_sys}
  - {id: sram,  role: slave,  protocol: native, dataWidth: 512, banks: 8, clock: npu, attach: snet_cmp}
fabric:
  mnet:
    - {name: mnet_cmp, clock: npu}
    - {name: mnet_sys, clock: soc}
  snet:
    - {name: snet_cmp, clock: npu}
    - {name: snet_sys, clock: soc}
  routers:
    - {name: r_cmp, in: 1, out: 2, clock: npu, switch: {kind: flat}}
    - {name: r_sys, in: 2, out: 1, clock: soc, switch: {kind: flat}}
  timing: {maxFanin: 8, maxCombDepth: 12}
  links:
    - {from: mnet_cmp, to: r_cmp,   width: 512, pipe: 1}
    - {from: r_cmp,   to: snet_cmp, width: 512, pipe: 1}
    - {from: mnet_sys, to: r_sys,   width: 512, pipe: 1}
    - {from: r_sys,   to: snet_sys, width: 512, pipe: 1}
    - {from: r_cmp,   to: r_sys,    width: 512, cdc: async, asyncFifoDepth: 8, pipe: 1}
  routing: {algorithm: static-table}
qos:
  classes:
    - {name: ctl,  vcs: 1, depth: 4,  arbitration: wrr, weight: 4}
    - {name: lat,  vcs: 1, depth: 8,  arbitration: wrr, weight: 2}
    - {name: bulk, vcs: 2, depth: 16, arbitration: wrr, weight: 1}
routing:  {algorithm: static-tree}
ordering: {model: address, orderedBit: true}
flow:     {scheme: credit, maxPacketBeats: 64}
deadlock:
  mode: constructive
  gates: [G1, G2, G3, G4, G5, G6, G7, G8, G9]
  formalFallback: true
partitions:
  mode: manual
  tops:
    - {name: compute, clock: npu, members: [mnet_cmp, r_cmp, snet_cmp], kind: partition}
    - {name: system,  clock: soc, members: [mnet_sys, r_sys, snet_sys], kind: partition}
    - {name: top,     kind: wrapper}
    - {name: verify,  kind: verify, stubs: []}
features:
  multicast: {enabled: false}
  atomics:   {nearHome: true, ops: [fadd, swap, cas]}
  poison: true
outputs: {rtlDir: generated/loom/edge_npu_fabric, testbench: true, formal: true, docs: true}
```

## 附录 B：Gate 检查表

| Gate | 静态判据 | 违反时的错误码 |
|---|---|---|
| G1 | 任意 Router 图的 CDG 无环（预置模板天然满足）；banked 纯流水级不计入，含独立仲裁的级按 Router 展开；路由确定性 | LM-G1 |
| G2 | 请求 VC 集 ∩ 响应 VC 集 = ∅ | LM-G2 |
| G3 | 每端点声明 returnSlots ≥ outstanding；目的端响应槽 ≥ Σ 源 outstanding | LM-G3 |
| G4 | 协议壳中响应分支不产生请求 | LM-G4 |
| G5 | ctl/lat/bulk 的 VC 集合两两不交 | LM-G5 |
| G6 | 信用深度 ≥ maxPacket；Pipe/异步 FIFO 占用计入在途；归还路径唯一 | LM-G6 |
| G7 | ctl 类 maxPacketBeats = 1 | LM-G7 |
| G8 | 仲裁 ∈ {wrr, rr} 或 (sp + aging) | LM-G8 |
| G9 | 每个 Pipe/异步边界 per-VC 保序，且边界两侧信用闭环 | LM-G9 |

## 附录 C：形式化属性与抽象

| 属性 | 抽象 | 预期收敛手段 |
|---|---|---|
| P1/P2 | 每事务一个 token | k-归纳 |
| P3 | VC 计数器 | 不变式归纳 |
| P4 | 地址比较折叠为等价类 | 归纳 + 案例拆分 |
| P5 | 公平性假设 GF(req) | progress checking |
| P6 | 拍计数 | 归纳 |
| P7 | 跨域信用往返计数器 | 保守上界 + 归纳 |
| P8 | 错误注入 token | 定向 + 归纳 |
| P9 | 分区前后事务 trace 等价 | 归纳 + 记录等价 |
