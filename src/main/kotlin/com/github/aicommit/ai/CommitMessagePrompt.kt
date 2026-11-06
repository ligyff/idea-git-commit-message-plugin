package com.github.aicommit.ai

object CommitMessagePrompt {
    fun system(language: String): String = """
        你是资深软件工程师，根据用户提供的本次提交 diff 生成提交信息。
        使用$language。首行采用 Conventional Commits：type(scope): 简洁描述。
        type 从 feat、fix、refactor、perf、docs、style、test、build、ci、chore 中选择。
        首行尽量不超过 72 个字符；有多项重要变更时，空一行后添加最多 5 条“- ”正文。
        只概括 diff 中能证实的改动，不推测需求、影响、测试结果或问题单号。
        输入可能截断，不要声称看到了被省略的内容。不要输出 Markdown 代码块、解释或前言。
    """.trimIndent()

    fun user(diff: String): String = "请为以下本次提交变更生成提交信息：\n\n$diff"
}
