package edu.stanford.nlp.process;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.util.ArrayCoreMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Iterables;
import edu.stanford.nlp.util.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static edu.stanford.nlp.process.TSVUtils.unescapeSQL;

/**
 * Reads sentences from a TSV, provided a list of fields to populate.
 *
 * @author Arun Chaganty
 */
public class TSVSentenceIterator implements Iterator<Sentence> {

  /** A list of possible fields in the sentence table */
  public enum SentenceField {
    ID,
    DEPENDENCIES_BASIC,
    DEPENDENCIES_COLLAPSED,
    DEPENDENCIES_COLLAPSED_CC,
    DEPENDENCIES_ALTERNATE,
    WORDS,
    LEMMAS,
    POS_TAGS,
    NER_TAGS,
    DOC_ID,
    SENTENCE_INDEX,
    CORPUS_ID,
    DOC_CHAR_BEGIN,
    DOC_CHAR_END,
    GLOSS,
    IGNORE; // Ignore this field.

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


  private final Iterator<List<String>> source;
  private final List<SentenceField> fields;

  public TSVSentenceIterator(Iterator<List<String>> recordSource, List<SentenceField> fields) {
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
      String value = unescapeSQL(entry.second);
      switch (field) {
        case WORDS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) {
            tokens = Optional.of(new ArrayList<>(values.size()));
            for (int i = 0; i < values.size(); i++) tokens.get().add(new CoreLabel());
          }

          int beginChar = 0;
          for (int i = 0; i < values.size(); i++) {
            tokens.get().get(i).setValue(values.get(i));
            tokens.get().get(i).setWord(values.get(i));
            tokens.get().get(i).setBeginPosition(beginChar);
            tokens.get().get(i).setEndPosition(beginChar + values.get(i).length());
            beginChar += values.get(i).length() + 1;
          }
        } break;
        case LEMMAS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) {
            tokens = Optional.of(new ArrayList<>(values.size()));
            for (int i = 0; i < values.size(); i++) tokens.get().add(new CoreLabel());
          }
          for (int i = 0; i < values.size(); i++) {
            tokens.get().get(i).setLemma(values.get(i));
          }
        } break;
        case POS_TAGS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) {
            tokens = Optional.of(new ArrayList<>(values.size()));
            for (int i = 0; i < values.size(); i++) tokens.get().add(new CoreLabel());
          }
          for (int i = 0; i < values.size(); i++) {
            tokens.get().get(i).setTag(values.get(i));
          }
        } break;
        case NER_TAGS: {
          List<String> values = TSVUtils.parseArray(value);
          if (!tokens.isPresent()) {
            tokens = Optional.of(new ArrayList<>(values.size()));
            for (int i = 0; i < values.size(); i++) tokens.get().add(new CoreLabel());
          }
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
      String value = unescapeSQL(entry.second);
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
    map.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex.orElse(0));

    // Doc-char
    if(tokens.isPresent()) {
      for (Pair<SentenceField, String> entry : Iterables.zip(fields, entries)) {
        SentenceField field = entry.first;
        String value = unescapeSQL(entry.second);
        switch (field) {
          case DOC_CHAR_BEGIN: {
            List<String> values = TSVUtils.parseArray(value);
            for (int i = 0; i < tokens.get().size(); i++) {
              tokens.get().get(i).setBeginPosition(Integer.parseInt(values.get(i)));
            }
          } break;
          case DOC_CHAR_END: {
            List<String> values = TSVUtils.parseArray(value);
            for (int i = 0; i < tokens.get().size(); i++) {
              tokens.get().get(i).setEndPosition(Integer.parseInt(values.get(i)));
            }
          } break;
          default: // ignore.
            break;
        }
      }
    }

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

    // Dependency trees
    if(tokens.isPresent()) {
      map.set(CoreAnnotations.TokensAnnotation.class, tokens.get());
      map.set(CoreAnnotations.TokenBeginAnnotation.class, 0);
      map.set(CoreAnnotations.TokenEndAnnotation.class, tokens.get().size());

      for (Pair<SentenceField, String> entry : Iterables.zip(fields, entries)) {
        SentenceField field = entry.first;
        String value = unescapeSQL(entry.second);
        switch (field) {
          case DEPENDENCIES_BASIC: {
            SemanticGraph graph = TSVUtils.parseJsonTree(value, tokens.get());
            map.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
//            if (!map.containsKey(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class))
//              map.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, graph);
//            if (!map.containsKey(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class))
//              map.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, graph);
          } break;
          case DEPENDENCIES_COLLAPSED: {
            SemanticGraph graph = TSVUtils.parseJsonTree(value, tokens.get());
            map.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, graph);
          } break;
          case DEPENDENCIES_COLLAPSED_CC: {
            SemanticGraph graph = TSVUtils.parseJsonTree(value, tokens.get());
//            if (!map.containsKey(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class))
//              map.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
//            map.set(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class, graph);
            map.set(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class, graph);
          } break;
          case DEPENDENCIES_ALTERNATE: {
            SemanticGraph graph = TSVUtils.parseJsonTree(value, tokens.get());
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
