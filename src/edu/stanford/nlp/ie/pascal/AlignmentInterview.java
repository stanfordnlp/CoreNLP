package edu.stanford.nlp.ie.pascal;

import java.io.*;
import java.util.*;
/**
 * Utility for aligning acronyms.
 * 
 * @author Jamie Nicolson
 */
public class AlignmentInterview {

  public static void main(String args[]) throws Exception {

    String infilename = args[0];
    String outfilename = args[1];

    BufferedReader input = new BufferedReader(new FileReader(infilename));
    PrintWriter output = new PrintWriter(new FileWriter(outfilename));
    BufferedReader cmdline = new BufferedReader(
      new InputStreamReader( System.in ) );

    while(true) {
      String lfLine = input.readLine();
      if( lfLine == null )
        break;
      String sfLine = input.readLine();
      if( sfLine == null ) 
        throw new Exception("file ended on long-form line");
      AlignmentFactory fact = new AlignmentFactory(
        lfLine.toCharArray(), AcronymModel.stripAcronym(sfLine));

      ArrayList<Alignment> alignments = fact.getAlignmentsList();

      if( alignments.size() == 0 ) {
        System.out.println("\nWarning: no alignments for:\n" + lfLine + "\n" +
          sfLine + "\n");
      } else {
        boolean done = false;
        while(!done) {
        System.out.println("\nChoose alignment for:\n" + lfLine + "\n" +
          sfLine + "\n");
          for( int i = 0; i < alignments.size(); ++i) {
            System.out.println(
              alignments.get(i).toString(Integer.toString(i) + ") ") );
          }
          System.out.println( alignments.size() + ") none of the above");
          System.out.println("\nWhich one? ");
          String responseString = cmdline.readLine();
          int response = parseInt(responseString);
          if( response < 0 || response > alignments.size() ) {
            System.out.println("Invalid response");
          } else if( response == alignments.size() ) {
            // skip this bogus data point
            done = true;
          } else {
            alignments.get(response).serialize(output);
            done = true;
          }
        }
      }

      output.flush();
    }

  }

  public static int parseInt(String s) {
    int retval = -1;
    try {
      retval = Integer.parseInt(s);
    } catch(NumberFormatException e) { }
    return retval;
  }
}
