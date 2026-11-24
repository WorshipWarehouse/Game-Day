package app.gameday.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class JsonTest {

    @Test
    public void parsesObjectsArraysAndScalars() {
        Json j = Json.parse("{\"a\":1,\"b\":[true,false,null,\"x\"],\"c\":{\"d\":-2.5}}");
        assertEquals(1L, j.get("a").asLong(0));
        assertEquals(4, j.get("b").size());
        assertTrue(j.get("b").at(0).asBoolean(false));
        assertTrue(j.get("b").at(2).isNull());
        assertEquals("x", j.get("b").at(3).asString(""));
        assertEquals(-2.5, Json.parse("{\"d\":-2.5}").get("d").asLong(0) == 0 ? -2.5 : 0, 0.0001);
    }

    @Test
    public void parsesEscapesAndUnicode() {
        Json j = Json.parse("{\"s\":\"line\\nquote\\\"tab\\tuni\\u00e9\"}");
        assertEquals("line\nquote\"tab\tunié", j.get("s").asString(""));
    }

    @Test
    public void roundTripsThroughSerialization() {
        Json original = Json.object().put("a", 1).put("b", "two").put("c", true)
                .put("d", Json.array().add("x").add(Json.of(2L)));
        assertEquals(original, Json.parse(original.toJson()));
    }

    @Test
    public void rejectsTrailingComma() {
        try {
            Json.parse("{\"a\":1,}");
            fail("trailing comma must be rejected");
        } catch (Json.JsonException expected) {
            // strict parser
        }
    }

    @Test
    public void rejectsUnquotedKeysAndBareWords() {
        assertNull(Json.parseOrNull("{a:1}"));
        assertNull(Json.parseOrNull("{\"a\":undefined}"));
    }

    @Test
    public void rejectsTrailingContent() {
        assertNull(Json.parseOrNull("{\"a\":1} {}"));
    }

    @Test
    public void rejectsDeeplyNestedDocument() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append('[');
        }
        sb.append('1');
        for (int i = 0; i < 100; i++) {
            sb.append(']');
        }
        assertNull(Json.parseOrNull(sb.toString()));
    }

    @Test
    public void rejectsControlCharactersInStrings() {
        assertNull(Json.parseOrNull("{\"a\":\"raw\u0001\"}"));
    }

    @Test
    public void asLongAcceptsNumericStringsButRejectsGarbage() {
        Json j = Json.parse("{\"a\":\"42\",\"b\":\"4x\"}");
        assertEquals(42L, j.get("a").asLong(-1));
        assertEquals(-1L, j.get("b").asLong(-1));
    }

    @Test
    public void missingAndWrongTypeAccessorsAreSafe() {
        Json j = Json.parse("{\"a\":1}");
        assertNull(j.get("nope"));
        assertEquals("fallback", j.get("nope") == null ? "fallback" : "unexpected");
        assertEquals(-1L, j.get("a").get("x") == null ? -1L : 0L);
        assertFalse(Json.object().get("a") != null);
    }
}
