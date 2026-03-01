# deploy/deploy.ps1 - Windows 一键部署脚本
# 用法:
#   .\deploy\deploy.ps1              # 完整部署
#   .\deploy\deploy.ps1 -BackendOnly # 仅部署后端
#   .\deploy\deploy.ps1 -FrontendOnly # 仅部署前端
#   .\deploy\deploy.ps1 -SkipBuild   # 跳过构建步骤

param(
    [switch]$SkipBuild,      # 跳过构建
    [switch]$BackendOnly,    # 仅部署后端
    [switch]$FrontendOnly,   # 仅部署前端
    [switch]$Help            # 显示帮助
)

# 显示帮助
if ($Help) {
    Write-Host @"
Grid Trading 一键部署脚本

用法:
    .\deploy\deploy.ps1 [参数]

参数:
    -SkipBuild      跳过本地构建步骤
    -BackendOnly    仅部署后端服务
    -FrontendOnly   仅部署前端服务
    -Help           显示此帮助信息

示例:
    .\deploy\deploy.ps1                    # 完整部署
    .\deploy\deploy.ps1 -BackendOnly       # 仅更新后端
    .\deploy\deploy.ps1 -FrontendOnly      # 仅更新前端
    .\deploy\deploy.ps1 -SkipBuild         # 跳过构建，仅上传重启
"@
    exit 0
}

# 加载配置
$ConfigPath = "$PSScriptRoot\config.ps1"
if (-not (Test-Path $ConfigPath)) {
    Write-Host "错误: 配置文件不存在: $ConfigPath" -ForegroundColor Red
    Write-Host "请先复制 config.ps1.example 为 config.ps1 并填入服务器信息" -ForegroundColor Yellow
    exit 1
}
. $ConfigPath

# 验证配置
if ($SERVER_IP -eq "your-server-ip") {
    Write-Host "错误: 请先修改 deploy/config.ps1 中的服务器 IP 地址" -ForegroundColor Red
    exit 1
}

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

Write-Host ""
Write-Host "╔════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║       Grid Trading 一键部署脚本            ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "目标服务器: $SERVER_USER@$SERVER_IP" -ForegroundColor White
Write-Host "部署路径: $REMOTE_PATH" -ForegroundColor White
Write-Host ""

# SSH 命令封装
function Invoke-SSH {
    param([string]$Command)
    if ($SSH_KEY -and (Test-Path $SSH_KEY)) {
        ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no -i $SSH_KEY "$SERVER_USER@$SERVER_IP" $Command
    } else {
        ssh -o ConnectTimeout=10 -o StrictHostKeyChecking=no "$SERVER_USER@$SERVER_IP" $Command
    }
    return $LASTEXITCODE
}

# SCP 命令封装
function Invoke-SCP {
    param(
        [string]$Source,
        [string]$Dest,
        [switch]$Recursive
    )
    $args = @("-o", "StrictHostKeyChecking=no")
    if ($SSH_KEY -and (Test-Path $SSH_KEY)) {
        $args += @("-i", $SSH_KEY)
    }
    if ($Recursive) {
        $args += "-r"
    }
    $args += $Source
    $args += "${SERVER_USER}@${SERVER_IP}:${Dest}"

    & scp @args
    return $LASTEXITCODE
}

# 步骤 1: 测试 SSH 连接
function Test-SSHConnection {
    Write-Host "[1/6] 测试 SSH 连接..." -ForegroundColor Yellow
    $result = Invoke-SSH "echo 'connected'"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ SSH 连接失败！" -ForegroundColor Red
        Write-Host "  请检查:" -ForegroundColor Yellow
        Write-Host "  1. 服务器 IP 是否正确: $SERVER_IP" -ForegroundColor Gray
        Write-Host "  2. SSH 密钥是否配置: $SSH_KEY" -ForegroundColor Gray
        Write-Host "  3. 服务器防火墙是否开放 22 端口" -ForegroundColor Gray
        exit 1
    }
    Write-Host "✓ SSH 连接成功！" -ForegroundColor Green
}

# 步骤 2: 构建后端
function Build-Backend {
    if ($SkipBuild -or $FrontendOnly) {
        Write-Host "[2/6] 跳过后端构建" -ForegroundColor Gray
        return
    }
    Write-Host "[2/6] 构建后端 JAR 包..." -ForegroundColor Yellow
    Push-Location "$ProjectRoot\backend"
    try {
        & $MAVEN_CMD clean package -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ 后端构建失败！" -ForegroundColor Red
            exit 1
        }
        Write-Host "✓ 后端构建完成！" -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

# 步骤 3: 构建前端
function Build-Frontend {
    if ($SkipBuild -or $BackendOnly) {
        Write-Host "[3/6] 跳过前端构建" -ForegroundColor Gray
        return
    }
    Write-Host "[3/6] 构建前端..." -ForegroundColor Yellow
    Push-Location "$ProjectRoot\frontend"
    try {
        & $NPM_CMD install --silent
        & $NPM_CMD run build
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ 前端构建失败！" -ForegroundColor Red
            exit 1
        }
        Write-Host "✓ 前端构建完成！" -ForegroundColor Green
    } finally {
        Pop-Location
    }
}

# 步骤 4: 上传文件
function Upload-Files {
    Write-Host "[4/6] 上传文件到服务器..." -ForegroundColor Yellow

    # 确保远程目录存在
    Invoke-SSH "mkdir -p $REMOTE_PATH/backend $REMOTE_PATH/frontend"

    if (-not $FrontendOnly) {
        Write-Host "  ├─ 上传后端 JAR 包..." -ForegroundColor Gray
        $jarFile = Get-ChildItem "$ProjectRoot\backend\target\*.jar" | Select-Object -First 1
        if ($jarFile) {
            Invoke-SCP $jarFile.FullName "$REMOTE_PATH/backend/"
        }

        Write-Host "  ├─ 上传 Dockerfile..." -ForegroundColor Gray
        Invoke-SCP "$ProjectRoot\backend\Dockerfile" "$REMOTE_PATH/backend/"

        Write-Host "  ├─ 上传 docker-compose.yml..." -ForegroundColor Gray
        Invoke-SCP "$ProjectRoot\docker-compose.yml" "$REMOTE_PATH/"

        Write-Host "  ├─ 上传 init.sql..." -ForegroundColor Gray
        Invoke-SCP "$ProjectRoot\backend\init.sql" "$REMOTE_PATH/backend/"
    }

    if (-not $BackendOnly) {
        Write-Host "  ├─ 清理远程前端目录..." -ForegroundColor Gray
        Invoke-SSH "rm -rf $REMOTE_PATH/frontend/*"

        Write-Host "  ├─ 上传前端构建文件..." -ForegroundColor Gray
        Invoke-SCP "$ProjectRoot\frontend\dist\*" "$REMOTE_PATH/frontend/" -Recursive

        Write-Host "  ├─ 上传 Nginx 配置..." -ForegroundColor Gray
        Invoke-SSH "mkdir -p /usr/local/openresty/nginx/conf/conf.d"
        Invoke-SCP "$ProjectRoot\deploy\nginx\grid-trading.conf" "/usr/local/openresty/nginx/conf/conf.d/"
    }

    Write-Host "✓ 文件上传完成！" -ForegroundColor Green
}

# 步骤 5: 远程部署
function Deploy-Remote {
    Write-Host "[5/6] 远程执行部署..." -ForegroundColor Yellow

    if (-not $FrontendOnly) {
        Write-Host "  ├─ 重启 Docker 容器..." -ForegroundColor Gray
        $dockerCommands = @"
cd $REMOTE_PATH
docker compose down 2>/dev/null || true
docker compose up -d --build
sleep 5
docker compose ps
"@
        Invoke-SSH $dockerCommands
    }

    if (-not $BackendOnly) {
        Write-Host "  ├─ 重载 OpenResty..." -ForegroundColor Gray

        # 检查 OpenResty 配置是否正确
        $checkNginx = @"
if ! grep -q 'include /usr/local/openresty/nginx/conf/conf.d/*.conf;' /usr/local/openresty/nginx/conf/nginx.conf 2>/dev/null; then
    # 在 http 块末尾添加 include
    sed -i '/http {/a\    include /usr/local/openresty/nginx/conf/conf.d/*.conf;' /usr/local/openresty/nginx/conf/nginx.conf 2>/dev/null || true
fi
openresty -t && openresty -s reload
"@
        Invoke-SSH $checkNginx
    }

    Write-Host "✓ 远程部署完成！" -ForegroundColor Green
}

# 步骤 6: 验证部署
function Test-Deployment {
    Write-Host "[6/6] 验证部署状态..." -ForegroundColor Yellow

    Start-Sleep -Seconds 3

    # 测试后端 API
    if (-not $FrontendOnly) {
        Write-Host "  ├─ 测试后端 API..." -ForegroundColor Gray
        try {
            $response = Invoke-WebRequest -Uri "http://${SERVER_IP}:8080/api/strategies" -TimeoutSec 15 -UseBasicParsing
            Write-Host "  │  ✓ 后端 API: 正常 (HTTP $($response.StatusCode))" -ForegroundColor Green
        } catch {
            Write-Host "  │  ✗ 后端 API: 异常 (请检查 Docker 日志)" -ForegroundColor Red
        }
    }

    # 测试前端页面
    if (-not $BackendOnly) {
        Write-Host "  ├─ 测试前端页面..." -ForegroundColor Gray
        try {
            $response = Invoke-WebRequest -Uri "http://${SERVER_IP}/" -TimeoutSec 10 -UseBasicParsing
            Write-Host "  │  ✓ 前端页面: 正常 (HTTP $($response.StatusCode))" -ForegroundColor Green
        } catch {
            Write-Host "  │  ✗ 前端页面: 异常 (请检查 OpenResty)" -ForegroundColor Red
        }
    }
}

# ============================================
# 主流程
# ============================================

$startTime = Get-Date

Test-SSHConnection
Build-Backend
Build-Frontend
Upload-Files
Deploy-Remote
Test-Deployment

$duration = (Get-Date) - $startTime

Write-Host ""
Write-Host "╔════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║           部署完成！                       ║" -ForegroundColor Green
Write-Host "╚════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "耗时: $($duration.TotalSeconds.ToString('0.0')) 秒" -ForegroundColor Gray
Write-Host ""
Write-Host "访问地址:" -ForegroundColor Yellow
Write-Host "  前端首页: http://$SERVER_IP/" -ForegroundColor White
Write-Host "  后端 API: http://$SERVER_IP:8080/api/strategies" -ForegroundColor White
Write-Host ""
Write-Host "运维命令 (SSH 到服务器后执行):" -ForegroundColor Yellow
Write-Host "  查看日志: docker compose -f $REMOTE_PATH/docker-compose.yml logs -f backend" -ForegroundColor Gray
Write-Host "  重启服务: docker compose -f $REMOTE_PATH/docker-compose.yml restart" -ForegroundColor Gray
Write-Host ""

