package edu.stanford.nlp.hcoref.sieve;

import java.util.Locale;

public class ChinesePreciseConstructs extends DeterministicCorefSieve{

  /**
   * 
   */
  private static final long serialVersionUID = 3864439829054859548L;
  public ChinesePreciseConstructs(){
    super();
    lang = Locale.CHINESE;
    flags.USE_INCOMPATIBLES = false;
    flags.USE_APPOSITION = true;
    flags.USE_PREDICATENOMINATIVES = true;
    flags.USE_ACRONYM = true;
    flags.USE_RELATIVEPRONOUN = true;
    // Disabled for Chinese
//    flags.USE_ROLEAPPOSITION = true;
    flags.USE_DEMONYM = true;
  }

}
