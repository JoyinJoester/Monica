package takagi.ru.monica.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import takagi.ru.monica.MainActivity
import takagi.ru.monica.data.AppLauncherIcon

object AppLauncherIconManager {
    private const val COMPAT_MODERN_ALIAS = "takagi.ru.monica.ModernLauncherAlias"
    private const val COMPAT_CLASSIC_ALIAS = "takagi.ru.monica.LockLauncherAlias"
    private const val HOME_MODERN_ALIAS = "takagi.ru.monica.ModernHomeLauncherAlias"
    private const val HOME_CLASSIC_ALIAS = "takagi.ru.monica.ClassicHomeLauncherAlias"
    private const val VISIBLE_MODERN_ALIAS = "takagi.ru.monica.ModernVisibleLauncherAlias"
    private const val VISIBLE_CLASSIC_ALIAS = "takagi.ru.monica.ClassicVisibleLauncherAlias"

    fun apply(context: Context, icon: AppLauncherIcon) {
        repairCompatibilityLaunchTargets(context)
        applyHomeLauncherSelection(context, icon)
    }

    fun repairLegacyDisabledComponents(context: Context) {
        repairCompatibilityLaunchTargets(context)
    }

    fun repairLaunchEntryPointsAfterUpgrade(context: Context, icon: AppLauncherIcon) {
        repairCompatibilityLaunchTargets(context)
        applyHomeLauncherSelection(context, icon)
    }

    private fun repairCompatibilityLaunchTargets(context: Context) {
        val packageManager = context.packageManager
        val components = listOf(
            ComponentName(context, MainActivity::class.java),
            ComponentName(context, COMPAT_MODERN_ALIAS),
            ComponentName(context, COMPAT_CLASSIC_ALIAS),
            ComponentName(context, HOME_MODERN_ALIAS),
            ComponentName(context, HOME_CLASSIC_ALIAS)
        )

        components.forEach { component ->
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    private fun applyHomeLauncherSelection(context: Context, icon: AppLauncherIcon) {
        val packageManager = context.packageManager
        val modernHome = ComponentName(context, VISIBLE_MODERN_ALIAS)
        val classicHome = ComponentName(context, VISIBLE_CLASSIC_ALIAS)

        val modernState = if (icon == AppLauncherIcon.MODERN) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val classicState = if (icon == AppLauncherIcon.LOCK_CLASSIC) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.setComponentEnabledSettings(
                listOf(
                    PackageManager.ComponentEnabledSetting(
                        modernHome,
                        modernState,
                        PackageManager.DONT_KILL_APP
                    ),
                    PackageManager.ComponentEnabledSetting(
                        classicHome,
                        classicState,
                        PackageManager.DONT_KILL_APP
                    )
                )
            )
            return
        }

        packageManager.setComponentEnabledSetting(
            modernHome,
            modernState,
            PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(
            classicHome,
            classicState,
            PackageManager.DONT_KILL_APP
        )
    }
}
