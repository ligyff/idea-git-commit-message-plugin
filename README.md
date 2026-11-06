# AI Commit Message

[![CI](https://github.com/ligyff/idea-git-commit-message-plugin/actions/workflows/build.yml/badge.svg)](../../actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

IntelliJ IDEA 插件：在 Commit 工具窗口中根据勾选的变更生成提交信息，结果填回输入框，由你确认后再手动提交。

## 功能

- 在提交信息输入框上方的工具栏（`Vcs.MessageActionGroup`）增加生成按钮。
- 读取提交面板**实际勾选**的变更，而不是 `git diff --cached`。
- 支持部分代码块提交：使用 IDEA 的部分变更包装逻辑，只描述勾选的代码块。
- 未版本控制但已勾选的文件按新增文件读取；二进制、目录、超长内容降级为路径与状态说明。
- 后台执行，可取消；失败、超时、项目关闭不会清空已有草稿。
- 生成期间若提交信息被改动，结果不写入并提示重新生成。

## 配置

`Settings → Tools → AI Commit Message`：

| 项 | 说明 |
|---|---|
| Base URL | OpenAI 兼容服务地址，请求 `POST {Base URL}/chat/completions`；服务不支持时自动回退 `responses` 协议 |
| 模型 | 服务商提供的模型 ID |
| API Key | 存入 IDEA `PasswordSafe`，不写入配置文件或日志 |
| 提交信息语言 | 默认中文 |
| 代码发送确认 | 首次使用必须勾选 |

## 使用

1. 打开 Commit 工具窗口，勾选本次要提交的文件或代码块。
2. 点击提交信息框上方的 **AI 生成提交信息** 按钮。
3. 等待生成完成，按需修改后手动提交。

输出格式为 Conventional Commits，例如：

```text
feat(user): 登录接口支持传入用户名

- login() 增加 name 参数
- 新增密码校验调用
```

## 从源码构建

```bash
./gradlew buildPlugin
```

产物在 `build/distributions/`，通过 `Settings → Plugins → ⚙️ → Install Plugin from Disk...` 安装。

运行沙箱验证：`./gradlew runIde`。

## 隐私与边界

- 只发送勾选变更的文本 diff；单文件超过 60,000 字符、总量超过 120,000 字符会截断并在提示中标注。
- 二进制与无法读取的内容不会上传，只发送文件路径与状态。
- `http://` 仅允许 `localhost` / `127.0.0.1` / `::1`，远程服务必须使用 `https://`。
- 日志只记录异常类型，不记录密钥、响应体或 diff 内容。
- 不会自动提交，也不会静默覆盖已有提交信息。
- 目前按 IDEA 2026.1（build 261）构建，`until-build` 限制在 261.*。

## 已知限制

- 已保存的密钥目前没有“清除”入口，需要时可在 IDEA PasswordSafe 中删除。
- 在设置页填完密钥后立刻点击生成，极小概率仍读到旧值，重试即可。

## License

[Apache-2.0](LICENSE)
