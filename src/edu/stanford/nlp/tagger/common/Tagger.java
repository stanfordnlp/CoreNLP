package edu.stanford.nlp.tagger.common;

import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;
import java.util.function.Function;
import edu.stanford.nlp.util.ReflectionLoading;

/**
 * This module includes constants that are the same for all taggers,
 * as opposed to being part of their configurations.
 * Also, can be used as an interface if you don't want to necessarily
 * include the MaxentTagger code, such as in public releases which
 * don't include that code.
 *
 * @author John Bauer
 */
public abstract class Tagger implements Function<List<? extends HasWord>,List<TaggedWord>> {

  public static final String EOS_TAG = ".$$.";
  public static final String EOS_WORD = ".$.";

  @Override
  public abstract List<TaggedWord> apply(List<? extends HasWord> in);

  public abstract Set<String> tagSet();

  public static Tagger loadModel(String path) {
    // TODO: we can avoid ReflectionLoading if we instead use the
    // serialization mechanism in MaxentTagger.  Similar to ParserGrammar
    return ReflectionLoading.loadByReflection("edu.stanford.nlp.tagger.maxent.MaxentTagger", path);
  }

}
