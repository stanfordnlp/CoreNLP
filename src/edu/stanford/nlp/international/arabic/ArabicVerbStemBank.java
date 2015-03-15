package edu.stanford.nlp.international.arabic;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.international.arabic.pipeline.*;
import edu.stanford.nlp.trees.treebank.Mapper;

/**
 * A singleton class backed by a map between words and stems. The present input format is
 * the same as that used by the Arabic subject detector.
 *
 * @author Spence Green
 */
public class ArabicVerbStemBank {

  private static ArabicVerbStemBank thisInstance = null;

  private final Map<String,String> verbStems;
  private final Buckwalter b2a;
  private final Mapper lexMapper;
  private ArabicVerbStemBank() {
    verbStems = new HashMap<String,String>();
    b2a = new Buckwalter();
    lexMapper = new DefaultLexicalMapper();
  }

  public static ArabicVerbStemBank getInstance() {
    if(thisInstance == null)
      thisInstance = new ArabicVerbStemBank();
    return thisInstance;
  }

  public String getStem(String word) {
    if(verbStems.containsKey(word))
      return verbStems.get(word);
    return word;
  }

  public void load(String filename) {
    int lineId = 0;
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filename)),"UTF-8"));
      for(lineId = 1; br.ready(); lineId++) {
        String[] toks = br.readLine().split("\\t");
        List<String> toksList = Arrays.asList(toks);

        assert toksList.size() == 8;

        String word = toksList.get(0).replaceAll("\\|", "");
        String stem = toksList.get(7).replaceAll("[_|-].*\\d$", "");

        if(stem.equals("NA") || stem.equals("O")) continue;

        stem = lexMapper.map(null, stem);
        String uniStem = b2a.buckwalterToUnicode(stem);
        if(!verbStems.containsKey(word))
          verbStems.put(word, uniStem);
      }

      System.err.printf("%s: Loaded %d stems\n", this.getClass().getName(), verbStems.keySet().size());

    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      //TODO Need to add proper debugging
      e.printStackTrace();
    }
  }

  //WSGDEBUG - For debugging
  public void debugPrint(PrintWriter pw) {
    for(String word : verbStems.keySet())
      pw.printf("%s : %s\n",word,getStem(word));
  }

  /**
   */
  public static void main(String[] args) {
    ArabicVerbStemBank vsb = ArabicVerbStemBank.getInstance();

    vsb.load("e.test");

    PrintWriter pw = new PrintWriter(System.out,true);
    vsb.debugPrint(pw);
  }

}
