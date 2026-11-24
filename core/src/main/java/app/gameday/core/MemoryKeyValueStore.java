package app.gameday.core;

import java.util.HashMap;
import java.util.Map;

/** In-memory {@link KeyValueStore}, used by unit tests and by previews without storage. */
public final class MemoryKeyValueStore implements KeyValueStore {

    private final Map<String, Object> values = new HashMap<String, Object>();

    @Override
    public String getString(String key, String fallback) {
        Object v = values.get(key);
        return v instanceof String ? (String) v : fallback;
    }

    @Override
    public long getLong(String key, long fallback) {
        Object v = values.get(key);
        return v instanceof Long ? (Long) v : fallback;
    }

    @Override
    public boolean getBoolean(String key, boolean fallback) {
        Object v = values.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    @Override
    public void putString(String key, String value) {
        if (value == null) {
            values.remove(key);
        } else {
            values.put(key, value);
        }
    }

    @Override
    public void putLong(String key, long value) {
        values.put(key, value);
    }

    @Override
    public void putBoolean(String key, boolean value) {
        values.put(key, value);
    }

    @Override
    public void remove(String key) {
        values.remove(key);
    }

    @Override
    public boolean commit() {
        return true;
    }

    public Map<String, Object> snapshot() {
        return new HashMap<String, Object>(values);
    }
}
