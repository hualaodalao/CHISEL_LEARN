#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Independent stimulus generation for FpAddNarrow verification (task#17).

Every vector carries its OWN exact golden value computed with fractions.Fraction
straight from OCP MX element semantics -- never from RTL, never from Robin's
Scala bench.

Rigour note: product slots are taken from a table built by ENUMERATING REAL MX
CODE PAIRS, so every (significand, fp32-exponent-field) combination used here is
genuinely reachable by an upstream narrow multiplier. Ad-hoc (P, eField) pairs
that no MX code pair can produce are never labelled "in-spec".
"""
import random
from fractions import Fraction
from ref import (TWO, fp32_encode_exact, fp32_decode, lazy40_encode, lazy40_decode,
                 mx_elem, mx_prod_slot, fp32_to_lazy40, _dyadic_parts)

F = Fraction
MX4_CODES = list(range(16))
E4M3_CODES = [c for c in range(256) if mx_elem(c, 'E4M3') is not None]
E5M2_CODES = [c for c in range(256) if mx_elem(c, 'E5M2') is not None]

MX8_PAIRS = [('E4M3', 'E4M3'), ('E5M2', 'E5M2'), ('E4M3', 'E5M2'), ('E5M2', 'E4M3')]


def _pool(fmt):
    return MX4_CODES if fmt == 'E2M1' else (E4M3_CODES if fmt == 'E4M3' else E5M2_CODES)


def _build_prod_table():
    """(mode, fp32 eField) -> sorted list of (bits, value, P) reachable from real codes."""
    tab = {}
    combos = [('mx4', ('E2M1', 'E2M1'))] + [('mx8', p) for p in MX8_PAIRS]
    for mode, (fa, fb) in combos:
        for ca in _pool(fa):
            for cb in _pool(fb):
                va, vb = mx_elem(ca, fa), mx_elem(cb, fb)
                if va is None or vb is None:
                    continue
                v = va * vb
                if v == 0:
                    continue
                try:
                    b = fp32_encode_exact(v)
                except AssertionError:
                    continue
                ef = (b >> 23) & 0xFF
                P = _dyadic_parts(v)[0]
                tab.setdefault((mode, ef), {})[b] = (b, v, P)
    out = {}
    for k, d in tab.items():
        out[k] = sorted(d.values(), key=lambda t: (abs(t[1]), t[0]))
    return out


PROD_TAB = _build_prod_table()


def efields(mode):
    return sorted(ef for (m, ef) in PROD_TAB if m == mode)


def pick(mode, ef, rng=None, want_P=None, sign=None, maxP=False):
    """Pick a genuinely MX-reachable product slot with fp32 exponent field `ef`."""
    lst = PROD_TAB.get((mode, ef))
    if not lst:
        return None
    cands = list(lst)
    if want_P is not None:
        c2 = [t for t in cands if t[2] == want_P]
        if c2:
            cands = c2
    if sign is not None:
        c2 = [t for t in cands if (t[1] < 0) == (sign < 0)]
        if c2:
            cands = c2
    if maxP:
        mx = max(t[2] for t in cands)
        cands = [t for t in cands if t[2] == mx]
    return (rng or random).choice(cands)


def psign(bits, value, s):
    """Force the sign of a product slot (flip bit31); value tracks it exactly."""
    b = (bits & 0x7FFFFFFF) | ((1 if s < 0 else 0) << 31)
    v = -abs(value) if s < 0 else abs(value)
    return b, v


class Vec(object):
    __slots__ = ('tag', 'group', 'domain', 'is4x', 'is2x', 'cReg', 'prods',
                 'exact', 'mode', 'note')

    def __init__(self, tag, group, domain, is4x, is2x, cReg, prods, exact, note=''):
        self.tag, self.group, self.domain = tag, group, domain
        self.is4x, self.is2x = is4x, is2x
        self.cReg, self.prods, self.exact = cReg, list(prods), exact
        self.mode = 'MX4' if is4x else ('MX8' if is2x else 'FP16ONLY')
        self.note = note

    def stim_line(self):
        return "%x %x %x %x %x %x %x" % (
            1 if self.is4x else 0, 1 if self.is2x else 0, self.cReg,
            self.prods[0], self.prods[1], self.prods[2], self.prods[3])


def exact_sum(cVal, prodVals, is4x, is2x):
    """exact = cReg_val + sum(ACTIVE lane product_val) -- pure Fraction."""
    act = (True, bool(is2x or is4x), bool(is4x), bool(is4x))
    t = cVal
    for i in range(4):
        if act[i] and prodVals[i] is not None:
            t = t + prodVals[i]
    return t


def c_min_win(eC, sign=0):
    """lazy-40 cReg whose window magnitude is EXACTLY 2^(W-1) at every W:
    mant31 = 2^30 (single bit) -> lossless extract, minimal |exact| contribution.
    This is the shape that maximises relative error, and it IS reachable: a
    self-produced accumulator with magTop == 2^(W-1) and k == 0."""
    v = TWO ** (eC - 127)
    return lazy40_encode(-v if sign else v, lz=0)


def c_units(eC, units, lowbits=0, W=None):
    """lazy-40 cReg with mant31 = (units<<(31-W)) | lowbits."""
    assert W is not None
    m31 = ((units << (31 - W)) | lowbits) & ((1 << 31) - 1)
    return ((eC & 0xFF) << 31) | m31


def mx4_product(rng=None, scale=127):
    rng = rng or random
    return mx_prod_slot(rng.choice(MX4_CODES), rng.choice(MX4_CODES), 'E2M1', 'E2M1', scale)


def mx8_product(pair, rng=None, scale=127):
    rng = rng or random
    fa, fb = pair
    return mx_prod_slot(rng.choice(_pool(fa)), rng.choice(_pool(fb)), fa, fb, scale)


# --------------------------------------------------------------------------
# Group 1: hand-picked representative + boundary vectors (task checklist)
# --------------------------------------------------------------------------

def build_hand(W):
    V = []
    add = V.append
    rng = random.Random(20260915)

    # ---- zero operands ---------------------------------------------------
    add(Vec('Z1-all-zero', 'zero', 'mx8', 0, 1, 0, [0, 0, 0, 0], F(0),
            'all slots zero -> must emit true zero 40h0'))
    for i in range(4):
        ef = efields('mx8')[len(efields('mx8')) // 2]
        t = pick('mx8', ef, random.Random(1000 + i))
        if t is None:
            continue
        prods = [0, 0, 0, 0]
        prods[i] = t[0]
        ex = exact_sum(F(0), [fp32_decode(x) for x in prods], False, True) if i <= 1 else F(0)
        add(Vec('Z2-single-lane%d' % i, 'zero', 'mx8', 0, 1, 0, prods, ex,
                'single active operand (lanes >=2 are inactive in MX8 -> exact must be 0)'))
    # inactive lanes stuffed with garbage large exponents: masking MUST clear expEff too
    g = (0xFE << 23) & 0xFFFFFFFF
    t = pick('mx8', efields('mx8')[-1], random.Random(7))
    add(Vec('Z3-inactive-garbage-exp', 'zero', 'mask', 0, 1, 0, [t[0], 0, g, g], t[1],
            'MX8: L2/L3 hold eField=254 garbage. If masking failed to clear expEff, E '
            'would rise to 254 and the real lane would be shifted out -> gross error'))
    t4 = pick('mx4', efields('mx4')[-1], random.Random(8))
    add(Vec('Z4-fp16only-degenerate', 'zero', 'fp16only', 0, 0, 0, [t4[0], g, g, g], t4[1],
            "don't-care degenerate mode {C,L0}; no accuracy claim"))

    # ---- normal accumulation ---------------------------------------------
    for pi, pair in enumerate(MX8_PAIRS):
        bs, vs = [], []
        for j in range(2):
            b, v = mx8_product(pair, random.Random(300 + pi * 10 + j))
            bs.append(b); vs.append(v)
        add(Vec('N-mx8-%s-%s' % pair, 'norm', 'mx8', 0, 1, 0, bs + [0, 0],
                exact_sum(F(0), vs + [None, None], False, True), 'MX8 3-slot normal'))
    bs, vs = [], []
    for j in range(4):
        b, v = mx4_product(rng=random.Random(50 + j))
        bs.append(b); vs.append(v)
    add(Vec('N-mx4-4lane', 'norm', 'mx4', 1, 0, 0, bs,
            exact_sum(F(0), vs, True, False), 'MX4 5-slot normal'))
    # mixed signs + non-zero cReg, all slots genuinely MX4-reachable
    cv = Fraction(9) * TWO ** 3
    prods, vals = [], []
    for j, sg in enumerate((1, -1, 1, -1)):
        t = pick('mx4', efields('mx4')[j % len(efields('mx4'))], random.Random(70 + j))
        b, v = psign(t[0], t[1], sg)
        prods.append(b); vals.append(v)
    add(Vec('N-mx4-mixedsign-nonzeroC', 'norm', 'mx4', 1, 0, lazy40_encode(cv), prods,
            exact_sum(cv, vals, True, False), 'mixed signs + non-zero cReg'))

    # ---- exponent gap crossing the W boundary ----------------------------
    # cReg expField = laneEF + d, so d is the TRUE gap; laneEF is MX-reachable.
    for mode, is4x in (('mx8', 0), ('mx4', 1)):
        efs = efields(mode)
        baseEF = efs[len(efs) // 2]
        for d in range(max(1, W - 12), W + 12):
            eC = baseEF + d
            if eC > 250:
                continue
            t = pick(mode, baseEF, random.Random(900 + d), maxP=True)
            if t is None:
                continue
            cBits = c_min_win(eC, 0)
            cVal = lazy40_decode(cBits)
            reg = 'lossless' if d <= W - 8 else ('lossy' if d < W else 'term-drop')
            add(Vec('WB-%s-d%d' % (mode, d), 'wbound', mode, is4x, 1 - is4x, cBits,
                    [t[0], 0, 0, 0],
                    exact_sum(cVal, [t[1], None, None, None], bool(is4x), not is4x),
                    'true exponent gap d=%d at W=%d -> %s' % (d, W, reg)))

    # ---- term-drop --------------------------------------------------------
    for mode, is4x in (('mx8', 0), ('mx4', 1)):
        efs = efields(mode)
        baseEF = efs[0]
        for d in (W, W + 1, W + 5, 40, 60):
            eC = baseEF + d
            if eC > 250:
                continue
            t = pick(mode, baseEF, random.Random(1100 + d), maxP=True)
            if t is None:
                continue
            cBits = c_min_win(eC, 1)
            add(Vec('DR-%s-drop-d%d' % (mode, d), 'drop', mode, is4x, 1 - is4x, cBits,
                    [0, t[0], 0, 0],
                    exact_sum(lazy40_decode(cBits), [None, t[1], None, None],
                              bool(is4x), not is4x),
                    'lane shifted entirely out of the window (gap %d >= W)' % d))
    # cReg itself dropped (lanes dominate)
    t = pick('mx8', efields('mx8')[-1], random.Random(5), maxP=True)
    t2 = pick('mx8', efields('mx8')[-1], random.Random(6), maxP=True)
    cBits = c_min_win(efields('mx8')[-1] - (W + 3), 1)
    add(Vec('DR-cReg-dropped', 'drop', 'mx8', 0, 1, cBits, [t[0], t2[0], 0, 0],
            exact_sum(lazy40_decode(cBits), [t[1], t2[1], None, None], False, True),
            'accumulator itself falls out of the window'))

    # ---- cancellation -----------------------------------------------------
    # NOTE: pick() returns whatever sign the underlying MX code pair produced, so the
    # "big" term is FORCED POSITIVE first; otherwise A + (-A) would be -2|A| and the
    # golden value would silently disagree with the intended cancellation.
    eC = efields('mx8')[-1]
    t = pick('mx8', eC, random.Random(11), maxP=True)
    bA, vA = psign(t[0], t[1], +1)                 # force positive
    nA, nvA = psign(t[0], t[1], -1)                # exact negation of the same magnitude
    add(Vec('X1-exact-cancel-2slot', 'cancel', 'mx8', 0, 1, 0, [bA, nA, 0, 0],
            exact_sum(F(0), [vA, nvA, None, None], False, True),
            'A + (-A) -> must emit true zero 40h0'))
    add(Vec('X2-exact-cancel-with-cReg', 'cancel', 'mx8', 0, 1,
            lazy40_encode(vA), [nA, 0, 0, 0],
            exact_sum(vA, [nvA, None, None, None], False, True), 'cReg + (-cReg) -> true zero'))
    # near-complete cancellation, residual still inside the window
    tBig = pick('mx8', eC, random.Random(12), maxP=True)
    bBig, big = psign(tBig[0], tBig[1], +1)        # force positive
    cBig = lazy40_encode(big)
    tRes = pick('mx8', eC - (W - 9), random.Random(13), maxP=True)
    if tRes is not None:
        add(Vec('X3-near-cancel-in-window', 'cancel', 'mx8', 0, 1, cBig,
                [psign(bBig, big, -1)[0], tRes[0], 0, 0],
                exact_sum(big, [-big, tRes[1], None, None], False, True),
                'residual still inside the window -> expect exact'))
    # pathological: residual BELOW one window LSB. The gap is expressed in W so the
    # vector is sub-LSB at every W -> shows relative error is unbounded for ANY W.
    tSub = pick('mx8', eC - (W + 4), random.Random(14), maxP=True)
    if tSub is not None:
        add(Vec('X4-pathological-subLSB(W-relative)', 'cancel', 'mx8', 0, 1, cBig,
                [psign(bBig, big, -1)[0], tSub[0], 0, 0],
                exact_sum(big, [-big, tSub[1], None, None], False, True),
                '|exact| < 1 window LSB by construction at every W -> relative error '
                'unbounded for ANY finite window (intrinsic, not a W=19 defect)'))
    # pathological with an ABSOLUTE gap: sub-LSB for small W but in-window for large W.
    # This is the decisive probe for the "does W=22 fix it?" question. The gap list is
    # deliberately W-INDEPENDENT so that W=16/19/22 are compared on IDENTICAL vectors.
    for G in range(13, 31):
        tt = pick('mx8', eC - G, random.Random(15 + G), maxP=True)
        if tt is None:
            continue
        add(Vec('X5-abs-gap%d' % G, 'cancel_abs', 'mx8', 0, 1, cBig,
                [psign(bBig, big, -1)[0], tt[0], 0, 0],
                exact_sum(big, [-big, tt[1], None, None], False, True),
                'residual at an ABSOLUTE gap %d: sub-LSB for small W, in-window for '
                'large W -> isolates the W-dependence of the cancellation domain' % G))

    # ---- growth tiers k = 0..3 -------------------------------------------
    eC = efields('mx8')[-1]
    tM = pick('mx8', eC, random.Random(16), maxP=True)
    add(Vec('G-mx8-3slot-same-exp', 'growth', 'mx8', 0, 1, c_min_win(eC, 0),
            [tM[0], tM[0], 0, 0],
            exact_sum(lazy40_decode(c_min_win(eC, 0)), [tM[1], tM[1], None, None], False, True),
            'MX-legal same-exponent accumulation -> low growth tier'))
    tM4 = pick('mx4', efields('mx4')[-1], random.Random(17), maxP=True)
    add(Vec('G-mx4-5slot-same-exp', 'growth', 'mx4', 1, 0, c_min_win(efields('mx4')[-1], 0),
            [tM4[0]] * 4,
            exact_sum(lazy40_decode(c_min_win(efields('mx4')[-1], 0)), [tM4[1]] * 4,
                      True, False),
            'MX-legal 5-slot same-exponent accumulation'))
    # synthetic full-window slots: the only way to reach k=3
    if W <= 24:
        full = (((1 << W) - 1) << (24 - W))
        mant = full - (1 << 23)
        if mant >= 0:
            eS = 140
            pBits = (eS << 23) | mant
            cB = (eS << 31) | (((1 << W) - 1) << (31 - W))
            pv = fp32_decode(pBits)
            add(Vec('S-k3-5x-fullwin', 'growth', 'synth', 1, 0, cB, [pBits] * 4,
                    exact_sum(lazy40_decode(cB), [pv] * 4, True, False),
                    'SYNTHETIC (not MX-legal): |S| = 5*(2^W-1) forces growth tier k=3'))
            for k in range(4):
                units = ((1 << (W + k - 1)) + 1) if k else (1 << (W - 1))
                if units >= (1 << (W + 3)):
                    continue
                per = units // 5
                cU = units - 4 * per
                if per <= 0 or cU <= 0 or per >= (1 << W) or cU >= (1 << W):
                    continue
                cB2 = c_units(eS, cU, 0, W)
                pv2 = Fraction(per) * TWO ** (eS - 126 - W)
                try:
                    pB2 = fp32_encode_exact(pv2)
                except AssertionError:
                    continue
                add(Vec('S-k%d-split' % k, 'growth', 'synth', 1, 0, cB2, [pB2] * 4,
                        exact_sum(lazy40_decode(cB2), [fp32_decode(pB2)] * 4, True, False),
                        'SYNTHETIC growth tier k=%d' % k))

    # ---- cReg with leading zeros ------------------------------------------
    t = pick('mx8', efields('mx8')[-1], random.Random(21), maxP=True)
    for lz in (0, 1, 3, 7, 12, 20):
        try:
            cBits = lazy40_encode(t[1], lz=lz)
        except AssertionError:
            continue
        add(Vec('LZ-lz%d' % lz, 'lz', 'mx8', 0, 1, cBits, [t[0], 0, 0, 0],
                exact_sum(lazy40_decode(cBits), [t[1], None, None, None], False, True),
                'cReg carrying %d leading zeros in mant31 (legal lazy form)' % lz))
    # cReg loaded from a FULL 24-bit-mantissa fp32 (external init) -> lossy extract
    for mant in (0x7FFFFF, 0x555555, 0x000001, 0x400000, 0x7FFFFE):
        bits = (140 << 23) | mant
        cBits = fp32_to_lazy40(bits)
        add(Vec('LZ-fullmant-%06x' % mant, 'lz', 'mx_full_c', 0, 1, cBits, [t[0], 0, 0, 0],
                exact_sum(lazy40_decode(cBits), [t[1], None, None, None], False, True),
                'cReg from a canonical fp32 load; mant31 low bits non-zero -> lossy extract'))
    # self-produced shape: mant31 = magTop << (31-W) -> must re-enter losslessly
    for units in (1 << (W - 1), (1 << W) - 1, 0x12345 & ((1 << W) - 1)):
        if units == 0:
            continue
        cBits = c_units(140, units, 0, W)
        add(Vec('LZ-self-produced-u%x' % units, 'lz', 'mx8', 0, 1, cBits, [t[0], 0, 0, 0],
                exact_sum(lazy40_decode(cBits), [t[1], None, None, None], False, True),
                "accumulator in the module's own output shape: re-entry must be lossless"))

    # ---- multi-loss (nLossy >= 2): the W-selection crux -------------------
    # dominant lossless cReg + several MX-legal lanes pushed far down the window,
    # each with a non-zero fractional tail.
    for mode, is4x in (('mx8', 0), ('mx4', 1)):
        efs = efields(mode)
        baseEF = efs[0]
        for d in range(max(1, W - 9), W + 4):
            eC = baseEF + d
            if eC > 250:
                continue
            cBits = c_min_win(eC, 0)
            cVal = lazy40_decode(cBits)
            lanes = []
            for j, seed in enumerate((31, 32, 33, 34)):
                tt = pick(mode, baseEF, random.Random(seed * 100 + d), maxP=(j == 0))
                if tt is None:
                    continue
                lanes.append(psign(tt[0], tt[1], 1 if j % 2 == 0 else -1))
            for nact in (2, 3, 4):
                if mode == 'mx8' and nact > 2:
                    continue
                prods = [0, 0, 0, 0]
                vals = [None, None, None, None]
                for j in range(min(nact, len(lanes))):
                    prods[j] = lanes[j][0]
                    vals[j] = lanes[j][1]
                add(Vec('ML-%s-d%d-n%d' % (mode, d, nact), 'multiloss', mode,
                        is4x, 1 - is4x, cBits, prods,
                        exact_sum(cVal, vals, bool(is4x), not is4x),
                        'dominant lossless cReg + %d lanes at gap %d -> nLossy>=2 candidate'
                        % (nact, d)))
    return V


# --------------------------------------------------------------------------
# Group 2: randomized MX-legal sweep (realistic working domain)
# --------------------------------------------------------------------------

def build_rand(W, n, seed):
    rng = random.Random(seed)
    V = []
    efs4, efs8 = efields('mx4'), efields('mx8')
    for i in range(n):
        is4x = rng.random() < 0.5
        is2x = not is4x
        pair = rng.choice(MX8_PAIRS)
        prods, vals = [], []
        for j in range(4):
            if is4x:
                b, v = mx4_product(rng=rng)
            elif j < 2:
                b, v = mx8_product(pair, rng)
            else:
                b, v = 0, F(0)
            prods.append(b); vals.append(v)
        r = rng.random()
        if r < 0.30:
            cBits, cVal, dom = 0, F(0), ('mx4' if is4x else 'mx8')
        elif r < 0.55:
            j = rng.randrange(4)
            cBits = lazy40_encode(vals[j]) if vals[j] != 0 else 0
            cVal = lazy40_decode(cBits)
            dom = 'mx4' if is4x else 'mx8'
        elif r < 0.75:
            lz = rng.choice((0, 0, 1, 3, 7, 12))
            sc = rng.choice((0, 1, 2, 3, 5, 9))
            mag = sum(vals[:2], F(0)) if is4x else vals[0]
            if mag == 0:
                mag = vals[1] if vals[1] != 0 else TWO ** 5
            try:
                cBits = lazy40_encode(F(sc) * mag, lz=lz)
            except AssertionError:
                cBits = 0
            cVal = lazy40_decode(cBits)
            dom = 'mx4' if is4x else 'mx8'
        else:
            ef = rng.randint(110, 170)
            bits = ((rng.getrandbits(1) << 31) | (ef << 23) | rng.getrandbits(23)) & 0xFFFFFFFF
            cBits = fp32_to_lazy40(bits)
            cVal = lazy40_decode(cBits)
            dom = 'mx_full_c'
        V.append(Vec('R%06d' % i, 'rand', dom, 1 if is4x else 0, 1 if is2x else 0,
                     cBits, prods, exact_sum(cVal, vals, is4x, is2x), 'random MX-legal'))
    return V


# --------------------------------------------------------------------------
# Group 3: adversarial search INSIDE the MX-legal envelope
#   dominant window-minimal accumulator + wide gaps + sign mixing
# --------------------------------------------------------------------------

def build_adv(W, n, seed):
    rng = random.Random(seed)
    V = []
    for i in range(n):
        is4x = rng.random() < 0.5
        is2x = not is4x
        mode = 'mx4' if is4x else 'mx8'
        nlane = 4 if is4x else 2
        efs = efields(mode)
        baseEF = rng.choice(efs)
        d = rng.choice([W - 9, W - 8, W - 7, W - 6, W - 4, W - 2, W - 1, W, W + 1,
                        rng.randint(1, W + 3)])
        eC = baseEF + d
        if eC > 250:
            eC = 250
        if rng.random() < 0.6:
            cBits = c_min_win(eC, rng.getrandbits(1))
        else:
            units = rng.randrange(1, 1 << W)
            cBits = c_units(eC, units, rng.getrandbits(max(1, 31 - W)), W)
        cVal = lazy40_decode(cBits)
        prods, vals = [0, 0, 0, 0], [None, None, None, None]
        for j in range(nlane):
            tt = pick(mode, baseEF, rng)
            if tt is None:
                continue
            b, v = psign(tt[0], tt[1], -1 if rng.getrandbits(1) else 1)
            prods[j], vals[j] = b, v
        V.append(Vec('A%06d' % i, 'adv', mode, 1 if is4x else 0, 1 if is2x else 0,
                     cBits, prods, exact_sum(cVal, vals, is4x, is2x),
                     'adversarial but MX-legal: dominant accumulator + wide gap'))
    return V


# --------------------------------------------------------------------------
# Group 4: out-of-spec -- fidelity + structural guards only (no golden verdict)
# --------------------------------------------------------------------------

def build_oos(W):
    V = []
    EB = 140
    sub = 0x400000                                   # fp32 subnormal slot
    infs = (0xFF << 23)
    nans = (0xFF << 23) | 0x000001
    V.append(Vec('O1-all-subnormal', 'oos', 'oos', 0, 1, 0, [sub, sub, 0, 0], None,
                 'every slot fp32-subnormal -> E must be 0 (E1)'))
    V.append(Vec('O2-cReg-exp0-mant-nz', 'oos', 'oos', 0, 1, (1 << 20),
                 [(EB << 23), 0, 0, 0], None,
                 'cReg violates the lazy-40 invariant exp==0 && mant31!=0 (E2)'))
    mantw = (((1 << W) - 1) << (24 - W)) & 0x7FFFFF if W <= 24 else 0x7FFFFF
    V.append(Vec('O3-exp-clamp', 'oos', 'oos', 1, 0,
                 (254 << 31) | (((1 << W) - 1) << (31 - W)),
                 [((254 << 23) | mantw)] * 4, None,
                 'E near 255 -> expOut must SATURATE to 255, never wrap in 8 bits (E3)'))
    V.append(Vec('O4-inf-slot', 'oos', 'oos', 0, 1, 0, [infs, 0, 0, 0], None, 'fp32 Inf slot'))
    V.append(Vec('O5-nan-slot', 'oos', 'oos', 0, 1, 0, [nans, 0, 0, 0], None, 'fp32 NaN slot'))
    V.append(Vec('O6-garbage-eField-254', 'oos', 'oos', 0, 1, 0,
                 [(EB << 23), 0, (254 << 23) | 0x7FFFFF, (254 << 23) | 0x7FFFFF], None,
                 'MX8 with L2/L3 holding eField=254 garbage (E6)'))
    return V
