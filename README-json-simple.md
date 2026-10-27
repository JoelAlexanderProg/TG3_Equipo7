![JSON.simple](doc/logo.png)

# JSON.simple

A small, dependency-free Java toolkit for encoding and decoding JSON, conforming
closely (but not strictly — see [Parser leniency](#parser-leniency)) to
[RFC 8259](https://www.rfc-editor.org/rfc/rfc8259). The jar is about 27 KB and
targets Java 8.

* Encodes and decodes `java.util.Map` / `java.util.List` directly — no data binding,
  no annotations, no reflection.
* Ships as an OSGi bundle and declares the JPMS module name `org.json.simple`.
* Includes a stoppable, SAX-style streaming parser for large documents.

```xml
<dependency>
    <groupId>com.googlecode.json-simple</groupId>
    <artifactId>json-simple</artifactId>
    <version>1.1.1</version>
</dependency>
```

1.1.1 is the current release on Maven Central. 1.1.2, described below, is not
published yet.

### Not to be confused with

Three widely used Java libraries expose a class called `JSONObject`, and they are
not compatible with one another. If code written against this one calls
`getString`, `getInt`, or `optString`, it is aimed at a different library — no
such methods exist here.

| Library | Coordinates | Reads a value as |
| --- | --- | --- |
| **This one** | `com.googlecode.json-simple:json-simple` | `(String) obj.get("k")` |
| JSON-java | `org.json:json` | `obj.getString("k")` |
| json-simple (Clifton Labs rewrite) | `com.github.cliftonlabs:json-simple` 2.x+ | `Jsoner.deserialize(...)`, `JsonObject` |

This library returns plain `Object` from `get`, so every read is a cast, and the
[type table below](#what-parse-returns) tells you what to cast to.

---

## About the 1.1.2 update

There had been no release since 2012. This one exists for two reasons.

**Fixing real defects.** Four bugs that had been present for years: an integer
too large for a `long` escaped as `NumberFormatException` instead of the
documented `ParseException`; `ItemList.getArray()` threw `ClassCastException`
for every input it was ever given; `ItemList(String, String)` used the input
string as its separator; and a `ParseException` could not be serialized. Three
further regressions had also reached `master` after 1.1.1 shipped and would have
broken existing users on upgrade — a widened parameter type that caused
`NoSuchMethodError` in already-compiled callers, a swapped
`toString()`/`getMessage()`, and a changed `serialVersionUID`. All are fixed,
and each has a regression test.

**Making the project easier to work on — for people and for AI assistants.**
A library this old carries knowledge that lived only in the maintainer's head,
which is exactly what a newcomer, human or otherwise, gets wrong first. So the
undocumented parts are now written down: the return-type contract, the parser's
long-standing leniency, and the behaviours that look like bugs but are relied on
downstream and must not be "fixed". `CLAUDE.md` records those constraints,
`Yylex.java` now says clearly that it is generated from `doc/json.lex`, and
`tools/compat-check.sh` compares every change against the last published release
on API surface, binary compatibility, behaviour, and serialization. The point is
that a wrong change now fails loudly instead of quietly shipping.

**How it was made.** This update was prepared with the help of
[Claude Code](https://claude.com/claude-code), with every change reviewed by
hand before it landed. The compatibility harness above exists partly for that
reason: generated changes need evidence, not trust.

---

## Decoding

`JSONValue.parseWithException` turns JSON text into plain Java objects.

```java
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

String text = "{\"name\":\"json-simple\",\"stars\":42,\"tags\":[\"java\",\"json\"]}";

JSONObject obj = (JSONObject) JSONValue.parseWithException(text);

String name = (String) obj.get("name");         // "json-simple"
Long stars  = (Long)   obj.get("stars");        // 42   <- Long, never Integer
JSONArray tags = (JSONArray) obj.get("tags");
String first = (String) tags.get(0);            // "java"
```

### What `parse` returns

This is the single most important thing to know about the library. Every JSON
value maps to exactly one Java type:

| JSON              | Java type                             |
| ----------------- | ------------------------------------- |
| object            | `org.json.simple.JSONObject` (a `HashMap`) |
| array             | `org.json.simple.JSONArray` (an `ArrayList`) |
| string            | `java.lang.String`                    |
| integer number    | **`java.lang.Long`** — never `Integer`, whatever the magnitude |
| fractional number | **`java.lang.Double`** — never `Float` or `BigDecimal` |
| `true` / `false`  | `java.lang.Boolean`                   |
| `null`            | `null`                                |

Casting a small integer to `Integer` is the most common mistake made against this
API; it always throws `ClassCastException`. Use `Long`, or call
`((Number) value).intValue()`.

`JSONObject` and `JSONArray` are raw `HashMap` / `ArrayList` subclasses in the 1.x
line, so values come back as `Object` and need casting. Key order in a `JSONObject`
is **not** preserved — see [Container factories](#container-factories) if you need it.

Because `get` returns `null` both for a missing key and for a key whose value is
JSON `null`, use `containsKey` when the difference matters:

```java
JSONObject obj = (JSONObject) JSONValue.parseWithException("{\"a\":null}");

obj.get("a");             // null
obj.get("b");             // null - indistinguishable
obj.containsKey("a");     // true  - present, with a null value
obj.containsKey("b");     // false - absent
```

Iterating a `JSONArray` yields `Object`, so elements need casting too:

```java
JSONArray tags = (JSONArray) obj.get("tags");
for (Object tag : tags) {
    System.out.println((String) tag);
}
```

### `parse` vs. `parseWithException`

```java
// Deprecated. Swallows every Exception and returns null, so malformed input is
// indistinguishable from a JSON document that legitimately says "null".
Object loose = JSONValue.parse(text);

// Preferred. Reports what went wrong and where.
try {
    Object strict = JSONValue.parseWithException(text);
} catch (ParseException e) {
    System.err.println(e.getMessage());   // e.g. "Unexpected token RIGHT BRACE(}) at position 4."
    System.err.println(e.getPosition());  // 0-based character offset
}
```

`JSONValue.parse` is deprecated and kept only for compatibility. It cannot report
errors, and because it catches `Exception` rather than `Throwable` it does not
even shield you from an `Error` raised deeper in the scanner.

### Reading from a stream

The `Reader` overload throws `IOException` as well as `ParseException`:

```java
try (Reader in = Files.newBufferedReader(Paths.get("config.json"), StandardCharsets.UTF_8)) {
    JSONObject obj = (JSONObject) JSONValue.parseWithException(in);
} catch (IOException e) {
    // could not read the file
} catch (ParseException e) {
    // the file is not valid JSON
}
```

---

## Encoding

`JSONValue.toJSONString` accepts any `Map`, `Collection`, array, primitive
wrapper, `String`, or `null` — the containers do not have to be `JSONObject` or
`JSONArray`.

```java
Map<String, Object> user = new LinkedHashMap<String, Object>();
user.put("name", "Yidong");
user.put("admin", true);
user.put("scores", Arrays.asList(1, 2, 3));

String json = JSONValue.toJSONString(user);
// {"name":"Yidong","admin":true,"scores":[1,2,3]}
```

Write straight to a `Writer` to avoid building the whole document in memory:

```java
try (Writer out = Files.newBufferedWriter(Paths.get("user.json"), StandardCharsets.UTF_8)) {
    JSONValue.writeJSONString(user, out);
}
```

To customise how your own class is encoded, implement `JSONAware` (returns a
`String`) or `JSONStreamAware` (writes to a `Writer`):

```java
class Point implements JSONAware {
    final int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    public String toJSONString() {
        return "{\"x\":" + x + ",\"y\":" + y + "}";
    }
}

JSONValue.toJSONString(new Point(3, 4));   // {"x":3,"y":4}
```

### Escaping

`JSONValue.escape` is deliberately conservative and escapes more than RFC 8259
requires. It emits `\/` for a forward slash, and `\uXXXX` for U+0000–U+001F,
U+007F–U+009F and U+2000–U+20FF. The last range covers U+2028 and U+2029, which
are legal in JSON but break `eval()` in some JavaScript engines.

This means `"a/b"` encodes as `"a\/b"`. That is valid JSON and every parser
accepts it, but it will not be byte-identical to the output of other libraries.

---

## Container factories

Supply a `ContainerFactory` to control which `Map` and `List` implementations the
parser builds. The usual reason is to preserve the order of object keys:

```java
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;

ContainerFactory ordered = new ContainerFactory() {
    public Map createObjectContainer() {
        return new LinkedHashMap();
    }
    // note: the interface spells this method without the trailing "e" in "create"
    public List creatArrayContainer() {
        return new ArrayList();
    }
};

JSONParser parser = new JSONParser();
Map result = (Map) parser.parse(text, ordered);   // keys in document order
```

`ContainerFactory.creatArrayContainer()` is missing a letter. It is a published
interface method, so the typo cannot be corrected without breaking every existing
implementation; a correctly spelled replacement is planned for 2.0.

---

## Streaming large documents

`ContentHandler` is a SAX-style callback interface. Returning `false` from any
callback stops the parse immediately, which makes it cheap to pull one field out
of a large document without materialising the rest.

```java
import org.json.simple.parser.ContentHandler;
import org.json.simple.parser.JSONParser;

class FindVersion implements ContentHandler {
    String version;
    private boolean inVersionKey;

    public boolean startObjectEntry(String key) {
        inVersionKey = "version".equals(key);
        return true;
    }
    public boolean primitive(Object value) {
        if (inVersionKey) {
            version = String.valueOf(value);
            return false;               // found it - stop parsing
        }
        return true;
    }

    public void startJSON() {}
    public void endJSON() {}
    public boolean startObject()      { return true; }
    public boolean endObject()        { return true; }
    public boolean endObjectEntry()   { return true; }
    public boolean startArray()       { return true; }
    public boolean endArray()         { return true; }
}

FindVersion handler = new FindVersion();
new JSONParser().parse(reader, handler);
System.out.println(handler.version);
```

A `JSONParser` instance is **not** thread-safe. Give each thread its own, or
create one per parse.

---

## Parser leniency

The parser has accepted the following non-conforming input since 2006. The
behaviour is documented here rather than changed, because downstream projects
have depended on it for well over a decade. An opt-in strict mode is planned for
2.0.

| Input                | Result        | Notes                                       |
| -------------------- | ------------- | ------------------------------------------- |
| `[1,2,]`             | `[1,2]`       | trailing comma ignored                      |
| `[1 2]`              | `[1,2]`       | separators are optional, not required       |
| `[1,,2]`             | `[1,2]`       | empty slots collapse                        |
| `{"a":1 "b":2}`      | `{"a":1,"b":2}` | commas and colons both optional           |
| `0123`               | `123`         | leading zeros allowed                       |
| `"\uZZZZ"`           | `\uZZZZ`      | invalid escape passes through literally     |
| `"a<TAB>b"`          | `"a\tb"`      | unescaped control characters allowed        |
| `1e999`              | `Infinity`    | encodes back out as `null` — lossy round trip |

Single-quoted strings, `undefined`, and capitalised `True` are correctly rejected.

Two limits are worth knowing about:

* **Decoding is iterative** and handles arbitrarily deep nesting without
  exhausting the stack.
* **Encoding is recursive.** `JSONValue.toJSONString` recurses once per level of
  nesting, so a deep enough structure exhausts the stack. The limit depends on
  the thread's stack size rather than on any fixed depth — measured at roughly
  12,000 levels on a default 1 MB stack, but under 2,000 on the 512 KB stacks
  common in containers and application servers. Because `StackOverflowError` is
  an `Error` and not an `Exception`, it is not caught by the usual handlers. Bound
  the size of untrusted input rather than relying on a depth number. A
  configurable depth limit is planned for 1.2.0.

---

## Upgrading from 1.1.1

The jar is a drop-in replacement: no public signature was removed, no
`serialVersionUID` changed, and code compiled against 1.1.1 runs unchanged.

One source-level change can affect code that is **recompiled**. Since 1.1.1,
`JSONArray.toJSONString` and `writeJSONString` gained overloads for every array
type, so a bare `null` literal no longer picks a unique method:

```java
JSONArray.toJSONString(null);           // no longer compiles: reference is ambiguous
JSONArray.toJSONString((List) null);    // pick the intended overload
```

Both forms return `"null"`. This is a compile error with a clear message, not a
silent behaviour change.

## Building

Requires JDK 9 or later to build; the resulting jar runs on Java 8.

```
mvn verify
```

Backward compatibility against the last published release is checked by:

```
tools/compat-check.sh
```

There is no Ant build any more — `build.xml` and `test.xml` specified
`source="1.2"`, which no currently supported JDK accepts.

## Contributing

`src/main/java/org/json/simple/parser/Yylex.java` is **generated** from
`doc/json.lex` by [JFlex](https://jflex.de/). Edit the `.lex` file, not the
generated scanner. See [CLAUDE.md](CLAUDE.md) for the full working agreement,
including the list of behaviours that must not change.

## Other work

Even in the AI era, R remains essential for statistics, data analysis, research,
and reproducible work. Learning it adds a powerful new skill to your programming
toolkit.

I wrote [*Master R Language in 30 Minutes*](https://www.amazon.com/dp/B0HGCTRHM5)
for experienced Java, C, C++, C#, Python, and JavaScript programmers who want to
learn R quickly and understand the mental model behind the language and tidyverse.

## License

[Apache License 2.0](LICENSE.txt).
