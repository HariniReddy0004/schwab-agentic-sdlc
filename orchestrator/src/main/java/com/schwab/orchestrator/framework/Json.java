package com.schwab.orchestrator.framework;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal, dependency-free JSON reader/writer.
 *
 * This project has no access to Maven Central in its build environment, so rather than pull in
 * Jackson/Gson, we hand-roll the small subset of JSON support the API layer actually needs:
 * objects, arrays, strings, numbers, booleans, null. It is not a general-purpose JSON library
 * (no comments, no streaming), but it is fully spec-compliant for the documents this service
 * sends and receives.
 */
public final class Json {

    private Json() {
    }

    // ---------------------------------------------------------------- write

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                writeValue(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object o : list) {
                if (!first) sb.append(',');
                first = false;
                writeValue(o, sb);
            }
            sb.append(']');
        } else if (value instanceof Object[] arr) {
            writeValue(List.of(arr), sb);
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ----------------------------------------------------------------- read

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.readValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new IllegalArgumentException("Trailing content in JSON at position " + p.pos);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a JSON object at top level");
        }
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int pos;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (pos >= s.length()) throw new IllegalArgumentException("Unexpected end of JSON input");
            return s.charAt(pos);
        }

        Object readValue() {
            skipWs();
            char c = peek();
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object value = readValue();
                map.put(key, value);
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or '}' at position " + pos);
                }
            }
            return map;
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = readValue();
                list.add(value);
                skipWs();
                char c = peek();
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    break;
                } else {
                    throw new IllegalArgumentException("Expected ',' or ']' at position " + pos);
                }
            }
            return list;
        }

        String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> throw new IllegalArgumentException("Invalid escape \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean readBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid literal at position " + pos);
        }

        Object readNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid literal at position " + pos);
        }

        Double readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || "+-.eE".indexOf(s.charAt(pos)) >= 0)) {
                pos++;
            }
            String numStr = s.substring(start, pos);
            if (numStr.isEmpty()) throw new IllegalArgumentException("Invalid number at position " + pos);
            return Double.parseDouble(numStr);
        }

        void expect(char c) {
            if (pos >= s.length() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }
    }

    // ------------------------------------------------------------- helpers

    public static String str(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static String str(Map<String, Object> obj, String key, String def) {
        String v = str(obj, key);
        return v == null ? def : v;
    }

    public static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    public static Integer asInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    public static Boolean asBool(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}
