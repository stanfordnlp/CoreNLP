package edu.stanford.nlp.ie.machinereading;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.stanford.nlp.ie.machinereading.structure.*;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.GenderAnnotation;
import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations.TriggerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.SemanticGraphFactory.Mode;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

// XXX convert to BasicRelationFeatureFactory, make RelationFeatureFactory an interface


/**
 *  @author Mason Smith
 *  @author Mihai Surdeanu
 */
public class BasicRelationFeatureFactory extends RelationFeatureFactory implements Serializable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(BasicRelationFeatureFactory.class);
  private static final long serialVersionUID = -7376668998622546620L;

  private static final Logger logger = Logger.getLogger(BasicRelationFeatureFactory.class.getName());


  protected static final List<String> dependencyFeatures = Collections.unmodifiableList(Arrays.asList(
          "dependency_path_lowlevel","dependency_path_length","dependency_path_length_binary",
          "verb_in_dependency_path","dependency_path","dependency_path_words","dependency_paths_to_verb",
          "dependency_path_stubs_to_verb",
          "dependency_path_POS_unigrams",
          "dependency_path_word_n_grams",
          "dependency_path_POS_n_grams",
          "dependency_path_edge_n_grams","dependency_path_edge_lowlevel_n_grams",
          "dependency_path_edge-node-edge-grams","dependency_path_edge-node-edge-grams_lowlevel",
          "dependency_path_node-edge-node-grams","dependency_path_node-edge-node-grams_lowlevel",
          "dependency_path_directed_bigrams",
          "dependency_path_edge_unigrams",
          "dependency_path_trigger"
  ));

  protected List<String> featureList;



  public BasicRelationFeatureFactory(String... featureList) {
    this.doNotLexicalizeFirstArg = false;
    this.dependencyType = DEPENDENCY_TYPE.COLLAPSED_CCPROCESSED;
    this.featureList = Collections.unmodifiableList(Arrays.asList(featureList));
  }

  static {
    logger.setLevel(Level.INFO);
  }


  public Datum<String,String> createDatum(RelationMention rel) {
    return createDatum(rel, (Logger) null);
  }

  public Datum<String,String> createDatum(RelationMention rel, Logger logger) {
    Counter<String> features = new ClassicCounter<>();
    if (rel.getArgs().size() != 2) {
      return null;
    }

    addFeatures(features, rel, featureList, logger);

    String labelString = rel.getType();
    return new RVFDatum<>(features, labelString);
  }

  @Override
  public Datum<String, String> createTestDatum(RelationMention rel, Logger logger) {
    return createDatum(rel, logger);
  }

  public Datum<String,String> createDatum(RelationMention rel, String positiveLabel) {
    Counter<String> features = new ClassicCounter<>();
    if (rel.getArgs().size() != 2) {
      return null;
    }

    addFeatures(features, rel, featureList);

    String labelString = rel.getType();
    if(! labelString.equals(positiveLabel)) labelString = RelationMention.UNRELATED;
    return new RVFDatum<>(features, labelString);
  }

  public boolean addFeatures(Counter<String> features, RelationMention rel, List<String> types) {
    return addFeatures(features, rel, types, null);
  }

  /**
   * Creates all features for the datum corresponding to this relation mention
   * Note: this assumes binary relations where both arguments are EntityMention
   * @param features Stores all features
   * @param rel The mention
   * @param types Comma separated list of feature classes to use
   */
  public boolean addFeatures(Counter<String> features, RelationMention rel, List<String> types, Logger logger) {
    // sanity checks: must have two arguments, and each must be an entity mention
    if(rel.getArgs().size() != 2) return false;
    if(! (rel.getArg(0) instanceof EntityMention)) return false;
    if(! (rel.getArg(1) instanceof EntityMention)) return false;

    EntityMention arg0 = (EntityMention) rel.getArg(0);
    EntityMention arg1 = (EntityMention) rel.getArg(1);

    Tree tree = rel.getSentence().get(TreeAnnotation.class);
    if(tree == null){
      throw new RuntimeException("ERROR: Relation extraction requires full syntactic analysis!");
    }
    List<Tree> leaves = tree.getLeaves();
    List<CoreLabel> tokens = rel.getSentence().get(TokensAnnotation.class);

    // this assumes that both args are in the same sentence as the relation object
    // let's check for this to be safe
    CoreMap relSentence = rel.getSentence();
    CoreMap arg0Sentence = arg0.getSentence();
    CoreMap arg1Sentence = arg1.getSentence();
    if(arg0Sentence != relSentence){
      log.info("WARNING: Found relation with arg0 in a different sentence: " + rel);
      log.info("Relation sentence: " + relSentence.get(TextAnnotation.class));
      log.info("Arg0 sentence: " + arg0Sentence.get(TextAnnotation.class));
      return false;
    }
    if(arg1Sentence != relSentence){
      log.info("WARNING: Found relation with arg1 in a different sentence: " + rel);
      log.info("Relation sentence: " + relSentence.get(TextAnnotation.class));
      log.info("Arg1 sentence: " + arg1Sentence.get(TextAnnotation.class));
      return false;
    }

    // Checklist keeps track of which features have been handled by an if clause
    // Should be empty after all the clauses have been gone through.
    List<String> checklist = new ArrayList<>(types);

    // arg_type: concatenation of the entity types of the args, e.g.
    // "arg1type=Loc_and_arg2type=Org"
    // arg_subtype: similar, for entity subtypes
    if (usingFeature(types, checklist, "arg_type")) {
      features.setCount("arg1type=" + arg0.getType() + "_and_arg2type=" + arg1.getType(), 1.0);
    }
    if (usingFeature(types,checklist,"arg_subtype")) {
      features.setCount("arg1subtype="+arg0.getSubType()+"_and_arg2subtype="+arg1.getSubType(),1.0);
    }

    // arg_order: which arg comes first in the sentence
    if (usingFeature(types, checklist, "arg_order")) {
      if (arg0.getSyntacticHeadTokenPosition() < arg1.getSyntacticHeadTokenPosition())
        features.setCount("arg1BeforeArg2", 1.0);
    }
    // same_head: whether the two args share the same syntactic head token
    if (usingFeature(types, checklist, "same_head")) {
      if (arg0.getSyntacticHeadTokenPosition() == arg1.getSyntacticHeadTokenPosition())
        features.setCount("arguments_have_same_head",1.0);
    }

    // full_tree_path: Path from one arg to the other in the phrase structure tree,
    // e.g., NNP -> PP -> NN <- NNP
    if (usingFeature(types, checklist, "full_tree_path")) {
      //log.info("ARG0: " + arg0);
      //log.info("ARG0 HEAD: " + arg0.getSyntacticHeadTokenPosition());
      //log.info("TREE: " + tree);
      //log.info("SENTENCE: " + sentToString(arg0.getSentence()));
      if(arg0.getSyntacticHeadTokenPosition() < leaves.size() && arg1.getSyntacticHeadTokenPosition() < leaves.size()){
        Tree arg0preterm = leaves.get(arg0.getSyntacticHeadTokenPosition()).parent(tree);
        Tree arg1preterm = leaves.get(arg1.getSyntacticHeadTokenPosition()).parent(tree);
        Tree join = tree.joinNode(arg0preterm, arg1preterm);
        StringBuilder pathStringBuilder = new StringBuilder();
        List<Tree> pathUp = join.dominationPath(arg0preterm);
        Collections.reverse(pathUp);
        for (Tree node : pathUp) {
          if (node != join) {
            pathStringBuilder.append(node.label().value() + " <- ");
          }
        }

        for (Tree node : join.dominationPath(arg1preterm)) {
          pathStringBuilder.append(((node == join) ? "" : " -> ") + node.label().value());
        }
        String pathString = pathStringBuilder.toString();
        if(logger != null && ! rel.getType().equals(RelationMention.UNRELATED)) logger.info("full_tree_path: " + pathString);
        features.setCount("treepath:"+pathString, 1.0);
      } else {
        log.info("WARNING: found weird argument offsets. Most likely because arguments appear in different sentences than the relation:");
        log.info("ARG0: " + arg0);
        log.info("ARG0 HEAD: " + arg0.getSyntacticHeadTokenPosition());
        log.info("ARG0 SENTENCE: " + sentToString(arg0.getSentence()));
        log.info("ARG1: " + arg1);
        log.info("ARG1 HEAD: " + arg1.getSyntacticHeadTokenPosition());
        log.info("ARG1 SENTENCE: " + sentToString(arg1.getSentence()));
        log.info("RELATION TREE: " + tree);
      }
    }

    int pathLength = tree.pathNodeToNode(tree.getLeaves().get(arg0.getSyntacticHeadTokenPosition()),
            tree.getLeaves().get(arg1.getSyntacticHeadTokenPosition())).size();
    // path_length: Length of the path in the phrase structure parse tree, integer-valued feature
    if (usingFeature(types, checklist, "path_length")) {
      features.setCount("path_length", pathLength);
    }
    // path_length_binary: Length of the path in the phrase structure parse tree, binary features
    if (usingFeature(types, checklist, "path_length_binary")) {
      features.setCount("path_length_" + pathLength, 1.0);
    }

    /* entity_order
           * This tells you for each of the two args
           * whether there are other entities before or after that arg.
           * In particular, it can tell whether an arg is the first entity of its type in the sentence
           * (which can be useful for example for telling the gameWinner and gameLoser in NFL).
           * TODO: restrict this feature so that it only looks for
           * entities of the same type?
           * */
    if (usingFeature(types, checklist, "entity_order")) {
      for (int i = 0; i < rel.getArgs().size(); i++) {
        // We already checked the class of the args at the beginning of the method
        EntityMention arg = (EntityMention) rel.getArgs().get(i);
        if(rel.getSentence().get(MachineReadingAnnotations.EntityMentionsAnnotation.class) != null) { // may be null due to annotation error
          for (EntityMention otherArg : rel.getSentence().get(MachineReadingAnnotations.EntityMentionsAnnotation.class)) {
            String feature;
            if (otherArg.getSyntacticHeadTokenPosition() > arg.getSyntacticHeadTokenPosition()) {
              feature = "arg" + i + "_before_" + otherArg.getType();
              features.setCount(feature, 1.0);
            }
            if (otherArg.getSyntacticHeadTokenPosition() < arg.getSyntacticHeadTokenPosition()) {
              feature = "arg" + i + "_after_" + otherArg.getType();
              features.setCount(feature, 1.0);
            }
          }
        }
      }
    }

    // surface_distance: Number of tokens in the sentence between the two words, integer-valued feature
    int surfaceDistance = Math.abs(arg0.getSyntacticHeadTokenPosition() - arg1.getSyntacticHeadTokenPosition());
    if (usingFeature(types, checklist, "surface_distance")) {
      features.setCount("surface_distance", surfaceDistance);
    }
    // surface_distance_binary: Number of tokens in the sentence between the two words, binary features
    if (usingFeature(types, checklist, "surface_distance_binary")) {
      features.setCount("surface_distance_" + surfaceDistance, 1.0);
    }
    // surface_distance_bins: number of tokens between the two args, binned to several intervals
    if(usingFeature(types, checklist, "surface_distance_bins")) {
      if(surfaceDistance < 4){
        features.setCount("surface_distance_bin" + surfaceDistance, 1.0);
      } else if(surfaceDistance < 6){
        features.setCount("surface_distance_bin_lt6", 1.0);
      } else if(surfaceDistance < 10) {
        features.setCount("surface_distance_bin_lt10", 1.0);
      } else {
        features.setCount("surface_distance_bin_ge10", 1.0);
      }
    }

    // separate_surface_windows: windows of 1,2,3 tokens before and after args, for each arg separately
    // Separate features are generated for windows to the left and to the right of the args.
    // Features are concatenations of words in the window (or NULL for sentence boundary).
    //
    // conjunction_surface_windows: concatenation of the windows of the two args
    //
    // separate_surface_windows_POS: windows of POS tags of size 1,2,3 for each arg
    //
    // conjunction_surface_windows_POS: concatenation of windows of the args

    List<EntityMention> args = new ArrayList<>();
    args.add(arg0); args.add(arg1);
    for (int windowSize = 1; windowSize <= 3; windowSize++) {

      String[] leftWindow, rightWindow, leftWindowPOS, rightWindowPOS;
      leftWindow = new String[2];
      rightWindow = new String[2];
      leftWindowPOS = new String[2];
      rightWindowPOS = new String[2];

      for (int argn = 0; argn <= 1; argn++) {
        int ind = args.get(argn).getSyntacticHeadTokenPosition();
        for (int winnum = 1; winnum <= windowSize; winnum++) {
          int windex = ind - winnum;
          if (windex > 0) {
            leftWindow[argn] = leaves.get(windex).label().value() + "_" + leftWindow[argn];
            leftWindowPOS[argn] = leaves.get(windex).parent(tree).label().value() + "_" + leftWindowPOS[argn];
          } else {
            leftWindow[argn] = "NULL_" + leftWindow[argn];
            leftWindowPOS[argn] = "NULL_" + leftWindowPOS[argn];
          }
          windex = ind + winnum;
          if (windex < leaves.size()) {
            rightWindow[argn] = rightWindow[argn] + "_" + leaves.get(windex).label().value();
            rightWindowPOS[argn] = rightWindowPOS[argn] + "_" + leaves.get(windex).parent(tree).label().value();
          } else {
            rightWindow[argn] = rightWindow[argn] + "_NULL";
            rightWindowPOS[argn] = rightWindowPOS[argn] + "_NULL";
          }
        }
        if (usingFeature(types, checklist, "separate_surface_windows")) {
          features.setCount("left_window_"+windowSize+"_arg_" + argn + ": " + leftWindow[argn], 1.0);
          features.setCount("left_window_"+windowSize+"_POS_arg_" + argn + ": " + leftWindowPOS[argn], 1.0);
        }
        if (usingFeature(types, checklist, "separate_surface_windows_POS")) {
          features.setCount("right_window_"+windowSize+"_arg_" + argn + ": " + rightWindow[argn], 1.0);
          features.setCount("right_window_"+windowSize+"_POS_arg_" + argn + ": " + rightWindowPOS[argn], 1.0);
        }

      }
      if (usingFeature(types, checklist, "conjunction_surface_windows")) {
        features.setCount("left_windows_"+windowSize+": " + leftWindow[0] + "__" + leftWindow[1], 1.0);
        features.setCount("right_windows_"+windowSize+": " + rightWindow[0] + "__" + rightWindow[1], 1.0);
      }
      if (usingFeature(types, checklist, "conjunction_surface_windows_POS")) {
        features.setCount("left_windows_"+windowSize+"_POS: " + leftWindowPOS[0] + "__" + leftWindowPOS[1], 1.0);
        features.setCount("right_windows_"+windowSize+"_POS: " + rightWindowPOS[0] + "__" + rightWindowPOS[1], 1.0);
      }
    }

    // arg_words:  The actual arg tokens as separate features, and concatenated
    String word0 = leaves.get(arg0.getSyntacticHeadTokenPosition()).label().value();
    String word1 = leaves.get(arg1.getSyntacticHeadTokenPosition()).label().value();
    if (usingFeature(types, checklist, "arg_words")) {
      if(doNotLexicalizeFirstArg == false)
        features.setCount("word_arg0: " + word0, 1.0);
      features.setCount("word_arg1: " + word1, 1.0);
      if(doNotLexicalizeFirstArg == false)
        features.setCount("words: " + word0 + "__" + word1, 1.0);
    }

    // arg_POS:  POS tags of the args, as separate features and concatenated
    String pos0 = leaves.get(arg0.getSyntacticHeadTokenPosition()).parent(tree).label().value();
    String pos1 = leaves.get(arg1.getSyntacticHeadTokenPosition()).parent(tree).label().value();
    if (usingFeature(types, checklist, "arg_POS")) {
      features.setCount("POS_arg0: " + pos0, 1.0);
      features.setCount("POS_arg1: " + pos1, 1.0);
      features.setCount("POSs: " + pos0 + "__" + pos1, 1.0);
    }

    // adjacent_words: words immediately to the left and right of the args
    if(usingFeature(types, checklist, "adjacent_words")){
      for(int i = 0; i < rel.getArgs().size(); i ++){
        Span s = ((EntityMention) rel.getArg(i)).getHead();
        if(s.start() > 0){
          String v = tokens.get(s.start() - 1).word();
          features.setCount("leftarg" + i + "-" + v, 1.0);
        }
        if(s.end() < tokens.size()){
          String v = tokens.get(s.end()).word();
          features.setCount("rightarg" + i + "-" + v, 1.0);
        }
      }
    }

    // entities_between_args:  binary feature for each type specifying whether there is an entity of that type in the sentence
    // between the two args.
    // e.g. "entity_between_args: Loc" means there is at least one entity of type Loc between the two args
    if (usingFeature(types, checklist, "entities_between_args")) {
      CoreMap sent = rel.getSentence();
      if(sent == null) throw new RuntimeException("NULL sentence for relation " + rel);
      List<EntityMention> relArgs = sent.get(MachineReadingAnnotations.EntityMentionsAnnotation.class);
      if(relArgs != null) { // may be null due to annotation errors!
        for (EntityMention arg : relArgs) {
          if ((arg.getSyntacticHeadTokenPosition() > arg0.getSyntacticHeadTokenPosition() && arg.getSyntacticHeadTokenPosition() < arg1.getSyntacticHeadTokenPosition())
                  || (arg.getSyntacticHeadTokenPosition() > arg1.getSyntacticHeadTokenPosition() && arg.getSyntacticHeadTokenPosition() < arg0.getSyntacticHeadTokenPosition())) {
            features.setCount("entity_between_args: " + arg.getType(), 1.0);
          }
        }
      }
    }

    // entity_counts: For each type, the total number of entities of that type in the sentence (integer-valued feature)
    // entity_counts_binary: Counts of entity types as binary features.
    Counter<String> typeCounts = new ClassicCounter<>();
    if(rel.getSentence().get(MachineReadingAnnotations.EntityMentionsAnnotation.class) != null){ // may be null due to annotation errors!
      for (EntityMention arg : rel.getSentence().get(MachineReadingAnnotations.EntityMentionsAnnotation.class))
        typeCounts.incrementCount(arg.getType());
      for (String type : typeCounts.keySet()) {
        if (usingFeature(types,checklist,"entity_counts"))
          features.setCount("entity_counts_"+type,typeCounts.getCount(type));
        if (usingFeature(types,checklist,"entity_counts_binary"))
          features.setCount("entity_counts_"+type+": "+typeCounts.getCount(type),1.0);
      }
    }

    // surface_path: concatenation of tokens between the two args
    // surface_path_POS: concatenation of POS tags between the args
    // surface_path_selective: concatenation of tokens between the args which are nouns or verbs
    StringBuilder sb = new StringBuilder();
    StringBuilder sbPOS = new StringBuilder();
    StringBuilder sbSelective = new StringBuilder();
    for (int i = Math.min(arg0.getSyntacticHeadTokenPosition(), arg1.getSyntacticHeadTokenPosition()) + 1; i < Math.max(arg0.getSyntacticHeadTokenPosition(), arg1.getSyntacticHeadTokenPosition()); i++) {
      String word = leaves.get(i).label().value();
      sb.append(word + "_");
      String pos = leaves.get(i).parent(tree).label().value();
      sbPOS.append(pos + "_");
      if (pos.equals("NN") || pos.equals("NNS") || pos.equals("NNP") || pos.equals("NNPS") || pos.equals("VB")
              || pos.equals("VBN") || pos.equals("VBD") || pos.equals("VBG") || pos.equals("VBP") || pos.equals("VBZ")) {
        sbSelective.append(word + "_");
      }
    }
    if (usingFeature(types, checklist, "surface_path")) {
      features.setCount("surface_path: " + sb, 1.0);
    }
    if (usingFeature(types, checklist, "surface_path_POS")) {
      features.setCount("surface_path_POS: " + sbPOS, 1.0);
    }
    if (usingFeature(types, checklist, "surface_path_selective")) {
      features.setCount("surface_path_selective: " + sbSelective, 1.0);
    }

    int swStart, swEnd; // must be initialized below
    if (arg0.getSyntacticHeadTokenPosition() < arg1.getSyntacticHeadTokenPosition()){
      swStart = arg0.getExtentTokenEnd();
      swEnd = arg1.getExtentTokenStart();
    } else {
      swStart = arg1.getExtentTokenEnd();
      swEnd = arg0.getExtentTokenStart();
    }

    // span_words_unigrams: words that appear in between the two arguments
    if (usingFeature(types, checklist, "span_words_unigrams")) {
      for(int i = swStart; i < swEnd; i ++){
        features.setCount("span_word:" + tokens.get(i).word(), 1.0);
      }
    }

    // span_words_bigrams: bigrams of words that appear in between the two arguments
    if (usingFeature(types, checklist, "span_words_bigrams")) {
      for(int i = swStart; i < swEnd - 1; i ++){
        features.setCount("span_bigram:" + tokens.get(i).word() + "-" + tokens.get(i + 1).word(), 1.0);
      }
    }

    if (usingFeature(types, checklist, "span_words_trigger")) {
      for (int i = swStart; i < swEnd; i++) {
        String trigger = tokens.get(i).get(TriggerAnnotation.class);
        if (trigger != null && trigger.startsWith("B-"))
          features.incrementCount("span_words_trigger=" + trigger.substring(2));
      }
    }

    if (usingFeature(types, checklist, "arg2_number")) {
      if (arg1.getType().equals("NUMBER")){
        try {
          int value = Integer.parseInt(arg1.getValue());

          if (2 <= value && value <= 100)
            features.setCount("arg2_number", 1.0);
          if (2 <= value && value <= 19)
            features.setCount("arg2_number_2", 1.0);
          if (20 <= value && value <= 59)
            features.setCount("arg2_number_20", 1.0);
          if (60 <= value && value <= 100)
            features.setCount("arg2_number_60", 1.0);
          if (value >= 100)
            features.setCount("arg2_number_100", 1.0);
        } catch (NumberFormatException e) {}
      }
    }

    if (usingFeature(types, checklist, "arg2_date")) {
      if (arg1.getType().equals("DATE")){
        try {
          int value = Integer.parseInt(arg1.getValue());

          if (0 <= value && value <= 2010)
            features.setCount("arg2_date", 1.0);
          if (0 <= value && value <= 999)
            features.setCount("arg2_date_0", 1.0);
          if (1000 <= value && value <= 1599)
            features.setCount("arg2_date_1000", 1.0);
          if (1600 <= value && value <= 1799)
            features.setCount("arg2_date_1600", 1.0);
          if (1800 <= value && value <= 1899)
            features.setCount("arg2_date_1800", 1.0);
          if (1900 <= value && value <= 1999)
            features.setCount("arg2_date_1900", 1.0);
          if (value >= 2000)
            features.setCount("arg2_date_2000", 1.0);
        } catch (NumberFormatException e) {}
      }
    }

    if (usingFeature(types, checklist, "arg_gender")) {
      boolean arg0Male = false, arg0Female = false;
      boolean arg1Male = false, arg1Female = false;
      System.out.println("Adding gender annotations!");

      int index = arg0.getExtentTokenStart();
      String gender = tokens.get(index).get(GenderAnnotation.class);
      System.out.println(tokens.get(index).word() + " -- " + gender);
      if (gender.equals("MALE"))
        arg0Male = true;
      else if (gender.equals("FEMALE"))
        arg0Female = true;

      index = arg1.getExtentTokenStart();
      gender = tokens.get(index).get(GenderAnnotation.class);
      if (gender.equals("MALE"))
        arg1Male = true;
      else if (gender.equals("FEMALE"))
        arg1Female = true;

      if (arg0Male) features.setCount("arg1_male", 1.0);
      if (arg0Female) features.setCount("arg1_female", 1.0);
      if (arg1Male) features.setCount("arg2_male", 1.0);
      if (arg1Female) features.setCount("arg2_female", 1.0);

      if ((arg0Male && arg1Male) || (arg0Female && arg1Female))
        features.setCount("arg_same_gender", 1.0);
      if ((arg0Male && arg1Female) || (arg0Female && arg1Male))
        features.setCount("arg_different_gender", 1.0);
    }

    List<String> tempDepFeatures = new ArrayList<>(dependencyFeatures);
    if (tempDepFeatures.removeAll(types) || types.contains("all")) { // dependencyFeatures contains at least one of the features listed in types
      addDependencyPathFeatures(features, rel, arg0, arg1, types, checklist, logger);
    }

    if (!checklist.isEmpty() && !checklist.contains("all"))
      throw new AssertionError("RelationFeatureFactory: features not handled: "+checklist);


    List<String> featureList = new ArrayList<>(features.keySet());
    Collections.sort(featureList);

//    for (String feature : featureList) {
//      logger.info(feature+"\n"+"count="+features.getCount(feature));
//    }

    return true;

  }

  String sentToString(CoreMap sentence) {
    StringBuilder os = new StringBuilder();
    List<CoreLabel> tokens = sentence.get(TokensAnnotation.class);
    if(tokens != null){
      boolean first = true;
      for(CoreLabel token: tokens) {
        if(! first) os.append(" ");
        os.append(token.word());
        first = false;
      }
    }

    return os.toString();
  }

  protected void addDependencyPathFeatures(
          Counter<String> features,
          RelationMention rel,
          EntityMention arg0,
          EntityMention arg1,
          List<String> types,
          List<String> checklist,
          Logger logger) {
    SemanticGraph graph = null;
    if(dependencyType == null) dependencyType = DEPENDENCY_TYPE.COLLAPSED_CCPROCESSED; // needed for backwards compatibility. old serialized models don't have it
    if(dependencyType == DEPENDENCY_TYPE.COLLAPSED_CCPROCESSED)
      graph = rel.getSentence().get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    else if(dependencyType == DEPENDENCY_TYPE.COLLAPSED)
      graph = rel.getSentence().get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
    else if(dependencyType == DEPENDENCY_TYPE.BASIC)
      graph = rel.getSentence().get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    else
      throw new RuntimeException("ERROR: unknown dependency type: " + dependencyType);

    if (graph == null) {
      Tree tree = rel.getSentence().get(TreeAnnotation.class);
      if(tree == null){
        log.info("WARNING: found sentence without TreeAnnotation. Skipped dependency-path features.");
        return;
      }
      try {
        graph = SemanticGraphFactory.makeFromTree(tree, Mode.COLLAPSED, GrammaticalStructure.Extras.NONE, null, true);

      } catch(Exception e){
        log.info("WARNING: failed to generate dependencies from tree " + tree.toString());
        e.printStackTrace();
        log.info("Skipped dependency-path features.");
        return;
      }
    }

    IndexedWord node0 = graph.getNodeByIndexSafe(arg0.getSyntacticHeadTokenPosition() + 1);
    IndexedWord node1 = graph.getNodeByIndexSafe(arg1.getSyntacticHeadTokenPosition() + 1);
    if (node0 == null) {
      checklist.removeAll(dependencyFeatures);
      return;
    }
    if (node1 == null) {
      checklist.removeAll(dependencyFeatures);
      return;
    }

    List<SemanticGraphEdge> edgePath = graph.getShortestUndirectedPathEdges(node0, node1);
    List<IndexedWord> pathNodes = graph.getShortestUndirectedPathNodes(node0, node1);

    if (edgePath == null) {
      checklist.removeAll(dependencyFeatures);
      return;
    }

    if (pathNodes == null || pathNodes.size() <= 1) { // arguments have the same head.
      checklist.removeAll(dependencyFeatures);
      return;
    }

    // dependency_path: Concatenation of relations in the path between the args in the dependency graph, including directions
    // e.g. "subj->  <-prep_in  <-mod"
    // dependency_path_lowlevel: Same but with finer-grained syntactic relations
    // e.g. "nsubj->  <-prep_in  <-nn"
    if (usingFeature(types, checklist, "dependency_path")) {
      features.setCount("dependency_path:"+generalizedDependencyPath(edgePath, node0), 1.0);
    }
    if (usingFeature(types, checklist, "dependency_path_lowlevel")) {
      String depLowLevel = dependencyPath(edgePath, node0);
      if(logger != null && ! rel.getType().equals(RelationMention.UNRELATED)) logger.info("dependency_path_lowlevel: " + depLowLevel);
      features.setCount("dependency_path_lowlevel:" + depLowLevel, 1.0);
    }

    List<String> pathLemmas = new ArrayList<>();
    List<String> noArgPathLemmas = new ArrayList<>();
    // do not add to pathLemmas words that belong to one of the two args
    Set<Integer> indecesToSkip = new HashSet<>();
    for(int i = arg0.getExtentTokenStart(); i < arg0.getExtentTokenEnd(); i ++) indecesToSkip.add(i + 1);
    for(int i = arg1.getExtentTokenStart(); i < arg1.getExtentTokenEnd(); i ++) indecesToSkip.add(i + 1);
    for (IndexedWord node : pathNodes){
      pathLemmas.add(Morphology.lemmaStatic(node.value(), node.tag(), true));
      if(! indecesToSkip.contains(node.index()))
        noArgPathLemmas.add(Morphology.lemmaStatic(node.value(), node.tag(), true));
    }


    // Verb-based features
    // These features were designed on the assumption that verbs are often trigger words
    // (specifically with the "Kill" relation from Roth CONLL04 in mind)
    // but they didn't end up boosting performance on Roth CONLL04, so they may not be necessary.
    //
    // dependency_paths_to_verb: for each verb in the dependency path,
    // the path to the left of the (lemmatized) verb, to the right, and both, e.g.
    // "subj-> be"
    // "be  <-prep_in  <-mod"
    // "subj->  be  <-prep_in  <-mod"
    // (Higher level relations used as opposed to "lowlevel" finer grained relations)
    if (usingFeature(types, checklist, "dependency_paths_to_verb")) {
      for (IndexedWord node : pathNodes) {
        if (node.tag().contains("VB")) {
          if (node.equals(node0) || node.equals(node1)) {
            continue;
          }
          String lemma = Morphology.lemmaStatic(node.value(), node.tag(), true);
          String node1Path = generalizedDependencyPath(graph.getShortestUndirectedPathEdges(node, node1), node);
          String node0Path = generalizedDependencyPath(graph.getShortestUndirectedPathEdges(node0, node), node0);
          features.setCount("dependency_paths_to_verb:" + node0Path + " " + lemma, 1.0);
          features.setCount("dependency_paths_to_verb:" + lemma + " " + node1Path, 1.0);
          features.setCount("dependency_paths_to_verb:" + node0Path + " " + lemma + " " + node1Path, 1.0);
        }
      }
    }
    // dependency_path_stubs_to_verb:
    // For each verb in the dependency path,
    // the verb concatenated with the first (high-level) relation in the path from arg0;
    // the verb concatenated with the first relation in the path from arg1,
    // and the verb concatenated with both relations.  E.g. (same arguments and sentence as example above)
    // "stub: subj->  be"
    // "stub: be  <-mod"
    // "stub: subj->  be  <-mod"
    if (usingFeature(types, checklist, "dependency_path_stubs_to_verb")) {
      for (IndexedWord node : pathNodes) {
        SemanticGraphEdge edge0 = edgePath.get(0);
        SemanticGraphEdge edge1 = edgePath.get(edgePath.size() - 1);
        if (node.tag().contains("VB")) {
          if (node.equals(node0) || node.equals(node1)) {
            continue;
          }
          String lemma = Morphology.lemmaStatic(node.value(), node.tag(), true);
          String edge0str, edge1str;
          if (node0.equals(edge0.getGovernor())) {
            edge0str = "<-" + generalizeRelation(edge0.getRelation());
          } else {
            edge0str = generalizeRelation(edge0.getRelation()) + "->";
          }
          if (node1.equals(edge1.getGovernor())) {
            edge1str = generalizeRelation(edge1.getRelation()) + "->";
          } else {
            edge1str = "<-" + generalizeRelation(edge1.getRelation());
          }
          features.setCount("stub: " + edge0str + " " + lemma, 1.0);
          features.setCount("stub: " + lemma + edge1str, 1.0);
          features.setCount("stub: " + edge0str + " " + lemma + " " + edge1str, 1.0);
        }
      }
    }

    if (usingFeature(types, checklist, "verb_in_dependency_path")) {
      for (IndexedWord node : pathNodes) {
        if (node.tag().contains("VB")) {
          if (node.equals(node0) || node.equals(node1)) {
            continue;
          }
          SemanticGraphEdge rightEdge = graph.getShortestUndirectedPathEdges(node, node1).get(0);
          SemanticGraphEdge leftEdge = graph.getShortestUndirectedPathEdges(node, node0).get(0);
          String rightRelation, leftRelation;
          boolean governsLeft = false, governsRight = false;
          if (node.equals(rightEdge.getGovernor())) {
            rightRelation = " <-" + generalizeRelation(rightEdge.getRelation());
            governsRight = true;
          } else {
            rightRelation = generalizeRelation(rightEdge.getRelation()) + "-> ";
          }
          if (node.equals(leftEdge.getGovernor())) {
            leftRelation = generalizeRelation(leftEdge.getRelation()) + "-> ";
            governsLeft = true;
          } else {
            leftRelation = " <-" + generalizeRelation(leftEdge.getRelation());
          }
          String lemma = Morphology.lemmaStatic(node.value(), node.tag(), true);

          if (governsLeft || governsRight) {
          }
          if (governsLeft) {
            features.setCount("verb: " + leftRelation + lemma, 1.0);
          }
          if (governsRight) {
            features.setCount("verb: " + lemma + rightRelation, 1.0);
          }
          if (governsLeft && governsRight) {
            features.setCount("verb: " + leftRelation + lemma + rightRelation, 1.0);
          }
        }
      }
    }


    // FEATURES FROM BJORNE ET AL., BIONLP'09
    // dependency_path_words: generates a feature for each word in the dependency path (lemmatized)
    // dependency_path_POS_unigrams: generates a feature for the POS tag of each word in the dependency path
    if (usingFeature(types, checklist, "dependency_path_words")) {
      for (String lemma : noArgPathLemmas)
        features.setCount("word_in_dependency_path:" + lemma, 1.0);
    }
    if (usingFeature(types, checklist, "dependency_path_POS_unigrams")) {
      for (IndexedWord node : pathNodes)
        if (!node.equals(node0) && !node.equals(node1))
          features.setCount("POS_in_dependency_path: "+node.tag(),1.0);
    }

    // dependency_path_word_n_grams: n-grams of words (lemmatized) in the dependency path, n=2,3,4
    // dependency_path_POS_n_grams: n-grams of POS tags of words in the dependency path, n=2,3,4
    for (int node = 0; node < pathNodes.size(); node++) {
      for (int n = 2; n <= 4; n++) {
        if (node+n > pathNodes.size())
          break;
        StringBuilder sb = new StringBuilder();
        StringBuilder sbPOS = new StringBuilder();

        for (int elt = node; elt < node+n; elt++) {
          sb.append(pathLemmas.get(elt));
          sb.append("_");
          sbPOS.append(pathNodes.get(elt).tag());
          sbPOS.append("_");
        }
        if (usingFeature(types, checklist, "dependency_path_word_n_grams"))
          features.setCount("dependency_path_"+n+"-gram: "+sb,1.0);
        if (usingFeature(types,checklist, "dependency_path_POS_n_grams"))
          features.setCount("dependency_path_POS_"+n+"-gram: "+sbPOS,1.0);
      }
    }
    // dependency_path_edge_n_grams: n_grams of relations (high-level) in the dependency path, undirected, n=2,3,4
    // e.g. "subj -- prep_in -- mod"
    // dependency_path_edge_lowlevel_n_grams: similar, for fine-grained relations
    //
    // dependency_path_node-edge-node-grams: trigrams consisting of adjacent words (lemmatized) in the dependency path
    // and the relation between them (undirected)
    // dependency_path_node-edge-node-grams_lowlevel: same, using fine-grained relations
    //
    // dependency_path_edge-node-edge-grams: trigrams consisting of words (lemmatized) in the dependency path
    // and the incoming and outgoing relations (undirected)
    // e.g. "subj -- television -- mod"
    // dependency_path_edge-node-edge-grams_lowlevel: same, using fine-grained relations
    //
    // dependency_path_directed_bigrams: consecutive words in the dependency path (lemmatized) and the direction
    // of the dependency between them
    // e.g. "Theatre -> exhibit"
    //
    // dependency_path_edge_unigrams: feature for each (fine-grained) relation in the dependency path,
    // with its direction in the path and whether it's at the left end, right end, or interior of the path.
    // e.g. "prep_at ->  - leftmost"
    for (int edge = 0; edge < edgePath.size(); edge++) {
      if (usingFeature(types, checklist, "dependency_path_edge_n_grams") ||
              usingFeature(types, checklist, "dependency_path_edge_lowlevel_n_grams")) {
        for (int n = 2; n <= 4; n++) {
          if (edge+n > edgePath.size())
            break;
          StringBuilder sbRelsHi = new StringBuilder();
          StringBuilder sbRelsLo = new StringBuilder();
          for (int elt = edge; elt < edge+n; elt++) {
            GrammaticalRelation gr = edgePath.get(elt).getRelation();
            sbRelsHi.append(generalizeRelation(gr));
            sbRelsHi.append("_");
            sbRelsLo.append(gr);
            sbRelsLo.append("_");
          }
          if (usingFeature(types, checklist, "dependency_path_edge_n_grams"))
            features.setCount("dependency_path_edge_"+n+"-gram: "+sbRelsHi,1.0);
          if (usingFeature(types, checklist, "dependency_path_edge_lowlevel_n_grams"))
            features.setCount("dependency_path_edge_lowlevel_"+n+"-gram: "+sbRelsLo,1.0);
        }
      }
      if (usingFeature(types, checklist, "dependency_path_node-edge-node-grams"))
        features.setCount(
                "dependency_path_node-edge-node-gram: "+
                        pathLemmas.get(edge)+" -- "+
                        generalizeRelation(edgePath.get(edge).getRelation())+" -- "+
                        pathLemmas.get(edge+1),
                1.0);
      if (usingFeature(types, checklist, "dependency_path_node-edge-node-grams_lowlevel"))
        features.setCount(
                "dependency_path_node-edge-node-gram_lowlevel: "+
                        pathLemmas.get(edge)+" -- "+
                        edgePath.get(edge).getRelation()+" -- "+
                        pathLemmas.get(edge+1),
                1.0);
      if (usingFeature(types,checklist, "dependency_path_edge-node-edge-grams") && edge > 0)
        features.setCount(
                "dependency_path_edge-node-edge-gram: "+
                        generalizeRelation(edgePath.get(edge-1).getRelation())+" -- "+
                        pathLemmas.get(edge)+" -- "+
                        generalizeRelation(edgePath.get(edge).getRelation()),
                1.0);
      if (usingFeature(types,checklist,"dependency_path_edge-node-edge-grams_lowlevel") && edge > 0)
        features.setCount(
                "dependency_path_edge-node-edge-gram_lowlevel: "+
                        edgePath.get(edge-1).getRelation()+" -- "+
                        pathLemmas.get(edge)+" -- "+
                        edgePath.get(edge).getRelation(),
                1.0);
      String dir = pathNodes.get(edge).equals(edgePath.get(edge).getDependent()) ? " -> " : " <- ";
      if (usingFeature(types, checklist, "dependency_path_directed_bigrams"))
        features.setCount(
                "dependency_path_directed_bigram: "+
                        pathLemmas.get(edge)+
                        dir+
                        pathLemmas.get(edge+1),
                1.0);
      if (usingFeature(types, checklist, "dependency_path_edge_unigrams"))
        features.setCount(
                "dependency_path_edge_unigram: "+
                        edgePath.get(edge).getRelation() +
                        dir+
                        (edge==0 ? " - leftmost" : edge==edgePath.size()-1 ? " - rightmost" : " - interior"),1.0);
    }

    // dependency_path_length: number of edges in the path between args in the dependency graph, integer-valued
    // dependency_path_length_binary: same, as binary features
    if (usingFeature(types, checklist, "dependency_path_length")) {
      features.setCount("dependency_path_length", edgePath.size());
    }
    if (usingFeature(types, checklist, "dependency_path_length_binary")) {
      features.setCount("dependency_path_length_" + new DecimalFormat("00").format(edgePath.size()), 1.0);
    }

    if (usingFeature(types, checklist, "dependency_path_trigger")) {
      List<CoreLabel> tokens = rel.getSentence().get(TokensAnnotation.class);

      for (IndexedWord node : pathNodes) {
        int index = node.index();
        if (indecesToSkip.contains(index)) continue;

        String trigger = tokens.get(index - 1).get(TriggerAnnotation.class);
        if (trigger != null && trigger.startsWith("B-"))
          features.incrementCount("dependency_path_trigger=" + trigger.substring(2));
      }
    }
  }

  /**
   * Helper method that checks if a feature type "type" is present in the list of features "types"
   * and removes it from "checklist"
   * @param types
   * @param checklist
   * @param type
   * @return true if types contains type
   */
  protected static boolean usingFeature(final List<String> types, List<String> checklist, String type) {
    checklist.remove(type);
    return types.contains(type) || types.contains("all");
  }

  protected static GrammaticalRelation generalizeRelation(GrammaticalRelation gr) {
    final GrammaticalRelation[] GENERAL_RELATIONS = { EnglishGrammaticalRelations.SUBJECT,
            EnglishGrammaticalRelations.COMPLEMENT, EnglishGrammaticalRelations.CONJUNCT,
            EnglishGrammaticalRelations.MODIFIER, };
    for (GrammaticalRelation generalGR : GENERAL_RELATIONS) {
      if (generalGR.isAncestor(gr)) {
        return generalGR;
      }
    }
    return gr;
  }

  /*
   * Under construction
   */

  public static List<String> dependencyPathAsList(List<SemanticGraphEdge> edgePath, IndexedWord node, boolean generalize) {
    if(edgePath == null) return null;
    List<String> path = new ArrayList<>();
    for (SemanticGraphEdge edge : edgePath) {
      IndexedWord nextNode;
      GrammaticalRelation relation;
      if (generalize) {
        relation = generalizeRelation(edge.getRelation());
      } else {
        relation = edge.getRelation();
      }

      if (node.equals(edge.getDependent())) {
        String v = (relation + "->").intern();
        path.add(v);
        nextNode = edge.getGovernor();
      } else {
        String v = ("<-" + relation).intern();
        path.add(v);
        nextNode = edge.getDependent();
      }
      node = nextNode;
    }

    return path;
  }

  public static String dependencyPath(List<SemanticGraphEdge> edgePath, IndexedWord node) {
    // the extra spaces are to maintain compatibility with existing relation extraction models
    return " " + StringUtils.join(dependencyPathAsList(edgePath, node, false), "  ") + " ";
  }

  public static String generalizedDependencyPath(List<SemanticGraphEdge> edgePath, IndexedWord node) {
    // the extra spaces are to maintain compatibility with existing relation extraction models
    return " " + StringUtils.join(dependencyPathAsList(edgePath, node, true), "  ") + " ";
  }

  public Set<String> getFeatures(RelationMention rel, String featureType) {
    Counter<String> features = new ClassicCounter<>();
    List<String> singleton = new ArrayList<>();
    singleton.add(featureType);
    addFeatures(features, rel, singleton);
    return features.keySet();
  }

  public String getFeature(RelationMention rel, String featureType) {
    Set<String> features = getFeatures(rel, featureType);
    if (features.size() == 0) {
      return "";
    } else {
      return features.iterator().next();
    }
  }


}
