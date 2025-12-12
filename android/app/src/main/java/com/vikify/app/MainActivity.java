package com.vikify.app;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.BridgeActivity;
import com.vikify.app.nowplaying.NowPlayingPlugin;

public class MainActivity extends BridgeActivity {

	private static final int REQUEST_CODE_NOTIFICATIONS = 1001;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		registerPlugin(NowPlayingPlugin.class);
		super.onCreate(savedInstanceState);
		requestNotificationPermissionIfNeeded();
	}

	private void requestNotificationPermissionIfNeeded() {
		// Android 13+ requires runtime permission to post notifications.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			return;
		}

		int granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS);
		if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) {
			return;
		}

		ActivityCompat.requestPermissions(
			this,
			new String[] { Manifest.permission.POST_NOTIFICATIONS },
			REQUEST_CODE_NOTIFICATIONS
		);
	}
}
