#include "workerbee/gem5/model/geometry.hh"

#include <algorithm>

namespace gem5::workerbee
{

namespace
{

uint64_t
bytesOf(uint64_t elems, DataFormat fmt)
{
    return elems * uint64_t(elemBits(fmt)) / 8;
}

/* The hardware splits input formats into "packed" (one byte or a nibble per
 * element) and "non-packed" (one element per 16-bit lane). Only the packed
 * families advance a K pass by totalN times elemsPerLane. */
bool
packedFormat(DataFormat fmt)
{
    return elemBits(fmt) <= 8;
}

int
blockSize(bool m32, int fallback)
{
    return m32 ? 32 : fallback;
}

} // namespace

Job
jobFromRegisters(const uint32_t *r)
{
    Job job;
    job.m = r[RegM] & 0xFFFF;
    job.n = r[RegNK] & 0xFFFF;
    job.k = (r[RegNK] >> 16) & 0xFFFF;
    job.aAddr = r[RegAAddr];
    job.bAddr = r[RegBAddr];
    job.cAddr = r[RegCAddr];
    const uint32_t control = r[RegControl];
    job.aFmt = toFormat(control & 0xF);
    job.bFmt = toFormat((control >> 4) & 0xF);
    job.cFmt = toFormat((control >> 8) & 0xF);
    job.reluOn = (control >> 15) & 1;
    job.rnd = (control >> 16) & 0x7;
    job.blkA = (control >> 22) & 1;
    job.blkB = (control >> 24) & 1;
    job.blkC = (control >> 26) & 1;
    job.loadWMode = (control >> 28) & 1;
    job.requantOn = (control >> 29) & 1;
    job.biasOn = (control >> 30) & 1;
    job.scaleAAddr = r[RegScaleAAddr];
    job.scaleBAddr = r[RegScaleBAddr];
    job.scaleCAddr = r[RegScaleCAddr];
    job.biasAddr = r[RegBiasAddr];
    job.rqMAddr = r[RegRqMAddr];
    job.rqShift = r[RegRqParam] & 0x3F;
    job.zeroPoint = int16_t(r[RegRqParam] >> 16);
    return job;
}

std::optional<int>
configError(const Job &job, const Hardware &hw)
{
    const DataFormat a = job.aFmt;
    const DataFormat b = job.bFmt;
    const DataFormat c = job.cFmt;
    const uint32_t k = job.k & 0xFFFF;
    const int kPerBeatA = hw.totalN * elemsPerLane(a);
    const int kPerBeatB = hw.totalN * elemsPerLane(b);
    const int kLoadA = packedFormat(a) ? kPerBeatA : hw.totalN * 4;

    const bool isAMx = isMx(a);
    const bool isBMx = isMx(b);
    const bool isMxRun = isAMx || isBMx;

    /* Overwriting in ascending priority reproduces the RTL last-connect chain:
     * 3(family) > 11 > 8 > 13 > 12 > 10 > 7 > 5 > 4 > 3(one-sided) > 0. */
    std::optional<int> code;

    /* errCode 0: an MX block larger than the window it scales. */
    if ((isMx(a) && blockSize(job.blkA, 16) > kLoadA) ||
        (isMx(b) && blockSize(job.blkB, 16) > kPerBeatB) ||
        (isMx(c) && blockSize(job.blkC, 16) > hw.totalN)) {
        code = 0;
    }
    if (isAMx != isBMx) {
        code = 3;
    }
    if ((isMxRun && !job.loadWMode) || (isFp8(a) && !job.loadWMode)) {
        code = 4;
    }
    if (a == DataFormat::FP32 || b == DataFormat::FP32) {
        code = 5;
    }
    if (job.m > uint32_t(hw.cBufferDepth - hw.totalN)) {
        code = 7;
    }
    if (job.cAddr & (hw.cBeatBytes() - 1)) {
        code = 10;
    }
    if (job.biasOn && (job.biasAddr & 3)) {
        code = 12;
    }
    if (job.requantOn && (c != DataFormat::INT8 && c != DataFormat::INT4)) {
        code = 13;
    }
    if (job.requantOn && (job.rqMAddr & 3)) {
        code = 13;
    }

    const bool mx8Run = (a == DataFormat::MXE4M3 || a == DataFormat::MXE5M2) ||
                        isFp8(a);
    if ((mx8Run || a == DataFormat::INT8) && (k % (hw.totalN * 2) || k == 0)) {
        code = 8;
    }
    if ((a == DataFormat::MXE2M1 || a == DataFormat::INT4) &&
        (k % (hw.totalN * 4) || k == 0)) {
        code = 8;
    }
    if ((a == DataFormat::MXE4M3 || a == DataFormat::MXE5M2) &&
        b == DataFormat::MXE2M1 &&
        (k % (hw.totalN * 4) || k == 0 ||
         (job.bAddr % uint32_t(hw.beatBytes())))) {
        code = 8;
    }
    if (a == DataFormat::INT8 && b == DataFormat::INT4 &&
        (!job.loadWMode || k % (hw.totalN * 4) || k == 0 ||
         (job.bAddr % uint32_t(hw.beatBytes())))) {
        code = 8;
    }
    if (a == DataFormat::BF16 && (k % hw.totalN || k == 0)) {
        code = 8;
    }

    const bool cIsInt = isIntFormat(c) || c == DataFormat::INT16 ||
                        c == DataFormat::INT32;
    const bool runIsInt = !(a == DataFormat::FP16 || a == DataFormat::BF16) &&
        a != DataFormat::FP32 && !isMx(a) && !isFp8(a);
    if (runIsInt != cIsInt) {
        code = 11;
    }

    if (a == DataFormat::MXE2M1 && b != DataFormat::MXE2M1) {
        code = 3;
    }
    if (a == DataFormat::INT4 && b == DataFormat::INT8) {
        code = 3;
    }
    if ((isFp8(a) != isFp8(b)) || isFp8(c)) {
        code = 3;
    }
    if ((a == DataFormat::BF16) != (b == DataFormat::BF16)) {
        code = 3;
    }

    return code;
}

Geometry
geometryOf(const Job &job, const Hardware &hw)
{
    Geometry g;
    const DataFormat a = job.aFmt;
    const DataFormat b = job.bFmt;
    const DataFormat c = job.cFmt;

    g.t = hw.totalN;
    g.m = int(job.m);
    g.nt = int((job.n + hw.totalN - 1) / hw.totalN);
    g.kPerBeatA = hw.totalN * elemsPerLane(a);
    g.kPerBeatB = hw.totalN * elemsPerLane(b);
    g.kt = packedFormat(a) ? int(job.k / g.kPerBeatA)
                           : int((job.k + hw.totalN - 1) / hw.totalN);

    g.aRowStride = bytesOf(job.k, a);
    g.aKtStride = bytesOf(g.kPerBeatA, a);
    g.bRowStride = job.loadWMode ? bytesOf(job.k, b) : bytesOf(job.n, b);
    g.bKtStride = bytesOf(g.kPerBeatB, b);
    g.cRowStride = bytesOf(job.n, c);
    g.cColTileStride = bytesOf(hw.totalN, c);
    g.cWriteBytes = int(g.cColTileStride);
    g.cBeatsPerRow = g.cWriteBytes > hw.cBeatBytes() ? 2 : 1;

    g.mxA = isMx(a);
    g.mxB = isMx(b);
    g.mxOut = isMx(c);
    g.needsBias = job.biasOn;
    g.needsRqM = job.requantOn;

    const int blkA = blockSize(job.blkA, 16);
    const int blkB = blockSize(job.blkB, 16);
    const int blkC = blockSize(job.blkC, 16);
    g.scaleARowStride = job.k / blkA;
    g.scaleAKtStride = g.kPerBeatA / blkA;
    /* Forward W4A8 (A=MX8, B=MX4) steps ScaleB by A's array window, not by
     * B's packed source word. */
    const bool w4a8 = (a == DataFormat::MXE4M3 || a == DataFormat::MXE5M2) &&
                      b == DataFormat::MXE2M1;
    const int kWindowB = w4a8 ? g.kPerBeatA : g.kPerBeatB;
    g.scaleRRowStride = job.k / blkB;
    g.scaleRKtStride = kWindowB / blkB;
    g.scaleCRowStride = (job.n + blkC - 1) / blkC;
    g.cBlock = blkC;
    return g;
}

} // namespace gem5::workerbee
