# Monica HyperOS 通行密钥安全兼容实现说明

## 1. 背景与目标

小米 HyperOS 对 AOSP Credential Manager / BiometricPrompt 行为存在差异，可能导致通行密钥创建与认证流程中的生物识别验证失败。

本次实现目标：

- 在 HyperOS 上规避通行密钥生物识别提示不兼容问题。
- 不降低安全基线，优先使用主密码回退验证。
- 通过 feature flag 提供可控开关，便于灰度、回滚和排障。
- 限定改动在通行密钥链路，避免影响 IME/Autofill 等其他生物识别场景。

## 2. 与 Bitwarden PR #6316 的关系

Bitwarden 的做法是在 CredentialEntry 构建层，针对 HyperOS 跳过 BiometricPromptData 注入。

Monica 当前架构中未使用 BiometricPromptData 注入，而是在 Passkey Activity 中主动触发 BiometricPrompt。

因此 Monica 采用等价策略：

- 在 PasskeyAuthActivity / PasskeyCreateActivity 的“用户验证入口”做 HyperOS 条件分支。
- 命中 HyperOS 且开关开启时，跳过 biometric prompt，转主密码验证回退。

## 3. 安全设计

### 3.1 安全原则

- 不静默降级。
- 不隐藏失败。
- 不把“兼容”变成“无验证放行”。

### 3.2 验证策略

当满足以下条件时触发 HyperOS 兼容路径：

- ROM 类型为 HyperOS。
- `hyperos_biometric_bypass_enabled` 开关为 true。

兼容路径行为：

- 若已设置主密码：弹出主密码验证对话框，验证通过后继续 Passkey 流程。
- 若未设置主密码：沿用当前产品安全模型直接继续（与现有无主密码用户行为一致）。

### 3.3 可观测性

新增审计/诊断事件：

- `PASSKEY_AUTH_BIOMETRIC_BYPASSED_HYPER_OS`
- `PASSKEY_CREATE_BIOMETRIC_BYPASSED_HYPER_OS`
- `master_password_success` / `master_password_failed`

## 4. 改动点清单

### 4.1 新增文件

- `Monica for Android/app/src/main/java/takagi/ru/monica/passkey/PasskeyBiometricCompatibilityPolicy.kt`
- `Monica for Android/app/src/test/java/takagi/ru/monica/passkey/PasskeyBiometricCompatibilityPolicyTest.kt`

### 4.2 修改文件

- `Monica for Android/app/src/main/java/takagi/ru/monica/passkey/PasskeyValidationFlags.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/passkey/PasskeyAuthActivity.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/passkey/PasskeyCreateActivity.kt`
- `Monica for Android/app/src/main/java/takagi/ru/monica/ui/screens/PasskeySettingsScreen.kt`
- `Monica for Android/app/src/main/res/values/strings.xml`
- `Monica for Android/app/src/main/res/values-zh/strings.xml`
- `Monica for Android/app/src/main/res/values-ja/strings.xml`
- `Monica for Android/app/src/main/res/values-vi/strings.xml`

## 5. Feature Flag 约定

存储位置：`passkey_validation_flags` SharedPreferences

新增键：

- `hyperos_biometric_bypass_enabled`

默认值：

- `true`（优先保证 HyperOS 可用性）

设置入口：

- Passkey 设置页 > 请求校验卡片 > HyperOS 生物识别兼容开关

## 6. 回归验证清单

### 6.1 功能路径

- HyperOS + 开关开启：
  - Passkey 认证：不拉起 biometric prompt，走主密码回退或继续认证。
  - Passkey 创建：不拉起 biometric prompt，走主密码回退或继续创建。

- HyperOS + 开关关闭：
  - 恢复原生物识别流程。

- 非 HyperOS + 开关开启：
  - 不应触发 bypass，应保持原生物识别流程。

### 6.2 安全路径

- 主密码存在时：
  - 主密码错误不得继续创建/认证。
  - 主密码正确后才允许继续。

- 主密码不存在时：
  - 行为应与当前产品既有策略一致，不引入新隐式放行。

### 6.3 兼容路径

- IME 解锁、Autofill 鉴权、设置页生物识别开关行为应不受影响。

## 7. 注意点（必须遵守）

- 兼容逻辑只作用于 passkey 流程，不要扩散到全局生物识别 helper。
- 不要把异常吞掉，错误必须可诊断（日志/审计/诊断事件）。
- 遇到厂商兼容问题优先使用开关可控策略，避免硬编码不可回滚方案。
- 任何与验证链路相关改动都必须补单测或最小可验证策略测试。
- 不要在同一文件继续堆叠复杂逻辑，优先抽出策略文件（policy）。
