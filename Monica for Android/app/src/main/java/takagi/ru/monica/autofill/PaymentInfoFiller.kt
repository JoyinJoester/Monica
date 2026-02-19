package takagi.ru.monica.autofill

import android.service.autofill.Dataset
import android.view.autofill.AutofillValue
import takagi.ru.monica.autofill.data.PaymentInfo

/**
 * 账单信息填充器
 * 
 * 负责将账单信息填充到自动填充数据集中
 */
object PaymentInfoFiller {
    
    /**
     * 创建账单信息数据集
     * 
     * @param paymentInfo 账单信息
     * @param parsedStructure 解析的结构
     * @return Dataset或null
     */
    fun createPaymentDataset(
        paymentInfo: PaymentInfo,
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): Dataset? {
        val datasetBuilder = Dataset.Builder()
        var hasField = false
        
        // 遍历所有字段,填充对应的账单信息
        parsedStructure.items.forEach { item ->
            when (item.hint) {
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER -> {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(paymentInfo.cardNumber)
                    )
                    hasField = true
                }
                
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE -> {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(paymentInfo.expiryDate)
                    )
                    hasField = true
                }
                
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE -> {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(paymentInfo.cvv)
                    )
                    hasField = true
                }
                
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_HOLDER_NAME -> {
                    datasetBuilder.setValue(
                        item.id,
                        AutofillValue.forText(paymentInfo.cardHolderName)
                    )
                    hasField = true
                }
                
                EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS -> {
                    paymentInfo.billingAddress?.let { address ->
                        datasetBuilder.setValue(
                            item.id,
                            AutofillValue.forText(address)
                        )
                        hasField = true
                    }
                }
                
                EnhancedAutofillStructureParserV2.FieldHint.POSTAL_CODE -> {
                    paymentInfo.zipCode?.let { zipCode ->
                        datasetBuilder.setValue(
                            item.id,
                            AutofillValue.forText(zipCode)
                        )
                        hasField = true
                    }
                }
                
                else -> {
                    // 其他字段类型不处理
                }
            }
        }
        
        return if (hasField) {
            datasetBuilder.build()
        } else {
            null
        }
    }
    
    /**
     * 检测是否为支付表单
     * 
     * @param parsedStructure 解析的结构
     * @return 是否为支付表单
     */
    fun isPaymentForm(parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure): Boolean {
        return parsedStructure.items.any { item ->
            item.hint in listOf(
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER,
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE,
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE
            )
        }
    }
    
    /**
     * 获取支付字段列表
     * 
     * @param parsedStructure 解析的结构
     * @return 支付字段列表
     */
    fun getPaymentFields(
        parsedStructure: EnhancedAutofillStructureParserV2.ParsedStructure
    ): List<EnhancedAutofillStructureParserV2.ParsedItem> {
        return parsedStructure.items.filter { item ->
            item.hint in listOf(
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_NUMBER,
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_EXPIRATION_DATE,
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_SECURITY_CODE,
                EnhancedAutofillStructureParserV2.FieldHint.CREDIT_CARD_HOLDER_NAME,
                EnhancedAutofillStructureParserV2.FieldHint.POSTAL_ADDRESS,
                EnhancedAutofillStructureParserV2.FieldHint.POSTAL_CODE
            )
        }
    }
}

