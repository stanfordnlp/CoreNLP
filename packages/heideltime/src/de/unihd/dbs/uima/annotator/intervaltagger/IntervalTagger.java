package de.unihd.dbs.uima.annotator.intervaltagger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.heideltime.resources.RePatternManager;
import de.unihd.dbs.uima.annotator.heideltime.utilities.Logger;
import de.unihd.dbs.uima.annotator.heideltime.utilities.Toolbox;
import de.unihd.dbs.uima.types.heideltime.IntervalCandidateSentence;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Timex3Interval;

/**
 * IntervalTagger is a UIMA annotator that discovers and tags intervals in documents.
 * @author Manuel Dewald, Julian Zell
 *
 */
public class IntervalTagger extends JCasAnnotator_ImplBase {

	// TOOL NAME (may be used as componentId)
	private Class<?> component = this.getClass();
	
	// descriptor parameter names
	public static String PARAM_LANGUAGE = "language";
	public static String PARAM_INTERVALS = "annotate_intervals";
	public static String PARAM_INTERVAL_CANDIDATES = "annotate_interval_candidates";
	// descriptor configuration
	private Language language = null;
	private Boolean find_intervals = true;
	private Boolean find_interval_candidates = true;
	
	private HashMap<Pattern, String> hmIntervalPattern = new HashMap<Pattern, String>();
	private HashMap<String, String> hmIntervalNormalization = new HashMap<String, String>();
	
	/**
	 * initialization: read configuration parameters and resources
	 */
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		
		language = Language.getLanguageFromString((String) aContext.getConfigParameterValue(PARAM_LANGUAGE));
		
		find_intervals = (Boolean) aContext.getConfigParameterValue(PARAM_INTERVALS);
		find_interval_candidates = (Boolean) aContext.getConfigParameterValue(PARAM_INTERVAL_CANDIDATES);
		
		readResources(readResourcesFromDirectory("rules"));
	}
	
	/**
	 * called by the pipeline to process the document
	 */
	public void process(JCas jcas) throws AnalysisEngineProcessException {
		if(find_intervals) {
			findIntervals(jcas);
			findSentenceIntervals(jcas);
		}
	}
	
	/**
	 * reads in heideltime's resource files.
	 * @throws ResourceInitializationException
	 */
	private void readResources(HashMap<String, String> hmResourcesRules) throws ResourceInitializationException {
		Pattern paReadRules = Pattern.compile("RULENAME=\"(.*?)\",EXTRACTION=\"(.*?)\",NORM_VALUE=\"(.*?)\"(.*)");
		// read normalization data
		try {
			for (String resource : hmResourcesRules.keySet()) {
				BufferedReader br = new BufferedReader(new InputStreamReader (this.getClass().getClassLoader().getResourceAsStream(hmResourcesRules.get(resource))));
				Logger.printDetail(component, "Adding rule resource: "+resource);
				for ( String line; (line=br.readLine()) != null; ) {
					if (!(line.startsWith("//"))) {
						boolean correctLine = false;
						if (!(line.equals(""))) {
							Logger.printDetail("DEBUGGING: reading rules..."+ line);
							// check each line for the name, extraction, and normalization part
							for (MatchResult r : Toolbox.findMatches(paReadRules, line)) {
								correctLine = true;
								String rule_name          = r.group(1);
								String rule_extraction    = r.group(2);
								String rule_normalization = r.group(3);
								
								////////////////////////////////////////////////////////////////////
								// RULE EXTRACTION PARTS ARE TRANSLATED INTO REGULAR EXPRESSSIONS //
								////////////////////////////////////////////////////////////////////
								// create pattern for rule extraction part
								Pattern paVariable = Pattern.compile("%(re[a-zA-Z0-9]*)");
								RePatternManager rpm = RePatternManager.getInstance(language);
								for (MatchResult mr : Toolbox.findMatches(paVariable,rule_extraction)) {
									Logger.printDetail("DEBUGGING: replacing patterns..."+ mr.group());
									if (!(rpm.containsKey(mr.group(1)))) {
										Logger.printError("Error creating rule:"+rule_name);
										Logger.printError("The following pattern used in this rule does not exist, does it? %"+mr.group(1));
										System.exit(-1);
									}
									rule_extraction = rule_extraction.replaceAll("%"+mr.group(1), rpm.get(mr.group(1)));
								}
								rule_extraction = rule_extraction.replaceAll(" ", "[\\\\s]+");
								Pattern pattern = null;
								try{
									pattern = Pattern.compile(rule_extraction);
								}
								catch (java.util.regex.PatternSyntaxException e) {
									Logger.printError("Compiling rules resulted in errors.");
									Logger.printError("Problematic rule is "+rule_name);
									Logger.printError("Cannot compile pattern: "+rule_extraction);
									e.printStackTrace();
									System.exit(-1);
								}
								
								/////////////////////////////////////////////////
								// READ INTERVAL RULES AND MAKE THEM AVAILABLE //
								/////////////////////////////////////////////////
								if(resource.equals("intervalrules")){
									hmIntervalPattern.put(pattern,rule_name);
									hmIntervalNormalization.put(rule_name, rule_normalization);
								}
							}
						}
						
						///////////////////////////////////////////
						// CHECK FOR PROBLEMS WHEN READING RULES //
						///////////////////////////////////////////
						if ((correctLine == false) && (!(line.matches("")))) {
							Logger.printError(component, "Cannot read the following line of rule resource "+resource);
							Logger.printError(component, "Line: "+line);
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new ResourceInitializationException();
		}
	}

	/**
	 * Reads resource files of the type resourceType from the "used_resources.txt" file and returns a HashMap
	 * containing information to access these resources.
	 * @return HashMap containing filename/path tuples
	 */
	protected HashMap<String, String> readResourcesFromDirectory(String resourceType) {

		HashMap<String, String> hmResources = new HashMap<String, String>();
		
		BufferedReader br = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream("used_resources.txt")));
		try {
			for (String line; (line=br.readLine()) != null; ) {
				String pathDelim = System.getProperty("file.separator");
				Pattern paResource = Pattern.compile(".(?:\\"+pathDelim+"|/)?(\\"+pathDelim+"|/)"+language.getResourceFolder()+"(?:\\"+pathDelim+"|/)"+resourceType+"(?:\\"+pathDelim+"|/)"+"resources_"+resourceType+"_"+"(.*?)\\.txt");
				for (MatchResult ro : Toolbox.findMatches(paResource, line)){
					pathDelim = ro.group(1);
					String foundResource  = ro.group(2);
					String pathToResource = language.getResourceFolder()+ro.group(1)+resourceType+ro.group(1)+"resources_"+resourceType+"_"+foundResource+".txt";
					hmResources.put(foundResource, pathToResource);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			Logger.printError(component, "Failed to read a resource from used_resources.txt.");
			System.exit(-1);
		}
		return hmResources;
	}
	
	/**
	 * Extract Timex3Intervals, delimited by two Timex3Intervals in a sentence.
	 * finsInterval needs to be run with jcas before.
	 * @param jcas
	 * @author Manuel Dewald
	 */
	private void findSentenceIntervals(JCas jcas){
		HashSet<Timex3Interval> timexesToRemove = new HashSet<Timex3Interval>();
		
		FSIterator iterSentence = jcas.getAnnotationIndex(Sentence.type).iterator();
		while (iterSentence.hasNext()) {
			Sentence s=(Sentence)iterSentence.next();
			String sString=s.getCoveredText();
			FSIterator iterInter = jcas.getAnnotationIndex(Timex3Interval.type).subiterator(s);
			int count=0;
			List<Timex3Interval> txes=new ArrayList<Timex3Interval>();
			List<Timex3Interval> sentenceTxes=new ArrayList<Timex3Interval>();
			
			while(iterInter.hasNext()){
				Timex3Interval t=(Timex3Interval)iterInter.next();
				sString=sString.replace(t.getCoveredText(), "<TX3_"+count+">");
				count++;
				txes.add(t);
			}

			if(count>0){

				if (find_interval_candidates){
					IntervalCandidateSentence sI=new IntervalCandidateSentence(jcas);
					sI.setBegin(s.getBegin());
					sI.setEnd(s.getEnd());
					sI.addToIndexes();
				}
				for(Pattern p: hmIntervalPattern.keySet()){
					
					String name=hmIntervalPattern.get(p);
					List<MatchResult>results=(List<MatchResult>)Toolbox.findMatches(p,sString);
					if(results.size()>0){
						//Interval in Sentence s found by Pattern p!
						for(MatchResult r: results){
							Pattern pNorm=Pattern.compile("group\\(([1-9]+)\\)-group\\(([1-9]+)\\)");
							String norm=hmIntervalNormalization.get(name);
							
							Matcher mNorm=pNorm.matcher(norm);
							if(!mNorm.matches()){
								System.err.println("Problem with the Norm in rule "+name);
							}
							Timex3Interval startTx=null,endTx=null;
							try{
								int startId=Integer.parseInt(mNorm.group(1));
								int endId=Integer.parseInt(mNorm.group(2));
								
								startTx=txes.get(Integer.parseInt(r.group(startId)));
								endTx=txes.get(Integer.parseInt(r.group(endId)));
							}catch(Exception e){
								e.printStackTrace();
								return;
							}
							Timex3Interval annotation=new Timex3Interval(jcas);
							annotation.setBegin(startTx.getBegin()>endTx.getBegin()?endTx.getBegin():startTx.getBegin());
							annotation.setEnd(startTx.getEnd()>endTx.getEnd()?startTx.getEnd():endTx.getEnd());
							
							//Does the interval already exist,
							//found by another pattern?
							boolean duplicate=false;
							for(Timex3Interval tx:sentenceTxes){
								if(tx.getBegin()==annotation.getBegin() &&
										tx.getEnd()==annotation.getEnd()){
									duplicate=true;
									break;
								}
							}
							
							if(!duplicate){
								annotation.setTimexValueEB(startTx.getTimexValueEB());
								annotation.setTimexValueLB(startTx.getTimexValueEE());
								annotation.setTimexValueEE(endTx.getTimexValueEB());
								annotation.setTimexValueLE(endTx.getTimexValueEE());
								annotation.setTimexType(startTx.getTimexType());
								annotation.setFoundByRule(name);
								
								
								// create emptyvalue value
								String emptyValue = createEmptyValue(startTx, endTx, jcas);
								annotation.setEmptyValue(emptyValue);
								annotation.setBeginTimex(startTx.getBeginTimex());
								annotation.setEndTimex(endTx.getEndTimex());
								
								try {
									sentenceTxes.add(annotation);
								} catch(NumberFormatException e) {
									Logger.printError(component, "Couldn't do emptyValue calculation on accont of a faulty normalization in " 
											+ annotation.getTimexValueEB() + " or " + annotation.getTimexValueEE());
								}
								
								// prepare tx3intervals to remove
								timexesToRemove.add(startTx);
								timexesToRemove.add(endTx);
								
								annotation.addToIndexes();
//								System.out.println(emptyValue);
							}
						}
					}
				}
			}
		}
		
		for(Timex3Interval txi : timexesToRemove) {
			txi.removeFromIndexes();
		}
	}
	
	private String createEmptyValue(Timex3Interval startTx, Timex3Interval endTx, JCas jcas) throws NumberFormatException {
		String dateStr = "", timeStr = "";

		// find granularity for start/end timex values
		Pattern p = Pattern.compile("(\\d{1,4})?-?(\\d{2})?-?(\\d{2})?(T)?(\\d{2})?:?(\\d{2})?:?(\\d{2})?");
		//							 1            2          3        4   5          6          7
		Matcher mStart = p.matcher(startTx.getTimexValue());
		Matcher mEnd = p.matcher(endTx.getTimexValue());
		Integer granularityStart = -1;
		Integer granularityEnd = -2;
		Integer granularity = -1;
		
		// find the highest granularity in each timex
		if(mStart.find() && mEnd.find()) {
			for(Integer i = 1; i <= mStart.groupCount(); i++) {
				if(mStart.group(i) != null)
					granularityStart = i;
				if(mEnd.group(i) != null)
					granularityEnd = i;
			}
		}
		
		// if granularities aren't the same, we can't do anything here.
		if(granularityEnd != granularityStart) {
			return "";
		} else { // otherwise, set maximum granularity
			granularity = granularityStart;
		}

		// check all the different granularities, starting with seconds, calculate differences, add carries
		Integer myYears = 0,
				myMonths = 0,
				myDays = 0,
				myHours = 0,
				myMinutes = 0,
				mySeconds = 0;
		
		if(granularity >= 7 && mStart.group(7) != null && mEnd.group(7) != null) {
			mySeconds = Integer.parseInt(mEnd.group(7)) - Integer.parseInt(mStart.group(7));
			if(mySeconds < 0) {
				mySeconds += 60;
				myMinutes -= 1;
			}
		}
		
		if(granularity >= 6 && mStart.group(6) != null && mEnd.group(6) != null) {
			myMinutes += Integer.parseInt(mEnd.group(6)) - Integer.parseInt(mStart.group(6));
			if(myMinutes < 0) {
				myMinutes += 60;
				myHours -= 1;
			}
		}
		
		if(granularity >= 5 && mStart.group(5) != null && mEnd.group(5) != null) {
			myHours += Integer.parseInt(mEnd.group(5)) - Integer.parseInt(mStart.group(5));
			if(myHours < 0) {
				myMinutes += 24;
				myDays -= 1;
			}
		}
		
		if(granularity >= 3 && mStart.group(3) != null && mEnd.group(3) != null) {
			myDays += Integer.parseInt(mEnd.group(3)) - Integer.parseInt(mStart.group(3));
			if(myDays < 0) {
				Calendar cal = Calendar.getInstance();
				cal.set(Calendar.YEAR, Integer.parseInt(mStart.group(1)));
				cal.set(Calendar.MONTH, Integer.parseInt(mStart.group(2)));

				myMonths = myMonths - 1;
				myDays += cal.getActualMaximum(Calendar.DAY_OF_MONTH);
			}
		}
		
		if(granularity >= 2 && mStart.group(2) != null && mEnd.group(2) != null) {
			myMonths += Integer.parseInt(mEnd.group(2)) - Integer.parseInt(mStart.group(2));
			if(myMonths < 0) {
				myMonths += Integer.parseInt(mStart.group(2));
				myYears -= 1;
			}
		}
		
		String myYearUnit = "";
		if(granularity >= 1 && mStart.group(1) != null && mEnd.group(1) != null) {
			String year1str = mStart.group(1), year2str = mEnd.group(1);
			
			// trim year strings to same length (NNNN year, NNN decade, NN century)
			while(year2str.length() > year1str.length())
				year2str = year2str.substring(0, year2str.length()-1);
			while(year1str.length() > year2str.length())
				year1str = year1str.substring(0, year1str.length()-1);
			
			// check for year unit
			switch(year1str.length()) {
				case 2:
					myYearUnit = "CE";
					myYears = Integer.parseInt(year2str) - Integer.parseInt(year1str);
					break;
				case 3:
					myYearUnit = "DE";
					myYears = Integer.parseInt(year2str) - Integer.parseInt(year1str);
					break;
				case 4:
					myYearUnit = "Y";
					myYears += Integer.parseInt(year2str) - Integer.parseInt(year1str);
					break;
				default:
					break;
			}
		}

		// assemble strings
		dateStr += (myYears > 0 ? myYears + myYearUnit : "");
		dateStr += (myMonths > 0 ? myMonths + "M" : "");
		dateStr += (myDays > 0 ? myDays + "D" : "");

		timeStr += (myHours > 0 ? myHours + "H" : "");
		timeStr += (myMinutes > 0 ? myMinutes + "M" : "");
		timeStr += (mySeconds > 0 ? mySeconds + "S" : "");

		// output
		return "P" + dateStr + (timeStr.length() > 0 ? "T" + timeStr : "");
	}

	/**
	 * Build Timex3Interval-Annotations out of Timex3Annotations in jcas.
	 * @author Manuel Dewald
	 * @param jcas
	 */
	private void findIntervals(JCas jcas) {
		ArrayList<Timex3Interval> newAnnotations = new ArrayList<Timex3Interval>();
		
		FSIterator iterTimex3 = jcas.getAnnotationIndex(Timex3.type).iterator();
		while (iterTimex3.hasNext()) {
			Timex3Interval annotation=new Timex3Interval(jcas);
			Timex3 timex3 = (Timex3) iterTimex3.next();
			
			//DATE Pattern
			Pattern pDate = Pattern.compile("(\\d+)(-(\\d+))?(-(\\d+))?(T(\\d+))?(:(\\d+))?(:(\\d+))?");
			Pattern pCentury = Pattern.compile("(\\d\\d)");
			Pattern pDecate = Pattern.compile("(\\d\\d\\d)");
			Pattern pQuarter = Pattern.compile("(\\d+)-Q([1-4])");
			Pattern pHalf = Pattern.compile("(\\d+)-H([1-2])");
			Pattern pSeason = Pattern.compile("(\\d+)-(SP|SU|FA|WI)");
			Pattern pWeek = Pattern.compile("(\\d+)-W(\\d+)");
			Pattern pWeekend = Pattern.compile("(\\d+)-W(\\d+)-WE");
			Pattern pTimeOfDay = Pattern.compile("(\\d+)-(\\d+)-(\\d+)T(AF|DT|MI|MO|EV|NI)");
			
			Matcher mDate   = pDate.matcher(timex3.getTimexValue());
			Matcher mCentury= pCentury.matcher(timex3.getTimexValue());
			Matcher mDecade = pDecate.matcher(timex3.getTimexValue());
			Matcher mQuarter= pQuarter.matcher(timex3.getTimexValue());
			Matcher mHalf   = pHalf.matcher(timex3.getTimexValue());
			Matcher mSeason = pSeason.matcher(timex3.getTimexValue());
			Matcher mWeek   = pWeek.matcher(timex3.getTimexValue());
			Matcher mWeekend= pWeekend.matcher(timex3.getTimexValue());
			Matcher mTimeOfDay= pTimeOfDay.matcher(timex3.getTimexValue());
			
			boolean matchesDate=mDate.matches();
			boolean matchesCentury=mCentury.matches();
			boolean matchesDecade=mDecade.matches();
			boolean matchesQuarter=mQuarter.matches();
			boolean matchesHalf=mHalf.matches();
			boolean matchesSeason=mSeason.matches();
			boolean matchesWeek=mWeek.matches();
			boolean matchesWeekend=mWeekend.matches();
			boolean matchesTimeOfDay=mTimeOfDay.matches();
			
			String beginYear, endYear;
			String beginMonth, endMonth;
			String beginDay, endDay;
			String beginHour, endHour;
			String beginMinute, endMinute;
			String beginSecond, endSecond;

			beginYear=endYear="UNDEF";
			beginMonth="01";
			endMonth="12";
			beginDay="01";
			endDay="31";
			beginHour="00";
			endHour="23";
			beginMinute="00";
			endMinute="59";
			beginSecond="00";
			endSecond="59";
			
			if(matchesDate){
				
				//Get Year(1)
				beginYear=endYear=mDate.group(1);
				
				//Get Month(3)
				if(mDate.group(3)!=null){
					beginMonth=endMonth=mDate.group(3);
					
					//Get Day(5)
					if(mDate.group(5)==null){
						Calendar c=Calendar.getInstance();
						c.set(Integer.parseInt(beginYear), Integer.parseInt(beginMonth)-1, 1);
						endDay=""+c.getActualMaximum(Calendar.DAY_OF_MONTH);
						beginDay="01";
					}else{
						beginDay=endDay=mDate.group(5);
						
						//Get Hour(7)
						if(mDate.group(7)!=null){
							beginHour=endHour=mDate.group(7);

							//Get Minute(9)
							if(mDate.group(9)!=null){
								beginMinute=endMinute=mDate.group(9);

								//Get Second(11)
								if(mDate.group(11)!=null){
									beginSecond=endSecond=mDate.group(11);
								}
							}
						}
						
					}
				}
				
			}else if(matchesCentury){
				beginYear=mCentury.group(1)+"00";
				endYear=mCentury.group(1)+"99";
			}else if(matchesDecade){
				beginYear=mDecade.group(1)+"0";
				endYear=mDecade.group(1)+"9";
			}else if(matchesQuarter){
				beginYear=endYear=mQuarter.group(1);
				int beginMonthI=3*(Integer.parseInt(mQuarter.group(2))-1)+1;
				beginMonth=""+beginMonthI;
				endMonth=""+(beginMonthI+2);
				Calendar c=Calendar.getInstance();
				c.set(Integer.parseInt(beginYear), Integer.parseInt(endMonth)-1, 1);
				endDay=""+c.getActualMaximum(Calendar.DAY_OF_MONTH);
			}else if(matchesHalf){
				beginYear=endYear=mHalf.group(1);
				int beginMonthI=6*(Integer.parseInt(mHalf.group(2))-1)+1;
				beginMonth=""+beginMonthI;
				endMonth=""+(beginMonthI+5);
				Calendar c=Calendar.getInstance();
				c.set(Integer.parseInt(beginYear), Integer.parseInt(endMonth)-1, 1);
				endDay=""+c.getActualMaximum(Calendar.DAY_OF_MONTH);
			}else if(matchesSeason){
				beginYear=mSeason.group(1);
				endYear=beginYear;
				if(mSeason.group(2).equals("SP")){
					beginMonth="03";
					beginDay="21";
					endMonth="06";
					endDay="20";
				}else if(mSeason.group(2).equals("SU")){
					beginMonth="06";
					beginDay="21";
					endMonth="09";
					endDay="22";
				}else if(mSeason.group(2).equals("FA")){
					beginMonth="09";
					beginDay="23";
					endMonth="12";
					endDay="21";
				}else if(mSeason.group(2).equals("WI")){
					endYear=""+(Integer.parseInt(beginYear)+1);
					beginMonth="12";
					beginDay="22";
					endMonth="03";
					endDay="20";
				}
			}else if(matchesWeek){
				beginYear=endYear=mWeek.group(1);
				Calendar c=Calendar.getInstance();
				c.setFirstDayOfWeek(Calendar.MONDAY);
				c.set(Calendar.YEAR,Integer.parseInt(beginYear));
				c.set(Calendar.WEEK_OF_YEAR, Integer.parseInt(mWeek.group(2)));
				c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
				beginDay=""+c.get(Calendar.DAY_OF_MONTH);
				beginMonth=""+(c.get(Calendar.MONTH)+1);
				c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
				endDay=""+(c.get(Calendar.DAY_OF_MONTH));
				endMonth=""+(c.get(Calendar.MONTH)+1);
			}else if(matchesWeekend){
				beginYear=endYear=mWeekend.group(1);
				Calendar c=Calendar.getInstance();
				c.setFirstDayOfWeek(Calendar.MONDAY);
				c.set(Calendar.YEAR,Integer.parseInt(beginYear));
				c.set(Calendar.WEEK_OF_YEAR, Integer.parseInt(mWeekend.group(2)));
				c.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
				beginDay=""+c.get(Calendar.DAY_OF_MONTH);
				beginMonth=""+(c.get(Calendar.MONTH)+1);
				c.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
				endDay=""+(c.get(Calendar.DAY_OF_MONTH));
				endMonth=""+(c.get(Calendar.MONTH)+1);
			}else if(matchesTimeOfDay){
				beginYear=endYear=mTimeOfDay.group(1);
				beginMonth=endMonth=mTimeOfDay.group(2);
				beginDay=endDay=mTimeOfDay.group(3);
			}
			if(!beginYear.equals("UNDEF") && !endYear.equals("UNDEF")){
				annotation.setTimexValueEB(beginYear+"-"+beginMonth+"-"+beginDay+"T"+beginHour+":"+beginMinute+":"+beginSecond);
				annotation.setTimexValueEE(endYear+"-"+endMonth+"-"+endDay+"T"+endHour+":"+endMinute+":"+endSecond);
				annotation.setTimexValueLB(beginYear+"-"+beginMonth+"-"+beginDay+"T"+beginHour+":"+beginMinute+":"+beginSecond);
				annotation.setTimexValueLE(endYear+"-"+endMonth+"-"+endDay+"T"+endHour+":"+endMinute+":"+endSecond);
		
				//Copy Values from the Timex3 Annotation
				annotation.setTimexFreq(timex3.getTimexFreq());
				annotation.setTimexId(timex3.getTimexId());
				annotation.setTimexInstance(timex3.getTimexInstance());
				annotation.setTimexMod(timex3.getTimexMod());
				annotation.setTimexQuant(timex3.getTimexMod());
				annotation.setTimexType(timex3.getTimexType());
				annotation.setTimexValue(timex3.getTimexValue());
				annotation.setSentId(timex3.getSentId());
				annotation.setBegin(timex3.getBegin());
				annotation.setFoundByRule(timex3.getFoundByRule());
				annotation.setEnd(timex3.getEnd());
				annotation.setAllTokIds(timex3.getAllTokIds());
				annotation.setFilename(timex3.getFilename());
				annotation.setBeginTimex(timex3.getTimexId());
				annotation.setEndTimex(timex3.getTimexId());
				
				// remember this one for addition to indexes later
				newAnnotations.add(annotation);
			}
		}
		// add to indexes
		for(Timex3Interval t3i : newAnnotations)
			t3i.addToIndexes();
	}

}
