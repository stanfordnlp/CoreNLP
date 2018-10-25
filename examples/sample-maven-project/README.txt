This is an example Maven project with Stanford CoreNLP as a dependency.

Make sure to set the RAM with this command:

export MAVEN_OPTS="-Xmx14000m"

You can build the project with this command:

mvn compile

You can run example demos with a command like this:

mvn exec:java -Dexec.mainClass="edu.stanford.nlp.StanfordCoreNLPEnglishTestApp" 
