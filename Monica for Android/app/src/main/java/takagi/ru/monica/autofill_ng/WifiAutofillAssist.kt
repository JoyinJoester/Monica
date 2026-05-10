package takagi.ru.monica.autofill_ng

import takagi.ru.monica.data.PasswordEntry

/**
 * WIFI 条目的自动填充补丁。
 *
 * 问题：Android 系统 Wi-Fi 密码输入框的 AutofillRequest 通常来自
 * `com.android.settings` 或 OEM 变体，里面没有 webDomain，也没有和
 * WIFI 条目匹配的 appPackageName，所以 [BitwardenLikeAutofillMatcherNg]
 * 永远给不出候选。用户明明在 Monica 里保存了一堆 Wi-Fi，却推不出来。
 *
 * 解决思路（保守版）：
 *   1. 如果当前请求来自一组"Wi-Fi 设置类"包名，就把所有 WIFI 条目
 *      作为补充候选插到结果最前面
 *   2. 不尝试抓当前 SSID（那需要 ACCESS_WIFI_STATE / 定位权限，
 *      不值得为这一个场景新增运行时权限）
 *   3. 普通登录场景一律不返回任何 WIFI 条目，避免给浏览器/App 登录框
 *      塞无关建议
 */
object WifiAutofillAssist {

    // 常见系统 Wi-Fi 设置页的 packageName；覆盖原生 + 主流 OEM。
    // 未命中的情况下认为不是 Wi-Fi 设置场景，不触发补充推荐。
    private val WIFI_SETTINGS_PACKAGES = setOf(
        "com.android.settings",          // AOSP / 多数 OEM
        "com.android.tv.settings",       // Android TV
        "com.google.android.apps.wearable.settings",
        "com.google.android.tv.frameworkpackagestubs",
        "com.oneplus.settings",
        "com.oppo.settings",
        "com.coloros.settings",
        "com.miui.securitycenter",       // MIUI Wi-Fi 有时托管在安全中心
        "com.android.settings.intelligence",
        "com.huawei.systemmanager",
        "com.sec.android.app.launcher",  // 个别三星跳转
        "com.samsung.android.settings.wifi"
    )

    fun isWifiSettingsPackage(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        return packageName.lowercase() in WIFI_SETTINGS_PACKAGES ||
            packageName.contains("settings", ignoreCase = true) &&
            packageName.contains("wifi", ignoreCase = true)
    }

    /**
     * 在常规匹配结果之上，如果请求看起来来自 Wi-Fi 设置页，就把 WIFI 条目
     * 推到最前。已经被 matcher 命中的条目不会重复出现。
     */
    fun augmentWithWifiEntries(
        originalRanked: List<PasswordEntry>,
        allEntries: List<PasswordEntry>,
        packageName: String?,
        maxSuggestions: Int
    ): List<PasswordEntry> {
        if (!isWifiSettingsPackage(packageName)) return originalRanked
        val wifiEntries = allEntries.filter { it.isWifiEntry() }
        if (wifiEntries.isEmpty()) return originalRanked

        // 排序：收藏置顶，再按最近更新时间；既可预测又避免频繁变化。
        val sortedWifi = wifiEntries.sortedWith(
            compareByDescending<PasswordEntry> { it.isFavorite }
                .thenByDescending { it.updatedAt.time }
        )

        val existingIds = originalRanked.map { it.id }.toHashSet()
        val additions = sortedWifi.filter { it.id !in existingIds }
        if (additions.isEmpty()) return originalRanked

        return (additions + originalRanked).take(maxSuggestions.coerceAtLeast(1))
    }
}
