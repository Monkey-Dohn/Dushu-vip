# 把本仓库整棵历史推到 GitHub（Windows PowerShell 版）
#
# 用法:
#   $env:GITHUB_TOKEN = "ghp_xxx"      # 有 repo 权限的 PAT
#   .\scripts\push-github.ps1 -Owner 你的用户名 -Repo 仓库名 [-Private]
param(
    [Parameter(Mandatory = $true)][string]$Owner,
    [Parameter(Mandatory = $true)][string]$Repo,
    [switch]$Private
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
$token = $env:GITHUB_TOKEN

Write-Host "== 1) 分步提交一览" -ForegroundColor Cyan
git log --oneline

if (-not $token) {
    Write-Host "== 未设置 GITHUB_TOKEN：只配置 remote" -ForegroundColor Yellow
    git remote remove origin 2>$null
    git remote add origin "https://github.com/$Owner/$Repo.git"
    Write-Host "已配置 origin。执行：git push -u origin $branch"
    exit 0
}

Write-Host "== 2) 创建仓库 $Owner/$Repo" -ForegroundColor Cyan
$body = @{
    name        = $Repo
    private     = [bool]$Private
    description = "申怡读书(com.dyw) 永久会员 LSPosed 模块 · 复刻 Deer God 思路"
} | ConvertTo-Json
try {
    Invoke-RestMethod -Method Post -Uri "https://api.github.com/user/repos" `
        -Headers @{ Authorization = "Bearer $token"; Accept = "application/vnd.github+json" } `
        -Body $body | Select-Object full_name, html_url | Format-List
} catch {
    Write-Host "建库返回: $($_.Exception.Message)（仓库已存在时可直接继续）" -ForegroundColor Yellow
}

Write-Host "== 3) 推送全部分步提交" -ForegroundColor Cyan
git remote remove origin 2>$null
git remote add origin "https://x-access-token:$token@github.com/$Owner/$Repo.git"
git push -u origin $branch --tags

Write-Host "== 4) remote 去掉 token" -ForegroundColor Cyan
git remote set-url origin "https://github.com/$Owner/$Repo.git"
Write-Host "完成: https://github.com/$Owner/$Repo" -ForegroundColor Green
