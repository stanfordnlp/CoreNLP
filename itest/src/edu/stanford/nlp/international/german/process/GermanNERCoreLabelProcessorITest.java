package edu.stanford.nlp.international.german.process;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.StringUtils;

/**
 * Testing for the module that alters tokenization during German NER.
 * German tokenization matches the CoNLL 2018 standard, but during
 * NER we want to use a tokenization that doesn't split on hyphens for
 * improved F1.  The GermanNERCoreLabelProcessor merges tokens originally
 * split on hyphen, passes that token list to the CRFClassifier, and then
 * creates a final tokens list with the correct NER-related annotations.
 */

public class GermanNERCoreLabelProcessorITest {

  public StanfordCoreNLP pipeline;
  public GermanNERCoreLabelProcessor germanNERCoreLabelProcessor;

  @Before
  public void setUp() {
    Properties props = StringUtils.argsToProperties("-props", "german");
    props.setProperty("annotators", "tokenize,ssplit");
    pipeline = new StanfordCoreNLP(props);
    germanNERCoreLabelProcessor = new GermanNERCoreLabelProcessor();
  }

  public void runTestProcessorExampleNoTags(String text, List<String> goldProcessedTokens) {
    runTestProcessorExample(text, goldProcessedTokens, null, null);
  }

  public void runTestProcessorExample(String text, List<String> goldProcessedTokens, List<String> tagsToApply,
                                   List<String> finalTags) {
    // tokenize and sentence split
    CoreDocument document = new CoreDocument(pipeline.process(text));
    // test processed tokens match expected tokenization pattern
    System.err.println("---");
    System.err.println("original tokens: "+document.sentences().get(0).tokensAsStrings());
    List<CoreLabel> processedTokens = germanNERCoreLabelProcessor.process(document.sentences().get(0).tokens());
    System.err.println("processed tokens: "+processedTokens.stream().map(
        tok -> tok.word()).collect(Collectors.toList()));
    assertEquals(goldProcessedTokens, processedTokens.stream().map(tok -> tok.word()).collect(Collectors.toList()));
    // if there are tags to apply, apply them
    if (tagsToApply != null) {
      for (int i = 0 ; i < tagsToApply.size() ; i++) {
        processedTokens.get(i).setNER(tagsToApply.get(i));
      }
    }
    List<CoreLabel> restoredTokens = germanNERCoreLabelProcessor.restore(document.tokens(), processedTokens);
    // test if tags were set correctly for the restored list
    if (finalTags != null) {
      List<String> restoredTags = restoredTokens.stream().map(tok -> tok.ner()).collect(Collectors.toList());
      assertEquals(finalTags, restoredTags);
      for (int i = 0 ; i < finalTags.size() ; i++) {
        restoredTokens.get(i).remove(CoreAnnotations.NamedEntityTagAnnotation.class);
      }
    }
    // test that restored tokens match the original tokenization
    System.err.println("restored tokens :"+restoredTokens.stream().map(tok -> tok.word()).collect(Collectors.toList()));
    assertEquals(document.tokens(), restoredTokens);
  }

  @Test
  public void testProcessor() {
    // basic example
    // should split "Microsoft-Aktie" into "Microsoft", "-", and "Aktie", merge during NER, then restore to split
    String basicExample = "Die Microsoft-Aktie sank daraufhin an der Wall Street um über vier Dollar auf 89,87 Dollar.";
    List<String> basicGoldTokens = Arrays.asList("Die", "Microsoft-Aktie", "sank", "daraufhin", "an", "der",
        "Wall", "Street", "um", "über", "vier", "Dollar", "auf", "89,87", "Dollar", ".");
    List<String> basicTagsAppliedToProcessed = Arrays.asList("O", "MISC", "O", "O", "O", "O", "LOC", "LOC", "O", "O",
        "O", "O", "O", "O", "O", "O");
    List<String> basicFinalTags = Arrays.asList("O", "MISC", "MISC", "MISC", "O", "O", "O", "O", "LOC", "LOC", "O", "O", "O",
        "O", "O", "O", "O", "O");
    runTestProcessorExample(basicExample, basicGoldTokens, basicTagsAppliedToProcessed, basicFinalTags);
    // basic example with multiple instances
    // split both "Mathematik-Lehrpläne" and "Nordrhein-Westfalen"
    String multiBasicExample = "Es sei wichtig, daß sich ein breiter Kreis mit den von Heymann vorgelegten Thesen " +
        "beschäftigt, meint Ringel, denn immerhin sei dieser offizieller Berater bei der Erarbeitung neuer " +
        "Mathematik-Lehrpläne in Nordrhein-Westfalen.";
    List<String> multiBasicGoldTokens = Arrays.asList("Es", "sei", "wichtig", ",", "daß", "sich", "ein", "breiter",
        "Kreis", "mit", "den", "von", "Heymann", "vorgelegten", "Thesen", "beschäftigt", ",", "meint", "Ringel", ",",
        "denn", "immerhin", "sei", "dieser", "offizieller", "Berater", "bei", "der", "Erarbeitung", "neuer",
        "Mathematik-Lehrpläne", "in", "Nordrhein-Westfalen", ".");
    List<String> multiBasicTagsAppliedToProcessed = Arrays.asList("O", "O", "O", "O", "O", "O", "O", "O", "O", "O",
        "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "MISC", "O",
        "LOC", "O");
    List<String> multiBasicFinalTags = Arrays.asList("O", "O", "O", "O", "O", "O", "O", "O", "O", "O",
        "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "O", "MISC",
        "MISC", "MISC", "O", "LOC", "LOC", "LOC", "O");
    runTestProcessorExample(multiBasicExample, multiBasicGoldTokens, multiBasicTagsAppliedToProcessed,
        multiBasicFinalTags);
    // not just letters example
    String notJustLettersExample = "Darüber hinaus gibt es Adobe - Trainings, Firmen-/Inhouse-Seminare sowie Coaching " +
        "für 1-2 Personen.";
    // triple example
    String tripleHyphenatedExample = "IG-Metall-Chef Klaus Zwickel hat in Mannheim überaus deutlich an diese Partei, " +
        "die auch die seine ist, als Verbündete appelliert.";
    // original tokenization throughout
    String originalHyphenSplitExample = "Mit dem Carsch - Haus verfügt der Stadtteil über ein Luxuskaufhaus.";
    // original tokenization throughout
    String wordEndingWithHyphenExample = "Rund 30 000 Tier- und Pflanzenarten sind weltweit in ihrer Existenz bedroht.";
    // original tokenization throughout
    String doubleHyphenExample = "Er will Ende November in Washington mit der US -- Regierung über den Friedensprozeß " +
        "sprechen.";
  }

}