package com.royanic26.captchaaccessible

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var preferences: SharedPreferences

    private lateinit var serverUrlInput: EditText
    private lateinit var resultStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        fun label(text: String, size: Float = 18f): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(0xFF202124.toInt())
                setPadding(0, dp(8), 0, dp(8))
            }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        content.addView(label(getString(R.string.app_name), 24f).apply {
            setTypeface(typeface, Typeface.BOLD)
            contentDescription = getString(R.string.app_name)
        })
        content.addView(label(getString(R.string.intro_text), 16f))

        content.addView(label(getString(R.string.server_url_label), 16f))
        serverUrlInput = EditText(this).apply {
            hint = getString(R.string.server_url_hint)
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(preferences.getString("server_url", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL)
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

        content.addView(label(getString(R.string.accessibility_usage_hint), 16f))

        resultStatus = label(getString(R.string.ready_status), 16f).apply {
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

    companion object {
        const val DEFAULT_SERVER_URL = "https://10.155.128.84:8000/api/v1/upload-image"
        const val ACTION_SERVICE_RESULT = "com.royanic26.captchaaccessible.SERVICE_RESULT"
    }

}
