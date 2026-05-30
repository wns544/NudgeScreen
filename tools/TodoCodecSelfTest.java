package com.example.screenlocktodo;

import java.util.ArrayList;
import java.util.List;

public final class TodoCodecSelfTest {
    public static void main(String[] args) {
        roundTripPreservesItems();
        malformedInputThrows();
        emptyInputIsEmptyList();
        System.out.println("TodoCodecSelfTest passed");
    }

    private static void roundTripPreservesItems() {
        List<TodoItem> items = new ArrayList<>();
        items.add(new TodoItem(200L, "삭제할 일", false));
        items.add(new TodoItem(100L, "지문 해제 전 확인\n따옴표 \" 테스트", true));

        String encoded = TodoCodec.encode(items);
        List<TodoItem> decoded = TodoCodec.decode(encoded);

        assertEquals(2, decoded.size(), "decoded size");
        assertEquals(200L, decoded.get(0).id, "first id");
        assertEquals("삭제할 일", decoded.get(0).text, "first text");
        assertEquals(false, decoded.get(0).done, "first done");
        assertEquals(100L, decoded.get(1).id, "second id");
        assertEquals("지문 해제 전 확인\n따옴표 \" 테스트", decoded.get(1).text, "second text");
        assertEquals(true, decoded.get(1).done, "second done");

        List<TodoItem> afterDelete = new ArrayList<>(decoded);
        afterDelete.remove(0);
        List<TodoItem> decodedAfterDelete = TodoCodec.decode(TodoCodec.encode(afterDelete));

        assertEquals(1, decodedAfterDelete.size(), "size after delete");
        assertEquals(100L, decodedAfterDelete.get(0).id, "remaining id");
    }

    private static void malformedInputThrows() {
        try {
            TodoCodec.decode("[{\"id\":]");
            throw new AssertionError("malformed input should throw");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void emptyInputIsEmptyList() {
        assertEquals(0, TodoCodec.decode("").size(), "empty string size");
        assertEquals(0, TodoCodec.decode(null).size(), "null size");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }
}
