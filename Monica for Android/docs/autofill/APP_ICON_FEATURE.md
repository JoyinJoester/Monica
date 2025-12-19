# 应用图标显示功能

## 功能说明

为密码列表中关联了应用的密码条目显示应用图标,提升视觉识别度。

## 实现细节

### 1. 图标加载机制

在 `PasswordListScreen.kt` 中添加了 `rememberAppIcon()` 函数:

```kotlin
@Composable
fun rememberAppIcon(context: Context, packageName: String?): Drawable? {
    return remember(packageName) {
        if (packageName.isNullOrEmpty()) {
            null
        } else {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                // 应用未安装
                null
            } catch (e: Exception) {
                // 其他错误
                null
            }
        }
    }
}
```

**特性:**
- 使用 `remember()` 缓存图标,避免重复加载
- 处理应用未安装的情况 (PackageManager.NameNotFoundException)
- 安全处理其他异常

### 2. UI 更新

在 `PasswordEntryCard` 组件中:

```kotlin
// 应用图标或默认密钥图标
val appIcon = rememberAppIcon(context, entry.appPackageName)
if (appIcon != null) {
    // 显示应用图标
    Image(
        painter = rememberDrawablePainter(drawable = appIcon),
        contentDescription = "App Icon",
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .padding(end = 12.dp)
    )
} else {
    // 显示默认密钥图标
    Icon(
        imageVector = Icons.Default.Key,
        contentDescription = "Password Icon",
        modifier = Modifier
            .size(40.dp)
            .padding(end = 12.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}
```

**显示规则:**
- 如果密码条目有 `appPackageName` 且应用已安装 → 显示应用图标(圆形裁剪)
- 否则 → 显示默认密钥图标

### 3. 依赖添加

#### gradle/libs.versions.toml
```toml
# Permissions
accompanist-permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "permissions" }
accompanist-drawablepainter = { group = "com.google.accompanist", name = "accompanist-drawablepainter", version.ref = "permissions" }
```

#### app/build.gradle
```gradle
// Permissions
implementation libs.accompanist.permissions
implementation libs.accompanist.drawablepainter
```

使用 Accompanist DrawablePainter 库将 Android Drawable 转换为 Compose Painter。

### 4. 导入更新

```kotlin
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.google.accompanist.drawablepainter.rememberDrawablePainter
```

## 工作流程

### 保存密码时

1. 用户在应用中填写登录表单并提交
2. 系统显示"保存密码到Monica?"对话框
3. 用户点击"保存" → 启动 AutofillSaveBottomSheet
4. Bottom Sheet 保存密码,自动填充:
   - `appPackageName`: 从 AssistStructure 获取
   - `appName`: 从 PackageManager.getApplicationLabel() 获取
   - `website`: 从 WebDomain 获取

### 查看密码列表时

1. 打开密码列表
2. 对于每个密码条目:
   - 检查 `entry.appPackageName` 是否存在
   - 如果存在 → 尝试加载应用图标
     - 成功 → 显示圆形应用图标
     - 失败(应用已卸载) → 显示默认密钥图标
   - 如果不存在 → 显示默认密钥图标

## 性能优化

### 图标缓存
- 使用 `remember(packageName)` 在 Composable 生命周期内缓存图标
- 只有当 packageName 改变时才重新加载
- 避免滚动列表时反复调用 PackageManager

### 异常处理
- 捕获 `PackageManager.NameNotFoundException`:应用已卸载
- 捕获通用 `Exception`:其他未知错误
- 失败时优雅降级到默认图标

## 测试场景

### 场景 1:保存应用密码
1. 打开测试应用(如 test-app)
2. 填写登录表单
3. 提交表单
4. 点击系统"保存"按钮
5. 在 Bottom Sheet 中确认保存
6. 返回密码列表 → **应显示测试应用的图标**

### 场景 2:网站密码(无应用关联)
1. 通过浏览器保存网站密码
2. 查看密码列表
3. **应显示默认密钥图标**(因为 appPackageName 为空)

### 场景 3:应用已卸载
1. 保存某应用的密码(如 test-app)
2. 卸载该应用
3. 查看密码列表
4. **应显示默认密钥图标**(因为 PackageManager 找不到应用)

### 场景 4:手动创建密码
1. 点击"+"手动添加密码
2. 不填写 appPackageName 字段
3. 保存密码
4. 查看列表 → **应显示默认密钥图标**

## UI 效果

### 应用密码卡片
```
┌─────────────────────────────────────┐
│ [✓] [应用图标] 标题文本        [⋮] │ ← 圆形应用图标(40dp)
│              website.com          │
└─────────────────────────────────────┘
```

### 网站密码卡片
```
┌─────────────────────────────────────┐
│ [✓] [🔑] 标题文本              [⋮] │ ← 默认密钥图标(40dp)
│          website.com              │
└─────────────────────────────────────┘
```

## 技术要点

1. **Drawable → Painter 转换**: 使用 `rememberDrawablePainter()` 
2. **圆形裁剪**: `.clip(CircleShape)` 使图标更美观
3. **图标尺寸**: 统一使用 40dp
4. **间距**: 图标右侧 padding 12dp
5. **颜色**: 默认密钥图标使用主题色 `MaterialTheme.colorScheme.primary`

## 兼容性

- **Android 版本**: 支持所有 Android 版本(PackageManager API 兼容)
- **Accompanist 版本**: 0.32.0
- **Compose 版本**: 与项目 Compose BOM 2024.04.01 兼容

## 后续优化建议

1. **图标预加载**: 在后台线程预加载常用应用图标
2. **LRU 缓存**: 实现全局 LRU 缓存,避免内存泄漏
3. **默认图标变体**: 根据应用类型显示不同默认图标(浏览器🌐、邮箱📧等)
4. **Favicon 支持**: 对于网站密码,尝试加载 favicon.ico
5. **占位符动画**: 图标加载时显示 shimmer 效果

## 相关文件

- `app/src/main/java/takagi/ru/monica/ui/screens/PasswordListScreen.kt`
- `gradle/libs.versions.toml`
- `app/build.gradle`
- `app/src/main/java/takagi/ru/monica/autofill/AutofillSaveBottomSheet.kt`
- `app/src/main/java/takagi/ru/monica/autofill/PasswordSaveHelper.kt`
