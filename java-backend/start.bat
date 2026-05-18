@echo off
echo =====================================
echo OpenClaw 智能作业批改系统 - 启动脚本
echo =====================================

echo.
echo 检查 Java 版本...
java -version
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到 Java 17 或更高版本
    pause
    exit /b 1
)

echo.
echo 检查 Maven...
mvn -version
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到 Maven
    pause
    exit /b 1
)

echo.
echo 正在构建项目...
call mvn clean package -DskipTests

if %errorlevel% neq 0 (
    echo ❌ 构建失败
    pause
    exit /b 1
)

echo.
echo ✅ 构建成功!
echo.
echo 启动应用...
echo 访问地址: http://localhost:8080/api/v1
echo 健康检查: http://localhost:8080/api/v1/grading/health
echo.

java -jar target\openclaw-intelligent-grading-1.0.0.jar

pause
