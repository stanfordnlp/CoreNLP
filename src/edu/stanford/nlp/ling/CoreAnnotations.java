package edu.stanford.nlp.ling;

import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.trees.ud.CoNLLUFeatures;
import edu.stanford.nlp.util.*;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * Set of common annotations for {@link CoreMap}s. The classes
 * defined here are typesafe keys for getting and setting annotation
 * values. These classes need not be instantiated outside of this
 * class. e.g {@link TextAnnotation}.class serves as the key and a
 * {@code String} serves as the value containing the
 * corresponding word.
 * <p>
 * New types of {@link CoreAnnotation} can be defined anywhere that is
 * convenient in the source tree - they are just classes. This file exists to
 * hold widely used "core" annotations and others inherited from the
 * {@link Label} family. In general, most keys should be placed in this file as
 * they may often be reused throughout the code. This architecture allows for
 * flexibility, but in many ways it should be considered as equivalent to an
 * enum in which everything should be defined
 * <p>
 * The getType method required by CoreAnnotation must return the same class type
 * as its value type parameter. It feels like one should be able to get away
 * without that method, but because Java erases the generic type signature, that
 * info disappears at runtime. See {@link ValueAnnotation} for an example.
 *
 * @author dramage
 * @author rafferty
 * @author bethard
 */
public class CoreAnnotations {

  private CoreAnnotations() { } // only static members

  /**
   * The CoreMap key identifying the annotation's text.
   *
   * Note that this key is intended to be used with many different kinds of
   * annotations - documents, sentences and tokens all have their own text.
   */
  public static class TextAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }


  /**
   * The CoreMap key for getting the lemma (morphological stem, lexeme form) of a token.
   *
   * This key is typically set on token annotations.
   */
  public static class LemmaAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key for getting the Penn part of speech of a token.
   *
   * This key is typically set on token annotations.
   */
  public static class PartOfSpeechAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key for getting the token-level named entity tag (e.g., DATE,
   * PERSON, etc.)
   *
   * This key is typically set on token annotations.
   */
  public static class NamedEntityTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Label and probability pair representing the coarse grained label and probability
   */
  public static class NamedEntityTagProbsAnnotation implements CoreAnnotation<Map<String,Double>> {
    @Override
    public Class<Map<String,Double>> getType() {
      return ErasureUtils.uncheckedCast(Map.class);
    }
  }

  /**
   * The CoreMap key for getting the coarse named entity tag (i.e. LOCATION)
   */
  public static class CoarseNamedEntityTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key for getting the fine grained named entity tag (i.e. CITY)
   */
  public static class FineGrainedNamedEntityTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key for getting the token-level named entity tag (e.g., DATE,
   * PERSON, etc.) from a previous NER tagger. NERFeatureFactory is sensitive to
   * this tag and will turn the annotations from the previous NER tagger into
   * new features. This is currently used to implement one level of stacking --
   * we may later change it to take a list as needed.
   *
   * This key is typically set on token annotations.
   */
  public static class StackedNamedEntityTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key for getting the token-level true case annotation (e.g.,
   * INIT_UPPER)
   *
   * This key is typically set on token annotations.
   */
  public static class TrueCaseAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key identifying the annotation's true-cased text.
   *
   * Note that this key is intended to be used with many different kinds of
   * annotations - documents, sentences and tokens all have their own text.
   */
  public static class TrueCaseTextAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key for getting the tokens contained by an annotation.
   *
   * This key should be set for any annotation that contains tokens. It can be
   * done without much memory overhead using List.subList.
   */
  public static class TokensAnnotation implements CoreAnnotation<List<CoreLabel>> {
    @Override
    public Class<List<CoreLabel>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * The CoreMap key for getting the tokens (can be words, phrases or anything that are of type CoreMap) contained by an annotation.
   *
   * This key should be set for any annotation that contains tokens (words, phrases etc). It can be
   * done without much memory overhead using List.subList.
   */
  public static class GenericTokensAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * The CoreMap key for getting the sentences contained in an annotation.
   * The sentences are represented as a {@code List<CoreMap>}.
   * Each sentence might typically have annotations such as {@code TextAnnotation},
   * {@code TokensAnnotation}, {@code SentenceIndexAnnotation}, and {@code BasicDependenciesAnnotation}.
   *
   * This key is typically set only on document annotations.
   */
  public static class SentencesAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * The CoreMap key for getting the quotations contained by an annotation.
   *
   * This key is typically set only on document annotations.
   */
  public static class QuotationsAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * The CoreMap key for getting the quotations contained by an annotation.
   *
   * This key is typically set only on document annotations.
   */
  public static class UnclosedQuotationsAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * Unique identifier within a document for a given quotation. Counts up from zero.
   */
  public static class QuotationIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * The index of the sentence that this annotation begins in. Currently only used by quote attribution.
   * Set to the SentenceIndexAnnotation of the first sentence of a quote.
   */
  public static class SentenceBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * The index of the sentence that this annotation begins in.
   */
  public static class SentenceEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }


  /**
   * The CoreMap key for getting the paragraphs contained by an annotation.
   *
   * This key is typically set only on document annotations.
   */
  public static class ParagraphsAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * The CoreMap key identifying the first token included in an annotation. The
   * token with index 0 is the first token in the document.
   *
   * This key should be set for any annotation that contains tokens.
   */
  public static class TokenBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * The CoreMap key identifying the last token after the end of an annotation.
   * The token with index 0 is the first token in the document.
   *
   * This key should be set for any annotation that contains tokens.
   */
  public static class TokenEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * The CoreMap key identifying the date and time associated with an
   * annotation.
   *
   * This key is typically set on document annotations.
   */
  public static class CalendarAnnotation implements CoreAnnotation<Calendar> {
    @Override
    public Class<Calendar> getType() {
      return Calendar.class;
    }
  }

  /*
   * These are the keys hashed on by IndexedWord
   */

  /**
   * This refers to the unique identifier for a "document", where document may
   * vary based on your application.
   */
  public static class DocIDAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * This indexes a token number inside a sentence.  Standardly, tokens are
   * indexed within a sentence starting at 1 (not 0: we follow common parlance
   * whereby we speak of the first word of a sentence).
   * This is generally an individual word or feature index - it is local, and
   * may not be uniquely identifying without other identifiers such as sentence
   * and doc. However, if these are the same, the index annotation should be a
   * unique identifier for differentiating objects.
   */
  public static class IndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Some datasets - for example, the UD Estonian EWT dataset - use
   * "empty" nodes to represent words that were unspoken / unwritten
   * but can be inferred from the structure of the sentence.  For
   * example, in English, one could say "Gimme" instead of "Give me
   * it", and "it" could be treated as an empty word.  A more common
   * example is when it is used in a similar manner to the copy nodes,
   * but displaced in time.  So, for example, a sentence which uses
   * them in the UD English EWT dataset (no relation) is:
   *<br>
   * "Over 300 Iraqis are reported dead and 500 wounded in Fallujah alone."
   *<br>
   * Here, one could build a dependency graph using "reported" as a
   * copy node, but instead the en_ewt dataset creates an "empty" node
   * and builds the enhanced dependencies using that node.
   *<br>
   * "Over 300 Iraqis are reported dead and 500 *reported* wounded in Fallujah alone."
   *<br>
   * Rather than the second "reported" being a copy of word 5, it is treated
   * as a separate word 8.1
   *<br>
   * As with IndexAnnotation, we count from 1.
   */
  public static class EmptyIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * This represents a list of the empty words.  We can attach to the
   * Sentence CoreMap when reading CoNLLU files with such empty words
   * <br>
   * See the desctiption of EmptyIndexAnnotation for more explanation
   * of when this is relevant
   */
  public static class EmptyTokensAnnotation implements CoreAnnotation<List<CoreLabel>> {
    @Override
    public Class<List<CoreLabel>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * This indexes the beginning of a span of words, e.g., a constituent in a
   * tree. See {@link edu.stanford.nlp.trees.Tree#indexSpans(int)}.
   * This annotation counts tokens.
   * It standardly indexes from 1 (like IndexAnnotation).  The reasons for
   * this are: (i) Talking about the first word of a sentence is kind of
   * natural, and (ii) We use index 0 to refer to an imaginary root in
   * dependency output.
   */
  public static class BeginIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * This indexes the end of a span of words, e.g., a constituent in a
   * tree.  See {@link edu.stanford.nlp.trees.Tree#indexSpans(int)}. This annotation
   * counts tokens.  It standardly indexes from 1 (like IndexAnnotation).
   * The end index is not a fencepost: its value is equal to the
   * IndexAnnotation of the last word in the span.
   */
  public static class EndIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * This indicates that starting at this token, the sentence should not be ended until
   * we see a ForcedSentenceEndAnnotation.  Used to force the ssplit annotator
   * (eg the WordToSentenceProcessor) to keep tokens in the same sentence
   * until ForcedSentenceEndAnnotation is seen.
   */
  public static class ForcedSentenceUntilEndAnnotation
          implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * This indicates the sentence should end at this token.  Used to
   * force the ssplit annotator (eg the WordToSentenceProcessor) to
   * start a new sentence at the next token.
   */
  public static class ForcedSentenceEndAnnotation
  implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * Unique identifier within a document for a given sentence. Counts up starting from zero.
   */
  public static class SentenceIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Line number for a sentence in a document delimited by newlines
   * instead of punctuation.  May skip numbers if there are blank
   * lines not represented as sentences.  Indexed from 1 rather than 0.
   */
  public static class LineNumberAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Contains the "value" - an ill-defined string used widely in MapLabel.
   */
  public static class ValueAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class CategoryAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The exact original surface form of a token.  This is created in the
   * invertible PTBTokenizer. The tokenizer may normalize the token form to
   * match what appears in the PTB, but this key will hold the original characters.
   */
  public static class OriginalTextAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Annotation for the whitespace characters appearing before this word. This
   * can be filled in by an invertible tokenizer so that the original text string can be
   * reconstructed.
   */
  public static class BeforeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Annotation for the whitespace characters appear after this word. This can
   * be filled in by an invertible tokenizer so that the original text string can be
   * reconstructed.
   *
   * Note: When running a tokenizer token-by-token, in general this field will only
   * be filled in after the next token is read, so you need to be reading this field
   * one behind. Be careful about this.
   */
  public static class AfterAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Comments on the sentence, such as the ones attached to CoNLLU sentences
   */
  public static class CommentsAnnotation implements CoreAnnotation<List<String>> {
    @Override
    public Class<List<String>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * CoNLL dep parsing - coarser POS tags.
   */
  public static class CoarseTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * CoNLL dep parsing - the dependency type, such as SBJ or OBJ. This should be unified with CoNLLDepTypeAnnotation.
   */
  public static class CoNLLDepAnnotation implements CoreAnnotation<CoreMap> {
    @Override
    public Class<CoreMap> getType() {
      return CoreMap.class;
    }
  }

  /**
   * CoNLL SRL/dep parsing - whether the word is a predicate
   */
  public static class CoNLLPredicateAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * CoNLL SRL/dep parsing - map which, for the current word, specifies its
   * specific role for each predicate
   */
  public static class CoNLLSRLAnnotation implements CoreAnnotation<Map<Integer,String>> {
    @Override
    public Class<Map<Integer,String>> getType() {
      return ErasureUtils.uncheckedCast(Map.class);
    }
  }

  /**
   * CoNLL dep parsing - the dependency type, such as SBJ or OBJ. This should be unified with CoNLLDepAnnotation.
   */
  public static class CoNLLDepTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * CoNLL-U dep parsing - span of multiword tokens
   */
  public static class CoNLLUTokenSpanAnnotation implements CoreAnnotation<IntPair> {
    @Override
    public Class<IntPair> getType() {
      return IntPair.class;
    }
  }

  /**
   * CoNLL-U dep parsing - List of secondary dependencies
   */
  public static class CoNLLUSecondaryDepsAnnotation implements CoreAnnotation<HashMap<String,String>> {
    @Override
    public Class<HashMap<String,String>> getType() {
      return ErasureUtils.uncheckedCast(HashMap.class);
    }
  }

  /**
   * CoNLL-U dep parsing - Map of morphological features
   */
  public static class CoNLLUFeats implements CoreAnnotation<CoNLLUFeatures> {
    @Override
    public Class<CoNLLUFeatures> getType() {
      return CoNLLUFeatures.class;
    }
  }

  /**
   * CoNLL-U dep parsing - Any other annotation
   */
  public static class CoNLLUMisc implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * CoNLL dep parsing - the index of the word which is the parent of this word
   * in the dependency tree
   */
  public static class CoNLLDepParentIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Inverse document frequency of the word this label represents
   */
  public static class IDFAnnotation implements CoreAnnotation<Double> {
    @Override
    public Class<Double> getType() {
      return Double.class;
    }
  }

  /**
   * The standard key for a propbank label which is of type Argument
   */
  public static class ArgumentAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Another key used for propbank - to signify core arg nodes or predicate
   * nodes
   */
  public static class MarkingAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for Semantic Head Word which is a String
   */
  public static class SemanticHeadWordAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for Semantic Head Word POS which is a String
   */
  public static class SemanticHeadTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Probank key for the Verb sense given in the Propbank Annotation, should
   * only be in the verbnode
   */
  public static class VerbSenseAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for storing category with functional tags.
   */
  public static class CategoryFunctionalTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * This is an NER ID annotation (in case the all caps parsing didn't work out
   * for you...)
   */
  public static class NERIDAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The key for the normalized value of numeric named entities.
   */
  public static class NormalizedNamedEntityTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public enum SRL_ID {
    ARG, NO, ALL_NO, REL
  }

  /**
   * The key for semantic role labels (Note: please add to this description if
   * you use this key)
   */
  public static class SRLIDAnnotation implements CoreAnnotation<SRL_ID> {
    @Override
    public Class<SRL_ID> getType() {
      return SRL_ID.class;
    }
  }

  /**
   * The standard key for the "shape" of a word: a String representing the type
   * of characters in a word, such as "Xx" for a capitalized word. See
   * {@link edu.stanford.nlp.process.WordShapeClassifier} for functions for
   * making shape strings.
   */
  public static class ShapeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The Standard key for storing the left terminal number relative to the root
   * of the tree of the leftmost terminal dominated by the current node
   */
  public static class LeftTermAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * The standard key for the parent which is a String
   */
  public static class ParentAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class INAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for span which is an IntPair
   */
  public static class SpanAnnotation implements CoreAnnotation<IntPair> {
    @Override
    public Class<IntPair> getType() {
      return IntPair.class;
    }
  }

  /**
   * The standard key for the answer which is a String
   */
  public static class AnswerAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The matching probability for the AnswerAnnotation
   */
  public static class AnswerProbAnnotation implements CoreAnnotation<Double> {
    @Override
    public Class<Double> getType() {
      return Double.class;
    }
  }

  /**
   * The standard key for the answer which is a String
   */
  public static class PresetAnswerAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for gold answer which is a String
   */
  public static class GoldAnswerAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for the features which is a Collection
   */
  public static class FeaturesAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for the semantic interpretation
   */
  public static class InterpretationAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for the semantic role label of a phrase.
   */
  public static class RoleAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The standard key for the gazetteer information
   */
  public static class GazetteerAnnotation implements CoreAnnotation<List<String>> {
    @Override
    public Class<List<String>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * Stem of the word this label represents. (This means the output of an IR-style stemmer,
   * such as the Porter stemmer, not a lemma.
   */
  public static class StemAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PolarityAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MorphoNumAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MorphoPersAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MorphoGenAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MorphoCaseAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * For Chinese: character level information, segmentation. Used for representing
   * a single character as a token.
   */
  public static class ChineseCharAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** For Chinese: the segmentation info existing in the original text. */
  public static class ChineseOrigSegAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** For Chinese: the segmentation information from the segmenter.
   *  Either a "1" for a new word starting at this position or a "0".
   */
  public static class ChineseSegAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Not sure exactly what this is, but it is different from
   * ChineseSegAnnotation and seems to indicate if the text is segmented
   */
  public static class ChineseIsSegmentedAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * for Arabic: character level information, segmentation
   */
  public static class ArabicCharAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** For Arabic: the segmentation information from the segmenter. */
  public static class ArabicSegAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key identifying the offset of the first char of an
   * annotation. The char with index 0 is the first char in the
   * document.
   *
   * Note that these are currently measured in terms of UTF-16 char offsets, not codepoints,
   * so that when non-BMP Unicode characters are present, such a character will add 2 to
   * the position. On the other hand, these values will work with String#substring() and
   * you can then calculate the number of codepoints in a substring.
   *
   * This key should be set for any annotation that represents a span of text.
   */
  public static class CharacterOffsetBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * The CoreMap key identifying the offset of the last character after the end
   * of an annotation. The character with index 0 is the first character in the
   * document.
   *
   * Note that these are currently measured in terms of UTF-16 char offsets, not codepoints,
   * so that when non-BMP Unicode characters are present, such a character will add 2 to
   * the position. On the other hand, these values will work with String#substring() and
   * you can then calculate the number of codepoints in a substring.
   *
   * This key should be set for any annotation that represents a span of text.
   */
  public static class CharacterOffsetEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Some codepoints count as more than one character.  For example,
   * mathematical symbols.  This can cause serious problems in other
   * languages such as Python which see those characters as one
   * character wide.
   * <br>
   * This annotation is how many codepoints to the beginning of the text.
   */
  public static class CodepointOffsetBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Some codepoints count as more than one character.  For example,
   * mathematical symbols.  This can cause serious problems in other
   * languages such as Python which see those characters as one
   * character wide.
   * <br>
   * This annotation is how many codepoints to the end of the text.
   */
  public static class CodepointOffsetEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }


  /**
   * Key for relative value of a word - used in RTE
   */
  public static class CostMagnificationAnnotation implements CoreAnnotation<Double> {
    @Override
    public Class<Double> getType() {
      return Double.class;
    }
  }

  public static class WordSenseAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class SRLInstancesAnnotation implements CoreAnnotation<List<List<Pair<String, Pair>>>> {
    @Override
    public Class<List<List<Pair<String, Pair>>>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * Used by RTE to track number of text sentences, to determine when hyp
   * sentences begin.
   */
  public static class NumTxtSentencesAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * Used in Trees
   */
  public static class TagLabelAnnotation implements CoreAnnotation<Label> {
    @Override
    public Class<Label> getType() {
      return Label.class;
    }
  }

  /**
   * Used in CRFClassifier stuff PositionAnnotation should possibly be an int -
   * it's present as either an int or string depending on context CharAnnotation
   * may be "CharacterAnnotation" - not sure
   */
  public static class DomainAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PositionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class CharAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** Note: this is not a catchall "unknown" annotation but seems to have a
   *  specific meaning for sequence classifiers
   */
  public static class UnknownAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class IDAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** Possibly this should be grouped with gazetteer annotation - original key
   *  was "gaz".
   */
  public static class GazAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PossibleAnswersAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class DistSimAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class AbbrAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class ChunkAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class GovernorAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class AbgeneAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class GeniaAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class AbstrAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class FreqAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class DictAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class WebAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class FemaleGazAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MaleGazAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class LastGazAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * it really seems like this should have a different name or else be a boolean
   */
  public static class IsURLAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class LinkAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class MentionsAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /** index into the list of entity mentions in a document **/
  public static class EntityMentionIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return ErasureUtils.uncheckedCast(Integer.class);
    }
  }

  /** Index into the list of entity mentions in a document for canonical entity mention.
   *  This is primarily for linking entity mentions to their canonical entity mention.
   */
  public static class CanonicalEntityMentionIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return ErasureUtils.uncheckedCast(Integer.class);
    }
  }

  /**
   * mapping from coref mentions to corresponding ner derived entity mentions
   */
  public static class CorefMentionToEntityMentionMappingAnnotation implements CoreAnnotation<Map<Integer,Integer>> {
    @Override
    public Class<Map<Integer,Integer>> getType() {
      return ErasureUtils.uncheckedCast(Map.class);
    }
  }

  /**
   * Mapping from NER-derived entity mentions to coref mentions.
   */
  public static class EntityMentionToCorefMentionMappingAnnotation implements CoreAnnotation<Map<Integer,Integer>> {
    @Override
    public Class<Map<Integer,Integer>> getType() {
      return ErasureUtils.uncheckedCast(Map.class);
    }
  }

  public static class EntityTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * it really seems like this should have a different name or else be a boolean
   */
  public static class IsDateRangeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class PredictedAnswerAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** Seems like this could be consolidated with something else... */
  public static class OriginalCharAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class UTypeAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  public static class EntityRuleAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Store a list of sections in the document
   */
  public static class SectionsAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() { return ErasureUtils.uncheckedCast(List.class); }
  }

  /**
   * Store an index into a list of sections
   */
  public static class SectionIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() { return ErasureUtils.uncheckedCast(Integer.class); }
  }

  /**
   * Store the beginning of the author mention for this section
   */
  public static class SectionAuthorCharacterOffsetBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() { return ErasureUtils.uncheckedCast(Integer.class); }
  }

  /**
   * Store the end of the author mention for this section
   */
  public static class SectionAuthorCharacterOffsetEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() { return ErasureUtils.uncheckedCast(Integer.class); }
  }

  /**
   * Store the xml tag for the section as a CoreLabel
   */
  public static class SectionTagAnnotation implements CoreAnnotation<CoreLabel> {
    @Override
    public Class<CoreLabel> getType() { return ErasureUtils.uncheckedCast(CoreLabel.class); }
  }

  /**
   * Store a list of CoreMaps representing quotes
   */
  public static class QuotesAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() { return ErasureUtils.uncheckedCast(List.class); }
  }

  /**
   * Indicate whether a sentence is quoted
   */
  public static class QuotedAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * Section of a document
   */
  public static class SectionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Date for a section of a document
   */
  public static class SectionDateAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Id for a section of a document
   */
  public static class SectionIDAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Indicates that the token starts a new section and the attributes
   *   that should go into that section
   */
  public static class SectionStartAnnotation implements CoreAnnotation<CoreMap> {
    @Override
    public Class<CoreMap> getType() {
      return CoreMap.class;
    }
  }

  /**
   * Indicates that the token end a section and the label of the section
   */
  public static class SectionEndAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class WordPositionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class ParaPositionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class SentencePositionAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  // Why do both this and sentenceposannotation exist? I don't know, but one
  // class
  // uses both so here they remain for now...
  public static class SentenceIDAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class EntityClassAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class AnswerObjectAnnotation implements CoreAnnotation<Object> {
    @Override
    public Class<Object> getType() {
      return Object.class;
    }
  }

  /**
   * Used in Task3 Pascal system
   */
  public static class BestCliquesAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class BestFullAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class LastTaggedAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Used in wsd.supwsd package
   */
  public static class LabelAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class NeighborsAnnotation implements CoreAnnotation<List<Pair<WordLemmaTag, String>>> {
    @Override
    public Class<List<Pair<WordLemmaTag, String>>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  public static class ContextsAnnotation implements CoreAnnotation<List<Pair<String, String>>> {
    @Override
    public Class<List<Pair<String, String>>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  public static class DependentsAnnotation implements
  CoreAnnotation<List<Pair<Triple<String, String, String>, String>>> {
    @Override
    public Class<List<Pair<Triple<String, String, String>, String>>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  public static class WordFormAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class TrueTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class SubcategorizationAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class BagOfWordsAnnotation implements CoreAnnotation<List<Pair<String, String>>> {
    @Override
    public Class<List<Pair<String, String>>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * Used in srl.unsup
   */
  public static class HeightAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class LengthAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Used in Gale2007ChineseSegmenter
   */
  public static class LBeginAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class LMiddleAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class LEndAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class D2_LBeginAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class D2_LMiddleAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class D2_LEndAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class UBlockAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** Used in Chinese segmenters for whether there was space before a character. */
  public static class SpaceBeforeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /*
   * Used in parser.discrim
   */

  /**
   * The base version of the parser state, like NP or VBZ or ...
   */
  public static class StateAnnotation implements CoreAnnotation<CoreLabel> {
    @Override
    public Class<CoreLabel> getType() {
      return CoreLabel.class;
    }
  }

  /**
   * used in binarized trees to say the name of the most recent child
   */
  public static class PrevChildAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * used in binarized trees to specify the first child in the rule for which
   * this node is the parent
   */
  public static class FirstChildAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * whether the node is the parent in a unary rule
   */
  public static class UnaryAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * annotation stolen from the lex parser
   */
  public static class DoAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * annotation stolen from the lex parser
   */
  public static class HaveAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * annotation stolen from the lex parser
   */
  public static class BeAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * annotation stolen from the lex parser
   */
  public static class NotAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * annotation stolen from the lex parser
   */
  public static class PercentAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * specifies the base state of the parent of this node in the parse tree
   */
  public static class GrandparentAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The key for storing a Head word as a string rather than a pointer (as in
   * TreeCoreAnnotations.HeadWordAnnotation)
   */
  public static class HeadWordStringAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Used in nlp.coref
   */
  public static class MonthAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class DayAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class YearAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Used in propbank.srl
   */
  public static class PriorAnnotation implements CoreAnnotation<Map<String, Double>> {
    @Override
    public Class<Map<String, Double>> getType() {
      return ErasureUtils.uncheckedCast(Map.class);
    }
  }

  public static class SemanticWordAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class SemanticTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class CovertIDAnnotation implements CoreAnnotation<List<IntPair>> {
    @Override
    public Class<List<IntPair>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  public static class ArgDescendentAnnotation implements CoreAnnotation<Pair<String, Double>> {

    @Override
    public Class<Pair<String, Double>> getType() {
      return ErasureUtils.uncheckedCast(Pair.class);
    }
  }

  /**
   * Used in SimpleXMLAnnotator. The value is an XML element name String for the
   * innermost element in which this token was contained.
   */
  public static class XmlElementAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Used in CleanXMLAnnotator.  The value is a list of XML element names indicating
   * the XML tag the token was nested inside.
   */
  public static class XmlContextAnnotation implements CoreAnnotation<List<String>> {

    @Override
    public Class<List<String>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   *
   * Used for Topic Assignments from LDA or its equivalent models. The value is
   * the topic ID assigned to the current token.
   *
   */
  public static class TopicAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  // gets the synonymn of a word in the Wordnet (use a bit differently in sonalg's code)
  public static class WordnetSynAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  //to get words of the phrase
  public static class PhraseWordsTagAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  //to get pos tag of the phrase i.e. root of the phrase tree in the parse tree
  public static class PhraseWordsAnnotation implements CoreAnnotation<List<String>> {
    @Override
    public Class<List<String>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  //to get prototype feature, see Haghighi Exemplar driven learning
  public static class ProtoAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  //which common words list does this word belong to
  public static class CommonWordsAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  // Document date
  // Needed by SUTime
  public static class DocDateAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Document type
   * What kind of document is it: story, multi-part article, listing, email, etc
   */
  public static class DocTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Document source type
   * What kind of place did the document come from: newswire, discussion forum, web...
   */
  public static class DocSourceTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Document title
   * What is the document title
   */
  public static class DocTitleAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Reference location for the document
   */
  public static class LocationAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * Author for the document
   * (really should be a set of authors, but just have single string for simplicity)
   */
  public static class AuthorAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  // Numeric annotations

  // Per token annotation indicating whether the token represents a NUMBER or ORDINAL
  // (twenty first => NUMBER ORDINAL)
  public static class NumericTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  // Per token annotation indicating the numeric value of the token
  // (twenty first => 20 1)
  public static class NumericValueAnnotation implements CoreAnnotation<Number> {
    @Override
    public Class<Number> getType() {
      return Number.class;
    }
  }

  // Per token annotation indicating the numeric object associated with an annotation
  public static class NumericObjectAnnotation implements CoreAnnotation<Object> {
    @Override
    public Class<Object> getType() {
      return Object.class;
    }
  }

  /** Annotation indicating whether the numeric phrase the token is part of
   * represents a NUMBER or ORDINAL (twenty first {@literal =>} ORDINAL ORDINAL).
   */
  public static class NumericCompositeValueAnnotation implements CoreAnnotation<Number> {
    @Override
    public Class<Number> getType() {
      return Number.class;
    }
  }

  /** Annotation indicating the numeric value of the phrase the token is part of
   * (twenty first {@literal =>} 21 21 ).
   */
  public static class NumericCompositeTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /** Annotation indicating the numeric object associated with an annotation. */
  public static class NumericCompositeObjectAnnotation implements CoreAnnotation<Object> {
    @Override
    public Class<Object> getType() {
      return Object.class;
    }
  }

  public static class NumerizedTokensAnnotation implements CoreAnnotation<List<CoreMap>> {
    @Override
    public Class<List<CoreMap>> getType() {
      return ErasureUtils.uncheckedCast(List.class);
    }
  }

  /**
   * used in dcoref.
   * to indicate that the it should use the discourse information annotated in the document
   */
  public static class UseMarkedDiscourseAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * used in dcoref.
   * to store discourse information. (marking {@code <TURN>} or quotation)
   */
  public static class UtteranceAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * used in dcoref.
   * to store speaker information.
   */
  public static class SpeakerAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * used to store speaker type information for coref
   */
  public static class SpeakerTypeAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * used in dcoref.
   * to store paragraph information.
   */
  public static class ParagraphAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * used in ParagraphAnnotator.
   * to store paragraph information.
   */
  public static class ParagraphIndexAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  /**
   * used in dcoref.
   * to store premarked entity mentions.
   */
  public static class MentionTokenAnnotation implements CoreAnnotation<MultiTokenTag> {
    @Override
    public Class<MultiTokenTag> getType() {
      return MultiTokenTag.class;
    }
  }

  /**
   * used in incremental DAG parser
   */
  public static class LeftChildrenNodeAnnotation implements CoreAnnotation<SortedSet<Pair<CoreLabel, String>>> {
    @Override
    public Class<SortedSet<Pair<CoreLabel, String>>> getType() {
      return ErasureUtils.uncheckedCast(SortedSet.class);
    }
  }

  /**
   * Stores an exception associated with processing this document
   */
  public static class ExceptionAnnotation implements CoreAnnotation<Throwable> {
    @Override
    public Class<Throwable> getType() {
      return ErasureUtils.uncheckedCast(Throwable.class);
    }
  }


  /**
   * The CoreMap key identifying the annotation's antecedent.
   *
   * The intent of this annotation is to go with words that have been
   * linked via coref to some other entity.  For example, if "dog" is
   * corefed to "cirrus" in the sentence "Cirrus, a small dog, ate an
   * entire pumpkin pie", then "dog" would have the
   * AntecedentAnnotation "cirrus".
   *
   * This annotation is currently used ONLY in the KBP slot filling project.
   * In that project, "cirrus" from the example above would also have an
   * AntecedentAnnotation of "cirrus".
   * Generally, you want to use the usual coref graph annotations
   */
  public static class AntecedentAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  public static class LabelWeightAnnotation implements CoreAnnotation<Double>{
   @Override
   public Class<Double> getType(){
     return Double.class;
   }
  }

  public static class ColumnDataClassifierAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() { return String.class; }
  }

  public static class LabelIDAnnotation implements CoreAnnotation<Integer>{
    @Override
    public Class<Integer> getType() { return Integer.class; }
  }

  /**
   * An annotation for a sentence tagged with its KBP relation.
   * Attaches to a sentence.
   *
   * @see edu.stanford.nlp.pipeline.KBPAnnotator
   */
  public static class KBPTriplesAnnotation implements CoreAnnotation<List<RelationTriple>>{
    @Override
    public Class<List<RelationTriple>> getType() { return ErasureUtils.uncheckedCast(List.class); }
  }

  /**
   * An annotation for the Wikipedia page (i.e., canonical name) associated with
   * this token.
   * This is the recommended annotation to use for entity linking that links to Wikipedia.
   * Attaches to a token, as well as to a mention (see (@link MentionsAnnotation}).
   *
   * @see edu.stanford.nlp.pipeline.WikidictAnnotator
   */
  public static class WikipediaEntityAnnotation implements CoreAnnotation<String>{
    @Override
    public Class<String> getType() { return ErasureUtils.uncheckedCast(String.class); }
  }


  /**
   * The CoreMap key identifying the annotation's text, as formatted by the
   * {@link edu.stanford.nlp.naturalli.QuestionToStatementTranslator}.
   *
   * This is attached to {@link CoreLabel}s.
   */
  public static class StatementTextAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreMap key identifying an entity mention's potential gender.
   *
   * This is attached to {@link CoreMap}s.
   */
  public static class GenderAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreLabel key identifying whether a token is a newline or not
   *
   * This is attached to {@link CoreLabel}s.
   */
  public static class IsNewlineAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {return Boolean.class;}
  }

  /**
   * The CoreLabel key identifying whether a token is a multi-word-token
   *
   * This is attached to {@link CoreLabel}s.
   */
  public static class IsMultiWordTokenAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {return Boolean.class;}
  }

  /**
   * Text of the token that was used to create this word during a multi word token split.
   */
  public static class MWTTokenTextAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }

  /**
   * The CoreLabel key identifying whether a token is the first word derived
   * from a multi-word-token.  So if "des" is split into "de" and "les", "de"
   * would be marked as true.
   */
  public static class IsFirstWordOfMWTAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }

  /**
   * CoNLL-U misc features specifically on the MWT part of a token rather than the word
   */
  public static class MWTTokenMiscAnnotation implements CoreAnnotation<String> {
    @Override
    public Class<String> getType() {
      return String.class;
    }
  }
}
