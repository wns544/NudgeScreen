package com.example.screenlocktodo;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
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
    private LinearLayout inputBlock;
    private LinearLayout inputRow;
    private TextView inputDivider;
    private EditText input;
    private View plusButton;
    private TextView timeText;
    private TextView meridiemText;
    private TextView dateText;
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
        setContentView(buildContent());
        refreshTodos();
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

        plusButton = new PlusButtonView(this);
        plusButton.setOnClickListener(v -> toggleInput());
        root.addView(plusButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)
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
        inputBlock.addView(inputDivider, fullWidthWrap());

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
        animateInputBlockHeight(inputBlock.getHeight(), 0, 170, () -> inputBlock.setVisibility(View.GONE));
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
        plusButton.animate()
                .rotation(45f)
                .alpha(0.9f)
                .translationY(-dp(2))
                .scaleX(1.32f)
                .scaleY(1.32f)
                .setDuration(160)
                .start();
    }

    private void animatePlusClosed() {
        if (plusButton == null) {
            return;
        }
        plusButton.animate().cancel();
        plusButton.animate()
                .rotation(0f)
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .start();
    }

    private final class PlusButtonView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

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
            float halfLength = dp(9);
            canvas.drawLine(centerX - halfLength, centerY, centerX + halfLength, centerY, paint);
            canvas.drawLine(centerX, centerY - halfLength, centerX, centerY + halfLength, paint);
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
                            || isInside(inputBlock, event)
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
                        animateDeleteTodo(item, index, rowView);
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
