# Firefox 兼容性快速指南

## 快速开始

### 构建 Firefox 版本

```bash
npm run build:firefox
```

### 构建 Chrome/Edge 版本

```bash
npm run build
```

## 安装到 Firefox

1. **构建扩展**
   ```bash
   npm run build:firefox
   ```

2. **打开 Firefox 调试页面**
   - 在地址栏输入：`about:debugging#/runtime/this-firefox`

3. **加载扩展**
   - 点击"临时载入附加组件"（Temporary Add-ons）
   - 选择 `dist/manifest.json` 文件

4. **验证安装**
   - 扩展图标应出现在工具栏
   - 点击图标打开扩展界面

## 主要功能

✅ 密码自动填充
✅ 2FA/TOTP 验证码填充
✅ WebDAV 备份
✅ 密码保存提示
✅ 加密笔记
✅ 文档管理
✅ 多语言支持

## 技术实现

### API 兼容层

```typescript
const browserAPI = {
  runtime: typeof chrome !== 'undefined' ? chrome.runtime : browser.runtime,
  storage: typeof chrome !== 'undefined' ? chrome.storage : browser.storage,
  scripting: typeof chrome !== 'undefined' ? chrome.scripting : browser.scripting,
  tabs: typeof chrome !== 'undefined' ? chrome.tabs : browser.tabs,
  action: typeof chrome !== 'undefined' ? chrome.action : browser.browserAction,
}
```

### Manifest 差异

| 特性 | Chrome | Firefox |
|------|--------|---------|
| background.type | "module" | (省略) |
| action.openPopup | 支持 | 不支持 |
| scripting.executeScript | 支持 | 支持 |

## 已知限制

1. **popup打开方式**
   - Firefox 不支持 `chrome.action.openPopup()`
   - 替代方案：创建新 tab 打开扩展

2. **Service Worker 格式**
   - Firefox 不完全支持 ES module 格式
   - 解决方案：使用 ES 格式但不指定 module 类型

3. **Manifest V3 支持**
   - Firefox 109+ 支持部分 Manifest V3 特性
   - 某些高级特性可能不可用

## 开发说明

### 文件结构

```
Monica for Browser/
├── public/
│   ├── manifest.json              # Chrome/Edge manifest
│   └── manifest.firefox.json     # Firefox 专用 manifest
├── src/
│   ├── background.ts              # 后台脚本（含兼容层）
│   ├── content.ts                 # 内容脚本（含兼容层）
│   └── types/
│       └── browser.d.ts           # Browser API 类型声明
├── package.json
├── vite.config.ts                # 构建配置（支持 Firefox）
└── FIREFOX_COMPATIBILITY.md      # 详细文档
```

### 添加新功能时的注意事项

1. **检查 API 兼容性**
   - 检查 Firefox 是否支持目标 API
   - 参考 [MDN WebExtensions](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions)

2. **使用 browserAPI**
   - 始终使用 `browserAPI` 而不是 `chrome`
   - 确保在两个浏览器上都能工作

3. **测试两个版本**
   - Chrome: `npm run build`
   - Firefox: `npm run build:firefox`

4. **更新文档**
   - 如有新的 API 差异，更新本文档
   - 更新 `FIREFOX_COMPATIBILITY.md`

## 故障排查

### 扩展无法加载

**问题**: Firefox 提示扩展无效

**解决**:
1. 检查是否使用了 `npm run build:firefox`
2. 确认 `dist/manifest.json` 不包含 `"type": "module"`
3. 检查 Firefox 版本（需要 109+）

### 功能不工作

**问题**: 某些功能在 Firefox 上不工作

**解决**:
1. 打开浏览器控制台（Ctrl+Shift+J）
2. 查找错误信息
3. 检查是否使用了不支持的 API
4. 在 `FIREFOX_COMPATIBILITY.md` 查看已知限制

### 类型错误

**问题**: TypeScript 编译错误

**解决**:
1. 确保使用正确的类型注解
2. 参考 `src/types/browser.d.ts` 中的类型定义
3. 使用类型断言时注意安全性

## 相关文档

- [Firefox 扩展开发文档](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions)
- [Chrome 扩展文档](https://developer.chrome.com/docs/extensions/mv3/)
- [Manifest V3 对比](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Manifest_V3)

## 支持

如遇到问题，请：

1. 查看本文档的故障排查部分
2. 查看 `FIREFOX_COMPATIBILITY.md` 获取详细信息
3. 在 GitHub 上提交 Issue

## 版本历史

- **v1.0.25** (2026-02-17)
  - 添加 Firefox 兼容性支持
  - 实现 API 兼容层
  - 添加专用构建脚本
  - 创建 Firefox 专用 manifest

---

**提示**: 推荐在 Firefox Nightly 或 Beta 版本上测试，以获得最新的 API 支持。
