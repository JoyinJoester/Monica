package takagi.ru.monica.bitwarden

import android.content.Context
import android.content.SharedPreferences

/**
 * 轻量存储 Bitwarden vault 的 `profile.premium` 字段。
 *
 * Bitwarden 附件功能在服务端要求 Premium 账户。同步阶段把 `profile.premium` 记下来，
 * 供 UI 层（批量移动 / 附件上传入口）判断是否要弹 Attachment_Aware_Move_Dialog 或
 * 直接禁用上传按钮。
 *
 * 选择 SharedPreferences 而不是扩 Room，是为了避免再加一次 migration；该状态天然
 * 是缓存（每次 sync 覆盖），丢失也不会造成数据损坏。
 */
object BitwardenVaultPremiumStore {
    private const val PREFS_NAME = "bitwarden_vault_premium"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 记录某 vault 的 premium 状态；未知 / 未 sync 时应 `clear`。 */
    fun setPremium(context: Context, vaultId: Long, premium: Boolean) {
        prefs(context).edit().putBoolean(key(vaultId), premium).apply()
    }

    /** 读取某 vault 的 premium 状态；默认 `false`（保守策略，按免费账户处理）。 */
    fun isPremium(context: Context, vaultId: Long): Boolean =
        prefs(context).getBoolean(key(vaultId), false)

    fun clear(context: Context, vaultId: Long) {
        prefs(context).edit().remove(key(vaultId)).apply()
    }

    private fun key(vaultId: Long): String = "vault_${vaultId}_premium"
}
