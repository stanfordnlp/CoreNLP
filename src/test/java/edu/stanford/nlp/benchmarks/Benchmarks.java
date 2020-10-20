package edu.stanford.nlp.benchmarks;

import edu.stanford.nlp.classify.Dataset;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.Minimizer;
import edu.stanford.nlp.optimization.SGDMinimizer;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Factory;

import java.util.*;

/**
 * Created by keenon on 6/19/15.
 *
 * Down and dirty (and not entirely representative) benchmarks to quickly judge improvement as we optimize stuff
 */
public class Benchmarks {
    /**
     * 67% of time spent in LogConditionalObjectiveFunction.rvfcalculate()
     * 29% of time spent in dataset construction (11% in RVFDataset.addFeatures(), 7% rvf incrementCount(), 11% rest)
     *
     * Single threaded, 4700 ms
     * Multi threaded, 700 ms
     *
     * With same data, seed 42, 245 ms
     * With reordered accesses for cacheing, 195 ms
     * Down to 80% of the time, not huge but a win nonetheless
     *
     * with 8 cpus, a 6.7x speedup -- almost, but not quite linear, pretty good
     */
    public static void benchmarkRVFLogisticRegression() {
        RVFDataset<String, String> data = new RVFDataset<>();
        for (int i = 0; i < 10000; i++) {
            Random r = new Random(42);
            Counter<String> features = new ClassicCounter<>();

            boolean cl = r.nextBoolean();

            for (int j = 0; j < 1000; j++) {
                double value;
                if (cl && i % 2 == 0) {
                    value = (r.nextDouble()*2.0)-0.6;
                }
                else {
                    value = (r.nextDouble()*2.0)-1.4;
                }
                features.incrementCount("f" + j, value);
            }

            data.add(new RVFDatum<>(features, "target:" + cl));
        }

        LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();

        long msStart = System.currentTimeMillis();
        factory.trainClassifier(data);
        long delay = System.currentTimeMillis() - msStart;
        System.out.println("Training took "+delay+" ms");
    }

    /**
     * 57% of time spent in LogConditionalObjectiveFunction.calculateCLBatch()
     * 22% spent in constructing datums (expensive)
     *
     * Single threaded, 4100 ms
     * Multi threaded, 600 ms
     *
     * With same data, seed 42, 52 ms
     * With reordered accesses for cacheing, 38 ms
     * Down to 73% of the time
     *
     * with 8 cpus, a 6.8x speedup -- basically the same as with RVFDatum
     */
    public static void benchmarkLogisticRegression() {
        Dataset<String, String> data = new Dataset<>();
        for (int i = 0; i < 10000; i++) {
            Random r = new Random(42);
            Set<String> features = new HashSet<>();

            boolean cl = r.nextBoolean();

            for (int j = 0; j < 1000; j++) {
                if (cl && i % 2 == 0) {
                    if (r.nextDouble() > 0.3) {
                        features.add("f:"+j+":true");
                    }
                    else {
                        features.add("f:"+j+":false");
                    }
                }
                else {
                    if (r.nextDouble() > 0.3) {
                        features.add("f:" + j + ":false");
                    }
                    else {
                        features.add("f:"+j+":false");
                    }
                }
            }

            data.add(new BasicDatum<String, String>(features, "target:" + cl));
        }

        LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();

        long msStart = System.currentTimeMillis();
        factory.trainClassifier(data);
        long delay = System.currentTimeMillis() - msStart;
        System.out.println("Training took "+delay+" ms");
    }

    /**
     * 29% in FactorTable.getValue()
     * 28% in CRFCliqueTree.getCalibratedCliqueTree()
     * 12.6% waiting for threads
     *
     * Single threaded: 15000 ms - 26000 ms
     * Multi threaded: 4500 ms - 7000 ms
     *
     * with 8 cpus, 3.3x - 3.7x speedup, around 800% utilization
     */
    public static void benchmarkCRF() {
        Properties props = new Properties();
        props.setProperty("macro", "true"); // use a generic CRF configuration
        props.setProperty("useIfInteger", "true");
        props.setProperty("featureFactory", "edu.stanford.nlp.benchmarks.BenchmarkFeatureFactory");
        props.setProperty("saveFeatureIndexToDisk", "false");

        CRFClassifier<CoreLabel> crf = new CRFClassifier<>(props);

        Random r = new Random(42);

        List<List<CoreLabel>> data = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            List<CoreLabel> sentence = new ArrayList<>();
            for (int j = 0; j < 20; j++) {
                CoreLabel l = new CoreLabel();

                l.setWord("j:"+j);

                boolean tag = j % 2 == 0 ^ (r.nextDouble() > 0.7);
                l.set(CoreAnnotations.AnswerAnnotation.class, "target:"+tag);
                sentence.add(l);
            }
            data.add(sentence);
        }

        long msStart = System.currentTimeMillis();
        crf.train(data);
        long delay = System.currentTimeMillis() - msStart;
        System.out.println("Training took "+delay+" ms");
    }


    public static void benchmarkSGD() {
        Dataset<String, String> data = new Dataset<>();
        for (int i = 0; i < 10000; i++) {
            Random r = new Random(42);
            Set<String> features = new HashSet<>();

            boolean cl = r.nextBoolean();

            for (int j = 0; j < 1000; j++) {
                if (cl && i % 2 == 0) {
                    if (r.nextDouble() > 0.3) {
                        features.add("f:"+j+":true");
                    }
                    else {
                        features.add("f:"+j+":false");
                    }
                }
                else {
                    if (r.nextDouble() > 0.3) {
                        features.add("f:" + j + ":false");
                    }
                    else {
                        features.add("f:"+j+":false");
                    }
                }
            }

            data.add(new BasicDatum<String, String>(features, "target:" + cl));
        }

        LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();
        factory.setMinimizerCreator(new Factory<Minimizer<DiffFunction>>() {
            @Override
            public Minimizer<DiffFunction> create() {
                return new SGDMinimizer<DiffFunction>(0.1, 100, 0, 1000);
            }
        });

        long msStart = System.currentTimeMillis();
        factory.trainClassifier(data);
        long delay = System.currentTimeMillis() - msStart;
        System.out.println("Training took "+delay+" ms");
    }

    public static void benchmarkDatum() {
        long msStart = System.currentTimeMillis();
        Dataset<String, String> data = new Dataset<>();
        for (int i = 0; i < 10000; i++) {
            Random r = new Random(42);
            Set<String> features = new HashSet<>();

            boolean cl = r.nextBoolean();

            for (int j = 0; j < 1000; j++) {
                if (cl && i % 2 == 0) {
                    if (r.nextDouble() > 0.3) {
                        features.add("f:"+j+":true");
                    }
                    else {
                        features.add("f:"+j+":false");
                    }
                }
                else {
                    if (r.nextDouble() > 0.3) {
                        features.add("f:" + j + ":false");
                    }
                    else {
                        features.add("f:"+j+":false");
                    }
                }
            }

            data.add(new BasicDatum<String, String>(features, "target:" + cl));
        }
        long delay = System.currentTimeMillis() - msStart;
        System.out.println("Dataset construction took "+delay+" ms");

        msStart = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            Random r = new Random(42);
            Set<String> features = new HashSet<>();

            boolean cl = r.nextBoolean();

            for (int j = 0; j < 1000; j++) {
                if (cl && i % 2 == 0) {
                    if (r.nextDouble() > 0.3) {

                    }
                    else {

                    }
                }
                else {
                    if (r.nextDouble() > 0.3) {

                    }
                    else {

                    }
                }
            }
        }
        delay = System.currentTimeMillis() - msStart;
        System.out.println("MultiVector took "+delay+" ms");
    }

    /**
     * on my machine this results in a factor of two gain, roughly
     */
    public static void testAdjacency() {
        double[][] sqar = new double[10000][1000];
        Random r = new Random();

        int k = 0;
        long msStart = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            int loc = r.nextInt(10000);
            for (int j = 0; j < 1000; j++) {
                k+= sqar[loc][j];
            }
        }
        long delay = System.currentTimeMillis() - msStart;
        System.out.println("Scanning with cache friendly lookups took "+delay+" ms");

        int[] randLocs = new int[10000];
        for (int i = 0; i < 10000; i++) {
            randLocs[i] = r.nextInt(10000);
        }

        k = 0;
        msStart = System.currentTimeMillis();
        for (int j = 0; j < 1000; j++) {
            for (int i = 0; i < 10000; i++) {
                k+= sqar[randLocs[i]][j];
            }
        }
        delay = System.currentTimeMillis() - msStart;
        System.out.println("Scanning with cache UNfriendly lookups took "+delay+" ms");
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            // benchmarkRVFLogisticRegression();
            // benchmarkLogisticRegression();
            benchmarkSGD();
            // benchmarkCRF();
            // testAdjacency();
        }
    }
}
