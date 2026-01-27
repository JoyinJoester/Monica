# Monica Wear - WebDAV同步功能实现指南

## 已完成

### 1. WearWebDavHelper (✅ 已创建)
- **位置**: `Monica-wear/src/main/java/takagi/ru/monica/wear/utils/WearWebDavHelper.kt`
- **功能**:
  - WebDAV连接配置（服务器地址、用户名、密码）
  - 加密配置支持
  - 自动同步逻辑（每天首次+12小时间隔）
  - 下载最新备份文件
  - 解密加密的备份
  - 导入TOTP数据到本地数据库
  - **仅下载同步，不上传**

## 待实现

### 2. EncryptionHelper (需要创建)
**位置**: `Monica-wear/src/main/java/takagi/ru/monica/wear/utils/EncryptionHelper.kt`

```kotlin
object EncryptionHelper {
    fun decryptFile(encryptedFile: File, decryptedFile: File, password: String): Result<Unit>
}
```

从原项目`app/src/main/java/takagi/ru/monica/utils/EncryptionHelper.kt`复制解密功能。

### 3. SyncWorker (需要创建)
**位置**: `Monica-wear/src/main/java/takagi/ru/monica/wear/workers/SyncWorker.kt`

```kotlin
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val helper = WearWebDavHelper(applicationContext)
        
        if (!helper.isConfigured()) return Result.success()
        if (!helper.isAutoSyncEnabled()) return Result.success()
        if (!helper.shouldAutoSync()) return Result.success()
        
        val syncResult = helper.downloadAndImportLatestBackup()
        
        return if (syncResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
```

### 4. Material 3 设置页面 (需要升级)
**位置**: `Monica-wear/src/main/java/takagi/ru/monica/wear/ui/screens/SettingsScreen.kt`

使用Material 3组件重新设计：
- `androidx.compose.material3.*`
- Card组件显示同步状态
- ListItem组件显示设置项
- Material You动态颜色主题

### 5. WebDAV配置对话框 (需要创建)
**位置**: `Monica-wear/src/main/java/takagi/ru/monica/wear/ui/components/WebDavConfigDialog.kt`

包含：
- 服务器地址输入（TextField）
- 用户名输入
- 密码输入（密码类型）
- 加密选项开关
- 加密密码输入（条件显示）
- 测试连接按钮
- 保存/取消按钮

### 6. 同步状态ViewModel (需要创建)
**位置**: `Monica-wear/src/main/java/takagi/ru/monica/wear/viewmodel/SettingsViewModel.kt`

```kotlin
class SettingsViewModel(context: Context) : ViewModel() {
    private val webDavHelper = WearWebDavHelper(context)
    
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _lastSyncTime = MutableStateFlow(0L)
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()
    
    suspend fun syncNow() {
        _syncState.value = SyncState.Syncing
        val result = webDavHelper.downloadAndImportLatestBackup()
        _syncState.value = if (result.isSuccess) {
            SyncState.Success(result.getOrNull() ?: 0)
        } else {
            SyncState.Error(result.exceptionOrNull()?.message ?: "同步失败")
        }
        _lastSyncTime.value = webDavHelper.getLastSyncTime()
    }
}

sealed class SyncState {
    object Idle : SyncState()
    object Syncing : SyncState()
    data class Success(val count: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}
```

### 7. 应用启动时触发同步
**位置**: `Monica-wear/src/main/java/takagi/ru/monica/wear/MainActivity.kt`

在`onCreate`或`LaunchedEffect`中：

```kotlin
LaunchedEffect(Unit) {
    val helper = WearWebDavHelper(context)
    if (helper.isConfigured() && helper.shouldAutoSync()) {
        launch {
            helper.downloadAndImportLatestBackup()
        }
    }
    
    // 启动WorkManager定期检查
    val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
        repeatInterval = 1, // 每1小时检查一次
        repeatIntervalTimeUnit = TimeUnit.HOURS
    ).build()
    
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(
            "auto_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
}
```

## 依赖项检查

确保`Monica-wear/build.gradle`包含：

```gradle
dependencies {
    // WebDAV
    implementation 'com.github.thegrizzlylabs:sardine-android:0.8'
    
    // WorkManager
    implementation "androidx.work:work-runtime-ktx:2.8.1"
    
    // Material 3
    implementation "androidx.compose.material3:material3:1.2.0"
}
```

## 使用流程

1. **用户配置WebDAV**:
   - 打开设置页面
   - 点击"WebDAV配置"
   - 输入服务器地址、用户名、密码
   - 可选：启用加密并设置加密密码
   - 点击"测试连接"
   - 保存配置

2. **自动同步**:
   - 每天首次打开应用时自动同步
   - 距离上次同步超过12小时也会自动同步
   - WorkManager每小时检查一次是否需要同步

3. **手动同步**:
   - 设置页面显示"立即同步"按钮
   - 显示最后同步时间
   - 显示同步状态（同步中/成功/失败）

## 下一步行动

1. 从原项目复制`EncryptionHelper`的解密功能
2. 创建`SyncWorker`
3. 升级`SettingsScreen`为Material 3设计
4. 创建`WebDavConfigDialog`
5. 实现`SettingsViewModel`
6. 在应用启动时集成同步逻辑

## 测试要点

- [ ] WebDAV连接测试
- [ ] 下载未加密备份
- [ ] 下载加密备份并解密
- [ ] 导入TOTP数据到数据库
- [ ] 首次打开触发同步
- [ ] 12小时后触发同步
- [ ] 手动同步功能
- [ ] WorkManager定期检查
- [ ] 错误处理和用户提示
