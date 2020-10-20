/**
 * A package for tree-pattern expressions and matching these expressions
 * to Tree instances.  The design is similar to the
 * {@code java.util.regex} package.  Internally, tree-pattern expressions
 * are parsed using a parser designed with <a href="https://javacc.dev.java.net/" target = "_top">
 * the javacc "compiler compiler" utility</a>.
 * <p> See {@code TregexPattern} for
 * a description of the command-line utility version.
 * <p>Note that the only
 * classes which should be public are the  TregexMatcher,
 * TregexPattern and  TregexPatternCompiler
 * classes -- the others were automatically given public access by
 * <a href="https://javacc.dev.java.net/" target = "_top">javacc</a>
 * although really they should be package-private.
 *
 * @author Roger Levy
 * @author Galen Andrew
 */
package edu.stanford.nlp.trees.tregex;