#ifndef GEM5_WORKERBEE_MODEL_GEOMETRY_HH
#define GEM5_WORKERBEE_MODEL_GEOMETRY_HH

#include <cstdint>
#include <optional>

#include "workerbee/gem5/model/types.hh"

namespace gem5::workerbee
{

/* Compile-time facts the hardware fixes for the default profile. */
struct Hardware
{
    int totalN = 16;
    int aBufferDepth = 64;
    int bBufferDepth = 64;
    int cBufferDepth = 64;
    int scaleBufferDepth = 64;
    int cDmaExtW = 256;
    int addrWidth = 32;
    /* Default profile: BF16, MXE4M3, MXE5M2, MXE2M1, INT8, INT4, FP8E4M3,
     * FP8E5M2 (bit per DataFormat code). */
    uint32_t supportedFmts = 0xF3C;

    int beatBytes() const { return totalN * 2; }       // 256-bit A/B port
    int scaleBeatBytes() const { return 4; }           // 32-bit scale port
    int cBeatBytes() const { return cDmaExtW / 8; }    // 256-bit C port
    bool supports(DataFormat fmt) const
    {
        return validFormat(fmt) && (supportedFmts & (1u << static_cast<int>(fmt)));
    }
};

/* Decoded register snapshot for one command. */
struct Job
{
    uint32_t m = 0;
    uint32_t n = 0;
    uint32_t k = 0;
    DataFormat aFmt = DataFormat::INT8;
    DataFormat bFmt = DataFormat::INT8;
    DataFormat cFmt = DataFormat::INT32;
    bool loadWMode = false;
    bool biasOn = false;
    bool requantOn = false;
    bool reluOn = false;
    uint32_t blkA = 16;
    uint32_t blkB = 16;
    uint32_t blkC = 16;
    uint64_t aAddr = 0;
    uint64_t bAddr = 0;
    uint64_t cAddr = 0;
    uint64_t scaleAAddr = 0;
    uint64_t scaleBAddr = 0;
    uint64_t scaleCAddr = 0;
    uint64_t biasAddr = 0;
    uint64_t rqMAddr = 0;
    uint8_t rnd = 0;
    uint32_t rqShift = 0;
    int16_t zeroPoint = 0;
};

Job jobFromRegisters(const uint32_t *regs);

struct Geometry
{
    int t = 0;               // totalN, compile-time hardware width
    int nt = 0;              // N tiles
    int kt = 0;              // K passes
    int kPerBeatA = 0;
    int kPerBeatB = 0;
    int m = 0;

    uint64_t aRowStride = 0; // K elements x element bytes
    uint64_t aKtStride = 0;  // kPerBeatA x element bytes
    uint64_t bRowStride = 0; // loadWMode selects K-major or N-major rows
    uint64_t bKtStride = 0;
    uint64_t cRowStride = 0;
    uint64_t cColTileStride = 0;
    int cWriteBytes = 0;     // valid bytes in one dense output row
    int cBeatsPerRow = 0;

    bool mxA = false;
    bool mxB = false;
    bool mxOut = false;
    bool needsBias = false;
    bool needsRqM = false;

    uint64_t scaleARowStride = 0;
    uint64_t scaleAKtStride = 0;
    uint64_t scaleRRowStride = 0;
    uint64_t scaleRKtStride = 0;
    uint64_t scaleCRowStride = 0;
    int cBlock = 16;

    uint64_t passes() const { return uint64_t(nt) * kt; }
    uint64_t aBeats() const { return passes() * m; }
    uint64_t bBeats() const { return passes() * uint64_t(t); }
    uint64_t cRows() const { return uint64_t(nt) * m; }
    uint64_t cBeats() const { return cRows() * cBeatsPerRow; }
};

/* Returns the specification error code for a START, or nullopt when legal.
 * The code order follows the hardware priority, not the check order below. */
std::optional<int> configError(const Job &job, const Hardware &hw);

Geometry geometryOf(const Job &job, const Hardware &hw);

} // namespace gem5::workerbee

#endif
