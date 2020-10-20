Adding a regression test
========================

A regression test runs an entire system from start to finish (training and test),
generates a score, and compares against a hard-coded baseline. This is to ensure
that code changes don't adversely affect final system performance.

Each file in this directory (besides this one) that starts with the prefix
"test-" will be treated as a {shell script / perl script / something else
executable} regression test. To add a regression test, simply add such a file
to this directory.

The script:
 - SHOULD run an entire training and test cycle on some fixed data, generate a
   score, and compare that score against a fixed baseline (probably hard-coded
   in the script itself.)
 - MAY assume a correctly set up JavaNLP environment, including access to files
   on NFS.
 - SHOULD NOT take more than half an hour or so to run on a reasonable machine.
   (So downsample your data until it fits in that limit!)

The final line of standard output of the script should either:

 - start with "PASS", in which case the test is assumed to pass; OR
 - start with "FAIL" followed by a one-line description of the error,
   (e.g. "baseline F-measure 0.82 but only acheived 0.75")

Any other output will be considered an error. Successes, failures and errors
will be reported to java-nlp-list at some regular interval.

Don't forget to check in all your files!

-- wtm 10/17/2006
