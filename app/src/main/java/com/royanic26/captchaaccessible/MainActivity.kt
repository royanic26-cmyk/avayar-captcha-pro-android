package com.royanic26.captchaaccessible

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.util.Random
import java.util.concurrent.Executors

class MainActivity : Activity() {
 private lateinit var preferences: SharedPreferences
 private val worker = Executors.newSingleThreadExecutor()
 private val mainHandler = Handler(Looper.getMainLooper())

 // متغیرهای اصلی احراز هویت و سرویس (کاملاً منطبق با ساختار اصلی)
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
 private lateinit var systemAccStatusBadge: TextView

 // متغیرهای تب‌بندی و ابزارهای تعاملی
 private val tabPages = mutableListOf<View>()
 private val tabButtons = mutableListOf<Button>()
 private var currentTabIndex = 0

 // متغیرهای آزمایشگاه تمرین و تست تصویر
 private var currentPracticeCode = ""
 private lateinit var practiceImageView: ImageView
 private lateinit var practiceEditInput: EditText
 private lateinit var practiceFeedbackLabel: TextView
 private var selectedGalleryBitmap: Bitmap? = null
 private lateinit var galleryPreviewImage: ImageView
 private lateinit var galleryResultLabel: TextView
 private lateinit var copyGalleryResultBtn: Button
 private var lastExtractedGalleryText = ""

 // رنگ‌های پوسته فعال (پشتیبانی از تم تیره، کنتراست بالا ویژه کم‌بینایان و تم روشن)
 private var colBgMain = 0xFF0F172A.toInt()
 private var colBgCard = 0xFF1E293B.toInt()
 private var colBgInput = 0xFF0F172A.toInt()
 private var colBorder = 0xFF334155.toInt()
 private var colTextPrimary = 0xFFF8FAFC.toInt()
 private var colTextSecondary = 0xFFCBD5E1.toInt()
 private var colAccent = 0xFF38BDF8.toInt()
 private var colPrimaryBtn = 0xFF0284C7.toInt()
 private var colSuccess = 0xFF059669.toInt()
 private var colHighlight = 0xFFFDE047.toInt()
 private var fontScale = 1.0f

 override fun onCreate(savedInstanceState: Bundle?) {
  try {
   requestWindowFeature(Window.FEATURE_NO_TITLE)
  } catch (_: Exception) {
  }
  super.onCreate(savedInstanceState)
  try {
   actionBar?.hide()
  } catch (_: Exception) {
  }

  title = "آوایار کپچاپرو"

  preferences = getSharedPreferences("app_settings", MODE_PRIVATE)
  preferences.edit().putString("server_url", DEFAULT_SERVER_URL).apply()

  var deviceId = preferences.getString("device_id", "") ?: ""
  if (deviceId.isBlank()) {
   val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
   deviceId = if (!androidId.isNullOrBlank()) androidId else "and_" + System.currentTimeMillis()
   preferences.edit().putString("device_id", deviceId).apply()
  }

  applyThemePalette()

  try {
   window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
   window.statusBarColor = colBgMain
   window.navigationBarColor = colBgCard
  } catch (_: Exception) {
  }

  val rootContainer = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setBackgroundColor(colBgMain)
   layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.MATCH_PARENT
   )
  }

  // نوار عنوان اختصاصی بالای برنامه
  val topAppBar = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setBackgroundColor(colBgCard)
   setPadding(dp(16), dp(14), dp(16), dp(12))
  }
  topAppBar.addView(
   makeLabel("آوایار کپچاپرو", 19f, true, colAccent, Gravity.CENTER)
  )
  systemAccStatusBadge = makeLabel(
   "● در حال بررسی وضعیت سرویس دسترسی‌پذیری...",
   12f,
   true,
   colHighlight,
   Gravity.CENTER
  )
  topAppBar.addView(systemAccStatusBadge)
  rootContainer.addView(topAppBar)

  // خط جداکننده ظریف زیر هدر
  rootContainer.addView(View(this).apply {
   setBackgroundColor(colBorder)
   layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
  })

  // بخش میانی (اسکرول محتوای تب فعال)
  val contentScroll = ScrollView(this).apply {
   isFillViewport = true
   setBackgroundColor(colBgMain)
   layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    0,
    1f
   )
  }

  val pagesHolder = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(dp(14), dp(14), dp(14), dp(20))
   layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.WRAP_CONTENT
   )
  }

  val tabHome = buildHomeTab()
  val tabPractice = buildPracticeAndTestTab()
  val tabGuide = buildGuideAndFaqTab()
  val tabSettings = buildSettingsAndToolsTab()

  tabPages.clear()
  tabPages.add(tabHome)
  tabPages.add(tabPractice)
  tabPages.add(tabGuide)
  tabPages.add(tabSettings)

  for (page in tabPages) {
   pagesHolder.addView(page)
  }
  contentScroll.addView(pagesHolder)
  rootContainer.addView(contentScroll)

  // بنر نمایش پیام‌های زنده وضعیت (قابل مشاهده و گویا در تمام تب‌ها)
  resultStatus = makeLabel("", 14f, true, Color.WHITE, Gravity.CENTER).apply {
   importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
   accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
   background = makeCardBg(colPrimaryBtn, colAccent, 10f)
   setPadding(dp(14), dp(10), dp(14), dp(10))
   val params = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.WRAP_CONTENT
   )
   params.setMargins(dp(12), dp(4), dp(12), dp(6))
   layoutParams = params
   visibility = View.GONE
  }
  rootContainer.addView(resultStatus)

  // خط جداکننده بالای منوی پایین
  rootContainer.addView(View(this).apply {
   setBackgroundColor(colBorder)
   layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
  })

  // نوار ناوبری پایین صفحه (Bottom Navigation Bar استاندارد ۴ تبی)
  val bottomNav = LinearLayout(this).apply {
   orientation = LinearLayout.HORIZONTAL
   setBackgroundColor(colBgCard)
   setPadding(dp(6), dp(6), dp(6), dp(6))
   layoutParams = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.WRAP_CONTENT
   )
  }

  val tabTitles = listOf("🏠 پیشخوان", "🧪 تمرین و تست", "📚 راهنما", "⚙️ تنظیمات")
  tabButtons.clear()
  for (i in tabTitles.indices) {
   val btn = Button(this).apply {
    text = tabTitles[i]
    textSize = 12f * fontScale
    minHeight = dp(50)
    isAllCaps = false
    setPadding(dp(4), dp(6), dp(4), dp(6))
    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    lp.setMargins(dp(3), 0, dp(3), 0)
    layoutParams = lp
    setOnClickListener {
     switchTab(i, contentScroll, true)
    }
   }
   tabButtons.add(btn)
   bottomNav.addView(btn)
  }
  rootContainer.addView(bottomNav)

  setContentView(rootContainer)

  val isLogged = preferences.getBoolean("is_logged_in", false) &&
    !preferences.getString("session_token", "").isNullOrBlank()
  updateUiState(isLogged)
  updateToggleUi()
  refreshSystemServiceBadge()

  val savedTab = preferences.getInt("last_active_tab", 0).coerceIn(0, 3)
  switchTab(savedTab, contentScroll, false)
 }

 override fun onResume() {
  super.onResume()
  refreshSystemServiceBadge()
  val isLogged = preferences.getBoolean("is_logged_in", false) &&
    !preferences.getString("session_token", "").isNullOrBlank()
  if (isLogged) {
   fetchUserAccountInfo()
  }
 }

 private fun dp(value: Int): Int {
  return (value * resources.displayMetrics.density).toInt()
 }

 private fun applyThemePalette() {
  fontScale = preferences.getFloat("ui_font_scale", 1.0f).coerceIn(0.9f, 1.45f)
  when (preferences.getString("ui_theme_mode", "dark")) {
   "contrast" -> {
    // پوسته کنتراست بسیار بالا استاندارد WCAG AAA ویژه کم‌بینایان شدید
    colBgMain = 0xFF000000.toInt()
    colBgCard = 0xFF121212.toInt()
    colBgInput = 0xFF000000.toInt()
    colBorder = 0xFFFACC15.toInt()
    colTextPrimary = 0xFFFFFFFF.toInt()
    colTextSecondary = 0xFFFEF08A.toInt()
    colAccent = 0xFFFDE047.toInt()
    colPrimaryBtn = 0xFF1D4ED8.toInt()
    colSuccess = 0xFF15803D.toInt()
    colHighlight = 0xFFFDE047.toInt()
   }
   "light" -> {
    // پوسته روشن استاندارد روز
    colBgMain = 0xFFF1F5F9.toInt()
    colBgCard = 0xFFFFFFFF.toInt()
    colBgInput = 0xFFF8FAFC.toInt()
    colBorder = 0xFFCBD5E1.toInt()
    colTextPrimary = 0xFF0F172A.toInt()
    colTextSecondary = 0xFF334155.toInt()
    colAccent = 0xFF0369A1.toInt()
    colPrimaryBtn = 0xFF0284C7.toInt()
    colSuccess = 0xFF059669.toInt()
    colHighlight = 0xFFB45309.toInt()
   }
   else -> {
    // پوسته سرمه‌ای مدرن (پیش‌فرض)
    colBgMain = 0xFF0F172A.toInt()
    colBgCard = 0xFF1E293B.toInt()
    colBgInput = 0xFF0F172A.toInt()
    colBorder = 0xFF334155.toInt()
    colTextPrimary = 0xFFF8FAFC.toInt()
    colTextSecondary = 0xFFCBD5E1.toInt()
    colAccent = 0xFF38BDF8.toInt()
    colPrimaryBtn = 0xFF0284C7.toInt()
    colSuccess = 0xFF059669.toInt()
    colHighlight = 0xFFFDE047.toInt()
   }
  }
 }

 private fun makeCardBg(bgColor: Int, strokeColor: Int = 0x00000000, radiusDp: Float = 14f): GradientDrawable {
  return GradientDrawable().apply {
   setColor(bgColor)
   cornerRadius = dp(radiusDp.toInt()).toFloat()
   if (strokeColor != 0x00000000) {
    setStroke(dp(1), strokeColor)
   }
  }
 }

 private fun makeLabel(
  text: String,
  sizeSp: Float = 15f,
  bold: Boolean = false,
  color: Int = colTextPrimary,
  gravity: Int = Gravity.RIGHT
 ): TextView {
  return TextView(this).apply {
   this.text = text
   textSize = sizeSp * fontScale
   setTextColor(color)
   this.gravity = gravity
   setPadding(0, dp(4), 0, dp(4))
   setLineSpacing(dp(3).toFloat(), 1.05f)
   if (bold) setTypeface(typeface, Typeface.BOLD)
   contentDescription = text
  }
 }

 private fun makeStyledCard(strokeColor: Int = colBorder): LinearLayout {
  return LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   background = makeCardBg(colBgCard, strokeColor, 14f)
   setPadding(dp(16), dp(15), dp(16), dp(16))
   val params = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.WRAP_CONTENT
   )
   params.bottomMargin = dp(14)
   layoutParams = params
  }
 }

 private fun makeActionButton(
  titleText: String,
  bgColor: Int = colPrimaryBtn,
  txtColor: Int = Color.WHITE,
  strokeColor: Int = 0x00000000,
  topMarginDp: Int = 8
 ): Button {
  return Button(this).apply {
   text = titleText
   contentDescription = titleText
   minHeight = dp(52)
   isAllCaps = false
   textSize = 14f * fontScale
   setTextColor(txtColor)
   setTypeface(typeface, Typeface.BOLD)
   background = makeCardBg(bgColor, strokeColor, 10f)
   setPadding(dp(12), dp(10), dp(12), dp(10))
   val lp = LinearLayout.LayoutParams(
    LinearLayout.LayoutParams.MATCH_PARENT,
    LinearLayout.LayoutParams.WRAP_CONTENT
   )
   lp.topMargin = dp(topMarginDp)
   layoutParams = lp
  }
 }

 private fun switchTab(index: Int, scrollView: ScrollView, announceChange: Boolean) {
  currentTabIndex = index
  preferences.edit().putInt("last_active_tab", index).apply()

  val plainNames = listOf("پیشخوان", "تمرین و تست زنده", "راهنما و سوالات متداول", "تنظیمات و ابزارها")
  for (i in tabPages.indices) {
   tabPages[i].visibility = if (i == index) View.VISIBLE else View.GONE
   val btn = tabButtons[i]
   if (i == index) {
    btn.background = makeCardBg(colPrimaryBtn, colAccent, 10f)
    btn.setTextColor(Color.WHITE)
    btn.setTypeface(btn.typeface, Typeface.BOLD)
    btn.contentDescription = "تب " + plainNames[i] + "، انتخاب شده"
   } else {
    btn.background = makeCardBg(colBgMain, colBorder, 10f)
    btn.setTextColor(colTextSecondary)
    btn.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
    btn.contentDescription = "تب " + plainNames[i]
   }
  }
  scrollView.scrollTo(0, 0)
  if (announceChange) {
   tabButtons[index].announceForAccessibility("بخش " + plainNames[index] + " باز شد")
  }
 }

 // =========================================================================
 // تب ۱: پیشخوان اصلی (سرویس دسترسی‌پذیری + ورود پیامکی + داشبورد کاربری)
 // =========================================================================
 private fun buildHomeTab(): LinearLayout {
  val container = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
  }

  // کارت خلاصه شاخص‌های عملکردی سامانه
  val metricsRow = LinearLayout(this).apply {
   orientation = LinearLayout.HORIZONTAL
   val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
   p.bottomMargin = dp(14)
   layoutParams = p
  }

  fun makeMetricBox(titleStr: String, valStr: String, valCol: Int): LinearLayout {
   return LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    background = makeCardBg(colBgCard, colBorder, 12f)
    setPadding(dp(8), dp(12), dp(8), dp(12))
    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    lp.setMargins(dp(4), 0, dp(4), 0)
    layoutParams = lp
    addView(makeLabel(valStr, 14f, true, valCol, Gravity.CENTER))
    addView(makeLabel(titleStr, 11f, false, colTextSecondary, Gravity.CENTER))
    contentDescription = titleStr + ": " + valStr
   }
  }

  metricsRow.addView(makeMetricBox("پاسخ‌گویی هوش مصنوعی", "کمتر از ۲ ثانیه", colAccent))
  metricsRow.addView(makeMetricBox("صفحه‌خوان نابینایان", "سازگار ۱۰۰٪", 0xFF4ADE80.toInt()))
  metricsRow.addView(makeMetricBox("درج در کادر ورودی", "خودکار و کپی", colHighlight))
  container.addView(metricsRow)

  // کارت مدیریت سرویس دسترسی‌پذیری
  val serviceCard = makeStyledCard(colAccent)
  serviceCard.addView(makeLabel("مدیریت سرویس دستیار قابلیت دسترسی", 16f, true, colAccent))
  serviceCard.addView(
   makeLabel(
    "پس از فعال‌سازی اولیه در تنظیمات قابلیت دسترسی گوشی، در هر سایت یا برنامه‌ای با لمس کلید میانبر دسترس‌پذیری، کد امنیتی خوانده و جای‌گذاری می‌شود.",
    13f,
    false,
    colTextSecondary
   )
  )

  toggleServiceBtn = makeActionButton("", colPrimaryBtn, Color.WHITE, 0x00000000, 10).apply {
   setOnClickListener {
    val next = !preferences.getBoolean("service_active", true)
    preferences.edit().putBoolean("service_active", next).apply()
    updateToggleUi()
    showStatus(if (next) "سرویس آوایار فعال شد." else "سرویس آوایار موقتاً غیرفعال شد.")
   }
  }
  serviceCard.addView(toggleServiceBtn)

  val accessibilityButton = makeActionButton(
   "⚙️ فعال‌سازی سرویس آوایار در قابلیت دسترسی گوشی",
   colSuccess,
   Color.WHITE,
   0x00000000,
   8
  ).apply {
   setOnClickListener { showAccessibilityDisclosureDialog() }
  }
  serviceCard.addView(accessibilityButton)

  val appDetailsButton = makeActionButton(
   "🔓 باز کردن تنظیمات برنامه (رفع محدودیت اندروید ۱۳ به بالا)",
   colBgInput,
   colTextPrimary,
   colBorder,
   8
  ).apply {
   setOnClickListener {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
     data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
   }
  }
  serviceCard.addView(appDetailsButton)
  container.addView(serviceCard)

  // کارت ورود و ثبت‌نام با شماره موبایل
  loginGroup = makeStyledCard(colPrimaryBtn)
  loginGroup.addView(makeLabel("ورود یا ثبت‌نام کاربری (همراه با سهمیه تست رایگان)", 16f, true, colAccent))
  loginGroup.addView(makeLabel("شماره موبایل ۱۱ رقمی خود را وارد نمایید:", 13f, false, colTextSecondary))

  mobileInput = EditText(this).apply {
   hint = "مثال: 09123456789"
   setSingleLine(true)
   inputType = InputType.TYPE_CLASS_PHONE
   setTextColor(colTextPrimary)
   setHintTextColor(0xFF64748B.toInt())
   minHeight = dp(52)
   gravity = Gravity.CENTER
   textSize = 16f * fontScale
   background = makeCardBg(colBgInput, colBorder, 10f)
   setText(preferences.getString("auth_mobile", "") ?: "")
   contentDescription = "کادر ورود شماره موبایل ۱۱ رقمی"
  }
  loginGroup.addView(mobileInput)

  sendOtpBtn = makeActionButton("📩 دریافت کد تایید ۴ رقمی پیامکی", colPrimaryBtn).apply {
   setOnClickListener { sendOtpCode() }
  }
  loginGroup.addView(sendOtpBtn)

  otpContainer = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   visibility = View.GONE
   setPadding(0, dp(12), 0, 0)
  }
  otpContainer.addView(makeLabel("کد ۴ رقمی پیامک‌شده را وارد کنید (معتبر تا ۵ دقیقه):", 14f, true, colHighlight))

  otpInput = EditText(this).apply {
   hint = "کد ۴ رقمی"
   setSingleLine(true)
   inputType = InputType.TYPE_CLASS_NUMBER
   setTextColor(colTextPrimary)
   setHintTextColor(0xFF64748B.toInt())
   minHeight = dp(52)
   gravity = Gravity.CENTER
   textSize = 18f * fontScale
   background = makeCardBg(colBgInput, colBorder, 10f)
   contentDescription = "کادر ورود کد ۴ رقمی پیامک‌شده"
  }
  otpContainer.addView(otpInput)

  verifyOtpBtn = makeActionButton("✅ تایید کد و فعال‌سازی در این گوشی", colSuccess).apply {
   setOnClickListener { verifyOtpCode() }
  }
  otpContainer.addView(verifyOtpBtn)
  loginGroup.addView(otpContainer)
  container.addView(loginGroup)

  // کارت داشبورد وضعیت حساب کاربر
  dashboardGroup = makeStyledCard(colSuccess).apply {
   visibility = View.GONE
  }
  dashboardGroup.addView(makeLabel("وضعیت اشتراک و حساب کاربری شما", 16f, true, colAccent))

  userMobileLabel = makeLabel("", 15f, true, colTextPrimary)
  creditsLabel = makeLabel("", 17f, true, 0xFF4ADE80.toInt())
  usageCountLabel = makeLabel("", 14f, false, colTextSecondary)
  expiryLabel = makeLabel("", 14f, true, colHighlight)

  dashboardGroup.addView(userMobileLabel)
  dashboardGroup.addView(creditsLabel)
  dashboardGroup.addView(usageCountLabel)
  dashboardGroup.addView(expiryLabel)

  val refreshStatsBtn = makeActionButton(
   "🔄 بروزرسانی آنی موجودی حساب",
   colBgInput,
   colTextPrimary,
   colBorder,
   10
  ).apply {
   setOnClickListener {
    showStatus("در حال دریافت آخرین وضعیت حساب از سرور...")
    fetchUserAccountInfo()
   }
  }
  dashboardGroup.addView(refreshStatsBtn)

  buyPlanBtn = makeActionButton(
   "🌐 ورود به پرتال کاربری تحت وب (avayar.site)",
   0xFF4F46E5.toInt(),
   Color.WHITE,
   0x00000000,
   8
  ).apply {
   setOnClickListener {
    val mobile = preferences.getString("auth_mobile", "").orEmpty()
    val token = preferences.getString("session_token", "").orEmpty()
    if (mobile.isBlank() || token.isBlank()) {
     showStatus("لطفاً ابتدا با شماره موبایل خود وارد حساب کاربری شوید.")
     return@setOnClickListener
    }
    val buyUrl = BASE_SERVER_URL + "/?mobile=" + URLEncoder.encode(mobile, "UTF-8") +
      "&token=" + URLEncoder.encode(token, "UTF-8") + "&from=app"
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buyUrl)))
   }
  }
  dashboardGroup.addView(buyPlanBtn)

  logoutButton = makeActionButton(
   "🚪 خروج از حساب کاربری در این دستگاه",
   0xFF991B1B.toInt(),
   Color.WHITE,
   0x00000000,
   8
  ).apply {
   setOnClickListener { showLogoutConfirmationDialog() }
  }
  dashboardGroup.addView(logoutButton)
  container.addView(dashboardGroup)

  return container
 }

 // =========================================================================
 // تب ۲: آزمایشگاه تمرین تعاملی کپچا و تست تصویر گالری
 // =========================================================================
 private fun buildPracticeAndTestTab(): LinearLayout {
  val container = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
  }

  // بخش اول: محیط شبیه‌ساز و تمرین آفلاین کپچا
  val simCard = makeStyledCard(colAccent)
  simCard.addView(makeLabel("🎯 محیط شبیه‌ساز و تمرین خوانش کد امنیتی", 16f, true, colAccent))
  simCard.addView(
   makeLabel(
    "در این بخش یک تصویر کد امنیتی واقعی به صورت زنده ساخته می‌شود. کاربران نابینا می‌توانند نشانگر را در کادر زیر قرار داده و عملکرد کلید میانبر آوایار را تمرین کنند؛ یا کد تصویر را به صورت دستی وارد و بررسی نمایند:",
    13f,
    false,
    colTextSecondary
   )
  )

  practiceImageView = ImageView(this).apply {
   scaleType = ImageView.ScaleType.FIT_CENTER
   adjustViewBounds = true
   background = makeCardBg(Color.WHITE, colBorder, 10f)
   setPadding(dp(6), dp(6), dp(6), dp(6))
   val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(115))
   lp.topMargin = dp(10)
   lp.bottomMargin = dp(8)
   layoutParams = lp
   contentDescription = "تصویر کد امنیتی تمرینی جهت تست تشخیص خودکار یا دیداری"
  }
  simCard.addView(practiceImageView)

  val regenBtn = makeActionButton(
   "🔄 تولید تصویر کد امنیتی جدید",
   colBgInput,
   colTextPrimary,
   colBorder,
   4
  ).apply {
   setOnClickListener {
    regeneratePracticeCaptcha()
    showStatus("تصویر کد امنیتی تمرینی جدید ایجاد شد.")
   }
  }
  simCard.addView(regenBtn)

  simCard.addView(makeLabel("کد امنیتی تصویر بالا را در این کادر وارد یا جای‌گذاری کنید:", 13f, true, colTextPrimary))

  practiceEditInput = EditText(this).apply {
   hint = "ورود کد ۵ کاراکتری تصویر"
   setSingleLine(true)
   inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
   setTextColor(colTextPrimary)
   setHintTextColor(0xFF64748B.toInt())
   minHeight = dp(52)
   gravity = Gravity.CENTER
   textSize = 17f * fontScale
   background = makeCardBg(colBgInput, colBorder, 10f)
   contentDescription = "کادر تمرین ورود کد امنیتی"
  }
  simCard.addView(practiceEditInput)

  practiceFeedbackLabel = makeLabel("", 14f, true, colHighlight, Gravity.CENTER).apply {
   visibility = View.GONE
  }
  simCard.addView(practiceFeedbackLabel)

  val checkPracticeBtn = makeActionButton("✔️ بررسی صحت کد واردشده", colSuccess).apply {
   setOnClickListener {
    val typed = practiceEditInput.text.toString().trim()
    if (typed.isBlank()) {
     showStatus("لطفاً ابتدا کد تصویر را وارد کنید یا از میانبر آوایار استفاده نمایید.")
     return@setOnClickListener
    }
    practiceFeedbackLabel.visibility = View.VISIBLE
    if (typed.equals(currentPracticeCode, ignoreCase = true)) {
     practiceFeedbackLabel.setTextColor(0xFF4ADE80.toInt())
     practiceFeedbackLabel.text = "🎉 آفرین! کد امنیتی («" + currentPracticeCode + "») کاملاً صحیح وارد شد."
     showStatus("کد وارد شده کاملاً صحیح است!")
    } else {
     practiceFeedbackLabel.setTextColor(0xFFF87171.toInt())
     practiceFeedbackLabel.text = "❌ کد واردشده با تصویر تطابق ندارد (کد صحیح: " + currentPracticeCode + "). دوباره امتحان کنید."
     showStatus("کد واردشده اشتباه بود. کد صحیح " + currentPracticeCode + " است.")
    }
   }
  }
  simCard.addView(checkPracticeBtn)
  container.addView(simCard)

  // بخش دوم: آپلود و خوانش مستقیم تصویر کپچا از گالری گوشی
  val galleryCard = makeStyledCard(colBorder)
  galleryCard.addView(makeLabel("🖼️ خوانش مستقیم تصویر کپچا از گالری گوشی", 16f, true, colAccent))
  galleryCard.addView(
   makeLabel(
    "چنانچه اسکرین‌شات یا تصویری از کد امنیتی در گوشی خود دارید، می‌توانید آن را انتخاب کنید تا هوش مصنوعی آوایار متن آن را استخراج کند:",
    13f,
    false,
    colTextSecondary
   )
  )

  galleryPreviewImage = ImageView(this).apply {
   scaleType = ImageView.ScaleType.FIT_CENTER
   adjustViewBounds = true
   visibility = View.GONE
   background = makeCardBg(colBgInput, colBorder, 10f)
   setPadding(dp(8), dp(8), dp(8), dp(8))
   val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150))
   lp.topMargin = dp(8)
   layoutParams = lp
   contentDescription = "پیش‌نمایش تصویر انتخاب‌شده از گالری"
  }
  galleryCard.addView(galleryPreviewImage)

  val pickImgBtn = makeActionButton(
   "📂 انتخاب تصویر از گالری دستگاه",
   colPrimaryBtn,
   Color.WHITE,
   0x00000000,
   8
  ).apply {
   setOnClickListener {
    try {
     val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
      type = "image/*"
     }
     startActivityForResult(Intent.createChooser(pickIntent, "انتخاب تصویر کپچا"), REQ_PICK_IMAGE)
    } catch (_: Exception) {
     showStatus("امکان باز کردن گالری در این دستگاه فراهم نشد.")
    }
   }
  }
  galleryCard.addView(pickImgBtn)

  val analyzeGalleryBtn = makeActionButton(
   "🤖 ارسال تصویر و استخراج متن با هوش مصنوعی",
   colSuccess,
   Color.WHITE,
   0x00000000,
   8
  ).apply {
   setOnClickListener { uploadSelectedGalleryImage() }
  }
  galleryCard.addView(analyzeGalleryBtn)

  galleryResultLabel = makeLabel("", 16f, true, colHighlight, Gravity.CENTER).apply {
   visibility = View.GONE
   background = makeCardBg(colBgInput, colAccent, 10f)
   setPadding(dp(12), dp(12), dp(12), dp(12))
   val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
   lp.topMargin = dp(10)
   layoutParams = lp
  }
  galleryCard.addView(galleryResultLabel)

  copyGalleryResultBtn = makeActionButton(
   "📋 کپی کد استخراج‌شده در حافظه کلیپ‌بورد",
   colBgInput,
   colTextPrimary,
   colBorder,
   6
  ).apply {
   visibility = View.GONE
   setOnClickListener {
    if (lastExtractedGalleryText.isNotBlank()) {
     val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
     cm.setPrimaryClip(ClipData.newPlainText("captcha_code", lastExtractedGalleryText))
     showStatus("کد «" + lastExtractedGalleryText + "» در کلیپ‌بورد کپی شد.")
    }
   }
  }
  galleryCard.addView(copyGalleryResultBtn)
  container.addView(galleryCard)

  regeneratePracticeCaptcha()
  return container
 }

 private fun regeneratePracticeCaptcha() {
  val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
  val rnd = Random()
  val sb = StringBuilder()
  for (i in 0 until 5) {
   sb.append(chars[rnd.nextInt(chars.length)])
  }
  currentPracticeCode = sb.toString()

  val w = 440
  val h = 140
  val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bmp)
  canvas.drawColor(Color.rgb(241, 245, 249))

  val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
   color = Color.rgb(203, 213, 225)
   strokeWidth = 1.5f
  }
  for (x in 0..w step 28) {
   canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), gridPaint)
  }
  for (y in 0..h step 28) {
   canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), gridPaint)
  }

  val lineColors = intArrayOf(
   Color.rgb(2, 132, 199),
   Color.rgb(79, 70, 229),
   Color.rgb(5, 150, 105)
  )
  for (i in 0 until 5) {
   val lp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = lineColors[i % lineColors.size]
    strokeWidth = 3.2f
   }
   canvas.drawLine(
    rnd.nextInt(w / 3).toFloat(),
    rnd.nextInt(h).toFloat(),
    (w - rnd.nextInt(w / 3)).toFloat(),
    rnd.nextInt(h).toFloat(),
    lp
   )
  }

  val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
   typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
   textSize = 54f
  }
  val charColors = intArrayOf(
   Color.rgb(15, 23, 42),
   Color.rgb(30, 58, 138),
   Color.rgb(6, 95, 70),
   Color.rgb(124, 45, 18),
   Color.rgb(88, 28, 135)
  )

  for (i in currentPracticeCode.indices) {
   textPaint.color = charColors[i % charColors.size]
   val cx = 55f + (i * 72f)
   val cy = 88f + (rnd.nextInt(20) - 10)
   val angle = (rnd.nextInt(26) - 13).toFloat()
   canvas.save()
   canvas.rotate(angle, cx, cy)
   canvas.drawText(currentPracticeCode[i].toString(), cx, cy, textPaint)
   canvas.restore()
  }

  practiceImageView.setImageBitmap(bmp)
  if (::practiceEditInput.isInitialized) {
   practiceEditInput.setText("")
  }
  if (::practiceFeedbackLabel.isInitialized) {
   practiceFeedbackLabel.visibility = View.GONE
  }
 }

 override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
  super.onActivityResult(requestCode, resultCode, data)
  if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK) {
   val uri = data?.data ?: return
   try {
    val boundsOpt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpt) }
    var sample = 1
    while (boundsOpt.outWidth / sample > 900 || boundsOpt.outHeight / sample > 900) {
     sample *= 2
    }
    val decodeOpt = BitmapFactory.Options().apply { inSampleSize = sample }
    val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpt) }
    if (bmp != null) {
     selectedGalleryBitmap = bmp
     galleryPreviewImage.setImageBitmap(bmp)
     galleryPreviewImage.visibility = View.VISIBLE
     galleryResultLabel.visibility = View.GONE
     copyGalleryResultBtn.visibility = View.GONE
     showStatus("تصویر انتخاب شد. اکنون دکمه «استخراج متن با هوش مصنوعی» را بزنید.")
    }
   } catch (_: Exception) {
    showStatus("خطا در بارگذاری تصویر انتخاب‌شده.")
   }
  }
 }

 private fun uploadSelectedGalleryImage() {
  val bmp = selectedGalleryBitmap
  if (bmp == null) {
   showStatus("لطفاً ابتدا یک تصویر از گالری انتخاب فرمایید.")
   return
  }
  val mobile = preferences.getString("auth_mobile", "").orEmpty()
  val token = preferences.getString("session_token", "").orEmpty()
  val deviceId = preferences.getString("device_id", "").orEmpty()

  if (mobile.isBlank() || token.isBlank()) {
   showStatus("برای ارسال تصویر به سرور ابری، ابتدا از تب «پیشخوان» وارد حساب کاربری خود شوید.")
   return
  }

  showStatus("در حال تحلیل تصویر توسط هوش مصنوعی آوایار...")
  worker.execute {
   try {
    val baos = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 92, baos)
    val imgBytes = baos.toByteArray()

    val boundary = "----AvayarBoundary" + System.currentTimeMillis()
    val authRaw = mobile + ":" + token
    val basicAuth = "Basic " + Base64.encodeToString(authRaw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    val conn = (URL(DEFAULT_SERVER_URL).openConnection() as HttpURLConnection).apply {
     requestMethod = "POST"
     connectTimeout = 60000
     readTimeout = 60000
     doOutput = true
     setRequestProperty("Authorization", basicAuth)
     setRequestProperty("X-Device-ID", deviceId)
     setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary)
    }

    DataOutputStream(conn.outputStream).use { dos ->
     dos.writeBytes("--" + boundary + "\r\n")
     dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"captcha.jpg\"\r\n")
     dos.writeBytes("Content-Type: image/jpeg\r\n\r\n")
     dos.write(imgBytes)
     dos.writeBytes("\r\n--" + boundary + "--\r\n")
     dos.flush()
    }

    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().trim()
    conn.disconnect()

    mainHandler.post {
     if (code in 200..299 && body.isNotBlank()) {
      lastExtractedGalleryText = body
      galleryResultLabel.text = "🎯 نتیجه تشخیص:\n" + body
      galleryResultLabel.contentDescription = "نتیجه تشخیص: " + body
      galleryResultLabel.visibility = View.VISIBLE
      copyGalleryResultBtn.visibility = View.VISIBLE
      showStatus("نتیجه استخراج شد: " + body)
      fetchUserAccountInfo()
     } else {
      val errJson = try { JSONObject(body).optString("detail", "") } catch (_: Exception) { "" }
      showStatus(friendlyErrorMessage(code, errJson))
     }
    }
   } catch (_: Exception) {
    mainHandler.post {
     showStatus("اختلال در ارتباط با سرور هنگام ارسال تصویر. لطفاً اتصال اینترنت را بررسی نمایید.")
    }
   }
  }
 }

 // =========================================================================
 // تب ۳: مرکز آموزش گام‌به‌گام و سوالات متداول بازشونده (Accordion FAQ)
 // =========================================================================
 private fun buildGuideAndFaqTab(): LinearLayout {
  val container = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
  }

  val stepsCard = makeStyledCard(colAccent)
  stepsCard.addView(makeLabel("📖 راهنمای جامع راه‌اندازی در ۳ گام ساده", 16f, true, colAccent))
  stepsCard.addView(
   makeLabel(
    "گام اول (فعال‌سازی مجوز): در تب پیشخوان، دکمه «فعال‌سازی سرویس آوایار در قابلیت دسترسی گوشی» را بزنید. وارد بخش «برنامه‌های نصب‌شده» (Installed Apps) شده و کلید «آوایار کپچاپرو» را روشن کنید.",
    13f,
    false,
    colTextPrimary
   )
  )
  stepsCard.addView(
   makeLabel(
    "گام دوم (مخصوص اندروید ۱۳، ۱۴ و ۱۵): اگر گزینه فعال‌سازی خاکستری و غیرقابل لمس بود، ابتدا در تب پیشخوان دکمه «باز کردن تنظیمات برنامه» را بزنید، از منوی سه‌نقطه بالا گزینه «اجازه دادن به تنظیمات محدودشده» (Allow restricted settings) را تایید کنید و سپس مجدداً سرویس را روشن نمایید.",
    13f,
    false,
    colTextSecondary
   )
  )
  stepsCard.addView(
   makeLabel(
    "گام سوم (استفاده تک‌ضربه‌ای در سایت‌ها): در هر سایت یا برنامه‌ای که کد امنیتی دارد، ابتدا کادر متنی ورود کد را لمس کنید تا نشانگر داخل آن قرار گیرد؛ سپس دکمه میانبر قابلیت دسترسی (آیکون آدمک پایین صفحه) را بزنید تا کد امنیتی خوانده شده، در فیلد درج گردد و در کلیپ‌بورد نیز کپی شود.",
    13f,
    false,
    colTextPrimary
   )
  )
  container.addView(stepsCard)

  // سوالات متداول تعاملی بازشونده
  val faqHeaderCard = makeStyledCard(colBorder)
  faqHeaderCard.addView(makeLabel("❓ پرسش‌های متداول کاربران (برای مشاهده پاسخ ضربه بزنید)", 15f, true, colHighlight))

  val faqs = listOf(
   Pair(
    "۱. چگونه با صفحه‌خوان TalkBack یا Jieshuo بدون تداخل کار کنم؟",
    "آوایار ۱۰۰٪ با TalkBack و صفحه‌خوان چینی (Jieshuo) سازگار است. برای اینکه با زدن دکمه قابلیت دسترسی پایین صفحه منوی انتخاب باز نشود، در تنظیمات قابلیت دسترسی گوشی، میانبر سایر برنامه‌ها را خاموش بگذارید تا تنها میانبر آوایار فعال باشد."
   ),
   Pair(
    "۲. آیا در صورت روشن بودن فیلترشکن (VPN) برنامه کار می‌کند؟",
    "سرویس تشخیص کپچای آوایار روی سرورهای ابری پرسرعت میزبانی شده و در اکثر شبکه‌ها بدون مشکل کار می‌کند؛ اما هنگام باز کردن درگاه پرداخت بانکی شاپرک در پرتال وب، حتماً باید VPN خود را خاموش نمایید."
   ),
   Pair(
    "۳. اگر در صفحه‌ای کپچا وجود نداشته باشد از اعتبارم کسر می‌شود؟",
    "خیر! در صورتی که هوش مصنوعی تشخیص دهد در تصویر ارسالی هیچ کد امنیتی وجود ندارد، هیچ اعتباری از حساب کاربری شما کسر نخواهد شد."
   ),
   Pair(
    "۴. چگونه می‌توانم پیش از استفاده در سایت‌های اصلی تمرین کنم؟",
    "کافی است وارد تب «تمرین و تست» در همین برنامه شوید. در آنجا یک شبیه‌ساز کد امنیتی تعبیه شده که می‌توانید بارها بدون نیاز به اینترنت یا کسر اعتبار با آن تمرین کنید."
   ),
   Pair(
    "۵. اگر سایتی کد امنیتی خاصی داشت که خوانده نشد چه کنم؟",
    "از تب «تنظیمات» در همین برنامه، آدرس سایت مورد نظر را در فرم «گزارش سایت‌های ناسازگار» ثبت فرمایید تا تیم فنی آوایار مدل هوش مصنوعی را برای آن سامانه بهینه‌سازی کند."
   ),
   Pair(
    "۶. در صورت تعویض گوشی چگونه حسابم را منتقل کنم؟",
    "هر اشتراک برای حفظ امنیت روی یک دستگاه فعال می‌شود. در صورت تعویض گوشی، کافی است با پشتیبانی آوایار در ارتباط باشید تا قفل دستگاه قبلی شما بازنشانی شود."
   )
  )

  for (item in faqs) {
   val qBox = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = makeCardBg(colBgInput, colBorder, 10f)
    setPadding(dp(12), dp(10), dp(12), dp(10))
    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    lp.topMargin = dp(8)
    layoutParams = lp
   }
   val qTitle = makeLabel("➕ " + item.first, 13f, true, colAccent)
   val qAnswer = makeLabel(item.second, 13f, false, colTextSecondary).apply {
    visibility = View.GONE
    setPadding(0, dp(6), 0, dp(2))
   }
   qBox.isClickable = true
   qBox.isFocusable = true
   qBox.contentDescription = item.first + " (جهت باز شدن پاسخ لمس کنید)"
   qBox.setOnClickListener {
    val opening = qAnswer.visibility == View.GONE
    qAnswer.visibility = if (opening) View.VISIBLE else View.GONE
    qTitle.text = (if (opening) "➖ " else "➕ ") + item.first
    qBox.announceForAccessibility(if (opening) item.second else "بسته شد")
   }
   qBox.addView(qTitle)
   qBox.addView(qAnswer)
   faqHeaderCard.addView(qBox)
  }

  container.addView(faqHeaderCard)
  return container
 }

 // =========================================================================
 // تب ۴: تنظیمات کم‌بینایان، تست سلامت سرور و ارسال گزارش خطا
 // =========================================================================
 private fun buildSettingsAndToolsTab(): LinearLayout {
  val container = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
  }

  // کارت شخصی‌سازی دیداری ویژه کم‌بینایان
  val visualCard = makeStyledCard(colAccent)
  visualCard.addView(makeLabel("👁️ شخصی‌سازی دیداری ویژه کم‌بینایان", 16f, true, colAccent))
  visualCard.addView(makeLabel("انتخاب پوسته رنگی و کنتراست محیط برنامه:", 13f, false, colTextSecondary))

  val curTheme = preferences.getString("ui_theme_mode", "dark") ?: "dark"
  val themeRow = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
  }

  fun addThemeOption(modeKey: String, titleStr: String) {
   val isSelected = curTheme == modeKey
   val prefix = if (isSelected) "🔘 " else "⚪ "
   val btn = makeActionButton(
    prefix + titleStr,
    if (isSelected) colPrimaryBtn else colBgInput,
    if (isSelected) Color.WHITE else colTextPrimary,
    if (isSelected) colAccent else colBorder,
    6
   ).apply {
    setOnClickListener {
     preferences.edit().putString("ui_theme_mode", modeKey).apply()
     recreate()
    }
   }
   themeRow.addView(btn)
  }

  addThemeOption("dark", "پوسته تیره سرمه‌ای (پیش‌فرض)")
  addThemeOption("contrast", "کنتراست بسیار بالا (زرد و مشکی ویژه کم‌بینایان)")
  addThemeOption("light", "پوسته روشن روز (سفید و آبی)")
  visualCard.addView(themeRow)

  visualCard.addView(makeLabel("اندازه نوشته‌های برنامه:", 13f, true, colTextPrimary).apply {
   setPadding(0, dp(12), 0, dp(4))
  })

  val fontRow = LinearLayout(this).apply {
   orientation = LinearLayout.HORIZONTAL
  }
  val fontOptions = listOf(
   Pair(1.0f, "معمولی"),
   Pair(1.18f, "بزرگ"),
   Pair(1.35f, "خیلی بزرگ")
  )
  for (fOpt in fontOptions) {
   val activeFont = kotlin.math.abs(fontScale - fOpt.first) < 0.08f
   val fBtn = Button(this).apply {
    text = fOpt.second
    isAllCaps = false
    textSize = 13f
    minHeight = dp(46)
    setTextColor(if (activeFont) Color.WHITE else colTextPrimary)
    background = makeCardBg(
     if (activeFont) colSuccess else colBgInput,
     if (activeFont) colAccent else colBorder,
     8f
    )
    val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    lp.setMargins(dp(3), 0, dp(3), 0)
    layoutParams = lp
    setOnClickListener {
     preferences.edit().putFloat("ui_font_scale", fOpt.first).apply()
     recreate()
    }
   }
   fontRow.addView(fBtn)
  }
  visualCard.addView(fontRow)
  container.addView(visualCard)

  // کارت عیب‌یابی و تست سرعت اتصال به سرور
  val diagCard = makeStyledCard(colBorder)
  diagCard.addView(makeLabel("📡 عیب‌یابی اتصال به سرور ابری آوایار", 15f, true, colAccent))
  val pingResultLabel = makeLabel("برای بررسی وضعیت شبکه و سلامت سرور دکمه زیر را لمس کنید.", 13f, false, colTextSecondary)
  diagCard.addView(pingResultLabel)

  val pingBtn = makeActionButton("⚡ تست زنده سلامت و پینگ سرور", colPrimaryBtn).apply {
   setOnClickListener {
    pingResultLabel.text = "⏳ در حال تست ارتباط با سرور avayar.site..."
    worker.execute {
     val t0 = System.currentTimeMillis()
     try {
      val conn = (URL(BASE_SERVER_URL + "/health").openConnection() as HttpURLConnection).apply {
       requestMethod = "GET"
       connectTimeout = 15000
       readTimeout = 15000
      }
      val code = conn.responseCode
      conn.disconnect()
      val elapsed = System.currentTimeMillis() - t0
      mainHandler.post {
       if (code in 200..299) {
        val msg = "✅ سرور آوایار برخط و سالم است (زمان پاسخ: " + elapsed + " میلی‌ثانیه)"
        pingResultLabel.setTextColor(0xFF4ADE80.toInt())
        pingResultLabel.text = msg
        showStatus(msg)
       } else {
        val msg = "⚠️ پاسخ غیرمنتظره از سرور (کد وضعیت: " + code + ")"
        pingResultLabel.text = msg
        showStatus(msg)
       }
      }
     } catch (_: Exception) {
      mainHandler.post {
       val msg = "❌ عدم برقراری ارتباط با سرور؛ لطفاً اینترنت یا فیلترشکن خود را بررسی کنید."
       pingResultLabel.setTextColor(0xFFF87171.toInt())
       pingResultLabel.text = msg
       showStatus(msg)
      }
     }
    }
   }
  }
  diagCard.addView(pingBtn)
  container.addView(diagCard)

  // کارت گزارش خطای سایت‌های ناسازگار به پشتیبانی
  val reportCard = makeStyledCard(colBorder)
  reportCard.addView(makeLabel("🛠️ گزارش سایت‌های دارای کپچای خوانده‌نشده", 15f, true, colHighlight))
  reportCard.addView(
   makeLabel(
    "اگر در سایتی کد امنیتی توسط آوایار خوانده نشد، آدرس آن را ارسال فرمایید تا پشتیبانی فنی آن را بررسی و سازگار نماید:",
    13f,
    false,
    colTextSecondary
   )
  )

  val siteUrlInput = EditText(this).apply {
   hint = "آدرس سایت (مثال: https://example.ir)"
   setSingleLine(true)
   inputType = InputType.TYPE_TEXT_VARIATION_URI
   setTextColor(colTextPrimary)
   setHintTextColor(0xFF64748B.toInt())
   minHeight = dp(48)
   gravity = Gravity.CENTER
   background = makeCardBg(colBgInput, colBorder, 10f)
   contentDescription = "کادر ورود آدرس اینترنتی سایت دارای مشکل"
  }
  reportCard.addView(siteUrlInput)

  val siteDescInput = EditText(this).apply {
   hint = "توضیحات تکمیلی (اختیاری)"
   setSingleLine(true)
   setTextColor(colTextPrimary)
   setHintTextColor(0xFF64748B.toInt())
   minHeight = dp(48)
   gravity = Gravity.CENTER
   val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
   lp.topMargin = dp(8)
   layoutParams = lp
   background = makeCardBg(colBgInput, colBorder, 10f)
   contentDescription = "کادر توضیحات تکمیلی گزارش خطا"
  }
  reportCard.addView(siteDescInput)

  val sendReportBtn = makeActionButton("📤 ثبت و ارسال گزارش برای تیم فنی", colSuccess).apply {
   setOnClickListener {
    val mob = preferences.getString("auth_mobile", "").orEmpty()
    val tok = preferences.getString("session_token", "").orEmpty()
    val siteVal = siteUrlInput.text.toString().trim()
    val descVal = siteDescInput.text.toString().trim()
    if (mob.isBlank() || tok.isBlank()) {
     showStatus("لطفاً ابتدا از تب پیشخوان وارد حساب کاربری خود شوید.")
     return@setOnClickListener
    }
    if (siteVal.isBlank()) {
     showStatus("لطفاً آدرس سایت مورد نظر را وارد نمایید.")
     return@setOnClickListener
    }
    showStatus("در حال ارسال گزارش به پشتیبانی آوایار...")
    worker.execute {
     try {
      val postData = "mobile=" + URLEncoder.encode(mob, "UTF-8") +
        "&token=" + URLEncoder.encode(tok, "UTF-8") +
        "&site_url=" + URLEncoder.encode(siteVal, "UTF-8") +
        "&description=" + URLEncoder.encode(descVal, "UTF-8")
      val conn = (URL(BASE_SERVER_URL + "/api/v1/user/report-error").openConnection() as HttpURLConnection).apply {
       requestMethod = "POST"
       connectTimeout = 30000
       readTimeout = 30000
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
       if (code in 200..299) {
        siteUrlInput.setText("")
        siteDescInput.setText("")
        showStatus(json.optString("message", "گزارش شما با موفقیت برای تیم پشتیبانی ارسال شد."))
       } else {
        showStatus(friendlyErrorMessage(code, json.optString("detail", "")))
       }
      }
     } catch (_: Exception) {
      mainHandler.post { showStatus("خطا در ارسال گزارش؛ اتصال اینترنت را بررسی کنید.") }
     }
    }
   }
  }
  reportCard.addView(sendReportBtn)
  container.addView(reportCard)

  // کارت قوانین، حریم خصوصی و درباره سامانه
  val legalCard = makeStyledCard(colBorder)
  legalCard.addView(makeLabel("⚖️ قوانین، حریم خصوصی و شناسنامه برنامه", 15f, true, colAccent))
  legalCard.addView(
   makeLabel(
    "سامانه هوشمند دسترس‌پذیری آوایار (نسخه ۱.۲.۰)\nپشتیبانی رسمی: support@avayar.site | وب‌سایت: avayar.site",
    12f,
    false,
    colTextSecondary
   )
  )

  val privacyButton = makeActionButton(
   "📜 مشاهده سیاست حریم خصوصی و قوانین خدمات",
   colBgInput,
   colAccent,
   colBorder,
   8
  ).apply {
   setOnClickListener {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE_SERVER_URL + "/#rules")))
   }
  }
  legalCard.addView(privacyButton)
  container.addView(legalCard)

  return container
 }

 // =========================================================================
 // بررسی وضعیت واقعی سرویس دسترسی‌پذیری در سیستم‌عامل اندروید
 // =========================================================================
 private fun isAccessibilityEnabledInSystem(): Boolean {
  return try {
   val expected = ComponentName(this, ScreenCaptureAccessibilityService::class.java)
   val enabledServices = Settings.Secure.getString(
    contentResolver,
    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
   ) ?: return false
   val splitter = TextUtils.SimpleStringSplitter(':')
   splitter.setString(enabledServices)
   while (splitter.hasNext()) {
    val item = splitter.next()
    val comp = ComponentName.unflattenFromString(item)
    if (comp != null && comp == expected) {
     return true
    }
   }
   false
  } catch (_: Exception) {
   false
  }
 }

 private fun refreshSystemServiceBadge() {
  if (!::systemAccStatusBadge.isInitialized) return
  val sysEnabled = isAccessibilityEnabledInSystem()
  val appActive = preferences.getBoolean("service_active", true)
  if (sysEnabled && appActive) {
   systemAccStatusBadge.text = "● سرویس قابلیت دسترسی در گوشی: فعال و آماده به کار"
   systemAccStatusBadge.setTextColor(0xFF4ADE80.toInt())
  } else if (sysEnabled && !appActive) {
   systemAccStatusBadge.text = "● سرویس در گوشی روشن است اما موقتاً در برنامه متوقف شده"
   systemAccStatusBadge.setTextColor(colHighlight)
  } else {
   systemAccStatusBadge.text = "⚠️ سرویس در تنظیمات قابلیت دسترسی گوشی هنوز روشن نشده است"
   systemAccStatusBadge.setTextColor(colHighlight)
  }
  systemAccStatusBadge.contentDescription = systemAccStatusBadge.text
 }

 private fun updateToggleUi() {
  val active = preferences.getBoolean("service_active", true)
  toggleServiceBtn.text = if (active) {
   "✅ وضعیت داخلی سرویس: فعال (جهت توقف موقت لمس کنید)"
  } else {
   "⏸️ وضعیت داخلی سرویس: غیرفعال (جهت روشن کردن لمس کنید)"
  }
  toggleServiceBtn.background = makeCardBg(
   if (active) colPrimaryBtn else 0xFF475569.toInt(),
   0x00000000,
   10f
  )
  toggleServiceBtn.contentDescription = toggleServiceBtn.text
  refreshSystemServiceBadge()
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

 // بیانیه صریح و الزامی افشای استفاده از خدمات قابلیت دسترسی (طبق قوانین کافه‌بازار و مایکت)
 private fun showAccessibilityDisclosureDialog() {
  val message = "اعلامیه مهم حریم خصوصی و قابلیت دسترسی:\n\n" +
   "برنامه «آوایار کپچاپرو» جهت ارائه خدمات به افراد نابینا و کم‌بینا از سرویس قابلیت دسترسی (Accessibility Service) استفاده می‌کند.\n\n" +
   "کاربرد دقیق این سرویس:\n" +
   "۱. شناسایی و استخراج کدهای امنیتی تصویری (کپچا) در صفحات وب و اپلیکیشن‌ها با فشردن کلید میانبر.\n" +
   "۲. الصاق خودکار متن شناسایی‌شده در کادر ورود کد برای تسهیل تعامل کاربران دارای معلولیت بینایی.\n\n" +
   "امنیت و حریم خصوصی:\n" +
   "این سرویس هیچ‌گونه اطلاعات شخصی، متون تایپ‌شده، گذرواژه‌ها یا گزارش‌های فعالیت شما را ذخیره، ضبط یا ارسال نمی‌کند و عملکرد آن صرفاً در لحظه فشردن کلید میانبر انجام می‌پذیرد."

  AlertDialog.Builder(this)
   .setTitle("مجوز خدمات قابلیت دسترسی (Accessibility)")
   .setMessage(message)
   .setPositiveButton("متوجه شدم و فعال‌سازی") { _, _ ->
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
   }
   .setNegativeButton("انصراف", null)
   .setNeutralButton("سیاست حریم خصوصی") { _, _ ->
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE_SERVER_URL + "/#rules")))
   }
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

 // =========================================================================
 // توابع اصلی ارتباط با سرور (بدون هیچ‌گونه تغییر در ساختار و کلیدهای ذخیره‌سازی)
 // =========================================================================
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
    val conn = (URL(BASE_SERVER_URL + "/api/v1/auth/send-otp").openConnection() as HttpURLConnection).apply {
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
     showStatus("پاسخ سرور بیش از حد طول کشید یا تعداد تلاش‌ها از حد مجاز گذشته است. لطفاً کمی صبر کنید.")
    }
   } catch (_: Exception) {
    mainHandler.post {
     showStatus("تعداد دفعات ورود یا درخواست از حد مجاز گذشته یا اینترنت قطع است. لطفاً کمی صبر کنید.")
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

    val conn = (URL(BASE_SERVER_URL + "/api/v1/auth/verify-otp").openConnection() as HttpURLConnection).apply {
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
     showStatus("زمان بررسی کد به پایان رسید. لطفاً چند لحظه صبر کرده و دوباره تلاش کنید.")
    }
   } catch (_: Exception) {
    mainHandler.post {
     showStatus("تعداد دفعات تلاش از حد مجاز گذشته یا ارتباط برقرار نشد. لطفاً کمی صبر کنید.")
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

    val conn = (URL(BASE_SERVER_URL + "/api/v1/user/session-info").openConnection() as HttpURLConnection).apply {
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
   } catch (_: Exception) {
   }
  }
 }

 private fun displayAccountStats(json: JSONObject) {
  val mobile = json.optString("mobile", preferences.getString("auth_mobile", "") ?: "")
  val credits = json.optInt("credits", 0)
  val used = json.optInt("total_used", 0)
  val expDisplay = json.optString("expires_at", "بدون محدودیت زمانی")

  userMobileLabel.text = "📱 نام کاربری: " + mobile
  userMobileLabel.contentDescription = "نام کاربری: " + mobile

  creditsLabel.text = "🎯 اعتبار باقی‌مانده: " + credits + " درخواست"
  creditsLabel.contentDescription = "اعتبار باقی‌مانده: " + credits + " درخواست"

  usageCountLabel.text = "📈 کل پردازش‌های انجام شده: " + used + " عدد"
  usageCountLabel.contentDescription = "کل پردازش‌های انجام شده: " + used + " عدد"

  expiryLabel.text = "⏳ مهلت باقی‌مانده: " + expDisplay
  expiryLabel.contentDescription = "مهلت باقی‌مانده: " + expDisplay
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
  private const val REQ_PICK_IMAGE = 1041
 }
}