# Loom Link / Transaction Spec（草案 v0.1）

> 日期：2026-11-20 | 状态：评审中 | 适用范围：`src/loom` 的 Router 直连 link、MNet/SNet 端口与 shell 边界
>
> 本文与 `architecture.md` 配套：架构文档定义系统级约束，本文定义一条 link 上"传什么、怎么传、何时算完成"。

## 1. 术语

| 术语 | 含义 |
|---|---|
| fabric width | 全网统一的数据位宽（Router、MNet/SNet、Router 间 link 均使用） |
| endpoint width | 单个端点的数据位宽；缺省等于 fabric width，允许更窄（≤ fabric width） |
| burst | 一次读或写事务；由 `src` + `id` 标识 |
| beat | burst 内的一次数据传输；每拍独立路由 |
| VC | 虚拟通道：同一通道内按 QoS 类或 dateline 划分的队列；编号随拍携带 |

## 2. Link 方向

Router 间 link 可配置为：

| 模式 | 说明 | 通道承载 |
|---|---|---|
| bidirectional | 正向与反向独立线，全双工 | 正向：`rreq` + `wdata`；反向：`rdata` + `wresp` |
| unidirectional | 只有一条发送方向 | 四个通道都走该方向，各自仍是独立物理通道 |

四个通道各自拥有独立线束、独立 `valid/ready`、独立信用与独立仲裁，任何两个通道之间不共用队列或仲裁器，因此不存在跨通道阻塞。方向模式只决定各通道占用哪一组线。

MNet/SNet 与 Router 之间的附接链路沿用同一模型；MNet/SNet 是端点的共享端口（每拍每方向一个事务）。

## 3. 通道定义

每条 link 有四个通道，且每个通道都是独立物理通道（独立线束、独立握手、独立信用），不是共享介质上的时分复用；方向模式只决定各通道占用哪一组线：

| 通道 | 载荷 | 粒度 | bidirectional | unidirectional |
|---|---|---|---|---|
| `rreq` | 读地址、`len`、`src`、`id`、`vc` | 每 burst 一个 | 正向 | 正向 |
| `wdata` | 地址、数据、`strb`、`src`、`id`、`last`、`vc` | 每拍一个 | 正向 | 正向 |
| `rdata` | 数据、`src`、`id`、`last`、`err`、`vc` | 每拍一个 | 反向 | 正向 |
| `wresp` | `src`、`id`、`err`、`vc` | 每 burst 一个 | 反向 | 正向 |

`rdata` 与 `wresp` 是两条互不相干的物理通道：读数据连拍不会占住 `wresp` 的线，写确认也不与读返回共用仲裁或缓冲；单向 link 上它们同样各占一组线。

字段说明：

- **wdata 每拍都携带地址**，不设头拍/尾拍之外的包结构；`last=1` 表示该 burst 的最后一个写拍。
- **写 burst 没有独立的写地址通道**；下游从第一拍 `wdata` 开始接收，以 `last` 结束，并在收到 `last` 后回一个 `wresp`。
- **rreq 只携带地址与 burst 长度**，不携带数据；窄通道独立于 `wdata`，不占宽数据带宽。
- **rdata 每拍携带 `last`**，最后一个读拍置 1；`rreq.len + 1` 必须等于返回拍数。
- `strb` 位宽 = fabric width / 8；读数据不使用 `strb`。
- `vc` 为虚拟通道编号，是拍上唯一随行的调度字段；类（ctl/lat/bulk）不上路，由 NI 按配置映射成 `vc`（见 §7）。

## 4. 事务流程

### 4.1 写 burst

1. master 发出 N 个 `wdata` 拍，每拍带自己的字节地址、`strb`、`src`、`id`；最后一拍 `last=1`。
2. 每拍在 Router 上独立按地址窗口译码选路；同一 burst 的拍可以经过不同路径，但必须保持 `(src,id)` 顺序。
3. 从端在 `last` 拍之后回一个 `wresp`（`id` 回显、`err` 为该 burst 的错误汇总）。
4. 写 burst 不产生逐拍响应；`wresp` 一个 burst 一个。

### 4.2 读 burst

1. master 发出一个 `rreq`（`addr`、`len`、`src`、`id`）。
2. 从端返回 `len+1` 个 `rdata` 拍，逐拍携带 `err`，最后一拍 `last=1`。
3. 读 burst 只有 `rreq` 一个请求拍；地址递增由从端按 beat 计算。

### 4.3 突发与交织

- 所有通道一律**burst 原子**：同一 VC 上一个 burst 从首拍占到 `last`，其他 burst 的拍不得插入。
- 不同 VC、不同通道可并行；同一 `(src,id)` 的拍顺序不得改变。
- `wresp` 只能在对应 burst 的 `last` 被下游接收后返回。

## 5. 顺序、ID 与错误

- `(src,id)` 标识一个 burst；同一 burst 的 `rdata`/`wdata` 顺序不得改变。
- 不同 burst 之间可以乱序完成；不提供全局顺序。
- 写：`wresp` 必须在该 burst 的 `last` 拍被下游接收后产生；`err` 为 burst 内错误的按位 OR。
- 读：`rdata` 逐拍携带 `err`；出错只上报、不打断数据流，从端仍返回完整 `len+1` 拍。
- `rdata` 与 `wresp` 是两条独立物理通道，B 不会被 R 数据阻塞；二者之间不需要 VC 隔离，也不做联合仲裁。

## 6. 信用与流控

- 每个通道、每个 VC 维护独立信用；**不允许跨通道共享队列或信用**。
- 信用单位一律按 burst：`rreq`、`wdata`、`rdata`、`wresp` 各 1 个 burst 信用（不按拍计）。
- burst 不可打断：授予信用时接收端必须能容纳整个 burst，即 VC 深度 ≥ 该类 `maxPacketBeats`（与 G6 一致）。
- master 返回槽按 burst 计；slave 响应槽按 burst 计，`rdata` 与 `wresp` 各占自己通道的槽，互不复用。
- 空链路（pipe）只延迟，不改变信用语义；async link 的 FIFO 深度即链路级容量。

## 7. VC 与 QoS

- `qos` 保持三类：ctl/lat/bulk；每类的 VC 数量来自 IR 的 `qos.classes[].vcs`，类到 VC 编号的映射由 NI 完成，拍上只带 `vc`。
- VC 只用于同一通道内部：按 QoS 类划分，并在单向环上用 dateline 打断本通道的环形依赖。
- dateline 的机械做法：在环上任选一根 link 作切点；消息过切点前用半区 0，过切点后由该路由器把 `vc` 的半区改写为 1，此后不得回到 0。VC 编号 = 类 × 2 + 半区，因此每通道 VC 数 = 类数 × 2。
- 切点默认取每个有向环上 link id 最小者；IR 可在某根 link 上标 `dateline: true` 覆盖默认。同一根 link 若落在多个环上，VC 需求按实际切点组合计算，由 C1 给出该 link 的 VC 数上限。
- dateline **逐通道独立**：`rreq`、`wdata`、`rdata`、`wresp` 各自成环，各用两个半区（VC 数 = 类数 × 2）；不存在一条依赖跨越两个通道。
- 不同路由转向（turn）的隔离同样靠 VC：后续在 VC 编号空间里为转向分配独立 VC，本版先只做类与 dateline。
- G1 的 CDG 顶点从"link"扩展为 `(link, 通道, 方向, vc)`；只允许 dateline 的单调 VC 切换（0→1）作为跨 VC 依赖。

### 7.1 环上的前进保证（已裁定：A）

包不会无限绕环：路由在到达目标 slave 的出口即离开，单向环最长 N−1 跳；只有引入偏转路由时才需要 TTL。

每跳有缓冲（现有 VC 队列）只能做到"接受与否只取决于本跳剩余空间"，从而断开组合 ready 链；它不保证前进。单向环上若各跳队列同时被"要去下一跳"的包填满，队头互相等待，形成的是**缓冲占用的环**，不是组合环。破解只有三条路：

| 模型 | 语义 | 实现 | 代价 |
|---|---|---|---|
| A dateline（采用） | 不死锁；允许中间跳有界等待 | 环上逐通道切一刀，VC0/VC1 各占半圈 | 环上 VC 存储翻倍 |
| B 缓冲总量约束 | 不死锁；允许等待 | 环上总信用 > 在途包数，环上恒有一个空位 | 缓冲面积随环长线性增长 |
| C 整路径预留 | 注入后不停顿（wait-free） | NI 先算路径并逐跳预留 landing slot，到达即可落下 | 每 burst 信用/槽位 O(路径长)；端口争用仍需调度 |

A 满足"不因下游未腾空而死锁"；字面意义的"任何情况都不等待"在全速率下不可达，因为同一输出端口的并发请求仍需仲裁。

## 8. 位宽与窄端点

- fabric width 全局统一；link/router/net 不单独定义位宽。
- endpoint width ≤ fabric width；缺省等于 fabric width。
- NI 边界做 **lane 适配**：窄端点的 `data`/`strb` 放在低位字节，高位补零；读响应取低位字节返回。
- 不做 AXI 式窄传输打包/拆拍（beat 数不变）；因此窄端点的一拍仍占 fabric 一拍带宽，只是数据有效字节少。后续如需节省带宽，再单独立项打包式适配。
- 地址对齐与窄访问合法性由 master/shell 负责；fabric 不推断字节使能（一律看 `strb`）。

## 9. 路由与地址

- 每拍按地址窗口译码选目标 slave（`base <= addr <= end`），窗口可编辑、互不重叠。
- 响应按 `src` 路由回发起 master（物理方向由 link 模式决定）；`wresp` 与 `rdata` 同样按 `src`。
- 未命中任何窗口的地址译码到 slave 0（当前兼容行为，后续可改为错误响应）。

## 10. 兼容与迁移

- 现有 `LmReq/LmNetReq` 等价于本 spec 的 `wdata`（写）与 `rreq`（读）合并形态；RTL 迁移分步进行。
- shell 对外协议不变（native 四通道、AXI4）；合并/拆分发生在 shell 与 fabric 之间。
- 旧配置在未启用新通道时保持现有每拍行为与门禁通过。

## 11. 待确认

1. 物理端口形态：四通道各自独立 `valid/ready`（当前定义），还是在 link 端聚合为一个宽 bundle、共用握手以减少端口数。
2. 同一根 link 落在多个环上、VC 数超过"类数 × 2"时，是接受更高 VC 数，还是先只允许简单单环。
3. dateline 切点默认"每个环取 link id 最小者"是否认可，还是要求 IR 必须显式标注。

## 12. 实现状态（2026-11-20）

已完成（103 用例全绿）：

- **IR/C0**：link 增加 `direction`（bidirectional/forward）与 `dateline`；`Reports.estimate` 输出二者。
- **G1**：CDG 改为 SCC 分析——每个顶点入/出度均为 1 且全为 router-router 的简单环，由切点放行（显式优先，否则自动取 link id 最小）；复杂 SCC 仍报 `LM-G1: CDG cycle ... (not a ring)`；自环仍拒绝；显式切点不在环上报错。
- **请求侧 dateline**：`LmReq/LmNetReq/LmRsp` 携带 `vc`（`vc = 类 × 2 + 半区`）；NI 赋半区 0；路由器每输入 6 个 VC 队列，三级仲裁；切点出端口把半区改写为 1，且不回切。
- **前向响应**：路由器新增 `outRspFwd`/`inRspFwd`；响应路由表按方向图 BFS（双向 link 走反向、单向 link 走正向），由 `routeMaster`/`routeMasterFwd` 分别给出入端口/出端口；响应开关统一按 src 选路。分区边界新增前向响应端口，async 切点加 CDC；`ring_4` 用例验证请求 2 跳、响应沿环前向返回。
- **时序安全**：路由器出端口固定一级寄存器、每跳 response 通道至少一级 pipe，打断环上组合环。

未实现：

- 响应通道的 dateline 半区翻转尚未实现（请求通道已有）；响应在环上目前依赖每跳寄存器与最短路径，安全性论证不完整。
- 四通道物理拆分（`rreq/wdata/rdata/wresp`、`strb` 端到端、burst 级 `wresp`）未做，现有 fabric 仍是每拍请求、写数据随请求。
- 全局位宽与窄端点 adapter 未接入 NI（`LmWidthAdapter` 已有 2:1）。
