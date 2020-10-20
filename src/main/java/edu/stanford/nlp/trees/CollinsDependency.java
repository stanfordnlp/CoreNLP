package edu.stanford.nlp.trees; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.trees.CollinsRelation.Direction;
import edu.stanford.nlp.util.Generics;

/**
 * Extracts bilexical dependencies from Penn Treebank-style phrase structure trees
 * as described in (Collins, 1999) and the later Comp. Ling. paper (Collins, 2003).
 *
 * @author Spence Green
 *
 */
public class CollinsDependency implements Dependency<CoreLabel, CoreLabel, String>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CollinsDependency.class);

	private static final long serialVersionUID = -4236496863919294754L;

	private static final String normPOSLabel = "TAG";

	private final CoreLabel modifier;
	private final CoreLabel head;
	private final CollinsRelation relation;

	/**
	 * Modifier must have IndexAnnotation. If head has 0 as its index, then it is
	 * the start symbol ("boundary symbol" in the Dan Klein code).
	 *
	 * @param modifier
	 * @param head
	 * @param rel
	 */
	public CollinsDependency(CoreLabel modifier, CoreLabel head, CollinsRelation rel) {

		if(modifier.index() == 0)
			throw new RuntimeException("No index annotation for " + modifier.toString());

		this.modifier = modifier;
		this.head = head;
		relation = rel;
	}

	public CollinsRelation getRelation() { return relation; }

	public DependencyFactory dependencyFactory() { return null; }

	public CoreLabel dependent() { return modifier; }

	public CoreLabel governor() { return head; }

	public boolean equalsIgnoreName(Object o) { return this.equals(o); }

	public String name() { return "CollinsBilexicalDependency"; }

	public String toString(String format) { return toString(); }


	private static CoreLabel makeStartLabel(String label) {
		CoreLabel root = new CoreLabel();
		root.set(CoreAnnotations.ValueAnnotation.class, label);
		root.set(CoreAnnotations.IndexAnnotation.class, 0);
		return root;
	}


	public static Set<CollinsDependency> extractFromTree(Tree t, String startSymbol, HeadFinder hf) {
		return extractFromTree(t,startSymbol,hf,false);
	}

	public static Set<CollinsDependency> extractNormalizedFromTree(Tree t, String startSymbol, HeadFinder hf) {
		return extractFromTree(t,startSymbol,hf,true);
	}

	/**
	 * This method assumes that a start symbol node has been added to the tree.
	 *
	 * @param t  The tree
	 * @param hf  A head finding algorithm.
	 * @return A set of dependencies
	 */
	private static Set<CollinsDependency> extractFromTree(Tree t, String startSymbol, HeadFinder hf, boolean normPOS) {
		if(t == null || startSymbol.equals("") || hf == null) return null;

		final Set<CollinsDependency> deps = Generics.newHashSet();

		if(t.value().equals(startSymbol)) t = t.firstChild();

		boolean mustProcessRoot = true;
		for(final Tree node : t) {
			if(node.isLeaf() || node.numChildren() < 2) continue;

			final Tree headDaughter = hf.determineHead(node);
			final Tree head = node.headTerminal(hf);

			if(headDaughter == null || head == null) {
				log.info("WARNING: CollinsDependency.extractFromTree() could not find root for:\n" + node.pennString());

			} else { //Make dependencies
				if(mustProcessRoot) {
					mustProcessRoot = false;
					final CoreLabel startLabel = makeStartLabel(startSymbol);
					deps.add(new CollinsDependency(new CoreLabel(head.label()), startLabel, new CollinsRelation(startSymbol, startSymbol, node.value(), Direction.Right)));
				}

				Direction dir = Direction.Left;
				for(final Tree daughter : node.children()) {

					if(daughter.equals(headDaughter)) {
						dir = Direction.Right;

					} else {
						final Tree headOfDaughter = daughter.headTerminal(hf);

						final String relParent = (normPOS && node.isPreTerminal()) ? normPOSLabel : node.value();
						final String relHead = (normPOS && headDaughter.isPreTerminal()) ? normPOSLabel : headDaughter.value();
						final String relModifier = (normPOS && daughter.isPreTerminal()) ? normPOSLabel : daughter.value();

						final CollinsDependency newDep =
							new CollinsDependency(new CoreLabel(headOfDaughter.label()), new CoreLabel(head.label()), new CollinsRelation(relParent, relHead, relModifier, dir));

						deps.add(newDep);
					}
				}
			}
		}

		//TODO Combine the indexing procedure above with yield here so that two searches aren't performed.
		if(t.yield().size() != deps.size()) {
			System.err.printf("WARNING: Number of extracted dependencies (%d) does not match yield (%d):\n", deps.size(), t.yield().size());
			log.info(t.pennString());
			log.info();
			int num = 0;
			for(CollinsDependency dep : deps)
				log.info(num++ + ": " + dep.toString());
		}

		return deps;
	}


	@Override
	public String toString() {
		return String.format("%s (%d)   %s (%d)  <%s>", modifier.value(),modifier.index(),head.value(),head.index(),relation.toString());
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (!(other instanceof CollinsDependency))
			return false;

		final CollinsDependency otherDep = (CollinsDependency) other;

		return (modifier.equals(otherDep.modifier) &&
				head.equals(otherDep.head) &&
				relation.equals(otherDep.relation));
	}

	@Override
	public int hashCode() {
		int hash = 1;
		hash *= (31 + modifier.index());
		hash *= 138 * head.value().hashCode();
		return hash;
	}

}
