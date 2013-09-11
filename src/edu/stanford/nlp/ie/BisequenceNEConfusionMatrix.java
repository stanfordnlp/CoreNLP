package edu.stanford.nlp.ie;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ie.BisequenceEmpiricalNERPrior.Entity;

import java.io.*;
import java.util.*;


/**
 * @author Mengqiu Wang
 */

public class BisequenceNEConfusionMatrix {

  private static int backgroundSymbolIndex;
  private static Index<String> tagIndex = new HashIndex<String>();
  private static Index<String> classIndex = new HashIndex<String>();
  private static int[][] entityMatrix, subEntityMatrix;

  public static void main(String[] args) throws Exception {
    String fileName = args[0];
    String[] tags = new String[]{"LOC", "ORG", "PERSON", "GPE", "O"};
    tagIndex.addAll(Arrays.asList(tags));

    String[] classes = new String[]{"O","B-PERSON", "B-GPE", "I-GPE", "B-ORG", "I-ORG", "I-PERSON", "B-LOC", "I-LOC"};
    classIndex.addAll(Arrays.asList(classes));
    int backgroundSymbolIndex = classIndex.indexOf("O");

    entityMatrix = new int[tags.length-1][tags.length-1];
    for (int i=0; i < entityMatrix.length; i++) {
      entityMatrix[i] = new int[tags.length-1];
    }
    subEntityMatrix = new int[tags.length-1][tags.length-1];
    for (int i=0; i < subEntityMatrix.length; i++) {
      subEntityMatrix[i] = new int[tags.length-1];
    }

    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName))));
    String line = null;
    List<String> wordDoc = new ArrayList<String>();
    List<Integer> sequenceList = new ArrayList<Integer>();
    while ( (line = br.readLine()) != null ) {
      line = line.trim();
      if (line.length() == 0) { // end of a Doc
        int[] listOfInt = new int[sequenceList.size()];
        for (int i=0; i < sequenceList.size(); i++) 
          listOfInt[i] = sequenceList.get(i);
        count(listOfInt, wordDoc);
        sequenceList.clear();
        wordDoc.clear();
      } else {
        String[] parts = line.split("\t");
        wordDoc.add(parts[0]);
        String tag = parts[1];
        sequenceList.add(classIndex.indexOf(tag));
      }
    }
    if (wordDoc.size() > 0) {
      int[] listOfInt = new int[sequenceList.size()];
      for (int i=0; i < sequenceList.size(); i++) 
        listOfInt[i] = sequenceList.get(i);
      count(listOfInt, wordDoc);
    }
    for (int i=0; i < entityMatrix.length; i++) {
      StringBuilder sb = new StringBuilder();
      for (int j=0; j < entityMatrix[i].length; j++) {
        sb.append(tagIndex.get(i));
        sb.append(":");
        sb.append(tagIndex.get(j));
        sb.append(" ");
        sb.append(entityMatrix[i][j]);
        sb.append("\t");
      }
      System.out.println(sb.toString().trim());
    }
    for (int i=0; i < subEntityMatrix.length; i++) {
      StringBuilder sb = new StringBuilder();
      for (int j=0; j < subEntityMatrix[i].length; j++) {
        sb.append(tagIndex.get(i));
        sb.append(":");
        sb.append(tagIndex.get(j));
        sb.append(" ");
        sb.append(subEntityMatrix[i][j]);
        sb.append("\t");
      }
      System.out.println(sb.toString().trim());
    }
  }

  private static void count(int[] sequence, List<String> wordDoc) {
    List<Entity> entities = BisequenceEmpiricalNERPrior.extractEntities(sequence, wordDoc, tagIndex, classIndex, backgroundSymbolIndex);
    for (Entity curr: entities) {
      int tag1 = curr.type;
      for (Entity match: curr.exactMatch) {
        int tag2 = match.type;
        entityMatrix[tag1][tag2]++;
        entityMatrix[tag2][tag1]++;
      }
      for (Entity match: curr.subMatch) {
        int tag2 = match.type;
        subEntityMatrix[tag1][tag2]++;
      }
    }
  }
}
