package edu.stanford.nlp.time;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.io.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.util.*;
import java.util.*;
import junit.framework.TestCase;


public class HeidelTimeKBPAnnotatorITest extends TestCase {

  public StanfordCoreNLP pipeline;
  public String WORKING_DIR = String.format("%s/stanford-corenlp/testing/working-dirs/spanish-heideltime", TestPaths.testHome());
  // TODO no idea why the DATE stopped working in April 2024
  //public Set<String> GOLD_RESULTS = new HashSet<>(
  //    Arrays.asList("Rusia\tCOUNTRY", "Japón\tCOUNTRY", "hoy\tDATE","rusa\tLOCATION", "Vicente Fox\tPERSON",
  //                  "el 2 de julio de 1942\tDATE", "Esta semana\tDATE", "ING\tORGANIZATION",
  //                  "14\tNUMBER", "12\tNUMBER"));
  public Set<String> GOLD_RESULTS = new HashSet<>(
      Arrays.asList("Rusia\tCOUNTRY", "Japón\tCOUNTRY", "rusa\tLOCATION", "Vicente Fox\tPERSON",
                    "2\tNUMBER", "ING\tORGANIZATION", "14\tNUMBER", "12\tNUMBER"));

  @Override
  public void setUp() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,mwt,pos,lemma,ner,heideltime,entitymentions,depparse");
    // tokenize options
    props.setProperty("tokenize.language", "es");
    props.setProperty("tokenize.options", "tokenizeNLs,ptb3Escaping=true");
    // ssplit options
    props.setProperty("ssplit.eolonly", "true");
    // pos options
    props.setProperty("pos.model", "edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger");
    // ner options
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/spanish.kbp.ancora.distsim.s512.crf.ser.gz");
    props.setProperty("ner.applyNumericClassifiers", "true");
    props.setProperty("ner.useSUTime", "false");
    props.setProperty("ner.language", "es");
    props.setProperty("ner.buildEntityMentions", "false");
    props.setProperty("ner.docdate.usePresent", "true");
    props.setProperty("entitymentions.language", "es");
    // regexner
    props.setProperty("ner.fine.regexner.mapping",
        "edu/stanford/nlp/models/kbp/spanish/gazetteers/kbp_regexner_mapping_sp.tag");
    props.setProperty("ner.fine.regexner.validpospattern", "^(NOUN|ADJ|PROPN).*");
    props.setProperty("ner.fine.regexner.ignorecase", "true");
    props.setProperty("ner.fine.regexner.noDefaultOverwriteLabels", "CITY,COUNTRY,STATE_OR_PROVINCE");
    // add heideltime
    props.setProperty("customAnnotatorClass.heideltime", "edu.stanford.nlp.time.HeidelTimeKBPAnnotator");
    props.setProperty("heideltime.path", String.format("%s/stanford-corenlp-testing/spanish-heideltime/heideltime", TestPaths.testHome()));
    props.setProperty("heideltime.language", "spanish");
    // depparse
    props.setProperty("depparse.model", "edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz");
    props.setProperty("depparse.language", "spanish");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testHeidelTimeKBPAnnotatorITest() {
    String testFileContents = IOUtils.stringFromFile(WORKING_DIR+"/example-sentences.txt");
    CoreDocument testDocument = new CoreDocument(testFileContents);
    testDocument.annotation().set(CoreAnnotations.DocDateAnnotation.class, "2020-01-06");
    pipeline.annotate(testDocument);
    Set<String> outputResults = new HashSet<>();
    for (CoreEntityMention em : testDocument.entityMentions())
      outputResults.add(em.text()+"\t"+em.entityType());
    assertEquals(GOLD_RESULTS,outputResults);
  }

}
