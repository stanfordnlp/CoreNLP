package edu.stanford.nlp.quoteattribution.Sieves.QMSieves;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.QuoteAttributionAnnotator;
import edu.stanford.nlp.quoteattribution.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
// import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

/**
 * @author Grace Muzny
 */
public class DependencyParseSieve extends QMSieve {

  /** A logger for this class */
  // private static final Redwood.RedwoodChannels log = Redwood.channels(DependencyParseSieve.class);

  public DependencyParseSieve(Annotation doc, Map<String, List<Person>> characterMap,
                              Map<Integer,String> pronounCorefMap, Set<String> animacySet) {
    super(doc, characterMap, pronounCorefMap, animacySet, "Deterministic depparse");
  }

  public void doQuoteToMention(Annotation doc) {
    // Trigram patterns
    // p/r 1/.304
    dependencyParses(doc);
    oneSpeakerSentence(doc);
  }

  private boolean inRange(Pair<Integer, Integer> range, int val) {
    return range.first <= val && val <= range.second;
  }

  //using quote-removed depparses
  public void dependencyParses(Annotation doc) {
    List<CoreMap> quotes = doc.get(CoreAnnotations.QuotationsAnnotation.class);
    // List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    // List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    for (CoreMap quote : quotes) {
      // log.info("DepParseSieve: examining " + quote.toShorterString());
      if (quote.get(QuoteAttributionAnnotator.MentionAnnotation.class) != null) {
        // we've already found a speaker mention
        continue;
      }
      Pair<Integer, Integer> range = QuoteAttributionUtils.getRemainderInSentence(doc, quote);
      if (range == null) {
        continue;
      }

      // log.info("DepParseSieve: passed initial checks");
      //search for mentions in the first run
      Pair<ArrayList<String>, ArrayList<Pair<Integer, Integer>>> namesAndNameIndices = scanForNames(range);
      ArrayList<String> names = namesAndNameIndices.first;
      ArrayList<Pair<Integer, Integer>> nameIndices = namesAndNameIndices.second;
      SemanticGraph graph = quote.get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
      SemgrexMatcher matcher = subjVerbPattern.matcher(graph);
      List<Pair<IndexedWord, IndexedWord>> subjVerbPairs = new ArrayList<>();
      // TODO: check and see if this is necessary
      while (matcher.find()) {
        IndexedWord subj = matcher.getNode("SUBJ");
        IndexedWord verb = matcher.getNode("VERB");
        subjVerbPairs.add(new Pair<>(subj, verb));
      }

      List<IndexedWord> vbs = graph.getAllNodesByPartOfSpeechPattern("VB.*");
      // log.info("Verb indices are " + vbs);
      for (IndexedWord iw : vbs) {
        // does it have an nsubj child?
        Set<IndexedWord> children = graph.getChildren(iw);
        List<IndexedWord> deps = Generics.newArrayList();
        IndexedWord nsubj = null;
        for (IndexedWord child : children) {
          SemanticGraphEdge sge = graph.getEdge(iw, child);
          if (sge.getRelation().getShortName().equals("dep") && child.tag().startsWith("VB")) {
            deps.add(child);
          } else if (sge.getRelation().getShortName().equals("nsubj")) {
            nsubj = child;
          }
        }
        if (nsubj != null) {
          for (IndexedWord dep : deps) {
            subjVerbPairs.add(new Pair<>(nsubj, dep));
          }
        }
      }
      // look for a speech verb
      for (Pair<IndexedWord, IndexedWord> SVPair : subjVerbPairs) {
        IndexedWord verb = SVPair.second;
        IndexedWord subj = SVPair.first;
        // log.info("Checking from subjVerbPairs: " + verb + " " + subj);
        // check if subj and verb outside of quote
        int verbTokPos = tokenToLocation(verb.backingLabel());
        int subjTokPos = tokenToLocation(verb.backingLabel());
        if (inRange(range, verbTokPos) && inRange(range, subjTokPos) && commonSpeechWords.contains(verb.lemma())) {
          if (subj.tag().equals("NNP") || subj.tag().equals("NNPS")) {
            int startChar = subj.beginPosition();
            // if (names.isEmpty()) log.info("No names for this verb");
            for (int i = 0; i < names.size(); i++) {
              Pair<Integer, Integer> nameIndex = nameIndices.get(i); //avoid names that don't actually exist in
              if (rangeContainsCharIndex(nameIndex, startChar)) {
                // log.info("DepParseSieve: adding name " + tokenRangeToString(nameIndex));
                fillInMention(quote, tokenRangeToString(nameIndex), nameIndex.first, nameIndex.second,
                        sieveName, NAME);
                break;
              }
              // else {
              //   log.info("Name " + names.get(i) + " not in range " + nameIndex + " " + startChar);
              // }
            }
          } else if (subj.tag().equals("PRP")) {
            int loc = tokenToLocation(subj.backingLabel());
            // log.info("DepParseSieve: adding animate noun " + tokenRangeToString(loc));
            fillInMention(quote, subj.word(), loc, loc, sieveName, PRONOUN);
            break;
          } else if ((subj.tag().equals("NN") || subj.tag().equals("NNS")) &&
                  animacySet.contains(subj.word().toLowerCase())) {
            int loc = tokenToLocation(subj.backingLabel());
            // log.info("DepParseSieve: adding animate noun " + tokenRangeToString(loc));
            fillInMention(quote, subj.word(), loc, loc, sieveName, ANIMATE_NOUN);
            break;
          }
          // else {
          //   log.info("Subj tag not recognized or not animate is " + subj.tag() + " " + subj);
          // }
        }
        // else {
        //   log.info("Nothing found! " + inRange(range, verbTokPos) + " " + inRange(range, subjTokPos) + " " +
        //           commonSpeechWords.contains(verb.lemma()) + " " + verb.lemma());
        // }
      }
      // log.info("DepParseSieve: finished that quote");
    }
  }

}
