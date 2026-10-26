以端侧（LLM decode / 小 batch / CNN 量化推理）视角过了一遍 WorkerBee 现状（16×16 阵列、K/N 空间轴、loadW 权重驻留、FIFO 预取、四 DMA 通道、8 槽寄存器 + execute/irq、errCode 体系）。按性价比排序：

## 一、Epilogue 是最大空白（寄存器和写回路径已核实无 bias/requant/激活，WbInterface.scala:122-180）

| 项 | 现状 | 建议 |
|---|---|---|
| **Fused bias** | GEMM 完后 bias 要回 memory 走一遍 | 复用 scale 的 32-bit 读通道搬 bias，C 写回前加一级加法器。面积近零，每层省一次 M×N 往返 |
| **Fused requant** | INT 系输出是裸累加值截断 | `out=sat(round(acc×scale+zp))`，per-tensor/per-channel；写回带宽有余量，一个共享 mul+round 单元即可。reg 槽位还有空（is(8.U)）。S8 全整数量化 pipeline 的刚需 |
| **ReLU** | 无 | 写回路径一位符号门控，几乎免费 |

这三项是从「GEMM core」到「layer engine」的分界线，端侧每层都吃收益。

## 二、格式补齐（中等成本）
- **INT 系 W4A8（INT8×INT4）**：P1-2 的 fp4→fp8 展开路天然可推广——INT4→INT8 只是符号扩展，连 LUT 都不用；CNN 端 S4A8 很常用。
- **非 MX 纯 fp8 档**（per-tensor scale，scale 流量归零）：decode 常用；改动在 Fmt 枚举 + scale tie 0 + errCode。
- **2:4 结构化稀疏**：行业标配，GEMV 下直接省一半权重带宽。但需 metadata 通道 + 变长窗口装载，工程量大，建议单独立项评估。

## 三、控制流（省 CPU、层间零气泡）
- 现在每层 GEMM 都要 Gingercat 写 8 个寄存器再 execute；LLM decode 每 token 7+ 层。建议把寄存器组做成 **2~4 深描述符队列**（或让 ForagerBee 命令流携带 GEMM 描述符），done 后自动装载下一条。阵列零改动，改动集中在 CalcConfig/寄存器侧。
- **perf counters**：status 只有 progress/occupancy（WbInterface.scala:107-116）。加 cycle 总数 + 窗口等待/背压 2~3 个计数器，近零成本，后续所有优化都靠它量化。

## 四、算力利用率（可接受，暂不动）
- loadW 窗口与计算串行：pass 开销 = 16/(16+m) 拍。m=1..4 decode 时空闲 50–90%，**但** bDma FIFO 预取使 B 端口持续满流，memory-bound 场景总吞吐不受损；batch≥8 时损失 ≤11%。修复 = PE 权重双缓冲（~8Kbit regs + mux），性价比一般，列观察项。
- M 上限 = cBufferDepth(64) 需软件分 M 块——每块 K 全长驻留无 partial 写回，无性能损失，只是文档化约束。

## 五、SoC 集成面（已有开放项，补两条）
- 四通道峰值 ~100B/cycle@500MHz≈50GB/s，各通道峰错开（loadW 期 A 口闲），建议 perf counters 落地后做 workload 级 profile。
- 功耗：无操作数隔离/时钟门控架构机制；低成本可做 aPop/bPop 零拍 PE clock-enable（纯功耗收益）。

**优先级建议**：P0 = bias+requant epilogue、perf counters；P1 = 描述符队列、INT 系 W4A8（借 P1-2 东风）；P2 = 纯 fp8 档、ReLU；P3 = 权重双缓冲、2:4 稀疏。

另：任务 #5（P1-2 W4A8）的**根因与修正方案仍在等你拍板**（上一轮消息：展开粒度从「1 读 2 推」改为「16B 逻辑拍」的阵列拍口径方案）——如果你先选这里的方向，我可以把两者排进同一份路线图。