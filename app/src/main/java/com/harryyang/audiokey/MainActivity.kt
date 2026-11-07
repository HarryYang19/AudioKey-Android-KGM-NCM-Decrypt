package com.harryyang.audiokey

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val preferences by lazy { getSharedPreferences("audio_key", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private val selectedFiles = mutableListOf<Uri>()
    private lateinit var fileList: LinearLayout
    private lateinit var outputFolderLabel: TextView
    private lateinit var statusLabel: TextView
    private lateinit var progress: ProgressBar
    private lateinit var addButton: MaterialButton
    private lateinit var folderButton: MaterialButton
    private lateinit var convertButton: MaterialButton
    private var isWorking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        buildUi()
        savedInstanceState?.getStringArrayList(STATE_SELECTED_URIS)?.forEach { selectedFiles.add(Uri.parse(it)) }
        updateOutputFolderLabel()
        renderFileList()
    }

    private fun buildUi() {
        val surface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        val onSurface = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val muted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, onSurface)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(surface)
            isFillViewport = true
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(28))
        }
        scroll.addView(column, ScrollView.LayoutParams(-1, -2))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, bars.bottom)
            insets
        }

        val brand = TextView(this).apply {
            text = "AUDIOKEY"
            textSize = 12f
            letterSpacing = 0.18f
            setTextColor(muted)
        }
        column.addView(brand)

        val title = TextView(this).apply {
            text = "离线音频解密"
            textSize = 30f
            setTextColor(onSurface)
            setPadding(0, dp(7), 0, dp(4))
        }
        column.addView(title)

        val subtitle = TextView(this).apply {
            text = "KGM / KGMA · NCM\n文件只在本机处理，不上传、不联网。"
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(muted)
        }
        column.addView(subtitle, marginParams(bottom = 20))

        val actionsCard = MaterialCardView(this).apply {
            radius = dp(22).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x22000000)
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer, surface))
        }
        val actionColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        actionsCard.addView(actionColumn)
        column.addView(actionsCard, marginParams(bottom = 16))

        addButton = MaterialButton(this).apply {
            text = "选择加密音频文件"
            isAllCaps = false
            textSize = 15f
            setOnClickListener { chooseFiles() }
        }
        actionColumn.addView(addButton, fullWidthParams())

        folderButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "选择输出文件夹"
            isAllCaps = false
            textSize = 15f
            setOnClickListener { chooseOutputFolder() }
        }
        actionColumn.addView(folderButton, marginParams(top = 10))

        outputFolderLabel = TextView(this).apply {
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(3), dp(9), dp(3), 0)
        }
        actionColumn.addView(outputFolderLabel)

        val listHeader = TextView(this).apply {
            text = "待处理文件"
            textSize = 18f
            setTextColor(onSurface)
        }
        column.addView(listHeader, marginParams(top = 8, bottom = 10))

        val listCard = MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = 0f
            strokeWidth = dp(1)
            strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x22000000)
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainerLow, surface))
        }
        fileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        listCard.addView(fileList)
        column.addView(listCard, marginParams(bottom = 16))

        convertButton = MaterialButton(this).apply {
            text = "开始本地解密"
            isAllCaps = false
            textSize = 16f
            setOnClickListener { startConversion() }
        }
        column.addView(convertButton, fullWidthParams())

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            visibility = View.GONE
            isIndeterminate = true
        }
        column.addView(progress, marginParams(top = 12))

        statusLabel = TextView(this).apply {
            text = "请选择文件和输出文件夹。"
            textSize = 13f
            setTextColor(muted)
            setPadding(dp(2), dp(10), dp(2), 0)
        }
        column.addView(statusLabel)

        val footnote = TextView(this).apply {
            text = "当前支持经典 KGM / KGMA 与常见 NCM 文件。新版依赖外部密钥的变体不在此版本范围内。"
            textSize = 11f
            setTextColor(muted)
            setPadding(dp(2), dp(24), dp(2), 0)
        }
        column.addView(footnote)

        setContentView(scroll)
    }

    private fun chooseFiles() {
        if (isWorking) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/octet-stream"))
        }
        startActivityForResult(intent, REQUEST_FILES)
    }

    private fun chooseOutputFolder() {
        if (isWorking) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_FOLDER)
    }

    @Deprecated("Activity result API kept minimal for Android 9 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_FILES -> {
                val clip = data.clipData
                val grantFlags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        val uri = clip.getItemAt(i).uri
                        persistReadPermission(uri, grantFlags)
                        if (!selectedFiles.contains(uri)) selectedFiles.add(uri)
                    }
                } else {
                    data.data?.let {
                        persistReadPermission(it, grantFlags)
                        if (!selectedFiles.contains(it)) selectedFiles.add(it)
                    }
                }
                renderFileList()
            }
            REQUEST_FOLDER -> {
                data.data?.let { uri ->
                    val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    try {
                        contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (_: SecurityException) {
                        // Some document providers grant transient permissions only.
                    }
                    preferences.edit().putString(KEY_OUTPUT_TREE, uri.toString()).apply()
                    updateOutputFolderLabel()
                }
            }
        }
    }

    private fun renderFileList() {
        fileList.removeAllViews()
        val muted = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
        if (selectedFiles.isEmpty()) {
            fileList.addView(TextView(this).apply {
                text = "尚未添加文件"
                textSize = 14f
                setTextColor(muted)
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }
        selectedFiles.forEachIndexed { index, uri ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val name = TextView(this).apply {
                text = getDisplayName(uri)
                textSize = 13f
                setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK))
                maxLines = 2
            }
            line.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            val remove = TextView(this).apply {
                text = "移除"
                textSize = 12f
                setTextColor(muted)
                setPadding(dp(12), dp(8), dp(4), dp(8))
                setOnClickListener {
                    if (!isWorking) {
                        selectedFiles.removeAt(index)
                        renderFileList()
                    }
                }
            }
            line.addView(remove)
            fileList.addView(line)
            if (index < selectedFiles.lastIndex) {
                val divider = View(this).apply {
                    setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant, 0x22000000))
                }
                fileList.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
            }
        }
    }

    private fun persistReadPermission(uri: Uri, flags: Int) {
        try {
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Providers that do not expose persistable grants remain usable for this session.
        }
    }

    private fun updateOutputFolderLabel() {
        val uriText = preferences.getString(KEY_OUTPUT_TREE, null)
        outputFolderLabel.text = if (uriText == null) {
            "尚未选择保存位置"
        } else {
            val tree = DocumentFile.fromTreeUri(this, Uri.parse(uriText))
            "保存到：${tree?.name ?: "已选择文件夹"}"
        }
    }

    private fun startConversion() {
        if (isWorking) return
        if (selectedFiles.isEmpty()) {
            Toast.makeText(this, "请先添加 KGM 或 NCM 文件", Toast.LENGTH_SHORT).show()
            return
        }
        val treeString = preferences.getString(KEY_OUTPUT_TREE, null)
        if (treeString.isNullOrBlank()) {
            Toast.makeText(this, "请先选择输出文件夹", Toast.LENGTH_SHORT).show()
            return
        }
        val treeUri = Uri.parse(treeString)
        val tree = DocumentFile.fromTreeUri(this, treeUri)
        if (tree == null || !tree.canWrite()) {
            Toast.makeText(this, "输出文件夹不可写，请重新选择", Toast.LENGTH_LONG).show()
            return
        }

        isWorking = true
        setBusy(true)
        val jobs = selectedFiles.toList()
        worker.execute {
            var success = 0
            var failed = 0
            val logLines = mutableListOf<String>()
            jobs.forEachIndexed { index, uri ->
                val displayName = try { getDisplayName(uri) } catch (_: Exception) { "selected-${index + 1}.audio" }
                runOnUiThread { statusLabel.text = "正在处理 ${index + 1}/${jobs.size}：$displayName" }
                val tempInput = File(cacheDir, "input_${System.nanoTime()}.tmp")
                val tempOutput = File(cacheDir, "output_${System.nanoTime()}.tmp")
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("无法读取所选文件")
                    inputStream.use { input ->
                        FileOutputStream(tempInput).use { output -> input.copyTo(output, 64 * 1024) }
                    }
                    val lower = displayName.lowercase()
                    val extension = when {
                        lower.endsWith(".ncm") -> NcmDecoder.decode(tempInput, tempOutput)
                        lower.endsWith(".kgm") || lower.endsWith(".kgma") -> KgmDecoder.decode(tempInput, tempOutput)
                        else -> detectDecoder(tempInput).let { decoder -> decoder(tempInput, tempOutput) }
                    }
                    require(tempOutput.isFile && tempOutput.length() > 0) { "输出音频为空" }
                    val base = displayName.substringBeforeLast('.', displayName).ifBlank { "audio" }
                    val outputName = uniqueName(tree, safeFileName(base), extension)
                    val mime = mimeFor(extension)
                    val destination = tree.createFile(mime, outputName) ?: throw IllegalStateException("无法创建输出文件")
                    try {
                        val outputStream = contentResolver.openOutputStream(destination.uri, "w")
                            ?: throw IllegalStateException("无法写入输出文件")
                        outputStream.use { output ->
                            FileInputStream(tempOutput).use { input -> input.copyTo(output, 64 * 1024) }
                        }
                    } catch (error: Exception) {
                        destination.delete()
                        throw error
                    }
                    success++
                    logLines.add("✓ $outputName")
                } catch (error: Exception) {
                    failed++
                    logLines.add("✕ $displayName：${error.message ?: "处理失败"}")
                } finally {
                    tempInput.delete()
                    tempOutput.delete()
                }
            }
            runOnUiThread {
                selectedFiles.clear()
                renderFileList()
                isWorking = false
                setBusy(false)
                statusLabel.text = "完成：成功 $success 个，失败 $failed 个\n" + logLines.take(8).joinToString("\n")
            }
        }
    }

    private fun detectDecoder(input: File): (File, File) -> String {
        FileInputStream(input).use { stream ->
            val header = ByteArray(16)
            val count = stream.read(header)
            if (count >= 8 && header.copyOfRange(0, 8).contentEquals("CTENFDAM".toByteArray())) {
                return NcmDecoder::decode
            }
            if (count >= 16) {
                val kgm = byteArrayOf(0x7C, 0xD5.toByte(), 0x32, 0xEB.toByte(), 0x86.toByte(), 0x02, 0x7F, 0x4B, 0xA8.toByte(), 0xAF.toByte(), 0xA6.toByte(), 0x8E.toByte(), 0x0F, 0xFF.toByte(), 0x99.toByte(), 0x14)
                if (header.copyOfRange(0, 16).contentEquals(kgm)) return KgmDecoder::decode
            }
        }
        throw IllegalArgumentException("无法识别文件格式，仅支持 NCM / KGM / KGMA")
    }

    private fun uniqueName(tree: DocumentFile, base: String, extension: String): String {
        var candidate = "$base.$extension"
        var suffix = 1
        while (tree.findFile(candidate) != null) {
            candidate = "$base ($suffix).$extension"
            suffix++
        }
        return candidate
    }

    private fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return cleaned.take(120).ifBlank { "audio" }
    }

    private fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "m4a" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        else -> "application/octet-stream"
    }

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "unknown.audio"
    }

    private fun setBusy(busy: Boolean) {
        addButton.isEnabled = !busy
        folderButton.isEnabled = !busy
        convertButton.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
        convertButton.text = if (busy) "正在解密…" else "开始本地解密"
    }

    private fun fullWidthParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, dp(52))
    private fun marginParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_SELECTED_URIS, ArrayList(selectedFiles.map { it.toString() }))
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdown()
    }

    companion object {
        private const val REQUEST_FILES = 1001
        private const val REQUEST_FOLDER = 1002
        private const val KEY_OUTPUT_TREE = "output_tree_uri"
        private const val STATE_SELECTED_URIS = "selected_uris"
    }
}
