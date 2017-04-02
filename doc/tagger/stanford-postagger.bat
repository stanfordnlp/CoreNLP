:: usage: stanford-postagger model textFile
::  e.g., stanford-postagger models\english-left3words-distsim.tagger sample-input.txt

java -mx300m -cp "stanford-postagger.jar;" edu.stanford.nlp.tagger.maxent.MaxentTagger -model %1 -textFile %2
