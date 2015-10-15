package edu.stanford.nlp.scoref;

import java.io.PrintWriter;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.logging.Redwood;

public class SimpleLinearClassifier {
  private final Loss defaultLoss;
  private final Penalty penalty;
  private final LearningRateSchedule learningRateSchedule;
  private final Counter<String> weights;

  public SimpleLinearClassifier(Loss loss, Penalty penalty,
      LearningRateSchedule learningRateSchedule) {
    this.defaultLoss = loss;
    this.penalty = penalty;
    this.learningRateSchedule = learningRateSchedule;
    this.weights = new ClassicCounter<>();
  }

  public SimpleLinearClassifier(Loss loss, Penalty penalty,
      LearningRateSchedule learningRateSchedule, String modelFile) {
    this.defaultLoss = loss;
    this.penalty = penalty;
    this.learningRateSchedule = learningRateSchedule;
    if (modelFile != null) {
      try {
        this.weights = IOUtils.readObjectFromURLOrClasspathOrFileSystem(modelFile);
      } catch (Exception e) {
        throw new RuntimeException("Error leading weights from " + modelFile, e);
      }
    } else {
      this.weights = new ClassicCounter<>();
    }
  }

  public void learn(Counter<String> features, double label, double weight) {
    learn(features, label, weight, defaultLoss);
  }

  public void learn(Counter<String> features, double label, double weight, Loss loss) {
    double dloss = loss.derivative(label, weightFeatureProduct(features));
    for (Map.Entry<String, Double> feature : features.entrySet()) {
      String featureName = feature.getKey();
      double w = weights.getCount(featureName);
      double dobjective = weight * (-dloss * feature.getValue() - penalty.derivative(w));

      learningRateSchedule.update(featureName, dobjective);
      if (dobjective != 0) {
        weights.incrementCount(featureName, dobjective *
            learningRateSchedule.getLearningRate(featureName));
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
    SortedMap<String, Double> m = new TreeMap<String, Double>((f1, f2) -> {
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
        return String.format("risk");
      }
    };
  }

  // ---------- REGULARIZATION PENALTIES ----------

  public static interface Penalty {
    public double derivative(double w);
  }

  public static Penalty none() {
    return new Penalty() {
      @Override
      public double derivative(double w) {
        return 0;
      }

      @Override
      public String toString() {
        return "none";
      }
    };
  }

  public static Penalty l1(final double alpha) {
    return new Penalty() {
      @Override
      public double derivative(double w) {
        return w == 0.0 ? 0 : alpha * Math.signum(w);
      }

      @Override
      public String toString() {
        return String.format("l1(%s)", alpha);
      }
    };
  }

  public static Penalty l2(final double alpha) {
    return new Penalty() {
      @Override
      public double derivative(double w) {
        return alpha * w;
      }

      @Override
      public String toString() {
        return String.format("l2(%s)", alpha);
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
