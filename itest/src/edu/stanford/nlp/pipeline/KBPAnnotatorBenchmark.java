package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.util.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;

import static org.junit.Assert.assertTrue;

import java.io.*;
import java.util.*;

import org.junit.Test;

public class KBPAnnotatorBenchmark {

  public HashMap<String,String> docIDToText;
  public HashMap<String,Set<String>> docIDToRelations;
  public StanfordCoreNLP pipeline;

  public String KBP_DOCS_DIR;
  public String GOLD_RELATIONS_PATH;
  public double KBP_MINIMUM_SCORE;

  public void loadGoldData() {
    // initialize HashMaps
    docIDToText = new HashMap<String,String>();
    docIDToRelations = new HashMap<String,Set<String>>();
    // load the gold relations from gold relations file
    List<String> goldRelationLines = IOUtils.linesFromFile(GOLD_RELATIONS_PATH);
    for (String relationLine : goldRelationLines) {
      String[] docIDAndRelation = relationLine.split("\t");
      if (docIDToRelations.get(docIDAndRelation[0]) == null) {
        docIDToRelations.put(docIDAndRelation[0], new HashSet<String>());
      }
      docIDToRelations.get(docIDAndRelation[0]).add(docIDAndRelation[1]);
    }
    // load the text for each docID
    File directoryWithDocs = new File(KBP_DOCS_DIR);
    File[] allFiles = directoryWithDocs.listFiles();
    for (File kbpTestDocFile : allFiles) {
      String kbpTestDocID = kbpTestDocFile.getName();
      String kbpTestDocPath = kbpTestDocFile.getAbsolutePath();
      String kbpTestDocContents = IOUtils.stringFromFile(kbpTestDocPath);
      docIDToText.put(kbpTestDocID, kbpTestDocContents);
    }
  }

  private String convertRelationName(String relationName) {
    /*if (relationName.equals("org:top_members/employees")) {
      return "org:top_members_employees";
    }*/
    if (relationName.equals("per:employee_of")) {
      return "per:employee_or_member_of";
    }
    if (relationName.equals("per:stateorprovinces_of_residence")) {
      return "per:statesorprovinces_of_residence";
    }
    if (relationName.equals("org:number_of_employees/members")) {
      return "org:number_of_employees_members";
    }
    if (relationName.equals("org:stateorprovince_of_headquarters")) {
      return "org:stateprovince_of_headquarters";
    }
    if (relationName.equals("per:other_family")) {
      return "per:otherfamily";
    }
    if (relationName.equals("org:founded")) {
      return "org:date_founded";
    }
    if (relationName.equals("org:political/religious_affiliation")) {
      return "org:political_religious_affiliation";
    }
    return relationName;
  }

  public Set<String> convertKBPTriplesToStrings(List<RelationTriple> relationTripleList) {
    HashSet<String> foundRelationStrings = new HashSet<>();
    for (RelationTriple rt : relationTripleList) {
      String relationName = convertRelationName(rt.relationGloss());
      String relationString = relationName+"("+rt.subjectGloss()+","+rt.objectGloss()+")";
      foundRelationStrings.add(relationString);
    }
    return foundRelationStrings;
  }

  @Test
  public void testKBPAnnotatorResults() {
    int totalGoldRelations = 0;
    int totalCorrectFoundRelations = 0;
    int totalWrongFoundRelations = 0;
    int totalGuessRelations = 0;
    double finalF1 = 0.0;
    for (String docID : docIDToText.keySet()) {
      System.out.println("---");
      System.out.println(docID);
      Annotation currAnnotation = new Annotation(docIDToText.get(docID));
      pipeline.annotate(currAnnotation);
      // increment number of seen gold relations
      int docGoldRelationSetSize = 0;
      if (docIDToRelations.get(docID) != null) {
        docGoldRelationSetSize = docIDToRelations.get(docID).size();
      }
      totalGoldRelations += docGoldRelationSetSize;
      ArrayList<RelationTriple> relationTriplesForThisDoc = new ArrayList<>();
      for (CoreMap sentence : currAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
        List<RelationTriple> rtList = sentence.get(CoreAnnotations.KBPTriplesAnnotation.class);
        for (RelationTriple rt : rtList) {
          System.out.println("\t"+rt.toString());
          relationTriplesForThisDoc.add(rt);
        }
      }
      System.out.println();
      System.out.println("gold relations: ");
      if (docIDToRelations.get(docID) != null) {
        for (String goldRelation : docIDToRelations.get(docID)) {
          System.out.println("\t" + goldRelation);
        }
      }
      Set<String> foundRelationStrings = convertKBPTriplesToStrings(relationTriplesForThisDoc);
      HashSet<String> intersectionOfFoundAndGold = new HashSet<>(foundRelationStrings);
      if (docIDToRelations.get(docID) != null) {
        intersectionOfFoundAndGold.retainAll(docIDToRelations.get(docID));
        totalCorrectFoundRelations += (intersectionOfFoundAndGold.size());
        totalWrongFoundRelations += (foundRelationStrings.size()-intersectionOfFoundAndGold.size());
      } else {
        totalWrongFoundRelations += foundRelationStrings.size();
      }
      System.out.println();
      List<CoreMap> sentences = currAnnotation.get(CoreAnnotations.SentencesAnnotation.class);
      System.out.println(sentences.get(0).get(CoreAnnotations.TextAnnotation.class)+"\n");
      if (sentences.get(0).get(TreeCoreAnnotations.TreeAnnotation.class) != null) {
        System.out.println("Constituency parse: ");
        System.out.println(sentences.get(0).get(TreeCoreAnnotations.TreeAnnotation.class).pennString());
      }
      System.out.println("Dependency parse: ");
      System.out.println(sentences.get(0).get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class).toList());
      
      System.out.println("");
      System.out.println("correct: "+intersectionOfFoundAndGold.size());
      System.out.println("wrong: "+(foundRelationStrings.size()-intersectionOfFoundAndGold.size()));
      System.out.println();
      totalGuessRelations += foundRelationStrings.size();
      System.out.println("curr score: ");
      System.out.println("\ttotal correct: "+totalCorrectFoundRelations);
      System.out.println("\ttotal wrong: "+totalWrongFoundRelations);
      System.out.println("\ttotal guesses: "+totalGuessRelations);
      System.out.println("\ttotal gold relations: "+totalGoldRelations);
      double recall = (((double) totalCorrectFoundRelations)/((double) totalGoldRelations));
      double precision = (((double) totalCorrectFoundRelations)/((double) totalGuessRelations));
      System.out.println("\trecall: "+recall);
      System.out.println("\tprecision: "+precision);
      double f1 = (2 * (precision * recall))/(precision + recall);
      System.out.println("\tf1: "+f1);
      finalF1 = f1;
    }
    // check final F1 score is
    assertTrue("f1 score: " + finalF1 +" is below threshold of "+KBP_MINIMUM_SCORE
            , finalF1 >= KBP_MINIMUM_SCORE);
  }
}
