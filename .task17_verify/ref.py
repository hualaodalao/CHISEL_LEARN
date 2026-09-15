#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
INDEPENDENT reference model for FpAddNarrow verification (task#17).

Written from FIRST PRINCIPLES only:
  * OCP MX element value formulas (as given in the task statement)
  * IEEE-754 binary32 slot decoding
  * lazy-40 accumulator decoding: value = mant31 * 2^(exp-157)
  * exact = cReg_val + sum(active lane product_val)   -- all in fractions.Fraction

This file does NOT import/copy Robin's Dyadic class nor any Scala test-bench
code. The golden value is exact rational arithmetic and never consults RTL.
RTL output is compared against it afterwards (see check.py).
"""
from fractions import Fraction

TWO = Fraction(2)

# Exponent-domain saturation clamp of the narrow accumulator, as a switchable
# hypothesis (see narrow_model). 255 = pre-task#18 buggy RTL, 254 = claimed fix.
SAT_MAX = 254

# --------------------------------------------------------------------------
# 1. Bit-field codecs (first principles)
# --------------------------------------------------------------------------

def fp32_decode(bits, ftz_subnormal=False):
    """IEEE-754 binary32 -> exact Fraction. None for inf/nan."""
    bits &= 0xFFFFFFFF
    s = (bits >> 31) & 1
    e = (bits >> 23) & 0xFF
    m = bits & 0x7FFFFF
    if e == 0xFF:
        return None
    if e == 0:
        if m == 0 or ftz_subnormal:
            v = Fraction(0)
        else:
            v = Fraction(m, 1 << 23) * TWO ** (-126)
    else:
        v = Fraction((1 << 23) | m, 1 << 23) * TWO ** (e - 127)
    return -v if s else v


def _dyadic_parts(v):
    """Fraction (dyadic) -> (odd mantissa M, exponent e2) with v = M * 2^e2."""
    num, den = abs(v).numerator, abs(v).denominator
    e2 = 0
    if den != 1:
        sh = den.bit_length() - 1
        assert TWO ** sh == Fraction(den), "denominator is not a power of two"
        e2 = -sh
    M = num
    while M > 1 and (M & 1) == 0:
        M >>= 1
        e2 += 1
    return M, e2


def fp32_encode_exact(v):
    """Exactly-fp32-representable Fraction -> bits. Asserts exactness."""
    if v == 0:
        return 0
    s = 1 if v < 0 else 0
    M, e2 = _dyadic_parts(v)
    bl = M.bit_length()
    assert bl <= 24, "significand %d bits > 24: not fp32-exact" % bl
    ef = bl - 1 + e2 + 127
    assert 1 <= ef <= 254, "expField %d outside fp32 normal range" % ef
    mant23 = (M << (24 - bl)) & 0x7FFFFF
    return ((s << 31) | (ef << 23) | mant23) & 0xFFFFFFFF


def lazy40_decode(raw):
    """lazy-40 -> exact Fraction. value = mant31 * 2^(exp-157)."""
    raw &= (1 << 40) - 1
    s = (raw >> 39) & 1
    e = (raw >> 31) & 0xFF
    m31 = raw & ((1 << 31) - 1)
    if e == 0 and m31 == 0:
        return Fraction(0)
    v = Fraction(m31) * TWO ** (e - 157)
    return -v if s else v


def lazy40_encode(v, lz=0):
    """exact Fraction -> lazy-40 bits with `lz` leading zeros injected in mant31."""
    if v == 0:
        return 0
    assert 0 <= lz <= 30
    s = 1 if v < 0 else 0
    M, e2 = _dyadic_parts(v)
    bl = M.bit_length()
    assert bl + lz <= 31, "significand %d + lz %d > 31" % (bl, lz)
    mant31 = M << (30 - lz - (bl - 1))
    ef = e2 + 126 + lz + bl
    assert 1 <= ef <= 255, "lazy-40 expField %d out of range" % ef
    return (s << 39) | (ef << 31) | mant31


def fp32_to_lazy40(bits):
    """fp32 bits -> lazy-40 bits (canonical lz=0 accumulator load)."""
    v = fp32_decode(bits)
    assert v is not None
    return lazy40_encode(v, lz=0)


# --------------------------------------------------------------------------
# 2. OCP MX element decoding (formulas verbatim from the task statement)
#    E2M1 normal = +-(2+m)*2^(e-2); subnormal = +-0.5, NOT FTZ
#    E4M3        = +-(8+m)*2^(e-10)
#    E5M2        = +-(4+m)*2^(e-17)
#    block scale (E8M0) = 2^(scale-127); scale=127 -> factor 1
# --------------------------------------------------------------------------
MX_PARAMS = {
    'E2M1': dict(expW=2, mantW=1, bias=1, no_inf_nan=True, ftz=False),
    'E4M3': dict(expW=4, mantW=3, bias=7, no_inf_nan=False, ftz=True),
    'E5M2': dict(expW=5, mantW=2, bias=15, no_inf_nan=False, ftz=True),
}


def mx_elem(code, fmt, scale=127):
    """MX element value (block scale already applied) -> exact Fraction; None if inf/nan."""
    p = MX_PARAMS[fmt]
    code &= 0xFF
    s = (code >> (p['expW'] + p['mantW'])) & 1
    e = (code >> p['mantW']) & ((1 << p['expW']) - 1)
    m = code & ((1 << p['mantW']) - 1)
    eMax = (1 << p['expW']) - 1
    mMax = (1 << p['mantW']) - 1
    sgn = -1 if s else 1
    if p['no_inf_nan']:
        is_inf_nan = False
    elif fmt == 'E4M3':
        is_inf_nan = (e == eMax and m == mMax)     # NaN only, no Inf
    else:
        is_inf_nan = (e == eMax)                   # E5M2: e all ones -> Inf/NaN
    if is_inf_nan:
        return None
    sx = (scale & 0xFF) - 127
    if e == 0 and m == 0:
        return Fraction(0)
    if e == 0:
        if p['ftz']:
            return Fraction(0)
        v = Fraction(1, 2) * TWO ** sx             # E2M1 +-0.5, first-class citizen
    elif fmt == 'E2M1':
        v = Fraction(2 + m) * TWO ** (e - 2) * TWO ** sx
    elif fmt == 'E4M3':
        v = Fraction(8 + m) * TWO ** (e - 10) * TWO ** sx
    else:
        v = Fraction(4 + m) * TWO ** (e - 17) * TWO ** sx
    return sgn * v


def mx_prod_slot(ca, cb, fmta, fmtb, scale=127):
    """Tensor-semantics product of two MX elements -> (fp32 bits, exact Fraction).
    The product is fp32-exact for all legal MX code pairs (significand <= 8 bits)."""
    va = mx_elem(ca, fmta, scale)
    vb = mx_elem(cb, fmtb, scale)
    assert va is not None and vb is not None, "inf/nan code in in-spec stimulus"
    v = va * vb
    return fp32_encode_exact(v), v


# --------------------------------------------------------------------------
# 3. Independent integer model of the narrow-window algorithm
#    Used ONLY to diagnose RTL<->documented-spec fidelity (J0-equivalent).
#    The authoritative accuracy verdict comes from RTL vs Fraction exact.
# --------------------------------------------------------------------------

def narrow_model(is4x, is2x, cReg, prods, W, sat=None):
    # `sat` is the exponent-domain saturation clamp under test. task#17 measured the
    # pre-fix RTL (255); task#18 claims it is now 254. Keeping it a PARAMETER lets the
    # very same independent model be run against both hypotheses and report which one
    # the RTL actually matches -- that is a discriminating test, not a fit.
    if sat is None:
        sat = SAT_MAX
    LOW_SH, MAGW = 31 - W, W + 3
    act = (True, bool(is2x or is4x), bool(is4x), bool(is4x))

    raw = cReg & (1 << 40) - 1
    cS = (raw >> 39) & 1
    cE = (raw >> 31) & 0xFF
    cM = raw & ((1 << 31) - 1)
    cZero = (cE == 0 and cM == 0)
    slots = [(cS, 0 if cZero else cE, (cM >> LOW_SH) if not cZero else 0, not cZero, 31, cM)]
    for i in range(4):
        b = prods[i] & 0xFFFFFFFF
        s = (b >> 31) & 1
        e = (b >> 23) & 0xFF
        m = b & 0x7FFFFF
        zero = (e == 0 and m == 0)
        valid = act[i] and not zero
        mag24 = (1 << 23) | m
        magW = (mag24 << (W - 24)) if W >= 24 else (mag24 >> (24 - W))
        slots.append((s, e if valid else 0, (magW & ((1 << W) - 1)) if valid else 0,
                      valid, 24, mag24))

    E = max([sl[1] for sl in slots if sl[3]], default=0)

    info = dict(E=E, nLossy=0, nDrop=0, per=[])
    Ssum = 0
    for idx, (s, e, magW, valid, fieldW, orig) in enumerate(slots):
        if not valid:
            info['per'].append(dict(idx=idx, valid=False, ts=None, a=0, lossy=False, drop=False))
            continue
        d = E - e
        sh = d if d < W else W
        a = magW >> sh
        # Lossiness is measured against the ORIGINAL field, because the window value
        # comes from two successive floors: the constant extract (fieldW-W) and the
        # dynamic align (sh). floor(floor(x/2^p)/2^q) == floor(x/2^(p+q)).
        ts = (fieldW - W) + sh if fieldW >= W else sh
        lost = orig & ((1 << ts) - 1) if ts > 0 else 0
        lossy = lost != 0
        drop = d >= W
        if lossy:
            info['nLossy'] += 1
        if drop:
            info['nDrop'] += 1
        info['per'].append(dict(idx=idx, valid=True, ts=ts, a=a, lossy=lossy, drop=drop, lost=lost))
        Ssum += -a if s else a

    neg = Ssum < 0
    magFull = abs(Ssum) & ((1 << MAGW) - 1)
    k = 3 if (magFull >> (MAGW - 1)) & 1 else 2 if (magFull >> (MAGW - 2)) & 1 else \
        1 if (magFull >> (MAGW - 3)) & 1 else 0
    magTop = (magFull >> k) & ((1 << W) - 1)
    expRaw = E + k
    expOut = sat if expRaw > sat else expRaw
    out = 0 if magFull == 0 else (((1 if neg else 0) << 39) | (expOut << 31) | (magTop << LOW_SH))
    info.update(k=k, S=Ssum, magFull=magFull, neg=neg, clamped=expRaw > sat, expRaw=expRaw)
    return out, info


# --------------------------------------------------------------------------
# 4. Exact relative-error measurement (pure integer, no floating point)
# --------------------------------------------------------------------------

def rel_shift(exact, diff):
    """Largest integer r with |diff|*2^r <= |exact|  =>  relErr <= 2^-r.
    +9999 when diff==0 (bit-exact); -9999 when exact==0 but diff!=0."""
    a, b = abs(exact), abs(diff)
    if b == 0:
        return 9999
    if a == 0:
        return -9999
    R = a.numerator * b.denominator
    L = b.numerator * a.denominator
    if L <= R:
        return (R // L).bit_length() - 1
    q = (L + R - 1) // R
    return -((q - 1).bit_length())


def abs_bound_ok(exact, dut, nLossy, k, E, W):
    """J3-equivalent analytic absolute bound: |diff| <= (nLossy + 2^k - 1) * windowLsb."""
    diff = abs(dut - exact)
    lim = (nLossy + (1 << k) - 1) * TWO ** (E - 126 - W)
    return diff <= lim, diff, lim
