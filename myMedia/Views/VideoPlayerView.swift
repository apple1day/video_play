//
//  VideoPlayerView.swift
//  myMedia
//
//  Created by houjiaying on 2026/1/2.
//

import SwiftUI
import AVKit
import Photos

struct VideoPlayerView: View {
    @ObservedObject var videoManager: VideoManager
    @State private var currentIndex: Int
    @State private var player: AVPlayer?
    @State private var isPlaying = false
    @State private var dragOffset: CGFloat = 0
    @State private var showControls = true
    @State private var currentTime: Double = 0
    @State private var duration: Double = 0
    @State private var isDraggingSlider = false
    @GestureState private var isLongPressing = false
    
    private let controlsHideDelay: Double = 3.0
    @State private var controlsTimer: Timer?
    
    init(videoManager: VideoManager, startIndex: Int = 0) {
        self.videoManager = videoManager
        _currentIndex = State(initialValue: startIndex)
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black.ignoresSafeArea()
                
                // 视频播放器
                if let player = player {
                    VideoPlayer(player: player)
                        .ignoresSafeArea()
                        .onTapGesture {
                            toggleControls()
                        }
                } else {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                }
                
                // 顶部信息栏
                VStack {
                    topInfoBar
                        .opacity(showControls ? 1 : 0)
                    
                    Spacer()
                    
                    // 底部控制栏
                    if showControls {
                        bottomControlBar
                    }
                }
                .animation(.easeInOut(duration: 0.3), value: showControls)
            }
            .gesture(
                DragGesture()
                    .onChanged { value in
                        dragOffset = value.translation.height
                    }
                    .onEnded { value in
                        handleSwipe(translation: value.translation.height)
                        dragOffset = 0
                    }
            )
            .onAppear {
                loadVideo(at: currentIndex)
            }
            .onDisappear {
                player?.pause()
                controlsTimer?.invalidate()
            }
        }
    }
    
    // MARK: - 顶部信息栏
    private var topInfoBar: some View {
        HStack {
            VStack(alignment: .leading, spacing: 8) {
                if let video = currentVideo {
                    Text(video.name)
                        .font(.headline)
                        .foregroundColor(.white)
                    
                    HStack(spacing: 16) {
                        Label(formatFileSize(video.fileSize), systemImage: "doc.fill")
                        Label(formatDuration(video.duration), systemImage: "clock.fill")
                        Label(formatDate(video.creationDate), systemImage: "calendar")
                    }
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.8))
                }
            }
            
            Spacer()
            
            // 收藏和删除按钮
            HStack(spacing: 20) {
                Button(action: toggleFavorite) {
                    Image(systemName: currentVideo?.isFavorite == true ? "heart.fill" : "heart")
                        .font(.title2)
                        .foregroundColor(currentVideo?.isFavorite == true ? .red : .white)
                }
                
                Button(action: togglePendingDelete) {
                    Image(systemName: currentVideo?.isPendingDelete == true ? "trash.fill" : "trash")
                        .font(.title2)
                        .foregroundColor(currentVideo?.isPendingDelete == true ? .orange : .white)
                }
            }
        }
        .padding()
        .background(
            LinearGradient(
                gradient: Gradient(colors: [Color.black.opacity(0.6), Color.clear]),
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }
    
    // MARK: - 底部控制栏
    private var bottomControlBar: some View {
        VStack(spacing: 12) {
            // 进度条
            VStack(spacing: 4) {
                Slider(
                    value: Binding(
                        get: { isDraggingSlider ? currentTime : currentTime },
                        set: { newValue in
                            currentTime = newValue
                            if isDraggingSlider {
                                seekTo(newValue)
                            }
                        }
                    ),
                    in: 0...max(duration, 0.1),
                    onEditingChanged: { editing in
                        isDraggingSlider = editing
                        if !editing {
                            seekTo(currentTime)
                        }
                    }
                )
                .accentColor(.white)
                
                // 时间显示
                HStack {
                    Text(formatTime(currentTime))
                        .font(.caption)
                        .foregroundColor(.white)
                    
                    Spacer()
                    
                    Text(formatTime(duration))
                        .font(.caption)
                        .foregroundColor(.white)
                }
            }
            .padding(.horizontal)
            
            // 播放控制按钮
            HStack(spacing: 40) {
                Button(action: playPreviousVideo) {
                    Image(systemName: "backward.fill")
                        .font(.title)
                        .foregroundColor(.white)
                }
                .disabled(currentIndex == 0)
                .opacity(currentIndex == 0 ? 0.3 : 1)
                
                Button(action: togglePlayPause) {
                    Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 50))
                        .foregroundColor(.white)
                }
                
                Button(action: playNextVideo) {
                    Image(systemName: "forward.fill")
                        .font(.title)
                        .foregroundColor(.white)
                }
                .disabled(currentIndex >= videoManager.videos.count - 1)
                .opacity(currentIndex >= videoManager.videos.count - 1 ? 0.3 : 1)
            }
        }
        .padding()
        .background(
            LinearGradient(
                gradient: Gradient(colors: [Color.clear, Color.black.opacity(0.6)]),
                startPoint: .top,
                endPoint: .bottom
            )
        )
    }
    
    // MARK: - Helper Properties
    private var currentVideo: VideoItem? {
        guard currentIndex >= 0 && currentIndex < videoManager.videos.count else {
            return nil
        }
        return videoManager.videos[currentIndex]
    }
    
    // MARK: - Video Control Functions
    private func loadVideo(at index: Int) {
        guard index >= 0 && index < videoManager.videos.count else { return }
        currentIndex = index
        
        let video = videoManager.videos[index]
        let options = PHVideoRequestOptions()
        options.isNetworkAccessAllowed = true
        options.deliveryMode = .highQualityFormat
        
        PHImageManager.default().requestAVAsset(forVideo: video.asset, options: options) { asset, _, _ in
            guard let urlAsset = asset as? AVURLAsset else { return }
            
            DispatchQueue.main.async {
                let playerItem = AVPlayerItem(asset: urlAsset)
                self.player = AVPlayer(playerItem: playerItem)
                self.duration = CMTimeGetSeconds(urlAsset.duration)
                
                // 添加时间观察器
                self.player?.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { time in
                    if !self.isDraggingSlider {
                        self.currentTime = CMTimeGetSeconds(time)
                    }
                }
                
                // 自动播放
                self.player?.play()
                self.isPlaying = true
                self.resetControlsTimer()
                
                // 监听播放结束
                NotificationCenter.default.addObserver(
                    forName: .AVPlayerItemDidPlayToEndTime,
                    object: playerItem,
                    queue: .main
                ) { _ in
                    self.playNextVideo()
                }
            }
        }
    }
    
    private func togglePlayPause() {
        guard let player = player else { return }
        
        if isPlaying {
            player.pause()
        } else {
            player.play()
        }
        isPlaying.toggle()
        resetControlsTimer()
    }
    
    private func seekTo(_ time: Double) {
        let cmTime = CMTime(seconds: time, preferredTimescale: 600)
        player?.seek(to: cmTime, toleranceBefore: .zero, toleranceAfter: .zero)
    }
    
    private func playNextVideo() {
        guard currentIndex < videoManager.videos.count - 1 else { return }
        player?.pause()
        loadVideo(at: currentIndex + 1)
    }
    
    private func playPreviousVideo() {
        guard currentIndex > 0 else { return }
        player?.pause()
        loadVideo(at: currentIndex - 1)
    }
    
    private func handleSwipe(translation: CGFloat) {
        if translation < -50 { // 向上滑动
            playNextVideo()
        } else if translation > 50 { // 向下滑动
            playPreviousVideo()
        }
    }
    
    // MARK: - UI Control Functions
    private func toggleControls() {
        showControls.toggle()
        if showControls {
            resetControlsTimer()
        } else {
            controlsTimer?.invalidate()
        }
    }
    
    private func resetControlsTimer() {
        controlsTimer?.invalidate()
        controlsTimer = Timer.scheduledTimer(withTimeInterval: controlsHideDelay, repeats: false) { _ in
            if isPlaying {
                withAnimation {
                    showControls = false
                }
            }
        }
    }
    
    private func toggleFavorite() {
        guard let video = currentVideo else { return }
        videoManager.toggleFavorite(video)
    }
    
    private func togglePendingDelete() {
        guard let video = currentVideo else { return }
        videoManager.togglePendingDelete(video)
    }
    
    // MARK: - Format Functions
    private func formatFileSize(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
    
    private func formatDuration(_ duration: TimeInterval) -> String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }
    
    private func formatTime(_ time: Double) -> String {
        let minutes = Int(time) / 60
        let seconds = Int(time) % 60
        return String(format: "%d:%02d", minutes, seconds)
    }
}
