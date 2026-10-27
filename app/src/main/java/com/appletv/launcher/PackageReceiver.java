package com.appletv.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageReceiver extends BroadcastReceiver {
    public static PackageListener listener;

    public interface PackageListener {
        void onPackageChanged();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (listener != null) {
            listener.onPackageChanged();
        }
    }
}
