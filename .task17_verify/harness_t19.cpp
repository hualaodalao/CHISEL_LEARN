// task#19 independent Verilator harness for the SUB-MODULE emission of
// Hive_FpAddNarrow (verilog_fusionfp_narrow/), which firtool DCE'd down to a
// purely combinational block with NO clock/reset ports.
// Stimulus line format: "is4x is2x cReg p0 p1 p2 p3" (all hex). Output: io_sum (hex).
#include "VHive_FpAddNarrow.h"
#include "verilated.h"
#include <cstdio>
#include <cstdint>

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: %s <stim.hex> <out.hex>\n", argv[0]); return 2; }
    Verilated::commandArgs(argc, argv);
    VHive_FpAddNarrow *dut = new VHive_FpAddNarrow;
    FILE *fi = fopen(argv[1], "r");
    FILE *fo = fopen(argv[2], "w");
    if (!fi || !fo) { fprintf(stderr, "cannot open files\n"); return 2; }
    dut->eval();

    char line[512];
    long long n = 0;
    while (fgets(line, sizeof(line), fi)) {
        if (line[0] == '#' || line[0] == '\n') continue;
        unsigned int is4x = 0, is2x = 0;
        unsigned long long cReg = 0, p0 = 0, p1 = 0, p2 = 0, p3 = 0;
        int got = sscanf(line, "%x %x %llx %llx %llx %llx %llx",
                         &is4x, &is2x, &cReg, &p0, &p1, &p2, &p3);
        if (got != 7) { fprintf(stderr, "bad line %lld: %s", n, line); return 3; }
        dut->io_is4x = is4x ? 1 : 0;
        dut->io_is2x = is2x ? 1 : 0;
        dut->io_cReg = (uint64_t)(cReg & 0xFFFFFFFFFFull);
        dut->io_prodFp16 = (uint32_t)p0;
        dut->io_prodFp8 = (uint32_t)p1;
        dut->io_prodFp4_0 = (uint32_t)p2;
        dut->io_prodFp4_1 = (uint32_t)p3;
        dut->eval();
        fprintf(fo, "%010llx\n", (unsigned long long)(dut->io_sum & 0xFFFFFFFFFFull));
        n++;
    }
    fclose(fi); fclose(fo);
    fprintf(stderr, "simulated %lld vectors\n", n);
    dut->final();
    delete dut;
    return 0;
}
