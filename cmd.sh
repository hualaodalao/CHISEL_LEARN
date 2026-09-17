# 跑用例并生成 FST 波形（复制到 sim/hivecore_sim.fst / sim/hivecore_loadw_sim.fst）
# 环境硬约束：Verilator 5.050 的 FST writer 无条件依赖 lz4 头文件，
# ChiselSim 不暴露 -CFLAGS 注入口，须在运行 sbt 前设置 CPATH/LIBRARY_PATH（本机 homebrew lz4）
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreSimCase -- -DemitFst=1"
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreSimCaseLoadWSpec -- -DemitFst=1"

#生成RTL的
sbt "runMain HiveCoreElaborate"
sbt "runMain ForagerBeeElaborate"

# ===== ForagerBee 功能仿真（本机环境硬约束）=====
# 本机装有企业安全软件（阿里 EDR com.alibaba.endpoint.aliedr）+ macOS AMFI，会 SIGKILL
# 从系统临时目录 /var/folders/.../T/ 执行的 ad-hoc 签名二进制（临时目录被视为高危执行位置），
# 但同样的二进制从项目目录内运行则正常。ChiselSim 的 EphemeralSimulator 用
# HasTestingDirectory.temporary() 把 Verilator 工作区建在 OS 临时目录 ⟹ simulation 二进制被杀 ⟹
# simulation-log.txt 从不生成 ⟹ 所有真仿真用例报 FileNotFoundException（此前被误判为"环境噪声"）。
# 修复：-Djava.io.tmpdir 重定向到项目内，让 temporary() 工作区落在项目目录（不被 EDR 拦截）。
# 另需 CPATH/LIBRARY_PATH（同上 lz4 约束）。
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt -J-Xmx6G \
  -Djava.io.tmpdir=/Users/wangliutailong/Qoder_Proj/Chisel_Learn/build/tmp \
  "testOnly *ForagerBee*"


# ===== 可传参调试用例 HiveCoreDebugCase（Task #19）=====
# 参数三途径：sbt "testOnly ... -- -Dk=v"（ConfigMap）> sbt -Dk=v "testOnly ..."（JVM 属性）
#            > HiveCoreDebugCase 文件顶部默认块（DEF_*）
# 常用键：m n k aFmt bFmt cFmt loadW aMxBlk bMxBlk cMxBlk addrMode rnd seed strictAddr maxCycles wave emitFst
# 格式值大小写不敏感，支持别名（fp16/half/f16、mxe4m3/e4m3、mxfp4/e2m1、i8/int8 ...）；非法值直接报清晰错误
# 三组内置：默认组(FP16 M=N=K=32 loadW=0) / INT 组(INT16×INT16→INT32) / MX 组(MXE4M3 loadW=1 aMxBlk=16)
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreDebugCase -- -DemitFst=1"
# 自定义形状与种子（波形落到 sim/hivecore_debug.fst）
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreDebugCase -- -DemitFst=1 -Dm=64 -Dn=32 -Dk=32 -Dseed=7"
# MXE4M3 单跑（-z MX 只跑 MX 组）+ 逐拍地址致命断言；blk=32=kPerBeat 时数值全绿
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreDebugCase -- -z MX -DemitFst=1 -DaFmt=mxe4m3 -DbFmt=mxe4m3 -DcFmt=fp32 -DloadW=1 -DaMxBlk=32 -DbMxBlk=32 -DstrictAddr=1 -Dwave=sim/debug/mx.fst"
# 对照：-DaMxBlk=16（<kPerBeat=32）地址对账仍 0 差异（证明 mxBlockSize 生效），
# 但 C 会 640/1024 失配且 relErr 恒为 2 的幂 —— 阶段 2 在途 executor scale 广播粒度局限
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreDebugCase -- -z MX -DemitFst=1 -DaMxBlk=16 -DbMxBlk=32 -Dwave=sim/debug/mx_blk16.fst"
# BF16 输出 + RUP 舍入
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreDebugCase -- -DemitFst=1 -DcFmt=bf16 -Drnd=rup -Dwave=sim/debug/bf16_rup.fst"
