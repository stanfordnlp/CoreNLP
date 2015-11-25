package edu.stanford.nlp.parser.lexparser;

import java.io.PrintWriter;

/**
 * Factor out some useful methods more than lexparser module may want
 */
public class ParserUtils {
  private ParserUtils() {} // static members only

  static void printOutOfMemory(PrintWriter pw) {
    pw.println();
    pw.println("*******************************************************");
    pw.println("***  WARNING!! OUT OF MEMORY! THERE WAS NOT ENOUGH  ***");
    pw.println("***  MEMORY TO RUN ALL PARSERS.  EITHER GIVE THE    ***");
    pw.println("***  JVM MORE MEMORY, SET THE MAXIMUM SENTENCE      ***");
    pw.println("***  LENGTH WITH -maxLength, OR PERHAPS YOU ARE     ***");
    pw.println("***  HAPPY TO HAVE THE PARSER FALL BACK TO USING    ***");
    pw.println("***  A SIMPLER PARSER FOR VERY LONG SENTENCES.      ***");
    pw.println("*******************************************************");
    pw.println();
  }

}

