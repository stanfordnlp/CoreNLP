package edu.stanford.nlp.scenegraph;

import static edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations.MULTI_WORD_EXPRESSION;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.SemanticGraphFactory.Mode;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.NPTmpRetainingTreeNormalizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

/**
 *
 * Performs several enhancements of semantic graphs to
 * make them a better semantic representation.
 *
 * Assumes that the graph contains Universal English
 * relations.
 *
 * @author Sebastian Schuster
 *
 */
//TODO: "Fix two men wearing shirts." in plural resolver.
public class SemanticGraphEnhancer {

  /* A lot of, an assortment of, ... */
  public static final SemgrexPattern QUANT_MOD_PATTERN = SemgrexPattern.compile("{word:/(?i:lot|assortment|number|couple|bunch|handful|litany|sheaf|slew|dozen|series|variety|multitude|wad|clutch|wave|mountain|array|spate|string|ton|range|plethora|heap|sort|form|kind|type|version|bit|pair|triple|total)/}=w2 >det {word:/(?i:an?)/}=w1 >/nmod.*/ ({tag:/(NN.*|PRP.*)/}=gov >case {word:/(?i:of)/}=w3) . {}=w3");

  /* Lots of, dozens of, heaps of ... */
  public static final SemgrexPattern QUANT_MOD_PATTERN2 = SemgrexPattern.compile("{word:/(?i:lots|many|several|plenty|tons|dozens|multitudes|mountains|loads|pairs|tens|hundreds|thousands|millions|billions|trillions|[0-9]+s)/}=w1 >/nmod.*/ ({tag:/(NN.*|PRP.*)/}=gov >case {word:/(?i:of)/}=w2) . {}=w2");

  /* Some of the ..., all of them, ... */
  public static final SemgrexPattern QUANT_MOD_PATTERN3 = SemgrexPattern.compile("{word:/(?i:some|all|both|neither|everyone|nobody|one|two|three|four|five|six|seven|eight|nine|ten|hundred|thousand|million|billion|trillion|[0-9]+)/}=w1 [>/nmod.*/ ({tag:/(NN.*)/}=gov >case ({word:/(?i:of)/}=w2 $+ {}=det) >det {}=det) |  >/nmod.*/ ({tag:/(PRP.*)/}=gov >case {word:/(?i:of)/}=w2)] . {}=w2");

  //do something about "a pair of sunglasses vs. the pair of sunglasses..."
  //TODO: Improve Partitives

  private static SimplePronounResolution pronounResolver = new SimplePronounResolution();



  /**
   * Turns quantifier-like expressions such as "a lot of" into
   * multi-word expressions and promotes the original nominal modifier
   * to the head of phrase.
   */
  public static void processQuanftificationModifiers(SemanticGraph sg) {

    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = QUANT_MOD_PATTERN.matcher(sgCopy);

    while (matcher.findNextMatchingNode()) {
      IndexedWord w1 = matcher.getNode("w1");
      IndexedWord w2 = matcher.getNode("w2");
      IndexedWord w3 = matcher.getNode("w3");
      IndexedWord gov = matcher.getNode("gov");

      if ( ! sg.getRoots().contains(w2)) {
        IndexedWord parent = sg.getParent(w2);
        if (parent == null) {
          return;
        }
        SemanticGraphEdge edge = sg.getEdge(parent, w2);
        sg.removeEdge(edge);
        sg.addEdge(parent, gov, edge.getRelation(), edge.getWeight(), edge.isExtra());
      } else {
        sg.getRoots().remove(w2);
        sg.addRoot(gov);

      }

      sg.removeEdge(sg.getEdge(w2, gov));

      List<IndexedWord> otherDeps = Generics.newLinkedList();

      otherDeps.add(w1);
      otherDeps.add(w2);
      otherDeps.add(w3);

      /* Look for amod children left to the main noun, e.g.
       * "whole" in "a whole bunch of"
       */
      for (IndexedWord child : sg.getChildrenWithReln(w2, UniversalEnglishGrammaticalRelations.ADJECTIVAL_MODIFIER)) {
        if (child.index() < w2.index()) {
          otherDeps.add(child);
        }
      }

      Collections.sort(otherDeps);
      createMultiWordExpression(sg, gov, UniversalEnglishGrammaticalRelations.QMOD, otherDeps.toArray(new IndexedWord[otherDeps.size()]));
    }

    //TODO: make this a function and don't duplicate for each pattern

    sgCopy = sg.makeSoftCopy();
    matcher = QUANT_MOD_PATTERN2.matcher(sgCopy);
    while (matcher.findNextMatchingNode()) {
      IndexedWord w1 = matcher.getNode("w1");
      IndexedWord w2 = matcher.getNode("w2");
      IndexedWord gov = matcher.getNode("gov");

      if ( ! sg.getRoots().contains(w1)) {
        IndexedWord parent = sg.getParent(w1);
        if (parent == null) {
          return;
        }
        SemanticGraphEdge edge = sg.getEdge(parent, w1);
        sg.removeEdge(edge);
        sg.addEdge(parent, gov, edge.getRelation(), edge.getWeight(), edge.isExtra());
      } else {
        sg.getRoots().remove(w1);
        sg.addRoot(gov);
      }

      sg.removeEdge(sg.getEdge(w1, gov));

      List<IndexedWord> otherDeps = Generics.newLinkedList();

      otherDeps.add(w1);
      otherDeps.add(w2);

      /* Look for amod children left to the main noun, e.g.
       * "whole" in "a whole bunch of"
       */
      for (IndexedWord child : sg.getChildren(w1)) {
        if (child.index() < w1.index()) {
          otherDeps.add(child);
        }
      }

      Collections.sort(otherDeps);
      createMultiWordExpression(sg, gov, UniversalEnglishGrammaticalRelations.QMOD, otherDeps.toArray(new IndexedWord[otherDeps.size()]));

    }

    sgCopy = sg.makeSoftCopy();
    matcher = QUANT_MOD_PATTERN3.matcher(sgCopy);
    while (matcher.findNextMatchingNode()) {
      IndexedWord w1 = matcher.getNode("w1");
      IndexedWord w2 = matcher.getNode("w2");
      IndexedWord gov = matcher.getNode("gov");

      if ( ! sg.getRoots().contains(w1)) {
        IndexedWord parent = sg.getParent(w1);
        if (parent == null) {
          return;
        }
        SemanticGraphEdge edge = sg.getEdge(parent, w1);
        sg.removeEdge(edge);
        sg.addEdge(parent, gov, edge.getRelation(), edge.getWeight(), edge.isExtra());
      } else {
        sg.getRoots().remove(w1);
        sg.addRoot(gov);
      }

      sg.removeEdge(sg.getEdge(w1, gov));


      List<IndexedWord> otherDeps = Generics.newLinkedList();

      otherDeps.add(w1);
      otherDeps.add(w2);




      /* Look for amod children left to the main noun, e.g.
       * "whole" in "a whole bunch of"
       */
      for (IndexedWord child : sg.getChildren(w1)) {
        if (child.index() < w1.index()) {
          otherDeps.add(child);
        }
      }


      Collections.sort(otherDeps);
      createMultiWordExpression(sg, gov, UniversalEnglishGrammaticalRelations.QMOD, otherDeps.toArray(new IndexedWord[otherDeps.size()]));

    }
  }

  private static void createMultiWordExpression(SemanticGraph sg, IndexedWord gov, GrammaticalRelation reln, IndexedWord... words) {
    if (sg.getRoots().isEmpty() || gov == null || words.length < 1) {
      return;
    }

    boolean first = true;
    IndexedWord mweHead = null;
    for (IndexedWord word : words) {
      for (SemanticGraphEdge edge : sg.incomingEdgeList(word)) {
        sg.removeEdge(edge);
      }

      if (first) {
        sg.addEdge(gov, word, reln, Double.NEGATIVE_INFINITY, false);
        mweHead = word;
        first = false;
      } else {
        sg.addEdge(mweHead, word, MULTI_WORD_EXPRESSION, Double.NEGATIVE_INFINITY, false);
      }
    }
  }

  /* Both subject and object or PP are plurals. */
  private static final SemgrexPattern PLURAL_SUBJECT_OBJECT_PATTERN = SemgrexPattern.compile("{}=pred >nsubj {tag:/NNP?S/}=subj [ >/(.obj)/ ({tag:/NNP?S/}=obj) |  >/(nmod:((?!agent).)*$)/ ({tag:/NNP?S/}=obj >case {}) ] ");

  /* Only subject is plural (either no object or PP exists, or they are singular). */
  private static final SemgrexPattern PLURAL_SUBJECT_PATTERN = SemgrexPattern.compile("{tag:/NNP?S/}=subj [ == {$} | <nsubj ({} !>/.obj/ {tag:/NNP?S/} !>/(nmod:((?!agent).)*$)/ ({tag:/NNP?S/} > case {}) )]");

  /* Only object is plural (either no subject or it is singular). */
  private static final SemgrexPattern PLURAL_OTHER_PATTERN = SemgrexPattern.compile("{tag:/NNP?S/}=word !== {$} !<nsubj {} !</.obj|nmod.*/ ({} >nsubj {tag:/NNP?S/})");


  /**
   * Creates copies of plural nodes which match any of the above defined patterns.
   * For cases where both the subject and the object are plural it assumes
   * a distributive reading.
   *
   * @param sg
   */
  public static void resolvePlurals(SemanticGraph sg) {

    SemanticGraph sgCopy = sg.makeSoftCopy();
    SemgrexMatcher matcher = PLURAL_SUBJECT_OBJECT_PATTERN.matcher(sgCopy);
    while (matcher.findNextMatchingNode()) {
      IndexedWord subj = matcher.getNode("subj");
      IndexedWord obj = matcher.getNode("obj");
      IndexedWord pred = matcher.getNode("pred");

      int numCopies = 1;

      if (sg.hasChildWithReln(subj, UniversalEnglishGrammaticalRelations.NUMERIC_MODIFIER)) {
        IndexedWord nummod = sg.getChildWithReln(subj,  UniversalEnglishGrammaticalRelations.NUMERIC_MODIFIER);
        /* Prevent things like "number 5" */
        if (nummod.index() > subj.index()) {
          continue;
        }
        if (nummod.get(CoreAnnotations.NumericValueAnnotation.class) != null
            && nummod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() < 20) {
          numCopies = nummod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() - 1;
        }
      } else if (sg.hasChildWithReln(subj, UniversalEnglishGrammaticalRelations.QMOD)) {
        IndexedWord qmod = sg.getChildWithReln(subj, UniversalEnglishGrammaticalRelations.QMOD);
        if (qmod.get(CoreAnnotations.NumericValueAnnotation.class) != null &&
            qmod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() < 20) {
          numCopies = qmod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() - 1;
        }
      }

      for (int i = 0; i < numCopies; i++) {
        IndexedWord predCopy = pred.makeSoftCopy();
        IndexedWord subjCopy = subj.makeSoftCopy();
        IndexedWord objCopy = obj.makeSoftCopy();

        SemanticGraphEdge subjEdge = sg.getEdge(pred, subj);
        SemanticGraphEdge objEdge = sg.getEdge(pred, obj);

        sg.addEdge(predCopy, subjCopy, subjEdge.getRelation(), subjEdge.getWeight(), subjEdge.isExtra());
        sg.addEdge(predCopy, objCopy, objEdge.getRelation(), objEdge.getWeight(), objEdge.isExtra());

        if (sg.getRoots().contains(pred)) {
          sg.addRoot(subjCopy);
        } else {
          for (SemanticGraphEdge edge : sg.incomingEdgeIterable(pred)) {
            sg.addEdge(edge.getGovernor(), predCopy, edge.getRelation(), edge.getWeight(), edge.isExtra());
          }
        }

        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(pred)) {
          if (edge.getDependent().equals(subj) || edge.getDependent().equals(obj)) {
            continue;
          }
          sg.addEdge(predCopy, edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
        }

        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(subj)) {
          if (edge.getDependent() == obj) {
            sg.addEdge(subjCopy, edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
          } else {
            // There is another direct relation between subject and object (e.g. possesor). Use the copy of
            // the object in this case.
            sg.addEdge(subjCopy, objCopy, edge.getRelation(), edge.getWeight(), edge.isExtra());
          }
        }

        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(obj)) {
          if (edge.getDependent() == subj) {
            sg.addEdge(objCopy, edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
          } else {
            // There is another direct relation between subject and object (e.g. possesor). Use the copy of
            // the subject in this case.
            sg.addEdge(objCopy, subjCopy, edge.getRelation(), edge.getWeight(), edge.isExtra());
          }
        }
      }
    }

    //TODO: refactor to get rid of duplicate code.

    matcher = PLURAL_SUBJECT_PATTERN.matcher(sgCopy);
    while (matcher.findNextMatchingNode()) {
      IndexedWord subj = matcher.getNode("subj");

      int numCopies = 1;

      if (sg.hasChildWithReln(subj, UniversalEnglishGrammaticalRelations.NUMERIC_MODIFIER)) {
        IndexedWord nummod = sg.getChildWithReln(subj,  UniversalEnglishGrammaticalRelations.NUMERIC_MODIFIER);
        /* Prevent things like "number 5" */
        if (nummod.index() > subj.index()) {
          continue;
        }
        if (nummod.get(CoreAnnotations.NumericValueAnnotation.class) != null
            && nummod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() < 20) {
          numCopies = nummod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() - 1;
        }
      } else if (sg.hasChildWithReln(subj, UniversalEnglishGrammaticalRelations.QMOD)) {
        IndexedWord qmod = sg.getChildWithReln(subj, UniversalEnglishGrammaticalRelations.QMOD);
        if (qmod.get(CoreAnnotations.NumericValueAnnotation.class) != null &&
            qmod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() < 20) {
          numCopies = qmod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() - 1;
        }
      }
      copyNode(sg, subj, numCopies);
    }

    matcher = PLURAL_OTHER_PATTERN.matcher(sgCopy);
    while (matcher.findNextMatchingNode()) {
      IndexedWord word = matcher.getNode("word");

      int numCopies = 0;

      if (sg.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.NUMERIC_MODIFIER)) {
        IndexedWord nummod = sg.getChildWithReln(word,  UniversalEnglishGrammaticalRelations.NUMERIC_MODIFIER);
        /* Prevent things like "number 5" */
        if (nummod.index() > word.index()) {
          continue;
        }
        if (nummod.get(CoreAnnotations.NumericValueAnnotation.class) != null
            && nummod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() < 20) {
          numCopies = nummod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() - 1;
        }
      } else if (sg.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.QMOD)) {
        IndexedWord qmod = sg.getChildWithReln(word, UniversalEnglishGrammaticalRelations.QMOD);
        if (qmod.get(CoreAnnotations.NumericValueAnnotation.class) != null &&
            qmod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() < 20) {
          numCopies = qmod.get(CoreAnnotations.NumericValueAnnotation.class).intValue() - 1;
        }
      }
      copyNode(sg, word, numCopies);
    }
  }


  private static void copyNode(SemanticGraph sg, IndexedWord node, int numCopies) {
    for (int i = 0; i < numCopies; i++) {
      IndexedWord copy = node.makeSoftCopy();

      for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(node)) {
        sg.addEdge(copy, edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
      }

      for (SemanticGraphEdge edge : sg.incomingEdgeIterable(node)) {
        sg.addEdge(edge.getGovernor(), copy, edge.getRelation(), edge.getWeight(), edge.isExtra());
      }

      if (sg.getRoots().contains(node)) {
        sg.addRoot(copy);
      }
    }
  }



  public static void collapseCompounds(SemanticGraph sg) {
    SemanticGraph sgCopy = sg.makeSoftCopy();

    for (IndexedWord word : sgCopy.vertexSet()) {
      if (sgCopy.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.COMPOUND_MODIFIER)) {
        List<IndexedWord> compound = Generics.newArrayList();
        compound.add(word);
        for (IndexedWord word2 : sgCopy.getChildrenWithReln(word, UniversalEnglishGrammaticalRelations.COMPOUND_MODIFIER)) {
          compound.add(word2);
        }
        Collections.sort(compound);

        boolean collapse = true;
        for (int i = 0, idx = compound.get(i).index() - 1, sz = compound.size(); i < sz; i++) {
          if (compound.get(i).index() != idx + 1 || compound.get(i).index() > word.index()) {
            collapse = false;
            break;
          }
          idx = compound.get(i).index();
        }

        if (collapse) {

          String lemma = StringUtils.join(compound.stream().map(x -> x.lemma() != null ? x.lemma() : x.word()), " ");
          word.set(SceneGraphCoreAnnotations.CompoundWordAnnotation.class, StringUtils.join(compound.stream().map(IndexedWord::word), " "));
          word.set(SceneGraphCoreAnnotations.CompoundLemmaAnnotation.class, lemma);

//          word.setWord(StringUtils.join(compound.stream().map(IndexedWord::word), " "));
//          word.setValue(StringUtils.join(compound.stream().map(IndexedWord::value), " "));
//          word.setLemma(lemma);
        }
        //for (IndexedWord word2 : compound) {
        //  if (word2 != word) {
        //    sg.removeVertex(word2);
        //  }
        //}
      }
    }
  }


  /**
   * Collapses verbs with verbal particles.
   * @param sg
   */
  public static void collapseParticles(SemanticGraph sg) {
    SemanticGraph sgCopy = sg.makeSoftCopy();

    for (IndexedWord word : sgCopy.vertexSet()) {
      if (sgCopy.hasChildWithReln(word, UniversalEnglishGrammaticalRelations.PHRASAL_VERB_PARTICLE)) {
        List<IndexedWord> compound = Generics.newArrayList();
        compound.add(word);
        for (IndexedWord word2 : sgCopy.getChildrenWithReln(word, UniversalEnglishGrammaticalRelations.PHRASAL_VERB_PARTICLE)) {
          compound.add(word2);
        }
        Collections.sort(compound);

        boolean collapse = true;
        for (int i = 0, sz = compound.size(); i < sz; i++) {
          if (compound.get(i).index() < word.index()) {
            collapse = false;
            break;
          }
        }

        if (collapse) {
          String lemma = StringUtils.join(compound.stream().map(x -> x.lemma() != null ? x.lemma() : x.word()), " ");
          word.set(SceneGraphCoreAnnotations.CompoundWordAnnotation.class, StringUtils.join(compound.stream().map(IndexedWord::word), " "));
          word.set(SceneGraphCoreAnnotations.CompoundLemmaAnnotation.class, lemma);
        }
//        for (IndexedWord word2 : compound) {
//          if (word2 != word) {
//            sg.removeVertex(word2);
//          }
//        }
      }
    }
  }


  /**
   * Resolves pronouns in the semantic graph and replaces
   * the pronominal node in the semantic graph with the node
   * of its antecedent.
   *
   * @param sg A SemanticGraph.
   */
  public static void resolvePronouns(SemanticGraph sg) {
    HashMap<Integer, Integer> resolvedPronouns = pronounResolver.resolvePronouns(sg);

    for (Integer key : resolvedPronouns.keySet()) {
      Integer mentionIdx = resolvedPronouns.get(key);
      IndexedWord mention = sg.getNodeByIndexSafe(mentionIdx);
      IndexedWord pronoun = sg.getNodeByIndexSafe(key);
      if (mention == null || pronoun == null) {
        continue;
      }

      if (sg.getRoots().contains(pronoun)) {
        sg.getRoots().remove(pronoun);
        sg.getRoots().add(mention);
      } else {
        List<SemanticGraphEdge> incomingEdges = sg.getIncomingEdgesSorted(pronoun);
        for (SemanticGraphEdge edge : incomingEdges) {
          sg.removeEdge(edge);
          sg.addEdge(edge.getGovernor(), mention, edge.getRelation(), edge.getWeight(), edge.isExtra());
        }
      }

      if (mention.get(SceneGraphCoreAnnotations.IndicesAnnotation.class) == null) {
        mention.set(SceneGraphCoreAnnotations.IndicesAnnotation.class, Generics.newHashSet());
        mention.get(SceneGraphCoreAnnotations.IndicesAnnotation.class).add(mention.index());
      }
      mention.get(SceneGraphCoreAnnotations.IndicesAnnotation.class).add(pronoun.index());


      List<SemanticGraphEdge> outgoingEdges = sg.getOutEdgesSorted(pronoun);
      for (SemanticGraphEdge edge : outgoingEdges) {
        sg.removeEdge(edge);
        sg.addEdge(mention, edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
      }

    }
  }


  /**
   * Performs all the enhancements in the following order:
   *
   * <ol>
   * <li>Process quantificational modifiers</li>
   * <li>Collapse verbs with particles, and compound nouns.</li>
   * <li>Resolve pronouns.</li>
   * <li>Resolve plural nouns.</li>
   * </ol>
   * @param sg
   */

  public static void enhance(SemanticGraph sg) {
    processQuanftificationModifiers(sg);

    collapseCompounds(sg);

    collapseParticles(sg);

    resolvePronouns(sg);

    resolvePlurals(sg);

  }

  /**
   *
   * @param args
   * @throws IOException
   */
  public static void main(String args[]) throws IOException {
    String treeFile = args[0];

    MemoryTreebank tb = new MemoryTreebank(new NPTmpRetainingTreeNormalizer(0, false, 1, false));
    tb.loadPath(treeFile);

    Iterator<Tree> it = tb.iterator();

    Index<String> relationIndex = new HashIndex<String>();
    relationIndex.addToIndex("root");

    if (args.length > 1 ) {
      relationIndex = HashIndex.loadFromFilename(args[1]);
      //prevent addition of new relations
      relationIndex.lock();
    }

    Index<String> vectorIndex = new HashIndex<String>();

    if (args.length > 2) {
      BufferedReader reader = IOUtils.readerFromString(args[2]);
      vectorIndex.addToIndex("--UNK--");
      reader.lines().forEach(x -> vectorIndex.addToIndex(x));
      reader.close();
      System.err.println(vectorIndex.size());
      vectorIndex.lock();
    }

    while (it.hasNext()) {
      Tree t = it.next();
      SemanticGraph sg = SemanticGraphFactory.makeFromTree(t, Mode.CCPROCESSED, Extras.MAXIMAL);
      processQuanftificationModifiers(sg);
      resolvePronouns(sg);
      resolvePlurals(sg);

      int rootIndex = sg.getFirstRoot().index();

      boolean printedRoot = false;
      for (SemanticGraphEdge edge : sg.edgeListSorted()) {
        if ( ! printedRoot && edge.getTarget().index() > rootIndex) {
          for (IndexedWord root : sg.getRoots()) {
            //reln|reln-idx|parent-idx|child-idx|child-word|child-vec-idx
            int idx = vectorIndex.addToIndex(root.value().toLowerCase());
            if (idx < 1) idx = 1;
            System.out.printf("%s|%d|%d|%d|%s|%d%n", "root", relationIndex.addToIndex("root"), 0, root.index(), root.value().toLowerCase(), idx);
          }
          printedRoot = true;
        }
        int idx = vectorIndex.addToIndex(edge.getTarget().value().toLowerCase());
        if (idx < 1) idx = 1;
        String reln = edge.getRelation().toString();
        System.out.printf("%s|%d|%d|%d|%s|%d%n", reln, relationIndex.addToIndex(reln), edge.getSource().index(), edge.getTarget().index(), edge.getTarget().value().toLowerCase(), idx);
      }

      if ( ! printedRoot) {
        for (IndexedWord root : sg.getRoots()) {
          int idx = vectorIndex.addToIndex(root.value().toLowerCase());
          if (idx < 1) idx = 1;
          System.out.printf("%s|%d|%d|%d|%s|%d%n", "root", relationIndex.addToIndex("root"), 0, root.index(), root.value().toLowerCase(), idx);
        }
      }

      System.out.println();

    }

    if (args.length < 2) {
      relationIndex.saveToFilename("relations.index");
    }

    System.err.println(relationIndex.size());

  }


}
