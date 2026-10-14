package com.royanic26.captchaaccessible

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.view.Display
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureAccessibilityService : AccessibilityService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingButton: Button? = null
    private val requestInProgress = AtomicBoolean(false)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
        }
        showFloatingButton()
    }


    private fun showFloatingButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || floatingButton != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val button = Button(this).apply {
            text = getString(R.string.take_screenshot_send)
            contentDescription = getString(R.string.take_screenshot_send)
            setOnClickListener { captureAndUpload() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        try {
            windowManager?.addView(button, params)
            floatingButton = button
        } catch (_: Exception) {
            announce(getString(R.string.overlay_unavailable))
        }
    }

    private fun captureAndUpload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            announce(getString(R.string.screenshot_android_version))
            return
        }
        if (!requestInProgress.compareAndSet(false, true)) {
            announce(getString(R.string.request_already_running))
            return
        }
        announce(getString(R.string.reading_code_wait))
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                screenshot.hardwareBuffer.close()
                if (bitmap == null) {
                    requestInProgress.set(false)
                    announce(getString(R.string.screenshot_failed))
                    return
                }
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                bitmap.recycle()
                if (copy == null) {
                    requestInProgress.set(false)
                    announce(getString(R.string.screenshot_failed))
                    return
                }
                worker.execute {
                    val result = try { uploadBitmap(copy) } catch (e: Exception) {
                        getString(R.string.upload_failed, e.localizedMessage ?: e.javaClass.simpleName)
                    } finally { copy.recycle() }
                    requestInProgress.set(false)
                    saveAndAnnounce(result)
                }
            }
            override fun onFailure(errorCode: Int) {
                requestInProgress.set(false)
                announce(getString(R.string.screenshot_failed_code, errorCode))
            }
        })
    }

    private fun uploadBitmap(bitmap: Bitmap): String {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val endpoint = prefs.getString("server_url", MainActivity.DEFAULT_SERVER_URL) ?: MainActivity.DEFAULT_SERVER_URL
        require(endpoint.startsWith("https://", ignoreCase = true)) { getString(R.string.https_required) }
        val imageBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
        val boundary = "----AccessibleScreen${UUID.randomUUID()}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Accept", "text/plain, application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        try {
            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"screen-capture.jpg\"\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
                output.write(imageBytes)
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().trim().take(1500)
            return if (code in 200..299) {
                val answer = extractServerAnswer(body)
                if (answer.isBlank()) getString(R.string.empty_server_response) else "${SUCCESS_PREFIX}$answer"
            } else {
                getString(R.string.server_error, code, body)
            }
        } finally { connection.disconnect() }
    }

    private fun saveAndAnnounce(message: String) {
        mainHandler.post {
            val userMessage = if (message.startsWith(SUCCESS_PREFIX)) {
                val answer = message.removePrefix(SUCCESS_PREFIX).trim()
                copyToClipboard(answer)
                val pasted = pasteIntoFocusedEditable()
                if (pasted) getString(R.string.code_received_pasted, answer)
                else getString(R.string.code_received_clipboard, answer)
            } else {
                message
            }
            getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                .putString("last_result", userMessage).apply()
            announce(userMessage)
            sendBroadcast(android.content.Intent(MainActivity.ACTION_SERVICE_RESULT)
                .setPackage(packageName).putExtra("result", userMessage))
        }
    }

    private fun extractServerAnswer(body: String): String {
        if (!body.startsWith("{") && !body.startsWith("[")) return body
        return try {
            val json = org.json.JSONObject(body)
            val keys = listOf("code", "answer", "result", "text", "message")
            keys.firstNotNullOfOrNull { key ->
                val value = json.opt(key)
                when (value) {
                    null, org.json.JSONObject.NULL -> null
                    is String -> value.takeIf { it.isNotBlank() && it != "null" }
                    is Number, is Boolean -> value.toString()
                    else -> null
                }
            } ?: body
        } catch (_: Exception) {
            body
        }
    }

    private fun copyToClipboard(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("captcha-code", value))
    }

    private fun pasteIntoFocusedEditable(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: findFocusedEditable(root)
                ?: return false
            if (!focused.isEditable || !focused.isEnabled) return false
            focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (_: Exception) {
            false
        }
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFocusedEditable(child)
            if (found != null) return found
        }
        return null
    }

    private fun announce(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            floatingButton?.announceForAccessibility(message)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val source = event.source
                if (source != null) {
                    try { source.refresh() } catch (_: Exception) { }
                    source.recycle()
                }
            }
        }
    }
    override fun onInterrupt() = Unit

    companion object {
        private const val SUCCESS_PREFIX = "__CAPTCHA_SUCCESS__:"
    }

    override fun onDestroy() {
        try { floatingButton?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        floatingButton = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
