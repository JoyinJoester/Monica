package takagi.ru.monica.autofill_ng

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Date
import kotlin.system.measureNanoTime
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.rustcore.RustAutofillCore

@RunWith(AndroidJUnit4::class)
class AutofillNativeIndexInstrumentedTest {
    @Test
    fun realNativeIndexPreservesPoliciesOrderAndUpdates() {
        val baseline = BitwardenLikeAutofillMatcherNg(cacheMetadata = false)
        val native = BitwardenLikeAutofillMatcherNg()
        var entries = rows(300) + listOf(
            entry(1001, "https://example.com", "androidapp://com.target.app"),
            entry(1002, "https://accounts.example.com", "com.other.app"),
            entry(1003, "https://other.example.com", "com.target.app"),
            entry(1004, "https://evil-example.com", "com.target.app"),
            entry(1005, "https://example.com.evil.test", "com.target.app"),
            entry(1006, "android-app://com.target.app; https://例子.测试", ""),
            entry(1007, "https://example.co.uk; https://second.test", ""),
            entry(1008, "", "", title = " 应用 École "),
            entry(1009, "com.target.app", ""),
        )
        try {
            for (flags in 0 until 32) {
                val config = BitwardenLikeAutofillMatcherNg.Config(
                    strictOnly = flags and 1 != 0,
                    allowSubdomainMatch = flags and 2 != 0,
                    allowBaseDomainMatch = flags and 4 != 0,
                    exactDomainOnly = flags and 8 != 0,
                    allowPackageMatch = flags and 16 != 0,
                    maxSuggestions = Int.MAX_VALUE,
                )
                for (host in listOf(null, "example.com", "accounts.example.com", "login.example.co.uk", "例子.测试")) {
                    assertEquals("flags=$flags host=$host",
                        baseline.match(entries, "com.target.app", host, "应用 École", config),
                        native.match(entries, "com.target.app", host, "应用 École", config))
                }
            }
            assertTrue("The test must actually call the packaged JNI index", native.nativeQueryCount > 0)
            assertTrue(native.match(entries, "com.target.app", null).any { it.id == 1009L })
            val exact = native.match(entries, "com.target.app", "example.com",
                config = BitwardenLikeAutofillMatcherNg.Config(exactDomainOnly = true))
            assertEquals(listOf(1001L), exact.map { it.id })

            entries = entries.reversed().filterNot { it.id == 1001L }.map {
                if (it.id == 1002L) it.copy(website = "https://moved.test", password = "updated-synthetic") else it
            } + entry(2001, "https://example.com", "com.new.app")
            assertEquals(baseline.match(entries, "com.browser", "example.com"),
                native.match(entries, "com.browser", "example.com"))
            native.clear()
            assertEquals(baseline.match(entries, "com.new.app", null),
                native.match(entries, "com.new.app", null))
        } finally {
            native.clear()
            baseline.clear()
        }
    }

    @Test
    fun nativeLifecycleAndOversizedFallbackStaySafe() {
        val row = RustAutofillCore.Row(setOf("com.synthetic"), setOf("example.test"),
            setOf("example.test"), setOf("应用"))
        val handle = requireNotNull(RustAutofillCore.open(listOf(row)))
        assertNotNull(RustAutofillCore.query(handle, "com.synthetic", "", "", ""))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            assertNull(RustAutofillCore.open(listOf(row)))
            assertNull(RustAutofillCore.query(handle, "com.synthetic", "", "", ""))
        }
        RustAutofillCore.close(handle)
        assertNull(RustAutofillCore.query(handle, "com.synthetic", "", "", ""))
        assertNull(RustAutofillCore.open(listOf(row.copy(labels = setOf("x".repeat(4097))))))

        val entries = rows(300) + entry(1001, "https://example.test", "", title = "x".repeat(4097))
        val fallback = BitwardenLikeAutofillMatcherNg()
        try {
            assertEquals(listOf(1001L), fallback.match(entries, "com.browser", "example.test").map { it.id })
            assertEquals(0, fallback.nativeQueryCount)
        } finally {
            fallback.clear()
        }
    }

    @Test
    fun measureCompleteMatchingWithUncachedCachedAndNativeImplementations() {
        val results = JSONArray()
        val config = BitwardenLikeAutofillMatcherNg.Config(maxSuggestions = Int.MAX_VALUE)
        for (size in listOf(1_000, 10_000, 50_000)) {
            val entries = rows(size)
            val matchers = linkedMapOf(
                "uncachedKotlin" to BitwardenLikeAutofillMatcherNg(cacheMetadata = false),
                "cachedKotlin" to BitwardenLikeAutofillMatcherNg(useNativeIndex = false),
                "nativeIndex" to BitwardenLikeAutofillMatcherNg(),
            )
            val cold = JSONObject()
            val samples = matchers.keys.associateWith { mutableListOf<Double>() }
            fun query(matcher: BitwardenLikeAutofillMatcherNg, target: Int) =
                matcher.match(entries, "com.browser", "login.service$target.test", config = config)
            try {
                for ((name, matcher) in matchers) {
                    cold.put(name, measureNanoTime { query(matcher, 44) } / 1_000_000.0)
                }
                repeat(3) { iteration -> matchers.values.forEach { query(it, iteration) } }
                repeat(11) { iteration ->
                    val target = if (iteration % 3 == 0) 9999 else 40 + iteration
                    val expected = query(matchers.getValue("uncachedKotlin"), target)
                    val order = if (iteration % 2 == 0) matchers.entries.toList() else matchers.entries.reversed()
                    for ((name, matcher) in order) {
                        lateinit var result: List<PasswordEntry>
                        samples.getValue(name) += measureNanoTime { result = query(matcher, target) } / 1_000_000.0
                        assertEquals(expected, result)
                    }
                }
                assertTrue(matchers.getValue("nativeIndex").nativeQueryCount >= 15)
                val measurements = JSONObject()
                for ((name, times) in samples) {
                    measurements.put(name, JSONObject()
                        .put("medianMs", times.sorted()[times.size / 2])
                        .put("p95Ms", times.sorted().last())
                        .put("samplesMs", JSONArray(times)))
                }
                results.put(JSONObject().put("rows", size).put("firstMatchMs", cold)
                    .put("warmQueries", measurements))
            } finally {
                matchers.values.forEach { it.clear() }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("autofill-performance"))
        File(directory, "audit-comparison.json").writeText(JSONObject()
            .put("environment", "Android emulator; debug Kotlin, release Rust; synthetic metadata")
            .put("warmups", 3).put("samples", 11).put("results", results).toString(2))
    }

    private fun rows(count: Int) = List(count) { index ->
        entry(index.toLong() + 1, "https://login.service${index % 500}.test", "com.service${index % 500}.app")
    }

    private fun entry(id: Long, website: String, app: String, title: String = "Synthetic $id") =
        PasswordEntry(id = id, title = title, website = website, appPackageName = app,
            username = "account-$id", password = "synthetic-value", createdAt = Date(0), updatedAt = Date(0))
}
