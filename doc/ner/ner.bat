java -mx1000m -cp stanford-ner.jar;lib/* edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier classifiers\english.all.3class.distsim.crf.ser.gz -textFile %1
