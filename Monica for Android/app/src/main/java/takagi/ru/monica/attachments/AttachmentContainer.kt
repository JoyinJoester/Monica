package takagi.ru.monica.attachments

import android.content.Context
import takagi.ru.monica.attachments.executor.BitwardenAttachmentExecutor
import takagi.ru.monica.attachments.executor.BitwardenAttachmentReconciler
import takagi.ru.monica.attachments.executor.KeePassAttachmentExecutor
import takagi.ru.monica.attachments.executor.KeePassAttachmentReconciler
import takagi.ru.monica.attachments.executor.LocalAttachmentExecutor
import takagi.ru.monica.attachments.facade.AttachmentBatchMoveAdvisor
import takagi.ru.monica.attachments.facade.AttachmentFacade
import takagi.ru.monica.attachments.repository.AttachmentRepository
import takagi.ru.monica.attachments.storage.AttachmentKeyVault
import takagi.ru.monica.attachments.storage.AttachmentPreviewCache
import takagi.ru.monica.attachments.storage.AttachmentStorage
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.repository.MdbxVaultStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.KeePassKdbxService

/**
 * 附件子系统的进程级入口，用法：
 *
 * ```
 * val facade = AttachmentContainer.facade(context)
 * val advisor = AttachmentContainer.batchMoveAdvisor(context)
 * val reconciler = AttachmentContainer.bitwardenReconciler(context)
 * ```
 *
 * 设计要点：
 * - 所有重资源（Storage / Executor / Facade）按需懒加载，持有 applicationContext；
 * - 不依赖 Koin，避免与现有模块注入方式耦合；
 * - `KeePassKdbxService` 用来注入 [KeePassAttachmentExecutor]，如果业务侧已经通过别的
 *   路径创建了 service，可通过 [registerKeePassService] 替换默认实例。
 */
object AttachmentContainer {

    @Volatile private var appContext: Context? = null

    @Volatile private var repositoryCache: AttachmentRepository? = null
    @Volatile private var storageCache: AttachmentStorage? = null
    @Volatile private var keyVaultCache: AttachmentKeyVault? = null
    @Volatile private var previewCacheCache: AttachmentPreviewCache? = null

    @Volatile private var localExecutorCache: LocalAttachmentExecutor? = null
    @Volatile private var bitwardenExecutorCache: BitwardenAttachmentExecutor? = null
    @Volatile private var keepassExecutorCache: KeePassAttachmentExecutor? = null
    @Volatile private var advisorCache: AttachmentBatchMoveAdvisor? = null
    @Volatile private var reconcilerCache: BitwardenAttachmentReconciler? = null
    @Volatile private var keepassReconcilerCache: KeePassAttachmentReconciler? = null
    @Volatile private var facadeCache: AttachmentFacade? = null
    @Volatile private var mdbxVaultStoreCache: MdbxVaultStore? = null

    @Volatile private var keepassServiceOverride: KeePassKdbxService? = null

    // ---------------------------------------------------------------- public API

    fun facade(context: Context): AttachmentFacade {
        val app = ensureContext(context)
        facadeCache?.let { return it }
        synchronized(this) {
            facadeCache?.let { return it }
            val facade = AttachmentFacade(
                context = app,
                repository = repository(app),
                localExecutor = localExecutor(app),
                bitwardenExecutor = bitwardenExecutor(app),
                keepassExecutor = keepassExecutor(app),
                storage = storage(app),
                keyVault = keyVault(app),
                previewCache = previewCache(app),
                passwordEntryDao = PasswordDatabase.getDatabase(app).passwordEntryDao(),
                mdbxVaultStore = mdbxVaultStore(app),
                fileProviderAuthority = "${app.packageName}.fileprovider"
            )
            facadeCache = facade
            return facade
        }
    }

    fun batchMoveAdvisor(context: Context): AttachmentBatchMoveAdvisor {
        val app = ensureContext(context)
        advisorCache?.let { return it }
        synchronized(this) {
            advisorCache?.let { return it }
            val advisor = AttachmentBatchMoveAdvisor(repository(app))
            advisorCache = advisor
            return advisor
        }
    }

    fun bitwardenReconciler(context: Context): BitwardenAttachmentReconciler {
        val app = ensureContext(context)
        reconcilerCache?.let { return it }
        synchronized(this) {
            reconcilerCache?.let { return it }
            val reconciler = BitwardenAttachmentReconciler(
                repository = repository(app),
                storage = storage(app)
            )
            reconcilerCache = reconciler
            return reconciler
        }
    }

    fun keepassReconciler(context: Context): KeePassAttachmentReconciler {
        val app = ensureContext(context)
        keepassReconcilerCache?.let { return it }
        synchronized(this) {
            keepassReconcilerCache?.let { return it }
            val reconciler = KeePassAttachmentReconciler.create(
                repository = repository(app),
                executor = keepassExecutor(app),
                storage = storage(app)
            )
            keepassReconcilerCache = reconciler
            return reconciler
        }
    }

    fun repository(context: Context): AttachmentRepository {
        val app = ensureContext(context)
        repositoryCache?.let { return it }
        synchronized(this) {
            repositoryCache?.let { return it }
            val db = PasswordDatabase.getDatabase(app)
            val repo = AttachmentRepository(db.attachmentDao())
            repositoryCache = repo
            return repo
        }
    }

    /** 供业务层替换 [KeePassKdbxService]（例如测试或复用已有实例）。 */
    fun registerKeePassService(service: KeePassKdbxService) {
        keepassServiceOverride = service
        // 重置已缓存的 keepass executor，下次调用时重建
        keepassExecutorCache = null
        keepassReconcilerCache = null
        facadeCache = null
    }

    // ---------------------------------------------------------------- lazy builders

    private fun storage(app: Context): AttachmentStorage =
        storageCache ?: synchronized(this) {
            storageCache ?: AttachmentStorage(app).also { storageCache = it }
        }

    private fun keyVault(app: Context): AttachmentKeyVault =
        keyVaultCache ?: synchronized(this) {
            keyVaultCache ?: AttachmentKeyVault(SecurityManager(app)).also { keyVaultCache = it }
        }

    private fun previewCache(app: Context): AttachmentPreviewCache =
        previewCacheCache ?: synchronized(this) {
            previewCacheCache ?: AttachmentPreviewCache(app).also { previewCacheCache = it }
        }

    private fun localExecutor(app: Context): LocalAttachmentExecutor =
        localExecutorCache ?: synchronized(this) {
            localExecutorCache ?: LocalAttachmentExecutor(
                context = app,
                storage = storage(app),
                keyVault = keyVault(app)
            ).also { localExecutorCache = it }
        }

    private fun bitwardenExecutor(app: Context): BitwardenAttachmentExecutor =
        bitwardenExecutorCache ?: synchronized(this) {
            bitwardenExecutorCache ?: BitwardenAttachmentExecutor(
                context = app,
                storage = storage(app),
                keyVault = keyVault(app)
            ).also { bitwardenExecutorCache = it }
        }

    private fun keepassExecutor(app: Context): KeePassAttachmentExecutor =
        keepassExecutorCache ?: synchronized(this) {
            keepassExecutorCache ?: run {
                val db = PasswordDatabase.getDatabase(app)
                val service = keepassServiceOverride
                    ?: KeePassKdbxService(
                        context = app,
                        dao = db.localKeePassDatabaseDao(),
                        securityManager = SecurityManager(app)
                    )
                KeePassAttachmentExecutor(
                    context = app,
                    kdbxService = service,
                    storage = storage(app),
                    keyVault = keyVault(app)
                ).also { keepassExecutorCache = it }
            }
        }

    private fun mdbxVaultStore(app: Context): MdbxVaultStore =
        mdbxVaultStoreCache ?: synchronized(this) {
            mdbxVaultStoreCache ?: run {
                val db = PasswordDatabase.getDatabase(app)
                MdbxVaultStore(
                    context = app,
                    databaseDao = db.localMdbxDatabaseDao(),
                    securityManager = SecurityManager(app),
                    remoteSourceDao = db.mdbxRemoteSourceDao(),
                    passwordEntryDao = db.passwordEntryDao(),
                    secureItemDao = db.secureItemDao()
                ).also { mdbxVaultStoreCache = it }
            }
        }

    private fun ensureContext(context: Context): Context {
        val app = context.applicationContext
        if (appContext !== app) {
            synchronized(this) {
                if (appContext !== app) {
                    appContext = app
                }
            }
        }
        return app
    }
}
