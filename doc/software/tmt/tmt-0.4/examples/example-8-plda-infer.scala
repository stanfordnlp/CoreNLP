// Stanford TMT Example 8 - LDA inference using a PLDA model
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

import scalala.operators.Implicits._;

// A new dataset for inference.  (Here we use the same dataset
// that we trained against, but this file could be something new.)
val source = CSVFile("pubmed-oa-subset.csv") ~> IDColumn(1);

val text = {
  source ~>                              // read from the source file
  Column(4) ~>                           // select column containing text
  TokenizeWith(model.tokenizer.get)      // tokenize with existing model's tokenizer
}

val labels = {
  source ~>                              // read from the source file
  Column(2) ~>                           // take column two, the year
  TokenizeWith(WhitespaceTokenizer()) ~> // turns label field into an array
  TermCounter() ~>                       // collect label counts
  TermMinimumDocumentCountFilter(10)     // filter labels in < 10 docs
}

// the path of the model to load
val modelPath = file("plda-cvb0-59ea15c7-1-b65bfa69-75faccf7");

// load PLDA model as an LDA model to do inference using all available topics
println("Loading "+modelPath);
val model = LoadCVB0PLDA(modelPath).asCVB0LDA;

// turn the text into a dataset ready to be used with LDA
val dataset = LDADataset(text, termIndex = model.termIndex);

// Base name of output files to generate
val output = file(modelPath, source.meta[java.io.File].getName.replaceAll(".csv",""));

// Generate per-document distribution over all topics
val outputDocumentTopicDistributions =
  CSVFile(output+"-document-topic-distributions.csv")

if (!outputDocumentTopicDistributions.exists) {
  println("Writing document distributions to "+outputDocumentTopicDistributions);
  val perDocTopicDistributions = InferCVB0DocumentTopicDistributions(model, dataset);
  outputDocumentTopicDistributions.write(perDocTopicDistributions);
}

// Generate per-label distribution over topics
val outputLabelTopicDistributions = CSVFile(output+"-label-topic-distributions.csv")

if (!outputLabelTopicDistributions.exists) {
  println("Writing label distributions to "+outputLabelTopicDistributions);
  def perDocTopicDistributions =
    outputDocumentTopicDistributions.read[Iterator[(String,Array[Double])]]

  val labelIndex = labels.meta[TermCounts].index
  val distributions = Array.fill(labelIndex.size, model.numTopics)(0.0)
  for (((id,dist),item) <- perDocTopicDistributions zip labels.iterator) {
    require(id == item.id)
    if (!dist.sum.isNaN) {
      for (label <- item.value) {
        distributions(labelIndex(label)) :+= dist
      }
    }
  }

  outputLabelTopicDistributions.write(labelIndex.iterator zip distributions.iterator)
}

