# 跑用例并生成 FST 波形（复制到 sim/hivecore_sim.fst / sim/hivecore_loadw_sim.fst）
# 环境硬约束：Verilator 5.050 的 FST writer 无条件依赖 lz4 头文件，
# ChiselSim 不暴露 -CFLAGS 注入口，须在运行 sbt 前设置 CPATH/LIBRARY_PATH（本机 homebrew lz4）
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreSimCase -- -DemitFst=1"
CPATH=/opt/homebrew/include LIBRARY_PATH=/opt/homebrew/lib sbt "testOnly HiveCoreSimCaseLoadWSpec -- -DemitFst=1"

#生成RTL的
sbt "runMain HiveCoreElaborate"
sbt "runMain ForagerBeeElaborate"
