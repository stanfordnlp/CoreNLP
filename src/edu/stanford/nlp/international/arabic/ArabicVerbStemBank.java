package edu.stanford.nlp.international.arabic; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;

import edu.stanford.nlp.international.arabic.pipeline.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.trees.treebank.Mapper;
import edu.stanford.nlp.util.Generics;

/**
 * A singleton class backed by a map between words and stems. The present input format is
 * the same as that used by the Arabic subject detector.
 *
 * @author Spence Green
 */
public class ArabicVerbStemBank  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ArabicVerbStemBank.class);

  private static ArabicVerbStemBank thisInstance = null;

  private final Map<String,String> verbStems;
  private final Buckwalter b2a;
  private final Mapper lexMapper;
  private ArabicVerbStemBank() {
    verbStems = Generics.newHashMap();
    b2a = new Buckwalter();
    lexMapper = new DefaultLexicalMapper();
  }

  public synchronized static ArabicVerbStemBank getInstance() {
    if(thisInstance == null) {
      thisInstance = new ArabicVerbStemBank();
    }
    return thisInstance;
  }

  public String getStem(String word) {
    if(verbStems.containsKey(word))
      return verbStems.get(word);
    return word;
  }

  public void load(String filename) {
    try {
      BufferedReader br = IOUtils.readerFromString(filename);
      while (br.ready()) {
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
