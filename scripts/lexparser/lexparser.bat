@echo off
:: Runs the English PCFG parser on one or more files, printing trees only
:: usage: lexparser fileToparse
java -mx150m -cp "*;" edu.stanford.nlp.parser.lexparser.LexicalizedParser -outputFormat "penn,typedDependencies" edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz %1
