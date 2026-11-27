# ChiselLearn

以 Chisel 描述的 NPU 硬件学习仓：把一颗 NPU core 里的标量核、DSA 派发前端、脉动阵列、张量 DMA、
末级缓存与片上互连分别实现为独立模块，配套权威规格文档、行为测试与 CIRCT 发射的 SystemVerilog。
另含一个纯 Python 的性能建模框架，用于在 RTL 之前评估模型映射与 roofline 上界。

单根 `build.sbt` 聚合全部模块，共享 `src/utils` 公共库。

## 模块一览

| 模块 | 路径 | 内容 | 规格入口 |
|---|---|---|---|
| Queen | `src/queen` | DSA 命令派发前端 + Scheduler：RISC-V `custom-0` 扩展解码、GEMM/DMA/SCHED 三端点分流、同步资源（event/timeline）、fault、flush 恢复、QRD 异步读回 | `src/queen/doc/spec.md`、`queenISA.md`、`queenHarewareInterface.md` |
| WorkerBee | `src/workerbee` | 脉动阵列 GEMM 引擎（Queen 的 GEMM 设备） | `src/workerbee/doc/spec.md` |
| ForagerBee | `src/foragerbee` | 多引擎多维张量 DMA（Queen 的 DMA 设备）：COPY/TRANSPOSE/PERMUTE/IM2COL/SCATTER/GATHER/TILE2LINEAR 及浮点转换与 MX 量化 | `src/foragerbee/doc` |
| LLC | `src/llc` | 末级缓存：AXI4 CPU slave + AXI4 内存 master + AxiLite4 控制口，含流水线、MSHR、写回与写合并缓冲 | `src/llc/doc` |
| Loom | `src/loom` | CHI 拓扑编译器与验证套件：JSON IR → Chisel RTL，附 GUI 编辑器、SystemVerilog 形式化抽象与 CI 脚本 | `src/loom/doc` |
| GingerCat | `src/gingercat` | 由 NaxRiscv（SpinalHDL）移植的 RISC-V 核，**进行中**：`build.sbt` 当前只纳入 `riscv`/`compat`/`regfile`/`fetch` | — |
| Hive | `src/hive` | NPU 权威规格文档 + 纯 Python 性能建模框架 `model_eval`（frontend → L0 roofline → L1 映射排程） | `src/hive/doc` |
| utils | `src/utils` | 公共库：`Stream`（SpinalHDL 风格 Flow/Stream 原语）、`bus`（amba4 / Tilelink / regif / misc） | — |

Queen 是设备无关的派发前端：WorkerBee 与 ForagerBee 作为它的两个设备，须遵守
`queenHarewareInterface.md` 定义的统一端点协议，而不是各自的原生接口。

## 工具链

- Scala 2.13.18、Chisel 7.13.0（含 `chisel-plugin` 命名插件）
- CIRCT firtool 发射 SystemVerilog
- ChiselSim + Verilator 跑行为测试，ScalaTest 3.2.19
- 波形为 FST，产物落在 `sim/`

## 常用命令

```bash
sbt Test/compile                        # 全仓编译
sbt "testOnly Queen*"                   # 按模块跑测试
sbt "runMain QueenElaborate"            # 发射 RTL 到 src/queen/verilog
sbt "runMain WorkerBeeElaborate"
sbt "runMain ForagerBeeElaborate"
sbt "runMain LlcElaborate"
```

Loom 另有 CLI 与 GUI 入口，见 `src/loom/scala/main/loom/LoomMain.scala` 与 `src/loom/scripts`。
Hive 的建模框架是 Python 包，测试在 `src/hive/model_eval/tests`。

## 本机环境硬约束

这两条不是可选优化，缺了会以「环境噪声」的假象让整组仿真用例红：

1. **Verilator 的 FST writer 依赖 lz4 头文件**，而 ChiselSim 不暴露 `-CFLAGS` 注入口，
   须在运行 sbt 前设置 `CPATH`/`LIBRARY_PATH` 指向 homebrew 的 lz4。
2. **企业 EDR + macOS AMFI 会 SIGKILL 从系统临时目录执行的 ad-hoc 签名二进制**。
   ChiselSim 默认把 Verilator 工作区建在 OS 临时目录，导致仿真二进制被杀、
   `simulation-log.txt` 从不生成，用例报 `FileNotFoundException`。
   须用 `-J-Djava.io.tmpdir=<项目内目录>` 把工作区重定向到项目目录。

合起来即 `cmd.sh` 里的写法：

```bash
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib \
  sbt -J-Xmx6G -J-Djava.io.tmpdir=$PWD/build/tmp "testOnly Queen*"
```

生成 FST 波形加 `-- -DemitFst=1`，产物落在 `sim/`。更多示例见 `cmd.sh`。

## 目录约定

```
src/<module>/scala/{main,test,common}   Chisel 源码与 ScalaTest 用例
src/<module>/doc                        该模块的权威规格（spec.md 为契约入口）
src/<module>/verilog                    CIRCT 发射产物，含 filelist.f 与 verification/ 断言层
```

`src/doc/spec_template.md` 是新模块规格的模板。规格文档带版本号与修订记录，
功能契约变更须注明相关 Feature ID；RTL 与文档不一致时以文档为契约、两者都要修。