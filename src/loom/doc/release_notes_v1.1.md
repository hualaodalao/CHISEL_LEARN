# Loom v1.1 发布说明

> 日期：2026-11-20 | 工具版本：`loom 1.1.0` | IR schema：`loom/v1`（字段增量，向后兼容） | 状态：发布

## 1. 范围

v1.1 在 v1.0 的协议语义与 schema 上补工程能力，不改变既有配置的 RTL 行为。四项交付：async 分区与多时钟 wrapper、AXI4 事务 ID 透传、SRAM block 与面积拆分、G9 形式化模型。

## 2. v1.1 能力

- **async 分区**：分区内所有成员必须同域；async 链路只允许连接不同时钟域，且只能出现在切分边上。`LmPartitionTop` 只拒绝分区内部 async。
- **多时钟 wrapper**：多域配置的 `LmWrapperTop` 暴露 `domainClk/domainReset`（按时钟 id 排序，每域一项），每域独立 `withClockAndReset`；async 切分边每个方向插入一个 `LmAsyncLink`，源侧用源域时钟、`dstClk` 用目的域时钟。单域配置端口与行为不变。
- **主顶层选择**：含 async 切分边的配置不再发射单时钟 `LmNetworkTop`，wrapper 成为主顶层；TB 骨架与 manifest 同步调整。
- **AXI4 ID**：IR 新增可选 `axiIdWidth`（默认 4，C0 限 1–8）。`LmReq/LmRsp/LmNetReq` 及 NI/Router/Pipe/分区/stub 全链路携带 `id`；AXI4 壳锁存 AW/AR ID、在 B/R 回显，`LmShellCore` 断言返回 ID 等于 burst ID。native/AxiLite4/TL-UL 前端固定 0。不实现按 ID 重排。
- **SRAM block 与面积拆分**：`estimate.json` 新增 `sramBits`、`sramBlocks`、`sramAreaProxyUm2`、`totalAreaProxyUm2`；≥1024 bit 的 VC 队列与 async FIFO 计入 SRAM，小队列仍计入逻辑代理。`emit` 生成 `sram/LmSramSp_<D>x<W>.sv` 与 `sram/filelist.f`，不替换 RTL 中的推断存储器。
- **G9 形式化**：新增 `token_cdc_buf.sv`，以单调 0–2 级指针同步器抽象跨域视图，证明占用不超 depth、弹出即最老序号；与 `token_cdc.sv` 共同覆盖 G9 数据面与信用面。`loom formal` 与 `p6_p8.sh` 均包含该模型。
- **GUI**：拓扑节点按时钟域着色，async 链路画虚线，图例列出时钟域与分区（`name@clock`）。图形化配置改为结构编辑器：`partition → router → mnet/snet` 层次建对象、附接链路自动生成、Router→Router 链路显式创建；时钟先行（节点时钟只从已建时钟选择，分区时钟级联成员）；设计级宽度收为 `addrWidth ∈ {16,32,48,64}`、`dataWidth ∈ {32,64,128,512,1024}`（C0 同集合校验）。
- **CI**：`chain_split_async` 纳入 CG0 与 CG2；lint 覆盖 async wrapper 与 SRAM blocks。

## 3. 版本冻结

| 项 | 冻结值 |
|---|---|
| IR schema | `loom/v1`（新增可选字段：`axiIdWidth`；其余同 v1.0） |
| 检查码 | 新增 `LM-S1` 成员时钟/端点时钟与 async 域判据；其余同 v1.0 |
| 生成物 schema | `loom/estimate/v1` 新增字段；其余 schema 不变 |
| 形式化门禁 | yosys-sat 有界：8×8 安全 depth 12、RR depth 20、response/CDC/G9 depth 12 |
| 计数宽度 | `tagWidth = axiIdWidth`，默认 4；`src` 仍为 master 序号 |

同一 IR 两次发射逐文件一致（CG2）；async 配置同样满足。

## 4. 发布验证记录

- 回归：`sbt -J-Xmx10G -batch "testOnly loom.*"` → 22 suite / 92 tests 全绿。
- 新增用例：`LmWrapperAsyncSpec`（2:1 双时钟跨域往返）、`LmNetworkSpec`（ID 端到端）、`LmShellSpec`（AXI4 B/R 回显）、`IrSpec`（S1 时钟判据 + `axiIdWidth`）、`ReportSpec`（SRAM 拆分）。
- CG2：minimal_4x4、tree_3r、chain_split、chain_split_async 整目录一致。
- CG5/CG6：同配置双发报告 diff 一致；222 个产物哈希校验通过。
- CG7：Verilator lint 覆盖 4×4（含 sram/）、8×8、mesh、wrapper、两个分区 top、async wrapper、`LmAsyncLink`。
- CG4：五个 token 模型全部 PASS（约 4 分钟）。
- 面积：minimal_4x4 生成 `LmSramSp_8x177` 与 `LmSramSp_16x177`（4×177 不足阈值，留在寄存器队列）。

## 5. 已知限制

- 不做按 AXI ID 的乱序重排；同 (src,id) 顺序由单路径结构保证，未实现 ID 级记分板。
- 跨域复位不做握手：各域独立复位，使用方需保证复位释放后同步延迟内不依赖对端计数。
- `LmNetworkTop`（未分区）仍只接受同步链路；async 必须走分区 wrapper。
- 只生成单口 `LmSramSp`；本设计无简单双口存储需求，未生成 `LmSramSdp`。
- SRAM 面积系数 0.6 µm²/bit 为占位值，未经存储器编译器标定；逻辑面积公式仍为 v1.0 拟合。
- 其余限制（TL-UL 多拍/poison、SNet 地址窗口译码、P4/深归纳、Clos、STA/功耗/布线）同 v1.0。

## 6. 升级注意事项

- 旧 IR 无需修改：`axiIdWidth` 缺省 4，不含 async 切分边的配置行为与 v1.0 相同；但所有 fabric 端口 bundle 新增了 `id` 字段，接死的 TB/脚本需按新端口表更新（见下条）。
- **端口变化**：所有 fabric 端口 bundle 新增 `id` 字段（默认 4 bit），已接死的 TB/脚本需按新端口表更新；`src` 宽度与含义不变。
- `manifest.json` 的工具版本升到 `1.1.0`，旧 `generated/` 缓存自动失效。
- 若复制了 `signoff.md` 作为评审记录，请重新运行 `loom formal` 以获得 G9 结果。
