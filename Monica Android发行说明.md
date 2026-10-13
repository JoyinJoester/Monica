# Monica for Android 1.0.320

> 未发布 / Unreleased

## 中文

- 优化原始数据库管理：名称最多显示两行、双栏独立滚动，支持滑动区间多选、长按菜单，以及跟随当前聚焦栏的底部操作与前进／后退导航；修复普通 MDBX 条目跨格式传输被误拦截。

- 新增 Bitwarden Authenticator CSV 导入，独立入口简称“Bitwarden Auth.”，保留验证器账户及验证码参数。

- 修复验证器导入名称重复包含账户名：扫码、链接和批量导入默认使用发行者名称，账户名保留在独立字段；手动填写的名称不受影响。

- 银行卡新增默认“开户行”字段，支持填写、编辑、复制和搜索，并在备份及受支持的数据库同步中保留。

## English

- Improved raw database management with two-line names, independent pane scrolling, swipe range selection, context menus, and focused-pane actions and navigation history; fixed ordinary MDBX entries being incorrectly blocked during cross-format transfers.

- Added CSV import from Bitwarden Authenticator through a separate “Bitwarden Auth.” entry, preserving accounts and OTP parameters.

- Authenticator imports now use the issuer as the default name for QR codes, links and batch imports, keeping the account in its own field and preserving manually entered names.

- Added a default bank branch field to bank cards, with editing, copying and search, preserved in backups and supported database sync.
