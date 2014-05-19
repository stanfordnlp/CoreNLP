STANFORD DEPENDENCIES.  Stanford Parser v3.3.1
-----------------------------------------------------------

The manual for the English version of the Stanford Dependencies
representation:

    StanfordDependenciesManual.pdf

should be consulted for the current set of dependency representations
and the correct commands for generating Stanford Dependencies together
with any of the Stanford Parser, another parser, or a treebank.

A typed dependencies representation is also available for Chinese.  For
the moment the documentation consists of the code, and a brief
presentation in this paper:

Pi-Chuan Chang, Huihsin Tseng, Dan Jurafsky, and Christopher
D. Manning. 2009.  Discriminative Reordering with Chinese Grammatical
Relations Features.  Third Workshop on Syntax and Structure in Statistical
Translation.


--------------------------------------
ORIGINAL DEPENDENCIES SCHEME

For an overview of the original typed dependencies scheme, please look
at:

  Marie-Catherine de Marneffe, Bill MacCartney, and Christopher D.
  Manning. 2006. Generating Typed Dependency Parses from Phrase
  Structure Parses. 5th International Conference on Language Resources
  and Evaluation (LREC 2006).
  http://nlp.stanford.edu/~manning/papers/LREC_2.pdf

For more discussion of the design principles, please see:

  Marie-Catherine de Marneffe and Christopher D. Manning. 2008. The
  Stanford typed dependencies representation. In Proceedings of the
  workshop on Cross-Framework and Cross-Domain Parser Evaluation, pp. 1-8.
  http://nlp.stanford.edu/~manning/papers/dependencies-coling08.pdf

These papers can be cited as references for the English Stanford
Dependencies.


--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v3.3.1

A couple of fixes/improvements were made in the dependency conversion,
and one change was made to the taxonomy of relations.

 - The partmod and infmod relations were deleted, and replaced with
vmod for reduced, non-finite verbal modifiers. The distinction between
these two relations can be recovered from the POS tag of the dependent.
 - A couple of improvements were made to the conversion, the largest
 one being recognizing pobj inside a PP not headed by something tagged
 as IN or TO.


--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v3.3

Some fixes/improvements were made in the dependency conversion, and one
change was made to the taxonomy of relations.

 - For currency amount expressions with a currency symbol like "$", it
   had previously been the case that "$" was the head, and then each
   number word modified it as a number. We realized that this was
   unnecessarily inconsistent. For the expression "two thousand dollars",
   "dollars" is the head, but "thousand" is a num modifier of it, and
   number is used for the parts of a number multi-word expression only.
   This analysis is now also used for cases with a currency symbol. E.g.,
   "for $ 52.7 million": prep(for, $) num($, million) number(million, 52.7).
   Similarly, for "the $ 2.29 billion value", we changed the analysis from
   num(value, $) number($, billion) to amod(value, $) num($, billion).
   This corresponds to hwat you got for "a two dollar value".
   This is actually the most common change (at least on WSJ newswire!).
 - Remove the attr relation. Some cases disappear by making the question
   phrase of WHNP be NP questions the root. Others (predicative NP
   complements) become xcomp.
 - Less aggressive labeling of participial form VPs as xcomp. More of them
   are correctly labeled partmod (but occasionally a true xcomp is also
   mislabeled as partmod).
 - Small rule changes to recognize a few more ccomp and parataxis.


--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v3.2, JUNE 2013

Various small fixes were made to the dependencies conversion,
and one change to the taxonomy of relations:
 - rel was removed. rel was originally used as the relation for an
    overt relativizer in a relative clause. But it was never a real
    grammatical relation, and we gradually started labeling easy cases
    as nsubj or dobj. In this release, rel is removed, pobj cases are
    also labeled, and the remaining hard cases are labeled as dep.

--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v2.0.5, MARCH 2013

We have begun a more major effort to improve the suitability and coverage of
Stanford Dependencies on less formal text types, and to clean up a couple of
the more quirky dependencies in the original set. These changes are still
ongoing, but in this first installment, we have removed 3 dependencies and
added 2:
 - abbrev was removed, and is now viewed as just a case of appos.
 - complm was removed, and is now viewed as just a case of mark.
    (This is consistent with an HPSG-like usage of mark.)
 - purpcl was removed, and is now viewed as just a case of advcl.
 - discourse was added. The lack of a dependency type for
    interjections was an omission even in the early versions, but it
    became essential as we expanded our consideration of informal
    text types. It is used for interjections, fillers, discourse markers
    and emoticons.
  - goeswith was added. In badly edited text, it is used to join the
    two parts of a word.

A few other changes and improvements were also made, including improvements
in the recognition of advcl. There has been a reduction of "dep" dependencies
of about 14% on newswire (and higher on more informal text genres).


--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v2.0.4, NOVEMBER 2012

A few minor changes and fixes were made: HYPH is now recognized, and treated
as punctuation and clausal complements of adjectives (including comparatives)
are recognized as ccomp.

--------------------------------------

CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v1.6.9

This version adds an explicit root dependency in the set of dependencies
returned.  In the past, there had been no explicit representation of the
root of the sentence in the set of dependencies returned, except in the
CoNLL format output, which always showed the root.  Now, there is always
an explicit extra dependency that marks the sentence root, using a fake
ROOT pseudoword with index 0.  That is, the root is marked in this way:
    root(ROOT-0, depends-3)
Otherwise there were only a couple of minute changes in the dependencies
produced (appositions are now recognized in WHNPs!).

--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v1.6.8

This version includes only small fixes, principally addressing some gaps
in the correct treatment of dependencies in inverted sentence (SQ and SINV)
constructions, and some errors in the treatment of copulas in the presence of
temporal NPs.


--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- NOVEMBER 2010 - JANUARY 2011

Two changes were made to the taxonomy of dependencies.
 - measure (phrase modifier) was generalized and replaced by
npadvmod (noun phrase adverbial modifier) which includes measure
phrases and other adverbial uses of noun phrases.  Temporal NPs
(tmod) are now a subtype of npadvmod in the dependency hierarchy.
 - mwe (multi-word expression) is introduced for certain common
function word dependencies for which another good analysis isn't
easy to come by (and which were frequently dep before) such as
"instead of" or "rather than".

A new option has ben added to allow the copula to be treated as
the head when it has an adjective or noun complement.

The conversion software will now work fairly well with the
David Vadas version of the treebank with extra noun phrase
structure.  (A few rare cases that are handled with the standard
treebank aren't yet handled, but you will get better dependencies
for compound nouns and multiword adjectival modifiers, etc.)

Considerable improvements were made in the coverage of named
dependencies. You should expect to see only about half as many generic
"dep" dependencies as in version 1.6.4.

--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- JUNE-AUGUST 2010

No new dependency relations have been introduced.

There have been some significant improvements in the generated
dependencies, principally covering:
 - Better resolution of nsubj and dobj long distance dependencies
   (but v1.6.4 fixes the overpercolation of dobj in v1.6.3)
 - Better handling of conjunction distribution in CCprocessed option
 - Correction of bug in v1.6.2 that made certain verb dependents noun
   dependents.
 - Better dependencies are generated for question structures (v1.6.4)
 - Other minor improvements in recognizing passives, adverbial
   modifiers, etc.

