package edu.stanford.nlp.ie.pascal; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import edu.stanford.nlp.util.StringUtils;

/**
 * Hyphenates words according to the TeX algorithm.
 * @author Jamie Nicolson (nicolson@cs.stanford.edu)
 */
public class TeXHyphenator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TeXHyphenator.class);

  private static class Node {
    HashMap children = new HashMap();

    int [] pattern = null;
  }

  /**
   * Loads the default hyphenation rules in DefaultTeXHyphenator.
   */
  public void loadDefault() {
    try {
      load( new BufferedReader(new StringReader(
        DefaultTeXHyphenData.hyphenData) ) );
    } catch(IOException e) {
      // shouldn't happen
      throw new RuntimeException(e);
    }
  }

  /**
   * Loads custom hyphenation rules. You probably want to use
   * loadDefault() instead.
   *
   */
  public void load(BufferedReader input) throws IOException {
    String line;
    while( (line=input.readLine()) != null ) {
      if( StringUtils.matches(line, "\\s*(%.*)?") ) {
        // comment or blank line
        log.info("Skipping: " + line);
        continue;
      }
      char [] linechars = line.toCharArray();
      int [] pattern = new int[linechars.length];
      char [] chars  = new char[linechars.length];
      int c = 0;
      for (char linechar : linechars) {
        if (Character.isDigit(linechar)) {
          pattern[c] = Character.digit(linechar, 10);
        } else {
          chars[c++] = linechar;
        }
      }
      char[] shortchars = new char[c];
      int [] shortpattern = new int[c+1];
      System.arraycopy(chars, 0, shortchars, 0, c);
      System.arraycopy(pattern, 0, shortpattern, 0, c+1);
      insertHyphPattern(shortchars, shortpattern);
    }
  }

  private Node head = new Node();

  public static String toString(int[]i) {
    StringBuilder sb = new StringBuilder();
    for (int anI : i) {
      sb.append(anI);
    }
    return sb.toString();
  }

  private void insertHyphPattern(char [] chars, int [] pattern) {
    // find target node, building as we go
    Node cur = head;
    for (char aChar : chars) {
      Character curchar = Character.valueOf(aChar);
      Node next = (Node) cur.children.get(curchar);
      if (next == null) {
        next = new Node();
        cur.children.put(curchar, next);
      }
      cur = next;
    }
    assert( cur.pattern == null );
    cur.pattern = pattern;
  }

  private List<int[]> getMatchingPatterns( char[] chars, int startingIdx ) {
    Node cur = head;
    LinkedList<int[]> matchingPatterns = new LinkedList<>();
    if ( cur.pattern != null ) {
      matchingPatterns.add(cur.pattern);
    }
    for (int c = startingIdx; cur != null && c < chars.length; ++c ) {
      Character curchar = Character.valueOf(chars[c]);
      Node next = (Node) cur.children.get(curchar);
      cur = next;
      if( cur != null && cur.pattern != null ) {
        matchingPatterns.add(cur.pattern);
      }
    }
    return matchingPatterns;
  }
      

  private void labelWordBreakPoints( char [] phrase, int start, int end,
    boolean[] breakPoints)
  {

    char [] word = new char[end-start+2];
    System.arraycopy(phrase, start, word, 1, end-start);
    word[0] = '.';
    word[word.length-1] = '.';

    // breakScore[i] is the score for breaking before word[i]
    int [] breakScore = new int [word.length + 1];

    for( int c = 0; c < word.length; ++c ) {
      List<int[]> patterns = getMatchingPatterns(word, c);
      for (int[] pattern : patterns) {
        for (int i = 0; i < pattern.length; ++i) {
          if (breakScore[c + i] < pattern[i]) {
            breakScore[c + i] = pattern[i];
          }
        }
      }
    }

    breakPoints[start] = true;
    for( int i = start+1; i < end; i++) {
      // remember that breakPoints is offset by one because we introduced
      // the leading "."
      breakPoints[i-1] |= (breakScore[i-start] % 2 == 1 );
    }
  }
  
  /**
   * @param lcphrase Some English text in lowercase.
   * @return An array of booleans, one per character of the input,
   *    indicating whether it would be OK to insert a hyphen before that
   *    character.
   */
  public boolean[] findBreakPoints(char [] lcphrase) {

    boolean [] breakPoints = new boolean[lcphrase.length];

    boolean inWord = false;
    int wordStart = 0;
    int c = 0;
    for(; c < lcphrase.length; ++c) {
      if( !inWord && Character.isLetter(lcphrase[c]) ) {
        wordStart = c;
        inWord = true;
      } else if( inWord && !Character.isLetter(lcphrase[c]) ) {
        inWord = false;
        labelWordBreakPoints(lcphrase, wordStart, c, breakPoints);
      }
    }
    if( inWord ) {
      labelWordBreakPoints(lcphrase, wordStart, c, breakPoints);
    }

    return breakPoints;
  }

  public static void main(String[] args) throws Exception {

    TeXHyphenator hyphenator = new TeXHyphenator();
    hyphenator.loadDefault();

    for (String arg : args) {
      char[] chars = arg.toLowerCase().toCharArray();
      boolean[] breakPoints = hyphenator.findBreakPoints(chars);
      System.out.println(arg);
      StringBuilder sb = new StringBuilder();
      for (boolean breakPoint : breakPoints) {
        if (breakPoint) {
          sb.append("^");
        } else {
          sb.append("-");
        }
      }
      System.out.println(sb);
    }
  }

}
