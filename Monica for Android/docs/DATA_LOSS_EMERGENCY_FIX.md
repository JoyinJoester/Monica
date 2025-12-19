# 🚨 数据丢失紧急修复指南

## 问题确认

✅ **您是对的!** 数据丢失是由于 **APK 签名不一致** 导致的。

### 为什么会发生?

```
旧版本 Monica (签名 A)
        ↓ 尝试安装
新版本 Monica (签名 B)  ← 签名不同!
        ↓
Android: "签名不匹配,无法覆盖安装"
        ↓
必须先卸载旧版
        ↓
所有数据被清除! ❌
```

## 🔍 诊断步骤

### 1. 运行诊断脚本

```powershell
# 在项目根目录运行
.\check-signature.ps1
```

这个脚本会:
- ✅ 检查设备上已安装的 Monica 签名
- ✅ 检查本地编译的 APK 签名
- ✅ 比较两者是否匹配
- ✅ 备份当前的 debug keystore

### 2. 手动检查签名

如果设备已连接:

```powershell
# 导出已安装的 APK
$path = adb shell pm path takagi.ru.monica
$path = $path.Replace("package:", "").Trim()
adb pull $path installed-monica.apk

# 查看签名
keytool -printcert -jarfile installed-monica.apk
```

查看本地编译的 APK:

```powershell
# 先编译
.\gradlew assembleDebug

# 查看签名
keytool -printcert -jarfile app\build\outputs\apk\debug\app-debug.apk
```

比较两个 APK 的 **SHA256 指纹**,如果不同 = 签名不匹配!

## 💡 解决方案

### 方案 A: 使用统一的 Debug Keystore (最简单)

**适用场景**: 个人开发,测试阶段

**步骤**:

1. **备份当前的 debug keystore**:
   ```powershell
   # 创建 keystore 目录
   New-Item -ItemType Directory -Force -Path "keystore"
   
   # 复制 debug keystore
   Copy-Item "$env:USERPROFILE\.android\debug.keystore" "keystore\debug.keystore"
   ```

2. **配置项目使用固定的 debug keystore**:
   
   在 `app/build.gradle` 的 `android {}` 块中添加:
   
   ```gradle
   android {
       // ... 其他配置 ...
       
       signingConfigs {
           debug {
               storeFile file('../keystore/debug.keystore')
               storePassword 'android'
               keyAlias 'androiddebugkey'
               keyPassword 'android'
           }
       }
       
       buildTypes {
           debug {
               signingConfig signingConfigs.debug
               // ... 其他配置 ...
           }
           release {
               signingConfig signingConfigs.debug  // 暂时也用 debug 签名
               // ... 其他配置 ...
           }
       }
   }
   ```

3. **重新编译并安装**:
   ```powershell
   .\gradlew clean
   .\gradlew assembleDebug
   adb install -r app\build\outputs\apk\debug\app-debug.apk
   ```

4. **以后在任何机器上**:
   - 使用相同的 `keystore/debug.keystore`
   - 签名就会保持一致

---

### 方案 B: 创建正式的 Release Keystore (推荐用于发布)

**适用场景**: 准备发布,需要正式签名

**步骤**:

1. **生成 release keystore**:
   ```powershell
   # 创建 keystore 目录
   New-Item -ItemType Directory -Force -Path "keystore"
   
   # 生成 keystore (请修改密码!)
   keytool -genkeypair `
       -v `
       -keystore keystore/monica-release.jks `
       -alias monica `
       -keyalg RSA `
       -keysize 2048 `
       -validity 10000 `
       -storepass "YourStrongPassword123!" `
       -keypass "YourStrongPassword123!" `
       -dname "CN=Monica Password Manager, OU=Development, O=Monica, L=Beijing, S=Beijing, C=CN"
   ```

2. **创建 keystore.properties**:
   ```powershell
   @"
   storeFile=keystore/monica-release.jks
   storePassword=YourStrongPassword123!
   keyAlias=monica
   keyPassword=YourStrongPassword123!
   "@ | Out-File -FilePath "keystore.properties" -Encoding utf8
   ```

3. **配置 app/build.gradle**:
   
   在文件顶部 (android 块之前) 添加:
   ```gradle
   def keystorePropertiesFile = rootProject.file("keystore.properties")
   def keystoreProperties = new Properties()
   if (keystorePropertiesFile.exists()) {
       keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
   }
   ```
   
   在 android 块中:
   ```gradle
   android {
       signingConfigs {
           release {
               if (keystorePropertiesFile.exists()) {
                   storeFile file(keystoreProperties['storeFile'])
                   storePassword keystoreProperties['storePassword']
                   keyAlias keystoreProperties['keyAlias']
                   keyPassword keystoreProperties['keyPassword']
               }
           }
           debug {
               // Debug 也使用相同签名
               if (keystorePropertiesFile.exists()) {
                   storeFile file(keystoreProperties['storeFile'])
                   storePassword keystoreProperties['storePassword']
                   keyAlias keystoreProperties['keyAlias']
                   keyPassword keystoreProperties['keyPassword']
               }
           }
       }
       
       buildTypes {
           release {
               signingConfig signingConfigs.release
               // ... 其他配置 ...
           }
           debug {
               signingConfig signingConfigs.debug
               // ... 其他配置 ...
           }
       }
   }
   ```

4. **重新编译**:
   ```powershell
   .\gradlew clean
   .\gradlew assembleDebug
   ```

---

### 方案 C: 尝试恢复旧签名 (如果设备上还有旧版)

**如果设备上还安装着旧版本**:

1. **导出旧版 APK**:
   ```powershell
   .\check-signature.ps1
   # 这会导出 installed-monica.apk
   ```

2. **查看旧版签名信息**:
   ```powershell
   keytool -printcert -jarfile installed-monica.apk
   ```

3. **如果是 debug 签名** (`CN=Android Debug`):
   - 找到生成旧版 APK 的那台机器
   - 复制它的 `C:\Users\<用户名>\.android\debug.keystore`
   - 使用那个 keystore

4. **如果是 release 签名**:
   - 找到原来的 keystore 文件
   - 如果找不到,**无法恢复** 😢

## 📋 防止未来数据丢失

### 1. 使用统一签名

✅ **建议**: 选择方案 A 或 B,配置固定的 keystore

### 2. 备份 Keystore

```powershell
# 多个位置备份
Copy-Item "keystore\monica-release.jks" "D:\Backup\monica-keystore.jks"
Copy-Item "keystore\monica-release.jks" "E:\USB\monica-keystore.jks"

# 云备份 (加密后)
# 上传到 OneDrive/Google Drive 等
```

### 3. 记录密码

在密码管理器中保存:
- Keystore 文件位置
- Store password
- Key alias
- Key password

### 4. 测试覆盖安装

每次发布前:
```powershell
# 1. 安装旧版本
adb install old-version.apk

# 2. 覆盖安装新版本
adb install -r new-version.apk

# 3. 验证数据未丢失
# 如果失败 = 签名不匹配!
```

## 🚀 立即行动

### 如果设备上还有数据:

1. **不要卸载应用!**
2. **立即备份数据**:
   - 使用 Monica 的 WebDAV 备份功能
   - 或手动导出所有密码
3. 运行 `.\check-signature.ps1` 诊断
4. 选择合适的解决方案
5. 配置签名后重新编译
6. 测试覆盖安装

### 如果数据已经丢失:

1. 检查是否有 WebDAV 备份
2. 检查 Android 系统备份
3. 配置正确的签名
4. 重新编译安装
5. 从备份恢复数据

## ❓ 常见问题

**Q: 为什么不同机器的 debug 签名不同?**  
A: 每台机器的 debug keystore 是独立生成的。需要共享同一个 keystore 文件。

**Q: 已经换了签名,用户怎么升级?**  
A: 必须卸载旧版,安装新版。提醒用户先备份数据!

**Q: 可以改回旧签名吗?**  
A: 如果还能找到旧的 keystore 文件,可以。否则不行。

**Q: Keystore 文件可以提交到 Git 吗?**  
A: **绝对不可以!** 会有安全风险。使用 .gitignore 排除。

---

**需要帮助?** 告诉我您的选择,我可以帮您配置签名!
