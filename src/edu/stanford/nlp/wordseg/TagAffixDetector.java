package edu.stanford.nlp.wordseg;

import edu.stanford.nlp.sequences.SeqClassifierFlags;

class TagAffixDetector {

  private CorpusChar cc;
  private affDict aD;
  //String sighanCorporaDict = "/u/nlp/data/chinese-segmenter/";
  private String corporaDict = "/u/nlp/data/gale/segtool/stanford-seg/data";

  public TagAffixDetector(SeqClassifierFlags flags) {
    if (flags.sighanCorporaDict != null) {
      corporaDict = flags.sighanCorporaDict;
    }

    if (!corporaDict.equals("") && !corporaDict.endsWith("/")) {
      corporaDict = corporaDict + "/";
    }

    String ccPath;
    String adPath;
    if (flags.useChPos || flags.useCTBChar2 || flags.usePKChar2) {
      // if we're using POS information, override the ccPath
      // For now we only have list for CTB and PK
      if (flags.useASBCChar2 || flags.useHKChar2 || flags.useMSRChar2) {
        throw new RuntimeException("only support settings for CTB and PK now.");
      } else if (flags.useCTBChar2) {
        ccPath = corporaDict+"dict/character_list";
        adPath = corporaDict+"dict/in.ctb";
      } else if (flags.usePKChar2) {
        ccPath = corporaDict+"dict/pos_open/character_list.pku.utf8";
        adPath = corporaDict+"dict/in.pk";
      } else {
        throw new RuntimeException("none of flags.useXXXChar2 are on");
      }
    } else {
      ccPath = corporaDict+"dict/pos_close/char.ctb.list";
      adPath = corporaDict+"dict/in.ctb";
    }
    System.err.println("INFO: TagAffixDetector: useChPos=" + flags.useChPos +
            " | useCTBChar2=" + flags.useCTBChar2 + " | usePKChar2=" + flags.usePKChar2);
    System.err.println("INFO: TagAffixDetector: building TagAffixDetector from "+ccPath+" and "+adPath);
    cc = new CorpusChar(ccPath);
    aD = new affDict(adPath);
  }

  String checkDic(String t2, String c2 ){
    if(cc.getTag(t2, c2).equals("1"))
      return "1";
    return "0";
  }

  String checkInDic(String c2 ){
    if(aD.getInDict(c2).equals("1"))
      return "1";
    return "0";
  }

}
