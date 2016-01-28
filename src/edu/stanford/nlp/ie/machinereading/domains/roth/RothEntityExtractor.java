package edu.stanford.nlp.ie.machinereading.domains.roth;

import java.util.HashMap;
import java.util.Map;

import edu.stanford.nlp.ie.machinereading.BasicEntityExtractor;
import edu.stanford.nlp.ie.machinereading.structure.EntityMentionFactory;

public class RothEntityExtractor extends BasicEntityExtractor {
  private static final long serialVersionUID = 1L;
  
  public static final boolean USE_SUB_TYPES = false;

  private Map<String, String> entityTagForNer;
  
  public RothEntityExtractor() {
    super(null, USE_SUB_TYPES, null, true, new EntityMentionFactory(), true);
    entityTagForNer = new HashMap<>();
//    entityTagForNer.put("person", "Peop");
//    entityTagForNer.put("organization", "Org");
//    entityTagForNer.put("location", "Loc");
    entityTagForNer.put("person", "PEOPLE");
    entityTagForNer.put("organization", "ORGANIZATION");
    entityTagForNer.put("location", "LOCATION");
    
  }
  
  @Override
  public  String getEntityTypeForTag(String ner){
    ner = ner.toLowerCase();
    if(entityTagForNer.containsKey(ner))
      return entityTagForNer.get(ner); 
    else
      return "O";
  }

}
