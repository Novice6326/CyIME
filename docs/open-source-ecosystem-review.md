# 文涛输入法：开源项目生态选型与整合计划

更新：2026-11-18

## 结论

不再把 CyIME 当成唯一参考或唯一可复用来源。采用“现有 Android IME 主干 + 可独立替换的引擎/模型/词库/交互模块”的组合路线。优先复用成熟实现和数据，不为追求项目数量而重复塞入多个互相冲突的输入法前端。

当前开发主线仍是 Novice6326/CyIME 的文涛分支；本文件是技术选型，不代表下列项目代码已经全部合并或通过构建。

## 第一批重点来源

| 来源 | 适合复用的部分 | 处理方式 | 许可证/注意事项 |
|---|---|---|---|
| [Xime](https://github.com/ximeiorg/Xime) | 当前 Android IME/Rime 主干、既有模型和设置体系 | 继续作为当前工程基础；逐模块审计 | GPL-3.0 |
| [CyIME](https://github.com/Cyletix/CyIME) | 手写、离线语音、OCR、多模态输入的现成集成经验 | 作为功能参考与当前分支基础，不排斥其他来源 | GPL-3.0；逐项检查 NOTICE 和模型/数据许可 |
| [语燕 YuyanIme](https://github.com/gurecn/YuyanIme) | 九宫格、双拼、键盘数字行、单手/悬浮、设置交互、Rime 词库扩展 | 优先对照其 Kotlin 实现，挑选可独立移植模块 | 仓库 LICENSE 为 BSD 风格自定义条款；保留版权/许可/免责声明，不能用作者名背书 |
| [Feelime](https://github.com/feelime/feelime) | 双通道语音：流式 Zipformer + Paraformer 整句复核、标点恢复；HTML/CSS/JS 键盘视觉和自动化测试 | 重点研究语音链路、延迟/纠错策略和测试用例；不整套替换当前 UI | GPL-3.0；模型、词库和第三方依赖分别核对 |
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Android 离线 ASR/TTS/VAD/说话人分离运行库和模型接口 | 作为语音引擎候选，优先复用官方 Android AAR/API 和校验过的模型 | Apache-2.0；每个预训练模型仍须单独核对许可 |
| [雾凇拼音 rime-ice](https://github.com/iDvel/rime-ice) | 全拼、常见双拼、精校中文词库、英文词库、模糊音和配置 | 作为中文词库/方案的主要候选，先做词条来源和冲突审计，再增量接入 | GPL-3.0；词库中引用的其他数据仍按其上游许可 |
| [OpenCC](https://github.com/BYVoid/OpenCC) | 简繁转换、地区词转换 | 作为可选转换库接入，增加简繁/港台用词测试 | Apache-2.0 |
| [Fcitx5 Android](https://github.com/fcitx5-android/fcitx5-android) | 插件化输入法架构、候选栏、主题、符号/Emoji 面板、引擎边界 | 借鉴插件接口与引擎解耦；不把整套前端与当前 Rime 前端叠加 | LGPL-2.1；插件及子模块各自核对 |
| [HeliBoard](https://github.com/HeliBorg/HeliBoard) | 拉丁文键盘、字典建议、拼写检查、Emoji/科学符号建议、隐私交互 | 作为英文输入、词典建议和无障碍交互参考 | GPL-3.0 |
| [FlorisBoard](https://github.com/florisboard/florisboard) | Compose UI、主题系统、布局与输入交互 | 只抽取可独立复用的设计/交互思路，不移植整套键盘状态机 | Apache-2.0 |
| [Ismantic/Handwritten](https://github.com/Ismantic/Handwritten) | CASIA HWDB1.1 中文手写识别、MobileNetV2/NCNN INT8 模型 | 作为独立手写模型候选，必须先确认仓库实际 LICENSE、训练数据授权和模型文件来源，再决定是否纳入 | 许可尚未完成核验，暂不打包 |

## 按功能的落地顺序

### 1. 中文输入与词库
- 保留当前 Rime/librime 作为中文输入核心，避免重复造拼音解析和候选排序引擎。
- 评估雾凇拼音的全拼、双拼、模糊音、词频与英文词库，生成可追溯的词库来源清单。
- 对比语燕九宫格方案和现有九键模型；按真实测试集的整句准确率、首候选命中率、内存占用和延迟选择，不凭项目介绍直接替换。
- OpenCC 用于简繁及地区词转换，不把字符转换误当成翻译。

### 2. 语音输入
- 以 sherpa-onnx 为底层运行库候选。
- 重点复用 Feelime 的“两遍识别”设计：第一遍流式返回临时结果，第二遍在停顿后用更强模型纠错，最后恢复标点、大小写和缩写。
- 对照当前工程语音模块，避免重复引入多个 ASR runtime；比较模型大小、冷启动、实时因子、端侧内存和中文/英文混输准确率。
- 所有语音结果必须经当前 IME InputConnection 写入当前焦点输入框；输入框切换、权限拒绝、服务不可用都要有明确处理。

### 3. 手写输入
- 先盘点当前工程已有手写模型及 ONNX Runtime/NCNN 依赖。
- 将 Ismantic/Handwritten 作为对照基准，评估模型体积、字表覆盖、Top-k 准确率、连续书写分割能力和设备性能。
- 仅在许可证与训练数据授权核验通过后才整合模型；不把单字识别准确率误认为连续手写体验已经达标。

### 4. 键盘 UI、英文与工具面板
- 语燕：参考九宫格、数字行、单手和悬浮模式。
- FlorisBoard：参考 Compose 主题与布局组织。
- HeliBoard：参考英文词典建议、拼写检查、Emoji/符号搜索。
- Fcitx5：参考插件接口和引擎解耦。
- 不整套合并多个键盘前端，避免重复服务、输入连接争用、状态同步和包体膨胀。

### 5. 文涛特色功能
- 逐字输入继续作为自研差异化功能，不被第三方输入方案覆盖。
- 速度档位、自定义速度、自然波动、标点停顿、进度、暂停/继续/停止、断点恢复都要保留。
- 逐字提交前持续验证 packageName、fieldId、inputType、fieldName；目标变化立即暂停，禁止把剩余文本写入其他应用或密码框。

## 整合准入门槛

任何外部代码、词库、模型进入正式包之前，必须记录：
1. 上游仓库、固定 commit/tag、下载地址和 SHA-256。
2. 代码许可证、词库许可证、训练数据许可证、模型许可证分别是什么。
3. 修改记录、NOTICE/版权声明和再分发义务。
4. Android ABI、最低系统版本、依赖冲突、APK/AAB 体积影响。
5. 离线能力、隐私权限、崩溃/耗电/内存和性能数据。
6. 单元测试、集成测试和真机回归结果。

## 明确不采用的做法

- 不因 Star 多就整仓复制。
- 不把两个完整 IME 前端直接叠加。
- 不下载来源不明或许可证不清楚的模型/词库。
- 不把“仓库开源”推断为其中所有训练数据、语音模型、字体和词库都可自由再分发。
- 不在没有构建与真机测试时宣称功能已经完成。

## 本轮已完成的调查

已检查主要候选项目的 README 和顶层 LICENSE：YuyanIme、Feelime、Fcitx5 Android、HeliBoard、sherpa-onnx、rime-ice、OpenCC、FlorisBoard。手写模型 Ismantic/Handwritten 仅作为候选，许可仍待人工核验。下一步应先做代码结构和依赖级别的差异审计，再逐项落地，不应一次性把所有仓库合并。


---

## 第二轮：对当前开发分支进行源码级差异审计（2026-11-18）

本轮检查了当前分支的 Gradle 依赖、资源目录、语音模型目录、Rime 词库 NOTICE、手写服务实现及键盘布局实现。结论修正第一轮的初步判断：多项“候选项目”对应的能力已经在当前工程中存在，不能再按“缺失功能”直接引入。

### 当前工程已经具备的能力（不重复移植）

| 能力 | 当前工程证据 | 决策 |
|---|---|---|
| Rime + 雾凇拼音 | `app/src/main/assets/rime_chinese/NOTICE.md` 明确记录固定上游提交 `9e66b0729083b37d217312294f6d516c8d7234be`；`tasks-chinese.gradle.kts` 负责构建接入 | 已集成。以后只做词库版本比较、差异词条评测，不再重复拷贝 |
| sherpa-onnx | `tasks-speech.gradle.kts` 固定 1.13.8 AAR 与 SHA-256；已有本地模型管理、VAD、流式识别 | 已集成。升级前必须做 JNI/API 兼容和模型回归 |
| 中文语音二次定稿 | `SpeechModelSelection`、`SherpaSpeechEngine`、`SecondPassGate` 已实现流式预览、SenseVoice/X-ASR 定稿及候选保护 | 已有成熟方案。Feelime 的双遍架构可作对照，不应直接覆盖 |
| 语音模型完整性 | `SpeechModelCatalog` 固定模型 URL、版本、文件 SHA-256 和下载镜像 | 已有校验；评估外部 ModelStore 时重点比较原子安装、损坏恢复和来源切换 |
| 手写识别 | `HandwritingEngine` 通过独立 `:inference` 进程加载 OCHWPro ONNX 模型，支持个人模型替换；有字表与笔画处理代码 | 已有端侧实现。Ismantic 模型只作离线基准，不直接替换 |
| 悬浮键盘 | `FloatingKeyboardContainer`、拖动/缩放/停靠状态机及多项 UI/设备测试 | 已有实现。只对照 Yuyan 的体验细节，不整套移植 |
| 主题 | `KeyboardPalette`、`ThemeSettingsScreen` 及主题测试 | 已有实现。FlorisBoard 只作视觉/主题结构参考 |
| Emoji/剪贴板 | Emoji 面板、剪贴板面板及导航测试已存在 | 不引入重复面板 |
| 插件 | 当前工程有 ExtensionManager、插件市场、ASR 后端适配和插件服务 | 已有插件体系。Fcitx5 只对照扩展隔离和生命周期，不移植整套框架 |

### 外部项目的实际增量价值

#### Feelime（当前优先级：高，限语音算法和键盘桥接审计）

已核对 Feelime 上游提交：`972757a6667fe795e80db2aff2e4bc48cc1e142b`（2026-11-17）。

可重点比较：
- 两遍识别：流式 Zipformer 先给预览，停顿后 Paraformer 整句重解码。
- 语音文本后处理：中文标点、英文大小写与缩写恢复。
- HTML/CSS/JS 键盘经 token-gated JS Bridge 调用 Android InputConnection 的安全边界。
- 模型按需下载、校验后安装、来源切换和损坏恢复。

当前工程已有流式预览 + SenseVoice/X-ASR 定稿和 SecondPassGate 语言模型保护。下一步应拿同一套中文/中英混输音频集做 CER、标点准确率、首字延迟、停顿定稿耗时和内存比较；只有 Feelime 在具体指标上胜出，才移植其独立后处理算法。不能仅凭“二次识别”概念判断其优于现有方案。

上游： https://github.com/feelime/feelime

#### 语燕 YuyanIme（当前优先级：中高，限键盘体验和手写对照）

上游 Gitee 发布页显示最新标签 v20261105.22，发布于 2026-11-06；更新涉及物理键盘候选栏、九键/中英混输控制、手写引擎和键盘菜单编辑等。其公开发布说明标注 BSD-3-Clause，但此前 GitHub 搜索出现的是镜像/个人 fork，后续移植必须以官方上游源代码与该发布包对应的 tag 为准，不能把 fork 当作官方版本。

可重点比较：九键候选排序、手写笔迹处理、键盘切换稳定性、符号侧栏、主题和设置交互。当前工程已具备悬浮/主题/剪贴板/Emoji 等基础能力，只移植经 A/B 测试证明更好的独立模块。

上游发布页：https://gitee.com/gurecn/YuyanIme/releases

#### Fcitx5 Android（当前优先级：中，限插件架构）

已具备自己的插件体系，不需要引入第二套 IME 服务和键盘 UI。只比较插件生命周期、外部引擎隔离、配置导入导出及物理键盘支持；若当前实现已经满足需求，不做代码移植。

上游：https://github.com/fcitx5-android/fcitx5-android

#### HeliBoard / FlorisBoard（当前优先级：中低，限局部交互）

当前工程已经有 Compose 键盘和主题系统。只参考英文拼写/词典建议、Emoji 搜索、无障碍语义、主题 token 和键盘尺寸管理。避免移植整套前端。

### 本轮关键风险发现

1. 当前工程的 sherpa-onnx AAR 是 1.13.8，而上游 Feelime 的语音实现也依赖 sherpa-onnx。不能把两个 AAR 或 ONNX Runtime 重复打包；任何升级要统一 runtime 版本并检查所有 JNI ABI。
2. 当前 Rime 中文 NOTICE 已固定雾凇上游提交。外部词库只能通过受控的生成脚本接入，并保留上游 LICENSE、NOTICE 和源版本；不能手工覆盖用户词库或 schema。
3. 当前手写模型通过独立 inference 进程隔离，换模型需验证 IPC 协议、字表映射、内存峰值、冷启动和进程回收恢复。不能只比较单字 Top-1。
4. 当前键盘使用 Compose 组件与状态机。Feelime 的 WebView/JS Bridge 与 Compose 是不同前端架构；不把两套键盘前端混合运行。
5. 上游项目更新频繁，必须固定 commit/tag 和 SHA-256，不能直接追踪 main/master 并在用户构建时下载不固定内容。

### 下一步可执行工作

1. 建立 Feelime 与当前语音实现的统一评测样本集和指标脚本；优先对比整句纠错、标点、英文缩写、静音切分和低音量识别。
2. 对照 YuyanIme 官方 v20261105.22 源码，做九键候选、手写、符号面板、物理键盘的逐项差异清单。
3. 检查当前手写模型的模型卡、训练数据和授权，再决定是否用 Ismantic 作为 benchmark；在授权核验前不合并。
4. 审计当前 IME 的所有语音/逐字输入路径是否始终通过当前 InputConnection，并对跨应用、焦点变化、密码框、权限拒绝和服务不可用做测试。
5. 完成上述差异评测后再挑选性移植，禁止为了“用了更多开源项目”而增加依赖。

本轮没有将外部项目代码直接复制进主工程，因为源码审计发现多数能力已经存在；这是有意避免重复架构和依赖冲突，不代表停止整合开源项目。


### 当前输入框安全链路的补充核验

本轮直接检查了 `VoiceTarget.kt`、`VoiceRecognitionHandler.kt`、`XimeInputMethodService.kt` 和 `VoiceTargetTest.kt`：

- 语音开始时 `VoiceTarget.begin()` 固定本次目标：若焦点在键盘内部输入框，就固定该内部输入框；否则使用宿主应用输入连接。
- 内部输入框连接失效时返回 null，不会自动回退写入宿主应用输入框。
- 部分结果和最终结果均先经过 `acceptsInputSession()`，会话 ID / 语言状态不匹配时放弃旧结果。
- 已有 `VoiceTargetTest` 覆盖翻译栏输入、焦点中途移动、隔空输入、内部连接不可用时不回退到宿主输入框，以及下一次会话重新选择目标。
- 本轮另修正逐字输入生命周期：`onFinishInput` 与 `onDestroy` 都会暂停调度并保留断点；仍需真机验证系统不同输入法生命周期回调顺序。

因此，当前语音输入并非完全没有目标保护；后续应以现有实现和测试为基准做补充测试，不应重复造一套语音目标路由。


### 第三轮补充：许可证交叉核对与逐字字符边界（2026-11-18）

#### Feelime 许可证必须以当前 LICENSE 文件为准

本轮直接读取 Feelime 当前 `main` 分支的 `LICENSE` 和 `README.md`：
- 当前顶层 `LICENSE` 正文是 GNU GPL version 3。
- 当前 README 明确写明“1.0.19 及之前的历史版本以 MIT 发布”，当前版本使用 GPL-3.0。
- 部分 GitHub 页面摘要仍显示 MIT，不能以摘要覆盖当前许可证文件。

结论：当前 Feelime 代码按 GPL-3.0 进行合规审查；若只研究历史 MIT 版本，必须固定到 1.0.19 或更早的具体 tag/commit，并核对该版本源码、词库、模型和 NOTICE。当前模型权重、词库及第三方模型依然必须分别核验授权，不能因源码 GPL 就推断模型也 GPL 或可再分发。

当前工程主项目也是 GPL-3.0，但这不代表可以忽略上游版权、修改标记、许可证副本、第三方模型和数据授权。当前不直接复制 Feelime 的 ASR 实现：现有工程已有自己的 streaming + finalization + SecondPassGate 流程，必须先用同一套音频数据对比 CER、首字延迟、停顿定稿时间和端侧内存。

#### YuyanIme 顶层许可证已核实

当前 `gurecn/YuyanIme` 的 `master:LICENSE` 是 BSD 3-Clause 风格条款，包含保留版权/许可/免责声明以及不得未经许可使用作者或贡献者名称背书等要求。移植代码时保留原始 LICENSE 文本与版权声明，不仅仅在 NOTICE 中写“BSD”。

#### 逐字输入：补齐 Hangul Jamo 字素簇边界

源码审查发现 `WentaoTypingText` 已处理代理对、组合标记、变体选择符、肤色修饰符、ZWJ emoji、国旗、标签字符和 CRLF，但此前没有处理韩文分解式 Hangul Jamo 的 GB6–GB8 连写规则。已增加 Hangul L/V/T、LV/LVT 组合判断，并新增测试：
- `한` 必须作为一个用户感知字符提交；
- `❤️`、`1️⃣` 等 Emoji 变体和键帽序列不得拆开；
- 原有家庭 ZWJ Emoji、肤色修饰符、国旗和 CRLF 测试继续保留。

这仍是轻量级字素簇分割实现，不宣称已经覆盖 Unicode UAX #29 的全部规则；后续还需增加印度文字 virama/conjunct、prepend 字符等测试。不得将“常见 emoji 测试通过”描述为“完整 Unicode 字素算法已通过”。

#### 构建与 CI 现状

当前分支有 `.github/workflows/build.yml`（push 到 main/dev* 会触发构建）和 `.github/workflows/test.yml`（手动触发完整单测，可选 instrumentation）。但查询当前分支 Actions runs 未返回任何运行记录，因此本轮不能报告 Gradle 构建或测试通过。build.yml 中单元测试步骤当前设置为 `continue-on-error: true`，即使失败也不会阻断后续 APK 构建；完整验证应以 test.yml 的单测结果和可选设备测试结果为准。


### 第四轮补充：Unicode 18.0 字素边界审计（2026-11-18）

本轮依据 Unicode Consortium 的 UAX #29 与 Unicode 18.0.0 DerivedCoreProperties 数据核对扩展字素簇规则。UAX #29 明确区分 legacy 与 extended grapheme clusters；一般文本处理推荐 extended 规则，其中包含 GB9a（SpacingMark）、GB9b（Prepend）和 GB9c（Indic conjunct）。参考：
- https://www.unicode.org/reports/tr29/
- https://www.unicode.org/Public/18.0.0/ucd/DerivedCoreProperties.txt

已在 WentaoTypingText 中补充：
- GB9b 常见 Prepend 代码点范围。
- GB9c 常见 Indic conjunct 连写：按 Unicode InCB Linker 代码点清单识别 linker，再结合 Indic 脚本字母范围近似判断前后辅音。
- 将 ZWNJ 纳入 Extend 处理。
- 对 Hangul、Indic conjunct、Prepend、ZWNJ 增加回归用例；并保留 Emoji、组合音标、CRLF 测试。

边界声明：当前仍是轻量级近似实现。Indic consonant 判定使用脚本区段和字母类别，不是完整 Unicode InCB 属性表；Prepend 范围也需要持续与 UCD 对照。尚未导入官方 GraphemeBreakTest.txt 做全量一致性测试，因此不能称为完整 UAX #29 合规。当前 Android 工程编译、Gradle 单测与真机测试仍未获得通过证据。


### 第五轮补充：逐字输入目标身份防串写（2026-11-18）

源码审查发现旧的会话保留条件存在边界：不同输入框可能复用相同 packageName、fieldId 和 inputType；旧逻辑在 restarting=false 但 EditorInfo 指纹相同时会暂停并保留任务，之后仍可能被手动恢复到新字段。

已修正：
- 只有 restarting=true 且完整 EditorInfo 指纹匹配，才保留当前内存任务。
- 普通新输入会话无条件停止并清除旧任务，不再因 EditorInfo 字段碰巧相同而保留。
- 持久化指纹增加 imeOptions、privateImeOptions、actionId、actionLabel、hintText 和 label。
- 跨服务恢复要求 fieldId > 0；无有效 View ID 时拒绝恢复。
- 新增 SequentialEditorPolicyTest，覆盖“新会话即使指纹相同也不保留”“仅匹配的 restart 保留”“fieldId 必须为正数”。

安全边界：EditorInfo 并非全局唯一的 View 句柄；完整指纹只是降低误匹配概率。正数 fieldId 也可能在不同 Activity/页面复用，因此恢复仍保持暂停，必须由用户手动继续。不同应用和无 ID 编辑器的真机验证尚未完成。


### 第六轮补充：Unicode 属性纠错与断点恢复边界（2026-11-18）

- 按 Unicode 18.0.0 官方 GraphemeBreakProperty.txt 校正 Prepend 属性：补入 U+113D1，移除错误归入 Prepend 的 U+13430..U+1343F（该段实际为 Control）。新增回归测试覆盖两类情况。
- 按 Unicode 18.0.0 DerivedCoreProperties.txt 的 InCB=Consonant 精确区间替换此前过宽的脚本区段 + Character.isLetter 判断，避免把独立元音等非 InCB 辅音错误拼进 GB9c 连写簇。
- 增加包含 ZWJ 的 Indic conjunct 用例；InCB=Extend 明确包含 U+200D。
- 修正 GB11：ZWJ 后只有 Extended_Pictographic 才能继续 emoji ZWJ 序列；普通字符不会因前一个字符是 ZWJ 而被错误合并。常见 Extended_Pictographic 区间仍是精简近似，未导入完整 emoji-data 属性表。
- 断点恢复进一步收紧：只有 restarting=true、有效正数 fieldId 和完整 EditorInfo 指纹匹配才恢复，且恢复后暂停；普通新输入会话清除断点。进程被杀后未必收到同编辑器 restart，因此不承诺跨进程无条件恢复。
- 依据 Unicode 18.0.0 官方 UAX #29、GraphemeBreakProperty.txt 和 DerivedCoreProperties.txt；官方完整 GraphemeBreakTest.txt 尚未集成，不能宣称全量一致性。


### 第七轮补充：对照 Unicode 18.0 官方属性表细化 GB9b / GB9c

本轮继续读取 Unicode 18.0.0 官方文件，而不是只凭脚本区段猜测：
- GraphemeBreakProperty.txt 的 Prepend 属性总计 27 个码点；实现现已补入 U+113D1，并删除误加的 U+13430..U+1343F（官方属性为 Control）。
- DerivedCoreProperties.txt 明确列出 InCB=Linker（23 个码点）与 InCB=Consonant（913 个码点）。实现已将辅音识别从“脚本区段 + Character.isLetter”收紧为官方 InCB=Consonant 区间二分查找；Linker 清单与官方列出的 23 个码点一致。
- 官方 InCB=Extend 包含 U+200D ZERO WIDTH JOINER，因此加入该码点的 GB9c 扫描处理，并新增“辅音 + virama + ZWJ + 辅音”测试。
- 新增反向测试：独立元音不能仅因位于 Indic 区段就被误认成 GB9c 的 LinkingConsonant。
- UAX #29 GB11 要求 Extended_Pictographic Extend* ZWJ × Extended_Pictographic；实现不再无条件把 ZWJ 后任意字符合并，普通字母测试已覆盖。当前 Extended_Pictographic 是精简范围表，并非官方 emoji-data.txt 的完整属性表。
- 参考文件：Unicode 18.0.0 GraphemeBreakProperty.txt、DerivedCoreProperties.txt、emoji-data.txt、UAX #29。官方完整 GraphemeBreakTest.txt 仍未集成，不能声称完整符合 UAX #29。


### 第八轮补充：Grapheme Control 优先级

- 对照 GraphemeBreakProperty.txt 增加 Unicode 18.0 GCB=Control 范围识别。
- 按 UAX #29 规则优先级处理 GB3 与 GB4/GB5：CRLF 保持为一个输入单位；CR/LF 及其他 Control 前后强制断开，不再让组合标记错误附着到控制字符上。
- 新增 CRLF 后接组合标记、NUL 后接组合标记、埃及象形文字控制字符等回归测试。
- 仍未导入完整官方 GraphemeBreakTest.txt，Extended_Pictographic 数据也仍是精简近似，因此当前仅能描述为针对已覆盖规则的增强实现，不能标记为 UAX #29 全量合规。


### 第九轮补充：YuyanIme / yuyansdk 手写与九键源码对照

已检查上游 YuyanIme 的 README、.gitmodules，以及 yuyansdk 的手写识别、分割、图像预处理、CTC 解码和九键容器源码。

观察结果：
- yuyansdk 的手写识别采用独立 RecEngine：先做整行识别，再根据置信度与笔画分组结果决定是否运行逐字识别，最后合并候选；它还包含 StrokeSegmenter、ImagePreprocessor 和 CTC beam decoder。这是可供对照的算法方案。
- 当前 CyIME 已有独立 :inference 进程、OCHWPro ONNX 手写模型和个人模型替换能力。直接移植另一套模型运行时会增加模型体积、内存、线程和维护成本，暂不移植；后续应以同一批手写样本对比 Top-1/Top-5、整行准确率、首候选延迟、内存峰值，再决定是否吸收分割或候选融合算法。
- yuyansdk 的 T9TextContainer 依赖旧式 View / RecyclerView / Rime DictDecoder。当前工程已具备 Rime 九键与独立 Compose 键盘，不移植整个容器；只将其九键候选侧栏和符号交互作为体验对照。
- 上游 yuyansdk 根目录 LICENSE 为 BSD 3-Clause 条款；但 src/main/assets/hw/rec.nb（约 16.7 MB）模型权重和 ocrv5.txt 字典的单独来源、许可与再分发权尚未得到独立证明。源代码许可证不能自动覆盖模型和数据，因此不复制这些二进制资源。
- 上游项目引用的代码与资源必须按固定 commit 审查，不能跟随 master 自动更新。
