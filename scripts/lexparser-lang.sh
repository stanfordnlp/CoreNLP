#!/usr/bin/env bash
#
# Defines standard configurations for parsing with the
# multilingual parsers (Arabic, Chinese, German, French). 
#
# For English, it is easier to use lexparser.sh, although you can load 
# an English grammar with this script.
#
# For details on the language-specific options, see the javadocs and
# lexparser_lang.def.
#

# Memory limit
mem=3g

if [ ! $# -ge 5 ]; then
   echo Usage: `basename $0` lang len grammar out_file FILE...
   echo
   echo '  lang       : Language to parse (Arabic, English, Chinese, German, French)'
   echo '  len        : Maximum length of the sentences to parse'
   echo '  grammar    : Serialized grammar file (look in the models jar)'
   echo '  out_file   : Prefix for the output filename'
   echo '  FILE       : List of files to parse'
   echo
   echo 'To set additional parser options, modify parse_opts in lexparser_lang.def'
   echo 
   echo 'Parser memory limit is currently:' "$mem"
   echo   
   exit
fi

# Setup command-line options
lang=$1
len=$2
grammar=$3
out_file=$4

shift 4

# Language-specific configuration
scriptdir=`dirname $0`
source $scriptdir/lexparser_lang.def

# Setting classpath
CLASSPATH="$CLASSPATH":"$scriptdir/*"

# Run the Stanford parser
java -Xmx"$mem" -cp "$scriptdir/*:" edu.stanford.nlp.parser.lexparser.LexicalizedParser -maxLength "$len" \
-tLPP "$tlp" $lang_opts $parse_opts -writeOutputFiles \
-outputFilesExtension "$out_file"."$len".stp -outputFormat "penn" \
-outputFormatOptions "removeTopBracket,includePunctuationDependencies" -loadFromSerializedFile $grammar $*
