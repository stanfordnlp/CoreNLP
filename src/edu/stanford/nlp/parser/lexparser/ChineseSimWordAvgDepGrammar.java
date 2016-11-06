package edu.stanford.nlp.parser.lexparser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * A Dependency grammar that smoothes by averaging over similar words.
 *
 * @author Galen Andrew
 * @author Pi-Chuan Chang
 */
@SuppressWarnings("deprecation")
public class ChineseSimWordAvgDepGrammar extends MLEDependencyGrammar  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ChineseSimWordAvgDepGrammar.class);

  private static final long serialVersionUID = -1845503582705055342L;

  private static final double simSmooth = 10.0;

  private static final String argHeadFile = "simWords/ArgHead.5";
  private static final String headArgFile = "simWords/HeadArg.5";
  private Map<Pair<Integer, String>, List<Triple<Integer, String, Double>>> simArgMap;
  private Map<Pair<Integer, String>, List<Triple<Integer, String, Double>>> simHeadMap;

  private static final boolean debug = true;

  private static final boolean verbose = false;
  //private static final double MIN_PROBABILITY = Math.exp(-100.0);

  public ChineseSimWordAvgDepGrammar(TreebankLangParserParams tlpParams, boolean directional, boolean distance, boolean coarseDistance, boolean basicCategoryTagsInDependencyGrammar, Options op, Index<String> wordIndex, Index<String> tagIndex) {
    super(tlpParams, directional, distance, coarseDistance, basicCategoryTagsInDependencyGrammar, op, wordIndex, tagIndex);

    simHeadMap = getMap(headArgFile);
    simArgMap = getMap(argHeadFile);
  }

  public Map<Pair<Integer, String>, List<Triple<Integer, String, Double>>> getMap(String filename) {
    Map<Pair<Integer, String>, List<Triple<Integer, String, Double>>> hashMap = Generics.newHashMap();
    try {
      BufferedReader wordMapBReader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));

      String wordMapLine;
      Pattern linePattern = Pattern.compile("sim\\((.+)/(.+):(.+)/(.+)\\)=(.+)");
      while ((wordMapLine = wordMapBReader.readLine()) != null) {
        Matcher m = linePattern.matcher(wordMapLine);
        if (!m.matches()) {
          log.info("Ill-formed line in similar word map file: " + wordMapLine);
          continue;
        }

        Pair<Integer, String> iTW = new Pair<>(wordIndex.addToIndex(m.group(1)), m.group(2));
        double score = Double.parseDouble(m.group(5));

        List<Triple<Integer, String, Double>> tripleList = hashMap.get(iTW);
        if (tripleList == null) {
          tripleList = new ArrayList<>();
          hashMap.put(iTW, tripleList);
        }

        tripleList.add(new Triple<>(wordIndex.addToIndex(m.group(3)), m.group(4), score));
      }
    } catch (IOException e) {
      throw new RuntimeException("Problem reading similar words file!");
    }

    return hashMap;
  }

  @Override
  public double scoreTB(IntDependency dependency) {
    //return op.testOptions.depWeight * Math.log(probSimilarWordAvg(dependency));
    return op.testOptions.depWeight * Math.log(probTBwithSimWords(dependency));
  }

  public void setLex(Lexicon lex) {
    this.lex = lex;
  }

  private ClassicCounter<String> statsCounter = new ClassicCounter<>();

  public void dumpSimWordAvgStats() {
    log.info("SimWordAvg stats:");
    log.info(statsCounter);
  }

  /*
  ** An alternative kind of smoothing.
  ** The first one is "probSimilarWordAvg" implemented by Galen
  ** This one is trying to modify "probTB" in MLEDependencyGrammar using the simWords list we have
  ** -pichuan
  */

  private double probTBwithSimWords(IntDependency dependency) {
    boolean leftHeaded = dependency.leftHeaded && directional;
    IntTaggedWord unknownHead = new IntTaggedWord(-1, dependency.head.tag);
    IntTaggedWord unknownArg = new IntTaggedWord(-1, dependency.arg.tag);
    if (verbose) {
      System.out.println("Generating " + dependency);
    }

    short distance = dependency.distance;
    // int hW = dependency.head.word;
    // int aW = dependency.arg.word;
    IntTaggedWord aTW = dependency.arg;
    // IntTaggedWord hTW = dependency.head;

    double pb_stop_hTWds = getStopProb(dependency);

    boolean isRoot = rootTW(dependency.head);
    if (dependency.arg.word == -2) {
      // did we generate stop?
      if (isRoot) {
        return 0.0;
      }
      return pb_stop_hTWds;
    }

    double pb_go_hTWds = 1.0 - pb_stop_hTWds;

    if (isRoot) {
      pb_go_hTWds = 1.0;
    }

    // generate the argument

    int valenceBinDistance = valenceBin(distance);

    // KEY:
    // c_     count of
    // p_     MLE prob of
    // pb_    MAP prob of
    // a      arg
    // h      head
    // T      tag
    // W      word
    // d      direction
    // ds     distance

    IntDependency temp = new IntDependency(dependency.head, dependency.arg, leftHeaded, valenceBinDistance);
    double c_aTW_hTWd = argCounter.getCount(temp);
    temp = new IntDependency(dependency.head, unknownArg, leftHeaded, valenceBinDistance);
    double c_aT_hTWd = argCounter.getCount(temp);
    temp = new IntDependency(dependency.head, wildTW, leftHeaded, valenceBinDistance);
    double c_hTWd = argCounter.getCount(temp);
    temp = new IntDependency(unknownHead, dependency.arg, leftHeaded, valenceBinDistance);
    double c_aTW_hTd = argCounter.getCount(temp);
    temp = new IntDependency(unknownHead, unknownArg, leftHeaded, valenceBinDistance);
    double c_aT_hTd = argCounter.getCount(temp);
    temp = new IntDependency(unknownHead, wildTW, leftHeaded, valenceBinDistance);
    double c_hTd = argCounter.getCount(temp);
    temp = new IntDependency(wildTW, dependency.arg, false, -1);
    double c_aTW = argCounter.getCount(temp);
    temp = new IntDependency(wildTW, unknownArg, false, -1);
    double c_aT = argCounter.getCount(temp);

    // do the magic
    double p_aTW_hTd = (c_hTd > 0.0 ? c_aTW_hTd / c_hTd : 0.0);
    double p_aT_hTd = (c_hTd > 0.0 ? c_aT_hTd / c_hTd : 0.0);
    double p_aTW_aT = (c_aTW > 0.0 ? c_aTW / c_aT : 1.0);

    double pb_aTW_hTWd; // = (c_aTW_hTWd + smooth_aTW_hTWd * p_aTW_hTd) / (c_hTWd + smooth_aTW_hTWd);
    double pb_aT_hTWd = (c_aT_hTWd + smooth_aT_hTWd * p_aT_hTd) / (c_hTWd + smooth_aT_hTWd);

    double score; // = (interp * pb_aTW_hTWd + (1.0 - interp) * p_aTW_aT * pb_aT_hTWd) * pb_go_hTWds;


    /* smooth by simWords -pichuan */
    List<Triple<Integer, String, Double>> sim2arg = simArgMap.get(new Pair<>(dependency.arg.word, stringBasicCategory(dependency.arg.tag)));
    List<Triple<Integer, String, Double>> sim2head = simHeadMap.get(new Pair<>(dependency.head.word, stringBasicCategory(dependency.head.tag)));

    List<Integer> simArg = new ArrayList<>();
    List<Integer> simHead= new ArrayList<>();

    if (sim2arg != null) {
      for (Triple<Integer,String,Double> t : sim2arg) {
        simArg.add(t.first);
      }
    }

    if (sim2head != null) {
      for (Triple<Integer,String,Double> t : sim2head) {
        simHead.add(t.first);
      }
    }

    double cSim_aTW_hTd = 0;
    double cSim_hTd = 0;
    for (int h : simHead) {
      IntTaggedWord hWord = new IntTaggedWord(h, dependency.head.tag);
      temp = new IntDependency(hWord, dependency.arg, dependency.leftHeaded, dependency.distance);
      cSim_aTW_hTd += argCounter.getCount(temp);

      temp = new IntDependency(hWord, wildTW, dependency.leftHeaded, dependency.distance);
      cSim_hTd += argCounter.getCount(temp);
    }
    double pSim_aTW_hTd = (cSim_hTd > 0.0 ? cSim_aTW_hTd / cSim_hTd : 0.0);  // P(Wa,Ta|Th)

    if (debug) {
      //if (simHead.size() > 0 && cSim_hTd == 0.0) {
        if (pSim_aTW_hTd > 0.0) {
        //System.out.println("# simHead("+dependency.head.word+"-"+wordNumberer.object(dependency.head.word)+") =\t"+cSim_hTd);
          System.out.println(dependency+"\t"+pSim_aTW_hTd);
          //System.out.println(wordNumberer);
        }
    }


    //pb_aTW_hTWd = (c_aTW_hTWd + smooth_aTW_hTWd * pSim_aTW_hTd + smooth_aTW_hTWd * p_aTW_hTd) / (c_hTWd + smooth_aTW_hTWd + smooth_aTW_hTWd);

    //if (pSim_aTW_hTd > 0.0) {
    double smoothSim_aTW_hTWd = 17.7;
    double smooth_aTW_hTWd = 17.7*2;

    //smooth_aTW_hTWd = smooth_aTW_hTWd*2;
    pb_aTW_hTWd = (c_aTW_hTWd + smoothSim_aTW_hTWd * pSim_aTW_hTd + smooth_aTW_hTWd * p_aTW_hTd) / (c_hTWd + smoothSim_aTW_hTWd + smooth_aTW_hTWd);
    System.out.println(dependency);
    System.out.println(c_aTW_hTWd+" + "+ smoothSim_aTW_hTWd+" * "+pSim_aTW_hTd+" + "+smooth_aTW_hTWd+" * "+p_aTW_hTd);
    System.out.println("--------------------------------  = "+pb_aTW_hTWd);
    System.out.println(c_hTWd+" + "+ smoothSim_aTW_hTWd+" + "+smooth_aTW_hTWd);
    System.out.println();
    //}

    //pb_aT_hTWd = (c_aT_hTWd + smooth_aT_hTWd * p_aT_hTd) / (c_hTWd + smooth_aT_hTWd);

    score = (interp * pb_aTW_hTWd + (1.0 - interp) * p_aTW_aT * pb_aT_hTWd) * pb_go_hTWds;

    if (verbose) {
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMaximumFractionDigits(2);
      System.out.println("  c_aTW_hTWd: " + c_aTW_hTWd + "; c_aT_hTWd: " + c_aT_hTWd + "; c_hTWd: " + c_hTWd);
      System.out.println("  c_aTW_hTd: " + c_aTW_hTd + "; c_aT_hTd: " + c_aT_hTd + "; c_hTd: " + c_hTd);
      System.out.println("  Generated with pb_go_hTWds: " + nf.format(pb_go_hTWds) + " pb_aTW_hTWd: " + nf.format(pb_aTW_hTWd) + " p_aTW_aT: " + nf.format(p_aTW_aT) + " pb_aT_hTWd: " + nf.format(pb_aT_hTWd));
      System.out.println("  NoDist score: " + score);
    }

    if (op.testOptions.prunePunc && pruneTW(aTW)) {
      return 1.0;
    }

    if (Double.isNaN(score)) {
      score = 0.0;
    }

    //if (op.testOptions.rightBonus && ! dependency.leftHeaded)
    //  score -= 0.2;

    if (score < MIN_PROBABILITY) {
      score = 0.0;
    }

    return score;
  }



  private double probSimilarWordAvg(IntDependency dep) {
    double regProb = probTB(dep);
    statsCounter.incrementCount("total");

    List<Triple<Integer, String, Double>> sim2arg = simArgMap.get(new Pair<>(dep.arg.word, stringBasicCategory(dep.arg.tag)));
    List<Triple<Integer, String, Double>> sim2head = simHeadMap.get(new Pair<>(dep.head.word, stringBasicCategory(dep.head.tag)));

    if (sim2head == null && sim2arg == null) {
      return regProb;
    }

    double sumScores = 0, sumWeights = 0;

    if (sim2head == null) {
      statsCounter.incrementCount("aSim");
      for (Triple<Integer, String, Double> simArg : sim2arg) {
        //double weight = 1 - simArg.third;
        double weight = Math.exp(-50*simArg.third);
        for (int tag = 0, numT = tagIndex.size(); tag < numT; tag++) {
          if (!stringBasicCategory(tag).equals(simArg.second)) {
            continue;
          }
          IntTaggedWord tempArg = new IntTaggedWord(simArg.first, tag);
          IntDependency tempDep = new IntDependency(dep.head, tempArg, dep.leftHeaded, dep.distance);
          double probArg = Math.exp(lex.score(tempArg, 0, wordIndex.get(tempArg.word), null));
          if (probArg == 0.0) {
            continue;
          }
          sumScores += probTB(tempDep) * weight / probArg;
          sumWeights += weight;
        }
      }
    } else if (sim2arg == null) {
      statsCounter.incrementCount("hSim");
      for (Triple<Integer, String, Double> simHead : sim2head) {
        //double weight = 1 - simHead.third;
        double weight = Math.exp(-50*simHead.third);
        for (int tag = 0, numT = tagIndex.size(); tag < numT; tag++) {
          if (!stringBasicCategory(tag).equals(simHead.second)) {
            continue;
          }
          IntTaggedWord tempHead = new IntTaggedWord(simHead.first, tag);
          IntDependency tempDep = new IntDependency(tempHead, dep.arg, dep.leftHeaded, dep.distance);
          sumScores += probTB(tempDep) * weight;
          sumWeights += weight;
        }
      }
    } else {
      statsCounter.incrementCount("hSim");
      statsCounter.incrementCount("aSim");
      statsCounter.incrementCount("aSim&hSim");
      for (Triple<Integer, String, Double> simArg : sim2arg) {
        for (int aTag = 0, numT = tagIndex.size(); aTag < numT; aTag++) {
          if (!stringBasicCategory(aTag).equals(simArg.second)) {
            continue;
          }
          IntTaggedWord tempArg = new IntTaggedWord(simArg.first, aTag);
          double probArg = Math.exp(lex.score(tempArg, 0, wordIndex.get(tempArg.word), null));
          if (probArg == 0.0) {
            continue;
          }
          for (Triple<Integer, String, Double> simHead : sim2head) {
            for (int hTag = 0; hTag < numT; hTag++) {
              if (!stringBasicCategory(hTag).equals(simHead.second)) {
                continue;
              }
              IntTaggedWord tempHead = new IntTaggedWord(simHead.first, aTag);
              IntDependency tempDep = new IntDependency(tempHead, tempArg, dep.leftHeaded, dep.distance);
              //double weight = (1-simHead.third) * (1-simArg.third);
              double weight = Math.exp(-50*simHead.third) * Math.exp(-50*simArg.third);
              sumScores += probTB(tempDep) * weight / probArg;
              sumWeights += weight;
            }
          }
        }
      }
    }

    IntDependency temp = new IntDependency(dep.head, wildTW, dep.leftHeaded, dep.distance);
    double countHead = argCounter.getCount(temp);

    double simProb;
    if (sim2arg == null) {
      simProb = sumScores / sumWeights;
    } else {
      double probArg = Math.exp(lex.score(dep.arg, 0, wordIndex.get(dep.arg.word), null));
      simProb = probArg * sumScores / sumWeights;
    }

    if (simProb == 0) {
      statsCounter.incrementCount("simProbZero");
    }
    if (regProb == 0) {
      //      log.info("zero reg prob");
      statsCounter.incrementCount("regProbZero");
    }
    double smoothProb = (countHead * regProb + simSmooth * simProb) / (countHead + simSmooth);
    if (smoothProb == 0) {
      //      log.info("zero smooth prob");
      statsCounter.incrementCount("smoothProbZero");
    }

    return smoothProb;
  }

  private String stringBasicCategory(int tag) {
    return tlp.basicCategory(tagIndex.get(tag));
  }

}
