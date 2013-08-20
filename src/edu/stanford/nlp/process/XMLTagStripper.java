package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;


import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.XMLUtils;

import java.io.StringReader;

/**
 * Class XMLTagStripper
 *
 * @author Teg Grenager
 */
public class XMLTagStripper implements Function<HasWord,String> {

  /**
   * Whether to insert "\n" words after ending block tags.
   */
  private boolean markLineBreaks;

  public String apply(HasWord in) {
    String s = null;
    s = in.word();
    return stripTags(s);
  }

  public String stripTags(String s) {
    String result = XMLUtils.stripTags(new StringReader(s), null, markLineBreaks);
    return result;
  }

  public XMLTagStripper(boolean markLineBreaks) {
    this.markLineBreaks = markLineBreaks;
  }

  public static void main(String[] args) throws Exception {
    String text = IOUtils.slurpFile(args[0]);
    XMLTagStripper stripper = new XMLTagStripper(true);
    String s = stripper.apply(new Word(text));
    System.out.println(s);
  }

}
