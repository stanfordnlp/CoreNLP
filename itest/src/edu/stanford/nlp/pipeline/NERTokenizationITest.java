package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.*;

import edu.stanford.nlp.util.StringUtils;
import junit.framework.TestCase;

import java.util.*;
import java.util.stream.*;

/**
 * Various testing of the NER-specific tokenization.
 */


public class NERTokenizationITest extends TestCase {

  public StanfordCoreNLP pipeline;

  /** Process a test example **/
  public static void processTestExample(CoreDocument doc, List<CoreLabel> goldTokens) {
    // check documents have proper numbers of tokens
    assertEquals(goldTokens.size(), doc.tokens().size());
    // check NER tokens are as expected
    for (int i = 0 ; i < doc.tokens().size() ; i++) {
      assertEquals(goldTokens.get(i).word(), doc.tokens().get(i).word(), goldTokens.get(i).word());
      assertEquals(goldTokens.get(i).ner(), doc.tokens().get(i).ner());
    }
  }

  /** Convert lists of words and labels into lists of tokens **/
  public static List<CoreLabel> stringListsToCoreLabels(List<String> words, List<String> nerLabels) {
    List<CoreLabel> tokens = words.stream().map(w -> CoreLabel.wordFromString(w)).collect(Collectors.toList());
    for (int i = 0 ; i < nerLabels.size() ; i++) {
      tokens.get(i).setNER(nerLabels.get(i));
    }
    return tokens;
  }

  /** Run full test on English **/
  public void testEnglishNERTokenization() {
    // setup pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    pipeline = new StanfordCoreNLP(props);
    // set up data lists
    List<String> docs = new ArrayList<>();
    List<List<String>> words = new ArrayList<>();
    List<List<String>> labels = new ArrayList<>();
    List<List<CoreLabel>> goldTokens = new ArrayList<>();
    // basic example
    docs.add("Barack Obama was born in Hawaii.");
    words.add(Arrays.asList("Barack", "Obama", "was", "born", "in", "Hawaii", "."));
    labels.add(Arrays.asList("PERSON", "PERSON", "O", "O", "O", "STATE_OR_PROVINCE", "O"));
    // basic example with "-"
    docs.add("She traveled to Port-au-Prince over the summer with Jane Smith.");
    words.add(Arrays.asList("She", "traveled", "to", "Port", "-", "au", "-", "Prince", "over", "the", "summer", "with",
        "Jane", "Smith", "."));
    labels.add(Arrays.asList("O", "O", "O", "CITY", "CITY", "CITY", "CITY", "CITY", "O", "O", "DATE", "O",
        "PERSON", "PERSON", "O"));
    // document that ends with -
    docs.add("He was cut off saying welc-");
    words.add(Arrays.asList("He", "was", "cut", "off", "saying", "welc", "-"));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "O"));
    // special case for things like Chicago-based
    docs.add("They are big fans of the Chicago-based musician.");
    words.add(Arrays.asList("They", "are", "big", "fans", "of", "the", "Chicago", "-", "based", "musician", "."));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "CITY", "O", "O", "TITLE", "O"));
    for (int i = 0 ; i < words.size() ; i++) {
      goldTokens.add(stringListsToCoreLabels(words.get(i), labels.get(i)));
    }
    for (int i = 0 ; i < docs.size() ; i++) {
      CoreDocument exampleDoc = new CoreDocument(docs.get(i));
      pipeline.annotate(exampleDoc);
      processTestExample(exampleDoc, goldTokens.get(i));
    }
  }

  /** Run full test on English, only using the statistical model **/
  public void testEnglishNERTokenizationJustStatistical() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("ner.applyFineGrained", "false");
    pipeline = new StanfordCoreNLP(props);
    // set up data lists
    List<String> docs = new ArrayList<>();
    List<List<String>> words = new ArrayList<>();
    List<List<String>> labels = new ArrayList<>();
    List<List<CoreLabel>> goldTokens = new ArrayList<>();
    // basic example
    docs.add("Barack Obama was born in Hawaii.");
    words.add(Arrays.asList("Barack", "Obama", "was", "born", "in", "Hawaii", "."));
    labels.add(Arrays.asList("PERSON", "PERSON", "O", "O", "O", "LOCATION", "O"));
    // basic example with "-"
    docs.add("She traveled to Port-au-Prince over the summer with Jane Smith.");
    words.add(Arrays.asList("She", "traveled", "to", "Port", "-", "au", "-", "Prince", "over", "the", "summer", "with",
        "Jane", "Smith", "."));
    labels.add(Arrays.asList("O", "O", "O", "LOCATION", "LOCATION", "LOCATION", "LOCATION", "LOCATION", "O", "O",
        "DATE", "O", "PERSON", "PERSON", "O"));
    // document that ends with -
    docs.add("He was cut off saying welc-");
    words.add(Arrays.asList("He", "was", "cut", "off", "saying", "welc", "-"));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "O"));
    // special case for things like Chicago-based
    docs.add("They are big fans of the Chicago-based musician.");
    words.add(Arrays.asList("They", "are", "big", "fans", "of", "the", "Chicago", "-", "based", "musician", "."));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "LOCATION", "O", "O", "O", "O"));
    for (int i = 0 ; i < words.size() ; i++) {
      goldTokens.add(stringListsToCoreLabels(words.get(i), labels.get(i)));
    }
    for (int i = 0 ; i < docs.size() ; i++) {
      CoreDocument exampleDoc = new CoreDocument(docs.get(i));
      pipeline.annotate(exampleDoc);
      processTestExample(exampleDoc, goldTokens.get(i));
    }
  }

  /** Run full test on English, using PTB3 escaping and only the statistical **/
  public void testEnglishNERTokenizationWithPTB3EscapingJustStatistical() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("tokenize.options", "ptb3Escaping=true");
    props.setProperty("ner.applyFineGrained", "false");
    pipeline = new StanfordCoreNLP(props);
    // set up data lists
    List<String> docs = new ArrayList<>();
    List<List<String>> words = new ArrayList<>();
    List<List<String>> labels = new ArrayList<>();
    List<List<CoreLabel>> goldTokens = new ArrayList<>();
    // basic example
    docs.add("Barack Obama was born in Hawaii.");
    words.add(Arrays.asList("Barack", "Obama", "was", "born", "in", "Hawaii", "."));
    labels.add(Arrays.asList("PERSON", "PERSON", "O", "O", "O", "LOCATION", "O"));
    // basic example with "-"
    docs.add("She traveled to Port-au-Prince over the summer with Jane Smith.");
    words.add(Arrays.asList("She", "traveled", "to", "Port-au-Prince", "over", "the", "summer", "with",
        "Jane", "Smith", "."));
    labels.add(Arrays.asList("O", "O", "O", "LOCATION", "O", "O", "DATE", "O", "PERSON", "PERSON", "O"));
    // document that ends with -
    docs.add("He was cut off saying welc-");
    words.add(Arrays.asList("He", "was", "cut", "off", "saying", "welc", "-"));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "O"));
    // special case for things like Chicago-based
    docs.add("They are big fans of the Chicago-based musician.");
    words.add(Arrays.asList("They", "are", "big", "fans", "of", "the", "Chicago-based", "musician", "."));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "MISC", "O", "O"));
    for (int i = 0 ; i < words.size() ; i++) {
      goldTokens.add(stringListsToCoreLabels(words.get(i), labels.get(i)));
    }
    for (int i = 0 ; i < docs.size() ; i++) {
      CoreDocument exampleDoc = new CoreDocument(docs.get(i));
      pipeline.annotate(exampleDoc);
      processTestExample(exampleDoc, goldTokens.get(i));
    }
  }

  /** Run full test on English **/
  public void testEnglishNERTokenizationTurnedOff() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.setProperty("ner.useNERSpecificTokenization", "false");
    pipeline = new StanfordCoreNLP(props);
    // set up data lists
    List<String> docs = new ArrayList<>();
    List<List<String>> words = new ArrayList<>();
    List<List<String>> labels = new ArrayList<>();
    List<List<CoreLabel>> goldTokens = new ArrayList<>();
    // basic example with "-"
    docs.add("She traveled to Port-au-Prince over the summer with Jane Smith.");
    words.add(Arrays.asList("She", "traveled", "to", "Port", "-", "au", "-", "Prince", "over", "the", "summer", "with",
        "Jane", "Smith", "."));
    labels.add(Arrays.asList("O", "O", "O", "LOCATION", "LOCATION", "CITY", "LOCATION", "TITLE", "O", "O", "DATE", "O",
        "PERSON", "PERSON", "O"));
    // special case for things like Chicago-based
    docs.add("They are big fans of the Chicago-based musician.");
    words.add(Arrays.asList("They", "are", "big", "fans", "of", "the", "Chicago", "-", "based", "musician", "."));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "CITY", "O", "O", "TITLE", "O"));
    for (int i = 0 ; i < words.size() ; i++) {
      goldTokens.add(stringListsToCoreLabels(words.get(i), labels.get(i)));
    }
    for (int i = 0 ; i < docs.size() ; i++) {
      CoreDocument exampleDoc = new CoreDocument(docs.get(i));
      pipeline.annotate(exampleDoc);
      processTestExample(exampleDoc, goldTokens.get(i));
    }
  }

  /** Run full test on German **/
  public void testGermanNERTokenization() {
    // set up pipeline
    Properties props = StringUtils.argsToProperties("-props", "german");
    props.setProperty("annotators", "tokenize,ssplit,mwt,pos,ner");
    pipeline = new StanfordCoreNLP(props);
    // set up data lists
    List<String> docs = new ArrayList<>();
    List<List<String>> words = new ArrayList<>();
    List<List<String>> labels = new ArrayList<>();
    List<List<CoreLabel>> goldTokens = new ArrayList<>();
    // basic example with "-"
    docs.add("Die Microsoft-Aktie sank daraufhin an der Wall Street um über vier Dollar auf 89,87 Dollar.");
    words.add(Arrays.asList("Die", "Microsoft", "-", "Aktie", "sank", "daraufhin", "an", "der", "Wall", "Street",
        "um", "über", "vier", "Dollar", "auf", "89,87", "Dollar", "."));
    labels.add(Arrays.asList("O", "MISC", "MISC", "MISC", "O", "O", "O", "O", "LOCATION", "LOCATION", "O", "O", "O",
        "MISC", "O", "O", "MISC", "O"));
    // another basic example with "-"
    docs.add("Stefan Müller-Doohm Interdisziplinarität von unten Gegen den Utopieverlust in der Soziologie:");
    words.add(Arrays.asList("Stefan", "Müller", "-", "Doohm", "Interdisziplinarität", "von", "unten", "Gegen",
        "den", "Utopieverlust", "in", "der", "Soziologie", ":"));
    labels.add(Arrays.asList("PERSON", "PERSON", "PERSON", "PERSON", "O", "O", "O", "O", "O", "O", "O", "O", "O",
        "O"));
    // example with MWT preceding a named entity
    docs.add("Der Filmhersteller Kodak übernimmt rückwirkend zum 1. Juli sein früheres Werk in Berlin-Köpenick.");
    words.add(Arrays.asList("Der", "Filmhersteller", "Kodak", "übernimmt", "rückwirkend", "zu", "dem", "1.", "Juli",
        "sein", "früheres", "Werk", "in", "Berlin", "-", "Köpenick", "."));
    labels.add(Arrays.asList("O", "O", "ORGANIZATION", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "LOCATION",
        "LOCATION", "LOCATION", "O"));
    for (int i = 0 ; i < words.size() ; i++) {
      goldTokens.add(stringListsToCoreLabels(words.get(i), labels.get(i)));
    }
    for (int i = 0 ; i < docs.size() ; i++) {
      CoreDocument exampleDoc = new CoreDocument(docs.get(i));
      pipeline.annotate(exampleDoc);
      processTestExample(exampleDoc, goldTokens.get(i));
    }
  }

  /** Run full test on French with NER tokenization turned on, handle MWT preceding entity case**/
  public void testFrenchNERTokenization() {
    // set up pipeline
    Properties props = StringUtils.argsToProperties("-props", "french");
    props.setProperty("annotators", "tokenize,ssplit,mwt,pos,ner");
    props.setProperty("ner.useNERTokenization", "true");
    pipeline = new StanfordCoreNLP(props);
    // set up data lists
    List<String> docs = new ArrayList<>();
    List<List<String>> words = new ArrayList<>();
    List<List<String>> labels = new ArrayList<>();
    List<List<CoreLabel>> goldTokens = new ArrayList<>();
    // example with preceding MWT
    docs.add("Ils se sont rendus aux États-Unis la semaine dernière.");
    words.add(Arrays.asList("Ils", "se", "sont", "rendus", "à", "les", "États-Unis", "la", "semaine", "dernière", "."));
    labels.add(Arrays.asList("O", "O", "O", "O", "O", "O", "I-LOC", "O", "O", "O", "O"));
    for (int i = 0 ; i < words.size() ; i++) {
      goldTokens.add(stringListsToCoreLabels(words.get(i), labels.get(i)));
    }
    for (int i = 0 ; i < docs.size() ; i++) {
      CoreDocument exampleDoc = new CoreDocument(docs.get(i));
      pipeline.annotate(exampleDoc);
      processTestExample(exampleDoc, goldTokens.get(i));
    }
  }

}
