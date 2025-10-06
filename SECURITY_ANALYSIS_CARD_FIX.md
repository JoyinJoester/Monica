# 安全分析页面卡片布局修复

## 问题描述

安全分析页面的统计卡片（4个并排显示）在数字较大时会出现换行问题，导致卡片高度不一致，布局混乱。

### 具体表现：
- 当数字超过3位数时，文本可能换行
- 卡片高度不统一
- 视觉效果不美观

## 修复方案

### 1. 优化卡片内容显示
**文件**: `SecurityAnalysisScreen.kt` - `CompactStatCard` 组件

#### 修改内容：

```kotlin
@Composable
fun CompactStatCard(...) {
    Card(...) {
        Column(...) {
            // 缩小图标尺寸：28.dp → 24.dp
            Icon(modifier = Modifier.size(24.dp))
            
            // 缩小间距：8.dp → 6.dp
            Spacer(modifier = Modifier.height(6.dp))
            
            // 优化数字文本
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,  // 从 titleLarge 改为 titleMedium
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,                    // 🔑 强制单行显示
                textAlign = TextAlign.Center     // 🔑 居中对齐
            )
            
            // 优化标签文本
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,                    // 🔑 强制单行显示
                textAlign = TextAlign.Center     // 🔑 居中对齐
            )
        }
    }
}
```

### 2. 优化外层布局
**文件**: `SecurityAnalysisScreen.kt` - `SecurityStatisticsCardsCompact` 组件

#### 修改内容：

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)  // 减小间距 12.dp → 8.dp
) {
    CompactStatCard(
        modifier = Modifier.weight(1f, fill = true)  // 添加 fill = true 参数
        ...
    )
    // ... 其他3个卡片同样处理
}
```

## 优化效果

### Before (修复前):
```
┌────────┬────────┬────────┬────────┐
│ Icon   │ Icon   │ Icon   │ Icon   │
│  123   │ 5678   │  99    │  456   │ ← 数字大小不一致
│重复密码│重复网址│已泄露  │无2FA   │
└────────┴────────┴────────┴────────┘
         ↑ 数字过大时会换行，卡片变形
```

### After (修复后):
```
┌────────┬────────┬────────┬────────┐
│  Icon  │  Icon  │  Icon  │  Icon  │ ← 图标缩小到 24dp
│  123   │ 5678   │   99   │  456   │ ← maxLines=1，不换行
│重复密码│重复网址│已泄露  │无2FA   │ ← maxLines=1，不换行
└────────┴────────┴────────┴────────┘
  ↑ 所有卡片高度一致，数字居中对齐
```

## 技术细节

### 关键改进点：

1. **强制单行显示**
   - 添加 `maxLines = 1` 属性
   - 防止长数字或文本换行

2. **文本居中对齐**
   - 添加 `textAlign = TextAlign.Center`
   - 确保数字和标签视觉上居中

3. **缩小元素尺寸**
   - Icon: 28.dp → 24.dp
   - 数字样式: titleLarge → titleMedium
   - 间距: 8.dp → 6.dp
   - 为更大的数字腾出空间

4. **优化权重分配**
   - 使用 `weight(1f, fill = true)` 替代 `weight(1f)`
   - 确保每个卡片占据相同宽度
   - 即使内容不同也保持一致性

5. **减小卡片间距**
   - 从 12.dp 减少到 8.dp
   - 为卡片内容提供更多空间

## 测试场景

### 测试数据：
- ✅ 单位数: 5
- ✅ 双位数: 56
- ✅ 三位数: 123
- ✅ 四位数: 5678
- ✅ 五位数以上: 12345+

### 测试结果：
所有场景下卡片高度保持一致，文本不换行，布局美观整洁。

## 编译信息

```bash
BUILD SUCCESSFUL in 8s
37 actionable tasks: 10 executed, 27 up-to-date
```

## 安装验证

```bash
> Task :app:installDebug
Installing APK 'app-debug.apk' on 'Medium_Phone_API_32(AVD) - 12' for :app:debug
Installed on 1 device.
BUILD SUCCESSFUL in 4s
```

## 相关文件

- `app/src/main/java/takagi/ru/monica/ui/screens/SecurityAnalysisScreen.kt`
  - `CompactStatCard()` 组件 (行 339-378)
  - `SecurityStatisticsCardsCompact()` 组件 (行 259-334)

## 版本信息

- 修复日期: 2025-10-06
- 影响版本: V1.0.2+
- 修复类型: UI/UX Bug Fix

## 注意事项

1. 此修复保持了卡片的响应式设计
2. 在不同屏幕尺寸上都能正常显示
3. 没有改变卡片的功能逻辑
4. 向后兼容，无需数据迁移

---

**状态**: ✅ 已修复并验证
**优先级**: Medium
**类型**: Bug Fix
