package takagi.ru.monica.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.text.InputType
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MonicaAccessibilityService : AccessibilityService() {
    private var lastPackageName: String = ""
    private var lastUrl: String = ""
    private var lastScanTime = 0L

    companion object {
        private const val TAG = "MonicaAccessibility"
        private const val SCAN_THROTTLE_MS = 400L
        private const val MAX_SCAN_DEPTH = 8
        private const val MAX_SCAN_NODES = 300
        private const val MAX_FILL_SCAN_NODES = 400
        private const val SCORE_PASSWORD_SIGNAL = 40
        private const val SCORE_USERNAME_SIGNAL = 24
        private const val SCORE_FOCUSED_BONUS = 12
        private const val SCORE_PASSWORD_FLAG = 90
        private const val SCORE_CONFIRM_PENALTY = 35

        @Volatile
        private var activeInstance: MonicaAccessibilityService? = null

        private data class BrowserSpec(
            val packageName: String,
            val urlFieldIds: Set<String>,
        )

        private val browserSpecsByPackage = listOf(
            BrowserSpec(
                packageName = "org.mozilla.fenix",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "org.mozilla.firefox",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "org.mozilla.firefox_beta",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "org.mozilla.fenix.nightly",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "io.github.forkmaintainers.iceraven",
                urlFieldIds = setOf("mozac_browser_toolbar_url_view", "url_bar_title"),
            ),
            BrowserSpec(
                packageName = "com.android.chrome",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.chrome.beta",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.chrome.dev",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.chrome.canary",
                urlFieldIds = setOf("url_bar"),
            ),
            BrowserSpec(
                packageName = "com.google.android.apps.chrome",
                urlFieldIds = setOf("url_bar"),
            ),
        ).associateBy { it.packageName }

        private val trackedEventTypes = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        )

        fun requestCredentialFill(
            targetPackageName: String?,
            username: String,
            password: String,
            preferPasswordField: Boolean,
        ): Boolean {
            return activeInstance?.fillCredentialsInActiveWindow(
                targetPackageName = targetPackageName,
                username = username,
                password = password,
                preferPasswordField = preferPasswordField
            ) ?: false
        }

        fun isCredentialFillAvailable(context: Context): Boolean {
            return isServiceEnabled(context) && activeInstance != null
        }

        fun isServiceEnabled(context: Context): Boolean {
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == context.packageName &&
                        serviceInfo.name == MonicaAccessibilityService::class.java.name
                }
        }
    }

    private enum class FillFieldType {
        USERNAME,
        PASSWORD,
        UNKNOWN
    }

    private data class FillCandidate(
        val node: AccessibilityNodeInfo,
        val type: FillFieldType,
        val score: Int,
        val isFocused: Boolean,
        val top: Int,
        val left: Int
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType !in trackedEventTypes) return

        val packageName = event.packageName?.toString().orEmpty()
        val browserSpec = browserSpecsByPackage[packageName] ?: return

        val now = System.currentTimeMillis()
        if (packageName == lastPackageName && now - lastScanTime < SCAN_THROTTLE_MS) return
        lastScanTime = now

        val root = rootInActiveWindow ?: event.source ?: return
        val url = findBrowserUrl(root, browserSpec) ?: return

        if (packageName == lastPackageName && url == lastUrl) return

        lastPackageName = packageName
        lastUrl = url
        BrowserAutofillContextStore.update(packageName, url)
        ValidatorContextManager.updateContext(packageName, url)
        Log.d(TAG, "Updated browser context: pkg=$packageName, url=$url")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onDestroy()
    }

    private fun fillCredentialsInActiveWindow(
        targetPackageName: String?,
        username: String,
        password: String,
        preferPasswordField: Boolean,
    ): Boolean {
        val root = rootInActiveWindow ?: return false
        val activePackageName = root.packageName?.toString().orEmpty()
        if (
            !targetPackageName.isNullOrBlank() &&
            activePackageName.isNotBlank() &&
            !activePackageName.equals(targetPackageName, ignoreCase = true)
        ) {
            Log.d(TAG, "Skip accessibility fill: active package mismatch ($activePackageName != $targetPackageName)")
            return false
        }

        val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val candidates = collectFillCandidates(root, focusedNode)
        if (candidates.isEmpty()) {
            Log.d(TAG, "Skip accessibility fill: no editable candidates")
            return false
        }

        val focusedCandidate = candidates.firstOrNull { it.isFocused }
        val passwordCandidate = selectBestCandidate(
            candidates = candidates,
            preferredType = FillFieldType.PASSWORD,
            excludedNode = null,
            fallback = if (preferPasswordField) {
                focusedCandidate?.node
            } else {
                nearestCandidate(candidates, focusedCandidate?.node, null)?.node
            }
        )
        val usernameCandidate = selectBestCandidate(
            candidates = candidates,
            preferredType = FillFieldType.USERNAME,
            excludedNode = passwordCandidate?.node,
            fallback = if (!preferPasswordField) {
                focusedCandidate?.node
            } else {
                nearestCandidate(candidates, focusedCandidate?.node, passwordCandidate?.node)?.node
            }
        )

        var usernameFilled = false
        var passwordFilled = false

        if (username.isNotBlank()) {
            usernameFilled = setNodeText(usernameCandidate?.node, username)
        }
        if (password.isNotBlank()) {
            passwordFilled = setNodeText(passwordCandidate?.node, password)
        }

        if (!usernameFilled && !passwordFilled) {
            Log.d(TAG, "Accessibility fill failed: no fields accepted text")
            return false
        }

        Log.d(
            TAG,
            "Accessibility fill success: usernameFilled=$usernameFilled, passwordFilled=$passwordFilled, preferPassword=$preferPasswordField"
        )
        return if (preferPasswordField) {
            passwordFilled || (password.isBlank() && usernameFilled)
        } else {
            usernameFilled || (username.isBlank() && passwordFilled)
        }
    }

    private fun findBrowserUrl(root: AccessibilityNodeInfo, browserSpec: BrowserSpec): String? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val (node, depth) = queue.removeFirst()
            visited += 1

            val viewId = node.viewIdResourceName.orEmpty()
            if (browserSpec.urlFieldIds.any { viewId.endsWith(it) }) {
                extractNodeText(node)?.let { return it }
            }

            if (depth >= MAX_SCAN_DEPTH) continue

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child ->
                    queue.add(child to (depth + 1))
                }
            }
        }

        return null
    }

    private fun extractNodeText(node: AccessibilityNodeInfo): String? {
        return sequenceOf(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString(),
        ).mapNotNull { it?.trim() }
            .firstOrNull { candidate ->
                candidate.isNotBlank() &&
                    candidate.any { it == '.' || it == ':' || it == '/' } &&
                    !candidate.startsWith("Search", ignoreCase = true)
            }
    }

    private fun collectFillCandidates(
        root: AccessibilityNodeInfo,
        focusedNode: AccessibilityNodeInfo?
    ): List<FillCandidate> {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<FillCandidate>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_FILL_SCAN_NODES) {
            val node = queue.removeFirst()
            visited += 1

            if (node.isVisibleToUser && node.isEnabled && supportsSetText(node)) {
                candidates += classifyFillCandidate(node)
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }

        if (
            focusedNode != null &&
            supportsSetText(focusedNode) &&
            candidates.none { it.node == focusedNode }
        ) {
            candidates += classifyFillCandidate(focusedNode)
        }

        return candidates.distinctBy { candidate ->
            val bounds = Rect().also(candidate.node::getBoundsInScreen)
            listOf(
                candidate.node.viewIdResourceName.orEmpty(),
                bounds.left.toString(),
                bounds.top.toString(),
                bounds.right.toString(),
                bounds.bottom.toString()
            ).joinToString("|")
        }
    }

    private fun supportsSetText(node: AccessibilityNodeInfo): Boolean {
        if (node.isEditable) return true
        return node.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SET_TEXT }
    }

    private fun classifyFillCandidate(node: AccessibilityNodeInfo): FillCandidate {
        val bounds = Rect().also(node::getBoundsInScreen)
        val signals = buildList {
            add(node.viewIdResourceName.orEmpty())
            add(node.hintText?.toString().orEmpty())
            add(node.contentDescription?.toString().orEmpty())
            add(node.text?.toString().orEmpty())
            add(node.tooltipText?.toString().orEmpty())
        }.joinToString(" ").lowercase()

        var passwordScore = 0
        var usernameScore = 0

        if (node.isPassword) {
            passwordScore += SCORE_PASSWORD_FLAG
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val inputType = node.inputType
            val inputClass = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (
                (inputClass == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    )) ||
                (inputClass == InputType.TYPE_CLASS_NUMBER &&
                    variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            ) {
                passwordScore += SCORE_PASSWORD_FLAG
            }
            if (
                inputClass == InputType.TYPE_CLASS_TEXT &&
                (
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_PERSON_NAME ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    )
            ) {
                usernameScore += SCORE_USERNAME_SIGNAL
            }
        }

        if (signals.contains("password") || signals.contains("passwd") || signals.contains("pwd")) {
            passwordScore += SCORE_PASSWORD_SIGNAL
        }
        if (signals.contains("passcode") || signals.contains("pin")) {
            passwordScore += SCORE_PASSWORD_SIGNAL / 2
        }
        if (
            signals.contains("confirm") ||
            signals.contains("re-enter") ||
            signals.contains("repeat")
        ) {
            passwordScore -= SCORE_CONFIRM_PENALTY
        }

        if (signals.contains("username") || signals.contains("user_name")) {
            usernameScore += SCORE_USERNAME_SIGNAL + 12
        }
        if (
            signals.contains("email") ||
            signals.contains("e-mail") ||
            signals.contains("login") ||
            signals.contains("account")
        ) {
            usernameScore += SCORE_USERNAME_SIGNAL
        }
        if (signals.contains("phone") || signals.contains("mobile")) {
            usernameScore += SCORE_USERNAME_SIGNAL / 2
        }

        if (node.isFocused) {
            passwordScore += SCORE_FOCUSED_BONUS
            usernameScore += SCORE_FOCUSED_BONUS
        }

        val type = when {
            passwordScore > usernameScore && passwordScore > 0 -> FillFieldType.PASSWORD
            usernameScore > passwordScore && usernameScore > 0 -> FillFieldType.USERNAME
            else -> FillFieldType.UNKNOWN
        }
        val resolvedScore = maxOf(passwordScore, usernameScore)

        return FillCandidate(
            node = node,
            type = type,
            score = resolvedScore,
            isFocused = node.isFocused,
            top = bounds.top,
            left = bounds.left
        )
    }

    private fun selectBestCandidate(
        candidates: List<FillCandidate>,
        preferredType: FillFieldType,
        excludedNode: AccessibilityNodeInfo?,
        fallback: AccessibilityNodeInfo?,
    ): FillCandidate? {
        val exactMatch = candidates
            .asSequence()
            .filter { candidate -> candidate.type == preferredType }
            .filterNot { candidate -> excludedNode != null && candidate.node == excludedNode }
            .sortedWith(
                compareByDescending<FillCandidate> { it.score }
                    .thenByDescending { it.isFocused }
                    .thenBy { it.top }
                    .thenBy { it.left }
            )
            .firstOrNull()
        if (exactMatch != null) return exactMatch

        val fallbackNode = fallback
            ?.takeIf { node -> excludedNode == null || node != excludedNode }
            ?.let { node -> candidates.firstOrNull { it.node == node } }
        if (fallbackNode != null) return fallbackNode

        return nearestCandidate(candidates, fallback, excludedNode)
            ?: candidates.firstOrNull { candidate -> excludedNode == null || candidate.node != excludedNode }
    }

    private fun nearestCandidate(
        candidates: List<FillCandidate>,
        anchorNode: AccessibilityNodeInfo?,
        excludedNode: AccessibilityNodeInfo?
    ): FillCandidate? {
        val anchor = anchorNode ?: return null
        val anchorRect = Rect().also(anchor::getBoundsInScreen)
        val anchorCenterX = anchorRect.centerX()
        val anchorCenterY = anchorRect.centerY()
        return candidates
            .asSequence()
            .filterNot { candidate -> candidate.node == anchor }
            .filterNot { candidate -> excludedNode != null && candidate.node == excludedNode }
            .sortedBy { candidate ->
                val dx = candidate.left - anchorCenterX
                val dy = candidate.top - anchorCenterY
                dx * dx + dy * dy
            }
            .firstOrNull()
    }

    private fun setNodeText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null || text.isBlank()) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return runCatching {
            if (!node.isFocused) {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }.getOrDefault(false)
    }
}
