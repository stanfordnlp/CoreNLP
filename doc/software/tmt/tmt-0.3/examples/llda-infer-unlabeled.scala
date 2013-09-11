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

if (args.length != 3) {
  System.err.println("Arguments: modelPath input.tsv output.tsv");
  System.err.println("  modelPath:  trained LLDA model");
  System.err.println("  input.tsv:  path to input file with two tab separated columns: id, words");
  System.err.println("  output.tsv: id followed by (word (label:prob)*)* for each word in each doc");
  System.exit(-1);
}

val modelPath = file(args(0));
val inputPath = file(args(1));
val outputPath = file(args(2));

System.err.println("Loading model ...");

val lldaModel = LoadCVB0LabeledLDA(modelPath);
val model = lldaModel.asCVB0LDA;
val source = TSVFile(inputPath) ~> IDColumn(1);
val text = source ~> Column(2) ~> TokenizeWith(model.tokenizer.get);

val dataset = LDADataset(text,model.termIndex);

System.err.println("Generating output ...");
val perDocTopicDistributions =
  InferCVB0DocumentTopicDistributions(model, dataset);

val perDocTermTopicDistributions =
  EstimatePerWordTopicDistributions(model, dataset, perDocTopicDistributions);

TSVFile(outputPath).write({
  for ((terms,(dId,dists)) <- text.iterator zip perDocTermTopicDistributions.iterator) yield {
    require(terms.id == dId);
    (terms.id,
     for ((term,dist) <- (terms.value zip dists)) yield {
       term + " " + dist.zipWithIndex.map({
         case (prob,topic) => lldaModel.topicIndex.get.get(topic) + ":" + prob
       }).mkString(" ");
     });
  }
});


