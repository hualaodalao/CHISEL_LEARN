# 跑用例并生成 VCD 波形（复制到 sim/hivecore_sim.vcd）
sbt -DemitVcd=1 "testOnly HiveCoreSimCase"