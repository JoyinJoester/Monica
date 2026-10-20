# Monica for Android 1.0.321

> 未发布 / Unreleased

## 中文

- 修复旧版 KeePass 验证器条目编辑保存后、切换数据库时出现重复项目的问题；保存前恢复原条目身份，无法唯一确认时停止写入，保留独立条目。

- 卡包新增会员卡、积分卡与旅行票证模板，可从新建页顶部菜单切换并保留各模板草稿；支持会员等级、积分余额／到期日、航班／车次、行程时间、航站楼、登机口、座位等字段，提供类型筛选、搜索、详情复制与重新编辑，沿用加密存储、附件及同步机制。#169

- 支持同步 Bitwarden 团队／组织保险库中有权读取的条目及附件元数据，兼容组织密钥与独立条目密钥；编辑回传保留组织、集合和密钥归属，只读或解密失败时保留本地更改并提示。#168

- 修复数据库筛选菜单编辑模式下 KeePass、MDBX、Bitwarden 文件夹无法打开操作弹窗的问题，统一重命名与删除确认，并在成功后刷新文件夹列表。

- 修复附近设备同步“仅接收”模式在等待连接时准备失败的问题，保持仅接收限制，并在现有超时范围内重试手动同步的临时连接失败。

- 修复部分嵌套网页丢失网址导致绑定账号无法推荐的问题；从键盘和主动填充入口打开选择页时复用现有匹配规则推荐已绑定账号。#167
- 修复将 Android 应用包名直接填写在网址字段时无法推荐绑定账号的问题；兼容裸包名和 `androidapp://` 绑定格式，并保留普通网址匹配。#167

- 修复银行卡有效期输入年份首位后光标跳回、后续数字顺序错乱的问题；同步修复密码条目支付信息中的有效期输入，改善粘贴、选区替换和删除时的光标位置。

- 优化自动填充账号操作为 M3E 居中弹窗，分组展示填充、复制和详情操作，支持大字体滚动；新增独立“无障碍填充”，从输入法、系统 Autofill 或磁贴进入均可使用，服务未启用时提供设置入口，并正确返回目标应用填写。

- 统一默认与新增账号密码凭据的编辑体验：添加密码时平滑展开，生成器与删除操作统一放入三点菜单，标签统一为“用户名”并复用星星生成器图标；每个凭据固定提供简单的多行备注，独立保存、编辑与展示，不覆盖页面已有笔记或备注。

- 优化凭据布局：新建与编辑页的备注紧接验证器密钥，连成一组卡片；详情页备注连接在验证码下方，空白备注不显示，支持复制；密码删除改为图标，与密码强度标签同一行。

- 新增每条网站 URL 的独立自动填充匹配策略，可在右侧三点菜单设置默认、主域名、主机名、前缀、精确 URL、正则或从不匹配；移动 URL 时规则一同移动。新建与编辑页的三点操作统一为 M3E 分组菜单，匹配检测使用居中弹窗。浏览器只提供域名时，不推测路径或查询参数。#171

## English

- Fix duplicate legacy KeePass authenticator entries after editing and switching databases. Resolve the original entry before saving and stop ambiguous writes while preserving distinct entries.

- Added membership, rewards and travel-pass templates to the wallet create menu, retaining separate drafts while switching. Includes membership tiers, points/expiry, flight or service numbers, itinerary times, terminals, gates and seats, with type filters, search, copyable details and re-editing through existing encrypted storage, attachment and sync flows. #169

- Added sync for readable Bitwarden team/organization items and attachment metadata, including organization and per-item keys. Updates retain organization, collections and key ownership; read-only or failed operations preserve pending local changes.
- Fixed folder actions in database filter edit mode for KeePass, MDBX and Bitwarden, with shared rename/delete dialogs, deletion confirmation and folder-list refresh after successful changes.
- Fixed receive-only nearby sync failing before connecting when the receiving device acts as the listener; receive-only limits remain enforced. Manual sync retries transient connection setup failures within its existing timeout.
- Fixed missing bound-account suggestions when nested web forms lost their origin. Pickers opened from the keyboard or active-fill notification now reuse existing matching rules for saved app and website bindings. #167
- Fixed missing autofill suggestions when an Android package name was entered directly in the website field. Bare package names and `androidapp://` bindings now match while ordinary website matching remains unchanged. #167
- Fixed the expiry-date cursor jumping before the first year digit and reversing subsequent input in bank cards and password payment fields. Pasting, selection replacement and deletion now preserve the correct editing position.
- Redesigned autofill account actions as a centered M3E dialog with grouped fill, copy and detail actions and scrolling for large text. Added independent Accessibility fill from the keyboard, system Autofill and quick tile, with a setup prompt when the service is disabled and correct return to the target app.
- Unified primary and added credential editors with smooth password expansion, shared overflow menus for generation/deletion, consistent Username labels and star generator icons. Each credential now has a fixed multiline note field, saved and displayed independently of page-level notes.
- Refined credential layouts: editor notes connect directly below the verification key; detail notes connect below the authenticator and stay hidden when blank, with copying supported. Password deletion uses an icon on the strength-label row.
- Added per-URL autofill matching from the website overflow menu: Default, Base domain, Host, Starts with, Exact URL, Regex and Never. Rules follow reordered URLs. Create/edit overflow actions now share grouped M3E menus, with a centered matching dialog. Path/query matching requires a full URL from the browser. #171
