@echo off
chcp 65001 >nul
echo =========================================
echo  Cross-domain exchange system - Windows launcher
echo =========================================
echo.

docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed or not on PATH.
    pause
    exit /b 1
)

echo [1/2] Building and starting services...
docker-compose up -d --build
if %errorlevel% neq 0 (
    echo [ERROR] Failed to start the stack.
    pause
    exit /b 1
)

echo.
echo [2/2] Waiting for services to become ready...
timeout /t 30 /nobreak >nul

echo.
echo =========================================
echo  Startup complete
echo  API:     http://localhost:8080
echo  EMQX:    http://localhost:18083
echo  H2:      http://localhost:8080/h2-console
echo =========================================
echo.
echo Demo accounts:
echo   admin / admin123
echo   producer_swu / 123456
echo   consumer_social / 123456
echo   consumer_c / 123456
echo.
pause
