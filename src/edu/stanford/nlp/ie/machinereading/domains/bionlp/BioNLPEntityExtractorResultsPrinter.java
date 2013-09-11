package edu.stanford.nlp.ie.machinereading.domains.bionlp;

import edu.stanford.nlp.ie.machinereading.EntityExtractorResultsPrinter;

public class BioNLPEntityExtractorResultsPrinter extends EntityExtractorResultsPrinter {
  public BioNLPEntityExtractorResultsPrinter() {
    super(null, BioNLPEntityExtractor.USE_SUB_TYPES);
  }

}
