#!/usr/bin/env bash
#
# Runs the Lexicalized Parser GUI.  You can just run this script and then
# load a grammar and file to be parsed from the menus or you can specify
# them on the command line.
#
# Usage: ./lexparser-gui.sh [parserDataFilename [textFileName]]
#


scriptdir=`dirname $0`

java -mx800m -cp "$scriptdir/*" edu.stanford.nlp.parser.ui.Parser $*
