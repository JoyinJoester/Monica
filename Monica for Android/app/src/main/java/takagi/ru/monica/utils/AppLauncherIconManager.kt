package takagi.ru.monica.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import takagi.ru.monica.MainActivity
import takagi.ru.monica.R
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

    fun getCurrentSelection(context: Context): AppLauncherIcon {
        val packageManager = context.packageManager
        val classicHome = ComponentName(context, VISIBLE_CLASSIC_ALIAS)
        val modernHome = ComponentName(context, VISIBLE_MODERN_ALIAS)

        if (packageManager.getComponentEnabledSetting(classicHome) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) {
            return AppLauncherIcon.LOCK_CLASSIC
        }

        if (packageManager.getComponentEnabledSetting(modernHome) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        ) {
            return AppLauncherIcon.MODERN
        }

        return AppLauncherIcon.LOCK_CLASSIC
    }

    fun resolveBrandingIconRes(context: Context): Int {
        return when (getCurrentSelection(context)) {
            AppLauncherIcon.MODERN -> R.drawable.monica_launcher
            AppLauncherIcon.LOCK_CLASSIC -> R.mipmap.ic_launcher_lock
        }
    }

    fun applyBiometricPromptBranding(context: Context, promptInfoBuilder: Any) {
        val builderClass = promptInfoBuilder.javaClass
        val iconRes = resolveBrandingIconRes(context)

        runCatching {
            builderClass.methods.firstOrNull { method ->
                method.name == "setLogoRes" &&
                    method.parameterTypes.size == 1 &&
                    method.parameterTypes[0] == Int::class.javaPrimitiveType
            }?.invoke(promptInfoBuilder, iconRes)
        }

        runCatching {
            builderClass.methods.firstOrNull { method ->
                method.name == "setLogoDescription" &&
                    method.parameterTypes.size == 1 &&
                    CharSequence::class.java.isAssignableFrom(method.parameterTypes[0])
            }?.invoke(promptInfoBuilder, context.getString(R.string.app_name))
        }
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
