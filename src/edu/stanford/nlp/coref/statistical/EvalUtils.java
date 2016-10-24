package edu.stanford.nlp.coref.statistical;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import edu.stanford.nlp.coref.statistical.Clusterer.Cluster;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Pair;

/**
 * Utility classes for computing the B^3 and MUC coreference metrics
 * @author Kevin Clark
 */
public class EvalUtils {
  public static double getCombinedF1(double mucWeight,
      List<List<Integer>> gold,
      List<Cluster> clusters,
      Map<Integer, List<Integer>> mentionToGold,
      Map<Integer, Cluster> mentionToSystem) {
    CombinedEvaluator combined = new CombinedEvaluator(mucWeight);
    combined.update(gold, clusters, mentionToGold, mentionToSystem);
    return combined.getF1();
  }

  public static double f1(double pNum, double pDen, double rNum, double rDen) {
    double p = pNum == 0 ? 0 : pNum / pDen;
    double r = rNum == 0 ? 0 : rNum / rDen;
    return p == 0 ? 0 : 2 * p * r / (p + r);
  }

  public interface Evaluator {
    public void update(List<List<Integer>> gold,
        List<Cluster> clusters,
        Map<Integer, List<Integer>> mentionToGold,
        Map<Integer, Cluster> mentionToSystem);
    public double getF1();
  }

  public static class CombinedEvaluator implements Evaluator {
    private final B3Evaluator b3Evaluator;
    private final MUCEvaluator mucEvaluator;
    private final double mucWeight;

    public CombinedEvaluator(double mucWeight) {
      b3Evaluator = new B3Evaluator();
      mucEvaluator = new MUCEvaluator();
      this.mucWeight = mucWeight;
    }

    @Override
    public void update(List<List<Integer>> gold,
        List<Cluster> clusters,
        Map<Integer, List<Integer>> mentionToGold,
        Map<Integer, Cluster> mentionToSystem) {
      if (mucWeight != 1) {
        b3Evaluator.update(gold, clusters, mentionToGold, mentionToSystem);
      }
      if (mucWeight != 0) {
        mucEvaluator.update(gold, clusters, mentionToGold, mentionToSystem);
      }
    }

    @Override
    public double getF1() {
      return (mucWeight == 0 ? 0 : mucWeight * mucEvaluator.getF1()) +
          (mucWeight == 1 ? 0 : (1 - mucWeight) * b3Evaluator.getF1());
    }
  }

  public static abstract class AbstractEvaluator implements Evaluator {
    public double pNum;
    public double pDen;
    public double rNum;
    public double rDen;

    @Override
    public void update(List<List<Integer>> gold,
        List<Cluster> clusters,
        Map<Integer, List<Integer>> mentionToGold,
        Map<Integer, Cluster> mentionToSystem) {
      List<List<Integer>> clustersAsList = clusters.stream().map(c -> c.mentions)
          .collect(Collectors.toList());
      Map<Integer, List<Integer>> mentionToSystemLists = mentionToSystem.entrySet().stream()
          .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().mentions));

      Pair<Double, Double> prec = getScore(clustersAsList, mentionToGold);
      Pair<Double, Double> rec = getScore(gold, mentionToSystemLists);
      pNum += prec.first;
      pDen += prec.second;
      rNum += rec.first;
      rDen += rec.second;
    }

    @Override
    public double getF1() {
      return f1(pNum, pDen, rNum, rDen);
    }

    public double getRecall() {
      return pNum == 0 ? 0 : pNum / pDen;
    }

    public double getPrecision() {
      return rNum == 0 ? 0 : rNum / rDen;
    }

    public abstract Pair<Double, Double> getScore(List<List<Integer>> clusters,
        Map<Integer, List<Integer>> mentionToGold);
  }

  public static class B3Evaluator extends AbstractEvaluator {
    @Override
    public Pair<Double, Double> getScore(List<List<Integer>> clusters,
        Map<Integer, List<Integer>> mentionToGold) {
      double num = 0;
      int dem = 0;

      for (List<Integer> c : clusters) {
        if (c.size() == 1) {
          continue;
        }

        Counter<List<Integer>> goldCounts = new ClassicCounter<>();
        double correct = 0;
        for (int m : c) {
          List<Integer> goldCluster = mentionToGold.get(m);
          if (goldCluster != null) {
            goldCounts.incrementCount(goldCluster);
          }
        }

        for (Map.Entry<List<Integer>, Double> e : goldCounts.entrySet()) {
          if (e.getKey().size() != 1) {
            correct += e.getValue() * e.getValue();
          }
        }
        num += correct / c.size();
        dem += c.size();
      }

      return new Pair<>(num, (double) dem);
    }
  }

  public static class MUCEvaluator extends AbstractEvaluator {
    @Override
    public Pair<Double, Double> getScore(List<List<Integer>> clusters,
        Map<Integer, List<Integer>> mentionToGold) {
      int tp = 0;
      int predictedPositive = 0;

      for (List<Integer> c : clusters) {
        predictedPositive += c.size() - 1;
        tp += c.size();
        Set<List<Integer>> linked = new HashSet<>();
        for (int m : c) {
          List<Integer> g = mentionToGold.get(m);
          if (g == null) {
            tp -= 1;
          } else {
            linked.add(g);
          }
        }
        tp -= linked.size();
      }

      return new Pair<>((double) tp, (double) predictedPositive);
    }
  }
}
