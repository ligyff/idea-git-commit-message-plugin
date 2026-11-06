#!/usr/bin/env bash
# 排查火山方舟端点/模型/协议组合。不会打印密钥本身。
#
# 用法：
#   ARK_API_KEY=你的key ./tools/diagnose-ark.sh 你的模型ID
#
# 可选：ARK_API_KEY_FILE=/path/to/keyfile，从文件读密钥，避免进 shell 历史。
set -u

BASE_HOST="https://ark.cn-beijing.volces.com"

if [[ -z "${ARK_API_KEY:-}" && -n "${ARK_API_KEY_FILE:-}" ]]; then
  ARK_API_KEY="$(tr -d '\r\n' < "$ARK_API_KEY_FILE")"
fi

MODEL="${1:-}"
if [[ -z "${ARK_API_KEY:-}" || -z "$MODEL" ]]; then
  echo "用法: ARK_API_KEY=... $0 <模型ID>" >&2
  exit 2
fi

echo "模型: $MODEL"
echo "密钥长度: ${#ARK_API_KEY}（不显示内容）"
echo

probe() {
  local label="$1" url="$2" body="$3"
  local tmp status
  tmp="$(mktemp)"
  status="$(curl -s -o "$tmp" -w '%{http_code}' --max-time 30 \
    -X POST "$url" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ARK_API_KEY" \
    -d "$body")"
  printf '%-46s HTTP %s\n' "$label" "$status"
  if [[ "$status" != "200" ]]; then
    head -c 220 "$tmp" | tr -d '\n'
    echo
  else
    echo "  ↳ 成功，返回片段：$(head -c 160 "$tmp" | tr -d '\n')"
  fi
  rm -f "$tmp"
}

for tier in "api/plan/v3:AgentPlan" "api/coding/v3:CodingPlan" "api/v3:按量计费"; do
  path="${tier%%:*}"; name="${tier##*:}"
  echo "=== $name ($path) ==="
  probe "chat/completions" "$BASE_HOST/$path/chat/completions" \
    "{\"model\":\"$MODEL\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"
  probe "responses" "$BASE_HOST/$path/responses" \
    "{\"model\":\"$MODEL\",\"instructions\":\"reply ok\",\"input\":\"hi\"}"
  echo
done

echo "判读："
echo "  三个端点全部 401/403 → 密钥或套餐权限问题"
echo "  单一端点 200           → 插件里就把 Base URL 设成那个路径"
echo "  三个端点全部 500       → 模型 ID 不属于该账号套餐，或该模型 ID 写错"
echo "  404/400 且提示模型不存在 → 模型 ID 写错"
