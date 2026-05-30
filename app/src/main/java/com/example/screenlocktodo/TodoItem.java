package com.example.screenlocktodo;

final class TodoItem {
    final long id;
    final String text;
    final boolean done;

    TodoItem(long id, String text, boolean done) {
        this.id = id;
        this.text = text;
        this.done = done;
    }

    TodoItem withDone(boolean nextDone) {
        return new TodoItem(id, text, nextDone);
    }

    TodoItem withText(String nextText) {
        return new TodoItem(id, nextText, done);
    }
}
