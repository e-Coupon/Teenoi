package th.ecoupon.makrobot

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

private data class StandaloneMakroApp(
    val label: String,
    val packageName: String,
    val componentName: ComponentName
)

class MainActivity : Activity() {
    private val accountInputs = mutableListOf<EditText>()
    private val productCodeInputs = mutableListOf<EditText>()
    private val quantityInputs = mutableListOf<EditText>()
    private lateinit var passwordInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var addressInput: EditText
    private lateinit var locationInput: EditText
    private lateinit var statusView: TextView

    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (::statusView.isInitialized) statusView.text = "สถานะ: ${AutomationSession.status}"
            refreshHandler.postDelayed(this, 700)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(page)

        page.addView(text("Makro PRO Bot", 26f, true))
        page.addView(text("จัดการบัญชีได้สูงสุด 10 บัญชี ทำทีละบัญชีตามลำดับ", 14f, false))
        page.addView(text("บัญชีผู้ใช้", 19f, true, top = 18))
        repeat(10) { index ->
            page.addView(text("ID / เบอร์โทร / อีเมล  ${index + 1}", 14f, true, top = 8))
            val field = input("กรอก ID บัญชีที่ ${index + 1}", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            accountInputs.add(field)
            page.addView(field)
        }

        page.addView(text("รหัสผ่าน (ใช้ร่วมกันทั้ง 10 ID)", 14f, true, top = 14))
        passwordInput = input("Password กลาง", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        page.addView(passwordInput)

        page.addView(text("รายการสินค้า (สูงสุด 3 รายการ)", 19f, true, top = 20))
        repeat(3) { index ->
            page.addView(text("สินค้า ${index + 1}", 14f, true, top = 8))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val code = input("รหัสสินค้า", InputType.TYPE_CLASS_TEXT).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(54), 2f).apply { marginEnd = dp(8) }
            }
            val qty = input("จำนวน", InputType.TYPE_CLASS_NUMBER).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f)
            }
            productCodeInputs.add(code)
            quantityInputs.add(qty)
            row.addView(code)
            row.addView(qty)
            page.addView(row)
        }

        page.addView(text("ข้อมูลจัดส่ง", 19f, true, top = 20))
        page.addView(text("สถานที่สำหรับค้นหาหมุด", 14f, true, top = 8))
        locationInput = input("ร้าน The Kim บางปะกง", InputType.TYPE_CLASS_TEXT).apply { setText("ร้านthe kim") }
        page.addView(locationInput)
        page.addView(text("เบอร์โทรศัพท์ผู้รับ", 14f, true, top = 8))
        phoneInput = input("0945674577", InputType.TYPE_CLASS_PHONE).apply { setText("0945674577") }
        page.addView(phoneInput)
        page.addView(text("ที่อยู่", 14f, true, top = 8))
        addressInput = input("88/121", InputType.TYPE_CLASS_TEXT).apply { setText("88/121") }
        page.addView(addressInput)

        page.addView(text("การตั้งค่าครั้งแรก", 19f, true, top = 22))
        val permissionButton = Button(this).apply {
            text = "เปิดการอนุญาตควบคุมหน้าจอ"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        page.addView(permissionButton)

        val startButton = Button(this).apply {
            text = "เริ่มทำงาน"
            textSize = 18f
            setOnClickListener { startBot() }
        }
        page.addView(startButton)

        val stopButton = Button(this).apply {
            text = "หยุดการทำงาน"
            setOnClickListener { AutomationSession.stop(); statusView.text = "สถานะ: ${AutomationSession.status}" }
        }
        page.addView(stopButton)

        statusView = text("สถานะ: ${AutomationSession.status}", 15f, true, top = 14)
        page.addView(statusView)
        page.addView(text("บอทจะเปิดไอคอน Makro PRO ที่ติดตั้งแยกโดยตรง ไม่เปิดหน้าเว็บใน Chrome แทน และจะหยุดหากตรวจพบว่าอยู่คนละแอป", 13f, false, top = 12))
        page.addView(text("ข้อควรทราบ: แอปจะไม่กด Checkout / ชำระเงิน และจะหยุดเมื่อหน้าจอหรือผลค้นหาไม่ตรงกับสิ่งที่ตรวจสอบได้", 13f, false, top = 8))

        setContentView(scroll)
        refreshHandler.post(refreshRunnable)
    }

    private fun startBot() {
        val accounts = accountInputs.map { it.text.toString().trim() }.filter { it.isNotEmpty() }
        val password = passwordInput.text.toString()
        val products = productCodeInputs.indices.mapNotNull { i ->
            val code = productCodeInputs[i].text.toString().trim()
            val qty = quantityInputs[i].text.toString().trim().toIntOrNull() ?: 0
            if (code.isNotEmpty() && qty > 0) ProductTask(code, qty) else null
        }

        if (accounts.isEmpty() || password.isBlank() || products.isEmpty()) {
            Toast.makeText(this, "กรอก ID อย่างน้อย 1 บัญชี, Password และสินค้าอย่างน้อย 1 รายการ", Toast.LENGTH_LONG).show()
            return
        }
        if (!isAutomationEnabled()) {
            Toast.makeText(this, "กรุณาเปิดการอนุญาตควบคุมหน้าจอก่อน", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val config = BotConfig(
            accounts = accounts.take(10),
            password = password,
            products = products,
            deliverySearch = locationInput.text.toString().trim(),
            phone = phoneInput.text.toString().trim(),
            address = addressInput.text.toString().trim(),
            targetPackage = ""
        )
        chooseStandaloneMakroApp(config)
    }

    @Suppress("DEPRECATION")
    private fun findStandaloneMakroApps(): List<StandaloneMakroApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val activity = info.activityInfo ?: return@mapNotNull null
                if (!activity.enabled || !activity.exported || activity.packageName == packageName) {
                    return@mapNotNull null
                }
                val label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (!label.contains("makro", ignoreCase = true) &&
                    !activity.packageName.contains("makro", ignoreCase = true)) {
                    return@mapNotNull null
                }
                StandaloneMakroApp(
                    label = if (label.isBlank()) "Makro PRO" else label,
                    packageName = activity.packageName,
                    componentName = ComponentName(activity.packageName, activity.name)
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(
                compareByDescending<StandaloneMakroApp> { it.label.contains("pro", ignoreCase = true) }
                    .thenBy { it.label }
            )
    }

    private fun chooseStandaloneMakroApp(config: BotConfig) {
        val apps = findStandaloneMakroApps()
        when {
            apps.isEmpty() -> {
                Toast.makeText(
                    this,
                    "ไม่พบไอคอนแอป Makro PRO ที่ติดตั้งแยก กรุณาติดตั้งเป็นแอปบนหน้าจอหลักก่อน",
                    Toast.LENGTH_LONG
                ).show()
                AutomationSession.status = "ไม่พบแอป Makro PRO ที่ติดตั้งแยก"
                statusView.text = "สถานะ: ${AutomationSession.status}"
            }
            apps.size == 1 -> startAutomation(config, apps.first())
            else -> {
                val labels = apps.map { "${it.label}  (${it.packageName})" }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("เลือกไอคอน Makro PRO ที่ติดตั้งแยก")
                    .setItems(labels) { _, index -> startAutomation(config, apps[index]) }
                    .setNegativeButton("ยกเลิก", null)
                    .show()
            }
        }
    }

    private fun startAutomation(config: BotConfig, app: StandaloneMakroApp) {
        val targetConfig = config.copy(targetPackage = app.packageName)
        AutomationSession.start(targetConfig)
        // Clear the visible password immediately. The active run keeps it only in RAM.
        passwordInput.setText("")
        statusView.text = "สถานะ: กำลังเปิดไอคอน ${app.label}"

        val launchIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            startActivity(launchIntent)
            Toast.makeText(this, "เปิดแอป Makro PRO ที่ติดตั้งแยกแล้ว", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            AutomationSession.stop("เปิดไอคอน Makro PRO ไม่สำเร็จ")
            statusView.text = "สถานะ: ${AutomationSession.status}"
            Toast.makeText(this, "เปิดไอคอน Makro PRO ไม่สำเร็จ", Toast.LENGTH_LONG).show()
        }
    }

    private fun isAutomationEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName && it.resolveInfo.serviceInfo.name.endsWith("MakroAccessibilityService") }
    }

    private fun text(value: String, size: Float, bold: Boolean, top: Int = 4): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(top), 0, dp(5))
        }

    private fun input(hintText: String, type: Int): EditText =
        EditText(this).apply {
            hint = hintText
            inputType = type
            setSingleLine(true)
            textSize = 16f
            setPadding(dp(12), dp(7), dp(12), dp(7))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
                bottomMargin = dp(3)
            }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onDestroy()
    }
}
