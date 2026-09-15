#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""task#19 INDEPENDENT re-verification of Robin's task#18 (saturation fix + narrow config).

Nothing here reuses Robin's Scala golden / Dyadic / expected values. The golden is
exact rational arithmetic (fractions.Fraction) derived from OCP MX element semantics
in ref.py, written during task#17 BEFORE task#18 existed.

The RTL under test is the task#18 emission:
    src/hive/verilog_fusionfp_narrow/Hive_FpAddNarrow.sv   (W=19, ExpSatMax claim=254)
compiled by Verilator into .task17_verify/build_t19/sim_t19.

Checks
  C1  saturation/Inf-domain probe: lazy-40 expField <= 254 for EVERY vector, and an
      independently written lazy40->fp32 RNE normalizer never yields NaN / 0x7FC00000.
  C2  DISCRIMINATING clamp test: the same independent integer model is run twice, once
      with clamp=255 (pre-fix hypothesis) and once with clamp=254 (claimed fix). The
      RTL must match 254 and must NOT match 255. (This is a discriminating test, not a
      fit: both hypotheses are pre-registered before looking at the RTL.)
  C3  full W=19 accuracy sweep re-run against the NEW RTL: J0 fidelity, J3 absolute
      bound (unconditional), J2 relative bound on the well-conditioned subset.
  C4  MX8 system-level-equivalent contract check with EXACT rational golden on
      wide-exponent-spread vectors (the domain where Robin's spec falls back to Double).
"""
import os
import subprocess
import sys
from fractions import Fraction
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
sys.path.insert(0, HERE)

import ref
from ref import (TWO, lazy40_decode, lazy40_encode, fp32_decode, fp32_encode_exact,
                 narrow_model, rel_shift, mx_elem)
import vectors as VG

SIM = os.path.join(HERE, 'build_t19', 'sim_t19')
W = 19
THR = W - 1                      # 2^-18
RTL_SV = os.path.join(REPO, 'src/hive/verilog_fusionfp_narrow/Hive_FpAddNarrow.sv')


# --------------------------------------------------------------------------
# independent lazy-40 -> fp32 (RNE) normalizer, written from the field layout
# --------------------------------------------------------------------------
def lazy40_to_fp32(raw):
    raw &= (1 << 40) - 1
    s = (raw >> 39) & 1
    e = (raw >> 31) & 0xFF
    m31 = raw & ((1 << 31) - 1)
    if m31 == 0:
        return (s << 31)                       # +-0
    lz = 31 - m31.bit_length()                 # MSB target is bit 30
    ef = e - lz
    frac = (m31 << lz) & ((1 << 31) - 1)       # bit30 == implicit 1
    f30 = frac & ((1 << 30) - 1)               # 30 fraction bits
    keep = f30 >> 7                            # top 23
    rem = f30 & 0x7F
    guard = (rem >> 6) & 1
    sticky = 1 if (rem & 0x3F) else 0
    rndup = guard and (sticky or (keep & 1))
    mant23 = keep + (1 if rndup else 0)
    if mant23 == (1 << 23):
        mant23 = 0
        ef += 1
    if ef >= 255:
        return (s << 31) | (0xFF << 23)        # Inf  (mant23 forced 0)
    if ef <= 0:
        return (s << 31)                       # flush to +-0 (subnormal path unused here)
    return ((s << 31) | (ef << 23) | mant23) & 0xFFFFFFFF


def selftest_normalizer():
    cases = [(lazy40_encode(Fraction(1)), 0x3F800000),
             (lazy40_encode(Fraction(3, 2)), 0x3FC00000),
             (lazy40_encode(Fraction(1), lz=3), 0x3F800000),
             (lazy40_encode(Fraction(-1)), 0xBF800000),
             (lazy40_encode(Fraction(1, 2)), 0x3F000000),
             (0, 0x00000000)]
    for raw, exp in cases:
        got = lazy40_to_fp32(raw)
        assert got == exp, "normalizer self-test FAIL raw=%010x got=%08x exp=%08x" % (raw, got, exp)
    print("[selftest] independent lazy40->fp32 RNE normalizer: %d/%d OK" % (len(cases), len(cases)))
    # CROSS-VALIDATION against the lazy-40 raws that Robin's system-level run printed in
    # the sbt log. My normalizer was written from the field layout only; agreeing on these
    # five independent data points shows it is the same function as swFpNormalize40to32.
    xv = [(0x7F60000000, 0x7F400000), (0x7F78000000, 0x7F700000), (0x7CF8000000, 0x7CF00000),
          (0x7AF8000000, 0x7AF00000), (0x3FF0800000, 0x3FE10000)]
    nbad = 0
    for raw, exp in xv:
        got = lazy40_to_fp32(raw)
        if got != exp:
            nbad += 1
            print("   [xval MISMATCH] raw=0x%010x mine=0x%08X sbt-log=0x%08X" % (raw, got, exp))
    print("[xval] my normalizer vs the 5 lazy-40 raws printed by the sbt run: %d/%d agree%s"
          % (len(xv) - nbad, len(xv), '' if nbad == 0 else '  <-- INVESTIGATE'))


# --------------------------------------------------------------------------
def run_sim(vecs, tag):
    stim = os.path.join(HERE, 't19_stim_%s.hex' % tag)
    out = os.path.join(HERE, 't19_out_%s.hex' % tag)
    with open(stim, 'w') as f:
        for v in vecs:
            f.write("%x %x %x %x %x %x %x\n" % (v['is4x'], v['is2x'], v['cReg'],
                                                v['prods'][0], v['prods'][1],
                                                v['prods'][2], v['prods'][3]))
    r = subprocess.run([SIM, stim, out], capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr)
        raise SystemExit("sim_t19 failed (%d)" % r.returncode)
    with open(out) as f:
        raws = [int(x, 16) for x in f.read().split()]
    assert len(raws) == len(vecs), "%d vs %d" % (len(raws), len(vecs))
    return raws


def run_sim_lines(lines, tag):
    """Same as run_sim but takes pre-formatted stimulus lines (vectors.py Vec.stim_line)."""
    stim = os.path.join(HERE, 't19_stim_%s.hex' % tag)
    out = os.path.join(HERE, 't19_out_%s.hex' % tag)
    with open(stim, 'w') as f:
        f.write("\n".join(lines) + "\n")
    r = subprocess.run([SIM, stim, out], capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr)
        raise SystemExit("sim_t19 failed (%d)" % r.returncode)
    with open(out) as f:
        raws = [int(x, 16) for x in f.read().split()]
    assert len(raws) == len(lines), "%d vs %d" % (len(raws), len(lines))
    return raws


def V(tag, is4x, is2x, cReg, prods, exact=None, note=''):
    return dict(tag=tag, is4x=is4x, is2x=is2x, cReg=cReg, prods=list(prods),
                exact=exact, note=note)


# --------------------------------------------------------------------------
# C1 + C2 : saturation / Inf domain probe
# --------------------------------------------------------------------------
def build_sat():
    """Worst-case exponent probes. No front-end model needed: eField=254 (fp32Max) is
    the largest finite fp32 exponent and eField=255 is the Inf/NaN reserved domain, so
    these two cover EVERY slot value the cvt/mul front-end can emit."""
    FPMAX = 0x7F7FFFFF            # eField=254, mant=all ones
    FPMAX_N = 0xFF7FFFFF          # same, negative
    INF = 0x7F800000              # eField=255, mant=0  (E5M2 inf code path)
    INF_N = 0xFF800000
    NAN = 0x7FC00001
    E254 = (254 << 31) | (((1 << W) - 1) << (31 - W))       # lazy-40 cReg at eField 254
    E253 = (253 << 31) | (((1 << W) - 1) << (31 - W))
    E255 = (255 << 31) | (((1 << W) - 1) << (31 - W))       # illegal lazy-40, stress only
    out = []
    # MX8 (is2x=1): active slots are 0 (prodFp16) and 1 (prodFp8)
    for nm, p in (('fp32max', FPMAX), ('-fp32max', FPMAX_N), ('inf', INF),
                  ('-inf', INF_N), ('nan', NAN)):
        out.append(V('S-mx8-%s-both' % nm, 0, 1, 0, [p, p, 0, 0],
                     note='MX8 both lanes = %s' % nm))
        out.append(V('S-mx8-%s-one' % nm, 0, 1, 0, [p, 0, 0, 0],
                     note='MX8 single lane = %s' % nm))
        out.append(V('S-mx8-%s+cReg254' % nm, 0, 1, E254, [p, 0, 0, 0],
                     note='MX8 %s on top of a cReg already at eField 254' % nm))
        out.append(V('S-mx8-%s+cReg253' % nm, 0, 1, E253, [p, p, 0, 0],
                     note='MX8 %s on top of cReg eField 253' % nm))
    # MX4 (is4x=1): all five slots active -> maximal growth tier k
    for nm, p in (('fp32max', FPMAX), ('inf', INF), ('mix', None)):
        pr = [FPMAX, INF, FPMAX, INF] if p is None else [p] * 4
        out.append(V('S-mx4-%s-all5' % nm, 1, 0, E254, pr,
                     note='MX4 all five slots at/above eField 254 -> forces max k'))
        out.append(V('S-mx4-%s-c0' % nm, 1, 0, 0, pr, note='MX4 %s, cReg=0' % nm))
    # cReg itself illegal at 255 (E5 garbage) with normal products
    out.append(V('S-cReg255+max', 0, 1, E255, [FPMAX, 0, 0, 0],
                 note='cReg eField=255 (out-of-spec lazy-40) + fp32Max slot'))
    out.append(V('S-cReg255+inf', 1, 0, E255, [FPMAX, INF, FPMAX, INF],
                 note='cReg eField=255 + Inf slots, MX4'))
    # exact golden where it is meaningful (finite fp32 slots only, no Inf/NaN)
    for v in out:
        if 'inf' in v['tag'] or 'nan' in v['tag'] or 'cReg255' in v['tag'] or 'mix' in v['tag']:
            continue
        try:
            cv = lazy40_decode(v['cReg'])
            act = (True, bool(v['is2x'] or v['is4x']), bool(v['is4x']), bool(v['is4x']))
            tot = cv
            for i in range(4):
                if act[i]:
                    d = fp32_decode(v['prods'][i])
                    if d is None:
                        tot = None
                        break
                    tot += d
            v['exact'] = tot
        except Exception:
            v['exact'] = None
    return out


def check_sat():
    print("\n" + "=" * 96)
    print("C1/C2  SATURATION + Inf DOMAIN PROBE  (independent worst-case exponent stimulus)")
    print("       RTL = %s" % RTL_SV.replace(REPO + '/', ''))
    print("=" * 96)
    vecs = build_sat()
    raws = run_sim(vecs, 'sat')

    n254 = n255 = 0
    bad_e = []
    bad_nan = []
    fid254 = fid255 = 0
    clamp_engaged = 0
    for v, raw in zip(vecs, raws):
        e40 = (raw >> 31) & 0xFF
        m31 = raw & ((1 << 31) - 1)
        fp32 = lazy40_to_fp32(raw)
        fexp = (fp32 >> 23) & 0xFF
        fmant = fp32 & 0x7FFFFF
        is_nan = (fexp == 0xFF and fmant != 0)
        if e40 > 254:
            bad_e.append((v['tag'], e40))
        if is_nan or fp32 == 0x7FC00000:
            bad_nan.append((v['tag'], fp32))
        # discriminating model comparison
        m254, i254 = narrow_model(v['is4x'], v['is2x'], v['cReg'], v['prods'], W, sat=254)
        m255, i255 = narrow_model(v['is4x'], v['is2x'], v['cReg'], v['prods'], W, sat=255)
        if i254['expRaw'] > 254:
            clamp_engaged += 1
        fid254 += (m254 == raw)
        fid255 += (m255 == raw)
        n254 += (raw == m254)
        n255 += (raw == m255)
    N = len(vecs)
    print("  vectors                                          : %d" % N)
    print("  vectors where the clamp ACTUALLY engages (E+k>254): %d" % clamp_engaged)
    print("  lazy-40 expField > 254                           : %d   %s" %
          (len(bad_e), 'PASS' if not bad_e else 'FAIL %s' % bad_e[:5]))
    print("  fp32 output is NaN / 0x7FC00000                  : %d   %s" %
          (len(bad_nan), 'PASS' if not bad_nan else 'FAIL %s' % [hex(x[1]) for x in bad_nan[:5]]))
    print("  RTL == independent model(clamp=254)              : %d/%d" % (fid254, N))
    print("  RTL == independent model(clamp=255)  [pre-fix]   : %d/%d" % (fid255, N))
    verdict = (fid254 == N and fid255 < N and not bad_e and not bad_nan and clamp_engaged > 0)
    print("  --> DISCRIMINATING VERDICT: %s" %
          ('PASS (RTL implements clamp=254, and is provably NOT clamp=255)' if verdict else 'FAIL'))
    if fid255 == N:
        print("      !! RTL still matches the pre-fix 255 model -> BUG NOT FIXED")
    if clamp_engaged == 0:
        print("      !! stimulus never engaged the clamp -> test is vacuous")

    print("\n  per-vector detail (first 14):")
    print("   %-26s %-12s %-10s %-6s %-10s %-8s" % ('tag', 'lazy40 raw', 'fp32', 'eOut40', 'E+k', 'verdict'))
    for v, raw in list(zip(vecs, raws))[:14]:
        e40 = (raw >> 31) & 0xFF
        fp32 = lazy40_to_fp32(raw)
        _, i = narrow_model(v['is4x'], v['is2x'], v['cReg'], v['prods'], W, sat=254)
        fexp = (fp32 >> 23) & 0xFF
        nan = fexp == 0xFF and (fp32 & 0x7FFFFF) != 0
        print("   %-26s 0x%010x 0x%08X %-6d %-10d %s" %
              (v['tag'], raw, fp32, e40, i['expRaw'], 'NaN!' if nan else 'finite'))
    return verdict, bad_e, bad_nan


# --------------------------------------------------------------------------
# C3 : full W=19 accuracy sweep on the NEW RTL
# --------------------------------------------------------------------------
def check_sweep(nrand, nadv):
    print("\n" + "=" * 96)
    print("C3  W=19 FULL ACCURACY SWEEP against the task#18 emission (independent Fraction golden)")
    print("=" * 96)
    ref.SAT_MAX = 254
    vecs = VG.build_hand(W) + VG.build_rand(W, nrand, seed=20260915 + W) + \
        VG.build_adv(W, nadv, seed=777000 + W) + VG.build_oos(W)
    raws = run_sim_lines([v.stim_line() for v in vecs], 'sweep')
    judged = []
    fid = 0
    j3fail = []
    byc = defaultdict(list)
    for v, raw in zip(vecs, raws):
        m, info = narrow_model(v.is4x, v.is2x, v.cReg, v.prods, W, sat=254)
        fid += (m == raw)
        if v.exact is None:
            continue
        dut = lazy40_decode(raw)
        diff = dut - v.exact
        lim = (info['nLossy'] + (1 << info['k']) - 1) * TWO ** (info['E'] - 126 - W)
        if abs(diff) > lim:
            j3fail.append((v.tag, info['nLossy'], info['k'], info['E']))
        r = rel_shift(v.exact, diff)
        lsb = TWO ** (info['E'] - 126 - W)
        eil = abs(v.exact) / lsb if lsb else Fraction(0)
        nb = info['nLossy'] + (1 << info['k']) - 1
        cls = ('exact-zero' if v.exact == 0 else 'subLSB' if eil < 1 else
               'illcond' if eil < nb * TWO ** (W - 1) else 'wellcond')
        dom = v.domain
        inspec = dom in ('mx4', 'mx8', 'mx_full_c')
        judged.append(dict(tag=v.tag, grp=v.group, dom=dom, r=r, cls=cls, inspec=inspec,
                           nLossy=info['nLossy'], k=info['k'], E=info['E'], eil=eil))
        if inspec:
            byc[cls].append(judged[-1])
    N = len(vecs)
    ins = [x for x in judged if x['inspec']]
    wc = byc['wellcond']
    allr = [x['r'] for x in judged if x['r'] > -9000]
    wcr = [x['r'] for x in wc]
    below18 = [x for x in ins if x['r'] < THR and x['r'] > -9000]
    print("  vectors total                                    : %d" % N)
    print("  J0 fidelity  RTL == independent model(clamp=254)  : %d/%d  %s" %
          (fid, N, 'PASS' if fid == N else 'FAIL'))
    print("  J3 absolute-bound violations (unconditional law)  : %d/%d  %s" %
          (len(j3fail), len(judged), 'PASS' if not j3fail else 'FAIL %s' % j3fail[:5]))
    print("  worst relative error, ALL judged                  : 2^%d" % min(allr))
    print("  worst relative error, in-spec MX-legal            : 2^%d" %
          min(x['r'] for x in ins))
    print("  in-spec conditioning: wellcond=%d illcond=%d subLSB=%d exact-zero=%d" %
          (len(wc), len(byc['illcond']), len(byc['subLSB']), len(byc['exact-zero'])))
    print("  worst relative error, WELL-CONDITIONED in-spec    : 2^%d   (threshold 2^-%d)  %s" %
          (min(wcr), THR, 'PASS' if min(wcr) >= THR else 'FAIL'))
    print("  wellcond vectors violating 2^-%d                   : %d/%d" %
          (THR, sum(1 for x in wc if x['r'] < THR), len(wc)))
    print("  in-spec vectors worse than 2^-%d (any conditioning): %d/%d" %
          (THR, len(below18), len(ins)))
    nc = [x for x in below18 if x['cls'] != 'subLSB' and x['grp'] not in ('cancel', 'cancel_abs')]
    print("     of which non-cancellation & non-subLSB         : %d" % len(nc))
    return dict(fid=fid, N=N, j3=len(j3fail), worst_wc=min(wcr), n_wc=len(wc),
                inspec=len(ins), below18=len(below18))


# --------------------------------------------------------------------------
# C4 : MX8 wide-spread, EXACT rational golden (Robin's spec uses Double here)
# --------------------------------------------------------------------------
def mx8_system_vectors():
    """My own MX8 stimulus, built from OCP element semantics only.

    MX8 products carry <=9 significant bits -> always fp32-exact, so injecting the
    exact fp32 product into the accumulator is EQUIVALENT to the lossless in-spec
    cvt+mul front-end. Two shapes are generated:
      (a) two lanes at the SAME exponent      -> nLossy=0, must be bit-exact
      (b) lane0 big / lane1 small at a swept ABSOLUTE exponent gap G in [10..60]
                                               -> term-drop / partial-loss domain
    The golden is the exact rational sum p0+p1 -- never Double, never the RTL.
    """
    out = []
    seen = set()

    def add(tag, prods, exact, note):
        if tag in seen:
            return
        seen.add(tag)
        out.append(V(tag, 0, 1, 0, prods, exact=exact, note=note))

    # largest LEGAL FINITE code per format: E5M2 0x7B = (4+3)*2^(30-17);
    #                                       E4M3 0x7E = (8+6)*2^(15-10)   (0x7F is NaN)
    specs = [('E5M2', 0x7B), ('E4M3', 0x7E)]
    for fa, ca in specs:
        for fb, cb in specs:
            for (sa, sb) in ((127, 127), (190, 110)):
                # (a) same-exponent pair, max mantissas -> 9 significant bits
                va, vb = mx_elem(ca, fa, sa), mx_elem(cb, fb, sb)
                if va is None or vb is None:
                    continue
                try:
                    pbits = fp32_encode_exact(va * vb)
                except AssertionError:
                    continue
                # MX8 slot map (read off the emitted RTL): slot0 = io_prodFp16 (always
                # active), slot1 = io_prodFp8 (active when is2x|is4x), slots2/3 =
                # io_prodFp4_0/1 (active only when is4x). So an MX8 lane pair lives in
                # slots 0 and 1 -- NOT 1 and 2.
                add('MX8same-%s%s-s%d_%d' % (fa, fb, sa, sb), [pbits, pbits, 0, 0],
                    va * vb * 2, 'two identical max-mantissa lanes, same exponent')
                # (b) swept absolute gap
                for G in range(10, 61, 3):
                    p0 = va * vb
                    p1 = p0 * TWO ** (-G)
                    try:
                        b0 = fp32_encode_exact(p0)
                        b1 = fp32_encode_exact(p1)
                    except AssertionError:
                        continue
                    add('MX8gap%d-%s%s-s%d_%d' % (G, fa, fb, sa, sb), [b0, b1, 0, 0],
                        p0 + p1, 'absolute product-exponent gap %d' % G)
                    # also with a non-zero cReg two octaves above -> 3 active slots
                    try:
                        cBits = lazy40_encode(p0 * 4)
                    except AssertionError:
                        cBits = None
                    if cBits is not None:
                        key = 'MX8gap%d-cReg-%s%s' % (G, fa, fb)
                        if key not in seen:
                            seen.add(key)
                            out.append(V(key, 0, 1, cBits, [b0, b1, 0, 0],
                                         exact=p0 * 4 + p0 + p1,
                                         note='3 active slots, gap %d' % G))
    return out


def check_mx8_contract():
    print("\n" + "=" * 96)
    print("C4  MX8 WIDE-SPREAD CONTRACT CHECK with EXACT rational golden (not Double)")
    print("=" * 96)
    vecs = mx8_system_vectors()
    if not vecs:
        print("  no vectors generated")
        return False
    raws = run_sim(vecs, 'mx8')
    nfid = 0
    j3fail = []
    wc_bad = []
    worst_wc = 9999
    n_exact = n_rel = n_ill = 0
    worst_all = 9999
    for v, raw in zip(vecs, raws):
        m, info = narrow_model(v['is4x'], v['is2x'], v['cReg'], v['prods'], W, sat=254)
        nfid += (m == raw)
        dut = lazy40_decode(raw)
        diff = dut - v['exact']
        lim = (info['nLossy'] + (1 << info['k']) - 1) * TWO ** (info['E'] - 126 - W)
        if abs(diff) > lim:
            j3fail.append(v['tag'])
        r = rel_shift(v['exact'], diff)
        worst_all = min(worst_all, r)
        lsb = TWO ** (info['E'] - 126 - W)
        eil = abs(v['exact']) / lsb
        nb = info['nLossy'] + (1 << info['k']) - 1
        if r == 9999:
            n_exact += 1
        if eil >= nb * TWO ** (W - 1):
            worst_wc = min(worst_wc, r)
            if r < THR:
                wc_bad.append((v['tag'], r, info['nLossy'], info['k'], info['E']))
            else:
                n_rel += 1
        else:
            n_ill += 1
    N = len(vecs)
    print("  MX8 wide-spread vectors                           : %d" % N)
    print("  J0 fidelity vs independent model(clamp=254)        : %d/%d  %s" %
          (nfid, N, 'PASS' if nfid == N else 'FAIL'))
    print("  J1 absolute bound (contract 1, unconditional)      : %d violations  %s" %
          (len(j3fail), 'PASS' if not j3fail else 'FAIL %s' % j3fail[:5]))
    print("  bit-exact (diff==0)                                : %d/%d" % (n_exact, N))
    print("  well-conditioned (contract 2 applicable)           : %d" % (N - n_ill))
    print("     worst relative error in that subset             : %s   threshold 2^-%d  %s" %
          (('2^%d' % worst_wc) if worst_wc < 9999 else 'n/a', THR,
           'PASS' if not wc_bad else 'FAIL'))
    print("     wellcond vectors violating 2^-%d                 : %d" % (THR, len(wc_bad)))
    print("  ill-conditioned / cancellation (contract 3)        : %d  (no relative claim, "
          "covered by J1)" % n_ill)
    print("  worst relative error over ALL these vectors        : 2^%d" % worst_all)
    if wc_bad:
        for t in wc_bad[:8]:
            print("     VIOLATION %s relErr<=2^%d nLossy=%d k=%d E=%d" % t)
    return (nfid == N and not j3fail and not wc_bad)


def main():
    selftest_normalizer()
    ok_sat, bad_e, bad_nan = check_sat()
    sw = check_sweep(int(sys.argv[1]) if len(sys.argv) > 1 else 3000,
                     int(sys.argv[2]) if len(sys.argv) > 2 else 3000)
    ok_mx8 = check_mx8_contract()
    print("\n" + "=" * 96)
    print("task#19 INDEPENDENT VERDICT")
    print("=" * 96)
    print("  C1/C2 saturation clamp 255->254 : %s" % ('PASS' if ok_sat else 'FAIL'))
    print("  C3    W=19 sweep on new RTL     : %s" %
          ('PASS' if (sw['fid'] == sw['N'] and sw['j3'] == 0 and sw['worst_wc'] >= THR) else 'FAIL'))
    print("  C4    MX8 contract (exact golden): %s" % ('PASS' if ok_mx8 else 'FAIL'))
    return 0 if (ok_sat and ok_mx8 and sw['fid'] == sw['N'] and sw['j3'] == 0
                 and sw['worst_wc'] >= THR) else 1


if __name__ == '__main__':
    sys.exit(main())
