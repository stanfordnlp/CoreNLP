
/* -- vnTokenizer 2.0 --
*
* Copyright information:
*
* LE Hong Phuong, NGUYEN Thi Minh Huyen,
* Faculty of Mathematics Mechanics and Informatics, 
* Hanoi University of Sciences, Vietnam.
*
* Copyright (c) 2003
* All rights reserved.
*
* Redistribution and use in source and binary forms are permitted
* provided that the above copyright notice and this paragraph are
* duplicated in all such forms and that any documentation,
* advertising materials, and other materials related to such
* distribution and use acknowledge that the software was developed
* by the author.  The name of the author may not be used to
* endorse or promote products derived from this software without
* specific prior written permission.
* 
* 
* THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR
* IMPLIED WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED
* WARRANTIES OF MERCHANTIBILITY AND FITNESS FOR A PARTICULAR PURPOSE.
* 
* 
* Last update : 04/2005
* 
*/

package vn.hus.nlp.tokenizer;

import java.util.ResourceBundle;

/**
 *
 * This class represents a resource handler of entire module. It is used to facilitate
 * manipulations of a ressource bundler.  
 * 
 */
public final class ResourceHandler {
	
	
	/**
	 * Get a resource value
	 * @param key a key of resource
	 * @return value of resource
	 */
	public static String get(String key) {
		return resource.getString(key);
	}
	
	/**
	 * The ressource bundle of the package
	 */
	static final ResourceBundle resource = ResourceBundle.getBundle("tokenizer");
 
}
