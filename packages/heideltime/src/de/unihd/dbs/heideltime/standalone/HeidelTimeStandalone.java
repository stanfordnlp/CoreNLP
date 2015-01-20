/*
 * HeidelTimeStandalone.java
 *
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, University of Heidelberg.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU General Public License.
 *
 * authors: Andreas Fay, Jannik Strötgen
 * email:  fay@stud.uni-heidelberg.de, stroetgen@uni-hd.de
 *
 * HeidelTime is a multilingual, cross-domain temporal tagger.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */ 

package de.unihd.dbs.heideltime.standalone;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.uima.UIMAFramework;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.XMLInputSource;

import de.unihd.dbs.heideltime.standalone.components.JCasFactory;
import de.unihd.dbs.heideltime.standalone.components.ResultFormatter;
import de.unihd.dbs.heideltime.standalone.components.PartOfSpeechTagger;
import de.unihd.dbs.heideltime.standalone.components.impl.HunPosTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.IntervalTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.JCasFactoryImpl;
import de.unihd.dbs.heideltime.standalone.components.impl.JVnTextProWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.StanfordPOSTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.TimeMLResultFormatter;
import de.unihd.dbs.heideltime.standalone.components.impl.TreeTaggerWrapper;
import de.unihd.dbs.heideltime.standalone.components.impl.UimaContextImpl;
import de.unihd.dbs.heideltime.standalone.components.impl.XMIResultFormatter;
import de.unihd.dbs.heideltime.standalone.exceptions.DocumentCreationTimeMissingException;
import de.unihd.dbs.uima.annotator.heideltime.HeidelTime;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.intervaltagger.IntervalTagger;
import de.unihd.dbs.uima.types.heideltime.Dct;

/**
 * Execution class for UIMA-Component HeidelTime. Singleton-Pattern
 * 
 * @author Andreas Fay, Jannik Strötgen, Heidelberg Universtiy
 * @version 1.01
 */
public class HeidelTimeStandalone {

	/**
	 * Used document type
	 */
	private DocumentType documentType;

	/**
	 * HeidelTime instance
	 */
	private HeidelTime heidelTime;

	/**
	 * Type system description of HeidelTime
	 */
	private JCasFactory jcasFactory;

	/**
	 * Used language
	 */
	private Language language;

	/**
	 * output format
	 */
	private OutputType outputType;

	/**
	 * POS tagger
	 */
	private POSTagger posTagger;

	/**
	 * Whether or not to do Interval Tagging
	 */
	private Boolean doIntervalTagging;

	/**
	 * Logging engine
	 */
	private static Logger logger = Logger.getLogger("HeidelTimeStandalone");

	
	/**
	 * empty constructor.
	 * 
	 * call initialize() after using this!
	 * 
	 * @param language
	 * @param typeToProcess
	 * @param outputType
	 */
	public HeidelTimeStandalone() {
	}
	
	/**
	 * constructor
	 * @param language
	 * @param typeToProcess
	 * @param outputType
	 */
	public HeidelTimeStandalone(Language language, DocumentType typeToProcess, OutputType outputType) {
		this(language, typeToProcess, outputType, null);
	}
	
	/**
	 * Constructor with configPath. Used primarily for WebUI
	 * 
	 * @param language
	 * @param typeToProcess
	 * @param outputType
	 * @param configPath
	 */
	public HeidelTimeStandalone(Language language, DocumentType typeToProcess, OutputType outputType, String configPath) {
		this.language = language;
		this.documentType = typeToProcess;
		this.outputType = outputType;
		
		this.initialize(language, typeToProcess, outputType, configPath);
	}
	
	/**
	 * Constructor with configPath
	 * 
	 * @param language
	 * @param typeToProcess
	 * @param outputType
	 * @param configPath
	 * @param posTagger
	 */
	public HeidelTimeStandalone(Language language, DocumentType typeToProcess, OutputType outputType, String configPath, POSTagger posTagger) {
		this.language = language;
		this.documentType = typeToProcess;
		this.outputType = outputType;
		
		this.initialize(language, typeToProcess, outputType, configPath, posTagger);
	}
	
	/**
	 * Constructor with configPath
	 * 
	 * @param language
	 * @param typeToProcess
	 * @param outputType
	 * @param configPath
	 * @param posTagger
	 */
	public HeidelTimeStandalone(Language language, DocumentType typeToProcess, OutputType outputType, String configPath, POSTagger posTagger, Boolean doIntervalTagging) {
		this.language = language;
		this.documentType = typeToProcess;
		this.outputType = outputType;
		this.doIntervalTagging = doIntervalTagging;
		
		this.initialize(language, typeToProcess, outputType, configPath, posTagger, doIntervalTagging);
	}

	/**
	 * Method that initializes all vital prerequisites
	 * 
	 * @param language	Language to be processed with this copy of HeidelTime
	 * @param typeToProcess	Domain type to be processed
	 * @param outputType	Output type
	 * @param configPath	Path to the configuration file for HeidelTimeStandalone
	 */
	public void initialize(Language language, DocumentType typeToProcess, OutputType outputType, String configPath) {
		initialize(language, typeToProcess, outputType, configPath, POSTagger.TREETAGGER);
	}

	/**
	 * Method that initializes all vital prerequisites, including POS Tagger
	 * 
	 * @param language	Language to be processed with this copy of HeidelTime
	 * @param typeToProcess	Domain type to be processed
	 * @param outputType	Output type
	 * @param configPath	Path to the configuration file for HeidelTimeStandalone
	 * @param posTagger		POS Tagger to use for preprocessing
	 */
	public void initialize(Language language, DocumentType typeToProcess, OutputType outputType, String configPath, POSTagger posTagger) {
		initialize(language, typeToProcess, outputType, configPath, posTagger, false);
	}

	/**
	 * Method that initializes all vital prerequisites, including POS Tagger
	 * 
	 * @param language	Language to be processed with this copy of HeidelTime
	 * @param typeToProcess	Domain type to be processed
	 * @param outputType	Output type
	 * @param configPath	Path to the configuration file for HeidelTimeStandalone
	 * @param posTagger		POS Tagger to use for preprocessing
	 * @param doIntervalTagging	Whether or not to invoke the IntervalTagger
	 */
	public void initialize(Language language, DocumentType typeToProcess, OutputType outputType, String configPath, POSTagger posTagger, Boolean doIntervalTagging) {
		logger.log(Level.INFO, "HeidelTimeStandalone initialized with language " + this.language.getName());

		// set the POS tagger
		this.posTagger = posTagger;
		
		// set doIntervalTagging flag
		this.doIntervalTagging = doIntervalTagging;
		
		// read in configuration in case it's not yet initialized
		if(!Config.isInitialized()) {
			if(configPath == null)
				readConfigFile(CLISwitch.CONFIGFILE.getValue().toString());
			else
				readConfigFile(configPath);
		}
		
		try {
			heidelTime = new HeidelTime();
			heidelTime.initialize(new UimaContextImpl(language, typeToProcess));
			logger.log(Level.INFO, "HeidelTime initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "HeidelTime could not be initialized");
		}

		// Initialize JCas factory -------------
		logger.log(Level.FINE, "Initializing JCas factory...");
		try {
			TypeSystemDescription[] descriptions = new TypeSystemDescription[] {
					UIMAFramework
							.getXMLParser()
							.parseTypeSystemDescription(
									new XMLInputSource(
											this.getClass()
													.getClassLoader()
													.getResource(
															Config.get(Config.TYPESYSTEMHOME)))),
					UIMAFramework
							.getXMLParser()
							.parseTypeSystemDescription(
									new XMLInputSource(
											this.getClass()
													.getClassLoader()
													.getResource(
															Config.get(Config.TYPESYSTEMHOME_DKPRO)))) };
			jcasFactory = new JCasFactoryImpl(descriptions);
			logger.log(Level.INFO, "JCas factory initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "JCas factory could not be initialized");
		}
	}
	
	/**
	 * Runs the IntervalTagger on the JCAS object.
	 * @param jcas jcas object
	 */
	private void runIntervalTagger(JCas jcas) {
		logger.log(Level.FINEST, "Running Interval Tagger...");
		Integer beforeAnnotations = jcas.getAnnotationIndex().size();
		
		// Prepare the options for IntervalTagger's execution
		Properties settings = new Properties();
		settings.put(IntervalTagger.PARAM_LANGUAGE, language.getResourceFolder());
		settings.put(IntervalTagger.PARAM_INTERVALS, true);
		settings.put(IntervalTagger.PARAM_INTERVAL_CANDIDATES, false);
		
		// Instantiate and process with IntervalTagger
		IntervalTaggerWrapper iTagger = new IntervalTaggerWrapper();
		iTagger.initialize(settings);
		iTagger.process(jcas);
		
		// debug output
		Integer afterAnnotations = jcas.getAnnotationIndex().size();
		logger.log(Level.FINEST, "Annotation delta: " + (afterAnnotations - beforeAnnotations));
	}

	/**
	 * Provides jcas object with document creation time if
	 * <code>documentCreationTime</code> is not null.
	 * 
	 * @param jcas
	 * @param documentCreationTime
	 * @throws DocumentCreationTimeMissingException
	 *             If document creation time is missing when processing a
	 *             document of type {@link DocumentType#NEWS}.
	 */
	private void provideDocumentCreationTime(JCas jcas,
			Date documentCreationTime)
			throws DocumentCreationTimeMissingException {
		if (documentCreationTime == null) {
			// Document creation time is missing
			if (documentType == DocumentType.NEWS) {
				// But should be provided in case of news-document
				throw new DocumentCreationTimeMissingException();
			}
			if (documentType == DocumentType.COLLOQUIAL) {
				// But should be provided in case of colloquial-document
				throw new DocumentCreationTimeMissingException();
			}
		} else {
			// Document creation time provided
			// Translate it to expected string format
			SimpleDateFormat dateFormatter = new SimpleDateFormat(
					"yyyy.MM.dd'T'HH:mm");
			String formattedDCT = dateFormatter.format(documentCreationTime);

			// Create dct object for jcas
			Dct dct = new Dct(jcas);
			dct.setValue(formattedDCT);

			dct.addToIndexes();
		}
	}

	/**
	 * Establishes preconditions for jcas to be processed by HeidelTime
	 * 
	 * @param jcas
	 */
	private void establishHeidelTimePreconditions(JCas jcas) {
		// Token information & sentence structure
		establishPartOfSpeechInformation(jcas);
	}

	/**
	 * Establishes part of speech information for cas object.
	 * 
	 * @param jcas
	 */
	private void establishPartOfSpeechInformation(JCas jcas) {
		logger.log(Level.FINEST, "Establishing part of speech information...");

		PartOfSpeechTagger partOfSpeechTagger = null;
		Properties settings = new Properties();
		switch (language) {
			case ARABIC:
				partOfSpeechTagger = new StanfordPOSTaggerWrapper();
				settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_TOKENS, true);
				settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_SENTENCES, true);
				settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_POS, true);
				settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_MODEL_PATH, Config.get(Config.STANFORDPOSTAGGER_MODEL_PATH));
				settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_CONFIG_PATH, Config.get(Config.STANFORDPOSTAGGER_CONFIG_PATH));
				break;
			case VIETNAMESE:
				partOfSpeechTagger = new JVnTextProWrapper();
				settings.put(PartOfSpeechTagger.JVNTEXTPRO_ANNOTATE_TOKENS, true);
				settings.put(PartOfSpeechTagger.JVNTEXTPRO_ANNOTATE_SENTENCES, true);
				settings.put(PartOfSpeechTagger.JVNTEXTPRO_ANNOTATE_POS, true);
				settings.put(PartOfSpeechTagger.JVNTEXTPRO_WORD_MODEL_PATH, Config.get(Config.JVNTEXTPRO_WORD_MODEL_PATH));
				settings.put(PartOfSpeechTagger.JVNTEXTPRO_SENT_MODEL_PATH, Config.get(Config.JVNTEXTPRO_SENT_MODEL_PATH));
				settings.put(PartOfSpeechTagger.JVNTEXTPRO_POS_MODEL_PATH, Config.get(Config.JVNTEXTPRO_POS_MODEL_PATH));
				break;
			case CROATIAN:
				partOfSpeechTagger = new HunPosTaggerWrapper();
				settings.put(PartOfSpeechTagger.HUNPOS_LANGUAGE, language);
				settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_TOKENS, true);
				settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_POS, true);
				settings.put(PartOfSpeechTagger.HUNPOS_ANNOTATE_SENTENCES, true);
				settings.put(PartOfSpeechTagger.HUNPOS_MODEL_PATH, Config.get(Config.HUNPOS_MODEL_PATH));
				break;
			default:
				if(POSTagger.STANFORDPOSTAGGER.equals(posTagger)) {
					partOfSpeechTagger = new StanfordPOSTaggerWrapper();
					settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_TOKENS, true);
					settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_SENTENCES, true);
					settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_ANNOTATE_POS, true);
					settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_MODEL_PATH, Config.get(Config.STANFORDPOSTAGGER_MODEL_PATH));
					settings.put(PartOfSpeechTagger.STANFORDPOSTAGGER_CONFIG_PATH, Config.get(Config.STANFORDPOSTAGGER_CONFIG_PATH));
				} else if(POSTagger.TREETAGGER.equals(posTagger)) {
					partOfSpeechTagger = new TreeTaggerWrapper();
					settings.put(PartOfSpeechTagger.TREETAGGER_LANGUAGE, language);
					settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_TOKENS, true);
					settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_SENTENCES, true);
					settings.put(PartOfSpeechTagger.TREETAGGER_ANNOTATE_POS, true);
					settings.put(PartOfSpeechTagger.TREETAGGER_IMPROVE_GERMAN_SENTENCES, (language == Language.GERMAN));
					settings.put(PartOfSpeechTagger.TREETAGGER_CHINESE_TOKENIZER_PATH, Config.get(Config.CHINESE_TOKENIZER_PATH));
				} else {
					logger.log(Level.FINEST, "Sorry, but you can't use that tagger.");
				}
		}
		partOfSpeechTagger.initialize(settings);
		partOfSpeechTagger.process(jcas);

		logger.log(Level.FINEST, "Part of speech information established");
	}

	private ResultFormatter getFormatter() {
		if (outputType.toString().equals("xmi")){
			return new XMIResultFormatter();
		} else {
			return new TimeMLResultFormatter();
		}
	}

	/**
	 * Processes document with HeidelTime
	 *
	 * @param document
	 * @return Annotated document
	 * @throws DocumentCreationTimeMissingException
	 *             If document creation time is missing when processing a
	 *             document of type {@link DocumentType#NEWS}. Use
	 *             {@link #process(String, Date)} instead to provide document
	 *             creation time!
	 */
	public String process(String document)
			throws DocumentCreationTimeMissingException {
		return process(document, null, getFormatter());
	}

	/**
	 * Processes document with HeidelTime
	 *
	 * @param document
	 * @return Annotated document
	 * @throws DocumentCreationTimeMissingException
	 *             If document creation time is missing when processing a
	 *             document of type {@link DocumentType#NEWS}. Use
	 *             {@link #process(String, Date)} instead to provide document
	 *             creation time!
	 */
	public String process(String document, Date documentCreationTime)
			throws DocumentCreationTimeMissingException {
		return process(document, documentCreationTime, getFormatter());
	}

	/**
	 * Processes document with HeidelTime
	 * 
	 * @param document
	 * @return Annotated document
	 * @throws DocumentCreationTimeMissingException
	 *             If document creation time is missing when processing a
	 *             document of type {@link DocumentType#NEWS}. Use
	 *             {@link #process(String, Date)} instead to provide document
	 *             creation time!
	 */
	public String process(String document, ResultFormatter resultFormatter)
			throws DocumentCreationTimeMissingException {
		return process(document, null, resultFormatter);
	}

	/**
	 * Processes document with HeidelTime
	 * 
	 * @param document
	 * @param documentCreationTime
	 *            Date when document was created - especially important if
	 *            document is of type {@link DocumentType#NEWS}
	 * @return Annotated document
	 * @throws DocumentCreationTimeMissingException
	 *             If document creation time is missing when processing a
	 *             document of type {@link DocumentType#NEWS}
	 */
	public String process(String document, Date documentCreationTime, ResultFormatter resultFormatter)
			throws DocumentCreationTimeMissingException {
		logger.log(Level.INFO, "Processing started");

		// Generate jcas object ----------
		logger.log(Level.FINE, "Generate CAS object");
		JCas jcas = null;
		try {
			jcas = jcasFactory.createJCas();
			jcas.setDocumentText(document);
			logger.log(Level.FINE, "CAS object generated");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "Cas object could not be generated");
		}

		// Process jcas object -----------
		try {
			logger.log(Level.FINER, "Establishing preconditions...");
			provideDocumentCreationTime(jcas, documentCreationTime);
			establishHeidelTimePreconditions(jcas);
			logger.log(Level.FINER, "Preconditions established");

			heidelTime.process(jcas);

			logger.log(Level.INFO, "Processing finished");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "Processing aborted due to errors");
		}

		// process interval tagging ---
		if(doIntervalTagging)
			runIntervalTagger(jcas);
		
		// Process results ---------------
		logger.log(Level.FINE, "Formatting result...");
		// PrintAnnotations.printAnnotations(jcas.getCas(), System.out);
		String result = null;
		try {
			result = resultFormatter.format(jcas);
			logger.log(Level.INFO, "Result formatted");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "Result could not be formatted");
		}

		return result;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		String docPath = null;
		for(int i = 0; i < args.length; i++) { // iterate over cli parameter tokens
			if(args[i].startsWith("-")) { // assume we found a switch
				// get the relevant enum
				CLISwitch sw = CLISwitch.getEnumFromSwitch(args[i]);
				if(sw == null) { // unsupported CLI switch
					logger.log(Level.WARNING, "Unsupported switch: "+args[i]+". Quitting.");
					System.exit(-1);
				}
				
				if(sw.getHasFollowingValue()) { // handle values for switches
					if(args.length > i+1 && !args[i+1].startsWith("-")) { // we still have an array index after this one and it's not a switch
						sw.setValue(args[++i]);
					} else { // value is missing or malformed
						logger.log(Level.WARNING, "Invalid or missing parameter after "+args[i]+". Quitting.");
						System.exit(-1);
					}
				} else { // activate the value-less switches
					sw.setValue(null);
				}
			} else { // assume we found the document's path/name
				docPath = args[i];
			}
		}
		
		
		// display help dialog if HELP-switch is given
		if(CLISwitch.HELP.getIsActive()) {
			printHelp();
			System.exit(0);
		}
		
		// start off with the verbosity recognition -- lots of the other 
		// stuff can be skipped if this is set too high
		if(CLISwitch.VERBOSITY2.getIsActive()) {
			logger.setLevel(Level.ALL);
			logger.log(Level.INFO, "Verbosity: '-vv'; Logging level set to ALL.");
		} else if(CLISwitch.VERBOSITY.getIsActive()) {
			logger.setLevel(Level.INFO);
			logger.log(Level.INFO, "Verbosity: '-v'; Logging level set to INFO and above.");
		} else {
			logger.setLevel(Level.WARNING);
			logger.log(Level.INFO, "Verbosity -v/-vv NOT FOUND OR RECOGNIZED; Logging level set to WARNING and above.");
		}
		
		// Check input encoding
		String encodingType = null;
		if(CLISwitch.ENCODING.getIsActive()) {
			encodingType = CLISwitch.ENCODING.getValue().toString();
			logger.log(Level.INFO, "Encoding '-e': "+encodingType);
		} else {
			// Encoding type not found
			encodingType = CLISwitch.ENCODING.getValue().toString();
			logger.log(Level.INFO, "Encoding '-e': NOT FOUND OR RECOGNIZED; set to 'UTF-8'");
		}
		
		// Check output format
		OutputType outputType = null;
		if(CLISwitch.OUTPUTTYPE.getIsActive()) {
			outputType = OutputType.valueOf(CLISwitch.OUTPUTTYPE.getValue().toString().toUpperCase());
			logger.log(Level.INFO, "Output '-o': "+outputType.toString().toUpperCase());
		} else {
			// Output type not found
			outputType = (OutputType) CLISwitch.OUTPUTTYPE.getValue();
			logger.log(Level.INFO, "Output '-o': NOT FOUND OR RECOGNIZED; set to "+outputType.toString().toUpperCase());
		}
		
		// Check language
		Language language = null;
		if(CLISwitch.LANGUAGE.getIsActive()) {
			language = Language.getLanguageFromString((String) CLISwitch.LANGUAGE.getValue());
			
			if(language == Language.WILDCARD) {
				logger.log(Level.SEVERE, "Language '-l': "+CLISwitch.LANGUAGE.getValue()+" NOT RECOGNIZED; aborting.");
				printHelp();
				System.exit(-1);
			} else {
				logger.log(Level.INFO, "Language '-l': "+language.toString().toUpperCase());	
			}
		} else {
			// Language not found
			language = Language.getLanguageFromString((String) CLISwitch.LANGUAGE.getValue());
			logger.log(Level.INFO, "Language '-l': NOT FOUND; set to "+language.toString().toUpperCase());
		}

		// Check type
		DocumentType type = null;
		if(CLISwitch.DOCTYPE.getIsActive()) {
			try {
				if(CLISwitch.DOCTYPE.getValue().equals("narrative")) { // redirect "narrative" to "narratives"
					CLISwitch.DOCTYPE.setValue("narratives");
				}
				type = DocumentType.valueOf(CLISwitch.DOCTYPE.getValue().toString().toUpperCase());
			} catch(IllegalArgumentException e) {
				logger.log(Level.WARNING, "Type '-t': NOT RECOGNIZED. These are the available options: " + Arrays.asList(DocumentType.values()));
				System.exit(-1);
			}
			logger.log(Level.INFO, "Type '-t': "+type.toString().toUpperCase());
		} else {
			// Type not found
			type = (DocumentType) CLISwitch.DOCTYPE.getValue();
			logger.log(Level.INFO, "Type '-t': NOT FOUND; set to "+type.toString().toUpperCase());
		}

		// Check document creation time
		Date dct = null;
		if(CLISwitch.DCT.getIsActive()) {
			try {
				DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
				dct = formatter.parse(CLISwitch.DCT.getValue().toString());
				logger.log(Level.INFO, "Document Creation Time '-dct': "+dct.toString());
			} catch (Exception e) {
				// DCT was not parseable
				logger.log(Level.WARNING, "Document Creation Time '-dct': NOT RECOGNIZED. Quitting.");
				printHelp();
				System.exit(-1);
			}
		} else {
			if ((type == DocumentType.NEWS) || (type == DocumentType.COLLOQUIAL)) {
				// Dct needed
				dct = (Date) CLISwitch.DCT.getValue();
				logger.log(Level.INFO, "Document Creation Time '-dct': NOT FOUND; set to local date ("
						+ dct.toString() + ").");
			} else {
				logger.log(Level.INFO, "Document Creation Time '-dct': NOT FOUND; skipping.");
			}
		}
		
		// Handle locale switch
		String locale = (String) CLISwitch.LOCALE.getValue();
		Locale myLocale = null;
		if(CLISwitch.LOCALE.getIsActive()) {
			// check if the requested locale is available
			for(Locale l : Locale.getAvailableLocales()) {
				if(l.toString().toLowerCase().equals(locale.toLowerCase()))
					myLocale = l;
			}
			
			try {
				Locale.setDefault(myLocale); // try to set the locale
				logger.log(Level.INFO, "Locale '-locale': "+myLocale.toString());
			} catch(Exception e) { // if the above fails, spit out error message and available locales
				logger.log(Level.WARNING, "Supplied locale parameter couldn't be resolved to a working locale. Try one of these:");
				logger.log(Level.WARNING, Arrays.asList(Locale.getAvailableLocales()).toString()); // list available locales
				printHelp();
				System.exit(-1);
			}
		} else {
			// no -locale parameter supplied: just show default locale
			logger.log(Level.INFO, "Locale '-locale': NOT FOUND, set to environment locale: "+Locale.getDefault().toString());
		}
		
		// Read configuration from file
		String configPath = CLISwitch.CONFIGFILE.getValue().toString();
		try {
			logger.log(Level.INFO, "Configuration path '-c': "+configPath);

			readConfigFile(configPath);

			logger.log(Level.FINE, "Config initialized");
		} catch (Exception e) {
			e.printStackTrace();
			logger.log(Level.WARNING, "Config could not be initialized! Please supply the -c switch or "
					+ "put a config.props into this directory.");
			printHelp();
			System.exit(-1);
		}

		// Set the preprocessing POS tagger
		POSTagger posTagger = null;
		if(CLISwitch.POSTAGGER.getIsActive()) {
			try {
				posTagger = POSTagger.valueOf(CLISwitch.POSTAGGER.getValue().toString().toUpperCase());
			} catch(IllegalArgumentException e) {
				logger.log(Level.WARNING, "Given POS Tagger doesn't exist. Please specify a valid one as listed in the help.");
				printHelp();
				System.exit(-1);
			}
			logger.log(Level.INFO, "POS Tagger '-pos': "+posTagger.toString().toUpperCase());
		} else {
			// Type not found
			posTagger = (POSTagger) CLISwitch.POSTAGGER.getValue();
			logger.log(Level.INFO, "POS Tagger '-pos': NOT FOUND OR RECOGNIZED; set to "+posTagger.toString().toUpperCase());
		}

		// Set whether or not to use the Interval Tagger
		Boolean doIntervalTagging = false;
		if(CLISwitch.INTERVALS.getIsActive()) {
			doIntervalTagging = CLISwitch.INTERVALS.getIsActive();
			logger.log(Level.INFO, "Interval Tagger '-it': " + doIntervalTagging.toString());
		} else {
			logger.log(Level.INFO, "Interval Tagger '-it': NOT FOUND OR RECOGNIZED; set to " + doIntervalTagging.toString());
		}
		
		// make sure we have a document path
		if (docPath == null) {
			logger.log(Level.WARNING, "No input file given; aborting.");
			printHelp();
			System.exit(-1);
		}
		
		

		// Run HeidelTime
		RandomAccessFile aFile = null;
		MappedByteBuffer buffer = null;
		FileChannel inChannel = null;
		PrintWriter pwOut = null;
		try {
			logger.log(Level.INFO, "Reading document using charset: " + encodingType);
			
			aFile = new RandomAccessFile(docPath, "r");
			inChannel = aFile.getChannel();
			buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, 0, inChannel.size());
			buffer.load();
			byte[] inArr = new byte[(int) inChannel.size()];
			
			for(int i = 0; i < buffer.limit(); i++) {
				inArr[i] = buffer.get();
			}
			
			// double-newstring should not be necessary, but without this, it's not running on Windows (?)
			String input = new String(new String(inArr, encodingType).getBytes("UTF-8"), "UTF-8");
			
			HeidelTimeStandalone standalone = new HeidelTimeStandalone(language, type, outputType, null, posTagger, doIntervalTagging);
			String out = standalone.process(input, dct);
			
			// Print output always as UTF-8
			pwOut = new PrintWriter(new OutputStreamWriter(System.out, "UTF-8"));
			pwOut.println(out);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if(pwOut != null) {
				pwOut.close();
			}
			if(buffer != null) {
				buffer.clear();
			}
			if(inChannel != null) {
				try {
					inChannel.close();
				} catch (IOException e) { }
			}
			if(aFile != null) {
				try {
					aFile.close();
				} catch (IOException e) { }
			}
		}
	}
	
	public static void readConfigFile(String configPath) {
		InputStream configStream = null;
		try {
			logger.log(Level.INFO, "trying to read in file "+configPath);
			configStream = new FileInputStream(configPath);
			
			Properties props = new Properties();
			props.load(configStream);

			Config.setProps(props);
			
			configStream.close();
		} catch (FileNotFoundException e) {
			logger.log(Level.WARNING, "couldn't open configuration file \""+configPath+"\". quitting.");
			System.exit(-1);
		} catch (IOException e) {
			logger.log(Level.WARNING, "couldn't close config file handle");
			e.printStackTrace();
		}
	}
	
	private static void printHelp() {
		String path = HeidelTimeStandalone.class.getProtectionDomain().getCodeSource().getLocation().getFile();
		String filename = path.substring(path.lastIndexOf(System.getProperty("file.separator")) + 1);
		
		System.out.println("Usage:");
		System.out.println("  java -jar " 
				+ filename 
				+ " <input-document> [-param1 <value1> ...]");
		System.out.println();
		System.out.println("Parameters and expected values:");
		for(CLISwitch c : CLISwitch.values()) {
			System.out.println("  " 
					+ c.getSwitchString() 
					+ "\t"
					+ ((c.getSwitchString().length() > 4)? "" : "\t")
					+ c.getName()
					);

			if(c == CLISwitch.LANGUAGE) {
				System.out.print("\t\t" + "Available languages: [ ");
				for(Language l : Language.values())
					if(l != Language.WILDCARD)
						System.out.print(l.getName().toLowerCase()+" ");
				System.out.println("]");
			}
			
			if(c == CLISwitch.POSTAGGER) {
				System.out.print("\t\t" + "Available taggers: [ ");
				for(POSTagger p : POSTagger.values())
					System.out.print(p.toString().toLowerCase()+" ");
				System.out.println("]");
			}
			
			if(c == CLISwitch.DOCTYPE) {
				System.out.print("\t\t" + "Available types: [ ");
				for(DocumentType t : DocumentType.values())
					System.out.print(t.toString().toLowerCase()+" ");
				System.out.println("]");
			}
		}
		
		System.out.println();
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public void setDocumentType(DocumentType documentType) {
		this.documentType = documentType;
	}

	public Language getLanguage() {
		return language;
	}

	public void setLanguage(Language language) {
		this.language = language;
	}

	public OutputType getOutputType() {
		return outputType;
	}

	public void setOutputType(OutputType outputType) {
		this.outputType = outputType;
	}

	public final POSTagger getPosTagger() {
		return posTagger;
	}

	public final void setPosTagger(POSTagger posTagger) {
		this.posTagger = posTagger;
	}

}
