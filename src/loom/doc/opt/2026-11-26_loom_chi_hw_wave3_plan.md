# Loom CHI 硬件 Wave3 实施计划（2026-11-26）

> 依据：用户裁定「只管硬件，A1～A4 一起做；GEN/IR 侧其他 agent 进行」。
> 范围：**仅 loom/chi 硬件 + 测试 + 规格**；IR schema/装配分派/estimate/per-source 过滤的 IR 表达归 GEN。
> 纪律：每故事仿真绿再进下一个；sbt 单进程后台等通知；禁手改发射物；git 写需授权。
> 前置：wave2（HW-08/13/09）已落地，23 suite/253 test 绿、formal 10/10。

## 故事

### W4 = A1 SN 写数据投递（ChiSnPort RX DAT）
**目标**：补全 SN 接收写数据的通路（当前 SN router 对写数据 ready=1 吸收）。
**任务**：
1. `ChiFlit.scala` ChiSnPort 增 `rxDat = Flipped(Decoupled(ChiDatFlit))`（SN RX 写数据；dat 仍为 TX 读数据）。
2. ChiNetworkTop / ChiPartitionTop SN 分支：`port.txDat` 由 ready=1 改 eject→io.sn.rxDat。
3. ChiPartitionStub：io.sn.rxDat 驱 valid=0/bits=0；ChiTop（退役）补 rxDat.ready=1 tie-off。
4. ChiWrapperTop / ChiAxiNetworkTop：rxDat 透传接线。
5. 测试 tie-off：ChiNetworkSpec/ChiPartitionSpec/ChiDvmNetworkSpec init 增 sn.rxDat.ready；ChiAxiNetworkSpec tieSn 增 rxDat；写用例改用 ChiWriteSink 真正消费写数据并校验重组（替换 WriteSnResp）。
**门禁**：AXI 写端到端含写数据重组绿；全量无回归。**体量**：中（波及面广但机械）。

### W5 = A2 SN CHI→AXI 反向桥（ChiAxiSnBridge）
**目标**：SN 边界 AXI（CHI req→AXI ar/aw，AXI r/b→CHI rsp/dat），对标 RN 侧桥。
**任务**：
1. 新 `ChiAxiSnBridge`：RX CHI req（ReadNoSnp/WriteNoSnp）→AXI 主 ar/aw+w；AXI r→CHI CompData dat；AXI b→CHI DBIDResp/Comp rsp；写数据 CHI rxDat→AXI w。
2. ChiAxiNetworkTop SN 边界按 portMode 例化反向桥（axi4/both），chi 模式原生 flit。
3. 测试：ChiAxiNetworkSpec 增 SN-AXI 端到端读/写（AXI slave 模型）。
**门禁**：SN-AXI 读/写绿。**依赖**：W4。**体量**：中大。

### W6 = A3 per-node 混合协议（both）
**目标**：ChiAxiNetworkTop 按 node.protocol 逐 RN/SN 分派 AXI 或原生 flit 边界。
**任务**：
1. ChiNetNode 增 protocol 字段（fromIr 从 endpoint.protocol 派生；默认随 cfg.portMode）。
2. ChiAxiNetworkTop io 按 per-node mode 生成混合端口（AXI 或 flit），逐节点例化桥/直通。
3. 测试：both 下部分 RN chi 部分 axi4 混合端到端。
**门禁**：混合协议绿；chi/axi4 统一模式无回归。**依赖**：W4/W5。**体量**：中。**协作**：GEN 装配传 per-node protocol。

### W7 = A4 per-source SAM 过滤
**目标**：硬件支持 per-RN SAM 表 / 禁止对（UP-01 forbid 语义的硬件消费）。
**任务**：
1. ChiNetworkParams.sam 扩为 per-RN（`samByRn: Map[Int,Seq[ChiSamEntry]]` 或 Seq[Seq]），空回退全网共享表。
2. ChiNetworkTop RN 入口按源取表译码；禁止对→未命中 NDERR。
3. 测试：不同 RN 同 addr 译码到不同 SN / 禁止对 NDERR。
**门禁**：per-source 译码绿；共享表无回归。**依赖**：W2（已落地）。**体量**：中。**协作**：GEN 提供 per-source IR routes/forbid。

## 依赖图
```text
W4 ─┬─ W5 ─┬─ W6
    └──────┘
W7（独立，依赖 W2）
```

## 验收
- A1～A4 硬件落地，对应仿真绿；全量 loom.* 回归绿 + formal 全 PASS。
- protocol_spec 更新（SN RX DAT、SN AXI 桥、per-node 混合、per-source SAM）；依赖清单 §6 回写。
- GEN/IR 侧（装配分派、IR routes/qosGen/subsystems、estimate）不在本 wave，由其他 agent 实施。