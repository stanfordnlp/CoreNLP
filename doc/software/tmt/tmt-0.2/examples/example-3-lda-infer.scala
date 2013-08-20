
// TMT Example 3 - Generating outputs

// tells Scala where to find the TMT classes
import scalanlp.stage._;
import scalanlp.stage.text._;
import scalanlp.stage.source._;

import edu.stanford.nlp.tmt.lda._;
import edu.stanford.nlp.tmt.stage.LDAStages._;      // new in 0.2
import edu.stanford.nlp.tmt.stage.GibbsLDAStages._; // new in 0.2

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

// the path to the model we want to load
val modelPath = file("lda-f7a35bfa-30-2b517070-7c1f94d2");

// load the trained model
val model = LoadGibbsLDA(modelPath);
  
// infer topic distributions for each word in each document in the dataset.
System.err.println("Running inference ... (this could take several minutes)");
val perDocWordTopicProbability = InferPerWordTopicDistributions(model, dataset);


//
// now build an object to query the inferred outputs
//

System.err.println("Generating general outputs ...");

// build an object to query the model
val fullLDAQuery = LDAQuery(perDocWordTopicProbability);

// write the top 20 words per topic to a csv file
fullLDAQuery.topK(20) | CSVFile("pubmed-topk.csv");

// track some words' usage
fullLDAQuery.trackWords("genes","probability") | CSVFile("pubmed-words.csv");

// write the overall topic usage
fullLDAQuery.usage | CSVFile("pubmed-usage.csv");


//
// now build an object to query by a field
//

System.err.println("Generating sliced outputs ...");

// define fields from the dataset we are going to slice against
val year = pubmed ~> Column(1);   // select column 1, the year
             
// create a slice object by binding the output of inference with the fields
val sliceLDAQuery = SlicedLDAQuery(perDocWordTopicProbability, year);

sliceLDAQuery.topK(20) | CSVFile("pubmed-slice-topk.csv");
sliceLDAQuery.trackWords("genes","probability") | CSVFile("pubmed-slice-trackwords.csv");
sliceLDAQuery.usage | CSVFile("pubmed-slice-usage.csv");

