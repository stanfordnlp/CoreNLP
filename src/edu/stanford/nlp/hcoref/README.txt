Sieve based hybrid coreference resolution system.

This package contains a hybrid coreference resolution system,
which can be used in pipeline by adding 'hcoref' annotator.
It includes all deterministic sieves used in dcoref system
(however, some behaviors might be changed to make the system simpler),
machine learning sieves based on random forest,
and oracle sieves for the system analysis.

System training requires hcoref.train package in research,
weka 3.7.12 (http://www.cs.waikato.ac.nz/ml/weka/documentation.html),
and FastRandomForest (https://code.google.com/p/fast-random-forest/).
A trained model is stored as hcoref.rf.RandomForest.
Training models 100 trees can be done in a machine with 32G RAM,
and running the system requires about 3G memory.
Using 100 trees will give 62.74 (trained on training data only) on CoNLL 2012 test data,
or 62.92 (trained on train+dev),
and we can get 63.32 from 1000 trees model trained on train+dev.
Dependency tree based models will give about 1 point lower score.

Here's the commands for system training and evaluation.

How to train models - all sieves will be trained contiguously.
$ java -Xmx30g edu.stanford.nlp.hcoref.train.CorefTrainer -props <PROPERTIES_FILE> >& log-train.txt

How to evaluate the system
$ java -Xmx30g edu.stanford.nlp.hcoref.CorefSystem -props <PROPERTIES_FILE> >& output.txt

Several properties files are in hcoref.properties.
  - coref-default-dep.properties: default settings for using hcoref in pipeline.
      Stanford corenlp pipeline will be used for POS tagging, NER, parsing.
  - coref-conll.properties: to replicate the performance on CoNLL data.
  - coref-conll-dep.properties: to evaluate the performance of dependency based system on CoNLL data.

Here's the brief workflow.

  1) Initializing the system (loading trained sieves, loading dictionaries, etc)
  2) DocReader: Read input from raw texts, CoNLL data, etc (TODO: ACE, MUC).
  3) CorefDocMaker: Make a Document for the input for coref system.
      - Adding missing annotations
      - Mention detection
      - Extract gold mention information
  4) Preprocessor: Preprocess the Document to extract information required for resolution
      - Preprocess mention information
      - Preprocess syntactic, discourse information
      - Initialize clusters
  5) CorefSystem: Resolve a document.

Here is an example code to use the system (See pipeline.HybridCorefAnnotator).

  import edu.stanford.nlp.hcoref.CorefCoreAnnotations;
  import edu.stanford.nlp.hcoref.CorefSystem;
  import edu.stanford.nlp.hcoref.data.CorefChain;
  import edu.stanford.nlp.hcoref.data.CorefChain.CorefMention;
  import edu.stanford.nlp.hcoref.data.Document;

  CorefSystem corefSystem = new CorefSystem(props);
  Document corefDoc = corefSystem.docMaker.makeDocument(annotation);
  Map<Integer, CorefChain> result = corefSystem.coref(corefDoc);
  annotation.set(CorefCoreAnnotations.CorefChainAnnotation.class, result);

Pretrained models are stored in /scr/nlp/data/coref/models/.


