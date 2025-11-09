# 自动保存设备兼容性实现任务

## 任务列表

- [x] 1. 创建 SaveInfoBuilder 核心组件




  - 创建 `SaveInfoBuilder.kt` 文件实现设备特定的 SaveInfo 构建逻辑
  - 实现 `build()` 方法根据设备信息构建 SaveInfo
  - 实现 `getDeviceSpecificFlags()` 方法返回设备特定标志
  - 实现 `collectSaveFieldIds()` 方法收集需要保存的字段 ID
  - 实现 `validateSaveInfo()` 方法验证 SaveInfo 配置有效性
  - _需求: 2.1, 2.2, 2.3, 2.4, 4.1, 4.2, 4.3, 4.4, 4.5_

- [x] 2. 扩展 DeviceInfo 数据类


  - 在 `DeviceUtils.kt` 中创建 `DeviceInfo` 数据类
  - 实现 `supportsDelayedSavePrompt()` 方法判断设备是否支持延迟保存
  - 实现 `getRecommendedSaveFlags()` 方法返回推荐的 SaveInfo 标志
  - 实现 `needsCustomSaveUI()` 方法判断是否需要自定义保存 UI
  - 添加工厂方法 `DeviceInfo.fromDevice()` 创建当前设备的 DeviceInfo 实例
  - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2_

- [x] 3. 修复 buildFillResponseEnhanced 中的 SaveInfo 配置



  - 在 `MonicaAutofillService.kt` 的 `buildFillResponseEnhanced()` 方法中添加 SaveInfo 配置
  - 使用 SaveInfoBuilder 构建设备适配的 SaveInfo
  - 确保字段 ID 数组不为空
  - 添加日志记录 SaveInfo 配置详情
  - _需求: 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 4. 修复 buildStandardResponse 中的 SaveInfo 配置

  - 在 `MonicaAutofillService.kt` 的 `buildStandardResponse()` 方法中使用 SaveInfoBuilder
  - 替换硬编码的标志为设备特定标志
  - 使用 `parsedStructure` 收集字段 ID 而不是 `fieldCollection`
  - 添加字段 ID 验证，确保不为空
  - _需求: 2.1, 2.2, 2.3, 2.4, 4.1, 4.2_

- [x] 5. 创建 SaveRequestProcessor 类



  - 创建 `SaveRequestProcessor.kt` 文件
  - 实现 `process()` 方法处理保存请求
  - 实现 `extractFormData()` 方法提取表单数据
  - 实现 `validateData()` 方法验证数据有效性
  - 实现 `checkDuplicate()` 方法检查重复密码
  - 实现 `createSaveIntent()` 方法创建保存 Intent
  - 定义 `ProcessResult` 密封类表示处理结果
  - _需求: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 6. 重构 MonicaAutofillService 的 onSaveRequest


  - 在 `MonicaAutofillService.kt` 中使用 SaveRequestProcessor 替代现有逻辑
  - 简化 `onSaveRequest()` 方法，委托给 SaveRequestProcessor
  - 移除 `processSaveRequest()` 方法中的重复逻辑
  - 添加错误处理和恢复策略
  - _需求: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 7. 扩展 AutofillDiagnostics 添加保存功能诊断


  - 在 `AutofillDiagnostics.kt` 中添加 `logSaveRequest()` 方法
  - 添加 `logSaveResult()` 方法记录保存结果
  - 添加 `logSaveInfoConfig()` 方法记录 SaveInfo 配置
  - 添加 `getSaveStatistics()` 方法获取保存统计信息
  - 创建 `SaveStatistics` 数据类
  - _需求: 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 8. 在关键位置添加诊断日志

  - 在 SaveInfoBuilder 中添加日志记录标志选择
  - 在 buildFillResponseEnhanced 中记录 SaveInfo 配置
  - 在 onSaveRequest 中记录保存请求详情
  - 在 SaveRequestProcessor 中记录处理结果
  - 确保日志不包含敏感信息（密码掩码处理）
  - _需求: 7.1, 7.2, 7.3, 7.4_

- [x] 9. 更新 AutofillSettingsScreen 添加保存功能设置



  - 在 `AutofillSettingsScreen.kt` 中添加"启用自动保存"开关
  - 添加"自动更新重复密码"开关
  - 添加"显示保存通知"开关
  - 添加"智能标题生成"开关
  - 确保设置变更立即生效
  - _需求: 8.1, 8.2, 8.3, 8.4, 8.5_

- [ ] 10. 创建设备兼容性测试工具
  - 创建 `SaveCompatibilityTest.kt` 测试类
  - 实现测试方法验证各设备的标志配置
  - 实现测试方法验证字段 ID 收集
  - 实现测试方法验证 SaveInfo 构建
  - 添加模拟不同设备的测试用例
  - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 4.1, 4.2_

- [ ]* 11. 编写单元测试
- [ ]* 11.1 为 SaveInfoBuilder 编写单元测试
  - 测试不同设备的标志配置
  - 测试字段 ID 收集
  - 测试 SaveInfo 验证
  - _需求: 2.1, 2.2, 2.3, 2.4, 4.1, 4.2_

- [ ]* 11.2 为 DeviceInfo 编写单元测试
  - 测试设备检测逻辑
  - 测试推荐标志获取
  - 测试延迟保存支持检测
  - _需求: 3.1, 3.2, 3.3, 3.4, 3.5_

- [ ]* 11.3 为 SaveRequestProcessor 编写单元测试
  - 测试表单数据提取
  - 测试数据验证
  - 测试重复检测
  - 测试 Intent 创建
  - _需求: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 12. 更新诊断报告 UI
  - 在 `TroubleshootDialog.kt` 中添加保存功能统计显示
  - 显示 SaveInfo 配置信息
  - 显示设备兼容性建议
  - 显示最近的保存请求历史
  - 添加"测试保存功能"按钮
  - _需求: 7.5_

- [ ] 13. 添加错误恢复机制
  - 在 SaveInfoBuilder 中实现标志降级策略
  - 在 SaveRequestProcessor 中实现重试逻辑
  - 添加错误通知机制
  - 记录错误到诊断系统
  - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ] 14. 优化性能
  - 缓存 DeviceInfo 实例避免重复检测
  - 优化字段 ID 收集算法
  - 使用协程优化异步处理
  - 添加性能监控日志
  - _需求: 7.1, 7.2, 7.3_

- [ ] 15. 更新文档
  - 更新 `troubleshooting-guide.md` 添加保存功能故障排查
  - 更新 `device-specific-guides.md` 添加保存功能设备特定说明
  - 更新 `faq.md` 添加保存功能常见问题
  - 创建 `SAVE_FEATURE_GUIDE.md` 详细说明保存功能
  - _需求: 所有需求_

## 测试验证清单

完成实现后，需要在以下设备上验证：

- [ ] 原生 Android 虚拟机（API 30+）
- [ ] 小米设备（MIUI 12+ 或 HyperOS）
- [ ] 华为设备（EMUI 10+ 或 HarmonyOS）
- [ ] OPPO 设备（ColorOS 11+）
- [ ] Vivo 设备（OriginOS 或 Funtouch OS）
- [ ] 三星设备（One UI 3+）

测试场景：
- [ ] 新账号注册（新密码场景）
- [ ] 登录表单提交（标准场景）
- [ ] 密码修改（更新场景）
- [ ] 重复密码检测
- [ ] 保存 UI 取消操作
- [ ] 保存 UI 确认操作
- [ ] 诊断日志验证

## 实现顺序建议

1. **Phase 1（核心修复）**: 任务 1, 2, 3, 4
2. **Phase 2（重构和增强）**: 任务 5, 6, 7, 8
3. **Phase 3（UI 和诊断）**: 任务 9, 12
4. **Phase 4（测试和优化）**: 任务 10, 11, 13, 14
5. **Phase 5（文档）**: 任务 15

## 注意事项

- 所有代码修改需要保持向后兼容性
- 添加充分的日志但不记录敏感信息
- 确保错误不会导致应用崩溃
- 性能优化不应影响功能正确性
- 测试覆盖主流设备品牌
