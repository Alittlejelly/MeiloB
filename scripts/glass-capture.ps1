# GlassMorph expand/collapse frame-capture pipeline
# Usage: powershell -File scripts\glass-capture.ps1 [-Seconds 8] [-Label expandcollapse]
# Captures: 1) simpleperf CPU samples (Skia/HWUI native stacks)
#           2) logcat for app pid (Skipped frames / Davey / GLASSPROBE markers)
#           3) gfxinfo framestats (per-frame 24-column timeline)
#           4) jank summary
# Notes: this ROM's perfetto traced is broken (vsock missing) -> framestats instead.
#        `input` injection is blocked by HyperOS security -> operate the phone manually.
param(
    [int]$Seconds = 8,
    [string]$Label = "morph",
    [string]$Device = "6924b60d",
    [string]$Adb = "C:\Users\18978\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    [switch]$NoPerf   # non-debuggable build: skip simpleperf/run-as, gfxinfo+framestats+logcat only
)

$Pkg = "com.neoruaa.meilox"          # package (gfxinfo / run-as)
$ProcName = "com.netease.cloudmusic" # android:process disguise in manifest (pidof)
$D = @("-s", $Device)
$OutDir = Join-Path (Split-Path $PSScriptRoot -Parent) ".perf\$Label-$(Get-Date -Format 'HHmmss')"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function ADB([string]$cmd) { & $Adb @D shell $cmd 2>&1 }

$appPid = ((ADB "pidof $ProcName") | Out-String).Trim()
if (-not $appPid) {
    Write-Host "[!] $Pkg not running, launching..."
    ADB "am start -n $Pkg/com.ljyh.mei.MainActivity" | Out-Null
    Start-Sleep 5
    $appPid = ((ADB "pidof $ProcName") | Out-String).Trim()
    if (-not $appPid) { throw "Process not found. Unlock the phone and make sure the app can run." }
}
Write-Host "[*] PID=$appPid  OutDir=$OutDir"

# Frame-logging switches (idempotent, re-applied per run)
ADB "setprop log.tag.Choreographer VERBOSE" | Out-Null
ADB "setprop log.tag.hwui INFO"             | Out-Null
ADB "setprop debug.hwui.profile true"       | Out-Null

ADB "log -p i -t GLASSPROBE === capture:$Label begin pid:$appPid ===" | Out-Null
ADB "dumpsys gfxinfo $ProcName reset" | Out-Null

# 1. Background: logcat for our pid -> file
$logcatFile = Join-Path $OutDir "logcat.txt"
$lcArgs = @('-s', $Device, 'logcat', '-v', 'epoch', "--pid=$appPid")
$logcatJob = Start-Process -FilePath $Adb -ArgumentList $lcArgs `
    -RedirectStandardOutput $logcatFile -PassThru -NoNewWindow

# 2. On-device simpleperf (all threads, 1000Hz, fp unwind) - blocks $Seconds
$dataName = "cap-$Label.data"
Write-Host ""
Write-Host "[>>] Sampling starts in 3s. On the phone repeat: EXPAND -> pause -> COLLAPSE (~3 rounds)" -ForegroundColor Yellow
Start-Sleep 3
ADB "log -p i -t GLASSPROBE --- sample window start ---" | Out-Null
if ($NoPerf) {
    # ring buffer keeps ~120 rows; snapshots every 2s capture mid-gesture frames
    $snapPy = Join-Path $PSScriptRoot "glass-snapshots.py"
    $snapArgs = @($snapPy, $Adb, $Device, $ProcName, $OutDir, ($Seconds + 2))
    $snapJob = Start-Process -FilePath "python" -ArgumentList $snapArgs -PassThru -NoNewWindow
    Start-Sleep $Seconds
    Wait-Process -Id $snapJob.Id -Timeout 30 -ErrorAction SilentlyContinue
    "snapshots (no-perf mode)" | Out-File (Join-Path $OutDir "note.txt") -Encoding utf8
} else {
$snapPy = Join-Path $PSScriptRoot "glass-snapshots.py"
$snapArgs = @($snapPy, $Adb, $Device, $ProcName, $OutDir, ($Seconds + 2))
$snapJob = Start-Process -FilePath "python" -ArgumentList $snapArgs -PassThru -NoNewWindow
$rec = ADB "run-as $Pkg simpleperf record -p $appPid --duration $Seconds -f 1000 -e cpu-clock --call-graph fp -o $dataName"
Wait-Process -Id $snapJob.Id -Timeout 20 -ErrorAction SilentlyContinue
$rec | Select-String "Samples recorded|lost|E " | ForEach-Object { Write-Host "    $_" }
}
ADB "log -p i -t GLASSPROBE --- sample window end ---" | Out-Null

# 3. framestats + summary right after sampling window
ADB "dumpsys gfxinfo $ProcName"            | Out-File (Join-Path $OutDir "gfxinfo.txt") -Encoding utf8
ADB "dumpsys gfxinfo $ProcName framestats" | Out-File (Join-Path $OutDir "framestats.txt") -Encoding utf8
Write-Host ""
Get-Content (Join-Path $OutDir "gfxinfo.txt") |
    Select-String "Total frames rendered|Janky frames|Hitch|50th|90th|95th|99th percentile" |
    ForEach-Object { Write-Host "    $($_.Line.Trim())" }

# 4. Pull data + reports
ADB "log -p i -t GLASSPROBE === capture:$Label end ===" | Out-Null
Start-Sleep 1
Stop-Process -Id $logcatJob.Id -Force -ErrorAction SilentlyContinue

if (-not $NoPerf) {
# binary-safe pull via python subprocess (PowerShell `>` re-encodes and corrupts .data;
# Start-Process -Wait + redirect hits a race after killing the logcat adb)
$dataFile = Join-Path $OutDir $dataName
& python -c "import subprocess,sys; d=subprocess.run([sys.argv[1],'-s',sys.argv[2],'exec-out','run-as',sys.argv[3],'cat',sys.argv[4]],capture_output=True).stdout; open(sys.argv[5],'wb').write(d); print(len(d),'bytes')" $Adb $Device $Pkg $dataName $dataFile

$spHost = "C:\Users\18978\AppData\Local\Android\Sdk\ndk\27.1.12297006\simpleperf\bin\windows\x86_64\simpleperf.exe"
& $spHost report -i (Join-Path $OutDir $dataName) -n -g        | Out-File (Join-Path $OutDir "report-callgraph.txt") -Encoding utf8
& $spHost report -i (Join-Path $OutDir $dataName) -n --children | Out-File (Join-Path $OutDir "report-flat.txt") -Encoding utf8
& $spHost report -i (Join-Path $OutDir $dataName) -n --sort tid | Out-File (Join-Path $OutDir "report-threads.txt") -Encoding utf8
$tnCmd = 'run-as ' + $Pkg + ' sh -c "for t in /proc/' + $appPid + '/task/*; do echo $(basename $t) $(cat $t/comm 2>/dev/null); done"'
ADB $tnCmd | Out-File (Join-Path $OutDir "thread-names.txt") -Encoding utf8
ADB "run-as $Pkg rm $dataName" | Out-Null
}

Write-Host ""
Write-Host "[OK] Done. Artifacts:" -ForegroundColor Green
Get-ChildItem $OutDir | ForEach-Object { Write-Host ("    {0,-24} {1,8:N0} B" -f $_.Name, $_.Length) }
Write-Host ""
Write-Host "Where to look:"
Write-Host "  report-flat.txt   - CPU hotspots (Path.combine / Skia / AGSL -> suspects 1 & 3)"
Write-Host "  framestats.txt    - per-frame DrawStart..GpuCompleted (CPU phase vs GPU phase)"
Write-Host "  logcat.txt        - GLASSPROBE markers + Skipped frames / Davey"
Write-Host "  $dataName - feed to NDK simpleperf for an HTML flamegraph"
