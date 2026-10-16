# 生物识别与主密码认证策略验证 · 1.0.320

2026-10-16，普通版与 F-Droid 未发布版本；公共 AVD Monica_Issue136_API_32，API 32、x86_64、专用用户 10。全部数据为合成测试数据。

## 改动范围

- 主应用、自动填充及共用生物识别入口仅允许 BIOMETRIC_STRONG，系统 PIN、图案和锁屏密码不再作为 Monica 的认证凭据。
- 回调明确拒绝设备凭据类型；Android 9/10 未报告类型的旧系统结果仅在已限制为强生物识别的弹窗中兼容。Android 11 及以上未知类型不授权。
- 自动填充选中建议和完整选择页复用既有 Monica 主密码组件作为备用路径；无指纹、未录入、用户选择密码或指纹锁定均可使用原主密码，不需要再次选择账号。返回或取消不授权。
- 普通版凭据交换在未设置 Monica 主密码时要求先完成设置，不再单独借用设备 PIN 授权导出。F-Droid 不包含该 Google 功能。
- 原有数据格式、主密码、Keystore 别名及密钥包装方式不变，不执行认证策略迁移或重置。验证既有密码时不套用新密码长度规则；未提交的输入不再写入保存状态。
- 用户此前反馈的设备锁屏／Keystore 问题不归因于本次代码，也未记为本次修复。

## 自动化与构建

- 普通版相关单元测试 328 项通过，F-Droid 330 项通过；包含实际 PromptInfo 配置及回调类型策略的 API 28/29/32/34 框架模拟。
- API 32 上的 MasterPasswordFallbackUiTest 两项通过：既有短主密码进入存储验证器、错误密码被拒绝，取消不会调用验证器或授权。
- 两版 Debug、AndroidTest 和 Release 均构建成功。API 36 厂商手机未实测，不能把模拟器结果扩展为所有设备保证。
- 最初额外运行旧 BiometricUnlockRegressionGuardTest 时有 4 条既有源码文本断言失败：mdkWrapperRebuildHandlesInvalidatedAndUnrecoverableKeystoreKeys、emptyRuntimeMdkCacheCannotMaskReadableKeystoreWrapper、pageSwitchHotPathsDoNotRunAuthOrBitwardenSyncWorkOnMainThread、autofillPasswordSelectionReturnPathDoesNotBlockMainThread。断言依赖的 SecurityManager、AddEditPasswordScreen、AutofillPickerActivityV2 和该测试文件均与修复前 HEAD 相同；未修改实现来迎合过时字符串检查。上述通过数量为本次选定的认证与自动填充套件，不代表全仓测试全绿。

## 覆盖升级

- 从公共测试机提取修改前 1.0.320-26101612-12 安装包所用环境，在旧包上运行 BiometricPolicyUpgradeTest 的 seed 阶段。
- 通过覆盖安装新包后运行 verify：启动前后 Keystore 别名及加密配置不变，错误主密码被拒绝，原主密码可重复解锁；密码、OTP、附件内容密钥及通行密钥私钥测试值均可读取，既有生物识别开关保留。
- 测试按独立前缀保存合成配置，结束时仅清理该前缀；没有删除公共 Keystore 别名。

## Android 系统交互

以下 18 项在普通版 APK 上执行。F-Droid 完成对应单元测试与构建，16 个共享实现、测试和报告文件已逐一核对一致；本轮未在模拟器中另行安装 F-Droid APK 重跑这些交互。

- device PIN rejected as Monica password; no biometric hardware/enrollment required for fallback：通过。
- original Monica password fills selected credential：通过。
- cancelled verification returns no credential values：通过。
- full picker requires Monica password even with quick verification off：通过。
- main app rejects device PIN：通过。
- main app accepts original Monica password after upgrade：通过。
- autofill native prompt offers biometrics and Monica password, no device-credential option：通过。
- unregistered fingerprint does not authorize autofill：通过。
- registered fingerprint fills selected credential in one verification：通过。
- native password button opens Monica password component：通过。
- Monica password fallback works with fingerprints enrolled：通过。
- cancelling native biometric prompt does not fill：通过。
- biometric lockout falls back to Monica password, not device PIN：通过。
- Monica password remains usable during biometric lockout：通过。
- unlocking Android with device PIN still requires fresh Monica biometric/password verification：通过。
- main app rejects unregistered fingerprint：通过。
- main biometric negative button retains original Monica password unlock：通过。
- main app still unlocks with enrolled fingerprint after upgrade：通过。

初次驱动遇到密码按键输入过快导致丢字、系统界面树缓存滞后、主页面标签使用缩写，以及设置页指纹录入 intent 缺少验证句柄等测试环境问题。已分别改为逐字输入、刷新节点、检查真实标签及经系统指纹设置页完成录入，再完成上述断言；未把驱动失败当作应用修复。

本地 M3E Canvas 草图已改为强生物识别与 Monica 主密码流程，并检查密码备用界面的实际渲染。设计和截图仅留本地，未纳入提交。

## 清理

- 测试会话正常结束并执行 finally：恢复应用设置，删除合成条目；升级测试的 cleanup 阶段通过。
- 用户 10 的临时 PIN 和指纹已移除，锁屏恢复为滑动，指纹数量核验为 0；自动填充恢复原 Google 服务。
- 全局保持唤醒恢复为 0，屏幕超时保持原值 600000；移除临时界面树文件和本任务 adb 端口转发，切回用户 0。
- 公共 AVD 在任务开始前已运行，因此继续保留运行；未新建 AVD，未删除共享密钥或数据盘。
