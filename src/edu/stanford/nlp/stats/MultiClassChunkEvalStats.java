package edu.stanford.nlp.stats;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.pipeline.LabeledChunkIdentifier;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * Calculates phrase based precision and recall (similar to conlleval)
 * Handles various encodings such as IO, IOB, IOE, BILOU, SBEIO, []
 *
 * Usage: java edu.stanford.nlp.stats.MultiClassChunkEvalStats [options] < filename
 *        -r - Do raw token based evaluation
 *        -d <delimiter> - Specifies delimiter to use (instead of tab)
 *        -b <boundary> - Boundary token (default is -X- )
 *        -t <defaultTag> - Default tag to use if tag is not prefixed (i.e. is not X-xxx )
 *        -ignoreProvidedTag - Discards the provided tag (i.e. if label is X-xxx, just use xxx for evaluation)
 * @author Angel Chang
 */
public class MultiClassChunkEvalStats extends MultiClassPrecisionRecallExtendedStats.MultiClassStringLabelStats {
  private boolean inCorrect = false;
  private LabeledChunkIdentifier.LabelTagType prevCorrect = null;
  private LabeledChunkIdentifier.LabelTagType prevGuess = null;
  private LabeledChunkIdentifier chunker;
  private boolean useLabel = false;

  public <F> MultiClassChunkEvalStats(Classifier<String,F> classifier, GeneralDataset<String,F> data, String negLabel)
  {
    super(classifier, data, negLabel);
    chunker = new LabeledChunkIdentifier();
    chunker.setNegLabel(negLabel);
  }

  public MultiClassChunkEvalStats(String negLabel)
  {
    super(negLabel);
    chunker = new LabeledChunkIdentifier();
    chunker.setNegLabel(negLabel);
  }

  public MultiClassChunkEvalStats(Index<String> dataLabelIndex, String negLabel)
  {
    super(dataLabelIndex, negLabel);
    chunker = new LabeledChunkIdentifier();
    chunker.setNegLabel(negLabel);
  }

  public LabeledChunkIdentifier getChunker()
  {
    return chunker;
  }

  public void clearCounts()
  {
    super.clearCounts();
    inCorrect = false;
    prevCorrect = null;
    prevGuess = null;
  }

  protected void finalizeCounts()
  {
    markBoundary();
    super.finalizeCounts();
  }

  private String getTypeLabel(LabeledChunkIdentifier.LabelTagType tagType)
  {
    if (useLabel) return tagType.label;
    else return tagType.type;
  }
  protected void markBoundary()
  {
    if (inCorrect) {
      inCorrect=false;
      correctGuesses.incrementCount(getTypeLabel(prevCorrect));
    }
    prevGuess = null;
    prevCorrect = null;
  }

  protected void addGuess(String guess, String trueLabel, boolean addUnknownLabels)
  {
    LabeledChunkIdentifier.LabelTagType guessTagType = chunker.getTagType(guess);
    LabeledChunkIdentifier.LabelTagType correctTagType = chunker.getTagType(trueLabel);
    addGuess(guessTagType, correctTagType, addUnknownLabels);
  }

  protected void addGuess(LabeledChunkIdentifier.LabelTagType guess,
                          LabeledChunkIdentifier.LabelTagType correct, boolean addUnknownLabels)
  {
    if (addUnknownLabels) {
      if (labelIndex == null) {
        labelIndex = new HashIndex<String>();
      }
      labelIndex.add(getTypeLabel(guess));
      labelIndex.add(getTypeLabel(correct));
    }
    if (inCorrect) {
       boolean prevCorrectEnded = chunker.isEndOfChunk(prevCorrect, correct);
       boolean prevGuessEnded = chunker.isEndOfChunk(prevGuess, guess);
       if (prevCorrectEnded && prevGuessEnded && prevGuess.typeMatches(prevCorrect)) {
         inCorrect=false;
         correctGuesses.incrementCount(getTypeLabel(prevCorrect));
       } else if (prevCorrectEnded != prevGuessEnded || !guess.typeMatches(correct)) {
         inCorrect=false;
       }
    }

    boolean correctStarted = chunker.isStartOfChunk(prevCorrect, correct);
    boolean guessStarted = chunker.isStartOfChunk(prevGuess, guess);
    if ( correctStarted && guessStarted && guess.typeMatches(correct)) {
      inCorrect = true;
    }

    if ( correctStarted ) {
      foundCorrect.incrementCount(getTypeLabel(correct));
    }
    if ( guessStarted ) {
      foundGuessed.incrementCount(getTypeLabel(guess));
    }

    if (chunker.isIgnoreProvidedTag()) {
      if (guess.typeMatches(correct)) {
        tokensCorrect++;
      }
    } else {
      if (guess.label.equals(correct.label)) {
        tokensCorrect++;
      }
    }
    
    tokensCount++;
    prevGuess = guess;
    prevCorrect = correct;
  }

  // Returns string precision recall in ConllEval format
  public String getConllEvalString()
  {
    return getConllEvalString(true);
  }

  public static void main(String[] args) throws Exception
  {
    StringUtils.printErrInvocationString("MultiClassChunkEvalStats", args);
    Properties props = StringUtils.argsToProperties(args);
    String boundary = props.getProperty("b","-X-");
    String delimiter = props.getProperty("d","\t");
    String defaultPosTag = props.getProperty("t", "I");
    boolean raw = Boolean.valueOf(props.getProperty("r","false"));
    boolean ignoreProvidedTag = Boolean.valueOf(props.getProperty("ignoreProvidedTag","false"));
    String format = props.getProperty("format", "conll");
    String filename = props.getProperty("i");
    String backgroundLabel = props.getProperty("k", "O");
    try {
      MultiClassPrecisionRecallExtendedStats stats;
      if (raw) {
        stats = new MultiClassStringLabelStats(backgroundLabel);
      } else {
        MultiClassChunkEvalStats mstats = new MultiClassChunkEvalStats(backgroundLabel);
        mstats.getChunker().setDefaultPosTag(defaultPosTag);
        mstats.getChunker().setIgnoreProvidedTag(ignoreProvidedTag);
        stats = mstats;
      }
      if (filename != null) {
        stats.score(filename, delimiter, boundary);
      } else {
        stats.score(new BufferedReader(new InputStreamReader(System.in)), delimiter, boundary);
      }
      if ("conll".equalsIgnoreCase(format)) {
        System.out.println(stats.getConllEvalString());
      } else {
        System.out.println(stats.getDescription(6));        
      }
    } catch (IOException ex) {
      System.err.println("Error processing file: " + ex.toString());
      ex.printStackTrace(System.err);
    }
  }
}
