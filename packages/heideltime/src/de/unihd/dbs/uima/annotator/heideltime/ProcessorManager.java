package de.unihd.dbs.uima.annotator.heideltime;

import java.util.EnumMap;
import java.util.LinkedList;

import org.apache.uima.UimaContext;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.uima.annotator.heideltime.processors.GenericProcessor;
import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
/**
 * This class implements a singleton "Addon Manager". Any subroutine (Processor) that
 * may be added to HeidelTime's code to achieve a specific goal which is self-sufficient,
 * i.e. creates new Timexes by itself and without the use of HeidelTime's non-resource
 * related functionality should be registered in an instance of this class. HeidelTime's
 * annotator will then execute every registered "Processor"'s process() function.
 * Note that these Processes will be instantiated and processed after the resource
 * collection and before HeidelTime's cleanup methods.
 * @author julian zell
 *
 */
public class ProcessorManager {
	// list of processes' package names
	private EnumMap<Priority, LinkedList<String>> processorNames;
	// array of instantiated processors
	private EnumMap<Priority, LinkedList<GenericProcessor>> processors;
	// self-identifying component for logging purposes
	private Class<?> component; 
	// flag for whether the processors have been initialized
	private boolean initialized = false;
	
	/**
	 * Private constructor, only to be called by the getInstance() method.
	 */
	public ProcessorManager() {
		this.processorNames = new EnumMap<Priority, LinkedList<String>>(Priority.class);
		this.component = this.getClass();
		this.processors = new EnumMap<Priority, LinkedList<GenericProcessor>>(Priority.class);
		
		for(Priority prio : Priority.values()) {
			processorNames.put(prio, new LinkedList<String>());
			processors.put(prio, new LinkedList<GenericProcessor>());
		}
	}
	
	/**
	 * method to register a processor
	 * @param processor processor to be registered in the processormanager's list
	 * @param p priority for the process to take
	 */
	public void registerProcessor(String processor, Priority prio) {
		this.processorNames.get(prio).add(processor);
	}
	
	/**
	 * method to register a processor without priority
	 * @param processor processor to be registered in the processormanager's list
	 */
	public void registerProcessor(String processor) {
		registerProcessor(processor, Priority.POSTPROCESSING);
	}
	
	/**
	 * Based on reflection, this method instantiates and initializes all of the
	 * registered Processors.
	 * @param jcas
	 */
	public void initializeAllProcessors(UimaContext aContext) {
		for(Priority prio : processorNames.keySet()) {
			for(String pn : processorNames.get(prio)) {
				try {
					Class<?> c = Class.forName(pn);
					GenericProcessor p = (GenericProcessor) c.newInstance();
					p.initialize(aContext);
					processors.get(prio).add(p);
				} catch (Exception exception) {
					exception.printStackTrace();
					Logger.printError(component, "Unable to initialize registered Processor " + pn + ", got: " + exception.toString());
					System.exit(-1);
				}
			}
		}
		
		this.initialized = true;
	}
	
	/**
	 * Based on reflection, this method instantiates and executes all of the
	 * registered Processors.
	 * @param jcas
	 */
	public void executeProcessors(JCas jcas, ProcessorManager.Priority prio) {
		if(!this.initialized) {
			Logger.printError(component, "Unable to execute Processors; initialization was not concluded successfully.");
			System.exit(-1);
		}
		
		LinkedList<GenericProcessor> myList = processors.get(prio);
		for(GenericProcessor gp : myList) {
			try {
				gp.process(jcas);
			} catch (Exception exception) {
				exception.printStackTrace();
				Logger.printError(component, "Unable to process registered Processor " + gp.getClass().getName() + ", got: " + exception.toString());
				System.exit(-1);
			}
		}
	}
	
	public enum Priority {
		PREPROCESSING, POSTPROCESSING, ARBITRARY
	}
}
