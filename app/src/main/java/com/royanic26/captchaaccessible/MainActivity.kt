package com.royanic26.captchaaccessible

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val pickImageRequest = 1001
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var selectedImage: Uri? = null
    private lateinit var preferences: SharedPreferences

    private lateinit var serverUrlInput: EditText
    private lateinit var imageStatus: TextView
    private lateinit var resultStatus: TextView
    private lateinit var uploadButton: Button
    private lateinit var progress: ProgressBar

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

        val chooseButton = Button(this).apply {
            text = getString(R.string.choose_image)
            setOnClickListener { chooseImage() }
        }
        content.addView(chooseButton)

        imageStatus = label(getString(R.string.no_image_selected), 16f).apply {
            contentDescription = text
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        content.addView(imageStatus)

        uploadButton = Button(this).apply {
            text = getString(R.string.upload_image)
            isEnabled = false
            setOnClickListener { uploadSelectedImage() }
        }
        content.addView(uploadButton)

        progress = ProgressBar(this).apply {
            visibility = View.GONE
            isIndeterminate = true
            contentDescription = getString(R.string.upload_in_progress)
        }
        content.addView(progress)

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

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, pickImageRequest)
    }

    @Deprecated("Uses compatible activity result callback for broad Android support")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickImageRequest && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedImage = uri
            imageStatus.text = getString(R.string.image_selected)
            imageStatus.contentDescription = getString(R.string.image_selected)
            uploadButton.isEnabled = true
            announce(imageStatus)
            showStatus(getString(R.string.ready_to_upload))
        }
    }

    private fun uploadSelectedImage() {
        val uri = selectedImage ?: run {
            showStatus(getString(R.string.select_image_first))
            return
        }
        val endpoint = serverUrlInput.text.toString().trim().also { preferences.edit().putString("server_url", it).apply() }
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            showStatus(getString(R.string.https_required))
            return
        }

        uploadButton.isEnabled = false
        progress.visibility = View.VISIBLE
        showStatus(getString(R.string.uploading))

        executor.execute {
            val message = try {
                sendMultipart(uri, endpoint)
            } catch (e: Exception) {
                getString(R.string.upload_failed, e.localizedMessage ?: e.javaClass.simpleName)
            }
            mainHandler.post {
                progress.visibility = View.GONE
                uploadButton.isEnabled = selectedImage != null
                showStatus(message)
            }
        }
    }

    private fun sendMultipart(uri: Uri, endpoint: String): String {
        val boundary = "----CaptchaUpload${UUID.randomUUID()}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            val fileName = queryDisplayName(uri) ?: "captcha-image.jpg"
            val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            connection.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"${safeFileName(fileName)}\"\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Type: $mimeType\r\n\r\n".toByteArray(Charsets.UTF_8))
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "تصویر انتخاب‌شده قابل خواندن نیست." }
                    input.copyTo(output)
                }
                output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val code = connection.responseCode
            val responseStream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return if (code in 200..299) {
                getString(R.string.upload_success, code, responseText.take(500))
            } else {
                getString(R.string.server_error, code, responseText.take(500))
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return null
    }

    private fun safeFileName(value: String): String =
        value.replace("\"", "_").replace("\r", "_").replace("\n", "_")

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
        const val DEFAULT_SERVER_URL = "https://10.83.137.84:8000/api/v1/upload-image"
        const val ACTION_SERVICE_RESULT = "com.royanic26.captchaaccessible.SERVICE_RESULT"
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
