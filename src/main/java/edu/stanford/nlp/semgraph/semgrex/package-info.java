/**
 * A package for dependency graph (i.e. SemanticGraph) pattern expressions and matching these expressions
 * to IndexedFeatureLabel instances.  The design is similar to the
 * <code>java.util.regex</code> package and based on the <code>edu.stanford.nlp.trees.tregex</code>. Internally, these expressions
 * are parsed using a parser designed with <a href="https://javacc.dev.java.net/" target = "_top">
 * the javacc "compiler compiler" utility</a>.
 * <p> See  SemgrexPattern for
 * a description of the command line utility version.
 * <p>Note that the only
 * classes which should be public are the  SemgrexMatcher,
 * SemgrexPattern and  SemgrexPatternCompiler
 * classes-- the others were automatically given public access by
 * <a href="https://javacc.dev.java.net/" target = "_top">javacc</a>
 * although really they should be package-private.
 * @author Chloe Kiddon
 */
package edu.stanford.nlp.semgraph.semgrex;