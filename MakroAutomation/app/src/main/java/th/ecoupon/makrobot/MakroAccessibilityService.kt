package th.ecoupon.makrobot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Conservative UI automation for the exact Makro PRO flow recorded by the user.
 * It never checks out, submits payment, or attempts to bypass account verification.
 * When a screen cannot be identified safely, it stops and reports the blocker.
 */
class MakroAccessibilityService : AccessibilityService() {
    private enum class Step {
        IDLE, WAIT_LANDING, CLICK_LOGIN, ENTER_ID, SUBMIT_ID,
        ENTER_PASSWORD, SUBMIT_PASSWORD, WAIT_HOME,
        ENTER_PRODUCT, SUBMIT_SEARCH, WAIT_RESULTS, ADD_PRODUCT,
        OPEN_CART, OPEN_ADDRESS, SEARCH_LOCATION, SELECT_LOCATION,
        CONFIRM_LOCATION, WAIT_ADDRESS_FORM, EDIT_CONTACT, ENTER_PHONE,
        SAVE_CONTACT, ENTER_ADDRESS, SAVE_ADDRESS,
        OPEN_MENU, CLICK_LOGOUT, WAIT_LOGOUT, DONE, BLOCKED
    }

    private var step = Step.IDLE
    private var stepStartedAt = 0L
    private var lastActionAt = 0L
    private var initialSessionChecked = false
    private var loggingOutInitialSession = false
    private var quantityAdded = 0
    private var locationScrollAttempts = 0
    private val handler = Handler(Looper.getMainLooper())
    private var loopPosted = false

    private val loop = object : Runnable {
        override fun run() {
            loopPosted = false
            if (!AutomationSession.running) return
            if (AutomationSession.pendingStart) beginFromPending()
            tick()
            scheduleNext()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutomationSession.service = this
        AutomationSession.status = "พร้อมควบคุมหน้าจอ"
        if (AutomationSession.pendingStart) beginFromPending()
    }

    fun beginFromPending() {
        if (!AutomationSession.running || !AutomationSession.pendingStart) return
        AutomationSession.pendingStart = false
        AutomationSession.accountIndex = 0
        AutomationSession.productIndex = 0
        quantityAdded = 0
        locationScrollAttempts = 0
        initialSessionChecked = false
        loggingOutInitialSession = false
        move(Step.WAIT_LANDING, "รอหน้า Makro PRO")
        scheduleNext()
    }

    fun releaseRunResources() {
        handler.removeCallbacks(loop)
        loopPosted = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (AutomationSession.running) scheduleNext()
    }

    override fun onInterrupt() {
        AutomationSession.stop("การควบคุมหน้าจอถูกขัดจังหวะ")
    }

    override fun onDestroy() {
        releaseRunResources()
        if (AutomationSession.service === this) AutomationSession.service = null
        super.onDestroy()
    }

    private fun scheduleNext() {
        if (!AutomationSession.running || loopPosted) return
        loopPosted = true
        handler.postDelayed(loop, 850)
    }

    private fun move(next: Step, message: String) {
        step = next
        stepStartedAt = SystemClock.uptimeMillis()
        lastActionAt = 0L
        AutomationSession.status = message
    }

    private fun waitOrBlock(message: String) {
        val elapsed = SystemClock.uptimeMillis() - stepStartedAt
        if (elapsed > 45_000L) {
            move(Step.BLOCKED, message)
            AutomationSession.running = false
            AutomationSession.config = null
        }
    }

    private fun tick() {
        val config = AutomationSession.config ?: run {
            AutomationSession.stop("ไม่พบข้อมูลตั้งค่างาน")
            return
        }
        val root = rootInActiveWindow ?: return

        // Verification prompts are not automated or bypassed.
        if (containsAny(root, "กรอกรหัส OTP", "ยืนยันรหัส OTP", "ส่งรหัส OTP", "รหัสยืนยันทาง SMS", "captcha", "CAPTCHA")) {
            move(Step.BLOCKED, "หน้าจอขอ OTP/CAPTCHA — หยุดให้ผู้ใช้ตรวจสอบ")
            AutomationSession.running = false
            AutomationSession.config = null
            return
        }

        when (step) {
            Step.IDLE -> Unit

            Step.WAIT_LANDING -> {
                if (isHome(root)) {
                    if (!initialSessionChecked && AutomationSession.accountIndex == 0) {
                        initialSessionChecked = true
                        loggingOutInitialSession = true
                        move(Step.OPEN_MENU, "พบว่ามีบัญชีล็อกอินค้างอยู่ กำลังออกจากระบบก่อนเริ่ม ID แรก")
                    } else {
                        move(Step.OPEN_MENU, "กำลังเปิดเมนูบัญชี")
                    }
                } else if (containsAny(root, "ยินดีต้อนรับ", "เข้าสู่ระบบ", "ลงทะเบียน")) {
                    move(Step.CLICK_LOGIN, "พบหน้าเริ่มต้น เข้าสู่ระบบ")
                } else {
                    waitOrBlock("ไม่พบหน้าเริ่มต้น Makro PRO")
                }
            }

            Step.CLICK_LOGIN -> {
                if (hasEditable(root, "เบอร์มือถือ", "เบอร์มือถือหรืออีเมล")) {
                    move(Step.ENTER_ID, "กรอก ID บัญชี")
                } else if (clickText(root, "เข้าสู่ระบบ")) {
                    acted()
                } else {
                    waitOrBlock("ไม่พบปุ่มเข้าสู่ระบบ")
                }
            }

            Step.ENTER_ID -> {
                val id = config.accounts.getOrNull(AutomationSession.accountIndex)
                if (id == null) {
                    finishAll()
                    return
                }
                val field = findEditable(root, "เบอร์มือถือ", "อีเมล")
                if (field != null && setText(field, id)) {
                    move(Step.SUBMIT_ID, "กรอก ID บัญชี ${AutomationSession.accountIndex + 1}/${config.accounts.size}")
                } else {
                    waitOrBlock("ไม่พบช่องเบอร์มือถือหรืออีเมล")
                }
            }

            Step.SUBMIT_ID -> {
                if (clickText(root, "เริ่มช้อปเลย") || clickText(root, "ต่อไป")) {
                    move(Step.ENTER_PASSWORD, "รอหน้ากรอกรหัสผ่าน")
                } else if (hasEditable(root, "รหัสผ่าน")) {
                    move(Step.ENTER_PASSWORD, "พบช่องรหัสผ่าน")
                } else {
                    waitOrBlock("ไม่พบปุ่มไปยังหน้ารหัสผ่าน")
                }
            }

            Step.ENTER_PASSWORD -> {
                val field = findEditable(root, "รหัสผ่าน")
                if (field != null && setText(field, config.password)) {
                    move(Step.SUBMIT_PASSWORD, "กรอกรหัสผ่านกลาง")
                } else {
                    waitOrBlock("ไม่พบช่องรหัสผ่าน")
                }
            }

            Step.SUBMIT_PASSWORD -> {
                if (clickText(root, "เข้าสู่ระบบ")) {
                    move(Step.WAIT_HOME, "กำลังเข้าสู่ระบบ")
                } else {
                    waitOrBlock("ไม่พบปุ่มยืนยันรหัสผ่าน")
                }
            }

            Step.WAIT_HOME -> {
                if (isHome(root)) {
                    AutomationSession.productIndex = 0
                    quantityAdded = 0
                    move(Step.ENTER_PRODUCT, "เข้าสู่ระบบแล้ว เริ่มค้นหาสินค้า")
                } else {
                    waitOrBlock("เข้าสู่ระบบไม่สำเร็จหรือหน้า Makro PRO ไม่ตอบสนอง")
                }
            }

            Step.ENTER_PRODUCT -> {
                val product = config.products.getOrNull(AutomationSession.productIndex)
                if (product == null) {
                    move(Step.OPEN_CART, "เพิ่มสินค้าครบแล้ว เปิดตะกร้า")
                    return
                }
                val field = findEditable(root, "ค้นหาสินค้า")
                if (field != null && setText(field, product.code)) {
                    move(Step.SUBMIT_SEARCH, "ค้นหารหัสสินค้า ${product.code}")
                } else {
                    waitOrBlock("ไม่พบช่องค้นหาสินค้า")
                }
            }

            Step.SUBMIT_SEARCH -> {
                val field = findEditable(root, "ค้นหาสินค้า")
                val sent = tapNormalized(0.94f, 0.875f)
                if (!sent) performGlobalAction(GLOBAL_ACTION_BACK)
                move(Step.WAIT_RESULTS, "รอผลค้นหาสินค้า")
            }

            Step.WAIT_RESULTS -> {
                if (containsAny(root, "ไม่พบสินค้า", "ไม่พบสินค้าที่ค้นหา")) {
                    move(Step.BLOCKED, "ไม่พบสินค้า ${config.products.getOrNull(AutomationSession.productIndex)?.code}")
                    AutomationSession.running = false
                    AutomationSession.config = null
                } else if (hasPlusControl(root)) {
                    quantityAdded = 0
                    move(Step.ADD_PRODUCT, "พบผลค้นหา กำลังเพิ่มจำนวน")
                } else {
                    waitOrBlock("ไม่พบปุ่มเพิ่มสินค้า (+) ในผลค้นหา")
                }
            }

            Step.ADD_PRODUCT -> {
                val product = config.products.getOrNull(AutomationSession.productIndex) ?: return
                if (quantityAdded < product.quantity) {
                    if (clickPlus(root)) {
                        quantityAdded += 1
                        AutomationSession.status = "เพิ่ม ${product.code}: ${quantityAdded}/${product.quantity}"
                        acted()
                        if (quantityAdded >= product.quantity) {
                            move(Step.ENTER_PRODUCT, "เพิ่ม ${product.code} ครบ ${product.quantity} หน่วย")
                            AutomationSession.productIndex += 1
                        }
                    } else {
                        waitOrBlock("กดปุ่ม + เพื่อเพิ่มจำนวนสินค้าไม่ได้")
                    }
                } else {
                    AutomationSession.productIndex += 1
                    move(Step.ENTER_PRODUCT, "เตรียมค้นหาสินค้ารายการถัดไป")
                }
            }

            Step.OPEN_CART -> {
                if (containsAny(root, "ตะกร้า(", "ตะกร้าสินค้า")) {
                    move(Step.OPEN_ADDRESS, "อยู่ในตะกร้าแล้ว")
                } else if (clickText(root, "ตะกร้า", "รถเข็น", "cart")) {
                    acted()
                } else if (tapNormalized(0.90f, 0.080f)) {
                    acted()
                } else {
                    waitOrBlock("ไม่พบไอคอนตะกร้าสินค้า")
                }
            }

            Step.OPEN_ADDRESS -> {
                if (containsAny(root, "เลือกพิกัดที่อยู่")) {
                    move(Step.SEARCH_LOCATION, "เปิดหน้าปักหมุดที่อยู่")
                } else if (clickText(root, "โปรดระบุที่จัดส่ง", "จัดส่งที่", "เลือกที่อยู่", "เพิ่มที่อยู่")) {
                    acted()
                } else {
                    waitOrBlock("ไม่พบแถบเลือกที่อยู่จัดส่งในตะกร้า")
                }
            }

            Step.SEARCH_LOCATION -> {
                val field = findEditable(root, "ค้นหาสถานที่", "ค้นหาที่อยู่", "ค้นหาพิกัด")
                    ?: findEditable(root)
                if (field != null && setText(field, config.deliverySearch)) {
                    move(Step.SELECT_LOCATION, "ค้นหาหมุด ${config.deliverySearch}")
                    tapNormalized(0.94f, 0.875f)
                } else {
                    waitOrBlock("ไม่พบช่องค้นหาสถานที่บนแผนที่")
                }
            }

            Step.SELECT_LOCATION -> {
                val exact = allNodes(root).firstOrNull { n ->
                    n.isVisibleToUser && !n.isEditable && nodeText(n).contains("the kim")
                }
                if (exact != null) {
                    if (clickNode(exact)) move(Step.CONFIRM_LOCATION, "เลือกผลค้นหา The Kim")
                } else if (containsAny(root, "สร้างที่อยู่", "ข้อมูลการติดต่อ")) {
                    move(Step.EDIT_CONTACT, "เข้าสู่แบบฟอร์มสร้างที่อยู่")
                } else if (locationScrollAttempts < 5 &&
                    SystemClock.uptimeMillis() - lastActionAt > 1_200L) {
                    if (scrollLocationResults(root)) {
                        locationScrollAttempts += 1
                        lastActionAt = SystemClock.uptimeMillis()
                        AutomationSession.status = "เลื่อนดูผลค้นหา The Kim (ครั้งที่ ${locationScrollAttempts})"
                    } else {
                        waitOrBlock("ไม่สามารถเลื่อนผลค้นหาสถานที่ได้")
                    }
                } else if (locationScrollAttempts >= 5) {
                    move(Step.BLOCKED, "ไม่พบผลค้นหาที่ตรงกับ The Kim — หยุดเพื่อป้องกันการปักหมุดผิด")
                    AutomationSession.running = false
                    AutomationSession.config = null
                }
            }

            Step.CONFIRM_LOCATION -> {
                if (containsAny(root, "สร้างที่อยู่", "ข้อมูลการติดต่อ")) {
                    move(Step.EDIT_CONTACT, "ได้พิกัดแล้ว เปิดข้อมูลติดต่อ")
                } else if (clickText(root, "ยืนยันตำแหน่ง", "ใช้ตำแหน่งนี้", "ยืนยัน", "บันทึกพิกัด")) {
                    acted()
                } else {
                    waitOrBlock("ไม่พบปุ่มยืนยันตำแหน่งบนแผนที่")
                }
            }

            Step.WAIT_ADDRESS_FORM -> move(Step.EDIT_CONTACT, "เปิดแบบฟอร์มข้อมูลที่อยู่")

            Step.EDIT_CONTACT -> {
                if (hasEditable(root, "เบอร์โทรศัพท์")) {
                    move(Step.ENTER_PHONE, "แก้ไขเบอร์โทรศัพท์ผู้รับ")
                } else if (clickText(root, "แก้ไขข้อมูลการติดต่อ", "แก้ไข")) {
                    acted()
                } else if (tapNormalized(0.89f, 0.135f)) {
                    acted()
                } else {
                    waitOrBlock("ไม่พบปุ่มแก้ไขข้อมูลการติดต่อ")
                }
            }

            Step.ENTER_PHONE -> {
                val field = findEditable(root, "เบอร์โทรศัพท์")
                if (field != null && setText(field, config.phone)) {
                    move(Step.SAVE_CONTACT, "กรอกเบอร์โทรศัพท์แล้ว")
                } else {
                    waitOrBlock("ไม่พบช่องเบอร์โทรศัพท์ในหน้าต่างข้อมูลติดต่อ")
                }
            }

            Step.SAVE_CONTACT -> {
                // In the recording, the contact editor is a modal with an X in its
                // upper-right corner; there is no separate Save button in that modal.
                if (clickText(root, "ปิด", "close")) {
                    move(Step.ENTER_ADDRESS, "ปิดหน้าต่างข้อมูลติดต่อแล้ว")
                } else if (tapNormalized(0.885f, 0.165f)) {
                    move(Step.ENTER_ADDRESS, "ปิดหน้าต่างข้อมูลติดต่อแล้ว")
                } else {
                    waitOrBlock("ปิดหน้าต่างข้อมูลติดต่อไม่ได้")
                }
            }

            Step.ENTER_ADDRESS -> {
                val field = findEditable(root, "ที่อยู่")
                if (field != null && setText(field, config.address)) {
                    move(Step.SAVE_ADDRESS, "กรอกที่อยู่ ${config.address}")
                } else {
                    waitOrBlock("ไม่พบช่องที่อยู่")
                }
            }

            Step.SAVE_ADDRESS -> {
                if (containsAny(root, "เพิ่มที่อยู่สำหรับจัดส่งแล้ว", "เพิ่มที่อยู่เรียบร้อย")) {
                    move(Step.OPEN_MENU, "บันทึกที่อยู่แล้ว เตรียมออกจากระบบ")
                } else if (clickText(root, "บันทึก")) {
                    acted()
                } else {
                    waitOrBlock("ไม่พบปุ่มบันทึกที่อยู่")
                }
            }

            Step.OPEN_MENU -> {
                if (containsAny(root, "ออกจากระบบ")) {
                    move(Step.CLICK_LOGOUT, "พบเมนูออกจากระบบ")
                } else if (clickText(root, "เมนู", "menu")) {
                    acted()
                } else if (tapNormalized(0.10f, 0.080f)) {
                    acted()
                } else {
                    waitOrBlock("เปิดเมนูบัญชีไม่ได้")
                }
            }

            Step.CLICK_LOGOUT -> {
                if (clickText(root, "ออกจากระบบ")) {
                    move(Step.WAIT_LOGOUT, "กำลังออกจากระบบ")
                } else {
                    waitOrBlock("ไม่พบเมนูออกจากระบบ")
                }
            }

            Step.WAIT_LOGOUT -> {
                if (containsAny(root, "ยินดีต้อนรับ", "เข้าสู่ระบบ", "ลงทะเบียน")) {
                    continueAfterLogout(config)
                } else if (isHome(root)) {
                    if (tapNormalized(0.10f, 0.055f)) {
                        continueAfterLogout(config)
                    } else {
                        waitOrBlock("เปิดเมนูหลังออกจากระบบไม่ได้")
                    }
                } else {
                    waitOrBlock("ออกจากระบบไม่สำเร็จหรือไม่พบหน้าเข้าสู่ระบบ")
                }
            }

            Step.DONE, Step.BLOCKED -> Unit
        }
    }

    private fun continueAfterLogout(config: BotConfig) {
        if (loggingOutInitialSession) {
            loggingOutInitialSession = false
            move(Step.CLICK_LOGIN, "ออกจากระบบเดิมแล้ว เริ่มบัญชีแรก")
            return
        }
        val next = AutomationSession.accountIndex + 1
        if (next >= config.accounts.size) {
            finishAll()
        } else {
            AutomationSession.accountIndex = next
            AutomationSession.productIndex = 0
            quantityAdded = 0
            move(Step.CLICK_LOGIN, "ออกจากระบบแล้ว เริ่มบัญชี ${next + 1}/${config.accounts.size}")
        }
    }

    private fun finishAll() {
        move(Step.DONE, "ครบทุกบัญชีแล้ว — ไม่ได้กด Checkout หรือชำระเงิน")
        AutomationSession.running = false
        AutomationSession.config = null
    }

    private fun acted() {
        lastActionAt = SystemClock.uptimeMillis()
    }

    private fun allNodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (root == null) return emptyList()
        val out = mutableListOf<AccessibilityNodeInfo>()
        fun visit(n: AccessibilityNodeInfo) {
            out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { visit(it) }
        }
        visit(root)
        return out
    }

    private fun nodeText(n: AccessibilityNodeInfo): String = listOfNotNull(
        n.text?.toString(), n.contentDescription?.toString(), n.hintText?.toString(),
        n.viewIdResourceName?.toString()
    ).joinToString(" ").lowercase()

    private fun findText(root: AccessibilityNodeInfo, vararg tokens: String): AccessibilityNodeInfo? =
        allNodes(root).firstOrNull { n ->
            n.isVisibleToUser && tokens.any { nodeText(n).contains(it.lowercase()) }
        }

    private fun containsAny(root: AccessibilityNodeInfo, vararg tokens: String): Boolean =
        findText(root, *tokens) != null

    private fun findEditable(root: AccessibilityNodeInfo, vararg tokens: String): AccessibilityNodeInfo? =
        allNodes(root).firstOrNull { n ->
            n.isVisibleToUser && n.isEditable &&
                (tokens.isEmpty() || tokens.any { nodeText(n).contains(it.lowercase()) })
        }

    private fun hasEditable(root: AccessibilityNodeInfo, vararg tokens: String): Boolean =
        findEditable(root, *tokens) != null

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 7) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty) return tapAt(rect.centerX().toFloat(), rect.centerY().toFloat())
        return false
    }

    private fun clickText(root: AccessibilityNodeInfo, vararg tokens: String): Boolean {
        val candidates = allNodes(root).filter { n ->
            n.isVisibleToUser && tokens.any { nodeText(n).contains(it.lowercase()) }
        }
        val clickable = candidates.firstOrNull { it.isClickable && it.isEnabled }
        if (clickable != null) return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val candidate = candidates.firstOrNull() ?: return false
        return clickNode(candidate)
    }

    private fun hasPlusControl(root: AccessibilityNodeInfo): Boolean =
        allNodes(root).any { isPlusNode(it) }

    private fun isPlusNode(n: AccessibilityNodeInfo): Boolean {
        val s = nodeText(n)
        return n.isVisibleToUser && (s.trim() == "+" || s.contains("เพิ่มจำนวน") ||
            s.contains("เพิ่มสินค้า") || s.contains("add to cart") || s.contains("increase quantity"))
    }

    private fun clickPlus(root: AccessibilityNodeInfo): Boolean {
        val candidate = allNodes(root).firstOrNull { isPlusNode(it) && it.isClickable && it.isEnabled }
            ?: allNodes(root).firstOrNull { isPlusNode(it) }
        if (candidate != null) return clickNode(candidate)

        // The recording shows the first result's red circular "+" at about
        // x=47%, y=75% of the portrait viewport. Use it only when no accessible
        // plus node exists; the next screen-state tick still checks the page.
        return tapNormalized(0.47f, 0.75f)
    }

    private fun isHome(root: AccessibilityNodeInfo): Boolean =
        containsAny(root, "ค้นหาสินค้า") && containsAny(root, "PRO Mall", "หมวดหมู่", "รวมแบรนด์เด็ด")

    private fun scrollLocationResults(root: AccessibilityNodeInfo): Boolean {
        val scrollable = allNodes(root).firstOrNull { it.isVisibleToUser && it.isScrollable }
        if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            return true
        }
        if (Build.VERSION.SDK_INT < 24) return false
        val metrics = resources.displayMetrics
        val path = Path().apply {
            moveTo(metrics.widthPixels * 0.50f, metrics.heightPixels * 0.43f)
            lineTo(metrics.widthPixels * 0.50f, metrics.heightPixels * 0.27f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 420))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun tapNormalized(x: Float, y: Float): Boolean {
        val metrics = resources.displayMetrics
        return tapAt(metrics.widthPixels * x, metrics.heightPixels * y)
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        return dispatchGesture(gesture, null, null)
    }
}
