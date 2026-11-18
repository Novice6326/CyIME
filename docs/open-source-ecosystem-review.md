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
