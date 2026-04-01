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

echo [1/3] 构建后端...
cd backend
call mvnw.cmd clean package -DskipTests -q
if %errorlevel% neq 0 (
    echo [错误] 后端构建失败
    pause
    exit /b 1
)
cd ..

echo [2/3] 安装前端依赖...
cd frontend
call npm install
call npm run build
cd ..

echo [3/3] 启动Docker Compose...
docker-compose up -d --build

echo.
echo =========================================
echo  启动完成！
echo  前端:     http://localhost:3000
echo  后端API:  http://localhost:8080
echo  EMQX管理: http://localhost:18083
echo  H2控制台: http://localhost:8080/h2-console
echo =========================================
echo.
echo 演示账号: admin / admin123
echo.
pause
