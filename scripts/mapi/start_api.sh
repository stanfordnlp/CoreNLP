ant
cd ../../classes
jar -cf ../stanford-corenlp.jar edu
# download models (see `README.md`)
wget -nc http://nlp.stanford.edu/software/stanford-corenlp-models-current.jar
wget -nc https://nlp.stanford.edu/software/stanford-english-extra-corenlp-models-current.jar

export CLASSPATH="`find . -name '*.jar'`"
java -cp "*" -mx8g edu.stanford.nlp.pipeline.StanfordCoreNLPServer