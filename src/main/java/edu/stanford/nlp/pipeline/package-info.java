/**
 * <h1>Linguistic Annotation Pipeline</h1>
 * The point of this package is to enable people to quickly and
 * painlessly get complete linguistic annotations of their text.  It
 * is designed to be highly flexible and extensible.  I will first discuss
 * the organization and functions of the classes, and then I will give some
 * sample code and a run-down of the implemented Annotators.
 * <p>
 * <h2>Annotation</h2>
 * An Annotation is the data structure which holds the results of annotators.
 * An Annotations is basically a map, from keys to bits of annotation, such
 * as the parse, the part-of-speech tags, or named entity tags.  Annotations
 * are designed to operate at the sentence-level, however depending on the
 * Annotators you use this may not be how you choose to use the package.
 * <h2>Annotators</h2>
 * The backbone of this package are the Annotators.  Annotators are a lot like
 * functions, except that they operate over Annotations instead of Objects.
 * They do things like tokenize, parse, or NER tag sentences.  In the
 * javadocs of your Annotator you should specify what the Annotator is
 * assuming already exists (for instance, the NERAnnotator assumes that the
 * sentence has been tokenized) and where to find these annotations (in
 * the example from the previous set of parentheses, it would be
 * <code>TextAnnotation.class</code>).  They should also specify what they add
 * to the annotation, and where.
 * <h2>AnnotationPipeline</h2>
 * An AnnotationPipeline is where many Annotators are strung together
 * to form a linguistic annotation pipeline.  It is, itself, an
 * Annotator.  AnnotationPipelines usually also keep track of how much time
 * they spend annotating and loading to assist users in finding where the
 * time sinks are.
 * However, the class AnnotationPipeline is not meant to be used as is.
 * It serves as an example on how to build your own pipeline.
 * If you just want to use a typical NLP pipeline take a look at StanfordCoreNLP
 * (described later in this document).
 * <h2>Sample Usage</h2>
 * Here is some sample code which illustrates the intended usage
 * of the package:
 * <pre>
 * public void testPipeline(String text) throws Exception {
 * // create pipeline
 * AnnotationPipeline pipeline = new AnnotationPipeline();
 * pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
 * pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
 * pipeline.addAnnotator(new POSTaggerAnnotator(false));
 * pipeline.addAnnotator(new MorphaAnnotator(false));
 * pipeline.addAnnotator(new NERCombinerAnnotator(false));
 * pipeline.addAnnotator(new ParserAnnotator(false, -1));
 * // create annotation with text
 * Annotation document = new Annotation(text);
 * // annotate text with pipeline
 * pipeline.annotate(document);
 * // demonstrate typical usage
 * for (CoreMap sentence: document.get(CoreAnnotations.SentencesAnnotation.class)) {
 * // get the tree for the sentence
 * Tree tree = sentence.get(TreeAnnotation.class);
 * // get the tokens for the sentence and iterate over them
 * for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
 * // get token attributes
 * String tokenText = token.get(TextAnnotation.class);
 * String tokenPOS = token.get(PartOfSpeechAnnotation.class);
 * String tokenLemma = token.get(LemmaAnnotation.class);
 * String tokenNE = token.get(NamedEntityTagAnnotation.class);
 * }
 * }
 * }
 * </pre>
 * <h2>Existing Annotators</h2>
 * There already exist Annotators for many common tasks, all of which include
 * default model locations, so they can just be used off the shelf.  They are:
 * <ul>
 * <li>TokenizerAnnotator - tokenizes the text based on language or Tokenizer class specifications </li>
 * <li>WordsToSentencesAnnotator - splits a sequence of words into a sequence of sentences</li>
 * <li>POSTaggerAnnotator - annotates the text with part-of-speech tags </li>
 * <li>MorphaAnnotator - morphological normalizer (generates lemmas)</li>
 * <li>NERClassifierCombiner - combines several NER models </li>
 * <li>TrueCaseAnnotator - detects the true case of words in free text (useful for all upper or lower case text)</li>
 * <li>ParserAnnotator - generates constituent and dependency trees</li>
 * <li>NumberAnnotator - recognizes numerical entities such as numbers, money, times, and dates</li>
 * <li>TimeWordAnnotator - recognizes common temporal expressions, such as "teatime"</li>
 * <li>QuantifiableEntityNormalizingAnnotator - normalizes the content of all numerical entities</li>
 * <li>DeterministicCorefAnnotator - implements anaphora resolution using a deterministic model </li>
 * <li>NFLAnnotator - implements entity and relation mention extraction for the NFL domain</li>
 * </ul>
 * <h2>How Do I Use This?</h2>
 * You do not have to construct your pipeline from scratch! For the typical NL processors, use
 * StanfordCoreNLP. This pipeline implements the most common functionality needed: tokenization,
 * lemmatization, POS tagging, NER, parsing and coreference resolution. Read below for how to use
 * this pipeline from the command line, or directly in your Java code.
 * <h3>Using StanfordCoreNLP from the Command Line</h3>
 * The command line for StanfordCoreNLP is:
 * <pre>
 * ./bin/stanfordcorenlp.sh
 * </pre>
 * or
 * <pre>
 * java -cp stanford-corenlp-YYYY-MM-DD.jar:stanford-corenlp-YYYY-MM-DD-models.jar:xom.jar:joda-time.jar -Xmx3g edu.stanford.nlp.pipeline.StanfordCoreNLP [ -props YOUR_CONFIGURATION_FILE ] -file YOUR_INPUT_FILE
 * </pre>
 * where the following properties are defined:
 * (if <code>-props</code> or <code>annotators</code> is not defined, default properties will be loaded via the classpath)
 * <pre>
 * 	"annotators" - comma separated list of annotators
 * 		The following annotators are supported: tokenize, ssplit, pos, lemma, ner, truecase, parse, dcoref, nfl
 * </pre>
 * More information is available here: <a href="http://nlp.stanford.edu/software/corenlp.shtml">Stanford CoreNLP</a>
 * <!--
 * where the following properties are defined:
 * (if <code>-props</code> or <code>annotators</code> is not defined, default properties will be loaded via the classpath)
 * <pre>
 * 	"annotators" - comma separated list of annotators
 * 		The following annotators are supported: tokenize, ssplit, pos, lemma, ner, truecase, parse, coref, dcoref, nfl
 * 	If annotator "pos" is defined:
 * 	"pos.model" - path towards the POS tagger model
 * 	If annotator "ner" is defined:
 * 	"ner.model.3class" - path towards the three-class NER model
 * 	"ner.model.7class" - path towards the seven-class NER model
 * 	"ner.model.MISCclass" - path towards the NER model with a MISC class
 *      "ner.docdate.useFixedDate" - a reference date to use when processing underspecified time values such as "January 5th" or "this Friday"
 * 	If annotator "truecase" is defined:
 * 	"truecase.model" - path towards the true-casing model; default: StanfordCoreNLPModels/truecase/noUN.ser.gz
 * 	"truecase.bias" - class bias of the true case model; default: INIT_UPPER:-0.7,UPPER:-0.7,O:0
 * 	"truecase.mixedcasefile" - path towards the mixed case file; default: StanfordCoreNLPModels/truecase/MixDisambiguation.list
 * 	If annotator "nfl" is defined:
 * 	"nfl.gazetteer" - path towards the gazetteer for the NFL domain
 * 	"nfl.relation.model" - path towards the NFL relation extraction model
 * 	If annotator "parse" is defined:
 * 	"parser.model" - path towards the PCFG parser model
 * Command line properties:
 * 	"file" - run the pipeline on the contents of this file, or on the contents of the files in this directory
 * 	         XML output is generated for every input file "file" as file.xml
 * 	"extension" - if -file used with a directory, process only the files with this extension
 * 	"filelist" - run the pipeline on the list of files given in this file
 * 	             XML output is generated for every input file as file.outputExtension
 * 	"outputDirectory" - where to put XML output (defaults to the current directory)
 * 	"outputExtension" - extension to use for the output file (defaults to ".xml").  Don't forget the dot!
 * 	"replaceExtension" - flag to chop off the last extension before adding outputExtension to file
 * "noClobber" - don't automatically override (clobber) output files that already exist
 * </pre>
 * If none of the above are present, run the pipeline in an interactive shell (default properties will be loaded from the classpath).
 * The shell accepts input from stdin and displays the output at stdout.
 * To avoid clutter in the command line you can store some or all of these properties in a
 * properties file and pass this file to <code>StanfordCoreNLP</code> using the <code>-props</code> option. For example,
 * my <code>pipe.properties</code> file contains the following:
 * <pre>
 * annotators=tokenize,ssplit,pos,lemma,ner,parse,coref
 * pos.model=models/left3words-wsj-0-18.tagger
 * ner.model.3class=models/ner-en-3class.crf.gz
 * ner.model.7class=models/all.7class.crf.gz
 * ner.model.distsim=models/conll.distsim.crf.ser.gz
 * #nfl.gazetteer = models/NFLgazetteer.txt
 * #nfl.relation.model = models/nfl_relation_model.ser
 * parser.model=models/englishPCFG.ser.gz
 * coref.model=models/coref/corefClassifierAll.March2009.ser.gz
 * coref.name.dir=models/coref
 * wordnet.dir=models/wordnet-3.0-prolog
 * </pre>
 * Using this properties file, I run the pipeline's interactive shell as follows:
 * <pre>
 * java -cp classes/:lib/xom.jar -Xmx2g edu.stanford.nlp.pipeline.StanfordCoreNLP -props pipe.properties
 * </pre>
 * In the above setup, the system displays a shell-like prompt and waits for stdin input.
 * You can input any English text.
 * Processing starts after each new line and the output is displayed at the standard output in a format (somewhat) interpretable by humans.
 * For example, for the input "Reagan announced he had Alzheimer's disease, an incurable brain affliction." the shell displays the following output:
 * <pre>
 * [Text=Reagan PartOfSpeech=NNP Lemma=Reagan NamedEntityTag=PERSON] [Text=announced PartOfSpeech=VBD Lemma=announce NamedEntityTag=O] [Text=he PartOfSpeech=PRP Lemma=he NamedEntityTag=O] [Text=had PartOfSpeech=VBD Lemma=have NamedEntityTag=O] [Text=Alzheimer PartOfSpeech=NNP Lemma=Alzheimer NamedEntityTag=O] [Text='s PartOfSpeech=POS Lemma='s NamedEntityTag=O] [Text=disease PartOfSpeech=NN Lemma=disease NamedEntityTag=O] [Text=, PartOfSpeech=, Lemma=, NamedEntityTag=O] [Text=an PartOfSpeech=DT Lemma=a NamedEntityTag=O] [Text=incurable PartOfSpeech=JJ Lemma=incurable NamedEntityTag=O] [Text=brain PartOfSpeech=NN Lemma=brain NamedEntityTag=O] [Text=affliction PartOfSpeech=NN Lemma=affliction NamedEntityTag=O] [Text=. PartOfSpeech=. Lemma=. NamedEntityTag=O]
 * (ROOT
 * (S
 * (NP (NNP Reagan))
 * (VP (VBD announced)
 * (SBAR
 * (S
 * (NP (PRP he))
 * (VP (VBD had)
 * (NP
 * (NP
 * (NP (NNP Alzheimer) (POS 's))
 * (NN disease))
 * (, ,)
 * (NP (DT an) (JJ incurable) (NN brain) (NN affliction)))))))
 * (. .)))
 * nsubj(announced-2, Reagan-1)
 * nsubj(had-4, he-3)
 * ccomp(announced-2, had-4)
 * poss(disease-7, Alzheimer-5)
 * dobj(had-4, disease-7)
 * det(affliction-12, an-9)
 * amod(affliction-12, incurable-10)
 * nn(affliction-12, brain-11)
 * appos(disease-7, affliction-12)
 * </pre>
 * where the first part of the output shows the individual words and their attributes, e.g., POS and NE tags,
 * the second block shows the constituent parse tree, and the last block shows the syntactic dependencies extracted from the parse tree.
 * Note that the coreference chains are stored in the individual words.
 * For example, the referent for the "he" pronoun is stored as "CorefDest=1 1", which means that the referent is the first token
 * in the first sentence in this text, i.e., "Reagan".
 * <p>
 * Alternatively, if you want to process all the .txt files in the directory data/, use this command line:
 * <pre>
 * java -cp classes/:lib/xom.jar -Xmx6g edu.stanford.nlp.pipeline.StanfordCoreNLP -props pipe.properties -file data -extension .txt
 * </pre>
 * Or, you can store all the files that you want processed one per line in a separate file, and pass the latter file to
 * StanfordCoreNLP with the following options:
 * <pre>
 * java -cp classes/:lib/xom.jar -Xmx6g edu.stanford.nlp.pipeline.StanfordCoreNLP -props pipe.properties -filelist list_of_files_to_process.txt
 * </pre>
 * In the latter cases the pipeline generates a file.txt.xml output file for every file.txt it processes.
 * For example, if file.txt contains the following text:
 * <pre>
 * Federal Reserve Chairman Ben Bernanke declared Friday
 * that the U.S. economy is on the verge of a long-awaited recovery.
 * </pre>
 * the pipeline generates the following XML output in file.txt.xml:
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 * &lt;root xmlns="http://nlp.stanford.edu"&gt;
 * &lt;sentence&gt;
 * &lt;wordTable&gt;
 * &lt;wordInfo id="1"&gt;
 * &lt;word&gt;Federal&lt;/word&gt;
 * &lt;lemma&gt;Federal&lt;/lemma&gt;
 * &lt;POS&gt;NNP&lt;/POS&gt;
 * &lt;NER&gt;ORGANIZATION&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="2"&gt;
 * &lt;word&gt;Reserve&lt;/word&gt;
 * &lt;lemma&gt;Reserve&lt;/lemma&gt;
 * &lt;POS&gt;NNP&lt;/POS&gt;
 * &lt;NER&gt;ORGANIZATION&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="3"&gt;
 * &lt;word&gt;Chairman&lt;/word&gt;
 * &lt;lemma&gt;Chairman&lt;/lemma&gt;
 * &lt;POS&gt;NNP&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="4"&gt;
 * &lt;word&gt;Ben&lt;/word&gt;
 * &lt;lemma&gt;Ben&lt;/lemma&gt;
 * &lt;POS&gt;NNP&lt;/POS&gt;
 * &lt;NER&gt;PERSON&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="5"&gt;
 * &lt;word&gt;Bernanke&lt;/word&gt;
 * &lt;lemma&gt;Bernanke&lt;/lemma&gt;
 * &lt;POS&gt;NNP&lt;/POS&gt;
 * &lt;NER&gt;PERSON&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="6"&gt;
 * &lt;word&gt;declared&lt;/word&gt;
 * &lt;lemma&gt;declare&lt;/lemma&gt;
 * &lt;POS&gt;VBD&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="7"&gt;
 * &lt;word&gt;Friday&lt;/word&gt;
 * &lt;lemma&gt;Friday&lt;/lemma&gt;
 * &lt;POS&gt;NNP&lt;/POS&gt;
 * &lt;NER&gt;DATE&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="8"&gt;
 * &lt;word&gt;that&lt;/word&gt;
 * &lt;lemma&gt;that&lt;/lemma&gt;
 * &lt;POS&gt;IN&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="9"&gt;
 * &lt;word&gt;the&lt;/word&gt;
 * &lt;lemma&gt;the&lt;/lemma&gt;
 * &lt;POS&gt;DT&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="10"&gt;
 * &lt;word&gt;U.S.&lt;/word&gt;
 * &lt;lemma&gt;U.S.&lt;/lemma&gt;
 * &lt;POS&gt;NNP&lt;/POS&gt;
 * &lt;NER&gt;LOCATION&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="11"&gt;
 * &lt;word&gt;economy&lt;/word&gt;
 * &lt;lemma&gt;economy&lt;/lemma&gt;
 * &lt;POS&gt;NN&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="12"&gt;
 * &lt;word&gt;is&lt;/word&gt;
 * &lt;lemma&gt;be&lt;/lemma&gt;
 * &lt;POS&gt;VBZ&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="13"&gt;
 * &lt;word&gt;on&lt;/word&gt;
 * &lt;lemma&gt;on&lt;/lemma&gt;
 * &lt;POS&gt;IN&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="14"&gt;
 * &lt;word&gt;the&lt;/word&gt;
 * &lt;lemma&gt;the&lt;/lemma&gt;
 * &lt;POS&gt;DT&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="15"&gt;
 * &lt;word&gt;verge&lt;/word&gt;
 * &lt;lemma&gt;verge&lt;/lemma&gt;
 * &lt;POS&gt;NN&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="16"&gt;
 * &lt;word&gt;of&lt;/word&gt;
 * &lt;lemma&gt;of&lt;/lemma&gt;
 * &lt;POS&gt;IN&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="17"&gt;
 * &lt;word&gt;a&lt;/word&gt;
 * &lt;lemma&gt;a&lt;/lemma&gt;
 * &lt;POS&gt;DT&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="18"&gt;
 * &lt;word&gt;long-awaited&lt;/word&gt;
 * &lt;lemma&gt;long-awaited&lt;/lemma&gt;
 * &lt;POS&gt;JJ&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="19"&gt;
 * &lt;word&gt;recovery&lt;/word&gt;
 * &lt;lemma&gt;recovery&lt;/lemma&gt;
 * &lt;POS&gt;NN&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;wordInfo id="20"&gt;
 * &lt;word&gt;.&lt;/word&gt;
 * &lt;lemma&gt;.&lt;/lemma&gt;
 * &lt;POS&gt;.&lt;/POS&gt;
 * &lt;NER&gt;O&lt;/NER&gt;
 * &lt;/wordInfo&gt;
 * &lt;/wordTable&gt;
 * &lt;parse&gt;(ROOT
 * (S
 * (NP (NNP Federal) (NNP Reserve) (NNP Chairman) (NNP Ben) (NNP Bernanke))
 * (VP (VBD declared)
 * (NP-TMP (NNP Friday))
 * (SBAR (IN that)
 * (S
 * (NP (DT the) (NNP U.S.) (NN economy))
 * (VP (VBZ is)
 * (PP (IN on)
 * (NP
 * (NP (DT the) (NN verge))
 * (PP (IN of)
 * (NP (DT a) (JJ long-awaited) (NN recovery)))))))))
 * (. .)))&lt;/parse&gt;
 * &lt;dependencies&gt;
 * &lt;dep type="nn"&gt;
 * &lt;governor idx="5"&gt;Bernanke&lt;/governor&gt;
 * &lt;dependent idx="1"&gt;Federal&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="nn"&gt;
 * &lt;governor idx="5"&gt;Bernanke&lt;/governor&gt;
 * &lt;dependent idx="2"&gt;Reserve&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="nn"&gt;
 * &lt;governor idx="5"&gt;Bernanke&lt;/governor&gt;
 * &lt;dependent idx="3"&gt;Chairman&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="nn"&gt;
 * &lt;governor idx="5"&gt;Bernanke&lt;/governor&gt;
 * &lt;dependent idx="4"&gt;Ben&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="nsubj"&gt;
 * &lt;governor idx="7"&gt;Friday&lt;/governor&gt;
 * &lt;dependent idx="5"&gt;Bernanke&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="dep"&gt;
 * &lt;governor idx="7"&gt;Friday&lt;/governor&gt;
 * &lt;dependent idx="6"&gt;declared&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="complm"&gt;
 * &lt;governor idx="12"&gt;is&lt;/governor&gt;
 * &lt;dependent idx="8"&gt;that&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="det"&gt;
 * &lt;governor idx="11"&gt;economy&lt;/governor&gt;
 * &lt;dependent idx="9"&gt;the&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="nn"&gt;
 * &lt;governor idx="11"&gt;economy&lt;/governor&gt;
 * &lt;dependent idx="10"&gt;U.S.&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="nsubj"&gt;
 * &lt;governor idx="12"&gt;is&lt;/governor&gt;
 * &lt;dependent idx="11"&gt;economy&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="ccomp"&gt;
 * &lt;governor idx="7"&gt;Friday&lt;/governor&gt;
 * &lt;dependent idx="12"&gt;is&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="prep"&gt;
 * &lt;governor idx="12"&gt;is&lt;/governor&gt;
 * &lt;dependent idx="13"&gt;on&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="det"&gt;
 * &lt;governor idx="15"&gt;verge&lt;/governor&gt;
 * &lt;dependent idx="14"&gt;the&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="pobj"&gt;
 * &lt;governor idx="13"&gt;on&lt;/governor&gt;
 * &lt;dependent idx="15"&gt;verge&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="prep"&gt;
 * &lt;governor idx="15"&gt;verge&lt;/governor&gt;
 * &lt;dependent idx="16"&gt;of&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="det"&gt;
 * &lt;governor idx="19"&gt;recovery&lt;/governor&gt;
 * &lt;dependent idx="17"&gt;a&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="amod"&gt;
 * &lt;governor idx="19"&gt;recovery&lt;/governor&gt;
 * &lt;dependent idx="18"&gt;long-awaited&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;dep type="pobj"&gt;
 * &lt;governor idx="16"&gt;of&lt;/governor&gt;
 * &lt;dependent idx="19"&gt;recovery&lt;/dependent&gt;
 * &lt;/dep&gt;
 * &lt;/dependencies&gt;
 * &lt;/sentence&gt;
 * &lt;/root&gt;
 * </pre>
 * <p>
 * If the NFL annotator is enabled, additional XML output is generated for the corresponding domain-specific entities and relations.
 * For example, for the sentence "The 49ers beat Dallas 20-10 in the Sunday game." the NFL-specific output is:
 * <pre>
 * &lt;MachineReading&gt;
 * &lt;entities&gt;
 * &lt;entity id="EntityMention1"&gt;
 * &lt;type&gt;NFLTeam&lt;/type&gt;
 * &lt;span start="1" end="2" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention2"&gt;
 * &lt;type&gt;NFLTeam&lt;/type&gt;
 * &lt;span start="3" end="4" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention3"&gt;
 * &lt;type&gt;FinalScore&lt;/type&gt;
 * &lt;span start="4" end="5" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention4"&gt;
 * &lt;type&gt;FinalScore&lt;/type&gt;
 * &lt;span start="6" end="7" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention5"&gt;
 * &lt;type&gt;Date&lt;/type&gt;
 * &lt;span start="9" end="10" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention6"&gt;
 * &lt;type&gt;NFLGame&lt;/type&gt;
 * &lt;span start="10" end="11" /&gt;
 * &lt;/entity&gt;
 * &lt;/entities&gt;
 * &lt;relations&gt;
 * &lt;relation id="RelationMention-11"&gt;
 * &lt;type&gt;teamScoringAll&lt;/type&gt;
 * &lt;arguments&gt;
 * &lt;entity id="EntityMention3"&gt;
 * &lt;type&gt;FinalScore&lt;/type&gt;
 * &lt;span start="4" end="5" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention1"&gt;
 * &lt;type&gt;NFLTeam&lt;/type&gt;
 * &lt;span start="1" end="2" /&gt;
 * &lt;/entity&gt;
 * &lt;/arguments&gt;
 * &lt;/relation&gt;
 * &lt;relation id="RelationMention-17"&gt;
 * &lt;type&gt;teamScoringAll&lt;/type&gt;
 * &lt;arguments&gt;
 * &lt;entity id="EntityMention4"&gt;
 * &lt;type&gt;FinalScore&lt;/type&gt;
 * &lt;span start="6" end="7" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention2"&gt;
 * &lt;type&gt;NFLTeam&lt;/type&gt;
 * &lt;span start="3" end="4" /&gt;
 * &lt;/entity&gt;
 * &lt;/arguments&gt;
 * &lt;/relation&gt;
 * &lt;relation id="RelationMention-20"&gt;
 * &lt;type&gt;teamFinalScore&lt;/type&gt;
 * &lt;arguments&gt;
 * &lt;entity id="EntityMention4"&gt;
 * &lt;type&gt;FinalScore&lt;/type&gt;
 * &lt;span start="6" end="7" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention6"&gt;
 * &lt;type&gt;NFLGame&lt;/type&gt;
 * &lt;span start="10" end="11" /&gt;
 * &lt;/entity&gt;
 * &lt;/arguments&gt;
 * &lt;/relation&gt;
 * &lt;relation id="RelationMention-25"&gt;
 * &lt;type&gt;gameDate&lt;/type&gt;
 * &lt;arguments&gt;
 * &lt;entity id="EntityMention5"&gt;
 * &lt;type&gt;Date&lt;/type&gt;
 * &lt;span start="9" end="10" /&gt;
 * &lt;/entity&gt;
 * &lt;entity id="EntityMention6"&gt;
 * &lt;type&gt;NFLGame&lt;/type&gt;
 * &lt;span start="10" end="11" /&gt;
 * &lt;/entity&gt;
 * &lt;/arguments&gt;
 * &lt;/relation&gt;
 * &lt;/relations&gt;
 * &lt;/MachineReading&gt;
 * </pre>
 * -->
 * <h3>The StanfordCoreNLP API</h3>
 * More information is available here: <a href="http://nlp.stanford.edu/software/corenlp.shtml">Stanford CoreNLP</a>
 * <!--
 * <p>
 * To construct a pipeline object from a given set of properties, use StanfordCoreNLP(Properties props).
 * This method creates the pipeline using the annotators given in the "annotators" property (see above for the complete list of properties).
 * Currently, we support the following options for the "annotators" property:
 * <ul>
 * <li> tokenize - Tokenizes the text using TokenizingAnnotator. This annotator is required by all following annotators!</li>
 * <li> ssplit - Splits the sequence of tokens into sentences using WordsToSentencesAnnotator. This annotator is required if the input text contains multiple sentences, e.g., it is an entire document.</li>
 * <li> pos - Runs the POS tagger using POSTaggerAnnotator</li>
 * <li> lemma - Generates the lemmas for all tokens using MorphaAnnotator</li>
 * <li> ner - Runs a combination of NER models using OldNERCombinerAnnotator</li>
 * <li> truecase - Detects the true case of words in free text</li>
 * <li> parse - Runs the PCFG parser using ParserAnnotator</li>
 * <li> coref - Implements pronominal anaphora resolution using a statistical model</li>
 * <li> dcoref - Implements pronominal anaphora resolution using a deterministic model</li>
 * <li> nfl - Implements entity and relation mention extraction for the NFL domain</li>
 * </ul>
 * <p>
 * To run the pipeline over some text, use StanfordCoreNLP.process(Reader reader).
 * This method returns an Annotation object, which stores all the annotations generated for the given text.
 * To access these annotations use the following methods:
 * <ul>
 * <li>Annotation.get(CoreAnnotations.SentencesAnnotation.class) returns the list of all sentences in the given text
 * as a List&lt;CoreMap&gt;. For each sentence annotation, sentence.get(CoreAnnotations.TokensAnnotation.class)
 * returns the list of all tokens in that sentence as a List&lt;CoreLabel&gt;.
 * Here you can access all the token-level information. For example:
 * <ul>
 * <li>Annotation.get(TextAnnotation.class) returns the text of the word</li>
 * <li>Annotation.get(LemmaAnnotation.class) returns the lemma of this word</li>
 * <li>Annotation.get(PartOfSpeechAnnotation.class) returns the POS tag of this word</li>
 * <li>Annotation.ner(NamedEntityTagAnnotation.class) returns the NE label of this word</li>
 * <li>Annotation.get(TrueCaseTextAnnotation.class) returns the true-cased text of this word</li>
 * </ul>
 * </li>
 * <li>For each SentenceAnnotation, sentence.get(TreeAnnotation.class) returns the parse tree of the sentence.</li>
 * <li>At the document level, Annotation.get(Annotation.CorefGraphAnnotation.class) returns the set of coreference links in this document if the DeterministicCorefAnnotator (dcoref) is enabled.
 * Each link is stored as a Pair&lt;IntTuple, IntTuple&gt; where the first element point to the source, and the second to the destination.
 * Each pointer is stored as a pair of integers, where the first integer is the offset of the sentence that contains the referent,
 * and the second integer is the offset of the referent head word in this sentence. Note that both offsets start at 1 (not 0!).
 * </li>
 * <li>Additionally, there are now two additional annotations are available from the dcoref annotator.  token.get(CorefClusterIdAnnotation.class) will return an arbitrary identifier for a
 * group of coreferent words.  In other words, two words are coreferent if they share the same coreference cluster ID: token1.get(CorefClusterIdAnnotation.class).equals(token2.get(CorefClusterIdAnnotation.class))
 * token.get(CorefClusterAnnotation.class) will return a set of CoreLabels of all the words that are coreferent with the token.  Note that currently, these CoreLabels will actually be
 * CyclicCoreLabels which do not hash the same way as their CoreLabel counterparts.</li>
 * <li>If the NFL annotator is enabled, then for each SentenceAnnotation:
 * <ul>
 * <li>sentence.get(MachineReadingAnnotations.EntityMentionsAnnotation.class) returns the list of EntityMention objects in this sentence.
 * Relevant methods in the EntityMention class: (a) getType() returns the type of the mention, e.g., "NFLTeam";
 * (b) getHeadTokenStart() returns the position of the first token of this mention;
 * (c) getHeadTokenEnd() returns the position after the last token of this mention;
 * (d) getObjectId() returns a unique String id corresponding this mention.
 * </li>
 * <li>sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class) returns the list of RelationMention objects in this sentence.
 * The RelationMention class supports all the same above methods plus getEntityMentionArgs(), which returns the list of arguments in this relation.
 * Note that the order in which the arguments is returned is important. For example, in the NFL domain, relation arguments are always sorted in alphabetical order.
 * Relations with the same arguments but stored in different order are not considered equal by the RelationMention.equals() method.
 * </li>
 * </ul>
 * </li>
 * </ul>
 * -->
 * @author Jenny Finkel
 * @author Mihai Surdeanu
 * @author Steven Bethard
 * @author David McClosky
 * <!-- hhmts start --> Last modified: May 7, 2012 <!-- hhmts end -->
 */
package edu.stanford.nlp.pipeline;
