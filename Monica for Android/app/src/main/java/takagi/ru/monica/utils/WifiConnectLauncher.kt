package takagi.ru.monica.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import takagi.ru.monica.R
import takagi.ru.monica.data.model.WifiData
import takagi.ru.monica.data.model.WifiSecurity

/**
 * 一键「跳到系统 Wi-Fi 设置 + 复制密码」。
 *
 * 之前版本优先走 [Settings.ACTION_WIFI_ADD_NETWORKS]：理论上 Android 11+
 * 会弹「加入此网络」确认面板。但在 HarmonyOS / 部分 MIUI ROM 上，
 * `resolveActivity` 返回非空却实际不弹 UI —— 用户看到 Toast 提示却什么都没
 * 发生。为了让行为在所有设备上可预测，这里统一改成：
 *
 *   1. 把密码写到剪贴板（如果有）
 *   2. 打开系统 Wi-Fi 设置页
 *   3. Toast 告诉用户去点击对应网络并粘贴密码
 *
 * 如果用户之前连接过同 SSID 的网络，系统通常会自动填充密码；如果没有，
 * 他们在弹出的密码框里长按粘贴即可。没有 OEM 依赖，行为稳定。
 */
object WifiConnectLauncher {
    private const val TAG = "WifiConnectLauncher"

    sealed interface Result {
        /** 已跳转到 Wi-Fi 设置页；密码可能已经复制到剪贴板。 */
        data class OpenedSettings(val passwordCopied: Boolean) : Result

        /** 完全失败（设备没有 Wi-Fi 设置页），几乎不会发生。 */
        data object Failed : Result
    }

    fun launch(context: Context, wifi: WifiData, password: String): Result {
        // 开放网络无需密码，也不用走剪贴板；有密码条目就一律尝试复制。
        val shouldCopy = wifi.security != WifiSecurity.NONE && password.isNotBlank()
        val copied = if (shouldCopy) copyPassword(context, password) else false

        val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(intent) }
                .onFailure { Log.w(TAG, "ACTION_WIFI_SETTINGS dispatch failed", it) }
            val msg = when {
                copied -> context.getString(R.string.wifi_connect_fallback_copied)
                else -> context.getString(R.string.wifi_connect_fallback_no_copy)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            Result.OpenedSettings(passwordCopied = copied)
        } else {
            Result.Failed
        }
    }

    private fun copyPassword(context: Context, password: String): Boolean {
        ClipboardUtils.copyToClipboard(
            context = context,
            text = password,
            label = "WIFI password",
            sensitive = true
        )
        return true
    }
}
