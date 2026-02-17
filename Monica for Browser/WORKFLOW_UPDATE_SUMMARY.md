# GitHub Actions 工作流更新总结

## 更新日期
2026-02-17

## 概述

为 Monica Browser Extension 添加了完整的 GitHub Actions CI/CD 支持，包括 Chrome/Edge 和 Firefox 的自动构建、测试和发布。

## 新增文件

### 1. `.github/workflows/Browser-CI.yml`
**用途**: Pull Request 时的质量检查

**任务**:
- TypeScript 类型检查
- ESLint 代码检查
- Chrome/Edge 构建
- Firefox 构建
- Manifest 文件验证

**特点**:
- 在 PR 时自动运行
- 严格的验证规则
- 确保 Chrome 和 Firefox manifest 的正确性

### 2. `.github/workflows/Browser-Release.yml`
**用途**: 创建正式版本发布

**触发条件**: 推送 `browser-v*` 标签

**功能**:
- 构建 Chrome/Edge 和 Firefox 版本
- 自动创建 GitHub Release
- 生成安装说明
- 上传构建产物

**使用示例**:
```bash
git tag -a browser-v1.0.0 -m "Release version 1.0.0"
git push origin browser-v1.0.0
```

### 3. `Monica for Browser/GITHUB_ACTIONS.md`
**用途**: GitHub Actions 工作流详细文档

**内容包括**:
- 所有工作流的详细说明
- 使用指南和最佳实践
- 故障排查步骤
- 工作流自定义方法

## 修改的文件

### 1. `.github/workflows/Browser-Extension.yml`
**变更**: 重构为支持多浏览器构建

**主要修改**:
- 拆分为 `build-chrome` 和 `build-firefox` 两个独立任务
- 添加 Manifest 验证步骤
- 为每个浏览器版本创建独立的 ZIP 文件
- 上传独立的构建产物

**构建产物**:
- `monica-browser-extension-chrome.zip` - Chrome/Edge 扩展
- `monica-browser-extension-firefox.zip` - Firefox 扩展

### 2. `Monica for Browser/README.md`
**变更**: 添加 CI/CD 章节

**新增内容**:
- GitHub Actions 工作流概述
- 自动化构建说明
- 本地测试命令
- 版本发布流程

## 工作流架构

### 触发条件

```
┌─────────────────┐
│   Push/PR      │ ───────► Browser-Extension.yml
│  (Browser/**)   │          (构建 + 上传)
└─────────────────┘

┌─────────────────┐
│   PR to        │ ───────► Browser-CI.yml
│ main/develop   │          (质量检查)
└─────────────────┘

┌─────────────────┐
│  Tag:          │ ───────► Browser-Release.yml
│ browser-v*     │          (创建 Release)
└─────────────────┘
```

### 构建流程

```
源代码变更
    │
    ├─► Chrome 构建
    │   ├─► npm run build
    │   ├─► 验证 manifest.json
    │   └─► 创建 monica-browser-chrome.zip
    │
    └─► Firefox 构建
        ├─► npm run build:firefox
        ├─► 验证 manifest.firefox.json
        └─► 创建 monica-browser-firefox.zip
```

## 验证规则

### Chrome Manifest 验证
```bash
✓ 必须包含 "manifest_version": 3
✓ 必须包含 "type": "module"
✓ 必须包含 background.js
✓ 必须包含 content.js
```

### Firefox Manifest 验证
```bash
✓ 必须包含 "manifest_version": 3
✓ 不能包含 "type": "module"
✓ 必须包含 background.js
✓ 必须包含 content.js
```

### 代码质量检查
```bash
✓ TypeScript 类型检查通过
✓ ESLint 检查通过（可配置 continue-on-error）
✓ 构建成功无错误
```

## 使用指南

### 日常开发

1. **本地测试**
   ```bash
   cd "Monica for Browser"
   npm ci
   npm run lint
   npm run build
   npm run build:firefox
   ```

2. **提交代码**
   ```bash
   git add .
   git commit -m "Add new feature"
   git push
   ```

3. **查看 CI**
   - GitHub 会自动运行检查
   - 等待所有检查通过
   - 修复任何失败的测试

### 创建新版本

1. **更新版本号**
   ```bash
   # 编辑 manifest.json 和 manifest.firefox.json
   # "version": "1.0.0" -> "version": "1.0.1"
   ```

2. **提交更改**
   ```bash
   git add "Monica for Browser/public/*.json"
   git commit -m "Bump version to 1.0.1"
   git push
   ```

3. **创建发布标签**
   ```bash
   git tag -a browser-v1.0.1 -m "Release version 1.0.1"
   git push origin browser-v1.0.1
   ```

4. **查看 Release**
   - 访问 GitHub Releases 页面
   - 下载生成的 ZIP 文件
   - 测试安装

## 构建产物

### Browser-Extension.yml
| 产物 | 保留时间 | 用途 |
|------|---------|------|
| `monica-browser-extension-chrome` | 7天 | Chrome/Edge 测试 |
| `monica-browser-extension-chrome-dist` | 7天 | 完整构建目录 |
| `monica-browser-extension-firefox` | 7天 | Firefox 测试 |
| `monica-browser-extension-firefox-dist` | 7天 | 完整构建目录 |

### Browser-Release.yml
| 产物 | 永久 | 用途 |
|------|------|------|
| `monica-browser-chrome-browser-v*.zip` | 是 | 官方发布 |
| `monica-browser-firefox-browser-v*.zip` | 是 | 官方发布 |

## 性能优化

### 缓存策略
- npm 依赖缓存
- 并行构建（Chrome 和 Firefox 同时构建）
- 增量构建

### 执行时间
- Browser-Extension.yml: ~2-3 分钟
- Browser-CI.yml: ~2-3 分钟
- Browser-Release.yml: ~3-4 分钟

## 监控和维护

### 监控指标
- 工作流成功率
- 平均构建时间
- 产物下载次数

### 维护任务
1. **定期更新依赖**
   - Node.js 版本
   - GitHub Actions 版本
   - 构建工具版本

2. **审查失败的工作流**
   - 分析失败原因
   - 更新验证规则
   - 修复代码问题

3. **优化构建性能**
   - 减少不必要的步骤
   - 优化缓存策略
   - 并行化独立任务

## 安全性

### 权限管理
```yaml
permissions:
  contents: read    # Browser-Extension, Browser-CI
  contents: write   # Browser-Release (创建 Release)
```

### 密钥管理
如需访问第三方服务（扩展商店 API）：
1. 在 GitHub 仓库设置中添加密钥
2. 在工作流中引用：`${{ secrets.API_KEY }}`
3. 避免在日志中输出敏感信息

## 故障排查

### 常见问题

**Q: 构建失败：manifest 验证错误**
A: 检查 manifest.json 和 manifest.firefox.json 的差异

**Q: TypeScript 类型错误**
A: 本地运行 `npx tsc --noEmit` 查看详细错误

**Q: Lint 错误**
A: 运行 `npm run lint` 并修复问题，或使用 `--fix` 自动修复

**Q: 构建产物不完整**
A: 检查 dist 目录是否包含所有必要文件

### 调试步骤

1. **查看工作流日志**
   - GitHub Actions → 选择工作流运行
   - 查看详细日志

2. **本地复现**
   ```bash
   # 下载构建产物
   # 本地解压并测试
   ```

3. **临时跳过检查**
   ```yaml
   - name: Run ESLint
     run: npm run lint
     continue-on-error: true  # 临时跳过
   ```

## 未来改进

### 短期目标
- [ ] 添加 E2E 测试
- [ ] 集成扩展商店自动发布
- [ ] 添加代码覆盖率报告
- [ ] 优化构建时间

### 长期目标
- [ ] 多平台支持（Safari、Edge Legacy）
- [ ] 自动化更新检查
- [ ] 性能监控
- [ ] 用户反馈收集

## 相关文档

- [GitHub Actions 官方文档](https://docs.github.com/en/actions)
- [Workflow 语法参考](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)
- [Firefox 扩展开发](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions)
- [Chrome 扩展开发](https://developer.chrome.com/docs/extensions/mv3/)

## 贡献指南

如需添加新的工作流或修改现有工作流：

1. 在 `.github/workflows/` 创建或修改文件
2. 本地测试工作流语法
3. 更新相关文档（GITHUB_ACTIONS.md）
4. 提交 PR 并等待 CI 通过
5. 合并后验证工作流运行正常

## 联系方式

如有问题或建议，请：
- 创建 GitHub Issue
- 查看现有文档
- 联系维护团队

---

**最后更新**: 2026-02-17
**维护者**: Monica Browser Extension Team
