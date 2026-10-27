package org.json.simple;

/**
 * Beans that support customized output of JSON text shall implement this interface.  
 * @author FangYidong &lt;fangyidong@gmail.com&gt;
 */
public interface JSONAware {
	/**
	 * @return JSON text
	 */
	String toJSONString();
}
