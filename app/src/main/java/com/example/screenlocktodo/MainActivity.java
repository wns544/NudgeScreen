package com.example.screenlocktodo;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
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
    private static final int COLOR_ACCENT = 0xFF6E8FBF;
    private static final int COLOR_GREEN = 0xFF7FA88A;
    private static final int COLOR_DANGER = 0xFFD9796F;
    private static final int COLOR_FIELD = 0xFFF2F2F7;

    private LinearLayout todoList;
    private EditText input;
    private TextView opacityValue;
    private View drawerScrim;
    private LinearLayout drawerPanel;
    private boolean drawerOpen;
    private float drawerDownX;
    private float drawerDownY;
    private boolean drawerSwiping;
    private boolean drawerOpening;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureMainWindow();
        requestNotificationPermission();
        syncLockMonitorService();
        registerBackHandler();
        setContentView(buildContent());
        refreshTodos();
        maybeShowBatteryGuideOnboarding();
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncLockMonitorService();
        refreshTodos();
    }

    @Override
    protected void onStop() {
        syncLockMonitorService();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        syncLockMonitorService();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }
    }

    private void handleBack() {
        if (drawerOpen) {
            closeDrawer();
            return;
        }
        closeMainTask();
    }

    private void closeMainTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    private void syncLockMonitorService() {
        if (AppSettings.lockScreenEnabled(this)) {
            LockMonitorService.start(getApplicationContext());
        } else {
            LockMonitorService.stop(getApplicationContext());
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
        drawerOpen = false;
        FrameLayout shell = new DrawerRootLayout(this);

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

        shell.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        shell.addView(drawerLayer(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        return shell;
    }

    private View hero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(0, dp(4), 0, dp(8));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView title = text("\uc7a0\uae50, \ud560 \uc77c", 34, COLOR_INK, true);
        title.setTypeface(Typeface.create("serif", Typeface.BOLD));
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView menu = text("\u2630", 22, COLOR_INK, false);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(v -> openDrawer());
        titleRow.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.LEFT);
        chips.setPadding(0, dp(12), 0, 0);
        chips.addView(chip(AppSettings.lockScreenEnabled(this) ? "\uc2e4\ud589 \uc911" : "\uc77c\uc2dc\uc911\uc9c0", 0x267FA88A, COLOR_GREEN));
        TextView second = chip(AppSettings.curtainUnlockBothDirections(this) ? "\uc88c\uc6b0 \ucee4\ud2bc" : "\uc624\ub978\ucabd \ucee4\ud2bc", 0x266E8FBF, COLOR_ACCENT);
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

        LinearLayout enabledRow = new LinearLayout(this);
        enabledRow.setGravity(Gravity.CENTER_VERTICAL);
        enabledRow.setOrientation(LinearLayout.HORIZONTAL);
        enabledRow.setPadding(0, dp(14), 0, dp(8));

        LinearLayout enabledCopy = new LinearLayout(this);
        enabledCopy.setOrientation(LinearLayout.VERTICAL);
        TextView enabledTitle = text("\uc7a0\uae08\ud654\uba74 \uc0ac\uc6a9", 16, COLOR_INK, false);
        TextView enabledSubtitle = text("\ud654\uba74\uc744 \ucf1c\uba74 \ud560 \uc77c \ucee4\ud2bc\uc774 \ub098\ud0c0\ub0a9\ub2c8\ub2e4.", 13, COLOR_MUTED, false);
        enabledSubtitle.setPadding(0, dp(3), dp(10), 0);
        enabledCopy.addView(enabledTitle);
        enabledCopy.addView(enabledSubtitle);
        enabledRow.addView(enabledCopy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Switch enabledSwitch = new Switch(this);
        enabledSwitch.setChecked(AppSettings.lockScreenEnabled(this));
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppSettings.setLockScreenEnabled(MainActivity.this, isChecked);
            syncLockMonitorService();
            setContentView(buildContent());
            refreshTodos();
        });
        enabledRow.addView(enabledSwitch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        card.addView(enabledRow);

        card.addView(divider());

        LinearLayout opacityHeader = new LinearLayout(this);
        opacityHeader.setGravity(Gravity.CENTER_VERTICAL);
        opacityHeader.setOrientation(LinearLayout.HORIZONTAL);
        opacityHeader.setPadding(0, dp(12), 0, 0);
        opacityHeader.addView(text("\ubc30\uacbd \uc5b4\ub461\uae30", 16, COLOR_INK, false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        opacityValue = text(AppSettings.overlayOpacity(this) + "%", 16, COLOR_ACCENT, false);
        opacityValue.setGravity(Gravity.RIGHT);
        opacityHeader.addView(opacityValue, new LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(opacityHeader);

        SeekBar opacity = new SeekBar(this);
        opacity.setMax(20);
        opacity.setProgress(Math.round(AppSettings.overlayOpacity(this) / 5f));
        card.addView(opacity, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int snapped = progress * 5;
                AppSettings.setOverlayOpacity(MainActivity.this, snapped);
                opacityValue.setText(snapped + "%");
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
        curtainBothDirections.setText("\uc591\ubc29\ud5a5 \ubc00\uc5b4\uc11c \uc7a0\uae08\ud574\uc81c");
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

    private View drawerLayer() {
        FrameLayout layer = new FrameLayout(this);
        layer.setClipChildren(false);

        drawerScrim = new View(this);
        drawerScrim.setBackgroundColor(0x66000000);
        drawerScrim.setAlpha(0f);
        drawerScrim.setVisibility(View.GONE);
        drawerScrim.setOnClickListener(v -> closeDrawer());
        drawerScrim.setOnTouchListener((view, event) -> handleDrawerSwipe(event));
        layer.addView(drawerScrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        int panelWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 5f / 6f);
        drawerPanel = new DrawerPanelLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setPadding(dp(20), dp(30), dp(20), dp(20));
        drawerPanel.setBackground(rounded(COLOR_PANEL, 8));
        drawerPanel.setClickable(true);
        drawerPanel.setFocusable(true);
        drawerPanel.setVisibility(View.GONE);
        drawerPanel.setTranslationX(panelWidth);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            drawerPanel.setElevation(dp(12));
        }

        TextView title = text("\uc124\uc815", 28, COLOR_INK, true);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        drawerPanel.addView(title);

        TextView subtitle = text("\uc7a0\uae50, \ud560 \uc77c", 14, COLOR_MUTED, false);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        drawerPanel.addView(subtitle);

        drawerPanel.addView(actionRow("\ubbf8\ub9ac\ubcf4\uae30", v -> {
            closeDrawer();
            startActivity(new Intent(this, LockActivity.class));
        }, true));
        drawerPanel.addView(actionRow("\uc54c\ub9bc \uad8c\ud55c", v -> {
            closeDrawer();
            openNotificationSettings();
        }, false));
        drawerPanel.addView(drawerSectionTitle("\ud560\uc77c \ucee4\ud2bc\uc774 \uc790\uafb8 \uc885\ub8cc\ub418\ub098\uc694?"));
        drawerPanel.addView(actionRow("\ubc30\ud130\ub9ac \uc81c\ud55c\uc5c6\uc74c \uc124\uc815\ud558\uae30", v -> {
            closeDrawer();
            showBatteryGuideDialog(false);
        }, false));
        drawerPanel.addView(actionRow("\ub2eb\uae30", v -> closeDrawer(), false));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.RIGHT
        );
        layer.addView(drawerPanel, panelParams);
        return layer;
    }

    private final class DrawerRootLayout extends FrameLayout {
        DrawerRootLayout(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (drawerOpen) {
                return super.onInterceptTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    drawerDownX = event.getRawX();
                    drawerDownY = event.getRawY();
                    drawerSwiping = false;
                    drawerOpening = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - drawerDownX;
                    float dy = event.getRawY() - drawerDownY;
                    if (!drawerSwiping && dx < -dp(18) && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        beginDrawerOpenDrag();
                        drawerSwiping = true;
                        drawerOpening = true;
                        updateDrawerOpenDrag(dx);
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!drawerOpening) {
                return super.onTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    updateDrawerOpenDrag(event.getRawX() - drawerDownX);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    finishDrawerOpenDrag(event.getRawX() - drawerDownX);
                    drawerOpening = false;
                    drawerSwiping = false;
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (drawerOpening) {
                return onTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    drawerSwiping = false;
                    drawerOpening = false;
                    break;
                default:
                    break;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private void beginDrawerOpenDrag() {
        if (drawerPanel == null || drawerScrim == null) {
            return;
        }
        drawerScrim.animate().cancel();
        drawerPanel.animate().cancel();
        drawerScrim.setVisibility(View.VISIBLE);
        drawerPanel.setVisibility(View.VISIBLE);
    }

    private void updateDrawerOpenDrag(float dx) {
        if (drawerPanel == null || drawerScrim == null) {
            return;
        }
        int panelWidth = drawerPanel.getWidth() > 0
                ? drawerPanel.getWidth()
                : Math.round(getResources().getDisplayMetrics().widthPixels * 5f / 6f);
        float drag = Math.min(panelWidth, Math.max(0f, -dx));
        float translation = panelWidth - drag;
        drawerPanel.setTranslationX(translation);
        drawerScrim.setAlpha(Math.min(1f, drag / Math.max(1, panelWidth)));
    }

    private void finishDrawerOpenDrag(float dx) {
        if (drawerPanel == null) {
            return;
        }
        int panelWidth = drawerPanel.getWidth() > 0
                ? drawerPanel.getWidth()
                : Math.round(getResources().getDisplayMetrics().widthPixels * 5f / 6f);
        float opened = Math.min(panelWidth, Math.max(0f, -dx));
        if (opened > Math.max(dp(90), panelWidth * 0.22f)) {
            openDrawer();
        } else {
            drawerOpen = true;
            closeDrawer();
        }
    }

    private boolean handleDrawerSwipe(MotionEvent event) {
        if (!drawerOpen || drawerPanel == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drawerDownX = event.getRawX();
                drawerDownY = event.getRawY();
                drawerSwiping = false;
                drawerPanel.animate().cancel();
                return false;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.max(0f, event.getRawX() - drawerDownX);
                float dy = event.getRawY() - drawerDownY;
                if (!drawerSwiping && dx > dp(14) && dx > Math.abs(dy) * 1.2f) {
                    drawerSwiping = true;
                }
                if (drawerSwiping) {
                    drawerPanel.setTranslationX(dx * 0.9f);
                    drawerScrim.setAlpha(Math.max(0f, 1f - dx / Math.max(1, drawerPanel.getWidth())));
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!drawerSwiping) {
                    return false;
                }
                drawerSwiping = false;
                float releaseDx = event.getRawX() - drawerDownX;
                if (releaseDx > Math.max(dp(90), drawerPanel.getWidth() * 0.22f)) {
                    closeDrawer();
                } else {
                    drawerPanel.animate().translationX(0f).setDuration(140).start();
                    drawerScrim.animate().alpha(1f).setDuration(140).start();
                }
                return true;
            default:
                return true;
        }
    }

    private final class DrawerPanelLayout extends LinearLayout {
        DrawerPanelLayout(Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (!drawerOpen) {
                return super.onInterceptTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    drawerDownX = event.getRawX();
                    drawerDownY = event.getRawY();
                    drawerSwiping = false;
                    animate().cancel();
                    if (drawerScrim != null) {
                        drawerScrim.animate().cancel();
                    }
                    return super.onInterceptTouchEvent(event);
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.max(0f, event.getRawX() - drawerDownX);
                    float dy = event.getRawY() - drawerDownY;
                    if (!drawerSwiping && dx > dp(14) && dx > Math.abs(dy) * 1.2f) {
                        drawerSwiping = true;
                    }
                    return drawerSwiping || super.onInterceptTouchEvent(event);
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    drawerSwiping = false;
                    return super.onInterceptTouchEvent(event);
                default:
                    return super.onInterceptTouchEvent(event);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!drawerOpen) {
                return super.onTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    float dx = Math.max(0f, event.getRawX() - drawerDownX);
                    setTranslationX(dx * 0.9f);
                    if (drawerScrim != null) {
                        drawerScrim.setAlpha(Math.max(0f, 1f - dx / Math.max(1, getWidth())));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    drawerSwiping = false;
                    float releaseDx = event.getRawX() - drawerDownX;
                    if (releaseDx > Math.max(dp(90), getWidth() * 0.22f)) {
                        closeDrawer();
                    } else {
                        animate().translationX(0f).setDuration(140).start();
                        if (drawerScrim != null) {
                            drawerScrim.animate().alpha(1f).setDuration(140).start();
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    private void openDrawer() {
        if (drawerPanel == null || drawerScrim == null || drawerOpen) {
            return;
        }
        drawerOpen = true;
        drawerScrim.animate().cancel();
        drawerPanel.animate().cancel();
        drawerScrim.setVisibility(View.VISIBLE);
        drawerPanel.setVisibility(View.VISIBLE);
        drawerScrim.animate().alpha(1f).setDuration(180).start();
        drawerPanel.animate()
                .translationX(0f)
                .setDuration(220)
                .start();
        drawerPanel.requestFocus();
    }

    private void closeDrawer() {
        if (drawerPanel == null || drawerScrim == null || !drawerOpen) {
            return;
        }
        drawerOpen = false;
        int panelWidth = drawerPanel.getWidth() > 0
                ? drawerPanel.getWidth()
                : Math.round(getResources().getDisplayMetrics().widthPixels * 5f / 6f);
        drawerScrim.animate().cancel();
        drawerPanel.animate().cancel();
        drawerScrim.animate()
                .alpha(0f)
                .setDuration(160)
                .withEndAction(() -> drawerScrim.setVisibility(View.GONE))
                .start();
        drawerPanel.animate()
                .translationX(panelWidth)
                .setDuration(180)
                .withEndAction(() -> drawerPanel.setVisibility(View.GONE))
                .start();
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
        Intent intent = batteryOptimizationIntent();
        try {
            startActivity(intent);
        } catch (Exception ignored) {
            startActivity(appDetailsIntent());
        }
    }

    private void maybeShowBatteryGuideOnboarding() {
        if (AppSettings.batteryGuideShown(this)) {
            return;
        }
        if (isBatteryUnrestricted()) {
            AppSettings.setBatteryGuideShown(this, true);
            return;
        }
        getWindow().getDecorView().post(() -> showBatteryGuideDialog(true));
    }

    private void showBatteryGuideDialog(boolean firstRun) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(20), dp(20), dp(16));
        box.setBackground(rounded(COLOR_PANEL, 12));

        TextView title = text("\ubc30\ud130\ub9ac \uc81c\ud55c\uc5c6\uc74c\uc774 \ud544\uc694\ud574\uc694", 20, COLOR_INK, true);
        box.addView(title);

        TextView body = text(
                "Android\uac00 \ubc30\ud130\ub9ac\ub97c \uc544\ub07c\ub824\uace0 \uc571\uc744 \uba48\ucd94\uba74 \ud654\uba74\uc744 \ucf30\uc744 \ub54c \ud560\uc77c \ucee4\ud2bc\uc774 \ub2a6\uac8c \ub728\uac70\ub098 \uc885\ub8cc\ub420 \uc218 \uc788\uc5b4\uc694. \uc124\uc815\uc5d0\uc11c \ubc30\ud130\ub9ac \uc0ac\uc6a9\ub7c9\uc744 '\uc81c\ud55c \uc5c6\uc74c'\uc73c\ub85c \ubc14\uafd4\ub450\uba74 \ub354 \uc548\uc815\uc801\uc73c\ub85c \uc791\ub3d9\ud569\ub2c8\ub2e4.",
                15,
                COLOR_MUTED,
                false
        );
        body.setPadding(0, dp(10), 0, dp(18));
        box.addView(body);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        Button later = quietButton("\ub098\uc911\uc5d0", COLOR_MUTED);
        later.setOnClickListener(v -> {
            if (firstRun) {
                AppSettings.setBatteryGuideShown(this, true);
            }
            dialog.dismiss();
        });
        buttons.addView(later, new LinearLayout.LayoutParams(dp(88), dp(44)));

        Button settings = filledButton("\uc124\uc815\uc73c\ub85c \uc774\ub3d9");
        settings.setOnClickListener(v -> {
            if (firstRun) {
                AppSettings.setBatteryGuideShown(this, true);
            }
            dialog.dismiss();
            openBatterySettings();
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(132), dp(44));
        settingsParams.leftMargin = dp(8);
        buttons.addView(settings, settingsParams);
        box.addView(buttons);

        dialog.setContentView(box);
        dialog.setOnCancelListener(d -> {
            if (firstRun) {
                AppSettings.setBatteryGuideShown(this, true);
            }
        });
        dialog.show();

        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
            dialogWindow.setLayout(
                    Math.min(getResources().getDisplayMetrics().widthPixels - dp(40), dp(420)),
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private Intent batteryOptimizationIntent() {
        if (!isBatteryUnrestricted()) {
            return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + getPackageName()));
        }
        return appDetailsIntent();
    }

    private Intent appDetailsIntent() {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:" + getPackageName()));
    }

    private boolean isBatteryUnrestricted() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
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
        return divider(4);
    }

    private View divider(int topMarginDp) {
        View view = new View(this);
        view.setBackgroundColor(COLOR_LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Math.max(1, dp(1))
        );
        params.topMargin = dp(topMarginDp);
        view.setLayoutParams(params);
        return view;
    }

    private View actionRow(String label, View.OnClickListener listener, boolean first) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        if (!first) {
            row.addView(divider(0));
        }

        TextView textView = text(label, 16, COLOR_INK, false);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setText(label + "  \u203a");
        textView.setPadding(0, dp(13), 0, dp(12));
        textView.setOnClickListener(listener);
        row.addView(textView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        return row;
    }

    private View drawerSectionTitle(String label) {
        TextView title = text(label, 14, COLOR_MUTED, true);
        title.setPadding(0, dp(20), 0, dp(4));
        return title;
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
