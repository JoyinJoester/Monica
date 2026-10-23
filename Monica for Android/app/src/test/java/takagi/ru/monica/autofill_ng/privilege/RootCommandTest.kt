package takagi.ru.monica.autofill_ng.privilege

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RootCommandTest {
    private val args = listOf("/system/bin/settings", "--user", "10", "put", "secure", "enabled_accessibility_services")

    @Test fun shellMetacharactersRemainInOneQuotedArgument() {
        val value = "pkg/.Service:'; \$(touch /bad); `id`"
        val command = RootCommand.settings(args + value)
        assertTrue(command.startsWith("[ \"\$(/system/bin/id -u)\" = 0 ] || exit 77; exec "))
        assertTrue(command.endsWith("'pkg/.Service:'\\''; \$(touch /bad); `id`'"))
        assertTrue(RootCommand.settings(args + "").endsWith("''"))
    }

    @Test fun onlyKnownSettingsForAnExplicitUserAreAccepted() {
        val invalid = listOf(args.toMutableList().apply { this[5] = "other_setting" } + "x",
            args.toMutableList().apply { this[2] = "-1" } + "x",
            args.toMutableList().apply { this[4] = "global" } + "x",
            args.toMutableList().apply { this[0] = "sh" } + "x",
            args + "line\nbreak", args + "null\u0000byte", args,
            args.toMutableList().apply { this[3] = "get" } + "x")
        invalid.forEach { command -> assertThrows(IllegalArgumentException::class.java) { RootCommand.settings(command) } }
    }

    @Test fun recoveryStartsOnlyTheFixedHelperForAnExplicitUser() {
        val command = RootCommand.recovery("/data/app/Monica/base.apk", 10)
        assertTrue(command.contains("CLASSPATH='/data/app/Monica/base.apk' exec '/system/bin/app_process'"))
        assertTrue(command.endsWith("'takagi.ru.monica.autofill_ng.privilege.RootAccessibilityMain' '10'"))
        assertThrows(IllegalArgumentException::class.java) { RootCommand.recovery("relative.apk", 10) }
        assertThrows(IllegalArgumentException::class.java) { RootCommand.recovery("/data/app/base.apk", -1) }
    }

    private fun fixture(mode: String) = listOf(
        File(System.getProperty("java.home"), "bin/java").path, "-cp",
        File(RootProcessFixture::class.java.protectionDomain.codeSource.location.toURI()).path,
        RootProcessFixture::class.java.name, mode,
    )

    @Test fun successAndDenialKeepExitCodeAndOutput() {
        assertEquals(RootResult(0, "0\n"), RootCommand.runProcess(fixture("ok"), 5))
        assertEquals(RootResult(1, "denied"), RootCommand.runProcess(fixture("denied"), 5))
    }

    @Test fun missingSuAndTimeoutReturnControl() {
        assertThrows(java.io.IOException::class.java) { RootCommand.runProcess(listOf("monica-missing-su-executable"), 1) }
        val start = System.nanoTime()
        assertThrows(RootTimeout::class.java) { RootCommand.runProcess(fixture("wait"), 1) }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5000)
    }

    @Test fun excessiveOutputIsBounded() {
        assertThrows(Exception::class.java) { RootCommand.runProcess(fixture("large"), 1) }
    }
}
