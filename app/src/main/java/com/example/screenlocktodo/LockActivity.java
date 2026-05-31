package com.example.screenlocktodo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LockActivity extends Activity {
    static final String EXTRA_TURN_SCREEN_ON = "com.example.screenlocktodo.TURN_SCREEN_ON";

    private LinearLayout todoList;
    private LinearLayout inputRow;
    private EditText input;
    private TextView undoButton;
    private TextView menuButton;
    private LinearLayout menuPanel;
    private TextView lockButton;
    private View curtainBackground;
    private TodoItem lastDeletedItem;
    private int lastDeletedIndex = -1;
    private boolean todosLocked;
    private long pendingTapItemId = -1L;
    private boolean firstTodoRender = true;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        configureLockWindow();
        super.onCreate(savedInstanceState);
        LockMonitorService.cancelLockNotification(this);
        setContentView(buildContent());
        refreshTodos();
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        configureLockWindow();
        LockMonitorService.cancelLockNotification(this);
        refreshTodos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LockMonitorService.cancelLockNotification(this);
        refreshTodos();
    }

    private void configureLockWindow() {
        boolean turnScreenOn = getIntent().getBooleanExtra(EXTRA_TURN_SCREEN_ON, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(turnScreenOn);
        } else {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            if (turnScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            }
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setNavigationBarColor(0x00000000);
        getWindow().setStatusBarColor(overlayColor());
        int systemUiFlags = getWindow().getDecorView().getSystemUiVisibility();
        systemUiFlags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            systemUiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            systemUiFlags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(systemUiFlags);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            if (getWindow().getInsetsController() != null) {
                getWindow().getInsetsController().hide(android.view.WindowInsets.Type.navigationBars());
                getWindow().getInsetsController().setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
            getWindow().setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow().getInsetsController() != null) {
            getWindow().getInsetsController().setSystemBarsAppearance(0,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    private View buildContent() {
        FrameLayout shell = new CurtainFrameLayout(this);

        curtainBackground = new View(this);
        curtainBackground.setBackgroundColor(overlayColor());
        shell.addView(curtainBackground, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        menuButton = text("\u22ee", 28, 0xDFFFFFFF, false);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setOnClickListener(v -> toggleMenu());
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                dp(48),
                dp(48),
                Gravity.TOP | Gravity.RIGHT
        );
        menuParams.topMargin = dp(34);
        menuParams.rightMargin = dp(12);

        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.HORIZONTAL);
        menuPanel.setGravity(Gravity.CENTER);
        menuPanel.setVisibility(View.GONE);
        menuPanel.setAlpha(0f);
        menuPanel.setTranslationY(-dp(6));
        menuPanel.setPadding(0, 0, 0, 0);
        menuPanel.setBackgroundColor(0x00000000);

        lockButton = text("\u26bf", 20, 0xDFFFFFFF, false);
        lockButton.setGravity(Gravity.CENTER);
        lockButton.setOnClickListener(v -> toggleTodoLock());
        menuPanel.addView(lockButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        undoButton = text("\u21b6", 22, 0xDFFFFFFF, false);
        undoButton.setGravity(Gravity.CENTER);
        undoButton.setOnClickListener(v -> undoDelete());
        menuPanel.addView(undoButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(44),
                Gravity.TOP | Gravity.RIGHT
        );
        panelParams.topMargin = dp(82);
        panelParams.rightMargin = dp(12);

        updateMenuButtons();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0x00000000);
        shell.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(72), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        Date now = new Date();
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(timeRow, fullWidthWrap());

        TextView time = text(new SimpleDateFormat("h:mm", Locale.getDefault()).format(now), 52, 0xEFFFFFFF, false);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timeRow.addView(time);

        TextView meridiem = text(new SimpleDateFormat("a", Locale.ENGLISH).format(now), 13, 0xDFFFFFFF, false);
        meridiem.setPadding(dp(5), 0, 0, dp(12));
        timeRow.addView(meridiem);

        TextView date = text(new SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(now), 16, 0xCCFFFFFF, false);
        date.setGravity(Gravity.CENTER);
        root.addView(date, fullWidthWrap());

        TextView plus = text("+", 32, 0xBFFFFFFF, false);
        plus.setGravity(Gravity.CENTER);
        plus.setPadding(0, dp(24), 0, dp(16));
        plus.setOnClickListener(v -> toggleInput());
        root.addView(plus, fullWidthWrap());

        inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setVisibility(View.GONE);
        inputRow.setAlpha(0f);
        inputRow.setTranslationY(-dp(8));
        root.addView(inputRow, narrowParams());

        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("\uc0c8 \ud560\uc77c");
        input.setGravity(Gravity.CENTER);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x99FFFFFF);
        input.setTextSize(16);
        input.setBackgroundColor(0x22FFFFFF);
        inputRow.addView(input, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button add = ghostButton("\ucd94\uac00", 15);
        add.setOnClickListener(v -> addTodo());
        inputRow.addView(add, new LinearLayout.LayoutParams(dp(74), dp(46)));

        todoList = new LinearLayout(this);
        todoList.setOrientation(LinearLayout.VERTICAL);
        todoList.setGravity(Gravity.CENTER_HORIZONTAL);
        todoList.setPadding(0, dp(8), 0, dp(18));
        todoList.setLayoutTransition(new android.animation.LayoutTransition());
        root.addView(todoList, narrowParams());

        TextView curtainHint = text(curtainHintText(), 14, 0xAFFFFFFF, false);
        curtainHint.setGravity(Gravity.CENTER);
        curtainHint.setPadding(0, dp(10), 0, dp(8));
        root.addView(curtainHint, fullWidthWrap());
        shell.addView(menuButton, menuParams);
        shell.addView(menuPanel, panelParams);
        return shell;
    }

    private String curtainHintText() {
        if (AppSettings.curtainUnlockBothDirections(this)) {
            return "\ubc00\uc5b4\uc11c \uc7a0\uae08\ud574\uc81c";
        }
        return "\uc624\ub978\ucabd\uc73c\ub85c \ubc00\uc5b4\uc11c \uc7a0\uae08\ud574\uc81c";
    }

    private void toggleMenu() {
        if (menuPanel.getVisibility() == View.VISIBLE) {
            menuPanel.animate()
                    .alpha(0f)
                    .translationY(-dp(6))
                    .setDuration(140)
                    .withEndAction(() -> menuPanel.setVisibility(View.GONE))
                    .start();
            return;
        }
        updateMenuButtons();
        menuPanel.animate().cancel();
        menuPanel.setVisibility(View.VISIBLE);
        menuPanel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(160)
                .start();
    }

    private void toggleTodoLock() {
        todosLocked = !todosLocked;
        updateMenuButtons();
        refreshTodos();
    }

    private void updateMenuButtons() {
        if (lockButton != null) {
            lockButton.setText(todosLocked ? "\ud83d\udd12" : "\ud83d\udd13");
            lockButton.setTextColor(todosLocked ? 0xFFFFE4A8 : 0xDFFFFFFF);
        }
        if (undoButton != null) {
            boolean canUndo = lastDeletedItem != null;
            undoButton.setEnabled(canUndo);
            undoButton.setTextColor(canUndo ? 0xDFFFFFFF : 0x55FFFFFF);
        }
    }

    private void addTodo() {
        TodoStore.add(this, input.getText().toString());
        input.setText("");
        hideInput();
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
            TextView empty = text("\uc624\ub298\uc740 \uc544\uc9c1 \ube44\uc5b4 \uc788\uc2b5\ub2c8\ub2e4.", 16, 0xBFFFFFFF, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(28), 0, dp(24));
            todoList.addView(empty);
            if (!firstTodoRender) {
                animateIn(empty, 0);
            }
            firstTodoRender = false;
            return;
        }

        boolean animateRows = !firstTodoRender;
        for (int i = 0; i < items.size(); i++) {
            View row = todoRow(items.get(i), i);
            todoList.addView(row);
            if (animateRows) {
                animateIn(row, i * 35);
            }
            TextView divider = text("\u2013", 16, 0x66FFFFFF, false);
            divider.setGravity(Gravity.CENTER);
            todoList.addView(divider, fullWidthWrap());
            if (animateRows) {
                animateIn(divider, i * 35 + 20);
            }
        }
        firstTodoRender = false;
    }

    private View todoRow(TodoItem item, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(5), 0, dp(5));
        row.setTag(item.id);

        row.addView(text("", 1, 0x00FFFFFF, false), new LinearLayout.LayoutParams(dp(58), dp(44)));

        TextView label = text(item.text, 16, todosLocked ? 0xCCFFFFFF : (item.done ? 0x99FFFFFF : 0xFFFFFFFF), false);
        label.setGravity(Gravity.CENTER);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView hint = text(todosLocked ? "\ud83d\udd12" : (item.done ? "\u2713" : ""), 14,
                todosLocked ? 0xAAFFFFFF : (item.done ? 0x99FFFFFF : 0x00FFFFFF), false);
        hint.setGravity(Gravity.CENTER);
        row.addView(hint, new LinearLayout.LayoutParams(dp(58), dp(44)));

        if (todosLocked) {
            return row;
        }

        SwipeActionListener swipeActionListener = new SwipeActionListener(item, index, row, hint);
        row.setOnTouchListener(swipeActionListener);
        label.setOnTouchListener(swipeActionListener);
        hint.setOnTouchListener(swipeActionListener);
        View.OnClickListener clickListener = v -> handleTodoTap(item);
        row.setOnClickListener(clickListener);
        label.setOnClickListener(clickListener);
        hint.setOnClickListener(clickListener);
        row.setOnLongClickListener(v -> startTodoDrag(row, item));
        label.setOnLongClickListener(v -> startTodoDrag(row, item));
        hint.setOnLongClickListener(v -> startTodoDrag(row, item));
        row.setOnDragListener((view, event) -> handleTodoDropTarget(row, index, event));
        return row;
    }

    private boolean startTodoDrag(View row, TodoItem item) {
        row.animate().scaleX(1.04f).scaleY(1.04f).alpha(0.82f).setDuration(120).start();
        ClipData data = ClipData.newPlainText("todo_id", String.valueOf(item.id));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            row.startDragAndDrop(data, new View.DragShadowBuilder(row), item.id, 0);
        } else {
            row.startDrag(data, new View.DragShadowBuilder(row), item.id, 0);
        }
        return true;
    }

    private boolean handleTodoDropTarget(View row, int targetIndex, DragEvent event) {
        Object localState = event.getLocalState();
        if (!(localState instanceof Long)) {
            return true;
        }
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_ENTERED:
                row.animate().translationY(dp(5)).setDuration(90).start();
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                row.animate().translationY(0f).setDuration(90).start();
                return true;
            case DragEvent.ACTION_DROP:
                TodoStore.move(this, (Long) localState, targetIndex);
                refreshTodos();
                return true;
            case DragEvent.ACTION_DRAG_ENDED:
                row.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                return true;
            default:
                return true;
        }
    }

    private void handleTodoTap(TodoItem item) {
        long now = SystemClock.uptimeMillis();
        if (pendingTapItemId == item.id) {
            pendingTapItemId = -1L;
            uiHandler.removeCallbacksAndMessages(item.id);
            showEditTodoDialog(item);
            return;
        }

        pendingTapItemId = item.id;
        uiHandler.postAtTime(() -> {
            if (pendingTapItemId == item.id) {
                pendingTapItemId = -1L;
                TodoStore.setDone(this, item.id, !item.done);
                refreshTodos();
            }
        }, item.id, now + 280);
    }

    private void showEditTodoDialog(TodoItem item) {
        EditText editor = new EditText(this);
        editor.setSingleLine(false);
        editor.setText(item.text);
        editor.setSelectAllOnFocus(false);
        editor.setTextColor(0xFF111111);
        editor.setTextSize(16);
        editor.setPadding(dp(16), dp(8), dp(16), dp(8));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("\ud560\uc77c \uc218\uc815")
                .setView(editor)
                .setNegativeButton("\ucde8\uc18c", null)
                .setPositiveButton("\uc800\uc7a5", (d, which) -> {
                    TodoStore.setText(this, item.id, editor.getText().toString());
                    refreshTodos();
                })
                .create();
        dialog.setOnShowListener(d -> {
            editor.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();
    }

    private void deleteTodo(TodoItem item, int index) {
        lastDeletedItem = item;
        lastDeletedIndex = index;
        updateMenuButtons();
        TodoStore.remove(this, item.id);
        refreshTodos();
    }

    private void undoDelete() {
        if (lastDeletedItem == null) {
            return;
        }
        TodoStore.restore(this, lastDeletedItem, lastDeletedIndex);
        lastDeletedItem = null;
        lastDeletedIndex = -1;
        updateMenuButtons();
        refreshTodos();
    }

    private void toggleInput() {
        if (inputRow.getVisibility() == View.VISIBLE) {
            hideInput();
            return;
        }
        inputRow.animate().cancel();
        inputRow.setVisibility(View.VISIBLE);
        inputRow.animate().alpha(1f).translationY(0f).setDuration(180).start();
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideInput() {
        inputRow.animate()
                .alpha(0f)
                .translationY(-dp(8))
                .setDuration(160)
                .withEndAction(() -> inputRow.setVisibility(View.GONE))
                .start();
    }

    private void continueSystemUnlock() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguardManager != null && keyguardManager.isKeyguardLocked()) {
            keyguardManager.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    closeLockTask();
                }
            });
        } else {
            closeLockTask();
        }
    }

    private void closeLockTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
        overridePendingTransition(0, 0);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(view.getTypeface(), Typeface.BOLD);
        }
        return view;
    }

    private Button ghostButton(String label, int sp) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(sp);
        button.setTextColor(0xCCFFFFFF);
        button.setBackgroundColor(0x22FFFFFF);
        return button;
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams narrowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(14);
        params.rightMargin = dp(14);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private int overlayColor() {
        int alpha = Math.round(AppSettings.overlayOpacity(this) * 255f / 100f);
        return (alpha << 24);
    }

    private void animateIn(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(8));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(180)
                .start();
    }

    private final class CurtainFrameLayout extends FrameLayout {
        private float downX;
        private float downY;
        private boolean curtainSwiping;
        private boolean curtainBlocked;

        CurtainFrameLayout(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    curtainSwiping = false;
                    curtainBlocked = (!todosLocked && isInside(todoList, event))
                            || isInside(inputRow, event)
                            || isInside(menuButton, event)
                            || isInside(menuPanel, event);
                    animate().cancel();
                    if (curtainBackground != null) {
                        curtainBackground.animate().cancel();
                    }
                    if (!curtainBlocked) {
                        setTranslationX(0f);
                        setAlpha(1f);
                        setCurtainBackgroundAlpha(1f);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (curtainBlocked) {
                        break;
                    }
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (!curtainSwiping && Math.abs(dx) > dp(22) && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                        curtainSwiping = true;
                    }
                    if (curtainSwiping) {
                        boolean bothDirections = AppSettings.curtainUnlockBothDirections(LockActivity.this);
                        float resistedDx = (!bothDirections && dx < 0) ? dx * 0.18f : dx * 0.92f;
                        setTranslationX(resistedDx);
                        updateCurtainOpacity(resistedDx, bothDirections || dx > 0);
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (curtainSwiping) {
                        releaseCurtain(event.getRawX() - downX);
                        curtainSwiping = false;
                        return true;
                    }
                    break;
                default:
                    break;
            }
            return super.dispatchTouchEvent(event);
        }

        private void releaseCurtain(float dx) {
            boolean bothDirections = AppSettings.curtainUnlockBothDirections(LockActivity.this);
            boolean allowedDirection = bothDirections || dx > 0;
            float threshold = Math.max(dp(150), getWidth() * 0.32f);
            if (allowedDirection && Math.abs(dx) >= threshold) {
                float targetX = dx < 0 ? -getWidth() : getWidth();
                animate()
                        .translationX(targetX)
                        .setDuration(240)
                        .withEndAction(() -> closeLockTask())
                        .start();
                if (curtainBackground != null) {
                    curtainBackground.animate()
                            .alpha(0f)
                            .setDuration(240)
                            .start();
                }
                return;
            }

            animate()
                    .translationX(0f)
                    .setDuration(170)
                    .start();
            if (curtainBackground != null) {
                curtainBackground.animate()
                        .alpha(1f)
                        .setDuration(170)
                        .start();
            }
        }

        private void updateCurtainOpacity(float dx, boolean allowedDirection) {
            float threshold = Math.max(dp(150), getWidth() * 0.32f);
            float progress = Math.min(1f, Math.abs(dx) / threshold);
            float fadeStrength = allowedDirection ? 0.82f : 0.24f;
            setCurtainBackgroundAlpha(Math.max(0.18f, 1f - progress * fadeStrength));
        }

        private void setCurtainBackgroundAlpha(float alpha) {
            if (curtainBackground != null) {
                curtainBackground.setAlpha(alpha);
            }
        }

        private boolean isInside(View child, MotionEvent event) {
            if (child == null || child.getVisibility() != View.VISIBLE || child.getWidth() <= 0 || child.getHeight() <= 0) {
                return false;
            }
            int[] location = new int[2];
            child.getLocationOnScreen(location);
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            return rawX >= location[0]
                    && rawX <= location[0] + child.getWidth()
                    && rawY >= location[1]
                    && rawY <= location[1] + child.getHeight();
        }
    }

    private final class SwipeActionListener implements View.OnTouchListener {
        private final TodoItem item;
        private final int index;
        private final View rowView;
        private final TextView actionHint;
        private float downX;
        private float downY;
        private boolean swiping;
        private boolean longPressTriggered;
        private Runnable longPressRunnable;

        SwipeActionListener(TodoItem item, int index, View rowView, TextView actionHint) {
            this.item = item;
            this.index = index;
            this.rowView = rowView;
            this.actionHint = actionHint;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    swiping = false;
                    longPressTriggered = false;
                    rowView.animate().cancel();
                    rowView.setScaleX(1f);
                    rowView.setScaleY(1f);
                    actionHint.setText(item.done ? "\u2713" : "");
                    actionHint.setTextColor(item.done ? 0x99FFFFFF : 0x00FFFFFF);
                    rowView.setBackgroundColor(0x00000000);
                    longPressRunnable = () -> {
                        longPressTriggered = true;
                        startTodoDrag(rowView, item);
                    };
                    uiHandler.postDelayed(longPressRunnable, 520);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float moveDx = event.getRawX() - downX;
                    float moveDy = event.getRawY() - downY;
                    if (Math.abs(moveDx) > dp(8) || Math.abs(moveDy) > dp(8)) {
                        cancelLongPress();
                    }
                    if (longPressTriggered) {
                        return true;
                    }
                    if (!swiping && Math.abs(moveDx) > dp(14) && Math.abs(moveDx) > Math.abs(moveDy)) {
                        swiping = true;
                    }
                    if (swiping) {
                        rowView.setTranslationX(moveDx * 0.82f);
                        rowView.setAlpha(1f);
                        if (moveDx < 0) {
                            actionHint.setText("\uc644\ub8cc");
                            actionHint.setTextColor(0xCC9BE7C2);
                            rowView.setBackgroundColor(0x1822B573);
                        } else {
                            actionHint.setText("\uc0ad\uc81c");
                            actionHint.setTextColor(0xFFFFB3A8);
                            rowView.setBackgroundColor(0x22C24132);
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    cancelLongPress();
                    if (longPressTriggered) {
                        rowView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(120).start();
                        return true;
                    }
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    if (Math.abs(dx) < dp(72) || Math.abs(dx) < Math.abs(dy) * 1.4f) {
                        rowView.animate()
                                .translationX(0f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .alpha(1f)
                                .setDuration(140)
                                .withEndAction(() -> {
                                    actionHint.setText(item.done ? "\u2713" : "");
                                    actionHint.setTextColor(item.done ? 0x99FFFFFF : 0x00FFFFFF);
                                    rowView.setBackgroundColor(0x00000000);
                                })
                                .start();
                        if (!swiping) {
                            view.performClick();
                        }
                        return true;
                    }
                    if (dx < 0) {
                        actionHint.setText("\uc644\ub8cc");
                        actionHint.setTextColor(0xCC9BE7C2);
                        animateThen(rowView, -rowView.getWidth(), () -> {
                            TodoStore.setDone(LockActivity.this, item.id, true);
                            refreshTodos();
                        });
                    } else {
                        actionHint.setText("\uc0ad\uc81c");
                        actionHint.setTextColor(0xFFFFB3A8);
                        animateThen(rowView, rowView.getWidth(), () -> deleteTodo(item, index));
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelLongPress();
                    rowView.animate()
                            .translationX(0f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(1f)
                            .setDuration(140)
                            .withEndAction(() -> {
                                actionHint.setText(item.done ? "\u2713" : "");
                                actionHint.setTextColor(item.done ? 0x99FFFFFF : 0x00FFFFFF);
                                rowView.setBackgroundColor(0x00000000);
                            })
                            .start();
                    return true;
                default:
                    return true;
            }
        }

        private void animateThen(View view, float targetX, Runnable action) {
            view.animate()
                    .translationX(targetX)
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .alpha(0f)
                    .setDuration(210)
                    .withEndAction(action)
                    .start();
        }

        private void cancelLongPress() {
            if (longPressRunnable != null) {
                uiHandler.removeCallbacks(longPressRunnable);
                longPressRunnable = null;
            }
        }
    }
}
