package takagi.ru.monica.autofill.builder

import android.app.PendingIntent
import android.content.Context
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import takagi.ru.monica.R

/**
 * Dataset 构建工具类
 * 参考 Keyguard 的 DatasetBuilder 实现
 * 
 * 统一处理 Android 各版本的 API 差异：
 * - Android 11-12 (API 30-32): 使用 setValue() API
 * - Android 13+ (API 33+): 使用 Presentations.Builder + setField() API
 * 
 * @author Monica Development Team
 * @version 1.0
 */
object AutofillDatasetBuilder {
    
    /**
     * 字段数据封装
     */
    data class FieldData(
        val value: AutofillValue?,
        val presentation: RemoteViews
    )
    
    /**
     * 创建 Dataset.Builder
     * 
     * 自动根据 Android 版本选择合适的 API
     * 
     * @param menuPresentation 菜单展示视图
     * @param fields 字段映射 (AutofillId -> FieldData)
     * @param provideInlinePresentation 内联展示提供者（懒加载）
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R, lambda = 2)
    inline fun create(
        menuPresentation: RemoteViews,
        fields: Map<AutofillId, FieldData?>,
        provideInlinePresentation: () -> InlinePresentation?
    ): Dataset.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            createForTiramisu(menuPresentation, fields, provideInlinePresentation)
        } else {
            createPreTiramisu(menuPresentation, fields, provideInlinePresentation)
        }
    }
    
    /**
     * Android 13+ (API 33) 的实现
     * 使用 Presentations.Builder + setField() API
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    inline fun createForTiramisu(
        menuPresentation: RemoteViews,
        fields: Map<AutofillId, FieldData?>,
        provideInlinePresentation: () -> InlinePresentation?
    ): Dataset.Builder {
        val presentations = Presentations.Builder().apply {
            setMenuPresentation(menuPresentation)
            provideInlinePresentation()?.let { setInlinePresentation(it) }
        }.build()
        
        return Dataset.Builder(presentations).apply {
            fields.forEach { (autofillId, fieldData) ->
                if (fieldData != null && fieldData.value != null) {
                    setField(
                        autofillId,
                        Field.Builder()
                            .setValue(fieldData.value)
                            .build()
                    )
                }
            }
        }
    }
    
    /**
     * Android 11-12 (API 30-32) 的实现
     * 使用传统的 setValue() API
     */
    inline fun createPreTiramisu(
        menuPresentation: RemoteViews,
        fields: Map<AutofillId, FieldData?>,
        provideInlinePresentation: () -> InlinePresentation?
    ): Dataset.Builder {
        return Dataset.Builder(menuPresentation).apply {
            // Android 11+ 支持内联建议
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                provideInlinePresentation()?.let { setInlinePresentation(it) }
            }
            
            fields.forEach { (autofillId, fieldData) ->
                if (fieldData != null && fieldData.value != null) {
                    setValue(autofillId, fieldData.value, fieldData.presentation)
                }
            }
        }
    }
    
    /**
     * 内联展示构建器
     * 参考 Keyguard 的内联建议实现
     */
    object InlinePresentationBuilder {
        
        /**
         * 尝试创建内联展示
         * 
         * @param context 上下文
         * @param spec 内联展示规格
         * @param specs 所有可用规格（用于回退）
         * @param index 当前索引
         * @param pendingIntent 点击触发的 PendingIntent
         * @param title 标题
         * @param subtitle 副标题
         * @param icon 图标
         * @param contentDescription 无障碍描述
         */
        @RequiresApi(Build.VERSION_CODES.R)
        fun tryCreate(
            context: Context,
            spec: InlinePresentationSpec,
            specs: List<InlinePresentationSpec>? = null,
            index: Int = 0,
            pendingIntent: PendingIntent,
            title: String,
            subtitle: String? = null,
            icon: Icon? = null,
            contentDescription: String? = null
        ): InlinePresentation? {
            // 规格回退逻辑
            val effectiveSpec = findCompatibleSpec(spec, specs, index) ?: return null
            
            return try {
                val builder = InlineSuggestionUi.newContentBuilder(pendingIntent).apply {
                    setTitle(title)
                    subtitle?.let { setSubtitle(it) }
                    icon?.let { setStartIcon(it) }
                    contentDescription?.let { setContentDescription(it) }
                }
                
                InlinePresentation(builder.build().slice, effectiveSpec, false)
            } catch (e: Exception) {
                android.util.Log.e("DatasetBuilder", "Failed to create inline presentation", e)
                null
            }
        }
        
        /**
         * 查找兼容的内联规格
         */
        @RequiresApi(Build.VERSION_CODES.R)
        private fun findCompatibleSpec(
            primarySpec: InlinePresentationSpec,
            allSpecs: List<InlinePresentationSpec>?,
            index: Int
        ): InlinePresentationSpec? {
            // 首先检查主规格
            if (UiVersions.getVersions(primarySpec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                return primarySpec
            }
            
            // 尝试回退到第一个兼容的规格
            allSpecs?.forEach { spec ->
                if (UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                    android.util.Log.d("DatasetBuilder", "Inline spec fallback: using compatible spec instead of spec[$index]")
                    return spec
                }
            }
            
            android.util.Log.w("DatasetBuilder", "No compatible inline spec found")
            return null
        }
        
        /**
         * 创建应用图标
         * 参考 Keyguard 的 createAppIcon
         */
        @RequiresApi(Build.VERSION_CODES.R)
        fun createAppIcon(context: Context, packageName: String?): Icon {
            return try {
                if (!packageName.isNullOrBlank()) {
                    val drawable = context.packageManager.getApplicationIcon(packageName)
                    if (drawable is android.graphics.drawable.BitmapDrawable) {
                        Icon.createWithBitmap(drawable.bitmap).apply {
                            setTintBlendMode(BlendMode.DST)
                        }
                    } else {
                        createDefaultIcon(context)
                    }
                } else {
                    createDefaultIcon(context)
                }
            } catch (e: Exception) {
                android.util.Log.w("DatasetBuilder", "Failed to create app icon", e)
                createDefaultIcon(context)
            }
        }
        
        /**
         * 创建默认图标
         */
        @RequiresApi(Build.VERSION_CODES.R)
        private fun createDefaultIcon(context: Context): Icon {
            return Icon.createWithResource(context, R.mipmap.ic_launcher).apply {
                setTintBlendMode(BlendMode.DST)
            }
        }
    }
    
    /**
     * RemoteViews 工厂
     * 集中管理所有自动填充相关的 RemoteViews 创建
     */
    object RemoteViewsFactory {
        
        /**
         * 创建密码条目展示
         */
        fun createPasswordEntry(
            context: Context,
            title: String,
            username: String,
            iconResId: Int = R.drawable.ic_key
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.autofill_dataset_card).apply {
                setTextViewText(R.id.text_title, title)
                setTextViewText(R.id.text_username, username)
                setImageViewResource(R.id.icon_app, iconResId)
            }
        }
        
        /**
         * 创建手动选择入口展示
         */
        fun createManualSelection(
            context: Context,
            domain: String? = null,
            packageName: String? = null
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.autofill_manual_card).apply {
                setTextViewText(R.id.text_title, "Monica自动填充")
                setTextViewText(R.id.text_username, domain ?: packageName ?: "Monica自动填充")
                setImageViewResource(R.id.icon_app, R.mipmap.ic_launcher)
            }
        }
        
        /**
         * 创建解锁提示展示
         */
        fun createUnlockPrompt(
            context: Context,
            message: String = "解锁 Monica"
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.autofill_manual_card).apply {
                setTextViewText(R.id.text_title, message)
                setTextViewText(R.id.text_username, "需要验证身份")
                setImageViewResource(R.id.icon_app, R.drawable.ic_key)
            }
        }
    }
}

