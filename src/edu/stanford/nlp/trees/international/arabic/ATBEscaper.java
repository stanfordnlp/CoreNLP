package edu.stanford.nlp.trees.international.arabic;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import java.util.function.Function;

/**
 * Escapes an Arabic string by replacing ATB reserved words with the appropriate
 * escape sequences. This class is appropriate for use in {@link edu.stanford.nlp.parser.lexparser.LexicalizedParser}
 * using the <code>-escaper</code> command-line parameter.
 *
 * @author Spence Green
 *
 */
public class ATBEscaper implements Function<List<HasWord>, List<HasWord>> {

  public List<HasWord> apply(List<HasWord> in) {
    List<HasWord> escaped = new ArrayList<>(in);
    for (HasWord word : escaped) {
      word.setWord(ATBTreeUtils.escape(word.word()));
    }
    return escaped;
  }

}
