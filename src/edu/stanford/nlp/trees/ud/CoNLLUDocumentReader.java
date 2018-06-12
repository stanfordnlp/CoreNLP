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
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;

/**
 * Reader for ConLL-U formatted dependency treebanks.
 *
 * @author Sebastian Schuster
 */
public class CoNLLUDocumentReader implements IteratorFromReaderFactory<SemanticGraph> {

  private static final String COMMENT_POS = "<COMMENT>";

  private static final long serialVersionUID = -7340310509954331983L;

  private IteratorFromReaderFactory<SemanticGraph> ifrf;

  public CoNLLUDocumentReader() {
    this.ifrf = DelimitRegExIterator.getFactory("\n(\\s*\n)+", new SentenceProcessor());
  }


  @Override
  public Iterator<SemanticGraph> getIterator(Reader r) {
    return ifrf.getIterator(r);
  }


  private static final Comparator<IndexedWord> byIndex = Comparator.naturalOrder();

  /** Comparator for putting multiword tokens before regular tokens.  */
  private static final Comparator<IndexedWord> byType = (i1, i2) ->
          i1.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class) ? -1 :
                  i2.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class) ? 1 : 0;

  private static class SentenceProcessor implements Function<String,SemanticGraph> {

    private int lineNumberCounter = 0;

    private static Pair<IndexedWord, GrammaticalRelation> getGovAndReln(int govIdx,
                                                                        int copyCount,
                                                                        IndexedWord word, String relationName,
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
        gov = SentenceProcessor.getToken(sortedTokens, govIdx, copyCount);
      }
      return Generics.newPair(gov, reln);
    }

    private static IndexedWord getToken(List<IndexedWord> sortedTokens, int index) {
      return SentenceProcessor.getToken(sortedTokens, index, 0);
    }


    private static IndexedWord getToken(List<IndexedWord> sortedTokens, int index, int copyCount) {


      int tokenLength = sortedTokens.size();
      for (int i = index - 1 ; i < tokenLength; i++) {
        IndexedWord token = sortedTokens.get(i);
        if (token.index() == index && token.copyCount() == copyCount) {
          return token;
        }
      }
      return null;
    }


    @Override
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
              .forEach(sorted::add);

      List<IndexedWord> sortedTokens = new ArrayList<>(wordList.size());
      sorted.stream()
              .filter(w -> !w.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class))
              .filter(w -> w.copyCount() == 0)
              .forEach(sortedTokens::add);

      sorted.stream()
          .filter(w -> !w.containsKey(CoreAnnotations.CoNLLUTokenSpanAnnotation.class))
          .filter(w -> w.copyCount() != 0)
          .forEach(w -> sortedTokens.add(sortedTokens.get(w.index() - 1).makeSoftCopy(w.copyCount())));

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
          HashMap<String,String> extraDeps = word.get(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class);
          if (extraDeps.isEmpty()) {
            int govIdx = word.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class);
            Pair<IndexedWord, GrammaticalRelation> govReln = getGovAndReln(govIdx, 0, word,
                word.get(CoreAnnotations.CoNLLDepTypeAnnotation.class), sortedTokens);
            IndexedWord gov = govReln.first();
            GrammaticalRelation reln  = govReln.second();
            TypedDependency dep = new TypedDependency(reln, gov, word);
            word.set(CoreAnnotations.LineNumberAnnotation.class, lineNumberCounter);
            deps.add(dep);
          } else {
            for (String extraGovIdxStr : extraDeps.keySet()) {
              if (extraGovIdxStr.contains(".")) {
                String[] indexParts = extraGovIdxStr.split("\\.");
                Integer extraGovIdx = Integer.parseInt(indexParts[0]);
                Integer copyCount = Integer.parseInt(indexParts[1]);
                Pair<IndexedWord, GrammaticalRelation> govReln = getGovAndReln(extraGovIdx, copyCount, word,
                    extraDeps.get(extraGovIdxStr), sortedTokens);
                IndexedWord gov = govReln.first();
                GrammaticalRelation reln = govReln.second();
                TypedDependency dep = new TypedDependency(reln, gov, word);
                dep.setExtra();
                deps.add(dep);
              } else {
                int extraGovIdx = Integer.parseInt(extraGovIdxStr);
                int mainGovIdx = word.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class) != null ?
                    word.get(CoreAnnotations.CoNLLDepParentIndexAnnotation.class) : -1;
                Pair<IndexedWord, GrammaticalRelation> govReln = getGovAndReln(extraGovIdx, 0, word,
                    extraDeps.get(extraGovIdxStr), sortedTokens);
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
      }
      lineNumberCounter++;

      SemanticGraph sg = new SemanticGraph(deps);

      comments.forEach(sg::addComment);

      return sg;
    }
  }

  private static class WordProcessor implements Function<String,IndexedWord> {

    @Override
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
      } else if(bits[0].contains(".")) {
        String[] indexParts = bits[0].split("\\.");
        Integer index = Integer.parseInt(indexParts[0]);
        Integer copyCount = Integer.parseInt(indexParts[1]);
        word.set(CoreAnnotations.IndexAnnotation.class, index);
        word.setIndex(index);
        word.setCopyCount(copyCount);
        word.setValue(bits[1]);

        /* Parse features. */
        HashMap<String, String> features = CoNLLUUtils.parseFeatures(bits[5]);
        word.set(CoreAnnotations.CoNLLUFeats.class, features);

        /* Parse extra dependencies. */
        HashMap<String,String> extraDeps = CoNLLUUtils.parseExtraDeps(bits[8]);
        word.set(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class, extraDeps);
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
        HashMap<String,String> extraDeps = CoNLLUUtils.parseExtraDeps(bits[8]);
        word.set(CoreAnnotations.CoNLLUSecondaryDepsAnnotation.class, extraDeps);
      }

      return word;
    }

  } // end static class WordProcessor

}
