#!/usr/bin/env bash
# 把本仓库整棵历史推到 GitHub（含每一步提交）
#
# 用法:
#   export GITHUB_TOKEN=ghp_xxx            # 有 repo 权限的 PAT（classic 勾 repo，fine-grained 勾 Contents+Administration）
#   bash scripts/push-github.sh <你的用户名> <仓库名> [private|public]
#
# 也可以先自己建空仓库，然后:
#   git remote add origin https://github.com/<user>/<repo>.git && git push -u origin main
set -euo pipefail

OWNER="${1:?用法: push-github.sh <owner> <repo> [private|public]}"
REPO="${2:?用法: push-github.sh <owner> <repo> [private|public]}"
VIS="${3:-public}"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
TOKEN="${GITHUB_TOKEN:-}"

if [ -z "$TOKEN" ]; then
  echo "未设置 GITHUB_TOKEN，跳过自动建库，仅配置 remote 并提示手动推送"
  git remote remove origin 2>/dev/null || true
  git remote add origin "https://github.com/$OWNER/$REPO.git"
  echo "已配置 origin -> https://github.com/$OWNER/$REPO.git"
  echo "执行: git push -u origin $BRANCH"
  exit 0
fi

echo "== 1) 创建仓库 $OWNER/$REPO ($VIS)"
curl -sS -X POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO\",\"private\":$([ "$VIS" = private ] && echo true || echo false),\"description\":\"申怡读书(com.dyw) 永久会员 LSPosed 模块 · 复刻 Deer God 思路\"}" \
  -o /tmp/gh_repo.json || true
grep -o '"full_name":"[^"]*"' /tmp/gh_repo.json || cat /tmp/gh_repo.json

echo "== 2) 配置 remote 并推送全部提交"
git remote remove origin 2>/dev/null || true
git remote add origin "https://x-access-token:$TOKEN@github.com/$OWNER/$REPO.git"
git push -u origin "$BRANCH" --tags

echo "== 3) 收尾：remote 换成不带 token 的地址"
git remote set-url origin "https://github.com/$OWNER/$REPO.git"
echo "完成: https://github.com/$OWNER/$REPO"
