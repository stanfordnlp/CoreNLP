---
layout: page
title: Parser FAQ
keywords: lexparser, parser
permalink: '/tools_parser_faq.html'
nav_order: 13
toc: true
parent: Additional Tools
---

### Where are the parser models?

In recent distributions, the models are included in a jar file inside the
parser distribution. For example, in the 2012-11-12 distribution, the models
are included in stanford-parser-2.0.4-models.jar The easiest way to access
these models is to include this file in your classpath. The parser will then
be able to read the models from that jar file.

Again using January 2014 version 3.3.1 as an example, you would not make your
classpath  
```
-cp stanford-parser-3.3.1.jar
```

Instead, you would make it  
Windows:  
```
-cp stanford-parser-3.3.1.jar;stanford-parser-3.3.1-models.jar
```

*nix:   
```
-cp stanford-parser-3.3.1.jar:stanford-parser-3.3.1-models.jar
```

In order to see exactly which models are available, you can use  
```
jar tvf stanford-parser-3.3.1-models.jar
```
This will show you that to access the Arabic Factored model, for example, you
would use the path  
```
edu/stanford/nlp/models/lexparser/arabicFactored.ser.gz
```

If you are encountering a `FileNotFoundException` or similar error when loading
models, the first thing to check is that the classpath is set up as described
here.

### Is there technical documentation for the parser?

There is considerable Javadoc documentation included in the `javadoc/`
directory of the distribution. You should start by looking at the javadoc for
the parser.lexparser package and the LexicalizedParser class.

(The documentation appearing on the `nlp.stanford.edu` website refers to code
under development and is not necessarily consistent with the released version
of the parser.) If you're interested in the theory and algorithms behind how
the parser works, look at the research papers listed.

### How do I use the API?

A brief demo program included with the download will demonstrate how to load
the tool and start processing text. When using this demo program, be sure to
include all of the appropriate jar files in the classpath.

### What is the inventory of tags, phrasal categories, and typed dependencies in your parser?

For part-of-speech tags and phrasal categories, this depends on the language
and treebank on which the parser was trained (and was decided by the treebank
producers not us). The parser can be used for English, Chinese, Arabic, or
German (among other languages). For part of speech and phrasal categories,
here are relevant links:

- English: the [Penn Treebank site](http://www.cis.upenn.edu/~treebank/). There is an [online copy of its documentation](http://catalog.ldc.upenn.edu/docs/LDC99T42/); in particular, see TAGGUID1.PDF (POS tagging guide) and PRSGUID1.PDF (phrase structure bracketing guide) and a (slightly dated) [introductory article](http://aclweb.org/anthology-new/J/J93/J93-2004.pdf). You might find it more convenient to use other simpler listings such as the [AMALGAM project page](http://www.comp.leeds.ac.uk/amalgam/tagsets/upenn.html) or [this page at MIT](http://web.mit.edu/6.863/www/PennTreebankTags.html). 
- Chinese: the [Penn Chinese Treebank](http://www.cis.upenn.edu/~chinese/)
- German: the [NEGRA](http://www.coli.uni-saarland.de/projects/sfb378/negra-corpus/) corpus 
- French: the [French Treebank](http://www.llf.cnrs.fr/Gens/Abeille/French-Treebank-fr.php)

Please read the documentation for each of these corpora to learn about their
tagsets and phrasal categories. You can often also find additional
documentation resources by doing web searches.

We defined or were involved in defining the typed dependency (grammatical
relations) output available for English and Chinese. For English, the parser
by default now produces [Universal Dependencies](http://universaldependencies.github.io/docs/), which are
extensively documented on that page. You can also have it produce the prior
Stanford Dependencies representation. For this, and for the Chinese
dependencies, you can find links to documentation on the [Stanford
Dependencies](https://nlp.stanford.edu/software/stanford-dependencies.html)
page.

Further information (definitions and examples of nearly all the grammatical
relations) appear in the included Javadoc documentation. Look at the
[EnglishGrammaticalRelations](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/trees/EnglishGrammaticalRelations.html)
and
[ChineseGrammaticalRelations](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/trees/international/pennchinese/ChineseGrammaticalRelations.html)
classes.

### Can I train the parser?

Yes, you can train a parser. You will need a collection of syntactically
annotated data such as the [Penn Treebank](http://www.cis.upenn.edu/~treebank/home.html) to train the parser.
If they are not in the same format as currently supported Treebanks, you may
need to write classes to read in the trees, etc. Read the Javadocs for the
[main method of the `LexicalizedParser` class](http://nlp.stanford.edu/nlp/javadoc/javanlp/edu/stanford/nlp/parser/lexparser/LexicalizedParser.html#main-java.lang.String:A-), particularly the `-train` option to find out about the
command options for training parsers. The supplied file `makeSerialized.csh`
shows exactly what options we used to train the parsers that are included in
the distribution. If you want to train the parser on a new language and/or
treebank format, you can (and people have done so), but you need to spend a
while learning about the code, especially if you wish to develop language-
specific features. Start by trying to train a plain PCFG on the data, and then
look at the `TreebankLangParserParams` class for how to do language-specific
processing.

### How do I train the RNN parser?

Training the RNN parser is a two step process. First, because the RNN parser
uses the parsings of a simpler PCFG parser to train, it is useful to precache
the results of that parser before training the RNN parser. This can be done
with an included tool, `CacheParseHypotheses`, which converts a treebank into
a compressed file of parsed trees.

An example command line for this process, with some of the most useful flags,
is  

```
java -mx4g edu.stanford.nlp.parser.dvparser.CacheParseHypotheses -model /path/to/pcfg/pcfg.ser.gz -treebank /path/to/wsj 200-2199 -output cached.wsj.ser.gz -numThreads 6
```

It is then necessary to run the DVParser module to create a new serialized
model. An example of this command line is  

```
java -mx12g edu.stanford.nlp.parser.dvparser.DVParser -cachedTrees /path/to/cached.wsj.ser.gz -train -testTreebank /path/to/wsj 2200-2219 -debugOutputFrequency 500 -trainingThreads 8 -parser /path/to/pcfg/pcfg.ser.gz -dvIterations 40 -dvBatchSize 25 -wordVectorFile /path/to/embedding -model /scr/nlp/data/dvparser/wsj/train/averaged/averaged.ser.gz
```

For an explanation of the various options, run `java edu.stanford.nlp.parser.dvparser.DVParser` with no flags.

The memory requirements of the parser is not actually that high, but the more
threads added with `-trainingThreads`, the more memory will be required to
train.

As the parser is training, it will output intermediate models every 20 minutes
(by default). These models will have the model's score on the dev set pointed
to with `-testTreebank`. When the model is finished training, or when you want
to test one of the intermediate models, you can run it using the standard
`LexicalizedParser` commands.

In our experiments, we found that [simpler PCFG models](tools_parser_faq.md#what-about-other-versions-of-weaker-models) actually make
better underlying PCFG models. Command lines for the PCFG models we use for
English and Chinese can be found in makeSerialized.csh

### Why do I get the exception "null head found for tree" after training my own parser model?

The default HeadFinder is written specifically for the PTB. If you train a
parser on trees that use a different set of productions, the default
HeadFinder will not know how to handle this and will throw this exception. The
easiest way to get around this problem is to use LeftHeadFinder instead. You
can also get a slight performance increase by writing a custom HeadFinder for
your treebank and using that instead.

### How do I force the parser to use my sentence delimitations? I want to give the parser a list of sentences, one per line, to parse.

Use the `-sentences` option. If you want to give the parser one sentence per
line, include the option `-sentences newline` in your invocation of
`LexicalizedParser`.

### Can the parser work as a filter (read from stdin and parse to stdout)?

Yes. The parser treats a filename as `-` as meaning to read from stdin and by
default writes to stdout (this can be changed with the `-writeOutputFiles`
option). Note: the tokenizer uses lookahead, so you will either need to close
the input to get the last sentence parsed, or use another option like
`-sentences newline`.

### How can I provide the correct tokenization of my sentence to the parser?

From the commandline, if you give the option `-tokenized`, then the parser
will assume white-space separated tokens, and use your tokenization as is. Of
course, parsing will suffer unless your tokenization accurately matches the
tokenization of the underlying treebank, for instance Penn Treebank
tokenization. A common occurrence is that your text is already correctly
tokenized but does not escape characters the way the Penn Treebank does (e.g.,
turning parentheses into `-LRB-` and `-RRB-`). In this case, you can use the
`-tokenized` option but also add the flag:

```
-escaper edu.stanford.nlp.process.PTBEscapingProcessor
```

(Note: the original Penn Treebank through the 1999 release also putt a
backslash in front of forward slashes and asterisks - presumably a holdover
from Lisp. This escaping is not used by the Stanford tools and is not
necessary.)

If calling the parser within your own program, the main `parse` methods take a
List of words which should already be correctly tokenized and escaped before
calling the parser. You don't need to and cannot give the `-tokenized` option.
If you have untokenized text, it needs to tokenized before parsing. You may
use the `parse` method that takes a String argument to have this done for you
or you may be able to use of classes in the `process` package, such as
`DocumentPreprocessor` and `PTBTokenizer` for tokenization, much as the main
method of the parser does. Or you may want to use your own tokenizer.

### Can I give the parser part-of-speech (POS) tagged input and force the parser to use those tags?

Yes, you can. However, for good results, you should make sure that you provide
correctly tokenized input and use exactly the correct tag names. (That is, the
input must be tokenized and normalized exactly as the material in the treebank
underlying the grammar is.)

Read the Javadocs for the main method of the LexicalizedParser class. The
relevant options are `-sentences` (see above), `-tokenized`,
`-tokenizerFactory`, `-tokenizerMethod`, and `-tagSeparator`. If, for example,
you want to denote a POS tag by appending `/POS` on a word, you would include
the options `-tokenized -tagSeparator / -tokenizerFactory edu.stanford.nlp.process.WhitespaceTokenizer -tokenizerMethod newCoreLabelTokenizerFactory`
in your invocation of `LexicalizedParser`. You could then give the parser input such as:

```
The/DT quick/JJ brown/JJ fox/NN jumped/VBD over/IN the/DT lazy/JJ dog/NN ./.
```

with the command:

```
java -mx1g -cp "*" edu.stanford.nlp.parser.lexparser.LexicalizedParser -sentences newline -tokenized -tagSeparator / -tokenizerFactory edu.stanford.nlp.process.WhitespaceTokenizer -tokenizerMethod newCoreLabelTokenizerFactory edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz fox.txt
```

Partially-tagged input (only indicating the POS of some words) is also OK.

If you wish to work with POS-tagged text programmatically, then things are
different. You pass to the `parse` method a `List`. If the items in this list
implement `HasTag`, such as being of type `TaggedWord` or `CoreLabel`, and the
tag value is not `null`, then the parser will use the tags that you provide.
You can use the `DocumentPreprocessor` class, as the `main` method does, to
produce these lists, or you could use `WhitespaceTokenizer` followed by
`WordToTaggedWordProcessor`. Another alternative is to feed the sentences
through the [Stanford POS
Tagger](https://nlp.stanford.edu/software/tagger.html), which produces either
`List<TaggedWord>` or `List<CoreLabel>` depending on the input. Either form of
list will pass the tags to the parser. Or you can do this with code that you
write. Here's an example that very manually makes the `List` in question:

```java
// set up grammar and options as appropriate
LexicalizedParser lp = LexicalizedParser.loadModel(grammar, options);
String[] sent3 = { "It", "can", "can", "it", "." };
// Parser gets tag of second "can" wrong without help  
String[] tag3 = { "PRP", "MD", "VB", "PRP", "." };  
List sentence3 = new ArrayList();
for (int i = 0; i < sent3.length; i++) {
  sentence3.add(new TaggedWord(sent3[i], tag3[i]));
}
Tree parse = lp.parse(sentence3);
parse.pennPrint();
```

### What other on the chosen parse constraints are possible?

There are other constraints which can be added, but they have to be added
programmatically. Look at the `LexicalizedParserQuery` object, which you can get
from `LexicalizedParser.parseQuery()`. There is a call, `setConstraints`, which
you can make before using the `LexicalizedParserQuery` to run the parser.

If you add a ParserConstraint object spanning a set of words, the parser will
only produce parse trees which include that span of words as a constituent. In
general, you will want to use `".*"` as the state accepted by this constraint.

It is also possible to specify constraints such as `"NN|JJ"` to enforce that the
parser uses either an `NN` or `JJ`, for example, but unfortunately there is a
subtle and complicated bug in the code that enforces that. If you do try to
use this, most of the parsers use vertical markovization, which means you will
need to make the constraints `"JJ|JJ[^a-zA-Z].*"` instead of `"JJ"`. In general,
though, you should not use this part of the feature and simply use `".*"`.

See the existing Javadoc for more information on this.

### Is it possible to pre-annotate the corpus with phrasal boundaries and labels which the parser has to use?

Not yet, but in the future, very possibly.

### Can I obtain multiple parse trees for a single input sentence?

Yes, for the PCFG parser (only). With a PCFG parser, you can give the option
`-printPCFGkBest n` and it will print the `n` highest-scoring parses for a
sentence. They can be printed either as phrase structure trees or as typed
dependencies in the usual way via the `-outputFormat` option, and each
receives a score (log probability). The `k` best parses are extracted
efficiently using the algorithm of Huang and Chiang (2005).

### I don't [understand/like/agree with] the parse tree that is assigned to my sentence. Can you [explain/fix] it?

This may be because the parser chose an incorrect structure for your sentence,
or because the phrase structure annotation conventions used for training the
parser don't match your expectations. To make sure you understand the
annotation conventions, please read the bracketing guidelines for the parser
model that you're using, which are referenced above. Or it may be because the
parser made a mistake. While our goal is to improve the parser when we can, we
can't fix individual examples. The parser is just choosing the highest
probability analysis according to its grammar.

Having said that, we have actually built
[a small treebank of handparsed text](https://github.com/stanfordnlp/handparsed-treebank).
If you have specific examples to analyze, we can add more trees to
that dataset.

### Why does the parser accept incorrect/ungrammatical sentences?

This parser is in the space of modern statistical parsers whose goal is to
give the most likely sentence analysis to a list of words. It does not attempt
to determine grammaticality, though it will normally prefer a "grammatical"
parse for a sentence if one exists. This is appropriate in many circumstances,
such as when wanting to interpret user input, or dealing with conversational
speech, web pages, non-native speakers, etc.

For other applications, such as grammar checking, this is less appropriate.
One could attempt to assess grammaticality by looking at the probabilities
that the parser returns for sentences, but it is difficult to normalize this
number to give a useful "grammaticality" score, since the probability strongly
depends on other factors like the length of the sentence, the rarity of the
words in the sentence, and whether word dependencies in the sentence being
tested were seen in the training data or not.

### How much memory do I need to parse long sentences?

The parser uses considerable amounts of memory. If you see a
`java.lang.OutOfMemoryError`, you either need to give the parser more memory
or to take steps to reduce the memory needed. (You give java more memory at
the command line by using the `-mx` flag, for example `-mx500m`.)

Memory usage by the parser depends on a number of factors:

- Memory usage expands roughly with the square of the sentence length. You may wish to set a `-maxLength` and to skip long sentences.
- The factored parser requires several times as much memory as just running the PCFG parser, since it runs 3 parsers.
- The command-line version of the parser currently loads the whole of an input file into memory before parsing any of it. If your file is extremely large, splitting it into multiple files and parsing them sequentially will reduce memory usage.
- A 64-bit application requires more memory than a 32-bit application (Java uses lots of pointers).
- A larger grammar or POS tag set requires more memory than a smaller one.

Below are some statistics for 32-bit operation with the supplied englishPCFG
and englishFactoredGrammars. We have parsed sentences as long as 234 words,
but you need lots of RAM and patience.

Length| PCFG| Factored  
---|---|---  
20| 50 MB| 250 MB  
50| 125 MB| 600 MB  
100| 350 MB| 2100 MB  

### What does an UnsupportedClassVersionError mean?

If you see the error:

```
Exception in thread "main" java.lang.UnsupportedClassVersionError: edu/stanford/nlp/parser/lexparser/LexicalizedParser (Unsupported major.minor version xy.z)
```

it means that you don't have a recent enough version of Java installed. If
"xy.z" is "49.0", then you don't have Java 5 installed. If "xy.z" is "52.0",
then you don't have Java 8 installed. Etc. You should upgrade at
<http://www.oracle.com/technetwork/java/javase/downloads/>.

### How can I obtain just the results of the POS tagger for each word in a sentence?

You can use the `-outputFormat wordsAndTags` option. Note: if you want to tag
a lot of text, it'd be much faster to use a dedicated POS tagger (such as
[ours](tools_pos_tagger.md) or [someone else's](https://nlp.stanford.edu/links/statnlp.html#Taggers)), since this
option has the parser parse the sentences and just not print the other
information. There isn't a separate included tagger; the parser does POS
tagging as part of parsing.

### Can I just get your typed dependencies (grammatical relations) output from the trees produced by another parser?

Yes, you can. You can use the main method of `EnglishGrammaticalStructure`
(for English, or the corresponding class for Chinese). You can give it options
like `-treeFile` to read in trees, and, say, `-collapsed` to output
`typedDependenciesCollapsed`. For example, this command (with appropriate
paths) will convert a Penn Treebank file to uncollapsed typed dependencies:

```
java -cp stanford-parser.jar edu.stanford.nlp.trees.EnglishGrammaticalStructure -treeFile wsj/02/wsj_0201.mrg -basic
```

Also, here is [a sample Java class](https://nlp.stanford.edu/software/TypedDependenciesDemo.java) that you can
download that converts from an input file of trees to typed dependencies.

_Fine print:_ There is one subtlety. The conversion code generally expects
Penn Treebank style trees which have been stripped of functional tags and
empty elements. This generally corresponds to the output of the Stanford,
Charniak or Collins/Bikel parsers. The exception is that it gets value from
the `-TMP` annotation on bare temporal NPs in order to recognize them as
having temporal function (`tmod`). (It also allows a `-ADV` annotation on
`NP`s.) Without the temporal annotation, some simple temporals like _today_ will
still be recognized, but a bare temporal like _last week_ in _I left last
week_ will be tagged as an object (`dobj`). With the Stanford parser, you can
get marking of temporal NPs in the tree output by giving the option
`-retainTmpSubcategories`, either on the command line or by passing it to the
`setOptionFlags(String[])` method of the parser.

See the javadoc for the main method of
`edu.stanford.nlp.trees.GrammaticalStructure.java` for more information on how
to extract dependencies using this tool.

### How can something be the subject of another thing when neither is a verb? I tried the sentence _Jill is a teacher_ and the parser created a _nsubj_ dependency between _teacher_ and _Jill_. Is that a mistake or have I not understood what _nsubj_ is?

This is an element of the dependency analysis we adopted. It's not
uncontroversial, and it could have been done differently, but we'll try to
explain briefly why we did things the way we did. The general philosophy of
the grammatical relations design is that main predicates should be heads and
auxiliaries should not. So, for the sentence _Jill is singing_ , you will see
**nsubj(singing, Jill)**. We feel that this is more useful for most semantic
interpretation applications, because it directly connects the main predicate
with its arguments, while the auxiliary is rendered as modifying the verb (
**aux(singing, is)** ). Most people seem to agree.

What then when the main predicate is an adjective or a noun? That is,
sentences like _Jill is busy_ or _Jill is a teacher_. We continue to regard
the adjective or noun as the predicate of which the subject is the argument,
rather than changing and now regarding the copular verb _is_ as the head and
_busy/teacher_ as a complement. That is, we produce `nsubj(busy, Jill)` and
`nsubj(teacher, Jill)`. This frequently seems to confuse people, because the
main predicate of the clause is now not a verb. But we believe that this is
the best thing to do for several reasons:

1. Consistency of treatment of auxiliary/copula between English periphrastic verb forms and adjectival/nominal predications. 
2. Crosslinguistic generalization of the grammatical relations system: many other languages sometimes or always do not use a copular verb when using an adjective or noun predicate. That is, they will just say _Jill busy_. 
3. Connection to logical representations: If you were to translate these sentences into a simple predicate logic form, you would presumably use **busy(jill)** and **teacher(jill)**. The treatment of the adjective or noun as the predicate in a predicate logic form parallels what we do in our grammatical relations representation. 
4. Similarity of representation across constructions. While the dependency still differs, both the attributive ( _the white daisy_ ) and predicative ( _the daisy is white_ ) use of adjectives yields a direct link between the adjective ( _white_ ) and the noun ( _daisy_ ): _amod(daisy, white)_ and _nsubj(white, daisy)_. 

### Can I just use your tokenizers for other purposes?

Yes, you can. Various tokenizers are included. The one used for English is
called `PTBTokenizer`. It is a hand-written rule-based (FSM) tokenizer, but is
quite accurate over newswire-style text. Because it is rule-based it is quite
fast (about 100,000 tokens per second on an Intel box in 2007). You can use it
as follows:

```
java edu.stanford.nlp.process.PTBTokenizer _inputFile_ > _outputFile_
```

There are several options, including one for batch-processing lots of files;
see the Javadoc documentation of the `main` method of `PTBTokenizer`.

### How can I parse my gigabytes of text more quickly?

Parsing speed depends strongly on the distribution of sentence lengths \- and
on your machine, etc. As one data point, using englishPCFG and a 2.8 GHz Intel
Core 2 Duo processor (mid 2009 Mac Book Pro vintage), 30 word sentences take
about 0.6 seconds to parse.

There's not much in the way of secret sauce to speed that up (partly by the
design of the parsers as guaranteed to find _model optimal_ solutions). If
you're not using `englishPCFG.ser.gz` for English, then you should be - it's
much faster than the Factored parser. If you can exclude extremely long
sentences (especially ones over 60 words or so), then that helps since they
take disproportionately long times to parse. If POS-tagging sentences prior to
parsing is an option, that speeds things up (less possibilities to search).

The main tool remaining is to run multiple parsers at once in parallel. If you
have a machine with enough memory and multiple cores, you can very usefully
run several parsing threads at once. You can do this from the command-line
with the `-nthreads k` option, where _k_ is the number of parsing threads you
want. While multiple `LexicalizedparserQuery` threads share the same grammar
(`LexicalizedParser`), the memory space savings aren't huge, as most of the
memory goes to the transient data structures used in chart parsing. So, if you
are running lots of parsing threads concurrently, you will need to give a lot
of memory to the JVM. You can of course also just use multiple machines or
multiple parsing processes on one machine. Around 2009, we parsed large
volumes of text at a rate of about 1,000,000 sentences a day by distributing
the work over 6 dual core/dual processor machines.

### Can you give me some help in getting started parsing Chinese?

Sure!! These instructions concentrate on parsing from the command line, since
you need to use that to be able to set most options. But you can also use the
parser on Chinese from within the GUI.

The parser is supplied with 5 Chinese grammars (and, with access to suitable
training data, you could train other versions). You can find them _inside_ the
supplied `stanford-parser- _YYYY-MM-DD_ -models.jar` file (in the GUI, select
this file and then navigate inside it; at the command line, use `jar -tf` to
see its contents). All of these grammars are trained on data from the [Penn
Chinese Treebank](http://www.cis.upenn.edu/~chinese/), and you should consult
their site for details of the syntactic representation of Chinese which they
use. They are:

| PCFG| Factored| Factored, segmenting  
---|---|---|---  
Xinhua (mainland, newswire) | `xinhuaPCFG.ser.gz` | `xinhuaFactored.ser.gz` | `xinhuaFactoredSegmenting.ser.gz`  
Mixed Chinese | `chinesePCFG.ser.gz` | `chineseFactored.ser.gz`  
  
The PCFG parsers are smaller and faster. But the Factored parser is
significantly better for Chinese, and we would generally recommend its use.
The `xinhua` grammars are trained solely on Xinhua newspaper text from
mainland China. We would recommend their use for parsing material from
mainland China. The `chinese` grammars also include some training material
from Hong Kong SAR and Taiwan. We'd recommend their use if parsing material
from these areas or a mixture of text types. Note, though that all the
training material uses simplified characters; traditional characters were
converted to simplified characters ( _usually_ correctly). Four of the parsers
assume input that has already been word segmented, while the fifth does word
segmentation internal to the parser. This is discussed further below. The
parser also comes with 3 Chinese example sentences, in files whose names all
begin with `chinese`.

**Character encoding:** The first thing to get straight is the character
encoding of the text you wish to parse. By default, our Chinese parser uses
GB18030 (the native character encoding of the Penn Chinese Treebank and the
national encoding of China) for input and output. However, it is very easy to
parse text in another character encoding: you simply give the flag `-encoding
_encoding_` to the parser, where `_encoding_` is a [character set encoding
name recognized within
Java](http://java.sun.com/j2se/1.5.0/docs/guide/intl/encoding.doc.html), such
as: `UTF-8`, `Big5-HKSCS`, or `GB18030`. This changes the input and output
encoding. If you want to display the output in a command window, you
separately also need to work out what character set your computer supports for
display. If that is different to the encoding of the file, you will need to
convert the encoding for display. If any of this encoding stuff is wrong, then
you are likely to see gibberish. Here are example commands for parsing two of
the test files, one in `UTF-8` and one in `GB18030`. The (Linux) computer that
this is being run on is set up to work with `UTF-8` (and this webpage is also in
`UTF-8`), so for the case of `GB18030`, the output is piped through the Unix
`iconv` utility for display.

```
$ java -server -mx500m
edu.stanford.nlp.parser.lexparser.LexicalizedParser -encoding utf-8 /u/nlp/data/lexparser/chineseFactored.ser.gz chinese-onesent-utf8.txt

Loading parser from serialized file /u/nlp/data/lexparser/chineseFactored.ser.gz ... done [20.7 sec].
Parsing file: chinese-onesent-utf8.txt with 2 sentences.
Parsing [sent. 1 len. 8]: 俄国 希望 伊朗 没有 制造 核武器 计划 。
(ROOT
  (IP
    (NP (NR 俄国))
    (VP (VV 希望)
      (IP
        (NP (NR 伊朗))
        (VP (VE 没有)
          (NP (NN 制造) (NN 核武器) (NN 计划)))))
    (PU 。)))

Parsing [sent. 2 len. 6]: 他 在 学校 里 学习 。
(ROOT
  (IP
    (NP (PN 他))
    (VP
      (PP (P 在)
        (LCP
          (NP (NN 学校))
          (LC 里)))
      (VP (VV 学习)))
    (PU 。)))

Parsed file: chinese-onesent-utf8.txt [2 sentences].
Parsed 14 words in 2 sentences (6.55 wds/sec; 0.94 sents/sec).
```

```
$ java -mx500m -cp stanford-parser.jar edu.stanford.nlp.parser.lexparser.LexicalizedParser chineseFactored.ser.gz chinese-onesent |& iconv -f gb18030 -t utf-8
Loading parser from serialized file chineseFactored.ser.gz ... done [13.3 sec].
Parsing file: chinese-onesent with 1 sentences.
Parsing [sent. 1 len. 10]: 他 和 我 在 学校 里 常 打 桌球 。
(ROOT
  (IP
    (NP (PN 他)
      (CC 和)
      (PN 我))
    (VP
      (PP (P 在)
        (LCP
          (NP (NN 学校))
          (LC 里)))
      (ADVP (AD 常))
      (VP (VV 打)
        (NP (NN 桌球))))
    (PU 。)))

Parsed file: chinese-onesent [1 sentences].
Parsed 10 words in 1 sentences (10.78 wds/sec; 1.08 sents/sec).
```

**Normalization:** As well as the character set, there are also issues of
"normalization" for characters: for instance, basic Latin letters can appear
in either their "regular ASCII" forms or as "full width" forms, equivalent in
size to Chinese characters. Character normalization is something we may
revisit in the future, but at present, _the parser was trained on text which
mainly has fullwidth Latin letters and punctuation and does no normalization,
and so you will get far better results if you also represent such characters
as fullwidth letters_. The parser does provide an escaper that will do this
mapping for you on input. You can invoke it with the `-escaper` flag, by using
a command like the following (which also shows output being sent to a file):

```
$ java -mx500m -cp stanford-parser.jar edu.stanford.nlp.parser.lexparser.LexicalizedParser -escaper edu.stanford.nlp.trees.international.pennchinese.ChineseEscaper -sentences newline chineseFactored.ser.gz chinese-onesent > chinese-onesent.stp
```

**Word segmentation:** Chinese is not normally written with spaces between
words. But the examples shown above were all parsing text that had already
been segmented into words according to the conventions of the Penn Chinese
Treebank. For best results, we recommend that you first segment input text
with a high quality word segmentation system which provides word segmentation
according to Penn Chinese Treebank conventions (note that there are many
different conventions for Chinese word segmentation...). You can find out much
more information about CTB word segmentation from the
[First](http://www.sighan.org/bakeoff2003/),
[Second](http://www.sighan.org/bakeoff2005/), or
[Third](http://www.sighan.org/bakeoff2006/) International Chinese Word
Segmentation Bakeoff. In particular, you can now download a version of our
CRF-based word segmenter (similar to [the system we used in the Second Sighan
Bakeoff](http://nlp.stanford.edu/~manning/papers/sighan_seg.pdf)) from our
[software page](/software/). However, for convenience, we also provide an
ability for the parser to do word segmentation. Essentially, it misuses the
parser as a first-order HMM Chinese word segmentation system. This gives a
reasonable, but not excellent, Chinese word segmentation system. (It's
performance _isn't_ as good as the Stanford CRF word segmenter mentioned
above.) To use it, you use the `-segmentMarkov` option or a grammar trained
with this option. For example:

```
$ iconv -f gb18030 -t utf8 < chinese-onesent-unseg.txt
 他在学校学习。
$ java -mx500m -cp stanford-parser.jar edu.stanford.nlp.parser.lexparser.LexicalizedParser xinhuaFactoredSegmenting.ser.gz chinese-onesent-unseg.txt | & iconv -f gb18030 -t utf-8
Loading parser from serialized file xinhuaFactoredSegmenting.ser.gz ... done [6.8 sec].
Parsing file: chinese-onesent-unseg.txt with 1 sentences.
Parsing [sent. 1 len. 5]: 他 在 学校 学习 。
Trying recovery parse...
Sentence couldn't be parsed by grammar.... falling back to PCFG parse.
(ROOT
  (IP
    (NP (PN 他))
    (VP
      (PP (P 在)
        (NP (NN 学校)))
      (VP (VV 学习)))
    (PU 。)))

Parsed file: chinese-onesent-unseg.txt [1 sentences].
Parsed 5 words in 1 sentences (6.08 wds/sec; 1.22 sents/sec).
  1 sentences were parsed by fallback to PCFG.
```

**Grammatical relations:** The Chinese parser also supports grammatical
relations (typed dependencies) output. For instance:

```
$ java -mx500m -cp stanford-parser.jar edu.stanford.nlp.parser.lexparser.LexicalizedParser -outputFormat typedDependencies xinhuaFactored.ser.gz chinese-onesent | & iconv -f gb18030 -t utf-8
Loading parser from serialized file xinhuaFactored.ser.gz ... done [4.9 sec].
Parsing file: chinese-onesent with 1 sentences.
Parsing [sent. 1 len. 10]: 他 和 我 在 学校 里 常 打 桌球 。
conj(我-3, 他-1)
cc(我-3, 和-2)
nsubj(打-8, 我-3)
prep(打-8, 在-4)
lobj(里-6, 学校-5)
plmod(在-4, 里-6)
advmod(打-8, 常-7)
dobj(打-8, 桌球-9)

Parsed file: chinese-onesent [1 sentences].
Parsed 10 words in 1 sentences (7.10 wds/sec; 0.71 sents/sec).
```

### Can you give me some help in getting started parsing Arabic?

Sure! See the [Stanford Arabic Parser FAQ](https://nlp.stanford.edu/software/parser-arabic-faq.html).

### Can I just use the parser as a vanilla PCFG parser?

There are many kinds of 'vanilla', but, providing your treebank is in Penn
Treebank format, then, yes, this is easy to do. You can train and test the
parser as follows, assuming that your training trees are in `train.txt` and
your test trees are in `test.txt`:

> ` java -mx1g edu.stanford.nlp.parser.lexparser.LexicalizedParser -PCFG
> -vMarkov 1 -uwm 0 -headFinder edu.stanford.nlp.trees.LeftHeadFinder -train
> train.txt -test test.txt > output.txt `

Going through the options, we ask for just the PCFG model (`-PCFG`), for just
conditioning context-free rules based on their left-hand side (parent)
(`-vMarkov 0`), whereas the default also conditions on grandparents (`-vMarkov
1`), to use no language-specific heuristics for unknown word processing (`-uwm
0`), and to always just choose the left-most category on a rule RHS as the
head (`-headFinder edu.stanford.nlp.trees.LeftHeadFinder`). When using a plain
PCFG (i.e., no markovization of rules), the `headFinder` does not affect
results, but unless you use this head finder, you will see errors about the
parser not finding head categories (if your categories differ from those of
the Penn Treebank). This HeadFinder will give consistent left-branching
binarization.

If you would like to also get out the true probabilities that a vanilla PCFG
parser would produce, there are a couple more options that you need to set:

> -smoothTagsThresh 0 -scTags

The option `-smoothTagsThresh 0` stops any probability mass being reserved for
unknown words. The `-scTags` option ensures that true values for P(w|t) are
used. Naturally, such a parser will be unable to parse any sentence with
unknown words in it.

### What about other versions of weaker models?

The [2003 unlexicalized parsing
paper](http://nlp.stanford.edu/~manning/papers/unlexicalized-parsing.pdf)
lists several modifications that gradually improve the performance of the
Stanford parser for English. The last three in particular each make very small
improvements to accuracy but increase the state space quite a bit. To turn
these off, you can use the following options:  BASE-NP| `-baseNP 0`  
---|---  
DOMINATES-V| `-dominatesV 0`  
RIGHT-REC-NP| `-noRightRec`  
  
Depending on the analysis you are doing, you may also want to turn off grammar
compaction, as this obfuscates many of the internal rules. This can be done
with the flag `-compactGrammar 0`

For Chinese, we found that the following options work well for making a
faster, simpler model suitable for the RNN parser:

`-chineseFactored -PCFG -hMarkov 1 -nomarkNPconj -compactGrammar 0`

### Can you give me complete documentation of command-line options/public APIs/included grammars/ParserDemo/...?

At present, we don't have any documentation beyond what you get in the
download and what's on this page. If _you_ would like to help by producing
better documentation, feel free to write to [`parser-
support@lists.stanford.edu`](mailto:parser-support@lists.stanford.edu).

Some parser command-line options are documented. See the `parser.lexparser`
package documentation, the `LexicalizedParser.main` method documentation, the
`TreePrint` class, and the documentation of variables in the `Train`, `Test`,
and `Options` classes, and appropriate language-particular
`TreebankLangParserParams`. For the rest, you need to look at the source code.
The public API is somewhat documented in the `LexicalizedParser` class
JavaDoc. See especially the sample invocation in the `parser.lexparser`
package documentation. The included file `makeSerialized.csh` effectively
documents how the included grammars were made.

The included file `ParserDemo.java` gives a good first example of how to call
the parser programmatically, including getting `Tree` and `typedDependencies`
output. It is included in the root directory of the parser download.

People are often confused about how to get from that example to parsing
paragraphs of text. You need to split the text into sentences first and then
to pass each sentence to the parser. To do that, we use the included class
`DocumentPreprocessor`. A second example titled `ParserDemo2.java` is included
which demostrates how to use the `DocumentPreprocessor`.

With this code, you should be able to parse the sentence in a file with a
command like this (details depending on your shell, OS, etc.):

> ` java -mx200m -cp "stanford-parser.jar:." ParserDemo2 englishPCFG.ser.gz
> testsent.txt `

By default, `DocumentPreprocessor` uses `PTBTokenizer` for tokenization. If
you need to change that, either because you have a better `Tokenizer` for your
domain or because you have already tokenized your text, you can do that by
passing in a `TokenizerFactory` such as a `WhitespaceTokenizerFactory` for no
tokenization beyond splitting on whitespace.

_Fine print:_ The above ParserDemo2 works with the 1.6.x releases of the
Stanford Parser, but doesn't work without adaptation with the Stanford CoreNLP
release of the parser (and will probably need adaptation with 1.7.x releases
of the parser).

### What output formats can I get with the `-outputFormat` and `-outputFormatOptions` options?

You can give the options `-outputFormat typedDependencies` or `-outputFormat
typedDependenciesCollapsed` to get typed dependencies (or grammatical
relations) output (for English and Chinese only, currently). You can print out
lexicalized trees (head words and tags at each phrasal node with the
`-outputFormatOptions lexicalize` option. You can see all the other options by
looking in the Javadoc of the `TreePrint` class.

A common option that people want for `-outputFormatOptions` is to get
punctuation tokens and dependencies when they are not printed by default. You
do that with `-outputFormatOptions includePunctuationDependencies`.

### Can I have the parser run as a filter (that is, parse stuff typed in)?

Yes, you use a filename of a single dash/minus character: -. E.g.,

> ` java -cp stanford-parser.jar
> edu.stanford.nlp.parser.lexparser.LexicalizedParser englishPCFG.ser.gz - `

For interactive use, you may find it convenient to turn off the stderr output.
For example, in bash you could use the command:

> ` java -cp stanford-parser.jar
> edu.stanford.nlp.parser.lexparser.LexicalizedParser englishPCFG.ser.gz - 2>
> /dev/null `

### Can you explain the different parsers? How can the PCFG parser produce typed dependency parses? Why if I use the getBestDependencyParse() method do I get `null` or an untyped dependency parse?

This answer is specific to English. It mostly applies to other languages
although some components are missing in some languages. The file
`englishPCFG.ser.gz` comprises just an unlexicalized PCFG grammar. It is
basically the parser described in the ACL 2003 Accurate Unlexicalized Parsing
paper. The typed dependencies are produced in a postprocessing step after
parsing by matching patterns on CFG trees. This process is described in the
several papers on the topic by Marie-Catherine de Marneffe. Confusingly, the
current code to generate Stanford Dependencies _requires_ a phrase structure
(CFG) parse. It doesn't require or use a dependency parse. The file
`englishFactored.ser.gz` contains two grammars and leads the system to run
_three_ parsers. It first runs a (simpler) PCFG parser and then an untyped
dependency parser, and then runs a third parser which finds the parse with the
best joint score across the two other parsers via a product model. This is
described in the NIPS Fast Exact Inference paper. You can get Stanford
Dependencies from the output of this parser, since it generates a phrase
structure parse. At the API level, with the factored parser, if you ask for
getBestDependencyParse(), then you will get the best untyped dependency parse.
If you call that method with `englishPCFG.ser.gz`, it will return `null`, as
there is no dependency parse. For either, you need to use the separate
GrammaticalStructure classes to get the typed Stanford Dependencies
representation. In general, with appropriate grammars loaded, you can parse
with and ask for output of the PCFG, (untyped) dependency, or factored
parsers. For English, although the grammars and parsing methods differ, the
average quality of `englishPCFG.ser.gz` and `englishFactored.ser.gz` is
similar, and so many people opt for the faster `englishPCFG.ser.gz`, though
`englishFactored.ser.gz` sometimes does better because it does include
lexicalization. For other languages, the factored models are considerably
better than the PCFG models, and are what people generally use. (Since these
parsers were written, direct typed dependency parsers have been increasingly
explored. Both us and others have now built parsers that directly parse to
Stanford Dependencies. See the [Stanford
Dependencies](https://nlp.stanford.edu/software/stanford-dependencies.html)
page for more information.)

### What are the training sets for the different parser models?

For Chinese (and Arabic, German, and "WSJ"), you can look at the included file
makeSerialized.csh , and easily see exactly what files the models are trained
on, in terms of LDC or Negra file numbers.

The only opaque case is english{Factored|PCFG}. For comparable results with
others, you should use the WSJ models which are trained on standard WSJ
sections 2-21, but the english* models should work a bit better for anything
other than 1980s WSJ text.

english{Factored|PCFG} is currently trained on:

    * WSJ sections 1-21 
    * Genia (biomedical English). Originally we used the treebank beta version reformatted by Andrew Clegg, his training split, but more recently (1.6.5+?) we've used the official Treebank, and [David McClosky's splits](http://nlp.stanford.edu/~mcclosky/biomedical.html)
    * 2 English Chinese Translation Treebank and 3 English Arabic Translation Treebank files backported to the original treebank annotation standards (by us) 
    * [209 sentences parsed by us](stanford-english-trees.txt) (mainly questions and imperatives; a few from recent newswire) 
    * [100 imperative sentences parsed by us](stanford-english-imperative-trees.txt)
    * 3924 questions from QuestionBank, with some [hand-correction done at Stanford](/data/QuestionBank-Stanford.html). 
    * [50 Tagged but not parsed sentences](train-tech-english.txt) with tech vocabulary not seen in the WSJ 

The Stanford-written trees are licensed under [Creative Commons Attribution
4.0 International](https://creativecommons.org/licenses/by/4.0/).

However, this list is likely to change in future releases, and this FAQ
question isn't always fully up to date....

### How can I adjust the tokenization of words, such as turning off the Americanization of spelling?

By default, the tokenizer used by the English parser (`PTBTokenizer`) performs
various normalizations so as to make the input closer to the normalized form
of English found in the Penn Treebank. One of these normalizations is the
Americanization of spelling variants (such as changing _colour_ to _color_ ).
Others include things like changing round parentheses to `-LRB-` and `-RRB-`.

Starting with version 1.6.2 of the parser, there is a fairly flexible scheme
for options in tokenization style. You can give options such as this one to
turn off Americanization of spelling:

> ` -tokenizerOptions "americanize=false"`

Or this one to change several options:

> ` -tokenizerOptions
> "americanize=false,normalizeCurrency=false,unicodeEllipsis=true"`

See the documentation of `PTBTokenizer` for details. Programmatically, you can
do the same things by creating a TokenizerFactory with the appropriate
options, such as:

> `parse(new
> DocumentPreprocessor(PTBTokenizerFactory.newWordTokenizerFactory("americanize=false")).getWordsFromString(str));`

There is nevertheless a potential cost of making tokenization changes. This
normalization was added in the first place because the parser is trained on
American English, normalized according to Penn Treebank conventions. There is
no special handling of alternate spellings, etc., so in general changing the
tokenization will mean that variant token forms will be treated via the
general unknown word handling. Often, that works out okay, but, overall,
results won't be quite as good.

### Can I use the parser with Jython?

Absolutely. You can find a helpful tuturial here:
[`http://blog.gnucom.cc/2010/using-the-stanford-parser-with-
jython/`](http://blog.gnucom.cc/2010/using-the-stanford-parser-with-jython/).

### What character encoding does the parser assume/use?

The default character encoding depends on the language that you are parsing.
It is defined in the appropriate TreebankLanguagePack class. That is, it will
never default to your platform default character encoding. The current
defaults are:

    * Arabic: UTF-8
    * Chinese: GB18030
    * English: UTF-8
    * French: ISO_8859-1
    * German: ISO_8859-1
    * Hebrew: UTF-8

However, the parser is able to parse text in any encoding, providing you pass
the correct encoding option on the command line, for example:

> `-encoding ISO_8859-15`

(Or, when used within a program, it is your job to open files with the right
kind of Reader/Writer.)

### What do you recommend for parsing tweets? Do you have a caseless parsing model?

We now (v2.0.1+) distribute a caseless English model, which should work better
for texts, tweets, and similar things. It's named:

`edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz`

The current caseless models can be found on the [CoreNLP home
page](corenlp.html).

So try something like this:

    
        $ java -cp "*" edu.stanford.nlp.parser.lexparser.LexicalizedParser edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz -
    Loading parser from serialized file edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz ... done [2.3 sec].
    Parsing file: -
    i can't believe @mistamau doesn't know who channing tatum is ... #loser
    Parsing [sent. 1 len. 14]: i ca n't believe @mistamau does n't know who channing tatum is ... #loser
    (ROOT
      (S
        (NP (PRP i))
        (VP (MD ca) (RB n't)
          (VP (VB believe)
            (SBAR
              (S
                (NP (NNP @mistamau))
                (VP (VBZ does) (RB n't)
                  (VP (VB know)
                    (SBAR
                      (WHNP (WP who))
                      (S
                        (NP (NNP channing) (NNP tatum))
                        (VP (VBZ is) (: ...)
                          (S
                            (VP (VB #loser))))))))))))))
    
    Parsed file: - [1 sentences].
    Parsed 14 words in 1 sentences (4.29 wds/sec; 0.31 sents/sec).
    

This parse isn't quite correct (it messes up the hashtag at the end), but the
caseless model does correctly parse "Channing Tatum" as a proper name.

### How do you get a SemanticGraph from a Tree?

The easiest way is to use the conversion methods in ParserAnnotatorUtils,
which is included in the latest versions of the parser (since 2.0.3).

### Why is the parser output different from the CoreNLP output?

For some sentences the parse tree output by the standalone parser and the tree
output by the CoreNLP pipeline can be different. The reason for this is that
if you run the CoreNLP pipeline with the default annotators, it will run a
part-of-speech (POS) tagger before running the parser. If you run the parser
on an already POS-tagged sentence, it considers the POS tags as being fixed
and ignores the words in the sentence.

If you want to obtain the same results, you can either POS-tag your corpus
before tagging it (see #12) or you can disable the POS tagger in CoreNLP by
updating the list of annotators:

    
        -annotators "tokenize,ssplit,parse,lemma,ner,dcoref"

### How can I get original Stanford Dependencies instead of Universal Dependencies?

If you run the parser or the dependency converter from the command line, then
just add the option `-originalDependencies` to your command.

If you call the parser programmatically and then convert the parse tree to a
list of grammatical relations, you have to call
`setGenerateOriginalDependencies(true)` on your instance of
`TreebankLanguagePack` as shown in the following snippet:

    
        LexicalizedParser lp = LexicalizedParser.loadModel(
                             "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz",
                             "-maxLength", "80", "-retainTmpSubcategories");
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    tlp.setGenerateOriginalDependencies(true);
    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
    
    String[] sent = "This", "is", "an", "easy", "sentence", "." ;
    Tree parse = lp.apply(Sentence.toWordList(sent));
    GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
    Collection<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
    System.out.println(tdl);
    

Alternatively, if you use `SemanticGraphFactory.makeFromTree()` to build a
`SemanticGraph` from a constitueny tree, then use the following method with
`originalDependencies` set to `true`. (Note: in previous versions of the
parser, the method had an additional `boolean threadSafe` parameter, which we
have now eliminated.)

    
        SemanticGraphFactory.makeFromTree(Tree tree,
                                        Mode mode,
                                        GrammaticalStructure.Extras includeExtras,
                                        Predicate filter,
                                        boolean originalDependencies)
    

### Can I get hold of the PCFG grammar for your PCFG models?

Yes, you can. You use a command like this to get it in a text file:

> `java -cp "*" edu.stanford.nlp.parser.lexparser.LexicalizedParser
> -loadFromSerializedFile edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz
> -saveToTextFile englishPCFG.txt `

However, there are a few caveats:

    1. The grammar is induced from data, not hand-written, and so expect something messy and ugly. 
    2. It’s already a binarized grammar with automatically generated labels for binarized nodes. 
    3. It includes the kind of state refinement introduced in the Klein and Manning (2003) paper. 
    4. The grammar includes word class based signatures for unknown words, and to fully use the grammar, you have to map unknown words on to those signatures. 

### How can I get the missing punctuation in my dependency output?

If your dependency output is missing punctuation tokens, you can get them by
adding the flag:

> ` -outputFormatOptions includePunctuationDependencies `

(We were originally too linguistic, and the default output did not have
punctuation tokens and punctuation dependencies. We're gradually moving the
default option over to have punctuation, but it's still a bit inconsistent as
to where it is the default or not.

