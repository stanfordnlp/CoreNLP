#!/usr/bin/env python


import os
import re
import time
import random

dataset = "RTE2_dev"
score = 0.0
minScore = 0.60

memory = "-ms2g -mx7g"


# Set up file paths -------------------------------------------------------------

dataDir = "/u/nlp/rte/data/byformat"
tmpDir = "/tmp/rte-regression.%d" % os.getpid()
os.mkdir(tmpDir)
rteFile = "%s/rte/%s.xml" % (dataDir, dataset)
kbeFile = "%s/%s.kbe.xml" % (tmpDir, dataset)
pipelineFile = "%s/%s.pipeline.info.xml" % (tmpDir, dataset)
alignFile = "%s/%s.align.xml" % (tmpDir, dataset)
logFile = "%s/%s.log" % (tmpDir, dataset)
regressionFile = "%s/regression/%s.regression.log" % (dataDir, dataset)


# Make KBE file from RTE file ---------------------------------------------------

def makeKBEFile():
  javaclass = "edu.stanford.nlp.util.XMLTransformer"
  xsltransformer = "/u/nlp/rte/data/resources/RTE_to_KBEval.xsl"
  cmd = "java -server %s %s " % (memory, javaclass) + \
        "-in %s " % rteFile + \
        "-out %s " % kbeFile + \
        "-transform %s " % xsltransformer + \
        "> %s 2>&1 " % logFile
  # print "cmd is:\n", cmd
  os.system(cmd)


# Annotation --------------------------------------------------------------------

def doAnnotation():
  javaclass = "edu.stanford.nlp.rte.RTEPipeline"
  cmd = "java -server %s %s " % (memory, javaclass) + \
        "-kbeIn %s " % kbeFile + \
        "-infoOut %s " % pipelineFile + \
        "> %s 2>&1 " % logFile
  # print "cmd is:\n", cmd
  os.system(cmd)


# Alignment & inference ---------------------------------------------------------
    
def doAlignmentAndInference():
  aligner = "stochastic"
  javaclass = "edu.stanford.nlp.rte.KBETester"
  cmd = "java -server %s %s " % (memory, javaclass) + \
        "-info %s " % pipelineFile + \
        "-saveAlignments %s " % alignFile + \
        "-aligner %s " % aligner + \
        "-twoClass " + \
        "-balancedData " + \
        "-verbose 1 " + \
        "> %s 2>&1 " % logFile
  # print "cmd is:\n", cmd
  os.system(cmd)


# Extract score -----------------------------------------------------------------

def extractScore():
  for line in os.popen("grep '^Accuracy:' %s" % logFile):
    line = line.strip()
    # print line
    fields = re.split('\s+', line)
    score = float(fields[-1])
  return score


# Get previous score ------------------------------------------------------------

def getPreviousScore():
  prev = 0.0
  for line in os.popen("grep '^PASS' %s" % regressionFile):
    line = line.strip()
    # print line
    fields = re.split('\s+', line)
    prev = float(fields[1])
  return prev


# Save score --------------------------------------------------------------------

def saveScore(score, minScore, logFile):
  if score >= minScore:
    result = "PASS"
  else:
    result = "FAIL"
  f = open(regressionFile, "a")
  print >>f, \
        "%s  %.4f  %.4f  %s  %s" % \
        (result,
         score,
         minScore,
         time.strftime("%Y%m%d-%H%M%S"),
         logFile)
  f.close()
  

# main --------------------------------------------------------------------------

makeKBEFile()
doAnnotation()
doAlignmentAndInference()
# score = random.random()
score = extractScore()
minScore = max(minScore, getPreviousScore())

if score >= minScore:
  print "PASS score %.4f >= min %.4f" % (score, minScore)
else:
  print "FAIL score %.4f >= min %.4f, output in %s" % (score, minScore, logFile)

saveScore(score, minScore, logFile)




