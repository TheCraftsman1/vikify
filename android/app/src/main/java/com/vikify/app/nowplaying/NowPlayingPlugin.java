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

    @PluginMethod
    public void update(PluginCall call) {
        String title = call.getString("title", "Vikify");
        String artist = call.getString("artist", "");
        boolean isPlaying = call.getBoolean("isPlaying", false);

        // JS provides seconds; Android MediaSession wants ms.
        double positionSeconds = call.getDouble("positionSeconds", 0.0);
        double durationSeconds = call.getDouble("durationSeconds", 0.0);
        long positionMs = (long) Math.max(0, positionSeconds * 1000.0);
        long durationMs = (long) Math.max(0, durationSeconds * 1000.0);

        if (mediaSession != null) {
            mediaSession.setMetadata(
                new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                    .build()
            );

            long actions =
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                PlaybackStateCompat.ACTION_SEEK_TO;

            int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            mediaSession.setPlaybackState(
                new PlaybackStateCompat.Builder()
                    .setActions(actions)
                    .setState(state, positionMs, 1.0f)
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

        Bitmap largeIcon = appIconBitmap();
        if (largeIcon != null) builder.setLargeIcon(largeIcon);

        // Media actions
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

        MediaStyle style = new MediaStyle()
            .setShowActionsInCompactView(0, 1, 2);
        if (mediaSession != null) {
            style.setMediaSession(mediaSession.getSessionToken());
        }
        builder.setStyle(style);

        Notification notification = builder.build();
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);

        call.resolve();
    }

    @PluginMethod
    public void clear(PluginCall call) {
        NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID);
        call.resolve();
    }
}
