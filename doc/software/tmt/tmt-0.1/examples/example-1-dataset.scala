
// TMT Example 1 - Loading data

// tells Scala where to find the TMT classes
import scalanlp.stage._;
import scalanlp.stage.text._;
import scalanlp.stage.source._;


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

// display information about the loaded dataset
println("Description of the loaded text field:");
println(text.description);

println();
println("------------------------------------");
println();

println("Terms in the stop list:");
for (term <- text.meta[TermStopListFilter].stops) {
  println("  " + term);
}

