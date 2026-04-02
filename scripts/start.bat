@echo off
chcp 65001 >nul
echo =========================================
echo  跨域数据交换系统 - Windows一键启动
echo =========================================
echo.

:: 检查Docker
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到Docker,请先安装Docker Desktop
    pause
    exit /b 1
)

echo [1/2] 获取 NanoMQ 桥接 Token...
echo 注意: 首次启动需要先生成桥接 Token
echo.

echo [2/2] 启动 Docker Compose...
docker-compose up -d --build

echo.
echo 等待服务启动...
timeout /t 30 /nobreak >nul

echo.
echo =========================================
echo  启动完成！
echo  应用入口:   http://localhost:8080
echo  EMQX管理:   http://localhost:18083
echo  H2控制台:   http://localhost:8080/h2-console
echo =========================================
echo.
echo 演示账号: admin / admin123
echo.
echo [重要] 首次启动后需要配置 NanoMQ 桥接:
echo   1. 登录系统获取 JWT Token
echo   2. 调用 GET /api/auth/bridge-token 获取桥接 Token
echo   3. 将 Token 填入 nanomq/nanomq.conf 的 password 字段
echo   4. 重启 NanoMQ: docker restart cross-domain-nanomq
echo.
pause
