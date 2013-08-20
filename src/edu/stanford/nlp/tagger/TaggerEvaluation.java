package edu.stanford.nlp.tagger;

import edu.stanford.nlp.ling.*;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;


/**
 * This class evaluates the accuracy of a proposed tagging against a gold
 * standard.  It will strip tags at a `|' mark.  This happens to work well
 * for both the Penn treebank `ambiguous' tags, and for Kristina's tagger.
 * <b>Warning:</b> This was done as a one-off, and isn't yet sensibly
 * generalized.
 *
 * @author <a href="mailto:manning@cs.stanford.edu">Christopher Manning</a>
 * @version 1.0
 */
public final class TaggerEvaluation {

  int sentRight;
  int sentWrong;
  int tagRight;
  int tagWrong;
  int unknownTagRight;
  int unknownTagWrong;

  public void printEvaluation() {
    double sentAcc = (sentRight == 0) ? 0.0 : (100.0 * sentRight) / (sentRight + sentWrong);
    double tagAcc = (tagRight == 0) ? 0.0 : (100.0 * tagRight) / (tagRight + tagWrong);
    double unknownTagAcc = (unknownTagRight == 0) ? 0.0 : (100.0 * unknownTagRight) / (unknownTagRight + unknownTagWrong);
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(3);
    System.out.println();
    System.out.println("Sentences: total: " + (sentRight + sentWrong) + "; right: " + sentRight + " (" + nf.format(sentAcc) + "%); wrong " + sentWrong + " (" + nf.format(100.0 - sentAcc) + "%).");
    System.out.println("Words: total: " + (tagRight + tagWrong) + "; right: " + tagRight + " (" + nf.format(tagAcc) + "%); wrong " + tagWrong + " (" + nf.format(100.0 - tagAcc) + "%).");
    System.out.println("Unknown words: total: " + (unknownTagRight + unknownTagWrong) + "; right: " + unknownTagRight + " (" + nf.format(unknownTagAcc) + "%); wrong " + unknownTagWrong + " (" + nf.format(100.0 - unknownTagAcc) + "%).");
  }


  public void evaluateTagging(Sentencebank<ArrayList<TaggedWord>,TaggedWord> sb1, Sentencebank<ArrayList<TaggedWord>,TaggedWord> sb2) {
    sentRight = 0;
    sentWrong = 0;
    tagRight = 0;
    tagWrong = 0;
    unknownTagRight = 0;
    unknownTagWrong = 0;
    Iterator<ArrayList<TaggedWord>> i2 = sb2.iterator();
    for (List<TaggedWord> s1 : sb1) {
      if ( ! i2.hasNext()) {
        throw new RuntimeException("Sentencebank size mismatch!");
      }
      List<TaggedWord> s2 = i2.next();
      evaluateSentence(s1, s2);
    }
  }


  private void evaluateSentence(List<TaggedWord> s1, List<TaggedWord> s2) {
    // System.err.println("Evaluating\n" + Sentence.listToString(s1, false) + "\n" + Sentence.listToString(s2, false));

    boolean allCorrect = true;
    int leng = s1.size();
    if (leng != s2.size()) {
      throw new RuntimeException("Sentence size mismatch!\n" + Sentence.listToString(s1, false) + "\n" + Sentence.listToString(s2, false));
    }
    for (int i = 0; i < leng; i++) {
      TaggedWord tw1 = s1.get(i);
      TaggedWord tw2 = s2.get(i);
      if (tw1.tag().equals(tw2.tag())) {
        tagRight++;
      } else {
        tagWrong++;
        allCorrect = false;
      }
    }
    if (allCorrect) {
      sentRight++;
    } else {
      sentWrong++;
    }
  }


  /**
   * Assess tagging accuracy.  With flag -retag do retagging of 2nd
   * argument.
   * usage: java TaggerEvaluation [-retag] file file
   * (First file is conventionally tagger output, and
   * second is gold standard, but doesn't matter unless using -retag.)
   *
   * @param args Command-line arguments
   */
  public static void main(String[] args) {
    if (args.length < 2) {
      System.out.println("usage: java TaggerEvaluation [-retag] goldFile guessFile");
    }
    SentenceReaderFactory<TaggedWord> srf = new AdwaitSentenceReaderFactory<TaggedWord>(true);
    // SentenceReaderFactory<TaggedWord> srf = new PennSentenceReaderFactory();
    Sentencebank<ArrayList<TaggedWord>,TaggedWord> sb1 = new MemorySentencebank<TaggedWord>(srf);
    SentenceReaderFactory<TaggedWord> srf2 = new AdwaitSentenceReaderFactory<TaggedWord>(true);
    // need sb2 in memory so we can change it!
    Sentencebank<ArrayList<TaggedWord>,TaggedWord> sb2 = new MemorySentencebank<TaggedWord>(srf2);
    boolean retag = false;
    int j = 0;
    if (args[j].equals("-retag")) {
      retag = true;
      j++;
    }
    sb1.loadPath(args[j++]);
    sb2.loadPath(args[j]);
    System.err.println("Loaded the two sentence banks");
    SentenceVisitor<TaggedWord> sp2 = new UndecorateSentenceVisitor();
    sb2.apply(sp2);
    if (retag) {
      SentenceVisitor<TaggedWord> sp = new ReTagSentenceVisitor();
      sb2.apply(sp);
    }
    System.err.println("Undecorated");
    TaggerEvaluation te = new TaggerEvaluation();
    te.evaluateTagging(sb1, sb2);
    te.printEvaluation();
  }

  private static class ReTagSentenceVisitor implements SentenceVisitor<TaggedWord> {

    public ReTagSentenceVisitor() {
    }

    public void visitSentence(ArrayList<TaggedWord> s) {
      // System.out.println("Before: " + s.toString(false));
      int leng = s.size();
      for (int i = 0; i < leng; i++) {
        TaggedWord tw = s.get(i);
        String tag = tw.tag();
        String word = tw.word();
        if (word.equals("#")) {
          tag = "#";
        } else if (word.equalsIgnoreCase("to") && tag.equals("IN")) {
          tag = "TO";
        } else if (tag.equals("DTP")) {
          // System.out.println("### Retagging DTP");
          tag = "DT";
        } else if (tag.equals("INS")) {
          tag = "IN";
        }
        TaggedWord tw2 = new TaggedWord(word, tag);
        s.set(i, tw2);
      }
      // System.out.println("After: " + s.toString(false));
    }

  }

  private static class UndecorateSentenceVisitor implements SentenceVisitor<TaggedWord> {

    public UndecorateSentenceVisitor() {
    }

    public void visitSentence(ArrayList<TaggedWord> s) {
      // System.out.println("Before: " + Sentence.listToString(s, false, "_"));
      int leng = s.size();
      for (int i = 0; i < leng; i++) {
        TaggedWord tw = s.get(i);
        String tag = tw.tag();
        String word = tw.word();
        int barIndex = tag.indexOf('|');
        if (barIndex > 0) {
          tag = tag.substring(0, barIndex);
          // System.out.println("### Retagging as " + tag);
        }

        int dashIndex = tag.indexOf('-');
        if (dashIndex > 0 && dashIndex < tag.length() - 1) {
          tag = tag.substring(0, dashIndex);
          // System.out.println("### Retagging as " + tag);
        }

        int caretIndex = tag.indexOf('^');
        if (caretIndex > 0 && caretIndex < tag.length() - 1) {
          tag = tag.substring(0, caretIndex);
          // System.out.println("### Retagging as " + tag);
        }

        int starIndex = tag.indexOf('*');
        if (starIndex > 0) {
          tag = tag.substring(0, starIndex);
        }
        TaggedWord tw2 = new TaggedWord(word, tag);
        s.set(i, tw2);
      }
      // System.out.println("After: " + Sentence.listToString(s, false, "_"));
    }

  }

}
