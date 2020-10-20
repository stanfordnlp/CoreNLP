package edu.stanford.nlp.util;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A simple class with a variety of acronym matching utilities.
 *
 * You're probably looking for the method {@link AcronymMatcher#isAcronym(String, List)}.
 *
 * @author Gabor Angeli
 */
public class AcronymMatcher {

  private static final Pattern discardPattern = Pattern.compile("[-._]");

  /** A set of words that should be considered stopwords for the acronym matcher */
  private static final Set<String> STOPWORDS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("'d");
    add("'ll");
    add("'re");
    add("'s");
    add("'t");
    add("'ve");
    add("n't");
    add("a");
    add("about");
    add("above");
    add("after");
    add("again");
    add("against");
    add("all");
    add("am");
    add("an");
    add("and");
    add("any");
    add("are");
    add("as");
    add("at");
    add("be");
    add("because");
    add("been");
    add("before");
    add("being");
    add("below");
    add("between");
    add("both");
    add("but");
    add("by");
    add("cannot");
    add("could");
    add("did");
    add("do");
    add("does");
    add("doing");
    add("down");
    add("during");
    add("each");
    add("few");
    add("for");
    add("from");
    add("further");
    add("had");
    add("has");
    add("have");
    add("having");
    add("he");
    add("her");
    add("here");
    add("hers");
    add("herself");
    add("him");
    add("himself");
    add("his");
    add("how");
    add("i");
    add("if");
    add("in");
    add("into");
    add("is");
    add("it");
    add("its");
    add("itself");
    add("me");
    add("more");
    add("most");
    add("my");
    add("myself");
    add("no");
    add("nor");
    add("not");
    add("of");
    add("off");
    add("on");
    add("once");
    add("only");
    add("or");
    add("other");
    add("ought");
    add("our");
    add("ours");
    add("ourselves");
    add("out");
    add("over");
    add("own");
    add("same");
    add("she");
    add("should");
    add("so");
    add("some");
    add("such");
    add("than");
    add("their");
    add("theirs");
    add("them");
    add("themselves");
    add("the");
    add("then");
    add("there");
    add("these");
    add("they");
    add("this");
    add("those");
    add("through");
    add("to");
    add("too");
    add("under");
    add("until");
    add("up");
    add("very");
    add("was");
    add("we");
    add("were");
    add("what");
    add("when");
    add("where");
    add("which");
    add("while");
    add("who");
    add("whom");
    add("why");
    add("with");
    add("would");
    add("you");
    add("your");
    add("yours");
    add("yourself");
    add("yourselves");

    add("de");
    add("del");
    add("di");
    add("y");

    add("corporation");
    add("corp");
    add("corp.");
    add("co");
    add("llc");
    add("inc");
    add("inc.");
    add("ltd");
    add("ltd.");
    add("llp");
    add("llp.");
    add("plc");
    add("plc.");

    add("&");
    add(",");
    add("-");
  }});

  private AcronymMatcher() {} // static methods


  private static List<String> getTokenStrs(List<CoreLabel> tokens) {
    List<String> mainTokenStrs = new ArrayList<>(tokens.size());
    for (CoreLabel token:tokens) {
      String text = token.get(CoreAnnotations.TextAnnotation.class);
      mainTokenStrs.add(text);
    }
    return mainTokenStrs;
  }

  private static List<String> getMainTokenStrs(List<CoreLabel> tokens) {
    List<String> mainTokenStrs = new ArrayList<>(tokens.size());
    for (CoreLabel token:tokens) {
      String text = token.get(CoreAnnotations.TextAnnotation.class);
      if (!text.isEmpty() && ( text.length() >= 4 || Character.isUpperCase(text.charAt(0))) ) {
        mainTokenStrs.add(text);
      }
    }
    return mainTokenStrs;
  }

  private static List<String> getMainTokenStrs(String[] tokens) {
    List<String> mainTokenStrs = new ArrayList<>(tokens.length);
    for (String text:tokens) {
      if ( !text.isEmpty() && ( text.length() >= 4 || Character.isUpperCase(text.charAt(0)) ) ) {
        mainTokenStrs.add(text);
      }
    }
    return mainTokenStrs;
  }

  public static List<String> getMainStrs(List<String> tokens) {
    List<String> mainTokenStrs = new ArrayList<>(tokens.size());
    mainTokenStrs.addAll(tokens.stream().filter(text -> !text.isEmpty() && (text.length() >= 4 || Character.isUpperCase(text.charAt(0)))).collect(Collectors.toList()));
    return mainTokenStrs;
  }

  public static boolean isAcronym(String str, String[] tokens) {
    return isAcronymImpl(str, Arrays.asList(tokens));
  }

  // Public static utility methods

  public static boolean isAcronymImpl(String str, List<String> tokens) {
    // Remove some words from the candidate acronym
    str = discardPattern.matcher(str).replaceAll("");
    // Remove stopwords if we need to
    if (str.length() != tokens.size()) {
      tokens = tokens.stream().filter(x -> !STOPWORDS.contains(x.toLowerCase())).collect(Collectors.toList());
    }
    // Run the matcher
    if (str.length() == tokens.size()) {
      for (int i = 0; i < str.length(); i++) {
        char ch = Character.toUpperCase(str.charAt(i));
        if ( !tokens.get(i).isEmpty() &&
            Character.toUpperCase(tokens.get(i).charAt(0)) != ch ) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  public static boolean isAcronym(String str, List<?> tokens) {
    List<String> strs = new ArrayList<>(tokens.size());
    for (Object tok : tokens) {
      if (tok instanceof String) {
        strs.add(tok.toString());
      } else if (tok instanceof CoreMap) {
        strs.add(((CoreMap) tok).get(CoreAnnotations.TextAnnotation.class));
      } else {
        strs.add(tok.toString());
      }
    }
    return isAcronymImpl(str, strs);
  }

  /**
   * Returns true if either chunk1 or chunk2 is acronym of the other.
   *
   * @return true if either chunk1 or chunk2 is acronym of the other
   */
  public static boolean isAcronym(CoreMap chunk1, CoreMap chunk2) {
    String text1 = chunk1.get(CoreAnnotations.TextAnnotation.class);
    String text2 = chunk2.get(CoreAnnotations.TextAnnotation.class);
    if (text1.length() <= 1 || text2.length() <= 1) { return false; }
    List<String> tokenStrs1 = getTokenStrs(chunk1.get(CoreAnnotations.TokensAnnotation.class));
    List<String> tokenStrs2 = getTokenStrs(chunk2.get(CoreAnnotations.TokensAnnotation.class));
    boolean isAcro = isAcronymImpl(text1, tokenStrs2) || isAcronymImpl(text2, tokenStrs1);
    if (!isAcro) {
      tokenStrs1 = getMainTokenStrs(chunk1.get(CoreAnnotations.TokensAnnotation.class));
      tokenStrs2 = getMainTokenStrs(chunk2.get(CoreAnnotations.TokensAnnotation.class));
      isAcro = isAcronymImpl(text1, tokenStrs2) || isAcronymImpl(text2, tokenStrs1);
    }
    return isAcro;
  }

  /** @see AcronymMatcher#isAcronym(edu.stanford.nlp.util.CoreMap, edu.stanford.nlp.util.CoreMap) */
  public static boolean isAcronym(String[] chunk1, String[] chunk2) {
    String text1 = StringUtils.join(chunk1);
    String text2 = StringUtils.join(chunk2);
    if (text1.length() <= 1 || text2.length() <= 1) { return false; }
    List<String> tokenStrs1 = Arrays.asList(chunk1);
    List<String> tokenStrs2 = Arrays.asList(chunk2);
    boolean isAcro = isAcronymImpl(text1, tokenStrs2) || isAcronymImpl(text2, tokenStrs1);
    if (!isAcro) {
      tokenStrs1 = getMainTokenStrs(chunk1);
      tokenStrs2 = getMainTokenStrs(chunk2);
      isAcro = isAcronymImpl(text1, tokenStrs2) || isAcronymImpl(text2, tokenStrs1);
    }
    return isAcro;
  }

  public static boolean isFancyAcronym(String[] chunk1, String[] chunk2) {
    String text1 = StringUtils.join(chunk1);
    String text2 = StringUtils.join(chunk2);
    if (text1.length() <= 1 || text2.length() <= 1) { return false; }
    List<String> tokenStrs1 = Arrays.asList(chunk1);
    List<String> tokenStrs2 = Arrays.asList(chunk2);
    return isFancyAcronymImpl(text1, tokenStrs2) || isFancyAcronymImpl(text2, tokenStrs1);
  }

  public static boolean isFancyAcronymImpl(String str, List<String> tokens) {
    str = discardPattern.matcher(str).replaceAll("");
    String text = StringUtils.join(tokens);
    int prev_index = 0;
    for(int i=0; i < str.length(); i++) {
      char ch = str.charAt(i);
      if(text.indexOf(ch) != -1) {
        prev_index = text.indexOf(ch, prev_index);
        if(prev_index == -1) {
          return false;
        }
      } else {
        return false;
      }
    }
    return true;
  }

}
