#ifndef GEM5_WORKERBEE_MODEL_TYPES_HH
#define GEM5_WORKERBEE_MODEL_TYPES_HH

#include <array>
#include <cstdint>

namespace gem5::workerbee
{

/* Encoding follows the hardware format table; only names used by the model are
 * listed here because the model never touches element values. */
enum class DataFormat : uint8_t
{
    FP32 = 0,
    FP16 = 1,
    BF16 = 2,
    MXE4M3 = 3,
    MXE5M2 = 4,
    MXE2M1 = 5,
    INT32 = 6,
    INT16 = 7,
    INT8 = 8,
    INT4 = 9,
    FP8E4M3 = 10,
    FP8E5M2 = 11
};

constexpr int numDataFormats = 12;

enum class Op : uint8_t
{
    RegWrite1 = 0,
    RegWrite2 = 1,
    RegRead = 2
};

struct Command
{
    uint8_t op = 0;
    uint8_t reg0 = 0;
    uint8_t reg1 = 0;
    uint32_t data0 = 0;
    uint32_t data1 = 0;
    uint32_t pc = 0;
    uint8_t tag = 0;
};

constexpr int numLanes = 4;
using Batch = std::array<Command, numLanes>;
using LaneMask = std::array<bool, numLanes>;

struct Response
{
    uint32_t data = 0;
    uint8_t lane = 0;
    bool err = false;
};

/* Register indices. */
enum RegIndex : uint8_t
{
    RegM = 0x00,
    RegNK = 0x01,
    RegAAddr = 0x02,
    RegBAddr = 0x03,
    RegCAddr = 0x04,
    RegControl = 0x05,
    RegScaleAAddr = 0x06,
    RegScaleBAddr = 0x07,
    RegScaleCAddr = 0x08,
    RegBiasAddr = 0x09,
    RegRqMAddr = 0x0A,
    RegRqParam = 0x0B,
    RegStart = 0x10,
    RegStatus = 0x1E,
    RegIrqRaw = 0x30,
    RegIrqMask = 0x31,
    RegIrqMasked = 0x32,
    RegIrqClear = 0x33,
    RegErrorCode = 0x34
};

/* Interrupt bit assignment (low 11 bits). */
enum IrqBit : uint8_t
{
    IrqDone = 0,
    IrqConfigError = 1,
    IrqDmaA = 2,
    IrqDmaB = 3,
    IrqDmaC = 4,
    IrqDmaScaleA = 5,
    IrqDmaScaleB = 6,
    IrqDmaScaleC = 7,
    IrqDmaBias = 8,
    IrqDmaRqM = 9,
    IrqWriteConflict = 10
};

constexpr uint32_t irqBits = 11;
constexpr uint32_t irqMaskAll = (1u << irqBits) - 1;

inline DataFormat
toFormat(uint8_t code)
{
    return static_cast<DataFormat>(code <= 11 ? code : 12);
}

inline bool
validFormat(DataFormat fmt)
{
    return static_cast<int>(fmt) < numDataFormats;
}

inline int
elemBits(DataFormat fmt)
{
    switch (fmt) {
      case DataFormat::FP32: case DataFormat::INT32: return 32;
      case DataFormat::FP16: case DataFormat::BF16: case DataFormat::INT16: return 16;
      case DataFormat::MXE4M3: case DataFormat::MXE5M2:
      case DataFormat::INT8: case DataFormat::FP8E4M3: case DataFormat::FP8E5M2:
        return 8;
      case DataFormat::MXE2M1: case DataFormat::INT4: return 4;
      default: return 0;
    }
}

inline int
elemsPerLane(DataFormat fmt)
{
    const int bits = elemBits(fmt);
    return bits == 0 ? 0 : 16 / bits;
}

inline bool
isMx(DataFormat fmt)
{
    return fmt == DataFormat::MXE4M3 || fmt == DataFormat::MXE5M2 ||
           fmt == DataFormat::MXE2M1;
}

inline bool
isFp8(DataFormat fmt)
{
    return fmt == DataFormat::FP8E4M3 || fmt == DataFormat::FP8E5M2;
}

inline bool
isIntFormat(DataFormat fmt)
{
    return fmt == DataFormat::INT8 || fmt == DataFormat::INT4;
}

inline bool
isBf16(DataFormat fmt)
{
    return fmt == DataFormat::BF16;
}

inline bool
isFloatFamily(DataFormat fmt)
{
    return fmt == DataFormat::FP32 || fmt == DataFormat::FP16 ||
           fmt == DataFormat::BF16 || isFp8(fmt) || isMx(fmt);
}

} // namespace gem5::workerbee

#endif
