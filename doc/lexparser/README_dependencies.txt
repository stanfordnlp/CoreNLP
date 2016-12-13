UNIVERSAL/STANFORD DEPENDENCIES.  Stanford Parser v3.7.0
-----------------------------------------------------------

IMPORTANT: Starting with version 3.5.2 the default dependencies
representation output by the Stanford Parser is the new Universal
Dependencies Representation. Universal Dependencies were developed
with the goal of being a cross-linguistically valid representation.
Note that some constructions such as prepositional phrases are now 
analyzed differently and that the set of relations was updated. The
online documentation of English Universal Dependencies at

    http://www.universaldependencies.org

should be consulted for the current set of dependency relations.


The parser and converter also still support the original 
Stanford Dependencies as described in the Stanford Dependencies 
manual. Use the flag

    -originalDependencies

to obtain the original Stanford Dependencies. Note, however, that we
are no longer maintaining the SD converter or representation and we
therefore recommend to use the Universal Dependencies representation
for any new projects.


The manual for the English version of the Stanford Dependencies
representation:

    StanfordDependenciesManual.pdf

should be consulted for the set of dependency relations in the original
Stanford Dependencies representation and the correct commands for 
generating Stanford Dependencies together with any of the Stanford Parser, 
another parser, or a treebank.

A typed dependencies representation is also available for Chinese.  For
the moment the documentation consists of the code, and a brief
presentation in this paper:

Pi-Chuan Chang, Huihsin Tseng, Dan Jurafsky, and Christopher
D. Manning. 2009.  Discriminative Reordering with Chinese Grammatical
Relations Features.  Third Workshop on Syntax and Structure in Statistical
Translation. http://nlp.stanford.edu/pubs/ssst09-chang.pdf

--------------------------------------
DEPENDENCIES SCHEMES

For an overview of the original English Universal Dependencies schemes, please look
at:

  Marie-Catherine de Marneffe, Timothy Dozat, Natalia Silveira, Katri Haverinen,
  Filip Ginter, Joakim Nivre, and Christopher D. Manning. 2014. Universal Stanford
  dependencies: A cross-linguistic typology. 9th International Conference on
  Language Resources and Evaluation (LREC 2014).
  http://nlp.stanford.edu/~manning/papers/USD_LREC14_UD_revision.pdf
  
  and
  
  Joakim Nivre, Marie-Catherine de Marneffe, Filip Ginter, Yoav Goldberg, Jan Hajič,
  Christopher D. Manning, Ryan McDonald, Slav Petrov, Sampo Pyysalo, Natalia Silveira,
  Reut Tsarfaty, and Daniel Zeman. 2016. Universal Dependencies v1: A Multilingual 
  Treebank Collection. In Proceedings of the Tenth International Conference on Language 
  Resources and Evaluation (LREC 2016).
  http://nlp.stanford.edu/pubs/nivre2016ud.pdf
  
Please note, though, that some of the relations discussed in the first paper
were subsequently updated and please refer to the online documentation at
    
    http://www.universaldependencies.org

for an up to date documention of the set of relations.

For an overview of the enhanced and enhanced++ dependency representations, please look 
at:

  Sebastian Schuster and Christopher D. Manning. 2016. Enhanced English Universal 
  Dependencies: An Improved Representation for Natural Language Understanding Tasks. 
  In Proceedings of the Tenth International Conference on Language Resources and 
  Evaluation (LREC 2016).
  http://nlp.stanford.edu/~sebschu/pubs/schuster-manning-lrec2016.pdf

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

These papers can be cited as references for the original English Stanford
Dependencies and Enlgish Universal Dependencies.

--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v3.7.0

Implementation of enhanced and enhanced++ dependency
representations as described in Schuster and Manning (2016).

Fixed concurrency issue.

--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v3.5.2

Switch to Universal Dependencies as the default representation.
Please see the Universal Dependencies documentation at

      http://www.universaldependencies.org

for more information on the new relations.

--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v3.5.1

A couple of small fixes were made, leading to ccomp and advcl being
recognized in a couple of new environments.

--------------------------------------
CHANGES IN ENGLISH TYPED DEPENDENCIES CODE -- v3.4

One major change was made to the dependency taxonomy:

 - We decided to collapse together the two dependencies partmod and infmod,
 since they have similar function and mainly differ in the form of the verbal
 head, which is anyways recorded in the POS tag. Those two relations are
 removed from the taxonomy, and a new relation vmod covering the union of both
 was added.

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

An improved dependency conversion means that our dependency trees are
not always projective, one deletion was made from the taxonomy of
relations, and various small converter fixes were made:
 - rel was removed. rel was originally used as the relation for an
    overt relativizer in a relative clause. But it was never a real
    grammatical relation, and we gradually started labeling easy cases
    as nsubj or dobj. In this release, rel is removed, pobj cases are
    also labeled, and the remaining hard cases are labeled as dep.
 - As a result of correctly labeling a pobj in questions and relative
   clauses, the converter now sometimes produces non-projective dependency
   trees (ones with crossing dependencies, if the words are laid out in
   their normal order in a line, and all dependency arcs are drawn above
   them). This is not a bug, it's an improvement in the generated
   dependencies, but you should be aware that Stanford Dependencies
   trees are now occasionally non-projective. (Some simple dependency
   parsing algorithms only produce projective dependency trees.)

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

