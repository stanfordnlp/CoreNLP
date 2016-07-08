package edu.stanford.nlp.wordseg;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Affixation information.
 *
 * @author Huihsin Tseng
 * @author Pichuan Chang
 */
public class AffixDictionary {

  private static final Redwood.RedwoodChannels logger = Redwood.channels(AffixDictionary.class);

  //public Set ctbIns, asbcIns, hkIns, pkIns, msrIns;
  public Set<String> ins;

  public AffixDictionary(String affixFilename)  {
    ins = readDict(affixFilename);
  }

  private Set<String> getInDict() {return ins;}


  private static Set<String> readDict(String filename)  {
    Set<String> a = Generics.newHashSet();

    try {
      /*
      if(filename.endsWith("in.as") ||filename.endsWith("in.city") ){
      	aDetectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "Big5_HKSCS"));
      }else{ aDetectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "GB18030"));
      }
      */
      BufferedReader aDetectorReader = IOUtils.readerFromString(filename, "UTF-8");

      //logger.debug("DEBUG: in affDict readDict");
      for (String aDetectorLine; (aDetectorLine = aDetectorReader.readLine()) != null; ) {
        //logger.debug("DEBUG: affDict: "+filename+" "+aDetectorLine);
        a.add(aDetectorLine);
      }
      aDetectorReader.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    //logger.info("XM:::readDict(filename: " + filename + ")");
    logger.info("Loading affix dictionary from " + filename + " [done].");
    return a;
  }


  public String getInDict(String a1) {
    if (getInDict().contains(a1))
      return "1";
    return "0";
  }

}//end of class
