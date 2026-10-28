# Framestats snapshot loop: dumps gfxinfo + ring rows every INTERVAL seconds
# for DURATION seconds, as fs-<epoch>.txt. Used by glass-capture.ps1 to catch
# per-frame timings DURING the gesture (the ring buffer alone only keeps the tail).
import subprocess, sys, time

adb, device, proc, outdir, duration = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], float(sys.argv[5])
interval = 2.0
t_end = time.time() + duration
n = 0
while time.time() < t_end:
    snap_at = time.time()
    r = subprocess.run(
        [adb, "-s", device, "exec-out", f"dumpsys gfxinfo {proc} framestats"],
        capture_output=True)
    with open(f"{outdir}/fs-{int(snap_at)}.txt", "wb") as f:
        f.write(r.stdout)
    n += 1
    sleep_for = interval - (time.time() - snap_at)
    if sleep_for > 0:
        time.sleep(sleep_for)
print(f"{n} snapshots")
