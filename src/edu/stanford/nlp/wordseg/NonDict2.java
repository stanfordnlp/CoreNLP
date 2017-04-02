package edu.stanford.nlp.wordseg;

import edu.stanford.nlp.sequences.SeqClassifierFlags;

public class NonDict2  {

  //public String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";
  public String corporaDict = "/u/nlp/data/gale/segtool/stanford-seg/data/";
  private static CorpusDictionary cd = null;

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
        System.err.println("INFO: flags.usePk=true | building NonDict2 from "+path);
      } else { // CTB
        System.err.println("INFO: flags.usePk=false | building NonDict2 from "+path);
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
