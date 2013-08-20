package edu.stanford.nlp.parser.tools;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Performs equivalence classing of punctuation per PTB guidelines. Many of the multilingual
 * treebanks mark all punctuation with a single POS tag, which is bad for parsing.
 * <p>
 * PTB punctuation POS tag set (12 tags):
 * 
 * 37. #  Pound sign 
 * 38. $  Dollar sign 
 * 39. .  Sentence-final punctuation 
 * 40. ,  Comma 
 * 41. :  Colon, semi-colon 
 * 42. (  Left bracket character 
 * 43. )  Right bracket character 
 * 44. "  Straight double quote 
 * 45. `  Left open single quote 
 * 46. "  Left open double quote 
 * 47. '  Right close single quote 
 * 48. "  Right close double quote
 * <p>
 * See http://www.ldc.upenn.edu/Catalog/docs/LDC95T7/cl93.html
 * 
 * @author Spence Green
 *
 */
public class PunctEquivalenceClasser {

  private static final String[] eolClassRaw = {".","?","!"};
  private static final Set<String> sfClass = new HashSet<String>(Arrays.asList(eolClassRaw));
  
  private static final String[] colonClassRaw = {":",";","-","_"};
  private static final Set<String> colonClass = new HashSet<String>(Arrays.asList(colonClassRaw));
  
  private static final String[] commaClassRaw = {",","ر"};
  private static final Set<String> commaClass = new HashSet<String>(Arrays.asList(commaClassRaw));
  
  private static final String[] currencyClassRaw = {"$","#","="};
  private static final Set<String> currencyClass = new HashSet<String>(Arrays.asList(currencyClassRaw));
  
  private static final Pattern pEllipsis = Pattern.compile("\\.\\.+");
  
  private static final String[] slashClassRaw = {"/","\\"};
  private static final Set<String> slashClass = new HashSet<String>(Arrays.asList(slashClassRaw));
     
  private static final String[] lBracketClassRaw = {"-LRB-","(","[","<"};
  private static final Set<String> lBracketClass = new HashSet<String>(Arrays.asList(lBracketClassRaw));

  private static final String[] rBracketClassRaw = {"-RRB-",")","]",">"};
  private static final Set<String> rBracketClass = new HashSet<String>(Arrays.asList(rBracketClassRaw));
  
  private static final String[] quoteClassRaw = {"\"","``","''","'","`"};
  private static final Set<String> quoteClass = new HashSet<String>(Arrays.asList(quoteClassRaw));
  
  /**
   * Return the equivalence class of the argument. If the argument is not contained in
   * and equivalence class, then an empty string is returned.
   * 
   * @param punc
   * @return The class name if found. Otherwise, an empty string.
   */
  public static String getPunctClass(String punc) {
    if(punc.equals("%") || punc.equals("-PLUS-"))//-PLUS- is an escape for "+" in the ATB
      return "perc";
    else if(punc.startsWith("*"))
      return "bullet";
    else if(sfClass.contains(punc))
      return "sf";
    else if(colonClass.contains(punc) || pEllipsis.matcher(punc).matches())
      return "colon";
    else if(commaClass.contains(punc))
      return "comma";
    else if(currencyClass.contains(punc))
      return "curr";
    else if(slashClass.contains(punc))
      return "slash";
    else if(lBracketClass.contains(punc))
      return "lrb";
    else if(rBracketClass.contains(punc))
      return "rrb";
    else if(quoteClass.contains(punc))
      return "quote";
    
    return "";
  }
}
