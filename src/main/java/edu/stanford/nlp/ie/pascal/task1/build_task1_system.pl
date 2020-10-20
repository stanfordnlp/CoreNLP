#######################################################
# build_task1_system.pl
#
# Builds a distribution of the task1 pascal system.
#
# See usage() for details on usage.

use strict;

########################################################
# setup variables

# add the javanlp tree to our Perl include path
my $JAVANLP_ROOT;
BEGIN {
  $JAVANLP_ROOT = "../../../../../../..";
  push(@INC, "$JAVANLP_ROOT/src");
}

# import module for generating dependencies
use edu::stanford::nlp::misc::GenDependencies qw(
  extractDependencies
  generateClosure
  generateJARfile
  classFilesToSourceFiles
  printAndDo
);

# list of classes that form the kernel of the class dependency graph.
my @classlist = qw(
  edu.stanford.nlp.ie.pascal.PhaseOneFeatureGrabber
  edu.stanford.nlp.ie.pascal.ClassifierWrapper
  edu.stanford.nlp.ie.pascal.MainDateProcessor
);

# construct timestamp
my ($sec, $min, $hour, $mday, $mon, $year) = localtime();
$year += 1900;
$mon++;
my $timestamp = sprintf("%04d%02d%02d%02d%02d", $year, $mon, $mday, $hour,
  $min);

# construct names of files and directories
my $distdir = "pascal_task1_$timestamp";
my $classdir = "$JAVANLP_ROOT/classes";
my $srcdir = "$JAVANLP_ROOT/src";
my $depdump = "$JAVANLP_ROOT/depdump";
my $classjar = "$distdir/pascal.jar";
my $srcjar = "$distdir/pascal_src.jar";
my $script_template= "task1_test.pl";
my $script = "$distdir/task1_test.pl";
my $tarfile = "$distdir.tgz";

# flags are command-line arguments that don't take an argument
# (i.e., they're boolean)
my %flags = map { ($_,1) } qw( cmm );

# Read command-line arguments into the %args hash.
my %args;
for(my $a = 0; $a < @ARGV; ++$a) {
  if( $ARGV[$a] =~ /^-(.*)$/ ) {
    my $key = lc($1);
    if( $flags{$key} ) {
      $args{$key} = 1;
    } else {
      $args{$key} = $ARGV[++$a];
    }
  }
}
my $serializedClassifier = $args{serializedclassifier};
my $propFile = $args{propfile};
my $cmm = $args{cmm};

# Warn the user if they try to package a CMMClassifier and don't specify
# the -cmm flag.
if( ($serializedClassifier =~ /cmm/i) && !$cmm ) {
  print "\n!!!!! WARNING: $serializedClassifier\n" .
    "!!!!! looks like it's a CMMClassifier.\n".
    "!!!!! Should you have specified the -cmm flag?\n\n";
}

# make sure the input files exist
unless( -e $serializedClassifier ) {
  print "serialized classifier \"$serializedClassifier\" does not exist\n";
  usage();
}
unless( -e $propFile ) {
  print "properties file \"$propFile\" does not exist\n";
  usage();
}

# build dependency dump
printAndDo("cd $JAVANLP_ROOT; make depdump");

# make the output directory
mkdir("pascal_task1_$timestamp") or die;

# generate the dependency closure
my $classfilelist = generateClosure($depdump, \@classlist);

# generate the class JAR file
if( -e $classjar ) {
  unlink $classjar or die;
}
generateJARfile($classfilelist, $classjar, $classdir);

# generate the source JAR file
if( -e $srcjar ) {
  unlink $srcjar or die;
}
my $sourceFileList = classFilesToSourceFiles($classfilelist, $srcdir);
generateJARfile($sourceFileList, $srcjar, $srcdir);

# copy in the serialized classifier
printAndDo("cp $serializedClassifier $distdir/serialized_classifier.gz");

# Read the properties, strip out the ones that tell the classifier to take some
# action.
open PROPS, "<$propFile" or die;
my @props = <PROPS>;
push(@props, "\n"); # in case the properties file didn't end in a newline
close PROPS;
@props = grep { !containsActionProp($_) } @props;
unless( $cmm ) {
  # add the useCRF=true flag for CRFClassifiers
  push(@props, "useCRF=true\n");
}

# process the script template and write out the real script
open SCRIPT_TEMPLATE, "<$script_template" or die;
open SCRIPT, ">$script" or die;
my $line;
while ( $line = <SCRIPT_TEMPLATE> ) {
  if( $line =~ /^\s*<INSERT PROPERTIES HERE>\s*$/ ) {
    print SCRIPT @props;
  } else {
    print SCRIPT $line;
  }
}
close SCRIPT_TEMPLATE;
close SCRIPT;

# copy in stripanswers.pl
printAndDo("cp stripanswers.pl $distdir");

# construct the tarfile
printAndDo("tar czf $tarfile $distdir");

print "Successfully created task1 distribution $tarfile.\n";


#######################################################
# END OF MAIN

# Does the given line of a properties file specify a property that
# causes the classifier to take some action?
sub containsActionProp {
  my @actionProps = qw( trainFile loadClassifier testFile serializeto );
  my $line = shift;
  foreach my $prop (@actionProps) {
    if( $line =~ /^\s*$prop/i ) {
      return 1;
    }
  }
  return 0;
}

sub usage
{

  print <<END;

Usage: perl build_task1_system.pl -serializedClassifier <serialized classifier>
        -propFile <properties file> [-cmm]

  Creates a subdirectory called pascal_task1_<timestamp> and copies
  all necessary files into it. Tars and gzips this directory into a file
  called pascal_task1_<timestamp>.tgz.

  -serializedClassifier F     Specifies the path to the serialized classifier
                              to be included with this distribution. If it's a
                              CMMClassifier, be sure to specify the -cmm flag.

  -propFile F                 The properties file used to train the serialized
                              classifier. It will be copied into the
                              distribution, with any properties that invoke
                              an action (serializeTo, trainFile, testFile,
                              loadClassifier) stripped out.

  -cmm                        If specified, the serializedClassifier is a
                              CMMClassifier. Otherwise it is assumed to be
                              a CRFClassifier, and the property "useCRF=true"
                              will be added to the properties file.

END

  die;
}
