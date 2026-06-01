package com.example.screenlocktodo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

final class TodoStore {
    private static final String PREFS = "todo_lock_store";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_MIGRATED = "migrated_to_device_protected";
    private static final String KEY_TUTORIAL_SEEDED = "unlock_tutorial_seeded";
    private static final String UNLOCK_TUTORIAL_TEXT = "\ubc00\uc5b4\uc11c \uc7a0\uae08\ud574\uc81c";
    private static List<TodoItem> cachedItems;

    private TodoStore() {
    }

    static synchronized List<TodoItem> load(Context context) {
        if (cachedItems != null) {
            return new ArrayList<>(cachedItems);
        }

        Context storeContext = storageContext(context);
        migrateIfNeeded(context, storeContext);
        SharedPreferences prefs = storeContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        seedUnlockTutorialIfNeeded(prefs);
        String raw = prefs.getString(KEY_ITEMS, "[]");

        try {
            cachedItems = TodoCodec.decode(raw);
            return new ArrayList<>(cachedItems);
        } catch (IllegalArgumentException ignored) {
            prefs.edit().putString(KEY_ITEMS, "[]").apply();
            cachedItems = TodoCodec.decode("[]");
            return new ArrayList<>(cachedItems);
        }
    }

    static void warm(Context context) {
        load(context);
    }

    static void add(Context context, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        List<TodoItem> items = load(context);
        items.add(0, new TodoItem(System.currentTimeMillis(), trimmed, false));
        save(context, items);
    }

    static void setDone(Context context, long id, boolean done) {
        List<TodoItem> items = load(context);
        for (int i = 0; i < items.size(); i++) {
            TodoItem item = items.get(i);
            if (item.id == id) {
                items.set(i, item.withDone(done));
                break;
            }
        }
        save(context, items);
    }

    static void setText(Context context, long id, String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        List<TodoItem> items = load(context);
        for (int i = 0; i < items.size(); i++) {
            TodoItem item = items.get(i);
            if (item.id == id) {
                items.set(i, item.withText(trimmed));
                break;
            }
        }
        save(context, items);
    }

    static void move(Context context, long id, int targetIndex) {
        List<TodoItem> items = load(context);
        TodoItem moving = null;
        int fromIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                moving = items.remove(i);
                fromIndex = i;
                break;
            }
        }
        if (moving == null) {
            return;
        }
        int safeIndex = Math.max(0, Math.min(targetIndex, items.size()));
        if (fromIndex >= 0 && fromIndex < targetIndex) {
            safeIndex = Math.max(0, safeIndex);
        }
        items.add(safeIndex, moving);
        save(context, items);
    }

    static void remove(Context context, long id) {
        List<TodoItem> items = load(context);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id == id) {
                items.remove(i);
            }
        }
        save(context, items);
    }

    static void restore(Context context, TodoItem item, int index) {
        List<TodoItem> items = load(context);
        int safeIndex = Math.max(0, Math.min(index, items.size()));
        items.add(safeIndex, item);
        save(context, items);
    }

    private static synchronized void save(Context context, List<TodoItem> items) {
        Context storeContext = storageContext(context);
        migrateIfNeeded(context, storeContext);
        SharedPreferences prefs = storeContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_ITEMS, TodoCodec.encode(items))
                .putBoolean(KEY_TUTORIAL_SEEDED, true)
                .apply();
        cachedItems = new ArrayList<>(items);
    }

    private static void seedUnlockTutorialIfNeeded(SharedPreferences prefs) {
        if (prefs.getBoolean(KEY_TUTORIAL_SEEDED, false) || prefs.contains(KEY_ITEMS)) {
            return;
        }

        List<TodoItem> tutorialItems = new ArrayList<>();
        tutorialItems.add(new TodoItem(System.currentTimeMillis(), UNLOCK_TUTORIAL_TEXT, false));
        prefs.edit()
                .putString(KEY_ITEMS, TodoCodec.encode(tutorialItems))
                .putBoolean(KEY_TUTORIAL_SEEDED, true)
                .apply();
    }

    private static Context storageContext(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return context.createDeviceProtectedStorageContext();
        }
        return context;
    }

    private static void migrateIfNeeded(Context context, Context storeContext) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }

        SharedPreferences devicePrefs = storeContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (devicePrefs.getBoolean(KEY_MIGRATED, false)) {
            return;
        }

        boolean moved = storeContext.moveSharedPreferencesFrom(context, PREFS);
        if (!moved && !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_ITEMS)) {
            moved = true;
        }
        if (!moved) {
            return;
        }

        storeContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MIGRATED, true)
                .apply();
    }
}
