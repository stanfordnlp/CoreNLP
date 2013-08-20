package edu.stanford.nlp.ie.machinereading.domains.ace;

import edu.stanford.nlp.ie.machinereading.EntityExtractorResultsPrinter;

public class ACEEntityExtractorResultsPrinter extends EntityExtractorResultsPrinter {
  public ACEEntityExtractorResultsPrinter() {
    super(null, ACEEntityExtractor.USE_SUB_TYPES);
  }
}
