# Mozilla 表单站点自动填充实测（1.0.320）

日期：2026-10-16（Asia/Shanghai）。这是问题发现报告，没有修改产品自动填充逻辑，也没有新增保存字段。

后续已根据用户授权实现字段扩展与修复；最新状态见 [独立资料字段与网页填充验证](wallet-profile-fields-20261016.md)。下文保留修复前的发现，不代表当前版本结论。

## 结论

登录、明确标注的新旧密码、密码生成、认证取消和本次跨域隔离检查表现正常；地址和银行卡的浏览器填充存在可复现的阻塞，不能认定这部分已全面可用。多数失败是已有资料的识别、映射或回填问题，并非 Monica 无法保存这些信息。

优先修复邮箱／电话语义丢失、下拉框值类型、地址分行及表单分区，再决定是否扩展资料模型。

## 环境与方法

- 公共 AVD `Monica_Issue136_API_32`，Android 12L / API 32、x86_64，专用用户 10，Gboard。
- Chrome `155.0.8059.39`，启用 Chrome 的“Autofill using another service”及 Android 的 Monica 自动填充。
- 普通版已安装构建 `1.0.320-26101512-34`。源码核对基准 `05fe9c323cacadf673815497ce559255b70e5e50`；后续构建修复未改变该 APK 对应的自动填充源码。
- 通过 `MozillaAutofillAuditSessionTest` 写入合成登录、地址、证件和银行卡资料；数据经过实际加密存储及实际选择器读取。没有使用真实账户或真实银行卡。
- 在 Android Chrome 中访问真实 HTTPS 页面，通过实际触摸、Gboard 建议、Monica 选择器和认证界面填充；Chrome DevTools Protocol 读取填充前后的 DOM，包括开放的 Shadow Root。没有用 JavaScript 写入目标值来充当自动填充成功。
- 动态地址测试仅用页面 `change` 事件切换 US / DE，触发页面自己的增删或显隐逻辑。动态卡片第二步由手动键入测试卡号触发；该号码已存在于 **before** 快照，不计为 Monica 填充成果。
- 共覆盖站内 20 个 HTML 页面：18 个有输入控件的页面及首页、成功页。保留 83 个选取资料用例、4 个生成用例、1 个生成取消用例、3 个认证用例，共 91 个操作记录；另保存跨域子框架检查及两个无表单页检查。
- 每个用例均比较所有输入项的前后值；“selected”仅表示选择了资料，**不等于回填成功**。机器可读摘要见 [results JSON](mozilla-autofill-20261016-results.json)。

合成资料的关键差异：姓名 `Jane Quinn Doe`，公司 `Example Test Labs`，邮箱 `address.audit@example.test`，电话 `+12025550142`；街道 `123 Test Street`、第二行 `Suite 45`、城市 `Mountain View`、州 `CA`、邮编 `94043`、国家 `US`。证件另有独立的 first / middle / last name 和第三地址行 `Building 7`。银行卡采用公开测试号 `4242424242424242`、08/2030、测试安全码 123。登录资料绑定 `mozilla.github.io`，与地址邮箱不同。

## 已确认的现有功能问题

| 编号 | 实际表现及影响 | 复现证据 / 原因 |
| --- | --- | --- |
| A1 | 选择地址资料后，邮箱和电话变成 `Jane Quinn Doe`。选择证件后，两者反而留空。选择登录资料填邮箱时，电话及电话分段也被写成登录邮箱。 | `basic-address`、`basic-identity`、`us-address`、`all-address`、`all-email-login`、`relay-address` 等。`AutofillParserNg` 将邮箱／电话表示成 `Login.Username`；`FillResponseBuilderNg.buildAutofillHintNames` 将其固定输出为 `USERNAME`。地址映射的 `contains("name")` 随后返回姓名，证件映射则返回空的 `username`。原始邮箱／电话角色没有传到资料选择器。 |
| A2 | 街道、地址第 1/2/3 行都可能得到同一段完整地址，混入城市、州、邮编及国家；一些布局甚至将完整地址填进城市和州。中间名、国家全称没有填出。 | `us-address`、`us-identity`、`all-identity`、`layout-address`、`billing`。`POSTAL_ADDRESS` 映射使用了用于整段展示的 `toAutofillAddress()`；细粒度字段语义需要保留。城市／州误识别范围依页面标记与布局而变化。 |
| A3 | 地址中的州／国家或银行卡有效期使用 `<select>` 时，选完资料，整次回填被浏览器中止。 | `textarea-address`、`textarea-street`、`textarea-city`、`select-card`、`select-card-number` 及动态表单。Chrome 明确报 `The given AutofillValue wasn't expected, abort autofill` / `value must be a list value, not type=1`。`AutofillPickerActivityV2.buildResultDataset` 对所有值使用 `AutofillValue.forText`；需根据选项返回列表索引，并处理 `8` / `08`、`30` / `2030` 等值与展示文本差异。 |
| A4 | 在同一 HTML form 内选择收货地址，会同时填账单地址和自定义分区；选第一张卡，会把第二张卡的分区也填掉，还可能填到普通姓名框。 | `shipping`、`billing`、`section-autocomplete-shipping/custom/card1/card2`。即使页面有明确 `shipping`、`billing`、`section-card1` / `section-card2` 标记也发生。当前字段 token 解析丢弃分区前缀，回填分组未保持这些边界。 |
| A5 | 基础银行卡页的 `cc-name` 无建议且不回填；从卡号进入后能填卡号、有效期和安全码，但姓名仍漏掉。分段的持卡人姓名也未填出。 | `basic-card` 对比 `basic-card-number` / `basic-card-csc`；`all-card`、`all-cc-name`。具有更明确“Card Holder Name”标签的其他页面能靠启发式填出姓名，不能据此判定标准 `cc-name` 已支持。 |
| A6 | 动态银行卡最初只有卡号框时，没有 Monica 填充入口；手动输入卡号展开第二步后可以选卡，但仍受 A3 阻断。 | 两种动态页的 `*-card`、`*-card-revealed`。服务日志中出现 `Skip weak structured autofill request` / `insufficient_structured_targets`。需让高置信单个卡号字段能够发起合理的填充流程，并处理后续新增字段。 |

另有两项密码边界表现：三个 DOM 版本的“5 Password form”均把所选旧密码填进五个无用途标记的框；无法知道这些框是否应代表不同密码，应重新评估模糊表单的填充范围。三个“Server-filled password form”的已填密码框都没有出现建议；本轮针对的是已有值的第一个框，**未以此推断第二个空框也不可填**。用户明确选取另一凭据时，预填用户名表单会用新用户名替换 `myusername`，这与本次操作意图一致。

## 已通过的检查及其范围

- `autocomplete-all.html` 的登录只修改所属登录 form；选旧凭据进入注册 form，只填用户名，不把旧密码填进 `new-password`。
- 登录／改密页面的 7 类表单分别存在于普通 DOM、单个 Shadow Root、form 内容位于 Shadow Root，共 21 个运行时表单。所有发生填充的用例都没有修改其他 form；明确的旧密码只填 `current-password`，未修改 `new-password`。
- 4 次强密码生成分别覆盖普通注册、普通改密及两种 Shadow DOM 改密；仅对应的新密码框改变，改密的两格新密码相同，旧密码和其他 form 不变。取消生成后所有值保持不变。
- 锁定后显示“Unlock Monica to autofill”。错误主密码提示 `Password incorrect`，DOM 未改变；取消认证不写入；正确认证后能填入所属登录 form。已解锁会话可被复用，不能把没有重复弹认证误当成绕过验证。
- 原站跨域 iframe 可正常加载。父页面填充未改变 `bugs.mattn.ca` 子页面；聚焦子页面登录密码后，建议和选择器均显示该子域，显示“无匹配建议”，没有将父页面的合成凭据作为匹配建议。测试没有为子域新增账户，因此此项证明的是本轮隔离与来源展示，未覆盖子域自有凭据的正向回填。
- `layout_cc.html` 的两个独立 form 能分别填入各自卡号／有效期／安全码，未串到另一 form。持卡人姓名仍有 A5 问题。

## 逐页覆盖

页面相对路径均基于 <https://mozilla.github.io/form-fill-examples/>。

| 页面 | 本轮操作 | 结果 |
| --- | --- | --- |
| `index.html` | 实际加载、检查表单数 | 无表单，作为导航页 |
| `autocomplete-all.html` | 地址、证件、卡片、邮箱、登录、注册、中间名／国家全称／卡姓名直接聚焦、生成与认证 | A1/A2/A5；登录／新密码保护及认证检查通过 |
| `autocomplete-us-separate-fields.html` | 地址、证件 | 姓／名部分正确；中间名遗漏，地址与拆分电话错误 |
| `basic.html` | 地址及证件；地址复测 | A1/A2，常规城市／州／邮编／国家可填 |
| `basic_cc.html` | 聚焦姓名、卡号、安全码 | 从卡号／安全码可填卡片数值；姓名遗漏 |
| `basic_cc_2.html` | 姓名与卡号入口 | 选择成功、整组回填中止（A3） |
| `billing_shipping.html` | 分别从收货／账单分区进入 | A1/A2/A4 |
| `dynamic_form_changes/dynamic_forms_node_addition_and_removal.html` | 初始地址、US 新增、切换 DE 替换、初始卡号、手动展开后补填 | A3/A6；新字段已确实出现后才尝试补填 |
| `dynamic_form_changes/dynamic_forms_visibility_state_change.html` | 初始隐藏、US 显示、DE 切换、初始卡号、展开后补填 | A3/A6。隐藏字段未改变，但整次回填失败，不能据此证明所有隐形字段防护正确 |
| `layout.html` | 不同宽度的地址字段 | A1/A2，城市／州也出现整段地址 |
| `layout_cc.html` | 姓名、第一组卡号、第二组卡号 | 两个 form 隔离正确，A5 |
| `multiple_sections/section_autocomplete.html` | 普通、收货、账单、自定义、工作／家庭、卡1／卡2 | 明确分区仍串填（A4），并有 A1/A2 |
| `multiple_sections/section_heuristics.html` | 同上，无明确地址／卡分区 token | 多组同填；城市／州部分正常，A1/A2 |
| `name_utils.html` | 证件资料入口探测 | 未提供填充建议；这是姓名拆分／合并工具，没有普通 HTML form，不将其工具按钮算作 Monica 功能 |
| `password_manager/form_with_iframe.html` | 父页面填充、子域聚焦与选择器、取消 | 本轮跨域隔离及来源展示通过 |
| `password_manager/login_and_pw_change_forms.html` | 21 个 form，以及三种改密生成 | 标注清楚的密码角色／form 隔离通过；五密码框和预填密码的边界见上文 |
| `relay/relay_registration_form.html` | 注册、仅邮箱、带地址的邮箱 form | 登录邮箱可填，旧密码不进新密码框；地址邮箱受 A1 影响 |
| `textarea_select.html` | 公司、textarea 街道、城市三个入口 | 都被 select 类型错误中止（A3），不能据此单独判定 textarea 不支持 |
| `two_forms.html` | 两组视觉区域分别聚焦 | 两组均被填充。源码实际上只有一个 `<form>`，因此不将此例误记为跨 HTML form 泄漏 |
| `password_manager/success.html` | 实际加载、检查表单数 | 无输入控件 |

## 哪些信息值得考虑扩展保存

这里的“缺少”指缺少专门字段或自动填充语义。Monica 已有自定义字段，因此不等同于字符串完全无法保存；也不代表应立即增加新的产品字段。

| 候选 | 现有能力与缺口 | 建议 |
| --- | --- | --- |
| 区／县／更细一级行政区 `address-level3` | 地址和证件模型均有城市、州／省，缺少独立的第三级行政区字段。本轮将其误填成完整地址 | 最值得新增的可选字段；修复错误回填不应等待扩展 |
| 工作／家庭联系方式 | 每份地址／证件有一组电话和邮箱，可建立多条资料；没有在同一资料中关联 `work` / `home` 的结构化角色 | 有需求再增加联系信息分组；先修复语义和页面分区，避免无条件填满两组 |
| 地址资料中的分段姓名和第三地址行 | `BillingAddressData` 只有 fullName、streetAddress、apartment；**DocumentData 已有 firstName / middleName / lastName 和 address1 / address2 / address3** | 可决定是否统一地址与证件的编辑能力；证件中已有字段应先修好映射 |
| 银行卡持卡人姓名的分段 | 卡片有完整 cardholderName，缺独立的持卡人姓／中间名／名；证件姓名不能假定与持卡人相同 | 可选高级字段。若不新增，拆名策略需避免假定所有文化姓名都可按空格可靠拆分 |

不需要新增保存字段就能改进的部分：

- 国家全称 `country-name` 可以从国家代码和所需语言解析；不是必须再保存一个互相可能不一致的国家字段。
- 电话国家码、区号、本地号码及分段应从已有电话与地区上下文解析；不能把姓名或邮箱填进去，也不宜简单按固定长度切割国际号码。
- `cc-exp` 可由现有月份／年份组合，标准 `cc-name` 应读取现有持卡人姓名。
- 邮箱、完整电话、证件中间名、证件第三行地址已经可以保存，属于修复范围。

Relay 页并不证明存在或缺少 Firefox Relay 服务集成：普通邮箱字符串已经可保存；自动生成／管理转发别名是另一个服务功能。本轮没有将该页“Fill Form”按钮注入的示例值算成 Monica 的结果。

## 限制与清理

- 站点名字里的“All supported autocomplete”不是整个 HTML 标准字段全集。本站没有提供的生日、性别等控件未测，也没有据此列为 Monica 缺少的保存功能。
- 站点 HTTP Basic Authentication 链接明确标注 GitHub Pages 不支持，无法在这里验证。本轮没有进行外部账户注册、真实凭据提交，或验证网站后端登录成功。
- 本轮评估现有资料回填及数据模型；不把它描述成所有资料类型“网页提交后自动保存”的完整持久化测试。不同厂商系统、其他浏览器、F-Droid APK、原生 App、OTP／Passkey 不是本轮站点实测范围。
- 最初测试驱动存在软键盘遮挡导致触摸未命中的情况。修正了坐标计算前收起键盘，并复测受影响的动态补填用例；最终摘要不保留那次误判。已有卡号框没有建议的用例正确取得了焦点，并有服务拒绝单字段请求的日志，属于 A6。
- 测试 harness 只在 AndroidTest APK 中，普通测试未显式启用时跳过。`assembleDebugAndroidTest -PincludeX86TestAbi --offline --max-workers=2` 成功；两个交互会话均正常结束并执行清理。它们的 `OK (1 test)` 仅代表会话／清理完成，不代表网页用例全部通过。另验证无 opt-in 参数时状态为 `-4`（跳过）。
- 已删除本轮合成密码和附属资料；第二会话恢复了生成历史快照。Android 自动填充恢复 Google，Chrome 恢复“Autofill with Google”并重启、重新检查选中状态；输入法保持 Gboard，返回用户 0，移除两个 adb 转发并停止本任务启动的公共 AVD。保留 AVD 配置和数据盘。
- 截图、网页原始快照和详细运行日志仅留在本地任务目录 `.codex-tasks/mozilla-autofill-320/`。本报告及 JSON 摘要不依赖截图链接。
- 本次没有新增产品功能，所以未将审计或尚未修复的问题写成发行说明中的“已修复”。版本仍为 1.0.320。

## 重现入口

构建并安装 AndroidTest APK，在专用合成数据用户中启用 Android Monica 自动填充和 Chrome 的其他提供方模式后运行：

```text
adb shell am instrument --user 10 -w -r
  -e autofillIsolatedUser true -e mozillaAudit true
  -e class takagi.ru.monica.autofill_ng.MozillaAutofillAuditSessionTest
  takagi.ru.monica.test/androidx.test.runner.AndroidJUnitRunner
```

以上分行仅用于阅读，实际命令需合并一行。harness 监听设备回环端口 37915，接受换行分隔 JSON 的 `dump`、`refresh`、`auth`、`done`；最后必须发送 `{"op":"done"}`，以删除资料并恢复设置／生成历史。100 分钟未结束会报失败并进入清理。网页端可按表中入口手动重现；本轮主机驱动和逐字段快照保存在本地任务目录。
