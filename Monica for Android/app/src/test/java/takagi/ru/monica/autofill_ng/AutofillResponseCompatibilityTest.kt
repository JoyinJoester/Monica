package takagi.ru.monica.autofill_ng

import android.app.Application
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.View
import android.view.autofill.AutofillValue
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.*
import takagi.ru.monica.autofill_ng.builder.FillResponseBuilderNg
import takagi.ru.monica.autofill_ng.model.*
import takagi.ru.monica.autofill_ng.parser.AutofillParserNg

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29,34], application = Application::class)
class AutofillResponseCompatibilityTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun response(roles: List<FieldHint>, locked: Boolean, requireAuth: Boolean): Pair<FillResponse,List<ParsedItem>> {
        val targets = roles.mapIndexed { index, role -> ParsedItem(
            id=EditText(context).autofillId,hint=role,accuracy=Accuracy.HIGH,isFocused=index==0,traversalIndex=index)
        }
        val request = AutofillParserNg().parse("audit.enterprise",null,targets,null) as AutofillRequest.Fillable
        val cipher = AutofillCipher.Login(cipherId="7",name="Audit entry",subtitle="account",username="account",
            password="secret",website="",appPackageName="audit.enterprise")
        val partitions = listOf(FilledPartition(cipher,targets.map {
            FilledItem(it.id,if(requireAuth) null else AutofillValue.forText(if(it.hint==FieldHint.PASSWORD) "secret" else "account"))
        },null,requiresAuthentication=requireAuth))
        val filled = FilledData(partitions,emptyList(),request.partition,request.uri,null,locked)
        return requireNotNull(FillResponseBuilderNg(context).build(request,filled,
            passwordSuggestionEnabled=false,requireAuthentication=requireAuth)) to targets
    }

    private fun property(value: Any, name: String): Any? = value.javaClass.getDeclaredMethod(name).invoke(value)

    @Test fun noVerificationModeContainsUsableValuesEvenWhenTheVaultSessionIsLocked() {
        val (response,targets) = response(listOf(FieldHint.USERNAME,FieldHint.PASSWORD),locked=true,requireAuth=false)
        assertNull(property(response,"getAuthentication"))
        val dataset = (property(response,"getDatasets") as List<*>).first() as Dataset
        assertNull(property(dataset,"getAuthentication"))
        assertEquals(targets.map { it.id },property(dataset,"getFieldIds"))
        val values = (property(dataset,"getFieldValues") as List<*>).map { (it as AutofillValue).textValue.toString() }
        assertEquals(listOf("account","secret"),values)
    }

    @Test fun verificationIsAttachedToCandidatesForBothLockedAndUnlockedVaults() {
        for (locked in listOf(true, false)) {
            val (response, targets) = response(listOf(FieldHint.USERNAME, FieldHint.PASSWORD), locked, requireAuth = true)
            assertNull(property(response, "getAuthentication"))
            val datasets = property(response, "getDatasets") as List<*>
            assertEquals(2, datasets.size) // candidate and full picker
            for (dataset in datasets) {
                assertNotNull(property(dataset!!, "getAuthentication"))
                assertEquals(targets.map { it.id }, property(dataset, "getFieldIds"))
            }
            assertEquals(listOf(null, null), property(datasets.first()!!, "getFieldValues"))
        }
    }

    @Test fun aPasswordOnlyStepStillProducesADirectDataset() {
        val (response,targets) = response(listOf(FieldHint.PASSWORD),locked=false,requireAuth=false)
        val dataset = (property(response,"getDatasets") as List<*>).first() as Dataset
        assertEquals(listOf(targets.single().id),property(dataset,"getFieldIds"))
        assertEquals("secret",((property(dataset,"getFieldValues") as List<*>).single() as AutofillValue).textValue)
    }

    @Test fun aUsernameOnlyStepNeverContainsAPasswordValue() {
        val (response,_) = response(listOf(FieldHint.USERNAME),locked=false,requireAuth=false)
        val dataset = (property(response,"getDatasets") as List<*>).first() as Dataset
        assertEquals(listOf("account"),(property(dataset,"getFieldValues") as List<*>).map { (it as AutofillValue).textValue.toString() })
    }
}
