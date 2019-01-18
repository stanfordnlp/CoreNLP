/**
 * <p>
 * The classify package provides facilities for training classifiers.
 * In this package, data points are viewed as single instances, not sequences.
 * The most commonly used classifier is the softmax log-linear classifier with binary features.
 * More classifiers, such as SVM and Naive Bayes, are also available in this package.
 * <p>The {@code Classifier} contract only guarantees routines for getting a classification for an example,
 * and the scores assigned to each class for that example.
 * <b>Note</b> that training is dependent upon the individual classifier.
 * <p>Classifiers operate over {@code Datum} objects.  A {@code Datum} is a list of descriptive features and
 * a class label; features and labels can be any object, but usually {@code String}s are used.  A Datum can store
 * only categorical features (common in NLP) or it can store features with real values. The latter is referred to in
 * this package as an RVFDatum (real-valued feature datum).
 * Datum objects are grouped using {@code Dataset} objects.  Some classifiers use Dataset objects as a way of grouping inputs.
 * <p>Following is a set of examples outlining how to create, train, and use each of the different classifier types.
 *
 * <h3>Linear Classifiers</h3>
 * <p>To build a classifier, one first creates a {@code GeneralDataset}, which is a list to {@code Datum} objects.
 * A {@code Datum} is a list of descriptive features, along with a label; features and labels can be any object,
 * though we usually use strings.
 * <p><pre>
 * GeneralDataset dataSet=new Dataset();
 * while (more datums to make) {
 * ... make featureList: e.g., ["PrevWord=at","CurrentTag=NNP","isUpperCase"]
 * ... make label: e.g., ["PLACE"];
 * Datum d = new BasicDatum(featureList, label);
 * dataSet.add(d);
 * }
 * </pre>
 * <p>There are some useful methods in {@code GeneralDataset} such as:
 * <p><pre>
 * dataSet.applyFeatureCountThreshold(int cutoff);
 * dataSet.summaryStatistics(); // dumps the number of features and datums
 * </pre>
 * <p>Next, one makes a {@code LinearClassifierFactory} and calls its {@code trainClassifier(GeneralDataset dataSet)} method:
 * <p><pre>
 * LinearClassifierFactory lcFactory = new LinearClassifierFactory();
 * LinearClassifier c = lcFactory.trainClassifier(dataSet);
 * </pre>
 * <p>{@code LinearClassifierFactory} has options for different optimizers (default: QNminimizer), the converge threshold for minimization, etc. Check the class description for detailed information.
 * <p>A classifier, once built, can be used to classify new {@code Datum} instances:
 * <p><pre>
 * Object label = c.classOf(mysteryDatum);
 * </pre>
 * If you want scores instead, you can ask:
 * <p><pre>
 * Counter scores = c.scoresOf(mysteryDatum);
 * </pre>
 * <p>The scores which are returned by the log-linear classifiers are the feature-weight
 * dot products, not the normalized probabilities.
 * <p>There are some other useful methods like {@code justificationOf(Datum d)}, and
 * {@code logProbabilityOf(Datum d)}, also various methods for visualizing the
 * weights and the most highly weighted features.
 * This concludes the log-linear classifiers with binary features.
 * <p>We can also train log-linear classifiers with real-valued features. In this case,
 * {@code RVFDatum} should be used.
 *
 * <h3>Real Valued Classifiers</h3>
 * <p>Real Valued Classifiers (RVF) operate over {@code RVFDatum} objects.  A RVFDatum is composed of a set of feature and real-value pairs.  RVFDatums are grouped using a {@code RVFDataset}.
 * <p>To assemble an {@code RVFDatum} by using a {@code Counter} and assigning an {@code Object} label to it.
 * <pre>
 * Counter features = new Counter();
 * features.incrementCount("FEATURE_A", 1.2);
 * features.incrementCount("FEATURE_B", 2.3);
 * features.incrementCount("FEATURE_C", 0.5);
 * RVFDatum rvfDatum = new RVFDatum(features, "DATUM_LABEL");
 * </pre>
 * <p>
 * {@code RVFDataset} objects are representations of {@code RVFDatum} objects that efficiently store
 * the data with which to train the classifier.  This type of dataset only accepts {@code RVFDatum} objects via its add
 * method (other {@code Datum} objects that are not instances of {@code RVFDatum} will be ignored), and is equivalent to a {@code Dataset}
 * if all {@code RVFDatum} objects have only features with value 1.0.  Since it is a subclass of {@code GeneralDataset},
 * the methods shown above as applied to the {@code GeneralDataset} can also be applied to the {@code RVFDataset}.
 * <p>
 * (TODO) An example for LinearType2Classifier.
 * <p>
 * (TODO) Saving Classifier out to file (from {@code LearningExperiment})
 * <pre>
 * private static void saveClassifierToFile(LinearClassifier classifier, String serializePath) {
 * log.info("Serializing classifier to " + serializePath + "...");
 * try {
 * ObjectOutputStream oos;
 * if (serializePath.endsWith(".gz")) {
 * oos = new ObjectOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(serializePath))));
 * } else {
 * oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(serializePath)));
 * }
 * oos.writeObject(classifier);
 * oos.close();
 * Logging.logger(this.getClass()).info("done.");
 * } catch (Exception e) {
 * e.printStackTrace();
 * throw new RuntimeException("Serialization failed: "+e.getMessage());
 * }
 * }
 * </pre>
 * <p>Alternately, if your features are Strings, and you wish to serialize to a human readable text file,
 * you can use {@code saveToFilename} in {@code LinearClassifier} and reconstitute using {@code loadFromFilename}
 * in {@code LinearClassifierFactory}.  Though the format is not as compact as a serialized object,
 * and implicitly presumes the features are Strings, this is useful for debugging purposes.
 *
 * @author Dan Klein
 * @author Eric Yeh
 */
package edu.stanford.nlp.classify;
