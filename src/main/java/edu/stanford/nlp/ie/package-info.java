/**
 * <p>
 * This package implements various subpackages for information extraction.
 * Some examples of use appear later in this description.
 * At the moment, three types of information extraction are supported
 * (where some of these have internal variants):
 * </p>
 * <ol>
 * <li>Regular expression based matching: These extractors are hand-written
 * and match whatever the regular expression matches.</li>
 * <li>Conditional Random Fields classifier: A sequence tagger based on
 * CRF model that can be used for NER tagging and other sequence labeling tasks.</li>
 * <li>Conditional Markov Model classifier: A classifier based on
 * CMM model that can be used for NER tagging and other labeling tasks.</li>
 * <li>Hidden Markov model based extractors:  These can be either single
 * 	field extractors or two level HMMs where the individual
 * 	component models and how they are glued together is trained
 * 	separately.  These models are trained automatically, but require tagged
 * 	training data.</li>
 * <li>Description extractor: This does higher level NLP analysis of
 * 	sentences (using a POS tagger and chunker) to find sentences
 * 	that describe an object.  This might be a biography of a person,
 * 	or a description of an animal.  This module is fixed: there is
 * 	nothing to write or train (unless one wants to start to change
 * 	its internal behavior).
 * </ol>
 * <p>
 * There are some demonstrations of the stuff here which you can run (and several
 * other classes have <code>main()</code> methods which exhibit their
 * functionality):
 * </p>
 * <ol>
 * <li><code>NERGUI</code> is a simple GUI front-end to the NER tagging
 * 	components.</li>
 * <li><code>crf/NERGUI</code> is a simple GUI front-end to the CRF-based NER tagging
 * 	components.  This version only supports the CRF-based NER tagger.</li>
 * <li><code>demo/NERDemo</code> is a simple class examplifying the programmatical use
 * of the CRF-based NER tagger.</li>
 * </ol>
 * <h3>Usage examples</h3>
 * <p>
 * 0. <i>Setup:</i> For all of these examples except 3., you need to be
 * connected to the Internet, and for the application's web search module
 * to be
 * able to connect to search engines.  The web search
 * functionality is provided by the supplied <code>edu.stanford.nlp.web</code>
 * package.  How web search works is controlled
 * by a <code>websearch.init</code> file in your current directory (or if
 * none is present, you will get search results from AltaVista).  If
 * you are registered to use the GoogleAPI, you should probably edit
 * this file so web queries can be done to Google using their SOAP
 * interface.  Even if not, you can specify additional or different
 * search engines to access in <code>websearch.init</code>.
 * A copy of this file is supplied in the distribution.  The
 * <code>DescExtractor</code> in 4. also requires another init file so that
 * it can use the include part-of-speech tagger.
 * <p>
 * 1. Corporate Contact Information.  This illustrates simple information
 * extraction from a web page.
 * Using the included
 * <code>ExtractDemo.bat</code> or by hand run:
 * <code>java edu.stanford.nlp.ie.ExtractDemo</code>
 * </p>
 * <ul>
 * <li>Select as Extractor Directory the folder:
 * <code>serialized-extractors/companycontact</code></li>
 * <li>Select as an Ontology the one in
 * <code>serialized-extractors/companycontact/Corporation-Information.kaon</code>
 * </li>
 * <li>Enter <code>Corporation</code> as the Concept to extract.</li>
 * <li>You can then do various searches:
 * <ul>
 * <li>You can enter a URL, click <code>Extract</code>, and look at the results:
 * <ul>
 * <li><code>http://www.ziatech.com/</code></li>
 * <li><code>http://www.cs.stanford.edu/</code></li>
 * <li><code>http://www.ananova.com/business/story/sm_635565.html</code></li>
 * </ul>
 * The components will work reasonably well on clean-ish text pages like
 * this.  They work even better on text such as newswire or press
 * releases, as one can demonstrate either over the web or using the
 * command line extractor</li>
 * <li>You can do a search for a term and get extraction from the top
 * search hits, by entering a term in the "Search for words" box and
 * 	    pressing "Extract":
 * <ul>
 * <li><code>Audiovox Corporation</code>
 * </ul>
 * Extraction is done over a number of pages from a search engine, and the
 * 	    results from each are shown.  Typically some of these pages
 * 	    will have suitable content to extract, and some just won't.
 * </ul>
 * </ul>
 * <p>2. Corporate Contact Information merged.  This illustrates the addition
 * of information merger across web pages.  Using the included
 * <code>MergeExtractDemo.bat</code> or similarly do:</p>
 * <center><code>java edu.stanford.nlp.ie.ExtractDemo -m</code></center>
 * <p>
 * The <code>ExtractDemo</code> screen is similar, but adds a button to
 * Select a Merger.
 * </p>
 * <ul>
 * <li>Select an Extractor Directory and Ontology as
 * above.</li>
 * <li>Click on "Select Merger" and then navigate to
 * <code>serialized-extractors/mergers</code> and Select the file
 * <code>unscoredmerger.obj</code>.</li>
 * <li>Enter the concept "Corporation" as before.
 * <li>One can now do search as above, by URL or search, but Merger is only
 * 	appropriate to a word search with multiple results.   Try Search
 * 	for words:
 * <ul>
 * <li><code>Audiovox Corporation</code></li>
 * </ul>
 * and press "Extract".  Results gradually appear.  After all results have
 * 	been processed (this may take a few seconds), a Merged best
 * 	extracted information result will be produced and displayed as
 * 	the first of the results.  "Merged Instance" will appear on the
 * 	bottom line corresponding to it, rather than a URL.
 * </ul>
 * <p>3. Company names via direct use of an HMM information extractor.
 * One can also train, load, and use HMM information extractors directly,
 * 	  without using any of the RDF-based KAON framework
 * (<code>http://kaon.semanticweb.org/</code>) used by ExtractDemo.
 * </p>
 * <ul>
 * <li>The file <code>edu.stanford.nlp.ie.hmm.Tester</code> illustrates the use
 * 	  of a pretrained HMM on data via the command line interface:
 * <ul>
 * <li><code>cd serialized-extractors/companycontact/</code></li>
 * <li><code>java edu.stanford.nlp.ie.hmm.Tester cisco.txt company
 * 	      company-name.hmm</code></li>
 * <li><code>java edu.stanford.nlp.ie.hmm.Tester EarningsReports.txt
 * 	      company company-name.hmm</code></li>
 * <li><code>java edu.stanford.nlp.ie.hmm.Tester companytest.txt
 * 	      company company-name.hmm</code></li>
 * </ul>
 * <p>
 * The first shows the HMM running on an unmarked up file with a single
 * 	document.  The second shows a <code>Corpus</code> of several
 * 	documents, separated with ENDOFDOC, used as a document delimiter
 * 	inside a Corpus.  This second use of <code>Tester</code> expects to
 * normally have an annotated corpus on which it can score its answers.
 * Here, the corpus is unannotated, and so some of the output is
 * 	inappropriate, but it shows what is selected as the company name
 * 	for each document (it's <i>mostly</i> correct...).
 * The final example shows it running on a corpus that does have answers
 * marked in it.  It does the testing with the XML elements stripped, but
 * 	then uses them to evaluate correctness.
 * </p>
 * </li>
 * <li>To train one's own HMM, one needs data where one or
 * 	    more fields is annotated in the data in the style of an XML
 * 	    element, with all the documents in one file, separated by
 * 	    lines with <code>ENDOFDOC</code> on them.  Then one can
 * 	    train (and then test) as follows.   Training an HMM
 * 	    (optimizing all its probabilities) takes a <i>long</i> time
 * 	    (it depends on the speed of the computer, but 10 minutes or
 * 	so to adjust probabilities for a fixed structure, and often
 * 	hours if one additionally attempts structure learning).
 * <ol>
 * <li><code>cd edu/stanford/nlp/ie/training/</code></li>
 * <li><code>java -server edu.stanford.nlp.ie.hmm.Trainer companydata.txt
 * 		  company mycompany.hmm</code></li>
 * <li><code>java edu.stanford.nlp.ie.hmm.HMMSingleFieldExtractor Company
 * 		  mycompany.hmm mycompany.obj</code></li>
 * <li><code>java edu.stanford.nlp.ie.hmm.Tester testdoc.txt company
 * 		  mycompany.hmm</code></li>
 * </ol>
 * The third step converts a serialized HMM into the serialized objects used
 * 	    in <code>ExtractDemo</code>.  Note that <code>company</code>
 * 	    in the second line must match the element name in the
 * 	    marked-up data that you will train on, while
 * 	    <code>Company</code> in the third line must match the
 * 	    relation name in the ontology over which you will extract with
 * 	    <code>mycompany.obj</code>.  These two names need not be the
 * 	    same.  The last step then runs the trained HMM on a file.
 * </li>
 * </ul>
 * <p>4. Extraction of descriptions (such as biographical information about
 * 	  a person or a description of an animal).
 * This does extraction of such descriptions
 * from a web page.  This component uses a POS tagger, and looks for where
 * 	  to find a path to it in the file
 * 	  <code>descextractor.init</code> in the current directory.  So,
 * 	  you should be in the root directory of the current archive,
 * 	  which has such a file.  Double click on the included
 * <code>MergeExtractDemo.bat</code> in that directory, or by hand
 * 	  one can equivalently do:
 * <code>java edu.stanford.nlp.ie.ExtractDemo -m</code>
 * </p>
 * <ul>
 * <li>Select as Extractor Directory the folder:
 * <code>serialized-extractors/description</code></li>
 * <li>Select as an Ontology the one in
 * <code>serialized-extractors/description/Entity-NameDescription.kaon</code>
 * </li>
 * <li>Click on "Select Merger" and then navigate to
 * <code>serialized-extractors/mergers</code> and Select the file
 * <code>unscoredmerger.obj</code>.</li>
 * <li>Enter <code>Entity</code> as the Concept to extract.
 * <li>You can then do various searches for people or animals by entering
 * 	    words in the "Search for words" box and pressing Extract:
 * <ul>
 * <li><code>Gareth Evans</code></li>
 * <li><code>Tawny Frogmouth</code></li>
 * <li><code>Christopher Manning</code></li>
 * <li><code>Joshua Nkomo</code></li>
 * </ul>
 * The first search will be slower than subsequent searches, as it takes a
 * 	    while to load the part of speech tagger.
 * </li>
 * </ul>
 */
package edu.stanford.nlp.ie;