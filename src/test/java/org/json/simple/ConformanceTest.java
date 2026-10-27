package org.json.simple;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.json.simple.parser.ParseException;

import junit.framework.TestCase;

/**
 * Differential test against JSON-java (org.json), the reference implementation.
 *
 * Every other test in this project checks json-simple against itself, so it can
 * only catch what the author thought to check. This one uses an independently
 * written library as the judge.
 *
 * Comparison is semantic, not textual. The two libraries produce different but
 * equally valid JSON text for the same data, and none of those differences is a
 * defect:
 *
 *   - json-simple escapes "/" as "\/" and escapes U+007F; the reference does
 *     not. Both are legal, and both are on this project's list of behaviours
 *     that must not change.
 *   - json-simple writes u-escapes with upper-case hex digits, the reference
 *     lower-case.
 *   - Numbers are formatted differently: 1.0E10 against 1E+10, -0.0 against -0.
 *
 * Comparing the text would therefore report failures for correct output. What
 * matters is that both libraries agree on what a document *means*, so the
 * assertions below compare decoded structures, using the reference parser as an
 * independent reader of json-simple's output.
 *
 * Note that org.json.JSONObject and org.json.simple.JSONObject are different
 * classes with the same name; the reference types are spelled out in full here.
 */
public class ConformanceTest extends TestCase {

	/**
	 * Strictly valid RFC 8259 documents. This is deliberately not the place for
	 * json-simple's documented leniency (trailing commas, optional separators,
	 * duplicate keys): the reference implementation rejects those, and the
	 * disagreement would say nothing about correctness. RegressionTest pins that
	 * behaviour instead.
	 */
	private static final String[] DOCUMENTS = {
		"{}",
		"[]",
		"null",
		"true",
		"false",
		"0",
		"-1",
		"42",
		"1.5",
		"1.50",
		"-0.0",
		"1e10",
		"1E-10",
		"1.7976931348623157e308",
		"9223372036854775807",
		"-9223372036854775808",
		"\"\"",
		"\"plain\"",
		"\"a/b\"",
		"\"</script>\"",
		"\"tab\\there\"",
		"\"newline\\nhere\"",
		"\"quote\\\"here\"",
		"\"backslash\\\\here\"",
		"\"\\b\\f\\r\"",
		"\"\\u0001\"",
		"\"\\u007f\"",
		"\"\\u0085\"",
		"\"\\u2028\\u2029\"",
		"\"\\u20ac\"",
		"\"\\ud83d\\ude00\"",
		"\"caf\u00e9\"",
		"[1,2,3]",
		"[\"a\",\"b\"]",
		"[null,true,false]",
		"[[],[[]],[[[]]]]",
		"[{},{\"a\":1}]",
		"{\"a\":1}",
		"{\"\":1}",
		"{\"a\":null}",
		"{\"a\":\"/\"}",
		"{\"a\":{\"b\":{\"c\":[1,2,{\"d\":null}]}}}",
		"{\"one\":1,\"two\":2,\"three\":3,\"four\":4,\"five\":5}",
		"  {  \"a\"  :  [ 1 , 2 ]  }  ",
		"{\"unicode\":\"\\u4e2d\\u6587\",\"emoji\":\"\\ud83c\\udf89\"}",
	};

	/** Both libraries must read the same document as the same data. */
	public void testDecodingAgreesWithTheReference() throws Exception {
		for (int i = 0; i < DOCUMENTS.length; i++) {
			String document = DOCUMENTS[i];
			assertEquivalent(document,
					JSONValue.parseWithException(document),
					reference(document));
		}
	}

	/**
	 * What json-simple writes must mean the same thing when an independent
	 * parser reads it back. This is what catches an encoder that escapes
	 * incorrectly or emits malformed text: json-simple's own parser might well
	 * accept its own mistake, but the reference will not.
	 */
	public void testEncodingIsReadableByTheReference() throws Exception {
		for (int i = 0; i < DOCUMENTS.length; i++) {
			String document = DOCUMENTS[i];
			String encoded = JSONValue.toJSONString(JSONValue.parseWithException(document));
			assertEquivalent(document + " -> " + encoded,
					JSONValue.parseWithException(document),
					reference(encoded));
		}
	}

	/** And json-simple must read back what the reference writes. */
	public void testReferenceOutputIsReadableByJsonSimple() throws Exception {
		for (int i = 0; i < DOCUMENTS.length; i++) {
			// A bare scalar comes back from the reference as a plain Java value
			// whose toString() is not JSON text, so wrap those in an array to
			// get something the reference will actually serialise. The scalar
			// still gets exercised, one level down.
			String document = isContainer(DOCUMENTS[i])
					? DOCUMENTS[i]
					: "[" + DOCUMENTS[i] + "]";
			String encoded = String.valueOf(reference(document));
			assertEquivalent(document + " <- " + encoded,
					JSONValue.parseWithException(encoded),
					reference(document));
		}
	}

	/**
	 * A differential test only catches what the other implementation rejects or
	 * reads differently, and the reference is laxer than RFC 8259 here: of the
	 * thirty-two control characters it refuses only NUL, LF and CR inside a
	 * string, so an encoder that stopped escaping U+0001 would produce invalid
	 * JSON that this comparison still called equivalent. Check the text itself.
	 *
	 * json-simple emits no insignificant whitespace, so no character below
	 * U+0020 may appear anywhere in its output.
	 */
	public void testEncodedOutputHasNoRawControlCharacters() throws Exception {
		for (int i = 0; i < DOCUMENTS.length; i++) {
			String encoded = JSONValue.toJSONString(
					JSONValue.parseWithException(DOCUMENTS[i]));
			for (int at = 0; at < encoded.length(); at++) {
				char c = encoded.charAt(at);
				assertTrue("unescaped control character U+"
								+ Integer.toHexString(c) + " at " + at
								+ " of " + DOCUMENTS[i] + " -> " + encoded,
						c >= 0x20);
			}
		}
	}

	/**
	 * A known and accepted divergence, asserted so that it is visible and so
	 * that this test starts failing the day it is addressed.
	 *
	 * The reference implementation returns a BigInteger for an integer too large
	 * for a long. json-simple reports a ParseException instead. Reading such
	 * numbers is a 1.2.0 item; until then the failure is at least well formed,
	 * which it was not before 1.1.2 - a raw NumberFormatException used to escape.
	 */
	public void testIntegersBeyondLongAreNotYetSupported() {
		String document = "123456789012345678901234567890";

		assertEquals(java.math.BigInteger.class, reference(document).getClass());

		try {
			JSONValue.parseWithException(document);
			fail("json-simple now reads integers beyond long; update this test "
					+ "and the note in README.md");
		} catch (ParseException expected) {
			assertEquals(ParseException.ERROR_UNEXPECTED_EXCEPTION, expected.getErrorType());
		}
	}

	// ----------------------------------------------------------------------

	private static Object reference(String document) {
		return new org.json.JSONTokener(document).nextValue();
	}

	private static boolean isContainer(String document) {
		String trimmed = document.trim();
		return trimmed.startsWith("{") || trimmed.startsWith("[");
	}

	/**
	 * Compares a json-simple value against a reference value by meaning:
	 * containers by contents, numbers by numeric value rather than by the way
	 * they happen to be formatted, everything else by equality.
	 */
	private static void assertEquivalent(String context, Object mine, Object theirs) {
		if (mine == null || org.json.JSONObject.NULL.equals(theirs)) {
			assertTrue(context + ": one side is null, the other is " + mine + " / " + theirs,
					mine == null && org.json.JSONObject.NULL.equals(theirs));
			return;
		}

		if (mine instanceof Map) {
			assertTrue(context + ": expected an object, got " + theirs.getClass().getName(),
					theirs instanceof org.json.JSONObject);
			Map mineMap = (Map) mine;
			org.json.JSONObject theirsObject = (org.json.JSONObject) theirs;
			assertEquals(context + ": different number of members",
					mineMap.size(), theirsObject.length());
			for (Iterator it = mineMap.keySet().iterator(); it.hasNext();) {
				String key = String.valueOf(it.next());
				assertTrue(context + ": the reference has no member " + key,
						theirsObject.has(key));
				assertEquivalent(context + "." + key, mineMap.get(key), theirsObject.get(key));
			}
			return;
		}

		if (mine instanceof List) {
			assertTrue(context + ": expected an array, got " + theirs.getClass().getName(),
					theirs instanceof org.json.JSONArray);
			List mineList = (List) mine;
			org.json.JSONArray theirsArray = (org.json.JSONArray) theirs;
			assertEquals(context + ": different number of elements",
					mineList.size(), theirsArray.length());
			for (int i = 0; i < mineList.size(); i++) {
				assertEquivalent(context + "[" + i + "]", mineList.get(i), theirsArray.get(i));
			}
			return;
		}

		if (mine instanceof Number) {
			assertTrue(context + ": expected a number, got " + theirs.getClass().getName(),
					theirs instanceof Number);
			// 1.0E10 and 1E+10 are the same number written two ways, and so are
			// -0.0 and -0; compareTo ignores scale, equals would not.
			assertEquals(context + ": " + mine + " != " + theirs, 0,
					new BigDecimal(mine.toString()).compareTo(new BigDecimal(theirs.toString())));
			return;
		}

		assertEquals(context, mine, theirs);
	}
}
