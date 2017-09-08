package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.util.ErasureUtils;

import java.util.StringTokenizer;

/**
 * This is an array of max 3 tags with probabilities for them, that the taggers have output.
 */
public class OutputTags {

  private static final int maxTags = 3;
  private int numTags;
  private int[] tags = new int[maxTags];
  //private double[] probabilities = new double[maxTags]; // mg: written, but never read

  public OutputTags() {
  }

  public OutputTags(String in, boolean probs) {
    ErasureUtils.noop(probs);
    // make an instance from the current position in the file to the end of the line
    // there might be one or more tags there together with probabilities for them
    // format as follows : tag1 \sp p1 \sp tag2 \sp p2 tag3 \sp p3
    // skip the first thing - the word
    try {
      StringTokenizer st = new StringTokenizer(in);
      while (st.hasMoreTokens()) {
        String tag = st.nextToken();
        numTags++;
        tags[numTags - 1] = GlobalHolder.tags.getIndex(tag);
        System.out.println(tags[numTags - 1]);
        //if (probs) {
          //double p = Double.parseDouble(st.nextToken());
          //probabilities[numTags - 1] = p;
        //}
      }// while
    } catch (Exception e) {
      e.printStackTrace();

    }
  }


  public String getTag() {
    return (GlobalHolder.tags.getTag(tags[0]));
  }


}
