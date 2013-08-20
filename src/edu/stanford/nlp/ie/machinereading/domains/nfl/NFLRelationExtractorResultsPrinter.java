package edu.stanford.nlp.ie.machinereading.domains.nfl;

import edu.stanford.nlp.ie.machinereading.RelationExtractorResultsPrinter;

public class NFLRelationExtractorResultsPrinter extends RelationExtractorResultsPrinter {
  public NFLRelationExtractorResultsPrinter() {
    super(new NFLRelationMentionFactory(), true);
  }
  
  public NFLRelationExtractorResultsPrinter(boolean createUnrelatedRelations) {
    super(new NFLRelationMentionFactory(), createUnrelatedRelations);
  }
}
