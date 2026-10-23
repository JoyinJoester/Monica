package takagi.ru.monica.autofill_ng.privilege

import android.os.Process
import androidx.annotation.Keep
import takagi.ru.monica.autofill_ng.protection.AccessibilityServiceRestart
import takagi.ru.monica.autofill_ng.protection.AccessibilityServiceSettings
import kotlin.system.exitProcess

/** One-shot app_process helper: finish restoration even if the unprivileged client dies. */
@Keep
object RootAccessibilityMain {
    @JvmStatic
    fun main(arguments: Array<String>) {
        if (Process.myUid() != 0) {
            System.err.println("MONICA_ROOT_REQUIRED")
            exitProcess(77)
        }
        require(arguments.size == 1)
        val userId = arguments[0].toInt().also { require(it in 0..21474) }
        val result = AccessibilityServiceRestart(object : AccessibilityServiceSettings {
            override fun readEnabledServices(): String {
                val value = command("get").trimEnd('\n', '\r')
                check(value.length <= 65536 && '\n' !in value && '\r' !in value)
                return if (value == "null") "" else value
            }

            override fun writeEnabledServices(value: String) { command("put", value) }

            private fun command(operation: String, value: String? = null): String {
                val args = listOf("/system/bin/settings", "--user", userId.toString(), operation,
                    "secure", "enabled_accessibility_services") + listOfNotNull(value)
                // Validate the same allowlist; execute argv directly as this process is already root.
                RootCommand.settings(args)
                val result = RootCommand.runProcess(args, 3)
                check(result.code == 0 && !Regex("(?im)^\\s*(?:error|.*exception|permission denial|permission denied)").containsMatchIn(result.output))
                return result.output
            }
        }, awaitDisconnect = { Thread.sleep(350) }).restart()
        println("MONICA_RECOVERY:${result.code}")
    }
}
