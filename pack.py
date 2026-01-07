#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Android 项目代码打包工具

功能：
1) 汇总 ./media 目录下所有源代码文件到 ./media.md
2) 解包 ./media.md 中的文件块到 ./unpacked/ 目录，保持原目录结构

用法：
  python pack.py pack    # 仅生成 media.md
  python pack.py unpack  # 仅解包到 unpacked/
  python pack.py         # 先 pack 再 unpack
"""

import re
import sys
from pathlib import Path

# 源目录（当前脚本同级的 media 目录）
ROOT = Path(__file__).resolve().parent / "media"
# 汇总输出文件
OUTPUT_MD = Path(__file__).resolve().parent / "media.md"
# 解包输出目录
UNPACK_DIR = Path(__file__).resolve().parent / "unpacked"

# 忽略的目录和文件
IGNORE = {
    # 构建输出
    "buildout",
    "build",
    "out",
    ".gradle",
    ".idea",
    ".cxx",
    
    # Git 和版本控制
    ".git",
    ".gitignore",
    
    # 依赖和缓存
    "node_modules",
    ".DS_Store",
    
    # 打包脚本相关
    "terminals",
    "unpacked",
    "media.md",
    
    # 二进制和资源（可选，根据需要调整）
    ".jar",
    ".aar",
    ".so",
    ".apk",
    ".dex",
}


def should_ignore(path: Path) -> bool:
    """判断是否应该忽略该路径"""
    # 检查路径名是否在忽略列表
    if path.name in IGNORE:
        return True
    
    # 检查文件扩展名是否在忽略列表
    if path.is_file() and path.suffix in IGNORE:
        return True
    
    # 检查是否在构建输出目录中
    try:
        rel_path = path.relative_to(ROOT)
        parts = rel_path.parts
        for part in parts:
            if part in IGNORE:
                return True
    except ValueError:
        pass
    
    return False


def walk(dir_path: Path):
    """递归遍历目录，收集所有需要打包的文件"""
    res = []
    try:
        for p in sorted(dir_path.iterdir()):
            if should_ignore(p):
                continue
            if p.is_dir():
                res.extend(walk(p))
            elif p.is_file():
                # 只收集源代码和配置文件
                if is_source_file(p):
                    res.append(p)
    except PermissionError:
        print(f"[WARN] 无权限访问: {dir_path}")
    return res


def to_rel(p: Path) -> str:
    return p.relative_to(ROOT).as_posix()


def is_source_file(file: Path) -> bool:
    """判断是否是源代码或配置文件"""
    ext = file.suffix.lower()
    source_extensions = {
        # 代码文件
        ".java", ".kt", ".xml", ".gradle", 
        # 配置文件
        ".properties", ".pro", ".txt",
        # 文档
        ".md", ".rst",
        # 脚本
        ".sh", ".bat", ".py",
        # 其他
        ".json", ".yaml", ".yml"
    }
    
    # 特殊文件名（无扩展名）
    special_files = {
        "AndroidManifest", "gradlew", "proguard-rules"
    }
    
    return ext in source_extensions or file.stem in special_files


def detect_lang(file: Path) -> str:
    """检测文件类型并返回对应的语言标识"""
    ext = file.suffix.lstrip(".").lower()
    if not ext:
        # 特殊处理无扩展名的文件
        if "gradlew" in file.name:
            return "bash"
        return "text"
    
    # 语言映射
    lang_map = {
        "java": "java",
        "kt": "kotlin",
        "xml": "xml",
        "gradle": "gradle",
        "properties": "properties",
        "pro": "text",
        "txt": "text",
        "md": "markdown",
        "rst": "rst",
        "sh": "bash",
        "bat": "batch",
        "py": "python",
        "json": "json",
        "yaml": "yaml",
        "yml": "yaml",
    }
    
    return lang_map.get(ext, "text")


def read_file_text(file: Path) -> str:
    try:
        return file.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        # 对非 UTF-8 文件，尽量用 latin-1 保留字节；如仍失败则返回空串
        try:
            return file.read_text(encoding="latin-1")
        except Exception:
            return ""


def generate_all_md():
    """生成汇总 Markdown 文件"""
    if not ROOT.exists():
        print(f"[ERROR] 源目录不存在: {ROOT}")
        return
    
    print(f"[INFO] 开始扫描目录: {ROOT}")
    files = walk(ROOT)
    files = sorted(files, key=lambda x: x.as_posix())
    
    if not files:
        print("[WARN] 未找到任何源文件")
        return
    
    OUTPUT_MD.parent.mkdir(parents=True, exist_ok=True)
    
    # 生成文件头
    parts = [
        "# Media 项目代码汇总\n",
        f"生成时间: {Path(__file__).stat().st_mtime}\n",
        f"源目录: {ROOT}\n",
        f"文件数量: {len(files)}\n",
        "---\n"
    ]
    
    # 统计信息
    file_types = {}
    total_size = 0
    
    for file in files:
        rel = to_rel(file)
        lang = detect_lang(file)
        content = read_file_text(file)
        
        # 统计
        file_size = file.stat().st_size
        total_size += file_size
        file_types[lang] = file_types.get(lang, 0) + 1
        
        # 添加文件内容
        parts.append(f"## {rel}\n")
        parts.append(f"<!-- 大小: {file_size:,} bytes -->\n")
        parts.append(f"```{lang}\n{content}\n```\n")
    
    # 添加统计信息到文件开头
    stats = ["\n## 统计信息\n"]
    stats.append(f"- 总文件数: {len(files)}\n")
    stats.append(f"- 总大小: {total_size:,} bytes ({total_size/1024/1024:.2f} MB)\n")
    stats.append(f"- 文件类型分布:\n")
    for lang, count in sorted(file_types.items(), key=lambda x: x[1], reverse=True):
        stats.append(f"  - {lang}: {count} 个文件\n")
    stats.append("\n---\n")
    
    # 插入统计信息
    parts = parts[:5] + stats + parts[5:]
    
    OUTPUT_MD.write_text("\n".join(parts), encoding="utf-8")
    print(f"[OK] 生成汇总: {OUTPUT_MD}")
    print(f"[OK] 共 {len(files)} 个文件，总大小 {total_size/1024/1024:.2f} MB")
    print(f"[OK] 文件类型: {', '.join(f'{k}({v})' for k, v in file_types.items())}")


def unpack_all_md():
    """从 Markdown 文件中解包所有文件"""
    if not OUTPUT_MD.exists():
        print(f"[WARN] 未找到文件: {OUTPUT_MD}，跳过解包")
        return
    
    print(f"[INFO] 开始解包: {OUTPUT_MD}")
    md = OUTPUT_MD.read_text(encoding="utf-8")
    
    # 匹配文件块：
    # ## path/to/file
    # <!-- 大小: xxx bytes --> (可选)
    # ```lang
    # ...content...
    # ```
    re_block = re.compile(
        r"^##\s+(.+?)\s*\n(?:<!--.*?-->\n)?```.*?\n([\s\S]*?)\n```", 
        re.M
    )
    
    count = 0
    errors = []
    
    for m in re_block.finditer(md):
        rel_path = m.group(1).strip()
        content = m.group(2)
        
        # 跳过统计信息等非文件块
        if rel_path.startswith("统计信息") or "---" in rel_path:
            continue
        
        try:
            out_path = UNPACK_DIR / rel_path
            out_path.parent.mkdir(parents=True, exist_ok=True)
            out_path.write_text(content, encoding="utf-8")
            count += 1
        except Exception as e:
            errors.append(f"{rel_path}: {e}")
    
    print(f"[OK] 解包完成，输出目录: {UNPACK_DIR}")
    print(f"[OK] 共写入 {count} 个文件")
    
    if errors:
        print(f"[WARN] {len(errors)} 个文件解包失败:")
        for err in errors[:10]:  # 只显示前10个错误
            print(f"  - {err}")


def main():
    """主函数"""
    print("=" * 60)
    print("Android 项目代码打包工具")
    print("=" * 60)
    
    mode = sys.argv[1].lower() if len(sys.argv) > 1 else None
    
    if mode in (None, "pack"):
        print("\n[STEP] 开始打包...")
        generate_all_md()
    
    if mode in (None, "unpack"):
        print("\n[STEP] 开始解包...")
        unpack_all_md()
    
    print("\n" + "=" * 60)
    print("完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()


# ============================================================
# 使用示例
# ============================================================
# 仅打包：python pack.py pack
# 仅解包：python pack.py unpack  
# 打包+解包：python pack.py
#
# 使用虚拟环境：
# D:\zcode\anxiong\.venv\Scripts\python.exe pack.py pack
# D:\zcode\anxiong\.venv\Scripts\python.exe pack.py unpack