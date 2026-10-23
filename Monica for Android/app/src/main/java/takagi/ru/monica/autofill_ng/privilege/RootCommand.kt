package takagi.ru.monica.autofill_ng.privilege

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class RootTimeout : Exception()
internal data class RootResult(val code: Int, val output: String)

/** No user-provided shell text: only identity and the four settings used by these features. */
internal object RootCommand {
    private val keys = setOf("enabled_accessibility_services", "credential_service", "credential_service_primary", "autofill_service")

    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun settings(arguments: List<String>): String {
        require(arguments.size in 6..7 && arguments[0] == "/system/bin/settings" && arguments[1] == "--user")
        require(arguments[2].toIntOrNull() in 0..21474 && arguments[4] == "secure" && arguments[5] in keys)
        require(arguments[3] in setOf("get", "put", "delete"))
        require((arguments[3] == "put") == (arguments.size == 7))
        arguments.getOrNull(6)?.let { require(it.length <= 65536 && '\u0000' !in it && '\n' !in it && '\r' !in it) }
        return "[ \"$(/system/bin/id -u)\" = 0 ] || exit 77; exec " + arguments.joinToString(" ", transform = ::quote)
    }

    fun recovery(apk: String, userId: Int): String {
        require(apk.startsWith('/') && apk.endsWith(".apk") && apk.length <= 4096 && apk.none { it == '\u0000' || it == '\n' || it == '\r' })
        require(userId in 0..21474)
        return "[ \"$(/system/bin/id -u)\" = 0 ] || exit 77; CLASSPATH=" + quote(apk) + " exec " +
            listOf("/system/bin/app_process", "/system/bin", "takagi.ru.monica.autofill_ng.privilege.RootAccessibilityMain", userId.toString())
                .joinToString(" ", transform = ::quote)
    }

    fun execute(command: String, timeoutSeconds: Long = 8): RootResult =
        runProcess(listOf("su", "-c", command), timeoutSeconds)

    // Separate from Android so missing binaries, denial, output limits and deadlines can be tested.
    fun runProcess(arguments: List<String>, timeoutSeconds: Long): RootResult {
        val process = ProcessBuilder(arguments).redirectErrorStream(true).start()
        val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "MonicaRootOutput").apply { isDaemon = true } }
        val output = reader.submit<String> {
            process.inputStream.bufferedReader().use { stream ->
                val text = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    check(text.length + count <= 131072) { "Root response too large" }
                    text.append(buffer, 0, count)
                }
                text.toString()
            }
        }
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) throw RootTimeout()
            return RootResult(process.exitValue(), output.get(1, TimeUnit.SECONDS))
        } finally {
            process.destroyForcibly()
            output.cancel(true)
            reader.shutdownNow()
            runCatching { process.inputStream.close() }
            runCatching { process.outputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }
}
