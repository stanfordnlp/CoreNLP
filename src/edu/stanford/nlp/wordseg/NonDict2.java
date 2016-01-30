package edu.stanford.nlp.wordseg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.nlp.sequences.SeqClassifierFlags;

public class NonDict2  {

  //public String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";
  public String corporaDict = "/u/nlp/data/gale/segtool/stanford-seg/data/";
  private static CorpusDictionary cd = null;

  private static Logger logger = LoggerFactory.getLogger(NonDict2.class);

  public NonDict2(SeqClassifierFlags flags) {
    if (cd == null) {

      if (flags.sighanCorporaDict != null) {
        corporaDict = flags.sighanCorporaDict; // use the same flag for Sighan 2005,
        // but our list is extracted from ctb
      }
      String path;
      if (flags.useAs || flags.useHk || flags.useMsr) {
        throw new RuntimeException("only support settings for CTB and PKU now.");
      } else if ( flags.usePk ) {
        path = corporaDict+"/dict/pku.non";
      } else { // CTB
        path = corporaDict+"/dict/ctb.non";
      }

      cd = new CorpusDictionary(path);
      // just output the msg...
      if (flags.useAs || flags.useHk || flags.useMsr) {
      } else if ( flags.usePk ) {
        logger.info("INFO: flags.usePk=true | building NonDict2 from "+path);
      } else { // CTB
        logger.info("INFO: flags.usePk=false | building NonDict2 from "+path);
      }
    }
  }

  public String checkDic(String c2, SeqClassifierFlags flags) {
    if (cd.getW(c2).equals("1")) {
      return "1";
    } 
    return "0";
  }

}
