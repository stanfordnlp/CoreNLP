java -mx1500m -cp stanford-ner.jar edu.stanford.nlp.ie.crf.CRFClassifier -loadClassifier classifiers\english.all.3class.distsim.crf.ser.gz -textFile %1
