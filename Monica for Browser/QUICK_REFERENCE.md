# GitHub Actions 快速参考

## 快速命令

### 本地测试
```bash
cd "Monica for Browser"

# 安装依赖
npm ci

# 运行类型检查
npx tsc --noEmit

# 运行 lint
npm run lint

# 构建 Chrome/Edge
npm run build

# 构建 Firefox
npm run build:firefox

# 构建所有版本
npm run build && npm run build:firefox
```

### 创建发布
```bash
# 1. 更新版本号
vim "Monica for Browser/public/manifest.json"
vim "Monica for Browser/public/manifest.firefox.json"

# 2. 提交更改
git add "Monica for Browser/public/*.json"
git commit -m "Bump version to 1.0.0"
git push

# 3. 创建标签
git tag -a browser-v1.0.0 -m "Release version 1.0.0"
git push origin browser-v1.0.0

# 4. GitHub Actions 自动创建 Release
```

### 手动触发构建
1. 访问 GitHub Actions 页面
2. 选择 "Browser Extension Build"
3. 点击 "Run workflow"
4. 选择分支并运行

## 工作流速查表

| 工作流 | 触发条件 | 用途 | 产物 |
|--------|---------|------|------|
| **Browser-Extension.yml** | Push/PR | 构建扩展 | ZIP + dist |
| **Browser-CI.yml** | PR | 质量检查 | 无 |
| **Browser-Release.yml** | Tag `browser-v*` | 创建 Release | ZIP（永久） |

## Manifest 验证规则

### Chrome/Edge
```bash
✓ "manifest_version": 3
✓ "type": "module"  # 必须有
✓ background.js 存在
✓ content.js 存在
```

### Firefox
```bash
✓ "manifest_version": 3
✓ 无 "type": "module"  # 不能有
✓ background.js 存在
✓ content.js 存在
```

## 构建产物

### Chrome/Edge
```
monica-browser-extension-chrome.zip
  └── dist/
      ├── manifest.json          # 包含 "type": "module"
      ├── background.js
      ├── content.js
      ├── index.html
      ├── icons/
      └── assets/
```

### Firefox
```
monica-browser-extension-firefox.zip
  └── dist/
      ├── manifest.json          # 不包含 "type": "module"
      ├── background.js
      ├── content.js
      ├── index.html
      ├── icons/
      └── assets/
```

## 下载构建产物

### 从 GitHub Actions
1. 进入工作流运行页面
2. 滚动到 "Artifacts"
3. 下载所需 ZIP

### 本地构建
```bash
cd "Monica for Browser"

# Chrome/Edge
npm run build
ls monica-browser-extension.zip  # 实际文件名

# Firefox
npm run build:firefox
ls monica-browser-extension.zip  # 实际文件名
```

## 常见问题速查

### 构建失败

**Manifest 验证错误**
```bash
# 检查 Chrome manifest
cat "Monica for Browser/public/manifest.json" | grep '"type"'

# 检查 Firefox manifest
cat "Monica for Browser/public/manifest.firefox.json" | grep '"type"'

# 应该：
# Chrome: "type": "module"
# Firefox: (无输出)
```

**TypeScript 错误**
```bash
cd "Monica for Browser"
npx tsc --noEmit
# 查看详细错误并修复
```

**Lint 错误**
```bash
cd "Monica for Browser"
npm run lint
# 自动修复
npx eslint . --fix
```

### 下载产物

**产物已过期**
```bash
# 重新运行工作流
# 或本地构建
npm run build
```

**产物不完整**
```bash
# 检查 dist 目录
ls -la "Monica for Browser/dist/"
# 应包含：manifest.json, background.js, content.js, index.html
```

## 版本号格式

### 语义化版本
```
browser-v1.0.0          # 正式版本
browser-v1.0.1-beta.1   # Beta 版本
browser-v2.0.0-alpha.1  # Alpha 版本
browser-v1.2.3-rc.1     # Release Candidate
```

### 更新版本号
```bash
# 1. 编辑 manifest.json
vim "Monica for Browser/public/manifest.json"
# "version": "1.0.0" -> "version": "1.0.1"

# 2. 编辑 manifest.firefox.json
vim "Monica for Browser/public/manifest.firefox.json"
# "version": "1.0.0" -> "version": "1.0.1"

# 3. 提交
git add "Monica for Browser/public/*.json"
git commit -m "Bump version to 1.0.1"
git push

# 4. 创建标签
git tag -a browser-v1.0.1 -m "Release version 1.0.1"
git push origin browser-v1.0.1
```

## CI/CD 流程

```
开发分支提交
    │
    ├─► 自动运行 Browser-Extension.yml
    │   ├─► 构建 Chrome/Edge
    │   ├─► 构建 Firefox
    │   └─► 上传产物（7天）
    │
    └─► PR 时运行 Browser-CI.yml
        ├─► 类型检查
        ├─► Lint
        ├─► 验证构建
        └─► 验证 Manifest

合并到主分支后
    │
    └─► 创建标签 browser-v*
        │
        └─► 自动运行 Browser-Release.yml
            ├─► 构建 Chrome/Edge
            ├─► 构建 Firefox
            └─► 创建 GitHub Release
```

## 权限说明

### Browser-Extension.yml
```yaml
permissions:
  contents: read
  actions: read
```
- 只读权限
- 不能创建 Release

### Browser-CI.yml
```yaml
permissions:
  contents: read
  actions: read
```
- 只读权限
- 仅用于质量检查

### Browser-Release.yml
```yaml
permissions:
  contents: write
```
- 写权限
- 可以创建 Release

## 环境变量

### 构建时
```bash
BROWSER=firefox  # 构建 Firefox 版本
# 不设置 = 构建 Chrome 版本
```

### 工作流中
```yaml
env:
  NODE_VERSION: '20'
  CACHE_PATH: 'Monica for Browser/package-lock.json'
```

## 密钥管理

### 添加密钥
1. Settings → Secrets and variables → Actions
2. New repository secret
3. 名称: `EXTENSION_API_KEY`
4. 值: `your-api-key`

### 使用密钥
```yaml
- name: Publish Extension
  env:
    API_KEY: ${{ secrets.EXTENSION_API_KEY }}
  run: |
    # 使用 API_KEY
```

## 性能优化

### 缓存
```yaml
- uses: actions/setup-node@v4
  with:
    cache: 'npm'
    cache-dependency-path: 'Monica for Browser/package-lock.json'
```

### 并行构建
```yaml
jobs:
  build-chrome:
    runs-on: ubuntu-latest
    # ...

  build-firefox:
    runs-on: ubuntu-latest
    # Chrome 和 Firefox 并行构建
```

## 监控

### 查看工作流状态
```bash
# GitHub CLI
gh workflow list
gh run list --workflow=Browser-Extension.yml

# 查看最新运行
gh run view --workflow=Browser-Extension.yml
```

### 下载日志
```bash
# 下载工作流日志
gh run download <run-id>
```

## 故障排查速查

| 问题 | 原因 | 解决 |
|------|------|------|
| 构建失败 | 依赖问题 | `npm ci` |
| 类型错误 | TypeScript | `npx tsc --noEmit` |
| Lint 错误 | 代码风格 | `npm run lint` |
| Manifest 错误 | 配置错误 | 检查 manifest 文件 |
| 产物过期 | 超过7天 | 重新构建 |
| 权限错误 | 密钥缺失 | 添加密钥 |

## 相关链接

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Workflow 语法](https://docs.github.com/en/actions/reference/workflow-syntax-for-github-actions)
- [扩展开发文档](./FIREFOX_QUICK_START.md)
- [详细工作流文档](./GITHUB_ACTIONS.md)

## 快速参考命令

```bash
# 完整测试流程
cd "Monica for Browser"
npm ci
npm run lint
npm run build
npm run build:firefox

# 查看构建产物
ls -la dist/

# 清理构建
rm -rf dist/

# 重新构建
npm run build
```

---

**提示**: 将此文件加入书签，方便快速查找常用命令。
