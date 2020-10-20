package edu.stanford.nlp.coref.statistical;

import java.io.PrintWriter;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * A simple linear classifier trained by SGD with support for several different loss functions
 * and learning rate schedules.
 * @author Kevin Clark
 */
public class SimpleLinearClassifier {
  private static Redwood.RedwoodChannels log = Redwood.channels(SimpleLinearClassifier.class);

  private final Loss defaultLoss;
  private final LearningRateSchedule learningRateSchedule;
  private final double regularizationStrength;
  private final Counter<String> weights;
  private final Counter<String> accessTimes;
  private int examplesSeen;

  public SimpleLinearClassifier(Loss loss, LearningRateSchedule learningRateSchedule,
      double regularizationStrength) {
    this(loss, learningRateSchedule, regularizationStrength, null);
  }

  public SimpleLinearClassifier(Loss loss,LearningRateSchedule learningRateSchedule,
      double regularizationStrength, String modelFile) {
    if (modelFile != null) {
      try {
        if (modelFile.endsWith(".tab.gz")) {
          Timing.startDoing("Reading " + modelFile);
          this.weights = Counters.deserializeStringCounter(modelFile);
          Timing.endDoing("Reading " + modelFile);
        } else {
          this.weights = IOUtils.readObjectAnnouncingTimingFromURLOrClasspathOrFileSystem(
              log, "Loading coref model", modelFile);
        }
      } catch (Exception e) {
        throw new RuntimeException("Error leading weights from " + modelFile, e);
      }
    } else {
      this.weights = new ClassicCounter<>();
    }

    this.defaultLoss = loss;
    this.regularizationStrength = regularizationStrength;
    this.learningRateSchedule = learningRateSchedule;
    accessTimes = new ClassicCounter<>();
    examplesSeen = 0;
  }

  public void learn(Counter<String> features, double label, double weight) {
    learn(features, label, weight, defaultLoss);
  }

  public void learn(Counter<String> features, double label, double weight, Loss loss) {
    examplesSeen++;
    double dloss = loss.derivative(label, weightFeatureProduct(features));
    for (Map.Entry<String, Double> feature : features.entrySet()) {
      double dfeature = weight * (-dloss * feature.getValue());
      if (dfeature != 0) {
        String featureName = feature.getKey();
        learningRateSchedule.update(featureName, dfeature);
        double lr = learningRateSchedule.getLearningRate(featureName);
        double w = weights.getCount(featureName);
        double dreg = weight * regularizationStrength
            * (examplesSeen - accessTimes.getCount(featureName));
        double afterReg = (w - Math.signum(w) * dreg * lr);
        weights.setCount(featureName, (Math.signum(afterReg) != Math.signum(w) ? 0 : afterReg)
            + dfeature * lr);
        accessTimes.setCount(featureName, examplesSeen);
      }
    }
  }

  public double label(Counter<String> features) {
    return defaultLoss.predict(weightFeatureProduct(features));
  }

  public double weightFeatureProduct(Counter<String> features) {
    double product = 0;
    for (Map.Entry<String, Double> feature : features.entrySet()) {
      product += feature.getValue() * weights.getCount(feature.getKey());
    }
    return product;
  }

  public void setWeight(String featureName, double weight) {
    weights.setCount(featureName, weight);
  }

  public SortedMap<String, Double> getWeightVector() {
    SortedMap<String, Double> m = new TreeMap<>((f1, f2) -> {
      double weightDifference = Math.abs(weights.getCount(f2)) - Math.abs(weights.getCount(f1));
      return weightDifference == 0 ? f1.compareTo(f2) : (int) Math.signum(weightDifference);
    });
    weights.entrySet().stream().forEach(e -> m.put(e.getKey(), e.getValue()));
    return m;
  }

  public void printWeightVector() {
    printWeightVector(null);
  }

  public void printWeightVector(PrintWriter writer) {
    SortedMap<String, Double> sortedWeights = getWeightVector();
    for (Map.Entry<String, Double> e : sortedWeights.entrySet()) {
      if (writer == null) {
        Redwood.log("scoref.train", e.getKey() + " => " + e.getValue());
      } else {
        writer.println(e.getKey() + " => " + e.getValue());
      }
    }
  }

  public void writeWeights(String fname) throws Exception {
    IOUtils.writeObjectToFile(weights, fname);
  }

  // ---------- LOSS FUNCTIONS ----------

  public static interface Loss {
    public double predict(double product);
    public double derivative(double label, double product);
  }

  public static Loss log() {
    return new Loss() {
      @Override
      public double predict(double product) {
        return (1 - (1 / (1 + Math.exp(product))));
      }

      @Override
      public double derivative(double label, double product) {
        return -label / (1 + Math.exp(label * product));
      }

      @Override
      public String toString() {
        return "log";
      }
    };
  }

  public static Loss quadraticallySmoothedSVM(final double gamma) {
    return new Loss() {
      @Override
      public double predict(double product) {
        return product;
      };

      @Override
      public double derivative(double label, double product) {
        double mistake = label * product;
        return mistake >= 1 ? 0 : (mistake >= 1 - gamma ?
            (mistake - 1) * label / gamma : -label);
      }

      @Override
      public String toString() {
        return String.format("quadraticallySmoothed(%s)", gamma);
      }
    };
  }

  public static Loss hinge() {
    return quadraticallySmoothedSVM(0);
  }

  public static Loss maxMargin(final double h) {
    return new Loss() {
      @Override
      public double predict(double product) {
        throw new UnsupportedOperationException("Predict not implemented for max margin");
      }

      @Override
      public double derivative(double label, double product) {
        return product < -h ? 0 : 1;
      }

      @Override
      public String toString() {
        return String.format("max-margin(%s)", h);
      }
    };
  }

  public static Loss risk() {
    return new Loss() {
      @Override
      public double predict(double product) {
        return 1 / (1 + Math.exp(product));
      }

      @Override
      public double derivative(double label, double product) {
        return -Math.exp(product) / Math.pow(1 + Math.exp(product), 2);
      }

      @Override
      public String toString() {
        return "risk";
      }
    };
  }

  // ---------- LEARNING RATE SCHEDULES ----------

  public static interface LearningRateSchedule {
    public void update(String feature, double gradient);
    public double getLearningRate(String feature);
  }

  private abstract static class CountBasedLearningRate implements LearningRateSchedule {
    private final Counter<String> counter;

    public CountBasedLearningRate() {
      counter = new ClassicCounter<>();
    }

    @Override
    public void update(String feature, double gradient) {
      counter.incrementCount(feature, getCounterIncrement(gradient));
    }

    @Override
    public double getLearningRate(String feature) {
      return getLearningRate(counter.getCount(feature));
    }

    public abstract double getCounterIncrement(double gradient);
    public abstract double getLearningRate(double count);
  }

  public static LearningRateSchedule constant(final double eta) {
    return new LearningRateSchedule() {
      @Override
      public double getLearningRate(String feature) {
        return eta;
      }

      @Override
      public void update(String feature, double gradient) { }

      @Override
      public String toString() {
        return String.format("constant(%s)", eta);
      }
    };
  }

  public static LearningRateSchedule invScaling(final double eta, final double p) {
    return new CountBasedLearningRate() {
      @Override
      public double getCounterIncrement(double gradient) {
        return 1.0;
      }

      @Override
      public double getLearningRate(double count) {
        return eta / Math.pow(1 + count, p);
      }

      @Override
      public String toString() {
        return String.format("invScaling(%s, %s)", eta, p);
      }
    };
  }

  public static LearningRateSchedule adaGrad(final double eta, final double tau) {
    return new CountBasedLearningRate() {
      @Override
      public double getCounterIncrement(double gradient) {
        return gradient * gradient;
      }

      @Override
      public double getLearningRate(double count) {
        return eta / (tau + Math.sqrt(count));
      }

      @Override
      public String toString() {
        return String.format("adaGrad(%s, %s)", eta, tau);
      }
    };
  }
}
