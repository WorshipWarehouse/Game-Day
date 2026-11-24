package app.gameday.core;

/** Small persistent store abstraction so core logic is testable without Android. */
public interface KeyValueStore {

    String getString(String key, String fallback);

    long getLong(String key, long fallback);

    boolean getBoolean(String key, boolean fallback);

    void putString(String key, String value);

    void putLong(String key, long value);

    void putBoolean(String key, boolean value);

    void remove(String key);

    /** @return true when the backing store accepted the write. */
    boolean commit();
}
