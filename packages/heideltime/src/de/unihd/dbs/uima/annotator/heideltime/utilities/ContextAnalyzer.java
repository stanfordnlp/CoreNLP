package de.unihd.dbs.uima.annotator.heideltime.utilities;

import java.util.List;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.uima.annotator.heideltime.resources.Language;
import de.unihd.dbs.uima.annotator.heideltime.resources.NormalizationManager;
import de.unihd.dbs.uima.annotator.heideltime.resources.RePatternManager;
import de.unihd.dbs.uima.types.heideltime.Sentence;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Token;
/**
 * 
 * This class contains methods that work with the dependence of a subject with its
 * surrounding data; namely via the jcas element or a subset list.
 * @author jannik stroetgen
 *
 */
public class ContextAnalyzer {
	/**
	 * The value of the x of the last mentioned Timex is calculated.
	 * @param linearDates list of previous linear dates
	 * @param i index for the previous date entry
	 * @param x type to search for
	 * @return last mentioned entry
	 */
	public static String getLastMentionedX(List<Timex3> linearDates, int i, String x, Language language) {
		NormalizationManager nm = NormalizationManager.getInstance(language);
		
		// Timex for which to get the last mentioned x (i.e., Timex i)
		Timex3 t_i = linearDates.get(i);
		
		String xValue = "";
		int j = i - 1;
		while (j >= 0) {
			Timex3 timex = linearDates.get(j);
			// check that the two timexes to compare do not have the same offset:
				if (!(t_i.getBegin() == timex.getBegin())) {
				
					String value = timex.getTimexValue();
					if (!(value.contains("funcDate"))){
						if (x.equals("century")) {
							if (value.matches("^[0-9][0-9].*")) {
								xValue = value.substring(0,2);
								break;
							}
							else if (value.matches("^BC[0-9][0-9].*")){
								xValue = value.substring(0,4);
								break;
							}
							else {
								j--;
							}
						}
						else if (x.equals("decade")) {
							if (value.matches("^[0-9][0-9][0-9].*")) {
								xValue = value.substring(0,3);
								break;
							}
							else if (value.matches("^BC[0-9][0-9][0-9].*")){
								xValue = value.substring(0,5);
								break;
							}
							else {
								j--;
							}
						}
						else if (x.equals("year")) {
							if (value.matches("^[0-9][0-9][0-9][0-9].*")) {
								xValue = value.substring(0,4);
								break;
							}
							else if (value.matches("^BC[0-9][0-9][0-9][0-9].*")){
								xValue = value.substring(0,6);
								break;
							}
							else {
								j--;
							}
						}
						else if (x.equals("dateYear")) {
							if (value.matches("^[0-9][0-9][0-9][0-9].*")) {
								xValue = value;
								break;
							}
							else if (value.matches("^BC[0-9][0-9][0-9][0-9].*")){
								xValue = value;
								break;
							}
							else {
								j--;
							}
						}
						else if (x.equals("month")) {
							if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9].*")) {
								xValue = value.substring(0,7);
								break;
							}
							else if (value.matches("^BC[0-9][0-9][0-9][0-9]-[0-9][0-9].*")){
								xValue = value.substring(0,9);
								break;
							}
							else {
								j--;
							}
						}
						else if (x.equals("month-with-details")) {
							if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9].*")) {
								xValue = value;
								break;
							}
//							else if (value.matches("^BC[0-9][0-9][0-9][0-9]-[0-9][0-9].*")) {
//								xValue = value;
//								break;
//							}
							else {
								j--;
							}
						}
						else if (x.equals("day")) {
							if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].*")) {
								xValue = value.substring(0,10);
								break;
							}
//							else if (value.matches("^BC[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].*")) {
//								xValue = value.substring(0,12);
//								break;
//							}
							else {
								j--;
							}
						}
						else if (x.equals("week")) {
							if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9].*")) {
								for (MatchResult r : Toolbox.findMatches(Pattern.compile("^(([0-9][0-9][0-9][0-9])-[0-9][0-9]-[0-9][0-9]).*"), value)) {
									xValue = r.group(2)+"-W"+DateCalculator.getWeekOfDate(r.group(1));
									break;
								}
								break;
							}
							else if (value.matches("^[0-9][0-9][0-9][0-9]-W[0-9][0-9].*")) {
								for (MatchResult r : Toolbox.findMatches(Pattern.compile("^([0-9][0-9][0-9][0-9]-W[0-9][0-9]).*"), value)) {
									xValue = r.group(1);
									break;
								}
								break;
							}
							// TODO check what to do for BC times
							else {
								j--;
							}
						}
						else if (x.equals("quarter")) {
							if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9].*")) {
								String month   = value.substring(5,7);
								String quarter = nm.getFromNormMonthInQuarter(month);
								if(quarter == null) {
									quarter = "1";
								}	
								xValue = value.substring(0,4)+"-Q"+quarter;
								break;
							}
							else if (value.matches("^[0-9][0-9][0-9][0-9]-Q[1234].*")) {
								xValue = value.substring(0,7);
								break;
							}
							// TODO check what to do for BC times
							else {
								j--;
							}
						}
						else if (x.equals("dateQuarter")) {
							if (value.matches("^[0-9][0-9][0-9][0-9]-Q[1234].*")) {
								xValue = value.substring(0,7);
								break;
							}
							// TODO check what to do for BC times
							else {
								j--;
							}
						}
						else if (x.equals("season")) {
							if (value.matches("^[0-9][0-9][0-9][0-9]-[0-9][0-9].*")) {
								String month   = value.substring(5,7);
								String season = nm.getFromNormMonthInSeason(month);
								xValue = value.substring(0,4)+"-"+season;
								break;
							}
//							else if (value.matches("^BC[0-9][0-9][0-9][0-9]-[0-9][0-9].*")) {
//								String month   = value.substring(7,9);
//								String season = nm.getFromNormMonthInSeason(month);
//								xValue = value.substring(0,6)+"-"+season;
//								break;
//							}
							else if (value.matches("^[0-9][0-9][0-9][0-9]-(SP|SU|FA|WI).*")) {
								xValue = value.substring(0,7);
								break;
							}
//							else if (value.matches("^BC[0-9][0-9][0-9][0-9]-(SP|SU|FA|WI).*")) {
//								xValue = value.substring(0,9);
//								break;
//							}
							else {
								j--;
							}
						}	
					} else {
						j--;
					}
				} else {
					j--;
				}
		}
		return xValue;
	}
	
	/**
	 * Get the last tense used in the sentence
	 * 
	 * @param timex timex construct to discover tense data for
	 * @return string that contains the tense
	 */
	public static String getClosestTense(Timex3 timex, JCas jcas, Language language) {
		RePatternManager rpm = RePatternManager.getInstance(language);
		
		String lastTense = "";
		String nextTense = "";
		
		int tokenCounter = 0;
		int lastid = 0;
		int nextid = 0;
		int tid    = 0;

		// Get the sentence
		FSIterator iterSentence = jcas.getAnnotationIndex(Sentence.type).iterator();
		Sentence s = new Sentence(jcas);
		while (iterSentence.hasNext()) {
			s = (Sentence) iterSentence.next();
			if ((s.getBegin() < timex.getBegin())
					&& (s.getEnd() > timex.getEnd())) {
				break;
			}
		}

		// Get the tokens
		TreeMap<Integer, Token> tmToken = new TreeMap<Integer, Token>();
		FSIterator iterToken = jcas.getAnnotationIndex(Token.type).subiterator(s);
		while (iterToken.hasNext()) {
			Token token = (Token) iterToken.next();
			tmToken.put(token.getEnd(), token);
		}
		
		// Get the last VERB token
		for (Integer tokEnd : tmToken.keySet()) {
			tokenCounter++;
			if (tokEnd < timex.getBegin()) {
				Token token = tmToken.get(tokEnd);
				
				Logger.printDetail("GET LAST TENSE: string:"+token.getCoveredText()+" pos:"+token.getPos());
				Logger.printDetail("hmAllRePattern.containsKey(tensePos4PresentFuture):"+rpm.get("tensePos4PresentFuture"));
				Logger.printDetail("hmAllRePattern.containsKey(tensePos4Future):"+rpm.get("tensePos4Future"));
				Logger.printDetail("hmAllRePattern.containsKey(tensePos4Past):"+rpm.get("tensePos4Past"));
				Logger.printDetail("CHECK TOKEN:"+token.getPos());
				
				if (token.getPos() == null) {
					
				}
				else if ((rpm.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(rpm.get("tensePos4PresentFuture")))) {
					lastTense = "PRESENTFUTURE";
					lastid = tokenCounter; 
				}
				else if ((rpm.containsKey("tensePos4Past")) && (token.getPos().matches(rpm.get("tensePos4Past")))) {
					lastTense = "PAST";
					lastid = tokenCounter;
				}
				else if ((rpm.containsKey("tensePos4Future")) && (token.getPos().matches(rpm.get("tensePos4Future")))) {
					if (token.getCoveredText().matches(rpm.get("tenseWord4Future"))) {
						lastTense = "FUTURE";
						lastid = tokenCounter;
					}
				}
			}
			else {
				if (tid == 0) {
					tid = tokenCounter;
				}
			}
		}
		tokenCounter = 0;
		for (Integer tokEnd : tmToken.keySet()) {
			tokenCounter++;
			if (nextTense.equals("")) {
				if (tokEnd > timex.getEnd()) {
					Token token = tmToken.get(tokEnd);
					
					Logger.printDetail("GET NEXT TENSE: string:"+token.getCoveredText()+" pos:"+token.getPos());
					Logger.printDetail("hmAllRePattern.containsKey(tensePos4PresentFuture):"+rpm.get("tensePos4PresentFuture"));
					Logger.printDetail("hmAllRePattern.containsKey(tensePos4Future):"+rpm.get("tensePos4Future"));
					Logger.printDetail("hmAllRePattern.containsKey(tensePos4Past):"+rpm.get("tensePos4Past"));
					Logger.printDetail("CHECK TOKEN:"+token.getPos());
					
					if (token.getPos() == null) {
						
					}
					else if ((rpm.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(rpm.get("tensePos4PresentFuture")))) {
						nextTense = "PRESENTFUTURE";
						nextid = tokenCounter;
					}
					else if ((rpm.containsKey("tensePos4Past")) && (token.getPos().matches(rpm.get("tensePos4Past")))) {
						nextTense = "PAST";
						nextid = tokenCounter;
					}
					else if ((rpm.containsKey("tensePos4Future")) && (token.getPos().matches(rpm.get("tensePos4Future")))) {
						if (token.getCoveredText().matches(rpm.get("tenseWord4Future"))) {
							nextTense = "FUTURE";
							nextid = tokenCounter;
						}
					}
				}
			}
		}
		if (lastTense.equals("")) {
			Logger.printDetail("TENSE: "+nextTense);
			return nextTense;
		}
		else if (nextTense.equals("")) {
			Logger.printDetail("TENSE: "+lastTense);
			return lastTense;
		}
		else {
			// If there is tense before and after the timex token, 
			// return the closer one:
			if ((tid - lastid) > (nextid - tid)) {
				Logger.printDetail("TENSE: "+nextTense);
				return nextTense;
			}
			else {
				Logger.printDetail("TENSE: "+lastTense);
				return lastTense;	
			}	
		}
	}
	
	
	/**
	 * Get the last tense used in the sentence
	 * 
	 * @param timex timex construct to discover tense data for
	 * @return string that contains the tense
	 */
	public static String getLastTense(Timex3 timex, JCas jcas, Language language) {
		RePatternManager rpm = RePatternManager.getInstance(language);
		
		String lastTense = "";

		// Get the sentence
		FSIterator iterSentence = jcas.getAnnotationIndex(Sentence.type).iterator();
		Sentence s = new Sentence(jcas);
		while (iterSentence.hasNext()) {
			s = (Sentence) iterSentence.next();
			if ((s.getBegin() < timex.getBegin())
					&& (s.getEnd() > timex.getEnd())) {
				break;
			}
		}

		// Get the tokens
		TreeMap<Integer, Token> tmToken = new TreeMap<Integer, Token>();
		FSIterator iterToken = jcas.getAnnotationIndex(Token.type).subiterator(s);
		while (iterToken.hasNext()) {
			Token token = (Token) iterToken.next();
			tmToken.put(token.getEnd(), token);
		}

		// Get the last VERB token
		for (Integer tokEnd : tmToken.keySet()) {
			if (tokEnd < timex.getBegin()) {
				Token token = tmToken.get(tokEnd);
				
				Logger.printDetail("GET LAST TENSE: string:"+token.getCoveredText()+" pos:"+token.getPos());
				Logger.printDetail("hmAllRePattern.containsKey(tensePos4PresentFuture):"+rpm.get("tensePos4PresentFuture"));
				Logger.printDetail("hmAllRePattern.containsKey(tensePos4Future):"+rpm.get("tensePos4Future"));
				Logger.printDetail("hmAllRePattern.containsKey(tensePos4Past):"+rpm.get("tensePos4Past"));
				Logger.printDetail("CHECK TOKEN:"+token.getPos());
				
				if (token.getPos() == null) {
					
				}
				else if ((rpm.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(rpm.get("tensePos4PresentFuture")))) {
					lastTense = "PRESENTFUTURE";
					Logger.printDetail("this tense:"+lastTense);
				}
				else if ((rpm.containsKey("tensePos4Past")) && (token.getPos().matches(rpm.get("tensePos4Past")))) {
					lastTense = "PAST";
					Logger.printDetail("this tense:"+lastTense);
				}
				else if ((rpm.containsKey("tensePos4Future")) && (token.getPos().matches(rpm.get("tensePos4Future")))) {
					if (token.getCoveredText().matches(rpm.get("tenseWord4Future"))) {
						lastTense = "FUTURE";
						Logger.printDetail("this tense:"+lastTense);
					}
				}
				if (token.getCoveredText().equals("since")) {
					lastTense = "PAST";
					Logger.printDetail("this tense:"+lastTense);
				}
				if (token.getCoveredText().equals("depuis")) { // French		
					lastTense = "PAST";
					Logger.printDetail("this tense:"+lastTense);
				}
			}
			if (lastTense.equals("")) {
				if (tokEnd > timex.getEnd()) {
					Token token = tmToken.get(tokEnd);
					
					Logger.printDetail("GET NEXT TENSE: string:"+token.getCoveredText()+" pos:"+token.getPos());
					Logger.printDetail("hmAllRePattern.containsKey(tensePos4PresentFuture):"+rpm.get("tensePos4PresentFuture"));
					Logger.printDetail("hmAllRePattern.containsKey(tensePos4Future):"+rpm.get("tensePos4Future"));
					Logger.printDetail("hmAllRePattern.containsKey(tensePos4Past):"+rpm.get("tensePos4Past"));
					Logger.printDetail("CHECK TOKEN:"+token.getPos());
					
					if (token.getPos() == null) {
						
					}
					else if ((rpm.containsKey("tensePos4PresentFuture")) && (token.getPos().matches(rpm.get("tensePos4PresentFuture")))) {
						lastTense = "PRESENTFUTURE";
						Logger.printDetail("this tense:"+lastTense);
					}
					else if ((rpm.containsKey("tensePos4Past")) && (token.getPos().matches(rpm.get("tensePos4Past")))) {
						lastTense = "PAST";
						Logger.printDetail("this tense:"+lastTense);
					}
					else if ((rpm.containsKey("tensePos4Future")) && (token.getPos().matches(rpm.get("tensePos4Future")))) {
						if (token.getCoveredText().matches(rpm.get("tenseWord4Future"))) {
							lastTense = "FUTURE";
							Logger.printDetail("this tense:"+lastTense);
						}
					}
				}
			}
		}
		// check for double POS Constraints (not included in the rule language, yet) TODO
		// VHZ VNN and VHZ VNN and VHP VNN and VBP VVN
		String prevPos = "";
		String longTense = "";
		if (lastTense.equals("PRESENTFUTURE")) {
			for (Integer tokEnd : tmToken.keySet()) {
				if (tokEnd < timex.getBegin()) {
					Token token = tmToken.get(tokEnd);
					if ((prevPos.equals("VHZ")) || (prevPos.equals("VBZ")) || (prevPos.equals("VHP")) || (prevPos.equals("VBP"))
							|| (prevPos.equals("VER:pres"))) {
						if (token.getPos().equals("VVN") || token.getPos().equals("VER:pper")) {
							if ((!(token.getCoveredText().equals("expected"))) && (!(token.getCoveredText().equals("scheduled")))) {
								lastTense = "PAST";
								longTense = "PAST";
								Logger.printDetail("this tense:"+lastTense);
							}
						}
					}
					prevPos = token.getPos();
				}
				if (longTense.equals("")) {
					if (tokEnd > timex.getEnd()) {
						Token token = tmToken.get(tokEnd);
						if ((prevPos.equals("VHZ")) || (prevPos.equals("VBZ")) || (prevPos.equals("VHP")) || (prevPos.equals("VBP"))
								|| (prevPos.equals("VER:pres"))) {
							if (token.getPos().equals("VVN") || token.getPos().equals("VER:pper")) {
								if ((!(token.getCoveredText().equals("expected"))) && (!(token.getCoveredText().equals("scheduled")))) {
									lastTense = "PAST";
									longTense = "PAST";
									Logger.printDetail("this tense:"+lastTense);
								}
							}
						}
						prevPos = token.getPos();
					}
				}
			}
		}
		// French: VER:pres VER:pper
		if (lastTense.equals("PAST")) {
			for (Integer tokEnd : tmToken.keySet()) { 
				if (tokEnd < timex.getBegin()) {
					Token token = tmToken.get(tokEnd);
					if ((prevPos.equals("VER:pres")) && (token.getPos().equals("VER:pper"))) {
							if (((token.getCoveredText().matches("^prévue?s?$"))) || ((token.getCoveredText().equals("^envisagée?s?$")))) {
								lastTense = "FUTURE";
								longTense = "FUTURE";
								Logger.printDetail("this tense:"+lastTense);
							}
					}
					prevPos = token.getPos();
				}
				if (longTense.equals("")) {
					if (tokEnd > timex.getEnd()) {
						Token token = tmToken.get(tokEnd);
						if ((prevPos.equals("VER:pres")) && (token.getPos().equals("VER:pper"))) {
							if (((token.getCoveredText().matches("^prévue?s?$"))) || ((token.getCoveredText().equals("^envisagée?s?$")))) {
								lastTense = "FUTURE";
								longTense = "FUTURE";
								Logger.printDetail("this tense:"+lastTense);
							}
						}
						prevPos = token.getPos();
					}
				}
			}
		}
		Logger.printDetail("TENSE: "+lastTense);
		
		return lastTense;
	}
	
	/**
	 * Check token boundaries of expressions.
	 * @param r MatchResult 
	 * @param s Respective sentence
	 * @return whether or not the MatchResult is a clean one
	 */
	public static Boolean checkInfrontBehind(MatchResult r, Sentence s) {
		Boolean ok = true;
		
		// get rid of expressions such as "1999" in 53453.1999
		if (r.start() > 1) {
			if ((s.getCoveredText().substring(r.start() - 2, r.start()).matches("\\d\\."))){
				ok = false;
			}
		}
		
		// get rid of expressions if there is a character or symbol ($+) directly in front of the expression
		if (r.start() > 0) {
			if (((s.getCoveredText().substring(r.start() - 1, r.start()).matches("[\\w\\$\\+]"))) &&
					(!(s.getCoveredText().substring(r.start() - 1, r.start()).matches("\\(")))){
				ok = false;
			}
		}
		
		if (r.end() < s.getCoveredText().length()) {
			if ((s.getCoveredText().substring(r.end(), r.end() + 1).matches("[°\\w]")) &&
					(!(s.getCoveredText().substring(r.end(), r.end() + 1).matches("\\)")))){
				ok = false;
			}
			if (r.end() + 1 < s.getCoveredText().length()) {
				if (s.getCoveredText().substring(r.end(), r.end() + 2).matches(
						"[\\.,]\\d")) {
					ok = false;
				}
			}
		}
		return ok;
	}
	
	/**
	* Check token boundaries using token information
	* @param r MatchResult
	* @param s respective Sentence
	* @param jcas current CAS object
	* @return whether or not the MatchResult is a clean one
	*/
	public static Boolean checkTokenBoundaries(MatchResult r, Sentence s, JCas jcas){
		Boolean beginOK = false;
		Boolean endOK = false;
	
		// whole expression is marked as a sentence
		if ((r.end() - r.start()) == (s.getEnd() -s.getBegin())){
			return true;
		}
		
		// Only check Token boundaries if no white-spaces in front of and behind the match-result
		if ((r.start() > 0) 
				&& ((s.getCoveredText().subSequence(r.start()-1, r.start()).equals(" ")))
				&& ((r.end() < s.getCoveredText().length()) && ((s.getCoveredText().subSequence(r.end(), r.end()+1).equals(" "))))) {
			return true;
		}
	
		// other token boundaries than white-spaces
		else {
			FSIterator iterToken = jcas.getAnnotationIndex(Token.type).subiterator(s);
			while (iterToken.hasNext()) {
				Token t = (Token) iterToken.next();
			
				// Check begin
				if ((r.start() + s.getBegin()) == t.getBegin()){
					beginOK = true;
				}
				// Tokenizer does not split number from some symbols (".", "/", "-", "–"),
				// e.g., "...12 August-24 Augsut..."
				else if ((r.start() > 0)
						&& ((s.getCoveredText().subSequence(r.start()-1, r.start()).equals("."))
						|| (s.getCoveredText().subSequence(r.start()-1, r.start()).equals("/")) 
						|| (s.getCoveredText().subSequence(r.start()-1, r.start()).equals("–"))
						|| (s.getCoveredText().subSequence(r.start()-1, r.start()).equals("-")))) {
					beginOK = true;
				}
			
				// Check end
				if ((r.end() + s.getBegin()) == t.getEnd()) {
					endOK = true;
				}
				// Tokenizer does not split number from some symbols (".", "/", "-", "–"),
				// e.g., "... in 1990. New Sentence ..."
				else if ((r.end() < s.getCoveredText().length()) 
						&& ((s.getCoveredText().subSequence(r.end(), r.end()+1).equals("."))
								|| (s.getCoveredText().subSequence(r.end(), r.end()+1).equals("/"))
								|| (s.getCoveredText().subSequence(r.end(), r.end()+1).equals("–")) 
								|| (s.getCoveredText().subSequence(r.end(), r.end()+1).equals("-")))) {
					endOK = true;
				}
			
				if (beginOK && endOK)
					return true;
			}
		}
		return false;
	} 
}
