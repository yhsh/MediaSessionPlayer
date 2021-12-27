package com.demo.cdmusic;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.demo.data.MusicLib;
import com.demo.service.MusicService;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // 保存服务端发送过来的媒体数据
    private List<MediaBrowserCompat.MediaItem> list;

    private MediaBrowserCompat mBrowser;

    private MediaControllerCompat mController;

    private DemoAdapter adapter;

    private Button btnPlay;

    private TextView textTitle;

    // 上一曲下一曲图片等
    private Button btnPrevious;

    private Button btnNext;

    private ImageView imageView;

    private TextView startTime;
    private TextView endTime;

    private SeekBar musicProgres;

    private Timer timer;

    private List<String> musicList;
    // 更新歌曲信息
    private final int UPDATEUI = 0;
    // 歌曲播放完毕
    private final int MUSICFINISH = 1;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.arg1) {
                case UPDATEUI: {
                    String sTime = msg.obj.toString();
                    int iTime = msg.arg2;
                    if (iTime >= currentMusicInfo.getCurrentMedia().getLong(MediaMetadataCompat.METADATA_KEY_DURATION)) {
                        timer.cancel();
                        return;
                    }
                    musicProgres.setProgress(iTime);
                    startTime.setText(sTime);
                }
                break;
                case MUSICFINISH: {
                    timer.cancel();
                }
                break;
            }
        }
    };
    private CurrentMusic currentMusicInfo;

    // 连接状态的回调函数
    private MediaBrowserCompat.ConnectionCallback BrowserConnectionCallback;
    // 订阅的回调函数
    private MediaBrowserCompat.SubscriptionCallback BrowserSubscriptionCallback;
    // 媒体控制器的回调接口，通过它来控制UI的状态
    private MediaControllerCompat.Callback controllerCallback;

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timer = new Timer();
        currentMusicInfo = new CurrentMusic();
        musicList = new ArrayList<>();

        btnPlay = findViewById(R.id.btn_play);
        btnPrevious = findViewById(R.id.btn_previous);
        btnNext = findViewById(R.id.btn_next);
        imageView = findViewById(R.id.music_bitmap);
        textTitle = findViewById(R.id.text_title);

        startTime = findViewById(R.id.start_time);
        endTime = findViewById(R.id.end_time);

        musicProgres = findViewById(R.id.music_progress);
        RecyclerView rvMusicList = findViewById(R.id.rv_music_list);
        adapter = new DemoAdapter(this, musicList);
        rvMusicList.setAdapter(adapter);
        rvMusicList.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        adapter.setMusicItemOnclickListener(new DemoAdapter.MusicItemOnclickListener() {
            @Override
            public void selectPosition(int position) {
                //点击了哪首开始播放哪首
                Bundle bundle = new Bundle();
                bundle.putInt("song", position);
                MusicLib.getInstance().setCurrent(position);
                MediaMetadataCompat mediaMetadataCompat = MusicLib.getInstance().musicList.get(position);
                String path = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_ART_URI);
                String title = mediaMetadataCompat.getString(MediaMetadataCompat.METADATA_KEY_TITLE);
                long duration = mediaMetadataCompat.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                Log.d(TAG, "打印path" + path);
                bundle.putString("path", path);
                bundle.putString("title", title);
                bundle.putLong("duration", duration);
//                mController.getTransportControls().playFromUri(Uri.parse("yhsh:/" + path), bundle);
                //方法一播放指定歌曲
                mController.getTransportControls().playFromUri(Uri.parse(path), bundle);
                //方法二播放指定歌曲
//                mBrowser.sendCustomAction("1", bundle, new MediaBrowserCompat.CustomActionCallback() {
//                    @Override
//                    public void onProgressUpdate(String action, Bundle extras, Bundle data) {
//                        super.onProgressUpdate(action, extras, data);
//                        Log.d(TAG, "onProgressUpdate");
//                    }
//
//                    @Override
//                    public void onResult(String action, Bundle extras, Bundle resultData) {
//                        super.onResult(action, extras, resultData);
//                        Log.d(TAG, "onResult");
//                    }
//
//                    @Override
//                    public void onError(String action, Bundle extras, Bundle data) {
//                        super.onError(action, extras, data);
//                        Log.d(TAG, "onError");
//                    }
//                });
            }
        });

        // 保存服务端发送过来的媒体数据
        list = new ArrayList<>();

        // 连接状态回调函数
        BrowserConnectionCallback = new MediaBrowserCompat.ConnectionCallback() {
            @Override
            public void onConnected() {
                if (mBrowser.isConnected()) {
                    String mediaId = mBrowser.getRoot();
                    Log.d(TAG, "mediaId:" + mediaId);
                    //Browser通过订阅的方式向Service请求数据，发起订阅请求需要两个参数，其一为mediaId
                    //而如果该mediaId已经被其他Browser实例订阅，则需要在订阅之前取消mediaId的订阅者
                    //虽然订阅一个 已被订阅的mediaId 时会取代原Browser的订阅回调，但却无法触发onChildrenLoaded回调
                    mBrowser.unsubscribe(mediaId);
                    mBrowser.subscribe(mediaId, BrowserSubscriptionCallback);
                    try {
                        // 连接成功后我们才可以创建媒体控制器
                        mController = new MediaControllerCompat(MainActivity.this, mBrowser.getSessionToken());
                        mController.registerCallback(controllerCallback);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        // 向媒体浏览器服务发起数据请求的回调函数,订阅
        BrowserSubscriptionCallback = new MediaBrowserCompat.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
                Log.d(TAG, "onChildrenLoaded: ---");
                // 如果一首歌都没有扫描到
                if (children.size() == 0) {
                    mBrowser.disconnect();
                }
                // children为service发送回来时的媒体数据集合
                for (MediaBrowserCompat.MediaItem item : children) {
                    Log.d(TAG, "onChildrenLoaded: " + item.getDescription().getTitle().toString());
                    list.add(item);
                    musicList.add(item.getDescription().getTitle().toString());
                    adapter.notifyDataSetChanged();
                    Log.d(TAG, "onChildrenLoaded: " + item.getDescription().getTitle().toString());
                }

            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        // controller的回调，更新UI
        controllerCallback = new MediaControllerCompat.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackStateCompat state) {

                currentMusicInfo.setCurrentMusicState(state);
                Log.d(TAG, "onPlaybackStateChanged: ");

                switch (state.getState()) {
                    case PlaybackStateCompat.STATE_NONE:
                    case PlaybackStateCompat.STATE_PAUSED:
                        btnPlay.setText("开始");
                        timer.cancel();
                        break;
                    case PlaybackStateCompat.STATE_PLAYING:
                        btnPlay.setText("暂停");
                        updateMusic();
                        break;
                    case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                    case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                        btnPlay.setText("暂停");

                    default:
                        break;
                }
            }

            @Override
            public void onMetadataChanged(MediaMetadataCompat metadata) {
                if (metadata == null) {
                    return;
                }
                currentMusicInfo.setCurrentMedia(metadata);

                Log.d(TAG, "onMetadataChanged: name: " + metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));

                textTitle.setText(metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
                if (metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON) != null) {
                    imageView.setImageBitmap(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON));
                }
                // 时长
                int duration = (int) metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
                // 改变最大值
                musicProgres.setMax(duration);
                endTime.setText(stringForTime(duration));

            }
        };
        // 进度条监听器
        musicProgres.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 拖动滑动条结束时更新
                mController.getTransportControls().seekTo(musicProgres.getProgress());
            }
        });

        mBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, MusicService.class),// 服务器
                BrowserConnectionCallback, null); // 连接成功回调
        // 点击播放
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handlerPlayEvent();
            }
        });
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().skipToNext();
                btnPlay.setText("暂停");
            }
        });
        btnPrevious.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mController.getTransportControls().skipToPrevious();
                btnPlay.setText("暂停");
            }
        });
    }

    // 点击播放暂停按钮，需要根据当前状态去采用不同的策略
    private void handlerPlayEvent() {
        switch (currentMusicInfo.getCurrentMusicState().getState()) {
            case PlaybackStateCompat.STATE_PAUSED:
                mController.getTransportControls().play();
                btnPlay.setText("播放");
                break;
            case PlaybackStateCompat.STATE_PLAYING:
                mController.getTransportControls().pause();
                btnPlay.setText("暂停");
                break;
            case PlaybackStateCompat.STATE_NONE:
                mController.getTransportControls().play();
                btnPlay.setText("暂停");
                break;
            default:
                break;
        }
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;

        StringBuilder mFormatBuilder = new StringBuilder();
        Formatter mFormatter = new Formatter(mFormatBuilder, Locale.getDefault());
        mFormatBuilder.setLength(0);
        if (hours > 0) {
            return mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return mFormatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    // 更新进度条，使用定时器，1s更新一次
    private void updateMusic() {
        // 先将上一次的取消掉
        timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                PlaybackStateCompat state = currentMusicInfo.getCurrentMusicState();
                long currentPositon = (long) (((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed()) + state.getPosition());
                Message msg = mHandler.obtainMessage();
                msg.arg1 = UPDATEUI;
                msg.arg2 = (int) currentPositon;
                msg.obj = stringForTime((int) currentPositon);
                mHandler.sendMessage(msg);
            }
        }, 0, 1000);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onStart() {
        super.onStart();
        if (!mBrowser.isConnected()) {
            mBrowser.connect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBrowser.disconnect();
        timer.cancel();
    }
}