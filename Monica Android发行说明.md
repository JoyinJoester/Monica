# Monica for Android 1.0.320

> 未发布 / Unreleased

## 中文

- 修复 Android Studio 未继承 Rust 工具路径时无法打包的问题，支持自动定位已安装工具和自定义 Cargo 路径。

- 补全附近设备同步的文件夹、根目录附件与独立标签，保留附件身份、扩展数据和来源归属；附件校验失败或不支持文件夹删除时保留受影响的数据并说明原因。

- 修复冷启动或绑定验证器查询超时时，自动填充后偶尔不显示验证码通知的问题；密码填充仍优先完成。

- 修复浏览器访问本机自托管网页时无法匹配已保存凭据的问题，保留应用内嵌页面的兼容行为。

- 升级注册、改密和分步登录的自动填充：已保存密码不会覆盖新密码框，生成密码仅填入新密码与确认框；保存时优先使用新密码，并可确认更新匹配的原条目，保留文件夹、备注、附件和其他账户。

- 无障碍补充填充同样避开新密码与确认框，并校验动态网页的密码写入结果以便必要时重试；再次锁定时立即关闭单独验证过的自动填充保存页。

- 改进验证返回与连续登录，限制临时上下文的来源和有效期；增加可取消的请求时限、缓存失效处理和不含凭据的分阶段耗时诊断。

- 改进自动填充的网页识别：支持浏览器标准化提示，准确区分账号、当前密码、新密码和验证码；网页提供表单结构时仅填充当前表单，保留原生应用和旧网页的兼容逻辑。

- 优化原始数据库管理：名称最多显示两行、双栏独立滚动，支持滑动区间多选、长按菜单，以及跟随当前聚焦栏的底部操作与前进／后退导航；修复普通 MDBX 条目跨格式传输被误拦截。

- 新增 Bitwarden Authenticator CSV 导入，独立入口简称“Bitwarden Auth.”，保留验证器账户及验证码参数。

- 修复验证器导入名称重复包含账户名：扫码、链接和批量导入默认使用发行者名称，账户名保留在独立字段；手动填写的名称不受影响。

- 银行卡新增默认“开户行”字段，支持填写、编辑、复制和搜索，并在备份及受支持的数据库同步中保留。

## English

- Fixed packaging when Android Studio does not inherit the Rust tool path, with installed-tool discovery and a custom Cargo path option.

- Nearby sync now preserves folder/root attachments, standalone labels, portable identities, extension data and source ownership. Failed attachment verification or unsupported folder deletion preserves the affected data and explains the issue.

- Fixed missing post-fill OTP notifications when a cold bound-authenticator lookup times out, without delaying credential filling.

- Fixed saved-credential matching on browser loopback pages while preserving hybrid-app compatibility.

- Improved autofill for sign-up, password changes and multi-step login. Stored passwords leave new-password fields untouched; generated passwords fill the new/confirmation pair. Saving prefers the new password and offers a reviewed update to a matching entry, preserving folders, notes, attachments and other accounts.

- Accessibility fallback also leaves new-password and confirmation fields untouched and verifies dynamic web writes before completing. Locking closes independently verified autofill save screens.

- Improved authentication return and login continuity with origin-scoped, expiring context; added cancellable request deadlines, cache invalidation and credential-free phase timing diagnostics.

- Improved web autofill with browser-normalized hints for accounts, current/new passwords and OTPs. When form structure is available, filling stays within the active form while retaining compatibility with native apps and older web pages.

- Improved raw database management with two-line names, independent pane scrolling, swipe range selection, context menus, and focused-pane actions and navigation history; fixed ordinary MDBX entries being incorrectly blocked during cross-format transfers.

- Added CSV import from Bitwarden Authenticator through a separate “Bitwarden Auth.” entry, preserving accounts and OTP parameters.

- Authenticator imports now use the issuer as the default name for QR codes, links and batch imports, keeping the account in its own field and preserving manually entered names.

- Added a default bank branch field to bank cards, with editing, copying and search, preserved in backups and supported database sync.
