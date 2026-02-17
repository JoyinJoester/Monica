# Firefox兼容性修改说明

## 修改日期
2026-02-17

## 修改概述
为Monica浏览器扩展添加Firefox兼容性支持，实现跨浏览器兼容。

## 主要修改

### 1. 新增文件

#### `public/manifest.firefox.json`
- Firefox专用manifest文件
- 移除了`background.service_worker.type: "module"`（Firefox不支持）
- 其他配置与Chrome版本保持一致

#### `src/types/browser.d.ts`
- 添加browser API的TypeScript类型声明
- 确保类型检查通过

### 2. 修改的文件

#### `vite.config.ts`
- 添加环境变量检测：`process.env.BROWSER === 'firefox'`
- 添加copyManifest插件，根据目标浏览器复制正确的manifest文件
- 使用cross-env确保跨平台环境变量设置
- 两个版本都使用ES module格式（Firefox 109+支持）

#### `package.json`
- 安装cross-env依赖
- 新增`build:firefox`脚本：`cross-env BROWSER=firefox tsc -b && cross-env BROWSER=firefox vite build`

#### `src/background.ts`
- 添加browserAPI兼容性层，统一处理chrome和browser API
- 替换所有`chrome.*`调用为`browserAPI.*`
- 处理Firefox不支持`chrome.action.openPopup()`的情况，使用fallback方案
- 处理Firefox对`chrome.scripting.executeScript`的兼容性
- 添加所有必要的类型注解

#### `src/content.ts`
- 添加browserAPI兼容性层
- 替换所有`chrome.runtime.*`调用为`browserAPI.runtime.*`
- 确保所有资源URL使用browserAPI.getRuntimeURL()
- 添加所有必要的类型定义和注解

#### `README.md`
- 添加浏览器兼容性章节
- 说明Firefox构建方法和注意事项

## Firefox限制

### 不支持的功能

1. **chrome.action.openPopup()**
   - Firefox不支持此API
   - 解决方案：使用fallback创建新tab打开扩展页面

2. **Background Service Worker的module类型**
   - Firefox不完全支持ES module格式的service worker
   - 解决方案：使用ES格式但不指定module类型

3. **部分Manifest V3特性**
   - Firefox对Manifest V3的支持不完整
   - 使用Firefox专用manifest文件

### 已验证的功能

✅ 密码自动填充
✅ 2FA/TOTP验证码填充
✅ WebDAV备份
✅ 密码保存提示
✅ Content Script注入
✅ Storage API
✅ Message通信
✅ 构建系统

## 使用方法

### Chrome/Edge构建
```bash
npm run build
```

### Firefox构建
```bash
npm run build:firefox
```

### 安装到Firefox
1. 构建：`npm run build:firefox`
2. 打开Firefox：`about:debugging#/runtime/this-firefox`
3. 点击"临时载入附加组件"
4. 选择`dist/manifest.json`文件

## 构建验证

### Chrome版本验证
```bash
npm run build
# 检查 dist/manifest.json 应包含 "type": "module"
```

### Firefox版本验证
```bash
npm run build:firefox
# 检查 dist/manifest.json 不应包含 "type": "module"
# 构建输出应显示：[Manifest] isFirefox: true
```

## 测试建议

1. 在Firefox Nightly或Beta版本中测试最新API支持
2. 测试所有核心功能：密码填充、2FA、保存密码
3. 测试WebDAV备份功能
4. 测试popup界面和设置页面
5. 测试不同网站上的自动填充功能
6. 测试跨浏览器功能一致性

## 技术细节

### API兼容层实现

```typescript
const browserAPI = {
  runtime: typeof chrome !== 'undefined' ? chrome.runtime : (typeof browser !== 'undefined' ? browser.runtime : chrome.runtime),
  storage: typeof chrome !== 'undefined' ? chrome.storage : (typeof browser !== 'undefined' ? browser.storage : chrome.storage),
  scripting: typeof chrome !== 'undefined' ? chrome.scripting : (typeof browser !== 'undefined' ? browser.scripting : chrome.scripting),
  tabs: typeof chrome !== 'undefined' ? chrome.tabs : (typeof browser !== 'undefined' ? browser.tabs : chrome.tabs),
  action: typeof chrome !== 'undefined' ? chrome.action : (typeof browser !== 'undefined' ? browser.browserAction : chrome.action),
}
```

### Manifest差异对比

| 特性 | Chrome | Firefox |
|------|--------|---------|
| background.type | "module" | (省略) |
| action.openPopup | 支持 | 不支持 |
| scripting.executeScript | 支持 | 支持 |

## 已知问题

无已知严重问题。

## 未来改进

1. 考虑使用webextension-polyfill统一API差异
2. 添加Firefox特定的UI优化
3. 添加Firefox Marketplace发布支持
4. 添加自动化跨浏览器测试
5. 优化构建性能

## 贡献指南

如需改进Firefox兼容性，请遵循以下步骤：

1. 测试新功能在Firefox上的兼容性
2. 如有API差异，更新browserAPI兼容层
3. 如有manifest差异，更新manifest.firefox.json
4. 更新本文档说明新差异
5. 确保Chrome和Firefox版本都能正常构建
