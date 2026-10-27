package org.json.simple;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.json.simple.parser.ParseException;
import org.json.simple.parser.Yytoken;

import junit.framework.TestCase;

/**
 * Regression tests for the defects fixed in 1.1.2, plus assertions pinning the
 * behaviours listed as compatibility red lines in CLAUDE.md.
 *
 * Every test in the first half fails against 1.1.1.
 */
public class RegressionTest extends TestCase {

	/**
	 * An integer too large for a long used to escape as NumberFormatException,
	 * violating the declared contract of parseWithException.
	 */
	public void testOverflowingIntegerReportsParseException() throws Exception {
		try {
			JSONValue.parseWithException("99999999999999999999999999");
			fail("expected a ParseException");
		} catch (ParseException e) {
			assertEquals(ParseException.ERROR_UNEXPECTED_EXCEPTION, e.getErrorType());
			assertTrue(e.getUnexpectedObject() instanceof NumberFormatException);
		}
	}

	/**
	 * getArray() cast the result of the no-arg toArray(), which is always an
	 * Object[], so it threw ClassCastException for every input.
	 */
	public void testItemListGetArray() {
		String[] items = new ItemList("a,b,c").getArray();
		assertEquals(3, items.length);
		assertEquals("a", items[0]);
		assertEquals("b", items[1]);
		assertEquals("c", items[2]);
	}

	/**
	 * The two-argument constructor assigned the input string to the separator
	 * field, so toString() joined the items with the whole input.
	 */
	public void testItemListSeparatorConstructor() {
		ItemList list = new ItemList("a|b", "|");
		assertEquals(2, list.size());
		assertEquals("a", list.get(0));
		assertEquals("b", list.get(1));
		assertEquals("a|b", list.toString());
	}

	/**
	 * ParseException holds the offending token, which was not serializable, so
	 * serializing the exception threw NotSerializableException.
	 */
	public void testParseExceptionIsSerializable() throws Exception {
		ParseException original = null;
		try {
			JSONValue.parseWithException("[1,2}");
			fail("expected a ParseException");
		} catch (ParseException e) {
			original = e;
		}
		assertTrue(original.getUnexpectedObject() instanceof Yytoken);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ObjectOutputStream out = new ObjectOutputStream(bytes);
		out.writeObject(original);
		out.close();

		ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()));
		ParseException restored = (ParseException) in.readObject();
		in.close();

		assertEquals(original.getMessage(), restored.getMessage());
		assertEquals(original.getPosition(), restored.getPosition());
	}

	/*
	 * ---- Binary compatibility with the published 1.1.1. ----
	 *
	 * These three regressions reached master after 1.1.1 shipped and were fixed
	 * in 1.1.2. Source compatibility never broke, so only reflection and
	 * serialization metadata can catch them.
	 */

	/**
	 * Widening these parameters from List to Collection left already-compiled
	 * callers looking up a descriptor that no longer existed, so they failed
	 * with NoSuchMethodError at runtime.
	 */
	public void testListOverloadsStillExist() throws Exception {
		assertNotNull(JSONArray.class.getMethod("toJSONString", java.util.List.class));
		assertNotNull(JSONArray.class.getMethod("writeJSONString",
				java.util.List.class, java.io.Writer.class));
		// and the Collection forms they delegate to
		assertNotNull(JSONArray.class.getMethod("toJSONString", java.util.Collection.class));
		assertNotNull(JSONArray.class.getMethod("writeJSONString",
				java.util.Collection.class, java.io.Writer.class));

		java.util.List list = new java.util.ArrayList();
		list.add("x");
		list.add(Long.valueOf(1));
		assertEquals("[\"x\",1]", JSONArray.toJSONString(list));

		java.io.StringWriter writer = new java.io.StringWriter();
		JSONArray.writeJSONString(list, writer);
		assertEquals("[\"x\",1]", writer.toString());
	}

	/**
	 * On 1.1.1 toString() carried the description and getMessage() returned
	 * null. Both must return it now, so callers written against either release
	 * see the same text.
	 */
	public void testParseExceptionToStringCarriesTheDescription() {
		try {
			JSONValue.parseWithException("[1,2}");
			fail("expected a ParseException");
		} catch (ParseException e) {
			String expected = "Unexpected token RIGHT BRACE(}) at position 4.";
			assertEquals(expected, e.getMessage());
			assertEquals(expected, e.toString());
		}
	}

	/** Changing this value would break deserialization across versions. */
	public void testParseExceptionSerialVersionUidIsUnchanged() {
		assertEquals(-7880698968187728548L,
				java.io.ObjectStreamClass.lookup(ParseException.class).getSerialVersionUID());
	}

	/*
	 * ---- Red lines. These pin behaviour that must not change; see CLAUDE.md. ----
	 */

	/** Forward slashes are escaped, and so are U+2028 and its neighbours. */
	public void testEscapingIsUnchanged() {
		assertEquals("\"a\\/b\"", JSONValue.toJSONString("a/b"));
		assertEquals("\"\\u2028\"", JSONValue.toJSONString("\u2028"));
		assertEquals("\"\\u0085\"", JSONValue.toJSONString("\u0085"));
		assertEquals("\"\\t\"", JSONValue.toJSONString("\t"));
	}

	/** Integers are always Long and fractions always Double, at any magnitude. */
	public void testDecodedNumberTypesAreUnchanged() throws Exception {
		assertEquals(Long.class, JSONValue.parseWithException("1").getClass());
		assertEquals(Long.class, JSONValue.parseWithException("2147483648").getClass());
		assertEquals(Double.class, JSONValue.parseWithException("1.5").getClass());
		assertEquals(Double.class, JSONValue.parseWithException("1e3").getClass());
	}

	/** The deprecated parse() still swallows everything and returns null. */
	public void testDeprecatedParseStillReturnsNullOnGarbage() {
		assertNull(JSONValue.parse("{"));
		assertNull(JSONValue.parse("'nope'"));
	}

	/** Documented parser leniency, relied on downstream for well over a decade. */
	public void testParserLeniencyIsUnchanged() throws Exception {
		assertEquals("[1,2]", JSONValue.toJSONString(JSONValue.parseWithException("[1,2,]")));
		assertEquals("[1,2]", JSONValue.toJSONString(JSONValue.parseWithException("[1 2]")));
		assertEquals("[1,2]", JSONValue.toJSONString(JSONValue.parseWithException("[1,,2]")));
		assertEquals("{\"a\":1}", JSONValue.toJSONString(JSONValue.parseWithException("{\"a\" 1}")));
		assertEquals(Long.valueOf(123), JSONValue.parseWithException("0123"));
	}

	/** Decoding is iterative and must stay safe at depths that break recursion. */
	public void testDeeplyNestedInputStillDecodes() throws Exception {
		int depth = 50000;
		StringBuilder sb = new StringBuilder(depth * 2);
		for (int i = 0; i < depth; i++) {
			sb.append('[');
		}
		for (int i = 0; i < depth; i++) {
			sb.append(']');
		}
		Object decoded = JSONValue.parseWithException(sb.toString());
		assertTrue(decoded instanceof JSONArray);
	}
}
