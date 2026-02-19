package takagi.ru.monica.autofill.debug

import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.util.Log

/**
 * SaveInfo 诊断工具
 * 用于调试 SaveInfo 配置问题
 */
object SaveInfoDiagnostic {
    
    private const val TAG = "SaveInfoDiag"
    
    /**
     * 诊断 FillResponse 中的 SaveInfo 配置
     */
    fun diagnose(response: FillResponse?) {
        Log.w(TAG, "╔════════════════════════════════════════╗")
        Log.w(TAG, "║   SaveInfo Diagnostic Report          ║")
        Log.w(TAG, "╚════════════════════════════════════════╝")
        
        if (response == null) {
            Log.e(TAG, "❌ FillResponse is NULL!")
            return
        }
        
        try {
            // 使用反射检查 SaveInfo
            val saveInfoField = FillResponse::class.java.getDeclaredField("mSaveInfo")
            saveInfoField.isAccessible = true
            val saveInfo = saveInfoField.get(response) as? SaveInfo
            
            if (saveInfo == null) {
                Log.e(TAG, "❌ SaveInfo is NULL in FillResponse!")
                Log.e(TAG, "   This is the ROOT CAUSE - SaveInfo was not added!")
                return
            }
            
            Log.i(TAG, "✅ SaveInfo exists in FillResponse")
            
            // 检查 SaveInfo 的详细信息
            diagnoseSaveInfo(saveInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during diagnosis: ${e.message}", e)
        }
        
        Log.w(TAG, "╚════════════════════════════════════════╝")
    }
    
    /**
     * 详细诊断 SaveInfo 对象
     */
    private fun diagnoseSaveInfo(saveInfo: SaveInfo) {
        try {
            // 检查 SaveInfo 类型
            val typeField = SaveInfo::class.java.getDeclaredField("mType")
            typeField.isAccessible = true
            val type = typeField.getInt(saveInfo)
            Log.d(TAG, "📋 SaveInfo Type: $type")
            Log.d(TAG, "   ${getSaveTypeDescription(type)}")
            
            // 检查必需字段
            val requiredIdsField = SaveInfo::class.java.getDeclaredField("mRequiredIds")
            requiredIdsField.isAccessible = true
            val requiredIds = requiredIdsField.get(saveInfo) as? Array<*>
            Log.d(TAG, "📌 Required fields: ${requiredIds?.size ?: 0}")
            requiredIds?.forEachIndexed { index, id ->
                Log.d(TAG, "   [$index] $id")
            }
            
            // 检查可选字段
            val optionalIdsField = SaveInfo::class.java.getDeclaredField("mOptionalIds")
            optionalIdsField.isAccessible = true
            val optionalIds = optionalIdsField.get(saveInfo) as? Array<*>
            Log.d(TAG, "📎 Optional fields: ${optionalIds?.size ?: 0}")
            optionalIds?.forEachIndexed { index, id ->
                Log.d(TAG, "   [$index] $id")
            }
            
            // 检查 flags
            val flagsField = SaveInfo::class.java.getDeclaredField("mFlags")
            flagsField.isAccessible = true
            val flags = flagsField.getInt(saveInfo)
            Log.d(TAG, "🚩 Flags: $flags")
            Log.d(TAG, "   ${getFlagsDescription(flags)}")
            
            // 检查描述
            val descriptionField = SaveInfo::class.java.getDeclaredField("mDescription")
            descriptionField.isAccessible = true
            val description = descriptionField.get(saveInfo)
            Log.d(TAG, "📝 Description: $description")
            
            // 关键警告
            if (requiredIds == null || requiredIds.isEmpty()) {
                Log.e(TAG, "⚠️ WARNING: No required fields! SaveInfo may not trigger!")
            }
            
            if (flags == 0) {
                Log.w(TAG, "⚠️ WARNING: No flags set!")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting SaveInfo: ${e.message}", e)
        }
    }
    
    /**
     * 获取保存类型的描述
     */
    private fun getSaveTypeDescription(type: Int): String {
        val types = mutableListOf<String>()
        
        if (type and SaveInfo.SAVE_DATA_TYPE_PASSWORD != 0) {
            types.add("PASSWORD")
        }
        if (type and SaveInfo.SAVE_DATA_TYPE_USERNAME != 0) {
            types.add("USERNAME")
        }
        if (type and SaveInfo.SAVE_DATA_TYPE_ADDRESS != 0) {
            types.add("ADDRESS")
        }
        if (type and SaveInfo.SAVE_DATA_TYPE_CREDIT_CARD != 0) {
            types.add("CREDIT_CARD")
        }
        if (type and SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS != 0) {
            types.add("EMAIL")
        }
        if (type and SaveInfo.SAVE_DATA_TYPE_GENERIC != 0) {
            types.add("GENERIC")
        }
        
        return if (types.isEmpty()) "UNKNOWN($type)" else types.joinToString(" | ")
    }
    
    /**
     * 获取 flags 的描述
     */
    private fun getFlagsDescription(flags: Int): String {
        if (flags == 0) return "NONE"
        
        val flagsList = mutableListOf<String>()
        
        if (flags and SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE != 0) {
            flagsList.add("SAVE_ON_ALL_VIEWS_INVISIBLE")
        }
        if (flags and SaveInfo.FLAG_DONT_SAVE_ON_FINISH != 0) {
            flagsList.add("DONT_SAVE_ON_FINISH")
        }
        
        return if (flagsList.isEmpty()) "UNKNOWN($flags)" else flagsList.joinToString(" | ")
    }
}

