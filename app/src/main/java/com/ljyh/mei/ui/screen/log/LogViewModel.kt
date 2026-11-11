package com.ljyh.mei.ui.screen.log

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject



@HiltViewModel
class LogViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    // 日志文件列表
    private val _logFiles = MutableStateFlow<List<File>>(emptyList())
    val logFiles = _logFiles.asStateFlow()

    // 当前查看的文件内容
    private val _currentFileContent = MutableStateFlow<String?>(null)
    val currentFileContent = _currentFileContent.asStateFlow()

    // 当前选中的文件对象（用于分享）
    private var currentSelectedFile: File? = null

    init {
        loadLogFiles()
    }

    /**
     * 加载日志文件列表
     */
    fun loadLogFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val logDir = File(context.filesDir, "app_logs")
            val crashDir = File(context.filesDir, "crash_logs")

            val files = mutableListOf<File>()
            // 安全检查文件夹是否存在
            if (logDir.exists()) files.addAll(logDir.listFiles() ?: emptyArray())
            if (crashDir.exists()) files.addAll(crashDir.listFiles() ?: emptyArray())

            // 按最后修改时间降序排列（最新的在最上面）
            files.sortByDescending { it.lastModified() }

            _logFiles.value = files
        }
    }

    /**
     * 读取文件内容
     */
    fun readContent(file: File) {
        currentSelectedFile = file // 记录当前选中的文件
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _currentFileContent.value = readTail(file, MAX_PREVIEW_BYTES)
            } catch (e: Exception) {
                _currentFileContent.value = "读取文件失败: ${e.message}"
            }
        }
    }

    /**
     * 单日日志上限 8MB，整份读进内存再整体塞进 Text 会直接卡住主线程。
     * 日志排查看的都是尾部，这里只取最后一段。
     */
    private fun readTail(file: File, maxBytes: Int): String {
        val length = file.length()
        if (length <= maxBytes) return file.readText()
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(length - maxBytes)
            val bytes = ByteArray(maxBytes)
            raf.readFully(bytes)
            val omitted = (length - maxBytes) / 1024
            return "……（文件共 ${length / 1024} KB，此处省略前 $omitted KB）\n\n" +
                String(bytes, Charsets.UTF_8)
        }
    }

    /**
     * 关闭详情页
     */
    fun closeContent() {
        _currentFileContent.value = null
        currentSelectedFile = null
    }

    /**
     * 分享当前打开的日志文件
     */
    fun shareCurrentFile() {
        val context = getApplication<Application>()
        val file = currentSelectedFile
        if (file == null || !file.exists()) {
            Toast.makeText(context, "没有可分享的日志", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // 获取 FileProvider URI
            // 注意：authority 必须与 AndroidManifest.xml 中的 provider authorities 一致
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                // 赋予临时读权限
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "分享日志给开发者")
            // 在非 Activity 环境启动 Activity 需要这个 Flag
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            // 以前这里只 printStackTrace，用户看到的就是"点了没反应"。
            Timber.tag("LogShare").e(e, "分享日志失败")
            Toast.makeText(context, "分享失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 清空所有日志
     */
    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _logFiles.value.forEach {
                try { it.delete() } catch (e: Exception) { e.printStackTrace() }
            }
            loadLogFiles() // 刷新列表
        }
    }

    private companion object {
        /** 详情页最多展示的字节数：单日日志上限 8MB，全量塞进 Text 会直接卡死。 */
        const val MAX_PREVIEW_BYTES = 512 * 1024
    }
}