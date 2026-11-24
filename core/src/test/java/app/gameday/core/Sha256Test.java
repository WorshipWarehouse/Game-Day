package app.gameday.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class Sha256Test {

    private static final String EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final String ABC =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    public void knownDigests() {
        assertEquals(EMPTY, Sha256.of(new byte[0]));
        assertEquals(ABC, Sha256.ofUtf8("abc"));
    }

    @Test
    public void streamDigestMatchesByteDigest() throws Exception {
        byte[] data = "GameDay".getBytes(StandardCharsets.UTF_8);
        assertEquals(Sha256.of(data),
                Sha256.ofStream(new ByteArrayInputStream(data)));
    }

    @Test
    public void matchesNormalizesSeparatorsAndCase() {
        assertTrue(Sha256.matches(ABC, ABC));
        assertTrue(Sha256.matches(ABC.toUpperCase(), ABC));
        assertTrue(Sha256.matches("BA:78:16:BF", "ba7816bf"));
    }

    @Test
    public void matchesRejectsMalformedOrEmptyExpectations() {
        assertFalse(Sha256.matches(null, ABC));
        assertFalse(Sha256.matches("", ""));
        assertFalse(Sha256.matches(ABC, ABC.substring(0, 60)));
        assertFalse(Sha256.matches("zzzz", "zzzz"));
    }
}
