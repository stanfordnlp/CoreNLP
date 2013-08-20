package edu.stanford.nlp.ie.machinereading.domains.ace;

import edu.stanford.nlp.ie.machinereading.BasicEntityExtractor;
import edu.stanford.nlp.ie.machinereading.structure.EntityMentionFactory;

public class ACEEntityExtractor extends BasicEntityExtractor {
    private static final long serialVersionUID = 1L;
    
    public static final boolean USE_SUB_TYPES = false;

    public ACEEntityExtractor(String gazetteerLocation) {
      super(gazetteerLocation, USE_SUB_TYPES, null, true, new EntityMentionFactory());
    }
}
