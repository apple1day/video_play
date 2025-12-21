#!/bin/bash

echo "========================================"
echo "查看删除相关日志"
echo "========================================"
echo ""

echo "📋 最近的删除日志："
echo "----------------------------------------"
adb logcat -d | grep -E "(deleteFile|deleteSelected|deleteCurrentFile)" | tail -50

echo ""
echo "========================================"
echo "📋 MediaStore 相关日志："
echo "----------------------------------------"
adb logcat -d | grep -i "mediastore" | tail -30

echo ""
echo "========================================"
echo "📋 错误和异常日志："
echo "----------------------------------------"
adb logcat -d | grep -E "(Exception|Error|失败|failed)" | grep -E "(delete|File)" | tail -30

echo ""
echo "========================================"
echo "📋 权限相关日志："
echo "----------------------------------------"
adb logcat -d | grep -i "permission" | tail -20

echo ""
echo "========================================"
echo "📋 PlayerActivity 和 HomeFragment 的所有日志："
echo "----------------------------------------"
adb logcat -d | grep -E "(PlayerActivity|HomeFragment)" | tail -100
