
// TMT Example 2 - Learning and LDA model

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

// turn the text into a dataset ready to be used with LDA
val dataset = LDADataset(text);
 
// define the model parameters
val numTopics = 30;
val modelParams = LDA.ModelParams(numTopics);
   // this is equivalent to:
   //
   // val modelParams = LDA.ModelParams(numTopics,
   //                                   LDA.TermSmoothing(.01),
   //                                   LDA.TopicSmoothing(50.0 / numTopics));
  
// define the training parameters
val trainingParams = GibbsLDA.DefaultTrainingParams;
    // this is equivalent to:
    //
    // import GibbsLDATrainer._;
    // val trainingParams =
    //   TrainingParams(MaxIterations(1500),
    //                  SaveEvery(50, PerplexityEstimate, PerplexityEstimatePerDocument,
    //                                DocumentTopicDistributions,DocumentTopicAssignments));
    //
    // SaveEvery(...) could be replaced by SaveFinal() to write less output
    //
    // val trainingParams = TrainingParams(MaxIterations(1500), SaveFinal());
  
// Name of the output model folder to generate
val output = file("lda-"+dataset.signature+"-"+modelParams.signature);

// Trains the model: the model (and intermediate models) are written to the output folder,
// which must be new
GibbsLDA.train(output, dataset, modelParams, trainingParams);

