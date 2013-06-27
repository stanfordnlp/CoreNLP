# This is a rudimentary Makefile for rebuilding Stanford CoreNLP.
# We actually use ant (q.v.) or a Java IDE.

JAVAC = javac
JAVAFLAGS = -O -d classes -encoding utf-8

# Builds the classes' jar file
corenlp: source
	mkdir -p classes
	$(JAVAC) $(JAVAFLAGS) src/edu/stanford/nlp/*/*.java \
	    src/edu/stanford/nlp/*/*/*.java \
	    src/edu/stanford/nlp/*/*/*/*.java \
	    src/edu/stanford/nlp/*/*/*/*/*.java \
	    src/edu/stanford/nlp/*/*/*/*/*/*.java
	cd classes ; jar -cfm ../stanford-corenlp-`date +%Y-%m-%d`.jar ../src/META-INF/MANIFEST.MF edu ; cd ..

# Before making, unjar the source jar file in the 'src' directory
source:
	if [ ! -e src ] ; then \
	  mkdir src ; cd src ; jar -xf ../stanford-corenlp-*-sources.jar; \
	fi;

clean:
	rm -rf classes
	rm -rf src
