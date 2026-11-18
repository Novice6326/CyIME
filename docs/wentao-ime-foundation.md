# 文涛输入法：Rime 底座迁移与特色功能计划

## 底座选择

当前工作分支以用户账号下的 CyIME/Rime 工程作为技术底座候选。该工程使用 librime，已有拼音、九键、手写、离线语音、剪贴板历史、主题与大量 Android IME 适配测试。它比继续扩写早期自研拼音原型更适合承载完整输入法。

CyIME 上游采用 GPL-3.0。基于该代码分发的文涛输入法版本必须遵守 GPL-3.0，包括提供对应完整源码、保留版权与许可证声明、标注修改内容；不能把 GPL 代码包装成闭源产品。模型、词库和第三方依赖需分别保留各自许可证与 NOTICE。

## 不直接照搬的原则

- 先审查代码、依赖、许可证和构建链，再迁移；不把整个大项目盲目复制到旧的轻量原型。
- 保留文涛输入法蓝色光感 Logo 与品牌识别。
- 保留并重新集成文涛输入法的剪贴板逐字输入特色功能；普通粘贴和逐字输入必须是两个独立操作。
- 语音结果必须通过当前 IME 服务持有的当前 InputConnection 提交；不能跳转到输入法设置/主界面，也不能使用过期连接。
- 对每次逐字提交重新校验目标应用、输入框身份与连接状态；切换输入框时暂停，不能把剩余文本泄漏到另一个字段。
- 逐字速度预设为 9 档（6,000–30,000 字/小时），并支持自定义速度、随机间隔、标点停顿、暂停/继续/停止、进度与安全断点恢复。自定义输入值不设预设上限，但调度间隔最低 1ms，实际输出速度受设备调度精度约束。
- 逐字单位按扩展字素簇近似拆分，不能拆开代理对、组合字符、旗帜、ZWJ emoji、韩文 Jamo 连写；当前实现另覆盖常见 Indic conjunct 与 Prepend 字符，但尚未声明完整符合 Unicode UAX #29。

## 第一阶段状态

- [x] 审查 Rime/librime 底座候选及现有能力。
- [x] 新增独立的逐字速度模型：9 档、无限预设上限的自定义速度、随机抖动接口、标点停顿规则。
- [x] 新增扩展字素簇拆分器及 JVM 单元测试。
- [x] 将速度/文本模型接入 Android IME 当前 InputConnection，逐次提交前校验当前 EditorInfo。
- [x] 接入工具栏开始/暂停/继续/停止入口；增加 9 档速度、自定义速度、随机间隔开关和候选栏上方实时进度。
- [x] 实现私有 SharedPreferences 任务持久化和断点恢复；按 packageName、fieldId、inputType、fieldName 及 EditorInfo 操作/提示元数据匹配，恢复后保持暂停；无有效正数 fieldId 时拒绝跨服务恢复。
- [ ] 完成 Android 工程编译、完整单元测试与真机多应用输入框验收。

当前分支已写入速度模型、字素簇拆分、IME 控制器、目标输入框保护、断点恢复、速度设置和进度 UI。本轮尚未取得 Android 工程构建与完整测试通过的 CI 证据；此前对纯 Kotlin 逻辑的独立检查不能替代 Gradle 工程编译。当前只能标记为“代码已接入、待构建验收”，不能视为已完成交付。


## 后续安全修正（2026-11-18）

- [x] 输入法服务收到 `onFinishInput` 时立即暂停逐字任务，避免编辑会话结束后仍继续提交。
- [x] 输入法服务销毁时移除逐字调度回调并保存断点，避免 Handler 回调持有已销毁服务。
- [x] 修正高速自定义档位的有效最小调度间隔，并修复换行/CRLF 测试字面量，避免测试实际验证成反斜杠字符。
- [ ] 仍需通过 Gradle 单测、Android instrumentation 和真机多应用测试确认以上行为。


## Unicode 字符边界补充（2026-11-18）

- [x] 增加 Unicode Prepend（GB9b）常见范围处理。
- [x] 增加 Indic conjunct（GB9c）轻量处理：使用 Unicode 18.0 的 InCB Linker 代码点清单，并结合 Indic 脚本字母范围识别连写辅音。
- [x] 将 ZWNJ 纳入 Extend 类边界处理。
- [x] 增加 Devanagari conjunct、组合标记、Prepend 和 ZWNJ 回归测试。
- [ ] 仍需引入官方 GraphemeBreakTest.txt / DerivedCoreProperties.txt 自动化一致性测试，评估完整 UAX #29 覆盖率；当前实现是轻量近似，不应标记为 Unicode 全量合规。


## 输入框身份保护补充（2026-11-18）

- [x] 修正 `onStartInput` 处理：只有 Android 明确标记为 restarting 且 EditorInfo 指纹匹配时，才保留当前内存任务；普通新输入会话即使包名/控件 ID/输入类型碰巧相同，也会清除旧任务。
- [x] 断点指纹增加 imeOptions、privateImeOptions、actionId、actionLabel、hintText 和 label，减少同应用不同输入框误匹配。
- [x] 跨服务/进程恢复要求 fieldId 为正数；系统未提供有效 View ID 时宁可放弃跨服务恢复，也不使用包名和输入类型猜测目标。
- [ ] 仍需在不同应用、Compose 输入框、无 ID 输入框、同布局 ID 复用场景验证恢复策略；EditorInfo 不是全局唯一控件句柄，持久化指纹只能降低误匹配，不能证明所有应用都唯一。


## 断点恢复安全边界补充（2026-11-18）

- [x] 持久化断点只有在 Android 明确报告 restarting=true、fieldId 为正数且完整 EditorInfo 指纹匹配时才恢复。
- [x] 普通新输入会话不恢复磁盘断点，即使 packageName、fieldId、inputType 等字段碰巧相同也清除，避免跨输入框误续写。
- [x] 恢复后仍处于暂停状态，必须由用户手动继续。
- [ ] 因安全优先，进程被系统杀死后若 Android 未报告同一编辑器重启，断点可能被丢弃；不承诺跨进程、跨应用页面的无条件恢复。后续若要支持，需要单独设计带目标信息展示和用户二次确认的恢复流程。


## Unicode 18.0 控制字符边界补充（2026-11-18）

- [x] 增加 GCB=Control 属性识别，并遵守 GB3 优先级：CRLF 仍作为一个单位。
- [x] 遵守 GB4/GB5：CR、LF 和其他 Control 字符前后强制断开，组合标记不能错误附着在控制字符后。
- [x] Prepend 属性与 Unicode 18.0 GraphemeBreakProperty.txt 的 27 个码点核对。
- [x] Indic InCB=Consonant 使用 Unicode 18.0 DerivedCoreProperties.txt 的精确区间；InCB=Linker 清单与官方 23 个码点核对。
- [x] 增加 ZWJ 后任意普通字母不能被误并入 Emoji ZWJ 序列的测试。
- [ ] Extended_Pictographic 仍为精简范围表，完整 emoji-data.txt 属性和官方 GraphemeBreakTest.txt 全量一致性尚未完成。
- [ ] Android Gradle 单测、Instrumentation 和多应用真机测试尚未获得通过记录。
