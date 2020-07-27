---
layout: page
title: SUTime
keywords: sutime, time expressions, dates
permalink: '/sutime.html'
nav_order: 3
toc: false
parent: Additional Tools
---

StanfordCoreNLP includes [SUTime](https://nlp.stanford.edu/software/sutime.shtml), a library for processing temporal expressions
such as `February 4th, 2019.`  SUTime is built on top of [TokensRegex](https://stanfordnlp.github.io/CoreNLP/tokensregex.html).


## Other Resources

In addition to the info on this page, there are some other great resources to check out with very useful information:

* [Original slides](https://nlp.stanford.edu/software/SUTime.pptx)
* [Original TokensRegex site](https://nlp.stanford.edu/software/sutime.shtml)

## High Level Overview

SUTime will match a variety of temporal expressions and link them to a [TIMEX3](http://www.timeml.org/site/publications/timeMLdocs/timeml_1.2.1.html#timex3)
object.

4 types of temporal expression can be recognized.

| Temporal type | Example |
| :--- | :--- |
| DATE | February 4th, 2019 |
| TIME | 4:00pm |
| DURATION | 15 years |
| SET | Every 3rd Wednesday |

Phrases are recognized and linked based on a set of TokensRegex rules.

The default English rules are: 
```
edu/stanford/nlp/models/sutime/defs.sutime.txt,
edu/stanford/nlp/models/sutime/english.sutime.txt,
edu/stanford/nlp/models/sutime/english.holidays.sutime.txt
```

These files are available in the default models jar and can also be viewed on [GitHub](https://github.com/stanfordnlp/CoreNLP/tree/master/src/edu/stanford/nlp/time/rules).

SUTime is generally run as a subcomponent of the `ner` annotator.  After it has run, the tokens of a time phrase will have a `NamedEntityTagAnnotation` for the 
type (e.g. DATE, TIME, DURATION, SET), and will have a `edu.stanford.nlp.time.Timex` object stored in the `TimexAnnotation` field.

Recognized temporal expressions can be resolved relative to the document date.  For instance, the expression `this Wednesday` will be resolved to the 
Wednesday that is closest to the document date, be it the current date or any other date.  The document date can be set in several ways
as will be documented below.

If you would like to customize SUTime or make additions, you can alter the rules files accordingly or add new rules files.  Setting
the property `sutime.rules = /path/to/my-rules.txt` (or a comma-separated list of rules files) will set the pipeline to use your custom rules.


### Java API

```java
package edu.stanford.nlp.examples;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.time.*;

import java.util.Properties;

public class SUTimeBasicExample {

    public static String[] examples = {
            "The concert will be on February 4th at the ampitheater.",
            "The meeting will be held at 4:00pm in the library",
            "The conflict has lasted for over 15 years and shows no signs of abating."
    };


    public static void main(String[] args) {
        // set up pipeline properties
        Properties props = new Properties();
        // general properties
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
        props.setProperty("ner.docdate.usePresent", "true");
        props.setProperty("sutime.includeRange", "true");
        props.setProperty("sutime.markTimeRanges", "true");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        for (String example : examples) {
            CoreDocument document = new CoreDocument(example);
            pipeline.annotate(document);
            for (CoreEntityMention cem : document.entityMentions()) {
                System.out.println("temporal expression: "+cem.text());
                System.out.println("temporal value: " +
                                   cem.coreMap().get(TimeAnnotations.TimexAnnotation.class));
            }
        }
    }
}
```

### Running SUTime in a Stanford CoreNLP Pipeline

SUTime will be run automatically as a subcomponent of the `ner` annotator.
Several properties can be set to alter the behavior of SUTime.

| Property | Type | Description |
| :--- | :--- | :--- |
| sutime.rules | String | Comma separated list of rules to use |
| sutime.markTimeRanges | boolean | Whether or not to recognize time ranges such as "July to August" |
| sutime.includeNested | boolean | Whether to mark time expressions within time expressions as well (e.g., "July" in "July to August") |
| sutime.teRelHeurLevel | String | Heuristic setting for resolving time expressions (NONE, BASIC, MORE) |
| sutime.includeRange | boolean | Whether or not to add range info to the TIMEX3 object |
| ner.docdate.useFixedDate | String | Provide a fixed date for each document. |
| ner.docdate.useMappingFile | boolean | Use a tab-delimited file to specify doc dates. First column is document ID, second column is date. |
| ner.docdate.usePresent | boolean | Give every document the present date. |
| ner.docdate.useRegex | String | Specify a regular expression matching file names. The first group will be extracted as the date. |

The document date used by SUTime can also be set by specifying a date in an `xml` file and using the `cleanxml` annotator.

## Example 1: Fiscal Year

The English SUTime system in Stanford CoreNLP is specified in 3 rules files:

* `defs.sutime.txt` handles setting up fundamental definitions which will be used in the later files.
* `english.sutime.txt` contains the bulk of the English SUTime rules
* `english.holidays.sutime.txt` contains additional rules for handling holidays

In general, temporal concepts are set up in `defs.sutime.txt`, and the rules for matching phrases to temporal concepts 
are specified in `english.sutime.txt`.  We will follow this pattern when adding fiscal year rules.

In this example we will add rules to match phrases such as `Q3 FY 2018`.

The fiscal year is typically divided into 4 quarters.   Here is an example for `FY 2019`.

| Quarter | Time Period |
| :--- | :--- |
| Q1 | October - December 2018 |
| Q2 | January - March 2019 |
| Q3 | April - June 2019 |
| Q4 | July - September 2019 |

The first step is to specify these periods of time in `defs.sutime.txt`.  Here are the rules in that file
that define those blocks of time:

```
  // Financial Quarters
  FYQ1 = {
      type: QUARTER_OF_YEAR,
      label: "FYQ1",
      value: TimeWithRange(TimeRange(IsoDate(ANY,10,1), IsoDate(ANY,12,31), QUARTER))
  }
  FYQ2 = {
      type: QUARTER_OF_YEAR,
      label: "FYQ2",
      value: TimeWithRange(TimeRange(IsoDate(ANY,1,1), IsoDate(ANY,3,31), QUARTER))
  }
  FYQ3 = {
      type: QUARTER_OF_YEAR,
      label: "FYQ3",
      value: TimeWithRange(TimeRange(IsoDate(ANY,4,1), IsoDate(ANY,6,30), QUARTER))
  }
  FYQ4 = {
      type: QUARTER_OF_YEAR,
      label: "FYQ4",
      value: TimeWithRange(TimeRange(IsoDate(ANY,7,1), IsoDate(ANY,9,30), QUARTER))
  }
```

The value of time objects in this example is an object of type `TimeWithRange`.  These class definitions
can be found in `edu.stanford.nlp.time.SUTime`.  If you want to capture a particular concept of time,
the best advice is to look through `defs.sutime.txt` and find a comparable temporal concept to model after.
The financial quarters were modeled after the seasons for example.

After this rule has been specified in `defs.sutime.txt`, further in that rule file and in the subsequent
rule files `FYQ1` can be accessed to refer to that temporal concept.

The remainder of this example will focus on making additions to `english.sutime.txt`.  

First, a few maps and regexes need to be defined.

```
  # Finanical Quarters
  FISCAL_YEAR_QUARTER_MAP = {
    "Q1": FYQ1,
    "Q2": FYQ2,
    "Q3": FYQ3,
    "Q4": FYQ4
  }
  FISCAL_YEAR_QUARTER_YEAR_OFFSETS_MAP = {
    "Q1": 1,
    "Q2": 0,
    "Q3": 0,
    "Q4": 0
  }
  $FiscalYearQuarterTerm = CreateRegex(Keys(FISCAL_YEAR_QUARTER_MAP))
```

Here two maps are produced, which will map `String`'s to specific financial quarters, and to integer offsets.
When we specify the final rule, we need to potentially subtract 1 from the year if we are recognizing `Q1`.
Also, using the `CreateRegex` function, we can generate a regex that will match any of the keys in the
`FISCAL_YEAR_QUARTER_MAP`.

Now that we have these tools, we can write the final TokensRegex rule that will match the actual phrases
for financial quarters.

```
  {
    matchWithResults: TRUE,
    pattern: ((/$FiscalYearQuarterTerm/) (FY)? (/(FY)?([0-9]{4})/)),
    result:  TemporalCompose(INTERSECT, IsoDate(Subtract({type: "NUMBER", value: $$3.matchResults[0].word.group(2)}, FISCAL_YEAR_QUARTER_YEAR_OFFSETS_MAP[$1[0].word]), ANY, ANY), FISCAL_YEAR_QUARTER_MAP[$1[0].word])
  }

  {
    pattern: ((/$FiscalYearQuarterTerm/)),
    result: FISCAL_YEAR_QUARTER_MAP[$1[0].word]
  }
```

The TokensRegex rule specifies a pattern over tokens, and then specifies the temporal value to return.

As a reminder, TokensRegex rules specify regular expression which match tokens.  Each space separated
regular expression matches a single token or sequence of tokens.

It is helpful to look at the first rule.  In the `pattern` part of the rule, 3 potential tokens are specifed. 

The `(/$FiscalYearQuarterTerm/)` token would represent `Q1, Q2, Q3, Q4`.
The `(FY)?` represents an optional `FY` token.  This is so both `Q1 2019` can be recognized as well as `Q1 FY 2019`.
The `(/(FY)?([0-9]{4})/)` token captures the year component of phrase, so both `Q1 FY2019` can be recognized as well as `Q1 2019`.

The `result` part of the rule specifies what temporal object to create when this pattern over tokens is recognized.

Here are some explanations of components of this admittedly complex expression which represents the intersection of the 
fiscal quarter with the specified year.

```
TemporalCompose(INTERSECT, IsoDate(Subtract({type: "NUMBER", value: $$3.matchResults[0].word.group(2)}, FISCAL_YEAR_QUARTER_YEAR_OFFSETS_MAP[$1[0].word]), ANY, ANY), FISCAL_YEAR_QUARTER_MAP[$1[0].word])
```

* The high level structure of this expression is to intersect the specified year with the specified quarter, hence the `TemporalCompose(INTERSECT, ... , ...)`

* Keep in mind that there are two types of patterns to consider, patterns over tokens, and patterns that match the `String` contents of a given token.

* For example `(/Q*/) (/[0-9]{4}/)` will match 2 tokens, the first token must start with a `Q`, and the second must be any 4-digit number. 

* In our example, the token pattern has 3 capture groups, and the String pattern for matching the year token has 2 capture groups `(FY)?` and `([0-9]{4})`.
  This means `$$3.matchResults[0].word.group(2)` will refer to the 3rd capture group of the token pattern (the year token), and the 2nd group of the
  regex matching the year token's `String`, which is the numerical part of the `String`.  

* The first component of the intersection is the result of the `Subtract`, which simply subtracts the quarter offset from the numerical value
  of the year token.  So if `FY2019` is matched, the numerical component will be `2019`.  The `FISCAL_YEAR_QUARTER_YEAR_OFFSETS_MAP` is used
  to determine the offset.  If `Q1 FY2019` was matched, `Q1` would be mapped to `1`, otherwise `0`.  So either `1` or `0` will be subtracted
  from `2019`, giving the correct year.  Note the Subtract is wrapped in an `IsoDate`.  You can build a specific date with `IsoDate($Year, $Month, $Day)`.
  Passing `ANY` to `IsoDate` will create a date with nothing specified for that field.  So `IsoDate(2019, ANY, ANY)` means the year 2019.

* The second component of the intersection is the fiscal quarter object.  Recall that the `FISCAL_YEAR_QUARTER_MAP` maps `String`'s to fiscal quarters.
  So the result of `FISCAL_YEAR_QUARTER_MAP[$1[0].word]` is the mapping for the `Q*` token.

Here is a breakdown of the value of different components of the above expression if it matched the phrase `Q1 FY2019`.

| Sub Expression | Description |
| :--- | :--- |
| `$1` | capture group 1 of the token pattern, e.g. `(/$FiscalYearQuarterTerm/)` |
| `$1[0]` | first token of capture group 1 of the token pattern, e.g. the `"Q1"` token |
| `$1[0].word` | string value of the first token of capture group 1 of the token pattern, e.g. the `String` `"Q1"` |
| `FISCAL_YEAR_QUARTER_MAP[$1[0].word]` | mapping of `"Q1"` token to `FYQ1` (defined in `defs.sutime.txt`) |
| `$$3.matchResults[0].word.group(2)` | token pattern capture group 3, string pattern capture group 2, e.g. `2019` |
| `$$3.matchResults[0].word.group(1)` | token pattern capture group 3, string pattern capture group 1, e.g. `FY` |
| `FISCAL_YEAR_QUARTER_YEAR_OFFSETS_MAP[$1[0].word]` | map `"Q1"` to `1` |
| `Subtract(..., ...)` | `2019 - 1 = 2018` |
| `IsoDate(..., ..., ...)` | an `IsoDate` built with `(2018, ANY, ANY)` |
| `TemporalCompose(INTERSECT, IsoDate(...), ...)` | intersection of `2018` with `FYQ1` |

The final temporal object is built and associated with the phrase.

## Example 2: Mapping Phrases To Southern Hemisphere Seasons

The word `winter` means different periods of time in the USA and in Australia.

If you wanted to customize the time period the season referred to, the best way to do this is 
to alter the seasonal definitions in `defs.sutime.txt`.

```
  // Dates are rough with respect to northern hemisphere (actual
  // solstice/equinox days depend on the year)
  SPRING_EQUINOX = {
    type: DAY_OF_YEAR,
	value: InexactTime( TimeRange( IsoDate(ANY, 3, 20), IsoDate(ANY, 3, 21) ) )
  }
  SUMMER_SOLSTICE = {
    type: DAY_OF_YEAR,
	value: InexactTime( TimeRange( IsoDate(ANY, 6, 20), IsoDate(ANY, 6, 21) ) )
  }
  FALL_EQUINOX = {
    type: DAY_OF_YEAR,
	value: InexactTime( TimeRange( IsoDate(ANY, 9, 22), IsoDate(ANY, 9, 23) ) )
  }
  WINTER_SOLSTICE = {
    type: DAY_OF_YEAR,
	value: InexactTime( TimeRange( IsoDate(ANY, 12, 21), IsoDate(ANY, 12, 22) ) )
  }

  ...
  
  // Dates for seasons are rough with respect to northern hemisphere
  SPRING = {
      type: SEASON_OF_YEAR,
      label: "SP",
      value: InexactTime( SPRING_EQUINOX, QUARTER, TimeRange( MARCH, JUNE, QUARTER ) ) }
  SUMMER = {
      type: SEASON_OF_YEAR,
      label: "SU",
      value: InexactTime( SUMMER_SOLSTICE, QUARTER, TimeRange( JUNE, SEPTEMBER, QUARTER ) )
  }
  FALL = {
      type: SEASON_OF_YEAR,
      label: "FA",
      value: InexactTime( FALL_EQUINOX, QUARTER, TimeRange( SEPTEMBER, DECEMBER, QUARTER ) )
  }
  WINTER = {
      type: SEASON_OF_YEAR,
      label: "WI",
      value: InexactTime( WINTER_SOLSTICE, QUARTER, TimeRange( DECEMBER, MARCH, QUARTER ) )
  }
```

For instance, these would be the updated rules in `defs.sutime.txt`

```
// Dates are rough with respect to northern hemisphere (actual
// solstice/equinox days depend on the year)
SPRING_EQUINOX = {
   type: DAY_OF_YEAR,
   value: InexactTime( TimeRange( IsoDate(ANY, 9, 22), IsoDate(ANY, 9, 23) ) )
}
SUMMER_SOLSTICE = {
   type: DAY_OF_YEAR,
   value: InexactTime( TimeRange( IsoDate(ANY, 12, 21), IsoDate(ANY, 12, 22) ) )
}
FALL_EQUINOX = {
   type: DAY_OF_YEAR,
   value: InexactTime( TimeRange( IsoDate(ANY, 3, 20), IsoDate(ANY, 3, 21) ) )
}
WINTER_SOLSTICE = {
   type: DAY_OF_YEAR,
   value: InexactTime( TimeRange( IsoDate(ANY, 6, 20), IsoDate(ANY, 6, 21) ) )
}

// Dates for seasons are rough with respect to northern hemisphere
SPRING = {
    type: SEASON_OF_YEAR,
    label: "SP",
    value: InexactTime( SPRING_EQUINOX, QUARTER, TimeRange( SEPTEMBER, DECEMBER, QUARTER ) ) }
SUMMER = {
    type: SEASON_OF_YEAR,
    label: "SU",
    value: InexactTime( SUMMER_SOLSTICE, QUARTER, TimeRange( DECEMBER, MARCH, QUARTER ) )
}
FALL = {
    type: SEASON_OF_YEAR,
    label: "FA",
    value: InexactTime( FALL_EQUINOX, QUARTER, TimeRange( MARCH, JUNE, QUARTER ) )
}
WINTER = {
    type: SEASON_OF_YEAR,
    label: "WI",
    value: InexactTime( WINTER_SOLSTICE, QUARTER, TimeRange( JUNE, SEPTEMBER, QUARTER ) )
}
```

If you have created a custom rules file, you can tell SUTime to use it by setting the `sutime.rules` property
when running a pipeline.  This property takes in a list of rules files and reads them in order.
