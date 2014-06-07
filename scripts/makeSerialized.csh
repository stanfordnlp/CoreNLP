#!/bin/csh -f

# This is the file we use to make the serialized grammars for the parser.
# If you are on the Stanford NLP machines, you can use it to remake the 
# serialized parsers (such as when there have been incompatible software
# changes).  Don't forget to klog first so you can access the AFS corpora.
#
# If you are not on the Stanford NLP machines, then the script won't work 
# for you as is, since it contains hard-coded paths to various treebanks.
# But it may still be useful to inspect it to see what options we used to
# generate the various supplied grammars.
#
# NOTE: Output files in this script should ALWAYS use relative paths, so
# that you can copy this script and run it in a different directory and
# it will write output files there.
#
# usage:
# cd /u/nlp/data/lexparser   # to have files output in "usual" location
# ./makeSerialized.csh
#
## Uncomment this bit to run it with older parser version
# setenv CLASSPATH /u/nlp/distrib/lexparser-2004-03-24/javanlp.jar:

if ( ! $?JAVANLP_HOME) then
  echo 'JAVANLP_HOME is not set'
  echo 'Add a line like setenv JAVANLP_HOME $HOME/javanlp to your environment'
  exit
endif

set wsjptb=/afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj
# now ctb6
set ctb=/afs/ir/data/linguistic-data/Chinese-Treebank/6/data/utf8/bracketed
# now ctb7!
set ctb7train=/u/nlp/data/chinese/ctb7/train.mrg
set ctb7test=/u/nlp/data/chinese/ctb7/test.mrg
set negra=/afs/ir/data/linguistic-data/NEGRA/penn-format-train-dev-test

set host=`hostname | cut -d. -f1`

if ( ! -r $wsjptb) then
  echo "Can't read WSJ PTB.  Maybe you forgot to klog??"
  exit
endif

mv -f serializedParsers.log serializedParsers.bak
uptime > serializedParsers.log
echo "Classpath is $CLASSPATH" >> serializedParsers.log

# English WSJ 2-21 PCFG binary and text grammars

( echo "Running wsjPCFG (goodPCFG) on $host -server" ; time java -server -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -goodPCFG -saveToSerializedFile wsjPCFG.ser.gz -saveToTextFile wsjPCFG.txt -maxLength 40 -train $wsjptb 200-2199 -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log

# English noTagSplit no rule compaction PCFG text grammar
( echo "Running wsjPCFG-noTagSplit-noCompact on $host -server" ; time java -server -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -goodPCFG -noTagSplit -saveToTextFile wsjPCFG-noTagSplit.txt -compactGrammar 0 -maxLength 40 -train $wsjptb 200-2199 -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log

# English WSJ 2-21 Factored binary

## Not yet clear that goodFactored is better than -ijcai03 -- not on dev set
# ( echo "Running wsjFactored (goodFactored) on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDAtsv" -goodFactored -saveToSerializedFile wsjFactored.ser.gz -saveToTextFile wsjFactored.txt -maxLength 40 -train $wsjptb 200-2199 -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log
( echo "Running wsjFactored (ijcai03 correctTags) on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -ijcai03 -v -printStates -compactGrammar 0 -correctTags -saveToSerializedFile wsjFactored.ser.gz -saveToTextFile wsjFactored.txt -maxLength 40 -train $wsjptb 200-2199 -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log
( echo "Running wsjFactored (ijcai03 replication) on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -ijcai03 -v -printStates -compactGrammar 0 -maxLength 40 -train $wsjptb 200-2199 -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log


## "General English" models 

# english{Factored|PCFG} is currently trained on:
# - WSJ sections 1-21
# - Genia as reformatted by Andrew Clegg, his training split
# - 2 English Chinese Translation Treebank and 3 English Arabic Translation 
#   Treebank files backported to the original treebank annotation standards
#   (by us) 
# - 95 sentences parsed by us (mainly questions and imperatives; a few from 
#   recent newswire).

# /u/nlp/data/genia/sentences_cleaned.tree

# "General English" Factored binary



( echo "Running englishFactored (from treebank) on $host server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -wordFunction edu.stanford.nlp.process.AmericanizeFunction -evals "factDA,tsv" -ijcai03 -saveToSerializedFile englishFactored.ser.gz -maxLength 40 -train /afs/ir/data/linguistic-data/Treebank/Treebank3Stanford/parsed/mrg/wsj 100-2199,9000-9099 -train2 /u/nlp/data/lexparser/extraTrain 1-4000 0.5 -taggedFiles tagSeparator=_,/u/nlp/data/pos-tagger/english/train-tech-english -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj/22 2200-2219 ) >>& ./serializedParsers.log

# "General English" PCFG binary 

( echo "Running englishPCFG (from treebank) on $host server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -wordFunction edu.stanford.nlp.process.AmericanizeFunction -evals "factDA,tsv" -goodPCFG -saveToSerializedFile englishPCFG.ser.gz -maxLength 40 -train /afs/ir/data/linguistic-data/Treebank/Treebank3Stanford/parsed/mrg/wsj 100-2199,9000-9099  -train2 /u/nlp/data/lexparser/extraTrain 1-4000 0.5 -taggedFiles tagSeparator=_,/u/nlp/data/pos-tagger/english/train-tech-english  -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj/22 2200-2219 ) >>& ./serializedParsers.log


# "General English" PCFG, case insensitive, binary

( echo "Running caseless englishPCFG (from treebank) on $host server" ; time java -mx4g edu.stanford.nlp.parser.lexparser.LexicalizedParser -wordFunction edu.stanford.nlp.process.LowercaseAndAmericanizeFunction -evals factDA,tsv -goodPCFG -saveToSerializedFile englishPCFG.caseless.ser.gz -maxLength 40 -train /afs/ir/data/linguistic-data/Treebank/Treebank3Stanford/parsed/mrg/wsj 100-2199,9000-9099 -train2 /u/nlp/data/lexparser/extraTrain 1-4000 0.5 -taggedFiles tagSeparator=_,/u/nlp/data/pos-tagger/english/train-tech-english  -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj/22 2200-2219 ) >>& ./serializedParsers.log


# English WSJ 2-21 PCFG simplified grammar
# This dumbed down parser is used by the RNN parser.  
# See /scr/nlp/data/dvparser for more details.
( echo "Running wsj pcfg (simplified for use in the RNN parser) on $host -server" ; time java -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -goodPCFG -noRightRec -dominatesV 0 -baseNP 0 -saveToSerializedFile wsjPCFG.nocompact.simple.ser.gz -maxLength 40  -compactGrammar 0 -train /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199 -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219 ) >>& ./serializedParsers.log

# English with extras PCFG simplified grammar
# This dumbed down parser is used by the RNN parser.  
# See /scr/nlp/data/dvparser for more details.
( echo "Running english pcfg (simplified for use in the RNN parser) on $host -server" ; time java -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -wordFunction edu.stanford.nlp.process.AmericanizeFunction -evals "factDA,tsv" -goodPCFG -noRightRec -dominatesV 0 -baseNP 0 -saveToSerializedFile englishPCFG.nocompact.simple.ser.gz -maxLength 40  -compactGrammar 0 -train /afs/ir/data/linguistic-data/Treebank/Treebank3Stanford/parsed/mrg/wsj 100-2199,9000-9099  -train2 /u/nlp/data/lexparser/extraTrain 1-4000 0.5 -taggedFiles tagSeparator=_,/u/nlp/data/pos-tagger/english/train-tech-english -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219 ) >>& ./serializedParsers.log


# Xinhua Mainland Chinese PCFG binary

( echo "Running xinhuaPCFG on $host -server" ; time java -server -mx800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -chinesePCFG -saveToSerializedFile xinhuaPCFG.ser.gz -maxLength 40 -train $ctb 026-270,301-499,600-999 -test $ctb 001-025 ) >>& ./serializedParsers.log
# new train list (Galen and Huihsin): 026-270,301-499,555-589,597-1041
# newer train list (Galen and Huihsin): 026-270,301-499,600-999
# this is all Xinhua minus Stanford devel and Bikel test

# Xinhua Mainland Chinese Factored binary

( echo "Running xinhuaFactored on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -acl03chinese -scTags -saveToSerializedFile xinhuaFactored.ser.gz -maxLength 40 -train $ctb 026-270,301-499,600-999 -test $ctb 001-025 ) >>& ./serializedParsers.log

# Mixed dialect Chinese on lots of data (with chineseFactored)

( echo "Running chineseFactored on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -chineseFactored -saveToSerializedFile chineseFactored.ser.gz -maxLength 40 -train $ctb7train -test $ctb7test ) >>& ./serializedParsers.log
# new train list (Galen and Huihsin): 026-270,301-499,555-589,597-1041
# newer train list (Galen and Huihsin): 026-270,301-499,600-999
# this is all Xinhua minus Stanford devel and Bikel test
# CTB files 001-499, 555-589,597-1000 are from newswire of
# XinHua.
# Files 500-554 are Information Services Department of HKSAR.
# Files 590-596 and 1001-1151 are Sinorama articles, more of literature
#   nature and from Taiwan.
# Files 2000-3145 are ACE broadcast news (from where?).  We only use a few for now.

# Mixed dialect Chinese PCFG on lots of data

( echo "Running chinesePCFG on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -chinesePCFG -useUnicodeType -saveToSerializedFile chinesePCFG.ser.gz -maxLength 40 -train $ctb7train -test $ctb7test ) >>& ./serializedParsers.log
# new train list (Galen and Huihsin): 026-270,301-499,555-589,597-1041
# newer train list (Galen and Huihsin): 026-270,301-499,600-999
# this is all Xinhua minus Stanford devel and Bikel test


# Chinese parser for unsegmented Chinese

( echo "Running xinhuaFactoredSegmenting on $host -server" ; time java -server -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -segmentMarkov -train $ctb 26-270,301-499,600-999 -sctags -acl03chinese -saveToSerializedFile xinhuaFactoredSegmenting.ser.gz ) >>& ./serializedParsers.log
java -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -encoding utf-8 xinhuaFactoredSegmenting.ser.gz /u/nlp/data/lexparser/chinese-onesent-unseg-utf8.txt >>& ./serializedParsers.log


# It used to be the case that explicitly saying tLPP on command line was 
# needed for file encoding.  But it has been fixed.
# ( echo "Running xinhuaFactored from serialized check on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -maxLength 40 -loadFromSerializedFile xinhuaFactored.ser.gz -test $ctb 001-025 ) >>& ./serializedParsers.log
# This now works
( echo "Running xinhuaFactored from serialized (check without specifying -tLPP) on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -maxLength 40 -loadFromSerializedFile xinhuaFactored.ser.gz -test $ctb 001-025 ) >>& ./serializedParsers.log

( echo "Running chinesePCFG (simplified for use in the RNN parser) on $host -server" ; time java -server -mx4g edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.ChineseTreebankParserParams -chineseFactored -PCFG -compactGrammar 0 -saveToSerializedFile chinesePCFG-simple.ser.gz -maxLength 40 -train $ctb7train -test $ctb7test ) >>& ./serializedParsers.log

# German Factored binary from Negra (version 2)
# $negra 3 is the dev set 

( echo "Running germanFactored on $host -server" ; time java -server -mx5g edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams -hMarkov 1 -vMarkov 2 -vSelSplitCutOff 300 -uwm 1 -unknownSuffixSize 2 -maxLength 40 -nodeCleanup 2 -saveToSerializedFile germanFactored.ser.gz -train $negra 1 -test $negra 3 ) >>& ./serializedParsers.log

# German PCFG from Negra (version 2)

( echo "Running germanPCFG on $host -server" ; time java -server -mx2g edu.stanford.nlp.parser.lexparser.LexicalizedParser -v -evals tsv -tLPP edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams -PCFG -hMarkov 1 -vMarkov 2 -vSelSplitCutOff 300 -uwm 1 -unknownSuffixSize 1 -maxLength 40 -nodeCleanup 2 -saveToSerializedFile germanPCFG.ser.gz -train $negra 1 -test $negra 3 ) >>& ./serializedParsers.log

# German Dependency parser
# This requires normalizing the dependency output to strip boundary symbol.
# ( echo "Running germanDep on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -tLPP edu.stanford.nlp.parser.lexparser.NegraPennTreebankParserParams -dep -hMarkov 1 -maxLength 40 -saveToSerializedFile germanDep.ser.gz -train $negra 1 -test $negra 3 ) >>& ./serializedParsers.log



########
# The languages below this line use TreebankPreprocessor for pre-processing prior to training
########
set mydir=`pwd`
set data_dir=/u/nlp/data/lexparser/trees
set tree_pipe=$JAVANLP_HOME/projects/core/scripts/run-tb-preproc
set train_sh=$JAVANLP_HOME/projects/core/scripts/lexparser-lang-train-test.sh

if( ! -e $data_dir ) then
  mkdir $data_dir
endif

########
# ARABIC
########
set ar_data_dir=$data_dir/Arabic
set ar_conf_file=$JAVANLP_HOME/projects/core/src/edu/stanford/nlp/international/arabic/pipeline/configurations/atb-latest.conf
set ar_train_args="Arabic 40 $ar_data_dir/2-Unvoc-All.utf8.txt $ar_data_dir/2-Unvoc-Dev.utf8.txt BASELINE_ar -saveToSerializedFile arabicFactored.ser.gz"

if( ! -e $ar_data_dir ) then
  mkdir $ar_data_dir
endif

echo Running $tree_pipe -p $ar_data_dir -v $ar_conf_file >>& ./serializedParsers.log
$tree_pipe -p $ar_data_dir -v $ar_conf_file >& $ar_data_dir/build.log

echo "" >>& ./serializedParsers.log
( echo "Training Arabic Factored grammar using baseline feature set" ; time $train_sh $ar_train_args ) >>& ./serializedParsers.log


########
# FRENCH
########
set fr_data_dir=$data_dir/French
set fr_conf_file=$JAVANLP_HOME/projects/core/src/edu/stanford/nlp/international/french/pipeline/configurations/ftb-latest.conf
set fr_train_args="French 40 $fr_data_dir/FTB-All.utf8.txt $fr_data_dir/FTB-Dev.utf8.txt BASELINE_fr -saveToSerializedFile frenchFactored.ser.gz"

if( ! -e $fr_data_dir ) then
  mkdir $fr_data_dir
endif

echo Running $tree_pipe -p $fr_data_dir -v $fr_conf_file >>& ./serializedParsers.log
$tree_pipe -p $fr_data_dir -v $fr_conf_file >& $fr_data_dir/build.log

echo "" >>& ./serializedParsers.log
echo time $train_sh $fr_train_args >>& ./serializedParsers.log
( echo "Training French Factored grammar using baseline feature set" ; time $train_sh $fr_train_args ) >>& ./serializedParsers.log




## English just to check parser code regression (not saved)

## Just for reference
( echo "Running wsjPCFG (acl03pcfg replication) on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -acl03pcfg -maxLength 40 -train $wsjptb 200-2199 -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log

## See if same results from serialized parser
( echo "Running wsjFactored (ijcai03 from serialized) on $host -server" ; time java -server -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -v -maxLength 40 -loadFromSerializedFile wsjFactored.ser.gz -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log
# ( echo "Running wsjFactored (ijcai03 with nodeprune) on $host -server" ; time java -server -mx1800m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -ijcai03 -v -compactGrammar 0 -nodePrune true -maxLength 40 -train $wsjptb 200-2199 -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log

## See if same results from text grammar parser
( echo "Running wsjFactored (ijcai03 from textGrammar) on $host -server" ; time java -server -mx1500m edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -v -maxLength 40 -loadFromTextFile wsjFactored.txt -testTreebank $wsjptb 2200-2219 ) >>& ./serializedParsers.log

uptime >> serializedParsers.log

mv -f serializedParsersPerformance.last serializedParsersPerformance.2ndlast
mv -f serializedParsersPerformance.current serializedParsersPerformance.last
echo -n "Parser run by $USER on " > serializedParsersPerformance.current
date >> serializedParsersPerformance.current
grep 'N: 253\|N: 393\|Done testing on treebank\|Running \| summary ' serializedParsers.log >> serializedParsersPerformance.current
echo >> serializedParsersPerformance.current
echo >> serializedParsersPerformance.current

cat serializedParsersPerformance.current >> serializedParsersPerformance.txt

cp -f serializedParsers.last serializedParsers.2ndlast
cp -f serializedParsers.current serializedParsers.last
cp -f serializedParsers.log serializedParsers.current
