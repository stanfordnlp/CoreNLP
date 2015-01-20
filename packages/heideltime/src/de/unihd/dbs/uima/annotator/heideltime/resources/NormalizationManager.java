package de.unihd.dbs.uima.annotator.heideltime.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import de.unihd.dbs.uima.annotator.heideltime.utilities.*;
/**
 * 
 * This class fills the role of a manager of all the Normalization resources.
 * It reads the data from a file system and fills up a bunch of HashMaps
 * with their information.
 * @author jannik stroetgen
 *
 */
public class NormalizationManager extends GenericResourceManager {
	protected static HashMap<Language, NormalizationManager> instances = new HashMap<Language, NormalizationManager>();
	// PATTERNS TO READ RESOURCES "RULES" AND "NORMALIZATION"
	private Pattern paReadNormalizations = Pattern.compile("\"(.*?)\",\"(.*?)\"");

	// STORE PATTERNS AND NORMALIZATIONS
	private HashMap<String, RegexHashMap<String>> hmAllNormalization;
	
	// ACCESS TO SOME NORMALIZATION MAPPINGS (set internally)
	private HashMap<String, String> normDayInWeek;
	private HashMap<String, String> normNumber;
	private HashMap<String, String> normMonthName;
	private HashMap<String, String> normMonthInSeason; 
	private HashMap<String, String> normMonthInQuarter;

	/**
	 * Constructor calls the parent constructor that sets language/resource parameters,
	 * initializes basic and collects resource normalization patterns.
	 * @param language
	 */
	private NormalizationManager(String language) {
		// calls the Generic constructor with normalization-parameter
		super("normalization", language);
		
		// initialize the data structures
		hmAllNormalization = new HashMap<String, RegexHashMap<String>>();
		
		normNumber = new HashMap<String, String>();
		normDayInWeek = new HashMap<String, String>();
		normMonthName = new HashMap<String, String>();
		normMonthInSeason = new HashMap<String, String>();
		normMonthInQuarter = new HashMap<String, String>();
		
		// GLOBAL NORMALIZATION INFORMATION
		readGlobalNormalizationInformation();
		
		////////////////////////////////////////////////////////////
		// READ NORMALIZATION RESOURCES FROM FILES AND STORE THEM //
		////////////////////////////////////////////////////////////
		HashMap<String, String> hmResourcesNormalization = readResourcesFromDirectory();
		
		for (String which : hmResourcesNormalization.keySet()) {
			hmAllNormalization.put(which, new RegexHashMap<String>());
		}
		
		readNormalizationResources(hmResourcesNormalization);
	}

	/**
	 * singleton producer.
	 * @return singleton instance of NormalizationManager
	 */
	public static NormalizationManager getInstance(Language language) {
		if(!instances.containsKey(language)) {
			NormalizationManager nm = new NormalizationManager(language.getResourceFolder());
			instances.put(language, nm);
		}
		
		return instances.get(language);
	}
	
	/**
	 * Read the resources (of any language) from resource files and 
	 * fill the HashMaps used for normalization tasks.
	 * @param hmResourcesNormalization normalization patterns to be interpreted
	 */
	public void readNormalizationResources(HashMap<String, String> hmResourcesNormalization) {
		try {
			for (String resource : hmResourcesNormalization.keySet()) {
				Logger.printDetail(component, "Adding normalization resource: "+resource);
				// create a buffered reader for every normalization resource file
				BufferedReader in = new BufferedReader(new InputStreamReader
						(this.getClass().getClassLoader().getResourceAsStream(hmResourcesNormalization.get(resource)),"UTF-8"));
				for ( String line; (line=in.readLine()) != null; ) {
					if (line.startsWith("//")) continue; // ignore comments
					
					// check each line for the normalization format (defined in paReadNormalizations)
					boolean correctLine = false;
					for (MatchResult r : Toolbox.findMatches(paReadNormalizations, line)) {
						correctLine = true;
						String resource_word   = r.group(1);
						String normalized_word = r.group(2);
						for (String which : hmAllNormalization.keySet()) {
							if (resource.equals(which)) {
								hmAllNormalization.get(which).put(resource_word,normalized_word);
							}
						}
						if ((correctLine == false) && (!(line.matches("")))) {
							Logger.printError("["+component+"] Cannot read one of the lines of normalization resource "+resource);
							Logger.printError("["+component+"] Line: "+line);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	
	/**
	 * sets a couple of rudimentary normalization parameters
	 */
	private void readGlobalNormalizationInformation() {

		// MONTH IN QUARTER
		normMonthInQuarter.put("01","1");
		normMonthInQuarter.put("02","1");
		normMonthInQuarter.put("03","1");
		normMonthInQuarter.put("04","2");
		normMonthInQuarter.put("05","2");
		normMonthInQuarter.put("06","2");
		normMonthInQuarter.put("07","3");
		normMonthInQuarter.put("08","3");
		normMonthInQuarter.put("09","3");
		normMonthInQuarter.put("10","4");
		normMonthInQuarter.put("11","4");
		normMonthInQuarter.put("12","4");
		
		// MONTH IN SEASON
		normMonthInSeason.put("", "");
		normMonthInSeason.put("01","WI");
		normMonthInSeason.put("02","WI");
		normMonthInSeason.put("03","SP");
		normMonthInSeason.put("04","SP");
		normMonthInSeason.put("05","SP");
		normMonthInSeason.put("06","SU");
		normMonthInSeason.put("07","SU");
		normMonthInSeason.put("08","SU");
		normMonthInSeason.put("09","FA");
		normMonthInSeason.put("10","FA");
		normMonthInSeason.put("11","FA");
		normMonthInSeason.put("12","WI");
		
		// DAY IN WEEK
		normDayInWeek.put("sunday","1");
		normDayInWeek.put("monday","2");
		normDayInWeek.put("tuesday","3");
		normDayInWeek.put("wednesday","4");
		normDayInWeek.put("thursday","5");
		normDayInWeek.put("friday","6");
		normDayInWeek.put("saturday","7");
		normDayInWeek.put("Sunday","1");
		normDayInWeek.put("Monday","2");
		normDayInWeek.put("Tuesday","3");
		normDayInWeek.put("Wednesday","4");
		normDayInWeek.put("Thursday","5");
		normDayInWeek.put("Friday","6");
		normDayInWeek.put("Saturday","7");
//		normDayInWeek.put("sunday","7");
//		normDayInWeek.put("monday","1");
//		normDayInWeek.put("tuesday","2");
//		normDayInWeek.put("wednesday","3");
//		normDayInWeek.put("thursday","4");
//		normDayInWeek.put("friday","5");
//		normDayInWeek.put("saturday","6");
//		normDayInWeek.put("Sunday","7");
//		normDayInWeek.put("Monday","1");
//		normDayInWeek.put("Tuesday","2");
//		normDayInWeek.put("Wednesday","3");
//		normDayInWeek.put("Thursday","4");
//		normDayInWeek.put("Friday","5");
//		normDayInWeek.put("Saturday","6");
		
		
		// NORM MINUTE
		normNumber.put("0","00");
		normNumber.put("00","00");
		normNumber.put("1","01");
		normNumber.put("01","01");
		normNumber.put("2","02");
		normNumber.put("02","02");
		normNumber.put("3","03");
		normNumber.put("03","03");
		normNumber.put("4","04");
		normNumber.put("04","04");
		normNumber.put("5","05");
		normNumber.put("05","05");
		normNumber.put("6","06");
		normNumber.put("06","06");
		normNumber.put("7","07");
		normNumber.put("07","07");
		normNumber.put("8","08");
		normNumber.put("08","08");
		normNumber.put("9","09");
		normNumber.put("09","09");
		normNumber.put("10","10");
		normNumber.put("11","11");
		normNumber.put("12","12");
		normNumber.put("13","13");
		normNumber.put("14","14");
		normNumber.put("15","15");
		normNumber.put("16","16");
		normNumber.put("17","17");
		normNumber.put("18","18");
		normNumber.put("19","19");
		normNumber.put("20","20");
		normNumber.put("21","21");
		normNumber.put("22","22");
		normNumber.put("23","23");
		normNumber.put("24","24");
		normNumber.put("25","25");
		normNumber.put("26","26");
		normNumber.put("27","27");
		normNumber.put("28","28");
		normNumber.put("29","29");
		normNumber.put("30","30");
		normNumber.put("31","31");
		normNumber.put("32","32");
		normNumber.put("33","33");
		normNumber.put("34","34");
		normNumber.put("35","35");
		normNumber.put("36","36");
		normNumber.put("37","37");
		normNumber.put("38","38");
		normNumber.put("39","39");
		normNumber.put("40","40");
		normNumber.put("41","41");
		normNumber.put("42","42");
		normNumber.put("43","43");
		normNumber.put("44","44");
		normNumber.put("45","45");
		normNumber.put("46","46");
		normNumber.put("47","47");
		normNumber.put("48","48");
		normNumber.put("49","49");
		normNumber.put("50","50");
		normNumber.put("51","51");
		normNumber.put("52","52");
		normNumber.put("53","53");
		normNumber.put("54","54");
		normNumber.put("55","55");
		normNumber.put("56","56");
		normNumber.put("57","57");
		normNumber.put("58","58");
		normNumber.put("59","59");
		normNumber.put("60","60");
		
		// NORM MONTH
		normMonthName.put("january","01");
		normMonthName.put("february","02");
		normMonthName.put("march","03");
		normMonthName.put("april","04");
		normMonthName.put("may","05");
		normMonthName.put("june","06");
		normMonthName.put("july","07");
		normMonthName.put("august","08");
		normMonthName.put("september","09");
		normMonthName.put("october","10");
		normMonthName.put("november","11");
		normMonthName.put("december","12");
	}
	/*
	 * a bunch of getter methods to facilitate access to the data structures
	 */
	public final RegexHashMap<String> getFromHmAllNormalization(String key) {
		return hmAllNormalization.get(key);
	}

	public final String getFromNormNumber(String key) {
		return normNumber.get(key);
	}

	public final String getFromNormDayInWeek(String key) {
		return normDayInWeek.get(key);
	}

	public final String getFromNormMonthName(String key) {
		return normMonthName.get(key);
	}

	public final String getFromNormMonthInSeason(String key) {
		return normMonthInSeason.get(key);
	}

	public final String getFromNormMonthInQuarter(String key) {
		return normMonthInQuarter.get(key);
	}
}
