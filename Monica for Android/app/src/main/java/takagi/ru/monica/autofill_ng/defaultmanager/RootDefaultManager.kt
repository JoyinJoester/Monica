package takagi.ru.monica.autofill_ng.defaultmanager

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import takagi.ru.monica.autofill_ng.privilege.PrivilegeAccess

/** Local adapter, called only inside the opted-in root transaction on Dispatchers.IO. */
internal class RootDefaultManager(private val context: Context) : IMonicaDefaultManager.Stub() {
    private val userId = android.os.Process.myUid() / 100_000
    private val controller = SettingsController(ShellSettingsStore { arguments ->
        CommandResult(0, PrivilegeAccess.settings(arguments))
    })

    override fun read(): Bundle = controller.read(userId).toBundle().apply { putBoolean("ok", true) }

    override fun apply(expected: Bundle, includeAutofill: Boolean, keepOthers: Boolean): Bundle {
        val before = expected.toSnapshot().also { require(it.userId == userId) }
        validate(MonicaDefaultServices.credential, "android.permission.BIND_CREDENTIAL_PROVIDER_SERVICE")
        if (includeAutofill) validate(MonicaDefaultServices.autofill, "android.permission.BIND_AUTOFILL_SERVICE")
        return controller.apply(before, MonicaDefaultServices.credential,
            MonicaDefaultServices.autofill.takeIf { includeAutofill }, keepOthers).toBundle().apply { putBoolean("ok", true) }
    }

    override fun restore(expected: Bundle, previous: Bundle): Bundle {
        val before = expected.toSnapshot().also { require(it.userId == userId) }
        val target = previous.toSnapshot().also { require(it.userId == userId) }
        return controller.replace(before, target).toBundle().apply { putBoolean("ok", true) }
    }

    @Suppress("DEPRECATION")
    private fun validate(component: String, permission: String) {
        val name = requireNotNull(ComponentName.unflattenFromString(component))
        require(name.packageName == context.packageName)
        val pm = context.packageManager
        val info = pm.getServiceInfo(name, 0)
        val setting = pm.getComponentEnabledSetting(name)
        check(info.applicationInfo.enabled && info.permission == permission)
        check(setting == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (setting == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && info.enabled))
    }

    override fun destroy() = Unit
}
