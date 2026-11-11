package com.ljyh.mei.utils

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import timber.log.Timber
import kotlin.math.max

/**
 * 形变动画掉帧定位探针。
 *
 * 玻璃形变每帧都在改捕获尺寸，所以每帧都会重建材质滤镜；但"掉帧"也可能只是 UI 线程
 * （measure / draw 录制）、Choreographer 动画回调，或者纯 GPU 填充。光靠读代码分不清，
 * 而 [FrameMetrics] 能把一帧拆成这几段，直接看哪一段超了。
 *
 * 每 1 秒汇总一行；只有真的有一帧超出预算时才输出（静止时完全不产生日志）。日志走 Timber，
 * 因此会落到 `files/app_logs`，可在 设置 → 日志 里导出。
 *
 * 读法：
 *  - `anim` 高 → `withFrameNanos` 驱动的那段（弹簧推进 + 快照 apply）本身重；
 *  - `measure` 高 → 重新布局（组合里的状态读错阶段会变成每帧布局）；
 *  - `draw` 高 → 绘制命令录制，也就是玻璃那条滤镜链在 UI 线程上重建；
 *  - `rt` 高 → RenderThread（同步 RenderNode 属性、提交）；
 *  - `gpu` 高 → 真正的像素填充（离屏滤镜链、模糊）。
 *
 * `slowest` 是最慢那一帧的分段耗时，用来判断某一次尖峰是布局、录制还是渲染线程。
 */
internal object FrameStats {

    private const val TAG = "MeiloPerf"

    private var thread: HandlerThread? = null
    private var attachedWindow: Window? = null

    /** 关键 Composable 每次重组都会 +1，用来判断是不是整棵树在每帧重组。 */
    private val recompositions = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    fun countRecomposition(tag: String = "nav") {
        recompositions.getOrPut(tag) { java.util.concurrent.atomic.AtomicInteger() }.incrementAndGet()
    }

    /** 给一段时间打标签（例：sheet 展开动画），日志行会带上它，避免再靠时间戳猜用户做了什么。 */
    @Volatile
    private var scene: String? = null

    /** 一秒窗口内出现过、但结束前又退出的场景（300ms 的过渡会被 1 秒窗口整个吞掉）。 */
    @Volatile
    private var recentScene: String? = null

    fun enterScene(label: String) {
        scene = label
        recentScene = label
    }

    fun exitScene() {
        scene = null
    }

    fun observe(window: Window) {
        // 性能探针只在 debug 包里跑；release 不产生任何 MeiloPerf 日志。
        if (!com.ljyh.mei.BuildConfig.DEBUG) return
        // addOnFrameMetricsAvailableListener 会立刻开始回调；重复注册会让每一帧被计两次，
        // 于是出现 "frames=240/120" 这种不可能的帧数，掉帧数也跟着翻倍。
        if (attachedWindow === window) return
        attachedWindow = window
        val looper = thread ?: HandlerThread("MeiloFrameStats").apply { start() }.also { thread = it }
        val refreshRate = (window.context.display?.refreshRate ?: 60f).coerceIn(30f, 165f)
        val periodNanos = 1_000_000_000.0 / refreshRate
        window.addOnFrameMetricsAvailableListener(WindowStats(periodNanos), Handler(looper.looper))
    }

    private class WindowStats(private val periodNanos: Double) :
        Window.OnFrameMetricsAvailableListener {

        private var windowStart = System.nanoTime()

        private var frames = 0
        private var jank = 0

        private var animSum = 0L
        private var animMax = 0L
        private var measureSum = 0L
        private var measureMax = 0L
        private var drawSum = 0L
        private var drawMax = 0L
        private var rtSum = 0L
        private var rtMax = 0L
        private var gpuSum = 0L
        private var gpuMax = 0L
        private var gpuSamples = 0
        private var totalMax = 0L
        private var slowest: Frame? = null

        private class Frame(
            val total: Long,
            val anim: Long,
            val measure: Long,
            val draw: Long,
            val rt: Long,
            val gpu: Long,
        )

        override fun onFrameMetricsAvailable(
            window: Window,
            metrics: FrameMetrics,
            dropCountSinceLastInvocation: Int,
        ) {
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (total <= 0L) return

            val anim = metrics.getMetric(FrameMetrics.ANIMATION_DURATION).atLeastZero()
            val measure = metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION).atLeastZero()
            val draw = metrics.getMetric(FrameMetrics.DRAW_DURATION).atLeastZero()
            val rt = metrics.getMetric(FrameMetrics.SYNC_DURATION).atLeastZero() +
                metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION).atLeastZero()
            val gpu = metrics.getMetric(FrameMetrics.GPU_DURATION)

            frames++
            totalMax = max(totalMax, total)
            // 1.5 个刷新周期才算真掉帧：120Hz 屏上一帧 10ms 也会"超过周期"，
            // 但用户几乎看不出来，按 1.2 倍统计会把正常帧也算成卡顿。
            if (total > periodNanos * 1.5) jank++
            if (slowest == null || total > slowest!!.total) {
                slowest = Frame(total, anim, measure, draw, rt, gpu)
            }

            animSum += anim
            animMax = max(animMax, anim)
            measureSum += measure
            measureMax = max(measureMax, measure)
            drawSum += draw
            drawMax = max(drawMax, draw)
            rtSum += rt
            rtMax = max(rtMax, rt)
            if (gpu >= 0L) {
                gpuSum += gpu
                gpuMax = max(gpuMax, gpu)
                gpuSamples++
            }

            val now = System.nanoTime()
            if (now - windowStart < 1_000_000_000L) return
            flush(now)
        }

        private fun flush(now: Long) {
            val elapsed = (now - windowStart) / 1e9
            val recomposed = recompositions.entries
                .joinToString(",") { (tag, counter) -> "$tag:${counter.getAndSet(0)}" }
            if (frames > 0 && jank > 2 && totalMax > 24_000_000L) {
                val n = frames.toDouble()
                val peak = slowest
                val label = scene ?: recentScene
                Timber.tag(TAG).i(
                    "%s%.1fs fps=%d jank=%d worst=%.1fms recomp=%s " +
                        "anim=%.1f/%.1f measure=%.1f/%.1f draw=%.1f/%.1f rt=%.1f/%.1f gpu=%s slowest=[a=%.1f m=%.1f d=%.1f r=%.1f g=%s]",
                    label?.let { "[$it] " } ?: "",
                    elapsed,
                    (frames / elapsed).toInt(),
                    jank,
                    totalMax / 1e6,
                    recomposed,
                    animSum / n / 1e6,
                    animMax / 1e6,
                    measureSum / n / 1e6,
                    measureMax / 1e6,
                    drawSum / n / 1e6,
                    drawMax / 1e6,
                    rtSum / n / 1e6,
                    rtMax / 1e6,
                    if (gpuSamples > 0) "%.1f/%.1f".format(gpuSum / gpuSamples / 1e6, gpuMax / 1e6)
                    else "n/a",
                    (peak?.anim ?: 0L) / 1e6,
                    (peak?.measure ?: 0L) / 1e6,
                    (peak?.draw ?: 0L) / 1e6,
                    (peak?.rt ?: 0L) / 1e6,
                    if (peak != null && peak.gpu >= 0L) "%.1f".format(peak.gpu / 1e6) else "n/a",
                )
            }
            frames = 0
            jank = 0
            recentScene = null
            animSum = 0
            animMax = 0
            measureSum = 0
            measureMax = 0
            drawSum = 0
            drawMax = 0
            rtSum = 0
            rtMax = 0
            gpuSum = 0
            gpuMax = 0
            gpuSamples = 0
            totalMax = 0
            slowest = null
            windowStart = now
        }

        private fun Long.atLeastZero(): Long = if (this > 0L) this else 0L
    }
}
