package edu.stanford.nlp.ie.machinereading.domains.roth;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.ie.machinereading.MachineReading;
import edu.stanford.nlp.ie.machinereading.MachineReadingProperties;
import edu.stanford.nlp.ie.machinereading.RelationFeatureFactory;
import edu.stanford.nlp.ie.machinereading.structure.RelationMentionFactory;
import edu.stanford.nlp.ie.machinereading.ResultsPrinter;
import edu.stanford.nlp.ie.machinereading.structure.AnnotationUtils;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;


/** @author Mason Smith */
public class RothResultsByRelation extends ResultsPrinter {

  /*
   * FeatureFactory class to use for generating features from relations for relation extraction.
   * Default is "edu.stanford.nlp.ie.machinereading.RelationFeatureFactory"
   */
  //private Class<RelationFeatureFactory> relationFeatureFactoryClass = edu.stanford.nlp.ie.machinereading.RelationFeatureFactory.class;

  /*
   * comma-separated list of feature types to generate for relation extraction
   */
  //private String relationFeatures;

  private RelationFeatureFactory featureFactory;
  private RelationMentionFactory mentionFactory;

  @Override
  public void printResults(PrintWriter pw,
                           List<CoreMap> goldStandard,
                           List<CoreMap> extractorOutput) {

    featureFactory = MachineReading.makeRelationFeatureFactory(MachineReadingProperties.relationFeatureFactoryClass, MachineReadingProperties.relationFeatures, false);
    mentionFactory = new RelationMentionFactory(); // generic mentions work well in this domain

    ResultsPrinter.align(goldStandard, extractorOutput);

    List<RelationMention> relations = new ArrayList<>();
    final Map<RelationMention,String> predictions = new HashMap<>();
    for (int i = 0; i < goldStandard.size(); i++) {
      List<RelationMention> goldRelations = AnnotationUtils.getAllRelations(mentionFactory, goldStandard.get(i), true);
      relations.addAll(goldRelations);
      for (RelationMention rel : goldRelations) {
        predictions.put(rel, AnnotationUtils.getRelation(mentionFactory, extractorOutput.get(i), rel.getArg(0),rel.getArg(1)).getType());
      }
    }

    final Counter<Pair<Pair<String, String>, String>> pathCounts = new ClassicCounter<>();

    for (RelationMention rel : relations) {
      pathCounts.incrementCount(new Pair<>(new Pair<>(
              rel.getArg(0).getType(), rel.getArg(1).getType()), featureFactory.getFeature(rel, "dependency_path_lowlevel")));
    }

    Counter<String> singletonCorrect = new ClassicCounter<>();
    Counter<String> singletonPredicted = new ClassicCounter<>();
    Counter<String> singletonActual = new ClassicCounter<>();
    for (RelationMention rel : relations) {
      if (pathCounts.getCount(new Pair<>(new Pair<>(rel.getArg(0).getType(),
              rel.getArg(1).getType()), featureFactory.getFeature(rel, "dependency_path_lowlevel"))) == 1.0) {
        String prediction = predictions.get(rel);
        if (prediction.equals(rel.getType())) {
          singletonCorrect.incrementCount(prediction);
        }
        singletonPredicted.incrementCount(prediction);
        singletonActual.incrementCount(rel.getType());
      }
    }

    class RelComp implements Comparator<RelationMention> {
      @Override
      public int compare(RelationMention rel1, RelationMention rel2) {

        // Group together actual relations of a type with relations that were
        // predicted to be that type
        String prediction1 = predictions.get(rel1);
        String prediction2 = predictions.get(rel2);
        // String rel1group = RelationsSentence.isUnrelatedLabel(rel1.getType())
        // ? prediction1 : rel1.getType();
        // String rel2group = RelationsSentence.isUnrelatedLabel(rel2.getType())
        // ? prediction2 : rel2.getType();
        int entComp = (rel1.getArg(0).getType() + rel1.getArg(1).getType()).compareTo(rel2.getArg(0).getType()
                + rel2.getArg(1).getType());
        // int groupComp = rel1group.compareTo(rel2group);
        int typeComp = rel1.getType().compareTo(rel2.getType());
        int predictionComp = prediction1.compareTo(prediction2);
        // int pathComp =
        // getFeature(rel1,"generalized_dependency_path").compareTo(getFeature(rel2,"generalized_dependency_path"));
        double pathCount1 = pathCounts.getCount(new Pair<>(new Pair<>(rel1
                .getArg(0).getType(), rel1.getArg(1).getType()), featureFactory.getFeature(rel1, "dependency_path_lowlevel")));
        double pathCount2 = pathCounts.getCount(new Pair<>(new Pair<>(rel2
                .getArg(0).getType(), rel2.getArg(1).getType()), featureFactory.getFeature(rel2, "dependency_path_lowlevel")));
        if (entComp != 0) {
          return entComp;
          // } else if (pathComp != 0) {
          // return pathComp;
        } else if (pathCount1 < pathCount2) {
          return -1;
        } else if (pathCount1 > pathCount2) {
          return 1;
        } else if (typeComp != 0) {
          return typeComp;
        } else if (predictionComp != 0) {
          return predictionComp;
        } else {
          return rel1.getSentence().get(CoreAnnotations.TextAnnotation.class).compareTo(rel2.getSentence().get(CoreAnnotations.TextAnnotation.class));
        }
      }
    }

    RelComp relComp = new RelComp();

    Collections.sort(relations, relComp);

    for (RelationMention rel : relations) {

      String prediction = predictions.get(rel);
      // if (RelationsSentence.isUnrelatedLabel(prediction) &&
      // RelationsSentence.isUnrelatedLabel(rel.getType())) {
      // continue;
      // }
      String type1 = rel.getArg(0).getType();
      String type2 = rel.getArg(1).getType();
      String path = featureFactory.getFeature(rel, "dependency_path_lowlevel");
      if (!((type1.equals("PEOPLE") && type2.equals("PEOPLE")) || (type1.equals("PEOPLE") && type2.equals("LOCATION"))
              || (type1.equals("LOCATION") && type2.equals("LOCATION")) || (type1.equals("ORGANIZATION") && type2.equals("LOCATION")) || (type1
              .equals("PEOPLE") && type2.equals("ORGANIZATION")))) {
        continue;
      }
      if (path.equals("")) {
        continue;
      }


      pw.println("\nLABEL: " + prediction);
      pw.println(rel);
      pw.println(path);
      pw.println(featureFactory.getFeatures(rel,"dependency_path_words"));
      pw.println(featureFactory.getFeature(rel, "surface_path_POS"));
    }
  }

  @Override
  public void printResultsUsingLabels(PrintWriter pw,
                                      List<String> goldStandard,
                                      List<String> extractorOutput) {
  }

}
