package edu.stanford.nlp.ie.machinereading.domains.roth;

import edu.stanford.nlp.ie.machinereading.BasicEntityExtractor;
import edu.stanford.nlp.ie.machinereading.structure.EntityMentionFactory;

public class RothEntityExtractor extends BasicEntityExtractor {
  private static final long serialVersionUID = 1L;
  
  public static final boolean USE_SUB_TYPES = false;

  public RothEntityExtractor() {
    super(null, USE_SUB_TYPES, null, true, new EntityMentionFactory());
  }
}
