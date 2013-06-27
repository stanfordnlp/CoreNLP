# This is a rudimentary Makefile for rebuilding the tregex distribution.
# We actually use ant (q.v.) or a Java IDE.

JAVAC = javac
JAVAFLAGS = -O -d classes -encoding utf-8

tregex:
	mkdir -p classes
	$(JAVAC) -classpath CLASSPATH:lib/AppleJavaExtensions.jar $(JAVAFLAGS) src/edu/stanford/nlp/*/*.java src/edu/stanford/nlp/*/*/*.java src/edu/stanford/nlp/*/*/*/*.java
	cd classes ; jar -cfm ../stanford-tregex-`date +%Y-%m-%d`.jar ../src/edu/stanford/nlp/trees/tregex/gui/tregex-manifest.txt edu ; cd ..
	cp stanford-tregex-`date +%Y-%m-%d`.jar stanford-tregex.jar

