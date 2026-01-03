#!/bin/bash

# iOS图标导入脚本
# 将appicon.co生成的AppIcons目录导入到Xcode项目中

set -e

echo "🐻 小熊播放器 - iOS图标导入工具"
echo "================================"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 项目路径
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
APPICONS_DIR="$PROJECT_ROOT/AppIcons"
APPICONS_ZIP="$PROJECT_ROOT/AppIcons.zip"

echo "📁 项目根目录: $PROJECT_ROOT"

# 检查AppIcons目录或zip文件
if [ ! -d "$APPICONS_DIR" ]; then
    if [ -f "$APPICONS_ZIP" ]; then
        echo -e "${YELLOW}⚠️  未找到AppIcons目录，但找到了zip文件${NC}"
        echo "正在解压 AppIcons.zip..."
        unzip -q "$APPICONS_ZIP" -d "$PROJECT_ROOT"
        echo -e "${GREEN}✅ 解压完成${NC}"
    else
        echo -e "${RED}❌ 错误: 未找到AppIcons目录或AppIcons.zip文件${NC}"
        echo "请确保以下文件之一存在于项目根目录："
        echo "  - AppIcons/ (目录)"
        echo "  - AppIcons.zip (压缩包)"
        exit 1
    fi
fi

# 列出AppIcons目录内容
echo ""
echo "📦 AppIcons目录内容:"
ls -lh "$APPICONS_DIR" | head -10

# 查找iOS AppIcon目录
echo ""
echo "🔍 查找iOS项目中的AppIcon位置..."

# 可能的AppIcon位置
POSSIBLE_PATHS=(
    "$PROJECT_ROOT/myMedia/myMedia/Assets.xcassets/AppIcon.appiconset"
    "$PROJECT_ROOT/myMedia/Assets.xcassets/AppIcon.appiconset"
    "$PROJECT_ROOT/iOS/myMedia/Assets.xcassets/AppIcon.appiconset"
)

APPICON_TARGET=""
for path in "${POSSIBLE_PATHS[@]}"; do
    if [ -d "$path" ]; then
        APPICON_TARGET="$path"
        echo -e "${GREEN}✅ 找到AppIcon目录: $path${NC}"
        break
    fi
done

if [ -z "$APPICON_TARGET" ]; then
    echo -e "${YELLOW}⚠️  未找到现有的AppIcon.appiconset目录${NC}"
    echo "请选择操作："
    echo "  1) 手动指定iOS项目路径"
    echo "  2) 创建新的Assets.xcassets目录"
    echo "  3) 显示详细信息后退出"
    read -p "请输入选项 (1-3): " choice
    
    case $choice in
        1)
            read -p "请输入iOS项目的完整路径: " custom_path
            APPICON_TARGET="$custom_path/Assets.xcassets/AppIcon.appiconset"
            mkdir -p "$APPICON_TARGET"
            echo -e "${GREEN}✅ 创建目录: $APPICON_TARGET${NC}"
            ;;
        2)
            APPICON_TARGET="$PROJECT_ROOT/myMedia/myMedia/Assets.xcassets/AppIcon.appiconset"
            mkdir -p "$APPICON_TARGET"
            echo -e "${GREEN}✅ 创建目录: $APPICON_TARGET${NC}"
            ;;
        3)
            echo ""
            echo "📋 详细信息："
            echo "AppIcons目录中的文件："
            find "$APPICONS_DIR" -type f -name "*.png" | sort
            exit 0
            ;;
        *)
            echo -e "${RED}❌ 无效选项${NC}"
            exit 1
            ;;
    esac
fi

# 备份现有图标
if [ -d "$APPICON_TARGET" ]; then
    BACKUP_DIR="$APPICON_TARGET.backup.$(date +%Y%m%d_%H%M%S)"
    echo ""
    echo "📦 备份现有图标到: $BACKUP_DIR"
    cp -r "$APPICON_TARGET" "$BACKUP_DIR"
fi

# 复制图标文件
echo ""
echo "🚀 开始导入图标..."

# appicon.co生成的标准文件结构
# 通常包含以下文件：
# - AppIcon.appiconset/ (包含所有尺寸的图标和Contents.json)
# - Assets.xcassets/ (完整的资源目录)
# - AppIcon-iOS/ (iOS专用图标)

# 优先查找AppIcon.appiconset目录
if [ -d "$APPICONS_DIR/Assets.xcassets/AppIcon.appiconset" ]; then
    SOURCE_DIR="$APPICONS_DIR/Assets.xcassets/AppIcon.appiconset"
    echo "📂 使用源目录: $SOURCE_DIR"
    
    # 清空目标目录
    rm -rf "$APPICON_TARGET"/*
    
    # 复制所有文件
    cp -r "$SOURCE_DIR"/* "$APPICON_TARGET/"
    
    echo -e "${GREEN}✅ 图标复制完成${NC}"
    
elif [ -d "$APPICONS_DIR/AppIcon.appiconset" ]; then
    SOURCE_DIR="$APPICONS_DIR/AppIcon.appiconset"
    echo "📂 使用源目录: $SOURCE_DIR"
    
    rm -rf "$APPICON_TARGET"/*
    cp -r "$SOURCE_DIR"/* "$APPICON_TARGET/"
    
    echo -e "${GREEN}✅ 图标复制完成${NC}"
    
else
    # 手动映射常见的图标尺寸
    echo "📂 未找到标准AppIcon.appiconset，尝试手动映射..."
    
    # 创建Contents.json
    cat > "$APPICON_TARGET/Contents.json" << 'EOF'
{
  "images" : [
    {
      "filename" : "icon-20@2x.png",
      "idiom" : "iphone",
      "scale" : "2x",
      "size" : "20x20"
    },
    {
      "filename" : "icon-20@3x.png",
      "idiom" : "iphone",
      "scale" : "3x",
      "size" : "20x20"
    },
    {
      "filename" : "icon-29@2x.png",
      "idiom" : "iphone",
      "scale" : "2x",
      "size" : "29x29"
    },
    {
      "filename" : "icon-29@3x.png",
      "idiom" : "iphone",
      "scale" : "3x",
      "size" : "29x29"
    },
    {
      "filename" : "icon-40@2x.png",
      "idiom" : "iphone",
      "scale" : "2x",
      "size" : "40x40"
    },
    {
      "filename" : "icon-40@3x.png",
      "idiom" : "iphone",
      "scale" : "3x",
      "size" : "40x40"
    },
    {
      "filename" : "icon-60@2x.png",
      "idiom" : "iphone",
      "scale" : "2x",
      "size" : "60x60"
    },
    {
      "filename" : "icon-60@3x.png",
      "idiom" : "iphone",
      "scale" : "3x",
      "size" : "60x60"
    },
    {
      "filename" : "icon-1024.png",
      "idiom" : "ios-marketing",
      "scale" : "1x",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
EOF
    
    echo -e "${GREEN}✅ 创建Contents.json${NC}"
    
    # 查找并复制图标文件
    echo "🔍 查找图标文件..."
    find "$APPICONS_DIR" -type f -name "*.png" | while read file; do
        filename=$(basename "$file")
        echo "  - 找到: $filename"
        cp "$file" "$APPICON_TARGET/"
    done
    
    # 尝试使用sips重命名和调整大小
    echo ""
    echo "🔧 尝试适配图标尺寸..."
    
    # 查找1024x1024的图标作为源文件
    MASTER_ICON=$(find "$APPICON_TARGET" -type f -name "*.png" -exec file {} \; | grep "1024 x 1024" | cut -d: -f1 | head -1)
    
    if [ -n "$MASTER_ICON" ]; then
        echo "📸 找到主图标: $(basename "$MASTER_ICON")"
        cp "$MASTER_ICON" "$APPICON_TARGET/icon-1024.png"
        
        # 生成其他尺寸
        sips -z 40 40 "$MASTER_ICON" --out "$APPICON_TARGET/icon-20@2x.png" >/dev/null 2>&1
        sips -z 60 60 "$MASTER_ICON" --out "$APPICON_TARGET/icon-20@3x.png" >/dev/null 2>&1
        sips -z 58 58 "$MASTER_ICON" --out "$APPICON_TARGET/icon-29@2x.png" >/dev/null 2>&1
        sips -z 87 87 "$MASTER_ICON" --out "$APPICON_TARGET/icon-29@3x.png" >/dev/null 2>&1
        sips -z 80 80 "$MASTER_ICON" --out "$APPICON_TARGET/icon-40@2x.png" >/dev/null 2>&1
        sips -z 120 120 "$MASTER_ICON" --out "$APPICON_TARGET/icon-40@3x.png" >/dev/null 2>&1
        sips -z 120 120 "$MASTER_ICON" --out "$APPICON_TARGET/icon-60@2x.png" >/dev/null 2>&1
        sips -z 180 180 "$MASTER_ICON" --out "$APPICON_TARGET/icon-60@3x.png" >/dev/null 2>&1
        
        echo -e "${GREEN}✅ 生成各尺寸图标完成${NC}"
    else
        echo -e "${YELLOW}⚠️  未找到1024x1024主图标，请手动调整${NC}"
    fi
fi

# 验证结果
echo ""
echo "📊 导入结果验证:"
echo "目标目录: $APPICON_TARGET"
echo ""
echo "文件列表:"
ls -lh "$APPICON_TARGET"

# 统计图标数量
PNG_COUNT=$(find "$APPICON_TARGET" -name "*.png" | wc -l | xargs)
JSON_COUNT=$(find "$APPICON_TARGET" -name "*.json" | wc -l | xargs)

echo ""
echo "📈 统计:"
echo "  PNG图标: $PNG_COUNT 个"
echo "  JSON配置: $JSON_COUNT 个"

if [ $PNG_COUNT -ge 8 ] && [ $JSON_COUNT -ge 1 ]; then
    echo ""
    echo -e "${GREEN}✅ 图标导入成功！${NC}"
    echo ""
    echo "🎯 下一步操作："
    echo "  1. 在Xcode中打开项目"
    echo "  2. 导航到 Assets.xcassets"
    echo "  3. 检查AppIcon是否正确显示"
    echo "  4. 清理项目 (Cmd+Shift+K)"
    echo "  5. 重新构建项目 (Cmd+B)"
    echo "  6. 运行应用查看新图标"
else
    echo ""
    echo -e "${YELLOW}⚠️  警告: 图标数量可能不足${NC}"
    echo "建议检查AppIcons目录的内容"
fi

echo ""
echo "================================"
echo "完成时间: $(date '+%Y-%m-%d %H:%M:%S')"
