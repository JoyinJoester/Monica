# Monica for Android 1.0.321

> 未发布 / Unreleased

## 中文

- 修复新建与编辑页三点菜单不随“页面缩放”变化的问题：弹出窗口整体继承页面密度，菜单尺寸、圆角、间距、图标和文字同步缩放；保留系统字体大小设置，较长菜单可滚动。

- 附近设备同步的连接诊断按钮居中；已配对设备支持长按／右滑多选及左滑删除，单项与批量删除配对均二次确认并保留本地内容。接收远端删除前预览并弹窗确认，可选择同步删除或仅接收新增和更新；取消、锁定及后台中断不会默认同意删除；按删除版本记住跳过选择，避免重复提示，新的删除版本仍需确认。

- 附近设备同步状态卡移至页面顶部，固定无背景 M3E 指示器搭配高对比度中央文字，在空闲时静止、同步时转动，中央显示当前阶段的实际进度；未知总量不显示虚假百分比。完善准备、等待与重试提示，准备完成后保留完整连接窗口；取消立即反馈并中断可取消的准备及网络任务，保留配对和已保存内容。

- 移除二维码模板编辑表单中标题、内容与备注输入框的底部指示线，聚焦、未聚焦及禁用时均保持完整圆角填充样式。

- 会员卡与积分卡详情新增“查看会员码”，直接使用已保存的会员号，复用现有二维码／Code 128 条形码展示页；保留开头的零与原始文本，不支持条形码的内容仍可查看二维码。

- 修复密码页面列表直接显示银行卡号和笔记内容的问题：卡号预览仅保留末四位（含旧导入条目的用户名／持卡人预览），笔记隐藏中间内容，短笔记全部遮挡；单项与分组卡片统一处理，保留原始数据、搜索及详情。

- 修复密码列表验证码进度条在首次进入或返回前台时从旧进度追赶的问题；立即按当前时间对齐，保留毫秒精度、按秒更新卡片与绘制层平滑动画，共享计时器恢复时重新采样。

- Monica 本地数据库新增“管理原始信息”入口；项目菜单支持单项／批量删除并二次确认，显示当前栏、数据库和所选数量，删除前重新校验数据版本。本地密码与卡片等内容移至回收站。

- 补齐 KeePass、MDBX 与 Bitwarden 文件夹的移动、复制入口，统一使用居中目标选择弹窗；KeePass/MDBX 复用原生树迁移并校验写入，失败保留源内容。Bitwarden 支持同账户内移动和复制，保留加密字段；含附件、通行密钥或组织条目时暂停复制。

- 修复天翼云盘等应用使用本地 file: WebView 登录页时，明确绑定应用或包名的凭据不推荐的问题；仅恢复精确包名匹配，保留缺失域名的浏览器及远程 WebView 保护。

- 修复旧版 KeePass 验证器条目编辑保存后、切换数据库时出现重复项目的问题；保存前恢复原条目身份，无法唯一确认时停止写入，保留独立条目。

- 卡包新增会员卡、积分卡与旅行票证模板，可从新建页顶部菜单切换并保留各模板草稿；支持会员等级、积分余额／到期日、航班／车次、行程时间、航站楼、登机口、座位等字段，提供类型筛选、搜索、详情复制与重新编辑，沿用加密存储、附件及同步机制。#169

- 支持同步 Bitwarden 团队／组织保险库中有权读取的条目及附件元数据，兼容组织密钥与独立条目密钥；编辑回传保留组织、集合和密钥归属，只读或解密失败时保留本地更改并提示。#168

- 修复数据库筛选菜单编辑模式下 KeePass、MDBX、Bitwarden 文件夹无法打开操作弹窗的问题，统一重命名与删除确认，并在成功后刷新文件夹列表。

- 修复附近设备同步“仅接收”模式在等待连接时准备失败的问题，保持仅接收限制，并在现有超时范围内重试手动同步的临时连接失败。

- 修复部分嵌套网页丢失网址导致绑定账号无法推荐的问题；从键盘和主动填充入口打开选择页时复用现有匹配规则推荐已绑定账号。#167
- 修复将 Android 应用包名直接填写在网址字段时无法推荐绑定账号的问题；兼容裸包名和 `androidapp://` 绑定格式，并保留普通网址匹配。#167

- 修复银行卡有效期输入年份首位后光标跳回、后续数字顺序错乱的问题；同步修复密码条目支付信息中的有效期输入，改善粘贴、选区替换和删除时的光标位置。

- 优化自动填充账号操作为 M3E 居中弹窗，分组展示填充、复制和详情操作，支持大字体滚动；新增独立“无障碍填充”，从输入法、系统 Autofill 或磁贴进入均可使用，服务未启用时提供设置入口，并正确返回目标应用填写。

- 统一默认与新增账号密码凭据的编辑体验：添加密码时平滑展开，生成器与删除操作统一放入三点菜单，标签统一为“用户名”并复用星星生成器图标；每个凭据固定提供简单的多行备注，独立保存、编辑与展示，不覆盖页面已有笔记或备注。#172

- 优化凭据布局：新建与编辑页的备注紧接验证器密钥，连成一组卡片；详情页备注连接在验证码下方，空白备注不显示，支持复制；密码删除改为图标，与密码强度标签同一行。

- 新增每条网站 URL 的独立自动填充匹配策略，可在右侧三点菜单设置默认、主域名、主机名、前缀、精确 URL、正则或从不匹配；移动 URL 时规则一同移动。匹配策略显示在对应网址卡片内，保持多网址卡片连续分组。新建与编辑页的三点操作统一为 M3E 分组菜单，去掉菜单外层厚包边与外壳阴影，并按当前屏幕宽度随 DPI 自适应，匹配检测使用居中弹窗，选项四角统一圆角。浏览器只提供域名时，不推测路径或查询参数。#171

## English

- Fixed create/edit three-dot menus ignoring interface scale: the entire popup now inherits page density, scaling its size, corners, spacing, icons and text together. System font size is retained and long menus remain scrollable.

- Centered nearby connection diagnostics. Paired devices support long-press/right-swipe selection and left-swipe removal; single and batch unpairing require confirmation and keep local content. Incoming deletions are previewed before applying, with a choice to include deletions or receive additions and updates only. Cancellation, locking and background interruptions never grant deletion consent. Skipped deletion versions are remembered locally to prevent repeat prompts; newer deletions still require consent.

- Moved nearby sync status to the top with a fixed background-free M3E indicator and high-contrast center text: static when idle, animated during sync, and showing actual per-stage progress when available. Clarified preparation, waiting and retries; the connection window starts after preparation. Cancel responds immediately and interrupts cancellable preparation and network work while retaining pairing and saved content.

- Removed the bottom indicators from title, content and notes fields in the QR template editor, retaining rounded filled fields when focused, unfocused or disabled.

- Membership and rewards card details now offer View membership code, reusing the existing QR / Code 128 viewer with the saved membership number. Leading zeros and original text are preserved; values unsupported by Code 128 can still be shown as QR codes.

- Mask bank-card numbers and note previews in the password list: show only the last four card digits, including legacy username/cardholder previews, and hide the middle of notes or all of short notes. Single and grouped cards share the rule; original data, search and details are preserved.

- Fixed password-list OTP progress catching up from stale positions on entry or foreground return. Progress now aligns immediately with current time, retains millisecond precision and draw-layer smoothing with second-level card updates, and refreshes the shared clock on resume.

- Added raw-information management for Monica local storage and confirmed single/batch deletion from item actions. Confirmation identifies the pane, database and selection count; writes recheck the source revision. Local passwords and secure items go to Trash.

- Added move/copy actions for KeePass, MDBX and Bitwarden folders with a centered destination picker. KeePass/MDBX use native tree transfers and verified writes, retaining the source on failure. Bitwarden supports same-account folder moves/copies while preserving encrypted fields; copies with attachments, passkeys or organization entries are blocked.

- Fixed missing explicitly app/package-bound suggestions for local file: WebView logins such as Tianyi Cloud. Only exact package matching is restored; originless browsers and remote WebViews remain protected.

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
- Added per-URL autofill matching from the website overflow menu: Default, Base domain, Host, Starts with, Exact URL, Regex and Never. Rules follow reordered URLs. Matching policies stay inside each URL card, keeping grouped rows connected. Create/edit overflow actions now share grouped M3E menus without the thick outer rim or shell shadow, resize with the current screen width/DPI, and use a centered matching dialog with uniform option corners. Path/query matching requires a full URL from the browser. #171
