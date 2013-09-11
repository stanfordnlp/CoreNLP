// Stanford TMT Example 7 - Training a PLDA model
// http://nlp.stanford.edu/software/tmt/0.4/

// tells Scala where to find the TMT classes
import scalanlp.io._;
import scalanlp.stage._;
import scalanlp.stage.text._;
import scalanlp.text.tokenize._;
import scalanlp.pipes.Pipes.global._;

import edu.stanford.nlp.tmt.stage._;
import edu.stanford.nlp.tmt.model.lda._;
import edu.stanford.nlp.tmt.model.llda._;
import edu.stanford.nlp.tmt.model.plda._;

val source = CSVFile("pubmed-oa-subset.csv") ~> IDColumn(1);

val tokenizer = {
  SimpleEnglishTokenizer() ~>            // tokenize on space and punctuation
  CaseFolder() ~>                        // lowercase everything
  WordsAndNumbersOnlyFilter() ~>         // ignore non-words and non-numbers
  MinimumLengthFilter(3)                 // take terms with >=3 characters
}

val text = {
  source ~>                              // read from the source file
  Column(4) ~>                           // select column containing text
  TokenizeWith(tokenizer) ~>             // tokenize with tokenizer above
  TermCounter() ~>                       // collect counts (needed below)
  TermMinimumDocumentCountFilter(4) ~>   // filter terms in <4 docs
  TermDynamicStopListFilter(30) ~>       // filter out 30 most common terms
  DocumentMinimumLengthFilter(5)         // take only docs with >=5 terms
}

// define fields from the dataset we are going to slice against
val labels = {
  source ~>                              // read from the source file
  Column(2) ~>                           // take column two, the year
  TokenizeWith(WhitespaceTokenizer()) ~> // turns label field into an array
  TermCounter() ~>                       // collect label counts
  TermMinimumDocumentCountFilter(10)     // filter labels in < 10 docs
}

val dataset = LabeledLDADataset(text, labels);

// define the model parameters
val numBackgroundTopics = 1;

val numTopicsPerLabel = SharedKTopicsPerLabel(4);
  // or could specify the number of topics per label based on the values
  // in a two-column CSV file containing label name and number of topics
  // val numTopicsPerLabel = CustomKTopicsPerLabel(
  //  CSVFile("topics-per-label.csv").read[Iterator[(String,Int)]].toMap);

val modelParams = PLDAModelParams(dataset,
  numBackgroundTopics, numTopicsPerLabel,
  termSmoothing = 0.01, topicSmoothing = 0.01);

// Name of the output model folder to generate
val modelPath = file("plda-cvb0-"+dataset.signature+"-"+modelParams.signature);

// Trains the model, writing to the given output path
TrainCVB0PLDA(modelParams, dataset, output = modelPath, maxIterations = 1000);

