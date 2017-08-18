/**
 * @author LE Hong Phuong
 * 8 sept. 2005
 */
package vn.hus.nlp.tokenizer.io;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import vn.hus.nlp.tokenizer.tokens.TaggedWord;

/**
 * @author LE Hong Phuong
 *         <p>
 *         This class is used to export the results of tokenization using 
 *         a formatter. If a formatter is not provided, the default plain formatter
 *         is used. 
 *         </p>
 *         @see {@link IOutputFormatter}
 */
public class Outputer {
	/**
	 * An output formatter
	 */
	private IOutputFormatter formatter;

	/**
	 * A list of output listeners
	 */
	private final List<IOutputListener> listeners;

	/**
	 * Default constructor.
	 */
	public Outputer() {
		// use plain outputer by default
		this.formatter = new PlainFormatter();
		listeners = new ArrayList<IOutputListener>();
	}
	
	/**
	 * This outputer uses a formatter to output the result.
	 * @param formatter a formatter
	 */
	public Outputer(IOutputFormatter formatter) {
		this.formatter = formatter;
		listeners = new ArrayList<IOutputListener>();
	}

	/**
	 * 
	 * Output the list of tokens.
	 * @param tokens a list of tokens to be outputed.
	 * @return a string represents the list. 
	 */
	public String output(List<TaggedWord> tokens) {
		String r = "";
		int loc = 0;
		Iterator<TaggedWord> it = tokens.iterator();
		while (it.hasNext()) {
			TaggedWord token = it.next();
			r += formatter.outputLexeme(token);
			// notifies the outputed token to all listeners
			notifyAllListeners(token);
			loc++;
		}
		return r;
	}

	/**
	 * @return Return the formatter in use.
	 */
	public IOutputFormatter getFormatter() {
		return formatter;
	}

	/**
	 * Set the formatter to use.
	 * @param formatter The formatter to set.
	 */
	public void setFormatter(IOutputFormatter formatter) {
		this.formatter = formatter;
	}
	/**
	 * Return the list of output listener
	 * @return the list of output listener
	 */
	public List<IOutputListener> getOutputListeners() {
		return listeners;
	}
	/**
	 * Add an output listener to the list of listeners.
	 * @param listener an ouput listener to add.
	 */
	public void addOutputListener(IOutputListener listener) {
		if (listener != null) {
			listeners.add(listener);
		}
	}
	/**
	 * Remove a listener from the list of listener
	 * @param listener an output listener.
	 */
	public void removeOutputListener(IOutputListener listener) {
		listeners.remove(listener);
	}
	/**
	 * Remove all listeners of the outputer.
	 *
	 */
	public void removeAllListeners() {
		listeners.clear();
	}
	/**
	 * Notifies all output registered listeners of the ouputed token.
	 * This invokes the method <code>processToken()</code>
	 * of all register output listeners.
	 * @param token an outputed token.
	 */
	private void notifyAllListeners(TaggedWord token) {
		for (IOutputListener listener : listeners) {
			listener.outputToken(token);
		}
	}
}
