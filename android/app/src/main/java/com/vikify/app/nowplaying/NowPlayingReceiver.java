package com.vikify.app.nowplaying;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NowPlayingReceiver extends BroadcastReceiver {
    public static final String ACTION_PLAY = "com.vikify.app.NOW_PLAY";
    public static final String ACTION_PAUSE = "com.vikify.app.NOW_PAUSE";
    public static final String ACTION_NEXT = "com.vikify.app.NOW_NEXT";
    public static final String ACTION_PREV = "com.vikify.app.NOW_PREV";
    public static final String ACTION_STOP = "com.vikify.app.NOW_STOP";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        NowPlayingPlugin.dispatchAction(action);
    }
}
