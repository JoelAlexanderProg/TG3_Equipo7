/*
 * $Id: JSONValue.java,v 1.1 2006/04/15 14:37:04 platform Exp $
 * Created on 2006-4-15
 */
package org.json.simple;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
// import java.util.List;
import java.util.Map;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * @author FangYidong &lt;fangyidong@gmail.com&gt;
 */
public class JSONValue {
	/**
	 * Utility class: all its methods are static, so it is not instantiated.
	 */
	private JSONValue(){
		// No instances.
	}
	
	/**
	 * Parse JSON text into java object from the input source.
	 * Please use parseWithException() if you don't want to ignore the exception.
	 * 
	 * @see org.json.simple.parser.JSONParser#parse(Reader)
	 * @see #parseWithException(Reader)
	 * 
	 * @param in
	 * @return Instance of the following:
	 *	org.json.simple.JSONObject,
	 * 	org.json.simple.JSONArray,
	 * 	java.lang.String,
	 * 	java.lang.Number,
	 * 	java.lang.Boolean,
	 * 	null
	 * 
	 * @deprecated this method may throw an {@code Error} instead of returning
	 * {@code null}; please use {@link JSONValue#parseWithException(Reader)}
	 * instead
	 */
	@Deprecated
	public static Object parse(Reader in){
		try{
			JSONParser parser=new JSONParser();
			return parser.parse(in);
		}
		catch(Exception e){
			return null;
		}
	}
	
	/**
	 * Parse JSON text into java object from the given string. 
	 * Please use parseWithException() if you don't want to ignore the exception.
	 * 
	 * @see org.json.simple.parser.JSONParser#parse(Reader)
	 * @see #parseWithException(Reader)
	 * 
	 * @param s
	 * @return Instance of the following:
	 *	org.json.simple.JSONObject,
	 * 	org.json.simple.JSONArray,
	 * 	java.lang.String,
	 * 	java.lang.Number,
	 * 	java.lang.Boolean,
	 * 	null
	 * 
	 * @deprecated this method may throw an {@code Error} instead of returning
	 * {@code null}; please use {@link JSONValue#parseWithException(String)}
	 * instead
	 */
	@Deprecated
	public static Object parse(String s){
		StringReader in=new StringReader(s);
		return parse(in);
	}
	
	/**
	 * Parse JSON text into java object from the input source.
	 * 
	 * @see org.json.simple.parser.JSONParser
	 * 
	 * @param in
	 * @return Instance of the following:
	 * 	org.json.simple.JSONObject,
	 * 	org.json.simple.JSONArray,
	 * 	java.lang.String,
	 * 	java.lang.Number,
	 * 	java.lang.Boolean,
	 * 	null
	 * 
	 * @throws IOException
	 * @throws ParseException
	 */
	public static Object parseWithException(Reader in) throws IOException, ParseException{
		JSONParser parser=new JSONParser();
		return parser.parse(in);
	}
	
	public static Object parseWithException(String s) throws ParseException{
		JSONParser parser=new JSONParser();
		return parser.parse(s);
	}
	
    /**
     * Encode an object into JSON text and write it to out.
     * <p>
     * If this object is a Map or a List, and it's also a JSONStreamAware or a JSONAware, JSONStreamAware or JSONAware will be considered firstly.
     * <p>
     * DO NOT call this method from writeJSONString(Writer) of a class that implements both JSONStreamAware and (Map or List) with 
     * "this" as the first parameter, use JSONObject.writeJSONString(Map, Writer) or JSONArray.writeJSONString(List, Writer) instead. 
     * 
     * @see org.json.simple.JSONObject#writeJSONString(Map, Writer)
     * @see org.json.simple.JSONArray#writeJSONString(List, Writer)
     * 
     * @param value the value to encode; may be null
     * @param out the writer to write the JSON text to
     * @throws IOException if the writer does
     */
	public static void writeJSONString(Object value, Writer out) throws IOException {
		if(value == null){
			out.write("null");
			return;
		}
		
		if(value instanceof String){		
            out.write('\"');
			out.write(escape((String)value));
            out.write('\"');
			return;
		}
		
		if(value instanceof Double || value instanceof Float){
			double number=((Number)value).doubleValue();
			if(Double.isInfinite(number) || Double.isNaN(number))
				out.write("null");
			else
				out.write(value.toString());
			return;
		}
		
		if(value instanceof Number || value instanceof Boolean){
			out.write(value.toString());
			return;
		}
		
		if((value instanceof JSONStreamAware)){
			((JSONStreamAware)value).writeJSONString(out);
			return;
		}
		
		if((value instanceof JSONAware)){
			out.write(((JSONAware)value).toJSONString());
			return;
		}
		
		if(value instanceof Map){
			JSONObject.writeJSONString((Map)value, out);
			return;
		}
		
		if(value instanceof Collection){
			JSONArray.writeJSONString((Collection)value, out);
            return;
		}
		
		if(JSONArray.writeJSONStringIfArray(value, out)){
			return;
		}
		
		out.write(value.toString());
	}

	/**
	 * Convert an object to JSON text.
	 * <p>
	 * If this object is a Map or a List, and it's also a JSONAware, JSONAware will be considered firstly.
	 * <p>
	 * DO NOT call this method from toJSONString() of a class that implements both JSONAware and Map or List with 
	 * "this" as the parameter, use JSONObject.toJSONString(Map) or JSONArray.toJSONString(List) instead. 
	 * 
	 * @see org.json.simple.JSONObject#toJSONString(Map)
	 * @see org.json.simple.JSONArray#toJSONString(List)
	 * 
	 * @param value
	 * @return JSON text, or "null" if value is null or it's an NaN or an INF number.
	 */
	public static String toJSONString(Object value){
		final StringWriter writer = new StringWriter();
		
		try{
			writeJSONString(value, writer);
			return writer.toString();
		} catch(IOException e){
			// This should never happen for a StringWriter
			throw new RuntimeException(e);
		}
	}

	/**
	 * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters (U+0000 through U+001F).
	 * @param s
	 * @return
	 */
	public static String escape(String s){
		if(s==null)
			return null;
        StringBuffer sb = new StringBuffer();
        escape(s, sb);
        return sb.toString();
    }

    /**
     * @param s - Must not be null.
     * @param sb
     */
    static void escape(String s, StringBuffer sb) {
    	final int len = s.length();
		for(int i=0;i<len;i++){
			char ch=s.charAt(i);
			switch(ch){
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\f':
				sb.append("\\f");
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
			case '/':
				sb.append("\\/");
				break;
			default:
				if(needsUnicodeEscape(ch)){
					sb.append(String.format("\\u%04X", (int)ch));
				}
				else{
					sb.append(ch);
				}
			}
		}//for
	}
	
	/**
	 * Tells if a character must be written as a Unicode escape sequence: the control characters
	 * (U+0000 to U+001F and U+007F to U+009F) and the characters from U+2000 to U+20FF.
	 * Reference: http://www.unicode.org/versions/Unicode5.1.0/
	 */
	private static boolean needsUnicodeEscape(char ch){
		return (ch>='\u0000' && ch<='\u001F') || (ch>='\u007F' && ch<='\u009F') || (ch>='\u2000' && ch<='\u20FF');
	}

}
