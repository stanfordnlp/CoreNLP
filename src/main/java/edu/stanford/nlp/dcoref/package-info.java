/**
 * <h1>Multi-pass Sieve Coreference Resolution System</h1>
 *
 * <a href="#authors">[authors]</a>
 * <a href="#current">[current results]</a>
 * <a href="#changes">[changes]</a>
 * <a href="#usage">[usage]</a>
 * <p>
 * This system implements the multi-pass sieve coreference resolution system of Raghunathan et al. at EMNLP 2010.
 * (This is an older coreference system; you might also want to look at the systems in the {@code coref} package.)
 * <p>
 * Note that all the results reported here use gold mentions (just as in the paper).
 * However, the DeterministicCorefAnnotator in StanfordCoreNLP implements a simple mention detection component,
 * so this code can be used to perform coreference resolution on raw text.
 * <p>
 * Note that this code is already different from the system reported in the paper.
 * After the EMNLP paper, two additional sieves were included. The current code gives slightly better scores than those in the paper.
 *
 * <h2><a name="authors">Authors</a></h2>
 * <ul>
 * <li>Karthik Raghunathan
 * <li>Heeyoung Lee
 * <li>Sudarshan Rangarajan
 * <li>Jenny Finkel
 * <li>Nathanael Chambers
 * <li>Mihai Surdeanu
 * <li>Dan Jurafsky
 * <li>Christopher Manning
 * </ul>
 *
 * <h2><a name="current">Current Results</a></h2>
 * <pre>
 * ----------------------------------------------------------------------------
 * MUC               B cubed             Pairwise
 * P     R     F1      P     R     F1      P     R     F1
 * ----------------------------------------------------------------------------
 * ACE2004 dev   | 84.5  75.7  79.8  | 88.0  75.8  81.4  | 78.6  53.8  63.9
 * ACE2004 test  | 80.4  72.9  76.4  | 85.1  76.4  80.5  | 68.7  48.9  57.1
 * ACE2004 nwire | 83.8  74.3  78.8  | 86.9  73.7  79.7  | 78.1  51.7  62.2
 * MUC6 test     | 90.5  69.0  78.3  | 90.5  62.5  73.9  | 89.3  56.1  68.9
 * ----------------------------------------------------------------------------
 * </pre>
 * <h2><a name="changes">Changes</a></h2>
 * <h3>August 26, 2010</h3>
 * <p>
 * This release is generally similar to the code used for EMNLP 2010,
 * with one additional sieve: relaxed exact string match.<br>
 * The score may differ also due to the change in Parser or NER.
 * <p>
 * Results:
 * <pre>
 * ----------------------------------------------------------------------------
 * MUC               B cubed             Pairwise
 * P     R     F1      P     R     F1      P     R     F1
 * ----------------------------------------------------------------------------
 * ACE2004 dev   | 84.1  73.9  78.7  | 88.3  74.2  80.7  | 80.0  51.0  62.3
 * ACE2004 test  | 80.5  72.3  76.2  | 85.4  75.9  80.4  | 68.7  47.8  56.4
 * ACE2004 nwire | 83.8  72.8  77.9  | 87.5  72.1  79.0  | 79.3  47.6  59.5
 * MUC6 test     | 90.3  68.9  78.2  | 90.5  62.3  73.8  | 89.4  55.5  68.5
 * ----------------------------------------------------------------------------
 * </pre>
 * <h2><a name="usage">Usage</a></h2>
 * <p>
 * <h3> Running coreference resolution on raw text </h3>
 * This software is now fully incorporated in StanfordCoreNLP, so all you have to do is add the dcoref annotator to the "annotators" property in StanfordCoreNLP.
 * For example:
 * <pre>
 * annotators = tokenize, ssplit, pos, lemma, ner, parse, dcoref
 * </pre>
 * The required properties for dcoref are the following:
 * <pre>
 * dcoref.demonym
 * dcoref.animate
 * dcoref.inanimate
 * dcoref.male
 * dcoref.neutral
 * dcoref.female
 * dcoref.plural
 * dcoref.singular
 * sievePasses         // If omitted, default value will be used.
 * </pre>
 * <p>
 * See StanfordCoreNLP for more details.
 * </p>
 * <p>
 * <h3> How to replicate the results in our EMNLP2010 paper</h3>
 * To replicate the results in the paper run:
 * <pre>
 * java -Xmx8g edu.stanford.nlp.dcoref.SieveCoreferenceSystem -props &lt;properties file&gt;
 * </pre>
 * A sample properties file (coref.properties) is included in dcoref package.
 * The properties file includes the following:
 * <pre>
 * annotators = pos, lemma, ner    // annotators needed for coreference resolution
 * pos.model                       // For POS model
 * ner.model.3class
 * ner.model.7class                // For NER
 * ner.model.MISCclass
 * parser.model                    // For parser
 * parser.maxlen = 100
 * dcoref.demonym                  // The path for a file that includes a list of demonyms
 * dcoref.animate                  // The list of animate/inanimate mentions (Ji and Lin, 2009)
 * dcoref.inanimate
 * dcoref.male                     // The list of male/neutral/female mentions (Bergsma and Lin, 2006)
 * dcoref.neutral                  // Neutral means a mention that is usually referred by 'it'
 * dcoref.female
 * dcoref.plural                   // The list of plural/singular mentions (Bergsma and Lin, 2006)
 * dcoref.singular
 * sievePasses                     // Sieve passes - each class is defined in dcoref/sievepasses/
 * logFile                         // Path for log file for coref system evaluation
 * ace2004 or mucfile              // Use either ace2004 or mucfile (not both)
 * // ace2004: path for the directory containing ACE2004 files
 * // mucfile: path for the MUC file
 * </pre>
 * This system can process both ACE2004 and MUC6 corpora in their original formats.
 * Examples of corpus are given below.
 * MUC6:
 * <pre>
 * ...
 * &lt;s&gt; By/IN proposing/VBG &lt;COREF ID="13" TYPE="IDENT" REF="6" MIN="date"&gt; a/DT meeting/NN date/NN&lt;/COREF&gt; ,/, &lt;COREF ID="14" TYPE="IDENT" REF="0"&gt;
 * &lt;ORGANIZATION&gt; Eastern/NNP&lt;/ORGANIZATION&gt;&lt;/COREF&gt; moved/VBD one/CD step/NN closer/JJR toward/IN reopening/VBG current/JJ high-cost/JJ contract/NN agreements/NNS with/IN &lt;COREF ID="15" TYPE="IDENT" REF="8" MIN="unions"&gt;&lt;COREF ID="16" TYPE="IDENT" REF="14"&gt; its/PRP$&lt;/COREF&gt; unions/NNS&lt;/COREF&gt; ./. &lt;/s&gt;
 * ...
 * </pre>
 * ACE2004:
 * <pre>
 * ...
 * &lt;document DOCID="20001115_AFP_ARB.0212.eng"&gt;
 * &lt;entity ID="20001115_AFP_ARB.0212.eng-E1" TYPE="ORG" SUBTYPE="Educational" CLASS="SPC"&gt;
 * &lt;entity_mention ID="1-47" TYPE="NAM" LDCTYPE="NAM"&gt;
 * &lt;extent&gt;
 * &lt;charseq START="475" END="506"&gt;the Globalization Studies Center&lt;/charseq&gt;
 * &lt;/extent&gt;
 * &lt;head&gt;
 * &lt;charseq START="479" END="506"&gt;Globalization Studies Center&lt;/charseq&gt;
 * &lt;/head&gt;
 * &lt;/entity_mention&gt;
 * ...
 * </pre>
 */
package edu.stanford.nlp.dcoref;