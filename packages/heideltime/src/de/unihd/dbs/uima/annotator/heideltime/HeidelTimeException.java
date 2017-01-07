/**
 * A dedicated Exception type for HeidelTime-based Exceptions.
 */
package de.unihd.dbs.uima.annotator.heideltime;

/**
 * @author Julian Zell
 *
 */
public class HeidelTimeException extends Exception {
	/**
	 * automatically generated uid
	 */
	private static final long serialVersionUID = 5934916764509083459L;
	private String message;
	
	public HeidelTimeException() {};
	
	public HeidelTimeException(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return this.message;
	}

}
