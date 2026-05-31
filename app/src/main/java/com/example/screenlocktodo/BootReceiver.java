package com.example.screenlocktodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || LockMonitorService.ACTION_RESTART_MONITOR.equals(action)) {
            if (AppSettings.lockScreenEnabled(context)) {
                LockMonitorService.start(context);
                LockMonitorService.scheduleRestart(context, 5000);
                LockMonitorService.scheduleRestart(context, 30000);
            } else {
                LockMonitorService.stop(context);
            }
        }
    }
}
