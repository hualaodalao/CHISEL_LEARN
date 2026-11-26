# Loom legacy（旧四通道运输层，先隔离后删除）

> 状态：S6（2026-11-25）按 `doc/ir_gen_spec.md` MIG-11 隔离。**默认不编译、不进 CI 矩阵**，
> 保留一个版本供比对，随后整体删除。用户口径见 `doc/ir_gen_spec.md`（loom/v2）与
> `doc/protocol_spec.md`（CHI 硬件）；本目录内容不是用户契约。

## 这是什么

`loom/v1` 时代的四通道（rreq/wdata/rdata/wresp）运输层 RTL、其参数装配与配套仿真测试：

- `main/loom/rtl/`：`LmTop`（flat fabric）、`LmNetworkTop`（多 router 网络）、
  `LmPartitionTop/LmWrapperTop/LmPartitionStub`（分区三件套）、`LmShell*`（native/AXI4/
  AxiLite4/TL-UL 协议壳）、`LmWidthAdapter`、`LmAddrDecode`、`FabricParams/NetworkParams/
  PartitionParams`。`LmRtl.scala` 与 `LmNetwork.scala` 为整文件原件（含少量仍被 CHI 使用的原语，
  见下）。
- `main/loom/report/ReportsV1.scala`：v1 分区 `ports.json` 渲染（`Reports.portsJson` 原身）。
- `test/loom/`：17 个 `Lm*Spec` 仿真套件 + `EmitSpec`（LmTop 发射确定性）+ `GraphV1Spec`
  （v1 XY 路由表断言）。v1 示例输入保留在 `src/loom/testdata/v1/`（升级器等价性用例专用，
  MIG-01/MIG-12），本目录测试也读该路径。
- `scripts/area_calibration.sh`：nangate45 面积校准，拟合对象是旧 `LmTop`（含已退役的 banked
  档）。CHI 路径的 PPA 校准另立任务（ir_gen_spec D10），故随 legacy 退役。

## 仍在主源码路径上的共享原语（不属于 legacy）

`loom.chi` 硬件复用以下积木，它们留在 `src/loom/scala/main/loom/rtl/`：

- `LmPrimitives.scala`：`LmWire`、`LmPipe`、`LmRrArbiterN`、`LmWrrArbiter`
  （逐字拷贝自本目录 `LmRtl.scala`/`LmNetwork.scala` 的原定义）；
- `LmCdc.scala`：`LoomCdc/LmAsyncFifo/LmCdcCredit/LmAsyncLink`（`ChiAsyncLink` 包装）。

`blocks` CLI 命令（旧 CDC/width 积木发射）已随 MIG-11/GEN-12 退役。

## 如何临时重新启用（比对用）

1. 把共享原语文件挪开，避免与本目录原件重复定义：
   `mv src/loom/scala/main/loom/rtl/LmPrimitives.scala /tmp/`
2. 在根 `build.sbt` 的 root settings 里临时追加（勿动其他模块配置）：
   ```scala
   Compile / unmanagedSourceDirectories += baseDirectory.value / "src" / "loom" / "legacy" / "main",
   Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "loom" / "legacy" / "test",
   ```
3. `sbt "testOnly loom.Lm* loom.EmitSpec loom.GraphV1Spec"` 运行旧套件。
4. 比对完成后还原 build.sbt 并把 `LmPrimitives.scala` 挪回；**不要**把本目录长期接回 CI。

## 删除计划

下一个主版本（ToolVersion 3.0.0 / MIG-06 删除项登记时）整体删除本目录，并同步删除
`testdata/v1/` 中不再被升级器用例引用的副本与 `Upgrade.v1ToV2` 的 v1 入口。