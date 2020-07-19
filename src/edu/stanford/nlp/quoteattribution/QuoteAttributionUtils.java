package edu.stanford.nlp.quoteattribution;

import java.io.File;
import java.util.*;

import edu.stanford.nlp.coref.*;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/** Various utility functions for Quote Attribution processing.
 *
 *  @author Grace Muzny
 *  @author Michael Fang
 */
public class QuoteAttributionUtils {

  private final static Redwood.RedwoodChannels log = Redwood.channels(QuoteAttributionUtils.class);

  /** Take a context for a quote using the following preferences in order:
   *  (i) prior words in same sentence, if at least two, (ii) following words in same sentence, if at least two,
   *  (iii) pPrevious sentence, if any, (iv) next sentence. This still isn't perfect, when there is material in
   *  a sentence before and after the quote, the speech verb may be afterwards.
   *
   * @param doc The document we are analyzing
   * @param quote The particular quote that we are finding a sentence remainder for in which to find speech verb
   * @return A document token index range for the sentence or part sentence.
   */
  // TODO: change this to take the nearest (non-quote) sentence (even if not part of it)
  // TODO [cdm 2020]: (Chris doesn't understand why we don't just keep the whole current sentence and use its parse.
  //   I'm not rewriting that right now, but I think it might work better!
  public static Pair<Integer, Integer> getRemainderInSentence(Annotation doc, CoreMap quote) {
    Pair<Boolean, Pair<Integer, Integer>> pair = getTokenRangePrecedingQuote(doc, quote);
    // log.info("For " + quote.toShorterString() + ", getTokenRangePrecedingQuote pair is " + pair);
    boolean sameSent = pair != null && pair.first();
    Pair<Integer, Integer> range = (pair == null) ? null: pair.second();
    if ( ! sameSent || range == null) {
      Pair<Boolean, Pair<Integer, Integer>> pairAfter = getTokenRangeFollowingQuote(doc, quote);
      if (pairAfter != null && (pairAfter.first() || range == null)) {
        range = pairAfter.second();
      }
    }
    // log.info("  selected range is: " + range);
    return range;
  }

  public static int getQuoteParagraphIndex(Annotation doc, CoreMap quote) {
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    return sentences.get(quote.get(CoreAnnotations.SentenceBeginAnnotation.class)).get(CoreAnnotations.ParagraphIndexAnnotation.class);
  }

  //taken from WordToSentencesAnnotator
  private static CoreMap constructSentence(List<CoreLabel> sentenceTokens, CoreMap prevSentence, CoreMap sentence,
                                           DependencyParser parser) {
    // get the sentence text from the first and last character offsets
    int begin = sentenceTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int last = sentenceTokens.size() - 1;
    int end = sentenceTokens.get(last).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    String sentenceText = prevSentence.get(CoreAnnotations.TextAnnotation.class) + sentence.get(CoreAnnotations.TextAnnotation.class);

    // create a sentence annotation with text and token offsets
    Annotation newSentence = new Annotation(sentenceText);
    newSentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
    newSentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
    newSentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
    newSentence.set(CoreAnnotations.TokenBeginAnnotation.class, prevSentence.get(CoreAnnotations.TokenBeginAnnotation.class));
    newSentence.set(CoreAnnotations.TokenEndAnnotation.class, sentence.get(CoreAnnotations.TokenEndAnnotation.class));
    newSentence.set(CoreAnnotations.ParagraphIndexAnnotation.class, sentence.get(CoreAnnotations.ParagraphIndexAnnotation.class));
    newSentence.set(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class, getParse(newSentence, parser));

    return newSentence;
//    newSentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentences.size());
  }

  public static class EnhancedSentenceAnnotation implements CoreAnnotation<CoreMap> {
    @Override
    public Class<CoreMap> getType() {
      return CoreMap.class;
    }
  }

  public static void addEnhancedSentences(Annotation doc, DependencyParser parser) {
    //for every sentence that begins a paragraph: append this sentence and the previous one and see if sentence splitter would make a single sentence out of it. If so, add as extra sentence.
    //for each sieve that potentially uses augmentedSentences in original:

    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);

    // create SentenceSplitter that never splits on newline
    WordToSentenceProcessor<CoreLabel> wsp =
            new WordToSentenceProcessor<>(WordToSentenceProcessor.NewlineIsSentenceBreak.NEVER);

    // int prevParagraph = 0;
    for (int i = 1, numSentences = sentences.size(); i < numSentences; i++) {
      CoreMap sentence = sentences.get(i);
      CoreMap prevSentence = sentences.get(i-1);

      List<CoreLabel> tokensConcat = new ArrayList<>();
      tokensConcat.addAll(prevSentence.get(CoreAnnotations.TokensAnnotation.class));
      tokensConcat.addAll(sentence.get(CoreAnnotations.TokensAnnotation.class));
      List<List<CoreLabel>> sentenceTokens = wsp.process(tokensConcat);
      if (sentenceTokens.size() == 1) { //wsp would have put them into a single sentence --> add enhanced sentence.
        sentence.set(EnhancedSentenceAnnotation.class, constructSentence(sentenceTokens.get(0), prevSentence, sentence, parser));
      }
    }
  }


  /** Gets range of tokens that are in the same sentence as the beginning of the quote that precede it, if they exist,
   *  or the previous sentence, if it is in the same paragraph. Return which you've used in extra argument.
   *  Also, ensure that the difference is at least two tokens.
   *
   *  @return A Pair of whether same sentence followed by a Pair that is a token range in the document.
   */
  public static Pair<Boolean, Pair<Integer, Integer>> getTokenRangePrecedingQuote(Annotation doc, CoreMap quote) {
    List<CoreMap> docSentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    int quoteBeginTokenIndex = quote.get(CoreAnnotations.TokenBeginAnnotation.class);
    if (quoteBeginTokenIndex <= 2) {
      return null;
    }
    int quoteBeginSentenceIndex = quote.get(CoreAnnotations.SentenceBeginAnnotation.class);
    CoreMap beginSentence = docSentences.get(quoteBeginSentenceIndex);

    if (beginSentence.get(EnhancedSentenceAnnotation.class) != null) {
      beginSentence = beginSentence.get(EnhancedSentenceAnnotation.class);
    }

    int quoteIndex = quote.get(CoreAnnotations.QuotationIndexAnnotation.class);
    if (beginSentence.get(CoreAnnotations.TokenBeginAnnotation.class) < quoteBeginTokenIndex - 1) {
      //check previous quote to make sure boundary is okay - modify if necessary.
      if (quoteIndex > 0) {
        CoreMap prevQuote = doc.get(CoreAnnotations.QuotationsAnnotation.class).get(quoteIndex - 1);
        int prevQuoteTokenEnd = prevQuote.get(CoreAnnotations.TokenEndAnnotation.class);
        if (prevQuoteTokenEnd > beginSentence.get(CoreAnnotations.TokenBeginAnnotation.class)) {
          if (prevQuoteTokenEnd + 1 == quoteBeginTokenIndex) {
            return null;
          }
          return new Pair<>(Boolean.TRUE, new Pair<>(prevQuoteTokenEnd + 1, quoteBeginTokenIndex - 1));
        }
      }
      return new Pair<>(Boolean.TRUE, new Pair<>(beginSentence.get(CoreAnnotations.TokenBeginAnnotation.class), quoteBeginTokenIndex - 1));
    } else if (quoteBeginSentenceIndex > 0) { //try previous sentence- if it is in the same paragraph.
      int currParagraph = beginSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class);
      CoreMap prevSentence = docSentences.get(quoteBeginSentenceIndex - 1);
      //check if prevSentence is in same paragraph
      if (prevSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == currParagraph) {
        //check previous quote boundary
        if (quoteIndex > 0) {
          CoreMap prevQuote = doc.get(CoreAnnotations.QuotationsAnnotation.class).get(quoteIndex - 1);
          int prevQuoteTokenEnd = prevQuote.get(CoreAnnotations.TokenEndAnnotation.class);
          if (prevQuoteTokenEnd > prevSentence.get(CoreAnnotations.TokenBeginAnnotation.class)) {
            if (prevQuoteTokenEnd + 1 == quoteBeginTokenIndex) {
              return null;
            }
            return new Pair<>(Boolean.FALSE, new Pair<>(prevQuoteTokenEnd + 1, quoteBeginTokenIndex - 1));
          }
          return new Pair<>(Boolean.FALSE, new Pair<>(prevSentence.get(CoreAnnotations.TokenBeginAnnotation.class), quoteBeginTokenIndex - 1));
        }
      }
    }
    return null;
  }

  /** Gets range of tokens that are in the same sentence as the beginning of the quote that follow it, if they exist,
   *  or the next sentence, if it is in the same paragraph. Return which you've used in extra argument.
   *  Also, ensure that the difference is at least two tokens.
   *
   *  @return A Pair of whether same sentence followed by a Pair that is a token range in the document.
   */
  public static Pair<Boolean, Pair<Integer, Integer>> getTokenRangeFollowingQuote(Annotation doc, CoreMap quote) {
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    int quoteEndTokenIndex = quote.get(CoreAnnotations.TokenEndAnnotation.class);
    if (quoteEndTokenIndex >= doc.get(CoreAnnotations.TokensAnnotation.class).size() - 2) {
      return null;
    }
    int quoteEndSentenceIndex = quote.get(CoreAnnotations.SentenceEndAnnotation.class);
    CoreMap endSentence = sentences.get(quoteEndSentenceIndex);
    int quoteIndex = quote.get(CoreAnnotations.QuotationIndexAnnotation.class);
    if (quoteEndTokenIndex < endSentence.get(CoreAnnotations.TokenEndAnnotation.class) - 2) { //quote TokenEndAnnotation is inclusive; sentence TokenEndAnnotation is exclusive
      //check next quote to ensure boundary
      if (quoteIndex < quotes.size() - 1) {
        CoreMap nextQuote = quotes.get(quoteIndex + 1);
        int nextQuoteTokenBegin = nextQuote.get(CoreAnnotations.TokenBeginAnnotation.class);
        if (nextQuoteTokenBegin < endSentence.get(CoreAnnotations.TokenEndAnnotation.class) - 1) {
          if (quoteEndTokenIndex + 1 == nextQuoteTokenBegin) {
            return null;
          }
          return new Pair<>(Boolean.TRUE, new Pair<>(quoteEndTokenIndex + 1, nextQuoteTokenBegin - 1));
        }
      }
      return new Pair<>(Boolean.TRUE, new Pair<>(quoteEndTokenIndex + 1, endSentence.get(CoreAnnotations.TokenEndAnnotation.class) - 1));
    } else if (quoteEndSentenceIndex < sentences.size() - 1) { //check next sentence
      CoreMap nextSentence = sentences.get(quoteEndSentenceIndex + 1);
      int currParagraph = endSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class);
      if (nextSentence.get(CoreAnnotations.ParagraphIndexAnnotation.class) == currParagraph) {
        //check next quote boundary
        if (quoteIndex < quotes.size() - 1) {
          CoreMap nextQuote = quotes.get(quoteIndex + 1);
          int nextQuoteTokenBegin = nextQuote.get(CoreAnnotations.TokenBeginAnnotation.class);
          if (nextQuoteTokenBegin < nextSentence.get(CoreAnnotations.TokenEndAnnotation.class) - 1) {
            if (quoteEndTokenIndex + 1 == nextQuoteTokenBegin) {
              return null;
            }
            return new Pair<>(Boolean.FALSE, new Pair<>(quoteEndTokenIndex + 1, nextQuoteTokenBegin - 1));
          }
          return new Pair<>(Boolean.FALSE, new Pair<>(quoteEndTokenIndex + 1, nextSentence.get(CoreAnnotations.TokenEndAnnotation.class) - 1));
        }
      }
    }
    return null;
  }

  private static CoreMap constructCoreMap(Annotation doc, Pair<Integer, Integer> run) {
    List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    // check if the second part of the run is a *NL* token, adjust accordingly
    int endTokenIndex = run.second;
    while (endTokenIndex > 0 && tokens.get(endTokenIndex).get(CoreAnnotations.IsNewlineAnnotation.class)) {
      endTokenIndex--;
    }
    // get the sentence text from the first and last character offsets
    int begin = tokens.get(run.first).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int end = tokens.get(endTokenIndex).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    String sentenceText = doc.get(CoreAnnotations.TextAnnotation.class).substring(begin, end);

    List<CoreLabel> sentenceTokens = tokens.subList(run.first, endTokenIndex+1);

    // create a sentence annotation with text and token offsets
    CoreMap sentence = new Annotation(sentenceText);
    sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
    sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
    sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
    return sentence;
  }

  private static SemanticGraph getParse(CoreMap sentence, DependencyParser parser) {
    GrammaticalStructure gs = parser.predict(sentence);
//    GrammaticalStructure.Extras maximal = GrammaticalStructure.Extras.MAXIMAL;
//        SemanticGraph deps = SemanticGraphFactory.makeFromTree(gs, SemanticGraphFactory.Mode.ENHANCED, maximal, true, null),
//                uncollapsedDeps = SemanticGraphFactory.makeFromTree(gs, SemanticGraphFactory.Mode.BASIC, maximal, true, null),
//    SemanticGraph ccDeps = SemanticGraphFactory.makeFromTree(gs, SemanticGraphFactory.Mode.ENHANCED_PLUS_PLUS, maximal, true, null);
    SemanticGraph ccDeps = SemanticGraphFactory.generateEnhancedPlusPlusDependencies(gs);
    return ccDeps;
  }

  public static void annotateForDependencyParse(Annotation doc, DependencyParser parser) {
    // for each quote, dependency parse sentences with quote-removed (if it exists).
    // todo [cdm 2020]: Maybe it would actually dependency parse better without removing the quote. Try this!

    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    for (CoreMap quote : quotes) {
      Pair<Integer, Integer> range = getRemainderInSentence(doc, quote);
      if (range != null) {
        CoreMap sentenceQuoteRemoved = constructCoreMap(doc, range);
        quote.set(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class, 
            getParse(sentenceQuoteRemoved, parser));
        // log.info("For " + quote.toShorterString());
        // log.info("sentence remainder = " + sentenceQuoteRemoved.toShorterString());
        // log.info(getParse(sentenceQuoteRemoved, parser));
      }
    }
  }


  public static int getParagraphRank(Annotation doc, CoreMap quote) {
    int quoteParaBegin = getParagraphBeginNumber(quote);
    List<CoreMap> sents = getSentsInParagraph(doc, quoteParaBegin);
    List<CoreMap> quotesInParagraph = Generics.newArrayList();
    for (CoreMap q : doc.get(CoreAnnotations.QuotationsAnnotation.class)) {
      if (getParagraphBeginNumber(q) == quoteParaBegin) {
        quotesInParagraph.add(q);
      }
    }
    return quotesInParagraph.indexOf(quote);
  }

  public static int getParagraphBeginNumber(CoreMap quote) {
    List<CoreMap> sents = quote.get(CoreAnnotations.SentencesAnnotation.class);
    return sents.get(0).get(CoreAnnotations.ParagraphIndexAnnotation.class);
  }

  public static int getParagraphEndNumber(CoreMap quote) {
    List<CoreMap> sents = quote.get(CoreAnnotations.SentencesAnnotation.class);
    return sents.get(sents.size() - 1).get(CoreAnnotations.ParagraphIndexAnnotation.class);
  }

  public static List<CoreMap> getSentsInParagraph(Annotation doc, int paragraph) {
    List<CoreMap> sents = doc.get(CoreAnnotations.SentencesAnnotation.class);
    List<CoreMap> targets = Generics.newArrayList();
    for (CoreMap sent : sents) {
      if (sent.get(CoreAnnotations.ParagraphIndexAnnotation.class) == paragraph) {
        targets.add(sent);
      }
    }
    return sents;
  }

  public static List<CoreMap> getSentsForQuoteParagraphs(Annotation doc, CoreMap quote) {
    List<CoreMap> sents = doc.get(CoreAnnotations.SentencesAnnotation.class);
    int paragraphBegin = getParagraphBeginNumber(quote);
    int paragraphEnd = getParagraphEndNumber(quote);
    List<CoreMap> targets = Generics.newArrayList();
    for (CoreMap sent : sents) {
      if (sent.get(CoreAnnotations.ParagraphIndexAnnotation.class) >= paragraphBegin &&
          sent.get(CoreAnnotations.ParagraphIndexAnnotation.class) <= paragraphEnd) {
        targets.add(sent);
      }
    }
    return sents;
  }

  public static Map<String, Person.Gender> readGenderedNounList(String filename) {
    Map<String, Person.Gender> genderMap = Generics.newHashMap();
    List<String> lines = IOUtils.linesFromFile(filename);
    for (String line : lines) {
      String[] nounAndStats = line.split("\\t");
      String[] stats = nounAndStats[1].split(" ");
      Person.Gender gender = (Integer.parseInt(stats[0]) >= Integer.parseInt(stats[1])) ?
          Person.Gender.MALE : Person.Gender.FEMALE;
      genderMap.put(nounAndStats[0], gender);
    }
    return genderMap;
  }

  public static Set<String> readFamilyRelations(String filename) {
    Set<String> familyRelations = Generics.newHashSet();
    List<String> lines = IOUtils.linesFromFile(filename);
    for (String line : lines) {
      if (line.trim().length() > 0) {
        familyRelations.add(line.toLowerCase().trim());
      }
    }
    return familyRelations;
  }

  public static Set<String> readAnimacyList(String filename) {
    Set<String> animacyList = Generics.newHashSet();
    List<String> lines = IOUtils.linesFromFile(filename);
    for (String line : lines) {
      if (!Character.isUpperCase(line.charAt(0))) //ignore names
        animacyList.add(line);
    }
    return animacyList;
  }

  //map each alias(i.e. the name of a character) to a character, potentially multiple if ambiguous.
  public static Map<String, List<Person>> readPersonMap(List<Person> personList) {
    Map<String, List<Person>>  personMap = new HashMap<>();
    for (Person person : personList) {
      for (String alias : person.aliases) {
        personMap.computeIfAbsent(alias, k -> new ArrayList<>());
        personMap.get(alias).add(person);
      }
    }
    return personMap;
  }

  public static Map<String, List<Person>> readPersonMap(String fileName) {
    return readPersonMap(readCharacterList(fileName));
  }

  public static ArrayList<Person> readCharacterList(String filename) {
    ArrayList<Person> characterList = new ArrayList<>();
    //format: name;Gender(M or F); aliases (everything semi-colon delimited)
    for (String line : IOUtils.readLines(new File(filename))) {
      String[] terms = line.split(";");

      if (terms.length == 2) {
        characterList.add(new Person(terms[0], terms[1], null));
      } else {
        ArrayList<String> aliases = new ArrayList<>();
        for (int l = 2; l < terms.length; l++) {
          aliases.add(terms[l]);
        }
        aliases.add(terms[0]);
        characterList.add(new Person(terms[0], terms[1], aliases));
      }
    }
    return characterList;
  }

  public static boolean isPronominal(String potentialPronoun) {
    String lower = potentialPronoun.toLowerCase();
    return lower.equals("he") || lower.equals("she") || lower.equals("they");
  }

  /** Create a map of coreference to canonical entities for pronouns.
   *  (todo: It'd be nice to also expand this to common nouns if they are coreferent to things!)
   *
   *  @param bammanFile A file giving coreferences derived from analysis of David Bamman's bookNLP.
   *                    This may be null, and then CoreNLP coref is used
   *  @param characterMap A mapping of names to people.
   *                      This is only used for Bamman coreference, otherwise, it can be null.
   *                      Or maybe not, maybe it is set up anyway from entity mentions....
   *  @param doc The CoreNLP document
   *  @return A Map from the pronoun word charOffsetBegin index (change to head's index!) to a
   *          String form of the canonical entity
   */
  public static Map<Integer,String> setupCoref(String bammanFile,
                                                Map<String, List<Person>> characterMap,
                                                Annotation doc ) {
    if (bammanFile != null) {
      //TODO: integrate coref
      Map<Integer, List<CoreLabel>> bammanTokens = BammanCorefReader.readTokenFile(bammanFile, doc);
      return mapBammanToCharacterMap(bammanTokens, characterMap);
    } else {
      // Hey, this does use our CoreNLP coref! Let's make more use of it.
      Map<Integer,String> pronounCorefMap = new HashMap<>();
      for (CorefChain cc : doc.get(CorefCoreAnnotations.CorefChainAnnotation.class).values()) {
        CorefChain.CorefMention m = cc.getRepresentativeMention();
        String representativeMention = m.mentionSpan;
        for (CorefChain.CorefMention cm : cc.getMentionsInTextualOrder()) {
          if (isPronominal(cm.mentionSpan)) {
            CoreMap cmSentence =
                doc.get(CoreAnnotations.SentencesAnnotation.class).get(cm.sentNum-1);
            List<CoreLabel> cmTokens = cmSentence.get(CoreAnnotations.TokensAnnotation.class);
            int charBegin = cmTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
            pronounCorefMap.put(charBegin, representativeMention);
          }
        }
      }
      return pronounCorefMap;
    }
  }

  //return map of index of CharacterOffsetBeginAnnotation to name of character.
  protected static Map<Integer,String> mapBammanToCharacterMap(Map<Integer, List<CoreLabel>> BammanTokens,
                                                               Map<String, List<Person>> characterMap) {
    Map<Integer, String> indexToCharacterName = new HashMap<>();

    // first, link the
    for (Integer characterID : BammanTokens.keySet()) {
      List<CoreLabel> tokens = BammanTokens.get(characterID);

      Counter<String> names = new ClassicCounter<>();
      int prevEnd = -2;
      String prevName = "";
      for (CoreLabel token : tokens) {
        if (token.tag().equals("NNP")) {
          int beginIndex = token.beginPosition();
          if (prevEnd +1 == beginIndex) { //adjacent to last token
            prevName += " " + token.word();
          }
          else { //not adjacent candidate: clear and then
            if ( ! prevName.isEmpty()) {
              names.incrementCount(prevName, 1);
            }
            prevName = token.word();
            prevEnd = token.endPosition();
          }
        } else {
          if (!prevName.equals("")) {
            names.incrementCount(prevName, 1);
          }
          prevName = "";
          prevEnd = -2;
        }
      }
      boolean flag = false;

      //exact match
      for (String name : Counters.toSortedList(names)) {
        if (characterMap.containsKey(name)) {
          indexToCharacterName.put(characterID, name);
          flag = true;
          break;
        }
      }
      //not exact match: try partial match
      if (!flag) {
        for (String charName : characterMap.keySet()) {
          for (String name : Counters.toSortedList(names)) {
            if (charName.contains(name)) {
              indexToCharacterName.put(characterID, charName);
              flag = true;
              log.info("contingency name found" + characterID);
              log.info(Counters.toSortedList(names));
              break;
            }

          }
          if (flag) {
            break;
          }
        }
      }
      if (!flag) {
        log.info("no name found :( " + characterID);
        log.info(Counters.toSortedList(names));
      }
    }

    Map<Integer, String> beginIndexToName = new HashMap<>();
    for (Integer charId: BammanTokens.keySet()) {
      if (indexToCharacterName.get(charId) == null)
        continue;
      List<CoreLabel> tokens = BammanTokens.get(charId);
      for (CoreLabel btoken : tokens) {
        if (btoken.tag().equals("PRP"))
          beginIndexToName.put(btoken.beginPosition(),indexToCharacterName.get(charId));
      }
    }
    return beginIndexToName;
  }

  //return true if one is contained in the other.
  public static boolean rangeContains(Pair<Integer, Integer> r1, Pair<Integer, Integer> r2) {
    return ((r1.first <= r2.first && r1.second >= r2.first) || (r1.first <= r2.second && r1.second >= r2.second));
  }

}
