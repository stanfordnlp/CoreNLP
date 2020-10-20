package edu.stanford.nlp.quoteattribution;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by michaelf on 12/30/15. Adapted from Grace Muzny's codebase
 */
public class BammanCorefReader {
  /**
   * The main output here is data/tokens/dickens.oliver.tokens, which contains the original book, one token per line, with part of speech, syntax, NER, coreference and other annotations. The (tab-separated) format is:
   *
   * Paragraph id
   * Sentence id
   * Token id
   * Byte start
   * Byte end
   * Whitespace following the token (useful for pretty-printing the original text)
   * Syntactic head id (-1 for the sentence root)
   * Original token
   * Normalized token (for quotes etc.)
   * Lemma
   * Penn Treebank POS tag
   * NER tag (PERSON, NUMBER, DATE, DURATION, MISC, TIME, LOCATION, ORDINAL, MONEY, ORGANIZATION, SET, O)
   * Stanford basic dependency label
   * Within-quotation flag
   * Character id (all coreferent tokens share the same character id)
   *
   * @param filename
   */
  public static Map<Integer, List<CoreLabel>> readTokenFile(String filename, Annotation novel) {
    List<String> lines = IOUtils.linesFromFile(filename);
    Map<Integer, List<CoreLabel>> charsToTokens = new HashMap<>();
    boolean first = true;
    int tokenOffset = 0;
    for (String line : lines) {
      if (first) {
        first = false;
        continue;
      }
      String[] pieces = line.split("\t");
      int tokenId = Integer.parseInt(pieces[2]) + tokenOffset;


      String token = pieces[7];
      String normalizedTok = pieces[8];
      int characterId = Integer.parseInt(pieces[14]);
      CoreLabel novelTok = novel.get(CoreAnnotations.TokensAnnotation.class).get(tokenId);
      // CoreNLP sometimes splits ". . . ." as ". . ." and "." and sometimes lemmatizes it. (The Steppe)
      if(pieces[7].equals(". . . .") && !novelTok.get(CoreAnnotations.OriginalTextAnnotation.class).equals(". . . .")) {
        tokenOffset++;
      }



      if (characterId != -1) {
        if (!novelTok.get(CoreAnnotations.TextAnnotation.class).equals(normalizedTok)) {
          System.err.println(token + " != " + novelTok.get(CoreAnnotations.TextAnnotation.class));
        } else {
          if (!charsToTokens.containsKey(characterId)) {
            charsToTokens.put(characterId, new ArrayList<>());
          }
          charsToTokens.get(characterId).add(novelTok);
        }
      }
    }
    return charsToTokens;
  }
}
