# 修复记录 — 2026-01-31

- MainActivity: 修复导航竞态条件，使用固定起始页并移除动态计算逻辑；使用 `rememberUpdatedState` 修复生命周期监听器中闭包状态捕获问题；为 `runBlocking` 添加 200ms 超时保护以防止 ANR。
- SecurityQuestionsSetupScreen: 将强制解包 (!!) 替换为安全的空检查以防止崩溃（已使用可空类型和显式空检查）。
- KeePassWebDavViewModel: 修复 WebDAV 操作中 `sardine` 对象的非空断言问题（改为安全的空检查和错误返回）。
- DocumentDetailScreen: 将 Bitmap 图片的强制解包替换为安全的 `let` 作用域调用。
