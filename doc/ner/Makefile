# This is a rudimentary Makefile for rebuilding the CRF NER distribution.
# We actually use ant (q.v.) or a Java IDE.

JAVAC = javac
JAVAFLAGS = -O -d classes -encoding utf-8

ner:
	mkdir -p classes
	$(JAVAC) $(JAVAFLAGS) src/edu/stanford/nlp/*/*.java src/edu/stanford/nlp/*/*/*.java
	cd classes ; jar -cfm ../stanford-ner-`date +%Y-%m-%d`.jar ../src/edu/stanford/nlp/ie/crf/ner-manifest.txt edu ; cd ..
	cp stanford-ner-`date +%Y-%m-%d`.jar stanford-ner.jar

