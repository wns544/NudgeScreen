package com.example.screenlocktodo;

import android.app.AlarmManager;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.hardware.display.DisplayManager;

public class LockMonitorService extends Service {
    private static final String TAG = "NudgeLockMonitor";
    static final String ACTION_RESTART_MONITOR = "com.example.screenlocktodo.RESTART_MONITOR";
    private static final String SERVICE_CHANNEL_ID = "todo_lock_service_quiet_v1";
    private static final String LOCK_CHANNEL_ID = "todo_lock_fullscreen_v1";
    private static final int SERVICE_NOTIFICATION_ID = 1001;
    private static final int LOCK_NOTIFICATION_ID = 1002;
    static final long QUICK_RESTART_DELAY_MS = 5000L;
    static final long KEEP_ALIVE_DELAY_MS = 5 * 60 * 1000L;
    private static final long LOCK_NOTIFICATION_COOLDOWN_MS = 15000L;
    private static final long UNLOCK_FROM_SCREEN_OFF_WINDOW_MS = 8000L;
    private static final long[] SCREEN_ON_RETRY_DELAYS_MS = {250L, 900L};
    private static final long[] USER_PRESENT_RETRY_DELAYS_MS = {250L, 1000L};
    private long lastLockNotificationAt;
    private long lastScreenOffAt;
    private boolean waitingForScreenOffUnlock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private DisplayManager displayManager;
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {
        }

        @Override
        public void onDisplayRemoved(int displayId) {
        }

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                handleDefaultDisplayState();
            }
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!AppSettings.lockScreenEnabled(context)) {
                LockMonitorService.stop(context);
                return;
            }
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                Log.i(TAG, "screen off");
                waitingForScreenOffUnlock = true;
                lastScreenOffAt = SystemClock.elapsedRealtime();
                cancelLockNotification(context);
                TodoStore.warm(context);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                Log.i(TAG, "screen on");
                showLockScreen(context, true, true, true);
                scheduleLockScreenRetries(false, true, true, SCREEN_ON_RETRY_DELAYS_MS);
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                Log.i(TAG, "user present recentScreenOff=" + wasRecentlyScreenOff());
                if (wasRecentlyScreenOff()) {
                    waitingForScreenOffUnlock = false;
                    showLockScreen(context, false, true, true);
                    scheduleLockScreenRetries(false, true, true, USER_PRESENT_RETRY_DELAYS_MS);
                }
            }
        }
    };

    private boolean registered;

    public static void start(Context context) {
        if (!AppSettings.lockScreenEnabled(context)) {
            stop(context);
            return;
        }

        Intent intent = new Intent(context, LockMonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException ignored) {
            scheduleRestart(context, QUICK_RESTART_DELAY_MS);
        }
    }

    public static void stop(Context context) {
        cancelScheduledRestarts(context);
        cancelLockNotification(context);
        cancelServiceNotification(context);
        context.stopService(new Intent(context, LockMonitorService.class));
    }

    static void scheduleRestart(Context context, long delayMillis) {
        if (!AppSettings.lockScreenEnabled(context)) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Intent restartIntent = new Intent(context, BootReceiver.class)
                .setAction(ACTION_RESTART_MONITOR)
                .setPackage(context.getPackageName());
        PendingIntent restartPendingIntent = PendingIntent.getBroadcast(
                context,
                (int) Math.max(1, Math.min(100000, delayMillis)),
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long triggerAt = SystemClock.elapsedRealtime() + delayMillis;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    restartPendingIntent
            );
        } else {
            alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    restartPendingIntent
            );
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (!AppSettings.lockScreenEnabled(this)) {
            stopSelf();
            return;
        }
        createNotificationChannels();
        registerScreenReceiver();
        registerDisplayListener();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification());
        TodoStore.warm(this);
        scheduleKeepAlive();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AppSettings.lockScreenEnabled(this)) {
            cancelLockNotification(this);
            cancelServiceNotification(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        createNotificationChannels();
        registerScreenReceiver();
        registerDisplayListener();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification());
        TodoStore.warm(this);
        scheduleKeepAlive();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (registered) {
            unregisterReceiver(screenReceiver);
            registered = false;
        }
        if (displayManager != null) {
            displayManager.unregisterDisplayListener(displayListener);
            displayManager = null;
        }
        handler.removeCallbacksAndMessages(null);
        if (AppSettings.lockScreenEnabled(this)) {
            scheduleRestart(1200);
            scheduleKeepAlive();
        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (AppSettings.lockScreenEnabled(this)) {
            scheduleRestart(700);
            scheduleRestart(QUICK_RESTART_DELAY_MS);
            scheduleKeepAlive();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void registerScreenReceiver() {
        if (registered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        registered = true;
    }

    private void registerDisplayListener() {
        if (displayManager != null) {
            return;
        }
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (displayManager != null) {
            displayManager.registerDisplayListener(displayListener, handler);
            handleDefaultDisplayState();
        }
    }

    private void handleDefaultDisplayState() {
        if (displayManager == null) {
            return;
        }
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return;
        }

        int state = display.getState();
        if (state == Display.STATE_OFF || state == Display.STATE_DOZE || state == Display.STATE_DOZE_SUSPEND) {
            Log.i(TAG, "display resting state=" + state);
            waitingForScreenOffUnlock = true;
            lastScreenOffAt = SystemClock.elapsedRealtime();
            cancelLockNotification(this);
            TodoStore.warm(this);
        } else if (state == Display.STATE_ON && wasRecentlyScreenOff()) {
            Log.i(TAG, "display on after resting state");
            waitingForScreenOffUnlock = false;
            showLockScreen(this, false, true, true);
            scheduleLockScreenRetries(false, true, true, SCREEN_ON_RETRY_DELAYS_MS);
        }
    }

    private boolean isKeyguardLocked(Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isKeyguardLocked();
    }

    private boolean wasRecentlyScreenOff() {
        return waitingForScreenOffUnlock
                && SystemClock.elapsedRealtime() - lastScreenOffAt <= UNLOCK_FROM_SCREEN_OFF_WINDOW_MS;
    }

    private void scheduleLockScreenRetries(
            boolean wakeDisplay,
            boolean allowBeforeKeyguard,
            boolean allowNotificationFallback,
            long[] delaysMillis
    ) {
        Context appContext = getApplicationContext();
        for (long delayMillis : delaysMillis) {
            handler.postDelayed(
                    () -> showLockScreen(appContext, wakeDisplay, allowBeforeKeyguard, allowNotificationFallback),
                    delayMillis
            );
        }
    }

    private void scheduleRestart(long delayMillis) {
        scheduleRestart(this, delayMillis);
    }

    private void scheduleKeepAlive() {
        scheduleRestart(KEEP_ALIVE_DELAY_MS);
    }

    static void cancelLockNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(LOCK_NOTIFICATION_ID);
        }
    }

    private static void cancelServiceNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(SERVICE_NOTIFICATION_ID);
        }
    }

    private static void cancelScheduledRestarts(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        cancelScheduledRestart(context, alarmManager, 700);
        cancelScheduledRestart(context, alarmManager, 1200);
        cancelScheduledRestart(context, alarmManager, QUICK_RESTART_DELAY_MS);
        cancelScheduledRestart(context, alarmManager, 30000);
        cancelScheduledRestart(context, alarmManager, KEEP_ALIVE_DELAY_MS);
    }

    private static void cancelScheduledRestart(Context context, AlarmManager alarmManager, long delayMillis) {
        Intent restartIntent = new Intent(context, BootReceiver.class)
                .setAction(ACTION_RESTART_MONITOR)
                .setPackage(context.getPackageName());
        PendingIntent restartPendingIntent = PendingIntent.getBroadcast(
                context,
                (int) Math.max(1, Math.min(100000, delayMillis)),
                restartIntent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (restartPendingIntent != null) {
            alarmManager.cancel(restartPendingIntent);
            restartPendingIntent.cancel();
        }
    }

    private Notification buildServiceNotification() {
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new Notification.Builder(this, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock_todo)
                .setContentTitle("Todo Lock 실행 중")
                .setContentText("화면이 켜지면 할일 커튼을 표시합니다.")
                .setContentIntent(contentIntent)
                .setDefaults(0)
                .setSound(null)
                .setVibrate(null)
                .setOngoing(true)
                .build();
    }

    private void showLockScreen(Context context, boolean wakeDisplay, boolean allowBeforeKeyguard, boolean allowNotificationFallback) {
        if (!AppSettings.lockScreenEnabled(context)) {
            LockMonitorService.stop(context);
            return;
        }

        if (!allowBeforeKeyguard && !isKeyguardLocked(context)) {
            return;
        }

        Intent lockIntent = new Intent(context, LockActivity.class)
                .putExtra(LockActivity.EXTRA_TURN_SCREEN_ON, wakeDisplay)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        PowerManager powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        if (wakeDisplay && powerManager != null && !powerManager.isInteractive()) {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "ScreenLockTodo:showLock"
            );
            wakeLock.acquire(3000);
        }

        launchLockActivity(context, lockIntent);

        if (!allowNotificationFallback) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastLockNotificationAt < LOCK_NOTIFICATION_COOLDOWN_MS) {
            return;
        }
        lastLockNotificationAt = now;

        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                context,
                1,
                lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(context, LOCK_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock_todo)
                .setContentTitle("오늘 할일 확인")
                .setContentText("잠금 해제 전에 할일을 확인하세요.")
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenIntent, true)
                .setDefaults(0)
                .setSound(null)
                .setVibrate(null)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(LOCK_NOTIFICATION_ID, notification);
        }
    }

    private void launchLockActivity(Context context, Intent lockIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        context,
                        2,
                        lockIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                ActivityOptions options = ActivityOptions.makeBasic();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    options.setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                    );
                } else {
                    options.setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    );
                }
                pendingIntent.send(context, 0, null, null, null, null, options.toBundle());
                Log.i(TAG, "sent lock activity pending intent with BAL allowed");
                cancelLockNotification(context);
                return;
            } catch (PendingIntent.CanceledException | RuntimeException e) {
                Log.w(TAG, "pending intent lock launch failed", e);
            }
        }

        try {
            context.startActivity(lockIntent);
            Log.i(TAG, "started lock activity directly");
            cancelLockNotification(context);
        } catch (RuntimeException e) {
            Log.w(TAG, "direct lock launch failed", e);
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel serviceChannel = new NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Todo Lock 실행 상태",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("화면 켜짐을 감지하기 위한 조용한 상태 알림입니다.");
        serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        serviceChannel.setSound(null, null);
        serviceChannel.enableVibration(false);

        NotificationChannel lockChannel = new NotificationChannel(
                LOCK_CHANNEL_ID,
                "Todo Lock 커튼",
                NotificationManager.IMPORTANCE_HIGH
        );
        lockChannel.setDescription("잠금화면 위에 할일 커튼을 띄우기 위한 알림입니다.");
        lockChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
            manager.createNotificationChannel(lockChannel);
        }
    }
}
