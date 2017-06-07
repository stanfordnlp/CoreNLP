package edu.stanford.nlp.trees.ud;

import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.function.Function;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;

/**
 * Reader for ConLL-U formatted dependency treebanks.
 *
 * @author Sebastian Schuster
 */
public class CoNLLUDocumentReader implements
    IteratorFromReaderFactory<SemanticGraph> {

  private static final String COMMENT_POS = "<COMMENT>";

  private IteratorFromReaderFactory<SemanticGraph> ifrf;

  public CoNLLUDocumentReader() {
    this.ifrf = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new SentenceProcessor());
  }


  @Override
  public Iterator<SemanticGraph> getIterator(Reader r) {
    return ifrf.getIterator(r);
  }


  private static final Comparator<IndexedWord> byIndex = (i1, i2) -> i1.compareTo(i2);

  /* Comparator for putting multiword tokens before regular tokens.  */
  private static final Comparator<IndexedWord> byType = (i1, i2) ->
          i1.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class) ? -1 :
                  i2.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class) ? 1 : 0;

  private static class SentenceProcessor implements Function<String,SemanticGraph> {

    private int lineNumberCounter = 0;

    private Pair<IndexedWord, GrammaticalRelation> getGovAndReln(int govIdx, IndexedWord word, String relationName,
                                                                 List<IndexedWord> sortedTokens) {
      IndexedWord gov;
      GrammaticalRelation reln;
      if (relationName.equals("root")) {
        reln = GrammaticalRelation.ROOT;
      } else {
        reln = GrammaticalRelation.valueOf(Language.UniversalEnglish, relationName);
      }
      if (govIdx == 0) {
        gov = new IndexedWord(word.docID(), word.sentIndex(), 0);
        gov.setValue("ROOT");
      } else {
        gov = sortedTokens.get(govIdx - 1);
      }
      return Generics.newPair(gov, reln);
    }

    public SemanticGraph apply(String line) {
      if (line == null) return null;

      Function<String,IndexedWord> func = new WordProcessor();
      ObjectBank<IndexedWord> words = ObjectBank.getLineIterator(new StringReader(line), func);

      List<IndexedWord> wordList = new ArrayList<>(words);

      List<IndexedWord> sorted = new ArrayList<>(wordList.size());

      List<String> comments = new LinkedList<>();

      /* Increase the line number in case there are comments before the actual sentence
       * and add them to the list of comments. */
      wordList.stream().filter(w -> w.tag() != null && w.tag().equals(COMMENT_POS))
              .forEach(w -> {
                lineNumberCounter++;
                comments.add(w.word());
              });

      wordList.stream().filter(w -> w.tag() == null || ! w.tag().equals(COMMENT_POS))
              .sorted(byIndex.thenComparing(byType))
              .forEach(w -> sorted.add(w));

      List<IndexedWord> sortedTokens = new ArrayList<>(wordList.size());
      sorted.stream()
              .filter(w -> !w.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class))
              .forEach(w -> sortedTokens.add(w));

      /* Construct a semantic graph. */
      List<TypedDependency> deps = new ArrayList<>(sorted.size());

      IntPair tokenSpan = null;
      String originalToken = null;
      for (IndexedWord word : sorted) {
        lineNumberCounter++;

        if (word.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class)) {
          tokenSpan = word.get(CoreAnnotations.CoNLLUTokenSpanAnnotation.class);
          originalToken = word.word();
        } else {
          /* Deal with multiword tokens. */
          if (tokenSpan != null && tokenSpan.getTarget() >= word.index()) {
            word.setOriginalText(originalToken);
            word.set(CoreAnnotations.CoNLLUTokenSpanAnnotation.class, tokenSpan);
          } else {
            tokenSpan = null;
            originalToken = null;
          }
          HashMap<Integer,String> extraDeps = word.get(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class);
          if (extraDeps.isEmpty()) {
            int govIdx = word.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class);
            Pair<IndexedWord, GrammaticalRelation> govReln = getGovAndReln(govIdx, word,
                word.get(CoreAnnotations.CoNLLDepTypeAnnotation.class), sortedTokens);
            IndexedWord gov = govReln.first();
            GrammaticalRelation reln  = govReln.second();
            TypedDependency dep = new TypedDependency(reln, gov, word);
            word.set(CoreAnnotations.LineNumberAnnotation.class, lineNumberCounter);
            deps.add(dep);
          } else {
            for (Integer extraGovIdx : extraDeps.keySet()) {
              int mainGovIdx = word.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class);
              Pair<IndexedWord, GrammaticalRelation> govReln = getGovAndReln(extraGovIdx, word,
                  extraDeps.get(extraGovIdx), sortedTokens);
              IndexedWord gov = govReln.first();
              GrammaticalRelation reln = govReln.second();
              TypedDependency dep = new TypedDependency(reln, gov, word);
              if (extraGovIdx != mainGovIdx) {
                dep.setExtra();
              }
              deps.add(dep);
            }
          }
        }
      }
      lineNumberCounter++;

      SemanticGraph sg = new SemanticGraph(deps);

      comments.forEach(c -> sg.addComment(c));

      return sg;
    }
  }

  private static class WordProcessor implements Function<String,IndexedWord> {
    public IndexedWord apply(String line) {


      IndexedWord word = new IndexedWord();
      if (line.startsWith("#")) {
        word.setWord(line);
        word.setTag(COMMENT_POS);
        return word;
      }


      String[] bits = line.split("\\s+");

      word.set(CoreAnnotations.TextAnnotation.class, bits[1]);

      /* Check if it is a multiword token. */
      if (bits[0].contains("-")) {
        String[] span = bits[0].split("-");
        Integer start = Integer.parseInt(span[0]);
        Integer end = Integer.parseInt(span[1]);
        word.set(CoreAnnotations.CoNLLUTokenSpanAnnotation.class, new IntPair(start, end));
        word.set(CoreAnnotations.IndexAnnotation.class, start);
      } else {
        word.set(CoreAnnotations.IndexAnnotation.class, Integer.parseInt(bits[0]));
        word.set(CoreAnnotations.LemmaAnnotation.class, bits[2]);
        word.set(CoreAnnotations.CoarseTagAnnotation.class, bits[3]);
        word.set(CoreAnnotations.PartOfSpeechAnnotation.class, bits[4]);

        word.set(CoreAnnotations.CoNLLDepParentIndexAnnotation.class, Integer.parseInt(bits[6]));
        word.set(CoreAnnotations.CoNLLDepTypeAnnotation.class, bits[7]);
        word.set(CoreAnnotations.CoNLLUMisc.class, bits[9]);

        word.setIndex(Integer.parseInt(bits[0]));
        word.setValue(bits[1]);

        /* Parse features. */
        HashMap<String, String> features = CoNLLUUtils.parseFeatures(bits[5]);
        word.set(CoreAnnotations.CoNLLUFeats.class, features);

        /* Parse extra dependencies. */
        HashMap<Integer,String> extraDeps = CoNLLUUtils.parseExtraDeps(bits[8]);
        word.set(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class, extraDeps);
      }

    return word;
    }
  }
}
