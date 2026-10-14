package takagi.ru.monica.autofill_ng

import android.app.Application
import android.service.autofill.FillResponse
import android.view.autofill.AutofillId
import android.widget.EditText
import android.widget.inline.InlinePresentationSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.*
import takagi.ru.monica.autofill_ng.builder.FillResponseBuilderNg
import takagi.ru.monica.autofill_ng.builder.FilledDataBuilderNg
import takagi.ru.monica.autofill_ng.model.*
import takagi.ru.monica.autofill_ng.parser.AutofillParserNg
import takagi.ru.monica.security.SecurityManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,34],application=Application::class)
class AutofillPasswordPurposeResponseTest {
    private val context = RuntimeEnvironment.getApplication()
    @Before fun grantTestReceiverPermission() {
        org.robolectric.Shadows.shadowOf(context).grantPermissions(context.packageName + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }
    private fun targets() = listOf(FieldHint.USERNAME,FieldHint.PASSWORD,FieldHint.NEW_PASSWORD,FieldHint.NEW_PASSWORD)
        .mapIndexed { index,hint -> ParsedItem(EditText(context).autofillId,hint,Accuracy.HIGH,traversalIndex=index) }

    @Test fun pickerReuseRejectsChangedTargetsValuesAndSavePreferences() {
        val request = AutofillPickerActivityV2.Args(applicationId="fixture", webDomain="login.example.test",
            autofillIds=arrayListOf(targets().first().id), saveEnabled=true, suggestedPasswordIds=longArrayOf(1))
        assertTrue(request.isSameRequestAs(request.copy(suggestedPasswordIds=longArrayOf(1))))
        listOf(request.copy(webDomain="other.example.test"), request.copy(saveEnabled=false),
            request.copy(saveCompatMode=true), request.copy(capturedUsername="other"),
            request.copy(capturedPassword="changed"), request.copy(rememberLastFilled=false),
            request.copy(autofillIds=arrayListOf(targets().first().id)),
            request.copy(suggestedPasswordIds=longArrayOf(2))).forEach {
            assertFalse(request.isSameRequestAs(it))
        }
    }

    @Test fun directAndAuthenticatedPartitionsExcludeNewPasswordFields() {
        val request=AutofillParserNg().parse("fixture.test",null,targets(),null) as AutofillRequest.Fillable
        // This mapping method never accesses storage. Avoid Android Keystore setup in the host fixture.
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible=true }.get(null)
        val unusedSecurityManager = unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, SecurityManager::class.java) as SecurityManager
        val builder=FilledDataBuilderNg(context,unusedSecurityManager)
        val method=FilledDataBuilderNg::class.java.getDeclaredMethod("fillLoginPartition",AutofillCipher.Login::class.java,
            List::class.java,InlinePresentationSpec::class.java,Boolean::class.javaPrimitiveType).apply { isAccessible=true }
        val cipher=AutofillCipher.Login("7","Fixture","account","account","old","","fixture.test")
        val views=(request.partition as AutofillPartition.Login).views
        for(auth in listOf(false,true)) {
            val result=method.invoke(builder,cipher,views,null,auth) as FilledPartition
            assertEquals(views.take(2).map { it.data.autofillId },result.filledItems.map { it.autofillId })
            if(auth) assertTrue(result.filledItems.all { it.value == null })
            else assertEquals(listOf("account","old"),result.filledItems.map { it.value?.textValue.toString() })
        }
    }

    @Test fun saveInfoRequiresNewAndConfirmationButNotTheOldPassword() {
        val fields=targets()
        val request=AutofillParserNg().parse("fixture.test",null,fields,null) as AutofillRequest.Fillable
        val filled=FilledData(emptyList(),emptyList(),request.partition,request.uri,null,false)
        val response=requireNotNull(FillResponseBuilderNg(context).build(request,filled,passwordSuggestionEnabled=false,requireAuthentication=false))
        val info=FillResponse::class.java.getDeclaredMethod("getSaveInfo").invoke(response)
        assertEquals(fields.drop(2).map { it.id }.toSet(),(info.javaClass.getMethod("getRequiredIds").invoke(info) as Array<*>).toSet())
        assertEquals(listOf(fields.first().id),(info.javaClass.getMethod("getOptionalIds").invoke(info) as Array<*>).toList())
    }

    @Test fun callbackNeverFillsStoredSecretsIntoNewPasswordIncludingFallback() {
        val activity=Robolectric.buildActivity(AutofillCipherCallbackActivity::class.java).get()
        val method=AutofillCipherCallbackActivity::class.java.getDeclaredMethod("resolveFilledValues",String::class.java,String::class.java,List::class.java,List::class.java).apply { isAccessible=true }
        val fields=targets()
        val values=method.invoke(activity,"account","old",fields.map { it.id },fields.map { it.hint.name }) as Map<*,*>
        assertEquals(mapOf(fields[0].id to "account",fields[1].id to "old"),values)
        assertTrue((method.invoke(activity,"account","old",listOf(fields[2].id),listOf("NEW_PASSWORD")) as Map<*,*>).isEmpty())
    }

    @Test fun manualGeneratorTargetsNewFieldsWhenChangeFormContainsBothRoles() {
        val activity=Robolectric.buildActivity(AutofillPickerActivityV2::class.java).get()
        val method=AutofillPickerActivityV2::class.java.getDeclaredMethod("buildGeneratedPasswordFillValues",List::class.java,List::class.java,String::class.java).apply { isAccessible=true }
        val fields=targets()
        val values=method.invoke(activity,fields.map { it.id },fields.map { it.hint.name },"generated") as Map<*,*>
        assertEquals(fields.drop(2).associate { it.id to "generated" },values)
    }

    @Test fun usernameStepDelaysSaveAndDisabledOrCompatSavingPublishesNoSaveInfo() {
        val account=targets().first()
        val fields=listOf(account.id to account.hint)
        val save=requireNotNull(AutofillSaveInfoPolicy.build(fields))
        assertEquals(android.service.autofill.SaveInfo.FLAG_DELAY_SAVE,save.javaClass.getMethod("getFlags").invoke(save))
        assertNull(AutofillSaveInfoPolicy.build(fields,enabled=false))
        assertNull(AutofillSaveInfoPolicy.build(fields,compatMode=true))
        assertNull(AutofillSaveInfoPolicy.build(fields+ (targets()[1].id to FieldHint.OTP_CODE)))
    }

    @Test fun resetAndChangeFormsCannotUsePasswordStepContinuation() {
        assertFalse(AutofillInteractionContextResolver.isPasswordOnlyLoginHints(listOf(FieldHint.NEW_PASSWORD)))
        assertFalse(AutofillInteractionContextResolver.isPasswordOnlyLoginHints(listOf(FieldHint.PASSWORD,FieldHint.NEW_PASSWORD)))
        assertTrue(AutofillInteractionContextResolver.isPasswordOnlyLoginHints(listOf(FieldHint.PASSWORD)))
    }
}
