package edu.stanford.nlp.scenegraph;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Scanner;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.scenegraph.image.SceneGraphImage;
import edu.stanford.nlp.scenegraph.image.SceneGraphImageRegion;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Triple;

public class RuleBasedParser extends AbstractSceneGraphParser {

  /* A man is riding a horse. */
  public static SemgrexPattern SUBJ_PRED_OBJ_TRIPLET_PATTERN = SemgrexPattern.compile("{}=pred >nsubj {tag:/NNP?S?/}=subj >/(iobj|obj|nmod:.*|obl:.*)/=objreln {tag:/NNP?S?/}=obj !> cop {}");

  /* A woman is smiling. */
  public static SemgrexPattern SUBJ_PRED_PAIR_PATTERN = SemgrexPattern.compile("{}=pred >nsubj {tag:/NNP?S?/}=subj !>/(iobj|obj|nmod:.*|obl:.*)/ {tag:/NNP?S?/} !>cop {}");

  /* The man is a rider. */
  public static SemgrexPattern COPULAR_PATTERN = SemgrexPattern.compile("{}=pred >nsubj {tag:/NNP?S?/}=subj >cop {}");

  /* A smart woman. */
  public static SemgrexPattern ADJ_MOD_PATTERN = SemgrexPattern.compile("{}=obj >/(amod)/ {}=adj");

  /* The man is tall. */
  public static SemgrexPattern ADJ_PRED_PATTERN = SemgrexPattern.compile("{tag:/J.*/}=adj >nsubj {}=obj");

  /* A woman is in the house. */
  public static SemgrexPattern PP_MOD_PATTERN = SemgrexPattern.compile("{tag:/NNP?S?/}=gov >/(nmod:.*|obl:.*)/=reln {}=mod");

  /* His watch. */
  public static SemgrexPattern POSS_PATTERN = SemgrexPattern.compile("{tag:/NNP?S?/}=gov >/nmod:poss/=reln {tag:/NNP?S?/}=mod");

  /*   */
  public static SemgrexPattern AGENT_PATTERN = SemgrexPattern.compile("{tag:/V.*/}=pred >/obl:agent/=reln {tag:/NNP?S?/}=subj >/nsubj:pass/ {tag:/NNP?S?/}=obj ");

  /* A cat sitting in a chair. */
  public static SemgrexPattern ACL_PATTERN = SemgrexPattern.compile("{}=subj >acl ({tag:/V.*/}=pred >/(iobj|obj|nmod:.*|obl:.*)/=objreln {tag:/NNP?S?/}=obj)");

  //TODO: do something special with nmod:by

  //Several people use laptop computers while sitting around on couches and chairs.
  //A red desk chair has been rolled away from the desk.
  //A red emergency truck has a strong silver guard on the front of its grill.
  //They are preparing the animals for a show.
  //Two engines are visible on the plane.

  //TODO: passives without agent
  //TODO: adverbial modifiers - > potentially (green + light green

  //more spatial relations: in the center of, in the front of,

  public RuleBasedParser() {
    super();
  }

  /**
   * Attaches particles to the main predicate.
   */
  private String getPredicate(SemanticGraph sg, IndexedWord mainPred) {
    if (sg.hasChildWithReln(mainPred, UniversalEnglishGrammaticalRelations.PHRASAL_VERB_PARTICLE)) {
      IndexedWord part = sg.getChildWithReln(mainPred, UniversalEnglishGrammaticalRelations.PHRASAL_VERB_PARTICLE);
      return String.format("%s %s", mainPred.lemma().equals("be") ? "" : mainPred.lemma(), part.value());
    }

    return mainPred.lemma();
  }



  @Override
  public SceneGraph parse(SemanticGraph sg) {

    SemanticGraphEnhancer.enhance(sg);

    SceneGraph scene = new SceneGraph();
    scene.sg = sg;

    SemgrexMatcher matcher = SUBJ_PRED_OBJ_TRIPLET_PATTERN.matcher(sg);
    while(matcher.find()) {
      IndexedWord subj = matcher.getNode("subj");
      IndexedWord obj = matcher.getNode("obj");
      IndexedWord pred = matcher.getNode("pred");
      String reln = matcher.getRelnString("objreln");

      String predicate = getPredicate(sg, pred);
      if (reln.startsWith("nmod:") && ! reln.equals("nmod:poss") && !reln.equals("nmod:agent")) {
        predicate += reln.replace("nmod:", " ").replace("_", " ");
      }

      SceneGraphNode node1 = new SceneGraphNode(subj);
      SceneGraphNode node2 = new SceneGraphNode(obj);
      scene.addEdge(node1, node2, predicate);

    }

    matcher = ACL_PATTERN.matcher(sg);
    while(matcher.find()) {
      IndexedWord subj = matcher.getNode("subj");
      IndexedWord obj = matcher.getNode("obj");
      IndexedWord pred = matcher.getNode("pred");
      String reln = matcher.getRelnString("objreln");

      String predicate = getPredicate(sg, pred);
      if (reln.startsWith("nmod:") && ! reln.equals("nmod:poss") && !reln.equals("nmod:agent")) {
        predicate += reln.replace("nmod:", " ").replace("_", " ");
      }

      SceneGraphNode node1 = new SceneGraphNode(subj);
      SceneGraphNode node2 = new SceneGraphNode(obj);
      scene.addEdge(node1, node2, predicate);

    }

    SemgrexPattern[] subjPredPatterns = {SUBJ_PRED_PAIR_PATTERN, COPULAR_PATTERN};

    for (SemgrexPattern p : subjPredPatterns) {
      matcher = p.matcher(sg);
      while (matcher.find()) {
        IndexedWord subj = matcher.getNode("subj");
        IndexedWord pred = matcher.getNode("pred");

        if (sg.hasChildWithReln(pred, UniversalEnglishGrammaticalRelations.CASE_MARKER)) {
          IndexedWord caseMarker = sg.getChildWithReln(pred, UniversalEnglishGrammaticalRelations.CASE_MARKER);
          String prep = caseMarker.value();
          if(sg.hasChildWithReln(caseMarker, UniversalEnglishGrammaticalRelations.MULTI_WORD_EXPRESSION)) {
            for (IndexedWord additionalCaseMarker : sg.getChildrenWithReln(caseMarker, UniversalEnglishGrammaticalRelations.MULTI_WORD_EXPRESSION)) {
              prep = prep + " " + additionalCaseMarker.value();
            }
          }

          SceneGraphNode node1 = new SceneGraphNode(subj);
          SceneGraphNode node2 = new SceneGraphNode(pred);
          scene.addEdge(node1, node2, prep);
        } else {
          if ( ! pred.lemma().equals("be")) {
            SceneGraphNode node = scene.getOrAddNode(subj);
            node.addAttribute(pred);
          }
        }
      }
    }

    matcher = ADJ_MOD_PATTERN.matcher(sg);
    while(matcher.find()) {
      IndexedWord obj = matcher.getNode("obj");
      IndexedWord adj = matcher.getNode("adj");
      SceneGraphNode node = scene.getOrAddNode(obj);
      node.addAttribute(adj);
    }

    matcher = ADJ_PRED_PATTERN.matcher(sg);
    while(matcher.find()) {
      IndexedWord obj = matcher.getNode("obj");
      IndexedWord adj = matcher.getNode("adj");
      SceneGraphNode node = scene.getOrAddNode(obj);
      node.addAttribute(adj);
    }


    matcher = PP_MOD_PATTERN.matcher(sg);
    while(matcher.find()) {
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");

      String reln = matcher.getRelnString("reln");

      String predicate = reln.replace("nmod:", "").replace("_", " ");
      if (predicate.equals("poss") || predicate.equals("agent")) {
        continue;
      }
      SceneGraphNode node1 = new SceneGraphNode(gov);
      SceneGraphNode node2 = new SceneGraphNode(mod);
      scene.addEdge(node1, node2, predicate);
    }

    matcher = POSS_PATTERN.matcher(sg);
    while (matcher.find()) {
      IndexedWord gov = matcher.getNode("gov");
      IndexedWord mod = matcher.getNode("mod");

      SceneGraphNode node1 = new SceneGraphNode(mod);
      SceneGraphNode node2 = new SceneGraphNode(gov);
      scene.addEdge(node1, node2, "have");
    }

    matcher = AGENT_PATTERN.matcher(sg);
    while (matcher.find()) {
      IndexedWord subj = matcher.getNode("subj");
      IndexedWord obj = matcher.getNode("obj");
      IndexedWord pred = matcher.getNode("pred");

      SceneGraphNode node1 = new SceneGraphNode(subj);
      SceneGraphNode node2 = new SceneGraphNode(obj);
      scene.addEdge(node1, node2, getPredicate(sg, pred));
    }

    return scene;

  }



  private static final SemgrexPattern NUMMOD_PATTERN = SemgrexPattern.compile("{} >/(nummod|qmod)/ {}");

  public static void countDoubleNumMods(List<SceneGraphImage> images) {
    RuleBasedParser parser = new RuleBasedParser();

    int doubleMatches  = 0;

    for (SceneGraphImage img : images) {
      for (SceneGraphImageRegion region : img.regions) {
        SceneGraph scene = parser.parse(region.phrase);
        SemgrexMatcher matcher = NUMMOD_PATTERN.matcher(scene.sg);
        int matches = 0;
        while (matcher.findNextMatchingNode()) {
          matches++;
        }
        if (matches > 1) {
          System.err.println(region.phrase);
          doubleMatches++;
        }
      }
    }

    System.err.println(doubleMatches);
  }


  public static void main(String args[]) throws IOException {


    AbstractSceneGraphParser parser = new RuleBasedParser();

    SceneGraphEvaluation eval = new SceneGraphEvaluation();

    if (args.length < 1) {

      System.err.println("Processing from stdin. Enter one sentence per line.");
      System.err.print("> ");
      Scanner scanner = new Scanner(System.in);
      String line;
      while ( (line = scanner.nextLine()) != null ) {
        SceneGraph scene = parser.parse(line);
        System.err.println(scene.toReadableString());

        System.err.println("------------------------");
        System.err.print("> ");

      }

      scanner.close();

    } else {
      String filename = args[0];
      BufferedReader reader = IOUtils.readerFromString(filename);

      PrintWriter predWriter = IOUtils.getPrintWriter(args[1]);
      PrintWriter goldWriter = IOUtils.getPrintWriter(args[2]);


      if (args.length > 1 && args[1].equals("n")) {
        List<SceneGraphImage> images = Generics.newLinkedList();
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
          SceneGraphImage img = SceneGraphImage.readFromJSON(line);
          if (img == null) {
            continue;
          }
          images.add(img);
        }
        countDoubleNumMods(images);
      } else {
        double count = 0.0;
        double f1Sum = 0.0;
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
          SceneGraphImage img = SceneGraphImage.readFromJSON(line);
          if (img == null) {
            continue;
          }
          for (SceneGraphImageRegion region : img.regions) {
            count += 1.0;
            SceneGraph scene = parser.parse(region.phrase);
            System.err.println(region.phrase);
            System.out.println(scene.toJSON(img.id, img.url, region.phrase));
            System.err.println(scene.toReadableString());
            System.err.println(region.toReadableString());
            Triple<Double, Double, Double> scores = eval.evaluate(scene, region);
            System.err.printf("Prec: %f, Recall: %f, F1: %f%n", scores.first, scores.second, scores.third);
            eval.toSmatchString(scene, region, predWriter, goldWriter);

            f1Sum += scores.third;
            System.err.println("------------------------");
          }
        }

        System.err.println("#########################################################");
        System.err.printf("Macro-averaged F1: %f%n", f1Sum/count);
        System.err.println("#########################################################");

      }
    }
  }
}
