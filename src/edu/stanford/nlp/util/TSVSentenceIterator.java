package edu.stanford.nlp.util;

import edu.stanford.nlp.io.RecordIterator;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.simple.Sentence;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * Reads sentences from a TSV, provided a list of fields to populate.
 */
public class TSVSentenceIterator implements Iterator<Sentence> {

  /** A list of possible fields in the sentence table */
  enum SentenceField {
    ID,
    DEPENDENCIES_STANFORD,
    DEPENDENCIES_EXTRAS,
    DEPENDENCIES_MALT,
    DEPENDENCIES_MALT_ALT1,
    DEPENDENCIES_MALT_ALT2,
    WORDS,
    LEMMAS,
    POS_TAGS,
    NER_TAGS,
    DOC_ID,
    SENTENCE_INDEX,
    CORPUS_ID,
    DOC_CHAR_BEGIN,
    DOC_CHAR_END,
    GLOSS;

    public boolean isToken() {
      switch(this) {
        case WORDS:
        case LEMMAS:
        case POS_TAGS:
        case NER_TAGS:
          return true;
        default:
          return false;
      }
    }
  }


  private final RecordIterator source;
  private final List<SentenceField> fields;

  public TSVSentenceIterator(RecordIterator recordSource, List<SentenceField> fields) {
    this.source = recordSource;
    this.fields = fields;
  }

  /**
   * Populates the fields of a sentence
   * @param fields
   * @param entries
   * @return
   */
  public static Sentence toSentence(List<SentenceField> fields, List<String> entries) {
    return new Sentence(toCoreMap(fields, entries));
  }

  public static CoreMap toCoreMap(List<SentenceField> fields, List<String> entries) {
    CoreMap map = new ArrayCoreMap(fields.size());
    Optional<List<CoreLabel>> tokens = Optional.empty();

    // First pass - process all token level stuff.
    for(Pair<SentenceField, String> entry : Iterables.zip(fields, entries)) {
      SentenceField field = entry.first;
      String value = entry.second;
      switch (field) {
        case WORDS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) tokens = Optional.of(new ArrayList<>(values.size()));
          int beginChar = 0;
          for (int i = 0; i < values.size(); i++) {
            tokens.get().get(i).setWord(values.get(i));
            tokens.get().get(i).setBeginPosition(beginChar);
            tokens.get().get(i).setEndPosition(beginChar + values.get(i).length());
            beginChar += values.get(i).length() + 1;
          }
        } break;
        case LEMMAS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) tokens = Optional.of(new ArrayList<>(values.size()));
          for (int i = 0; i < values.size(); i++) {
            tokens.get().get(i).setLemma(values.get(i));
          }
        } break;
        case POS_TAGS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) tokens = Optional.of(new ArrayList<>(values.size()));
          for (int i = 0; i < values.size(); i++) {
            tokens.get().get(i).setTag(values.get(i));
          }
        } break;
        case NER_TAGS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) tokens = Optional.of(new ArrayList<>(values.size()));
          for (int i = 0; i < values.size(); i++) {
            tokens.get().get(i).setNER(values.get(i));
          }
        } break;
        default: // ignore.
          break;
      }
    }

    // Document specific stuff.
    Optional<String> docId = Optional.empty();
    Optional<String> sentenceId = Optional.empty();
    Optional<Integer> sentenceIndex = Optional.empty();

    for(Pair<SentenceField, String> entry : Iterables.zip(fields, entries)) {
      SentenceField field = entry.first;
      String value = entry.second;
      switch (field) {
        case ID:
          sentenceId = Optional.of(value);
          break;
        case DOC_ID:
          docId = Optional.of(value);
          break;
        case SENTENCE_INDEX:
          sentenceIndex = Optional.of(Integer.parseInt(value));
          break;
        case GLOSS:
          value = value.replace("\\n", "\n").replace("\\t", "\t");
          map.set(CoreAnnotations.TextAnnotation.class, value);
          break;
        default: // ignore.
          break;
      }
    }
    // High level document stuff
    map.set(CoreAnnotations.SentenceIDAnnotation.class, sentenceId.orElse("-1"));
    map.set(CoreAnnotations.DocIDAnnotation.class, docId.orElse("???"));
    map.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex.orElse(-1));

    // Final token level stuff.
    if (tokens.isPresent()) {
      for (int i = 0; i < tokens.get().size(); i++) {
        tokens.get().get(i).set(CoreAnnotations.DocIDAnnotation.class, docId.orElse("???"));
        tokens.get().get(i).set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex.orElse(-1));
        tokens.get().get(i).set(CoreAnnotations.IndexAnnotation.class, i+1);
        tokens.get().get(i).set(CoreAnnotations.TokenBeginAnnotation.class, i);
        tokens.get().get(i).set(CoreAnnotations.TokenEndAnnotation.class, i+1);
      }
    }
    if(tokens.isPresent()) {
      map.set(CoreAnnotations.TokensAnnotation.class, tokens.get());
      map.set(CoreAnnotations.TokenBeginAnnotation.class, 0);
      map.set(CoreAnnotations.TokenEndAnnotation.class, tokens.get().size());

      for (Pair<SentenceField, String> entry : Iterables.zip(fields, entries)) {
        SentenceField field = entry.first;
        String value = entry.second;
        switch (field) {
          case DEPENDENCIES_STANFORD: {
            SemanticGraph graph = TSVUtils.parseTree(value, tokens.get());
            map.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
            if (!map.containsKey(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class))
              map.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, graph);
            if (!map.containsKey(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class))
              map.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, graph);
          } break;
          case DEPENDENCIES_EXTRAS: {
            SemanticGraph graph = TSVUtils.parseTree(value, tokens.get());
            if (!map.containsKey(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class))
              map.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
            map.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, graph);
            map.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, graph);
          } break;
          case DEPENDENCIES_MALT:
          case DEPENDENCIES_MALT_ALT1:
          case DEPENDENCIES_MALT_ALT2: {
            SemanticGraph graph = TSVUtils.parseTree(value, tokens.get());
            map.set(SemanticGraphCoreAnnotations.AlternativeDependenciesAnnotation.class, graph);
          } break;
          default: // ignore.
            break;
        }
      }
    }

    return map;
  }


  @Override
  public boolean hasNext() {
    return source.hasNext();
  }

  @Override
  public Sentence next() {
    return toSentence(fields, source.next());
  }
}
