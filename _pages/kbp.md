---
layout: page
title: KBP
keywords: kbp, KBP
permalink: '/kbp.html'
nav_order: 16
parent: Pipeline
---

## Description

Extracts relation triples meeting the [TAC-KBP](https://tac.nist.gov/2017/KBP/) competition specifications.

For example when run on the input sentence:

```
Joe Smith was born in Oregon.
```

The annotator will find the following `("subject", "relation", "object")` triple:

```
("Joe Smith", "per:stateorprovince_of_birth", "Oregon" }
```

| Property name | Annotator class name | Generated Annotation |
| --- | --- | --- |
| kbp | KBPAnnotator | KBPTriplesAnnotation |

## Example Usage

### Command Line

```
java -Xmx16g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner,parse,coref,kbp -coref.md.type RULE -file example.txt
```

## List Of Relations

| Relation name |
| --- |
| gpe_subsidiaries |
| org_alternate_names |
| org_city_of_headquarters |
| org_country_of_headquarters |
| org_date_dissolved |
| org_date_founded |
| org_dissolved |
| org_founded |
| org_founded_by |
| org_member_of |
| org_members |
| org_number_of_employees/members |
| org_parents |
| org_political/religious_affiliation |
| org_shareholders |
| org_stateorprovince_of_headquarters |
| org_subsidiaries |
| org_top_members/employees |
| org_website |
| per_age |
| per_alternate_names |
| per_cause_of_death |
| per_charges |
| per_children |
| per_cities_of_residence |
| per_city_of_birth |
| per_city_of_death |
| per_countries_of_residence |
| per_country_of_birth |
| per_country_of_death |
| per_date_of_birth |
| per_date_of_death |
| per_employee_of |
| per_member_of |
| per_origin |
| per_other_family |
| per_parents |
| per_religion |
| per_schools_attended |
| per_siblings |
| per_spouse |
| per_stateorprovince_of_birth |
| per_stateorprovince_of_death |
| per_stateorprovinces_of_residence |
| per_title |
 

## Options

| Option name | Type | Default | Description |
| --- | --- | --- | --- |
| kbp.model | file, classpath, or URL | edu/stanford/nlp/models/kbp/english/tac-re-lr.ser.gz | Relation extraction model to be used, set to "none" to use no statistical model |
| kbp.semgrex | file, classpath, or URL | edu/stanford/nlp/models/kbp/english/semgrex | Directory containing semgrex rules (rules over dependency patterns) to be used by relation extractor, set to "none" to use no semgrex rules |
| kbp.tokensregex | file, classpath, or URL | edu/stanford/nlp/models/kbp/english/tokensregex | Directory containing tokensregex rules (rules over token patterns) to be used, set to "none" to use no tokensregex rules |

* There are currently models for `Chinese, English, Spanish`.  The properties files for those languages have the proper settings for using the `KBPAnnotator` in that language

## More information 

There are descriptions of the sentence level statistical model, semgrex rules, and tokensregex rules in the write up for our [2016 TAC-KBP submission](https://nlp.stanford.edu/pubs/zhang2016stanford.pdf), though this paper also includes details about our overall KBP system which is not included in Stanford CoreNLP.


