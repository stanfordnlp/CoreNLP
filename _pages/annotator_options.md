---
title: Annotator options 
keywords: options
permalink: '/annotator_options.html'
---

While all Annotators have a default behavior that is likely to be sufficient for the majority of users, most Annotators take additional options that can be passed as Java properties in the configuration file. We list below the configuration options for all Annotators:

**general options**:

* outputFormat: different methods for outputting results.  Can be "xml", "text" or "serialized".
* annotators: which annotators to use.
* encoding: the character encoding or charset. The default is "UTF-8".

**tokenize**:

* tokenize.whitespace: if set to true, separates words only when
whitespace is encountered.
* tokenize.options: Accepts the options of `PTBTokenizer` or example, things like "americanize=false" or "strictTreebank3=true,untokenizable=allKeep".

**cleanxml**:

* clean.xmltags: Discard xml tag tokens that match this regular expression.  For example, .* will discard all xml tags.
* clean.sentenceendingtags: treat tags that match this regular expression as the end of a sentence.  For example, p will treat &lt;p&gt; as the end of a sentence.
* clean.allowflawedxml: if this is true, allow errors such as unclosed tags.  Otherwise, such xml will cause an exception.
* clean.datetags: a regular expression that specifies which tags to treat as the reference date of a document.  Defaults to datetime|date


**ssplit**:

* ssplit.eolonly: only split sentences on newlines.  Works well in
conjunction with "-tokenize.whitespace true", in which case
StanfordCoreNLP will treat the input as one sentence per line, only separating
words on whitespace.
* ssplit.isOneSentence: each document is to be treated as one
sentence, no sentence splitting at all.
* ssplit.newlineIsSentenceBreak: Whether to treat newlines as sentence
  breaks.  This property has 3 legal values: "always", "never", or
  "two". The default is "never".  "always" means that a newline is always
  a sentence break (but there still may be multiple sentences per
  line). This is often appropriate for texts with soft line
  breaks. "never" means to ignore newlines for the purpose of sentence
  splitting. This is appropriate when just the non-whitespace
  characters should be used to determine sentence breaks. "two" means
  that two or more consecutive newlines will be 
  treated as a sentence break. This option can be appropriate when
  dealing with text with hard line breaking, and a blank line between paragraphs.
  **Note**: A side-effect of setting ssplit.newlineIsSentenceBreak to "two" or "always" is that tokenizer will tokenize newlines.
    
* ssplit.boundaryMultiTokenRegex: Value is a multi-token sentence
  boundary regex.
* ssplit.boundaryTokenRegex:
* ssplit.boundariesToDiscard:
* ssplit.htmlBoundariesToDiscard
* ssplit.tokenPatternsToDiscard: 
 

**pos**:

* pos.model: POS model to use. There is no need to explicitly set this option, unless you want to use a different POS model (for advanced developers only). By default, this is set to the english left3words POS model included in the stanford-corenlp-models JAR file.
* pos.maxlen: Maximum sentence size for the POS sequence tagger.  Useful to control the speed of the tagger on noisy text without punctuation marks.  Note that the parser, if used, will be much more expensive than the tagger.


**ner**:

* ner.useSUTime: Whether or not to use sutime.  On by default in the version which includes sutime, off by default in the version that doesn't.  If not processing English, make sure to set this to false.
* ner.model: NER model(s) in a comma separated list to use instead of the default models.  By default, the models used will be the 3class, 7class, and MISCclass models, in that order.
* ner.applyNumericClassifiers: Whether or not to use numeric classifiers, including [SUTime](http://nlp.stanford.edu/software/regexner/).  These are hardcoded for English, so if using a different language, this should be set to false.
* sutime.markTimeRanges: Tells sutime to mark phrases such as "From January to March" instead of marking "January" and "March" separately
* sutime.includeRange: If marking time ranges, set the time range in the TIMEX output from sutime


**regexner**:

* regexner.mapping: The name of a file, classpath, or URI that contains NER rules, i.e., the mapping from regular expressions to NE classes. The format is one rule per line; each rule has two mandatory fields separated by one tab. The first field stores one or more Java regular expression (without any slashes or anything around them) separated by non-tab whitespace. The second token gives the named entity class to assign when the regular expression matches one or a sequence of tokens. An optional third tab-separated field indicates which regular named entity types can be overwritten by the current rule. For example, the rule "U\.S\.A\.        COUNTRY        LOCATION" marks the token "U.S.A." as a COUNTRY, allowing overwriting the previous LOCATION label (if it exists).  An optional fourth  tab-separated field gives a real number-valued rule priority. Higher priority rules are tried first for matches.  Here is [a simple example](http://nlp.stanford.edu/software/regexner/).
* regexner.ignorecase: if set to true, matching will be case insensitive. Default value is false.  In the simplest case, the mapping file can be just a word list of lines of "word TAB class". Especially in this case, it may be easiest to set this to true, so it works regardless of capitalization.
* regexner.validpospattern: If given (non-empty and non-null) this is a regex that must be matched (with <code>find()</code>) againstat least one token in a match for the NE to be labeled.


**parse**:

* parse.model: parsing model to use. There is no need to explicitly set this option, unless you want to use a different parsing model (for advanced developers only). By default, this is set to the parsing model included in the stanford-corenlp-models JAR file.
* parse.maxlen: if set, the annotator parses only sentences shorter (in terms of number of tokens) than this number. For longer sentences, the parser creates a flat structure, where every token is assigned to the non-terminal X. This is useful when parsing noisy web text, which may generate arbitrarily long sentences. By default, this option is not set.
* parse.flags: flags to use when loading the parser model.  The English model used by default uses "-retainTmpSubcategories"
* parse.originalDependencies: Generate original Stanford Dependencies grammatical relations instead of Universal Dependencies. Note, however, that some annotators that use dependencies such as natlog might not function properly if you use this option.  If you are using the [Neural Network dependency parser](http://nlp.stanford.edu/software/nndep.html) and want to get the original SD relations, see the [CoreNLP FAQ](http://127.0.0.1:4001/doc_faq.html#how-can-i-get-original-stanford-dependencies-instead-of-universal-dependencies) on how to use a model trained on Stanford Dependencies.


**depparse**:

* depparse.model: dependency parsing model to use. There is no need to
  explicitly set this option, unless you want to use a different parsing
  model than the default. By default, this is set to the UD parsing model included in the stanford-corenlp-models JAR file.

* depparse.extradependencies: Whether to include extra (enhanced)
  dependencies in the output. The default is NONE (basic dependencies)
  and this can have other values of the GrammaticalStructure.Extras
  enum, such as SUBJ_ONLY or MAXIMAL (all extra dependencies).
s
**dcoref**:

* dcoref.sievePasses: list of sieve modules to enable in the system, specified as a comma-separated list of class names. By default, this property is set to include: "edu.stanford.nlp.dcoref.sievepasses.MarkRole, edu.stanford.nlp.dcoref.sievepasses.DiscourseMatch, edu.stanford.nlp.dcoref.sievepasses.ExactStringMatch, edu.stanford.nlp.dcoref.sievepasses.RelaxedExactStringMatch, edu.stanford.nlp.dcoref.sievepasses.PreciseConstructs, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch1, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch2, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch3, edu.stanford.nlp.dcoref.sievepasses.StrictHeadMatch4, edu.stanford.nlp.dcoref.sievepasses.RelaxedHeadMatch, edu.stanford.nlp.dcoref.sievepasses.PronounMatch".  The default value can be found in Constants.SIEVEPASSES.
* dcoref.demonym: list of demonyms from <a href="http://en.wikipedia.org/wiki/List_of_adjectival_forms_of_place_names">http://en.wikipedia.org/wiki/List_of_adjectival_forms_of_place_names</a>. The format of this file is: location TAB singular gentilic form TAB plural gentilic form, e.g., "Algeria Algerian Algerians".
* dcoref.animate and dcoref.inanimate: lists of animate/inanimate words, from (Ji and Lin, 2009). The format is one word per line.
* dcoref.male, dcoref.female, dcoref.neutral: lists of words of male/female/neutral gender, from (Bergsma and Lin, 2006) and (Ji and Lin, 2009). The format is one word per line.
* dcoref.plural and dcoref.singular: lists of words that are plural or singular, from (Bergsma and Lin, 2006). The format is one word per line. All the above dictionaries are already set to the files included in the stanford-corenlp-models JAR file, but they can easily be adjusted to your needs by setting these properties.
* dcoref.maxdist: the maximum distance at which to look for mentions.  Can help keep the runtime down in long documents.
* oldCorefFormat: produce a CorefGraphAnnotation, the output format used in releases v1.0.3 or earlier.  Note that this uses quadratic memory rather than linear.


**sentiment**:

* sentiment.model: which model to load.  Will default to the model included in the models jar.


**quote**:

* quote.singleQuotes: whether or not to consider single quotes as quote delimiters. Default is "false".


<p>**<u>Javadoc</u>**

<p>
More information is available in the javadoc: 
<a href="http://nlp.stanford.edu/nlp/javadoc/javanlp/">
  Stanford Core NLP Javadoc</a>.
