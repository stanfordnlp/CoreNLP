package edu.stanford.nlp.international.arabic.pipeline;

import java.util.regex.*;

/**
 * Returns 
 * 
 * @author Spence Green
 *
 */
public class UnvocLexicalMapper extends DefaultLexicalMapper {

  private static final long serialVersionUID = -8702531532523913125L;

  private static final Pattern decoration = Pattern.compile("\\+|\\[.*\\]$");
  
  @Override
  public String map(String parent, String element) {
    
    String cleanElement = decoration.matcher(element).replaceAll("");
    
    if(cleanElement.equals(""))
      return element;
    
    return cleanElement;
  }
}
