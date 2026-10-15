package com.royanic26.captchaaccessible

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
  private lateinit var preferences: SharedPreferences
  private val worker = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())

  private lateinit var usernameInput: EditText
  private lateinit var passwordInput: EditText
  private lateinit var resultStatus: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    preferences = getSharedPreferences("app_settings", MODE_PRIVATE)
    if (preferences.getString("server_url", null).isNullOrBlank()) {
      preferences.edit().putString("server_url", DEFAULT_SERVER_URL).apply()
    }

    // بروزرسانی خودکار آدرس سرور در پس‌زمینه
    syncRemoteServerConfig(this)

    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    fun label(text: String, size: Float = 18f, bold: Boolean = false): TextView =
      TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(0xFF202124.toInt())
        setPadding(0, dp(8), 0, dp(4))
        if (bold) setTypeface(typeface, Typeface.BOLD)
      }

    val content = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(20), dp(20), dp(20))
      layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    }

    content.addView(label(getString(R.string.app_name), 24f, true).apply {
      contentDescription = getString(R.string.app_name)
    })
    content.addView(label(getString(R.string.intro_text), 15f))

    content.addView(label(getString(R.string.login_section_title), 19f, true))

    content.addView(label(getString(R.string.username_label), 16f))
    usernameInput = EditText(this).apply {
      hint = getString(R.string.username_hint)
      setSingleLine(true)
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
      setText(preferences.getString("auth_username", "") ?: "")
      contentDescription = getString(R.string.username_label)
    }
    content.addView(usernameInput)

    content.addView(label(getString(R.string.password_label), 16f))
    passwordInput = EditText(this).apply {
      hint = getString(R.string.password_hint)
      setSingleLine(true)
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
      setText(preferences.getString("auth_password", "") ?: "")
      contentDescription = getString(R.string.password_label)
    }
    content.addView(passwordInput)

    val loginButton = Button(this).apply {
      text = getString(R.string.login_button)
      setOnClickListener { verifyAndSaveLogin() }
    }
    content.addView(loginButton)

    val logoutButton = Button(this).apply {
      text = getString(R.string.logout_button)
      setOnClickListener {
        preferences.edit()
          .remove("auth_username")
          .remove("auth_password")
          .putBoolean("is_logged_in", false)
          .apply()
        usernameInput.setText("")
        passwordInput.setText("")
        showStatus("از حساب کاربری خارج شدید.")
      }
    }
    content.addView(logoutButton)

    val accessibilityButton = Button(this).apply {
      text = getString(R.string.enable_accessibility_service)
      setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }
    content.addView(accessibilityButton)
    content.addView(label(getString(R.string.accessibility_service_hint), 14f))
    content.addView(label(getString(R.string.accessibility_usage_hint), 15f))

    resultStatus = label(getString(R.string.ready_status), 16f, true).apply {
      contentDescription = text
      importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    content.addView(resultStatus)

    val scroll = ScrollView(this).apply {
      addView(content)
      isFillViewport = true
    }
    setContentView(scroll)
  }

  private fun verifyAndSaveLogin() {
    val user = usernameInput.text.toString().trim()
    val pass = passwordInput.text.toString().trim()
    if (user.isEmpty() || pass.isEmpty()) {
      showStatus(getString(R.string.empty_credentials))
      return
    }

    showStatus(getString(R.string.login_checking))

    worker.execute {
      try {
        // ابتدا چک کردن آخرین آدرس سرور از گیت‌هاب
        fetchLatestServerUrlSync(this@MainActivity)
        val uploadUrl = preferences.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        val baseUri = uploadUrl.substringBefore("/api/v1/").trimEnd('/')
        val authCheckUrl = "$baseUri/api/v1/auth-check"

        val token = Base64.encodeToString("$user:$pass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val conn = (URL(authCheckUrl).openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 15000
          readTimeout = 15000
          setRequestProperty("Authorization", "Basic $token")
        }
        val code = conn.responseCode
        conn.disconnect()

        mainHandler.post {
          if (code in 200..299) {
            preferences.edit()
              .putString("auth_username", user)
              .putString("auth_password", pass)
              .putBoolean("is_logged_in", true)
              .apply()
            showStatus(getString(R.string.login_success))
          } else if (code == 401) {
            preferences.edit().putBoolean("is_logged_in", false).apply()
            showStatus(getString(R.string.login_failed))
          } else {
            showStatus("خطا در بررسی ورود. کد HTTP: $code")
          }
        }
      } catch (e: Exception) {
        mainHandler.post {
          showStatus(getString(R.string.upload_failed, e.localizedMessage ?: e.javaClass.simpleName))
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (::preferences.isInitialized && ::resultStatus.isInitialized) {
      preferences.getString("last_result", null)?.let { showStatus(it) }
    }
  }

  private fun showStatus(message: String) {
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
    const val DEFAULT_SERVER_URL = "https://smart-eye-captcha-651852203203.us-central1.run.app/api/v1/upload-image"
    const val REMOTE_CONFIG_URL = "https://raw.githubusercontent.com/royanic26-cmyk/captcha-accessible-android/main/mobile-app/config.json"
    const val ACTION_SERVICE_RESULT = "com.royanic26.captchaaccessible.SERVICE_RESULT"

    fun syncRemoteServerConfig(context: Context) {
      Thread { fetchLatestServerUrlSync(context) }.start()
    }

    fun fetchLatestServerUrlSync(context: Context): String {
      val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
      try {
        val conn = (URL(REMOTE_CONFIG_URL).openConnection() as HttpURLConnection).apply {
          requestMethod = "GET"
          connectTimeout = 8000
          readTimeout = 8000
          useCaches = false
        }
        if (conn.responseCode in 200..299) {
          val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
          val json = JSONObject(text)
          val newUrl = json.optString("server_url", "").trim()
          if (newUrl.startsWith("https://", ignoreCase = true)) {
            prefs.edit().putString("server_url", newUrl).apply()
            conn.disconnect()
            return newUrl
          }
        }
        conn.disconnect()
      } catch (_: Exception) { }
      return prefs.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
  }
}
