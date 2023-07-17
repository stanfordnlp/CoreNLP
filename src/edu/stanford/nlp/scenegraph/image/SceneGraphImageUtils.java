package edu.stanford.nlp.scenegraph.image;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonString;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasLemma;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalStructure;
import edu.stanford.nlp.util.Generics;

public class SceneGraphImageUtils {

  public static final String SEPARATOR = "|||";
  public static final String SEPARATOR_PATTERN = "\\|\\|\\|";

  public static CoreLabel labelFromString(String str) {
    CoreLabel label = new CoreLabel();

    String[] parts = str.split(SEPARATOR_PATTERN);
    for (String part : parts) {
      if (part.startsWith("word:::")) {
        label.setWord(part.substring("word:::".length()));
        label.setValue(part.substring("word:::".length()));
      } else if (part.startsWith("tag:::")) {
        label.setTag(part.substring("tag:::".length()));
      } else if (part.startsWith("lemma:::")) {
        label.setLemma(part.substring("lemma:::".length()));
      } else if (part.startsWith("idx:::")) {
        label.setIndex(Integer.parseInt(part.substring("idx:::".length())));
      } else if (part.startsWith("num:::")) {
        label.set(CoreAnnotations.NumericValueAnnotation.class,
            Double.parseDouble(part.substring("num:::".length())));
      }
    }
    return label;
  }

  public static String labelToString(CoreLabel label) {
    StringBuffer sb = new StringBuffer();
    boolean first = true;

    if (label.word() != null) {
      sb.append("word:::").append(label.word());
      first = false;
    }

    if (label.tag() != null) {
      if ( ! first) sb.append(SEPARATOR);
      sb.append("tag:::").append(label.tag());
      first = false;
    }

    if (label.index() > 0) {
      if ( ! first) sb.append(SEPARATOR);
      sb.append("idx:::").append(label.index());
      first = false;
    }

    if (label.lemma() != null) {
      if ( ! first) sb.append(SEPARATOR);
      sb.append("lemma:::").append(label.lemma());
      first = false;
    }

    if (label.containsKey(CoreAnnotations.NumericValueAnnotation.class)) {
      if ( ! first) sb.append(SEPARATOR);
      sb.append("num:::").append(label.get(CoreAnnotations.NumericValueAnnotation.class));
      first = false;
    }

    return sb.toString();
  }

  public static boolean containsLemma(Iterable<? extends HasLemma> list, String lemma) {
    for (HasLemma lbl : list) {
      if (lbl.lemma() != null && lbl.lemma().equals(lemma)) {
        return true;
      }
    }
    return false;
  }

  public static boolean containsWord(Iterable<? extends HasWord> list, String word) {
    for (HasWord lbl : list) {
      if (lbl.word() != null && lbl.word().equals(word)) {
        return true;
      }
    }
    return false;
  }

  public static List<IndexedWord> findWord(List<IndexedWord> tokens, String word) {
    List<IndexedWord> result = Generics.newArrayList();
    for (IndexedWord lbl : tokens) {
      if (lbl.word() != null && lbl.word().equals(word)) {
        result.add(lbl);
      }
    }
    return result;
  }

  public static List<IndexedWord> findLemma(List<IndexedWord> tokens, String lemma) {
    List<IndexedWord> result = Generics.newArrayList();
    for (IndexedWord lbl : tokens) {
      if (lbl.lemma() != null && lbl.lemma().equals(lemma)) {
        result.add(lbl);
      }
    }
    return result;
  }

  public static GrammaticalStructure getSemanticGraph(JsonArray depTriplets, List<CoreLabel> tokens) {
    List<TypedDependency> dependencies = Generics.newArrayList(depTriplets.size());

    Map<Integer, IndexedWord> idx2Node = Generics.newHashMap(depTriplets.size());

    IndexedWord root = new IndexedWord(new Word("ROOT"));
    root.set(CoreAnnotations.IndexAnnotation.class, 0);

    for (JsonString depTripletRaw : depTriplets.getValuesAs(JsonString.class)) {
      String depTriplet = depTripletRaw.getString();
      String parts[] = depTriplet.split(SEPARATOR_PATTERN);
      int depIdx = Integer.parseInt(parts[0]);
      int govIdx = Integer.parseInt(parts[1]);
      String relnName = parts[2];

      IndexedWord dep = idx2Node.get(depIdx);
      IndexedWord gov = govIdx == 0 ? root : idx2Node.get(govIdx);

      if (dep == null) {
        dep = new IndexedWord(tokens.get(depIdx - 1));
        idx2Node.put(depIdx, dep);
      }

      if (gov == null) {
        gov = new IndexedWord(tokens.get(govIdx - 1));
        idx2Node.put(govIdx, gov);
      }

      GrammaticalRelation reln = govIdx == 0 ? GrammaticalRelation.ROOT :
          GrammaticalRelation.valueOf(Language.UniversalEnglish, relnName);
      TypedDependency td = new TypedDependency(reln, gov, dep);
      dependencies.add(td);
    }

    TreeGraphNode rootNode = new TreeGraphNode(root);
    GrammaticalStructure gs = new UniversalEnglishGrammaticalStructure(dependencies, rootNode);

    return gs;
  }


  @SuppressWarnings("unchecked")
  public static JsonArray grammaticalStructureToJSON(GrammaticalStructure gs) {
    JsonArrayBuilder arr = Json.createArrayBuilder();
    for (TypedDependency td : gs.typedDependencies()) {
      arr.add(String.format("%d%s%d%s%s", td.dep().index(), SEPARATOR, td.gov().index(), SEPARATOR, td.reln().getShortName()));
    }
    return arr.build();
  }

  /**
   * A utility method for javax json: extract a list of strings from the object
   */
  public static List<String> getJsonStringList(JsonObject obj, String name) {
    JsonArray strArray = obj.getJsonArray(name);
    List<String> strList = new ArrayList<>(strArray.size());
    for (JsonString str : strArray.getValuesAs(JsonString.class)) {
      strList.add(str.getString());
    }
    return strList;
  }
}
