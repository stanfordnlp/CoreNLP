// TMT Example 0 - Basic data loading

import scalanlp.stage.source._;

val pubmed = CSVFile("pubmed-oa-subset.csv");

println("Success: " + pubmed + " contains " + pubmed.data.size + " records");

