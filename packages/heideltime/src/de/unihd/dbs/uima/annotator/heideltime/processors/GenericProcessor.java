package de.unihd.dbs.uima.annotator.heideltime.processors;

import org.apache.uima.UimaContext;
import org.apache.uima.jcas.JCas;
/**
 * 
 * Abstract class to for all Processors to inherit from. A processor is a
 * modular, self-sufficient piece of code that was added to HeidelTime to
 * fulfill a specific function.
 * @author julian zell
 *
 */
public abstract class GenericProcessor {
	protected Class<?> component;
	
	/**
	 * Constructor that sets the component for logger use.
	 * Any inheriting class should run this via super()
	 */
	public GenericProcessor() {
		this.component = this.getClass();
	}
	
	/**
	 * sets up for later work done in process(). This shouldn't change the jcas object.
	 * @param jcas
	 * @throws ProcessorInitializationException Exception
	 */
	public abstract void initialize(final UimaContext aContext) throws ProcessorInitializationException;
	
	/**
	 * starts the processing of the processor during HeidelTime's process()ing method.
	 * @param jcas
	 */
	public abstract void process(JCas jcas) throws ProcessorProcessingException;
}
