package com.vikify.app.nowplaying;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.SystemClock;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.core.app.NotificationCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "NowPlaying")
public class NowPlayingPlugin extends Plugin {
    private static final String CHANNEL_ID = "vikify_playback";
    private static final int NOTIFICATION_ID = 4242;

    private static final String ACTION_SEEK_TO = "com.vikify.app.NOW_SEEK_TO";

    private MediaSessionCompat mediaSession;

    private final ExecutorService artworkExecutor = Executors.newSingleThreadExecutor();
    private String artworkUrlCached = null;
    private Bitmap artworkBitmapCached = null;
    private boolean artworkFetchInFlight = false;

    private String lastTitle = "Vikify";
    private String lastArtist = "";
    private boolean lastIsPlaying = false;
    private long lastPositionMs = 0;
    private long lastDurationMs = 0;

    private static NowPlayingPlugin instance;

    static void dispatchAction(String action) {
        if (instance == null || action == null) return;

        JSObject payload = new JSObject();
        payload.put("action", action);
        instance.notifyListeners("action", payload);
    }

    static void dispatchSeekTo(long positionMs) {
        if (instance == null) return;

        JSObject payload = new JSObject();
        payload.put("action", ACTION_SEEK_TO);
        payload.put("positionMs", positionMs);
        instance.notifyListeners("action", payload);
    }

    @Override
    public void load() {
        super.load();
        instance = this;
        ensureChannel();

        if (mediaSession == null) {
            mediaSession = new MediaSessionCompat(getContext(), "VikifyNowPlaying");
            mediaSession.setCallback(new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    dispatchAction(NowPlayingReceiver.ACTION_PLAY);
                }

                @Override
                public void onPause() {
                    dispatchAction(NowPlayingReceiver.ACTION_PAUSE);
                }

                @Override
                public void onSkipToNext() {
                    dispatchAction(NowPlayingReceiver.ACTION_NEXT);
                }

                @Override
                public void onSkipToPrevious() {
                    dispatchAction(NowPlayingReceiver.ACTION_PREV);
                }

                @Override
                public void onStop() {
                    dispatchAction(NowPlayingReceiver.ACTION_STOP);
                }

                @Override
                public void onSeekTo(long pos) {
                    dispatchSeekTo(pos);
                }
            });
            mediaSession.setActive(true);
        }
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (instance == this) instance = null;

        try {
            artworkExecutor.shutdownNow();
        } catch (Exception ignored) {}

        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        Context ctx = getContext();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Vikify playback controls");
        nm.createNotificationChannel(channel);
    }

    private PendingIntent actionIntent(String action) {
        Context ctx = getContext();
        Intent intent = new Intent(ctx, NowPlayingReceiver.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, action.hashCode(), intent, flags);
    }

    private Bitmap appIconBitmap() {
        try {
            return BitmapFactory.decodeResource(getContext().getResources(), com.vikify.app.R.mipmap.ic_launcher);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap fetchBitmap(String urlString) {
        if (urlString == null || urlString.isEmpty()) return null;
        HttpURLConnection connection = null;
        InputStream in = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;
            in = connection.getInputStream();
            return BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    private void ensureArtworkAsync(final String artworkUrl) {
        if (artworkUrl == null || artworkUrl.isEmpty()) {
            artworkUrlCached = null;
            artworkBitmapCached = null;
            return;
        }
        if (artworkUrl.equals(artworkUrlCached) && artworkBitmapCached != null) return;
        if (artworkFetchInFlight) return;

        artworkFetchInFlight = true;
        artworkUrlCached = artworkUrl;
        artworkBitmapCached = null;

        artworkExecutor.execute(() -> {
            Bitmap bmp = fetchBitmap(artworkUrl);
            artworkBitmapCached = bmp;
            artworkFetchInFlight = false;

            // If we got artwork, refresh the notification once using the latest payload.
            if (bmp != null) {
                try {
                    notifyNowPlaying(lastTitle, lastArtist, lastIsPlaying, lastPositionMs, lastDurationMs);
                } catch (Exception ignored) {}
            }
        });
    }

    private void notifyNowPlaying(String title, String artist, boolean isPlaying, long positionMs, long durationMs) {
        if (mediaSession != null) {
            MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs);

            if (artworkBitmapCached != null) {
                meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmapCached);
                meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artworkBitmapCached);
            }

            mediaSession.setMetadata(meta.build());

            long actions =
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO;

            int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            float rate = isPlaying ? 1.0f : 0.0f;

            mediaSession.setPlaybackState(
                new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, positionMs, rate, SystemClock.elapsedRealtime())
                    .build()
            );
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
            .setSmallIcon(com.vikify.app.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(artist)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setShowWhen(false);

        Bitmap largeIcon = artworkBitmapCached != null ? artworkBitmapCached : appIconBitmap();
        if (largeIcon != null) builder.setLargeIcon(largeIcon);

        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Prev",
            actionIntent(NowPlayingReceiver.ACTION_PREV)
        ));

        builder.addAction(new NotificationCompat.Action(
            isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
            isPlaying ? "Pause" : "Play",
            actionIntent(isPlaying ? NowPlayingReceiver.ACTION_PAUSE : NowPlayingReceiver.ACTION_PLAY)
        ));

        builder.addAction(new NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            actionIntent(NowPlayingReceiver.ACTION_NEXT)
        ));

        MediaStyle style = new MediaStyle().setShowActionsInCompactView(0, 1, 2);
        if (mediaSession != null) {
            style.setMediaSession(mediaSession.getSessionToken());
        }
        builder.setStyle(style);

        Notification notification = builder.build();
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);
    }

    @PluginMethod
    public void update(PluginCall call) {
        String title = call.getString("title", "Vikify");
        String artist = call.getString("artist", "");
        boolean isPlaying = call.getBoolean("isPlaying", false);

        String artworkUrl = call.getString("artworkUrl", null);

        // JS provides seconds; Android MediaSession wants ms.
        double positionSeconds = call.getDouble("positionSeconds", 0.0);
        double durationSeconds = call.getDouble("durationSeconds", 0.0);
        long positionMs = (long) Math.max(0, positionSeconds * 1000.0);
        long durationMs = (long) Math.max(0, durationSeconds * 1000.0);

        lastTitle = title;
        lastArtist = artist;
        lastIsPlaying = isPlaying;
        lastPositionMs = positionMs;
        lastDurationMs = durationMs;

        ensureArtworkAsync(artworkUrl);
        notifyNowPlaying(title, artist, isPlaying, positionMs, durationMs);

        call.resolve();
    }

    @PluginMethod
    public void clear(PluginCall call) {
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
        call.resolve();
    }
}
