package edu.stanford.nlp.misc;

import java.io.IOException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import edu.stanford.nlp.io.IOUtils;


/** Basic keyword-in-context (KWIC) concordance based on a regular
 *  expression.
 *  <p>
 *  Works for me for lightweight usage.
 *
 *  @author Christopher Manning
 */
public class KWIC {

  private static final String padding = "                              ";
  private KWIC() {}

  public static void main(String[] args) throws IOException {
    Pattern pat = Pattern.compile("(?:.|\n){30}(?:" + args[0] + ")(?:.|\n){30}");
    for (int i = 1; i < args.length; i++) {
      String fContents = IOUtils.slurpFile(args[i]);
      String padContents = padding + fContents + padding;
      Matcher mat = pat.matcher(padContents);
      int ind = 0;
      while (mat.find(ind)) {
        String match = mat.group();
        match = match.replace('\n', ' ');
        System.out.println(match);
        ind = mat.start() + 1;
      }
    }
  }

}
