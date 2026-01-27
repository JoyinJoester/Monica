# 💾 密码保存失败问题分析

## 🔍 问题确认

**现象**: 在实体机上密码保存失败,但测试可能成功。

## ❓ 存储上限检查结果

### ✅ 代码分析结果:

```kotlin
// PasswordEntryDao.kt
@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertPasswordEntry(entry: PasswordEntry): Long
```

**结论**: 
- ❌ **没有密码数量上限!**
- ✅ 使用 Room 数据库,理论上可以存储无限数量
- ✅ 唯一限制是设备存储空间

## 🐛 可能的失败原因

### 1. 设备存储空间不足 ⭐⭐⭐⭐⭐

**最可能的原因!**

检查方法:
```powershell
adb shell df /data
```

如果显示使用率 > 95%,这就是问题所在。

**解决方案**:
- 清理设备存储
- 卸载不用的应用
- 清除应用缓存

---

### 2. 数据库文件损坏 ⭐⭐⭐⭐

检查方法:
```powershell
adb shell run-as takagi.ru.monica
sqlite3 databases/password_database
.integrity_check
```

**解决方案**:
- 导出现有密码
- 清除应用数据
- 重新导入

---

### 3. 加密失败 ⭐⭐⭐

可能原因:
- KeyStore 问题
- 加密密钥丢失
- 系统 KeyStore 满了

检查日志:
```
SecurityException
KeyStore
Encryption
```

**解决方案**:
```kotlin
// 在 AutofillSaveBottomSheet.kt 中添加更详细的日志
try {
    val encryptedPassword = securityManager.encryptData(password)
    android.util.Log.d("AutofillSave", "✅ 加密成功: ${encryptedPassword.length} bytes")
} catch (e: Exception) {
    android.util.Log.e("AutofillSave", "❌ 加密失败!", e)
    throw e
}
```

---

### 4. 数据库版本冲突 ⭐⭐

如果从旧版本升级:
- 数据库迁移可能失败
- 表结构不匹配

检查方法:
```powershell
adb shell run-as takagi.ru.monica
sqlite3 databases/password_database
.schema password_entries
```

应该看到所有字段,包括:
- id
- title
- username
- password
- website
- appName
- appPackageName
- isFavorite
- sortOrder
- isGroupCover
- createdAt
- updatedAt

---

### 5. 权限问题 (Android 14+) ⭐⭐

某些设备可能有额外的权限限制

---

### 6. 应用被杀死 ⭐⭐

保存过程中应用被系统杀死

检查日志:
```
Process died
LOW_MEMORY
```

---

## 🔧 诊断步骤

### 立即运行诊断脚本:

```powershell
.\diagnose-save-failure.ps1
```

这个脚本会检查:
1. ✅ 当前密码数量 (无上限)
2. ✅ 数据库文件大小
3. ✅ 设备存储空间
4. ✅ 应用权限
5. ✅ 实时错误日志

---

## 📊 预期正常流程

```
1. 用户点击登录
   → onSaveRequest TRIGGERED

2. 显示保存界面
   → SavePasswordBottomSheetContent

3. 用户点击保存
   → 保存密码信息:
   → Username: xxx
   → Password: xxx chars

4. 加密密码
   → 加密成功: xxx bytes

5. 插入数据库
   → insertPasswordEntry
   → ✅ 保存新密码成功!

6. 查询验证
   → 在 Monica 主界面看到新密码
```

---

## 🚨 常见错误模式

### 错误 1: SQLite 错误

```
SQLiteException: disk I/O error
SQLiteException: database is locked
SQLiteException: no such table
```

**原因**: 存储空间不足、数据库损坏、并发访问

### 错误 2: 加密错误

```
SecurityException: Key not found
InvalidKeyException
BadPaddingException
```

**原因**: KeyStore 问题、密钥丢失

### 错误 3: 内存错误

```
OutOfMemoryError
```

**原因**: 设备内存不足

---

## 🔍 深度调试

### 手动检查数据库

```powershell
# 连接到设备
adb shell

# 切换到应用目录
run-as takagi.ru.monica

# 进入数据库目录
cd databases

# 列出所有文件
ls -lh

# 打开数据库
sqlite3 password_database

# 查询密码数量
SELECT COUNT(*) FROM password_entries;

# 查看最近的密码
SELECT id, title, username, website, createdAt 
FROM password_entries 
ORDER BY createdAt DESC 
LIMIT 10;

# 检查数据库完整性
.integrity_check

# 退出
.exit
```

### 查看详细错误

```powershell
# 清除日志
adb logcat -c

# 实时查看所有错误
adb logcat -v time *:E

# 或者只看 Monica 的错误
adb logcat -v time | Select-String "Monica|Autofill|Password" | Select-String "Error|Exception"
```

---

## 💡 临时解决方案

### 方案 1: 清理存储空间

1. 打开设置 → 存储
2. 清理缓存
3. 删除不用的应用
4. 移动照片到云端

### 方案 2: 重建数据库

```powershell
# 1. 先备份现有密码 (在 Monica 中导出)

# 2. 清除应用数据
adb shell pm clear takagi.ru.monica

# 3. 重新安装应用
adb install -r app-debug.apk

# 4. 恢复密码数据
```

### 方案 3: 降级数据库版本

如果是升级后出现问题,可以尝试:
1. 卸载当前版本
2. 安装旧版本
3. 导出数据
4. 重新安装新版本
5. 导入数据

---

## 📝 添加详细日志

在 `AutofillSaveBottomSheet.kt` 的 `savePassword` 方法中添加:

```kotlin
private fun savePassword(...) {
    lifecycleScope.launch {
        try {
            android.util.Log.d("AutofillSave", "━━━━ 开始保存流程 ━━━━")
            
            // 1. 加密
            android.util.Log.d("AutofillSave", "1️⃣ 开始加密密码...")
            val encryptedPassword = securityManager.encryptData(password)
            android.util.Log.d("AutofillSave", "   ✅ 加密成功: ${encryptedPassword.length} bytes")
            
            // 2. 检查重复
            android.util.Log.d("AutofillSave", "2️⃣ 检查重复密码...")
            val existingPasswords = passwordRepository.getAllPasswordEntries().first()
            android.util.Log.d("AutofillSave", "   📊 现有密码数量: ${existingPasswords.size}")
            
            // 3. 创建条目
            android.util.Log.d("AutofillSave", "3️⃣ 创建新密码条目...")
            val newEntry = PasswordSaveHelper.createNewPasswordEntry(...)
            android.util.Log.d("AutofillSave", "   ✅ 条目创建完成")
            
            // 4. 插入数据库
            android.util.Log.d("AutofillSave", "4️⃣ 插入数据库...")
            val newId = passwordRepository.insertPasswordEntry(newEntry)
            android.util.Log.d("AutofillSave", "   ✅ 保存成功! ID=$newId")
            
            // 5. 验证
            android.util.Log.d("AutofillSave", "5️⃣ 验证保存结果...")
            val saved = passwordRepository.getPasswordEntryById(newId)
            if (saved != null) {
                android.util.Log.d("AutofillSave", "   ✅ 验证成功! 密码已正确保存")
            } else {
                android.util.Log.e("AutofillSave", "   ❌ 验证失败! 数据库中找不到刚保存的密码")
            }
            
            android.util.Log.d("AutofillSave", "━━━━ 保存流程完成 ━━━━")
            
        } catch (e: Exception) {
            android.util.Log.e("AutofillSave", "❌ 保存失败!", e)
            android.util.Log.e("AutofillSave", "错误类型: ${e.javaClass.simpleName}")
            android.util.Log.e("AutofillSave", "错误信息: ${e.message}")
            android.util.Log.e("AutofillSave", "堆栈跟踪:", e)
        }
    }
}
```

---

## 🎯 下一步行动

1. **立即运行**: `.\diagnose-save-failure.ps1`
2. **查看日志**: 找到具体错误
3. **报告结果**: 告诉我看到了什么错误
4. **针对性修复**: 根据错误类型解决

---

**需要帮助?** 运行诊断脚本并告诉我结果!
