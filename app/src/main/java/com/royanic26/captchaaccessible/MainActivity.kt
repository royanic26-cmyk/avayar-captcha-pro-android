package com.royanic26.captchaaccessible

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var preferences: SharedPreferences
    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var mobileInput: EditText
    private lateinit var otpInput: EditText
    private lateinit var sendOtpBtn: Button
    private lateinit var verifyOtpBtn: Button
    private lateinit var otpContainer: LinearLayout
    private lateinit var loginGroup: LinearLayout

    private lateinit var dashboardGroup: LinearLayout
    private lateinit var userMobileLabel: TextView
    private lateinit var creditsLabel: TextView
    private lateinit var usageCountLabel: TextView
    private lateinit var expiryLabel: TextView
    private lateinit var buyPlanBtn: Button
    private lateinit var logoutButton: Button

    private lateinit var toggleServiceBtn: Button
    private lateinit var resultStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "آوایار کپچاپرو"

        preferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        preferences.edit().putString("server_url", DEFAULT_SERVER_URL).apply()

        var deviceId = preferences.getString("device_id", "") ?: ""
        if (deviceId.isBlank()) {
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            deviceId = if (!androidId.isNullOrBlank()) androidId else "and_" + System.currentTimeMillis()
            preferences.edit().putString("device_id", deviceId).apply()
        }

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        fun label(text: String, size: Float = 16f, bold: Boolean = false, color: Int = 0xFF202124.toInt()): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                setPadding(0, dp(6), 0, dp(4))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                contentDescription = text
            }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        content.addView(label("آوایار کپچاپرو", 22f, true, 0xFF0284C7.toInt()))

        // بخش ورود و ثبت‌نام پیامکی
        loginGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }

        loginGroup.addView(label("ورود یا ثبت‌نام با شماره موبایل (سهمیه تست رایگان):", 16f, true))
        loginGroup.addView(label("شماره موبایل ۱۱ رقمی خود را وارد نمایید:", 14f))

        mobileInput = EditText(this).apply {
            hint = "مثال: 09123456789"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_PHONE
            setText(preferences.getString("auth_mobile", "") ?: "")
            contentDescription = "کادر ورود شماره موبایل ۱۱ رقمی"
        }
        loginGroup.addView(mobileInput)

        sendOtpBtn = Button(this).apply {
            text = "دریافت کد ۴ رقمی پیامکی"
            setOnClickListener { sendOtpCode() }
        }
        loginGroup.addView(sendOtpBtn)

        otpContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, dp(8), 0, dp(8))
        }

        otpContainer.addView(label("کد ۴ رقمی پیامک‌شده را وارد کنید (معتبر تا ۵ دقیقه):", 15f, true))
        otpInput = EditText(this).apply {
            hint = "کد ۴ رقمی"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            contentDescription = "کادر ورود کد ۴ رقمی پیامک‌شده"
        }
        otpContainer.addView(otpInput)

        verifyOtpBtn = Button(this).apply {
            text = "تایید کد و فعال‌سازی در این گوشی"
            setBackgroundColor(0xFF059669.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener { verifyOtpCode() }
        }
        otpContainer.addView(verifyOtpBtn)
        loginGroup.addView(otpContainer)
        content.addView(loginGroup)

        // داشبورد وضعیت حساب کاربر
        dashboardGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xFFF1F5F9.toInt())
        }

        userMobileLabel = label("", 16f, true, 0xFF0F172A.toInt())
        creditsLabel = label("", 18f, true, 0xFF059669.toInt())
        usageCountLabel = label("", 14f, false, 0xFF334155.toInt())
        expiryLabel = label("", 15f, true, 0xFF0284C7.toInt())

        dashboardGroup.addView(userMobileLabel)
        dashboardGroup.addView(creditsLabel)
        dashboardGroup.addView(usageCountLabel)
        dashboardGroup.addView(expiryLabel)

        buyPlanBtn = Button(this).apply {
            text = "خرید و شارژ آنلاین اشتراک"
            setBackgroundColor(0xFF4F46E5.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                val mobile = preferences.getString("auth_mobile", "").orEmpty()
                val token = preferences.getString("session_token", "").orEmpty()
                val buyUrl = if (mobile.isNotBlank() && token.isNotBlank()) {
                    "$BASE_SERVER_URL/?mobile=" + URLEncoder.encode(mobile, "UTF-8") +
                            "&token=" + URLEncoder.encode(token, "UTF-8") + "&from=app"
                } else {
                    BASE_SERVER_URL
                }
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buyUrl)))
            }
        }
        dashboardGroup.addView(buyPlanBtn)

        logoutButton = Button(this).apply {
            text = "خروج از حساب در این دستگاه"
            setBackgroundColor(0xFF991B1B.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener { showLogoutConfirmationDialog() }
        }
        dashboardGroup.addView(logoutButton)
        content.addView(dashboardGroup)

        toggleServiceBtn = Button(this).apply {
            setOnClickListener {
                val next = !preferences.getBoolean("service_active", true)
                preferences.edit().putBoolean("service_active", next).apply()
                updateToggleUi()
                showStatus(if (next) "سرویس آوایار فعال شد." else "سرویس آوایار موقتاً غیرفعال شد.")
            }
        }
        content.addView(toggleServiceBtn)

        val guideButton = Button(this).apply {
            text = "📖 راهنمای استفاده از برنامه"
            setOnClickListener { showHelpDialog() }
        }
        content.addView(guideButton)

        val appDetailsButton = Button(this).apply {
            text = "تنظیمات برنامه (مجوز دسترسی در اندروید ۱۳+)"
            setOnClickListener {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
        content.addView(appDetailsButton)

        val accessibilityButton = Button(this).apply {
            text = "فعال‌سازی سرویس آوایار در دسترسی‌پذیری"
            setBackgroundColor(0xFF0284C7.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        content.addView(accessibilityButton)

        resultStatus = label("", 15f, true).apply {
            contentDescription = text
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        content.addView(resultStatus)

        val scroll = ScrollView(this).apply {
            addView(content)
            isFillViewport = true
        }
        setContentView(scroll)

        val isLogged = preferences.getBoolean("is_logged_in", false) &&
                !preferences.getString("session_token", "").isNullOrBlank()
        updateUiState(isLogged)
        updateToggleUi()
    }

    override fun onResume() {
        super.onResume()
        val isLogged = preferences.getBoolean("is_logged_in", false) &&
                !preferences.getString("session_token", "").isNullOrBlank()
        if (isLogged) {
            fetchUserAccountInfo()
        }
    }

    private fun updateToggleUi() {
        val active = preferences.getBoolean("service_active", true)
        toggleServiceBtn.text = if (active) "وضعیت سرویس: فعال (جهت خاموش کردن بزنید)" else "وضعیت سرویس: غیرفعال (جهت روشن کردن بزنید)"
        toggleServiceBtn.contentDescription = toggleServiceBtn.text
    }

    private fun updateUiState(loggedIn: Boolean) {
        loginGroup.visibility = if (loggedIn) View.GONE else View.VISIBLE
        dashboardGroup.visibility = if (loggedIn) View.VISIBLE else View.GONE
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("تایید خروج")
            .setMessage("آیا از خروج از حساب کاربری در این دستگاه اطمینان دارید؟")
            .setPositiveButton("بله، خارج شو") { _, _ ->
                preferences.edit()
                    .remove("session_token")
                    .putBoolean("is_logged_in", false)
                    .apply()
                otpInput.setText("")
                otpContainer.visibility = View.GONE
                updateUiState(false)
                showStatus("از حساب کاربری خارج شدید.")
            }
            .setNegativeButton("انصراف", null)
            .show()
    }

    private fun showHelpDialog() {
        val helpMessage = """
            به سامانه آوایار کپچاپرو خوش آمدید.

            نحوه استفاده آسان:
            ۱. پس از فعال‌سازی دسترسی‌پذیری برنامه، در هر سایت یا اپلیکیشنی که تصویر امنیتی (کپچا) دارد، ابتدا نشانگر را داخل کادر متنی ورود کد قرار دهید.
            
            ۲. کلید میانبر دسترسی‌پذیری (دکمه شناور یا فشردن همزمان کلیدهای کنترل صدا) را فشار دهید.
            
            ۳. درخواست شما بلافاصله به سرور ارسال شده و ظرف چند ثانیه متن کد استخراج می‌شود و به صورت خودکار درون کادر متنی جای‌گذاری و در حافظه کپی می‌گردد.

            نکته برای اندروید ۱۳ و بالاتر:
            در صورت غیرفعال بودن دکمه دسترسی‌پذیری در تنظیمات گوشی، ابتدا دکمه «تنظیمات برنامه» را در همین صفحه بزنید، منوی ۳ نقطه بالای صفحه را باز کرده و گزینه «اجازه دادن به تنظیمات محدودشده» را انتخاب کنید.
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("راهنمای استفاده از آوایار کپچاپرو")
            .setMessage(helpMessage)
            .setPositiveButton("متوجه شدم", null)
            .show()
    }

    private fun friendlyErrorMessage(code: Int, serverDetail: String?): String {
        if (!serverDetail.isNullOrBlank()) return serverDetail
        return when (code) {
            400, 401 -> "کد وارد شده اشتباه یا منقضی است. لطفاً مجدداً بررسی کنید یا کد جدید دریافت نمایید."
            403 -> "مهلت استفاده یا اعتبار حساب شما به پایان رسیده است. لطفاً اشتراک تهیه کنید یا با پشتیبانی تماس بگیرید."
            429 -> "تعداد دفعات ورود یا درخواست کد از حد مجاز گذشته است. لطفاً دقایقی صبر کنید یا با پشتیبانی تماس بگیرید."
            else -> "تعداد دفعات تلاش بیش از حد مجاز است یا ارتباط موقتاً برقرار نشد. لطفاً کمی صبر کنید یا با پشتیبانی تماس بگیرید."
        }
    }

    private fun sendOtpCode() {
        val mobile = mobileInput.text.toString().trim()
        if (mobile.length != 11 || !mobile.startsWith("09")) {
            showStatus("لطفاً شماره موبایل ۱۱ رقمی معتبر با صفر وارد نمایید.")
            return
        }
        showStatus("در حال ارسال کد تایید پیامکی، لطفاً صبر کنید...")

        worker.execute {
            try {
                val postData = "mobile=" + URLEncoder.encode(mobile, "UTF-8")
                val conn = (URL("$BASE_SERVER_URL/api/v1/auth/send-otp").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 60000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(postData) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                conn.disconnect()

                val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                mainHandler.post {
                    if (code in 200..299 && json.optBoolean("success", false)) {
                        otpContainer.visibility = View.VISIBLE
                        preferences.edit().putString("auth_mobile", mobile).apply()
                        showStatus(json.optString("message", "کد ۴ رقمی ارسال شد. لطفاً کد را وارد نمایید."))
                        otpInput.requestFocus()
                    } else {
                        showStatus(friendlyErrorMessage(code, json.optString("detail", "")))
                    }
                }
            } catch (_: SocketTimeoutException) {
                mainHandler.post {
                    showStatus("پاسخ سرور بیش از حد طول کشید یا تعداد تلاش‌ها از حد مجاز گذشته است. لطفاً کمی صبر کنید یا با پشتیبانی تماس بگیرید.")
                }
            } catch (_: Exception) {
                mainHandler.post {
                    showStatus("تعداد دفعات ورود یا درخواست از حد مجاز گذشته یا اینترنت قطع است. لطفاً کمی صبر کنید یا با پشتیبانی تماس بگیرید.")
                }
            }
        }
    }

    private fun verifyOtpCode() {
        val mobile = mobileInput.text.toString().trim()
        val codeText = otpInput.text.toString().trim()
        val deviceId = preferences.getString("device_id", "") ?: ""

        if (codeText.length != 4) {
            showStatus("لطفاً کد ۴ رقمی پیامک‌شده را کامل وارد کنید.")
            return
        }
        showStatus("در حال بررسی کد و فعال‌سازی حساب، لطفاً صبر کنید...")

        worker.execute {
            try {
                val postData = "mobile=" + URLEncoder.encode(mobile, "UTF-8") +
                        "&otp_code=" + URLEncoder.encode(codeText, "UTF-8") +
                        "&device_id=" + URLEncoder.encode(deviceId, "UTF-8")

                val conn = (URL("$BASE_SERVER_URL/api/v1/auth/verify-otp").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 60000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("X-Device-ID", deviceId)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(postData) }
                val respCode = conn.responseCode
                val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                conn.disconnect()

                val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
                mainHandler.post {
                    if (respCode in 200..299 && json.optBoolean("success", false)) {
                        val token = json.optString("session_token", "")
                        preferences.edit()
                            .putString("auth_mobile", mobile)
                            .putString("session_token", token)
                            .putBoolean("is_logged_in", true)
                            .apply()

                        updateUiState(true)
                        displayAccountStats(json)
                        showStatus(json.optString("message", "ورود شما با موفقیت انجام شد."))
                    } else {
                        showStatus(friendlyErrorMessage(respCode, json.optString("detail", "")))
                    }
                }
            } catch (_: SocketTimeoutException) {
                mainHandler.post {
                    showStatus("زمان بررسی کد به پایان رسید. لطفاً چند لحظه صبر کرده و دوباره تلاش کنید یا با پشتیبانی تماس بگیرید.")
                }
            } catch (_: Exception) {
                mainHandler.post {
                    showStatus("تعداد دفعات تلاش از حد مجاز گذشته یا ارتباط برقرار نشد. لطفاً کمی صبر کنید یا با پشتیبانی تماس بگیرید.")
                }
            }
        }
    }

    private fun fetchUserAccountInfo() {
        val mobile = preferences.getString("auth_mobile", "") ?: return
        val token = preferences.getString("session_token", "") ?: return
        val deviceId = preferences.getString("device_id", "") ?: ""

        worker.execute {
            try {
                val postData = "mobile=" + URLEncoder.encode(mobile, "UTF-8") +
                        "&token=" + URLEncoder.encode(token, "UTF-8")

                val conn = (URL("$BASE_SERVER_URL/api/v1/user/session-info").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 60000
                    readTimeout = 60000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("X-Device-ID", deviceId)
                }
                OutputStreamWriter(conn.outputStream).use { it.write(postData) }
                val respCode = conn.responseCode
                val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                conn.disconnect()

                if (respCode in 200..299) {
                    val json = JSONObject(body)
                    mainHandler.post { displayAccountStats(json) }
                }
            } catch (_: Exception) { }
        }
    }

    private fun displayAccountStats(json: JSONObject) {
        val mobile = json.optString("mobile", preferences.getString("auth_mobile", "") ?: "")
        val credits = json.optInt("credits", 0)
        val used = json.optInt("total_used", 0)
        val expDisplay = json.optString("expires_at", "بدون محدودیت زمانی")

        userMobileLabel.text = "نام کاربری: $mobile"
        userMobileLabel.contentDescription = "نام کاربری: $mobile"

        creditsLabel.text = "اعتبار باقی‌مانده: $credits درخواست"
        creditsLabel.contentDescription = "اعتبار باقی‌مانده: $credits درخواست"

        usageCountLabel.text = "کل پردازش‌های انجام شده: $used عدد"
        usageCountLabel.contentDescription = "کل پردازش‌های انجام شده: $used عدد"

        expiryLabel.text = "مهلت باقی‌مانده: $expDisplay"
        expiryLabel.contentDescription = "مهلت باقی‌مانده: $expDisplay"
    }

    private fun showStatus(message: String) {
        if (message.isBlank()) {
            resultStatus.visibility = View.GONE
            return
        }
        resultStatus.visibility = View.VISIBLE
        resultStatus.text = message
        resultStatus.contentDescription = message
        announce(resultStatus)
    }

    private fun announce(view: View) {
        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        view.announceForAccessibility(view.contentDescription ?: view.toString())
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val BASE_SERVER_URL = "https://avayar.site"
        const val DEFAULT_SERVER_URL = "https://avayar.site/api/v1/upload-image"
    }
}