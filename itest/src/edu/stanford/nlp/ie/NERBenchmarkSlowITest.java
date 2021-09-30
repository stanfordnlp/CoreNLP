package edu.stanford.nlp.ie;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.pipeline.NERCombinerAnnotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.sequences.CoNLLDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.TestPaths;


/**
 * A comprehensive Itest for benchmarking
 * the performance of the current NER system
 * used by the CoreNLP pipeline.
 *
 * @author Mihail Eric
 */
public class NERBenchmarkSlowITest {
    private static Redwood.RedwoodChannels log = Redwood.channels(NERBenchmarkSlowITest.class);
    // Conll paths
    private static final String CONLL_BASE_DIR = String.format("%s/ner/conll/", TestPaths.testHome());
    private static final String CONLL_TRAIN = CONLL_BASE_DIR + "eng.train";
    private static final String CONLL_DEV = CONLL_BASE_DIR + "eng.testa";
    private static final String CONLL_TEST = CONLL_BASE_DIR + "eng.testb";
    private static final String CONLL_OUTPUT_TRAIN = "conll_output_train.txt";
    private static final String CONLL_OUTPUT_DEV = "conll_output_dev.txt";
    private static final String CONLL_OUTPUT_TEST = "conll_output_test.txt";

    // Onto paths
    private static final String ONTO_BASE_DIR = String.format("%s/ner/ontonotes/", TestPaths.testHome());
    private static final String ONTO_DEV = ONTO_BASE_DIR + "onto-3class-dev.tsv";
    private static final String ONTO_TEST = ONTO_BASE_DIR + "onto-3class-test.tsv";

    // TODO: use the model directly to run the test
    /** official CoNLL NER evaluation script **/
    private static final String CONLL_EVAL = String.format("%s/ner/benchmark/eval_conll.sh", TestPaths.testHome());
    // private static final String CONLL_EVAL = (new File("projects/core/scripts/ner/eval_conll_cmd.sh").exists() ?
    //                                           "projects/core/scripts/ner/eval_conll_cmd.sh" :
    //                                           "../../scripts/ner/eval_conll_cmd.sh");
    private static final Pattern FB1_Pattern = Pattern.compile("FB1:  (\\d+\\.\\d+)");

    // Note we need two annotator pipelines because the datasets use different NER models
    private static NERCombinerAnnotator conllNERAnnotator = null;
    private static AnnotationPipeline conllNERAnnotationPipeline = null;

    private static NERCombinerAnnotator ontoNERAnnotator = null;
    private static AnnotationPipeline ontoNERAnnotationPipeline = null;

    // Scores to match -- 7/27/17
    // TODO: Change this to accurately reflect scores
    private static final Double CONLL03_DEV_TOTAL_F1 = 93.20;
    private static final Double CONLL03_DEV_LOC_F1 = 95.38;
    private static final Double CONLL03_DEV_MISC_F1 = 88.96;
    private static final Double CONLL03_DEV_ORG_F1 = 88.17;
    private static final Double CONLL03_DEV_PER_F1 = 96.76;

    private static final Double CONLL03_TEST_TOTAL_F1 = 88.80;
    private static final Double CONLL03_TEST_LOC_F1 = 89.84;
    private static final Double CONLL03_TEST_MISC_F1 = 79.94;
    private static final Double CONLL03_TEST_ORG_F1 = 84.69;
    private static final Double CONLL03_TEST_PER_F1 = 94.83;

    private static final Double ONTO_DEV_TOTAL_F1 = 89.93;
    private static final Double ONTO_DEV_LOC_F1 = 90.53;
    private static final Double ONTO_DEV_ORG_F1 = 85.12;
    private static final Double ONTO_DEV_PER_F1 = 93.31;

    private static final Double ONTO_TEST_TOTAL_F1 = 90.79;
    private static final Double ONTO_TEST_LOC_F1 = 91.17;
    private static final Double ONTO_TEST_ORG_F1 = 88.87;
    private static final Double ONTO_TEST_PER_F1 = 92.88;

    //TODO: Consider using NERFromConllAnnotator format

    @Before
    public void setUp() throws IOException {
        if(conllNERAnnotator == null || ontoNERAnnotator == null){
            // Default properties are fine but need to provide a properties object in factory method
            Properties nerProps = new Properties();
            nerProps.setProperty("ner.useSUTime", "false");
            nerProps.setProperty("ner.applyNumericClassifiers", "false");
            nerProps.setProperty("ner.model", DefaultPaths.DEFAULT_NER_CONLL_MODEL);
            nerProps.setProperty("applyNumericClassifiers", "false");

            conllNERAnnotator = new NERCombinerAnnotator(nerProps);
            // Change NER model for different dataset -- everything else is the same
            nerProps.setProperty("ner.model", DefaultPaths.DEFAULT_NER_THREECLASS_MODEL);
            ontoNERAnnotator = new NERCombinerAnnotator(nerProps);

            // Set up conll pipeline
            Properties tokenizerProps = new Properties();
            tokenizerProps.setProperty("tokenize.whitespace", "true");
            conllNERAnnotationPipeline = new AnnotationPipeline();
            conllNERAnnotationPipeline.addAnnotator(new TokenizerAnnotator(false, tokenizerProps));
            conllNERAnnotationPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
            conllNERAnnotationPipeline.addAnnotator(conllNERAnnotator);

            // Set up onto pipeline
            ontoNERAnnotationPipeline = new AnnotationPipeline();
            ontoNERAnnotationPipeline.addAnnotator(new TokenizerAnnotator(false, tokenizerProps));
            ontoNERAnnotationPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
            ontoNERAnnotationPipeline.addAnnotator(ontoNERAnnotator);
        }
    }

    /**
     *  Parse all results output from conlleval script.
     *  Returns overall F1 score
     *  @param results Results collected from stdout from script
     *  @return Hashmap containing F1 scores by label and overall
     */
    public HashMap<String, Double> parseResults(String results){
        HashMap<String, Double> f1Results = new HashMap<String, Double>();
        double result = 0.0;
        String[] lines = results.split("\n");
        for(int idx = 0; idx < lines.length; idx++){
            String line = lines[idx];
            Matcher m = FB1_Pattern.matcher(line);
            // Should parse the F1 after "FB1:"
            while(m.find()){
                String f1 = m.group(1);
                result = Double.parseDouble(f1);
            }

            String key;
            if(line.contains("LOC")){
                key = "LOC";
            }else if(line.contains("MISC")){
                key = "MISC";
            }else if(line.contains("ORG")){
                key = "ORG";
            }else if(line.contains("PER")){
                key = "PER";
            }else{
                key = "TOTAL";
            }

            f1Results.put(key, result);
        }
        return f1Results;
    }

    /**
     * Convert original tag converting from "ORGANIZATION" -> "ORG", etc.
     * @param origTag Original tag
     * @return The convert tag format
     */
    public String convert(String origTag) {
        String converted;
        switch(origTag){
            case "ORGANIZATION":
            case "ORG":
                converted = "ORG";
                break;
            case "LOCATION":
            case "LOC":
                converted = "LOC";
                break;
            case "PERSON":
                converted = "PER";
                break;
            case "MISC":
                converted = "MISC";
                break;
            case "O":
            // TODO(meric): Temporary hack for now
//            case "GPE":
//            case "CARDINAL":
//            case "DATE":
//            case "MONEY":
//            case "PERCENT":
//            case "EVENT":
//            case "ORDINAL":
//            case "QUANTITY":
//            case "NORP":
//            case "WORK_OF_ART":
//            case "LAW":
//            case "FAC":
//            case "TIME":
//            case "PRODUCT":
//            case "LANGUAGE":
                converted = "O";
                break;
            default:
                throw new RuntimeException("System outputting invalid label " + origTag);
        }
        return converted;
    }
    // TODO(meric): Should I be using a different CRF classifier for ONTO with more/less than 4 labels?

    /**
     * Run conlleval perl script on given input file
     * @param resultsFile
     * @return String with output of running perl eval script
     */
    public String runEvalScript(File resultsFile) throws IOException{
        String result = null;
        String cmd = CONLL_EVAL + " " + resultsFile;
        Process p = Runtime.getRuntime().exec(cmd);
        BufferedReader in =
                new BufferedReader(new InputStreamReader(p.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            System.out.println(inputLine);
            result += inputLine + "\n";
        }
        in.close();
        return result;
    }


    /**
     * The main engine that does the heavy lifting for evaluating a dataset. We are performing
     * 4-way classification on: ORG, PER, LOC, MISC
     * @param dataset Dataset prefix to evaluate. Should be one of "train", "dev", "test"
     * @throws IOException
     * @throws Exception
     * @return F1 computed for given dataset by model
     */
    // NOTE that CoNLL tests assume a 4-class classification scheme: ORG, PER, LOC, MISC
    public HashMap<String, Double> evalConll(String dataset) throws IOException {
        SeqClassifierFlags flags = new SeqClassifierFlags();
        flags.entitySubclassification = "noprefix";
        CoNLLDocumentReaderAndWriter rw = new CoNLLDocumentReaderAndWriter();
        rw.init(flags);

        String inputFile;
        File resultsFile;
        switch(dataset){
            case "train":
                resultsFile = File.createTempFile("conlldev", null);
                inputFile = CONLL_DEV;
                break;
            case "dev":
                resultsFile = File.createTempFile("conlldev", null);
                inputFile = CONLL_DEV;
                break;
            case "test":
                resultsFile = File.createTempFile("conlltest", null);
                inputFile = CONLL_TEST;
                break;
            default:
                throw new RuntimeException("Not a valid dataset name provided!");
        }
        resultsFile.deleteOnExit();

        PrintWriter writer = new PrintWriter(resultsFile);
        for(Iterator<List<CoreLabel>> itr = rw.getIterator(IOUtils.readerFromString(inputFile)); itr.hasNext();){
            List<CoreLabel> goldLabels = itr.next();
            String docString = "";
            for(CoreLabel f1: goldLabels){
                docString += " " + f1.word();
            }
            Annotation docAnnotation = new Annotation(docString);
            conllNERAnnotationPipeline.annotate(docAnnotation);

            List<CoreLabel> predictLabels = new ArrayList<CoreLabel>();
            for(CoreLabel l : docAnnotation.get(TokensAnnotation.class)){
                predictLabels.add(l);
            }

            assertEquals("# gold outputs not same as # predicted!\n", goldLabels.size(), predictLabels.size());
            int numLabels = goldLabels.size();

            // Write to output file
            for(int i = 0; i < numLabels; i++){
                CoreLabel gold = goldLabels.get(i);
                String goldToken;
                // TODO(meric): What is difference between GoldAnswer and Answer annotation?
                goldToken = gold.get(AnswerAnnotation.class);

                CoreLabel predict = predictLabels.get(i);
                String predictStr = predict.get(NamedEntityTagAnnotation.class);
                String predictPrefix = convert(predictStr);

                assertEquals("Gold and Predict words don't match!\n", gold.get(TextAnnotation.class),
                        predict.get(TextAnnotation.class));
                writer.println(gold.get(TextAnnotation.class) + "\t" + "_" + "\t"
                        + goldToken + "\t"
                        + predictPrefix);
            }
        }
        writer.close();

        // Run CoNLL eval script and extract F1 score
        String result = runEvalScript(resultsFile);
        HashMap<String, Double> parsedF1 = parseResults(result);

        return parsedF1;
    }

    /**
     * Read onto file and return list of list of corelabels (where each inner list
     * represents a sentence)
     * @param file Name of Onto file formatted with each line containing a token and NER tag
     * @return List of list of corelabels
     */
    public List<List<CoreLabel>> readTokensFromOntoFile(String file){
        List<List<CoreLabel>> sentences = new ArrayList<>();
        List<CoreLabel> currSentenceTokens = new ArrayList<CoreLabel>();
        int wordsSeen = 0;
        for (String line : IOUtils.readLines(file)) {
            String[] entries = line.split("\t");
            if (entries.length == 2) {
                String word = entries[0];
                String nerTag = entries[1];
                wordsSeen++;
                CoreLabel token = new CoreLabel();
                token.setWord(word);
                token.setNER(nerTag);
                currSentenceTokens.add(token);
            } else {
                if (currSentenceTokens.size() != 0) {
                    sentences.add(currSentenceTokens);
                    currSentenceTokens = new ArrayList<CoreLabel>();
                }
            }
        }
        return sentences;
    }

    public HashMap<String, Double> evalOnto(String dataset) throws IOException {
        String inputFile;
        File resultsFile;
        switch(dataset){
            case "dev":
                resultsFile = File.createTempFile("ontodev", null);
                inputFile = ONTO_DEV;
                break;
            case "test":
                resultsFile = File.createTempFile("ontotest", null);
                inputFile = ONTO_TEST;
                break;
            default:
                throw new RuntimeException("Not a valid dataset name provided!");
        }
        resultsFile.deleteOnExit();
        List<List<CoreLabel>> ontoSentences = readTokensFromOntoFile(inputFile);
        PrintWriter writer = new PrintWriter(resultsFile);
        // Run ner annotation pipeline on every sentence in dataset
        for(List<CoreLabel> sentenceLabels: ontoSentences){
            String sentence = "";
            for(CoreLabel label: sentenceLabels){
                sentence += " " + label.word();
            }

            // Now run through ner pipeline
            Annotation sentenceAnnotation = new Annotation(sentence);
            ontoNERAnnotationPipeline.annotate(sentenceAnnotation);

            List<CoreLabel> predictLabels = new ArrayList<CoreLabel>();
            for(CoreLabel l : sentenceAnnotation.get(TokensAnnotation.class)){
                predictLabels.add(l);
            }

            int numLabels = sentenceLabels.size();

            // Write to output file
            for(int i = 0; i < numLabels; i++){
                CoreLabel gold = sentenceLabels.get(i);
                String goldToken;
                goldToken = gold.get(NamedEntityTagAnnotation.class);
                String goldPrefix = convert(goldToken);

                CoreLabel predict = predictLabels.get(i);
                String predictStr = predict.get(NamedEntityTagAnnotation.class);
                String predictPrefix = convert(predictStr);

                assertEquals("Gold and Predict words don't match!\n", gold.get(TextAnnotation.class),
                        predict.get(TextAnnotation.class));
                writer.println(gold.get(TextAnnotation.class) + "\t" + "_" + "\t"
                        + goldPrefix + "\t"
                        + predictPrefix);
            }
        }
        writer.close();

        // Run CoNLL eval script and extract F1 score
        String result = runEvalScript(resultsFile);
        HashMap<String, Double> parsedF1 = parseResults(result);

        return parsedF1;
    }

    @Test
    public void testConLLDev() {
        try{
            log.log("Evaluating on CoNLL Dev");
//            HashMap<String, Double> parsedF1 = evalConll("dev");
//            Double totalF1 = parsedF1.get("TOTAL");
//            Double locF1 = parsedF1.get("LOC");
//            Double miscF1 = parsedF1.get("MISC");
//            Double orgF1 = parsedF1.get("ORG");
//            Double perF1 = parsedF1.get("PER");
//            assertEquals(String.format("CoNLL03 Total Dev F1 should be %.2f but was %.2f",
//                    CONLL03_DEV_TOTAL_F1, totalF1), CONLL03_DEV_TOTAL_F1, totalF1, 1e-2);
//            assertEquals(String.format("CoNLL03 LOC Dev F1 should be %.2f but was %.2f",
//                    CONLL03_DEV_LOC_F1, locF1), CONLL03_DEV_LOC_F1, locF1, 1e-2);
//            assertEquals(String.format("CoNLL03 MISC Dev F1 should be %.2f but was %.2f",
//                    CONLL03_DEV_MISC_F1, miscF1), CONLL03_DEV_MISC_F1, miscF1, 1e-2);
//            assertEquals(String.format("CoNLL03 ORG Dev F1 should be %.2f but was %.2f",
//                    CONLL03_DEV_ORG_F1, orgF1), CONLL03_DEV_ORG_F1, orgF1, 1e-2);
//            assertEquals(String.format("CoNLL03 PER Dev F1 should be %.2f but was %.2f",
//                    CONLL03_DEV_PER_F1, perF1), CONLL03_DEV_PER_F1, perF1, 1e-2);
        }catch(Exception e){
            log.log(e);
        }
    }

    @Test
    public void testConLLTest() {
        try{
            log.log("Evaluating on CoNLL Test");
//            HashMap<String, Double> parsedF1 = evalConll("test");
//            Double totalF1 = parsedF1.get("TOTAL");
//            Double locF1 = parsedF1.get("LOC");
//            Double miscF1 = parsedF1.get("MISC");
//            Double orgF1 = parsedF1.get("ORG");
//            Double perF1 = parsedF1.get("PER");
//            assertEquals(String.format("CoNLL03 Total Test F1 should be %.2f but was %.2f",
//                    CONLL03_TEST_TOTAL_F1, totalF1), CONLL03_TEST_TOTAL_F1, totalF1, 1e-2);
//            assertEquals(String.format("CoNLL03 LOC Test F1 should be %.2f but was %.2f",
//                    CONLL03_TEST_LOC_F1, locF1), CONLL03_TEST_LOC_F1, locF1, 1e-2);
//            assertEquals(String.format("CoNLL03 MISC Test F1 should be %.2f but was %.2f",
//                    CONLL03_TEST_MISC_F1, miscF1), CONLL03_TEST_MISC_F1, miscF1, 1e-2);
//            assertEquals(String.format("CoNLL03 ORG Test F1 should be %.2f but was %.2f",
//                    CONLL03_TEST_ORG_F1, orgF1), CONLL03_TEST_ORG_F1, orgF1, 1e-2);
//            assertEquals(String.format("CoNLL03 PER Test F1 should be %.2f but was %.2f",
//                    CONLL03_TEST_PER_F1, perF1), CONLL03_TEST_PER_F1, perF1, 1e-2);
        }catch(Exception e){
            log.log(e);
        }
    }

    @Test
    public void testOntoDev() throws IOException {
      HashMap<String, Double> parsedF1 = evalOnto("dev");
      Double totalF1 = parsedF1.get("TOTAL");
      Double locF1 = parsedF1.get("LOC");
      Double orgF1 = parsedF1.get("ORG");
      Double perF1 = parsedF1.get("PER");
      assertEquals(String.format("Onto Total Test F1 should be %.2f but was %.2f",
                                 ONTO_DEV_TOTAL_F1, totalF1),
                   ONTO_DEV_TOTAL_F1, totalF1, 1.0);
      assertEquals(String.format("Onto LOC Test F1 should be %.2f but was %.2f",
                                 ONTO_DEV_LOC_F1, locF1),
                   ONTO_DEV_LOC_F1, locF1, 1.0);
      assertEquals(String.format("Onto ORG Test F1 should be %.2f but was %.2f",
                                 ONTO_DEV_ORG_F1, orgF1),
                   ONTO_DEV_ORG_F1, orgF1, 1.0);
      assertEquals(String.format("Onto PER Test F1 should be %.2f but was %.2f",
                                 ONTO_DEV_PER_F1, perF1),
                   ONTO_DEV_PER_F1, perF1, 1.0);
    }

    @Test
    public void testOntoTest() throws IOException {
      HashMap<String, Double> parsedF1 = evalOnto("test");
      Double totalF1 = parsedF1.get("TOTAL");
      Double locF1 = parsedF1.get("LOC");
      Double orgF1 = parsedF1.get("ORG");
      Double perF1 = parsedF1.get("PER");
      assertEquals(String.format("Onto Total Test F1 should be %.2f but was %.2f",
                                 ONTO_TEST_TOTAL_F1, totalF1),
                   ONTO_TEST_TOTAL_F1, totalF1, 1.0);
      assertEquals(String.format("Onto LOC Test F1 should be %.2f but was %.2f",
                                 ONTO_TEST_LOC_F1, locF1),
                   ONTO_TEST_LOC_F1, locF1, 1.0);
      assertEquals(String.format("Onto ORG Test F1 should be %.2f but was %.2f",
                                 ONTO_TEST_ORG_F1, orgF1),
                   ONTO_TEST_ORG_F1, orgF1, 1.0);
      assertEquals(String.format("Onto PER Test F1 should be %.2f but was %.2f",
                                 ONTO_TEST_PER_F1, perF1),
                   ONTO_TEST_PER_F1, perF1, 1.0);
    }

}
