package takagi.ru.monica.autofill_ng

import android.app.Application
import android.widget.EditText
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import takagi.ru.monica.autofill_ng.EnhancedAutofillStructureParserV2.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,34],application=Application::class)
class AutofillStructureMemoryTest {
    @Test fun cachedStructureLosesSecretsAndCannotReviveReplacedFields() {
        val field=ParsedItem(EditText(RuntimeEnvironment.getApplication()).autofillId,FieldHint.PASSWORD,Accuracy.HIGH,value="synthetic secret")
        val memory=AutofillStructureMemory { 100 }
        memory.put("app|https|a.test",listOf(field),1)
        assertNull(memory.get("app|https|a.test",setOf(field.id))!!.single().value)
        assertNull(memory.get("app|https|b.test",setOf(field.id)))
        assertNull(memory.get("app|https|a.test",emptySet()))
        memory.put("app|https|a.test",listOf(field.copy(hint=FieldHint.NEW_PASSWORD)),2)
        assertNull(memory.get("app|https|a.test",setOf(field.id)))
    }
    @Test fun memoryExpiresIsBoundedAndIgnoresLateOlderRequests() {
        var now=0L
        val memory=AutofillStructureMemory { now }
        val field=ParsedItem(EditText(RuntimeEnvironment.getApplication()).autofillId,FieldHint.PASSWORD,Accuracy.HIGH)
        val ids=setOf(field.id)
        repeat(33) { memory.put("key$it",listOf(field),it.toLong()) }
        assertNull(memory.get("key0",ids))
        assertNotNull(memory.get("key32",ids))
        memory.put("key32",listOf(field.copy(hint=FieldHint.NEW_PASSWORD)),1)
        assertNotNull(memory.get("key32",ids))
        now=15_000
        assertNull(memory.get("key32",ids))
        memory.put("new",listOf(field),100)
        memory.clear()
        assertNull(memory.get("new",ids))
    }
}
