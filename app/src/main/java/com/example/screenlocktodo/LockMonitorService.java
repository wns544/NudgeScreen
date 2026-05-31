package com.example.screenlocktodo;

import android.app.AlarmManager;
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
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

public class LockMonitorService extends Service {
    static final String ACTION_RESTART_MONITOR = "com.example.screenlocktodo.RESTART_MONITOR";
    private static final String SERVICE_CHANNEL_ID = "todo_lock_service_quiet_v1";
    private static final String LOCK_CHANNEL_ID = "todo_lock_fullscreen_v1";
    private static final int SERVICE_NOTIFICATION_ID = 1001;
    private static final int LOCK_NOTIFICATION_ID = 1002;
    private static final long KEEP_ALIVE_DELAY_MS = 30000L;
    private static final long LOCK_NOTIFICATION_COOLDOWN_MS = 15000L;
    private long lastLockNotificationAt;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                cancelLockNotification(context);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                showLockScreen(context, true, true, true);
            }
        }
    };

    private boolean registered;

    public static void start(Context context) {
        Intent intent = new Intent(context, LockMonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException ignored) {
            scheduleRestart(context, 5000);
        }
    }

    static void scheduleRestart(Context context, long delayMillis) {
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
        alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMillis,
                restartPendingIntent
        );
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        registerScreenReceiver();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification());
        scheduleKeepAlive();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannels();
        registerScreenReceiver();
        startForeground(SERVICE_NOTIFICATION_ID, buildServiceNotification());
        scheduleKeepAlive();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (registered) {
            unregisterReceiver(screenReceiver);
            registered = false;
        }
        scheduleRestart(1200);
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        scheduleRestart(700);
        scheduleRestart(5000);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        registered = true;
    }

    private boolean isKeyguardLocked(Context context) {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
        return keyguardManager != null && keyguardManager.isKeyguardLocked();
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
        if (wakeDisplay && powerManager != null) {
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "ScreenLockTodo:showLock"
            );
            wakeLock.acquire(3000);
        }

        try {
            context.startActivity(lockIntent);
            cancelLockNotification(context);
        } catch (RuntimeException ignored) {
        }

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
