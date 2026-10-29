package com.ljyh.mei.utils.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把日志落到 `files/app_logs/log-yyyy-MM-dd.txt`，供"关于 → 日志收集"导出。
 *
 * 原来每条日志都 `scope.launch { 新建 SimpleDateFormat / open FileWriter / 写 / close }`，
 * 播放器与网络日志一多就是持续的磁盘 IO，而且每条日志都要建一个协程和一个格式器。
 * 现在改成：
 *  - 有界队列 + 单个写线程：日志风暴时丢最旧的，不会堆积协程；
 *  - 复用日期格式器（只在写线程里用，天然线程安全）；
 *  - 单日文件超过上限就停止写入，启动时顺带清理过期文件。
 */
class FileLoggingTree(
    context: Context,
    /** 低于该等级的日志不落盘。默认 DEBUG，与之前行为一致。 */
    private val minPriority: Int = Log.DEBUG,
) : Timber.Tree() {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Entry>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** 当前正在写的文件与已写字节数，只在写线程里访问。 */
    private var currentDay: String? = null
    private var currentFile: File? = null
    private var currentBytes = 0L
    private var pruned = false

    private data class Entry(
        val priority: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable?,
    )

    init {
        scope.launch {
            for (entry in queue) write(entry)
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < minPriority) return
        queue.trySend(Entry(priority, tag, message, t))
    }

    private fun write(entry: Entry) {
        if (!pruned) {
            pruned = true
            pruneOldLogs()
        }
        try {
            val file = openFor(fileFormat.format(Date())) ?: return
            val line = buildString {
                append(timeFormat.format(Date())).append(' ')
                append(levelOf(entry.priority)).append('/').append(entry.tag).append(": ")
                append(entry.message).append('\n')
                if (entry.throwable != null) append(Log.getStackTraceString(entry.throwable))
            }
            FileWriter(file, true).use { it.append(line) }
            currentBytes += line.length
        } catch (e: Exception) {
            // 写日志失败绝不能影响主流程
            Log.e("FileLoggingTree", "Error writing log to file", e)
        }
    }

    /** 切换到当天的文件；超过单日上限则不再落盘。 */
    private fun openFor(day: String): File? {
        if (day == currentDay) {
            return if (currentBytes < MAX_BYTES_PER_FILE) currentFile else null
        }
        val dir = File(appContext.filesDir, LOG_DIR).apply { if (!exists()) mkdirs() }
        val file = File(dir, "log-$day.txt")
        currentDay = day
        currentFile = file
        currentBytes = file.length()
        return if (currentBytes < MAX_BYTES_PER_FILE) file else null
    }

    private fun pruneOldLogs() {
        try {
            val dir = File(appContext.filesDir, LOG_DIR)
            if (!dir.exists()) return
            val cutoff = System.currentTimeMillis() - RETAIN_DAYS * 24L * 60 * 60 * 1000
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        } catch (_: Exception) {
            // 清理失败无所谓
        }
    }

    private fun levelOf(priority: Int): String = when (priority) {
        Log.DEBUG -> "D"
        Log.INFO -> "I"
        Log.WARN -> "W"
        Log.ERROR -> "E"
        Log.ASSERT -> "A"
        else -> "V"
    }

    private companion object {
        const val LOG_DIR = "app_logs"
        const val QUEUE_CAPACITY = 256
        const val MAX_BYTES_PER_FILE = 8L * 1024 * 1024
        const val RETAIN_DAYS = 7L
    }
}
