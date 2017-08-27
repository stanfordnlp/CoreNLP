package vn.hus.nlp.tokenizer.io;

/**
 * @author Le Hong Phuong
 *         <p>
 *         This is a helper factory class for easy of creation of outputers.
 * 
 */
public final class FormatterFactory {

	public static final String PLAIN_OUTPUTER_NAME = "PLAIN";

	public static final String TWO_COLUMNS_OUTPUTER_NAME = "2COLS";

	public static final String XML_OUTPUTER_NAME = "XML";

	/**
	 * Private constructor
	 * 
	 */
	private FormatterFactory() {
	}

	/**
	 * @return an instance of this class.
	 */
	public static FormatterFactory getDefault() {
		return new FormatterFactory();
	}

	/**
	 * Get an formatter
	 * 
	 * @param name
	 *            name of the formatter
	 * @return a formatter
	 */
	public static IOutputFormatter getFormater(String name) {
		if (name.equals(FormatterFactory.PLAIN_OUTPUTER_NAME))
			return new PlainFormatter();
		else if (name.equals(FormatterFactory.XML_OUTPUTER_NAME))
			return new XMLFormatter();
		else if (name.equals(FormatterFactory.TWO_COLUMNS_OUTPUTER_NAME))
			return new TwoColumnsFormatter();
		else return null;
	}
}
