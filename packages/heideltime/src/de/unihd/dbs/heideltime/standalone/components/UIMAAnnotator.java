package de.unihd.dbs.heideltime.standalone.components;

import java.util.Properties;

import org.apache.uima.jcas.JCas;

/**
 * Interface for a common UIMA annotator.
 * 
 * @author Julian Zell, University of Heidelberg
 */
public interface UIMAAnnotator {
	/**
	 * Initializes the jcas object.
	 * 
	 * @param jcas
	 * @param language Language of document
	 */
	public abstract void initialize(Properties settings);
	
	/**
	 * Processes jcas object.
	 * 
	 * @param jcas
	 * @param language Language of document
	 */
	public abstract void process(JCas jcas);
}
