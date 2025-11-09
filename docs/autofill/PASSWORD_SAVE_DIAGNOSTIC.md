# 🔍 密码自动保存问题诊断清单

## 问题描述
密码自动保存功能没有成功保存到 Monica 软件

## 🎯 诊断步骤

### 1. 运行诊断脚本

```powershell
.\diagnose-autosave.ps1
```

这个脚本会实时监控日志，帮助您找出问题所在。

### 2. 手动检查清单

#### ✅ 前置条件检查

- [ ] Monica 应用已安装
- [ ] 已在系统设置中启用 Monica 的自动填充服务
  - **路径**: 设置 → 系统 → 语言和输入法 → 自动填充服务 → 选择 Monica
- [ ] Monica 应用有必要的权限
- [ ] 设备已连接并启用 USB 调试（用于查看日志）

#### 🔍 功能流程检查

**步骤 1: 触发保存请求**
- [ ] 打开任意应用（如浏览器、登录应用）
- [ ] 输入用户名和密码
- [ ] 点击登录按钮
- [ ] **预期**: 应该触发 `onSaveRequest` 回调

**步骤 2: 显示保存界面**
- [ ] **预期**: 应该弹出 Monica 的密码保存对话框（BottomSheet）
- [ ] 对话框显示正确的用户名和密码
- [ ] 可以编辑标题、网站等信息

**步骤 3: 保存到数据库**
- [ ] 点击"保存"按钮
- [ ] **预期**: 密码应该保存到数据库
- [ ] 在 Monica 主界面可以看到新保存的密码

### 3. 日志关键词检查

运行 `diagnose-autosave.ps1` 后，查找以下关键日志：

#### ✅ 正常流程的日志:

```
1. onSaveRequest 被触发:
   💾💾💾 onSaveRequest TRIGGERED! 💾💾💾

2. 解析保存数据:
   📱 保存密码信息:
   - Username: xxx
   - Password: xxx chars
   - Website: xxx

3. 显示保存界面:
   SavePasswordBottomSheetContent

4. 保存成功:
   ✅ 保存新密码成功!
```

#### ❌ 可能的问题日志:

```
1. onSaveRequest 未触发
   → 检查自动填充服务是否启用
   → 检查应用是否设置了 FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE

2. "无法获取填充上下文"
   → SaveRequest 数据为空
   → 应用可能阻止了自动填充

3. "保存密码失败"
   → 数据库操作失败
   → 权限问题
```

## 🐛 常见问题和解决方案

### 问题 1: onSaveRequest 从未被触发

**可能原因**:
1. 自动填充服务未启用
2. 应用禁用了自动填充
3. 表单没有正确标记为登录表单

**解决方案**:
1. 检查自动填充服务设置
   ```
   adb shell settings get secure autofill_service
   ```
   应该显示: `takagi.ru.monica/.autofill.MonicaAutofillService`

2. 如果未设置，手动启用:
   - 设置 → 系统 → 语言和输入法 → 自动填充服务 → Monica

3. 测试不同的应用（有些应用会禁用自动填充）

### 问题 2: onSaveRequest 触发了，但没有显示保存界面

**可能原因**:
1. PendingIntent 创建失败
2. Activity 启动失败
3. BottomSheet 显示失败

**解决方案**:

检查日志中是否有:
```
✅ 返回 PendingIntent 给系统
```

如果没有，检查代码中的 PendingIntent 创建逻辑。

### 问题 3: 保存界面显示了，但点击保存后没有保存成功

**可能原因**:
1. 数据库操作失败
2. 加密失败
3. Repository 未正确初始化

**解决方案**:

1. 检查日志中的错误信息:
   ```
   保存密码失败
   ```

2. 检查数据库权限:
   ```powershell
   adb shell run-as takagi.ru.monica ls -la /data/data/takagi.ru.monica/databases/
   ```

3. 检查加密服务是否正常工作

### 问题 4: 保存成功，但在 Monica 中看不到

**可能原因**:
1. UI 未刷新
2. 数据库查询问题
3. 过滤条件问题

**解决方案**:

1. 重启 Monica 应用
2. 检查数据库中是否真的有数据:
   ```powershell
   adb shell run-as takagi.ru.monica
   cd databases
   sqlite3 password_database
   SELECT * FROM password_entries ORDER BY id DESC LIMIT 5;
   ```

## 🔧 调试工具

### 实时日志监控

```powershell
# 只看 Monica 的日志
adb logcat -v time | Select-String "MonicaAutofill|AutofillSave"

# 看所有自动填充相关日志
adb logcat -v time | Select-String "Autofill"

# 清除日志后重新测试
adb logcat -c
adb logcat -v time | Select-String "MonicaAutofill"
```

### 数据库检查

```powershell
# 进入应用数据目录
adb shell run-as takagi.ru.monica

# 查看数据库文件
ls -la databases/

# 打开数据库
sqlite3 databases/password_database

# 查询所有密码（加密的）
SELECT id, title, username, website, appName FROM password_entries;

# 查询最近保存的密码
SELECT id, title, username, website, appName, createdAt 
FROM password_entries 
ORDER BY createdAt DESC 
LIMIT 10;
```

### 自动填充服务状态检查

```powershell
# 检查当前自动填充服务
adb shell settings get secure autofill_service

# 启用 Monica 自动填充（如果未启用）
adb shell settings put secure autofill_service takagi.ru.monica/.autofill.MonicaAutofillService

# 禁用自动填充（用于测试）
adb shell settings put secure autofill_service null
```

## 📊 测试应用推荐

使用以下应用测试自动填充功能:

### 推荐测试应用:
1. **Chrome 浏览器** - 标准 Web 表单
2. **Firefox 浏览器** - WebView 测试
3. **项目中的 test-app** - 专门的测试应用
4. **任意常用应用** - 真实场景测试

### 测试步骤:
1. 打开应用
2. 找到登录页面
3. 输入新的用户名和密码
4. 点击登录
5. 查看是否弹出保存提示
6. 点击保存
7. 验证密码已保存

## 🎯 快速测试脚本

使用项目中的测试应用:

```powershell
# 编译测试应用
.\gradlew :test-app:assembleDebug

# 安装测试应用
adb install -r test-app\build\outputs\apk\debug\test-app-debug.apk

# 启动测试应用
adb shell am start -n com.test.autofilltest/.MainActivity

# 同时开始监控日志
.\diagnose-autosave.ps1
```

## 📝 报告问题

如果问题仍然存在，请提供以下信息:

1. **设备信息**:
   ```powershell
   adb shell getprop ro.build.version.release  # Android 版本
   adb shell getprop ro.product.model          # 设备型号
   ```

2. **完整日志**:
   ```powershell
   adb logcat -v time > autofill-debug.log
   # 然后测试保存功能
   # Ctrl+C 停止
   ```

3. **测试的应用信息**:
   - 应用名称
   - 包名
   - 是否是 WebView

4. **具体现象**:
   - onSaveRequest 是否触发
   - 保存界面是否显示
   - 点击保存后有什么反应
   - 有没有错误提示

---

**需要帮助?** 运行诊断脚本: `.\diagnose-autosave.ps1`
