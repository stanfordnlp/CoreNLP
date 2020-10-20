/**
 * The ObjectBank class is designed to make it easy to change the format/source
 * of data read in by other classes and to standardize how data is read in javaNLP
 * classes.  This should make reuse of existing code (by non-authors of the code)
 * easier because one has to just create a new ObjectBank which knows where to
 * look for the data and how to turn it into Objects, and then use the new
 * ObjectBank in the class.  This will also make it easier to reuse code for
 * reading in the same data.
 * <p>
 * An ObjectBank is a Collection of Objects.  These objects are taken
 * from input sources and then tokenized and parsed into the desired
 * kind of Object.  An ObjectBank requires a  ReaderIteratorFactory and an
 * IteratorFromReaderFactory.  The  ReaderIteratorFactory is used to get
 * an Iterator over java.util.Readers which contain representations of
 * the Objects.  A  ReaderIteratorFactory resembles a  Collection that
 * takes input sources and dispenses  Iterators over  java.util.Readers
 * of those sources.  An  IteratorFromReaderFactory is used to turn a single
 * java.util.Reader into an  Iterator over Objects.  The  IteratorFromReaderFactory
 * splits the contents of the  java.util.Reader into Strings and then parses them
 * into appropriate Objects.
 * <p>
 * Example Usage:
 * <p>
 * You have a collection of files in the directory /u/nlp/data/gre/questions.  Each file
 * contains several Puzzle documents which look like:
 * <pre>
 * &lt;puzzle&gt;
 * &lt;preamble&gt; some text &lt;/preamble&gt;
 * &lt;question&gt; some intro text
 * &lt;answer&gt; answer1 &lt;/answer&gt;
 * &lt;answer&gt; answer2 &lt;/answer&gt;
 * &lt;answer&gt; answer3 &lt;/answer&gt;
 * &lt;answer&gt; answer4 &lt;/answer&gt;
 * &lt;/question&gt;
 * &lt;question&gt; another question
 * &lt;answer&gt; answer1 &lt;/answer&gt;
 * &lt;answer&gt; answer2 &lt;/answer&gt;
 * &lt;answer&gt; answer3 &lt;/answer&gt;
 * &lt;answer&gt; answer4 &lt;/answer&gt;
 * &lt;/question&gt;
 * &lt;/puzzle&gt;
 * </pre>
 *
 * First you need to build a  ReaderIteratorFactory which will provide  java.io.Readers
 * over all the files in your directory:
 * <p>
 * </pre>
 * Collection c = new FileSequentialCollection("/u/nlp/data/gre/questions/", "", false);
 * ReaderIteratorFactory rif = new ReaderIteratorFactory(c);
 * </pre>
 * <p>
 * Next you need to make a  IteratorFromReaderFactory which will take the  java.io.Readers
 * vended by the  ReaderIteratorFactory, split them up into documents (Strings) and
 * then convert the Strings into Objects.  In this case we want to keep everything
 * between each set of &lt;puzzle&gt; &lt;/puzzle&gt; tags so we would use a  BeginEndIteratorFactory.
 * You would also need to write a class which extends Appliable and whose apply method
 * converts the String between the &lt;puzzle&gt; &lt;/puzzle&gt; tags into Puzzle objects.
 *
 * <pre>
 * public class PuzzleParser implements Appliable {
 * public Object apply (Object o) {
 * String s = (String)o;
 * ...
 * Puzzle p = new Puzzle(...);
 * ...
 * return p;
 * </pre>
 *
 * Now to build the  IteratorFromReaderFactory:
 *
 * <pre>
 * IteratorFromReaderFactory rtif = BeginEndIterator.getFactory("&lt;puzzle&gt;", "&lt;/puzzle&gt;", new PuzzleParser());
 * </pre>
 *
 * Now, to create your  ObjectBank you just give it the  ReaderIteratorFactory and
 * IteratorFromReaderFactory that you just created:
 *
 * <pre>
 * ObjectBank puzzles = new ObjectBank(rif, rtif);
 * </pre>
 *
 * Now, if you get a new set of puzzles that are located elsewhere and formatted differently
 * you create a new  ObjectBank for reading them in and use that  ObjectBank instead with only
 * trivial changes (or possible none at all if the ObjectBank is read in on a constructor)
 * to your code.  Or even better, if someone else wants to use your code to evaluate their puzzles,
 * which are  located elsewhere and formatted differently, they already know what they have to do
 * to make your code work for them.
 */
package edu.stanford.nlp.objectbank;
