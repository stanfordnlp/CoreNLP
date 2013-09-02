
// TMT Example 5 - Training a LabeledLDA model

// tells Scala where to find the TMT classes
import scalanlp.stage._;
import scalanlp.stage.text._;
import scalanlp.stage.source._;

import edu.stanford.nlp.tmt.lda._;
import edu.stanford.nlp.tmt.stage.{LDAMetadata, LabeledLDAMetadata};
import edu.stanford.nlp.tmt.stage.LDAStages._;
import edu.stanford.nlp.tmt.stage.GibbsLDAStages._;
import edu.stanford.nlp.tmt.stage.LabeledLDAStages._;
import edu.stanford.nlp.tmt.stage.GibbsLabeledLDAStages._;

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

// define fields from the dataset we are going to slice against
val year = {
    pubmed ~>                            // read from the pubmed file
    Column(1) ~>                         // take column one, the year
    WhitespaceTokenizer                  // turns label field into an array
}

val dataset = LabeledLDADataset(text, year);

// the label set
val labels = dataset.meta[LabeledLDAMetadata.LabelIndex].value;
println("Labels: "+labels.mkString(" "));

// define the model parameters
val modelParams = LabeledLDA.ModelParams();
   // this is equivalent to:
   //
   // val modelParams = LabeledLDA.ModelParams(
   //                                   LDA.TermSmoothing(.01),
   //                                   LDA.TopicSmoothing(0.1));
  
// define the training parameters
val trainingParams = GibbsLDA.DefaultTrainingParams;
    // this is equivalent to:
    //
    // import GibbsLDA.LearningModel._;
    // val trainingParams =
    //   TrainingParams(MaxIterations(1500),
    //                  SaveEvery(50, LogProbabilityEstimate,
    //                                DocumentTopicDistributions,
    //                                DocumentTopicAssignments));
    //
    // SaveEvery(...) could be replaced by SaveFinal() to write less output
    //
    // val trainingParams = TrainingParams(MaxIterations(1500), SaveFinal());
  
// Name of the output model folder to generate
val modelPath = file("labeled-lda-"+dataset.signature+"-"+modelParams.signature);

// Trains the model: the model (and intermediate models) are written to the
// output folder.  If a partially trained model with the same dataset and
// parameters exists in that folder, training will be resumed.
TrainGibbsLabeledLDA(modelPath, dataset, modelParams, trainingParams);

// Does inference on the same dataset, this time ignoring the assigned labels
// and letting the model decide which labels to apply
val model = GibbsLDA.loadInferenceModel(modelPath);
val perDocWordTopicProbability = InferPerWordTopicDistributions(model, LDADataset(text));
DocumentTopicUsage(perDocWordTopicProbability) | CSVFile(modelPath, "usage-after-inference.csv");

