/**
 * <p>
 *   A package for (NLP) trees, sentences, and similar things.
 *   This package provides several key abstractions (via abstract classes)
 *   and a number of further classes for related objects.
 *   Most of these classes use a Factory pattern to instantiate objects.
 * </p><p>
 *   A <code>Label</code> is something that can be the label of a Tree or a
 *   Constituent.  The simplest label is a <code>StringLabel</code>.
 *   A <code>Word</code> or a <code>TaggedWord</code> is a
 *   <code>Label</code>.  They can be constructed with a
 *   <code>LabelFactory</code>.  A <code>Label</code> often implements
 *   various interfaces, such as <code>HasWord</code>.
 * </p><p>
 *   A <code>Constituent</code> object defines a generic edge in a graph.  It
 *   has a start and end, and usually a <code>Label</code>.  A
 *   <code>ConstituentFactory</code> builds a <code>Constituent</code>.
 * </p><p>
 *   A <code>Tree</code> object provides generic facilities for manipulating
 *   NLP trees.  A <code>TreeFactory</code> can build a <code>Tree</code>.
 *   A <code>Treebank</code> provides an interface to a
 *   collection of parsed sentences (normally found on disk as a corpus).
 *   A <code>TreeReader</code> reads trees from an <code>InputStream</code>.
 *   A <code>TreeReaderFactory</code> builds a <code>TreeReader</code>.
 *   A <code>TreeNormalizer</code> canonicalizes a <code>Tree</code> on
 *   input from a <code>File</code>.  A <code>HeadFinder</code> finds the
 *   head daughter of a <code>Tree</code>. The <code>TreeProcessor</code>
 *   interface is for general sequential processing of trees, and the
 *   <code>TreeTransformer</code> interface is for changing them.
 * </p><p>
 *   A <code>Sentence</code> is a subclass of an <code>ArrayList</code>.
 *   A <code>Sentencebank</code> provides an interface to a large number of
 *   sentences (normally found on disk as a corpus).
 *   A <code>SentenceReader</code> reads sentences from an
 *   <code>InputStream</code>.  A <code>SentenceReaderFactory</code>
 *   builds a <code>SentenceReader</code>.  A <code>SentenceNormalizer</code>
 *   canonicalizes a <code>Sentence</code> on input from a <code>File</code>.
 *   The <code>SentenceProcessor</code> interface is for general sequential
 *   processing of sentences.
 * </p><p>
 *   There are also various subclasses of <code>StreamTokenizer</code>.  The class
 *   <code>PairFinder</code> should probably be removed to <code>samples</code>.
 * </p>
 * <p>
 *   <i>Design notes:</i> This package is the result of several iterations of
 *   trying to come up with a reusable and extendable set of tree
 *   classes.  It may still be nonoptimal, but some thought went into
 *   it!  At any rate, there are several things that it is important to
 *   understand to use the class effectively.  One is that a Label has
 *   a primary value() which is always a String, and this is the only
 *   thing that matters for fundamental Label operations, such as
 *   checking equality.  While anything else (or nothing) can be stored
 *   in a Label, all other Label content is regarded as purely
 *   decorative.  All Label implementations should implement a
 *   labelFactory() method that returns a LabelFactory for the appropriate
 *   kind of Label.  Since this depends on the exact class, this method
 *   should always be overwritten when a Label class is extended.  The
 *   existing Label classes also provide a static factory() method
 *   which returns the same thing.
 * </p>
 *
 * <h2>Illustrations of use of the <code>trees</code> package</h2>
 *
 * <h3>Treebank and Tree</h3>
 *
 * <p>Here is some fairly straightforward code for loading trees from a
 * treebank and iterating over the trees contained therein. It builds
 * a histogram of sentence lengths.<p>
 *
 * <blockqouote>
 *   <pre>
 * import java.util.Iterator;
 * import edu.stanford.nlp.trees.*;
 * import edu.stanford.nlp.io.NumberRangesFileFilter;
 * import edu.stanford.nlp.util.Timing;
 *
 * /** This class just prints out sentences and their lengths.
 *  *  Use: java SentenceLengths /turing/corpora/Treebank2/combined/wsj/07
 *  *              [fileRange]
 *  *\/
 * public class SentenceLengths {
 *
 *   private static final int maxleng = 100;
 *   private static int[] lengthCounts = new int[maxleng+1];
 *   private static int numSents = 0;
 *
 *   public static void main(String[] args) {
 *     Timing.startTime();
 *     Treebank treebank = new DiskTreebank(
 *       new LabeledScoredTreeReaderFactory());
 *     if (args.length > 1) {
 *       treebank.loadPath(args[0], new NumberRangesFileFilter(args[1],
 *         true));
 *     } else {
 *       treebank.loadPath(args[0]);
 *     }
 *
 *     for (Iterator it = treebank.iterator(); it.hasNext(); ) {
 *       Tree t = (Tree) it.next();
 *       numSents++;
 *       int len = t.yield().length();
 *       if (len &lt;= maxleng) {
 *         lengthCounts[len]++;
 *       }
 *     }
 *     System.out.print("Files " + args[0] + " ");
 *     if (args.length > 1) {
 *       System.out.print(args[1] + " ");
 *     }
 *     System.out.println("consists of " + numSents + " sentences");
 *     for (int i = 0; i &lt;= maxleng; i++) {
 *       System.out.println("  " + lengthCounts[i] + " of length " + i);
 *     }
 *     Timing.endTime("Read/count all trees");
 *   }
 * }
 * </pre>
 * </blockquote>
 *
 * <h3>Treebank, custom TreeReaderFactory, Tree, and Constituent</h3>
 *
 * <p>
 *   This example illustrates building a Treebank by hand, specifying a
 *   custom <code>TreeReaderFactory</code>, and illustrates more of the
 *   <code>Tree</code> package, and the notion of a
 *   <code>Constituent</code>.  A <code>Constituent</code> has a
 *   start and end point and a <code>Label</code>.
 *   </p>
 *
 * <blockquote>
 *   <pre>
 * import java.io.*;
 * import java.util.*;
 *
 * import edu.stanford.nlp.trees.*;
 * import edu.stanford.nlp.util.*;
 *
 * /** This class counts how often each constituent appears
 * *  Use: java ConstituentCounter /turing/corpora/Treebank2/combined/wsj/07
 * *\
 *
 * public class ConstituentCounter {
 *
 *   public static void main(String[] args) {
 *     Treebank treebank = new DiskTreebank(new TreeReaderFactory() {
 *       public TreeReader newTreeReader(Reader in) {
 *         return new TreeReader(in,
 *           new LabeledScoredTreeFactory(new StringLabelFactory()),
 *           new BobChrisTreeNormalizer());
 *       }
 *     });
 *
 *     treebank.loadPath(args[0]);
 *     Counter cnt = new Counter();
 *
 *     ConstituentFactory confac = LabeledConstituent.factory();
 *     for (Iterator it = treebank.iterator(); it.hasNext(); ) {
 *       Tree t = (Tree) it.next();
 *       Set constituents = t.constituents(confac);
 *       for (Iterator it2 = constituents.iterator(); it2.hasNext(); ) {
 *         Constituent c = (Constituent) it2.next();
 *         cnt.increment(c);
 *       }
 *     }
 *     SortedSet ss = new TreeSet(cnt.seenSet());
 *     for (Iterator it = ss.iterator(); it.hasNext(); ) {
 *       Constituent c = (Constituent) it.next();
 *       System.out.println(c + "  " + cnt.countOf(c));
 *     }
 *   }
 * }
 * </pre>
 * </blockquote>
 *
 *
 * <h3>Tree and Label</h3>
 *
 * <p>
 * Dealing with the <code>Tree</code> and <code>Label</code> classes is a
 *  central part of using this package.  This code works out the
 *  set of tags (preterminal labels) used in a Treebank.  It
 *  illustrates writing ones own code to recurse through a Tree, and getting
 *  a String value for a Label.
 * </p>
 *
 * <blockquote>
 * <pre>
 * import java.util.*;
 * import edu.stanford.nlp.trees.*;
 * import edu.stanford.nlp.util.Counter;
 *
 * /** This class prints out trees from strings and counts their preterminals.
 * *  Use: java TreesFromStrings '(S (NP (DT This)) (VP (VBD was) (JJ good)))'
 * *\/
 * public class TreesFromStrings {
 *
 * private static void addTerminals(Tree t, Counter c) {
 *     if (t.isLeaf()) {
 *       // do nothing
 *    } else if (t.isPreTerminal()) {
 *      c.increment(t.label().value());
 *    } else {
 *      // phrasal node
 *      Tree[] kids = t.children();
 *      for (int i = 0; i &lt; kids.length; i++) {
 *        addTerminals(kids[i], c);
 *      }
 *    }
 *  }
 *
 * public static void main(String[] args) {
 *    Treebank tb = new MemoryTreebank();
 *  for (int i = 0; i &lt; args.length; i++) {
 *       try {
 *      Tree t = Tree.valueOf(args[i]);
 *      tb.add(t);
 *    } catch (Exception e) {
 *      e.printStackTrace();
 *    }
 *  }
 *  Counter c = new Counter();
 *  for (Iterator it = tb.iterator(); it.hasNext(); ) {
 *    Tree t = (Tree) it.next();
 *    addTerminals(t, c);
 *  }
 *     System.out.println(c);
 * }
 *
 * }
 * </pre>
 * </blockquote>
 *
 * <p>As well as the Treebank classes, there are corresponding Sentencebank
 *  classes (though they are not quite so extensively developed.
 *  This final example shows use of a Sentencebank.  It also
 *  illustrates the Visitor pattern for examining sentences in a
 *  Sentencebank.  This was actually the original visitation
 *  pattern for Treebank and Sentencebank, but these days, it's in
 *  general easier to use an Iterator.  You can also get Sentences
 *  from a Treebank, by taking the yield() or taggedYield() of
 *  each Tree.
 * </p>
 *
 * <blockquote>
 * <pre>
 * import java.io.*;
 *
 * import edu.stanford.nlp.trees.*;
 *
 * public class SentencePrinter {
 *
 * /** Loads SentenceBank from first argument and prints it out.  <br>
 *  *  Usage: java SentencePrinter sentencebankPath
 *  *  @param args Array of command-line arguments
 *  *\/
 * public static void main(String[] args) {
 *  SentenceReaderFactory srf = new SentenceReaderFactory() {
 *    public SentenceReader newSentenceReader(Reader in) {
 *      return new SentenceReader(in, new TaggedWordFactory(),
 *          new PennSentenceNormalizer(),
 *          new PennTagbankStreamTokenizer(in));
 *    }
 *  };
 *  Sentencebank sentencebank = new DiskSentencebank(srf);
 *  sentencebank.loadPath(args[0]);
 *
 *  sentencebank.apply(new SentenceVisitor() {
 *    public void visitSentence(final Sentence s) {
 *      // also print tag as well as word
 *      System.out.println(s.toString(false));
 *   }
 *  });
 * }
 *
 * }
 * </pre>
 * </blockquote>
 *
 * @since 1.2
 * @author Christopher Manning
 * @author Dan Klein
 */
package edu.stanford.nlp.trees;