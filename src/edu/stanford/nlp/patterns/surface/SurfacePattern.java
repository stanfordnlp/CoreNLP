package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.StringUtils;

public class SurfacePattern implements Serializable {

  private static final long serialVersionUID = 1L;

  private String[] prevContext;
  private String[] nextContext;
  String prevContextStr = "", nextContextStr = "";
  private PatternToken token;
  private String originalPrevStr;

  private String originalNextStr;

  public static boolean insertModifierWildcard = false;

  public SurfacePattern(String[] prevContext, PatternToken token,
      String[] nextContext, String originalPrevStr, String originalNextStr) {
    this.setPrevContext(prevContext);
    this.setNextContext(nextContext);

    if (prevContext != null)
      prevContextStr = StringUtils.join(prevContext, " ");

    if (nextContext != null)
      nextContextStr = StringUtils.join(nextContext, " ");

    this.setToken(token);
    this.setOriginalNextStr(originalNextStr);
    this.setOriginalPrevStr(originalPrevStr);
  }

  public static String getContextStr(CoreLabel tokenj,
      boolean useLemmaContextTokens, boolean lowerCaseContext) {
    String str = "";

    if (useLemmaContextTokens) {
      String tok = tokenj.lemma();
      if (lowerCaseContext)
        tok = tok.toLowerCase();
      str = "[{lemma:/\\Q" + tok.replaceAll("/", "\\\\/") + "\\E/}] ";
    } else {
      String tok = tokenj.word();
      if (lowerCaseContext)
        tok = tok.toLowerCase();
      str = "[{word:/\\Q" + tok.replaceAll("/", "\\\\/") + "\\E/}] ";

    }
    return str;
  }

  public static String getContextStr(String w) {
    String str = "[/\\Q" + w.replaceAll("/", "\\\\/") + "\\E/] ";
    return str;
  }

  public String toString() {
    return (prevContextStr + " " + getToken().getTokenStr() + " " + nextContextStr)
        .trim();
  }

  public String toString(String morePreviousPattern, String moreNextPattern) {
    return (prevContextStr + " " + morePreviousPattern + " "
        + getToken().getTokenStr() + " " + moreNextPattern + " " + nextContextStr)
        .trim();
  }

  // returns 0 is exactly equal, Integer.MAX_VALUE if the contexts are not same.
  // If contexts are same : it returns (objects restrictions on the token minus
  // p's restrictions on the token). So if returns negative then p has more
  // restrictions.
  public int equalContext(SurfacePattern p) {
    if (p.equals(this))
      return 0;
    if (prevContextStr.equals(p.prevContextStr)
        && nextContextStr.equals(p.nextContextStr)) {
      int this_restriction = 0, p_restriction = 0;
      if (this.getToken().useTag)
        this_restriction++;
      if (p.getToken().useTag)
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
    if (toString().equals(p.toString()))
      // if (token.equals(p.token) && this.prevContext.equals(p.prevContext) &&
      // this.nextContext.equals(p.nextContext))
      return true;
    else
      return false;
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  public String toStringToWrite() {
    return prevContextStr + "##" + getToken().toStringToWrite() + "##"
        + nextContextStr;
  }

  public String toStringSimple() {
    return getOriginalPrevStr() + " <b>" + getToken().toStringToWrite() + "</b> "
        + getOriginalNextStr();
  }

  public String[] getPrevContext() {
    return prevContext;
  }

  public void setPrevContext(String[] prevContext) {
    this.prevContext = prevContext;
  }

  public String[] getNextContext() {
    return nextContext;
  }

  public void setNextContext(String[] nextContext) {
    this.nextContext = nextContext;
  }

  public PatternToken getToken() {
    return token;
  }

  public void setToken(PatternToken token) {
    this.token = token;
  }

  public String getOriginalPrevStr() {
    return originalPrevStr;
  }

  public void setOriginalPrevStr(String originalPrevStr) {
    this.originalPrevStr = originalPrevStr;
  }

  public String getOriginalNextStr() {
    return originalNextStr;
  }

  public void setOriginalNextStr(String originalNextStr) {
    this.originalNextStr = originalNextStr;
  }

  // public static SurfacePattern parse(String s) {
  // String[] t = s.split("##", -1);
  // String prev = t[0];
  // PatternToken tok = PatternToken.parse(t[1]);
  // String next = t[2];
  // return new SurfacePattern(prev, tok, next);
  // }

}
