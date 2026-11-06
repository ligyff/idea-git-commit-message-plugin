# AI Commit Message 插件设计

## 目标

为 IntelliJ IDEA 2026.1.1（build 261）的 Commit 工具窗口增加 AI 按钮，根据提交面板勾选的变更生成中文 Conventional Commits 信息，供开发者确认、编辑和手动提交。

## 已确认决策

- 使用 Kotlin、IntelliJ Platform Gradle Plugin 2.x；以 IDEA 2026.1 为最低目标。
- 本机使用 JDK 21 启动 Gradle，通过 Java Toolchain 获取 Java 25 编译环境。
- 注册到 `Vcs.MessageActionGroup`，不遍历 Swing 私有组件。
- 使用 `COMMIT_WORKFLOW_UI.getIncludedChanges()`，不将 IDE 勾选状态误当作 Git index。
- 使用可配置 Base URL、Model、API Key 的 OpenAI 兼容 `/chat/completions` 接口。
- 默认生成中文提交信息，首行遵循 Conventional Commits，必要时添加简短正文。
- 不自动提交；已有文案覆盖前确认；失败、取消、项目关闭不破坏旧草稿。
- 不编写测试类；使用 Gradle 编译、插件构建和 Plugin Verifier 验证。

## 范围与边界

- 首版支持普通新增、修改、删除和重命名文件的文本变更摘要。
- 根据勾选的完整 `Change` 构造 diff；未版本控制的已勾选文件先加入 IDE 的纳入模型再读取。
- 首版不承诺精确还原逐代码块 inclusion；检测到部分代码块选择时提醒用户结果可能包含同文件其他变更。
- diff 设总大小和单文件大小限制；超限、二进制与无法读取的内容用文件路径和状态说明代替，并明确提示模型。
- API Key 存入 PasswordSafe，不写普通持久化配置、项目文件或日志。
- 网络请求有超时、取消和错误提示；不在 EDT 上读取大文件或调用 HTTP。
- 不做账号体系、流式生成、多模型供应商适配、提交历史检索和自动提交。

## 模块

1. `GenerateCommitMessageAction`：获取 UI 快照、确认覆盖、后台任务、回填。
2. `CommitDiffCollector`：将 `Change` 转为受限的统一 diff 输入。
3. `OpenAiCompatibleClient`：最小 JSON 请求与响应、超时和取消。
4. `CommitMessagePrompt`：约束输出语言、格式与上下文边界。
5. `AiCommitSettings` 与 `AiCommitConfigurable`：普通设置与安全密钥。
6. Gradle 与 plugin.xml：固定目标平台、Git/VCS 依赖和 Action 注册。
