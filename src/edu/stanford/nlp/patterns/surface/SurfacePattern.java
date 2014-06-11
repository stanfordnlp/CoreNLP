package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.StringUtils;

public class SurfacePattern implements Serializable {

  private static final long serialVersionUID = 1L;

  String[] prevContext;
  String[] nextContext;
  String prevContextStr = "", nextContextStr = "";
  PatternToken token;
  String originalPrevStr, originalNextStr;

  public static boolean insertModifierWildcard = false;

  public SurfacePattern(String[] prevContext, PatternToken token,
      String[] nextContext, String originalPrevStr, String originalNextStr) {
    this.prevContext = prevContext;
    this.nextContext = nextContext;

    if (prevContext != null)
      prevContextStr = StringUtils.join(prevContext, " ");

    if (nextContext != null)
      nextContextStr = StringUtils.join(nextContext, " ");

    this.token = token;
    this.originalNextStr = originalNextStr;
    this.originalPrevStr = originalPrevStr;
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
    return (prevContextStr + " " + token.getTokenStr() + " " + nextContextStr)
        .trim();
  }

  public String toString(String morePreviousPattern, String moreNextPattern) {
    return (prevContextStr + " " + morePreviousPattern + " "
        + token.getTokenStr() + " " + moreNextPattern + " " + nextContextStr)
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
      if (this.token.useTag)
        this_restriction++;
      if (p.token.useTag)
        p_restriction++;
      this_restriction -= this.token.numWordsCompound;
      p_restriction -= this.token.numWordsCompound;
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
    return prevContextStr + "##" + token.toStringToWrite() + "##"
        + nextContextStr;
  }

  public String toStringSimple() {
    return originalPrevStr + " <b>" + token.toStringToWrite() + "</b> "
        + originalNextStr;
  }

  // public static SurfacePattern parse(String s) {
  // String[] t = s.split("##", -1);
  // String prev = t[0];
  // PatternToken tok = PatternToken.parse(t[1]);
  // String next = t[2];
  // return new SurfacePattern(prev, tok, next);
  // }

}
