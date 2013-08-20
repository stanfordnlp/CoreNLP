package edu.stanford.nlp.ie.machinereading.domains.nfl;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.io.RuntimeIOException;

public class NFLGazetteer {
  private static final Set<String> STOP_WORDS = new HashSet<String>(Arrays.asList(new String[] {"st", "st.", "san", "new", "old", "the" }));
  
  public static final int MAX_MENTION_LENGTH = 5;
  
  public static HashMap<String, String> loadGazetteer(String gazetteerLocation) {
    try {
      HashMap<String, String> gazetteer = new HashMap<String, String>();
      // try to load the file from the CLASSPATH first
      InputStream is = NFLGazetteer.class.getClassLoader().getResourceAsStream(gazetteerLocation);
      // if not found in the CLASSPATH, load from the file system
      if (is == null) is = new FileInputStream(gazetteerLocation);
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      
      String line;
      while((line = rd.readLine()) != null){
        String [] tokens = line.split("[ \t\n]+");
        if(tokens.length == 0) continue;
        String label = tokens[0];
        for(int i = tokens.length; i > 1; i --){
          addEntry(gazetteer, tokens, 1, i, label);
        }
        for(int i = 2; i < tokens.length; i ++){
          addEntry(gazetteer, tokens, i, tokens.length, label);
        }
      }
      rd.close();
      is.close();
      return gazetteer;
    } catch(IOException e) {
      throw new RuntimeIOException(e);
    }
  }
  
  private static void addEntry(HashMap<String, String> gazetteer, String [] tokens, int start, int end, String label){
    StringBuffer os = new StringBuffer();
    for(int i = start; i < end; i ++){
      if(i > start) os.append(" ");
      os.append(tokens[i].toLowerCase());
    }
    String name = os.toString();
    if(! STOP_WORDS.contains(name)){
      gazetteer.put(name, label);
    }
  }
  
}
