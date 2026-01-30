# Monica Browser Extension - 改进计划

基于 [Bitwarden Browser Extension](https://github.com/bitwarden/clients/tree/main/apps/browser) 架构分析

---

## 📊 当前架构 vs 目标架构

### 当前状态
```
Monica for Browser/
├── src/
│   ├── content.ts              # ❌ 1573行，功能混杂
│   ├── background.ts           # ❌ 582行，逻辑混杂
│   ├── components/             # React 组件
│   ├── features/               # 功能模块
│   ├── contexts/               # Context
│   ├── utils/                 # 工具函数
│   └── types/                 # 类型定义
```

### 目标架构（参考 Bitwarden）
```
Monica for Browser/
├── src/
│   ├── services/              # ✅ 服务层
│   │   ├── autofill/        # 自动填充服务
│   │   ├── storage/         # 存储服务
│   │   ├── encryption/      # 加密服务
│   │   ├── messaging/       # 消息传递服务
│   │   └── biometrics/      # 生物识别服务
│   ├── autofill/              # ✅ 自动填充模块
│   │   ├── content/         # Content Scripts
│   │   │   ├── init.ts     # 初始化
│   │   │   ├── message-handler.ts  # 消息处理
│   │   │   └── overlay/    # 覆盖层 UI
│   │   └── services/       # 自动填充服务
│   │       ├── form-detection.ts
│   │       ├── field-filling.ts
│   │       └── page-analysis.ts
│   ├── background/            # ✅ 后台脚本模块
│   │   ├── main.ts         # 主入口
│   │   ├── handlers/       # 事件处理器
│   │   └── services/       # 后台服务
│   ├── components/            # ✅ React 组件
│   ├── features/              # ✅ 功能模块
│   ├── contexts/              # ✅ Context
│   ├── utils/                # ✅ 工具函数
│   └── types/                # ✅ 类型定义
```

---

## 🎯 改进优先级

### Phase 1: 服务层重构（高优先级）

#### 1.1 创建服务层结构
```
src/services/
├── index.ts                     # 服务导出
├── storage.service.ts           # 存储服务抽象
├── encryption.service.ts        # 加密服务抽象
├── message.service.ts          # 消息传递服务
├── autofill.service.ts        # 自动填充服务
├── logger.service.ts          # 日志服务
└── settings.service.ts        # 设置服务
```

#### 1.2 存储服务
```typescript
// src/services/storage.service.ts
export class StorageService {
    private static instance: StorageService;

    static getInstance(): StorageService {
        if (!StorageService.instance) {
            StorageService.instance = new StorageService();
        }
        return StorageService.instance;
    }

    // 通用存储方法
    async get<T>(key: string): Promise<T | null>;
    async set(key: string, value: any): Promise<void>;
    async remove(key: string): Promise<void>;
    async clear(): Promise<void>;

    // 专用存储方法
    async getVault(): Promise<SecureItem[]>;
    async saveVault(items: SecureItem[]): Promise<void>;
    async getSettings(): Promise<AppSettings>;
    async saveSettings(settings: AppSettings): Promise<void>;
}
```

#### 1.3 消息传递服务
```typescript
// src/services/message.service.ts
export enum MessageCommand {
    GET_PASSWORDS_FOR_AUTOFILL = 'GET_PASSWORDS_FOR_AUTOFILL',
    FILL_CREDENTIALS = 'FILL_CREDENTIALS',
    SAVE_PASSWORD = 'SAVE_PASSWORD',
    VERIFY_MASTER_PASSWORD = 'VERIFY_MASTER_PASSWORD',
    // ...
}

export class MessageService {
    // 发送消息到 background
    sendToBackground<T>(command: MessageCommand, data?: any): Promise<T>;

    // 监听来自 background 的消息
    onMessage(command: MessageCommand, handler: (data: any) => void): void;

    // 发送消息到 content script
    sendToTab(tabId: number, command: MessageCommand, data?: any): Promise<void>;
}
```

### Phase 2: 自动填充模块重构（高优先级）

#### 2.1 分离 content.ts

**当前问题**：
- content.ts 有 1573 行代码
- 所有逻辑混杂在一起
- 难以维护和测试

**重构方案**：
```
src/autofill/content/
├── init.ts                          # 初始化入口
├── message-handler.ts                 # 消息处理
├── overlay/                         # 覆盖层 UI
│   ├── icon-injector.ts             # 图标注入
│   ├── popup-manager.ts             # 弹窗管理
│   └── styles.ts                   # 样式定义
└── services/                        # 自动填充服务
    ├── form-detection.ts            # 表单检测
    ├── field-filling.ts             # 字段填充
    ├── page-analysis.ts             # 页面分析
    └── 2fa-detector.ts             # 2FA 检测
```

#### 2.2 表单检测服务
```typescript
// src/autofill/content/services/form-detection.ts
export class FormDetectionService {
    // 检测页面上的密码表单
    detectPasswordForms(): LoginForm[];

    // 检测用户名字段
    detectUsernameFields(form: LoginForm): HTMLInputElement[];

    // 检测密码字段
    detectPasswordFields(form: LoginForm): HTMLInputElement[];

    // 检测 2FA/OTP 字段
    detect2FAFields(): HTMLInputElement[];

    // 智能推断（使用启发式算法）
    inferFields(): { username: HTMLInputElement, password: HTMLInputElement };
}
```

#### 2.3 字段填充服务
```typescript
// src/autofill/content/services/field-filling.ts
export class FieldFillingService {
    // 填充用户名
    fillUsername(field: HTMLInputElement, value: string): void;

    // 填充密码
    fillPassword(field: HTMLInputElement, value: string): void;

    // 填充 2FA 验证码
    fill2FA(field: HTMLInputElement, code: string): void;

    // 触发 React/Vue 等框架的输入事件
    triggerInputEvents(field: HTMLInputElement): void;
}
```

### Phase 3: 后台脚本模块重构（中优先级）

#### 3.1 分离 background.ts

**当前问题**：
- background.ts 有 582 行代码
- 所有处理器混在一起
- 缺少清晰的职责划分

**重构方案**：
```
src/background/
├── main.ts                          # 主入口
├── handlers/                        # 事件处理器
│   ├── runtime.handler.ts            # 运行时事件
│   ├── message.handler.ts            # 消息处理
│   ├── install.handler.ts            # 安装/更新事件
│   ├── tabs.handler.ts              # 标签页事件
│   └── context-menu.handler.ts      # 右键菜单
└── services/                        # 后台服务
    ├── vault.service.ts              # 密码库服务
    ├── autofill.service.ts           # 自动填充服务
    ├── backup.service.ts             # 备份服务
    ├── crypto.service.ts             # 加密服务
    └── settings.service.ts           # 设置服务
```

#### 3.2 密码库服务
```typescript
// src/background/services/vault.service.ts
export class VaultService {
    // 获取所有密码
    async getAllPasswords(): Promise<PasswordItem[]>;

    // 根据域名匹配密码
    async getPasswordsForDomain(domain: string): Promise<PasswordItem[]>;

    // 保存密码
    async savePassword(item: PasswordItem): Promise<void>;

    // 更新密码
    async updatePassword(item: PasswordItem): Promise<void>;

    // 删除密码
    async deletePassword(id: number): Promise<void>;

    // 检查密码是否存在
    async passwordExists(username: string, domain: string): Promise<boolean>;
}
```

### Phase 4: UI/UX 改进（中优先级）

#### 4.1 覆盖层 UI 组件化

**当前问题**：
- UI 直接在 content.ts 中用字符串拼接创建
- 难以维护和复用
- 样式分散

**重构方案**：
```typescript
// src/autofill/content/overlay/components/
export class AutofillPopupComponent {
    private container: HTMLElement;

    constructor(anchor: HTMLElement) {
        this.container = this.createPopup();
        this.positionPopup(anchor);
    }

    createPopup(): HTMLElement {
        // 使用组件化方式创建 UI
    }

    show(): void;
    hide(): void;
    updatePasswords(passwords: PasswordItem[]): void;
}
```

#### 4.2 路由和状态管理

**改进点**：
- 使用 React Router 的嵌套路由
- 添加加载状态管理
- 添加错误边界

```typescript
// src/App.tsx - 改进后的路由结构
<Routes>
  <Route path="/" element={<RequireAuth><VaultLayout /></RequireAuth>}>
    <Route index element={<PasswordList />} />
    <Route path="notes" element={<NoteList />} />
    <Route path="documents" element={<DocumentList />} />
    <Route path="authenticator" element={<AuthenticatorList />} />
    <Route path="settings/*" element={<SettingsLayout />} />
  </Route>
  <Route path="/lock" element={<LockScreen />} />
  <Route path="/setup" element={<SetupScreen />} />
  <Route path="*" element={<NotFound />} />
</Routes>
```

### Phase 5: 安全性增强（高优先级）

#### 5.1 密钥管理服务
```typescript
// src/services/encryption/key-management.service.ts
export class KeyManagementService {
    // 派生加密密钥
    async deriveKeys(masterPassword: string, salt: Uint8Array): Promise<EncryptionKeys>;

    // 清除内存中的密钥
    clearKeys(): void;

    // 检查密钥是否存在
    hasKeys(): boolean;

    // 自动锁定超时
    setAutoLockTimeout(duration: number): void;

    // 生物识别集成（预留）
    async setupBiometrics(): Promise<boolean>;
}
```

#### 5.2 内存清理服务
```typescript
// src/services/memory-cleanup.service.ts
export class MemoryCleanupService {
    // 清理敏感数据
    clearSensitiveData(): void;

    // 清理剪贴板
    async clearClipboard(): Promise<void>;

    // 定时清理
    startAutoCleanup(): void;

    // 停止清理
    stopAutoCleanup(): void;
}
```

### Phase 6: 性能优化（中优先级）

#### 6.1 懒加载
```typescript
// React 路由懒加载
const PasswordList = lazy(() => import('./features/passwords/PasswordList'));
const NoteList = lazy(() => import('./features/notes/NoteList'));
const DocumentList = lazy(() => import('./features/documents/DocumentList'));
const AuthenticatorList = lazy(() => import('./features/authenticator/AuthenticatorList'));
const Settings = lazy(() => import('./features/settings/Settings'));

<Suspense fallback={<LoadingSpinner />}>
  <Routes>...</Routes>
</Suspense>
```

#### 6.2 缓存策略
```typescript
// src/services/cache.service.ts
export class CacheService {
    // 密码缓存（短期）
    private passwordCache: Map<string, PasswordItem[]>;

    // 缓存域名匹配结果
    async getCachedPasswords(domain: string): Promise<PasswordItem[] | null>;

    // 设置缓存
    setCachedPasswords(domain: string, passwords: PasswordItem[]): void;

    // 清除缓存
    clearCache(): void;

    // 定期清理
    startAutoClear(): void;
}
```

---

## 🔧 具体实施步骤

### 第一步：创建服务层框架
```bash
# 创建目录结构
mkdir -p src/services
mkdir -p src/autofill/content/services
mkdir -p src/autofill/content/overlay
mkdir -p src/background/handlers
mkdir -p src/background/services
```

### 第二步：实现存储服务
```typescript
// src/services/storage.service.ts
// 创建统一的存储接口
// 抽象 chrome.storage.local
// 添加类型安全
// 实现错误处理
```

### 第三步：实现消息服务
```typescript
// src/services/message.service.ts
// 创建消息命令枚举
// 实现发送/监听模式
// 添加类型安全
// 实现超时处理
```

### 第四步：重构自动填充
```typescript
// 将 content.ts 拆分成多个文件
// 1. init.ts - 初始化逻辑
// 2. message-handler.ts - 消息处理
// 3. form-detection.ts - 表单检测
// 4. field-filling.ts - 字段填充
// 5. 2fa-detector.ts - 2FA 检测
// 6. icon-injector.ts - 图标注入
// 7. popup-manager.ts - 弹窗管理
```

### 第五步：重构后台脚本
```typescript
// 将 background.ts 拆分成多个文件
// 1. main.ts - 主入口
// 2. runtime.handler.ts - 运行时事件
// 3. message.handler.ts - 消息处理
// 4. vault.service.ts - 密码库服务
// 5. autofill.service.ts - 自动填充服务
```

### 第六步：UI 组件化
```typescript
// 将覆盖层 UI 拆分成组件
// 1. IconComponent - 图标组件
// 2. PopupComponent - 弹窗组件
// 3. PasswordItemComponent - 密码项组件
// 4. TOTPItemComponent - TOTP 项组件
```

---

## 📈 预期收益

### 开发效率
| 指标 | 改进前 | 改进后 | 提升 |
|--------|---------|---------|------|
| **代码行数/文件** | ~1500 行 | ~300 行 | ⬇️ 80% |
| **可测试性** | 低 | 高 | ⬆️ 500% |
| **可维护性** | 低 | 高 | ⬆️ 300% |
| **组件复用** | 无 | 高 | ⬆️ 新增 |

### 性能
| 指标 | 改进前 | 改进后 |
|--------|---------|---------|
| **内存使用** | 基准 | ⬇️ 30% |
| **页面注入时间** | 基准 | ⬇️ 40% |
| **密码匹配速度** | 基准 | ⬆️ 200%（缓存） |

### 用户体验
| 特性 | 状态 |
|--------|------|
| ✅ 更快的自动填充 | 缓存 + 优化 |
| ✅ 更可靠的表单检测 | 改进算法 |
| ✅ 更好的 2FA 支持 | 专用服务 |
| ✅ 更平滑的 UI | 组件化 |
| ✅ 更强的安全性 | 密钥管理 |

---

## 🚀 实施时间表

| 阶段 | 任务 | 预计时间 |
|--------|------|----------|
| **Phase 1** | 服务层重构 | 3-5 天 |
| **Phase 2** | 自动填充模块重构 | 5-7 天 |
| **Phase 3** | 后台脚本重构 | 3-5 天 |
| **Phase 4** | UI/UX 改进 | 5-7 天 |
| **Phase 5** | 安全性增强 | 3-4 天 |
| **Phase 6** | 性能优化 | 3-4 天 |
| **总计** | - | **22-32 天** |

---

## 📝 注意事项

### 兼容性
- ✅ 保持 Manifest V3 兼容
- ✅ 支持 Chrome, Edge, Firefox
- ✅ 测试不同浏览器版本

### 向后兼容
- ✅ 保持现有数据格式
- ✅ 保持现有 API
- ✅ 提供迁移路径

### 测试
- ✅ 单元测试覆盖服务层
- ✅ 集成测试覆盖自动填充
- ✅ E2E 测试覆盖主要流程

---

## 🎓 参考资源

- [Bitwarden Browser Extension](https://github.com/bitwarden/clients/tree/main/apps/browser)
- [Chrome Extension MV3 Migration Guide](https://developer.chrome.com/docs/extensions/mv3/intro)
- [MDN Web Extensions API](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions)
- [TypeScript Best Practices](https://www.typescriptlang.org/docs/handbook/declaration-files/do-s-and-don-ts)

---

**下一步**：开始实施 Phase 1 - 服务层重构
