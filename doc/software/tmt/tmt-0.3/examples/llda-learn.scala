// http://nlp.stanford.edu/software/tmt/0.3/

// tells Scala where to find the TMT classes
import scalanlp.io._;
import scalanlp.stage._;
import scalanlp.stage.text._;
import scalanlp.text.tokenize._;
import scalanlp.pipes.Pipes.global._;

import edu.stanford.nlp.tmt.stage._;
import edu.stanford.nlp.tmt.model.lda._;
import edu.stanford.nlp.tmt.model.llda._;

if (args.length != 1) {
  println("arguments: inputFile.tsv");
  println("  inputFile.tsv should contain three tab-speparated columns:");
  println("    id	text	labels");
  println("  with text and labels already separated by whitespace");
  System.exit(-1);
}

if (!file(args(0)).exists) {
  println("input path " + args(0) + " not found");
}

val source = TSVFile(args(0)) ~> IDColumn(1);
val text = source ~> Column(2) ~> TokenizeWith(WhitespaceTokenizer()) ~> TermCounter();
val labels = source ~> Column(3) ~> TokenizeWith(WhitespaceTokenizer()) ~> TermCounter();

// define the dataset
val dataset = LabeledLDADataset(text, labels);

// define the model parameters
val modelParams = LabeledLDAModelParams(dataset);

// Name of the output model folder to generate
val modelPath = file("llda-cvb0-"+dataset.signature+"-"+modelParams.signature);

// Trains the model, writing to the given output path
TrainCVB0LabeledLDA(modelParams, dataset, output = modelPath, maxIterations = 5);
// or could use TrainGibbsLabeledLDA(modelParams, dataset, output = modelPath, maxIterations = 1500);

