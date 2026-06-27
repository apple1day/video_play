package androidx.media3.demo.media;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 字幕 / 多音轨 / 倍速 三合一控制面板（BottomSheet）。
 *
 * 设计：
 *  - 通过外部 attach(player) 传入当前 ExoPlayer；面板不持有播放器生命周期。
 *  - 字幕 / 音轨 列表来自 player.getCurrentTracks()，支持选择 + 关闭字幕。
 *  - 倍速使用预设值，通过 PlayerActivity 中已有的 PlaybackSpeedHelper 保存。
 */
public class TrackBottomSheet extends BottomSheetDialogFragment {

    private static final int TAB_SUBTITLE = 0;
    private static final int TAB_AUDIO = 1;
    private static final int TAB_SPEED = 2;

    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};
    private static final String[] SPEED_LABELS = {"0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "1.75x", "2.0x"};

    @Nullable private ExoPlayer player;
    private RecyclerView list;
    private TrackAdapter adapter;
    private int currentTab = TAB_SUBTITLE;

    public void attach(ExoPlayer p) { this.player = p; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_track, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TabLayout tabs = view.findViewById(R.id.track_tabs);
        list = view.findViewById(R.id.track_list);

        tabs.addTab(tabs.newTab().setText("字幕"));
        tabs.addTab(tabs.newTab().setText("音轨"));
        tabs.addTab(tabs.newTab().setText("倍速"));

        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TrackAdapter();
        list.setAdapter(adapter);

        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                refresh();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        refresh();
    }

    private void refresh() {
        if (adapter == null) return;
        switch (currentTab) {
            case TAB_SUBTITLE:
                adapter.submit(collectTextTracks(), buildSubtitleHandler());
                break;
            case TAB_AUDIO:
                adapter.submit(collectAudioTracks(), buildAudioHandler());
                break;
            case TAB_SPEED:
            default:
                adapter.submit(collectSpeedItems(), buildSpeedHandler());
                break;
        }
    }

    // ---------------- 字幕 ----------------

    private List<TrackItem> collectTextTracks() {
        List<TrackItem> out = new ArrayList<>();
        // 第一项总是"关闭字幕"
        boolean textDisabled = player == null
                || player.getTrackSelectionParameters().disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                || !hasAnySelectedText();
        out.add(new TrackItem("关闭字幕", "", textDisabled, -1, -1));
        if (player == null) return out;
        Tracks tracks = player.getCurrentTracks();
        int groupIdx = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) { groupIdx++; continue; }
            TrackGroup tg = group.getMediaTrackGroup();
            for (int t = 0; t < tg.length; t++) {
                Format f = tg.getFormat(t);
                String title = formatLanguageOrLabel(f, "字幕轨 " + (t + 1));
                String sub = nonNull(f.sampleMimeType);
                boolean selected = !textDisabled && group.isTrackSelected(t);
                out.add(new TrackItem(title, sub, selected, groupIdx, t));
            }
            groupIdx++;
        }
        return out;
    }

    private boolean hasAnySelectedText() {
        if (player == null) return false;
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) return true;
                }
            }
        }
        return false;
    }

    private OnTrackPickHandler buildSubtitleHandler() {
        return (pos, item) -> {
            if (player == null) return;
            TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
            if (item.groupIndex < 0) {
                // 关闭字幕
                b.clearOverridesOfType(C.TRACK_TYPE_TEXT);
                b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true);
            } else {
                Tracks tracks = player.getCurrentTracks();
                if (item.groupIndex < tracks.getGroups().size()) {
                    TrackGroup tg = tracks.getGroups().get(item.groupIndex).getMediaTrackGroup();
                    b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false);
                    b.setOverrideForType(new TrackSelectionOverride(tg, item.trackIndex));
                }
            }
            player.setTrackSelectionParameters(b.build());
            refresh();
        };
    }

    // ---------------- 音轨 ----------------

    private List<TrackItem> collectAudioTracks() {
        List<TrackItem> out = new ArrayList<>();
        if (player == null) {
            out.add(new TrackItem("无音轨", "", false, -1, -1));
            return out;
        }
        Tracks tracks = player.getCurrentTracks();
        int groupIdx = 0;
        boolean any = false;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) { groupIdx++; continue; }
            TrackGroup tg = group.getMediaTrackGroup();
            for (int t = 0; t < tg.length; t++) {
                any = true;
                Format f = tg.getFormat(t);
                String title = formatLanguageOrLabel(f, "音轨 " + (t + 1));
                String sub = (f.channelCount > 0 ? f.channelCount + "ch · " : "") + nonNull(f.sampleMimeType);
                boolean selected = group.isTrackSelected(t);
                out.add(new TrackItem(title, sub, selected, groupIdx, t));
            }
            groupIdx++;
        }
        if (!any) out.add(new TrackItem("当前视频只有一条音轨", "", true, -1, -1));
        return out;
    }

    private OnTrackPickHandler buildAudioHandler() {
        return (pos, item) -> {
            if (player == null || item.groupIndex < 0) return;
            Tracks tracks = player.getCurrentTracks();
            if (item.groupIndex >= tracks.getGroups().size()) return;
            TrackGroup tg = tracks.getGroups().get(item.groupIndex).getMediaTrackGroup();
            TrackSelectionParameters.Builder b = player.getTrackSelectionParameters().buildUpon();
            b.setOverrideForType(new TrackSelectionOverride(tg, item.trackIndex));
            player.setTrackSelectionParameters(b.build());
            refresh();
        };
    }

    // ---------------- 倍速 ----------------

    private List<TrackItem> collectSpeedItems() {
        List<TrackItem> out = new ArrayList<>();
        float current = player != null ? player.getPlaybackParameters().speed : 1.0f;
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            boolean selected = Math.abs(SPEED_OPTIONS[i] - current) < 0.01f;
            out.add(new TrackItem(SPEED_LABELS[i],
                    SPEED_OPTIONS[i] == 1.0f ? "正常" : "",
                    selected, -2, i));
        }
        return out;
    }

    private OnTrackPickHandler buildSpeedHandler() {
        return (pos, item) -> {
            if (player == null) return;
            float newSpeed = SPEED_OPTIONS[item.trackIndex];
            player.setPlaybackParameters(new PlaybackParameters(newSpeed));
            // 复用已有的持久化
            Context ctx = getContext();
            if (ctx != null) PlaybackSpeedHelper.setPlaybackSpeed(ctx, player, newSpeed);
            Toast.makeText(getContext(), "倍速: " + SPEED_LABELS[item.trackIndex], Toast.LENGTH_SHORT).show();
            refresh();
        };
    }

    // ---------------- adapter ----------------

    private String formatLanguageOrLabel(Format f, String fallback) {
        if (f.label != null) return f.label;
        if (f.language != null && !"und".equalsIgnoreCase(f.language)) return f.language;
        return fallback;
    }

    private String nonNull(String s) { return s == null ? "" : s; }

    interface OnTrackPickHandler {
        void onPick(int position, TrackItem item);
    }

    static class TrackItem {
        final String title;
        final String subtitle;
        final boolean selected;
        final int groupIndex; // -1: 关闭字幕 / 无可选；-2: 倍速行；其它为 currentTracks 中 group 索引
        final int trackIndex;
        TrackItem(String t, String s, boolean sel, int gi, int ti) {
            title = t; subtitle = s; selected = sel; groupIndex = gi; trackIndex = ti;
        }
    }

    private class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.VH> {
        private final List<TrackItem> data = new ArrayList<>();
        @Nullable private OnTrackPickHandler handler;

        void submit(List<TrackItem> items, OnTrackPickHandler h) {
            data.clear();
            data.addAll(items);
            this.handler = h;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_track, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            TrackItem it = data.get(position);
            h.title.setText(it.title);
            h.title.setTextColor(it.selected ? Color.parseColor("#FF6B35") : Color.WHITE);
            h.subtitle.setText(it.subtitle);
            h.check.setVisibility(it.selected ? View.VISIBLE : View.INVISIBLE);
            boolean clickable = !(it.groupIndex == -1 && data.size() == 1) ; // 占位提示禁点
            h.itemView.setOnClickListener(clickable && handler != null
                    ? v -> handler.onPick(position, it) : null);
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView title, subtitle;
            final ImageView check;
            VH(@NonNull View v) {
                super(v);
                title = v.findViewById(R.id.title);
                subtitle = v.findViewById(R.id.subtitle);
                check = v.findViewById(R.id.check);
            }
        }
    }
}
