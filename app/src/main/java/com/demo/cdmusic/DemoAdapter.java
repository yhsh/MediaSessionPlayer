package com.demo.cdmusic;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.demo.data.MusicLib;

import java.text.MessageFormat;
import java.util.List;

/**
 * author:杨旭东
 * 创建时间:2021/9/26
 */
public class DemoAdapter extends RecyclerView.Adapter<DemoAdapter.ViewHolder> {


    private final List<String> musicList;

    private final Context context;
    private int currentPosition = -1;

    public DemoAdapter(Context context, List<String> musicList) {
        this.musicList = musicList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.music_item, null, true);
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DemoAdapter.ViewHolder holder, int position) {
        String name = musicList.get(position);
        holder.textView.setTextColor(currentPosition == position ? Color.RED : Color.BLACK);
        holder.textView.setText(MessageFormat.format("{0}.{1}", position + 1, name));
        holder.itemView.setOnClickListener(v -> musicItemOnclickListener.selectPosition(position));
    }

    @Override
    public int getItemCount() {
        return this.musicList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.music_name);
        }
    }

    interface MusicItemOnclickListener {
        /**
         * 点击的哪首歌曲播放即可
         *
         * @param position 歌曲位置
         */
        void selectPosition(int position);
    }

    MusicItemOnclickListener musicItemOnclickListener;

    public void setMusicItemOnclickListener(MusicItemOnclickListener musicItemOnclickListener) {
        this.musicItemOnclickListener = musicItemOnclickListener;
    }

    /**
     * 通过歌曲名称更新当前播放的歌曲
     *
     * @param musicName 歌曲名字
     */
    public void updateCurrentMusic(String musicName) {
        for (int i = 0; i < musicList.size(); i++) {
            if (musicList.get(i).contains(musicName)) {
                currentPosition = i;
                notifyDataSetChanged();
            }
        }
    }

    /**
     * 通过当前position位置更新当前歌曲
     */
    public void updateCurrentMusic() {
        currentPosition = MusicLib.getInstance().current;
        notifyDataSetChanged();
    }
}
