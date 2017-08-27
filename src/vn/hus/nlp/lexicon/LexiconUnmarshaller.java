/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.lexicon;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import vn.hus.nlp.lexicon.jaxb.Corpus;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * A unmarshaller for lexicon.
 */
public class LexiconUnmarshaller {

	
	JAXBContext jaxbContext; 
	
	Unmarshaller unmarshaller;
	
	/**
	 * Default constructor.
	 */
	public LexiconUnmarshaller() {
		// create JAXB context
		//
		createContext();
	}
	
	private void createContext() {
		jaxbContext = null;
		try {
			jaxbContext = JAXBContext.newInstance(IConstants.PACKAGE_NAME);
		} catch (JAXBException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * Get the marshaller object.
	 * @return the marshaller object
	 */
	protected Unmarshaller getUnmarshaller() {
		if (unmarshaller == null) {
			try {
				// create the unmarshaller
				unmarshaller = jaxbContext.createUnmarshaller();
			} catch (JAXBException e) {
				e.printStackTrace();
			}
		}
		return unmarshaller;
	}

	/**
	 * Unmarshal a lexicon.
	 * @param filename a lexicon file
	 * @return a Corpus object.
	 */
	public Corpus unmarshal(String filename) {
		try {
			Object object = getUnmarshaller().unmarshal(new FileInputStream(filename));
			if (object instanceof Corpus) {
				Corpus corpus = (Corpus) object;
				return corpus;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (JAXBException e) {
			e.printStackTrace();
		}
		return null;
	}
	
}
