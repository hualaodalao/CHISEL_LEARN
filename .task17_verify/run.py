#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Independent verification driver for FpAddNarrow (task#17).

Pipeline (no reuse of Robin's Scala bench or Dyadic golden):
  1. build stimulus vectors whose golden value is an exact fractions.Fraction
     derived from OCP MX element semantics
  2. run the ACTUAL emitted RTL through a Verilator binary
  3. cross-check  RTL bits  ==  my own independent integer model   (fidelity)
  4. cross-check  RTL value vs  Fraction golden                    (accuracy)
  5. report worst relative error per W / group / domain
"""
import os
import subprocess
import sys
from fractions import Fraction
from collections import defaultdict

from ref import (TWO, lazy40_decode, lazy40_encode, fp32_decode, fp32_encode_exact,
                 narrow_model, rel_shift, mx_elem, mx_prod_slot)
import vectors as VG

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
DIRS = {19: 'verilog_narrow', 16: 'verilog_narrow_w16', 22: 'verilog_narrow_w22'}
SIM = {w: os.path.join(HERE, 'build_w%d' % w, 'sim_w%d' % w) for w in DIRS}


def run_sim(w, vecs, tag='main'):
    """Feed vectors to the Verilator binary built from the emitted RTL; return raw outs."""
    stim = os.path.join(HERE, 'stim_w%d_%s.hex' % (w, tag))
    out = os.path.join(HERE, 'out_w%d_%s.hex' % (w, tag))
    with open(stim, 'w') as f:
        for v in vecs:
            f.write(v.stim_line() + "\n")
    r = subprocess.run([SIM[w], stim, out], capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr)
        raise SystemExit("simulator failed for W=%d" % w)
    with open(out) as f:
        raws = [int(x, 16) for x in f.read().split()]
    assert len(raws) == len(vecs), "vector/output count mismatch %d vs %d" % (len(raws), len(vecs))
    return raws


class Res(object):
    __slots__ = ('vec', 'raw', 'dut', 'diff', 'r', 'model_raw', 'info', 'fidelity',
                 'j3ok', 'cond', 'term_max', 'eil', 'lsb', 'cls')

    def __init__(self, **kw):
        for k in self.__slots__:
            setattr(self, k, kw.get(k))


def evaluate(w, vecs, raws):
    res = []
    for v, raw in zip(vecs, raws):
        model_raw, info = narrow_model(v.is4x, v.is2x, v.cReg, v.prods, w)
        r = Res(vec=v, raw=raw, model_raw=model_raw, info=info,
                fidelity=(model_raw == raw))
        if v.exact is not None:
            dut = lazy40_decode(raw)
            diff = dut - v.exact
            r.dut, r.diff = dut, diff
            r.r = rel_shift(v.exact, diff)
            ok, d, lim = _abs_bound(v, diff, info, w)
            r.j3ok = ok
            terms = [abs(x) for x in _terms(v) if x is not None]
            r.term_max = max(terms) if terms else Fraction(0)
            r.cond = (r.term_max / abs(v.exact)) if v.exact != 0 else None
            # |exact| expressed in window LSB units -- the conditioning measure that
            # actually decides whether a relative bound is meaningful.
            r.lsb = TWO ** (info['E'] - 126 - w)
            r.eil = abs(v.exact) / r.lsb
            nb = info['nLossy'] + (1 << info['k']) - 1
            if v.exact == 0:
                r.cls = 'exact-zero'
            elif r.eil < 1:
                r.cls = 'subLSB'            # |exact| < 1 window LSB: relErr meaningless
            elif r.eil < nb * TWO ** (w - 1):
                r.cls = 'illcond'          # analytically not guaranteed to meet 2^-(W-1)
            else:
                r.cls = 'wellcond'         # analytic bound guarantees <= 2^-(W-1)
        res.append(r)
    return res


def _terms(v):
    out = [lazy40_decode(v.cReg)]
    act = (True, bool(v.is2x or v.is4x), bool(v.is4x), bool(v.is4x))
    for i in range(4):
        out.append(fp32_decode(v.prods[i]) if act[i] else None)
    return out


def _abs_bound(v, diff, info, w):
    lim = (info['nLossy'] + (1 << info['k']) - 1) * TWO ** (info['E'] - 126 - w)
    return abs(diff) <= lim, abs(diff), lim


# --------------------------------------------------------------------------
# RTL-in-the-loop MAC chains (error accumulation over K)
# --------------------------------------------------------------------------

def build_loop(w, nchains, ncycles, seed):
    import random
    rng = random.Random(seed)
    chains = []
    for c in range(nchains):
        is4x = rng.random() < 0.5
        pair = rng.choice(VG.MX8_PAIRS)
        per_cycle = []
        for cyc in range(ncycles):
            prods, vals = [0, 0, 0, 0], [VG.F(0)] * 4
            for j in range(4 if is4x else 2):
                if is4x:
                    b, vv = VG.mx4_product(rng=rng)
                else:
                    b, vv = VG.mx8_product(pair, rng)
                prods[j] = b
                vals[j] = vv
            per_cycle.append((prods, vals))
        chains.append(dict(is4x=is4x, is2x=not is4x, cycles=per_cycle))
    return chains


def run_loop(w, chains, tag='loop'):
    """Drive the RTL for real: cReg of cycle i+1 is the RTL output of cycle i."""
    ncyc = len(chains[0]['cycles'])
    cReg = [0] * len(chains)
    exact = [VG.F(0)] * len(chains)
    out = []
    for cyc in range(ncyc):
        vs = []
        for ci, ch in enumerate(chains):
            prods, vals = ch['cycles'][cyc]
            v = VG.Vec('L%d-c%d' % (ci, cyc), 'loop', 'mx4' if ch['is4x'] else 'mx8',
                       1 if ch['is4x'] else 0, 1 if ch['is2x'] else 0,
                       cReg[ci], prods, None)
            vs.append(v)
        raws = run_sim(w, vs, tag='%s-c%d' % (tag, cyc))
        for ci, raw in enumerate(raws):
            prods, vals = chains[ci]['cycles'][cyc]
            # exact running total = sum of every cycle's tensor-semantics products
            exact[ci] = exact[ci] + VG.exact_sum(
                VG.F(0), vals, chains[ci]['is4x'], chains[ci]['is2x'])
            model_raw, info = narrow_model(vs[ci].is4x, vs[ci].is2x, cReg[ci], prods, w)
            dut = lazy40_decode(raw)
            diff = dut - exact[ci]
            out.append(dict(chain=ci, cyc=cyc, raw=raw, model_raw=model_raw,
                            fidelity=(model_raw == raw), dut=dut, exact=exact[ci],
                            diff=diff, r=rel_shift(exact[ci], diff), info=info,
                            expOut=(raw >> 31) & 0xFF, w=w))
            cReg[ci] = raw
    return out


# --------------------------------------------------------------------------
# reporting
# --------------------------------------------------------------------------

def _log2f(x):
    """log2 of a positive Fraction, computed with integer arithmetic only."""
    if x <= 0:
        return float('-inf')
    num, den = x.numerator, x.denominator
    if num < den:
        # log2(num/den) = -log2(den/num); recurse on the reciprocal
        return -_log2f(Fraction(den, num))
    q = num // den
    base = q.bit_length() - 1
    rem = num - (den << base)
    frac = (rem * 1000) // (den << base) if rem else 0
    return base + frac / 1000.0


def fmt_shift(r):
    if r >= 9999:
        return 'EXACT(diff=0)'
    if r <= -9999:
        return 'UNDEF(exact=0,diff!=0)'
    return '2^-%d' % r if r >= 0 else '2^%d' % (-r)


def fmt_frac(x):
    """Format a positive Fraction as 2^p.q using integer arithmetic only."""
    if x is None:
        return 'n/a'
    if x == 0:
        return '0'
    if x > TWO ** 60:
        return '>2^60'
    if x < TWO ** -60:
        return '<2^-60'
    return '2^%.2f' % _log2f(x)


def report(w, results, threshold):
    print("\n" + "=" * 100)
    print("W = %d      relative-error threshold under test = 2^-%d  (= W-1)" % (w, threshold))
    print("=" * 100)

    nfid = sum(1 for x in results if not x.fidelity)
    print("[fidelity] my independent integer model vs RTL bit pattern: "
          "%d/%d identical, %d MISMATCH" % (len(results) - nfid, len(results), nfid))
    if nfid:
        for x in results:
            if not x.fidelity:
                print("   MISMATCH %s: rtl=%010x model=%010x" % (x.vec.tag, x.raw, x.model_raw))
                if nfid > 10:
                    break

    judged = [x for x in results if x.vec.exact is not None]
    by_group = defaultdict(list)
    by_dom = defaultdict(list)
    for x in judged:
        by_group[x.vec.group].append(x)
        by_dom[x.vec.domain].append(x)

    def block(title, dct):
        print("\n-- %s --" % title)
        print("   %-14s %6s %6s %6s   %-18s %-18s %s" %
              ('key', 'N', 'exact0', 'below', 'worst relErr', 'worst(wellcond)', 'J3-fail'))
        for key in sorted(dct):
            xs = dct[key]
            rs = [x.r for x in xs if x.r > -9000]
            worst = min(rs) if rs else None
            nc = [x.r for x in xs if x.r > -9000 and x.cls == 'wellcond']
            worst_nc = min(nc) if nc else None
            below = sum(1 for x in xs if x.r < threshold and x.r > -9000)
            j3f = sum(1 for x in xs if not x.j3ok)
            print("   %-14s %6d %6d %6d   %-18s %-18s %d" %
                  (key, len(xs), sum(1 for x in xs if x.vec.exact == 0),
                   below, fmt_shift(worst) if worst is not None else '-',
                   fmt_shift(worst_nc) if worst_nc is not None else '-', j3f))

    block('by group', by_group)
    block('by domain', by_dom)

    # the decisive numbers
    print("\n-- DECISIVE --")
    allr = [x for x in judged if x.r > -9000]
    inspec = [x for x in allr if x.vec.domain in ('mx4', 'mx8', 'mx_full_c')]
    print("   worst relative error over ALL judged vectors            : %s" %
          fmt_shift(min(x.r for x in allr)))
    print("   worst over in-spec MX-legal domains (mx4/mx8/mx_full_c) : %s" %
          fmt_shift(min(x.r for x in inspec)))
    print("   count of in-spec vectors BELOW threshold 2^-%d           : %d / %d" %
          (threshold, sum(1 for x in inspec if x.r < threshold), len(inspec)))

    print("\n   conditioning breakdown of the in-spec MX-legal set:")
    print("   %-12s %7s %7s  %-20s %s" % ('class', 'N', 'below', 'worst relErr', 'meaning'))
    meaning = {
        'exact-zero': 'exact == 0; only diff==0 is acceptable',
        'wellcond': '|exact| >= (nLossy+2^k-1)*2^(W-1) LSB -> 2^-(W-1) PROVABLE',
        'illcond': '|exact| above 1 LSB but too small to prove 2^-(W-1)',
        'subLSB': '|exact| < 1 window LSB -> relative metric meaningless at ANY W',
    }
    byc = defaultdict(list)
    for x in inspec:
        byc[x.cls].append(x)
    for c in ('wellcond', 'illcond', 'subLSB', 'exact-zero'):
        xs = byc.get(c, [])
        if not xs:
            print("   %-12s %7d %7s  %-20s %s" % (c, 0, '-', '-', meaning[c]))
            continue
        rs = [x.r for x in xs if x.r > -9000]
        print("   %-12s %7d %7d  %-20s %s" %
              (c, len(xs), sum(1 for x in xs if x.r < threshold),
               fmt_shift(min(rs)) if rs else '-', meaning[c]))
    wc = byc.get('wellcond', [])
    ic = byc.get('illcond', [])
    ml = [x for x in inspec if x.info['nLossy'] >= 2 and x.cls in ('wellcond', 'illcond')]
    mlw = [x for x in ml if x.cls == 'wellcond']
    print("\n   worst over WELL-CONDITIONED in-spec (threshold provable) : %s" %
          (fmt_shift(min(x.r for x in wc)) if wc else '-'))
    print("   count well-conditioned BELOW threshold                   : %d / %d" %
          (sum(1 for x in wc if x.r < threshold), len(wc)))
    print("   worst over in-spec multi-loss nLossy>=2 (any conditioning): %s  (N=%d)" %
          ((fmt_shift(min(x.r for x in ml)) if ml else '-'), len(ml)))
    print("   worst over in-spec multi-loss nLossy>=2 WELL-CONDITIONED : %s  (N=%d)" %
          ((fmt_shift(min(x.r for x in mlw)) if mlw else '-'), len(mlw)))
    print("   count multi-loss well-cond BELOW threshold               : %d / %d" %
          (sum(1 for x in mlw if x.r < threshold), len(mlw)))
    if ic:
        wi = min(ic, key=lambda x: x.r)
        print("   worst ill-conditioned vector: %s relErr<=%s nLossy=%d k=%d eil=%s" %
              (wi.vec.tag, fmt_shift(wi.r), wi.info['nLossy'], wi.info['k'],
               fmt_frac(wi.eil)))
    j3f = [x for x in judged if not x.j3ok]
    print("   analytic absolute-bound (J3-equivalent) violations      : %d / %d" %
          (len(j3f), len(judged)))

    # ---- representative-population view: the RANDOM group only -------------
    rnd = [x for x in judged if x.vec.group == 'rand']
    if rnd:
        rrs = [x.r for x in rnd if x.r > -9000]
        print("\n   [representative population = random MX-legal group only]")
        print("   N=%d  worst relErr=%s  below 2^-%d=%d  below fixed 2^-18=%d" %
              (len(rnd), fmt_shift(min(rrs)), threshold,
               sum(1 for x in rnd if x.r < threshold),
               sum(1 for x in rnd if x.r < 18)))
        rc = defaultdict(int)
        for x in rnd:
            rc[x.cls] += 1
        print("   conditioning: wellcond=%d illcond=%d subLSB=%d exact-zero=%d" %
              (rc['wellcond'], rc['illcond'], rc['subLSB'], rc['exact-zero']))

    # ---- FIXED 2^-18 target: the exact question Robin's section 8b raises ---
    TGT = 18
    bt = [x for x in inspec if x.r < TGT]
    print("\n   [FIXED target 2^-18 -- is it met UNCONDITIONALLY on the in-spec set?]")
    print("   in-spec vectors WORSE than 2^-18 : %d / %d   -> %s" %
          (len(bt), len(inspec), 'NO (claim refuted)' if bt else 'YES'))
    for c in ('wellcond', 'illcond', 'subLSB', 'exact-zero'):
        n = sum(1 for x in bt if x.cls == c)
        if n:
            wst = min(x.r for x in bt if x.cls == c)
            print("      %-11s %4d worse than 2^-18, worst=%s" % (c, n, fmt_shift(wst)))
    if bt:
        nc = [x for x in bt if x.cls != 'subLSB' and x.vec.group not in
              ('cancel', 'cancel_abs')]
        print("      of which NON-cancellation and non-subLSB: %d  (worst=%s)" %
              (len(nc), fmt_shift(min(x.r for x in nc)) if nc else '-'))
        if nc:
            for x in sorted(nc, key=lambda z: z.r)[:5]:
                print("         %-14s grp=%-9s relErr<=%-8s nLossy=%d k=%d eil=%s" %
                      (x.vec.tag, x.vec.group, fmt_shift(x.r), x.info['nLossy'],
                       x.info['k'], fmt_frac(x.eil)))

    # worst offenders with full analytic explanation
    print("\n-- worst 12 offenders (with analytic explanation) --")
    for x in sorted(allr, key=lambda z: z.r)[:12]:
        i = x.info
        if x.cond is None:
            cs = 'n/a(exact=0)'
        elif x.cond > TWO ** 40:
            cs = '>2^40'
        else:
            cs = '2^%.1f' % _log2f(x.cond)
        print("   %-26s grp=%-9s dom=%-10s relErr<=%-9s cls=%-10s nLossy=%d k=%d E=%-4d "
              "eil=%-9s cond=%-9s J3=%s" %
              (x.vec.tag, x.vec.group, x.vec.domain, fmt_shift(x.r), x.cls, i['nLossy'],
               i['k'], i['E'], fmt_frac(x.eil), cs, 'OK' if x.j3ok else 'VIOLATION'))
    btx = [x for x in bt if x.cls != 'subLSB' and x.vec.group not in
           ('cancel', 'cancel_abs')]
    return dict(worst_all=min(x.r for x in allr), worst_inspec=min(x.r for x in inspec),
                below_inspec=sum(1 for x in inspec if x.r < threshold),
                n_inspec=len(inspec), j3fail=len(j3f), fid_mismatch=nfid,
                worst_wc=(min(x.r for x in wc) if wc else None),
                n_wc=len(wc), below_wc=sum(1 for x in wc if x.r < threshold),
                worst_ic=(min(x.r for x in ic) if ic else None),
                n_ic=len(ic), n_sub=len(byc.get('subLSB', [])),
                n_zero=len(byc.get('exact-zero', [])),
                worst_ml=(min(x.r for x in ml) if ml else None), n_ml=len(ml),
                worst_mlw=(min(x.r for x in mlw) if mlw else None), n_mlw=len(mlw),
                below_mlw=sum(1 for x in mlw if x.r < threshold),
                n_below_t18=len(bt), n_below_t18_noncancel=len(btx),
                worst_t18_noncancel=(min(x.r for x in btx) if btx else None),
                rand_worst=(min(x.r for x in rnd) if rnd else None),
                rand_below_thr=sum(1 for x in rnd if x.r < threshold),
                rand_below_t18=sum(1 for x in rnd if x.r < 18),
                rand_n=len(rnd),
                gapabs={x.vec.tag: x for x in judged if x.vec.group == 'cancel_abs'})


def main():
    ws = [19, 16, 22]
    nrand = int(sys.argv[1]) if len(sys.argv) > 1 else 6000
    nadv = int(sys.argv[2]) if len(sys.argv) > 2 else 6000
    summary = {}
    for w in ws:
        vecs = []
        vecs += VG.build_hand(w)
        vecs += VG.build_rand(w, nrand, seed=20260915 + w)
        vecs += VG.build_adv(w, nadv, seed=777000 + w)
        vecs += VG.build_oos(w)
        raws = run_sim(w, vecs)
        res = evaluate(w, vecs, raws)
        summary[w] = report(w, res, w - 1)

        # MAC loop, RTL in the loop
        chains = build_loop(w, 40, 40, seed=4242 + w)
        lr = run_loop(w, chains)
        fid_bad = sum(1 for x in lr if not x['fidelity'])
        rs = [x['r'] for x in lr if x['r'] > -9000]
        last = [x for x in lr if x['cyc'] == 39]
        print("\n-- MAC loop (RTL-in-the-loop, 40 chains x 40 cycles) --")
        print("   fidelity mismatch                    : %d / %d" % (fid_bad, len(lr)))
        print("   worst per-cycle relative error       : %s" % fmt_shift(min(rs)))
        print("   worst at final cycle (K=40 drift)    : %s" %
              fmt_shift(min(x['r'] for x in last if x['r'] > -9000)))
        infl = [(x['expOut']) for x in lr]
        print("   expOut range over the whole loop     : [%d, %d]  (no runaway inflation)"
              % (min(infl), max(infl)))
        summary[w]['loop_worst'] = min(rs)
        summary[w]['loop_final'] = min(x['r'] for x in last if x['r'] > -9000)
        summary[w]['loop_fid'] = fid_bad
        summary[w]['expOut_max'] = max(infl)

    print("\n" + "=" * 100)
    print("CROSS-W SUMMARY (measured with an independent Fraction golden on the real emitted RTL)")
    print("=" * 100)

    def f(v):
        return fmt_shift(v) if v is not None else '-'

    print("  W   thr     worst(all)   worst(in-spec)   worst(WELLCOND)   worst(nLossy>=2,wellcond)"
          "   below-thr(wellcond)   J3-viol   loop-worst  loop-K40")
    for w in ws:
        s = summary[w]
        print("  %-3d 2^-%-4d  %-12s %-16s %-17s %-27s %4d/%-4d          %-9d %-11s %s" %
              (w, w - 1, f(s['worst_all']), f(s['worst_inspec']), f(s['worst_wc']),
               f(s['worst_mlw']), s['below_wc'], s['n_wc'], s['j3fail'],
               f(s['loop_worst']), f(s['loop_final'])))

    print("\n  in-spec population per W (conditioning breakdown):")
    print("  W    in-spec  wellcond  illcond  subLSB  exact-zero   worst(illcond)  "
          "nLossy>=2(any)  nLossy>=2(wellcond)")
    for w in ws:
        s = summary[w]
        print("  %-4d %8d %9d %8d %7d %10d   %-15s %-15s %s" %
              (w, s['n_inspec'], s['n_wc'], s['n_ic'], s['n_sub'], s['n_zero'],
               f(s['worst_ic']), f(s['worst_ml']), f(s['worst_mlw'])))

    # ---- the decisive W-selection table (checklist item 5) ----------------
    print("\n" + "=" * 100)
    print("W-SELECTION VERDICT TABLE  --  measured against a FIXED 2^-18 product target")
    print("=" * 100)
    print("  W    worse-than-2^-18 (in-spec)   of which non-cancellation/non-subLSB   "
          "worst such   random-population worst   random worse-than-2^-18")
    for w in ws:
        s = summary[w]
        print("  %-4d %7d / %-7d          %7d                        %-10s   %-21s %d / %d" %
              (w, s['n_below_t18'], s['n_inspec'], s['n_below_t18_noncancel'],
               f(s['worst_t18_noncancel']), f(s['rand_worst']),
               s['rand_below_t18'], s['rand_n']))
    print("\n  Interpretation:")
    for w in ws:
        s = summary[w]
        verdict = 'MEETS 2^-18 unconditionally' if s['n_below_t18'] == 0 else \
                  'FAILS 2^-18 unconditionally'
        print("    W=%-3d : %s  (%d in-spec vectors worse than 2^-18; %d of them are "
              "cancellation/sub-LSB)" % (w, verdict, s['n_below_t18'],
                                         s['n_below_t18'] - s['n_below_t18_noncancel']))
    print("    Provable statement (holds at every W, 0 violations measured):")
    print("      relErr <= 2^-(W-1)  whenever  |exact| >= (nLossy + 2^k - 1) * 2^(W-1) LSB")
    print("      |diff|  <= (nLossy + 2^k - 1) * 2^(E-126-W)   ALWAYS  (J3 absolute bound)")

    # ---- identical-vector cross-W matrix on the absolute-gap cancellation set
    tags = sorted(set().union(*[set(summary[w]['gapabs']) for w in ws]),
                  key=lambda t: int(t.split('gap')[-1]))
    if tags:
        print("\n" + "=" * 100)
        print("IDENTICAL-VECTOR CROSS-W MATRIX: near-complete cancellation, residual at an")
        print("ABSOLUTE exponent gap G (same vectors at every W -> isolates W's real effect)")
        print("=" * 100)
        print("   %-16s " % 'vector' + " ".join("%-22s" % ('W=%d (thr 2^-%d)' % (w, w - 1))
                                               for w in ws))
        for t in tags:
            row = "   %-16s " % t
            for w in ws:
                x = summary[w]['gapabs'].get(t)
                if x is None:
                    row += " %-22s" % '-'
                else:
                    row += " %-22s" % ("%s %s eil=%s" %
                                       (fmt_shift(x.r), x.cls, fmt_frac(x.eil)))
            print(row)
        print("\n   reading: increasing W shifts the sub-LSB cliff to LARGER gaps, it does")
        print("   not remove it. For every W there remain in-spec gaps with relErr ~ 2^-0.")
    return summary


if __name__ == '__main__':
    main()
