# WebDAV 405 错误解决方案

## 🔍 问题分析

### 错误信息
```
Error contacting https://ajiro.infini-cloud.net/dav/ (405 Method Not Allowed)
```

### 错误原因
405 Method Not Allowed 表示:
- ✅ **认证成功** (不再是 401)
- ❌ **HTTP 方法不被允许**
- 可能的原因:
  1. URL 路径不存在
  2. 服务器不支持 PROPFIND 方法
  3. 需要先创建目录

## ✅ 已修复

### 改进的连接测试逻辑

**位置**: `app/src/main/java/takagi/ru/monica/utils/WebDavHelper.kt`

**新逻辑**:
1. 使用 `exists()` (HEAD 方法) 代替直接 `list()` (PROPFIND)
2. 如果路径不存在,自动尝试创建目录
3. 增强错误提示,区分 405 错误

**关键代码**:
```kotlin
// 1. 先检查路径是否存在
val exists = sardine?.exists(serverUrl) ?: false

// 2. 如果不存在,尝试创建
if (!exists) {
    sardine?.createDirectory(serverUrl)
}

// 3. 列出目录内容
val resources = sardine?.list(serverUrl)
```

## 🛠️ 使用建议

### 正确的 URL 格式

#### ❌ 错误格式
```
https://ajiro.infini-cloud.net/dav/
```
**问题**: 只有基础路径,没有指定备份目录

#### ✅ 正确格式
```
https://ajiro.infini-cloud.net/dav/Monica_Backups/
```
**说明**: 
- 完整路径,包含备份文件夹名
- 以斜杠结尾
- 首次使用会自动创建目录

### 配置步骤

1. **打开应用** → **设置** → **WebDAV备份**

2. **填写配置**:
   ```
   服务器地址: https://ajiro.infini-cloud.net/dav/Monica_Backups/
   用户名: [你的用户名]
   密码: [你的密码]
   ```

3. **点击"测试连接"**
   - 如果目录不存在,会自动创建
   - 成功后会显示"连接成功"

4. **创建备份**
   - 点击"创建新备份"按钮
   - 备份文件会上传到服务器

## 🔎 其他可能的 URL 格式

根据不同的 WebDAV 服务器,可能需要不同的 URL:

### InfiniCLOUD
```
https://ajiro.infini-cloud.net/dav/Monica_Backups/
```

### Nextcloud
```
https://your-server.com/remote.php/dav/files/username/Monica_Backups/
```

### OwnCloud
```
https://your-server.com/remote.php/webdav/Monica_Backups/
```

### Synology NAS
```
https://your-nas.com:5006/Monica_Backups/
```

### 坚果云
```
https://dav.jianguoyun.com/dav/Monica_Backups/
```

## 📋 测试清单

在配置前,请确认:

- [ ] 服务器地址正确
- [ ] 包含完整路径(含备份文件夹名)
- [ ] URL 以斜杠 `/` 结尾
- [ ] 用户名和密码正确
- [ ] 网络连接正常
- [ ] 对该目录有读写权限

## 🐛 如果仍然失败

### 查看详细日志
```bash
adb logcat | grep WebDavHelper
```

### 日志会显示
- 测试的完整 URL
- exists() 检查结果
- 是否尝试创建目录
- 具体的错误类型

### 手动测试 WebDAV
使用浏览器访问:
```
https://ajiro.infini-cloud.net/dav/
```
应该能看到登录界面或文件列表

### 使用 WebDAV 客户端测试
推荐工具:
- **Windows**: WinSCP, Cyberduck
- **macOS**: Cyberduck, Transmit
- **Android**: Solid Explorer, Total Commander

如果这些工具都无法连接,说明可能是服务器配置问题。

## 📞 联系服务商

如果问题持续,建议联系 InfiniCLOUD 支持:
1. 确认 WebDAV 功能是否已启用
2. 询问正确的 WebDAV URL 格式
3. 检查账户权限设置

## ✨ 总结

**已修复**:
- ✅ 改用更兼容的 `exists()` 方法
- ✅ 自动创建不存在的目录
- ✅ 增强 405 错误提示

**建议操作**:
1. 确保 URL 包含备份文件夹名: `/dav/Monica_Backups/`
2. 重新测试连接
3. 查看日志获取详细信息

祝使用顺利! 🎉
