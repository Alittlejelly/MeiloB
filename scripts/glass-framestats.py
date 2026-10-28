# Phase-attribution analysis for one capture dir (works WITHOUT simpleperf data).
# Unions all fs-<epoch>.txt ring snapshots + framestats.txt, dedupes by vsync id,
# breaks each frame into UI-thread phases and classifies the dominant cost of janks.
import re, sys, glob, os
import statistics

def read_text(p):
    raw = open(p, 'rb').read()
    if raw[:2] in (b'\xff\xfe', b'\xfe\xff') or raw[1:2] == b'\x00':
        return raw.decode('utf-16', 'replace')
    return raw.decode('utf-8', 'replace')

d = sys.argv[1]
header = None
rows = {}
files = sorted(glob.glob(os.path.join(d, 'fs-*.txt')))
tail = os.path.join(d, 'framestats.txt')
if os.path.exists(tail):
    files.append(tail)
for p in files:
    if not os.path.exists(p):
        continue
    lines = read_text(p).splitlines()
    hi = [i for i, l in enumerate(lines) if l.startswith('Flags,FrameTimelineVsyncId')]
    if not hi:
        continue
    h = [x.strip() for x in lines[hi[0]].rstrip(',').split(',')]
    for l in lines[hi[0] + 1:]:
        if re.match(r'^\d+,\d+,', l):
            parts = [x.strip() for x in l.rstrip(',').split(',')]
            if len(parts) == len(h):
                rows[parts[1]] = dict(zip(h, parts))

if not rows:
    print("no framestats rows found in", d)
    sys.exit(1)

ms = lambda v: float(v) / 1e6
recs = []
for vid, r in rows.items():
    try:
        v = {c: ms(r[c]) for c in ('IntendedVsync','Vsync','HandleInputStart','AnimationStart',
             'PerformTraversalsStart','DrawStart','FrameDeadline','FrameInterval','SyncQueued',
             'SyncStart','IssueDrawCommandsStart','SwapBuffers','FrameCompleted','GpuCompleted')}
    except (ValueError, KeyError):
        continue
    rec = dict(vid=vid, interval=v['FrameInterval'] / 1e6)
    rec['total']      = v['FrameCompleted'] - v['IntendedVsync']
    rec['input_anim'] = v['PerformTraversalsStart'] - v['HandleInputStart']   # measure/layout
    rec['draw_rec']   = v['SyncQueued'] - v['DrawStart'] if v['SyncQueued'] >= v['DrawStart'] else 0  # compose record
    rec['draw_all']   = v['SyncQueued'] - v['PerformTraversalsStart']
    rec['sync']       = v['IssueDrawCommandsStart'] - v['SyncQueued']
    rec['rt_submit']  = v['SwapBuffers'] - v['IssueDrawCommandsStart']        # RenderThread GL work
    rec['gpu_wait']   = v['FrameCompleted'] - v['SwapBuffers']
    rec['overrun']    = v['FrameCompleted'] - v['FrameDeadline']
    recs.append(rec)

recs.sort(key=lambda r: r['total'])
t = sorted(r['total'] for r in recs)
n = len(t)
print(f"dir: {os.path.basename(d)}   unique frames: {n}")
print(f"refresh interval median: {statistics.median(r['interval'] for r in recs):.2f} ms")
print(f"total: med {statistics.median(t):.2f}  p90 {t[int(n*.9)]:.2f}  p99 {t[int(n*.99)]:.2f}  max {t[-1]:.2f}  (ms)")
over = [r for r in recs if r['total'] > 1.5 * r['interval'] and r['total'] > 20]
print(f"frames >20ms: {len(over)}")
print()
print(f"{'total':>7} {'lay':>6} {'draw':>6} {'sync':>6} {'rt':>7} {'gpu':>6}  dominant")
for r in sorted(over, key=lambda x: -x['total'])[:16]:
    dom = max(('layout','input_anim'), ('record','draw_all'), ('sync','sync'), ('renderthread','rt_submit'), ('gpu-wait','gpu_wait'), key=lambda p: r[p[1]])[0]
    print(f"{r['total']:7.1f} {r['input_anim']:6.1f} {r['draw_all']:6.1f} {r['sync']:6.1f} {r['rt_submit']:7.1f} {r['gpu_wait']:6.1f}  {dom}")

if over:
    for k in ('input_anim', 'draw_all', 'sync', 'rt_submit', 'gpu_wait'):
        s = sum(r[k] for r in over)
        print(f"jank-sum {k:10s}: {s:7.1f} ms")
