package edu.stanford.nlp.wordseg;

import java.util.*;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.Generics;

/**
 * affixation information
 * @author Huihsin Tseng
 * @author Pichuan Chang
 */

 
  @SuppressWarnings("unused")
public class affDict {
  private String affixFilename;

  private static Logger logger = LoggerFactory.getLogger(affDict.class);
  
  //public Set ctbIns, asbcIns, hkIns, pkIns, msrIns;
  public Set<String> ins;

  public affDict(String affixFilename)  {
    ins=readDict(affixFilename);
  }
  
  Set<String> getInDict() {return ins;}


  
  private Set<String> readDict(String filename)  {
    Set<String> a = Generics.newHashSet();
   
    //logger.info("XM:::readDict(filename: " + filename + ")");
    logger.info("Loading affix dictionary from " + filename);
    try {
      /*
      if(filename.endsWith("in.as") ||filename.endsWith("in.city") ){
      	aDetectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "Big5_HKSCS"));
      }else{ aDetectorReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "GB18030"));
      }
      */
      InputStream is = IOUtils.getInputStreamFromURLOrClasspathOrFileSystem(filename);
      BufferedReader aDetectorReader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

      String aDetectorLine;
   
      //logger.debug("DEBUG: in affDict readDict");
      while ((aDetectorLine = aDetectorReader.readLine()) != null) {
        //logger.debug("DEBUG: affDict: "+filename+" "+aDetectorLine);
        a.add(aDetectorLine);
      }
      is.close();
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    return a;
  }


  public String getInDict(String a1) {
    if (getInDict().contains(a1))
      return "1";
    return "0";
  }
}//end of class
