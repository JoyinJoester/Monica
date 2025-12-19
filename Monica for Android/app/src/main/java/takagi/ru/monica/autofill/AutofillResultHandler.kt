package takagi.ru.monica.autofill

import android.content.Context
import android.content.Intent
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.widget.RemoteViews
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.autofill.data.PaymentInfo

/**
 * AutofillPicker结果处理器
 * 
 * 负责处理AutofillPickerActivity返回的结果并构建FillResponse
 */
object AutofillResultHandler {
    
    /**
     * 处理密码选择结果
     * 
     * @param context Context
     * @param intent 返回的Intent
     * @param password 选中的密码
     * @param parsedStructure 解析的结构
     * @return FillResponse
     */
    fun handlePasswordSelection(
        context: Context,
        intent: Intent,
        password: PasswordEntry,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // 创建RemoteViews - 使用卡片布局
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(R.id.text_title, password.title.ifEmpty { password.username })
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        // 创建Dataset
        val datasetBuilder = Dataset.Builder(presentation)
        var hasField = false
        
        // 填充字段
        parsedStructure.items.forEach { item ->
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.USERNAME,
                EnhancedAutofillStructureParserV2.FieldHint.EMAIL_ADDRESS -> {
                    if (password.username.isNotBlank()) {
                        datasetBuilder.setValue(
                            item.id,
                            android.view.autofill.AutofillValue.forText(password.username)
                        )
                        hasField = true
                    }
                }
                
                EnhancedAutofillStructureParserV2.FieldHint.PASSWORD,
                EnhancedAutofillStructureParserV2.FieldHint.NEW_PASSWORD -> {
                    if (password.password.isNotBlank()) {
                        datasetBuilder.setValue(
                            item.id,
                            android.view.autofill.AutofillValue.forText(password.password)
                        )
                        hasField = true
                    }
                }
                
                EnhancedAutofillStructureParserV2.FieldHint.PHONE_NUMBER -> {
                    if (password.phone.isNotBlank()) {
                        datasetBuilder.setValue(
                            item.id,
                            android.view.autofill.AutofillValue.forText(password.phone)
                        )
                        hasField = true
                    }
                }
                
                else -> {
                    // 其他字段类型暂不处理
                }
            }
        }
        
        if (hasField) {
            responseBuilder.addDataset(datasetBuilder.build())
        }
        
        return responseBuilder.build()
    }
    
    /**
     * 处理账单信息选择结果
     * 
     * @param context Context
     * @param intent 返回的Intent
     * @param paymentInfo 选中的账单信息
     * @param parsedStructure 解析的结构
     * @return FillResponse
     */
    fun handlePaymentSelection(
        context: Context,
        intent: Intent,
        paymentInfo: PaymentInfo,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // 创建RemoteViews - 使用卡片布局
        val presentation = RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
            setTextViewText(
                R.id.text_title,
                "•••• ${paymentInfo.cardNumber.takeLast(4)}"
            )
            setImageViewResource(R.id.icon_app, R.drawable.ic_key)
        }
        
        // 使用PaymentInfoFiller创建Dataset
        val dataset = PaymentInfoFiller.createPaymentDataset(paymentInfo, parsedStructure)
        
        if (dataset != null) {
            responseBuilder.addDataset(dataset)
        }
        
        return responseBuilder.build()
    }
    
    /**
     * 从Intent解析选择类型和ID
     * 
     * @param intent 返回的Intent
     * @return Pair<选择类型, ID>
     */
    fun parseSelectionFromIntent(intent: Intent): Pair<String?, Long?> {
        val selectionType = intent.getStringExtra(AutofillPickerActivity.RESULT_SELECTION_TYPE)
        
        val id = when (selectionType) {
            AutofillPickerActivity.SELECTION_TYPE_PASSWORD -> {
                intent.getLongExtra(AutofillPickerActivity.RESULT_PASSWORD_ID, -1L)
            }
            AutofillPickerActivity.SELECTION_TYPE_PAYMENT -> {
                intent.getLongExtra(AutofillPickerActivity.RESULT_PAYMENT_ID, -1L)
            }
            else -> null
        }
        
        return Pair(selectionType, if (id != null && id != -1L) id else null)
    }
}
