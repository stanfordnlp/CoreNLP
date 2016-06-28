/**
 * A package for supervised relation and event extraction.
 *
 * <h2>Usage</h2>
 * The easiest way to run Machine Reading is using the following command
 * from your {@code javanlp} directory.
 * <p><code>
 * bin/javanlp.sh edu.stanford.nlp.ie.machinereading.MachineReading --arguments <i>machinereading.properties</i>
 * </code></p>
 * Sample properties files are included in
 * {@code projects/core/src/edu/stanford/nlp/ie/machinereading}
 * . Eventually, we will have one for each corpus. The attributes for the
 * properties file are explained below:
 * <h3>{@code MachineReading} Properties</h3>
 * <h4>Required Properties</h4>
 * <ol>
 * 	<li>{@code datasetReaderClass}: which {@code GenericDataSetReader} to use (needs to match
 * 	the corpus in question). For example: {@code edu.stanford.nlp.ie.machinereading.reader.AceReader}</li>
 * 	<li>{@code serializedModelPath}: where to store/load the
 * 	serialized extraction model</li>
 * 	<li>{@code trainPath}: path to the training file/directory
 * 	(needs to match the {@code datasetReaderClass})</li>
 * 	<li>{@code serializedTrainingSentencesPath}: where to store
 * 	the serialized training sentences objects (To save time loading the
 * 	training data, the objects produced when reading them in are
 * 	serialized.)</li>
 * </ol>
 * <h4>Optional Properties:</h4>
 * The following properties are optional because the code assumes default
 * values, which it prints out if not defined.
 * <ol>
 * 	<li>{@code forceRetraining}: retrains an extraction model
 * 	even if it already exists (otherwise, we only train if the {@code serializedModelPath}
 * 	doesn't exist on disk, default is {@code false}).</li>
 * 	<li>{@code trainOnly}: if true, don't run evaluation (implies
 * 	{@code forceRetraining}, default is {@code false})</li>
 * 	<li>The {@code testPath} and {@code serializedTestSentencesPath}
 * 	properties can be omitted if {@code trainOnly} is {@code true}.
 * 	Otherwise, these are analogous to their train counterparts.</li>
 * 	<li>{@code extractRelations}: whether we should extract relations <b>(currently ignored)</b></li>
 * 	<li>{@code extractEvents}: whether we should extract events <b>(currently ignored)</b></li>
 * </ol>
 */
package edu.stanford.nlp.ie.machinereading;
