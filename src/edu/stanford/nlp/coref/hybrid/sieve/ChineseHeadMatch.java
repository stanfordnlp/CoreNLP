package edu.stanford.nlp.coref.hybrid.sieve;

import java.util.Properties;

public class ChineseHeadMatch extends DeterministicCorefSieve {
  public ChineseHeadMatch() {
    super();
    flags.USE_CHINESE_HEAD_MATCH = true;
  }
  // for debug
  public ChineseHeadMatch(Properties props) {
    super(props);
    flags.USE_CHINESE_HEAD_MATCH = true;
  }
}
