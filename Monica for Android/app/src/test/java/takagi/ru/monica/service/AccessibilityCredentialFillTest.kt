package takagi.ru.monica.service

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.graphics.Rect
import android.text.InputType
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34], application = Application::class)
class AccessibilityCredentialFillTest {
    private lateinit var controller: ServiceController<MonicaAccessibilityService>
    private lateinit var service: MonicaAccessibilityService
    private lateinit var root: AccessibilityNodeInfo
    private val writes = mutableMapOf<String, String>()

    @Before fun setUp() {
        controller = Robolectric.buildService(MonicaAccessibilityService::class.java).create()
        service = controller.get()
        root = AccessibilityNodeInfo.obtain(View(RuntimeEnvironment.getApplication()))
        root.packageName = "audit.login"
        shadowOf(service).setRootInActiveWindow(root)
    }

    @After fun tearDown() { controller.destroy() }

    private fun field(id: String, hint: String, inputType: Int, focused: Boolean = false, top: Int = 100): AccessibilityNodeInfo {
        val node = AccessibilityNodeInfo.obtain(View(RuntimeEnvironment.getApplication()))
        node.packageName = "audit.login"
        node.viewIdResourceName = "audit.login:id/$id"
        node.hintText = hint
        node.inputType = inputType
        node.isPassword = inputType and InputType.TYPE_MASK_VARIATION == InputType.TYPE_TEXT_VARIATION_PASSWORD
        node.isEditable = true
        node.isEnabled = true
        node.isVisibleToUser = true
        node.isFocused = focused
        node.setBoundsInScreen(Rect(20,top,600,top+80))
        shadowOf(node).setOnPerformActionListener { action, args ->
            if (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                writes[id] = args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE).toString()
                true
            } else action != AccessibilityNodeInfo.ACTION_PASTE
        }
        shadowOf(root).addChild(node)
        return node
    }

    private fun fill(preferPassword: Boolean = false, username: String = "test-account", password: String = "test-secret"): Boolean {
        AccessibilityNodeInfo::class.java.getDeclaredMethod("setSealed", Boolean::class.javaPrimitiveType)
            .invoke(root, true)
        return runBlocking {
            service.fillCredentialsInActiveWindow("audit.login",username,password,preferPassword)
        }
    }

    @Test fun usernameOnlyStepReceivesUsernameAndNeverPassword() {
        field("username","Username",InputType.TYPE_CLASS_TEXT,focused=true)
        val filled = fill()
        assertEquals(mapOf("username" to "test-account"),writes)
        assertTrue(filled)
    }

    @Test fun passwordOnlyStepReportsSuccessfulPasswordFill() {
        field("password","Password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,focused=true)
        val filled = fill()
        assertEquals(mapOf("password" to "test-secret"),writes)
        assertTrue(filled)
    }

    @Test fun registrationNeverReceivesAnExistingPassword() {
        field("username", "Username", InputType.TYPE_CLASS_TEXT, focused = true)
        field("newPassword", "New password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, top = 220)
        field("confirmation", "Confirm password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, top = 340)
        assertTrue(fill())
        assertEquals(mapOf("username" to "test-account"), writes)
    }

    @Test fun focusedNewPasswordDoesNotStealTheCurrentPasswordOnAChangeForm() {
        field("current", "Current password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        field("newPassword", "New password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, focused = true, top = 220)
        field("confirmation", "Confirm password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, top = 340)
        assertTrue(fill(preferPassword = true))
        assertEquals(mapOf("current" to "test-secret"), writes)
    }

    @Test fun resetOnlyFormCannotUseTheUnknownFieldFallback() {
        field("reset", "新密码", InputType.TYPE_CLASS_TEXT, focused = true)
        field("confirmation", "确认密码", InputType.TYPE_CLASS_TEXT, top = 220)
        assertFalse(fill(preferPassword = true))
        assertTrue(writes.isEmpty())
    }

    @Test fun currentPasswordReplacedWithNewPasswordAfterAccountInputIsNotFilled() {
        val account = field("username", "Username", InputType.TYPE_CLASS_TEXT, focused = true)
        field("current", "Current password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, top = 220)
        shadowOf(account).setOnPerformActionListener { action, args ->
            if (action != AccessibilityNodeInfo.ACTION_SET_TEXT) false else {
                writes["username"] = args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE).toString()
                root = AccessibilityNodeInfo.obtain(View(RuntimeEnvironment.getApplication())).apply { packageName = "audit.login" }
                shadowOf(service).setRootInActiveWindow(root)
                field("replacement", "New password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, top = 220)
                AccessibilityNodeInfo::class.java.getDeclaredMethod("setSealed", Boolean::class.javaPrimitiveType).invoke(root, true)
                true
            }
        }
        fill()
        assertEquals(mapOf("username" to "test-account"), writes)
    }

    @Test fun standardLoginFillsBothFields() {
        field("username","Username",InputType.TYPE_CLASS_TEXT,focused=true)
        field("password","Password",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,top=220)
        assertTrue(fill())
        assertEquals(mapOf("username" to "test-account","password" to "test-secret"),writes)
    }

    @Test fun loginAlongsideAnOtpNeverPutsThePasswordInTheOtp() {
        field("username","Email",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,focused=true)
        field("otp","Verification code",InputType.TYPE_CLASS_NUMBER,top=220)
        assertTrue(fill())
        assertEquals(mapOf("username" to "test-account"),writes)
    }

    @Test fun searchIsNotACredentialTarget() {
        field("search","Search",InputType.TYPE_CLASS_TEXT,focused=true)
        assertFalse(fill())
        assertTrue(writes.isEmpty())
    }

    @Test fun focusedChinesePasswordWithNormalInputTypeKeepsRoles() {
        field("first","账号",InputType.TYPE_CLASS_TEXT)
        field("second","密码",InputType.TYPE_CLASS_TEXT,focused=true,top=220)
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-account","second" to "test-secret"),writes)
    }

    @Test fun explicitUsernameIsNotRepurposedEvenWhenPasswordIsPreferred() {
        field("first","工号",InputType.TYPE_CLASS_TEXT,focused=true)
        assertTrue(fill(preferPassword=true))
        assertEquals(mapOf("first" to "test-account"),writes)
    }

    @Test fun phoneInputTypeCanFillTheAccountStep() {
        field("first","",InputType.TYPE_CLASS_PHONE,focused=true)
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-account"),writes)
    }

    @Test fun numericOtpIsExcludedEvenWhenItIsMasked() {
        field("first","一次性验证码",InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,focused=true)
        assertFalse(fill(preferPassword=true))
        assertTrue(writes.isEmpty())
    }

    @Test fun typedTextDoesNotOverrideTheFieldRole() {
        field("first","用户名",InputType.TYPE_CLASS_TEXT,focused=true).text = "password-manager-fan"
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-account"),writes)
    }

    @Test fun associatedLabelIsUsedWhenTheInputHasNoHint() {
        val input = field("first","",InputType.TYPE_CLASS_TEXT,focused=true)
        val label = AccessibilityNodeInfo.obtain().apply { text = "密码" }
        shadowOf(input).setLabeledBy(label)
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-secret"),writes)
    }

    @Test fun disabledAndInvisibleFieldsAreNotFilled() {
        field("first","账号",InputType.TYPE_CLASS_TEXT,focused=true).isEnabled = false
        field("second","密码",InputType.TYPE_CLASS_TEXT,top=220).isVisibleToUser = false
        assertFalse(fill())
        assertTrue(writes.isEmpty())
    }

    @Test fun explicitPasswordAndUnlabelledAccountArePaired() {
        field("first","",InputType.TYPE_CLASS_TEXT,focused=true)
        field("second","",InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,top=220)
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-account","second" to "test-secret"),writes)
    }

    @Test fun aRejectedPasswordWriteDoesNotReportCompleteSuccess() {
        field("first","账号",InputType.TYPE_CLASS_TEXT,focused=true)
        val rejected = field("second","密码",InputType.TYPE_CLASS_TEXT,top=220)
        shadowOf(rejected).setOnPerformActionListener { _, _ -> false }
        assertFalse(fill())
        assertEquals(mapOf("first" to "test-account"),writes)
    }

    @Test fun webPasswordAcknowledgmentWithAnEmptyControlDoesNotReportSuccess() {
        root.className = "android.webkit.WebView"
        field("password", "Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, focused = true).text = ""
        assertFalse(fill(username = ""))
        assertEquals(mapOf("password" to "test-secret"), writes)
    }

    @Test fun maskedWebPasswordRemainsCompatibleWithPostWriteVerification() {
        root.className = "android.webkit.WebView"
        field("password", "Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, focused = true).text = "••••••••"
        assertTrue(fill(username = ""))
        assertEquals(mapOf("password" to "test-secret"), writes)
    }

    @Test fun aPasswordControlReplacedAfterUsernameInputIsResolvedAgain() {
        val account = field("first","账号",InputType.TYPE_CLASS_TEXT,focused=true)
        field("old-password","密码",InputType.TYPE_CLASS_TEXT,top=220)
        shadowOf(account).setOnPerformActionListener { action, args ->
            if (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                writes["first"] = args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE).toString()
                root = AccessibilityNodeInfo.obtain(View(RuntimeEnvironment.getApplication()))
                root.packageName = "audit.login"
                shadowOf(service).setRootInActiveWindow(root)
                field("replacement","密码",InputType.TYPE_CLASS_TEXT,top=220)
                AccessibilityNodeInfo::class.java.getDeclaredMethod("setSealed", Boolean::class.javaPrimitiveType)
                    .invoke(root, true)
                true
            } else false
        }
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-account", "replacement" to "test-secret"), writes)
    }

    @Test fun changingAppsAfterUsernameInputPreventsThePasswordWrite() {
        val account = field("first","账号",InputType.TYPE_CLASS_TEXT,focused=true)
        field("password","密码",InputType.TYPE_CLASS_TEXT,top=220)
        shadowOf(account).setOnPerformActionListener { action, args ->
            if (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                writes["first"] = args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE).toString()
                root = AccessibilityNodeInfo.obtain(View(RuntimeEnvironment.getApplication()))
                root.packageName = "another.app"
                shadowOf(service).setRootInActiveWindow(root)
                true
            } else false
        }
        assertFalse(fill())
        assertEquals(mapOf("first" to "test-account"), writes)
    }

    @Test fun directWritesDoNotDependOnPasteOrChangeTheClipboard() {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("previous","original clipboard"))
        var pasteCount = 0
        val node = field("first","账号",InputType.TYPE_CLASS_TEXT,focused=true)
        shadowOf(node).setOnPerformActionListener { action, args ->
            if (action == AccessibilityNodeInfo.ACTION_SET_TEXT) {
                writes["first"] = args.getCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE).toString()
            }
            if (action == AccessibilityNodeInfo.ACTION_PASTE) pasteCount++
            true
        }
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-account"), writes)
        assertEquals(0, pasteCount)
        assertEquals("original clipboard",clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test fun pasteFallbackRestoresTheClipboardWhenTheFieldHasFocus() {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("previous","original clipboard"))
        val node = field("first","账号",InputType.TYPE_CLASS_TEXT,focused=true)
        shadowOf(node).setOnPerformActionListener { action, _ ->
            when (action) {
                AccessibilityNodeInfo.ACTION_SET_TEXT -> false
                AccessibilityNodeInfo.ACTION_PASTE -> {
                    writes["first"] = clipboard.primaryClip?.getItemAt(0)?.text.toString()
                    true
                }
                else -> true
            }
        }
        assertTrue(fill())
        assertEquals(mapOf("first" to "test-account"), writes)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(600))
        assertEquals("original clipboard",clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test fun consumedFocusActionDoesNotPasteIntoThePreviousField() {
        var pasteCount = 0
        val node = field("first","账号",InputType.TYPE_CLASS_TEXT,focused=false)
        shadowOf(node).setOnPerformActionListener { action, _ ->
            if (action == AccessibilityNodeInfo.ACTION_PASTE) pasteCount++
            action != AccessibilityNodeInfo.ACTION_SET_TEXT
        }
        assertFalse(fill())
        assertEquals(0, pasteCount)
        assertTrue(writes.isEmpty())
    }

    @Test fun changingTheActivePackagePreventsAnyWrite() {
        field("first","账号",InputType.TYPE_CLASS_TEXT,focused=true)
        root.packageName = "another.app"
        assertFalse(fill())
        assertTrue(writes.isEmpty())
    }
}
