package old.edu.stanford.nlp.tagger.maxent;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;


/**
 * This class will just hold an array of the outputs of all available taggers.
 */
public class CollectionTaggerOutputs {

  int numTaggers;
  static int baseToken = 0;
  private TaggerOutputHolder[] taggers;

  public CollectionTaggerOutputs(int num) {
    this.numTaggers = num;
    taggers = new TaggerOutputHolder[numTaggers];
  }

  public final void readOutput(int nO, String filename, boolean probs) {
    taggers[nO] = new TaggerOutputHolder(filename, probs);
  }


  public String getTag(int numTagger, int numToken) {
    return (taggers[numTagger].getTag(numToken + baseToken));
  }


  /**
   * These will be like commnad-line arguments. Format
   * numTaggers file_0 f|t file_1 f|t file_2 f|t ... file_n f|t
   */
  public CollectionTaggerOutputs(String[] args) {
    int num = Integer.parseInt(args[0]);
    if (args.length != (2 * num + 1)) {
      System.out.println("Error, wrong number of arguments.");
      System.exit(1);
    }
    this.numTaggers = num;
    taggers = new TaggerOutputHolder[numTaggers];
    for (int i = 0; i < num; i++) {
      String filename = args[i * 2 + 1];
      boolean prb = args[i * 2 + 2].equals("t");
      readOutput(i, filename, prb);
    }//for
  }


  /**
   * This class has instances for each tagger used in the tagger combination. It will contain an array of elements
   *  - one element per token in the training/test sets.
   * Each element will be an array of length 3 - the top probable 3 tags with scaled probability.
   */
  public static class TaggerOutputHolder {

    final boolean probs;

    /**
     * This array is the size of the training data, the number of tokens.
     */
    private OutputTags[] tagArrays;


    public TaggerOutputHolder(boolean probs) {
      this.probs = probs;
      //tagArrays=new OutputTags[GlobalHolder.pairs.getSize()];
    }

    public TaggerOutputHolder(String fileName, boolean probs) {
      this(probs);
      try {

        BufferedReader in = new BufferedReader(new FileReader(fileName));
        int nO = 0;
        ArrayList<OutputTags> v = new ArrayList<OutputTags>();
        for (String lineS; (lineS = in.readLine()) != null; ) {
          if (lineS.startsWith("%%")) {
            continue;
          }
          v.add(new OutputTags(lineS, probs));
          nO++;
          //if(nO==tagArrays.length) break;
        }
        tagArrays = new OutputTags[nO];
        for (int i = 0; i < nO; i++) {
          tagArrays[i] = v.get(i);
        }
        v.clear();
        in.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }


    public String getTag(int numToken) {
      return tagArrays[numToken].getTag();
    }

  } // end static class TaggerOutputHolder

}
