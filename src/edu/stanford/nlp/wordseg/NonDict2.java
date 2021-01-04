package edu.stanford.nlp.wordseg;


import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.sequences.SeqClassifierFlags;

public class NonDict2  {

  //public String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";
  public static final String DEFAULT_HOME = "/u/nlp/data/gale/segtool/stanford-seg/data/";
  public final String corporaDict;
  private final CorpusDictionary cd;

  private static Redwood.RedwoodChannels logger = Redwood.channels(NonDict2.class);

  public NonDict2(SeqClassifierFlags flags) {
    if (flags.sighanCorporaDict != null) {
      corporaDict = flags.sighanCorporaDict; // use the same flag for Sighan 2005,
      // but our list is extracted from ctb
    } else {
      corporaDict = DEFAULT_HOME;
    }

    String path;
    if (flags.useAs || flags.useHk || flags.useMsr) {
      throw new RuntimeException("only support settings for CTB and PKU now.");
    } else if ( flags.usePk ) {
      path = corporaDict+"/dict/pku.non";
      logger.info("INFO: flags.usePk=true | building NonDict2 from "+path);
    } else { // CTB
      path = corporaDict+"/dict/ctb.non";
      logger.info("INFO: flags.usePk=false | building NonDict2 from "+path);
    }

    cd = new CorpusDictionary(path);
  }

  public String checkDic(String c2, SeqClassifierFlags flags) {
    if (cd.getW(c2).equals("1")) {
      return "1";
    } 
    return "0";
  }

}
