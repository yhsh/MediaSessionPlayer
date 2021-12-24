package com.demo.cdmusic;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.PlaybackStateCompat;

/**
 * author:杨旭东
 * 创建时间:2021/11/24
 */
public class CurrentMusic {
    // 当前音乐的播放状态
    private PlaybackStateCompat currentMusicState;
    // 当前的音乐信息
    private MediaMetadataCompat currentMedia;
    // 希望播放，但是因为没有焦点而停止
    public static final int REQUESTFOCUS = 0;
    // 正在播放，但是因为失去焦点而暂停
    public static final int LOSSFOCUSPAUSE = 1;
    // 不处于播放状态
    public static final int FOCUSNOMATTER = 2;

    public CurrentMusic() {
        currentMedia = new MediaMetadataCompat.Builder().build();
        currentMusicState = new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f).build();
    }

    public PlaybackStateCompat getCurrentMusicState() {
        return currentMusicState;
    }

    public void setCurrentMusicState(PlaybackStateCompat currentMusicState) {
        this.currentMusicState = currentMusicState;
    }

    public MediaMetadataCompat getCurrentMedia() {
        return currentMedia;
    }

    public void setCurrentMedia(MediaMetadataCompat currentMedia) {
        this.currentMedia = currentMedia;
    }
}
