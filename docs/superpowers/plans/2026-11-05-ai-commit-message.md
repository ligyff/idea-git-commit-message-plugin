# AI Commit Message Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 IDEA 2026.1 Commit 工具窗口提供可配置、可取消的 AI 提交信息生成按钮。

**Architecture:** 一个 Action 协调 UI 与后台任务；独立的变更采集器生成受限 diff；一个 OpenAI 兼容客户端负责网络；应用级服务保存普通设置，PasswordSafe 保存密钥。仅使用公开 VCS UI 数据接口，不操作 Swing 内部组件。

**Tech Stack:** Kotlin 2.x、Java 25 Toolchain、Gradle 9、IntelliJ Platform Gradle Plugin 2.x、IDEA 2026.1、Git4Idea/VCS API、java.net.http、PasswordSafe。

**Spec:** `docs/superpowers/specs/2026-11-05-ai-commit-message-design.md`

## Global Constraints

- 不写测试类，不引入测试依赖；验证使用 `compileKotlin`、`buildPlugin`、`verifyPlugin`。
- 不自动提交、不静默覆盖草稿、不上传未勾选的文件 diff。
- 目标平台固定为 IDEA 2026.1；Kotlin stdlib/coroutines 使用 IDE 自带版本。
- 核心代码保持可读性，长调用按语义换行并保留必要空行。
- 当前目录不是 Git 仓库，不自行提交或创建分支。

## Review Focus

- 空勾选、新增/删除/重命名、二进制、无法读取的文件：采集器应降级为文件状态而非抛出或读取无关内容。
- 总 diff 超限：必须截断并提示，避免超额发送源码。
- 旧草稿与请求期间用户继续编辑：成功回填前检查，不覆盖新输入。
- 无密钥、HTTP 错误、超时、取消、项目关闭：保留草稿并给出可理解提示。
- Action update 的 Swing 访问在 EDT，文件与网络访问在后台。

---

### Task 1: 固定工程与插件描述符

**Files:**
- Create: `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`.gitignore`
- Create: `src/main/resources/META-INF/plugin.xml`
- Create: `gradle/wrapper/gradle-wrapper.properties` 与 wrapper 脚本/JAR

**Interfaces:** 提供可编译的 IDEA 2026.1 插件工程和 Action 注册入口。

- [ ] 配置 Gradle 2.x、Kotlin 与 Java 25 Toolchain，固定版本并添加 IntelliJ 默认仓库。
- [ ] 添加 `Git4Idea` 和平台/VCS 模块依赖；注册 `Vcs.MessageActionGroup` Action 与设置页。
- [ ] 生成 Gradle wrapper，验证 `java -version` 与 `./gradlew --version`。

### Task 2: 设置、提示词和 OpenAI 兼容客户端

**Files:**
- Create: `src/main/kotlin/.../settings/AiCommitSettings.kt`
- Create: `src/main/kotlin/.../settings/AiCommitConfigurable.kt`
- Create: `src/main/kotlin/.../ai/CommitMessagePrompt.kt`
- Create: `src/main/kotlin/.../ai/OpenAiCompatibleClient.kt`

**Interfaces:**
- Produces: `AiCommitSettings` 应用服务；`OpenAiCompatibleClient.generate(diff: String): String`；设置页可保存 Base URL、Model、语言和密钥。

- [ ] 使用 IDE 可用 JSON 能力与 java.net.http 实现非流式 `/chat/completions`，设置超时、响应大小和错误处理。
- [ ] 用 PasswordSafe 读写密钥，禁止 EDT 调用，禁止日志记录密钥和完整 diff。
- [ ] 提示词要求只输出提交信息，默认中文 Conventional Commits，明确截断输入不能臆测。

### Task 3: 变更采集与生成 Action

**Files:**
- Create: `src/main/kotlin/.../vcs/CommitDiffCollector.kt`
- Create: `src/main/kotlin/.../action/GenerateCommitMessageAction.kt`

**Interfaces:**
- Consumes: Task 2 的客户端与设置。
- Produces: 点击按钮后读取 included changes、后台生成、成功后安全写回 `commitMessageUi`。

- [ ] 在 EDT 捕获工作流与草稿快照；确认覆盖并处理无变更；保存未保存文档后再读取。
- [ ] 将 before/after revision 转为统一 diff，处理文本编码、单文件/总量限制、二进制与错误降级。
- [ ] 通过可取消后台任务请求模型，返回 EDT 检查项目、原文与任务有效性，再写回消息。
- [ ] 配置空变更、失败、取消、重复点击、部分代码块选择提示。

### Task 4: 文档和构建验证

**Files:**
- Create: `README.md`

**Interfaces:** 说明安装、配置、隐私、支持范围与验证结果。

- [ ] 编写使用与开发说明，明确代码发送范围和部分代码块限制。
- [ ] 运行 `./gradlew compileKotlin buildPlugin verifyPlugin`，修复真实编译或描述符问题。
- [ ] 核对最终文件和构建产物，报告未能实机验证的交互与需要 API Key 才能验证的请求。
