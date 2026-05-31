package com.example.screenlocktodo;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.window.OnBackInvokedDispatcher;

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 40;

    private static final int COLOR_BG = 0xFFF5F5F7;
    private static final int COLOR_INK = 0xFF1D1D1F;
    private static final int COLOR_MUTED = 0xFF6E6E73;
    private static final int COLOR_PANEL = 0xFFFFFFFF;
    private static final int COLOR_LINE = 0xFFE5E5EA;
    private static final int COLOR_ACCENT = 0xFF007AFF;
    private static final int COLOR_GREEN = 0xFF34C759;
    private static final int COLOR_DANGER = 0xFFFF3B30;
    private static final int COLOR_FIELD = 0xFFF2F2F7;

    private LinearLayout todoList;
    private EditText input;
    private TextView opacityValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureMainWindow();
        requestNotificationPermission();
        LockMonitorService.start(this);
        registerBackHandler();
        setContentView(buildContent());
        refreshTodos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LockMonitorService.start(getApplicationContext());
        refreshTodos();
    }

    @Override
    protected void onStop() {
        LockMonitorService.start(getApplicationContext());
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        LockMonitorService.start(getApplicationContext());
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        closeMainTask();
    }

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::closeMainTask
            );
        }
    }

    private void closeMainTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    private void configureMainWindow() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BG);
        window.setNavigationBarColor(COLOR_BG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = window.getDecorView().getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(hero());
        root.addView(lockSettingsCard(), cardParams());
        root.addView(todoCard(), cardParams());
        root.addView(actionCard(), cardParams());

        return scroll;
    }

    private View hero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(0, dp(4), 0, dp(8));

        TextView title = text("Todo Lock", 34, COLOR_INK, true);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        hero.addView(title);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.LEFT);
        chips.setPadding(0, dp(12), 0, 0);
        chips.addView(chip("\uc2e4\ud589 \uc911", 0x1F34C759, COLOR_GREEN));
        TextView second = chip(AppSettings.curtainUnlockBothDirections(this) ? "\uc88c\uc6b0 \ucee4\ud2bc" : "\uc624\ub978\ucabd \ucee4\ud2bc", 0x1F007AFF, COLOR_ACCENT);
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(34)
        );
        secondParams.leftMargin = dp(8);
        chips.addView(second, secondParams);
        hero.addView(chips);

        return hero;
    }

    private View lockSettingsCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("\uc7a0\uae08\ud654\uba74", null));

        LinearLayout opacityHeader = new LinearLayout(this);
        opacityHeader.setGravity(Gravity.CENTER_VERTICAL);
        opacityHeader.setOrientation(LinearLayout.HORIZONTAL);
        opacityHeader.setPadding(0, dp(16), 0, 0);
        opacityHeader.addView(text("\ubc30\uacbd \uc5b4\ub461\uae30", 16, COLOR_INK, false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        opacityValue = text(AppSettings.overlayOpacity(this) + "%", 16, COLOR_ACCENT, false);
        opacityValue.setGravity(Gravity.RIGHT);
        opacityHeader.addView(opacityValue, new LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(opacityHeader);

        SeekBar opacity = new SeekBar(this);
        opacity.setMax(100);
        opacity.setProgress(AppSettings.overlayOpacity(this));
        card.addView(opacity, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                AppSettings.setOverlayOpacity(MainActivity.this, progress);
                opacityValue.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        card.addView(divider());

        CheckBox curtainBothDirections = new CheckBox(this);
        curtainBothDirections.setText("\uc88c\uc6b0 \ubc00\uae30 \ud5c8\uc6a9");
        curtainBothDirections.setTextSize(15);
        curtainBothDirections.setTextColor(COLOR_INK);
        curtainBothDirections.setPadding(0, dp(10), 0, dp(2));
        curtainBothDirections.setChecked(AppSettings.curtainUnlockBothDirections(this));
        curtainBothDirections.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setCurtainUnlockBothDirections(MainActivity.this, isChecked);
            setContentView(buildContent());
            refreshTodos();
        });
        card.addView(curtainBothDirections);

        return card;
    }

    private View todoCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("\ud560 \uc77c", null));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(14), 0, dp(8));
        card.addView(inputRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("\uc7a0\uae08\ud654\uba74\uc5d0 \ub744\uc6b8 \ud560\uc77c");
        input.setTextColor(COLOR_INK);
        input.setHintTextColor(0x99667085);
        input.setTextSize(15);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setBackground(rounded(COLOR_FIELD, 8));
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button add = filledButton("\ucd94\uac00");
        add.setOnClickListener(v -> addTodo());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(82), dp(50));
        addParams.leftMargin = dp(8);
        inputRow.addView(add, addParams);

        todoList = new LinearLayout(this);
        todoList.setOrientation(LinearLayout.VERTICAL);
        todoList.setPadding(0, dp(4), 0, 0);
        card.addView(todoList);

        return card;
    }

    private View actionCard() {
        LinearLayout card = card();
        card.addView(sectionTitle("\uc571", null));

        Button preview = outlineButton("\ubbf8\ub9ac\ubcf4\uae30");
        preview.setOnClickListener(v -> startActivity(new Intent(this, LockActivity.class)));
        card.addView(preview, fullButtonParams());

        Button notificationSettings = outlineButton("\uc54c\ub9bc \uad8c\ud55c");
        notificationSettings.setOnClickListener(v -> openNotificationSettings());
        card.addView(notificationSettings, fullButtonParams());

        Button batterySettings = outlineButton("\ubc30\ud130\ub9ac \uc124\uc815");
        batterySettings.setOnClickListener(v -> openBatterySettings());
        card.addView(batterySettings, fullButtonParams());

        return card;
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = text(title, 17, COLOR_INK, true);
        box.addView(titleView);

        if (subtitle != null && subtitle.length() > 0) {
            TextView subtitleView = text(subtitle, 14, COLOR_MUTED, false);
            subtitleView.setPadding(0, dp(4), 0, 0);
            box.addView(subtitleView);
        }
        return box;
    }

    private void addTodo() {
        String value = input.getText().toString().trim();
        if (value.length() == 0) {
            return;
        }
        TodoStore.add(this, value);
        input.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        refreshTodos();
    }

    private void refreshTodos() {
        if (todoList == null) {
            return;
        }

        todoList.removeAllViews();
        List<TodoItem> items = TodoStore.load(this);
        if (items.isEmpty()) {
            TextView empty = text("\uc544\uc9c1 \ud560\uc77c\uc774 \uc5c6\uc2b5\ub2c8\ub2e4.", 15, COLOR_MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(18));
            empty.setBackground(rounded(COLOR_FIELD, 8));
            todoList.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(86)
            ));
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            View row = todoRow(items.get(i));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.topMargin = i == 0 ? 0 : dp(8);
            todoList.addView(row, params);
        }
    }

    private View todoRow(TodoItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setBackground(rounded(item.done ? COLOR_FIELD : 0x00FFFFFF, 8));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setChecked(item.done);
        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            TodoStore.setDone(this, item.id, isChecked);
            refreshTodos();
        });
        row.addView(checkBox, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView label = text(item.text, 16, item.done ? COLOR_MUTED : COLOR_INK, false);
        if (item.done) {
            label.setPaintFlags(label.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button delete = quietButton("\uc0ad\uc81c", COLOR_DANGER);
        delete.setOnClickListener(v -> {
            TodoStore.remove(this, item.id);
            refreshTodos();
        });
        row.addView(delete, new LinearLayout.LayoutParams(dp(68), dp(42)));
        return row;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && needsFullScreenIntentPermission()) {
            intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(Uri.parse("package:" + getPackageName()));
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
        }
        startActivity(intent);
    }

    private void openBatterySettings() {
        Intent intent;
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + getPackageName()));
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName()));
        }
        startActivity(intent);
    }

    private boolean needsFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        return manager != null && !manager.canUseFullScreenIntent();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(16));
        card.setBackground(rounded(COLOR_PANEL, 8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(0);
        }
        return card;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(COLOR_LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        );
        params.topMargin = dp(4);
        view.setLayoutParams(params);
        return view;
    }

    private TextView chip(String value, int bgColor, int textColor) {
        TextView chip = text(value, 13, textColor, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setBackground(rounded(bgColor, 17));
        chip.setMinHeight(dp(34));
        return chip;
    }

    private Button filledButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(rounded(COLOR_ACCENT, 8));
        return button;
    }

    private Button outlineButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(COLOR_INK);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setBackground(rounded(COLOR_FIELD, 8));
        return button;
    }

    private Button quietButton(String label, int color) {
        Button button = baseButton(label);
        button.setTextColor(color);
        button.setBackground(rounded(0x00FFFFFF, 8));
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(1), 1.0f);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp(12);
        return params;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        params.topMargin = dp(10);
        return params;
    }

    private GradientDrawable gradient(int startColor, int endColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor}
        );
        drawable.setCornerRadius(dp(8));
        return drawable;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable roundedStroke(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = rounded(fillColor, radiusDp);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
