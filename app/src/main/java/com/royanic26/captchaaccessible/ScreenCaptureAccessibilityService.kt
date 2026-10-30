package com.royanic26.captchaaccessible

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
    private var a11yButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var lastScreenScanTime = 0L

    @Volatile
    private var lastKnownEditableNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        createFloatingButton()
        registerShortcutCallback()
        updateFloatingButtonVisibility()
    }

    private fun registerShortcutCallback() {
        try {
            val controller = accessibilityButtonController ?: return
            val callback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController?) {
                    captureAndUpload()
                }
            }
            controller.registerAccessibilityButtonCallback(callback, mainHandler)
            a11yButtonCallback = callback
        } catch (_: Exception) { }
    }

    private fun createFloatingButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || floatingButton != null) return
        if (!getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean("service_active", true)) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val button = Button(this).apply {
            text = "خواندن کپچا"
            contentDescription = "دکمه شناور خواندن کپچا و تصویر امنیتی"
            visibility = View.GONE
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
        } catch (_: Exception) { }
    }

    private fun updateFloatingButtonVisibility() {
        val btn = floatingButton ?: return
        val isActive = getSharedPreferences("app_settings", Context.MODE_PRIVATE).getBoolean("service_active", true)
        val hasCaptcha = isActive && isCaptchaVisibleOnScreen()
        mainHandler.post {
            btn.visibility = if (hasCaptcha) View.VISIBLE else View.GONE
        }
    }

    private fun isCaptchaVisibleOnScreen(): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg == packageName || pkg.contains("launcher", ignoreCase = true) || pkg == "com.android.systemui") {
                return false
            }
            containsCaptchaNode(root, 0)
        } catch (_: Exception) {
            false
        }
    }

    private fun containsCaptchaNode(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 14) return false
        if (node.isVisibleToUser) {
            val combined = "${node.text ?: ""} ${node.hintText ?: ""} ${node.contentDescription ?: ""} ${node.viewIdResourceName ?: ""}".lowercase()
            if (CAPTCHA_INDICATORS.any { combined.contains(it) }) {
                return true
            }
        }
        val count = node.childCount
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            if (containsCaptchaNode(child, depth + 1)) return true
        }
        return false
    }

    private fun captureAndUpload() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("service_active", true)) {
            announce("سرویس خواندن کپچا موقتاً غیرفعال است.")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            announce("گرفتن تصویر نیازمند اندروید ۱۱ یا بالاتر است.")
            return
        }

        val mobile = prefs.getString("auth_mobile", "")?.trim().orEmpty()
        val token = prefs.getString("session_token", "")?.trim().orEmpty()
        val deviceId = prefs.getString("device_id", "") ?: ""

        if (mobile.isEmpty() || token.isEmpty()) {
            announce("ابتدا وارد حساب کاربری خود در برنامه شوید.")
            return
        }

        if (!requestInProgress.compareAndSet(false, true)) {
            announce("درخواست قبلی در حال پردازش است...")
            return
        }

        cacheCurrentFocusedEditable()

        val wasVisible = floatingButton?.visibility == View.VISIBLE
        if (wasVisible) floatingButton?.visibility = View.INVISIBLE

        announce("لطفاً چند ثانیه صبر کنید...")
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                if (wasVisible) floatingButton?.visibility = View.VISIBLE
                val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
                screenshot.hardwareBuffer.close()
                if (bitmap == null) {
                    requestInProgress.set(false)
                    announce("خطا در ایجاد تصویر صفحه.")
                    return
                }
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                bitmap.recycle()
                if (copy == null) {
                    requestInProgress.set(false)
                    announce("خطا در آماده‌سازی تصویر.")
                    return
                }
                worker.execute {
                    val result = try {
                        uploadToEndpoint(copy, MainActivity.DEFAULT_SERVER_URL, mobile, token, deviceId)
                    } catch (e: Exception) {
                        "خطا در ارسال تصویر: ${e.localizedMessage ?: e.javaClass.simpleName}"
                    } finally {
                        copy.recycle()
                    }
                    requestInProgress.set(false)
                    saveAndAnnounce(result)
                }
            }

            override fun onFailure(errorCode: Int) {
                if (wasVisible) floatingButton?.visibility = View.VISIBLE
                requestInProgress.set(false)
                announce("خطا در تصویربرداری: کد $errorCode")
            }
        })
    }

    private fun uploadToEndpoint(bitmap: Bitmap, endpoint: String, mobile: String, token: String, deviceId: String): String {
        val imageBytes = ByteArrayOutputStream().use { stream ->
            val maxDim = 1000
            val w = bitmap.width
            val h = bitmap.height
            val scaled = if (w > maxDim || h > maxDim) {
                val r = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                Bitmap.createScaledBitmap(bitmap, (w * r).toInt(), (h * r).toInt(), true)
            } else bitmap
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            stream.toByteArray()
        }

        val authHeader = Base64.encodeToString("$mobile:$token".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val boundary = "----AvayarScreen${UUID.randomUUID()}"

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Authorization", "Basic $authHeader")
            setRequestProperty("X-Device-ID", deviceId)
            setRequestProperty("Accept", "text/plain, application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AvayarAccessibility/5.0")
        }

        try {
            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"screen.jpg\"\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
                output.write(imageBytes)
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().trim().take(1500)

            return if (code in 200..299) {
                val answer = extractServerAnswer(body)
                if (answer.isBlank()) "پاسخی از سرور دریافت نشد." else "${SUCCESS_PREFIX}$answer"
            } else if (code == 401) {
                "نشست شما منقضی شده است. لطفاً وارد برنامه شوید."
            } else if (code == 403) {
                body.ifBlank { "اعتبار شما به پایان رسیده است. لطفاً حساب خود را شارژ کنید." }
            } else {
                "خطای سرور: کد $code"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun saveAndAnnounce(message: String) {
        mainHandler.post {
            val userMessage = if (message.startsWith(SUCCESS_PREFIX)) {
                val answer = message.removePrefix(SUCCESS_PREFIX).trim()
                copyToClipboard(answer)
                val pasted = insertTextIntoTargetField(answer)
                if (pasted) "کد دریافت و وارد شد: $answer"
                else "کد دریافت و کپی شد: $answer"
            } else {
                message
            }
            getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit()
                .putString("last_result", userMessage).apply()
            announce(userMessage)
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

    private fun isNodeEditable(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isEnabled) return false
        if (node.isEditable) return true
        val cls = node.className?.toString().orEmpty()
        if (cls.contains("EditText", ignoreCase = true) || cls.contains("AutoCompleteTextView", ignoreCase = true)) {
            return true
        }
        return node.actionList.any {
            it.id == AccessibilityNodeInfo.ACTION_SET_TEXT || it.id == AccessibilityNodeInfo.ACTION_PASTE
        }
    }

    private fun cacheCurrentFocusedEditable() {
        try {
            val root = rootInActiveWindow ?: return
            val inputFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (isNodeEditable(inputFocused)) {
                lastKnownEditableNode = inputFocused
                return
            }
            val a11yFocused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            if (isNodeEditable(a11yFocused)) {
                lastKnownEditableNode = a11yFocused
            }
        } catch (_: Exception) { }
    }

    private fun insertTextIntoTargetField(codeText: String): Boolean {
        return try {
            val candidates = mutableListOf<AccessibilityNodeInfo>()

            rootInActiveWindow?.let { root ->
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let { if (isNodeEditable(it)) candidates.add(it) }
                root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.let { if (isNodeEditable(it)) candidates.add(it) }
            }

            lastKnownEditableNode?.let { cached ->
                try {
                    cached.refresh()
                    if (isNodeEditable(cached)) candidates.add(cached)
                } catch (_: Exception) { }
            }

            val allEditables = mutableListOf<AccessibilityNodeInfo>()
            val windowRoots = mutableListOf<AccessibilityNodeInfo>()
            rootInActiveWindow?.let { windowRoots.add(it) }
            try {
                windows?.forEach { win ->
                    if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION || win.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        win.root?.let { r -> windowRoots.add(r) }
                    }
                }
            } catch (_: Exception) { }

            for (r in windowRoots) {
                collectEditableNodes(r, allEditables)
            }

            val bestMatches = allEditables.sortedByDescending { node ->
                val meta = "${node.hintText ?: ""} ${node.text ?: ""} ${node.contentDescription ?: ""} ${node.viewIdResourceName ?: ""}".lowercase()
                var score = 0
                if (node.isFocused || node.isAccessibilityFocused) score += 100
                if (CAPTCHA_INDICATORS.any { meta.contains(it) }) score += 50
                if (!node.isPassword) score += 20
                if (node.text.isNullOrBlank()) score += 10
                score
            }

            candidates.addAll(bestMatches)

            for (target in candidates) {
                if (tryFillNode(target, codeText)) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun tryFillNode(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            node.refresh()
            if (!node.isEnabled) return false

            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return true
            }

            if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                return true
            }

            false
        } catch (_: Exception) {
            false
        }
    }

    private fun collectEditableNodes(node: AccessibilityNodeInfo?, list: MutableList<AccessibilityNodeInfo>) {
        if (node == null) return
        if (isNodeEditable(node) && node.isVisibleToUser) {
            list.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
            collectEditableNodes(child, list)
        }
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
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                val source = event.source
                if (source != null) {
                    try {
                        if (isNodeEditable(source)) {
                            lastKnownEditableNode = source
                        }
                    } catch (_: Exception) { }
                }
            }
        }

        val now = System.currentTimeMillis()
        if (now - lastScreenScanTime > 800) {
            lastScreenScanTime = now
            updateFloatingButtonVisibility()
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val SUCCESS_PREFIX = "__CAPTCHA_SUCCESS__:"
        private val CAPTCHA_INDICATORS = listOf(
            "کد امنیتی", "تصویر امنیتی", "کپچا", "عبارت امنیتی", "کد تصویر", "کد روبرو", "کد مقابل",
            "captcha", "security code", "verification code", "verify code", "enter the code"
        )
    }

    override fun onDestroy() {
        try {
            a11yButtonCallback?.let { accessibilityButtonController?.unregisterAccessibilityButtonCallback(it) }
        } catch (_: Exception) { }
        try { floatingButton?.let { windowManager?.removeView(it) } } catch (_: Exception) { }
        floatingButton = null
        worker.shutdownNow()
        super.onDestroy()
    }
}