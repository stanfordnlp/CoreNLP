package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.process.LexerTokenizer;

import java.io.*;

/**
 * Merges and tokenizes a directory of text files for BioCreative task 1B,
 * printing the result to stdout.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class BioCreative1BPreprocessor {

  /**
   * Usage: BioCreative1BPreprocessor &lt;<i>directory containing .txt
   * files</i>&gt;
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Usage: BioCreative1BPreprocessor <text_dir>");
      System.exit(1);
    }
    try {
      File[] files = new File(args[0]).listFiles(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.endsWith(".txt");
        }
      });
      for (int i = 0; i < files.length; i++) {
        // merge all lines
        BufferedReader br = new BufferedReader(new FileReader(files[i]));

        StringBuffer sb = new StringBuffer();
        String line;
        while ((line = br.readLine()) != null) {
          sb.append(line);
          sb.append(' ');
        }
        // put each sentence on a separate line for the tagger
        for (int j = 0; j < sb.length(); j++) {
          if (sb.charAt(j) == '!' || sb.charAt(j) == '?') {
            sb.insert(j + 1, '\n');
          } else if (sb.charAt(j) == '.' && isSentenceBoundary(sb, j)) {
            sb.insert(j + 1, '\n');
          }
        }

        String name = files[i].getName();
        name = name.substring(0, name.lastIndexOf(".txt"));
        System.out.print("@@" + name + " ");
        // tokenize and print to stdout
        LexerTokenizer t = new LexerTokenizer(new BioCreativeLexer(new StringReader(sb.toString())));
        while (t.hasNext()) {
          String s = t.next();
          System.out.print(s);
          if (!"\n".equals(s)) {
            System.out.print(' ');
          }
        }
        System.out.print('\n');
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Uses heuristics to determine if the period at the given index is
   * a sentence boundary or not.  Pretty conservative.  Only returns
   * true if the next, non-whitespace character is upper-case and the
   * previous non-whitespace character is not upper-case.
   */
  private static boolean isSentenceBoundary(StringBuffer sb, int index) {
    int i = index - 1;
    while (i > 0 && Character.isWhitespace(sb.charAt(i))) {
      i--;
    }
    if (i < 0) {
      return false;
    }
    if (Character.isUpperCase(sb.charAt(i))) {
      return false;
    }
    int j = index + 1;
    while (j < sb.length() - 1 && Character.isWhitespace(sb.charAt(j))) {
      j++;
    }
    if (Character.isUpperCase(sb.charAt(j))) {
      return true;
    }
    return false;
  }
}
