/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph.util;


/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * Oct 20, 2007, 11:29:53 AM
 * <p>
 * An index exception. 
 */
public class IndexException extends Exception {
	
	public IndexException() {
		super();
	}
	 @Override
	public void printStackTrace() {
		super.printStackTrace();
		System.err.println("The vertex index is not valid!");
	}
}
