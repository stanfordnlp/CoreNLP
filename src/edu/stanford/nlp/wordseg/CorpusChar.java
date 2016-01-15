package edu.stanford.nlp.wordseg;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.Generics;

/**
 * Check tag of each character from 5 different corpora. (4 official training corpora of Sighan bakeoff 2005, plus CTB)
 * These tags are not external knowledge. They are learned from the training corpora.
 * @author Huihsin Tseng
 * @author Pichuan Chang
 */

 
public class CorpusChar {
  private Map <String, Set <String>> charMap;

  public CorpusChar(String charlistFilename)  {
    charMap=readDict(charlistFilename); 
  }

  Map<String, Set<String>> getCharMap() {
    return charMap;
  }
  
  
  private Map<String, Set <String>> char_dict;

  private Map<String, Set<String>> readDict(String filename)  {

    System.err.println("Loading character dictionary file from " + filename);

    try {
      InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(filename);      
      BufferedReader DetectorReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      String DetectorLine;

      char_dict = Generics.newHashMap();
      //System.err.println("DEBUG: in CorpusChar readDict");
      while ((DetectorLine = DetectorReader.readLine()) != null) {
        
        String[] fields = DetectorLine.split("	");
        String tag=fields[0];

        Set<String> chars=char_dict.get(tag);
	 
        if(chars==null){
          chars = Generics.newHashSet();
          char_dict.put(tag,chars);
        } 
        //System.err.println("DEBUG: CorpusChar: "+filename+" "+fields[1]);
        chars.add(fields[1]);
    
    
      }
      is.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return char_dict;
  }

  public String getTag(String a1, String a2) {
    Map<String, Set<String>> h1=getCharMap();
    Set<String> h2=h1.get(a1);
    if (h2 == null) return "0";
    if (h2.contains(a2)) 
      return "1";
    return "0"; 
  }
  /*
  public String getCtbTag(String a1, String a2) {
    HashMap h1=dict.getctb();
    Set h2=(Set)h1.get(a1);
    if (h2 == null) return "0";
    if (h2.contains(a2)) 
      return "1";
    return "0"; 
  }

  public String getAsbcTag(String a1, String a2) {
    HashMap h1=dict.getasbc();
    Set h2=(Set)h1.get(a1);
    if (h2 == null) return "0";
    if (h2.contains(a2)) 
      return "1";
    return "0"; 
  }
  
  public String getPkuTag(String a1, String a2) {
    HashMap h1=dict.getpku();
    Set h2=(Set)h1.get(a1);
    if (h2 == null) return "0";
    if (h2.contains(a2)) 
      return "1";
    return "0"; 
  }

  public String getHkTag(String a1, String a2) {
    HashMap h1=dict.gethk();
    Set h2=(Set)h1.get(a1);
    if (h2 == null) return "0";
    if (h2.contains(a2)) 
      return "1";
    return "0"; 
  }


  public String getMsrTag(String a1, String a2) {
    HashMap h1=dict.getmsr();
    Set h2=(Set)h1.get(a1);
    if (h2 == null) return "0";
    if (h2.contains(a2)) 
      return "1";
    return "0"; 
    }*/

}//end of class
