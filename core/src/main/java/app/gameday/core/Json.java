package app.gameday.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal immutable JSON model with a strict parser and serializer.
 *
 * <p>GameDay deliberately avoids third-party JSON libraries: the payloads it handles are small
 * (update manifests, diagnostics reports) and every byte counts on the target device. The parser
 * is strict: trailing commas, comments, unquoted keys and NaN/Infinity are rejected so a malformed
 * remote manifest can never be silently half-honoured.
 */
public final class Json {

    public enum Type { OBJECT, ARRAY, STRING, NUMBER, BOOLEAN, NULL }

    private static final Json NULL = new Json(Type.NULL, null);
    private static final Json TRUE = new Json(Type.BOOLEAN, Boolean.TRUE);
    private static final Json FALSE = new Json(Type.BOOLEAN, Boolean.FALSE);

    private final Type type;
    private final Object value;

    private Json(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    public static Json nul() {
        return NULL;
    }

    public static Json of(boolean b) {
        return b ? TRUE : FALSE;
    }

    public static Json of(long n) {
        return new Json(Type.NUMBER, n);
    }

    public static Json of(double n) {
        if (Double.isNaN(n) || Double.isInfinite(n)) {
            throw new IllegalArgumentException("NaN/Infinity is not valid JSON");
        }
        return new Json(Type.NUMBER, n);
    }

    public static Json of(String s) {
        return new Json(Type.STRING, s);
    }

    public static Json object() {
        return new Json(Type.OBJECT, new LinkedHashMap<String, Json>());
    }

    public static Json array() {
        return new Json(Type.ARRAY, new ArrayList<Json>());
    }

    public Type type() {
        return type;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public boolean isObject() {
        return type == Type.OBJECT;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    public boolean isNumber() {
        return type == Type.NUMBER;
    }

    public boolean isString() {
        return type == Type.STRING;
    }

    public Json put(String key, Json v) {
        requireObject();
        map().put(key, v == null ? NULL : v);
        return this;
    }

    public Json put(String key, String v) {
        return put(key, v == null ? NULL : of(v));
    }

    public Json put(String key, long v) {
        return put(key, of(v));
    }

    public Json put(String key, boolean v) {
        return put(key, of(v));
    }

    public Json add(Json v) {
        requireArray();
        list().add(v == null ? NULL : v);
        return this;
    }

    public Json add(String v) {
        return add(of(v));
    }

    public Json get(String key) {
        if (type != Type.OBJECT) {
            return null;
        }
        return map().get(key);
    }

    public Json at(int index) {
        if (type != Type.ARRAY) {
            return null;
        }
        List<Json> l = list();
        return index >= 0 && index < l.size() ? l.get(index) : null;
    }

    public int size() {
        if (type == Type.ARRAY) {
            return list().size();
        }
        if (type == Type.OBJECT) {
            return map().size();
        }
        return 0;
    }

    public List<String> keys() {
        if (type != Type.OBJECT) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(map().keySet()));
    }

    public String asString(String fallback) {
        if (type == Type.STRING) {
            return (String) value;
        }
        if (type == Type.NUMBER) {
            return String.valueOf(value);
        }
        return fallback;
    }

    public long asLong(long fallback) {
        if (type == Type.NUMBER) {
            Object v = value;
            if (v instanceof Long) {
                return (Long) v;
            }
            double d = (Double) v;
            if (d != Math.rint(d) || d < Long.MIN_VALUE || d > Long.MAX_VALUE) {
                return fallback;
            }
            return (long) d;
        }
        if (type == Type.STRING) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public boolean asBoolean(boolean fallback) {
        if (type == Type.BOOLEAN) {
            return (Boolean) value;
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Json> map() {
        return (Map<String, Json>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Json> list() {
        return (List<Json>) value;
    }

    private void requireObject() {
        if (type != Type.OBJECT) {
            throw new IllegalStateException("not a JSON object: " + type);
        }
    }

    private void requireArray() {
        if (type != Type.ARRAY) {
            throw new IllegalStateException("not a JSON array: " + type);
        }
    }

    // ---------------------------------------------------------------- parse

    public static Json parse(String text) {
        if (text == null) {
            throw new JsonException("null input");
        }
        Parser p = new Parser(text);
        p.skipWhitespace();
        Json v = p.parseValue(0);
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw new JsonException("trailing content at offset " + p.pos);
        }
        return v;
    }

    /** Parses and returns {@code fallback} instead of throwing when the document is invalid. */
    public static Json parseOrNull(String text) {
        try {
            return parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private static final int MAX_DEPTH = 64;
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Json parseValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw new JsonException("document nested too deeply");
            }
            if (atEnd()) {
                throw new JsonException("unexpected end of input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return parseObject(depth);
                case '[':
                    return parseArray(depth);
                case '"':
                    return Json.of(parseString());
                case 't':
                    expect("true");
                    return TRUE;
                case 'f':
                    expect("false");
                    return FALSE;
                case 'n':
                    expect("null");
                    return NULL;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new JsonException("unexpected character '" + c + "' at offset " + pos);
            }
        }

        private Json parseObject(int depth) {
            Json obj = Json.object();
            pos++; // {
            skipWhitespace();
            if (!atEnd() && s.charAt(pos) == '}') {
                pos++;
                return obj;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || s.charAt(pos) != '"') {
                    throw new JsonException("expected object key at offset " + pos);
                }
                String key = parseString();
                skipWhitespace();
                if (atEnd() || s.charAt(pos) != ':') {
                    throw new JsonException("expected ':' at offset " + pos);
                }
                pos++;
                skipWhitespace();
                obj.put(key, parseValue(depth + 1));
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("unterminated object");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == '}') {
                    pos++;
                    return obj;
                }
                throw new JsonException("expected ',' or '}' at offset " + pos);
            }
        }

        private Json parseArray(int depth) {
            Json arr = Json.array();
            pos++; // [
            skipWhitespace();
            if (!atEnd() && s.charAt(pos) == ']') {
                pos++;
                return arr;
            }
            while (true) {
                skipWhitespace();
                arr.add(parseValue(depth + 1));
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("unterminated array");
                }
                char c = s.charAt(pos);
                if (c == ',') {
                    pos++;
                    continue;
                }
                if (c == ']') {
                    pos++;
                    return arr;
                }
                throw new JsonException("expected ',' or ']' at offset " + pos);
            }
        }

        private String parseString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonException("unterminated escape");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (pos + 4 > s.length()) {
                                throw new JsonException("truncated unicode escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw new JsonException("invalid unicode escape");
                            }
                            pos += 4;
                            break;
                        default:
                            throw new JsonException("invalid escape '\\" + e + "'");
                    }
                } else if (c < 0x20) {
                    throw new JsonException("unescaped control character in string");
                } else {
                    sb.append(c);
                }
            }
        }

        private Json parseNumber() {
            int start = pos;
            if (!atEnd() && s.charAt(pos) == '-') {
                pos++;
            }
            int digits = pos;
            while (!atEnd() && Character.isDigit(s.charAt(pos))) {
                pos++;
            }
            if (pos == digits) {
                throw new JsonException("invalid number at offset " + start);
            }
            boolean integral = true;
            if (!atEnd() && s.charAt(pos) == '.') {
                integral = false;
                pos++;
                int frac = pos;
                while (!atEnd() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
                if (pos == frac) {
                    throw new JsonException("invalid fraction at offset " + start);
                }
            }
            if (!atEnd() && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                integral = false;
                pos++;
                if (!atEnd() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                    pos++;
                }
                int exp = pos;
                while (!atEnd() && Character.isDigit(s.charAt(pos))) {
                    pos++;
                }
                if (pos == exp) {
                    throw new JsonException("invalid exponent at offset " + start);
                }
            }
            String raw = s.substring(start, pos);
            if (integral) {
                try {
                    return Json.of(Long.parseLong(raw));
                } catch (NumberFormatException ignored) {
                    // falls through to double for very large literals
                }
            }
            return Json.of(Double.parseDouble(raw));
        }

        private void expect(String literal) {
            if (!s.startsWith(literal, pos)) {
                throw new JsonException("expected '" + literal + "' at offset " + pos);
            }
            pos += literal.length();
        }
    }

    // ------------------------------------------------------------- serialize

    public String toJson() {
        StringBuilder sb = new StringBuilder(256);
        write(sb);
        return sb.toString();
    }

    private void write(StringBuilder sb) {
        switch (type) {
            case OBJECT: {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, Json> e : map().entrySet()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    writeString(sb, e.getKey());
                    sb.append(':');
                    e.getValue().write(sb);
                }
                sb.append('}');
                break;
            }
            case ARRAY: {
                sb.append('[');
                boolean first = true;
                for (Json v : list()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    v.write(sb);
                }
                sb.append(']');
                break;
            }
            case STRING:
                writeString(sb, (String) value);
                break;
            case NUMBER:
                sb.append(String.valueOf(value));
                break;
            case BOOLEAN:
                sb.append(((Boolean) value) ? "true" : "false");
                break;
            default:
                sb.append("null");
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20 || c == 0x7f) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Json)) {
            return false;
        }
        Json other = (Json) o;
        return type == other.type && toJson().equals(other.toJson());
    }

    @Override
    public int hashCode() {
        return toJson().hashCode();
    }
}
