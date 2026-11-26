# Loom CHI 硬件 Wave4 实施计划（2026-11-26）

> 依据：依赖清单 §8（GEN S7/S8 落地，ir_gen_spec v1.3.0，commit 80f1e46，24 suite/279 test 绿）解除 HW-10/12/14 阻塞。
> 范围：**仅 loom/chi 硬件 + 测试 + 规格**；IR/校验/estimate/API 已 GEN 冻结，GEN 按硬件定好的参数接口装配接线。
> 纪律：硬件模块参数化可复用、GEN 只例化接线；每故事仿真绿再进下一个；sbt 单进程后台等通知；禁手改发射物；git 写需授权。
> 前置：wave1+2+3 闭合（24 suite/258 test 绿，formal 10/10，protocol_spec v1.4.0）。

## 0. 硬件参数接口裁定（定好后通知 GEN 装配）

硬件不 import loom.gen；在 loom.chi 定义镜像参数 case class，GEN 把 ChiAssemble.QosGenParams/AdaptParams 映射传入：
- `chi.ChiQosGen(nodeId: Int, kind: String, bytesPerCycle: Double, saturationBytes: Int)`（镜像 GEN-23/SEM-18）。
- `chi.ChiAdapt(nodeId: Int, dataWidth: Int, datWidth: Int, fifoDepth: Int, mismatch: Boolean)`（镜像 GEN-24/SEM-19；时钟域由 HW-14 async 承载，宽度适配用 dataWidth/datWidth）。
- 挂载点：`ChiNetworkParams` 增 `qosGen: Seq[ChiQosGen] = Seq.empty`、`adapt: Seq[ChiAdapt] = Seq.empty`（按 nodeId 索引，空=无调节/无适配，行为不变）。

## 故事

### W8 = HW-14 flat 配置 async link 钩子（中优先，先做）
**裁定：选 ①**（ChiNetLink 增 asyncFifoDepth，ChiNetworkTop 内部 link 级例化 ChiAsyncLink）。理由：GEN flat 装配只需填 link 字段，无需 chunk API；复用 token_chi_async_credit 证明。
**任务**：
1. `ChiNetLink` 增 `asyncFifoDepth: Int = 0`（0=sync 直连，≥2=异步 FIFO 深度）。
2. ChiNetworkTop link 循环：asyncFifoDepth≥2 时每通道类（REQ/RSP/SNP/DAT）插 ChiAsyncLink（dstClk/dstReset 由参数给目的域时钟），否则直连。
3. ChiNetworkParams 增 link 目的时钟映射（或 ChiNetLink 增 dstClock 字段）；fromIr 透传 IR cdc/asyncFifoDepth（GEN 装配）。
4. 测试：ChiLinkSpec/ChiNetworkSpec 增 flat 双时钟 chain/ring 跨域 REQ/DAT 端到端（复位期 toggle dstClk、dst 上升沿采样）。
**门禁**：flat 双时钟发射+仿真绿；formal 跨域信用 PASS；sync 链路无回归。**体量**：中。

### W9 = HW-10 NIU QoS 带宽调节（中优先）
**目标**：RN 注入侧 per-node 令牌桶速率调节（fixed/limiter/regulator），不丢 flit、速率收敛声明值。
**任务**：
1. 新 `chi/ChiQosRegulator.scala`：令牌桶（rate=bytesPerCycle 累加，burst=saturationBytes 上限），flit 消耗 datWidth/8 字节令牌；令牌不足反压 req.valid。fixed=burst 0 恒速率；limiter/regulator=峰值+突发。
2. ChiNetworkTop RN 入口（SAM/NI 之后、inject 之前）按 p.qosGen 匹配 nodeId 串入调节器；未声明节点直通。
3. 参数 bytesPerCycle 为 Double→硬件用定点累加（每拍加 rate×scale，阈值比较），scale 取 2^k 保精度。
4. 测试：ChiQosRegulatorSpec 对拍声明速率（±burst）；不丢 flit；未声明节点行为不变。
**门禁**：速率收敛仿真绿；279 test 不回退；protocol_spec 增 block。**体量**：中。

### W10 = HW-12 ChiNi 速率适配（低优先）
**目标**：宽度/频率失配 store&fwd 适配（深度=fifoDepth），匹配节点零开销直通。
**任务**：
1. 新 `chi/ChiNiAdapt.scala`：宽度失配（dataWidth≠datWidth）store&fwd 重组（窄→宽拼 flit、宽→窄拆 flit，DataID 对齐）；频率失配经异步 FIFO（复用 ChiAsyncLink，深度 fifoDepth）。
2. ChiNetworkTop RN/SN 入口按 p.adapt 匹配 nodeId 串入；mismatch=false 直通。
3. AXI 边界宽度适配仍归桥接层（不重复）。
4. 测试：宽度/频率失配数据逐位一致、无丢 flit；匹配节点零开销。
**门禁**：失配用例绿；匹配无回归；protocol_spec 增规格。**体量**：中大。**依赖**：W8（异步 FIFO 复用）。

## 依赖图
```text
W8 ─┬─ W9（独立可并行）
    └─ W10
```

## 验收
- HW-14/10/12 硬件落地，对应仿真绿；全量 loom.* 回归绿（含 GEN 279 test 不回退）+ formal 全 PASS。
- protocol_spec 增 ChiQosRegulator/ChiNiAdapt/link async 规格；依赖清单 §6 回写 + §8 通知 GEN 接消费（参数接口 §0）。
- HW-11（UP-07）仍待用户立项，不在本 wave。