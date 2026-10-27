package org.json.simple;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import junit.framework.TestCase;

/**
 * Randomised differential test against JSON-java (org.json).
 *
 * ConformanceTest uses a hand-written corpus, which is deterministic and easy to
 * review but only covers what somebody thought of. This one generates documents
 * instead: text in a dozen scripts, integers and doubles across their whole
 * ranges, booleans, nulls, and arbitrarily combined arrays and objects. It is
 * looking for the combinations nobody thought of.
 *
 * Documents are generated two ways, because one generator cannot do both jobs
 * well. The first combines value types freely and produces broad shapes; with a
 * two-in-seven chance of a container at each slot it is flat about half the
 * time, which is fine for exercising leaves but poor at depth. The second makes
 * depth the variable: a chain of up to sixty-four containers, each
 * independently an object or an array, so object-in-array-in-object alternation
 * is the normal case, with a random number of ordinary members at every level.
 *
 * Runs are reproducible. The seed is fixed so that a green build means the same
 * thing every time; pass -Djson.simple.test.seed=N to explore a different part
 * of the space, and -Djson.simple.test.documents=N or
 * -Djson.simple.test.deep.documents=N to generate more. Every failure message
 * carries the seed, and the deep one the depth, needed to replay it.
 *
 * Three properties are checked for every generated document, covering both
 * directions:
 *
 *   - what json-simple writes, the reference reads back as the same data;
 *   - what the reference writes, json-simple reads back as the same data;
 *   - json-simple's own round trip reproduces its text exactly.
 *
 * Generation deliberately stays inside the intersection of the two libraries.
 * It produces no integer wider than a long (json-simple cannot read those - see
 * ConformanceTest), no duplicate keys and no trailing commas (the reference
 * rejects both), and no unpaired surrogates (not well-formed Unicode).
 */
public class RandomConformanceTest extends TestCase {

	private static final long DEFAULT_SEED = 20120129L;
	private static final int DEFAULT_DOCUMENTS = 2000;
	private static final int DEFAULT_DEEP_DOCUMENTS = 300;

	/** Shape generation: deep enough to combine containers freely. */
	private static final int MAX_DEPTH = 6;
	private static final int MAX_MEMBERS = 6;

	/**
	 * Depth generation. Both libraries recurse somewhere: json-simple when it
	 * encodes (measured at roughly 12,000 levels on a 1 MB stack, under 2,000 on
	 * 512 KB) and the reference when it parses (roughly 4,000 and 750). Sixty-four
	 * exercises real nesting while staying an order of magnitude clear of the
	 * smaller of those, so the test cannot turn into a stack-size measurement.
	 */
	private static final int MAX_CHAIN_DEPTH = 64;

	/**
	 * Code point ranges to draw text from. Latin, accented Latin, Greek,
	 * Cyrillic, Hebrew, Arabic, Devanagari, Thai, Hiragana, Katakana, CJK,
	 * Hangul, currency and punctuation blocks that json-simple escapes, C0
	 * controls, and emoji above the BMP.
	 */
	private static final int[][] SCRIPTS = {
		{ 0x0020, 0x007e }, { 0x00a0, 0x00ff }, { 0x0370, 0x03ff },
		{ 0x0400, 0x04ff }, { 0x0590, 0x05ff }, { 0x0600, 0x06ff },
		{ 0x0900, 0x097f }, { 0x0e00, 0x0e7f }, { 0x3040, 0x309f },
		{ 0x30a0, 0x30ff }, { 0x4e00, 0x9fff }, { 0xac00, 0xd7a3 },
		{ 0x2000, 0x20ff }, { 0x0000, 0x001f }, { 0x007f, 0x009f },
		{ 0x1f300, 0x1f6ff },
	};

	private long seed;
	private Random random;

	protected void setUp() {
		seed = Long.getLong("json.simple.test.seed", DEFAULT_SEED).longValue();
		random = new Random(seed);
	}

	/** Broad shapes: every value type, containers combined freely. */
	public void testGeneratedDocumentsSurviveBothLibraries() throws Exception {
		int documents = Integer.getInteger("json.simple.test.documents",
				DEFAULT_DOCUMENTS).intValue();

		for (int i = 0; i < documents; i++) {
			Object generated = random.nextBoolean() ? object(MAX_DEPTH) : array(MAX_DEPTH);
			verify(generated, "seed " + seed + ", document " + i);
		}
	}

	/**
	 * Depth specifically. The generator above combines containers, but with a
	 * two-in-seven chance of choosing one at each slot it produces a flat
	 * document about half the time and rarely reaches its own ceiling. This test
	 * makes depth the variable instead: a chain of up to sixty-four containers,
	 * each independently an object or an array, so object-in-array-in-object
	 * alternation is the normal case rather than a lucky one. Every level also
	 * carries a random number of ordinary members beside the one that continues
	 * the chain, so width varies with depth.
	 */
	public void testDeeplyNestedDocumentsSurviveBothLibraries() throws Exception {
		int documents = Integer.getInteger("json.simple.test.deep.documents",
				DEFAULT_DEEP_DOCUMENTS).intValue();

		for (int i = 0; i < documents; i++) {
			int depth = 1 + random.nextInt(MAX_CHAIN_DEPTH);
			verify(chain(depth), "seed " + seed + ", depth " + depth + ", document " + i);
		}
	}

	/**
	 * Both directions, for one document: what json-simple writes the reference
	 * must read as the same data, what the reference writes json-simple must
	 * read as the same data, and json-simple's own round trip must reproduce its
	 * text exactly rather than merely something equivalent.
	 */
	private void verify(Object generated, String label) throws Exception {
		String written = JSONValue.toJSONString(generated);
		String where = label + ": " + abbreviate(written);

		Object read;
		try {
			read = new org.json.JSONTokener(written).nextValue();
		} catch (Throwable failure) {
			throw new AssertionError("the reference could not read json-simple's output ("
					+ where + "): " + failure);
		}
		assertEquivalent(where, generated, read);

		String rewritten = String.valueOf(read);
		assertEquivalent("re-read of the reference's output (" + where + ")",
				JSONValue.parseWithException(rewritten), read);

		assertEquals("json-simple's round trip is not stable (" + where + ")",
				written, JSONValue.toJSONString(JSONValue.parseWithException(written)));
	}

	// ---------------------------------------------------------------- values

	private Object value(int depth) {
		int choice = random.nextInt(depth > 0 ? 7 : 5);
		switch (choice) {
			case 0: return null;
			case 1: return Boolean.valueOf(random.nextBoolean());
			case 2: return Long.valueOf(anyLong());
			case 3: return Double.valueOf(anyDouble());
			case 4: return text();
			case 5: return array(depth - 1);
			default: return object(depth - 1);
		}
	}

	private JSONArray array(int depth) {
		JSONArray array = new JSONArray();
		int size = random.nextInt(MAX_MEMBERS + 1);
		for (int i = 0; i < size; i++) {
			array.add(value(depth));
		}
		return array;
	}

	private JSONObject object(int depth) {
		JSONObject object = new JSONObject();
		int size = random.nextInt(MAX_MEMBERS + 1);
		for (int i = 0; i < size; i++) {
			// A key collision would silently shrink the object and, worse, the
			// reference rejects duplicates outright, so keep them distinct.
			object.put(text() + "#" + i, value(depth));
		}
		return object;
	}

	/**
	 * A chain of `depth` containers, each independently an object or an array,
	 * each carrying a random number of leaves alongside the single member that
	 * continues the chain. The continuing member sits at a random position, so
	 * the nesting is not always the first or last element.
	 */
	private Object chain(int depth) {
		Object node = value(0);
		for (int level = 0; level < depth; level++) {
			int siblings = random.nextInt(MAX_MEMBERS);
			if (random.nextBoolean()) {
				JSONObject object = new JSONObject();
				for (int i = 0; i < siblings; i++) {
					object.put(text() + "#" + i, value(0));
				}
				object.put("nested#" + level, node);
				node = object;
			} else {
				JSONArray array = new JSONArray();
				for (int i = 0; i < siblings; i++) {
					array.add(value(0));
				}
				array.add(random.nextInt(array.size() + 1), node);
				node = array;
			}
		}
		return node;
	}

	private long anyLong() {
		switch (random.nextInt(6)) {
			case 0: return 0L;
			case 1: return Long.MAX_VALUE;
			case 2: return Long.MIN_VALUE;
			case 3: return random.nextInt(1000);
			case 4: return -random.nextInt(1000);
			default: return random.nextLong();
		}
	}

	private double anyDouble() {
		switch (random.nextInt(7)) {
			case 0: return 0.0d;
			case 1: return -0.0d;
			case 2: return Double.MAX_VALUE;
			case 3: return Double.MIN_VALUE;
			case 4: return random.nextDouble();
			case 5: return -random.nextDouble() * 1e9;
			default:
				// Anything non-finite is encoded as null and cannot round trip.
				double value = Double.longBitsToDouble(random.nextLong());
				return Double.isNaN(value) || Double.isInfinite(value) ? 1.5d : value;
		}
	}

	private String text() {
		StringBuffer text = new StringBuffer();
		int length = random.nextInt(12);
		for (int i = 0; i < length; i++) {
			int[] script = SCRIPTS[random.nextInt(SCRIPTS.length)];
			int codePoint = script[0] + random.nextInt(script[1] - script[0] + 1);
			// Unpaired surrogates are not well-formed text; skip that block.
			if (Character.isSurrogate((char) codePoint)) {
				codePoint = 'x';
			}
			text.append(Character.toChars(codePoint));
		}
		return text.toString();
	}

	private static String abbreviate(String document) {
		return document.length() <= 200 ? document : document.substring(0, 200) + "...";
	}

	// ----------------------------------------------------------- comparison

	/** Same contract as ConformanceTest.assertEquivalent: compares meaning. */
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
			assertEquals(context + ": " + mine + " != " + theirs, 0,
					new BigDecimal(mine.toString()).compareTo(new BigDecimal(theirs.toString())));
			return;
		}

		assertEquals(context, mine, theirs);
	}
}
