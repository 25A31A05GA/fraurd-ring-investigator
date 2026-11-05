package com.frauddetect;

import java.util.*;

/** Tiny JSON writer so the project needs zero third-party dependencies. */
final class Json {
    private Json() { }

    static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) sb.append("null");
        else if (o instanceof Number || o instanceof Boolean) sb.append(o);
        else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                str(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Record rec) {
            Map<String, Object> m = new LinkedHashMap<>();
            try {
                for (java.lang.reflect.RecordComponent rc : rec.getClass().getRecordComponents()) {
                    java.lang.reflect.Method acc = rc.getAccessor();
                    acc.setAccessible(true);
                    m.put(rc.getName(), acc.invoke(rec));
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            write(sb, m);
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object x : it) { if (!first) sb.append(','); first = false; write(sb, x); }
            sb.append(']');
        } else str(sb, String.valueOf(o));
    }

    private static void str(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        sb.append('"');
    }

    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
