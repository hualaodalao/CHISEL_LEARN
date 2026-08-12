# 跑用例并生成 VCD 波形（复制到 sim/hivecore_sim.vcd）
sbt "testOnly HiveCoreSimCase -- -DemitVcd=1"

#生成RTL的
sbt "runMain HiveCoreElaborate"
sbt "runMain ForagerBeeElaborate"
