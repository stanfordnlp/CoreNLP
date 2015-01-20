/*
 * HeidelTime.java
 * 
 * Copyright (c) 2011, Database Research Group, Institute of Computer Science, Heidelberg University. 
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the GNU General Public License.
 * 
 * author: Jannik Strötgen
 * email:  stroetgen@uni-hd.de
 * 
 * HeidelTime is a multilingual, cross-domain temporal tagger.
 * For details, see http://dbs.ifi.uni-heidelberg.de/heideltime
 */

package de.unihd.dbs.uima.annotator.heideltime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.unihd.dbs.uima.annotator.heideltime.ProcessorManager.Priority;
import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.heideltime.resources.NormalizationManager;
import de.unihd.dbs.uima.annotator.heideltime.resources.RePatternManager;
import de.unihd.dbs.uima.annotator.heideltime.resources.RegexHashMap;
import de.unihd.dbs.uima.annotator.heideltime.resources.RuleManager;
import de.unihd.dbs.uima.annotator.heideltime.utilities.DateCalculator;
import de.unihd.dbs.uima.annotator.heideltime.utilities.ContextAnalyzer;
import de.unihd.dbs.uima.annotator.heideltime.utilities.LocaleException;
import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.annotator.heideltime.utilities.Toolbox;
import de.unihd.dbs.uima.types.heideltime.Dct;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Token;


/**
 * HeidelTime finds temporal expressions and normalizes them according to the TIMEX3 
 * TimeML annotation standard.
 * 
 * @author jannik stroetgen
 * 
 */
public class HeidelTime extends JCasAnnotator_ImplBase {

	// TOOL NAME (may be used as componentId)
	private Class<?> component = this.getClass();
	
	// PROCESSOR MANAGER
	private ProcessorManager procMan = new ProcessorManager();

	// COUNTER (how many timexes added to CAS? (finally)
	public int timex_counter        = 0;
	public int timex_counter_global = 0;
	
	// FLAG (for historic expressions referring to BC)
	public Boolean flagHistoricDates = false;
	
	// COUNTER FOR TIMEX IDS
	private int timexID = 0;
	
	// INPUT PARAMETER HANDLING WITH UIMA
	private String PARAM_LANGUAGE         = "Language";
	// supported languages (2012-05-19): english, german, dutch, englishcoll, englishsci
	private String PARAM_TYPE_TO_PROCESS  = "Type";
	// chosen locale parameter name
	private String PARAM_LOCALE			   = "locale";
	// supported types (2012-05-19): news (english, german, dutch), narrative (english, german, dutch), colloquial
	private Language language       = Language.ENGLISH;
	private String typeToProcess  = "news";
	
	// INPUT PARAMETER HANDLING WITH UIMA (which types shall be extracted)
	private String PARAM_DATE      = "Date";
	private String PARAM_TIME      = "Time";
	private String PARAM_DURATION  = "Duration";
	private String PARAM_SET       = "Set";
	private String PARAM_DEBUG	   = "Debugging";
	private String PARAM_GROUP     = "ConvertDurations";
	private Boolean find_dates     = true;
	private Boolean find_times     = true;
	private Boolean find_durations = true;
	private Boolean find_sets      = true;
	private Boolean group_gran     = true;
	// FOR DEBUGGING PURPOSES (IF FALSE)
	private Boolean deleteOverlapped = true;


	/**
	 * @see AnalysisComponent#initialize(UimaContext)
	 */
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);

		/////////////////////////////////
		// DEBUGGING PARAMETER SETTING //
		/////////////////////////////////
		this.deleteOverlapped = true;
		Boolean doDebug = (Boolean) aContext.getConfigParameterValue(PARAM_DEBUG);
		Logger.setPrintDetails(doDebug == null ? false : doDebug);
		
		/////////////////////////////////
		// HANDLE LOCALE    		   //
		/////////////////////////////////
		String requestedLocale = (String) aContext.getConfigParameterValue(PARAM_LOCALE);
		if(requestedLocale == null || requestedLocale.length() == 0) { // if the PARAM_LOCALE setting was left empty, 
			Locale.setDefault(Locale.UK); // use a default, the ISO8601-adhering UK locale (equivalent to "en_GB")
		} else { // otherwise, check if the desired locale exists in the JVM's available locale repertoire
			try {
				Locale locale = DateCalculator.getLocaleFromString(requestedLocale);
				Locale.setDefault(locale); // sets it for the entire JVM session
			} catch (LocaleException e) {
				Logger.printError("Supplied locale parameter couldn't be resolved to a working locale. Try one of these:");
				String localesString = new String();
				for(Locale l : Locale.getAvailableLocales()) { // list all available locales
					localesString += l.toString()+" ";
				}
				Logger.printError(localesString);
				System.exit(-1);
			}
		}
		
		//////////////////////////////////
		// GET CONFIGURATION PARAMETERS //
		//////////////////////////////////
		language = Language.getLanguageFromString((String) aContext.getConfigParameterValue(PARAM_LANGUAGE));
		
		typeToProcess  = (String)  aContext.getConfigParameterValue(PARAM_TYPE_TO_PROCESS);
		find_dates     = (Boolean) aContext.getConfigParameterValue(PARAM_DATE);
		find_times     = (Boolean) aContext.getConfigParameterValue(PARAM_TIME);
		find_durations = (Boolean) aContext.getConfigParameterValue(PARAM_DURATION);
		find_sets      = (Boolean) aContext.getConfigParameterValue(PARAM_SET);
		group_gran	   = (Boolean) aContext.getConfigParameterValue(PARAM_GROUP);
		////////////////////////////////////////////////////////////
		// READ NORMALIZATION RESOURCES FROM FILES AND STORE THEM //
		////////////////////////////////////////////////////////////
		NormalizationManager.getInstance(language);
		
		//////////////////////////////////////////////////////
		// READ PATTERN RESOURCES FROM FILES AND STORE THEM //
		//////////////////////////////////////////////////////
		RePatternManager.getInstance(language);
	
		///////////////////////////////////////////////////
		// READ RULE RESOURCES FROM FILES AND STORE THEM //
		///////////////////////////////////////////////////
		RuleManager.getInstance(language);
		
		/////////////////////////////////////////////////////////////////////////////////
		// SUBPROCESSOR CONFIGURATION. REGISTER YOUR OWN PROCESSORS HERE FOR EXECUTION //
		/////////////////////////////////////////////////////////////////////////////////
		procMan.registerProcessor("de.unihd.dbs.uima.annotator.heideltime.processors.HolidayProcessor");
		procMan.registerProcessor("de.unihd.dbs.uima.annotator.heideltime.processors.DecadeProcessor");
		procMan.initializeAllProcessors(aContext);
		
		/////////////////////////////
		// PRINT WHAT WILL BE DONE //
		/////////////////////////////
		if (find_dates) Logger.printDetail("Getting Dates...");	
		if (find_times) Logger.printDetail("Getting Times...");	
		if (find_durations) Logger.printDetail("Getting Durations...");	
		if (find_sets) Logger.printDetail("Getting Sets...");
	}

	
	/**
	 * @see JCasAnnotator_ImplBase#process(JCas)
	 */
	public void process(JCas jcas) {
		// run preprocessing processors
		procMan.executeProcessors(jcas, Priority.PREPROCESSING);
		
		RuleManager rulem = RuleManager.getInstance(language);
		
		timexID = 1; // reset counter once per document processing

		timex_counter = 0;

		flagHistoricDates = false;
		
		////////////////////////////////////////////
		// CHECK SENTENCE BY SENTENCE FOR TIMEXES //
		////////////////////////////////////////////
		FSIterator sentIter = jcas.getAnnotationIndex(Sentence.type).iterator();
		/* 
		 * check if the pipeline has annotated any sentences. if not, heideltime can't do any work,
		 * will return from process() with a warning message.
		 */
		if(!sentIter.hasNext()) {
			Logger.printError(component, "HeidelTime has not found any sentence tokens in this document. " +
					"HeidelTime needs sentence tokens tagged by a preprocessing UIMA analysis engine to " +
					"do its work. Please check your UIMA workflow and add an analysis engine that creates " +
					"these sentence tokens.");
		}
		
		while (sentIter.hasNext()) {
			Sentence s = (Sentence) sentIter.next();
			
			Boolean debugIteration = false;
			Boolean oldDebugState = Logger.getPrintDetails();
			do {
				try {
					if (find_dates) {
						findTimexes("DATE", rulem.getHmDatePattern(), rulem.getHmDateOffset(), rulem.getHmDateNormalization(), rulem.getHmDateQuant(), s, jcas);
					}
					if (find_times) {
						findTimexes("TIME", rulem.getHmTimePattern(), rulem.getHmTimeOffset(), rulem.getHmTimeNormalization(), rulem.getHmTimeQuant(), s, jcas);
					}
					
					/*
					 *  check for historic dates/times starting with BC
					 *  to check if post-processing step is required
					 */
					if (typeToProcess.equals("narrative") || typeToProcess.equals("narratives")){
						FSIterator iterDates = jcas.getAnnotationIndex(Timex3.type).iterator();
						while (iterDates.hasNext()){
							Timex3 t = (Timex3) iterDates.next();
							if (t.getTimexValue().startsWith("BC")){
								flagHistoricDates = true;
								break;
							}
						}
					}
					
					if (find_sets) {
						findTimexes("SET", rulem.getHmSetPattern(), rulem.getHmSetOffset(), rulem.getHmSetNormalization(), rulem.getHmSetQuant(), s, jcas);
					}
					if (find_durations) {
						findTimexes("DURATION", rulem.getHmDurationPattern(), rulem.getHmDurationOffset(), rulem.getHmDurationNormalization(), rulem.getHmDurationQuant(), s, jcas);
					}
				} catch(NullPointerException npe) {
					if(!debugIteration) {
						debugIteration = true;
						Logger.setPrintDetails(true);
						
						Logger.printError(component, "HeidelTime's execution has been interrupted by an exception that " +
								"is likely rooted in faulty normalization resource files. Please consider opening an issue " +
								"report containing the following information at our Google Code project issue tracker: " +
								"https://code.google.com/p/heideltime. Thanks!");
						npe.printStackTrace();
						Logger.printError(component, "Sentence [" + s.getBegin() + "-" + s.getEnd() + "]: " + s.getCoveredText());
						Logger.printError(component, "Language: " + language);
						Logger.printError(component, "Re-running this sentence with DEBUGGING enabled...");
					} else {
						debugIteration = false;
						Logger.setPrintDetails(oldDebugState);
						
						Logger.printError(component, "Execution will now resume.");
					}
				}
			} while(debugIteration);
		}

		/*
		 * kick out some overlapping expressions
		 */
		if (deleteOverlapped == true)
			deleteOverlappedTimexesPreprocessing(jcas);

		/*
		 * specify ambiguous values, e.g.: specific year for date values of
		 * format UNDEF-year-01-01; specific month for values of format UNDEF-last-month
		 */
		specifyAmbiguousValues(jcas);
		
		// disambiguate historic dates
		// check dates without explicit hints to AD or BC if they might refer to BC dates
		if (flagHistoricDates)
			try {
				disambiguateHistoricDates(jcas);
			} catch(Exception e) {
				Logger.printError("Something went wrong disambiguating historic dates.");
				e.printStackTrace();
			}

		/*
		 * kick out the rest of the overlapping expressions
		 */
		if (deleteOverlapped == true)
			deleteOverlappedTimexesPostprocessing(jcas);
		
		// run arbitrary processors
		procMan.executeProcessors(jcas, Priority.ARBITRARY);
		
		// remove invalid timexes
		removeInvalids(jcas);
		
		// run postprocessing processors
		procMan.executeProcessors(jcas, Priority.POSTPROCESSING);

		timex_counter_global = timex_counter_global + timex_counter;
		Logger.printDetail(component, "Number of Timexes added to CAS: "+timex_counter + "(global: "+timex_counter_global+")");
	}

	
	/**
	 * Add timex annotation to CAS object.
	 * 
	 * @param timexType
	 * @param begin
	 * @param end
	 * @param timexValue
	 * @param timexId
	 * @param foundByRule
	 * @param jcas
	 */
	public void addTimexAnnotation(String timexType, int begin, int end, Sentence sentence, String timexValue, String timexQuant,
			String timexFreq, String timexMod, String emptyValue, String timexId, String foundByRule, JCas jcas) {
		
		Timex3 annotation = new Timex3(jcas);
		annotation.setBegin(begin);
		annotation.setEnd(end);

		annotation.setFilename(sentence.getFilename());
		annotation.setSentId(sentence.getSentenceId());
		
		annotation.setEmptyValue(emptyValue);

		FSIterator iterToken = jcas.getAnnotationIndex(Token.type).subiterator(sentence);
		String allTokIds = "";
		while (iterToken.hasNext()) {
			Token tok = (Token) iterToken.next();
			if (tok.getBegin() <= begin && tok.getEnd() > begin) {
				annotation.setFirstTokId(tok.getTokenId());
				allTokIds = "BEGIN<-->" + tok.getTokenId();
			}
			if ((tok.getBegin() > begin) && (tok.getEnd() <= end)) {
				allTokIds = allTokIds + "<-->" + tok.getTokenId();
			}
		}
		annotation.setAllTokIds(allTokIds);
		annotation.setTimexType(timexType);
		annotation.setTimexValue(timexValue);
		annotation.setTimexId(timexId);
		annotation.setFoundByRule(foundByRule);
		if ((timexType.equals("DATE")) || (timexType.equals("TIME"))) {
			if ((timexValue.startsWith("X")) || (timexValue.startsWith("UNDEF"))) {
				annotation.setFoundByRule(foundByRule+"-relative");
			} else {
				annotation.setFoundByRule(foundByRule+"-explicit");
			}
		}
		if (!(timexQuant == null)) {
			annotation.setTimexQuant(timexQuant);
		}
		if (!(timexFreq == null)) {
			annotation.setTimexFreq(timexFreq);
		}
		if (!(timexMod == null)) {
			annotation.setTimexMod(timexMod);
		}
		annotation.addToIndexes();
		this.timex_counter++;
		
		Logger.printDetail(annotation.getTimexId()+"EXTRACTION PHASE:   "+" found by:"+annotation.getFoundByRule()+" text:"+annotation.getCoveredText());
		Logger.printDetail(annotation.getTimexId()+"NORMALIZATION PHASE:"+" found by:"+annotation.getFoundByRule()+" text:"+annotation.getCoveredText()+" value:"+annotation.getTimexValue());
		
	}

	
	/**
	 * Postprocessing: Check dates starting with "0" which were extracted without 
	 * explicit "AD" hints if it is likely that they refer to the respective date BC
	 * 
	 * @param jcas
	 */
	public void disambiguateHistoricDates(JCas jcas){
		
		// build up a list with all found TIMEX expressions
		List<Timex3> linearDates = new ArrayList<Timex3>();
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();

		// Create List of all Timexes of types "date" and "time"
		while (iterTimex.hasNext()) {
			Timex3 timex = (Timex3) iterTimex.next();
			if (timex.getTimexType().equals("DATE") || timex.getTimexType().equals("TIME")) {
				linearDates.add(timex);
			}
		}
		
        //////////////////////////////////////////////
        // go through list of Date and Time timexes //
        //////////////////////////////////////////////
		for (int i = 1; i < linearDates.size(); i++) {
			Timex3 t_i = (Timex3) linearDates.get(i);
			String value_i = t_i.getTimexValue();
			String newValue = value_i;
			Boolean change = false;
			if (!(t_i.getFoundByRule().contains("-BCADhint"))){
				if (value_i.startsWith("0")){
					Integer offset = 1, counter = 1;
					do {
						if ((i == 1 || (i > 1 && !change)) && linearDates.get(i-offset).getTimexValue().startsWith("BC")){
							if (value_i.length()>1){
								if ((linearDates.get(i-offset).getTimexValue().startsWith("BC"+value_i.substring(0,2))) ||
										(linearDates.get(i-offset).getTimexValue().startsWith("BC"+String.format("%02d",(Integer.parseInt(value_i.substring(0,2))+1))))){
									if (((value_i.startsWith("00")) && (linearDates.get(i-offset).getTimexValue().startsWith("BC00"))) ||
											((value_i.startsWith("01")) && (linearDates.get(i-offset).getTimexValue().startsWith("BC01")))){
										if ((value_i.length()>2) && (linearDates.get(i-offset).getTimexValue().length()>4)){
											if (Integer.parseInt(value_i.substring(0,3)) <= Integer.parseInt(linearDates.get(i-offset).getTimexValue().substring(2,5))){
												newValue = "BC" + value_i;
												change = true;
												Logger.printDetail("DisambiguateHistoricDates: "+value_i+" to "+newValue+". Expression "+t_i.getCoveredText()+" due to "+linearDates.get(i-offset).getCoveredText());
											}
										}
									}
									else{
										newValue = "BC" + value_i;
										change = true;
										Logger.printDetail("DisambiguateHistoricDates: "+value_i+" to "+newValue+". Expression "+t_i.getCoveredText()+" due to "+linearDates.get(i-offset).getCoveredText());
									}
								}
							}               
						}
						
						if ((linearDates.get(i-offset).getTimexType().equals("TIME") || linearDates.get(i-offset).getTimexType().equals("DATE")) &&
								(linearDates.get(i-offset).getTimexValue().matches("^\\d.*"))) {
							counter++;
						}
					} while (counter < 5 && ++offset < i);
				}
			}
			if (!(newValue.equals(value_i))){
				t_i.removeFromIndexes();
				Logger.printDetail("DisambiguateHistoricDates: value changed to BC");

				t_i.setTimexValue(newValue);
				t_i.addToIndexes();
				linearDates.set(i, t_i);
			}
		}	
	}
	
	/**
	 * Postprocessing: Remove invalid timex expressions. These are already
	 * marked as invalid: timexValue().equals("REMOVE")
	 * 
	 * @param jcas
	 */
	public void removeInvalids(JCas jcas) {

		/*
		 * Iterate over timexes and add invalids to HashSet 
		 * (invalids cannot be removed directly since iterator is used)
		 */
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
		HashSet<Timex3> hsTimexToRemove = new HashSet<Timex3>();
		while (iterTimex.hasNext()) {
			Timex3 timex = (Timex3) iterTimex.next();
			if (timex.getTimexValue().equals("REMOVE")) {
				hsTimexToRemove.add(timex);
			}
		}

		// remove invalids, finally
		for (Timex3 timex3 : hsTimexToRemove) {
			timex3.removeFromIndexes();
			this.timex_counter--;
			Logger.printDetail(timex3.getTimexId()+" REMOVING PHASE: "+"found by:"+timex3.getFoundByRule()+" text:"+timex3.getCoveredText()+" value:"+timex3.getTimexValue());
		}
	}

	@SuppressWarnings("unused")
	public String specifyAmbiguousValuesString(String ambigString, Timex3 t_i, Integer i, List<Timex3> linearDates, JCas jcas) {
		NormalizationManager norm = NormalizationManager.getInstance(language);

		// //////////////////////////////////////
		// IS THERE A DOCUMENT CREATION TIME? //
		// //////////////////////////////////////
		boolean dctAvailable = false;

		// ////////////////////////////
		// DOCUMENT TYPE TO PROCESS //
		// //////////////////////////
		boolean documentTypeNews = false;
		boolean documentTypeNarrative = false;
		boolean documentTypeColloquial = false;
		boolean documentTypeScientific = false;
		if (typeToProcess.equals("news")) {
			documentTypeNews = true;
		}
		if (typeToProcess.equals("narrative")
				|| typeToProcess.equals("narratives")) {
			documentTypeNarrative = true;
		}
		if (typeToProcess.equals("colloquial")) {
			documentTypeColloquial = true;
		}
		if (typeToProcess.equals("scientific")) {
			documentTypeScientific = true;
		}

		// get the dct information
		String dctValue = "";
		int dctCentury = 0;
		int dctYear = 0;
		int dctDecade = 0;
		int dctMonth = 0;
		int dctDay = 0;
		String dctSeason = "";
		String dctQuarter = "";
		String dctHalf = "";
		int dctWeekday = 0;
		int dctWeek = 0;

		// ////////////////////////////////////////////
		// INFORMATION ABOUT DOCUMENT CREATION TIME //
		// ////////////////////////////////////////////
		FSIterator dctIter = jcas.getAnnotationIndex(Dct.type).iterator();
		if (dctIter.hasNext()) {
			dctAvailable = true;
			Dct dct = (Dct) dctIter.next();
			dctValue = dct.getValue();
			// year, month, day as mentioned in the DCT
			if (dctValue.matches("\\d\\d\\d\\d\\d\\d\\d\\d")) {
				dctCentury = Integer.parseInt(dctValue.substring(0, 2));
				dctYear = Integer.parseInt(dctValue.substring(0, 4));
				dctDecade = Integer.parseInt(dctValue.substring(2, 3));
				dctMonth = Integer.parseInt(dctValue.substring(4, 6));
				dctDay = Integer.parseInt(dctValue.substring(6, 8));

				Logger.printDetail("dctCentury:" + dctCentury);
				Logger.printDetail("dctYear:" + dctYear);
				Logger.printDetail("dctDecade:" + dctDecade);
				Logger.printDetail("dctMonth:" + dctMonth);
				Logger.printDetail("dctDay:" + dctDay);
			} else {
				dctCentury = Integer.parseInt(dctValue.substring(0, 2));
				dctYear = Integer.parseInt(dctValue.substring(0, 4));
				dctDecade = Integer.parseInt(dctValue.substring(2, 3));
				dctMonth = Integer.parseInt(dctValue.substring(5, 7));
				dctDay = Integer.parseInt(dctValue.substring(8, 10));

				Logger.printDetail("dctCentury:" + dctCentury);
				Logger.printDetail("dctYear:" + dctYear);
				Logger.printDetail("dctDecade:" + dctDecade);
				Logger.printDetail("dctMonth:" + dctMonth);
				Logger.printDetail("dctDay:" + dctDay);
			}
			dctQuarter = "Q"
					+ norm.getFromNormMonthInQuarter(norm
							.getFromNormNumber(dctMonth + ""));
			dctHalf = "H1";
			if (dctMonth > 6) {
				dctHalf = "H2";
			}

			// season, week, weekday, have to be calculated
			dctSeason = norm.getFromNormMonthInSeason(norm
					.getFromNormNumber(dctMonth + "") + "");
			dctWeekday = DateCalculator.getWeekdayOfDate(dctYear + "-"
					+ norm.getFromNormNumber(dctMonth + "") + "-"
					+ norm.getFromNormNumber(dctDay + ""));
			dctWeek = DateCalculator.getWeekOfDate(dctYear + "-"
					+ norm.getFromNormNumber(dctMonth + "") + "-"
					+ norm.getFromNormNumber(dctDay + ""));

			Logger.printDetail("dctQuarter:" + dctQuarter);
			Logger.printDetail("dctSeason:" + dctSeason);
			Logger.printDetail("dctWeekday:" + dctWeekday);
			Logger.printDetail("dctWeek:" + dctWeek);
		} else {
			Logger.printDetail("No DCT available...");
		}
		
		// check if value_i has month, day, season, week (otherwise no UNDEF-year is possible)
		Boolean viHasMonth   = false;
		Boolean viHasDay     = false;
		Boolean viHasSeason  = false;
		Boolean viHasWeek    = false;
		Boolean viHasQuarter = false;
		Boolean viHasHalf    = false;
		int viThisMonth      = 0;
		int viThisDay        = 0;
		String viThisSeason  = "";
		String viThisQuarter = "";
		String viThisHalf    = "";
		String[] valueParts  = ambigString.split("-");
		// check if UNDEF-year or UNDEF-century
		if ((ambigString.startsWith("UNDEF-year")) || (ambigString.startsWith("UNDEF-century"))) {
			if (valueParts.length > 2) {
				// get vi month
				if (valueParts[2].matches("\\d\\d")) {
					viHasMonth  = true;
					viThisMonth = Integer.parseInt(valueParts[2]);
				}
				// get vi season
				else if ((valueParts[2].equals("SP")) || (valueParts[2].equals("SU")) || (valueParts[2].equals("FA")) || (valueParts[2].equals("WI"))) {
					viHasSeason  = true;
					viThisSeason = valueParts[2]; 
				}
				// get v1 quarter
				else if ((valueParts[2].equals("Q1")) || (valueParts[2].equals("Q2")) || (valueParts[2].equals("Q3")) || (valueParts[2].equals("Q4"))) {
					viHasQuarter  = true;
					viThisQuarter = valueParts[2]; 
				}
				// get v1 half
				else if ((valueParts[2].equals("H1")) || (valueParts[2].equals("H2"))) {
					viHasHalf  = true;
					viThisHalf = valueParts[2];
				}
				// get vi day
				if ((valueParts.length > 3) && (valueParts[3].matches("\\d\\d"))) {
					viHasDay = true;
					viThisDay = Integer.parseInt(valueParts[3]);
				}
			}
		}
		else {
			if (valueParts.length > 1) {
				// get vi month
				if (valueParts[1].matches("\\d\\d")) {
					viHasMonth  = true;
					viThisMonth = Integer.parseInt(valueParts[1]);
				}
				// get vi season
				else if ((valueParts[1].equals("SP")) || (valueParts[1].equals("SU")) || (valueParts[1].equals("FA")) || (valueParts[1].equals("WI"))) {
					viHasSeason  = true;
					viThisSeason = valueParts[1]; 
				}
				// get v1 quarter
				else if ((valueParts[1].equals("Q1")) || (valueParts[1].equals("Q2")) || (valueParts[1].equals("Q3")) || (valueParts[1].equals("Q4"))) {
					viHasQuarter  = true;
					viThisQuarter = valueParts[1]; 
				}
				// get v1 half
				else if ((valueParts[1].equals("H1")) || (valueParts[1].equals("H2"))) {
					viHasHalf  = true;
					viThisHalf = valueParts[1];
				}
				// get vi day
				if ((valueParts.length > 2) && (valueParts[2].matches("\\d\\d"))) {
					viHasDay = true;
					viThisDay = Integer.parseInt(valueParts[2]);
				}
			}
		}
		// get the last tense (depending on the part of speech tags used in front or behind the expression)
		String last_used_tense = ContextAnalyzer.getLastTense(t_i, jcas, language);

		//////////////////////////
		// DISAMBIGUATION PHASE //
		//////////////////////////
		
		////////////////////////////////////////////////////
		// IF YEAR IS COMPLETELY UNSPECIFIED (UNDEF-year) // 
		////////////////////////////////////////////////////
		String valueNew = ambigString;
		if (ambigString.startsWith("UNDEF-year")) {
			String newYearValue = dctYear+"";
			// vi has month (ignore day)
			if ((viHasMonth == true) && (viHasSeason == false)) {
				// WITH DOCUMENT CREATION TIME
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					//  Tense is FUTURE
					if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
						// if dct-month is larger than vi-month, than add 1 to dct-year
						if (dctMonth > viThisMonth) {
							int intNewYear = dctYear + 1;
							newYearValue = intNewYear + "";
						}
					}
					// Tense is PAST
					if ((last_used_tense.equals("PAST"))) {
						// if dct-month is smaller than vi month, than substrate 1 from dct-year						
						if (dctMonth < viThisMonth) {
							int intNewYear = dctYear - 1;
							newYearValue = intNewYear + "";
						}
					}
				}
				// WITHOUT DOCUMENT CREATION TIME
				else {
					newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year", language);
				}
			}
			// vi has quaurter
			if (viHasQuarter == true) {
				// WITH DOCUMENT CREATION TIME
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					//  Tense is FUTURE
					if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
						if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))) {
							int intNewYear = dctYear + 1;
							newYearValue = intNewYear + "";
						}
					}
					// Tense is PAST
					if ((last_used_tense.equals("PAST"))) {
						if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))) {
							int intNewYear = dctYear - 1;
							newYearValue = intNewYear + "";
						}
					}
					// IF NO TENSE IS FOUND
					if (last_used_tense.equals("")){
						if (documentTypeColloquial){
							// IN COLLOQUIAL: future temporal expressions
							if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))){
								int intNewYear = dctYear + 1;
								newYearValue = intNewYear + "";
							}
						}
						else{
							// IN NEWS: past temporal expressions
							if (Integer.parseInt(dctQuarter.substring(1)) < Integer.parseInt(viThisQuarter.substring(1))){
								int intNewYear = dctYear - 1;
								newYearValue = intNewYear + "";
							}
						}
					}
				}
				// WITHOUT DOCUMENT CREATION TIME
				else {
					newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year", language);
				}
			}
			// vi has half
			if (viHasHalf == true) {
				// WITH DOCUMENT CREATION TIME
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					//  Tense is FUTURE
					if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
						if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))) {
							int intNewYear = dctYear + 1;
							newYearValue = intNewYear + "";
						}
					}
					// Tense is PAST
					if ((last_used_tense.equals("PAST"))) {
						if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))) {
							int intNewYear = dctYear - 1;
							newYearValue = intNewYear + "";
						}
					}
					// IF NO TENSE IS FOUND
					if (last_used_tense.equals("")){
						if (documentTypeColloquial){
							// IN COLLOQUIAL: future temporal expressions
							if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))){
								int intNewYear = dctYear + 1;
								newYearValue = intNewYear + "";
							}
						}
						else{
							// IN NEWS: past temporal expressions
							if (Integer.parseInt(dctHalf.substring(1)) < Integer.parseInt(viThisHalf.substring(1))){
								int intNewYear = dctYear - 1;
								newYearValue = intNewYear + "";
							}
						}
					}
				}
				// WITHOUT DOCUMENT CREATION TIME
				else {
					newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year", language);
				}
			}
			
			// vi has season
			if ((viHasMonth == false) && (viHasDay == false) && (viHasSeason == true)) {
				// TODO check tenses?
				// WITH DOCUMENT CREATION TIME
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					newYearValue = dctYear+"";
				}
				// WITHOUT DOCUMENT CREATION TIME
				else {
					newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year", language);
				}
			}
			// vi has week
			if (viHasWeek) {
				// WITH DOCUMENT CREATION TIME
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					newYearValue = dctYear+"";
				}
				// WITHOUT DOCUMENT CREATION TIME
				else {
					newYearValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "year", language);
				}
			}

			// REPLACE THE UNDEF-YEAR WITH THE NEWLY CALCULATED YEAR AND ADD TIMEX TO INDEXES
			if (newYearValue.equals("")) {
				valueNew = ambigString.replaceFirst("UNDEF-year", "XXXX");
			}
			else {
				valueNew = ambigString.replaceFirst("UNDEF-year", newYearValue);
			}
		}

		///////////////////////////////////////////////////
		// just century is unspecified (UNDEF-century86) //
		///////////////////////////////////////////////////
		else if ((ambigString.startsWith("UNDEF-century"))) {
			String newCenturyValue = dctCentury+"";
			
			// NEWS and COLLOQUIAL DOCUMENTS
			if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable) && !ambigString.equals("UNDEF-century")) {
				int viThisDecade = Integer.parseInt(ambigString.substring(13, 14));
				
				Logger.printDetail("dctCentury"+dctCentury);
				
				newCenturyValue = dctCentury+"";
				Logger.printDetail("dctCentury"+dctCentury);
				
				//  Tense is FUTURE
				if ((last_used_tense.equals("FUTURE")) || (last_used_tense.equals("PRESENTFUTURE"))) {
					if (viThisDecade < dctDecade) {
						newCenturyValue = dctCentury + 1+"";
					} else {
						newCenturyValue = dctCentury+"";
					}
				}
				// Tense is PAST
				if ((last_used_tense.equals("PAST"))) {
					if (dctDecade < viThisDecade) {
						newCenturyValue = dctCentury - 1+"";
					} else {
						newCenturyValue = dctCentury+"";
					}
				}
			}
			// NARRATIVE DOCUMENTS
			else {
				newCenturyValue = ContextAnalyzer.getLastMentionedX(linearDates, i, "century", language);
				if (!(newCenturyValue.startsWith("BC"))){
					if ((newCenturyValue.matches("^\\d\\d.*")) && (Integer.parseInt(newCenturyValue.substring(0, 2)) < 10)){
						newCenturyValue = "00";
					}
				}else{
					newCenturyValue = "00";
				}
			}
			if (newCenturyValue.equals("")){
				if (!(documentTypeNarrative)) {
					// always assume that sixties, twenties, and so on are 19XX if no century found (LREC change)
					valueNew = ambigString.replaceFirst("UNDEF-century", "19");
				}
				// LREC change: assume in narrative-style documents that if no other century was mentioned before, 1st century
				else {
					valueNew = ambigString.replaceFirst("UNDEF-century", "00");
				}
			}
			else {
				valueNew = ambigString.replaceFirst("UNDEF-century", newCenturyValue);
			}
			// always assume that sixties, twenties, and so on are 19XX -- if not narrative document (LREC change)
			if ((valueNew.matches("\\d\\d\\d")) && (!(documentTypeNarrative))) {
				valueNew = "19" + valueNew.substring(2);
			}
		}
		
		////////////////////////////////////////////////////
		// CHECK IMPLICIT EXPRESSIONS STARTING WITH UNDEF //
		////////////////////////////////////////////////////
		else if (ambigString.startsWith("UNDEF")) {
			valueNew = ambigString;
			if (ambigString.matches("^UNDEF-REFDATE$")){
				if (i > 0){
					Timex3 anyDate = linearDates.get(i-1);
					String lmDate = anyDate.getTimexValue();
					valueNew = lmDate;
				}
				else{
					valueNew = "XXXX-XX-XX";
				}

				//////////////////
				// TO CALCULATE //
				//////////////////
				// year to calculate
			} else if (ambigString.matches("^UNDEF-(this|REFUNIT|REF)-(.*?)-(MINUS|PLUS)-([0-9]+).*")) {
				for (MatchResult mr : Toolbox.findMatches(Pattern.compile("^(UNDEF-(this|REFUNIT|REF)-(.*?)-(MINUS|PLUS)-([0-9]+)).*"), ambigString)) {
					String checkUndef = mr.group(1);
					String ltn  = mr.group(2);
					String unit = mr.group(3);
					String op   = mr.group(4);
					int diff    = Integer.parseInt(mr.group(5));
					
					// do the processing for SCIENTIFIC documents (TPZ identification could be improved)
					if ((documentTypeScientific)){
						String opSymbol = "-";
						if (op.equals("PLUS")){
							opSymbol = "+";
						}
						if (unit.equals("year")){
							String diffString = diff+"";
							if (diff < 10){
								diffString = "000"+diff;
							}
							else if (diff < 100){
								diffString = "00"+diff;
							}
							else if (diff < 1000){
								diffString = "0"+diff;
							}
							valueNew = "TPZ"+opSymbol+diffString;
						}
						else if (unit.equals("month")){
							String diffString = diff+"";
							if (diff < 10){
								diffString = "0000-0"+diff;
							}
							else {
								diffString = "0000-"+diff;
							}
							valueNew = "TPZ"+opSymbol+diffString;
						}
						else if (unit.equals("week")){
							String diffString = diff+"";
							if (diff < 10){
								diffString = "0000-W0"+diff;
							}
							else {
								diffString = "0000-W"+diff;
							}
							valueNew = "TPZ"+opSymbol+diffString;
						}
						else if (unit.equals("day")){
							String diffString = diff+"";
							if (diff < 10){
								diffString = "0000-00-0"+diff;
							}
							else {
								diffString = "0000-00-"+diff;
							}
							valueNew = "TPZ"+opSymbol+diffString;
						}
						else if (unit.equals("hour")){
							String diffString = diff+"";
							if (diff < 10){
								diffString = "0000-00-00T0"+diff;
							}
							else {
								diffString = "0000-00-00T"+diff;
							}
							valueNew = "TPZ"+opSymbol+diffString;
						}
						else if (unit.equals("minute")){
							String diffString = diff+"";
							if (diff < 10){
								diffString = "0000-00-00T00:0"+diff;
							}
							else {
								diffString = "0000-00-00T00:"+diff;
							}
							valueNew = "TPZ"+opSymbol+diffString;								
						}							
						else if (unit.equals("second")){
							String diffString = diff+"";
							if (diff < 10){
								diffString = "0000-00-00T00:00:0"+diff;
							}
							else {
								diffString = "0000-00-00T00:00:"+diff;
							}
							valueNew = "TPZ"+opSymbol+diffString;
						}
					}
					else{	
						
						
						// check for REFUNIT (only allowed for "year")
						if ((ltn.equals("REFUNIT")) && (unit.equals("year"))) {
							String dateWithYear = ContextAnalyzer.getLastMentionedX(linearDates, i, "dateYear", language);
							String year = dateWithYear;
							if (dateWithYear.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX");
							} else {
								if (dateWithYear.startsWith("BC")){
									year = dateWithYear.substring(0,6);
								}
								else{
									year = dateWithYear.substring(0,4);
								}
								if (op.equals("MINUS")) {
									diff = diff * (-1);
								}
								String yearNew = DateCalculator.getXNextYear(dateWithYear, diff);
								String rest = dateWithYear.substring(4);
								valueNew = valueNew.replace(checkUndef, yearNew+rest);
							}
						}
						
						
						// REF and this are handled here
						if (unit.equals("century")) {
							if ((documentTypeNews|documentTypeColloquial||documentTypeScientific) && (dctAvailable) && (ltn.equals("this"))) {
								int century = dctCentury;
								if (op.equals("MINUS")) {
									century = dctCentury - diff;
								} else if (op.equals("PLUS")) {
									century = dctCentury + diff;
								}
								valueNew = valueNew.replace(checkUndef, century+"");
							} else {
								String lmCentury = ContextAnalyzer.getLastMentionedX(linearDates, i, "century", language);
								if (lmCentury.equals("")) {
									valueNew = valueNew.replace(checkUndef, "XX");
								} else {
									if (op.equals("MINUS")) {
										diff = (-1) * diff;
									} 
									lmCentury = DateCalculator.getXNextCentury(lmCentury, diff);
									valueNew = valueNew.replace(checkUndef, lmCentury);
								}
							}
						} else if (unit.equals("decade")) {
							if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable) && (ltn.equals("this"))) {
								int dctDecadeLong = Integer.parseInt(dctCentury + "" + dctDecade);
								int decade = dctDecadeLong;
								if (op.equals("MINUS")) {
									decade = dctDecadeLong - diff;
								} else if (op.equals("PLUS")) {
									decade = dctDecadeLong + diff;
								}
								valueNew = valueNew.replace(checkUndef, decade+"X");
							} else {
								String lmDecade = ContextAnalyzer.getLastMentionedX(linearDates, i, "decade", language);
								if (lmDecade.equals("")) {
									valueNew = valueNew.replace(checkUndef, "XXX");
								} else {
									if (op.equals("MINUS")) {
										diff = (-1) * diff;
									}
									lmDecade = DateCalculator.getXNextDecade(lmDecade, diff);
									valueNew = valueNew.replace(checkUndef, lmDecade);
								}
							}
						} else if (unit.equals("year")) {
							if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable) && (ltn.equals("this"))) {
								int intValue = dctYear;
								if (op.equals("MINUS")) {
									intValue = dctYear - diff;
								} else if (op.equals("PLUS")) {
									intValue = dctYear + diff;
								}
								valueNew = valueNew.replace(checkUndef, intValue + "");
							} else {
								String lmYear = ContextAnalyzer.getLastMentionedX(linearDates, i, "year", language);
								if (lmYear.equals("")) {
									valueNew = valueNew.replace(checkUndef, "XXXX");
								} else {
									if (op.equals("MINUS")) {
										diff = (-1) * diff;
									} 
									lmYear = DateCalculator.getXNextYear(lmYear, diff);
									valueNew = valueNew.replace(checkUndef, lmYear);
								}
							}
							// TODO BC years
						} else if (unit.equals("quarter")) {
							if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable) && (ltn.equals("this"))) {
								int intYear    = dctYear;
								int intQuarter = Integer.parseInt(dctQuarter.substring(1));
								int diffQuarters = diff % 4;
								diff = diff - diffQuarters;
								int diffYears    = diff / 4;
								if (op.equals("MINUS")) {
									diffQuarters = diffQuarters * (-1);
									diffYears    = diffYears    * (-1);
								}
								intYear    = intYear + diffYears;
								intQuarter = intQuarter + diffQuarters; 
								valueNew = valueNew.replace(checkUndef, intYear+"-Q"+intQuarter);
							} else {
								String lmQuarter = ContextAnalyzer.getLastMentionedX(linearDates, i, "quarter", language);
								if (lmQuarter.equals("")) {
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								} else {
									int intYear    = Integer.parseInt(lmQuarter.substring(0, 4));
									int intQuarter = Integer.parseInt(lmQuarter.substring(6)); 
									int diffQuarters = diff % 4;
									diff = diff - diffQuarters;
									int diffYears    = diff / 4;
									if (op.equals("MINUS")) {
										diffQuarters = diffQuarters * (-1);
										diffYears    = diffYears    * (-1);
									}
									intYear    = intYear + diffYears;
									intQuarter = intQuarter + diffQuarters; 
									valueNew = valueNew.replace(checkUndef, intYear+"-Q"+intQuarter);
								}
							}
						} else if (unit.equals("month")) {
							if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable) && (ltn.equals("this"))) {
								if (op.equals("MINUS")) {
									diff = diff * (-1);
								}
								valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(dctYear + "-" + norm.getFromNormNumber(dctMonth+""), diff));
							} else {
								String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates, i, "month", language);
								if (lmMonth.equals("")) {
									valueNew = valueNew.replace(checkUndef, "XXXX-XX");
								} else {
									if (op.equals("MINUS")) {
										diff = diff * (-1);
									}
									valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(lmMonth, diff));
								}
							}
						} else if (unit.equals("week")) {
							if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable) && (ltn.equals("this"))) {
								if (op.equals("MINUS")) {
									diff = diff * (-1);
								} else if (op.equals("PLUS")) {
									// diff = diff * 7;
								}
								valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextWeek(dctYear+"-W"+norm.getFromNormNumber(dctWeek+""), diff, language));
							} else {
								String lmDay = ContextAnalyzer.getLastMentionedX(linearDates, i, "day", language);
								if (lmDay.equals("")) {
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								} else {
									if (op.equals("MINUS")) {
										diff = diff * 7 * (-1);
									} else if (op.equals("PLUS")) {
										diff = diff * 7;
									}
									valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay, diff));
								}
							}
						} else if (unit.equals("day")) {
							if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable) && (ltn.equals("this"))) {
								if (op.equals("MINUS")) {
									diff = diff * (-1);
								}
								valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(dctYear + "-" + norm.getFromNormNumber(dctMonth+"") + "-"	+ dctDay, diff));
							} else {
								String lmDay = ContextAnalyzer.getLastMentionedX(linearDates, i, "day", language);
								if (lmDay.equals("")) {
									valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
								} else {
									if (op.equals("MINUS")) {
										diff = diff * (-1);
									}
									valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay, diff));
								}
							}
						}
					}
				}
			}
		
			// century
			else if (ambigString.startsWith("UNDEF-last-century")) {
				String checkUndef = "UNDEF-last-century";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, norm.getFromNormNumber(dctCentury - 1 +""));
				} else {
					String lmCentury = ContextAnalyzer.getLastMentionedX(linearDates,i,"century", language);
					if (lmCentury.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XX");
					} 
					else {
						lmCentury = DateCalculator.getXNextCentury(lmCentury, -1);
						valueNew = valueNew.replace(checkUndef, lmCentury);
					}
				}
			} else if (ambigString.startsWith("UNDEF-this-century")) {
				String checkUndef = "UNDEF-this-century";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, norm.getFromNormNumber(dctCentury+""));
				} else {
					String lmCentury = ContextAnalyzer.getLastMentionedX(linearDates,i,"century", language);
					if (lmCentury.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XX");
					} else {
						valueNew = valueNew.replace(checkUndef, lmCentury);
					}
				}
			} else if (ambigString.startsWith("UNDEF-next-century")) {
				String checkUndef = "UNDEF-next-century";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, norm.getFromNormNumber(dctCentury + 1+""));
				} else {
					String lmCentury = ContextAnalyzer.getLastMentionedX(linearDates,i,"century", language);
					if (lmCentury.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XX");
					} else {
						lmCentury = DateCalculator.getXNextCentury(lmCentury, +1);
						valueNew = valueNew.replace(checkUndef, lmCentury);
					}
				}
			}

			// decade
			else if (ambigString.startsWith("UNDEF-last-decade")) {
				String checkUndef = "UNDEF-last-decade";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, (dctYear - 10+"").substring(0,3));
				} else {
					String lmDecade = ContextAnalyzer.getLastMentionedX(linearDates,i,"decade", language);
					if (lmDecade.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX");
					} else {
						lmDecade = DateCalculator.getXNextDecade(lmDecade, -1);
						valueNew = valueNew.replace(checkUndef, lmDecade);
					}
				}
			} else if (ambigString.startsWith("UNDEF-this-decade")) {
				String checkUndef = "UNDEF-this-decade";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, (dctYear+"").substring(0,3));
				} else {
					String lmDecade = ContextAnalyzer.getLastMentionedX(linearDates,i,"decade", language);
					if (lmDecade.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX");
					} else {
						valueNew = valueNew.replace(checkUndef, lmDecade);
					}
				}
			} else if (ambigString.startsWith("UNDEF-next-decade")) {
				String checkUndef = "UNDEF-next-decade";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, (dctYear + 10+"").substring(0,3));
				} else {
					String lmDecade = ContextAnalyzer.getLastMentionedX(linearDates,i,"decade", language);
					if (lmDecade.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX");
					} else {
						lmDecade = DateCalculator.getXNextDecade(lmDecade, 1);
						valueNew = valueNew.replace(checkUndef, lmDecade);
					}
				}
			}
			
			// year
			else if (ambigString.startsWith("UNDEF-last-year")) {
				String checkUndef = "UNDEF-last-year";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, dctYear -1 +"");
				} else {
					String lmYear = ContextAnalyzer.getLastMentionedX(linearDates,i,"year", language);
					if (lmYear.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX");
					} else {
						lmYear = DateCalculator.getXNextYear(lmYear, -1);
						valueNew = valueNew.replace(checkUndef, lmYear);
					}
				}
				if (valueNew.endsWith("-FY")){					
					valueNew = "FY" + valueNew.substring(0, Math.min(valueNew.length(), 4));
				}
			} else if (ambigString.startsWith("UNDEF-this-year")) {
				String checkUndef = "UNDEF-this-year";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, dctYear +"");
				} else {
					String lmYear = ContextAnalyzer.getLastMentionedX(linearDates,i,"year", language);
					if (lmYear.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX");
					} else {
						valueNew = valueNew.replace(checkUndef, lmYear);
					}
				}
				if (valueNew.endsWith("-FY")){					
					valueNew = "FY" + valueNew.substring(0, Math.min(valueNew.length(), 4));
				}
			} else if (ambigString.startsWith("UNDEF-next-year")) {
				String checkUndef = "UNDEF-next-year";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, dctYear +1 +"");	
				} else {
					String lmYear = ContextAnalyzer.getLastMentionedX(linearDates,i,"year", language);
					if (lmYear.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX");
					} else {
						lmYear = DateCalculator.getXNextYear(lmYear, 1);
						valueNew = valueNew.replace(checkUndef, lmYear);
					}
				}
				if (valueNew.endsWith("-FY")){					
					valueNew = "FY" + valueNew.substring(0, Math.min(valueNew.length(), 4));
				}
			}
			
			// month
			else if (ambigString.startsWith("UNDEF-last-month")) {
				String checkUndef = "UNDEF-last-month";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(dctYear + "-" + norm.getFromNormNumber(dctMonth+""), -1));
				} else {
					String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates,i,"month", language);
					if (lmMonth.equals("")) {
						valueNew =  valueNew.replace(checkUndef, "XXXX-XX");
					} else {
						valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(lmMonth, -1));
					}
				}
			} else if (ambigString.startsWith("UNDEF-this-month")) {
				String checkUndef = "UNDEF-this-month";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, dctYear + "-" + norm.getFromNormNumber(dctMonth+""));
				} else {
					String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates,i,"month", language);
					if (lmMonth.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-XX");
					} else { 
						valueNew = valueNew.replace(checkUndef, lmMonth);
					}
				}
			}
			else if (ambigString.startsWith("UNDEF-next-month")) {
				String checkUndef = "UNDEF-next-month";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(dctYear + "-" + norm.getFromNormNumber(dctMonth+""), 1));
				} else {
					String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates,i,"month", language);
					if (lmMonth.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-XX");
					} else {
						valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextMonth(lmMonth, 1));
					}
				}
			}
			
			// day
			else if (ambigString.startsWith("UNDEF-last-day")) {
				String checkUndef = "UNDEF-last-day";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(dctYear + "-" + norm.getFromNormNumber(dctMonth+"") + "-"+ dctDay, -1));
				} else {
					String lmDay = ContextAnalyzer.getLastMentionedX(linearDates,i,"day", language);
					if (lmDay.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
					} else {
						valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay,-1));
					}
				}
			} else if (ambigString.startsWith("UNDEF-this-day")) {
				String checkUndef = "UNDEF-this-day";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, dctYear + "-" + norm.getFromNormNumber(dctMonth+"") + "-"+ norm.getFromNormNumber(dctDay+""));
				} else {
					String lmDay = ContextAnalyzer.getLastMentionedX(linearDates,i,"day", language);
					if (lmDay.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
					} else {
						valueNew = valueNew.replace(checkUndef, lmDay);
					}
					if (ambigString.equals("UNDEF-this-day")) {
						valueNew = "PRESENT_REF"; 
					}
				}					
			}
			else if (ambigString.startsWith("UNDEF-next-day")) {
				String checkUndef = "UNDEF-next-day";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(dctYear + "-" + norm.getFromNormNumber(dctMonth+"") + "-"+ dctDay, 1));
				} else {
					String lmDay = ContextAnalyzer.getLastMentionedX(linearDates,i,"day", language);
					if (lmDay.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
					} else {
						valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay,1));
					}
				}	
			}

			// week
			else if (ambigString.startsWith("UNDEF-last-week")) {
				String checkUndef = "UNDEF-last-week";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextWeek(dctYear+"-W"+norm.getFromNormNumber(dctWeek+""),-1, language));
				} else {
					String lmWeek = ContextAnalyzer.getLastMentionedX(linearDates,i,"week", language);
					if (lmWeek.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-WXX");
					} else {
						valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextWeek(lmWeek,-1, language));
					}
				}
			} else if (ambigString.startsWith("UNDEF-this-week")) {
				String checkUndef = "UNDEF-this-week";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef,dctYear+"-W"+norm.getFromNormNumber(dctWeek+""));
				} else {
					String lmWeek = ContextAnalyzer.getLastMentionedX(linearDates,i,"week", language);
					if (lmWeek.equals("")) {
						valueNew = valueNew.replace(checkUndef,"XXXX-WXX");
					} else {
						valueNew = valueNew.replace(checkUndef,lmWeek);
					}
				}					
			} else if (ambigString.startsWith("UNDEF-next-week")) {
				String checkUndef = "UNDEF-next-week";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextWeek(dctYear+"-W"+norm.getFromNormNumber(dctWeek+""),1, language));
				} else {
					String lmWeek = ContextAnalyzer.getLastMentionedX(linearDates,i,"week", language);
					if (lmWeek.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-WXX");
					} else {
						valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextWeek(lmWeek,1, language));
					}
				}
			}
			
			// quarter
			else if (ambigString.startsWith("UNDEF-last-quarter")) {
				String checkUndef = "UNDEF-last-quarter";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					if (dctQuarter.equals("Q1")) {
						valueNew = valueNew.replace(checkUndef, dctYear-1+"-Q4");
					} else {
						int newQuarter = Integer.parseInt(dctQuarter.substring(1,2))-1;
						valueNew = valueNew.replace(checkUndef, dctYear+"-Q"+newQuarter);
					}
				} else {
					String lmQuarter  = ContextAnalyzer.getLastMentionedX(linearDates, i, "quarter", language);
					if (lmQuarter.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-QX");
					} else {
						int lmQuarterOnly = Integer.parseInt(lmQuarter.substring(6,7));
						int lmYearOnly    = Integer.parseInt(lmQuarter.substring(0,4));
						if (lmQuarterOnly == 1) {
							valueNew = valueNew.replace(checkUndef, lmYearOnly-1+"-Q4");
						} else {
							int newQuarter = lmQuarterOnly-1;
							valueNew = valueNew.replace(checkUndef, lmYearOnly+"-Q"+newQuarter);
						}
					}
				}
			} else if (ambigString.startsWith("UNDEF-this-quarter")) {
				String checkUndef = "UNDEF-this-quarter";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					valueNew = valueNew.replace(checkUndef, dctYear+"-"+dctQuarter);
				} else {
					String lmQuarter = ContextAnalyzer.getLastMentionedX(linearDates, i, "quarter", language);
					if (lmQuarter.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-QX");
					} else {
						valueNew = valueNew.replace(checkUndef, lmQuarter);
					}
				}					
			} else if (ambigString.startsWith("UNDEF-next-quarter")) {
				String checkUndef = "UNDEF-next-quarter";
				if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
					if (dctQuarter.equals("Q4")) {
						valueNew = valueNew.replace(checkUndef, dctYear+1+"-Q1");
					} else {
						int newQuarter = Integer.parseInt(dctQuarter.substring(1,2))+1;
						valueNew = valueNew.replace(checkUndef, dctYear+"-Q"+newQuarter);
					}						
				} else {
					String lmQuarter  = ContextAnalyzer.getLastMentionedX(linearDates, i, "quarter", language);
					if (lmQuarter.equals("")) {
						valueNew = valueNew.replace(checkUndef, "XXXX-QX");
					} else {
						int lmQuarterOnly = Integer.parseInt(lmQuarter.substring(6,7));
						int lmYearOnly    = Integer.parseInt(lmQuarter.substring(0,4));
						if (lmQuarterOnly == 4) {
							valueNew = valueNew.replace(checkUndef, lmYearOnly+1+"-Q1");
						} else {
							int newQuarter = lmQuarterOnly+1;
							valueNew = valueNew.replace(checkUndef, dctYear+"-Q"+newQuarter);
						}
					}
				}
			}
			
			// MONTH NAMES
			else if (ambigString.matches("UNDEF-(last|this|next)-(january|february|march|april|may|june|july|august|september|october|november|december).*")) {
				for (MatchResult mr : Toolbox.findMatches(Pattern.compile("(UNDEF-(last|this|next)-(january|february|march|april|may|june|july|august|september|october|november|december))(.*)"),ambigString)) {
					String rest = mr.group(4);
					int day = 0;
					for (MatchResult mr_rest : Toolbox.findMatches(Pattern.compile("-([0-9][0-9])"),rest)){
						day = Integer.parseInt(mr_rest.group(1));
					}
					String checkUndef = mr.group(1);
					String ltn      = mr.group(2);
					String newMonth = norm.getFromNormMonthName((mr.group(3)));
					int newMonthInt = Integer.parseInt(newMonth);
					if (ltn.equals("last")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							// check day if dct-month and newMonth are equal
							if ((dctMonth == newMonthInt) && (!(day == 0))){
								if (dctDay > day){
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
								}
								else{
									valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newMonth);
								}
							}
							else if (dctMonth <= newMonthInt) {
								valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newMonth);
							} else {
								valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
							}
						} else {
							String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates, i, "month-with-details", language);
							if (lmMonth.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX");
							} else {
								int lmMonthInt = Integer.parseInt(lmMonth.substring(5,7));
								// 
								int lmDayInt  = 0;
								if ((lmMonth.length() > 9) && (lmMonth.subSequence(8,10).toString().matches("\\d\\d"))){
									lmDayInt = Integer.parseInt(lmMonth.subSequence(8,10)+"");
								}
								if ((lmMonthInt == newMonthInt) && (!(lmDayInt == 0)) && (!(day == 0))){
									if (lmDayInt > day){
										valueNew = valueNew.replace(checkUndef, lmMonth.substring(0,4)+"-"+newMonth);
									}
									else{
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmMonth.substring(0,4))-1+"-"+newMonth);
									}
								}
								if (lmMonthInt <= newMonthInt) {
									valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmMonth.substring(0,4))-1+"-"+newMonth);
								} else {
									valueNew = valueNew.replace(checkUndef, lmMonth.substring(0,4)+"-"+newMonth);
								}
							}
						}
					} else if (ltn.equals("this")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
						} else {
							String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates, i, "month-with-details", language);
							if (lmMonth.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX");
							} else {
								valueNew = valueNew.replace(checkUndef, lmMonth.substring(0,4)+"-"+newMonth);
							}
						}
					} else if (ltn.equals("next")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							// check day if dct-month and newMonth are equal								
							if ((dctMonth == newMonthInt) && (!(day == 0))){
								if (dctDay < day){
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
								}
								else{
									valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newMonth);
								}
							}
							else if (dctMonth >= newMonthInt) {
								valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newMonth);
							} else {
								valueNew = valueNew.replace(checkUndef, dctYear+"-"+newMonth);
							}
						} else {
							String lmMonth = ContextAnalyzer.getLastMentionedX(linearDates, i, "month-with-details", language);
							if (lmMonth.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX");
							} else {
								int lmMonthInt = Integer.parseInt(lmMonth.substring(5,7));
								if (lmMonthInt >= newMonthInt) {
									valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmMonth.substring(0,4))+1+"-"+newMonth);
								} else {
									valueNew = valueNew.replace(checkUndef, lmMonth.substring(0,4)+"-"+newMonth);
								}
							}	
						}
					}
				}
			}
			
			// SEASONS NAMES
			else if (ambigString.matches("^UNDEF-(last|this|next)-(SP|SU|FA|WI).*")) {
				for (MatchResult mr : Toolbox.findMatches(Pattern.compile("(UNDEF-(last|this|next)-(SP|SU|FA|WI)).*"),ambigString)) {
					String checkUndef = mr.group(1);
					String ltn       = mr.group(2);
					String newSeason = mr.group(3);
					if (ltn.equals("last")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							if (dctSeason.equals("SP")) {
								valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
							} else if (dctSeason.equals("SU")) {
								if (newSeason.equals("SP")) {
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
								} else {
									valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
								}
							} else if (dctSeason.equals("FA")) {
								if ((newSeason.equals("SP")) || (newSeason.equals("SU"))) {
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
								} else {
									valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
								}
							} else if (dctSeason.equals("WI")) {
								if (newSeason.equals("WI")) {
									valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
								} else {
									if (dctMonth < 12){
										valueNew = valueNew.replace(checkUndef, dctYear-1+"-"+newSeason);
									}
									else{
										valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
									}
								}
							}
						} else { // NARRATVIE DOCUMENT
							String lmSeason = ContextAnalyzer.getLastMentionedX(linearDates, i, "season", language);
							if (lmSeason.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX");
							} else {
								if (lmSeason.substring(5,7).equals("SP")) {
									valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
								} else if (lmSeason.substring(5,7).equals("SU")) {
									if (lmSeason.substring(5,7).equals("SP")) {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
									} else {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
									}
								} else if (lmSeason.substring(5,7).equals("FA")) {
									if ((newSeason.equals("SP")) || (newSeason.equals("SU"))) {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
									} else {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
									}
								} else if (lmSeason.substring(5,7).equals("WI")) {
									if (newSeason.equals("WI")) {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))-1+"-"+newSeason);
									} else {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
									}
								}
							}
						}
					} else if (ltn.equals("this")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							// TODO include tense of sentence?
							valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
						} else {
							// TODO include tense of sentence?
							String lmSeason = ContextAnalyzer.getLastMentionedX(linearDates, i, "season", language);
							if (lmSeason.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX");
							} else {
								valueNew = valueNew.replace(checkUndef, lmSeason.substring(0,4)+"-"+newSeason);
							}
						}
					} else if (ltn.equals("next")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							if (dctSeason.equals("SP")) {
								if (newSeason.equals("SP")) {
									valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
								} else {
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
								}
							} else if (dctSeason.equals("SU")) {
								if ((newSeason.equals("SP")) || (newSeason.equals("SU"))) {
									valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
								} else {
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
								}
							} else if (dctSeason.equals("FA")) {
								if (newSeason.equals("WI")) {
									valueNew = valueNew.replace(checkUndef, dctYear+"-"+newSeason);
								} else {
									valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
								}
							} else if (dctSeason.equals("WI")) {
								valueNew = valueNew.replace(checkUndef, dctYear+1+"-"+newSeason);
							}
						} else { // NARRATIVE DOCUMENT
							String lmSeason = ContextAnalyzer.getLastMentionedX(linearDates, i, "season", language);
							if (lmSeason.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX");
							} else {
								if (lmSeason.substring(5,7).equals("SP")) {
									if (newSeason.equals("SP")) {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
									} else {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
									}
								} else if (lmSeason.substring(5,7).equals("SU")) {
									if ((newSeason.equals("SP")) || (newSeason.equals("SU"))) {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
									} else {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
									}
								} else if (lmSeason.substring(5,7).equals("FA")) {
									if (newSeason.equals("WI")) {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+"-"+newSeason);
									} else {
										valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
									}
								} else if (lmSeason.substring(5,7).equals("WI")) {
									valueNew = valueNew.replace(checkUndef, Integer.parseInt(lmSeason.substring(0,4))+1+"-"+newSeason);
								}
							}
						}
					}
				}
			}
			
			// WEEKDAY NAMES
			// TODO the calculation is strange, but works
			// TODO tense should be included?!
			else if (ambigString.matches("^UNDEF-(last|this|next|day)-(monday|tuesday|wednesday|thursday|friday|saturday|sunday).*")) {
				for (MatchResult mr : Toolbox.findMatches(Pattern.compile("(UNDEF-(last|this|next|day)-(monday|tuesday|wednesday|thursday|friday|saturday|sunday)).*"),ambigString)) {
					String checkUndef = mr.group(1);
					String ltnd       = mr.group(2);
					String newWeekday = mr.group(3);
					int newWeekdayInt = Integer.parseInt(norm.getFromNormDayInWeek(newWeekday));
					if (ltnd.equals("last")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							int diff = (-1) * (dctWeekday - newWeekdayInt);
							if (diff >= 0) {
								diff = diff - 7;
							}
							valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(dctYear + "-" + dctMonth + "-" + dctDay, diff));
						} else {
							String lmDay     = ContextAnalyzer.getLastMentionedX(linearDates, i, "day", language);
							if (lmDay.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
							} else {
								int lmWeekdayInt = DateCalculator.getWeekdayOfDate(lmDay);
								int diff = (-1) * (lmWeekdayInt - newWeekdayInt);
								if (diff >= 0) {
									diff = diff - 7;
								}
								valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay, diff));
							}
						}
					} else if (ltnd.equals("this")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							// TODO tense should be included?!		
							int diff = (-1) * (dctWeekday - newWeekdayInt);
							if (diff >= 0) {
								diff = diff - 7;
							}
							if (diff == -7) {
								diff = 0;
							}
							
							valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(dctYear + "-" + dctMonth + "-"+ dctDay, diff));
						} else {
							// TODO tense should be included?!
							String lmDay     = ContextAnalyzer.getLastMentionedX(linearDates, i, "day", language);
							if (lmDay.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
							} else {
								int lmWeekdayInt = DateCalculator.getWeekdayOfDate(lmDay);
								int diff = (-1) * (lmWeekdayInt - newWeekdayInt);
								if (diff >= 0) {
									diff = diff - 7;
								}
								if (diff == -7) {
									diff = 0;
								}
								valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay, diff));
							}
						}							
					} else if (ltnd.equals("next")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							int diff = newWeekdayInt - dctWeekday;
							if (diff <= 0) {
								diff = diff + 7;
							}
							valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(dctYear + "-" + dctMonth + "-"+ dctDay, diff));
						} else {
							String lmDay     = ContextAnalyzer.getLastMentionedX(linearDates, i, "day", language);
							if (lmDay.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
							} else {
								int lmWeekdayInt = DateCalculator.getWeekdayOfDate(lmDay);
								int diff = newWeekdayInt - lmWeekdayInt;
								if (diff <= 0) {
									diff = diff + 7;
								}
								valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay, diff));
							}
						}
					} else if (ltnd.equals("day")) {
						if ((documentTypeNews||documentTypeColloquial||documentTypeScientific) && (dctAvailable)) {
							// TODO tense should be included?!
							int diff = (-1) * (dctWeekday - newWeekdayInt);
							if (diff >= 0) {
								diff = diff - 7;
							}
							if (diff == -7) {
								diff = 0;
							}
							//  Tense is FUTURE
							if ((last_used_tense.equals("FUTURE")) && diff != 0) {
								diff = diff + 7;
							}
							// Tense is PAST
							if ((last_used_tense.equals("PAST"))) {
							
							}
							valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(dctYear + "-" + dctMonth + "-"+ dctDay, diff));
						} else {
							// TODO tense should be included?!
							String lmDay     = ContextAnalyzer.getLastMentionedX(linearDates, i, "day", language);
							if (lmDay.equals("")) {
								valueNew = valueNew.replace(checkUndef, "XXXX-XX-XX");
							} else {
								int lmWeekdayInt = DateCalculator.getWeekdayOfDate(lmDay);
								int diff = (-1) * (lmWeekdayInt - newWeekdayInt);
								if (diff >= 0) {
									diff = diff - 7;
								}
								if (diff == -7) {
									diff = 0;
								}
								valueNew = valueNew.replace(checkUndef, DateCalculator.getXNextDay(lmDay, diff));
							}
						}
					}
				}
				
			} else {
				Logger.printDetail(component, "ATTENTION: UNDEF value for: " + valueNew+" is not handled in disambiguation phase!");
			}
		}
		
		return valueNew;
	}
	
	/**
	 * Under-specified values are disambiguated here. Only Timexes of types "date" and "time" can be under-specified.
	 * @param jcas
	 */
	public void specifyAmbiguousValues(JCas jcas) {
		// build up a list with all found TIMEX expressions
		List<Timex3> linearDates = new ArrayList<Timex3>();
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();

		// Create List of all Timexes of types "date" and "time"
		while (iterTimex.hasNext()) {
			Timex3 timex = (Timex3) iterTimex.next();
			if (timex.getTimexType().equals("DATE") || timex.getTimexType().equals("TIME")) {
				linearDates.add(timex);
			}
			
			if(timex.getTimexType().equals("DURATION") && !timex.getEmptyValue().equals("")) {
				linearDates.add(timex);
			}
		}
		
		//////////////////////////////////////////////
		// go through list of Date and Time timexes //
		//////////////////////////////////////////////
		for (int i = 0; i < linearDates.size(); i++) {
			Timex3 t_i = (Timex3) linearDates.get(i);
			String value_i = t_i.getTimexValue();
			
			String valueNew = value_i;
			// handle the value attribute only if we have a TIME or DATE
			if(t_i.getTimexType().equals("TIME") || t_i.getTimexType().equals("DATE"))
					valueNew = specifyAmbiguousValuesString(value_i, t_i, i, linearDates, jcas);
			
			// handle the emptyValue attribute for any type
			if(t_i.getEmptyValue() != null && t_i.getEmptyValue().length() > 0) {
				String emptyValueNew = specifyAmbiguousValuesString(t_i.getEmptyValue(), t_i, i, linearDates, jcas);
				t_i.setEmptyValue(emptyValueNew);
			}
			
			t_i.removeFromIndexes();
			Logger.printDetail(t_i.getTimexId()+" DISAMBIGUATION PHASE: foundBy:"+t_i.getFoundByRule()+" text:"+t_i.getCoveredText()+" value:"+t_i.getTimexValue()+" NEW value:"+valueNew);
			
			t_i.setTimexValue(valueNew);
			t_i.addToIndexes();
			linearDates.set(i, t_i);
		}
	}
	
	
	/**
	 * @param jcas
	 */
	private void deleteOverlappedTimexesPreprocessing(JCas jcas) {
		FSIterator timexIter1 = jcas.getAnnotationIndex(Timex3.type).iterator();
		HashSet<Timex3> hsTimexesToRemove = new HashSet<Timex3>();
		while (timexIter1.hasNext()) {
			Timex3 t1 = (Timex3) timexIter1.next();
			FSIterator timexIter2 = jcas.getAnnotationIndex(Timex3.type).iterator();

			while (timexIter2.hasNext()) {
				Timex3 t2 = (Timex3) timexIter2.next();
				if (((t1.getBegin() >= t2.getBegin()) && (t1.getEnd() < t2.getEnd())) ||     // t1 starts inside or with t2 and ends before t2 -> remove t1
						((t1.getBegin() > t2.getBegin()) && (t1.getEnd() <= t2.getEnd()))) { // t1 starts inside t2 and ends with or before t2 -> remove t1
					hsTimexesToRemove.add(t1);
				} 
				else if (((t2.getBegin() >= t1.getBegin()) && (t2.getEnd() < t1.getEnd())) || // t2 starts inside or with t1 and ends before t1 -> remove t2
						((t2.getBegin() > t1.getBegin()) && (t2.getEnd() <= t1.getEnd()))) {    // t2 starts inside t1 and ends with or before t1 -> remove t2
					hsTimexesToRemove.add(t2);
				}
				// identical length
				if ((t1.getBegin() == t2.getBegin()) && (t1.getEnd() == t2.getEnd())) {
					if ((t1.getTimexType().equals("SET")) || (t2.getTimexType().equals("SET"))) {
						// REMOVE REAL DUPLICATES (the one with the lower timexID)
						if ((Integer.parseInt(t1.getTimexId().substring(1)) < Integer.parseInt(t2.getTimexId().substring(1)))) {
							hsTimexesToRemove.add(t1);
						}
					} else {
						if (!(t1.equals(t2))) {
							if ((t1.getTimexValue().startsWith("UNDEF")) && (!(t2.getTimexValue().startsWith("UNDEF")))) {
								hsTimexesToRemove.add(t1);
							} 
							else if ((!(t1.getTimexValue().startsWith("UNDEF"))) && (t2.getTimexValue().startsWith("UNDEF"))) {
								hsTimexesToRemove.add(t2);
							}
							// t1 is explicit, but t2 is not
							else if ((t1.getFoundByRule().endsWith("explicit")) && (!(t2.getFoundByRule().endsWith("explicit")))) {
								hsTimexesToRemove.add(t2);
							}
							// remove timexes that are identical, but one has an emptyvalue
							else if(t2.getEmptyValue().equals("") && !t1.getEmptyValue().equals("")) {
								hsTimexesToRemove.add(t2);
							}
							// REMOVE REAL DUPLICATES (the one with the lower timexID)
							else if ((Integer.parseInt(t1.getTimexId().substring(1)) < Integer.parseInt(t2.getTimexId().substring(1)))) {
								hsTimexesToRemove.add(t1);
							}
						}
					}
				}
			}
		}
		// remove, finally
		for (Timex3 t : hsTimexesToRemove) {
			Logger.printDetail("REMOVE DUPLICATE: " + t.getCoveredText()+"(id:"+t.getTimexId()+" value:"+t.getTimexValue()+" found by:"+t.getFoundByRule()+")");
			
			t.removeFromIndexes();
			timex_counter--;
		}
	}
	
	private void deleteOverlappedTimexesPostprocessing(JCas jcas) {
		FSIterator timexIter = jcas.getAnnotationIndex(Timex3.type).iterator();
		FSIterator innerTimexIter = timexIter.copy();
		HashSet<ArrayList<Timex3>> effectivelyToInspect = new HashSet<ArrayList<Timex3>>();
		ArrayList<Timex3> allTimexesToInspect = new ArrayList<Timex3>();
		while(timexIter.hasNext()) {
			Timex3 myTimex = (Timex3) timexIter.next();
			
			ArrayList<Timex3> timexSet = new ArrayList<Timex3>();
			timexSet.add(myTimex);
			
			// compare this timex to all other timexes and mark those that have an overlap
			while(innerTimexIter.hasNext()) {
				Timex3 myInnerTimex = (Timex3) innerTimexIter.next();
				
				if((myTimex.getBegin() <= myInnerTimex.getBegin() && myTimex.getEnd() > myInnerTimex.getBegin()) || // timex1 starts, timex2 is partial overlap
				   (myInnerTimex.getBegin() <= myTimex.getBegin() && myInnerTimex.getEnd() > myTimex.getBegin()) || // same as above, but in reverse
				   (myInnerTimex.getBegin() <= myTimex.getBegin() && myTimex.getEnd() <= myInnerTimex.getEnd()) || // timex 1 is contained within or identical to timex2
				   (myTimex.getBegin() <= myInnerTimex.getBegin() && myInnerTimex.getEnd() <= myTimex.getEnd())) { // same as above, but in reverse
					timexSet.add(myInnerTimex); // increase the set
					
					allTimexesToInspect.add(myTimex); // note that these timexes are being looked at
					allTimexesToInspect.add(myInnerTimex);
				}
			}
			
			// if overlaps with myTimex were detected, memorize them
			if(timexSet.size() > 1)
				effectivelyToInspect.add(timexSet);
			
			// reset the inner iterator
			innerTimexIter.moveToFirst();
		}
		
		/* prune those sets of overlapping timexes that are subsets of others 
		 * (i.e. leave only the largest union of overlapping timexes)
		 */
		HashSet<ArrayList<Timex3>> newEffectivelyToInspect = new HashSet<ArrayList<Timex3>>();
		for(Timex3 t : allTimexesToInspect) {
			ArrayList<Timex3> setToKeep = new ArrayList<Timex3>();
			
			// determine the largest set that contains this timex
			for(ArrayList<Timex3> tSet : effectivelyToInspect) {
				if(tSet.contains(t) && tSet.size() > setToKeep.size())
					setToKeep = tSet;
			}
			
			newEffectivelyToInspect.add(setToKeep);
		}
		// overwrite previous list of sets
		effectivelyToInspect = newEffectivelyToInspect;
		
		// iterate over the selected sets and merge information, remove old timexes
		for(ArrayList<Timex3> tSet : effectivelyToInspect) {
			Timex3 newTimex = new Timex3(jcas);
			
			// if a timex has the timex value REMOVE, remove it from consideration
			@SuppressWarnings("unchecked")
			ArrayList<Timex3> newTSet = (ArrayList<Timex3>) tSet.clone();
			for(Timex3 t : tSet) {
				if(t.getTimexValue().equals("REMOVE")) { // remove timexes with value "REMOVE"
					newTSet.remove(t);
				}
			}
			tSet = newTSet;
			
			// iteration is done if all the timexes have been removed, i.e. the set is empty 
			if(tSet.size() == 0)
				continue;
			
			/* 
			 * check 
			 * - whether all timexes of this set have the same timex type attribute,
			 * - which one in the set has the longest value attribute string length,
			 * - what the combined extents are
			 */
			Boolean allSameTypes = true;
			String timexType = null;
			Timex3 longestTimex = null;
			Integer combinedBegin = Integer.MAX_VALUE, combinedEnd = Integer.MIN_VALUE;
			ArrayList<Integer> tokenIds = new ArrayList<Integer>();
			for(Timex3 t : tSet) {
				// check whether the types are identical and either all DATE or TIME
				if(timexType == null) {
					timexType = t.getTimexType();
				} else {
					if(allSameTypes && !timexType.equals(t.getTimexType()) || !(timexType.equals("DATE") || timexType.equals("TIME"))) {
						allSameTypes = false;
					}
				}
				Logger.printDetail("Are these overlapping timexes of same type? => " + allSameTypes);
				
				// check timex value attribute string length
				if(longestTimex == null) {
					longestTimex = t;
				} else if(allSameTypes && t.getFoundByRule().indexOf("-BCADhint") != -1) {
					longestTimex = t;
				} else if(allSameTypes && t.getFoundByRule().indexOf("relative") == -1 && longestTimex.getFoundByRule().indexOf("relative") != -1) { 
					longestTimex = t;
				} else if(longestTimex.getTimexValue().length() == t.getTimexValue().length()) {
					if(t.getBegin() < longestTimex.getBegin())
						longestTimex = t;
				} else if(longestTimex.getTimexValue().length() < t.getTimexValue().length()) {
					longestTimex = t;
				}
				Logger.printDetail("Selected " + longestTimex.getTimexId() + ": " + longestTimex.getCoveredText() + 
						"[" + longestTimex.getTimexValue() + "] as the longest-valued timex.");
				
				// check combined beginning/end
				if(combinedBegin > t.getBegin())
					combinedBegin = t.getBegin();
				if(combinedEnd < t.getEnd())
					combinedEnd = t.getEnd();
				Logger.printDetail("Selected combined constraints: " + combinedBegin + ":" + combinedEnd);
				
				// disassemble and remember the token ids
				String[] tokenizedTokenIds = t.getAllTokIds().split("<-->");
				for(Integer i = 1; i < tokenizedTokenIds.length; i++) {
					if(!tokenIds.contains(Integer.parseInt(tokenizedTokenIds[i]))) {
						tokenIds.add(Integer.parseInt(tokenizedTokenIds[i]));
					}
				}
			}

			/* types are equal => merge constraints, use the longer, "more granular" value. 
			 * if types are not equal, just take the longest value.
			 */
			Collections.sort(tokenIds);
			newTimex = longestTimex;
			if(allSameTypes) {
				newTimex.setBegin(combinedBegin);
				newTimex.setEnd(combinedEnd);
				if(tokenIds.size() > 0)
					newTimex.setFirstTokId(tokenIds.get(0));
				String tokenIdText = "BEGIN";
				for(Integer tokenId : tokenIds) {
					tokenIdText += "<-->" + tokenId;
				}
				newTimex.setAllTokIds(tokenIdText);
			}
			
			// remove old overlaps.
			for(Timex3 t : tSet) {
				t.removeFromIndexes();
			}
			// add the single constructed/chosen timex to the indexes.
			newTimex.addToIndexes();
		}
	}
	
	
	/**
	 * Identify the part of speech (POS) of a MarchResult.
	 * @param tokBegin
	 * @param tokEnd
	 * @param s
	 * @param jcas
	 * @return
	 */
	public String getPosFromMatchResult(int tokBegin, int tokEnd, Sentence s, JCas jcas) {
		// get all tokens in sentence
		HashMap<Integer, Token> hmTokens = new HashMap<Integer, Token>();
		FSIterator iterTok = jcas.getAnnotationIndex(Token.type).subiterator(s);
		while (iterTok.hasNext()) {
			Token token = (Token) iterTok.next();
			hmTokens.put(token.getBegin(), token);
		}
		// get correct token
		String pos = "";
		if (hmTokens.containsKey(tokBegin)) {
			Token tokenToCheck = hmTokens.get(tokBegin);
			pos = tokenToCheck.getPos();
		}
		return pos;
	}

	
	/**
	 * Apply the extraction rules, normalization rules
	 * @param timexType
	 * @param hmPattern
	 * @param hmOffset
	 * @param hmNormalization
	 * @param hmQuant
	 * @param s
	 * @param jcas
	 */
	public void findTimexes(String timexType, 
							HashMap<Pattern, String> hmPattern,
							HashMap<String, String> hmOffset,
							HashMap<String, String> hmNormalization,
							HashMap<String, String> hmQuant,
							Sentence s,
							JCas jcas) {
		RuleManager rm = RuleManager.getInstance(language);
		HashMap<String, String> hmDatePosConstraint = rm.getHmDatePosConstraint();
		HashMap<String, String> hmDurationPosConstraint = rm.getHmDurationPosConstraint();
		HashMap<String, String> hmTimePosConstraint = rm.getHmTimePosConstraint();
		HashMap<String, String> hmSetPosConstraint = rm.getHmSetPosConstraint();
		
		// Iterator over the rules by sorted by the name of the rules
		// this is important since later, the timexId will be used to 
		// decide which of two expressions shall be removed if both
		// have the same offset
		for (Iterator<Pattern> i = Toolbox.sortByValue(hmPattern).iterator(); i.hasNext(); ) {
            Pattern p = (Pattern) i.next();

			for (MatchResult r : Toolbox.findMatches(p, s.getCoveredText())) {
				boolean infrontBehindOK = ContextAnalyzer.checkTokenBoundaries(r, s, jcas) // improved token boundary checking
									&& ContextAnalyzer.checkInfrontBehind(r, s);

				boolean posConstraintOK = true;
				// CHECK POS CONSTRAINTS
				if (timexType.equals("DATE")) {
					if (hmDatePosConstraint.containsKey(hmPattern.get(p))) {
						posConstraintOK = checkPosConstraint(s , hmDatePosConstraint.get(hmPattern.get(p)), r, jcas);
					}
				} else if (timexType.equals("DURATION")) {
					if (hmDurationPosConstraint.containsKey(hmPattern.get(p))) {
						posConstraintOK = checkPosConstraint(s , hmDurationPosConstraint.get(hmPattern.get(p)), r, jcas);
					}					
				} else if (timexType.equals("TIME")) {
					if (hmTimePosConstraint.containsKey(hmPattern.get(p))) {
						posConstraintOK = checkPosConstraint(s , hmTimePosConstraint.get(hmPattern.get(p)), r, jcas);
					}
				} else if (timexType.equals("SET")) {
					if (hmSetPosConstraint.containsKey(hmPattern.get(p))) {
						posConstraintOK = checkPosConstraint(s , hmSetPosConstraint.get(hmPattern.get(p)), r, jcas);
					}
				}
				
				if ((infrontBehindOK == true) && (posConstraintOK == true)) {
					
					// Offset of timex expression (in the checked sentence)
					int timexStart = r.start();
					int timexEnd   = r.end();
					
					// Normalization from Files:
					
					// Any offset parameter?
					if (hmOffset.containsKey(hmPattern.get(p))) {
						String offset    = hmOffset.get(hmPattern.get(p));
				
						// pattern for offset information
						Pattern paOffset = Pattern.compile("group\\(([0-9]+)\\)-group\\(([0-9]+)\\)");
						for (MatchResult mr : Toolbox.findMatches(paOffset,offset)) {
							int startOffset = Integer.parseInt(mr.group(1));
							int endOffset   = Integer.parseInt(mr.group(2));
							timexStart = r.start(startOffset);
							timexEnd   = r.end(endOffset); 
						}
					}
					
					// Normalization Parameter
					if (hmNormalization.containsKey(hmPattern.get(p))) {
						String[] attributes = new String[5];
						if (timexType.equals("DATE")) {
							attributes = getAttributesForTimexFromFile(hmPattern.get(p), rm.getHmDateNormalization(), rm.getHmDateQuant(), rm.getHmDateFreq(), rm.getHmDateMod(), rm.getHmDateEmptyValue(), r, jcas);
						} else if (timexType.equals("DURATION")) {
							attributes = getAttributesForTimexFromFile(hmPattern.get(p), rm.getHmDurationNormalization(), rm.getHmDurationQuant(), rm.getHmDurationFreq(), rm.getHmDurationMod(), rm.getHmDurationEmptyValue(), r, jcas);
						} else if (timexType.equals("TIME")) {
							attributes = getAttributesForTimexFromFile(hmPattern.get(p), rm.getHmTimeNormalization(), rm.getHmTimeQuant(), rm.getHmTimeFreq(), rm.getHmTimeMod(), rm.getHmTimeEmptyValue(), r, jcas);
						} else if (timexType.equals("SET")) {
							attributes = getAttributesForTimexFromFile(hmPattern.get(p), rm.getHmSetNormalization(), rm.getHmSetQuant(), rm.getHmSetFreq(), rm.getHmSetMod(), rm.getHmSetEmptyValue(), r, jcas);
						}
						addTimexAnnotation(timexType, timexStart + s.getBegin(), timexEnd + s.getBegin(), s, 
								attributes[0], attributes[1], attributes[2], attributes[3], attributes[4], "t" + timexID++, hmPattern.get(p), jcas);
					}
					else {
						Logger.printError("SOMETHING REALLY WRONG HERE: "+hmPattern.get(p));
					}
				}
			}
		}
	}
	
	
	/**
	 * Check whether the part of speech constraint defined in a rule is satisfied.
	 * @param s
	 * @param posConstraint
	 * @param m
	 * @param jcas
	 * @return
	 */
	public boolean checkPosConstraint(Sentence s, String posConstraint, MatchResult m, JCas jcas) {
		Pattern paConstraint = Pattern.compile("group\\(([0-9]+)\\):(.*?):");
		for (MatchResult mr : Toolbox.findMatches(paConstraint,posConstraint)) {
			int groupNumber = Integer.parseInt(mr.group(1));
			int tokenBegin = s.getBegin() + m.start(groupNumber);
			int tokenEnd   = s.getBegin() + m.end(groupNumber);
			String pos = mr.group(2);
			String pos_as_is = getPosFromMatchResult(tokenBegin, tokenEnd ,s, jcas);
			if (pos_as_is.matches(pos)) {
				Logger.printDetail("POS CONSTRAINT IS VALID: pos should be "+pos+" and is "+pos_as_is);
			} else {
				return false;
			}
		}
		return true;
	}
	
	
	public String applyRuleFunctions(String tonormalize, MatchResult m) {
		NormalizationManager norm = NormalizationManager.getInstance(language);
		
		String normalized = "";
		// pattern for normalization functions + group information
		// pattern for group information
		Pattern paNorm  = Pattern.compile("%([A-Za-z0-9]+?)\\(group\\(([0-9]+)\\)\\)");
		Pattern paGroup = Pattern.compile("group\\(([0-9]+)\\)");
		while ((tonormalize.contains("%")) || (tonormalize.contains("group"))) {
			// replace normalization functions
			for (MatchResult mr : Toolbox.findMatches(paNorm,tonormalize)) {
				Logger.printDetail("-----------------------------------");
				Logger.printDetail("DEBUGGING: tonormalize:"+tonormalize);
				Logger.printDetail("DEBUGGING: mr.group():"+mr.group());
				Logger.printDetail("DEBUGGING: mr.group(1):"+mr.group(1));
				Logger.printDetail("DEBUGGING: mr.group(2):"+mr.group(2));
				Logger.printDetail("DEBUGGING: m.group():"+m.group());
				Logger.printDetail("DEBUGGING: m.group("+Integer.parseInt(mr.group(2))+"):"+m.group(Integer.parseInt(mr.group(2))));
				Logger.printDetail("DEBUGGING: hmR...:"+norm.getFromHmAllNormalization(mr.group(1)).get(m.group(Integer.parseInt(mr.group(2)))));
				Logger.printDetail("-----------------------------------");
				
				if (! (m.group(Integer.parseInt(mr.group(2))) == null)) {
					String partToReplace = m.group(Integer.parseInt(mr.group(2))).replaceAll("[\n\\s]+", " ");
					if (!(norm.getFromHmAllNormalization(mr.group(1)).containsKey(partToReplace))) {
						Logger.printDetail("Maybe problem with normalization of the resource: "+mr.group(1));
						Logger.printDetail("Maybe problem with part to replace? "+partToReplace);
					}
					tonormalize = tonormalize.replace(mr.group(), norm.getFromHmAllNormalization(mr.group(1)).get(partToReplace));
				} else {
					Logger.printDetail("Empty part to normalize in "+mr.group(1));
					
					tonormalize = tonormalize.replace(mr.group(), "");
				}
			}
			// replace other groups
			for (MatchResult mr : Toolbox.findMatches(paGroup,tonormalize)) {
				Logger.printDetail("-----------------------------------");
				Logger.printDetail("DEBUGGING: tonormalize:"+tonormalize);
				Logger.printDetail("DEBUGGING: mr.group():"+mr.group());
				Logger.printDetail("DEBUGGING: mr.group(1):"+mr.group(1));
				Logger.printDetail("DEBUGGING: m.group():"+m.group());
				Logger.printDetail("DEBUGGING: m.group("+Integer.parseInt(mr.group(1))+"):"+m.group(Integer.parseInt(mr.group(1))));
				Logger.printDetail("-----------------------------------");
				
				tonormalize = tonormalize.replace(mr.group(), m.group(Integer.parseInt(mr.group(1))));
			}	
			// replace substrings
			Pattern paSubstring = Pattern.compile("%SUBSTRING%\\((.*?),([0-9]+),([0-9]+)\\)");
			for (MatchResult mr : Toolbox.findMatches(paSubstring,tonormalize)) {
				String substring = mr.group(1).substring(Integer.parseInt(mr.group(2)), Integer.parseInt(mr.group(3)));
				tonormalize = tonormalize.replace(mr.group(),substring);
			}
			if(language.getName().compareTo("arabic") != 0)
			{		
				// replace lowercase
				Pattern paLowercase = Pattern.compile("%LOWERCASE%\\((.*?)\\)");
				for (MatchResult mr : Toolbox.findMatches(paLowercase,tonormalize)) {
					String substring = mr.group(1).toLowerCase();
					tonormalize = tonormalize.replace(mr.group(),substring);
				}
			
				// replace uppercase
				Pattern paUppercase = Pattern.compile("%UPPERCASE%\\((.*?)\\)");
				for (MatchResult mr : Toolbox.findMatches(paUppercase,tonormalize)) {
					String substring = mr.group(1).toUpperCase();
					tonormalize = tonormalize.replace(mr.group(),substring);
				}
			}
			// replace sum, concatenation
			Pattern paSum = Pattern.compile("%SUM%\\((.*?),(.*?)\\)");
			for (MatchResult mr : Toolbox.findMatches(paSum,tonormalize)) {
				int newValue = Integer.parseInt(mr.group(1)) + Integer.parseInt(mr.group(2));
				tonormalize = tonormalize.replace(mr.group(), newValue+"");
			}
			// replace normalization function without group
			Pattern paNormNoGroup = Pattern.compile("%([A-Za-z0-9]+?)\\((.*?)\\)");
			for (MatchResult mr : Toolbox.findMatches(paNormNoGroup, tonormalize)) {
				tonormalize = tonormalize.replace(mr.group(),norm.getFromHmAllNormalization(mr.group(1)).get(mr.group(2)));
			}
			// replace Chinese with Arabic numerals
			Pattern paChineseNorm = Pattern.compile("%CHINESENUMBERS%\\((.*?)\\)");
			for (MatchResult mr : Toolbox.findMatches(paChineseNorm, tonormalize)) {
				RegexHashMap<String> chineseNumerals = new RegexHashMap<String>();
				chineseNumerals.put("[零０0]", "0");
				chineseNumerals.put("[一１1]", "1");
				chineseNumerals.put("[二２2]", "2");
				chineseNumerals.put("[三３3]", "3");
				chineseNumerals.put("[四４4]", "4");
				chineseNumerals.put("[五５5]", "5");
				chineseNumerals.put("[六６6]", "6");
				chineseNumerals.put("[七７7]", "7");
				chineseNumerals.put("[八８8]", "8");
				chineseNumerals.put("[九９9]", "9");
				String outString = "";
				for(Integer i = 0; i < mr.group(1).length(); i++) {
					String thisChar = mr.group(1).substring(i, i+1);
					if(chineseNumerals.containsKey(thisChar)){
						outString += chineseNumerals.get(thisChar);
					} else {
						System.out.println(chineseNumerals.entrySet());
						Logger.printError(component, "Found an error in the resources: " + mr.group(1) + " contains " +
								"a character that is not defined in the Chinese numerals map. Normalization may be mangled.");
						outString += thisChar;
					}
				}
				tonormalize = tonormalize.replace(mr.group(), outString);
			}
		}
		normalized = tonormalize;
		return normalized;
	}
	
	
	public String[] getAttributesForTimexFromFile(String rule,
													HashMap<String, String> hmNormalization,
													HashMap<String, String> hmQuant,
													HashMap<String, String> hmFreq,
													HashMap<String, String> hmMod,
													HashMap<String, String> hmEmptyValue,
													MatchResult m, 
													JCas jcas) {
		String[] attributes = new String[5];
		String value = "";
		String quant = "";
		String freq = "";
		String mod = "";
		String emptyValue = "";
		
		// Normalize Value
		String value_normalization_pattern = hmNormalization.get(rule);
		value = applyRuleFunctions(value_normalization_pattern, m);
		
		// get quant
		if (hmQuant.containsKey(rule)) {
			String quant_normalization_pattern = hmQuant.get(rule);
			quant = applyRuleFunctions(quant_normalization_pattern, m);
		}

		// get freq
		if (hmFreq.containsKey(rule)) {
			String freq_normalization_pattern = hmFreq.get(rule);
			freq = applyRuleFunctions(freq_normalization_pattern, m);
		}
		
		// get mod
		if (hmMod.containsKey(rule)) {
			String mod_normalization_pattern = hmMod.get(rule);
			mod = applyRuleFunctions(mod_normalization_pattern, m);
		}
		
		// get emptyValue
		if (hmEmptyValue.containsKey(rule)) {
			String emptyValue_normalization_pattern = hmEmptyValue.get(rule);
			emptyValue = applyRuleFunctions(emptyValue_normalization_pattern, m);
			emptyValue = correctDurationValue(emptyValue);
		}
		// For example "PT24H" -> "P1D"
		if (group_gran)
			value = correctDurationValue(value);

		attributes[0] = value;
		attributes[1] = quant;
		attributes[2] = freq;
		attributes[3] = mod;
		attributes[4] = emptyValue;
		
		return attributes;
	}
	

	/**
	 * Durations of a finer granularity are mapped to a coarser one if possible, e.g., "PT24H" -> "P1D".
	 * One may add several further corrections.
	 * @param value 
     * @return
     */
	public String correctDurationValue(String value) {
		if (value.matches("PT[0-9]+H")){
			for (MatchResult mr : Toolbox.findMatches(Pattern.compile("PT([0-9]+)H"), value)){
				try {
					int hours = Integer.parseInt(mr.group(1));
					if ((hours % 24) == 0){
						int days = hours / 24;
						value = "P"+days+"D";
					}
				} catch(NumberFormatException e) {
					Logger.printDetail(component, "Couldn't do granularity conversion for " + value);
				}
			}
		} else if (value.matches("PT[0-9]+M")){
			for (MatchResult mr : Toolbox.findMatches(Pattern.compile("PT([0-9]+)M"), value)){
				try {
					int minutes = Integer.parseInt(mr.group(1));
					if ((minutes % 60) == 0){
						int hours = minutes / 60;
						value = "PT"+hours+"H";
					}
				} catch(NumberFormatException e) {
					Logger.printDetail(component, "Couldn't do granularity conversion for " + value);
				}
			}
		} else if (value.matches("P[0-9]+M")){
			for (MatchResult mr : Toolbox.findMatches(Pattern.compile("P([0-9]+)M"), value)){
				try {
					int months = Integer.parseInt(mr.group(1));
					if ((months % 12) == 0){
						int years = months / 12;
						value = "P"+years+"Y";
					}
				} catch(NumberFormatException e) {
					Logger.printDetail(component, "Couldn't do granularity conversion for " + value);
				}
			}
		}
		return value;
	}
		
}