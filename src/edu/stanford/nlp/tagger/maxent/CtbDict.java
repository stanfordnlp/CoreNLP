package edu.stanford.nlp.tagger.maxent;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.Generics;

public class CtbDict {

  private static final String defaultFilename = "ctb_dict.txt";
  private static CtbDict ctbDictSingleton;


  private static synchronized CtbDict getInstance() {
    if (ctbDictSingleton == null) {
      ctbDictSingleton = new CtbDict();
    }
    return ctbDictSingleton;
  }


  private CtbDict() {
    try {
      readCtbDict("/u/nlp/data/pos-tagger/dictionary" + '/' + defaultFilename);
    } catch(IOException e) {
      throw new RuntimeException("can't open file: " + e.getMessage());
      /* java sucks */
    }
  }


  public Map <String, Set <String>> ctb_pre_dict;
  public Map <String, Set <String>> ctb_suf_dict;


  private void readCtbDict(String filename) throws IOException {
    BufferedReader ctbDetectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "GB18030"));
    String ctbDetectorLine;

    ctb_pre_dict = Generics.newHashMap();
    ctb_suf_dict = Generics.newHashMap();

    while ((ctbDetectorLine = ctbDetectorReader.readLine()) != null) {
      String[] fields = ctbDetectorLine.split("	");
      String tag=fields[0];
      Set<String> pres=ctb_pre_dict.get(tag);
      Set<String> sufs=ctb_suf_dict.get(tag);

      if(pres==null){
        pres = Generics.newHashSet();
        ctb_pre_dict.put(tag,pres);
      }
      pres.add(fields[1]);

      if(sufs==null){
        sufs = Generics.newHashSet();
        ctb_suf_dict.put(tag,sufs);
      }
      sufs.add(fields[2]);


    }
  }//try


  protected static String getTagPre(String a1, String a2) {
    CtbDict dict = CtbDict.getInstance();
    if (dict.getpre(a1)== null)	return "0";
    if (dict.getpre(a1).contains(a2))
      return "1";
    return "0";
  }



  protected static String getTagSuf(String a1, String a2) {
    CtbDict dict = CtbDict.getInstance();
    if (dict.getsuf(a1)== null)	return "0";
    if (dict.getsuf(a1).contains(a2))
      return "1";
    return "0";
  }


  private Set<String> getpre(String a){
    return ctb_pre_dict.get(a);
  }

  private Set<String> getsuf(String a){
    return ctb_suf_dict.get(a);
  }

}//class
