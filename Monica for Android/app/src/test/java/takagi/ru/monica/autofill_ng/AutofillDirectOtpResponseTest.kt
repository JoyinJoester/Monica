package takagi.ru.monica.autofill_ng

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.os.Looper
import android.os.Parcel
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.autofill.AutofillValue
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.Accuracy
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.FieldHint
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.ParsedItem
import takagi.ru.monica.autofill_ng.builder.FillResponseBuilderNg
import takagi.ru.monica.autofill_ng.model.*
import takagi.ru.monica.autofill_ng.parser.AutofillParserNg

/** Real framework datasets on Q and current Android, including the parcel delivered to the callback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34], application = Application::class)
class AutofillDirectOtpResponseTest {
    private val context: Application = RuntimeEnvironment.getApplication()
    private val targets = listOf(FieldHint.USERNAME, FieldHint.PASSWORD).mapIndexed { index, hint ->
        ParsedItem(EditText(context).autofillId, hint, Accuracy.HIGH, isFocused = index == 0, traversalIndex = index)
    }
    private val preparedUsername = "prepared account"
    private val preparedPassword = "prepared secret \n with spaces"

    private fun partition(id: Long, fields: List<ParsedItem> = targets,
        values: List<AutofillValue?> = fields.map { AutofillValue.forText(if (it.hint == FieldHint.PASSWORD) preparedPassword else preparedUsername) },
        authentication: Boolean = false): FilledPartition {
        val cipher = AutofillCipher.Login(cipherId = id.toString(), name = "Credential $id", subtitle = "account $id",
            username = "cipher account differs", password = "cipher secret differs", website = "", appPackageName = "otp.fixture")
        return FilledPartition(cipher, fields.zip(values) { field, value -> FilledItem(field.id, value) }, null,
            requiresAuthentication = authentication)
    }

    private fun response(partitions: List<FilledPartition> = listOf(partition(7)), fields: List<ParsedItem> = targets,
        optedIn: Set<Long> = emptySet(), locked: Boolean = false, requireAuthentication: Boolean = false): FillResponse {
        val request = AutofillParserNg().parse("otp.fixture", null, fields, null) as AutofillRequest.Fillable
        return requireNotNull(FillResponseBuilderNg(context).build(request,
            FilledData(partitions, emptyList(), request.partition, request.uri, null, locked),
            passwordSuggestionEnabled = false, requireAuthentication = requireAuthentication,
            postFillOtpPasswordIds = optedIn))
    }

    private fun property(value: Any, name: String): Any? = value.javaClass.getDeclaredMethod(name).invoke(value)
    private fun datasets(response: FillResponse) = (property(response, "getDatasets") as List<*>).map { it as Dataset }
    private fun authentication(dataset: Dataset) = property(dataset, "getAuthentication") as? IntentSender
    private fun fieldIds(dataset: Dataset) = property(dataset, "getFieldIds") as List<*>
    private fun values(dataset: Dataset) = (property(dataset, "getFieldValues") as List<*>).map { (it as? AutofillValue)?.textValue?.toString() }

    private fun deliveredIntent(sender: IntentSender): Intent {
        val shadow = Shadows.shadowOf(context)
        while (shadow.nextStartedActivity != null) { /* Clear unrelated earlier callbacks. */ }
        sender.sendIntent(context, 0, null, null, null)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        return requireNotNull(shadow.nextStartedActivity)
    }

    @Suppress("DEPRECATION")
    private fun directResult(dataset: Dataset): Dataset {
        val intent = deliveredIntent(requireNotNull(authentication(dataset)))
        assertEquals(AutofillCipherCallbackActivity::class.java.name, intent.component?.className)
        val original = requireNotNull(intent.getParcelableExtra<Dataset>(AutofillCipherCallbackActivity.EXTRA_DIRECT_DATASET))
        val parcel = Parcel.obtain()
        return try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            Dataset.CREATOR.createFromParcel(parcel)
        } finally { parcel.recycle() }
    }

    @Test fun onlyOptedInCredentialUsesACallbackWithExactPreparedValues() {
        val result = response(partitions = listOf(partition(7), partition(8)), optedIn = setOf(7L))
        assertNull(property(result, "getAuthentication"))
        val candidates = datasets(result)
        assertEquals(3, candidates.size) // Two credentials plus the unchanged manual-vault action.
        val original = directResult(candidates[0])
        assertNull(authentication(original))
        assertEquals(targets.map { it.id }, fieldIds(original))
        assertEquals(listOf(preparedUsername, preparedPassword), values(original))
        assertNull(authentication(candidates[1]))
        assertEquals(targets.map { it.id }, fieldIds(candidates[1]))
        assertEquals(listOf(preparedUsername, preparedPassword), values(candidates[1]))
    }

    @Test fun disabledAndUnmatchedOtpOptionsKeepPlainDatasets() {
        listOf(emptySet(), setOf(99L)).forEach { optedIn ->
            val candidate = datasets(response(optedIn = optedIn)).first()
            assertNull(authentication(candidate))
            assertEquals(targets.map { it.id }, fieldIds(candidate))
            assertEquals(listOf(preparedUsername, preparedPassword), values(candidate))
        }
    }

    @Test fun accountOnlyRequestNeverWrapsEvenIfCipherAndOtpContainSecrets() {
        val account = targets.take(1)
        val candidate = datasets(response(partitions = listOf(partition(7, account)), fields = account, optedIn = setOf(7L))).first()
        assertNull(authentication(candidate))
        assertEquals(account.map { it.id }, fieldIds(candidate))
        assertEquals(listOf(preparedUsername), values(candidate))
    }

    @Test fun passwordTargetInRequestIsInsufficientUnlessThisDatasetActuallyFillsIt() {
        val candidate = datasets(response(partitions = listOf(partition(7, targets.take(1))), optedIn = setOf(7L))).first()
        assertNull(authentication(candidate))
        assertEquals(listOf(targets.first().id), fieldIds(candidate))
        assertEquals(listOf(preparedUsername), values(candidate))
    }

    @Test fun nullAndEmptyPasswordValuesNeverWrap() {
        listOf(null, AutofillValue.forText(""), AutofillValue.forText(" \t ")).forEach { passwordValue ->
            val candidate = datasets(response(partitions = listOf(partition(7,
                values = listOf(AutofillValue.forText(preparedUsername), passwordValue))), optedIn = setOf(7L))).first()
            assertNull(authentication(candidate))
            assertEquals(listOf(preparedUsername, passwordValue?.textValue?.toString()), values(candidate))
        }
    }

    @Test fun callbackExtrasSurviveFrameworkOnlyClassLoaderAndProcessLoss() {
        val intent = deliveredIntent(requireNotNull(authentication(datasets(response(optedIn = setOf(7L))).first())))
        val parcel = Parcel.obtain()
        try {
            intent.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = Intent.CREATOR.createFromParcel(parcel)
            val frameworkLoader = object : ClassLoader(Intent::class.java.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name.startsWith("takagi.ru.monica")) throw ClassNotFoundException(name)
                    return super.loadClass(name, resolve)
                }
            }
            restored.setExtrasClassLoader(frameworkLoader)
            val extras = requireNotNull(restored.extras)
            extras.keySet().forEach { key ->
                @Suppress("DEPRECATION") val value = extras.get(key)
                if (value is android.os.Bundle) {
                    value.classLoader = frameworkLoader
                    assertEquals(7L, value.getLong("password_id"))
                    assertEquals(2, value.getStringArrayList("autofill_hints")?.size)
                }
            }
            // No dependency on pendingArgsByToken or loading any Monica Parcelable.
            assertNotNull(restored.getBundleExtra("extra_args_bundle"))
        } finally { parcel.recycle() }
    }

    @Test fun newPasswordTargetDoesNotTriggerAnExistingAccountOtp() {
        val newPassword = listOf(targets.last().copy(hint = FieldHint.NEW_PASSWORD))
        val candidate = datasets(response(partitions = listOf(partition(7, newPassword,
            values = listOf(AutofillValue.forText(preparedPassword)))), fields = newPassword, optedIn = setOf(7L))).first()
        assertNull(authentication(candidate))
        assertEquals(newPassword.map { it.id }, fieldIds(candidate))
        assertEquals(listOf(preparedPassword), values(candidate))
    }

    @Test fun sessionLockWithoutRequiredVerificationStillReturnsThePreparedCredential() {
        val result = response(optedIn = setOf(7L), locked = true, requireAuthentication = false)
        assertNull(property(result, "getAuthentication"))
        val original = directResult(datasets(result).first())
        assertNull(authentication(original))
        assertEquals(targets.map { it.id }, fieldIds(original))
        assertEquals(listOf(preparedUsername, preparedPassword), values(original))
    }

    @Test fun passwordOnlyRequestKeepsOnlyItsOriginalPasswordFieldInCallbackResult() {
        val password = targets.drop(1)
        val candidate = datasets(response(partitions = listOf(partition(7, password)), fields = password, optedIn = setOf(7L))).first()
        val original = directResult(candidate)
        assertNull(authentication(original))
        assertEquals(password.map { it.id }, fieldIds(original))
        assertEquals(listOf(preparedPassword), values(original))
    }

    @Test fun lockedVerifiedResponseOffersCandidateWithoutSecretsOrDirectBypass() {
        val result = response(partitions = listOf(partition(7, values = listOf(null, null), authentication = true)),
            optedIn = setOf(7L), locked = true, requireAuthentication = true)
        assertNull(property(result, "getAuthentication"))
        val candidate = datasets(result).first()
        assertEquals(targets.map { it.id }, fieldIds(candidate))
        assertEquals(listOf(null, null), values(candidate))
        val intent = deliveredIntent(requireNotNull(authentication(candidate)))
        assertEquals(AutofillCipherCallbackActivity::class.java.name, intent.component?.className)
        assertFalse(intent.hasExtra(AutofillCipherCallbackActivity.EXTRA_DIRECT_DATASET))
        val manual = datasets(result).last()
        assertEquals(AutofillPickerActivityV2::class.java.name,
            deliveredIntent(requireNotNull(authentication(manual))).component?.className)
    }

    @Test fun nativeAuthenticatedCallbackDoesNotTreatPackageAsWebsite() {
        val candidate = datasets(response(partitions = listOf(partition(7, values = listOf(null, null), authentication = true)),
            optedIn = setOf(7L), requireAuthentication = true)).first()
        val intent = deliveredIntent(requireNotNull(authentication(candidate)))
        val args = requireNotNull(intent.getBundleExtra("extra_args_bundle"))
        assertNull(args.getString("web_domain"))
    }

    @Test fun authenticationRequiredDatasetNeverReceivesTheDirectResultBypass() {
        val candidate = datasets(response(partitions = listOf(partition(7, values = listOf(null, null), authentication = true)),
            optedIn = setOf(7L), requireAuthentication = true)).first()
        val intent = deliveredIntent(requireNotNull(authentication(candidate)))
        assertEquals(AutofillCipherCallbackActivity::class.java.name, intent.component?.className)
        assertFalse(intent.hasExtra(AutofillCipherCallbackActivity.EXTRA_DIRECT_DATASET))
        assertEquals(listOf(null, null), values(candidate))
    }

    @Test fun manualFallbackStillUsesPickerAuthenticationAndTheSameTargetIds() {
        val manual = datasets(response(optedIn = setOf(7L))).last()
        val intent = deliveredIntent(requireNotNull(authentication(manual)))
        assertEquals(AutofillPickerActivityV2::class.java.name, intent.component?.className)
        assertFalse(intent.hasExtra(AutofillCipherCallbackActivity.EXTRA_DIRECT_DATASET))
        assertEquals(targets.map { it.id }, fieldIds(manual))
        assertEquals(listOf("PLACEHOLDER", "PLACEHOLDER"), values(manual))
    }
}
