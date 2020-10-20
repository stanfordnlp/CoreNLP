package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.util.CoreMap;

import java.util.List;

/**
 * Token Sequence Matcher for regular expressions over sequences of tokens.
 *
 * @author Angel Chang
 */
public class TokenSequenceMatcher extends CoreMapSequenceMatcher<CoreMap> {

 /* protected static Function<List<? extends CoreLabel>, String> CORELABEL_LIST_TO_STRING_CONVERTER =
          new Function<List<? extends CoreLabel>, String>() {
            public String apply(List<? extends CoreLabel> in) {
              return (in != null)? ChunkAnnotationUtils.getTokenText(in, CoreAnnotations.TextAnnotation.class): null;
            }
          };     */

  public TokenSequenceMatcher(SequencePattern<CoreMap> pattern, List<? extends CoreMap> tokens) {
    super(pattern, tokens);
    //   this.nodesToStringConverter = CORELABEL_LIST_TO_STRING_CONVERTER;
    this.nodesToStringConverter = COREMAP_LIST_TO_STRING_CONVERTER;
  }

}
