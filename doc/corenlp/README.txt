Stanford CoreNLP - Stanford's Suite of NLP Tools
------------------------------------------------

Copyright © 2009-2020 The Board of Trustees of
The Leland Stanford Junior University. All Rights Reserved.

DOCUMENTATION

Please look at the URL below for documentation for Stanford CoreNLP:

  https://nlp.stanford.edu/software/corenlp.html

LICENSE

//
// StanfordCoreNLP -- a suite of NLP tools
// Copyright © 2009-2020 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//

---------------------------------
CHANGES
---------------------------------

2025-06-06   4.5.10     Additional Semgrex / SSurgeon features and
                        remove patterns/lucene because of security
                        warning

2025-03-23    4.5.9     Additional Semgrex / SSurgeon features and a
                        security patch recommended for deserialization

2024-12-11    4.5.8     Update paths to reflect an internal file 
                        server dying.  Add brat demo files to distrib 
                        to reduce dependencies on nlp.stanford.edu.  
                        Update the con->dep converter for UD.  Update 
                        some external libraries for security 
                        vulnerabilities

2024-04-18    4.5.7     Add the f1 scores to the parser output,
                        improve the dependency converter

2024-01-31    4.5.6     Minor lemmatizer and tokenizer fixes

2023-09-06    4.5.5     Fix up some SD and UD conversion errors.  Add
                        SceneGraph to the server.  Fix Tregex optional
                        bug.  "fourty" and forty (40) days in SUTime.

2023-03-15    4.5.4     Minor updates to Ssurgeon, include the
                        Morphology CLI

2023-03-10    4.5.3     Fix the collinizer.  Add an Ssurgeon interface

2023-01-19    4.5.2     Update package dependencies to avoid security
                        problems.  Add protobuf CLI tools for use in
                        Python or elsewhere

2022-08-29    4.5.1     Bugfixes to tokenizer and semgrex

2022-07-21    4.5.0     Tokenizer and lemmatizer upgrades, along with
                        a new tsurgeon operation and some bugfixes

2022-01-20    4.4.0     Fix issue with Italian depparse, tsurgeon CLI,
                        fix security issues, bug fixes

2021-10-14    4.3.1     Minor bugfixes to German umlaut tokenization,
                        CDC tokenizer offset, Hungarian model package

2021-09-26    4.3.0     Add trained tokenizer from corenlp-it, add
                        Italian and Hungarian pipelines using data
                        from FBK, UD, Szeged, NYTK, and SPMRL.  Better
                        emoji support in the PTB tokenizer

2021-05-14    4.2.2     Fix issue with demo.

2021-05-05    4.2.1     Fix Turkish locale bug, QuoteAnnotator crash
                        fixes, smaller srparser models, improvements
                        to enhanced UD converter, Updated dependencies
                        (istack, protobuf), batch processing of
                        semgrex & enhancer requests when using stanza

2020-11-16    4.2.0     Bug fixes, Retrained English parser models
                        with improved trees, Updated dependencies
                        (ejml, junit, jflex), Speed up loading
                        Wikidict annotator, New features for server
                        handling of tokensregex and tregex requests,
                        Release built directly from GitHub repo

2020-07-31    4.1.0     Improved server interface, improved memory
                        usage of sutime, spanish tokenization upgrades

2020-04-19    4.0.0     Changed to UDv2 tokenization ("new" LDC Treebank,
                        for English); handles multi-word-tokens;
                        improved UDv2-based taggers and parsers for
                        English, French, German, Spanish; new French NER;
                        new Chinese segmenter; library updates, bug fixes

2018-10-05    3.9.2     improved NER pipeline and entity mention
                        confidences; support for Java 11; new POS
                        models for English; 4 methods for setting
                        document dates; tokenizer improvements;
                        CoreNLP runs as filter from stdin to stdout;
                        bug fixes

2018-02-27    3.9.1     Bug fixes, minor enhancements

2018-01-31    3.9.0     Spanish KBP and new dependency parse model,
                        wrapper API for data, quote attribution
                        improvements, easier use of coref info, bug
                        fixes

2017-06-09    3.8.0     Web service annotator, discussion forum
                        handling, new French and Spanish models

2016-10-31    3.7.0     KBP Annotator, improved coreference, Arabic
                        pipeline

2015-12-09    3.6.0     Improved coreference, OpenIE integration,
                        Stanford CoreNLP server

2015-04-20    3.5.2     Switch to Universal dependencies, add Chinese
                        coreference system to CoreNLP

2015-01-29    3.5.1     NER, dependency parser, SPIED improvements;
                        general bugfixes

2014-10-26    3.5.0     Upgrade to Java 1.8; add annotators for
                        dependency parsing and relation extraction

2014-08-27    3.4.1     Add Spanish models

2014-06-16      3.4     Add shift reduce parser

2014-01-04    3.3.1     Bugfix release

2013-11-12    3.3.0     Add sentiment model, minor sutime improvements

2013-06-19    3.2.0     New RNN parser model, more efficient tagger

2013-04-04    1.3.5     Speed improvements, coref improvements,
                        Chinese version, -nthreads option

2012-11-12    1.3.4     Improved ner model and dependency code,
                        now possible to change annotator pool for
                        later StanfordCoreNLP objects

2012-07-09    1.3.3     Minor bug fixes

2012-05-22    1.3.2     Improvements to sutime

2012-03-09    1.3.1     Now supports caseless models (available as DLC)

2011-12-16    1.3.0     Threadsafe!
                        Bugs in time annotation fixed

2011-09-14    1.2.0     Time expression recognizer added to ner annotator
                        Output bugfixes
                        Parser can now substitute for tagger

2011-06-19    1.1.0     Improved coref release

2011-05-15    1.0.4     More efficient dcoref data structure
                        Supports already-tokenized input text

2011-04-17    1.0.3     Compatible with other releases
                        Support loading arbitrary annotators
                        Tagger bug fixes, such as "EOS" token

2010-11-11    1.0.2     Remove wn.jar

2010-11-11    1.0.1     Add xml removal

2010-10-07      1.0     Initial release

