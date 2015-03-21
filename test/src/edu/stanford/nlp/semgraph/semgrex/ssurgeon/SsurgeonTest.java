package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.util.Collection;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;

public class SsurgeonTest {

	/**
	 * Simple test of an Ssurgeon edit script.  This instances a simple semantic graph,
	 * a semgrex pattern, and then the resulting actions over the named nodes in the
	 * semgrex match.
	 */
	@Test
	public void simpleTest() throws Exception {
		SemanticGraph sg = SemanticGraph.valueOf("[mixed/VBN nsubj>[Joe/NNP appos>[bartender/NN det>the/DT]]  dobj>[drink/NN det>a/DT]]");
		SemgrexPattern semgrexPattern = SemgrexPattern.compile("{}=a1 >appos=e1 {}=a2 <nsubj=e2 {}=a3");
		SsurgeonPattern pattern = new SsurgeonPattern(semgrexPattern);

		System.out.println("Start = "+sg.toCompactString());

		// Find and snip the appos and root to nsubj links
		SsurgeonEdit apposSnip = new RemoveNamedEdge("e1", "a1", "a2");
		pattern.addEdit(apposSnip);

		SsurgeonEdit nsubjSnip = new RemoveNamedEdge("e2", "a3", "a1");
		pattern.addEdit(nsubjSnip);

		// Attach Joe to be the nsubj of bartender
		SsurgeonEdit reattachSubj = new AddEdge("a2", "a1", EnglishGrammaticalRelations.NOMINAL_SUBJECT);
		pattern.addEdit(reattachSubj);

		// Attach copula
		IndexedWord isNode = new IndexedWord();
		isNode.set(CoreAnnotations.TextAnnotation.class, "is");
		isNode.set(CoreAnnotations.LemmaAnnotation.class, "is");
		isNode.set(CoreAnnotations.OriginalTextAnnotation.class, "is");
		isNode.set(CoreAnnotations.PartOfSpeechAnnotation.class, "VBN");
		SsurgeonEdit addCopula = new AddDep("a2", EnglishGrammaticalRelations.COPULA, isNode);
		pattern.addEdit(addCopula);

		// Destroy subgraph
		SsurgeonEdit destroySubgraph = new DeleteGraphFromNode("a3");
		pattern.addEdit(destroySubgraph);

		// Process and output modified
		Collection<SemanticGraph> newSgs = pattern.execute(sg);
		for (SemanticGraph newSg : newSgs)
			System.out.println("Modified = "+newSg.toCompactString());
		String firstGraphString = newSgs.iterator().next().toCompactString().trim();
		assertEquals(firstGraphString, "[bartender cop>is nsubj>Joe det>the]");
	}
}
