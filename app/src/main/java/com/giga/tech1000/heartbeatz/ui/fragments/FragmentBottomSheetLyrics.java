package com.giga.tech1000.heartbeatz.ui.fragments;

import com.giga.tech1000.heartbeatz.app_worker.HeartBeatzApp;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giga.tech1000.heartbeatz.R;
import com.giga.tech1000.heartbeatz.ui.UIThread;
import com.giga.tech1000.heartbeatz.views.BottomSheetView;
import com.giga.tech1000.media_player.models.Song;
import com.giga.tech1000.media_player.models.extended_models.PlayerCacheModel;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@UnstableApi
public class FragmentBottomSheetLyrics extends Fragment {

    private RecyclerView recyclerView;
    private LyricsAdapter adapter;
    private CircularProgressIndicator loadingIndicator;
    private TextView errorText;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<LyricLine> lyricLines = new ArrayList<>();
    private String lastFetchedSongId = "";

    public FragmentBottomSheetLyrics() {
    }

    // Note: This constructor is used by StateFragmentAdapter. 
    // Ideally, we should use setArguments() to survive process death.
    @SuppressLint("ValidFragment")
    public FragmentBottomSheetLyrics(BottomSheetView bottomSheetView) {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bottom_sheet_lyrics, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.d("LyricsFragment", "onViewCreated");
        recyclerView = view.findViewById(R.id.lyrics_recycler_view);
        loadingIndicator = view.findViewById(R.id.lyrics_loading_indicator);
        errorText = view.findViewById(R.id.lyrics_error_text);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new LyricsAdapter();
        recyclerView.setAdapter(adapter);

        observePlayback();
    }

    private void observePlayback() {
        Log.d("LyricsFragment", "observePlayback: start");
        HeartBeatzApp.container(requireContext()).requireUiThread().getPlayingCache().getPlayerCacheInfo()
                .observe(getViewLifecycleOwner(), this::handleSongChange);

        HeartBeatzApp.container(requireContext()).requireUiThread().getPlayingCache().getProgress()
                .observe(getViewLifecycleOwner(), this::highlightCurrentLine);
    }

    private void handleSongChange(PlayerCacheModel cacheModel) {
        if (cacheModel == null) {
            Log.d("LyricsFragment", "handleSongChange: cacheModel is null");
            return;
        }
        Song song = cacheModel.getCurrentSong();
        if (song == null) {
            Log.d("LyricsFragment", "handleSongChange: song is null");
            return;
        }

        String songId = song.artist + song.title;
        Log.d("LyricsFragment", "handleSongChange: song=" + song.title + ", id=" + songId + ", last=" + lastFetchedSongId);
        if (songId.equals(lastFetchedSongId)) return;

        lastFetchedSongId = songId;
        fetchLyrics(song);
    }

    private void fetchLyrics(Song song) {
        showLoading();

        String artist = sanitize(song.artist);
        String title = sanitize(song.title);

        Log.d("LyricsFragment", "fetchLyrics: Sanity check - Original Title: " + song.title + " -> Sanitized: " + title);

        // Using search API as requested to be more lenient with metadata
        String url = String.format(Locale.getDefault(), "https://lrclib.net/api/search?track_name=%s&artist_name=%s",
                encode(title), encode(artist));

        Log.d("LyricsFragment", "fetchLyrics: URL=" + url);

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "HeartBeatz/1.0 (https://github.com/PneumaTech/HeartBeatz)")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("LyricsFragment", "fetchLyrics: failure", e);
                mainHandler.post(() -> showError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                int code = response.code();
                Log.d("LyricsFragment", "fetchLyrics: response code=" + code);
                if (response.isSuccessful()) {
                    String json = response.body().string();
                    Log.v("LyricsFragment", "fetchLyrics: JSON=" + json);
                    
                    // Search returns an array
                    LrcLibResponse[] results = gson.fromJson(json, LrcLibResponse[].class);
                    
                    if (results != null && results.length > 0) {
                        // Pick the best match based on duration proximity
                        LrcLibResponse bestMatch = results[0];
                        long targetDurationSec = song.duration / 1000;
                        
                        for (LrcLibResponse res : results) {
                            if (Math.abs(res.duration - targetDurationSec) < Math.abs(bestMatch.duration - targetDurationSec)) {
                                bestMatch = res;
                            }
                        }
                        
                        LrcLibResponse finalBestMatch = bestMatch;
                        mainHandler.post(() -> processLyrics(finalBestMatch));
                    } else {
                        mainHandler.post(() -> showError("Lyrics not found"));
                    }
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "null";
                    Log.w("LyricsFragment", "fetchLyrics: not successful, body=" + errorBody);
                    mainHandler.post(() -> showError("Lyrics not found"));
                }
            }
        });
    }

    private String sanitize(String value) {
        if (value == null) return "";

        // 1. Handle "|" or "||" - truncate everything after including the symbol
        int pipeIndex = value.indexOf("|");
        if (pipeIndex != -1) {
            value = value.substring(0, pipeIndex);
        }

        // 2. Replace symbols like "-" or "_" with " ", but keep "'"
        // Regex [^a-zA-Z0-9'\s] matches any char that is NOT:
        // a-z, A-Z, 0-9, ' (single quote), or \s (whitespace)
        value = value.replaceAll("[^a-zA-Z0-9'\\s]", " ");

        // 3. Clean up multiple spaces and trim
        return value.replaceAll("\\s+", " ").trim();
    }

    private void processLyrics(LrcLibResponse response) {
        Log.d("LyricsFragment", "processLyrics: start");
        lyricLines.clear();
        String source = (response.syncedLyrics != null && !response.syncedLyrics.isEmpty())
                ? response.syncedLyrics
                : response.plainLyrics;

        if (source == null || source.isEmpty()) {
            Log.w("LyricsFragment", "processLyrics: no source found");
            showError("No lyrics available");
            return;
        }

        if (response.syncedLyrics != null && !response.syncedLyrics.isEmpty()) {
            Log.d("LyricsFragment", "processLyrics: parsing synced");
            parseSyncedLyrics(response.syncedLyrics);
        } else {
            Log.d("LyricsFragment", "processLyrics: parsing plain");
            parsePlainLyrics(response.plainLyrics);
        }

        Log.d("LyricsFragment", "processLyrics: done, lines=" + lyricLines.size());
        adapter.setLines(lyricLines);
        showContent();
    }

    private void parseSyncedLyrics(String synced) {
        Pattern pattern = Pattern.compile("\\[(\\d+):(\\d+\\.\\d+)\\](.*)");
        for (String line : synced.split("\n")) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                long min = Long.parseLong(Objects.requireNonNull(matcher.group(1)));
                double sec = Double.parseDouble(Objects.requireNonNull(matcher.group(2)));
                long time = (long) ((min * 60 + sec) * 1000);
                lyricLines.add(new LyricLine(time, Objects.requireNonNull(matcher.group(3)).trim()));
            }
        }
    }

    private void parsePlainLyrics(String plain) {
        for (String line : plain.split("\n")) {
            lyricLines.add(new LyricLine(-1, line.trim()));
        }
    }

    private void showLoading() {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
    }

    private void showError(String message) {
        loadingIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        errorText.setVisibility(View.VISIBLE);
        errorText.setText(message);
    }

    private void showContent() {
        loadingIndicator.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
    }

    private String encode(String value) {
        if (value == null) return "";
        return android.net.Uri.encode(value);
    }

    private void highlightCurrentLine(long position) {
        int targetIndex = -1;
        for (int i = 0; i < lyricLines.size(); i++) {
            if (lyricLines.get(i).time != -1 && lyricLines.get(i).time <= position) {
                targetIndex = i;
            } else {
                break;
            }
        }

        if (targetIndex != -1) {
            adapter.setCurrentIndex(targetIndex);
            recyclerView.smoothScrollToPosition(targetIndex);
        }
    }

    // --- Inner Classes for Lyrics ---

    private static class LyricLine {
        long time;
        String text;

        LyricLine(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    private static class LrcLibResponse {
        String plainLyrics;
        String syncedLyrics;
        boolean instrumental;
        double duration;
    }

    private static class LyricsAdapter extends RecyclerView.Adapter<LyricsAdapter.ViewHolder> {
        private final List<LyricLine> lines = new ArrayList<>();
        private int currentIndex = -1;

        @SuppressLint("NotifyDataSetChanged")
        void setLines(List<LyricLine> newLines) {
            lines.clear();
            lines.addAll(newLines);
            currentIndex = -1;
            notifyDataSetChanged();
        }

        @SuppressLint("NotifyDataSetChanged")
        void setCurrentIndex(int index) {
            if (currentIndex != index) {
                currentIndex = index;
                notifyDataSetChanged();
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lyric_line, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.text.setText(lines.get(position).text);

            if (position == currentIndex) {
                holder.text.setAlpha(1.0f);
                holder.text.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
            } else {
                holder.text.setAlpha(0.4f);
                holder.text.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
            }
        }

        @Override
        public int getItemCount() {
            return lines.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView text;

            ViewHolder(View v) {
                super(v);
                text = v.findViewById(R.id.lyric_text);
            }
        }
    }
}
