#!/bin/bash

# 删除功能调试脚本
# 用法: ./debug_delete.sh [选项]
# 选项:
#   -c, --clear    清空日志
#   -p, --perm     授予所有必要权限
#   -l, --log      实时查看删除日志
#   -i, --info     显示设备和应用信息
#   -a, --all      执行所有操作

PACKAGE_NAME="androidx.media3.demo.filebrowser"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

function print_header() {
    echo -e "${BLUE}======================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}======================================${NC}"
}

function print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

function print_error() {
    echo -e "${RED}❌ $1${NC}"
}

function print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

function print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

# 检查设备连接
function check_device() {
    print_header "检查设备连接"
    
    if ! adb devices | grep -q "device$"; then
        print_error "未检测到设备，请确保设备已连接并启用 USB 调试"
        exit 1
    fi
    
    print_success "设备已连接"
}

# 清空日志
function clear_logs() {
    print_header "清空日志"
    adb logcat -c
    print_success "日志已清空"
}

# 授予权限
function grant_permissions() {
    print_header "授予权限"
    
    echo "授予存储权限..."
    adb shell pm grant $PACKAGE_NAME android.permission.READ_EXTERNAL_STORAGE 2>/dev/null
    adb shell pm grant $PACKAGE_NAME android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null
    adb shell pm grant $PACKAGE_NAME android.permission.READ_MEDIA_VIDEO 2>/dev/null
    adb shell pm grant $PACKAGE_NAME android.permission.READ_MEDIA_AUDIO 2>/dev/null
    adb shell pm grant $PACKAGE_NAME android.permission.READ_MEDIA_IMAGES 2>/dev/null
    
    echo "授予管理所有文件权限..."
    adb shell appops set $PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow
    
    print_success "权限已授予"
}

# 显示设备信息
function show_info() {
    print_header "设备和应用信息"
    
    echo -e "${BLUE}Android 版本:${NC}"
    SDK_VERSION=$(adb shell getprop ro.build.version.sdk)
    RELEASE_VERSION=$(adb shell getprop ro.build.version.release)
    echo "  API Level: $SDK_VERSION"
    echo "  Android: $RELEASE_VERSION"
    
    echo -e "\n${BLUE}设备型号:${NC}"
    DEVICE_MODEL=$(adb shell getprop ro.product.model)
    MANUFACTURER=$(adb shell getprop ro.product.manufacturer)
    echo "  制造商: $MANUFACTURER"
    echo "  型号: $DEVICE_MODEL"
    
    echo -e "\n${BLUE}应用权限状态:${NC}"
    adb shell dumpsys package $PACKAGE_NAME | grep -A 20 "granted=true" | head -20
    
    echo -e "\n${BLUE}MANAGE_EXTERNAL_STORAGE 权限:${NC}"
    MANAGE_PERM=$(adb shell appops get $PACKAGE_NAME MANAGE_EXTERNAL_STORAGE)
    echo "  $MANAGE_PERM"
    
    echo -e "\n${BLUE}应用安装状态:${NC}"
    APP_INSTALLED=$(adb shell pm list packages | grep $PACKAGE_NAME)
    if [ -n "$APP_INSTALLED" ]; then
        print_success "应用已安装"
    else
        print_error "应用未安装"
    fi
}

# 实时日志监控
function watch_logs() {
    print_header "实时删除日志监控"
    print_info "按 Ctrl+C 停止监控"
    echo ""
    
    # 使用颜色高亮关键信息
    adb logcat | grep --line-buffered -E "(HomeFragment|PlayerActivity|deleteFile|MediaStore)" | \
    while read line; do
        if echo "$line" | grep -q "成功"; then
            echo -e "${GREEN}$line${NC}"
        elif echo "$line" | grep -q "失败\|错误\|异常\|Exception"; then
            echo -e "${RED}$line${NC}"
        elif echo "$line" | grep -q "警告\|Warning"; then
            echo -e "${YELLOW}$line${NC}"
        else
            echo "$line"
        fi
    done
}

# 重启应用
function restart_app() {
    print_header "重启应用"
    
    echo "强制停止应用..."
    adb shell am force-stop $PACKAGE_NAME
    sleep 1
    
    echo "启动应用..."
    adb shell am start -n $PACKAGE_NAME/.MainActivity
    
    if [ $? -eq 0 ]; then
        print_success "应用已重启"
    else
        print_error "启动应用失败"
    fi
}

# 检查文件系统
function check_files() {
    print_header "检查媒体文件"
    
    echo -e "${BLUE}DCIM 目录:${NC}"
    adb shell ls -la /storage/emulated/0/DCIM/ 2>/dev/null | head -10
    
    echo -e "\n${BLUE}Movies 目录:${NC}"
    adb shell ls -la /storage/emulated/0/Movies/ 2>/dev/null | head -10
    
    echo -e "\n${BLUE}Download 目录:${NC}"
    adb shell ls -la /storage/emulated/0/Download/ 2>/dev/null | head -10
}

# 执行删除测试
function test_delete() {
    print_header "删除功能测试"
    
    print_warning "请在手机上手动执行以下操作："
    echo "  1. 打开应用"
    echo "  2. 选择一个视频文件"
    echo "  3. 点击删除按钮"
    echo ""
    print_info "观察下方日志输出..."
    echo ""
    
    watch_logs
}

# 显示帮助
function show_help() {
    echo "删除功能调试脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  -c, --clear     清空日志"
    echo "  -p, --perm      授予所有必要权限"
    echo "  -l, --log       实时查看删除日志"
    echo "  -i, --info      显示设备和应用信息"
    echo "  -f, --files     检查媒体文件"
    echo "  -r, --restart   重启应用"
    echo "  -t, --test      执行删除测试（清空日志+授权+重启+监控）"
    echo "  -a, --all       执行所有检查"
    echo "  -h, --help      显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -t           # 快速测试删除功能"
    echo "  $0 -i           # 查看设备信息"
    echo "  $0 -p -r -l     # 授权、重启并监控日志"
}

# 主函数
function main() {
    check_device
    
    if [ $# -eq 0 ]; then
        show_help
        exit 0
    fi
    
    while [ $# -gt 0 ]; do
        case "$1" in
            -c|--clear)
                clear_logs
                ;;
            -p|--perm)
                grant_permissions
                ;;
            -l|--log)
                watch_logs
                ;;
            -i|--info)
                show_info
                ;;
            -f|--files)
                check_files
                ;;
            -r|--restart)
                restart_app
                ;;
            -t|--test)
                clear_logs
                grant_permissions
                restart_app
                sleep 2
                test_delete
                ;;
            -a|--all)
                show_info
                echo ""
                grant_permissions
                echo ""
                check_files
                ;;
            -h|--help)
                show_help
                exit 0
                ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
        shift
    done
}

# 运行主函数
main "$@"
