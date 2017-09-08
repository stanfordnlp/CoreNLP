package old.edu.stanford.nlp.tagger.maxent;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;



public class ASBCunkDict {

  private static final String defaultFilename = "/u/nlp/data/pos-tagger/asbc_amb.fixed.gb18030";
  private static ASBCunkDict ASBCunkDictSingleton = null;

  private static synchronized ASBCunkDict getInstance()  {

     if (ASBCunkDictSingleton == null) {
        ASBCunkDictSingleton = new ASBCunkDict();
     }
     return ASBCunkDictSingleton;
  }


  private ASBCunkDict() {
      readASBCunkDict(defaultFilename);
  }


  private static HashMap <String, Set <String>> ASBCunk_dict;


  private static void readASBCunkDict(String filename) {
    try{
      BufferedReader ASBCunkDetectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "GB18030"));
      String ASBCunkDetectorLine;

      ASBCunk_dict = new HashMap<String, Set <String>>();

      while ((ASBCunkDetectorLine = ASBCunkDetectorReader.readLine()) != null) {
        String[] fields = ASBCunkDetectorLine.split(" ");
        String tag=fields[1];
        Set<String> words=ASBCunk_dict.get(tag);

        if (words==null) {
          words = new HashSet<String>();
          ASBCunk_dict.put(tag,words);
	}
        words.add(fields[0]);
      }
    } catch (FileNotFoundException e) {
      System.err.println("ASBCunk not found:");
      System.exit(-1);
    } catch (IOException e) {
      System.err.println("ASBCunk");
      System.exit(-1);
    }
  }


 protected static String getTag(String a1, String a2) {
   ASBCunkDict dict = ASBCunkDict.getInstance();
    if (dict.get(a1)== null) {
      return "0";
    }
    if (dict.get(a1).contains(a2)) {
      return "1";
    }
    return "0";
  }



  private Set<String> get(String a){
    return ASBCunk_dict.get(a);
  }

  /*
  public static String getPathPrefix() {
    return pathPrefix;
  }


  public static void setPathPrefix(String pathPrefix) {
    ASBCunkDict.pathPrefix = pathPrefix;
  }
  */

}//class
