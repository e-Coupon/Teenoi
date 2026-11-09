package th.ecoupon.makrobot

data class ProductTask(val code: String, val quantity: Int)
data class BotConfig(
    val accounts: List<String>,
    val password: String,
    val products: List<ProductTask>,
    val deliverySearch: String,
    val phone: String,
    val address: String,
    val targetPackage: String
)

/** Runtime-only session. Credentials are deliberately never written to preferences or files. */
object AutomationSession {
    @Volatile var config: BotConfig? = null
    @Volatile var pendingStart: Boolean = false
    @Volatile var running: Boolean = false
    @Volatile var status: String = "ยังไม่ได้เริ่ม"
    @Volatile var accountIndex: Int = 0
    @Volatile var productIndex: Int = 0
    @Volatile var service: MakroAccessibilityService? = null

    @Synchronized fun start(value: BotConfig) {
        config = value
        accountIndex = 0
        productIndex = 0
        status = "เตรียมเปิดแอป Makro PRO ที่ติดตั้งแยก"
        running = true
        pendingStart = true
        service?.beginFromPending()
    }

    @Synchronized fun stop(message: String = "ผู้ใช้สั่งหยุด") {
        running = false
        pendingStart = false
        status = message
        config = null
        service?.releaseRunResources()
    }
}
