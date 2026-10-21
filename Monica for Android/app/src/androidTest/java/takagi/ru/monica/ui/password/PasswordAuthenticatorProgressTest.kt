package takagi.ru.monica.ui.password

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PasswordAuthenticatorProgressTest {
    @get:Rule val compose = createComposeRule()

    private fun assertImmediateClockCorrection(before: Float, after: Float, smooth: Boolean = true) {
        var sample by mutableStateOf(before)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PasswordAuthenticatorProgressIndicator(
                    state = PasswordAuthenticatorDisplayState("123 456", 20, sample, 30),
                    smoothProgress = smooth
                )
            }
        }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle { sample = after }
        compose.mainClock.advanceTimeBy(32)
        val displayed = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        ).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertEquals("Clock correction must not animate through stale progress", after, displayed, 0.006f)
    }

    @Test fun foregroundForwardJumpDoesNotChaseOldProgress() =
        assertImmediateClockCorrection(0.2f, 0.65f)

    @Test fun foregroundBackwardJumpDoesNotRewindThroughOldProgress() =
        assertImmediateClockCorrection(0.65f, 0.2f)

    @Test fun periodRolloverStartsAtCurrentProgress() =
        assertImmediateClockCorrection(0.95f, 0.03f)

    @Test fun discreteProgressAlsoAppliesClockCorrectionsImmediately() =
        assertImmediateClockCorrection(0.2f, 0.65f, smooth = false)

    @Test fun fractionalFirstSampleIsAlignedToTheNextSecond() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                PasswordAuthenticatorProgressIndicator(
                    // 2.5s into a 10s period: the next second is only 500ms away.
                    PasswordAuthenticatorDisplayState("123 456", 8, 0.25f, 10, 2500L),
                    smoothProgress = true
                )
            }
        }
        compose.mainClock.advanceTimeBy(48)
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        fun progress() = compose.onNode(matcher).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        val before = progress()
        compose.mainClock.advanceTimeBy(240)
        assertEquals("Progress must advance at the real period rate", 0.024f, progress() - before, 0.003f)
        compose.mainClock.advanceTimeBy(400)
        assertEquals("Interpolation ends at the next wall-clock second", 0.3f, progress(), 0.001f)
    }

    @Test fun returningFromBackgroundImmediatelyUsesFreshTimeAndMillisecondProgress() {
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        var observed: PasswordAuthenticatorDisplayState? = null
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val state = rememberPasswordAuthenticatorDisplayState(
                    authenticatorKey = "otpauth://totp/Fixture?secret=JBSWY3DPEHPK3PXP&period=60",
                    timeOffsetSeconds = 7,
                    smoothProgress = true
                )
                SideEffect { observed = state }
            }
        }
        compose.waitUntil(10_000) { observed?.sampledAtMillis != null }
        fun assertAlignedSample() {
            val state = requireNotNull(observed)
            val now = requireNotNull(state.sampledAtMillis)
            val corrected = now + 7000L
            assertEquals((corrected % 60_000L) / 60_000f, requireNotNull(state.progress), 0.00001f)
            assertEquals(60 - (corrected / 1000L % 60L).toInt(), state.remainingSeconds)
        }
        compose.runOnIdle { assertAlignedSample() }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.waitForIdle()
        val pausedAt = requireNotNull(observed).sampledAtMillis
        // Longer than the shared ticker's stop timeout, so resubscription also
        // encounters its cached replay value.
        Thread.sleep(5500)
        compose.runOnIdle { assertEquals(pausedAt, requireNotNull(observed).sampledAtMillis) }
        var resumedAt = 0L
        compose.runOnUiThread {
            resumedAt = System.currentTimeMillis()
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitUntil(10_000) { (observed?.sampledAtMillis ?: 0L) >= resumedAt }
        compose.runOnIdle { assertAlignedSample() }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.DESTROYED }
    }
}
