package androidx.media3.demo.media;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频文件的"伴生字幕"自动加载工具。
 *
 * 主流播放器（VLC / MX / nPlayer）打开 Movie.mp4 时，
 * 会自动把同目录下同基名的字幕（Movie.srt / Movie.ass / Movie.zh.srt 等）作为外挂字幕加载。
 * 我们做同样的事：扫描同目录、同基名前缀的 .srt/.ass/.ssa/.vtt 文件，
 * 通过 MediaItem.Subtitle Configuration 一起塞给 ExoPlayer。
 *
 * 用法：把 MediaItem.fromUri(...) 换成 SubtitleAutoLoader.buildItem(file)。
 */
public final class SubtitleAutoLoader {

    private static final String[] SUB_EXTS = {".srt", ".ass", ".ssa", ".vtt"};

    private SubtitleAutoLoader() {}

    @NonNull
    public static MediaItem buildItem(@NonNull File videoFile) {
        Uri videoUri = Uri.fromFile(videoFile);
        List<MediaItem.SubtitleConfiguration> subs = findCompanionSubtitles(videoFile);
        MediaItem.Builder b = new MediaItem.Builder().setUri(videoUri);
        if (!subs.isEmpty()) {
            b.setSubtitleConfigurations(subs);
        }
        return b.build();
    }

    private static List<MediaItem.SubtitleConfiguration> findCompanionSubtitles(File videoFile) {
        List<MediaItem.SubtitleConfiguration> out = new ArrayList<>();
        File dir = videoFile.getParentFile();
        if (dir == null || !dir.isDirectory()) return out;
        String base = stripExt(videoFile.getName()).toLowerCase();

        File[] siblings = dir.listFiles();
        if (siblings == null) return out;
        for (File f : siblings) {
            if (!f.isFile()) continue;
            String lower = f.getName().toLowerCase();
            String ext = findSubtitleExt(lower);
            if (ext == null) continue;
            String nameNoExt = stripExt(lower);
            // 命中：完全同名（Movie.srt）或以基名加点开头（Movie.zh.srt / Movie.en.srt）
            if (!(nameNoExt.equals(base) || nameNoExt.startsWith(base + "."))) continue;

            String mime = mimeForExt(ext);
            // 语言标签：Movie.zh.srt -> zh；Movie.srt -> 空
            String language = null;
            if (nameNoExt.length() > base.length() + 1) {
                language = nameNoExt.substring(base.length() + 1);
                // 仅当看起来像短 BCP47 / ISO 语言时保留
                if (language.length() > 12) language = null;
            }

            MediaItem.SubtitleConfiguration.Builder sb =
                    new MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(f))
                            .setMimeType(mime)
                            .setSelectionFlags(0); // 不自动选中，用户自己在面板里挑
            if (language != null) sb.setLanguage(language);
            sb.setLabel(f.getName());
            out.add(sb.build());
        }
        return out;
    }

    private static String findSubtitleExt(String lower) {
        for (String ext : SUB_EXTS) {
            if (lower.endsWith(ext)) return ext;
        }
        return null;
    }

    private static String stripExt(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx > 0 ? filename.substring(0, idx) : filename;
    }

    private static String mimeForExt(String ext) {
        switch (ext) {
            case ".srt": return MimeTypes.APPLICATION_SUBRIP;
            case ".ass":
            case ".ssa": return MimeTypes.TEXT_SSA;
            case ".vtt": return MimeTypes.TEXT_VTT;
            default: return MimeTypes.APPLICATION_SUBRIP;
        }
    }
}
