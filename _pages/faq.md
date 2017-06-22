---
title: Frequently Asked Questions 
keywords: faq
permalink: '/faq.html'
---

## CoreNLP stops with a FileNotFoundException, RuntimeIOException, or after failing to find a class or data file.  How do I fix this?

The most likely cause of these errors is that one or more of the
important jar files is missing.  If it occurs when loading the models,
make sure the current models file is in the classpath. The basic
models file has a name like `stanford-corenlp-V.V.V-models.jar`,
depending on the version. For other language models, you may also need
additional models jars, which will have the language name in them. If you
encounter this exception when trying to produce XML output, make sure
`xom.jar` is included.  Finally, if it seems to occur when loading
SUTime, be sure to include `joda-time.jar`, etc.

Basically, you want to include all of the jar files in the download
directory unless you are sure a particular jar is not needed.

## How do I use the API?

A brief demo program included with the download will demonstrate how
to load the tool and start processing text.  When using this demo
program, be sure to include all of the appropriate jar files in the
classpath.

Once you have tried this, there is quite a bit of information on the
[CoreNLP home page](http://nlp.stanford.edu/software/corenlp.html) 
describing what Annotators are available, what annotations they add to
the text, and what options they support.

## What character encoding does Stanford CoreNLP use?

By default, it uses Unicode's UTF-8.  You can change the encoding used when
reading files by either setting the Java encoding property or more
simply by supplying the program with the command line flag `-encoding FOO` (or including the
corresponding property in a properties file that you are using).

## Can you say more about adding a custom annotator?

Here are the steps:

- Extend the class edu.stanford.nlp.pipeline.Annotator
I assume you're writing your own code to do the processing.  Whatever
code you write, you want to call it from a class that is a subclass of
Annotator.  Look at any of the existing Annotator classes, such as
POSTaggerAnnotator, and try to emulate what it does.
- Have a constructor with the signature (String, Properties)
If your new annotator is FilterAnnotator, for example, it must have a
constructor FilterAnnotator(String name, Properties props) in order to
work.
- Add the property `customAnnotatorClass.FOO=BAR`
Using the same example, suppose your full class name is
com.foo.FilterAnnotator, and you want the new annotator to have the
name "filter".  When creating the CoreNLP properties, you need to add
the flag `customAnnotatorClass.filter=com.foo.FilterAnnotator`
You can then add "filter" to the list of annotators in the annotators
property.  When you do that, the constructor FilterAnnotator(String,
Properties) will be called with the name "filter" and the properties
files you run CoreNLP with.  This lets you define any property flag
you want.  For example, you could name a flag filter.verbose and then
extract that flag from the properties to determine the verbosity of
your new annotator.

## What is the format of the XML output for coref?

Here is a sample block of coref xml output:
```
<coreference>
  <coreference>
    <mention representative="true">
      <sentence>1</sentence>
      <start>1</start>
      <end>3</end>
      <head>2</head>
    </mention>
    <mention>
      <sentence>2</sentence>
      <start>1</start>
      <end>2</end>
      <head>1</head>
    </mention>
  </coreference>
</coreference>
```

The entire coref section is demarked by
a `<coreference>` section.  Each individual chain is
then demarked by another `<coreference>`.  (This is
perhaps an unfortunate naming, but at this point there are no plans to
change it.)
Inside the `<coreference>` section for each chain is
a block describing each of the mentions.  One mention will be labeled
the `representative` mention.  There are fields
for `sentence`, indexed from 1 the range of words,
from `start` (inclusive) to `end` (not
inclusive), also indexed from 1, and `head`, the index in
the sentence of the head word of this mention.

## CoreNLP runs out of memory?

Either give CoreNLP more memory, use fewer annotators, or give CoreNLP smaller documents.  Nearly all our
annotators load large model files which use lots of memory.  Running the
full CoreNLP pipeline requires the sum of all these memory
requirements.  Typically, this means that CoreNLP needs about 2GB to run
the entire pipeline.  Additionally, the coreference module operates over an
entire document.  Unless things are size-limited, as either sentence
length or document size increases, processing time
and space increase without bound.  

Running from the command line, you need to supply a flag like
`-Xmx2g`.  
If running CoreNLP from within Eclipse, follow 
[these instructions](http://stackoverflow.com/questions/4175188/setting-memory-of-java-programs-that-runs-from-eclipse)
to increase the memory given to a program being run from inside
Eclipse. Increasing the amount of memory given to Eclipse itself won't help.

## What does SET mean in the NER output?

This is part of SUTime. It applies to repeating events such as
"every other week" or "every two weeks".  SET is not the best name for
such an event, but it matches the 
[TIMEX3 standard](http://www.timeml.org/site/publications/timeMLdocs/timeml_1.2.1.html)
(see section 2.3 of the linked document)

## How do I run CoreNLP on other languages?

Other than English, we currently provide trained CoreNLP models for Chinese. To run CoreNLP on Chinese text, you first have to download the models, which can be found in our [release history](doc_history.html).
Include this .jar in your classpath, and use the StanfordCoreNLP-chinese.properties file it contains to process Chinese. For example, if you put the .jar in your distribution directory, you could run (adjusting the .jar version file extensions to your current release):
`java -cp stanford-corenlp-VV.jar:stanford-chinese-corenlp-VV-models.jar -Xmx3g edu.stanford.nlp.pipeline.StanfordCoreNLP -props StanfordCoreNLP-chinese.properties -file your-chinese-file.txt`

## How do I fix CoreNLP giving a NoSuchMethodError or NoSuchFieldError?

If you see an Exception stacktrace  message like:
```
Exception in thread "main" java.lang.NoSuchFieldError: featureFactoryArgs
    at edu.stanford.nlp.ie.AbstractSequenceClassifier.<init>(AbstractSequenceClassifier.java:127)
    at edu.stanford.nlp.ie.crf.CRFClassifier.<init>(CRFClassifier.java:173)
```
or
```
Exception in thread "main" java.lang.NoSuchMethodError: edu.stanford.nlp.tagger.maxent.TaggerConfig.getTaggerDataInputStream(Ljava/lang/String;)Ljava/io/DataInputStream;
```
or
```
Caused by: java.lang.NoSuchMethodError: edu.stanford.nlp.util.Generics.newHashMap()Ljava/util/Map;
    at edu.stanford.nlp.pipeline.AnnotatorPool.<init>(AnnotatorPool.java:27)
    at edu.stanford.nlp.pipeline.StanfordCoreNLP.getDefaultAnnotatorPool(StanfordCoreNLP.java:305)
```

then this *isn't* caused by the shiny new Stanford NLP tools that
you've just downloaded.  It is because you also have old versions of one
or more Stanford NLP tools on your classpath.  

The straightforward case
is if you have an older version of a Stanford NLP tool.  For example,
you may still have a version of Stanford NER on your classpath that was
released in 2009.  In this case, you should upgrade, or at least use
matching versions. For any releases from 2011 on, just use tools
released at the same time -- such as the most recent version of 
everything :) -- and they will all be compatible and play nicely together.

The tricky case of this is when people distribute jar files that hide
other people's classes inside them. People think this will make it easy
for users, since they can distribute one jar that has everything you
need, but, in practice, as soon as people are building applications
using multiple components, this results in a particular bad form
of *jar hell*. People just shouldn't do this.
The only way to check that other jar files do not
contain conflicting versions of Stanford tools is to look at what is inside
them (for example, with the `jar -tf` command).

In practice, if you're having problems, the most common cause (in
2013-2014) is that you have
`ark-tweet-nlp` on your classpath. The jar file in their github
download hides *old* versions of *many* other people's jar files, including Apache
commons-codec (v1.4), commons-lang, commons-math, commons-io, Lucene; Twitter
commons; Google Guava (v10); Jackson; Berkeley NLP code; Percy Liang's fig;
GNU trove; *and* an outdated version of the Stanford POS tagger
(from 2011). You should complain to them for creating you and us
grief. But you can then fix the problem by using [their jar file from
Maven Central](http://search.maven.org/#browse%7C964929444). It doesn't have all those other libraries stuffed inside.

## How do I fix CoreNLP giving an "IllegalArgumentException: Unknown option: -retainTmpSubcategories", when trying to use it with a language other than English?

You need to add the flag `-parse.flags ""` (or the
corresponding property `parse.flags: &nbsp; `). It's sort of a misfeature/bug
that the default properties of CoreNLP turn this option on by default, because it is useful for
English, but it isn't defined for other languages, and so you get an error.) 

## How do I add constraints to the parser in CoreNLP?

The parser can be instructed to keep certain sets of tokens together
as a single constituent.  If you do this, it will try to make a parse
which contains a subtree where the exact set of tokens in that subtree
are the ones specified in the constraint.

For any sentence where you want to add constraints, attach
the `ParserAnnotations.ConstraintAnnotation` to that
sentence.  This annotation is
a `List<ParserConstraint>`,
where `ParserConstraint` specifies the start (inclusive)
and end (exclusive) of the range and a pattern which the enclosing
constituent must match.  However, there is a bug in the way patterns
are handled in the parser, so it is strongly recommended to
use `.*` for the matching pattern.

## How can I get original Stanford Dependencies instead of Universal Dependencies?

If you want CoreNLP to output the original Stanford Dependencies instead of the new Universal Dependencies, simply add the option
`-parse.originalDependencies` or the property `("parse.originalDependencies", true)` to your command or code, respectively.

Note, however,  that some annotators that use dependencies such as natlog might not function properly if you use this option.
In case you are using the [Neural Network Dependency Parser](http://nlp.stanford.edu/software/nndep.html), use the following model to get Stanford Dependencies: 
```
-depparse.model "edu/stanford/nlp/models/parser/nndep/english_SD.gz"
```



