package com.demo.data;

import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * author:杨旭东
 * 创建时间:2021/9/28
 */
public class MusicLib {

    // title
    private String title;
    // 列表
    public List<MediaMetadataCompat> musicList;
    // 当前音乐
    public int current;

    public void setCurrent(int current) {
        this.current = current;
    }

    private MusicLib() {
        current = 0;
        musicList = new ArrayList<>();
    }

    private static MusicLib mMusicLib;

    public static MusicLib getInstance() {
        if (mMusicLib == null) {
            mMusicLib = new MusicLib();
        }
        return mMusicLib;
    }

    public MediaMetadataCompat getNext() {
        if (musicList.size() == 0) {
            return null;
        }
        if (current < musicList.size() - 1) {
            current++;
        } else {
            current = 0;
        }
        return musicList.get(current);
    }

    public MediaMetadataCompat getPrevious() {
        if (musicList.size() == 0) {
            return null;
        }
        if (current == 0) {
            current = musicList.size() - 1;
        } else {
            current -= 1;
        }
        return musicList.get(current);
    }

    public void add(MediaMetadataCompat mediaItem) {
        musicList.add(mediaItem);
    }

    public MediaMetadataCompat getCurrent() {
        if (musicList.size() == 0) {
            return null;
        } else {
            return musicList.get(current);
        }
    }
}
