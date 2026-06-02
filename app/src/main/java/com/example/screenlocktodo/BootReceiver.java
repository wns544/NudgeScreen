package com.example.screenlocktodo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "NudgeBootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)
                || Intent.ACTION_USER_PRESENT.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_SCREEN_ON.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || LockMonitorService.ACTION_RESTART_MONITOR.equals(action)) {
            AppSettings.applyLockScreenRecovery(context);
            if (AppSettings.lockScreenEnabled(context)) {
                DiagnosticLog.record(context, TAG, "starting monitor from action=" + action);
                LockMonitorService.start(context);
                LockMonitorService.scheduleRestart(context, LockMonitorService.KEEP_ALIVE_DELAY_MS);
            } else {
                DiagnosticLog.record(context, TAG, "stopping monitor from action=" + action);
                LockMonitorService.stop(context);
            }
        }
    }
}
