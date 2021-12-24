package com.demo.data;

import android.graphics.Bitmap;

/**
 * author:杨旭东
 * 创建时间:2021/9/27
 */
public class MusicInfo {
    // 音乐名
    private String name;
    // 音乐id，根据id找到音乐播放
    private int id;
    // 封面
    private Bitmap bitmap;
    // 专辑名
    private String album;
    // 全路径
    private String url;
    // 总时长
    private int duration;

    public MusicInfo() {

    }

    public MusicInfo(String name, int id, Bitmap bitmap, String album, String url, int duration) {
        this.name = name;
        this.id = id;
        this.bitmap = bitmap;
        this.album = album;
        this.url = url;
        this.duration = duration;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
