package com.royanic26.captchaaccessible

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
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
import android.widget.Button
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class ScreenCaptureAccessibilityService : AccessibilityService() {
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var floatingButton: Button? = null

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
        announce(getString(R.string.screenshot_in_progress))
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                screenshot.hardwareBuffer.close()
                if (bitmap == null) {
                    announce(getString(R.string.screenshot_failed))
                    return
                }
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                bitmap.recycle()
                if (copy == null) {
                    announce(getString(R.string.screenshot_failed))
                    return
                }
                worker.execute {
                    val result = try { uploadBitmap(copy) } catch (e: Exception) {
                        getString(R.string.upload_failed, e.localizedMessage ?: e.javaClass.simpleName)
                    } finally { copy.recycle() }
                    saveAndAnnounce(result)
                }
            }
            override fun onFailure(errorCode: Int) {
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
            setRequestProperty("Accept", "application/json")
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
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().take(1500)
            return if (code in 200..299) getString(R.string.upload_success, code, body)
                else getString(R.string.server_error, code, body)
        } finally { connection.disconnect() }
    }

    private fun saveAndAnnounce(message: String) {
        getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().putString("last_result", message).apply()
        mainHandler.post {
            announce(message)
            sendBroadcast(android.content.Intent(MainActivity.ACTION_SERVICE_RESULT).setPackage(packageName).putExtra("result", message))
        }
    }

    private fun announce(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            floatingButton?.announceForAccessibility(message)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try { floatingButton?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        floatingButton = null
        worker.shutdownNow()
        super.onDestroy()
    }
}
