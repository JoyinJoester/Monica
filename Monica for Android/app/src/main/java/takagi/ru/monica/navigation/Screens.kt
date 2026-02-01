package takagi.ru.monica.navigation

/**
 * Screen destinations for navigation
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Main : Screen("main?tab={tab}") {
        fun createRoute(tab: Int = 0): String {
            return "main?tab=$tab"
        }
        const val routePattern = "main?tab={tab}"
    }
    object PasswordList : Screen("password_list")
    object DataList : Screen("data_list")  // 新的统一数据列表界面
    object NoteList : Screen("note_list")
    object AddEditNote : Screen("add_edit_note/{noteId}") {
        fun createRoute(noteId: Long? = null): String {
            return if (noteId != null) {
                "add_edit_note/$noteId"
            } else {
                "add_edit_note/-1"
            }
        }
    }
    object AddEditPassword : Screen("add_edit_password/{passwordId}") {
        fun createRoute(passwordId: Long? = null): String {
            return if (passwordId != null) {
                "add_edit_password/$passwordId"
            } else {
                "add_edit_password/-1"
            }
        }
    }
    object AddEditTotp : Screen("add_edit_totp/{totpId}") {
        fun createRoute(totpId: Long? = null): String {
            return if (totpId != null) {
                "add_edit_totp/$totpId"
            } else {
                "add_edit_totp/-1"
            }
        }
    }
    object AddEditBankCard : Screen("add_edit_bank_card/{cardId}") {
        fun createRoute(cardId: Long? = null): String {
            return if (cardId != null) {
                "add_edit_bank_card/$cardId"
            } else {
                "add_edit_bank_card/-1"
            }
        }
    }
    object AddEditDocument : Screen("add_edit_document/{documentId}") {
        fun createRoute(documentId: Long? = null): String {
            return if (documentId != null) {
                "add_edit_document/$documentId"
            } else {
                "add_edit_document/-1"
            }
        }
    }
    object DocumentDetail : Screen("document_detail/{documentId}") {
        fun createRoute(documentId: Long): String {
            return "document_detail/$documentId"
        }
    }
    object PasswordDetail : Screen("password_detail/{passwordId}") {
        fun createRoute(passwordId: Long): String {
            return "password_detail/$passwordId"
        }
    }
    object QrScanner : Screen("qr_scanner")
    object QuickTotpScan : Screen("quick_totp_scan")  // 快速扫码添加验证器
    object Settings : Screen("settings")
    object ResetPassword : Screen("reset_password?skipCurrentPassword={skipCurrentPassword}") {
        fun createRoute(skipCurrentPassword: Boolean = false): String {
            return "reset_password?skipCurrentPassword=$skipCurrentPassword"
        }
    }
    object ForgotPassword : Screen("forgot_password")
    object SecurityQuestionsSetup : Screen("security_questions_setup")
    object SecurityQuestionsVerification : Screen("security_questions_verification")
    object SupportAuthor : Screen("support_author")
    object WebDavBackup : Screen("webdav_backup")
    object KeePassWebDav : Screen("keepass_webdav")  // KeePass 兼容性配置页面
    object ExportData : Screen("export_data")
    object ImportData : Screen("import_data")
    object ChangePassword : Screen("change_password")
    object SecurityQuestion : Screen("security_question")
    object AutofillSettings : Screen("autofill_settings")
    object PasskeySettings : Screen("passkey_settings")
    object SecurityAnalysis : Screen("security_analysis")
    object BottomNavSettings : Screen("bottom_nav_settings")
    object ColorSchemeSelection : Screen("color_scheme_selection")
    object CustomColorSettings : Screen("custom_color_settings")
    object Generator : Screen("generator")  // 添加生成器页面路由
    object DeveloperSettings : Screen("developer_settings")  // 添加开发者设置页面路由
    object PermissionManagement : Screen("permission_management")  // 权限管理页面路由
    object Extensions : Screen("extensions")  // 功能拓展页面路由
    object PasswordFieldCustomization : Screen("password_field_customization")  // 添加密码页面字段定制
    object SyncBackup : Screen("sync_backup")  // 同步与备份页面路由
    object LocalKeePass : Screen("local_keepass")  // 本地 KeePass 数据库页面
    object MonicaPlus : Screen("monica_plus") // Monica Plus 页面
    object Payment : Screen("payment") // 付款页面
}