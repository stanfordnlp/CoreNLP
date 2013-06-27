# This is a rudimentary Makefile for rebuilding the classifier.
# We actually use ant (q.v.) or a Java IDE.

JAVAC = javac
JAVAFLAGS = -O -d classes

classifier:
	mkdir -p classes
	$(JAVAC) $(JAVAFLAGS) src/edu/stanford/nlp/*/*.java
	cd classes ; jar -cfm ../stanford-classifier-`date +%Y-%m-%d`.jar ../src/edu/stanford/nlp/classify/classifier-manifest.txt edu ; cd ..
	cp stanford-classifier-`date +%Y-%m-%d`.jar stanford-classifier.jar
	rm -rf classes
