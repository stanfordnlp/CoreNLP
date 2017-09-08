/**
 * Phuong LE HONG, phuonglh@gmail.com
 */
package vn.hus.nlp.tokenizer.segmenter;

import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import vn.hus.nlp.lexicon.LexiconUnmarshaller;
import vn.hus.nlp.lexicon.jaxb.Corpus;
import vn.hus.nlp.lexicon.jaxb.W;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 * <p>
 * Dec 28, 2009, 3:23:53 PM
 * <p>
 * An additional lexicon recognizer which recognises an external lexicon provided 
 * by users in case they want to use a custom lexicon (in addition with the internal 
 * lexicon of the tokenizer). 
 */
public class ExternalLexiconRecognizer extends AbstractLexiconRecognizer {

	
	private Set<String> externalLexicon;
	
	
	/**
	 * Default constructor. 
	 */
	public ExternalLexiconRecognizer() {
		this(IConstants.EXTERNAL_LEXICON);
	}
	
	/**
	 * Creates an external lexicon recognizer given a lexicon.
	 * @param externalLexiconFilename a lexicon filename
	 */
	public ExternalLexiconRecognizer(String externalLexiconFilename) {
		// load the prefix lexicon
		// 
		LexiconUnmarshaller  lexiconUnmarshaller = new LexiconUnmarshaller();
		Corpus lexicon = lexiconUnmarshaller.unmarshal(externalLexiconFilename);
		List<W> ws = lexicon.getBody().getW();
		externalLexicon = new HashSet<String>();
		// add all prefixes to the set after converting them to lowercase
		for (W w : ws) {
			externalLexicon.add(w.getContent().toLowerCase());
		}
		System.out.println("External lexicon loaded.");
	}

	public ExternalLexiconRecognizer(Properties properties) {
		this(properties.getProperty("externalLexicon"));
	}
	
	/* (non-Javadoc)
	 * @see vn.hus.nlp.tokenizer.segmenter.AbstractLexiconRecognizer#accept(java.lang.String)
	 */
	@Override
	public boolean accept(String token) {
		return externalLexicon.contains(token);
	}

	/* (non-Javadoc)
	 * @see vn.hus.nlp.tokenizer.segmenter.AbstractLexiconRecognizer#dispose()
	 */
	@Override
	public void dispose() {
		externalLexicon.clear();
		externalLexicon = null;
	}

	/**
	 * Gets the external lexicon.
	 * @return the external lexicon.
	 */
	public Set<String> getExternalLexicon() {
		return externalLexicon;
	}
}
