package edu.stanford.nlp.ie;

import junit.framework.TestCase;
import java.io.*;
import java.lang.ProcessBuilder;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.sequences.CoNLLDocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.CoreMap;


/**
 * A comprehensive Itest for benchmarking
 * the performance of the current NER system
 * used by the CoreNLP pipeline.
 *
 * @author Mihail Eric
 */
public class NERBenchmarkSlowITest extends TestCase{
    private static Redwood.RedwoodChannels log = Redwood.channels(NERBenchmarkSlowITest.class);
    // Changed this to be on NLP machine
    //"/u/nlp/data/ner/conll/"
    private static final String BASE_DIR = "/u/nlp/data/ner/conll/"; //"/Users/mihaileric/Documents/Research/Data/conll/";
    private static final String CONLL_TRAIN = BASE_DIR + "eng.train";
    private static final String CONLL_DEV = BASE_DIR + "eng.testa";
    private static final String CONLL_TEST = BASE_DIR + "eng.testb";
    private static final String CONLL_OUTPUT_TRAIN = "conll_output_train.txt";
    private static final String CONLL_OUTPUT_DEV = "conll_output_dev.txt";
    private static final String CONLL_OUTPUT_TEST = "conll_output_test.txt";

    // CoNLL eval shell script
    private static final String CONLL_EVAL = "../scripts/ner/eval_conll_cmd.sh";
    private static final Pattern FB1_Pattern = Pattern.compile("FB1:  (\\d+\\.\\d+)");

    private static NERCombinerAnnotator nerAnnotator = null;
    private static AnnotationPipeline nerAnnotationPipeline = null;

    // Scores to match -- 7/27/17
    // TODO: Change this when I rerun tests
    private static final Double CONLL03_TRAIN_TOTAL_F1 = 97.27;
    private static final Double CONLL03_TRAIN_LOC_F1 = 97.27;
    private static final Double CONLL03_TRAIN_MISC_F1 = 97.27;
    private static final Double CONLL03_TRAIN_ORG_F1 = 97.27;
    private static final Double CONLL03_TRAIN_PER_F1 = 97.27;

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

    //TODO: Consider using NERFromConllAnnotator format

    @Override
    public void setUp() throws Exception{
        if(nerAnnotator == null){
            // Default properties are fine but need to provide a properties object in factory method
            Properties nerProps = new Properties();
            nerProps.setProperty("ner.useSUTime", "true");
            nerProps.setProperty("ner.applyNumericClassifiers", "false");
            nerProps.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
            nerProps.setProperty("applyNumericClassifiers", "false");
            nerAnnotator = new NERCombinerAnnotator(nerProps);

            Properties tokenizerProps = new Properties();
            tokenizerProps.setProperty("tokenize.whitespace", "true");
            nerAnnotationPipeline = new AnnotationPipeline();
            nerAnnotationPipeline.addAnnotator(new TokenizerAnnotator(false, tokenizerProps));
            nerAnnotationPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
            nerAnnotationPipeline.addAnnotator(nerAnnotator);
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
     * Abbreviate original tag converting from "ORGANIZATION" -> "ORG", etc.
     * @param origTag Original tag
     * @return The abbreviated tag format
     */
    public String abbreviate(String origTag) throws Exception{
        String predictPrefix;
        switch(origTag){
            case "ORGANIZATION":
                predictPrefix = "ORG";
                break;
            case "LOCATION":
                predictPrefix = "LOC";
                break;
            case "PERSON":
                predictPrefix = "PER";
                break;
            case "MISC":
                predictPrefix = "MISC";
                break;
            case "O":
                predictPrefix = "O";
                break;
            default:
                throw new Exception("System outputting invalid label " + origTag);
        }
        return predictPrefix;
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
    public HashMap<String, Double> evalDataset(String dataset) throws IOException, Exception{
        SeqClassifierFlags flags = new SeqClassifierFlags();
        flags.entitySubclassification = "noprefix";
        CoNLLDocumentReaderAndWriter rw = new CoNLLDocumentReaderAndWriter();
        rw.init(flags);

        String inputFile;
        String outputFile;
        switch(dataset){
            case "train":
                outputFile = CONLL_OUTPUT_DEV;
                inputFile = CONLL_DEV;
                break;
            case "dev":
                outputFile = CONLL_OUTPUT_DEV;
                inputFile = CONLL_DEV;
                break;
            case "test":
                outputFile = CONLL_OUTPUT_TEST;
                inputFile = CONLL_TEST;
                break;
            default:
                throw new Exception("Not a valid dataset name provided!");
        }

        PrintWriter writer = new PrintWriter(outputFile);
        for(Iterator<List<CoreLabel>> itr = rw.getIterator(IOUtils.readerFromString(inputFile)); itr.hasNext();){
            List<CoreLabel> goldLabels = itr.next();
            String docString = "";
            for(CoreLabel f1: goldLabels){
                docString += " " + f1.word();
            }
            Annotation docAnnotation = new Annotation(docString);
            nerAnnotationPipeline.annotate(docAnnotation);

            List<CoreLabel> predictLabels = new ArrayList<CoreLabel>();
            for(CoreLabel l : docAnnotation.get(TokensAnnotation.class)){
                predictLabels.add(l);
            }

            assertEquals("# gold outputs not same as # predicted!\n", goldLabels.size(), predictLabels.size());
            int numLabels = goldLabels.size();

            // Write to output file
            for(int i = 0; i < numLabels; ++i){
                CoreLabel gold = goldLabels.get(i);
                String goldToken;
                // TODO(meric): What is difference between GoldAnswer and Answer annotation?
                goldToken = gold.get(AnswerAnnotation.class);

                CoreLabel predict = predictLabels.get(i);
                String predictStr = predict.get(NamedEntityTagAnnotation.class);
                String predictPrefix = abbreviate(predictStr);

                assertEquals("Gold and Predict words don't match!\n", gold.get(TextAnnotation.class),
                        predict.get(TextAnnotation.class));
                writer.println(gold.get(TextAnnotation.class) + "\t" + "_" + "\t"
                        + goldToken + "\t"
                        + predictPrefix);
            }
        }
        writer.close();

        // Run CoNLL eval script and extract F1 score
        String result = null;
        String cmd = CONLL_EVAL + " " + outputFile;
        Process p = Runtime.getRuntime().exec(cmd);
        BufferedReader in =
                new BufferedReader(new InputStreamReader(p.getInputStream()));
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            System.out.println(inputLine);
            result += inputLine + "\n";
        }
        in.close();

        HashMap<String, Double> parsedF1 = parseResults(result);
        return parsedF1;
    }

    public void testConLLTrain(){
//        try{
//            HashMap<String, Double> parsedF1 = evalDataset("train");
//            Double totalF1 = parsedF1.get("TOTAL");
//            Double locF1 = parsedF1.get("LOC");
//            Double miscF1 = parsedF1.get("MISC");
//            Double orgF1 = parsedF1.get("ORG");
//            Double perF1 = parsedF1.get("PER");
//            assertEquals(String.format("CoNLL03 Total Train F1 should be %.2f but was %.2f",
//                    CONLL03_TRAIN_TOTAL_F1, totalF1), CONLL03_TRAIN_TOTAL_F1, totalF1, 1e-2);
//            assertEquals(String.format("CoNLL03 LOC Train F1 should be %.2f but was %.2f",
//                    CONLL03_TRAIN_LOC_F1, locF1), CONLL03_TRAIN_LOC_F1, locF1, 1e-2);
//            assertEquals(String.format("CoNLL03 MISC Train F1 should be %.2f but was %.2f",
//                    CONLL03_TRAIN_MISC_F1, miscF1), CONLL03_TRAIN_MISC_F1, miscF1, 1e-2);
//            assertEquals(String.format("CoNLL03 ORG Train F1 should be %.2f but was %.2f",
//                    CONLL03_TRAIN_ORG_F1, orgF1), CONLL03_TRAIN_ORG_F1, orgF1, 1e-2);
//            assertEquals(String.format("CoNLL03 PER Train F1 should be %.2f but was %.2f",
//                    CONLL03_TRAIN_PER_F1, perF1), CONLL03_TRAIN_PER_F1, perF1, 1e-2);
//        }catch(Exception e){
//            log.log(e);
//        }
    }

    public void testConLLDev() {
        try{
            log.log("Evaluating on CoNLL Dev");
            HashMap<String, Double> parsedF1 = evalDataset("dev");
            Double totalF1 = parsedF1.get("TOTAL");
            Double locF1 = parsedF1.get("LOC");
            Double miscF1 = parsedF1.get("MISC");
            Double orgF1 = parsedF1.get("ORG");
            Double perF1 = parsedF1.get("PER");
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

    public void testConLLTest() {
        try{
            log.log("Evaluating on CoNLL Test");
            HashMap<String, Double> parsedF1 = evalDataset("test");
            Double totalF1 = parsedF1.get("TOTAL");
            Double locF1 = parsedF1.get("LOC");
            Double miscF1 = parsedF1.get("MISC");
            Double orgF1 = parsedF1.get("ORG");
            Double perF1 = parsedF1.get("PER");
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

}
