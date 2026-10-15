package com.royanic26.captchaaccessible

import android.app.Activity
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
  private lateinit var preferences: SharedPreferences
  private val worker = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())

  private lateinit var usernameInput: EditText
  private lateinit var passwordInput: EditText
  private lateinit var serverUrlInput: EditText
  private lateinit var resultStatus: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    preferences = getSharedPreferences("app_settings", MODE_PRIVATE)
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
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_USERNAME
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

    content.addView(label(getString(R.string.server_url_label), 16f, true))
    serverUrlInput = EditText(this).apply {
      hint = getString(R.string.server_url_hint)
      setSingleLine(true)
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
      val savedUrl = preferences.getString("server_url", null)
      val initialUrl = if (savedUrl.isNullOrBlank() || savedUrl.contains("10.155.")) {
        preferences.edit().putString("server_url", DEFAULT_SERVER_URL).apply()
        DEFAULT_SERVER_URL
      } else {
        savedUrl
      }
      setText(initialUrl)
      setSelectAllOnFocus(true)
      contentDescription = getString(R.string.server_url_label)
    }
    content.addView(serverUrlInput)

    val saveAddressButton = Button(this).apply {
      text = getString(R.string.save_server_url)
      setOnClickListener {
        val value = serverUrlInput.text.toString().trim()
        if (!value.startsWith("https://", ignoreCase = true)) {
          showStatus(getString(R.string.https_required))
        } else {
          preferences.edit().putString("server_url", value).apply()
          showStatus(getString(R.string.server_url_saved))
        }
      }
    }
    content.addView(saveAddressButton)

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

    val uploadUrl = serverUrlInput.text.toString().trim().ifEmpty { DEFAULT_SERVER_URL }
    if (!uploadUrl.startsWith("https://", ignoreCase = true)) {
      showStatus(getString(R.string.https_required))
      return
    }

    val baseUri = uploadUrl.substringBefore("/api/v1/").trimEnd('/')
    val authCheckUrl = "$baseUri/api/v1/auth-check"

    showStatus(getString(R.string.login_checking))

    worker.execute {
      try {
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
              .putString("server_url", uploadUrl)
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
    const val ACTION_SERVICE_RESULT = "com.royanic26.captchaaccessible.SERVICE_RESULT"
  }
}
