package takagi.ru.monica.autofill.bwcompat.builder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import takagi.ru.monica.autofill.AutofillPickerActivityV2
import takagi.ru.monica.autofill.builder.AutofillDatasetBuilder
import takagi.ru.monica.autofill.bwcompat.model.AutofillRequest
import takagi.ru.monica.autofill.bwcompat.model.AutofillView
import takagi.ru.monica.autofill.bwcompat.model.FilledData

class FillResponseBuilder(
    private val context: Context,
) {
    private companion object {
        private const val TAG = "MonicaAutofillBwCompat"
        // 总列表最多 5 项，其中最后 1 项固定为“打开自动填充页面”。
        private const val MAX_TOTAL_DATASETS = 5
        private const val MANUAL_ENTRY_COUNT = 1
        // Bitwarden-style placeholder for authenticated manual selection dataset.
        private const val MANUAL_PLACEHOLDER_VALUE = "MONICA_AUTOFILL_MANUAL_PLACEHOLDER"
    }

    fun build(
        request: AutofillRequest.Fillable,
        filledData: FilledData,
    ): FillResponse? {
        android.util.Log.i(
            TAG,
            "build start: package=${request.packageName}, uri=${request.uri ?: "n/a"}, " +
                "partitions=${filledData.filledPartitions.size}, ignoredIds=${filledData.ignoreAutofillIds.size}, " +
                "inlineMax=${request.maxInlineSuggestionsCount}, inlineSpecs=${request.inlinePresentationSpecs?.size ?: 0}"
        )
        val responseBuilder = FillResponse.Builder()
        var datasetsAdded = 0

        if (filledData.ignoreAutofillIds.isNotEmpty()) {
            responseBuilder.setIgnoredIds(*filledData.ignoreAutofillIds.toTypedArray())
        }

        // Some apps enforce a smaller suggestion cap (e.g. 3). Reserve the last slot for
        // manual entry so it never gets truncated by the framework.
        val systemDatasetCap = request.maxInlineSuggestionsCount
            .takeIf { it > 0 }
            ?.coerceAtMost(MAX_TOTAL_DATASETS)
            ?: MAX_TOTAL_DATASETS
        val maxCredentialDatasets = (systemDatasetCap - MANUAL_ENTRY_COUNT).coerceAtLeast(0)
        val credentialPartitions = filledData.filledPartitions.take(maxCredentialDatasets)
        android.util.Log.d(
            TAG,
            "datasetCap: raw=${request.maxInlineSuggestionsCount}, inlineSpecs=${request.inlinePresentationSpecs?.size ?: 0}, " +
                "effective=$systemDatasetCap, credentialLimit=$maxCredentialDatasets, matched=${filledData.filledPartitions.size}"
        )
        // 页面建议区需要看到当前上下文全部匹配项；仅系统自动填充列表限制为前 4 个 + 手动入口。
        val suggestedPasswordIds = filledData.filledPartitions
            .mapNotNull { it.autofillCipher.cipherId?.toLongOrNull() }
            .distinct()
            .toLongArray()

        credentialPartitions.forEachIndexed { index, partition ->
            val menuPresentation = AutofillDatasetBuilder.RemoteViewsFactory.createPasswordEntry(
                context = context,
                title = partition.autofillCipher.name,
                username = partition.autofillCipher.subtitle
            )

            val fields: LinkedHashMap<AutofillId, AutofillDatasetBuilder.FieldData?> = linkedMapOf()
            partition.filledItems.forEach { filledItem ->
                fields[filledItem.autofillId] = AutofillDatasetBuilder.FieldData(
                    value = filledItem.value,
                    presentation = menuPresentation
                )
            }
            if (fields.isEmpty()) return@forEachIndexed

            val datasetBuilder = AutofillDatasetBuilder.create(
                menuPresentation = menuPresentation,
                fields = fields
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val spec = partition.inlinePresentationSpec ?: return@create null
                    val pendingIntent = buildInlinePendingIntent(index)
                    AutofillDatasetBuilder.InlinePresentationBuilder.tryCreate(
                        context = context,
                        spec = spec,
                        specs = request.inlinePresentationSpecs,
                        index = index,
                        pendingIntent = pendingIntent,
                        title = partition.autofillCipher.name,
                        subtitle = partition.autofillCipher.subtitle,
                        icon = AutofillDatasetBuilder.InlinePresentationBuilder.createAppIcon(
                            context = context,
                            packageName = request.packageName
                        ),
                        contentDescription = buildString {
                            append(partition.autofillCipher.name)
                            if (partition.autofillCipher.subtitle.isNotBlank()) {
                                append(" ")
                                append(partition.autofillCipher.subtitle)
                            }
                        }
                    )
                } else {
                    null
                }
            }
            responseBuilder.addDataset(datasetBuilder.build())
            datasetsAdded += 1
        }

        val credentialInlineUsed = credentialPartitions.count { it.inlinePresentationSpec != null }
        val manualAdded = addManualSelectionDataset(
            responseBuilder = responseBuilder,
            request = request,
            suggestedPasswordIds = suggestedPasswordIds,
            credentialInlineUsed = credentialInlineUsed
        )
        if (manualAdded) {
            datasetsAdded += 1
        }

        if (datasetsAdded == 0) {
            android.util.Log.w(TAG, "build result: empty response, no datasets added")
            return null
        }

        android.util.Log.i(
            TAG,
            "build result: datasets=$datasetsAdded, credentialDatasets=${datasetsAdded - if (manualAdded) 1 else 0}, " +
                "manualAdded=$manualAdded, suggestedIds=${suggestedPasswordIds.size}, inlineCredentialUsed=$credentialInlineUsed"
        )

        attachSaveInfoIfNeeded(responseBuilder, request)
        return responseBuilder.build()
    }

    private fun buildInlinePendingIntent(index: Int): PendingIntent {
        val requestCode = (System.currentTimeMillis().toInt() and 0x7FFFFFFF) + index
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val intent = Intent().apply {
            action = "takagi.ru.monica.BW_COMPAT_INLINE"
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private fun addManualSelectionDataset(
        responseBuilder: FillResponse.Builder,
        request: AutofillRequest.Fillable,
        suggestedPasswordIds: LongArray,
        credentialInlineUsed: Int,
    ): Boolean {
        val autofillIds = request.partition.views
            .map { it.data.autofillId }
            .distinct()
        if (autofillIds.isEmpty()) {
            android.util.Log.w(TAG, "manual dataset skipped: no autofillIds")
            return false
        }

        val autofillHints = request.partition.views
            .map { view ->
                when (view) {
                    is AutofillView.Login.Username -> "USERNAME"
                    is AutofillView.Login.Password -> "PASSWORD"
                }
            }

        val webDomain = extractWebDomain(request.uri)
        android.util.Log.i(
            TAG,
            "manual dataset building: package=${request.packageName}, domain=${webDomain ?: "n/a"}, " +
                "ids=${autofillIds.size}, hints=${autofillHints.size}, suggestedIds=${suggestedPasswordIds.size}, " +
                "credentialInlineUsed=$credentialInlineUsed, responseAuthMode=false"
        )
        val args = AutofillPickerActivityV2.Args(
            applicationId = request.packageName,
            webDomain = webDomain,
            autofillIds = ArrayList(autofillIds),
            autofillHints = ArrayList(autofillHints),
            suggestedPasswordIds = suggestedPasswordIds,
            isSaveMode = false,
            rememberLastFilled = false,
        )
        val pickerIntent = AutofillPickerActivityV2.getIntent(context, args)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val manualPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt() and 0x7FFFFFFF,
            pickerIntent,
            flags
        )

        val manualPresentation = AutofillDatasetBuilder.RemoteViewsFactory.createManualSelection(
            context = context,
            domain = webDomain,
            packageName = request.packageName
        )
        val manualInline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            createManualInlinePresentation(
                request = request,
                credentialInlineCount = credentialInlineUsed,
                pendingIntent = manualPendingIntent,
                domain = webDomain
            )
        } else {
            null
        }

        val manualFields = linkedMapOf<AutofillId, AutofillDatasetBuilder.FieldData?>()
        autofillIds.forEach { id ->
            manualFields[id] = AutofillDatasetBuilder.FieldData(
                value = AutofillValue.forText(MANUAL_PLACEHOLDER_VALUE),
                presentation = manualPresentation
            )
        }
        val manualDatasetBuilder = AutofillDatasetBuilder.create(
            menuPresentation = manualPresentation,
            fields = manualFields
        ) { manualInline }
        manualDatasetBuilder.setAuthentication(manualPendingIntent.intentSender)
        responseBuilder.addDataset(manualDatasetBuilder.build())
        android.util.Log.d(
            TAG,
            "manual dataset added: ids=${autofillIds.size}, inline=${manualInline != null}, requestCode=${manualPendingIntent.hashCode()}"
        )
        return true
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun createManualInlinePresentation(
        request: AutofillRequest.Fillable,
        credentialInlineCount: Int,
        pendingIntent: PendingIntent,
        domain: String?,
    ): android.service.autofill.InlinePresentation? {
        val specs = request.inlinePresentationSpecs ?: return null
        if (specs.isEmpty()) return null
        val manualSpec = specs.getOrLastOrNull(credentialInlineCount) ?: return null
        val subtitle = domain
            ?.takeIf { it.isNotBlank() }
            ?: request.packageName
        return AutofillDatasetBuilder.InlinePresentationBuilder.tryCreate(
            context = context,
            spec = manualSpec,
            specs = specs,
            index = credentialInlineCount,
            pendingIntent = pendingIntent,
            title = context.getString(takagi.ru.monica.R.string.autofill_manual_entry_title),
            subtitle = subtitle,
            icon = AutofillDatasetBuilder.InlinePresentationBuilder.createAppIcon(
                context = context,
                packageName = request.packageName
            ),
            contentDescription = context.getString(takagi.ru.monica.R.string.autofill_manual_entry_title)
        )
    }

    private fun extractWebDomain(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        return runCatching { Uri.parse(uri).host }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun attachSaveInfoIfNeeded(
        responseBuilder: FillResponse.Builder,
        request: AutofillRequest.Fillable,
    ) {
        if (!request.partition.canPerformSaveRequest) return
        val requiredIds = request.partition.requiredSaveIds.toTypedArray()
        if (requiredIds.isEmpty()) return

        val saveInfoBuilder = SaveInfo.Builder(request.partition.saveType, requiredIds)
        val requiredSet = requiredIds.toSet()
        val optionalIds = request.partition.optionalSaveIds
            .filterNot { requiredSet.contains(it) }
            .toTypedArray()
        if (optionalIds.isNotEmpty()) {
            saveInfoBuilder.setOptionalIds(optionalIds)
        }
        responseBuilder.setSaveInfo(saveInfoBuilder.build())
    }
}

private fun <T> List<T>.getOrLastOrNull(index: Int): T? = getOrNull(index) ?: lastOrNull()
