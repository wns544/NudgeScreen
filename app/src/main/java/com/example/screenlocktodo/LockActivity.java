package com.example.screenlocktodo;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.window.OnBackInvokedDispatcher;
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
    private static final long TODO_DOUBLE_TAP_MS = 420L;

    private LinearLayout todoList;
    private LinearLayout inputBlock;
    private LinearLayout inputRow;
    private TextView inputDivider;
    private TextView topTodoDivider;
    private EditText input;
    private PlusButtonView plusButton;
    private TextView timeText;
    private TextView meridiemText;
    private TextView dateText;
    private UndoButtonView undoButton;
    private TextView menuButton;
    private LinearLayout menuPanel;
    private LockToggleView lockButton;
    private View curtainBackground;
    private FrameLayout curtainContent;
    private TodoItem lastDeletedItem;
    private int lastDeletedIndex = -1;
    private boolean todosLocked;
    private String lastRenderedTodoKey;
    private long pendingTapItemId = -1L;
    private long draggingTodoId = -1L;
    private int draggingTodoIndex = -1;
    private int dragPreviewIndex = -1;
    private View draggingTodoRow;
    private float draggingTodoStartCenterY;
    private float draggingTouchToCenterOffset;
    private int draggingTodoMoveOffset;
    private View draggingPreviousDivider;
    private View draggingNextDivider;
    private boolean firstTodoRender = true;
    private boolean clockReceiverRegistered;
    private ValueAnimator inputBlockHeightAnimator;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver clockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateClock();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        configureLockWindow();
        super.onCreate(savedInstanceState);
        LockMonitorService.cancelLockNotification(this);
        registerBackHandler();
        setContentView(buildContent());
        refreshTodos();
    }

    @Override
    public void onBackPressed() {
        // The curtain is dismissed only by the unlock swipe, not by system back.
    }

    private void registerBackHandler() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    () -> {
                    }
            );
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        configureLockWindow();
        LockMonitorService.cancelLockNotification(this);
        updateClock();
        refreshTodos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LockMonitorService.cancelLockNotification(this);
        updateClock();
        registerClockReceiver();
        refreshTodos();
    }

    @Override
    protected void onPause() {
        unregisterClockReceiver();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterClockReceiver();
        super.onDestroy();
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

        curtainContent = new FrameLayout(this);
        shell.addView(curtainContent, new FrameLayout.LayoutParams(
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

        lockButton = new LockToggleView(this);
        lockButton.setOnClickListener(v -> toggleTodoLock());
        menuPanel.addView(lockButton, new LinearLayout.LayoutParams(dp(44), dp(44)));

        undoButton = new UndoButtonView(this);
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
        curtainContent.addView(scroll, new FrameLayout.LayoutParams(
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

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setGravity(Gravity.CENTER);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(timeRow, fullWidthWrap());

        timeText = text("", 52, 0xEFFFFFFF, false);
        timeText.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        timeRow.addView(timeText);

        meridiemText = text("", 13, 0xDFFFFFFF, false);
        meridiemText.setPadding(dp(5), 0, 0, dp(12));
        timeRow.addView(meridiemText);

        dateText = text("", 16, 0xCCFFFFFF, false);
        dateText.setGravity(Gravity.CENTER);
        root.addView(dateText, fullWidthWrap());
        updateClock();

        FrameLayout plusTouchRow = new FrameLayout(this);
        plusTouchRow.setClickable(false);
        plusTouchRow.setFocusable(false);
        plusTouchRow.setClipChildren(false);
        plusTouchRow.setClipToPadding(false);
        plusButton = new PlusButtonView(this);
        plusButton.setOnClickListener(v -> toggleInput());
        FrameLayout.LayoutParams plusButtonParams = new FrameLayout.LayoutParams(
                dp(44),
                dp(44),
                Gravity.CENTER
        );
        plusTouchRow.addView(plusButton, plusButtonParams);
        root.addView(plusTouchRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(82)
        ));

        inputBlock = new LinearLayout(this);
        inputBlock.setOrientation(LinearLayout.VERTICAL);
        inputBlock.setVisibility(View.GONE);
        inputBlock.setAlpha(0f);
        inputBlock.setTranslationY(-dp(8));
        LinearLayout.LayoutParams inputBlockParams = narrowParams();
        inputBlockParams.height = 0;
        root.addView(inputBlock, inputBlockParams);

        inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputBlock.addView(inputRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        ));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("\uc0c8 \ud560\uc77c");
        input.setGravity(Gravity.CENTER);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0x99FFFFFF);
        input.setTextSize(16);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setBackgroundColor(0x00000000);
        input.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                addTodo();
                return true;
            }
            return false;
        });
        inputRow.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        inputDivider = text("\u2013", 16, 0x66FFFFFF, false);
        inputDivider.setGravity(Gravity.CENTER);
        inputBlock.addView(inputDivider, compactDividerParams());

        topTodoDivider = text("\u2013", 16, 0x66FFFFFF, false);
        topTodoDivider.setGravity(Gravity.CENTER);
        topTodoDivider.setVisibility(View.GONE);
        root.addView(topTodoDivider, compactDividerParams());

        todoList = new LinearLayout(this);
        todoList.setOrientation(LinearLayout.VERTICAL);
        todoList.setGravity(Gravity.CENTER_HORIZONTAL);
        todoList.setPadding(0, dp(11), 0, dp(18));
        root.addView(todoList, narrowParams());

        curtainContent.addView(menuButton, menuParams);
        curtainContent.addView(menuPanel, panelParams);
        return shell;
    }

    private void updateClock() {
        if (timeText == null || meridiemText == null || dateText == null) {
            return;
        }
        Date now = new Date();
        timeText.setText(new SimpleDateFormat("h:mm", Locale.getDefault()).format(now));
        meridiemText.setText(new SimpleDateFormat("a", Locale.ENGLISH).format(now));
        dateText.setText(new SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH).format(now));
    }

    private void registerClockReceiver() {
        if (clockReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        registerReceiver(clockReceiver, filter);
        clockReceiverRegistered = true;
    }

    private void unregisterClockReceiver() {
        if (!clockReceiverRegistered) {
            return;
        }
        unregisterReceiver(clockReceiver);
        clockReceiverRegistered = false;
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
            lockButton.setLocked(todosLocked);
        }
        if (undoButton != null) {
            boolean canUndo = lastDeletedItem != null;
            undoButton.setActive(canUndo);
        }
    }

    private void addTodo() {
        if (input.getText().toString().trim().length() == 0) {
            hideInput();
            return;
        }
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

        List<TodoItem> items = TodoStore.load(this);
        String renderKey = TodoCodec.encode(items) + "|" + todosLocked;
        updateTopTodoDividerVisibility(!items.isEmpty() && inputBlock.getVisibility() != View.VISIBLE);
        if (renderKey.equals(lastRenderedTodoKey) && todoList.getChildCount() > 0) {
            return;
        }

        todoList.removeAllViews();
        lastRenderedTodoKey = renderKey;
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
            if (!todosLocked) {
                divider.setOnTouchListener(new DividerTouchListener(i));
            }
            todoList.addView(divider, compactDividerParams());
            if (animateRows) {
                animateIn(divider, i * 35 + 20);
            }
        }
        firstTodoRender = false;
    }

    private void updateTopTodoDividerVisibility(boolean visible) {
        if (topTodoDivider == null) {
            return;
        }
        topTodoDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private View todoRow(TodoItem item, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, 0, 0, 0);
        row.setTag(item.id);

        row.addView(text("", 1, 0x00FFFFFF, false), new LinearLayout.LayoutParams(dp(58), dp(38)));

        TextView label = text(item.text, 16, item.done ? 0x99FFFFFF : 0xFFFFFFFF, false);
        label.setGravity(Gravity.CENTER);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView hint = text(item.done ? "\u2713" : "", 14, item.done ? 0x99FFFFFF : 0x00FFFFFF, false);
        hint.setGravity(Gravity.CENTER);
        row.addView(hint, new LinearLayout.LayoutParams(dp(58), dp(38)));

        if (todosLocked) {
            return row;
        }

        SwipeActionListener swipeActionListener = new SwipeActionListener(item, index, row, hint);
        label.setOnTouchListener(swipeActionListener);
        label.setOnClickListener(v -> handleTodoTap(item));
        return row;
    }

    private void beginTodoDrag(View row, TodoItem item, float rawY) {
        draggingTodoId = item.id;
        draggingTodoIndex = indexOfTodoRow(row);
        dragPreviewIndex = draggingTodoIndex;
        draggingTodoRow = row;
        int[] location = new int[2];
        row.getLocationOnScreen(location);
        draggingTodoStartCenterY = location[1] + row.getHeight() * 0.5f;
        draggingTouchToCenterOffset = draggingTodoStartCenterY - rawY;
        draggingTodoMoveOffset = todoMoveOffsetForIndex(draggingTodoIndex);
        hideDraggedTodoDividers(row);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            row.setElevation(dp(6));
        }
        row.animate().scaleX(1.03f).scaleY(1.03f).alpha(0.9f).setDuration(120).start();
    }

    private void updateTodoDrag(float rawY) {
        if (draggingTodoRow == null || draggingTodoIndex < 0) {
            return;
        }
        float draggedCenterY = rawY + draggingTouchToCenterOffset;
        float translationY = draggedCenterY - draggingTodoStartCenterY;
        draggingTodoRow.setTranslationY(translationY);

        int targetIndex = dragTargetIndex(draggedCenterY);
        updateReorderPreview(targetIndex);
    }

    private void finishTodoDrag(TodoItem item) {
        if (draggingTodoRow == null || draggingTodoIndex < 0) {
            resetTodoDragState();
            return;
        }
        int targetIndex = Math.max(0, dragPreviewIndex);
        View row = draggingTodoRow;
        row.animate().cancel();
        int dropDuration = targetIndex == draggingTodoIndex ? 180 : 150;
        float targetTranslation = dragTranslationToIndex(targetIndex);
        if (targetIndex == draggingTodoIndex) {
            animateReorderPreviewToZero(dropDuration);
        }
        row.animate()
                .translationY(targetTranslation)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(dropDuration)
                .withEndAction(() -> {
                    if (targetIndex != draggingTodoIndex) {
                        TodoStore.move(LockActivity.this, item.id, targetIndex);
                        moveTodoViews(draggingTodoIndex, targetIndex);
                    }
                    clearReorderPreviewImmediately();
                    restoreDraggedTodoDividers();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        row.setElevation(0f);
                    }
                    resetTodoDragState();
                })
                .start();
    }

    private void animateReorderPreviewToZero(int duration) {
        for (int i = 0; i < todoList.getChildCount(); i++) {
            View child = todoList.getChildAt(i);
            if (child == draggingTodoRow) {
                continue;
            }
            child.animate().cancel();
            child.animate().translationY(0f).setDuration(duration).start();
        }
    }

    private void cancelTodoDrag() {
        if (draggingTodoRow != null) {
            draggingTodoRow.animate()
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(140)
                    .start();
        }
        clearReorderPreview();
        restoreDraggedTodoDividers();
        resetTodoDragState();
    }

    private void resetTodoDragState() {
        draggingTodoId = -1L;
        draggingTodoIndex = -1;
        dragPreviewIndex = -1;
        draggingTodoRow = null;
        draggingTodoStartCenterY = 0f;
        draggingTouchToCenterOffset = 0f;
        draggingTodoMoveOffset = 0;
        draggingPreviousDivider = null;
        draggingNextDivider = null;
    }

    private void hideDraggedTodoDividers(View row) {
        int rowPosition = todoList.indexOfChild(row);
        draggingPreviousDivider = rowPosition > 0 ? todoList.getChildAt(rowPosition - 1) : null;
        draggingNextDivider = rowPosition + 1 < todoList.getChildCount()
                ? todoList.getChildAt(rowPosition + 1)
                : null;
        setDividerVisibilityDuringDrag(draggingPreviousDivider, 0f);
        setDividerVisibilityDuringDrag(draggingNextDivider, 0f);
    }

    private void restoreDraggedTodoDividers() {
        setDividerVisibilityDuringDrag(draggingPreviousDivider, 1f);
        setDividerVisibilityDuringDrag(draggingNextDivider, 1f);
    }

    private void setDividerVisibilityDuringDrag(View divider, float alpha) {
        if (divider == null || divider == draggingTodoRow) {
            return;
        }
        divider.animate().cancel();
        divider.setAlpha(alpha);
    }

    private int dragTargetIndex(float rawY) {
        int itemCount = Math.max(0, (todoList.getChildCount() + 1) / 2);
        int nearestIndex = Math.max(0, Math.min(draggingTodoIndex, itemCount - 1));
        float nearestDistance = Float.MAX_VALUE;
        for (int i = 0; i < todoList.getChildCount(); i += 2) {
            View row = todoList.getChildAt(i);
            int[] location = new int[2];
            row.getLocationOnScreen(location);
            int rowIndex = i / 2;
            float middle = row == draggingTodoRow
                    ? draggingTodoStartCenterY
                    : location[1] + row.getHeight() * 0.5f - row.getTranslationY();
            float distance = Math.abs(rawY - middle);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = rowIndex;
            }
        }
        return nearestIndex;
    }

    private int indexOfTodoRow(View row) {
        int rowPosition = todoList.indexOfChild(row);
        return rowPosition < 0 ? -1 : rowPosition / 2;
    }

    private void updateReorderPreview(int targetIndex) {
        if (draggingTodoIndex < 0 || targetIndex == dragPreviewIndex) {
            return;
        }
        dragPreviewIndex = targetIndex;
        int offset = draggingTodoMoveOffset > 0 ? draggingTodoMoveOffset : todoMoveOffsetForIndex(draggingTodoIndex);
        for (int i = 0; i < todoList.getChildCount(); i += 2) {
            View row = todoList.getChildAt(i);
            Object tag = row.getTag();
            if (tag instanceof Long && (Long) tag == draggingTodoId) {
                continue;
            }
            int rowIndex = i / 2;
            float translation = 0f;
            if (targetIndex > draggingTodoIndex && rowIndex > draggingTodoIndex && rowIndex <= targetIndex) {
                translation = -offset;
            } else if (targetIndex < draggingTodoIndex && rowIndex >= targetIndex && rowIndex < draggingTodoIndex) {
                translation = offset;
            }
            row.animate().translationY(translation).setDuration(150).start();
            if (i + 1 < todoList.getChildCount()) {
                todoList.getChildAt(i + 1).animate().translationY(translation).setDuration(150).start();
            }
        }
    }

    private float dragTranslationToIndex(int targetIndex) {
        if (targetIndex == draggingTodoIndex) {
            return 0f;
        }
        int from = Math.min(draggingTodoIndex, targetIndex);
        int to = Math.max(draggingTodoIndex, targetIndex);
        int distance = 0;
        for (int index = from; index <= to; index++) {
            if (index == draggingTodoIndex) {
                continue;
            }
            distance += todoMoveOffsetForIndex(index);
        }
        return targetIndex > draggingTodoIndex ? distance : -distance;
    }

    private void clearReorderPreview() {
        for (int i = 0; i < todoList.getChildCount(); i++) {
            todoList.getChildAt(i).animate().translationY(0f).setDuration(120).start();
        }
    }

    private void clearReorderPreviewImmediately() {
        for (int i = 0; i < todoList.getChildCount(); i++) {
            View child = todoList.getChildAt(i);
            child.animate().cancel();
            child.setTranslationY(0f);
            child.setScaleX(1f);
            child.setScaleY(1f);
            child.setAlpha(1f);
        }
    }

    private void moveTodoViews(int fromIndex, int toIndex) {
        int fromChildIndex = fromIndex * 2;
        if (fromChildIndex < 0 || fromChildIndex >= todoList.getChildCount()) {
            return;
        }
        View row = todoList.getChildAt(fromChildIndex);
        View divider = fromChildIndex + 1 < todoList.getChildCount()
                ? todoList.getChildAt(fromChildIndex + 1)
                : null;
        android.animation.LayoutTransition transition = todoList.getLayoutTransition();
        todoList.setLayoutTransition(null);
        todoList.removeView(row);
        if (divider != null) {
            todoList.removeView(divider);
        }

        int safeIndex = Math.max(0, Math.min(toIndex, todoList.getChildCount() / 2));
        int insertChildIndex = safeIndex * 2;
        todoList.addView(row, insertChildIndex);
        if (divider != null) {
            todoList.addView(divider, insertChildIndex + 1);
        }
        todoList.setLayoutTransition(transition);
    }

    private int todoMoveOffsetForIndex(int index) {
        int rowHeight = dp(54);
        int dividerHeight = dp(24);
        int rowChildIndex = index * 2;
        if (rowChildIndex >= 0 && rowChildIndex < todoList.getChildCount()) {
            View row = todoList.getChildAt(rowChildIndex);
            if (row.getHeight() > 0) {
                rowHeight = row.getHeight();
            }
        }
        int dividerChildIndex = rowChildIndex + 1;
        if (dividerChildIndex >= 0 && dividerChildIndex < todoList.getChildCount()) {
            View divider = todoList.getChildAt(dividerChildIndex);
            if (divider.getHeight() > 0) {
                dividerHeight = divider.getHeight();
            }
        }
        return rowHeight + dividerHeight;
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
            }
        }, item.id, now + TODO_DOUBLE_TAP_MS);
    }

    private void showEditTodoDialog(TodoItem item) {
        Dialog dialog = new Dialog(this);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(16));
        panel.setBackground(rounded(0xEE171717, dp(18)));

        TextView title = text("\ud560\uc77c \uc218\uc815", 17, 0xF2FFFFFF, true);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, fullWidthWrap());

        EditText editor = new EditText(this);
        editor.setSingleLine(false);
        editor.setText(item.text);
        editor.setSelectAllOnFocus(false);
        editor.setTextColor(0xFFFFFFFF);
        editor.setHintTextColor(0x88FFFFFF);
        editor.setTextSize(16);
        editor.setGravity(Gravity.CENTER);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setBackground(rounded(0x22FFFFFF, dp(10)));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(62)
        );
        editorParams.topMargin = dp(14);
        panel.addView(editor, editorParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        actionsParams.topMargin = dp(10);
        panel.addView(actions, actionsParams);

        TextView cancel = text("\ucde8\uc18c", 15, 0xAAFFFFFF, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(v -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(70), dp(44)));

        TextView save = text("\uc800\uc7a5", 15, 0xF2FFFFFF, true);
        save.setGravity(Gravity.CENTER);
        save.setOnClickListener(v -> {
            TodoStore.setText(this, item.id, editor.getText().toString());
            dialog.dismiss();
            refreshTodos();
        });
        actions.addView(save, new LinearLayout.LayoutParams(dp(70), dp(44)));

        editor.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                TodoStore.setText(this, item.id, editor.getText().toString());
                dialog.dismiss();
                refreshTodos();
                return true;
            }
            return false;
        });

        dialog.setContentView(panel);
        dialog.setOnShowListener(d -> {
            editor.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0x00000000));
                dialog.getWindow().setDimAmount(0.42f);
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                dialog.getWindow().setLayout(
                        Math.round(getResources().getDisplayMetrics().widthPixels * 0.84f),
                        WindowManager.LayoutParams.WRAP_CONTENT
                );
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

    private void animateDeleteTodo(TodoItem item, int index, View rowView) {
        rowView.setEnabled(false);
        int rowPosition = todoList.indexOfChild(rowView);
        View dividerView = rowPosition >= 0 && rowPosition + 1 < todoList.getChildCount()
                ? todoList.getChildAt(rowPosition + 1)
                : null;
        int rowHeight = rowView.getHeight() > 0 ? rowView.getHeight() : dp(54);
        int dividerHeight = dividerView != null && dividerView.getHeight() > 0 ? dividerView.getHeight() : dp(24);

        rowView.animate()
                .translationX(rowView.getWidth() * 0.8f)
                .alpha(0f)
                .setDuration(150)
                .start();
        if (dividerView != null) {
            dividerView.animate()
                    .alpha(0f)
                    .translationY(-dp(4))
                    .setDuration(150)
                    .start();
        }

        ValueAnimator collapse = ValueAnimator.ofFloat(1f, 0f);
        collapse.setStartDelay(70);
        collapse.setDuration(190);
        collapse.addUpdateListener(animation -> {
            float value = (Float) animation.getAnimatedValue();
            setViewHeight(rowView, Math.round(rowHeight * value));
            if (dividerView != null) {
                setViewHeight(dividerView, Math.round(dividerHeight * value));
            }
        });
        collapse.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                deleteTodo(item, index);
            }
        });
        collapse.start();
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
        if (inputBlock.getVisibility() == View.VISIBLE) {
            hideInput();
            return;
        }
        animatePlusOpen();
        updateTopTodoDividerVisibility(false);
        inputBlock.animate().cancel();
        cancelInputBlockHeightAnimation();
        inputBlock.setVisibility(View.VISIBLE);
        inputBlock.setAlpha(0f);
        inputBlock.setTranslationY(-dp(8));
        setInputBlockHeight(0);
        animateInputBlockHeight(0, inputBlockTargetHeight(), 190, null);
        inputBlock.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(190)
                .start();
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideInput() {
        animatePlusClosed();
        inputBlock.animate().cancel();
        cancelInputBlockHeightAnimation();
        animateInputBlockHeight(inputBlock.getHeight(), 0, 170, () -> {
            inputBlock.setVisibility(View.GONE);
            updateTopTodoDividerVisibility(!TodoStore.load(this).isEmpty());
        });
        inputBlock.animate()
                .alpha(0f)
                .translationY(-dp(8))
                .setDuration(170)
                .start();
    }

    private int inputBlockTargetHeight() {
        return dp(72);
    }

    private void setInputBlockHeight(int height) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) inputBlock.getLayoutParams();
        params.height = height;
        inputBlock.setLayoutParams(params);
    }

    private void setViewHeight(View view, int height) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }

    private void animateInputBlockHeight(int fromHeight, int toHeight, long duration, Runnable endAction) {
        inputBlockHeightAnimator = ValueAnimator.ofInt(fromHeight, toHeight);
        inputBlockHeightAnimator.setDuration(duration);
        inputBlockHeightAnimator.addUpdateListener(animation -> setInputBlockHeight((Integer) animation.getAnimatedValue()));
        if (endAction != null) {
            inputBlockHeightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean canceled;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    canceled = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    inputBlockHeightAnimator = null;
                    if (!canceled) {
                        endAction.run();
                    }
                }
            });
        } else {
            inputBlockHeightAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    inputBlockHeightAnimator = null;
                }
            });
        }
        inputBlockHeightAnimator.start();
    }

    private void cancelInputBlockHeightAnimation() {
        if (inputBlockHeightAnimator != null) {
            inputBlockHeightAnimator.cancel();
            inputBlockHeightAnimator = null;
        }
    }

    private void animatePlusOpen() {
        if (plusButton == null) {
            return;
        }
        plusButton.animate().cancel();
        plusButton.animateOpen();
        plusButton.animate()
                .alpha(0.9f)
                .translationY(-dp(2))
                .setDuration(160)
                .start();
    }

    private void animatePlusClosed() {
        if (plusButton == null) {
            return;
        }
        plusButton.animate().cancel();
        plusButton.animateClosed();
        plusButton.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150)
                .start();
    }

    private final class PlusButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private ValueAnimator iconAnimator;
        private float openProgress;

        PlusButtonView(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            paint.setColor(0xBFFFFFFF);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(dp(2));
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
            super.onSizeChanged(width, height, oldWidth, oldHeight);
            setPivotX(width * 0.5f);
            setPivotY(height * 0.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float centerX = getWidth() * 0.5f;
            float centerY = getHeight() * 0.5f;
            float halfLength = dp(9) * (1f + 0.4142f * openProgress);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xBFFFFFFF);
            canvas.save();
            canvas.rotate(45f * openProgress, centerX, centerY);
            canvas.drawLine(centerX - halfLength, centerY, centerX + halfLength, centerY, paint);
            canvas.drawLine(centerX, centerY - halfLength, centerX, centerY + halfLength, paint);
            canvas.restore();
        }

        void animateOpen() {
            animateIconTo(1f, 160);
        }

        void animateClosed() {
            animateIconTo(0f, 150);
        }

        private void animateIconTo(float target, long duration) {
            if (iconAnimator != null) {
                iconAnimator.cancel();
            }
            iconAnimator = ValueAnimator.ofFloat(openProgress, target);
            iconAnimator.setDuration(duration);
            iconAnimator.addUpdateListener(animation -> {
                openProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            iconAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    iconAnimator = null;
                }
            });
            iconAnimator.start();
        }
    }

    private final class LockToggleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private boolean locked;

        LockToggleView(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            paint.setColor(0xCCFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(1.7f));
        }

        void setLocked(boolean locked) {
            this.locked = locked;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float bodyWidth = dp(14);
            float bodyHeight = dp(13);
            float left = cx - bodyWidth * 0.5f;
            float top = cy + dp(2);
            float right = cx + bodyWidth * 0.5f;
            float bottom = top + bodyHeight;
            rect.set(left, top, right, bottom);
            canvas.drawRoundRect(rect, dp(3), dp(3), paint);

            float shackleTop = top - dp(14);
            float shackleBottom = top + dp(1);
            float shackleLeft = cx - dp(6);
            float shackleRight = cx + dp(6);
            if (locked) {
                rect.set(shackleLeft, shackleTop, shackleRight, shackleBottom);
                canvas.drawArc(rect, 205, 130, false, paint);
                canvas.drawLine(shackleLeft + dp(1), top - dp(5), shackleLeft + dp(1), top - dp(1), paint);
                canvas.drawLine(shackleRight - dp(1), top - dp(5), shackleRight - dp(1), top - dp(1), paint);
            } else {
                canvas.save();
                canvas.rotate(-24f, cx + dp(4), top - dp(8));
                float openLeft = cx - dp(1);
                float openRight = cx + dp(11);
                float openTop = top - dp(15);
                float openBottom = top;
                rect.set(openLeft, openTop, openRight, openBottom);
                canvas.drawArc(rect, 206, 126, false, paint);
                canvas.drawLine(openLeft + dp(1), top - dp(6), openLeft + dp(1), top - dp(1), paint);
                canvas.restore();
            }
        }
    }

    private final class UndoButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arcBounds = new RectF();
        private boolean active;

        UndoButtonView(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(dp(1.8f));
        }

        void setActive(boolean active) {
            this.active = active;
            setEnabled(active);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setColor(active ? 0xDFFFFFFF : 0x55FFFFFF);

            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f + dp(1);
            float radius = dp(8.5f);
            arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(arcBounds, 205, 250, false, paint);

            float arrowX = cx - dp(8);
            float arrowY = cy - dp(6);
            canvas.drawLine(arrowX, arrowY, arrowX - dp(5), arrowY, paint);
            canvas.drawLine(arrowX, arrowY, arrowX, arrowY - dp(5), paint);
        }
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

    private LinearLayout.LayoutParams fullWidthWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams compactDividerParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(20)
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

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int overlayColor() {
        int alpha = Math.round(AppSettings.overlayOpacity(this) * 255f / 100f);
        return (alpha << 24);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
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
                    curtainBlocked = (!todosLocked && isInsideTodoGestureTarget(event))
                            || isInside(inputBlock, event)
                            || isInside(menuButton, event)
                            || isInside(menuPanel, event);
                    animate().cancel();
                    if (curtainBackground != null) {
                        curtainBackground.animate().cancel();
                    }
                    if (curtainContent != null) {
                        curtainContent.animate().cancel();
                    }
                    if (!curtainBlocked) {
                        setCurtainContentTranslationX(0f);
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
                        setCurtainContentTranslationX(resistedDx);
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
                if (curtainContent != null) {
                    curtainContent.animate()
                            .translationX(targetX)
                            .setDuration(240)
                            .withEndAction(() -> closeLockTask())
                            .start();
                } else {
                    closeLockTask();
                }
                if (curtainBackground != null) {
                    curtainBackground.animate()
                            .alpha(0f)
                            .setDuration(240)
                            .start();
                }
                return;
            }

            if (curtainContent != null) {
                curtainContent.animate()
                        .translationX(0f)
                        .setDuration(170)
                        .start();
            }
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

        private void setCurtainContentTranslationX(float translationX) {
            if (curtainContent != null) {
                curtainContent.setTranslationX(translationX);
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

        private boolean isInsideTodoGestureTarget(MotionEvent event) {
            if (todoList == null || todoList.getVisibility() != View.VISIBLE) {
                return false;
            }
            for (int i = 0; i < todoList.getChildCount(); i += 2) {
                View row = todoList.getChildAt(i);
                if (row instanceof LinearLayout && ((LinearLayout) row).getChildCount() > 1) {
                    View label = ((LinearLayout) row).getChildAt(1);
                    if (isInside(label, event)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    private final class DividerTouchListener implements View.OnTouchListener {
        private final int dividerIndex;
        private SwipeActionListener activeDelegate;

        DividerTouchListener(int dividerIndex) {
            this.dividerIndex = dividerIndex;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN || activeDelegate == null) {
                activeDelegate = delegateForDividerTouch(view, event);
            }
            if (activeDelegate == null) {
                return false;
            }
            boolean handled = activeDelegate.onTouch(view, event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                activeDelegate = null;
            }
            return handled;
        }

        private SwipeActionListener delegateForDividerTouch(View divider, MotionEvent event) {
            List<TodoItem> items = TodoStore.load(LockActivity.this);
            if (items.isEmpty()) {
                return null;
            }
            int[] location = new int[2];
            divider.getLocationOnScreen(location);
            float middleY = location[1] + divider.getHeight() * 0.5f;
            int targetIndex = event.getRawY() < middleY ? dividerIndex : dividerIndex + 1;
            targetIndex = Math.max(0, Math.min(targetIndex, items.size() - 1));
            int rowChildIndex = targetIndex * 2;
            if (rowChildIndex < 0 || rowChildIndex >= todoList.getChildCount()) {
                return null;
            }
            View row = todoList.getChildAt(rowChildIndex);
            if (!(row instanceof LinearLayout) || ((LinearLayout) row).getChildCount() <= 2) {
                return null;
            }
            View hintView = ((LinearLayout) row).getChildAt(2);
            if (!(hintView instanceof TextView)) {
                return null;
            }
            return new SwipeActionListener(items.get(targetIndex), targetIndex, row, (TextView) hintView);
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
                        beginTodoDrag(rowView, item, downY);
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
                        updateTodoDrag(event.getRawY());
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
                        finishTodoDrag(item);
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
                        animateDeleteTodo(item, index, rowView);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelLongPress();
                    if (longPressTriggered) {
                        cancelTodoDrag();
                        longPressTriggered = false;
                        return true;
                    }
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
