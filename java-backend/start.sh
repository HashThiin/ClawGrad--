#!/bin/bash

echo "====================================="
echo "OpenClaw 智能作业批改系统 - 启动脚本"
echo "====================================="

# 检查 Java 版本
echo ""
echo "检查 Java 版本..."
java -version
if [ $? -ne 0 ]; then
    echo "❌ 错误: 未找到 Java 17 或更高版本"
    exit 1
fi

# 检查 Maven
echo ""
echo "检查 Maven..."
mvn -version
if [ $? -ne 0 ]; then
    echo "❌ 错误: 未找到 Maven"
    exit 1
fi

# 构建项目
echo ""
echo "正在构建项目..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ 构建失败"
    exit 1
fi

echo ""
echo "✅ 构建成功!"
echo ""
echo "启动应用..."
echo "访问地址: http://localhost:8080/api/v1"
echo "健康检查: http://localhost:8080/api/v1/grading/health"
echo ""

# 运行应用
java -jar target/openclaw-intelligent-grading-1.0.0.jar
