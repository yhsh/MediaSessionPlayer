package com.demo.service;


import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media.MediaBrowserServiceCompat;

import com.demo.cdmusic.CurrentMusic;
import com.demo.data.MusicLib;
import com.demo.global.BaseApplication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * author:杨旭东
 * 创建时间:2021/9/26
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MusicService extends MediaBrowserServiceCompat {
    private static final String TAG = "MusicService";
    private final String MEDIA_ID_ROOT = "MEDIA_ID_ROOT";

    // 音频服务
    private AudioManager audioManager;

    /**
     * 媒体会话，即受控端，通过设置MediaSessionCompat.Callback回调来接收媒体控制器
     * MediaController发送的指令，当收到指令时会触发Callback中各个指令对应的回调方法
     * （回调方法中会执行播放器相应的操作，如播放、暂停等）。Session一般在
     * Service.onCreate方法中创建，最后需调用setSessionToken方法设置用于和控制器配对
     * 的令牌并通知浏览器连接服务成功
     */
    private MediaSessionCompat mSession;
    private PlaybackStateCompat mPlaybackState;
    // 响应控制器指令的回调
    private MediaSessionCompat.Callback sessionCallback;
    // 音乐列表
    private MusicLib mMusicLib;
    // 从系统文件中扫描到的音乐文件信息

    // 音乐播放器
    private MediaPlayer mMediaPlayer;

    // 监听mediaplayer.prepare
    private MediaPlayer.OnPreparedListener preparedListener;
    // 监听播放结束事件
    private MediaPlayer.OnCompletionListener completionListener;
    private List<MediaBrowserCompat.MediaItem> mediaItems;

    // 音频焦点监听器
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    // 失去焦点前状态
    private int mediaStateBeforeRequestAudio = CurrentMusic.FOCUSNOMATTER;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onCreate() {
        super.onCreate();

        audioManager = (AudioManager) BaseApplication.sContext.getSystemService(Context.AUDIO_SERVICE);

        mMusicLib = MusicLib.getInstance();
        mediaItems = new ArrayList<>();
        // 对所有监听器初始化
        initAllListener();

        // 返回给客户端播放状态
        mPlaybackState = new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .build();

        mSession = new MediaSessionCompat(this, "MusicService");
        mSession.setCallback(sessionCallback); // 接受来自客户端的回调
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setPlaybackState(mPlaybackState);
        // 设置token后会触发MediaBrowser.ConnectionCallback的回调方法，表示连接成功
        setSessionToken(mSession.getSessionToken());

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setOnPreparedListener(preparedListener);
        mMediaPlayer.setOnCompletionListener(completionListener);

    }

    private void initAllListener() {
        // 音频监听器
        audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                Log.d(TAG, "onAudioFocusChange: 焦点改变了");
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: {
                        // 暂时失去焦点，调低音量,先暂停掉
                        // audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,-1,AudioManager.ADJUST_LOWER);
                    }
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: {
                        // 暂时失去焦点，暂停播放
                        Log.d(TAG, "onAudioFocusChange: 暂时失去焦点");
                        if (mMediaPlayer.isPlaying()) {
                            mMediaPlayer.pause();
                            PlaybackStateCompat state = new PlaybackStateCompat.Builder()
                                    .setState(PlaybackStateCompat.STATE_PAUSED, mMediaPlayer.getCurrentPosition(), 1.0f)
                                    .build();
                            mSession.setPlaybackState(state);
                            mediaStateBeforeRequestAudio = CurrentMusic.LOSSFOCUSPAUSE;
                        }
                    }
                    break;
                    case AudioManager.AUDIOFOCUS_LOSS: {
                        // 永久失去焦点,停止服务
                        Log.d(TAG, "onAudioFocusChange: 永久失去焦点");
                        audioManager.abandonAudioFocus(audioFocusChangeListener);
                        stopSelf();
                    }
                    break;
                    case AudioManager.AUDIOFOCUS_GAIN: {
                        // 再次获取焦点,根据失去焦点前的状态进行恢复
                        Log.d(TAG, "onAudioFocusChange: 获取焦点");
                        if (mediaStateBeforeRequestAudio == CurrentMusic.LOSSFOCUSPAUSE || mediaStateBeforeRequestAudio == CurrentMusic.REQUESTFOCUS) {
                            // 失去焦点前正在播放，因此暂停播放
                            mMediaPlayer.start();
                            mediaStateBeforeRequestAudio = CurrentMusic.FOCUSNOMATTER;
                            // 通知客户端恢复
                            PlaybackStateCompat stateCompat = new PlaybackStateCompat.Builder()
                                    .setState(PlaybackStateCompat.STATE_PLAYING, mMediaPlayer.getCurrentPosition(), 1.0f).build();
                            mSession.setPlaybackState(stateCompat);
                        }
                    }
                    break;
                    default:
                        break;
                }

            }

        };
        // 处理来自客户端的命令
        sessionCallback = new MediaSessionCompat.Callback() {
            // client click to skip next sing
            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                Log.d(TAG, "onSkipToNext: 跳转下一首");
                MediaMetadataCompat next = mMusicLib.getNext();
                if (next != null) {
                    mSession.setMetadata(next);
                    String path = next.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
                    try {
                        mMediaPlayer.reset();
                        mMediaPlayer.setDataSource(path);
                        // 先申请音频焦点
                        if (!requetFocus()) {
                            mediaStateBeforeRequestAudio = CurrentMusic.REQUESTFOCUS;
                            Log.d(TAG, "焦点被占用");
                            // 跳转到下一首，但是不能播放
                            mPlaybackState = new PlaybackStateCompat.Builder()
                                    .setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, 0, 1.0f)
                                    .build();
                            mSession.setPlaybackState(mPlaybackState);
                            mPlaybackState = new PlaybackStateCompat.Builder()
                                    .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                                    .build();
                            mSession.setPlaybackState(mPlaybackState);
                        } else {
                            mMediaPlayer.prepare();
                            mMediaPlayer.start();
                            mPlaybackState = new PlaybackStateCompat.Builder()
                                    .setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, 0, 1.0f)
                                    .build();
                            mSession.setPlaybackState(mPlaybackState);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                Log.d(TAG, "onSkipToPrevious: 跳转上一曲");
                MediaMetadataCompat previous = mMusicLib.getPrevious();
                if (previous == null) {
                    return;
                }
                mSession.setMetadata(previous);
                String path = previous.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
                try {
                    mMediaPlayer.reset();
                    mMediaPlayer.setDataSource(path);
                    // 先申请音频焦点
                    if (!requetFocus()) {
                        mediaStateBeforeRequestAudio = CurrentMusic.REQUESTFOCUS;
                        Log.d(TAG, "焦点被占用");
                        // 跳转到下一首，但是不能播放
                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, 0, 1.0f)
                                .build();
                        mSession.setPlaybackState(mPlaybackState);
                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                                .build();
                        mSession.setPlaybackState(mPlaybackState);
                    } else {
                        mMediaPlayer.prepare();
                        mMediaPlayer.start();
                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, 0, 1.0f)
                                .build();
                        mSession.setPlaybackState(mPlaybackState);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            // 客户端点击进度条
            @Override
            public void onSeekTo(long pos) {
                super.onSeekTo(pos);
                // 如果没有申请到焦点，能跳转到对应位置，但是不能播放
                if (!requetFocus()) {
                    mMediaPlayer.seekTo((int) pos);
                    mPlaybackState = new PlaybackStateCompat.Builder()
                            .setState(PlaybackStateCompat.STATE_PAUSED, pos, 1.0f)
                            .build();
                    mSession.setPlaybackState(mPlaybackState);
                } else {
                    mMediaPlayer.seekTo((int) pos);
                    mPlaybackState = new PlaybackStateCompat.Builder()
                            .setState(PlaybackStateCompat.STATE_PLAYING, pos, 1.0f)
                            .build();
                    mSession.setPlaybackState(mPlaybackState);
                }

            }

            // 响应MediaController.getTranportControllers().play()
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onPlay() {
                if (mPlaybackState.getState() == PlaybackStateCompat.STATE_NONE && requetFocus()) {
                    try {
                        MediaMetadataCompat first = mMusicLib.getCurrent();
                        if (first == null) {
                            return;
                        }
                        String path = first.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
                        mMediaPlayer.reset();
                        mMediaPlayer.setDataSource(path);
                        mMediaPlayer.prepare();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mMediaPlayer.start();
                    mPlaybackState = new PlaybackStateCompat.Builder()
                            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                            .build();
                    mSession.setPlaybackState(mPlaybackState);

                }
                // 如果当前处于暂停状态，那么播放它,客户端通过调用play会到达这里
                if (mPlaybackState.getState() == PlaybackStateCompat.STATE_PAUSED) {
                    if (requetFocus()) {
                        Log.d(TAG, "onPlay: 将处于暂停状态的音乐开始播放");
                        mMediaPlayer.start();
                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_PLAYING, mMediaPlayer.getCurrentPosition(), 1.0f)
                                .build();
                        mSession.setPlaybackState(mPlaybackState);
                    }
                }
            }

            @Override
            public void onPause() {
                mMediaPlayer.pause();
                mPlaybackState = new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PAUSED, 0, 1.0f)
                        .build();
                mSession.setPlaybackState(mPlaybackState);
            }

            @Override
            public void onPlayFromUri(Uri uri, Bundle extras) {
                Log.d(TAG, "onPlayFromUri");
                try {
                    switch (mPlaybackState.getState()) {
                        case PlaybackStateCompat.STATE_PLAYING:
                        case PlaybackStateCompat.STATE_PAUSED:
                        case PlaybackStateCompat.STATE_NONE: {
                            mMediaPlayer.reset();
                            String path = uri.getPath();
                            String authority = uri.getAuthority();
                            String scheme = uri.getScheme();
                            int port = uri.getPort();
                            String userInfo = uri.getUserInfo();
                            String host = uri.getHost();
                            Log.d("打印参数1", path + "");
                            Log.d("打印参数2", authority + "");
                            Log.d("打印参数3", scheme + "");
                            Log.d("打印参数4", port + "=");
                            Log.d("打印参数5", userInfo + "=");
                            Log.d("打印参数6", host + "=");
//                            mMediaPlayer.setDataSource(authority + "/" + host);
                            mMediaPlayer.setDataSource(MusicService.this, uri);
                            mMediaPlayer.prepare();
                            mPlaybackState = new PlaybackStateCompat.Builder()
                                    .setState(PlaybackStateCompat.STATE_CONNECTING, 0, 1.0f)
                                    .build();
                            mSession.setPlaybackState(mPlaybackState);
                            // 保存当前的音乐的信息，以便客户端刷新UI
                            mSession.setMetadata(new MediaMetadataCompat.Builder()
                                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, extras.getString("title"))
                                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, extras.getLong("duration"))
                                    .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, extras.getString("path"))
                                    .build());
                        }
                        break;
                        default:
                            break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        preparedListener = new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d(TAG, "onPrepared: 开始播放音乐");
                mp.start();
                mPlaybackState = new PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                        .build();
                mSession.setPlaybackState(mPlaybackState);
            }
        };
        // 当前音乐播放完毕，自动换下一首
        completionListener = new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                MediaMetadataCompat next = mMusicLib.getNext();
                if (next != null) {
                    String path = next.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
                    AssetFileDescriptor descriptor = null;
                    try {
                        mMediaPlayer.reset();
                        mMediaPlayer.setDataSource(path);
                        mMediaPlayer.prepare();
                        mMediaPlayer.start();

                        mPlaybackState = new PlaybackStateCompat.Builder()
                                .setState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT, 0, 1.0f)
                                .build();
                        mSession.setPlaybackState(mPlaybackState);
                        // 更新UI
                        mSession.setMetadata(next);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        };


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: 退出服务");
        mMediaPlayer.release();
        audioManager.abandonAudioFocus(audioFocusChangeListener);
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        Log.d(TAG, "onGetRoot:");
        return new BrowserRoot(clientPackageName, null);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        Log.d(TAG, "onLoadChildren");

        result.detach();
        scanMusic();
        // 向mediaBrower发送歌曲数据
        result.sendResult(mediaItems);
        /**
         * 将第一首歌设置到播放器里
         */
        MediaMetadataCompat first = mMusicLib.getCurrent();
        mSession.setMetadata(first);

    }

    @Override
    public void onCustomAction(String action, Bundle extras, MediaBrowserServiceCompat.Result<Bundle> result) {
        Log.d(TAG, "onCustomAction");
        result.detach();
        if ("1".equals(action)) {
            //播放指定歌曲
            int position = extras.getInt("song");
            mMusicLib.setCurrent(position);
            try {
                MediaMetadataCompat first = mMusicLib.getCurrent();
                //发送歌曲信息,更新播放界面UI
                mSession.setMetadata(first);
                if (first == null) {
                    return;
                }
                String path = first.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
                mMediaPlayer.reset();
                mMediaPlayer.setDataSource(path);
                mMediaPlayer.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mMediaPlayer.start();
            mPlaybackState = new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                    .build();
            mSession.setPlaybackState(mPlaybackState);
        }
        Bundle bundle = new Bundle();
        bundle.putString("action_key", "123");
        result.sendResult(bundle);
    }

    private MediaBrowserCompat.MediaItem createMediaItem(MediaMetadataCompat metadata) {
        return new MediaBrowserCompat.MediaItem(
                metadata.getDescription(),
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        );
    }

    // 扫描音乐信息
    private void scanMusic() {
        Log.d(TAG, "scanMusic: 开始扫描");
        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null, null);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                String display_name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME));
                // 如果不是mp3文件则跳过
                if (!display_name.endsWith("mp3") && !display_name.endsWith("m4a")) {
                    Log.d(TAG, "scanMusic: " + display_name);
                    cursor.moveToNext();
                    continue;
                }
                Log.d(TAG, "scanMusic: " + display_name);
                String name = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                int id = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media._ID));
                String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
                String singer = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
                int duration = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
                String path = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));

                MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
                //获取专辑封面
                try {
                    mediaMetadataRetriever.setDataSource(path);
                } catch (Exception e) {
                    Log.d(TAG, "scanMusic: 封面路径不存在");
                }
                Bitmap bp = null;
                byte[] picture = mediaMetadataRetriever.getEmbeddedPicture();
                if (picture != null) {
                    bp = BitmapFactory.decodeByteArray(picture, 0, picture.length);
                }

                MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, name)
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, String.valueOf(id))
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                        .putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, singer)
                        .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, path)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, display_name)
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bp)
                        .build();

                mMusicLib.add(metadata);
                MediaDescriptionCompat description = metadata.getDescription();

                mediaItems.add(new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));

                cursor.moveToNext();
            }
        }
        cursor.close();
    }

    // 申请Audio焦点
    private boolean requetFocus() {
        int result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

}
