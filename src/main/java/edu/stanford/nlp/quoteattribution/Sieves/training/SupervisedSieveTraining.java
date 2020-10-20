package edu.stanford.nlp.quoteattribution.Sieves.training;

import edu.stanford.nlp.classify.Classifier;
import edu.stanford.nlp.classify.GeneralDataset;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.paragraphs.ParagraphAnnotator;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.quoteattribution.Sieves.Sieve;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;

/**
 * Created by mjfang on 12/1/16.
 */
public class SupervisedSieveTraining {

  // Take in a training Annotated document:
  // convert document to dataset & featurize
  // train classifier
  // output model
  // report training F1/accuracy

  public static final Set<String> punctuation = new HashSet<>(Arrays.asList(new String[]{",", ".", "\"", "\n"}));
  public static final Set<String> punctuationForFeatures = new HashSet<>(Arrays.asList(new String[]{",", ".", "!", "?"})); //TODO: in original iteration: maybe can combine these!?


  //given a sentence, return the begin token of the paragraph it's in
  private static int getParagraphBeginToken(CoreMap sentence, List<CoreMap> sentences) {
    int paragraphId = sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class);
    int paragraphBeginToken = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
    for (int i = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class) - 1; i >= 0; i--) {
      CoreMap currSentence = sentences.get(i);
      if(currSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == paragraphId) {
        paragraphBeginToken = currSentence.get(CoreAnnotations.TokenBeginAnnotation.class);
      } else {
        break;
      }
    }
    return paragraphBeginToken;
  }

  private static int getParagraphEndToken(CoreMap sentence, List<CoreMap> sentences) {
    int quoteParagraphId = sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class);
    int paragraphEndToken = sentence.get(CoreAnnotations.TokenEndAnnotation.class) - 1;
    for (int i = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class); i < sentences.size(); i++) {
      CoreMap currSentence = sentences.get(i);
      if (currSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == quoteParagraphId) {
        paragraphEndToken = currSentence.get(CoreAnnotations.TokenEndAnnotation.class) - 1;
      } else {
        break;
      }
    }
    return paragraphEndToken;
  }

  private static Map<Integer, List<CoreMap>> getQuotesInParagraph(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    Map<Integer, List<CoreMap>> paragraphToQuotes = new HashMap<>();
    for(CoreMap quote : quotes) {
      CoreMap sentence = sentences.get(quote.get(CoreAnnotations.SentenceBeginAnnotation.class));
      paragraphToQuotes.putIfAbsent(sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class), new ArrayList<>());
      paragraphToQuotes.get(sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class)).add(quote);
    }
    return paragraphToQuotes;
  }

  //assumption: exclusion list is non-overlapping, ordered
  private static List<Pair<Integer, Integer>> getRangeExclusion(Pair<Integer, Integer> originalRange, List<Pair<Integer, Integer>> exclusionList) {

    List<Pair<Integer, Integer>> leftoverRanges = new ArrayList<>();
    Pair<Integer, Integer> currRange = originalRange;
    for (Pair<Integer, Integer> exRange : exclusionList) {
      Pair<Integer, Integer> leftRange = new Pair<>(currRange.first, exRange.first - 1);
      if (leftRange.second - leftRange.first >= 0) {
        leftoverRanges.add(leftRange);
      }

      if (currRange.second.equals(exRange.second)) {
        break;
      } else {
        currRange = new Pair<>(exRange.second + 1, currRange.second);
      }
    }
    if(currRange.first < currRange.second)
      leftoverRanges.add(currRange);
    return leftoverRanges;
  }

  public static class FeaturesData {
    public GeneralDataset<String, String> dataset;
    public Map<Integer, Pair<Integer, Integer>> mapQuoteToDataRange;
    public Map<Integer, Sieve.MentionData> mapDatumToMention;

    public FeaturesData(Map<Integer, Pair<Integer, Integer>> mapQuoteToDataRange, Map<Integer, Sieve.MentionData> mapDatumToMention, GeneralDataset<String, String> dataset) {
      this.mapQuoteToDataRange = mapQuoteToDataRange;
      this.mapDatumToMention = mapDatumToMention;
      this.dataset = dataset;
    }
  }

  public static class SieveData {
    final Annotation doc;
    final Map<String, List<Person>> characterMap;
    final Map<Integer,String> pronounCorefMap;
    final Set<String> animacyList;

    public SieveData(Annotation doc,
                     Map<String, List<Person>> characterMap,
                     Map<Integer,String> pronounCorefMap,
                     Set<String> animacyList) {
      this.doc = doc;
      this.characterMap = characterMap;
      this.pronounCorefMap = pronounCorefMap;
      this.animacyList = animacyList;
    }
  }


  //goldList null if not training
  public static FeaturesData featurize(SieveData sd, List<XMLToAnnotation.GoldQuoteInfo> goldList, boolean isTraining) {

    Annotation doc = sd.doc;

    // use to access functions
    Sieve sieve = new Sieve(doc, sd.characterMap, sd.pronounCorefMap, sd.animacyList);

    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);

    Map<Integer, List<CoreMap>> paragraphToQuotes = getQuotesInParagraph(doc);
    GeneralDataset<String, String> dataset = new RVFDataset<>();

    //necessary for 'ScoreBestMention'
    Map<Integer, Pair<Integer, Integer>> mapQuoteToDataRange = new HashMap<>(); //maps quote to corresponding indices in the dataset
    Map<Integer, Sieve.MentionData> mapDatumToMention = new HashMap<>();

    if(isTraining && goldList.size() != quotes.size()) {
      throw new RuntimeException("Gold Quote List size doesn't match quote list size!");
    }

    for (int quoteIdx = 0; quoteIdx < quotes.size(); quoteIdx++) {

      int initialSize = dataset.size();

      CoreMap quote = quotes.get(quoteIdx);
      XMLToAnnotation.GoldQuoteInfo gold = null;
      if(isTraining) {
        gold = goldList.get(quoteIdx);
        if (gold.speaker.isEmpty()) {
          continue;
        }
      }

      CoreMap quoteFirstSentence = sentences.get(quote.get(CoreAnnotations.SentenceBeginAnnotation.class));
      Pair<Integer, Integer> quoteRun = new Pair<>(quote.get(CoreAnnotations.TokenBeginAnnotation.class), quote.get(CoreAnnotations.TokenEndAnnotation.class));
//      int quoteChapter = quoteFirstSentence.get(ChapterAnnotator.ChapterAnnotation.class);
      int quoteParagraphIdx = quoteFirstSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class);

      //add mentions before quote up to the previous paragraph
      int rightValue = quoteRun.first - 1;
      int leftValue = quoteRun.first - 1;
      //move left value to be the first token idx of the previous paragraph
      for(int sentIdx = quote.get(CoreAnnotations.SentenceBeginAnnotation.class); sentIdx >= 0; sentIdx--) {
        CoreMap sentence = sentences.get(sentIdx);
        if(sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == quoteParagraphIdx) {
          continue;
        }
        if(sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == quoteParagraphIdx - 1) { //quoteParagraphIdx - 1 for this and prev
          leftValue = sentence.get(CoreAnnotations.TokenBeginAnnotation.class);
        }
        else {
          break;
        }
      }

      List<Sieve.MentionData> mentionsInPreviousParagraph = new ArrayList<>();
      if (leftValue > -1 && rightValue > -1)
        mentionsInPreviousParagraph = eliminateDuplicates(sieve.findClosestMentionsInSpanBackward(new Pair<>(leftValue, rightValue)));

      //mentions in next paragraph
      leftValue = quoteRun.second + 1;
      rightValue = quoteRun.second + 1;
      for(int sentIdx = quote.get(CoreAnnotations.SentenceEndAnnotation.class); sentIdx < sentences.size(); sentIdx++) {
        CoreMap sentence = sentences.get(sentIdx);
//        if(sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == quoteParagraphIdx) {
//          continue;
//        }
        if(sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == quoteParagraphIdx ) { //quoteParagraphIdx + 1
          rightValue = sentence.get(CoreAnnotations.TokenEndAnnotation.class) - 1;
        }
        else {
          break;
        }
      }

      List<Sieve.MentionData> mentionsInNextParagraph = new ArrayList<>();
      if (leftValue < tokens.size() && rightValue < tokens.size())
        mentionsInNextParagraph = sieve.findClosestMentionsInSpanForward(new Pair<>(leftValue, rightValue));

      List<Sieve.MentionData> candidateMentions = new ArrayList<>();
      candidateMentions.addAll(mentionsInPreviousParagraph);
      candidateMentions.addAll(mentionsInNextParagraph);

//      System.out.println(candidateMentions.size());
      int rankedDistance = 1;
      int numBackwards = mentionsInPreviousParagraph.size();

      for (Sieve.MentionData mention : candidateMentions) {

//        List<CoreLabel> mentionCandidateTokens = doc.get(CoreAnnotations.TokensAnnotation.class).subList(mention.begin, mention.end + 1);
//        CoreMap mentionCandidateSentence = sentences.get(mentionCandidateTokens.get(0).sentIndex());
//        if (mentionCandidateSentence.get(ChapterAnnotator.ChapterAnnotation.class) != quoteChapter) {
//          continue;
//        }

        Counter<String> features = new ClassicCounter<>();

        boolean isLeft = true;
        int distance = quoteRun.first - mention.end;

        if (distance < 0) {
          isLeft = false;
          distance = mention.begin - quoteRun.second;
        }
        if (distance < 0) {
          continue; //disregard mention-in-quote cases.
        }

        features.setCount("wordDistance", distance);

        List<CoreLabel> betweenTokens;
        if (isLeft) {
          betweenTokens = tokens.subList(mention.end + 1, quoteRun.first);
        } else {
          betweenTokens = tokens.subList(quoteRun.second + 1, mention.begin);
        }

        //Punctuation in between
        for (CoreLabel token : betweenTokens) {
          if (punctuation.contains(token.word())) {
            features.setCount("punctuationPresence:" + token.word(), 1);
          }
        }

        // number of mentions away
        features.setCount("rankedDistance", rankedDistance);
        rankedDistance++;
        if (rankedDistance == numBackwards) {//reset for the forward
          rankedDistance = 1;
        }

//        int quoteParagraphIdx = quoteFirstSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class);

        //third distance: # of paragraphs away
        int mentionParagraphIdx = -1;
        CoreMap sentenceInMentionParagraph = null;

        int quoteParagraphBeginToken = getParagraphBeginToken(quoteFirstSentence, sentences);
        int quoteParagraphEndToken = getParagraphEndToken(quoteFirstSentence, sentences);

        if (isLeft) {
          if (quoteParagraphBeginToken <= mention.begin && mention.end <= quoteParagraphEndToken) {
            features.setCount("leftParagraphDistance", 0);
            mentionParagraphIdx = quoteParagraphIdx;
            sentenceInMentionParagraph = quoteFirstSentence;
          } else {
            int paragraphDistance = 1;
            int currParagraphIdx = quoteParagraphIdx - paragraphDistance;
            CoreMap currSentence = quoteFirstSentence;
            int currSentenceIdx = currSentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
            while (currParagraphIdx >= 0) {
//              Paragraph prevParagraph = paragraphs.get(prevParagraphIndex);
              //extract begin and end tokens of
              while (currSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) != currParagraphIdx) {
                currSentenceIdx--;
                currSentence = sentences.get(currSentenceIdx);
              }
              int prevParagraphBegin = getParagraphBeginToken(currSentence, sentences);
              int prevParagraphEnd = getParagraphEndToken(currSentence, sentences);

              if (prevParagraphBegin <= mention.begin && mention.end <= prevParagraphEnd) {
                mentionParagraphIdx = currParagraphIdx;
                sentenceInMentionParagraph = currSentence;

                features.setCount("leftParagraphDistance", paragraphDistance);
                if (paragraphDistance % 2 == 0)
                  features.setCount("leftParagraphDistanceEven", 1);
                break;
              }
              paragraphDistance++;
              currParagraphIdx--;
            }
          }
        } else //right
        {
          if (quoteParagraphBeginToken <= mention.begin && mention.end <= quoteParagraphEndToken) {
            features.setCount("rightParagraphDistance", 0);
            sentenceInMentionParagraph = quoteFirstSentence;
            mentionParagraphIdx = quoteParagraphIdx;
          } else {
            int paragraphDistance = 1;
            int nextParagraphIndex = quoteParagraphIdx + paragraphDistance;
            CoreMap currSentence = quoteFirstSentence;
            int currSentenceIdx = currSentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
            while (currSentenceIdx < sentences.size()) {
              while (currSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) != nextParagraphIndex) {
                currSentenceIdx++;
                currSentence = sentences.get(currSentenceIdx);
              }
              int nextParagraphBegin = getParagraphBeginToken(currSentence, sentences);
              int nextParagraphEnd = getParagraphEndToken(currSentence, sentences);
              if (nextParagraphBegin <= mention.begin && mention.end <= nextParagraphEnd) {
                sentenceInMentionParagraph = currSentence;
                features.setCount("rightParagraphDistance", paragraphDistance);
                break;
              }
              paragraphDistance++;
              nextParagraphIndex++;
            }

          }
        }

        //2. mention features
        if (sentenceInMentionParagraph != null) {
          int mentionParagraphBegin = getParagraphBeginToken(sentenceInMentionParagraph, sentences);
          int mentionParagraphEnd = getParagraphEndToken(sentenceInMentionParagraph, sentences);

          if (!(mentionParagraphBegin == quoteParagraphBeginToken && mentionParagraphEnd == quoteParagraphEndToken)) {
            List<CoreMap> quotesInMentionParagraph = paragraphToQuotes.getOrDefault(mentionParagraphIdx, new ArrayList<>());
            Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> namesInMentionParagraph = sieve.scanForNames(new Pair<>(mentionParagraphBegin, mentionParagraphEnd));

            features.setCount("quotesInMentionParagraph", quotesInMentionParagraph.size());
            features.setCount("wordsInMentionParagraph", mentionParagraphEnd - mentionParagraphBegin + 1);
            features.setCount("namesInMentionParagraph", namesInMentionParagraph.first.size());

            //mention ordering in paragraph it is in
            for (int i = 0; i < namesInMentionParagraph.second.size(); i++) {
              if (ExtractQuotesUtil.rangeContains(new Pair<>(mention.begin, mention.end), namesInMentionParagraph.second.get(i)))
                features.setCount("orderInParagraph", i);
            }

            //if mention paragraph is all one quote

            if (quotesInMentionParagraph.size() == 1) {
              CoreMap qInMentionParagraph = quotesInMentionParagraph.get(0);
              if (qInMentionParagraph.get(CoreAnnotations.TokenBeginAnnotation.class) == mentionParagraphBegin && qInMentionParagraph.get(CoreAnnotations.TokenEndAnnotation.class) - 1 == mentionParagraphEnd) {
                features.setCount("mentionParagraphIsInConversation", 1);
              } else {
                features.setCount("mentionParagraphIsInConversation", -1);
              }
            }
            for (CoreMap quoteIMP : quotesInMentionParagraph) {
              if (ExtractQuotesUtil.rangeContains(new Pair<>(quoteIMP.get(CoreAnnotations.TokenBeginAnnotation.class), quoteIMP.get(CoreAnnotations.TokenEndAnnotation.class) - 1), new Pair<>(mention.begin, mention.end)))
                features.setCount("mentionInQuote", 1);
            }
            if (features.getCount("mentionInQuote") != 1)
              features.setCount("mentionNotInQuote", 1);
          }
        }

        // nearby word syntax types...make sure to check if there are previous or next words
        // or there will be an array index crash
        if (mention.begin > 0) {
          CoreLabel prevWord = tokens.get(mention.begin - 1);
          features.setCount("prevWordType:" + prevWord.tag(), 1);
          if (punctuationForFeatures.contains(prevWord.lemma()))
            features.setCount("prevWordPunct:" + prevWord.lemma(), 1);
        }
        if (mention.end+1 < tokens.size()) {
          CoreLabel nextWord = tokens.get(mention.end + 1);
          features.setCount("nextWordType:" + nextWord.tag(), 1);
          if (punctuationForFeatures.contains(nextWord.lemma()))
            features.setCount("nextWordPunct:" + nextWord.lemma(), 1);
        }
//                    features.setCount("prevAndNext:" + prevWord.tag()+ ";" + nextWord.tag(), 1);

        //quote paragraph features
        List<CoreMap> quotesInQuoteParagraph = paragraphToQuotes.get(quoteParagraphIdx);
        features.setCount("QuotesInQuoteParagraph", quotesInQuoteParagraph.size());
        features.setCount("WordsInQuoteParagraph", quoteParagraphEndToken - quoteParagraphBeginToken + 1);
        features.setCount("NamesInQuoteParagraph", sieve.scanForNames(new Pair<>(quoteParagraphBeginToken, quoteParagraphEndToken)).first.size());

        //quote features
        features.setCount("quoteLength", quote.get(CoreAnnotations.TokenEndAnnotation.class) - quote.get(CoreAnnotations.TokenBeginAnnotation.class) + 1);
        for (int i = 0; i < quotesInQuoteParagraph.size(); i++) {
          if (quotesInQuoteParagraph.get(i).equals(quote)) {
            features.setCount("quotePosition", i + 1);
          }
        }
        if (features.getCount("quotePosition") == 0)
          throw new RuntimeException("Check this (equality not working)");


        Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> namesData = sieve.scanForNames(quoteRun);
        for (String name : namesData.first) {
          features.setCount("charactersInQuote:" + sd.characterMap.get(name).get(0).name, 1);
        }

        //if quote encompasses entire paragraph
        if (quote.get(CoreAnnotations.TokenBeginAnnotation.class) == quoteParagraphBeginToken && quote.get(CoreAnnotations.TokenEndAnnotation.class) == quoteParagraphEndToken) {
          features.setCount("isImplicitSpeaker", 1);
        } else {
          features.setCount("isImplicitSpeaker", -1);
        }

        //Vocative detection
        if (mention.type.equals("name")) {
          List<Person> pList = sd.characterMap.get(sieve.tokenRangeToString(new Pair<>(mention.begin, mention.end)));
          Person p = null;
          if (pList != null)
            p = pList.get(0);
          else {
            Pair<ArrayList<String>, ArrayList<Pair<Integer,Integer>>> scanForNamesResultPair
                = sieve.scanForNames(new Pair<>(mention.begin, mention.end));
            if (scanForNamesResultPair.first.size() != 0) {
              String scanForNamesResultString = scanForNamesResultPair.first.get(0);
              if (scanForNamesResultString != null && sd.characterMap.containsKey(scanForNamesResultString)) {
                p = sd.characterMap.get(scanForNamesResultString).get(0);
              }
            }
          }

          if (p != null) {
            for (String name : namesData.first) {
              if (p.aliases.contains(name))
                features.setCount("nameInQuote", 1);
            }

            if (quoteParagraphIdx > 0) {
//            Paragraph prevParagraph = paragraphs.get(ex.paragraph_idx - 1);
              List<CoreMap> quotesInPrevParagraph = paragraphToQuotes.getOrDefault(quoteParagraphIdx - 1, new ArrayList<>());
              List<Pair<Integer, Integer>> exclusionList = new ArrayList<>();
              for (CoreMap quoteIPP : quotesInPrevParagraph) {
                Pair<Integer, Integer> quoteRange = new Pair<>(quoteIPP.get(CoreAnnotations.TokenBeginAnnotation.class), quoteIPP.get(CoreAnnotations.TokenEndAnnotation.class));
                exclusionList.add(quoteRange);
                for (String name : sieve.scanForNames(quoteRange).first) {
                  if (p.aliases.contains(name))
                    features.setCount("nameInPrevParagraphQuote", 1);
                }
              }

              int sentenceIdx = quoteFirstSentence.get(CoreAnnotations.SentenceIndexAnnotation.class);

              CoreMap sentenceInPrevParagraph = null;
              for (int i = sentenceIdx - 1; i >= 0; i--) {
                CoreMap currSentence = sentences.get(i);
                if (currSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == quoteParagraphIdx - 1) {
                  sentenceInPrevParagraph = currSentence;
                  break;
                }
              }

              int prevParagraphBegin = getParagraphBeginToken(sentenceInPrevParagraph, sentences);
              int prevParagraphEnd = getParagraphEndToken(sentenceInPrevParagraph, sentences);
              List<Pair<Integer, Integer>> prevParagraphNonQuoteRuns = getRangeExclusion(new Pair<>(prevParagraphBegin, prevParagraphEnd), exclusionList);
              for (Pair<Integer, Integer> nonQuoteRange : prevParagraphNonQuoteRuns) {
                for (String name : sieve.scanForNames(nonQuoteRange).first) {
                  if (p.aliases.contains(name))
                    features.setCount("nameInPrevParagraphNonQuote", 1);
                }
              }
            }
          }
        }

        if (isTraining) {
          if (QuoteAttributionUtils.rangeContains(new Pair<>(gold.mentionStartTokenIndex, gold.mentionEndTokenIndex), new Pair<>(mention.begin, mention.end))) {
            RVFDatum<String, String> datum = new RVFDatum<>(features, "isMention");
            datum.setID(Integer.toString(dataset.size()));
            mapDatumToMention.put(dataset.size(), mention);
            dataset.add(datum);
          } else {
            RVFDatum<String, String> datum = new RVFDatum<>(features, "isNotMention");
            datum.setID(Integer.toString(dataset.size()));
            dataset.add(datum);
            mapDatumToMention.put(dataset.size(), mention);
          }
        }
        else {
          RVFDatum<String, String> datum = new RVFDatum<>(features, "none");
          datum.setID(Integer.toString(dataset.size()));
          mapDatumToMention.put(dataset.size(), mention);
          dataset.add(datum);
        }
      }

      mapQuoteToDataRange.put(quoteIdx, new Pair<>(initialSize, dataset.size() - 1));
    }
    return new FeaturesData(mapQuoteToDataRange, mapDatumToMention, dataset);
  }

  //TODO: potential bug in previous iteration: not implementing order reversal in eliminateDuplicates
  private static List<Sieve.MentionData> eliminateDuplicates(List<Sieve.MentionData> mentionCandidates)
  {
    List<Sieve.MentionData> newList = new ArrayList<>();
    Set<String> seenText = new HashSet<>();
    for (Sieve.MentionData mentionCandidate : mentionCandidates) {
      String text = mentionCandidate.text;
      if (!seenText.contains(text) || mentionCandidate.type.equals("Pronoun"))
        newList.add(mentionCandidate);
      seenText.add(text);
    }
    return newList;
  }

  // todo [cdm Nov 2020: Isn't there already a method like this for Classifier?
  public static void outputModel(String fileName, Classifier<String, String> clf) {
    try {
      FileOutputStream fo = new FileOutputStream(fileName);
      ObjectOutputStream so = new ObjectOutputStream(fo);
      so.writeObject(clf);
      so.flush();
      so.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void train(XMLToAnnotation.Data data, Properties props) {
    Map<String, List<Person>> characterMap = QuoteAttributionUtils.readPersonMap(props.getProperty("charactersPath"));
    Map<Integer,String> pronounCorefMap =
            QuoteAttributionUtils.setupCoref(props.getProperty("booknlpCoref"), characterMap, data.doc);
    Set<String> animacyList = QuoteAttributionUtils.readAnimacyList(QuoteAttributionAnnotator.ANIMACY_WORD_LIST);
    FeaturesData fd = featurize(new SieveData(data.doc, characterMap, pronounCorefMap, animacyList), data.goldList, true);
    ExtractQuotesClassifier quotesClassifier = new ExtractQuotesClassifier(fd.dataset);
    outputModel(props.getProperty("modelPath"), quotesClassifier.getClassifier());
  }


  public static void main(String[] args) throws Exception {
    String home = "/home/mjfang/action_grammars/";
    // make the first argument one for a base directory
    String specificFile = "1PPDevUncollapsed.props";
    if (args.length >= 1) {
      home = args[0];
    }
    if (args.length >= 2) {
      specificFile = args[1];
    }
    System.out.println("Base directory: " + home);
    Properties props = StringUtils.propFileToProperties(home + "ExtractQuotesXMLScripts/" + specificFile);
    XMLToAnnotation.Data data = XMLToAnnotation.readXMLFormat(props.getProperty("file"));
    Properties propsPara = new Properties();
    propsPara.setProperty("paragraphBreak", "one");
    ParagraphAnnotator pa = new ParagraphAnnotator(propsPara, false);
    pa.annotate(data.doc);
    Properties annotatorProps = new Properties();
    annotatorProps.setProperty("charactersPath", props.getProperty("charactersPath")); //"characterList.txt"
    annotatorProps.setProperty("booknlpCoref", props.getProperty("booknlpCoref"));
    annotatorProps.setProperty("modelPath", props.getProperty("modelPath"));//"model.ser");
    QuoteAttributionAnnotator qaa = new QuoteAttributionAnnotator(annotatorProps);
    qaa.annotate(data.doc);
    ChapterAnnotator ca = new ChapterAnnotator();
    ca.annotate(data.doc);
    train(data, annotatorProps);
  }

}
