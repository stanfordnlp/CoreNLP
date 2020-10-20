package edu.stanford.nlp.quoteattribution;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.paragraphs.ParagraphAnnotator;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;

/**
 * @author Michael Fang, Grace Muzny
 */
public class QuoteAttributionEvaluation {

  //these are hardcoded in the order we wish the results to be presented.
  private static final String[] mentionKeyOrder = {"trigram CVQ", "trigram VCQ", "trigram PVQ", "trigram VPQ", "trigram QVC", "trigram QCV", "trigram QVP", "trigram QPV", "Deterministic depparse", "Deterministic oneNameSentence", "Deterministic Vocative -- name", "Deterministic Vocative -- animate noun", "Deterministic endQuoteClosestBefore", "Deterministic one speaker sentence", "supervised", "conv", "loose", null};
  private static final String[] speakerKeyOrder = {"automatic name", "coref", "Baseline Top conversation - prev", "Baseline Top conversation - next", "Baseline Top family animate", "Baseline Top"};
  private enum Result {
    SKIPPED, CORRECT, INCORRECT;
  }
  private static String outputMapResultsDefaultKeys(Map<String, Counter<Result>> tagResults, String[] keyOrder)
  {
    StringBuilder output = new StringBuilder();
    Result[] order = new Result[]{Result.CORRECT, Result.INCORRECT, Result.SKIPPED};
    for(String tag : keyOrder)
    {
      Counter<Result> resultsCounter = tagResults.get(tag);
      if(resultsCounter == null ) {
        continue;
      }
      if(tag == null) {
        output.append("No label" + "\t");
      }
      else {
        output.append(tag + "\t");
      }


      for(Result result : order) {
        output.append(result.toString() + "\t" + resultsCounter.getCount(result) + "\t");
      }

      //append total and precision
      double numCorrect = resultsCounter.getCount(Result.CORRECT);
      double numIncorrect = resultsCounter.getCount(Result.INCORRECT);
      double total = numCorrect + numIncorrect;
      double precision = (total == 0) ? 0 : numCorrect / total;
      output.append(total + "\t" + precision + "\n");

    }
    return output.toString();
  }

  private static int getQuoteChapter(Annotation doc, CoreMap quote) {
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    return sentences.get(quote.get(CoreAnnotations.SentenceBeginAnnotation.class)).get(ChapterAnnotator.ChapterAnnotation.class);
  }

  private static void evaluate(Annotation doc, List<XMLToAnnotation.GoldQuoteInfo> goldList) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    Map<String, Counter<Result>> mentionPredTypeResults = new HashMap<>();
    Map<String, Counter<Result>> speakerPredTypeResults = new HashMap<>();
    Counter<Result> mentionResults = new ClassicCounter<>();
    Counter<Result> speakerResults = new ClassicCounter<>();
    //aggregate counts
    for(int i = 0; i < quotes.size(); i++) {
      CoreMap quote = quotes.get(i);

      XMLToAnnotation.GoldQuoteInfo gold = goldList.get(i);
      if(gold.speaker.equals("UNSURE") || gold.speaker.equals("NOTANUTTERANCE") || gold.mentionStartTokenIndex == -1)
        continue;
      String speakerPred = quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class);
      Integer mentionBeginPred = quote.get(QuoteAttributionAnnotator.MentionBeginAnnotation.class);
      Integer mentionEndPred = quote.get(QuoteAttributionAnnotator.MentionEndAnnotation.class);

      Result mentionResult;
      if(mentionBeginPred == null) {
        mentionResult = Result.SKIPPED;
      }
      else if ((gold.mentionStartTokenIndex <= mentionBeginPred && gold.mentionEndTokenIndex >= mentionEndPred)
            || (gold.mentionStartTokenIndex <= mentionEndPred && gold.mentionEndTokenIndex >= mentionEndPred)) {
        mentionResult = Result.CORRECT;
      }
      else {
        mentionResult = Result.INCORRECT;
      }
      Result speakerResult;
      if(speakerPred == null) {
        speakerResult = Result.SKIPPED;
      }
      else if (speakerPred.equals(gold.speaker)) {
        speakerResult = Result.CORRECT;
      }
      else {
        speakerResult = Result.INCORRECT;
      }

      boolean verbose = true;

      if(verbose) {
        if(!mentionResult.equals(Result.CORRECT) || !speakerResult.equals(Result.CORRECT)) {
          System.out.println("====");
          System.out.println("Id: " + i + " Quote: " + quote.get(CoreAnnotations.TextAnnotation.class));
          System.out.println("Speaker: " + goldList.get(i).speaker + " Predicted: " + quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) + " " + speakerResult.name());
          System.out.println("Speaker Tag: " + quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class));
          System.out.println("Gold Mention: " + gold.mention);// + " context: " + tokenRangeToString(goldRangeExtended, doc));

          if (mentionResult.equals(Result.INCORRECT)) {
            System.out.println("Predicted Mention: " + quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) + " INCORRECT");
            System.out.println("Mention Tag: " + quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class));
          } else if (mentionResult.equals(Result.SKIPPED)) {
            System.out.println("Mention SKIPPED");
          } else {
            System.out.println("Gold Mention: " + quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) + " CORRECT");
            System.out.println("Mention tag: " + quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class));
          }
        }
        else {
          System.out.println("====");
          System.out.println("Id: " + i + " Quote: " + quote.get(CoreAnnotations.TextAnnotation.class));
          System.out.println("Mention Tag: " + quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class) + " Speaker Tag: " + quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class) );
          System.out.println("Speaker: " + quote.get(QuoteAttributionAnnotator.SpeakerAnnotation.class) + " Mention: " + quote.get(QuoteAttributionAnnotator.MentionAnnotation.class));
          System.out.println("ALL CORRECT");
        }
      }


      mentionResults.incrementCount(mentionResult);
      speakerResults.incrementCount(speakerResult);
      mentionPredTypeResults.putIfAbsent(quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class), new ClassicCounter<>());
      mentionPredTypeResults.get(quote.get(QuoteAttributionAnnotator.MentionSieveAnnotation.class)).incrementCount(mentionResult);
      speakerPredTypeResults.putIfAbsent(quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class), new ClassicCounter<>());
      speakerPredTypeResults.get(quote.get(QuoteAttributionAnnotator.SpeakerSieveAnnotation.class)).incrementCount(speakerResult);
    }
    //output results
    double mCorrect = mentionResults.getCount(Result.CORRECT);
    double mIncorrect = mentionResults.getCount(Result.INCORRECT);
    double mSkipped = mentionResults.getCount(Result.SKIPPED);
    double mPrecision = mCorrect / (mCorrect + mIncorrect);
    double mRecall = mCorrect / (mCorrect + mSkipped);
    double mF1 = (2 * (mPrecision * mRecall) / (mPrecision+mRecall));
    double mAccuracy = mCorrect / (mCorrect + mIncorrect + mSkipped);

    double sCorrect = speakerResults.getCount(Result.CORRECT);
    double sIncorrect = speakerResults.getCount(Result.INCORRECT);
    double sSkipped = speakerResults.getCount(Result.SKIPPED);
    double sPrecision = sCorrect / (sCorrect + sIncorrect);
    double sRecall = sCorrect / (sCorrect + sSkipped);
    double sF1 = (2 * (sPrecision * sRecall) / (sPrecision+sRecall));
    double sAccuracy = sCorrect / (sCorrect + sIncorrect + sSkipped);

    System.out.println(outputMapResultsDefaultKeys(mentionPredTypeResults, mentionKeyOrder));
    System.out.println(outputMapResultsDefaultKeys(speakerPredTypeResults, speakerKeyOrder));

    System.out.printf("Mention C:%d\tI:%d\tS:%d\tP:%.3f\tR:%.3f\tF1:%.3f\tA:%.3f\t\tSpeaker C:%d\tI:%d\tS:%d\tP:%.3f\tR:%.3f\tF1:%.3f\tA:%.3f\n",
            (int)mCorrect, (int)mIncorrect, (int)mSkipped, mPrecision, mRecall, mF1, mAccuracy,
            (int)sCorrect, (int)sIncorrect, (int)sSkipped, sPrecision, sRecall, sF1, sAccuracy);
  }

  /**
   * Usage: java QuoteAttributionEvaluation path_to_properties_file
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    // make the first argument one for a base directory
    if (args.length != 1) {
      System.out.println("Usage: java QuoteAttributionEvaluation path_to_properties_file");
      System.exit(1);
    }
    String specificFile = args[0];

    System.out.println("Using properties file: " + specificFile);
    Properties props = StringUtils.propFileToProperties(specificFile);

    //convert XML file to (1) the Annotation (data.doc) (2) a list of people in the text (data.personList)
    // and (3) the gold info to be used by evaluate (data.goldList).
    XMLToAnnotation.Data data = XMLToAnnotation.readXMLFormat(props.getProperty("file"));
    Properties annotatorProps = new Properties();
//    XMLToAnnotation.writeCharacterList("characterListPP.txt", data.personList); //Use this to write the person list to a file
    annotatorProps.setProperty("charactersPath", props.getProperty("charactersPath"));
    annotatorProps.setProperty("booknlpCoref", props.getProperty("booknlpCoref"));
    annotatorProps.setProperty("familyWordsFile", props.getProperty("familyWordsFile"));
    annotatorProps.setProperty("animacyWordsFile", props.getProperty("animacyWordsFile"));
    annotatorProps.setProperty("genderNamesFile", props.getProperty("genderNamesFile"));
    annotatorProps.setProperty("modelPath", props.getProperty("modelPath"));
    QuoteAttributionAnnotator qaa = new QuoteAttributionAnnotator(annotatorProps);
    qaa.annotate(data.doc);
    evaluate(data.doc, data.goldList);
  }
}
