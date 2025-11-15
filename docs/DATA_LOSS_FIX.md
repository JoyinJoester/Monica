# 数据丢失问题修复说明

## 🚨 严重问题

**版本**: 1.0.8 及以前版本  
**问题**: 安装到实体机后,设备上的 Monica 数据被清空

## 问题根源

应用的 Android 备份配置不完整,导致在版本升级时:

1. **旧版本数据未被正确备份**
   - `backup_rules.xml` 和 `data_extraction_rules.xml` 文件几乎为空
   - 没有明确指定要备份的数据(数据库、SharedPreferences 等)

2. **新版本安装时数据恢复失败**
   - Android 系统尝试恢复数据,但因为备份规则不正确,恢复了空数据
   - 或者因为签名/版本不一致,系统清除了旧数据

3. **影响范围**
   - 所有密码数据 (数据库文件)
   - 应用设置 (SharedPreferences)
   - 用户配置文件

## ✅ 修复方案 (V1.0.9)

### 1. 完善备份规则

**`backup_rules.xml`** (API < 31):
```xml
<full-backup-content>
    <!-- 备份 SharedPreferences (应用设置) -->
    <include domain="sharedpref" path="."/>
    
    <!-- 备份数据库文件 (密码数据) -->
    <include domain="database" path="."/>
    
    <!-- 备份应用内部文件 -->
    <include domain="file" path="."/>
    
    <!-- 排除敏感的设备特定文件 -->
    <exclude domain="sharedpref" path="device.xml"/>
    
    <!-- 排除临时缓存 -->
    <exclude domain="file" path="cache"/>
</full-backup-content>
```

**`data_extraction_rules.xml`** (API 31+):
```xml
<data-extraction-rules>
    <cloud-backup>
        <include domain="sharedpref" path="."/>
        <include domain="database" path="."/>
        <include domain="file" path="."/>
        <exclude domain="sharedpref" path="device.xml"/>
        <exclude domain="file" path="cache"/>
    </cloud-backup>
    
    <device-transfer>
        <include domain="sharedpref" path="."/>
        <include domain="database" path="."/>
        <include domain="file" path="."/>
        <exclude domain="sharedpref" path="device.xml"/>
        <exclude domain="file" path="cache"/>
    </device-transfer>
</data-extraction-rules>
```

### 2. 版本更新

- **versionCode**: 7 → 8
- **versionName**: "1.0.8" → "1.0.8"

## 🛡️ 预防措施

### 对于已经丢失数据的用户:

1. **检查是否有备份**:
   ```
   设置 → Monica → WebDAV 备份
   ```

2. **检查 Android 系统备份**:
   ```
   设置 → 系统 → 备份
   ```
   - 如果启用了 Google Drive 备份,可能可以恢复

3. **卸载并重新安装旧版本**:
   - 如果有旧版本 APK,先卸载当前版本
   - 安装旧版本,看是否能恢复数据
   - ⚠️ **在卸载前请先尝试导出数据!**

### 对于未更新的用户:

**在更新到 1.0.8 之前,请务必**:

1. ✅ 使用 WebDAV 备份功能备份所有密码
2. ✅ 或者导出密码到安全位置
3. ✅ 记录重要密码

## 📱 安全升级步骤

1. **备份数据** (非常重要!)
   - 在 Monica 中使用 WebDAV 备份
   - 或手动导出所有密码

2. **卸载旧版本**
   ```
   注意: 卸载会删除所有数据!
   ```

3. **安装新版本 (1.0.8)**

4. **恢复数据**
   - 从 WebDAV 恢复
   - 或重新导入密码

## 🔍 技术细节

### Android 备份机制

Android 提供两种自动备份:

1. **Auto Backup** (API 23+)
   - 配置文件: `backup_rules.xml`
   - 备份到 Google Drive
   - 最大 25MB

2. **Backup and Restore** (API 31+)
   - 配置文件: `data_extraction_rules.xml`
   - 支持云备份和设备间传输
   - 更灵活的规则

### 问题诊断

如果用户报告数据丢失,检查:

1. **设备 Android 版本**
   - API < 31: 使用 `backup_rules.xml`
   - API ≥ 31: 使用 `data_extraction_rules.xml`

2. **安装方式**
   - 覆盖安装: 应保留数据
   - 卸载后安装: 数据会丢失
   - 签名不一致: 无法覆盖安装

3. **备份状态**
   ```bash
   adb shell bmgr list transports
   adb shell bmgr backupnow takagi.ru.monica
   ```

## 🚀 后续改进

1. **添加数据导出/导入功能**
   - JSON 格式导出
   - 加密导出文件
   - 方便用户手动备份

2. **升级前提醒**
   - 检测版本升级
   - 提示用户备份数据

3. **自动备份提醒**
   - 定期提醒用户备份
   - 一键备份到 WebDAV

## 📞 用户支持

如果您遇到数据丢失问题:

1. **不要卸载应用!** - 这会永久删除数据
2. **不要清除应用数据!** - 这也会删除所有密码
3. **立即联系开发者** - 我们会帮助您恢复数据
4. **检查系统备份** - 可能可以从 Google 备份恢复

---

**修复版本**: 1.0.8  
**修复日期**: 2025-11-08  
**严重程度**: 🔴 Critical - 数据丢失  
**状态**: ✅ 已修复
