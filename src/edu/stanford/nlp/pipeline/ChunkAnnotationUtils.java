package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.AnnotationLookup;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.CoreTokenFactory;
import edu.stanford.nlp.util.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for annotating chunks
 *
 * @author Angel Chang
 */
public class ChunkAnnotationUtils {
  private static final Logger logger = Logger.getLogger(ChunkAnnotationUtils.class.getName());
  private static CoreLabelTokenFactory tokenFactory = new CoreLabelTokenFactory(true);

  /**
   * Checks if offsets of doc and sentence matches
   * @param docAnnotation
   * @return true if the offsets match, false otherwise
   */
  public static boolean checkOffsets(CoreMap docAnnotation)
  {
    boolean okay = true;
    String docText = docAnnotation.get(CoreAnnotations.TextAnnotation.class);
    String docId = docAnnotation.get(CoreAnnotations.DocIDAnnotation.class);
    List<CoreLabel> docTokens = docAnnotation.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> sentences = docAnnotation.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap sentence:sentences) {
      String sentText = sentence.get(CoreAnnotations.TextAnnotation.class);
      List<CoreLabel> sentTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      int sentBeginChar = sentence.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int sentEndChar = sentence.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      int sentBeginToken = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
      int sentEndToken = sentence.get(CoreAnnotations.TokenEndAnnotation.class);
      String docTextSpan = docText.substring(sentBeginChar, sentEndChar);
      List<CoreLabel> docTokenSpan = new ArrayList<CoreLabel>(docTokens.subList(sentBeginToken, sentEndToken));
      logger.finer("Checking Document " + docId + " span (" + sentBeginChar + "," + sentEndChar + ") ");
      if (!docTextSpan.equals(sentText) ) {
        okay = false;
        logger.finer("WARNING: Document " + docId + " span does not match sentence");
        logger.finer("DocSpanText: " + docTextSpan);
        logger.finer("SentenceText: " + sentText);
      }
      String sentTokenStr = getTokenText(sentTokens, CoreAnnotations.TextAnnotation.class);
      String docTokenStr = getTokenText(docTokenSpan, CoreAnnotations.TextAnnotation.class);
      if (!docTokenStr.equals(sentTokenStr) ) {
        okay = false;
        logger.finer("WARNING: Document " + docId + " tokens does not match sentence");
        logger.finer("DocSpanTokens: " + docTokenStr);
        logger.finer("SentenceTokens: " + sentTokenStr);
      }
    }
    return okay;
  }

  /**
   * Fix token offsets of sentences to match those in the document (assumes tokens are shared)
   * sentence token indices may not match document token list if certain html elements are ignored
   * @param docAnnotation
   * @return true if fix was okay, false otherwise
   */
  public static boolean fixTokenOffsets(CoreMap docAnnotation)
  {
    List<CoreLabel> docTokens = docAnnotation.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> sentences = docAnnotation.get(CoreAnnotations.SentencesAnnotation.class);
    int i = 0;
    CoreLabel curDocToken = docTokens.get(0);
    for (CoreMap sentence:sentences) {
      List<CoreLabel> sentTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      CoreLabel sentTokenFirst = sentTokens.get(0);
      while (curDocToken != sentTokenFirst) {
        i++;
        if (i >= docTokens.size()) { return false; }
        curDocToken = docTokens.get(i);
      }
      int sentTokenBegin = i;
      CoreLabel sentTokenLast = sentTokens.get(sentTokens.size()-1);
      while (curDocToken != sentTokenLast) {
        i++;
        if (i >= docTokens.size()) { return false; }
        curDocToken = docTokens.get(i);
      }
      int sentTokenEnd = i+1;
      sentence.set(CoreAnnotations.TokenBeginAnnotation.class, sentTokenBegin);
      sentence.set(CoreAnnotations.TokenEndAnnotation.class, sentTokenEnd);
    }
    return true;
  }


  /**
   * Copies annotation over to this coremap if not already set
   */
  public static void copyUnsetAnnotations(CoreMap src, CoreMap dest) {
    for (Class key : src.keySet()) {
      if (!dest.has(key)) {
        dest.set(key, src.get(key));
      }
    }
  }

  /**
   * Give an list of character offsets for chunk, fix tokenization so tokenization occurs at
   * boundary of chunks
   * @param docAnnotation
   * @param chunkCharOffsets
   */
  public static boolean fixChunkTokenBoundaries(CoreMap docAnnotation, List<IntPair> chunkCharOffsets)
  {
    // First identify any tokens that need to be fixed
    String text = docAnnotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreLabel> tokens = docAnnotation.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreLabel> output = new ArrayList<CoreLabel>(tokens.size());
    int i = 0;
    CoreLabel token = tokens.get(i);
    for (IntPair offsets:chunkCharOffsets) {
      assert(token.beginPosition() >= 0);
      assert(token.endPosition() >= 0);
      int offsetBegin = offsets.getSource();
      int offsetEnd = offsets.getTarget();
      // Find tokens where token begins after chunk starts
      // and token ends after chunk starts
      while (offsetBegin < token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)
              || offsetBegin >= token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)) {
        output.add(token);
        i++;
        if (i >= tokens.size()) { return false; }
        token = tokens.get(i);
      }
      // offsetBegin is now >= token begin and < token end
      // go until we find a token that starts after our chunk has ended
      while (offsetEnd > token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)) {
        // Check if chunk includes token
        if (offsetBegin > token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)) {
          // Chunk starts in the middle of the token
          if (offsetEnd < token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)) {
            output.add(tokenFactory.makeToken(text.substring(token.beginPosition(), offsetBegin),
                    token.beginPosition(), offsetBegin-token.beginPosition()));
            output.add(tokenFactory.makeToken(text.substring(offsetBegin,offsetEnd),
                    offsetBegin, offsetEnd-offsetBegin));
            output.add(tokenFactory.makeToken(text.substring(offsetEnd,token.endPosition()),
                    offsetEnd, token.endPosition()-offsetEnd));
          } else {
            output.add(tokenFactory.makeToken(text.substring(token.beginPosition(), offsetBegin),
                    token.beginPosition(), offsetBegin-token.beginPosition()));
            output.add(tokenFactory.makeToken(text.substring(offsetBegin,token.endPosition()),
                    offsetBegin, token.endPosition()-offsetBegin));
          }
        } else if (offsetEnd < token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)) {
          output.add(tokenFactory.makeToken(text.substring(token.beginPosition(),offsetEnd),
                  token.beginPosition(), offsetEnd-token.beginPosition()));
          output.add(tokenFactory.makeToken(text.substring(offsetEnd,token.endPosition()), offsetEnd,
                  token.endPosition()-offsetEnd));
        } else {
          // success!  chunk contains token
          output.add(token);
        }
        i++;
        if (i >= tokens.size()) { return false; }
        token = tokens.get(i);
      }
    }
    // Add rest of the tokens
    for (; i < tokens.size(); i++) {
      token = tokens.get(i);
      output.add(token);
    }
    docAnnotation.set(CoreAnnotations.TokensAnnotation.class, output);
    return true;
  }

  /**
   * Create chunk that is merged from chunkIndexStart to chunkIndexEnd (exclusive)
   * @param chunkList - List of chunks
   * @param origText - Text from which to extract chunk text
   * @param chunkIndexStart - Index of first chunk to merge
   * @param chunkIndexEnd - Index of last chunk to merge (exclusive)
   * @return new merged chunk
   */
  public static CoreMap getMergedChunk(List<? extends CoreMap> chunkList, String origText,
                                       int chunkIndexStart, int chunkIndexEnd)
  {
    CoreMap firstChunk = chunkList.get(chunkIndexStart);
    CoreMap lastChunk = chunkList.get(chunkIndexEnd-1);
    int firstCharOffset = firstChunk.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int lastCharOffset = lastChunk.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    int firstTokenIndex = firstChunk.get(CoreAnnotations.TokenBeginAnnotation.class);
    int lastTokenIndex = lastChunk.get(CoreAnnotations.TokenEndAnnotation.class);

    String chunkText = origText.substring(firstCharOffset, lastCharOffset);
    CoreMap newChunk = new Annotation(chunkText);

    newChunk.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, firstCharOffset);
    newChunk.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, lastCharOffset);
    newChunk.set(CoreAnnotations.TokenBeginAnnotation.class, firstTokenIndex);
    newChunk.set(CoreAnnotations.TokenEndAnnotation.class, lastTokenIndex);
    List<CoreLabel> tokens = new ArrayList<CoreLabel>(lastTokenIndex-firstTokenIndex);
    for (int i = chunkIndexStart; i < chunkIndexEnd; i++) {
      CoreMap chunk = chunkList.get(i);
      tokens.addAll(chunk.get(CoreAnnotations.TokensAnnotation.class));
    }
    newChunk.set(CoreAnnotations.TokensAnnotation.class, tokens);
    // TODO: merge other keys into this new chunk ??

    return newChunk;
  }

  /**
   * Create chunk that is merged from chunkIndexStart to chunkIndexEnd (exclusive)
   * @param chunkList - List of chunks
   * @param chunkIndexStart - Index of first chunk to merge
   * @param chunkIndexEnd - Index of last chunk to merge (exclusive)
   * @param aggregators - Aggregators
   * @return new merged chunk
   */
  public static CoreMap getMergedChunk(List<? extends CoreMap> chunkList,
                                       int chunkIndexStart, int chunkIndexEnd,
                                       Map<Class, CoreMapAttributeAggregator> aggregators)
  {
    CoreMap newChunk = new Annotation("");
    for (Map.Entry<Class,CoreMapAttributeAggregator> entry:aggregators.entrySet()) {
      if (chunkIndexEnd > chunkList.size()) {
        assert(false);
      }
      Object value = entry.getValue().aggregate(entry.getKey(), chunkList.subList(chunkIndexStart, chunkIndexEnd));
      newChunk.set(entry.getKey(), value);
    }
    return newChunk;
  }

  /**
   * Return chunk offsets
   * @param chunkList - List of chunks
   * @param charStart - character begin offset
   * @param charEnd - character end offset
   * @return chunk offsets
   */
  public static Interval<Integer> getChunkOffsetsUsingCharOffsets(List<? extends CoreMap> chunkList,
                                       int charStart, int charEnd)
  {
    int chunkStart = 0;
    int chunkEnd = chunkList.size();
    // Find first chunk with start > charStart
    for (int i = 0; i < chunkList.size(); i++) {
      int start = chunkList.get(i).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      if (start > charStart) {
        break;
      }
      chunkStart = i;
    }
    // Find first chunk with start >= charEnd
    for (int i = chunkStart; i < chunkList.size(); i++) {
      int start = chunkList.get(i).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      if (start >= charEnd) {
        chunkEnd = i;
        break;
      }
    }
    return Interval.toInterval(chunkStart, chunkEnd, Interval.INTERVAL_OPEN_END);
  }


  /**
   * Merge chunks from chunkIndexStart to chunkIndexEnd (exclusive) and replace them in the list
   * @param chunkList - List of chunks
   * @param origText - Text from which to extract chunk text
   * @param chunkIndexStart - Index of first chunk to merge
   * @param chunkIndexEnd - Index of last chunk to merge (exclusive)
   */
  public static void mergeChunks(List<CoreMap> chunkList, String origText,
                                 int chunkIndexStart, int chunkIndexEnd)
  {
    CoreMap newChunk = getMergedChunk(chunkList, origText, chunkIndexStart, chunkIndexEnd);
    int nChunksToRemove = chunkIndexEnd - chunkIndexStart - 1;
    for (int i = 0; i < nChunksToRemove; i++) {
      chunkList.remove(chunkIndexStart);
    }
    chunkList.set(chunkIndexStart, newChunk);
  }

  public static Character getFirstNonWsChar(CoreMap sent)
  {
    String sentText = sent.get(CoreAnnotations.TextAnnotation.class);
    for (int j = 0; j < sentText.length(); j++) {
      char c = sentText.charAt(j);
      if (!Character.isWhitespace(c)) {
        return c;
      }
    }
    return null;
  }

  public static Integer getFirstNonWsCharOffset(CoreMap sent, boolean relative)
  {
    String sentText = sent.get(CoreAnnotations.TextAnnotation.class);
    for (int j = 0; j < sentText.length(); j++) {
      char c = sentText.charAt(j);
      if (!Character.isWhitespace(c)) {
        if (relative) {
          return j;
        } else {
          return j + sent.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        }
      }
    }
    return null;
  }

  public static String getTrimmedText(CoreMap sent)
  {
    String sentText = sent.get(CoreAnnotations.TextAnnotation.class);
    return sentText.trim();
  }

  /**
   * Give an list of character offsets for chunk, fix sentence splitting
   * so sentences doesn't break the chunks
   * @param docAnnotation Document with sentences
   * @param chunkCharOffsets ordered pairs of different chunks that should appear in sentences
   * @return true if fix was okay (chunks are in all sentences), false otherwise
   */
  public static boolean fixChunkSentenceBoundaries(CoreMap docAnnotation, List<IntPair> chunkCharOffsets)
  {
    return fixChunkSentenceBoundaries(docAnnotation, chunkCharOffsets, false, false, false);
  }

  /**
   * Give an list of character offsets for chunk, fix sentence splitting
   * so sentences doesn't break the chunks
   * @param docAnnotation Document with sentences
   * @param chunkCharOffsets ordered pairs of different chunks that should appear in sentences
   * @param offsetsAreNotSorted Treat each pair of offsets as independent (look through all sentences again)
   * @param extendedFixSentence Do extended sentence fixing based on some heuristics
   * @param moreExtendedFixSentence Do even more extended sentence fixing based on some heuristics
   * @return true if fix was okay (chunks are in all sentences), false otherwise
   */
  public static boolean fixChunkSentenceBoundaries(CoreMap docAnnotation, List<IntPair> chunkCharOffsets,
                                                   boolean offsetsAreNotSorted,
                                                   boolean extendedFixSentence, boolean moreExtendedFixSentence)
  {
    String text = docAnnotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreMap> sentences = docAnnotation.get(CoreAnnotations.SentencesAnnotation.class);
    if (sentences == null || sentences.size() == 0) return true;
    if (chunkCharOffsets != null) {
      int i = 0;
      CoreMap sentence = sentences.get(i);
      for (IntPair offsets:chunkCharOffsets) {
        int offsetBegin = offsets.getSource();
        int offsetEnd = offsets.getTarget();
        // Find sentence where sentence begins after chunk starts
        // and sentence ends after chunk starts
        while (offsetBegin < sentence.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)
                || offsetBegin >= sentence.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)) {
          i++;
          if (i >= sentences.size()) { return false; }
          sentence = sentences.get(i);
        }
        // offsetBegin is now >= sentence begin and < sentence end
        // Check if sentence end includes chunk
        if (sentence.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) >= offsetEnd) {
          // success!  sentence contains chunk
        } else {
          // hmm, sentence contains beginning of chunk, but not end
          // Lets find sentence that contains end of chunk and merge sentences
          int startSentIndex = i;
          while (offsetEnd > sentence.get(CoreAnnotations.CharacterOffsetEndAnnotation.class)) {
            i++;
            if (i >= sentences.size()) { return false; }
            sentence = sentences.get(i);
          }
          Integer firstNonWsCharOffset = getFirstNonWsCharOffset(sentence, false);
          if (firstNonWsCharOffset != null && firstNonWsCharOffset >= offsetEnd) {
            // Ends before first real character of this sentence, don't include this sentence
            i--;
            sentence = sentences.get(i);
          }
          // Okay, now let's merge sentences from startSendIndex to i (includes i)
          mergeChunks(sentences, text, startSentIndex, i+1);
          // Reset our iterating index i to startSentIndex
          i = startSentIndex;
          sentence = sentences.get(i);
        }
        if (extendedFixSentence) {
          //System.err.println("Doing extended fixing of sentence:" + text.substring(offsetBegin,offsetEnd));
          if (i+1 < sentences.size()) {
            // Extended sentence fixing:
            // Check if entity is at the end of this sentence and if next sentence starts with uppercase
            // If not uppercase, merge with next sentence
            boolean entityAtSentEnd = true;
            int sentCharBegin = sentence.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
            String sentText = sentence.get(CoreAnnotations.TextAnnotation.class);
            int offsetEndInSentText = offsetEnd - sentCharBegin;
            for (int j = offsetEndInSentText; j < sentText.length(); j++) {
              char c = sentText.charAt(j);
              if (!Character.isWhitespace(c)) {
                entityAtSentEnd = false;
                break;
              }
            }
            boolean doMerge = false;
            if (entityAtSentEnd) {
              CoreMap nextSentence = sentences.get(i+1);
              Character c = getFirstNonWsChar(nextSentence);
              if (c != null) {
                doMerge = !Character.isUpperCase(c);
                if (!doMerge) {
                  logger.finer("No merge: c is '" + c + "'");
                }
              } else {
                logger.finer("No merge: no char");
              }
            } else {
              logger.finer("No merge: entity not at end");
            }
            if (doMerge) {
              logger.finer("Merge chunks");
              mergeChunks(sentences, text, i, i+2);
            }
          }
        }
        if (offsetsAreNotSorted) {
          i = 0;
        }
        sentence = sentences.get(i);
      }
    }
    // Do a bit more sentence fixing
    if (moreExtendedFixSentence) {
      int i = 0;
      while (i+1 < sentences.size()) {
        boolean doMerge = false;
        CoreMap sentence = sentences.get(i);
        CoreMap nextSentence = sentences.get(i+1);
        String sentTrimmedText = getTrimmedText(sentence);
        String nextSentTrimmedText = getTrimmedText(nextSentence);
        if (sentTrimmedText.length() <= 1 || nextSentTrimmedText.length() <= 1) {
          // Merge
          doMerge = true;
        } else {
 //         List<CoreLabel> sentTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
 //         CoreLabel lastSentToken = sentTokens.get(sentTokens.size()-1);
          Character c = getFirstNonWsChar(nextSentence);
 //         List<CoreLabel> nextSentTokens = nextSentence.get(CoreAnnotations.TokensAnnotation.class);
          if (c != null && !Character.isUpperCase(c)) {
            if (c == ',' || (Character.isLowerCase(c))) {
              doMerge = true;
            }
          }
        }
        if (doMerge) {
          mergeChunks(sentences, text, i, i+2);
        } else {
          i++;
        }
      }
    }
    // Set sentence indices
    for (int i = 0; i < sentences.size(); i++) {
      CoreMap sentence = sentences.get(i);
      sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, i);
    }
    return true;
  }
  /**
   * Annotates a CoreMap representing a chunk with basic chunk information
   *   CharacterOffsetBeginAnnotation - set to CharacterOffsetBeginAnnotation of first token in chunk
   *   CharacterOffsetEndAnnotation - set to CharacterOffsetEndAnnotation of last token in chunk
   *   TokensAnnotation - List of tokens in this chunk
   *   TokenBeginAnnotation - Index of first token in chunk (index in original list of tokens)
   *                          tokenStartIndex + totalTokenOffset
   *   TokenEndAnnotation - Index of last token in chunk (index in original list of tokens)
   *                          tokenEndIndex + totalTokenOffset
   * @param chunk - CoreMap to be annotated
   * @param tokens - List of tokens to look for chunks
   * @param tokenStartIndex - Index (relative to current list of tokens) at which this chunk starts
   * @param tokenEndIndex - Index (relative to current list of tokens) at which this chunk ends (not inclusive)
   * @param totalTokenOffset - Index of tokens to offset by
   */
  public static void annotateChunk(CoreMap chunk,
                                   List<CoreLabel> tokens, int tokenStartIndex, int tokenEndIndex,  int totalTokenOffset)
  {
    List<CoreLabel> chunkTokens = new ArrayList<CoreLabel>(tokens.subList(tokenStartIndex, tokenEndIndex));
    chunk.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class,
            chunkTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    chunk.set(CoreAnnotations.CharacterOffsetEndAnnotation.class,
            chunkTokens.get(chunkTokens.size()-1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    chunk.set(CoreAnnotations.TokensAnnotation.class, chunkTokens);
    chunk.set(CoreAnnotations.TokenBeginAnnotation.class, tokenStartIndex+totalTokenOffset);
    chunk.set(CoreAnnotations.TokenEndAnnotation.class, tokenEndIndex+totalTokenOffset);
  }

  public static String getTokenText(List<? extends CoreMap> tokens, Class tokenTextKey)
  {
    return getTokenText(tokens, tokenTextKey, " ");
  }
  public static String getTokenText(List<? extends CoreMap> tokens, Class tokenTextKey, String delimiter)
  {
    StringBuilder sb = new StringBuilder();
    for (CoreMap t: tokens) {
      if (sb.length() != 0) {
        sb.append(delimiter);
      }
      sb.append(t.get(tokenTextKey));
    }
    return sb.toString();
  }

  /**
   * Annotates a CoreMap representing a chunk with text information
   *   TextAnnotation - String representing tokens in this chunks (token text separated by space)
   * @param chunk - CoreMap to be annotated
   * @param tokenTextKey - Key to use to find the token text
   */
  public static void annotateChunkText(CoreMap chunk, Class tokenTextKey)
  {
    List<CoreLabel> chunkTokens = chunk.get(CoreAnnotations.TokensAnnotation.class);
    String text = getTokenText(chunkTokens, tokenTextKey);
    chunk.set(CoreAnnotations.TextAnnotation.class, text);
  }

  public static boolean hasCharacterOffsets(CoreMap chunk)
  {
    return (chunk.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) != null &&
            chunk.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) != null);
  }

  /**
   * Annotates a CoreMap representing a chunk with text information
   *   TextAnnotation - String extracted from the origAnnotation using character offset information for this chunk
   * @param chunk - CoreMap to be annotated
   * @param origAnnotation - Annotation from which to extract the text for this chunk
   */
  public static boolean annotateChunkText(CoreMap chunk, CoreMap origAnnotation)
  {
    String annoText = origAnnotation.get(CoreAnnotations.TextAnnotation.class);
    if (annoText == null) return false;
    if (!hasCharacterOffsets(chunk)) return false;
    Integer annoBeginCharOffset = origAnnotation.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    if (annoBeginCharOffset == null) { annoBeginCharOffset = 0; }
    int chunkBeginCharOffset = chunk.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) - annoBeginCharOffset;
    int chunkEndCharOffset = chunk.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) - annoBeginCharOffset;
    if (chunkBeginCharOffset < 0) {
      logger.fine("Adjusting begin char offset from " + chunkBeginCharOffset + " to 0");
      logger.fine("Chunk begin offset: " + chunk.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) +
        ", Source text begin offset " + annoBeginCharOffset);
      chunkBeginCharOffset = 0;
    }
    if (chunkBeginCharOffset > annoText.length()) {
      logger.fine("Adjusting begin char offset from " + chunkBeginCharOffset + " to " + annoText.length());
      logger.fine("Chunk begin offset: " + chunk.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) +
        ", Source text begin offset " + annoBeginCharOffset);
      chunkBeginCharOffset = annoText.length();
    }
    if (chunkEndCharOffset < 0) {
      logger.fine("Adjusting end char offset from " + chunkEndCharOffset + " to 0");
      logger.fine("Chunk end offset: " + chunk.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) +
        ", Source text begin offset " + annoBeginCharOffset);
      chunkEndCharOffset = 0;
    }
    if (chunkEndCharOffset > annoText.length()) {
      logger.fine("Adjusting end char offset from " + chunkEndCharOffset + " to " + annoText.length());
      logger.fine("Chunk end offset: " + chunk.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) +
        ", Source text begin offset " + annoBeginCharOffset);
      chunkEndCharOffset = annoText.length();
    }
    if (chunkEndCharOffset < chunkBeginCharOffset) {
      logger.fine("Adjusting end char offset from " + chunkEndCharOffset + " to " + chunkBeginCharOffset);
      logger.fine("Chunk end offset: " + chunk.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) +
        ", Source text begin offset " + annoBeginCharOffset);
      chunkEndCharOffset = chunkBeginCharOffset;
    }
    String chunkText = annoText.substring(chunkBeginCharOffset, chunkEndCharOffset);
    chunk.set(CoreAnnotations.TextAnnotation.class, chunkText);
    return true;
  }

  /**
   * Annotates tokens in chunk
   * @param chunk - CoreMap representing chunk (should have TextAnnotation and TokensAnnotation)
   * @param tokenChunkKey - If not null, each token is annotated with the chunk using this key
   * @param tokenLabelKey - If not null, each token is annotated with the text associated with the chunk using this key
   */
  public static void annotateChunkTokens(CoreMap chunk, Class tokenChunkKey, Class tokenLabelKey)
  {
    List<CoreLabel> chunkTokens = chunk.get(CoreAnnotations.TokensAnnotation.class);
    if (tokenLabelKey != null) {
      String text = chunk.get(CoreAnnotations.TextAnnotation.class);
      for (CoreLabel t: chunkTokens) {
        t.set(tokenLabelKey, text);
      }
    }
    if (tokenChunkKey != null) {
      for (CoreLabel t: chunkTokens) {
        t.set(tokenChunkKey, chunk);
      }
    }
  }

  /**
   * Create a new chunk Annotation with basic chunk information
   *   CharacterOffsetBeginAnnotation - set to CharacterOffsetBeginAnnotation of first token in chunk
   *   CharacterOffsetEndAnnotation - set to CharacterOffsetEndAnnotation of last token in chunk
   *   TokensAnnotation - List of tokens in this chunk
   *   TokenBeginAnnotation - Index of first token in chunk (index in original list of tokens)
   *                          tokenStartIndex + totalTokenOffset
   *   TokenEndAnnotation - Index of last token in chunk (index in original list of tokens)
   *                          tokenEndIndex + totalTokenOffset
   * @param tokens - List of tokens to look for chunks
   * @param tokenStartIndex - Index (relative to current list of tokens) at which this chunk starts
   * @param tokenEndIndex - Index (relative to current list of tokens) at which this chunk ends (not inclusive)
   * @param totalTokenOffset - Index of tokens to offset by
   * @return Annotation representing new chunk
   */
  public static Annotation getAnnotatedChunk(List<CoreLabel> tokens, int tokenStartIndex, int tokenEndIndex, int totalTokenOffset)
  {
    Annotation chunk = new Annotation("");
    annotateChunk(chunk, tokens, tokenStartIndex, tokenEndIndex, totalTokenOffset);
    return chunk;
  }

  /**
   * Create a new chunk Annotation with basic chunk information
   *   CharacterOffsetBeginAnnotation - set to CharacterOffsetBeginAnnotation of first token in chunk
   *   CharacterOffsetEndAnnotation - set to CharacterOffsetEndAnnotation of last token in chunk
   *   TokensAnnotation - List of tokens in this chunk
   *   TokenBeginAnnotation - Index of first token in chunk (index in original list of tokens)
   *                          tokenStartIndex + totalTokenOffset
   *   TokenEndAnnotation - Index of last token in chunk (index in original list of tokens)
   *                          tokenEndIndex + totalTokenOffset
   *   TextAnnotation - String extracted from the origAnnotation using character offset information for this chunk
   * @param tokens - List of tokens to look for chunks
   * @param tokenStartIndex - Index (relative to current list of tokens) at which this chunk starts
   * @param tokenEndIndex - Index (relative to current list of tokens) at which this chunk ends (not inclusive)
   * @param totalTokenOffset - Index of tokens to offset by
   * @param tokenChunkKey - If not null, each token is annotated with the chunk using this key
   * @param tokenTextKey - Key to use to find the token text
   * @param tokenLabelKey - If not null, each token is annotated with the text associated with the chunk using this key
   * @return Annotation representing new chunk
   */
  public static Annotation getAnnotatedChunk(List<CoreLabel> tokens, int tokenStartIndex, int tokenEndIndex, int totalTokenOffset,
                                             Class tokenChunkKey, Class tokenTextKey,  Class tokenLabelKey)
  {
    Annotation chunk = getAnnotatedChunk(tokens, tokenStartIndex, tokenEndIndex, totalTokenOffset);
    annotateChunkText(chunk, tokenTextKey);
    annotateChunkTokens(chunk, tokenChunkKey, tokenLabelKey);
    return chunk;
  }

  /**
   * Create a new chunk Annotation with basic chunk information
   *   CharacterOffsetBeginAnnotation - set to CharacterOffsetBeginAnnotation of first token in chunk
   *   CharacterOffsetEndAnnotation - set to CharacterOffsetEndAnnotation of last token in chunk
   *   TokensAnnotation - List of tokens in this chunk
   *   TokenBeginAnnotation - Index of first token in chunk (index in original list of tokens)
   *                          tokenStartIndex + annotation's TokenBeginAnnotation
   *   TokenEndAnnotation - Index of last token in chunk (index in original list of tokens)
   *                          tokenEndIndex + annotation's TokenBeginAnnotation
   *   TextAnnotation - String extracted from the origAnnotation using character offset information for this chunk
   * @param annotation - Annotation from which to extract the text for this chunk
   * @param tokenStartIndex - Index (relative to current list of tokens) at which this chunk starts
   * @param tokenEndIndex - Index (relative to current list of tokens) at which this chunk ends (not inclusive)
   * @return Annotation representing new chunk
   */
  public static Annotation getAnnotatedChunk(CoreMap annotation, int tokenStartIndex, int tokenEndIndex)
  {
    Integer annoTokenBegin = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (annoTokenBegin == null) { annoTokenBegin = 0; }
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    Annotation chunk = getAnnotatedChunk(tokens, tokenStartIndex, tokenEndIndex, annoTokenBegin);
    boolean annotatedTextFromCharOffsets = annotateChunkText(chunk, annotation);
    if (!annotatedTextFromCharOffsets) {
      // Use tokens to get text annotation
      annotateChunkText(chunk, CoreAnnotations.TextAnnotation.class);
    }
    return chunk;
  }

  /**
   * Create a new chunk Annotation with basic chunk information
   *   CharacterOffsetBeginAnnotation - set to CharacterOffsetBeginAnnotation of first token in chunk
   *   CharacterOffsetEndAnnotation - set to CharacterOffsetEndAnnotation of last token in chunk
   *   TokensAnnotation - List of tokens in this chunk
   *   TokenBeginAnnotation - Index of first token in chunk (index in original list of tokens)
   *                          tokenStartIndex + annotation's TokenBeginAnnotation
   *   TokenEndAnnotation - Index of last token in chunk (index in original list of tokens)
   *                          tokenEndIndex + annotation's TokenBeginAnnotation
   *   TextAnnotation - String extracted from the origAnnotation using character offset information for this chunk
   * @param annotation - Annotation from which to extract the text for this chunk
   * @param tokenStartIndex - Index (relative to current list of tokens) at which this chunk starts
   * @param tokenEndIndex - Index (relative to current list of tokens) at which this chunk ends (not inclusive)
   * @param tokenChunkKey - If not null, each token is annotated with the chunk using this key
   * @param tokenLabelKey - If not null, each token is annotated with the text associated with the chunk using this key
   * @return Annotation representing new chunk
   */
  public static Annotation getAnnotatedChunk(CoreMap annotation, int tokenStartIndex, int tokenEndIndex,
                                             Class tokenChunkKey, Class tokenLabelKey)
  {
    Annotation chunk = getAnnotatedChunk(annotation, tokenStartIndex, tokenEndIndex);
    annotateChunkTokens(chunk, tokenChunkKey, tokenLabelKey);
    return chunk;
  }

  public static CoreMap getAnnotatedChunkUsingCharOffsets(CoreMap annotation, int charOffsetStart, int charOffsetEnd)
  {
    // TODO: make more efficient search
    List<CoreMap> cm = getAnnotatedChunksUsingSortedCharOffsets(annotation,
            CollectionUtils.makeList(new IntPair(charOffsetStart, charOffsetEnd)));
    if (cm != null && cm.size() > 0) {
      return cm.get(0);
    } else {
      return null;
    }
  }

  public static List<CoreMap> getAnnotatedChunksUsingSortedCharOffsets(
          CoreMap annotation, List<IntPair> charOffsets)
  {
    return getAnnotatedChunksUsingSortedCharOffsets(annotation, charOffsets, true, null, null, true);
  }
  
  /**
   * Create a list of new chunk Annotation with basic chunk information
   *   CharacterOffsetBeginAnnotation - set to CharacterOffsetBeginAnnotation of first token in chunk
   *   CharacterOffsetEndAnnotation - set to CharacterOffsetEndAnnotation of last token in chunk
   *   TokensAnnotation - List of tokens in this chunk
   *   TokenBeginAnnotation - Index of first token in chunk (index in original list of tokens)
   *                          tokenStartIndex + annotation's TokenBeginAnnotation
   *   TokenEndAnnotation - Index of last token in chunk (index in original list of tokens)
   *                          tokenEndIndex + annotation's TokenBeginAnnotation
   *   TextAnnotation - String extracted from the origAnnotation using character offset information for this chunk
   * @param annotation - Annotation from which to extract the text for this chunk
   * @param charOffsets - List of start and end (not inclusive) character offsets
   *                      Note: assume char offsets are sorted and nonoverlapping!!!
   * @param charOffsetIsRelative - Whether the character offsets are relative to the current annotation or absolute offsets
   * @param tokenChunkKey - If not null, each token is annotated with the chunk using this key
   * @param tokenLabelKey - If not null, each token is annotated with the text associated with the chunk using this key
   * @param allowPartialTokens - Whether to allow partial tokens or not
   * @return List of annotation representing new chunks
   */
  public static List<CoreMap> getAnnotatedChunksUsingSortedCharOffsets(
          CoreMap annotation, List<IntPair> charOffsets, boolean charOffsetIsRelative,
          Class tokenChunkKey, Class tokenLabelKey, boolean allowPartialTokens)
  {
    String annoText = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreMap> chunks = new ArrayList<CoreMap>(charOffsets.size());
    List<CoreLabel> annoTokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    Integer annoCharBegin = annotation.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    if (annoCharBegin == null) { annoCharBegin = 0; }
    Integer annoTokenBegin = annotation.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (annoTokenBegin == null) { annoTokenBegin = 0; }
    int i = 0;
    for (IntPair p:charOffsets) {
      int beginRelCharOffset = charOffsetIsRelative? p.getSource():p.getSource()-annoCharBegin;
      int endRelCharOffset = charOffsetIsRelative? p.getTarget():p.getTarget()-annoCharBegin;
      int beginCharOffset = beginRelCharOffset + annoCharBegin;
      int endCharOffset = endRelCharOffset + annoCharBegin;
      if (beginRelCharOffset >= annoText.length()) { break; }
      if (endRelCharOffset > annoText.length()) { endRelCharOffset = annoText.length(); }
      if (allowPartialTokens) {
        while (i < annoTokens.size() && annoTokens.get(i).endPosition() <= beginCharOffset) {
          i++;
        }
      } else {
        while (i < annoTokens.size() && annoTokens.get(i).beginPosition() < beginCharOffset) {
          i++;
        }
      }
      if (i >= annoTokens.size()) break;
      int tokenBegin = i;
      int j = i;
      if (allowPartialTokens) {
        while (j < annoTokens.size() && annoTokens.get(j).beginPosition() < endCharOffset) {
          j++;
        }
      } else {
        while (j < annoTokens.size() && annoTokens.get(j).endPosition() <= endCharOffset) {
          assert(annoTokens.get(j).beginPosition() >= beginCharOffset);
          j++;
        }
      }
      int tokenEnd = j;

      List<CoreLabel> chunkTokens = new ArrayList<CoreLabel>(annoTokens.subList(tokenBegin, tokenEnd));
      String chunkText = annoText.substring(beginRelCharOffset, endRelCharOffset);
      Annotation chunk = new Annotation(chunkText);
      chunk.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, beginCharOffset);
      chunk.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, endCharOffset);
      chunk.set(CoreAnnotations.TokensAnnotation.class, chunkTokens);
      chunk.set(CoreAnnotations.TokenBeginAnnotation.class, tokenBegin + annoTokenBegin);
      chunk.set(CoreAnnotations.TokenEndAnnotation.class, tokenEnd + annoTokenBegin);
      annotateChunkTokens(chunk, tokenChunkKey, tokenLabelKey);
      chunks.add(chunk);
      if (j >= annoTokens.size()) break;
    }
    if (chunks.size() != charOffsets.size()) {
      logger.warning("WARNING: Only " + chunks.size() + "/" + charOffsets.size()
              + " chunks found.  Check if offsets are sorted/nonoverlapping");      
    }
    return chunks;
  }

  public static void annotateChunk(CoreMap annotation, Class newAnnotationKey,
                                   Class aggrKey, CoreMapAttributeAggregator aggregator)
  {
    Object v = aggregator.aggregate(aggrKey, annotation.get(CoreAnnotations.TokensAnnotation.class));
    annotation.set(newAnnotationKey, v);
  }

  public static void annotateChunk(CoreMap chunk, Map<String,String> attributes)
  {
    for (String attr:attributes.keySet()) {
      String value = attributes.get(attr);
      AnnotationLookup.KeyLookup lookup = AnnotationLookup.getCoreKey(attr);
      if (attr != null) {
        if (value != null)  {
          try {
            Class valueClass = AnnotationLookup.getValueType(lookup.coreKey);
            if (valueClass == String.class) {
              chunk.set(lookup.coreKey, value);              
            } else {
             Method valueOfMethod = valueClass.getMethod("valueOf", String.class);
              if (valueOfMethod != null) {
                chunk.set(lookup.coreKey, valueOfMethod.invoke(valueClass, value));
              }
            }
          } catch (Exception ex) {
            throw new RuntimeException("Unable to annotate attribute " + attr, ex);
          }
        } else {
          chunk.set(lookup.coreKey, null);
        }
      } else {
        throw new UnsupportedOperationException("Unknown attributes: " + attr);
      }
    }
  }

  public static void annotateChunks(List<? extends CoreMap> chunks, int start, int end, Map<String,String> attributes)
  {
    for (int i = start; i < end; i++) {
      annotateChunk(chunks.get(i), attributes);
    }
  }

  public static void annotateChunks(List<? extends CoreMap> chunks, Map<String,String> attributes)
  {
    for (CoreMap chunk:chunks) {
      annotateChunk(chunk, attributes);
    }
  }

  public static <T extends CoreMap> T createCoreMap(CoreMap cm, String text, int start, int end,
                                                    CoreTokenFactory<T> factory) {
    if (end > start) {
      T token = factory.makeToken();
      Integer cmCharStart = cm.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      if (cmCharStart == null) cmCharStart = 0;
      token.set(CoreAnnotations.TextAnnotation.class, text.substring(start, end));
      token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, cmCharStart + start);
      token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, cmCharStart + end);
      return token;
    } else {
      return null;
    }
  }

  public static <T extends CoreMap> void appendCoreMap(List<T> res,
                                                       CoreMap cm, String text, int start, int end,
                                                       CoreTokenFactory<T> factory) {
    T scm = createCoreMap(cm, text, start, end, factory);
    if (scm != null) {
      res.add(scm);
    }
  }

  public static <T extends CoreMap> List<T> splitCoreMap(Pattern p, boolean includeMatched,
                                                         CoreMap cm, CoreTokenFactory<T> factory)
  {
    List<T> res = new ArrayList<T>();
    String text = cm.get(CoreAnnotations.TextAnnotation.class);
    Matcher m = p.matcher(text);
    int index = 0;
    while (m.find()) {
      int start = m.start();
      int end = m.end();
      // Include characters from index to m.start()
      appendCoreMap(res, cm, text, index, start, factory);
      // Include matched pattern
      if (includeMatched) {
        appendCoreMap(res, cm, text, start, end, factory);
      }
      index = end;
    }
    appendCoreMap(res, cm, text, index, text.length(), factory);
    return res;
  }

}
