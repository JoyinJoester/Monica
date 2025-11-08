# Monica 无障碍服务 - 无系统对话框模式

## 🎯 功能特点

✅ **完全自定义 Material 3 UI**
✅ **没有任何系统对话框**
✅ **自动检测登录表单**
✅ **智能识别表单提交**

## 📱 启用步骤

### 1. 打开 Monica 设置
- 打开 Monica 应用
- 进入"设置"页面

### 2. 启用无障碍服务
1. 点击"无系统对话框模式"卡片
2. 点击"前往设置启用"
3. 在系统设置中找到"Monica 自动填充"
4. 开启服务
5. 允许所需权限

### 3. 测试功能
1. 打开任意应用的登录页面
2. 输入用户名和密码
3. 点击登录/提交按钮
4. **🎉 直接看到 Monica 的 Material 3 Bottom Sheet!**
5. **没有系统对话框!**

## 🔍 工作原理

### 检测登录表单
```
用户打开应用/网页
↓
Accessibility Service 检测到登录字段
↓
缓存表单信息(用户名、密码字段)
```

### 检测表单提交
```
用户点击登录/注册按钮
↓
Accessibility Service 识别按钮类型
↓
提取用户名和密码值
↓
直接显示 Material 3 Bottom Sheet
↓
用户确认保存
```

## 🆚 对比两种模式

### Autofill Framework 模式(旧)
- ✅ 系统原生支持
- ❌ 必须显示系统对话框
- ❌ UI 不可定制
- 流程: **系统对话框** → PendingIntent → 自定义 UI

### Accessibility Service 模式(新)
- ✅ 完全自定义 UI
- ✅ 没有系统对话框
- ✅ 更好的用户体验
- ⚠️ 需要额外权限
- 流程: **直接显示自定义 UI**

## 🔧 技术细节

### 监听的事件类型
- `TYPE_WINDOW_STATE_CHANGED`: 检测新窗口(登录页面)
- `TYPE_VIEW_CLICKED`: 检测按钮点击(提交表单)
- `TYPE_WINDOW_CONTENT_CHANGED`: 监听文本变化

### 字段识别策略
通过以下属性匹配字段:
- `viewIdResourceName`: 如 "username", "password"
- `text`: 字段显示的文本
- `hintText`: 字段的提示文本
- `contentDescription`: 无障碍描述

### 提交按钮识别
匹配以下关键词:
- 英文: login, sign in, submit, continue
- 中文: 登录, 注册, 提交, 确定

## 📊 日志标签

所有日志使用标签: `MonicaAccessibility`

查看日志:
```bash
adb logcat -s MonicaAccessibility
```

## ⚠️ 注意事项

1. **权限要求**: 需要用户手动授予无障碍权限
2. **兼容性**: 部分应用可能阻止无障碍服务
3. **安全性**: 只在用户点击提交后才读取密码
4. **隐私**: 不缓存密码值,不发送任何数据

## 🐛 故障排除

### 服务未检测到表单
1. 检查服务是否已启用
2. 查看 logcat 日志
3. 尝试重启服务

### 提交时未弹出对话框
1. 确认按钮文本包含关键词
2. 检查密码字段是否有值
3. 查看日志排查

### 服务被系统关闭
1. 在系统设置中关闭电池优化
2. 锁定 Monica 应用
3. 允许后台运行

## 🎨 UI 定制

所有 UI 使用现有的 `AutofillSaveTransparentActivity` 和 `AutofillSaveBottomSheet`,保持一致的 Material 3 设计风格!

---

**享受无系统对话框的丝滑体验! 🚀**
