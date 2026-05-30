package com.example.screenlocktodo;

import java.util.ArrayList;
import java.util.List;

final class TodoCodec {
    private TodoCodec() {
    }

    static String encode(List<TodoItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < items.size(); i++) {
            TodoItem item = items.get(i);
            if (i > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append("\"id\":").append(item.id).append(',')
                    .append("\"text\":\"").append(escape(item.text)).append("\",")
                    .append("\"done\":").append(item.done)
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    static List<TodoItem> decode(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return new Parser(raw).parseItems();
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    private static final class Parser {
        private final String raw;
        private int index;

        Parser(String raw) {
            this.raw = raw;
        }

        List<TodoItem> parseItems() {
            List<TodoItem> items = new ArrayList<>();
            skipWhitespace();
            expect('[');
            skipWhitespace();
            if (peek(']')) {
                index++;
                return items;
            }
            while (true) {
                items.add(parseItem());
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    continue;
                }
                expect(']');
                return items;
            }
        }

        private TodoItem parseItem() {
            long id = 0L;
            String text = "";
            boolean done = false;

            skipWhitespace();
            expect('{');
            skipWhitespace();
            if (peek('}')) {
                index++;
                return new TodoItem(id, text, done);
            }

            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();

                if ("id".equals(key)) {
                    id = parseLong();
                } else if ("text".equals(key)) {
                    text = parseString();
                } else if ("done".equals(key)) {
                    done = parseBoolean();
                } else {
                    skipValue();
                }

                skipWhitespace();
                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    continue;
                }
                expect('}');
                return new TodoItem(id, text, done);
            }
        }

        private void skipValue() {
            if (peek('"')) {
                parseString();
            } else if (peek('t') || peek('f')) {
                parseBoolean();
            } else if (peek('{')) {
                int depth = 0;
                do {
                    char ch = raw.charAt(index++);
                    if (ch == '{') {
                        depth++;
                    } else if (ch == '}') {
                        depth--;
                    }
                } while (index < raw.length() && depth > 0);
            } else {
                while (index < raw.length() && ",}]".indexOf(raw.charAt(index)) < 0) {
                    index++;
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < raw.length()) {
                char ch = raw.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch != '\\') {
                    builder.append(ch);
                    continue;
                }
                if (index >= raw.length()) {
                    throw error("Unfinished escape");
                }
                char escaped = raw.charAt(index++);
                switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        builder.append(escaped);
                        break;
                    case 'b':
                        builder.append('\b');
                        break;
                    case 'f':
                        builder.append('\f');
                        break;
                    case 'n':
                        builder.append('\n');
                        break;
                    case 'r':
                        builder.append('\r');
                        break;
                    case 't':
                        builder.append('\t');
                        break;
                    case 'u':
                        builder.append(parseUnicode());
                        break;
                    default:
                        throw error("Bad escape");
                }
            }
            throw error("Unfinished string");
        }

        private char parseUnicode() {
            if (index + 4 > raw.length()) {
                throw error("Bad unicode escape");
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(raw.charAt(index++), 16);
                if (digit < 0) {
                    throw error("Bad unicode escape");
                }
                value = (value << 4) + digit;
            }
            return (char) value;
        }

        private long parseLong() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            while (index < raw.length() && Character.isDigit(raw.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw error("Expected number");
            }
            return Long.parseLong(raw.substring(start, index));
        }

        private boolean parseBoolean() {
            if (raw.startsWith("true", index)) {
                index += 4;
                return true;
            }
            if (raw.startsWith("false", index)) {
                index += 5;
                return false;
            }
            throw error("Expected boolean");
        }

        private void skipWhitespace() {
            while (index < raw.length() && Character.isWhitespace(raw.charAt(index))) {
                index++;
            }
        }

        private boolean peek(char expected) {
            return index < raw.length() && raw.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (!peek(expected)) {
                throw error("Expected " + expected);
            }
            index++;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + index);
        }
    }
}
