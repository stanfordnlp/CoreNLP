#!/usr/pubsw/bin/perl

package edu::stanford::nlp::misc::GenDependencies;
require Exporter;

our @ISA = qw(Exporter);
our @EXPORT_OK = qw( extractDependencies classFilesToSourceFiles
                     generateClosure generateJARfile printAndDo );
our $VERSION = 1.00;

use strict;
use Cwd ('getcwd', 'realpath');
use File::Basename;

my $nlphome = $ENV{"NLP_HOME"} || "/u/nlp";

my $depExtractClasspath = "$nlphome/java/liball/DependencyFinder.jar:$nlphome/java/lib/jakarta-oro.jar:$nlphome/java/liball/log4j.jar:$nlphome/java/liball/xalan.jar:$nlphome/java/liball/xercesImpl.jar:$nlphome/java/liball/xml-apis.jar";

# Invokes DependencyFinder to extract all the dependencies in the class
# hierarchy. This can take a while.
sub extractDependencies {
    my ($depdump, $classdir) = @_;

    # tell the user what we're about to do
    print "\n#####\nEXTRACTING DEPENDENCIES\n#####\n";
    print "\nDependency dump file:\n\t$depdump\n";
    print "Class directory:\n\t$classdir\n\n";

    # invoke DependencyExtractor
    printAndDo("java -classpath $depExtractClasspath com.jeantessier.dependencyfinder.cli.DependencyExtractor -maximize -out $depdump $classdir");
}

# Invokes DependencyAnalyzer to generate the transitive dependency closure
# for $classlist.
sub generateClosure {
    my ($depdump, $classlist) = @_;

    # tell the user what we're about to do
    print "\n#####\nGENERATING CLOSURE\n#####\n";
    print "Dependency dump file:\n\t$depdump\n\n";
    print "Starting classes:\n\t" . join("\n\t", @$classlist) . "\n\n";

    # invoke DependencyAnalyzer
    my $cmd = "java edu.stanford.nlp.misc.DependencyAnalyzer $depdump "
        . join(" ", @$classlist);
    print $cmd . "\n";
    my $output = `$cmd`;
    my $rv = $?>>8;
    if( $rv ) {
        die "ERROR: DependencyAnalyzer failed with return value $rv\n";
    }

    # parse the output into an array
    my @classfilelist = split(/\s+/, $output);

    # print out all the class files in the dependency closure
    print "\nClass list:\n";
    foreach my $class (@classfilelist) {
        print "\t$class\n";
    }
    print "\n";

    # return list of class files
    \@classfilelist;
}

sub classFilesToSourceFiles {

    my ($classfilelist, $srcdir) = @_;
    my %sourceFiles;

    foreach my $classfile (@$classfilelist) {
        # strip off .class suffix and any inner classes
        $classfile =~ s/\.class//;
        $classfile =~ s/\$[^\/]+//;

        # append ".java"
        $classfile = $classfile . ".java";

        # see if the file exists
        if( -f "$srcdir/$classfile" ) {
            $sourceFiles{$classfile} = 1;
        }
    }
    return [ sort keys %sourceFiles ];
}

# Changes to directory $dir and generates the JAR file $jarfile containing all
# the files in $classfilelist.
sub generateJARfile {
    my ($filelist, $jarfile, $dir) = @_;

    # tell the user what we're about to do
    print "\n#####\nGENERATING JAR FILE\n#####\n\n";
    print "JAR file:\n\t$jarfile\n";
    print "Root directory:\n\t$dir\n\n";

    # change to class directory before running the jar command.
    my $startdir = getcwd();
    $jarfile = realpath(dirname($jarfile)) . "/" . basename($jarfile);
    chdir($dir) or die "Unable to change to $dir\n";

    # escape dollar signs in file list
    $filelist = [ map { $_ =~ s/\$/\\\$/g; $_ } @$filelist ];

    # perform jar operation
    printAndDo("jar cf $jarfile " . join(" ", @$filelist) );

    # change back to starting directory
    chdir($startdir);
}

sub printAndDo {
    my $cmd = shift;
    print "$cmd\n";
    system($cmd);
    my $rv = $?>>8;
    if( $rv ) {
        my $shortCmd = shortCmd($cmd);
        die "ERROR: \"$shortCmd\" failed with return value $rv\n";
    }
}

sub shortCmd {
    my $long = shift;
    my @words = split(/\s+/, $long);
    if( @words > 3 ) {
        @words = (@words[0..2], "...");
    }
    return join(" ", @words);
}

# end of module
1;
