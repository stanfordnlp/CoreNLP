# This is a rudimentary Makefile for rebuilding the parser.
# We actually use ant (q.v.) or a Java IDE.

JAVAC = javac
JAVAFLAGS = -O -d classes -encoding utf-8

parser:
	mkdir -p classes
	$(JAVAC) $(JAVAFLAGS) src/edu/stanford/nlp/*/*.java \
	    src/edu/stanford/nlp/*/*/*.java src/edu/stanford/nlp/*/*/*/*.java
	cd classes ; jar -cfm ../stanford-parser-`date +%Y-%m-%d`.jar ../src/edu/stanford/nlp/parser/lexparser/lexparser-manifest.txt edu ; cd ..
	cp stanford-parser-`date +%Y-%m-%d`.jar stanford-parser.jar
	rm -rf classes
