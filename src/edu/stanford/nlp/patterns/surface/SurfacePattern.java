package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;
import java.util.*;


import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.patterns.PatternsAnnotations;
import edu.stanford.nlp.util.*;

/**
 * To represent a surface pattern in more detail than TokenSequencePattern (this
 * class object is eventually compiled as TokenSequencePattern via the toString
 * method). See {@link PatternToken} for more info on how matching of target
 * phrases is done.
 * 
 * Author: Sonal Gupta (sonalg@stanford.edu)
 */

public class SurfacePattern extends Pattern implements Serializable, Comparable<SurfacePattern>{


  @Override
  public CollectionValuedMap<String, String> getRelevantWords() {
    CollectionValuedMap<String, String> relwordsThisPat = new CollectionValuedMap<>();
    Token[] next = getNextContext();
    getRelevantWordsBase(next, relwordsThisPat);
    Token[] prev = getPrevContext();
    getRelevantWordsBase(prev, relwordsThisPat);
    return relwordsThisPat;
  }

  @Override
  public int equalContext(Pattern p) {
    return equalContext((SurfacePattern)p);
  }




  private static final long serialVersionUID = 1L;

  public Token[] prevContext;
  public Token[] nextContext;
  // String prevContextStr = "", nextContextStr = "";
  public PatternToken token;
  // protected String[] originalPrev;
  // protected String[] originalNext;
  // protected String originalPrevStr = "";
  // protected String originalNextStr = "";
  // protected String toString;
  protected int hashcode;
  protected SurfacePatternFactory.Genre genre;

  public SurfacePatternFactory.Genre getGenre() {
    return genre;
  }

  public void setGenre(SurfacePatternFactory.Genre genre) {
    this.genre = genre;
  }


  public SurfacePattern(Token[] prevContext, PatternToken token, Token[] nextContext, SurfacePatternFactory.Genre genre) {
    super(PatternFactory.PatternType.SURFACE);
    this.setPrevContext(prevContext);
    this.setNextContext(nextContext);


    this.setToken(token);
    this.genre = genre;

    hashcode = toString().hashCode();
  }

  public SurfacePattern copyNewToken(){
    return new SurfacePattern(this.prevContext, token.copy(), this.nextContext, genre);
  }

  public static Token getContextToken(CoreLabel tokenj) {
    Token token = new Token(PatternFactory.PatternType.SURFACE);
    token.addORRestriction(PatternsAnnotations.ProcessedTextAnnotation.class, tokenj.get(PatternsAnnotations.ProcessedTextAnnotation.class));
    return token;
  }

//  public static String getContextStr(CoreLabel tokenj, boolean useLemmaContextTokens, boolean lowerCaseContext) {
//    String str = "";
//
//    if (useLemmaContextTokens) {
//      String tok = tokenj.lemma();
//      if (lowerCaseContext)
//        tok = tok.toLowerCase();
//      str = "[{lemma:/" + Pattern.quote(tok.replaceAll("/", "\\\\/"))+ "/}] ";
//      //str = "[{lemma:/\\Q" + tok.replaceAll("/", "\\\\/") + "\\E/}] ";
//    } else {
//      String tok = tokenj.word();
//      if (lowerCaseContext)
//        tok = tok.toLowerCase();
//      str = "[{word:/" + Pattern.quote(tok.replaceAll("/", "\\\\/")) + "/}] ";
//      //str = "[{word:/\\Q" + tok.replaceAll("/", "\\\\/") + "\\E/}] ";
//
//    }
//    return str;
//  }

  public static String getContextStr(String w) {
    String str = "[/" + java.util.regex.Pattern.quote(w.replaceAll("/", "\\\\/")) + "/] ";
    //String str = "[/\\Q" + w.replaceAll("/", "\\\\/") + "\\E/] ";
    return str;
  }

  public String toString(List<String> notAllowedClasses) {
    String prevContextStr = "", nextContextStr = "";
    if (prevContext != null)
      prevContextStr = StringUtils.join(prevContext, " ");

    if (nextContext != null)
      nextContextStr = StringUtils.join(nextContext, " ");

    return (prevContextStr + " " + getToken().getTokenStr(notAllowedClasses) + " " + nextContextStr).trim();
  }

  public String toString(String morePreviousPattern, String moreNextPattern, List<String> notAllowedClasses) {

    String prevContextStr = "", nextContextStr = "";
    if (prevContext != null)
      prevContextStr = StringUtils.join(prevContext, " ");

    if (nextContext != null)
      nextContextStr = StringUtils.join(nextContext, " ");

    return (prevContextStr + " " + morePreviousPattern + " " + getToken().getTokenStr(notAllowedClasses) + " " + moreNextPattern + " " + nextContextStr)
        .trim();
  }

  public String getPrevContextStr() {
    String prevContextStr = "";
    if (prevContext != null)
      prevContextStr = StringUtils.join(prevContext, " ");
    return prevContextStr;
  }

  public String getNextContextStr() {
    String nextContextStr = "";
    if (nextContext != null)
      nextContextStr = StringUtils.join(nextContext, " ");
    return nextContextStr;
  }

  // returns 0 is exactly equal, Integer.MAX_VALUE if the contexts are not same.
  // If contexts are same : it returns (objects restrictions on the token minus
  // p's restrictions on the token). So if returns negative then p has more
  // restrictions.
  public int equalContext(SurfacePattern p) {
    if (p.equals(this))
      return 0;

    if (Arrays.equals(this.prevContext, p.getPrevContext()) && Arrays.equals(this.nextContext, p.getNextContext())) {
      int this_restriction = 0, p_restriction = 0;

      if (this.getToken().useTag)
        this_restriction++;
      if (p.getToken().useTag)
        p_restriction++;

      if (this.getToken().useNER)
        this_restriction++;
      if (p.getToken().useNER)
        p_restriction++;

      if (this.getToken().useTargetParserParentRestriction)
        this_restriction++;
      if (p.getToken().useTargetParserParentRestriction)
        p_restriction++;

      this_restriction -= this.getToken().numWordsCompound;
      p_restriction -= this.getToken().numWordsCompound;
      return this_restriction - p_restriction;
    }
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean equals(Object b) {
    if (!(b instanceof SurfacePattern))
      return false;
    SurfacePattern p = (SurfacePattern) b;
    // if (toString().equals(p.toString()))

    if (!token.equals(p.token))
      return false;

    if ((this.prevContext == null && p.prevContext != null) || (this.prevContext != null && p.prevContext == null))
      return false;

    if ((this.nextContext == null && p.nextContext != null) || (this.nextContext != null && p.nextContext == null))
      return false;

    if (this.prevContext != null && !Arrays.equals(this.prevContext, p.prevContext))
      return false;

    if (this.nextContext != null && !Arrays.equals(this.nextContext, p.nextContext))
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    return toString(null);
  }

  public String toStringToWrite() {

    return getPrevContextStr() + "##" + getToken().toStringToWrite() + "##" + getNextContextStr();
  }

  public String[] getSimplerTokensPrev() {
    return getSimplerTokens(prevContext);
  }

  public String[] getSimplerTokensNext() {
    return getSimplerTokens(nextContext);
  }

//  static Pattern p1 = Pattern.compile(Pattern.quote("[") + "\\s*" + Pattern.quote("{") + "\\s*(lemma|word)\\s*:\\s*/" + Pattern.quote("\\Q") + "(.*)"
//      + Pattern.quote("\\E") + "/\\s*" + Pattern.quote("}") + "\\s*" + Pattern.quote("]"));
//
//  static Pattern p2 = Pattern.compile(Pattern.quote("[") + "\\s*" + Pattern.quote("{") + "\\s*(.*)\\s*:\\s*(.*)\\s*" + Pattern.quote("}") + "\\s*"
//      + Pattern.quote("]"));

  public String[] getSimplerTokens(Token[] p){
    if (p == null)
      return null;

    String[] sim = new String[p.length];
    for (int i = 0; i < p.length; i++) {

      assert p[i] != null : "How is the any one " + Arrays.toString(p) + " null!";
      sim[i] = p[i].getSimple();

    }
    return sim;
  }
/*
  public String[] getSimplerTokens(String[] p) {
    if (p == null)
      return null;

    String[] sim = new String[p.length];
    for (int i = 0; i < p.length; i++) {

      assert p[i] != null : "How is the any one " + Arrays.toString(p) + " null!";

      if (p1 == null)
        throw new RuntimeException("how is p1 null");

      Matcher m = p1.matcher(p[i]);

      if (m.matches()) {
        sim[i] = m.group(2);
      } else {
        Matcher m2 = p2.matcher(p[i]);
        if (m2.matches()) {
          sim[i] = m2.group(2);
        } else if (p[i].startsWith("$FILLER"))
          sim[i] = "FW";
        else if (p[i].startsWith("$STOP"))
          sim[i] = "SW";
        else
          throw new RuntimeException("Cannot understand " + p[i]);
      }
    }
    return sim;

  }
*/
  public String toStringSimple() {

    String[] simprev = getSimplerTokensPrev();
    String[] simnext = getSimplerTokensNext();
    String prevstr = simprev == null ? "" : StringUtils.join(simprev, " ");
    String nextstr = simnext == null ? "" : StringUtils.join(simnext, " ");

    String sim = prevstr.trim() + " <b>" + getToken().toStringToWrite() + "</b> " + nextstr.trim();
    return sim;
  }

  public Token[] getPrevContext() {
    return prevContext;
  }

  public void setPrevContext(Token[] prevContext) {
    this.prevContext = prevContext;
  }

  public Token[] getNextContext() {
    return nextContext;
  }

  public void setNextContext(Token[] nextContext) {
    this.nextContext = nextContext;
  }

  public PatternToken getToken() {
    return token;
  }

  public void setToken(PatternToken token) {
    this.token = token;
  }

  // private String getOriginalPrevStr() {
  // String originalPrevStr = "";
  // if (originalPrev != null)
  // originalPrevStr = StringUtils.join(originalPrev, " ");
  //
  // return originalPrevStr;
  // }

  // public void setOriginalPrevStr(String originalPrevStr) {
  // this.originalPrevStr = originalPrevStr;
  // }

  // public String getOriginalNextStr() {
  // String originalNextStr = "";
  // if (originalNext != null)
  // originalNextStr = StringUtils.join(originalNext, " ");
  // return originalNextStr;
  // }

  // public void setOriginalNextStr(String originalNextStr) {
  // this.originalNextStr = originalNextStr;
  // }

  // public String[] getOriginalPrev() {
  // return originalPrev;
  // }
  //
  // public void setOriginalPrev(String[] originalPrev) {
  // this.originalPrev = originalPrev;
  // }
  //
  // public String[] getOriginalNext() {
  // return originalNext;
  // }
  //
  // public void setOriginalNext(String[] originalNext) {
  // this.originalNext = originalNext;
  // }

  public static boolean sameGenre(SurfacePattern p1, SurfacePattern p2) {
    return p1.getGenre().equals(p2.getGenre());
  }

  /**
   * True if array1 contains array2. Also true if both array1 and array2 are
   * null
   * 
   * @param array1
   * @param array2
   * @return
   */
  static public boolean subsumesArray(Object[] array1, Object[] array2) {

    if ((array1 == null && array2 == null)) {
      return true;
    }

    // only one of them is null
    if (array1 == null || array2 == null) {
      return false;
    }

    if (array2.length > array1.length) {
      return false;
    }

    for (int i = 0; i < array1.length; i++) {
      if (array1[i].equals(array2[0])) {
        boolean found = true;
        for (int j = 0; j < array2.length; j++) {
          if (array1.length <= i + j || !array2[j].equals(array1[i + j])) {
            found = false;
            break;
          }
        }
        if (found) {
          return true;
        }

      }
    }
    return false;
  }

  /**
   * True p1 subsumes p2 (p1 has longer context than p2)
   * 
   * @param p1
   * @param p2
   * @return
   */
  public static boolean subsumes(SurfacePattern p1, SurfacePattern p2) {

    if (subsumesArray(p1.getNextContext(), p2.getNextContext()) && subsumesArray(p1.getPrevContext(), p2.getPrevContext())) {
      return true;
    }
    return false;
  }

  // true if one pattern subsumes another
  public static boolean subsumesEitherWay(SurfacePattern p1, SurfacePattern p2) {
    if (subsumes(p1, p2) || subsumes(p2, p1)) {
      return true;
    }
    return false;
  }

  public static boolean sameRestrictions(SurfacePattern p1, SurfacePattern p2) {
    PatternToken token1 = p1.token;
    PatternToken token2 = p2.token;
    if (token1.equals(token2))
      return true;
    else
      return false;
  }

  @Override
  public int compareTo(SurfacePattern o) {
    int numthis = this.getPreviousContextLen() + this.getNextContextLen();
    int numthat = o.getPreviousContextLen() + o.getNextContextLen();

    if (numthis > numthat) {
      return -1;
    } else if (numthis < numthat) {
      return 1;
    } else
      return this.toString().compareTo(o.toString());
  }

  public int getPreviousContextLen() {
    if (this.prevContext == null)
      return 0;
    else
      return this.prevContext.length;
  }

  public int getNextContextLen() {
    if (this.nextContext == null)
      return 0;
    else
      return this.nextContext.length;
  }

  public static boolean sameLength(SurfacePattern p1, SurfacePattern p2) {
    if (p1.getPreviousContextLen() == p2.getPreviousContextLen() && p1.getNextContextLen() == p2.getNextContextLen())
      return true;
    else
      return false;
  }

  public void setNumWordsCompound(Integer numWordsCompound) {
    token.numWordsCompound = numWordsCompound;
  }

  // public static SurfacePattern parse(String s) {
  // String[] t = s.split("##", -1);
  // String prev = t[0];
  // PatternToken tok = PatternToken.parse(t[1]);
  // String next = t[2];
  // return new SurfacePattern(prev, tok, next);
  // }



}
