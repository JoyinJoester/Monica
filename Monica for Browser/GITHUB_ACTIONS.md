# GitHub Actions 工作流文档

## 概述

Monica Browser Extension 使用 GitHub Actions 进行自动化构建、测试和发布。

## 工作流文件

### 1. Browser-Extension.yml

**位置**: `.github/workflows/Browser-Extension.yml`

**用途**: 构建和上传浏览器扩展

**触发条件**:
- 推送到 `main` 或 `develop` 分支
- 针对 `main` 或 `develop` 分支的 Pull Request
- 手动触发 (workflow_dispatch)

**任务**:
- `build-chrome`: 构建 Chrome/Edge 版本
- `build-firefox`: 构建 Firefox 版本

**验证步骤**:
- Chrome manifest 必须包含 `"type": "module"`
- Firefox manifest 不能包含 `"type": "module"`

**构建产物**:
| 产物名称 | 内容 |
|---------|------|
| `monica-browser-extension-chrome` | Chrome/Edge 扩展 ZIP |
| `monica-browser-extension-chrome-dist` | Chrome/Edge 构建目录 |
| `monica-browser-extension-firefox` | Firefox 扩展 ZIP |
| `monica-browser-extension-firefox-dist` | Firefox 构建目录 |

**保留时间**: 7 天

### 2. Browser-CI.yml

**位置**: `.github/workflows/Browser-CI.yml`

**用途**: 在 Pull Request 时进行质量检查

**触发条件**:
- 针对 `main` 或 `develop` 分支的 Pull Request

**任务**:
- `type-check`: TypeScript 类型检查
- `lint`: ESLint 代码检查
- `build-chrome`: 构建 Chrome/Edge 版本
- `build-firefox`: 构建 Firefox 版本
- `validate-manifests`: 验证 Manifest 文件

**验证内容**:
1. **Manifest 版本**: 必须为 `"manifest_version": 3`
2. **Chrome 特定**:
   - 必须包含 `"type": "module"`
   - 必须包含 `background.js`
   - 必须包含 `content.js`
3. **Firefox 特定**:
   - 不能包含 `"type": "module"`
   - 必须包含 `background.js`
   - 必须包含 `content.js`
4. **文件存在性**:
   - `public/manifest.json` 必须存在
   - `public/manifest.firefox.json` 必须存在

### 3. Browser-Release.yml

**位置**: `.github/workflows/Browser-Release.yml`

**用途**: 创建正式版本发布

**触发条件**:
- 推送格式为 `browser-v*` 的标签（如 `browser-v1.0.0`）

**权限**:
- `contents: write` (创建 Release)

**任务**:
- `release`: 创建 GitHub Release

**构建产物**:
| 文件名 | 内容 |
|--------|------|
| `monica-browser-chrome-browser-v1.0.0.zip` | Chrome/Edge 扩展 |
| `monica-browser-firefox-browser-v1.0.0.zip` | Firefox 扩展 |

**Release 内容**:
- 版本号自动从标签名提取
- 包含 Chrome/Edge 和 Firefox 安装说明
- 列出主要功能和已知限制
- 自动标记为正式版本（非草稿、非预发布）

## 使用指南

### 创建新版本

1. **更新版本号**
   ```bash
   # 更新 public/manifest.json 中的 version
   # 更新 public/manifest.firefox.json 中的 version
   ```

2. **提交更改**
   ```bash
   git add "Monica for Browser/public/*.json"
   git commit -m "Bump version to 1.0.0"
   git push
   ```

3. **等待 CI 通过**
   - 确保所有 GitHub Actions 检查通过
   - 修复任何失败的测试

4. **创建标签**
   ```bash
   git tag -a browser-v1.0.0 -m "Release version 1.0.0"
   git push origin browser-v1.0.0
   ```

5. **查看 Release**
   - 访问 GitHub 仓库的 Releases 页面
   - 下载生成的 ZIP 文件
   - 发布到扩展商店（可选）

### 手动触发构建

1. 访问 GitHub Actions 页面
2. 选择 "Browser Extension Build" 工作流
3. 点击 "Run workflow"
4. 选择分支并运行

### 下载构建产物

**方法 1: 从 GitHub Actions**
1. 进入具体的工作流运行
2. 滚动到 "Artifacts" 部分
3. 下载所需的 ZIP 文件

**方法 2: 本地构建**
```bash
# Chrome/Edge
npm run build
ls Monica\ for\ Browser/monica-browser-extension.zip

# Firefox
npm run build:firefox
ls Monica\ for\ Browser/monica-browser-extension.zip
```

## 故障排查

### 构建失败：Manifest 验证错误

**问题**: Chrome manifest 缺少 `"type": "module"`

**解决**:
```bash
# 检查 manifest
cat "Monica for Browser/public/manifest.json" | grep '"type"'
# 应该输出: "type": "module"

# 如果缺失，手动添加或重新构建
npm run build
```

**问题**: Firefox manifest 包含 `"type": "module"`

**解决**:
```bash
# 检查 Firefox manifest
cat "Monica for Browser/public/manifest.firefox.json" | grep '"type"'
# 不应该有任何输出

# 如果存在，检查 manifest.firefox.json 文件
```

### 构建失败：TypeScript 错误

**问题**: 类型检查失败

**解决**:
```bash
# 本地运行类型检查
cd "Monica for Browser"
npx tsc --noEmit

# 修复类型错误
npm run lint
```

### 构建失败：Lint 错误

**问题**: ESLint 检查失败

**解决**:
```bash
# 本地运行 lint
cd "Monica for Browser"
npm run lint

# 自动修复可修复的问题
npx eslint . --fix
```

### 构建产物不完整

**问题**: ZIP 文件缺少必要文件

**解决**:
```bash
# 检查 dist 目录
cd "Monica for Browser"
ls -la dist/

# 应该包含：
# - manifest.json
# - background.js
# - content.js
# - index.html
# - icons/
# - assets/
```

## 工作流自定义

### 修改 Node.js 版本

编辑 `.github/workflows/Browser-Extension.yml`:

```yaml
- name: Set up Node.js
  uses: actions/setup-node@v4
  with:
    node-version: '20'  # 修改版本号
    cache: 'npm'
    cache-dependency-path: 'Monica for Browser/package-lock.json'
```

### 修改产物保留时间

```yaml
- name: Upload Chrome/Edge Artifact
  uses: actions/upload-artifact@v4
  with:
    name: monica-browser-extension-chrome
    path: "Monica for Browser/monica-browser-extension-chrome.zip"
    retention-days: 7  # 修改保留天数
```

### 添加新的构建步骤

在 `build-chrome` 或 `build-firefox` 任务中添加：

```yaml
- name: Run Custom Tests
  run: npm run test:custom
```

## 性能优化

### 使用缓存

工作流已经配置了 npm 缓存：

```yaml
- uses: actions/setup-node@v4
  with:
    node-version: '20'
    cache: 'npm'
    cache-dependency-path: 'Monica for Browser/package-lock.json'
```

### 并行构建

Chrome 和 Firefox 版本并行构建，减少总时间：

```yaml
jobs:
  build-chrome:  # 与 build-firefox 并行
    runs-on: ubuntu-latest
    # ...

  build-firefox:  # 与 build-chrome 并行
    runs-on: ubuntu-latest
    # ...
```

## 安全性

### 权限最小化

工作流只申请必要的权限：

```yaml
permissions:
  contents: read  # 只读
  actions: read   # 只读
```

### 密钥管理

如果需要在 Release 时访问第三方服务（如扩展商店 API）：

```yaml
- name: Publish to Chrome Web Store
  env:
    CHROME_WEBSTORE_API_KEY: ${{ secrets.CHROME_WEBSTORE_API_KEY }}
  run: |
    # 使用 API 密钥发布
```

在 GitHub 仓库设置中添加密钥：
1. Settings → Secrets and variables → Actions
2. New repository secret
3. 添加密钥名称和值

## 最佳实践

1. **始终在本地测试**
   ```bash
   npm ci
   npm run lint
   npm run build
   npm run build:firefox
   ```

2. **使用语义化版本标签**
   - `browser-v1.0.0` - 正式版本
   - `browser-v1.0.1-beta.1` - Beta 版本
   - `browser-v2.0.0-alpha.1` - Alpha 版本

3. **保持工作流文档更新**
   - 添加新的构建步骤时更新此文档
   - 记录任何重大变更

4. **监控工作流运行**
   - 定期检查失败的工作流
   - 及时修复问题

5. **使用分支保护**
   - 要求 PR 通过 CI 检查
   - 防止直接推送到主分支

## 相关资源

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Chrome 扩展发布指南](https://developer.chrome.com/docs/webstore/publish/)
- [Firefox 扩展发布指南](https://extensionworkshop.com/documentation/publish/)

## 支持

如需帮助，请：
1. 查看本文档的故障排查部分
2. 查看 GitHub Actions 运行日志
3. 在 GitHub Issues 中报告问题
