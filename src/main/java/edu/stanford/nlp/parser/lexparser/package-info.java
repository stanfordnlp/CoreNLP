/**
 * <p>
 * This package contains implementations of three probabilistic parsers for
 * natural language text.  There is an accurate unlexicalized probabilistic
 * context-free grammar (PCFG) parser, a probabilistic lexical dependency parser,
 * and a factored, lexicalized
 * probabilistic context free grammar parser, which does joint inference
 * over the product of the first two parsers.  The parser supports various
 * languages and input formats.
 * For English, for most purposes, we now recommend just using the unlexicalized PCFG.
 * With a well-engineered grammar (as supplied for English), it is
 * fast, accurate, requires much less memory, and in many real-world uses,
 * lexical preferences are
 * unavailable or inaccurate across domains or genres and the
 * unlexicalized parser will
 * perform just as well as a lexicalized parser.  However, the
 * factored parser will sometimes provide greater accuracy on English
 * through
 * knowledge of lexical dependencies.  Moreover, it is considerably better than the
 * PCFG parser alone for most other languages (with less rigid word
 * order), including German, Chinese, and Arabic.  The dependency parser
 * can be run alone, but this is
 * usually not useful (its accuracy is much lower). The output
 * of the parser can be presented in various forms, such as just part-of-speech
 * tags, phrase structure trees, or dependencies, and is controlled by options
 * passed to the TreePrint class.
 * </p>
 * <h3>References</h3>
 * <p>
 * The factored parser and the unlexicalized PCFG parser are described in:
 * <ul>
 * <li>Dan Klein and Christopher D. Manning. 2002. Fast Exact Inference with a
 * Factored Model for Natural Language Parsing. <em>Advances
 * in Neural Information Processing Systems 15 (NIPS 2002)</em>.
 * [<a href="http://nlp.stanford.edu/~manning/papers/lex-parser.pdf">pdf</a>]</li>
 * <li>Dan Klein and Christopher D. Manning. 2003. Accurate
 * Unlexicalized Parsing. <em>Proceedings of the Association for
 * 	  Computational Linguistics</em>, 2003.
 * [<a href="http://nlp.stanford.edu/~manning/papers/unlexicalized-parsing.pdf">pdf</a>]</li>
 * </ul>
 * <p>
 * The factored parser uses a product model, where the preferences of an
 * unlexicalized PCFG parser and a lexicalized dependency parser are
 * combined by a third parser, which does exact search using
 * A* outside estimates (which are Viterbi outside scores,
 * precalculated during PCFG and dependency parsing of the sentence).
 * </p>
 * <p>
 * We have been splitting up the parser into public classes, but some of
 * the internals are still contained in the file
 * {@code FactoredParser.java}.
 * </p>
 * <p>
 * The class {@code LexicalizedParser} provides an interface for
 * either
 * training a parser from a treebank, or parsing text using a saved
 * parser.  It can be called programmatically, or the commandline main()
 * method supports many options.
 * </p>
 * <p>
 * The parser has been ported to multiple languages.  German, Chinese, and Arabic
 * grammars are included.  The first publication below documents the
 * Chinese parser.  The German parser was developed for and used in the
 * second paper (but the paper contains very little detail on it).</p>
 * <ul>
 * <li>Roger Levy and Christopher D. Manning. 2003. Is it harder to
 * parse Chinese, or the Chinese Treebank?  <em>ACL 2003</em>, pp. 439-446.</li>
 * <li>Roger Levy and Christopher D. Manning. 2004. Deep dependencies from
 * context-free statistical parsers: correcting the surface dependency
 * approximation. <em>ACL 2004</em>, pp. 328-335.</li>
 * </ul>
 * <p>
 * The grammatical relations output of the parser is presented in:
 * </p>
 * <ul>
 * <li>Marie-Catherine de Marneffe, Bill MacCartney and Christopher
 * D. Manning. 2006. Generating Typed Dependency Parses from Phrase Structure
 * Parses.  <em>LREC 2006</em>.</li>
 * </ul>
 * <h3>End user usage</h3>
 * <h4>Requirements</h4>
 * <p>You need Java 1.6+ installed on your system, and
 * {@code java} in your PATH where commands are looked for.</p>
 * <p>
 * You need a machine with a fair amount of memory.  Required memory
 * depends on the choice of parser, the size of the grammar, and
 * other factors like the presence of numerous unknown words
 * To run the PCFG parser
 * on sentences of up to 40 words you need 100 MB of memory.  To be
 * able to handle longer sentences, you need more (to parse sentences
 * up to 100 words, you need 400 MB).  For running the
 * Factored Parser, 600 MB is needed for dealing with sentences
 * up to 40 words.  Factored parsing of sentences up to 200 words
 * requires around 3GB of memory.
 * Training a new lexicalized parser requires about 1500m of memory;
 * much less is needed for training a PCFG.
 * </p>
 * <p>
 * For just parsing text, you need a saved parser model (grammars, lexicon,
 * etc.), which can be
 * represented either as a text file or as a binary (Java serialized
 * object) representation, and which can be gzip compressed.
 * A number are provided contained in the supplied
 * stanford-parser-$VERSION-models.jar file in the distributed version,
 * and can be accessed from there by having this jar file on your
 * CLASSPATH and specifying them via a classpath entry such as:
 * {@code edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz}.
 * (Stanford NLP people can also find the grammars in the directory
 * {@code /u/nlp/data/lexparser}.)  Other available grammars include
 * {@code englishFactored.ser.gz} for English, and
 * {@code chineseFactored.ser.gz} for Chinese.
 * </p>
 * <p>
 * You need the parser code and grammars
 * accessible.  This can be done by having the supplied jar files on
 * your CLASSPATH.  The examples below assume you are in the parser
 * distribution home directory. From there you can set up the classpath with the
 * command-line argument {@code -cp "*"} (or perhaps {@code -cp "*;"}
 * on certain versions of Windows).
 * Then if you have some sentences in {@code testsent.txt} (as plain
 * text), the following commands should work.
 * </p>
 * <h4>Command-line parsing usage</h4>
 * <p>Parsing a local text file:</p>
 * <blockquote>
 * {@code java -mx100m -cp "*" edu.stanford.nlp.parser.lexparser.LexicalizedParser
 * edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz testsent.txt
 * }
 * </blockquote>
 * <p>Parsing a document over the web:</p>
 * <blockquote>
 * {@code java -mx100m -cp "*" edu.stanford.nlp.parser.lexparser.LexicalizedParser
 * -maxLength 40 edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz https://nlp.stanford.edu/software/lex-parser.html}
 * </blockquote>
 * <p>Note the {@code -maxLength} flag: this will set a maximum length
 * sentence to parse.  If you do not set one, the parser will try to parse
 * sentences up to any length, but will usually run out of memory when
 * trying to do this.  This is important with web pages with text that may
 * not be real sentences (or just with technical documents that turn out to
 * have 300 word sentences).
 * The parser just does very rudimentary stripping of HTML tags, and
 * so it'll work okay on plain text web pages, but it won't work
 * adequately on most complex commercial script-driven pages.  If you
 * want to handle these, you'll need to provide your own preprocessor,
 * and then to call the parser on its output.</p>
 * <p>The parser will send parse trees to {@code stdout} and other
 * information on what it is doing to {@code stderr}, so one commonly
 * wants to direct just {@code stdout} to an output file, in the
 * standard way.</p>
 * <h4>Other languages: Chinese</h4>
 * <p>Parsing a Chinese sentence (in the default input encoding for
 * Chinese of GB18030
 * - note you'll need the right fonts to see the output correctly):</p>
 * <blockquote>{@code
 * java -mx100m -cp "*" edu.stanford.nlp.parser.lexparser.LexicalizedParser -tLPP
 * edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams
 * edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz chinese-onesent
 * }</blockquote>
 * <p>or for Unicode (UTF-8) format files:</p>
 * <blockquote>{@code
 * java -mx100m -cp "*"edu.stanford.nlp.parser.lexparser.LexicalizedParser -tLPP
 * edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams
 * -encoding UTF-8 edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz chinese-onesent-utf
 * }</blockquote>
 * <p>
 * For Chinese, the package includes two simple word segmenters.  One is a
 * lexicon-based maximum match segmenter, and the other uses the parser to
 * do Hidden Markov Model-based word segmentation.  These segmentation
 * methods are okay, but if you would like a high quality segmentation of
 * Chinese text, you will have to segment the Chinese by yourself as a
 * preprocessing step.  The supplied grammars assume that
 * Chinese input has already been word-segmented according to Penn
 * Chinese Treebank conventions.  Choosing
 * Chinese with {@code -tLPP
 * edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams}
 * makes space-separated words the default tokenization.
 * To do word segmentation within the parser, give one of the options
 * {@code -segmentMarkov} or {@code -segmentMaxMatch}.
 * </p>
 * <h4>Other languages</h4>
 * <p>
 * The parser also supports other languages including German and French.
 * </p>
 * <h4>Command-line options</h4>
 * <p>The program has many options.  The most useful end-user option is
 * <code>-maxLength&nbsp<em>n</em></code> which determines the maximum
 * length sentence that the parser will parser.  Longer sentences are
 * skipped, with a message printed to {@code stderr}.</p>
 * <h5>Input formatting and tokenization options</h5>
 * <p>The parser supports many different input formats: tokenized/not,
 * sentences/not, and tagged/not.</p>
 * <p>The input may be
 * tokenized or not, and users may supply their own tokenizers. The input
 * is by default assumed to not be tokenized; if the
 * input is tokenized, supply the option {@code -tokenized}. If the
 * input is not tokenized, you may supply the name of a tokenizer class
 * with {@code -tokenizer tokenizerClassName}; otherwise the default
 * tokenizer ({@code edu.stanford.nlp.processor.PTBTokenizer}) is
 * used.  This tokenizer should perform well over typical plain
 * newswire-style text.
 * <p>The
 * input may have already been split into sentences or not. The input is by
 * default assumed
 * to be not split; if sentences are split, supply the option
 * {@code -sentences delimitingToken}, where the delimiting token
 * may be any string.  As a special case, if the delimiting token
 * is {@code "newline"} the parser will assume that each line of the
 * file is a sentence.</p>
 * <p>Simple XML can also be parsed.  The main method does not incorporate an XML
 * parser, but one can fake certain simple cases with the
 * {@code -parseInside regex} which will only parse the tokens inside
 * elements matched by the regular expression {@code regex}.  These
 * elements are assumed to be pure CDATA.
 * If you use {@code -parseInside s}, then the parser will accept
 * input in which sentences are marked XML-style with
 * &lt;s&gt;&nbsp;...&nbsp;&lt;/s&gt; (the same format as the input to
 * Eugene Charniak's parser).
 * </p>
 * <p>Finally, the input may be tagged or not. If it is tagged, the program
 * assumes that words and tags are separated by a non-whitespace
 * separating character such as '/' or '_'. You give the option
 * {@code -tagSeparator tagSeparator} to specify tagged text with a
 * tag separator. You also need to tell the parser to use a different
 * tokenizer, using the flags
 * {@code -tokenizerFactory edu.stanford.nlp.process.WhitespaceTokenizer
 * -tokenizerMethod newCoreLabelTokenizerFactory}
 * </p>
 * <p>You can see examples of many of these options in the
 * {@code test} directory. As an example, you can parse the example file with partial POS-tagging
 * with this command:</p>
 * <blockquote>{@code
 * java edu.stanford.nlp.parser.lexparser.LexicalizedParser -maxLength 20 -sentences newline -tokenized -tagSeparator / -tokenizerFactory edu.stanford.nlp.process.WhitespaceTokenizer -tokenizerMethod newCoreLabelTokenizerFactory englishPCFG.ser.gz pos-sentences.txt
 * }</blockquote>
 * <p>There are some restrictions on the interpretation of POS-tagged input:</p>
 * <ul>
 * <li>The tagset must match the parser POS set.  If you are using our
 * supplied parser data files, that means you must be using Penn Treebank
 * POS tags.
 * <li>An indicated tagging will determine which of the taggings allowed by
 * the lexicon
 * will be used, but the parser will not accept tags not allowed by its
 * lexicon.  This is usually not problematic, since rare or unknown words
 * are allowed to have many POS tags, but would be if you were trying to
 * persuade it that <em>are</em> should be tagged as a noun in the sentence <em>"100
 * are make up one hectare."</em> since it will only allow <em>are</em> to
 * 	have a verbal tagging.
 * </ul>
 * <p>
 * For the examples in {@code pos-sentences.txt}:</p>
 * <ol>
 * <li> This sentence is parsed correctly with no tags given.
 * <li> So it is also parsed correctly telling the parser <em>butter</em> is a verb.
 * <li> You get a different worse parse telling it <em>butter</em> is a noun.
 * <li> You get the same parse as 1. with all tags correctly supplied.
 * <li> It won't accept <em>can</em> as a VB, but does accept <em>butter</em>
 * 	as a noun, so you get the same parse as 3.
 * <li> <em>People can butter</em> can be an NP.
 * <LI> Most words can be NN, but not common function words like <em>their,
 * 	  with, a</em>.
 * </ol>
 * <p>
 * Note that if the program is reading tags correctly, they <em>aren't</em>
 * printed in the
 * sentence it says it is parsing.  Only the words are printed there.
 * </p>
 * <h5>Output formatting options</h5>
 * <p>You can set how sentences are printed out by using the
 * {@code -outputFormat format} option.  The native and default format is as
 * trees are formatted in the Penn Treebank, but there are a number of
 * other useful options:
 * </p>
 * <ul>
 * <li>{@code penn} The default.</li>
 * <li>{@code oneline} Printed out on one line.</li>
 * <li>{@code wordsAndTags} Use the parser as a POS tagger.</li>
 * <li>{@code latexTree} Help write your LaTeX papers (for use with
 * Avery Andrews' {@code trees.sty} package.</li>
 * <li>{@code typedDependenciesCollapsed} Write sentences in a typed
 * dependency format that represents sentences via grammatical relations
 * between words.  Suitable for representing text as a semantic network.</li>
 * </ul>
 * <p>
 * You can get each sentence printed in multiple formats by giving a
 * comma-separated list of formats.  See the TreePrint class for more
 * information on available output formats and options.
 * </p>
 * <h4>Programmatic usage</h4>
 * <p>{@code LexicalizedParser} can be easily called
 * within a larger
 * application.  It implements a couple of useful interfaces that
 * provide for simple use:
 * {@code edu.stanford.nlp.parser.ViterbiParser}
 * and {@code edu.stanford.nlp.process.Function}.
 * The following simple class shows typical usage:</p>
 * <blockquote><pre>
 * import java.util.*;
 * import edu.stanford.nlp.ling.*;
 * import edu.stanford.nlp.trees.*;
 * import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
 * class ParserDemo {
 * public static void main(String[] args) {
 * LexicalizedParser lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
 * lp.setOptionFlags(new String[]{"-maxLength", "80", "-retainTmpSubcategories"});
 * String[] sent = { "This", "is", "an", "easy", "sentence", "." };
 * List&lt;CoreLabel&gt; rawWords = Sentence.toCoreLabelList(sent);
 * Tree parse = lp.apply(rawWords);
 * parse.pennPrint();
 * System.out.println();
 * TreebankLanguagePack tlp = new PennTreebankLanguagePack();
 * GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
 * GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
 * List&lt;TypedDependency&gt; tdl = gs.typedDependenciesCCprocessed();
 * System.out.println(tdl);
 * System.out.println();
 * TreePrint tp = new TreePrint("penn,typedDependenciesCollapsed");
 * tp.printTree(parse);
 * }
 * }
 * </pre></blockquote>
 * <p>In a usage such as this, the parser expects sentences already
 * tokenized according to Penn Treebank conventions.  For arbitrary text,
 * prior processing must be done to achieve such tokenization (the
 * main method of LexicalizedParser provides an
 * example of doing this).  The example shows how most command-line
 * arguments can also be passed to the parser when called
 * programmatically. Note that using the
 * {@code -retainTmpSubcategories} option is necessary to get the best
 * results in the typed dependencies output recognizing temporal noun phrases
 * ("last week", "next February").
 * </p>
 * <p>Some code fragments which include tokenization using Penn Treebank conventions follows:</p>
 * <blockquote>
 * <pre>
 * import java.io.StringReader;
 * import edu.stanford.nlp.trees.Tree;
 * import edu.stanford.nlp.objectbank.TokenizerFactory;
 * import edu.stanford.nlp.process.CoreLabelTokenFactory;
 * import edu.stanford.nlp.ling.CoreLabel;
 * import edu.stanford.nlp.process.PTBTokenizer;
 * import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
 * LexicalizedParser lp = LexicalizedParser.loadModel("englishPCFG.ser.gz");
 * lp.setOptionFlags(new String[]{"-outputFormat", "penn,typedDependenciesCollapsed", "-retainTmpSubcategories"});
 * TokenizerFactory&lt;CoreLabel&gt; tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "");
 * public Tree processSentence(String sentence) {
 * List&lt;CoreLabel&gt; rawWords = tokenizerFactory.getTokenizer(new StringReader(sentence)).tokenize();
 * Tree bestParse = lp.parseTree(rawWords);
 * return bestParse;
 * }
 * </pre>
 * </blockquote>
 * <h4>Writing and reading trained parsers to and from files</h4>
 * <p>A trained parser consists of grammars, a lexicon, and option values. Once
 * a parser has been trained, it may be written to file in one of two
 * formats: binary serialized Java objects or human readable text data. A parser
 * can also be quickly reconstructed (either programmatically or at the command line)
 * from files containing a parser in either of these formats.</p>
 * <p>The binary serialized Java
 * objects format is created using standard tools provided by the {@code java.io}
 * package, and is not text, and not human-readable. To train and then save a parser
 * as a binary serialized objects file, use a command line invocation of the form:</p>
 * <blockquote>{@code
 * java -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser
 * -train trainFilePath [fileRange] -saveToSerializedFile outputFilePath
 * }</blockquote>
 * <p>The text data format is human readable and modifiable, and consists of
 * four sections, appearing in the following order:</p>
 * <ul>
 * <li>Options - consists of variable-value pairs, one per line, which must remain constant across training and parsing.</li>
 * <li>Lexicon - consists of lexical entries, one per line, each of which is preceded by the keyword SEEN or UNSEEN, and followed by a raw count.</li>
 * <li>Unary Grammar - consists of unary rewrite rules, one per line, each of which is of the form A -> B, followed by the normalized log probability.</li>
 * <li>Binary Grammar - consists of binary rewrite rules, one per line, each of which is of the form A -> B C, followed by the normalized log probability.</li>
 * <li>Dependency Grammar</li>
 * </ul>
 * <p>Each section is headed by a line consisting of multiple asterisks (*) and the name
 * of the section. Note that the file format does not support rules of arbitrary arity,
 * only binary and unary rules. To train and then save a parser
 * as a text data file, use a command line invocation of the form:</p>
 * <blockquote>{@code
 * java -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser
 * -train trainFilePath start stop -saveToTextFile outputFilePath
 * }</blockquote>
 * <p>To parse a file with a saved parser, either in text data or serialized data format, use a command line invocation of the following form:</p>
 * <blockquote>{@code
 * java -mx500m edu.stanford.nlp.parser.lexparser.LexicalizedParser
 * parserFilePath test.txt
 * }</blockquote>
 * <h4>A Note on Text Grammars</h4>
 * <p>If you want to use the text grammars in another parser and duplicate our
 * performance, you will need to know how we handle the POS tagging of rare
 * and unknown words:</p>
 * <ul>
 * <li>Unknown Words: rather than scoring all words unseen during
 * training with a single distribution over tags, we score unknown
 * words based on their word shape signatures, defined as
 * follows. Beginning with the original string, all lowercase
 * alphabetic characters are replaced with x, uppercase with X, digits
 * with d, and other characters are unchanged. Then, consecutive
 * duplicates are eliminated. For example, Formula-1 would become
 * Xx-1. The probability of tags given signatures is estimated on words
 * occurring in only the second half of the training data, then
 * inverted. However, in the current release of the parser, this is all
 * done programmatically, and so the text lexicon contains only a single
 * UNK token. To duplicate our behavior, one would be best off building
 * one's own lexicon with the above behavior.</li>
 * <li>Rare Words: all words with frequency less than a cut-off (of 100)
 * are allowed to take tags with which they were not seen during
 * training. In this case, they are eligible for (i) all tags that
 * either they were seen with, or (ii) any tag an unknown word can
 * receive (lexicon entry for UNK). The probability of a tag given a
 * rare word is an interpolation of the word's own tag distribution and
 * the unknown distribution for that word's signature. Because of the
 * tag-splitting used in our parser, this ability to take
 * out-of-lexicon tags is fairly important, and not represented in our
 * text lexicon.</li>
 * </ul>
 * <h4>For additional information</h4>
 * <p>
 * For more information, you should next look at the Javadocs for the
 * LexicalizedParser class.  In particular, the {@code main} method of
 * that class documents more precisely a number of the input preprocessing
 * options that were presented chattily above.
 * </p>
 * @author Dan Klein
 * @author Christopher Manning
 * @author Roger Levy
 * @author Teg Grenager
 * @author Galen Andrew
 */
package edu.stanford.nlp.parser.lexparser;