package edu.stanford.nlp.loglinear.learning;

import edu.stanford.nlp.loglinear.inference.CliqueTree;
import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by keenon on 8/26/15.
 *
 * This loads the CoNLL dataset and 300 dimensional google word embeddings and trains a model on the data using binary
 * and unary factors. This is a nice explanation of why it is key to have ConcatVector as a datastructure, since there
 * is no need to specify the number of words in advance anywhere, and data structures will happily resize with a minimum
 * of GCC wastage.
 *
 * So far, the feature set is extremely simplistic, though it would be awesome if people expanded it. Look at
 * generateSentenceModel() to make changes.
 */
public class CoNLLBenchmark {
    Map<String, double[]> embeddings = new HashMap<>();

    public static void main(String[] args) throws Exception {
        new CoNLLBenchmark().benchmarkOptimizer();
    }

    public void benchmarkOptimizer() throws Exception {
        String prefix = System.getProperty("user.dir");
        if (prefix.endsWith("javanlp")) prefix = prefix+"/projects/core/test/src/edu/stanford/nlp/loglinear/learning/";
        System.out.println(prefix);

        List<CoNLLSentence> train = getSentences(prefix + "conll.iob.4class.train");
        List<CoNLLSentence> testA = getSentences(prefix + "conll.iob.4class.testa");
        List<CoNLLSentence> testB = getSentences(prefix + "conll.iob.4class.testb");

        List<CoNLLSentence> allData = new ArrayList<>();
        allData.addAll(train);
        allData.addAll(testA);
        allData.addAll(testB);

        Set<String> tagsSet = new HashSet<>();
        for (CoNLLSentence sentence : allData) for (String nerTag : sentence.ner) tagsSet.add(nerTag);
        List<String> tags = new ArrayList<>();
        tags.addAll(tagsSet);

        embeddings = getEmbeddings(prefix + "google-300-trimmed.ser.gz", allData);

        System.err.println("Making the training set...");

        int trainSize = train.size();
        GraphicalModel[] trainingSet = new GraphicalModel[trainSize];
        for (int i = 0; i < trainSize; i++) {
            if (i % 10 == 0) {
                System.err.println(i+"/"+trainSize);
            }
            trainingSet[i] = generateSentenceModel(train.get(i), tags);
        }

        System.err.println("Training system...");

        AbstractBatchOptimizer opt = new BacktrackingAdaGradOptimizer();

        // This training call is basically what we want the benchmark for. It should take 99% of the wall clock time
        ConcatVector weights = opt.optimize(trainingSet, new LogLikelihoodFunction(), new ConcatVector(0), 0.1);

        System.err.println("Testing system...");

        // Evaluation method lifted from the CoNLL 2004 perl script

        Map<String,Double> correctChunk = new HashMap<>();
        Map<String,Double> foundCorrect = new HashMap<>();
        Map<String,Double> foundGuessed = new HashMap<>();
        double correct = 0.0;
        double total = 0.0;

        for (CoNLLSentence sentence : testA) {
            GraphicalModel model = generateSentenceModel(sentence, tags);
            int[] guesses = new CliqueTree(model, weights).calculateMAP();
            String[] nerGuesses = new String[guesses.length];
            for (int i = 0; i < guesses.length; i++) {
                nerGuesses[i] = tags.get(guesses[i]);
                if (nerGuesses[i].equals(sentence.ner.get(i))) {
                    correct++;
                    correctChunk.put(nerGuesses[i], correctChunk.getOrDefault(nerGuesses[i], 0.) + 1);
                }
                total++;
                foundCorrect.put(sentence.ner.get(i), foundCorrect.getOrDefault(sentence.ner.get(i), 0.) + 1);
                foundGuessed.put(nerGuesses[i], foundGuessed.getOrDefault(nerGuesses[i], 0.) + 1);
            }
        }

        System.err.println("\nSystem results:\n");

        System.err.println("Accuracy: "+(correct / total)+"\n");

        for (String tag : tags) {
            double precision = foundGuessed.getOrDefault(tag, 0.0) == 0 ? 0.0 : correctChunk.getOrDefault(tag, 0.0) / foundGuessed.get(tag);
            double recall = foundCorrect.getOrDefault(tag, 0.0) == 0 ? 0.0 : correctChunk.getOrDefault(tag, 0.0) / foundCorrect.get(tag);
            double f1 = (precision * recall * 2) / (precision + recall);
            System.err.println(tag);
            System.err.println("\tP:"+precision);
            System.err.println("\tR:"+recall);
            System.err.println("\tF1:"+f1);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    // GENERATING MODELS
    ////////////////////////////////////////////////////////////////////////////////////////////

    // Indexes for sparse features

    Index<String> wordIndex = new HashIndex<>();
    Index<String> prefix1Index = new HashIndex<>();
    Index<String> prefix2Index = new HashIndex<>();
    Index<String> prefix3Index = new HashIndex<>();
    Index<String> suffix1Index = new HashIndex<>();
    Index<String> suffix2Index = new HashIndex<>();
    Index<String> suffix3Index = new HashIndex<>();
    Index<String> bigramIndex = new HashIndex<>();

    private GraphicalModel generateSentenceModel(CoNLLSentence sentence, List<String> tags) {
        GraphicalModel model = new GraphicalModel();

        for (int i = 0; i < sentence.token.size(); i++) {

            // Add the training label

            model.getVariableMetaDataByReference(i).put(LogLikelihoodFunction.VARIABLE_TRAINING_VALUE, ""+tags.indexOf(sentence.ner.get(i)));

            final int iFinal = i;

            // Add the unary factor

            GraphicalModel.Factor f = model.addFactor(new int[]{iFinal}, new int[]{tags.size()}, (assignment) -> {

                // This is the anonymous function that generates a feature vector for each assignment to the unary
                // factor

                String tag = tags.get(assignment[0]);

                int numFeatures = 8;

                ConcatVector features = new ConcatVector(numFeatures*tags.size());
                if (embeddings.get(sentence.token.get(iFinal)) != null) {
                    String token = sentence.token.get(iFinal);

                    // Feature 0, for this tag, word embedding
                    features.setDenseComponent(assignment[0]*numFeatures, embeddings.get(token));

                    // Feature 1, for this tag, word
                    features.setSparseComponent(assignment[0]*numFeatures + 1, wordIndex.indexOf(token), 1.0);

                    // Prefix and suffix features
                    int len = token.length();
                    if (len >= 1) {
                        features.setSparseComponent(assignment[0] * numFeatures + 2, prefix1Index.indexOf(token.substring(0, 0)), 1.0);
                        features.setSparseComponent(assignment[0] * numFeatures + 5, suffix1Index.indexOf(token.substring(len - 1)), 1.0);
                    }
                    if (len >= 2) {
                        features.setSparseComponent(assignment[0]*numFeatures + 3, prefix2Index.indexOf(token.substring(0,1)), 1.0);
                        features.setSparseComponent(assignment[0]*numFeatures + 6, suffix2Index.indexOf(token.substring(len-2)), 1.0);
                    }
                    if (len >= 3) {
                        features.setSparseComponent(assignment[0]*numFeatures + 7, suffix3Index.indexOf(token.substring(len-3)), 1.0);
                        features.setSparseComponent(assignment[0]*numFeatures + 4, prefix3Index.indexOf(token.substring(0,2)), 1.0);
                    }
                }

                return features;
            });

            // If this is not the last variable, add a binary factor

            if (i < sentence.token.size() - 1) {
                GraphicalModel.Factor jf = model.addFactor(new int[]{iFinal, iFinal + 1}, new int[]{tags.size(), tags.size()}, (assignment) -> {

                    // This is the anonymous function that generates a feature vector for every joint assignment to the
                    // binary factor

                    String thisTag = tags.get(assignment[0]);
                    String nextTag = tags.get(assignment[1]);

                    int numFeatures = 3;

                    ConcatVector features = new ConcatVector(numFeatures * tags.size() * tags.size());

                    if (embeddings.containsKey(sentence.token.get(iFinal)) || embeddings.containsKey(sentence.token.get(iFinal + 1))) {

                        int index = assignment[0] * tags.size() + assignment[1];

                        if (embeddings.get(sentence.token.get(iFinal)) != null) {
                            features.setDenseComponent(index * numFeatures, embeddings.get(sentence.token.get(iFinal)));
                        }
                        if (embeddings.get(sentence.token.get(iFinal + 1)) != null) {
                            features.setDenseComponent(index * numFeatures + 1, embeddings.get(sentence.token.get(iFinal + 1)));
                        }

                        features.setSparseComponent(index * numFeatures + 2, bigramIndex.indexOf(sentence.token.get(iFinal)+sentence.token.get(iFinal + 1)), 1.0);
                    }

                    return features;
                });
            }
        }

        assert(model.factors != null);
        for (GraphicalModel.Factor f : model.factors) {
            assert(f != null);
        }

        return model;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////
    // LOADING DATA FROM FILES
    ////////////////////////////////////////////////////////////////////////////////////////////

    private static class CoNLLSentence {
        public List<String> token = new ArrayList<>();
        public List<String> ner = new ArrayList<>();

        public CoNLLSentence(List<String> token, List<String> ner) {
            this.token = token;
            this.ner = ner;
        }
    }

    private List<CoNLLSentence> getSentences(String filename) throws IOException {
        List<CoNLLSentence> sentences = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        List<String> ners = new ArrayList<>();

        BufferedReader br = new BufferedReader(new FileReader(filename));

        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split("\t");
            if (parts.length == 2) {
                tokens.add(parts[0]);
                ners.add(parts[1]);
                if (parts[0].equals(".")) {
                    sentences.add(new CoNLLSentence(tokens, ners));
                    tokens = new ArrayList<>();
                    ners = new ArrayList<>();
                }
            }
        }

        return sentences;
    }

    @SuppressWarnings("unchecked")
    private Map<String,double[]> getEmbeddings(String cacheFilename, List<CoNLLSentence> sentences) throws IOException, ClassNotFoundException {
        File f = new File(cacheFilename);
        Map<String,double[]> trimmedSet;

        if (!f.exists()) {
            trimmedSet = new HashMap<>();

            Map<String,double[]> massiveSet = loadEmbeddingsFromFile("../google-300.txt");
            System.err.println("Got massive embedding set size "+massiveSet.size());

            for (CoNLLSentence sentence : sentences) {
                for (String token : sentence.token) {
                    if (massiveSet.containsKey(token)) {
                        trimmedSet.put(token, massiveSet.get(token));
                    }
                }
            }
            System.err.println("Got trimmed embedding set size "+trimmedSet.size());

            f.createNewFile();
            ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(cacheFilename)));
            oos.writeObject(trimmedSet);
            oos.close();

            System.err.println("Wrote trimmed set to file");
        }
        else {
            ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(cacheFilename)));
            trimmedSet = (Map<String,double[]>)ois.readObject();
        }

        return trimmedSet;
    }

    private Map<String,double[]> loadEmbeddingsFromFile(String filename) throws IOException {
        Map<String, double[]> embeddings = new HashMap<>();

        BufferedReader br = new BufferedReader(new FileReader(filename));

        int readLines = 0;

        String line = br.readLine();
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(" ");

            if (parts.length == 302) {
                String token = parts[0];
                double[] embedding = new double[300];
                for (int i = 1; i < parts.length - 1; i++) {
                    embedding[i - 1] = Double.parseDouble(parts[i]);
                }
                embeddings.put(token, embedding);
            }

            readLines++;
            if (readLines % 10000 == 0) {
                System.err.println("Read "+readLines+" lines");
            }
        }

        return embeddings;
    }
}