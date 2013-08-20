Stanford RTE System - 2008
Copyright (c) 2005-2008 The Board of Trustees of
The Leland Stanford Junior University. All Rights Reserved.


Original code produced by the Stanford NLP Group.
This distribution produced by Sebastian Pado.
Please write to java-nlp-support@cs.stanford.edu if you have
questions about this distribution.

------------------------------------------------------------

This software package contains basically the Stanford entry to the
NIST Metrics MATR challenge for the single-reference track (with some
minimal debugging).

It is based on the Stanford RTE system, which performs inference
between two short texts, determining if one is entailed by the other.
We use this inference mechanism to predict the adequacy of MT system
output at the segment level compared to a reference translation.

NOTE: The system produces two runs. The primary run predicts adequacy
from a combination of entailment features and "classical" MT
evaluation scores. This is the system referred to as "RTE+MT" in the
technical report [1]. The secondary run uses entailment features
only. This is the system referred to as "RTE" in the technical
report. The results of "MT" (MT scores only regression model) are
currently not part of the system output.

The system is intended to be used via the "runSystem" script. Other 
forms of use are possible, but would require further integration work. 
See below for further details.

This distribution supports Mac OS X or Linux. It requires at least
Java 1.6. The machine it runs on should have at least 8 GB of memory.

The supplied code is almost all in Java. In addition to Stanford code,
there is use of various libraries: (Apache) commons-logging.jar,
jgrapht.jar, (Dan Ramage's) ra.jar, fastutil.jar, jdom.jar, jwnl.jar
(jwnlx.jar is our patched version of that library), (GNU) trove.jar,
jgraph.jar, opennlpx.jar (our combination of the OpenNLP project
maxent and tools packages), (Dan Bikel's) wn.jar, and scala-library.jar
of the scala programming language. These all have some form of open 
source license, but the user should check the details as appropriate.  
There is one piece of native code to calculate Infomap word association 
scores (see http://infomap-nlp.sourceforge.net/ ). If it does not 
load, this lexical resources will simply be omitted.


The runSystem script
--------------------

All interaction with the system should happen through the runSystem
script. We assume that the input corresponds to the specification in
the NIST MetricsMATR08 evaluation plan at
http://www.nist.gov/speech/tests/metricsmatr/2008/doc/mm08_evalplan_v1.1.pdf

Since there were some minor details in which the NIST MetricsMATR08
development data differed from the specifications in the evaluation
plan, we specifically follow the evaluation plan in making the
following assumptions about the input data:

- We assume that there is one reference file, whose filename
  corresponds to its sysid attribute (e.g., reference02.[xml|sgm] for 
  sysid="reference02").

- We assume that there is a directory with translation files (system
  outputs), whose filenames corrrespond to their
  sysid attributes (e.g., system05.[xml|sgm] for sysid="system05").

- We assume that unlike in the development set, the reference file
  and all translation files are valid XML and conform to the DTD 
  referenced in the evaluation plan.

- We assume that unlike in the development set, all corresponding
  system outputs and the reference have the same setid.


Usage 
-----

runSystem has four parameters.

./runSystem.sh -refFile ../references/reference01.xml 
               -sysDir ../ttsystems/
	           -outDir ../out/ 
	           -tempDir ../tmp/

refFile: the reference file
sysDir: a directory with translation files (all XML files in this 
        directory are assumed to be translation files and are attempted
        to be parsed as such)
outDir: output directory for the metrics 
	    - the primary metric (RTE+MT features) will write three files called stanford_primary.*
	    - the secondary metric (RTE features only) will write three files called stanford_secondary.*

tempDir: directory into which the system can write temporary files



Notes: 

 - The system should be able to handle relative paths

 - The system attempts to give instructive error message if things go wrong.

 - Some of the temporary files can become large. If /tmp is used 
   as tempDir, ensure that there is sufficient space (on some systems,
   /tmp is limited to ~1G which can be too little for large datasets)

 - The files that the RTE system produces in the tempDir are as follows:
   - "pascal files": a representation of the reference translation/MT
     output segment pairs
   - "info files": pairs with linguistic analysis
   - "output files": feature representation for input files 

 - The system does internal caching of lexical similarity scores. This
   means that 
   (a) repeated system runs on the same or similar data can see a 
       considerable speedup
   (b) The size of the distribution (specifically, the size of 
       the direcetory rteResources/resources/lex/CacheDatabase.db)
       is likely to slowly grow over time


References
----------
[1] S. Pado, M. Galley, D. Jurafsky, and C. Manning. 2008. Evaluation MT output
 with entailment technology. NIST MetricsMATR'08 workshop system
 description. Available at http://nlp.stanford.edu/pubs/metricsmatr08.pdf.
