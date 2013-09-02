
// TMT Example 4 - Selecting the number of topics

// tells Scala where to find the TMT classes
import scalanlp.stage._;
import scalanlp.stage.text._;
import scalanlp.stage.source._;

import edu.stanford.nlp.tmt.lda._;
import edu.stanford.nlp.tmt.stage._;

import scalara.ra.RA.{global => ra};
import ra.pipes._;

// input file to read
val pubmed = CSVFile("pubmed-oa-subset.csv");

// the text field extracted and processed from the file
val text = {
    pubmed ~>                            // read from the pubmed file
    Column(3) ~>                         // select column three, the abstracts
    CaseFolder ~>                        // lowercase everything
    SimpleEnglishTokenizer ~>            // tokenize on spaces characters
    WordsAndNumbersOnlyFilter ~>         // ignore non-words and non-numbers
    TermCounter ~>                       // collect counts (needed below)
    TermMinimumLengthFilter(3) ~>        // take terms with >=3 characters
    TermMinimumDocumentCountFilter(4) ~> // filter terms in <4 docs
    TermDynamicStopListFilter(30) ~>     // filter out 30 most common terms
    DocumentMinimumLengthFilter(5)       // take only docs with >=5 terms
}
    
// set aside 80 percent of the input text as training data ...
val numTrain = (text.data.size * .8).toInt;
  
// build a training dataset
val training = LDADataset(text ~> Take(numTrain));
 
// build a test dataset, using term index from the training dataset 
val testing  = LDADataset(text ~> Drop(numTrain), training.meta[LDADataset.TermIndex]);

// a pair of (number of topics, perplexity) representing best value
var best : (Int,Double) = (-1,Double.MaxValue);

// loop over various numbers of topics, training and evaluating each model
for (numTopics <- List(4,5,6,7,8,9,10,15,20)) {
  val modelParams = LDA.ModelParams(numTopics);
  val output = file("lda-"+training.signature+"-"+modelParams.signature);
  val model = GibbsLDA.train(output, training, modelParams, GibbsLDA.DefaultTrainingParams);
  
  println("[perplexity] computing at "+numTopics);

  val perplexity = model.computePerplexity(testing, GibbsLDA.DefaultInferenceParams);
    
  Iterator.single(perplexity.toString) | file(output, "perplexity.txt");
  
  println("[perplexity] perplexity at "+numTopics+" : "+perplexity);
  
  if (perplexity < best._2) { // smaller is better
    best = (numTopics, perplexity);
  }
}

println("[perplexity] best model at " + best._1 + " : " + best._2);

