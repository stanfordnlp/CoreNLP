---
title: CoreNLP Server 
keywords: server
permalink: '/corenlp-server.html'
---


This page describes setting up the CoreNLP server included in the release. This provides both a convenient graphical way to interface with your version of CoreNLP, and provides an API to call CoreNLP using any programming languages.

## Getting Started
Stanford CoreNLP ships with a built-in server, and requires only the CoreNLP dependencies. To run this server, simply run:

```bash
# Set up your classpath. For example:
export CLASSPATH="lib/protobuf.jar:lib/joda-time.jar:lib/jollyday.jar:lib/xom-1.2.10.jar:classes/"

# Run the server
java -mx4g edu.stanford.nlp.pipeline.StanfordCoreNLPServer [port?]
```

If no value for `port` is provided, port 9000 will be used by default. You can then test your server by visiting

    http://localhost:9000/

You should see a website similar to [corenlp.run](http://corenlp.run/), with an input box for text and a list of annotators you can run. From this interface, you can test out each of the annotators by adding/removing them from this list. You can test out the API by sending a `POST` request to the server with the appropriate properties. An easy way to do this is with [wget](https://www.gnu.org/software/wget/). The following will annotate the sentence "*the quick brown fox jumped over the lazy dog*" with part of speech tags:

```bash
wget --post-data 'the quick brown fox jumped over the lazy dog' 'localhost:9000/?properties={"tokenize.whitespace": "true", "annotators": "tokenize,ssplit,pos", "outputFormat": "json"}' -O -
```

The rest of this document describes the API in more detail, describes a Java client to the API as a drop-in replacement for the `StanfordCoreNLP` annotator pipeline, and talks about administering the server.


## API Documentation
The greatest strength of the server is the ability to make API calls against it. 

> **NOTE**: Please do **not** make API calls against [corenlp.run](http://corenlp.run). It is not set up to handle a large volume of requests. Instructions for setting up your own server can be found in the [Dedicated Server](#DedicatedServer) section.

There are three endpoints provided by the server, which we'll describe in more detail below. Each of them takes as input a series of `GET` parameters, as well as `POST` data consisting of the serialized document or raw text to be annotated. The endpoints are:

*  `/` Provides an interface to annotate documents with CoreNLP.
* `/tokensregex` Provides an interface for querying text for TokensRegex patterns, once it has been annotated with CoreNLP (using the enpoint above).
* `/semgrex` Similar to `/tokensregex` above, this endpoint matches text against semgrex patterns.

### Annotate with CoreNLP: `/`
This endpoint takes as input a JSON-formatted properties string under the key `properties=<properties>`, and as `POST`data text to annotate. The properties should mirror the properties file passed into the CoreNLP command line. For example, the following will tokenize the input text on whitespace, run part of speech tagging, and output it as JSON to standard out:

```bash
wget --post-data 'the quick brown fox jumped over the lazy dog' 'localhost:9000/?properties={"tokenize.whitespace": "true", "annotators": "tokenize,ssplit,pos", "outputFormat": "json"}' -O -
```

A common property to set is the output format of the API. The server supports all output formats provided by CoreNLP. These are listed below, along with their relevant properties:

* **JSON**: Print the annotations in JSON format. This corresponds to the properties: `{"outputFormat": "json"}`.
* **XML**: Print the annotations in XML format. This corresponds to the properties: `{"outputFormat": "xml"}`.
* **Text**: Print the annotations in a human-readable text format. This is the default format for the CoreNLP command-line interface. This corresponds to the property: `{"outputFormat": "text"}`.  
* **Serialized**: Print the annotations in a losslessly serialized format. This is the recommended option when calling the API programmatically from a language that supports one of the serialized formats. In addition to setting the output format flag, you must also provide a valid serializer class. For example, for protocol buffers, this would be: 
    ```{"outputFormat": "serialized", 
        "serializer": "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer"}
    ```
    The serializers currently supported are:
    - `edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer` Writes the output to a protocol buffer, as defined in the definition file `edu.stanford.nlp.pipeline.CoreNLP.proto`.
    -  `edu.stanford.nlp.pipeline.GenericAnnotationSerializer` Writes the output to a Java serialized object. This is only suitable for transferring data between Java programs. This also produces relatively large serialized objects.
    - `edu.stanford.nlp.pipeline.CustomAnnotationSerializer` Writes the output to a (lossy!) textual representation, which is much smaller than the `GenericAnnotationSerializer` but does not include all the relevant information.

From the other side, the server accepts input in a variety of formats. By default, it takes input as raw text sent as `POST` data to the server. However, it can also be configured to read the `POST` data using one of the CoreNLP serializers. This can be set up by setting the properties `inputFormat` and `inputSerializer`. For example, to read the data as a protocol buffer (useful if, e.g., it is already partially annotated), simply include the following in your `GET` parameters:

```json
{"inputFormat": "serialized",
 "inputSerializer": "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer"}
```

A complete call to the server, taking as input a protobuf serialized document at path `/path/to/file.proto` and returning as a response the document annotated for part of speech and named entity tags (to the file `/path/to/annotated_file.proto` could be:

```bash
wget --post-file /path/to/file.proto 'localhost:9000/?properties={"inputFormat": "serialized", "inputSerializer", "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer", "annotators": "tokenize,ssplit,pos,lemma,ner", "outputFormat": "serialized", "serializer", "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer"}' -O /path/to/annotated_file.proto
```

### Query TokensRegex: `/tokensregex`
Similar to the CoreNLP target, `/tokensregex` takes a block of data (e.g., text) as `POST` data, and a series of `GET` parameters. Currently, only plain-text `POST` data is supported. The two relevant `GET` parameters are:

* `pattern`: The TokensRegex pattern to annotate.
* `filter`: If true, entire sentences must match the pattern, rather than the API finding matching sections.

The response is always in JSON, formatted as follows:

```json
{"sentences": {
	"0": {
	  "text": "the matched text",
	  "begin": 2,
	  "end": 5,
	  "$captureGroupKey": {
		  "text": "the matched text",
		  "begin": 2,
		  "end": 5,
            }
        }
    }
}
```

### Query Semgrex: `/semgrex`
Similar to the CoreNLP target, and nearly identical to TokensRegex, `/semgrex` takes a block of data (e.g., text) as `POST` data, and a series of `GET` parameters. Currently, only plain-text `POST` data is supported. The two relevant `GET` parameters are:

* `pattern`: The Semgrex pattern to annotate.
* `filter`: If true, entire sentences must match the pattern, rather than the API finding matching sections.

The response is always in JSON, formatted identically to the tokensregex output, with the exception that all spans are single words (only the root of the match is returned):

```json
{"sentences": {
	"0": {
	  "text": "text",
	  "begin": 4,
	  "end": 5,
	  "$captureGroupKey": {
		  "text": "text",
		  "begin": 4,
		  "end": 5,
            }
        }
    }
}
```


## Java Client
CoreNLP includes a Java client to the server -- `StanfordCoreNLPClient` -- which mirrors the interface of the annotation pipeline (`StanfordCoreNLP.java`) as closely as possible. The primary motivating use-cases for using this class and not the local pipeline are:

* The models are not re-loaded every time your program runs. This is useful when debugging a block of code which runs CoreNLP annotations, as the CoreNLP models often take on the order of minutes to load from disk.
* The machine running the server often has more compute and more memory than your local machine. Never again must gmail and CoreNLP compete for the same memory.

The constructors to `StanfordCoreNLPClient` take the following 3 required arguments, and a fourth optional argument:

1. `Properties props`: Mirroring the local pipeline exactly, these are the properties to use when annotating text with the pipeline. Minimally, this specifies the annotators to run.
2. `String host`: The hostname of the server.
3. `int port`: The port that the server is running on.
4. `int threads`: Optionally, the number of threads to hit the server with. If, for example, the server is running on an 8 core machine, you can specify this to be 8, and the client will allow you to make 8 simultaneous requests to the server. Note that there is nothing that ensures that you have these threads reserved on the server: two clients can both hit the server with 8 threads, and the server will just respond half as fast.

An example programmatic usage of the client, hitting a server at localhost:9000 with up to 2 threads, could be as follows. Note that this exactly mirrors the usage of the conventional pipeline.

```java
// creates a StanfordCoreNLP object, with POS tagging, lemmatization, NER, parsing, and coreference resolution 
Properties props = new Properties();
props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
StanfordCoreNLPClient pipeline = new StanfordCoreNLPClient(props, "localhost", 9000, 2);

// read some text in the text variable
String text = ... // Add your text here!

// create an empty Annotation just with the given text
Annotation document = new Annotation(text);

// run all Annotators on this text
pipeline.annotate(document);
```

You can also run the client from the command line, and get an interface similar to the command line usage for the local CoreNLP program. The following will annotate a file `input.txt` with part-of-speech, lemmas, named entities, constituency parses, and coreference:

```bash
java -cp "*" -Xmx2g edu.stanford.nlp.pipeline.StanfordCoreNLPClient -annotators tokenize,ssplit,pos,lemma,ner,parse,dcoref -file input.txt
```

> **NOTE**: Again, please do **not** make API calls against `http://corenlp.run`. It is not set up to handle a large volume of requests. Instructions for setting up your own server can be found in the [Dedicated Server](#DedicatedServer) section.

## Administration
This section describes how to administer the server, including starting and stopping the server, as well as setting it up as a startup task 

### Starting the Server
The server is started directly though calling it with `java`. For example, the following will start the server in the background on port 1337, assuming your classpath is set properly:

```bash
nohup java -mx4g edu.stanford.nlp.pipeline.StanfordCoreNLPServer 1337 &
```

The classpath must include all of the CoreNLP dependencies. The memory requirements of the server are the same as that of CoreNLP, though it will grow as you load more models (e.g., memory increases if you load both the PCFG and Shift-Reduce constituency parser models). A safe minimum is 4gb; 8gb is recommended if you can spare it.

### Stopping the Server
The server can be stopped programmatically by making a call to the `/shutdown` endpoint with an appropriate shutdown key. This key is saved to the file `/tmp/corenlp.shutdown` when the server starts. An example command to shut down the server would be:

```bash
wget "localhost:9000/shutdown?key=`cat /tmp/corenlp.shutdown`" -O -
```

### Dedicated Server
This section describes how to set up a dedicated CoreNLP server on a fresh Linux install. As always, make sure you understand the command being run below, as they largely require root permissions:

1. Place all of the CoreNLP jars (code, models, and library dependencies) in a directory `/opt/corenlp`. The code will be in a jar named `stanford-corenlp-version.jar`. The models will be in a jar named `stanford-corenlp-models.jar`; caseless and shift-reduce models can also be added here. The minimal library dependencies, included in the CoreNLP release, are:
	* `joda-time.jar`
	* `jollyday-<version>.jar`
	* `protobuf.jar`
	* `xom-<version>.jar`

2. Install [authbind](https://en.wikipedia.org/wiki/Authbind). On Ubuntu, this is as easy as `sudo apt-get install authbind`.

3. Create a user `nlp` with permissions to read the directory `/opt/corenlp`. Allow the user to bind to port 80:
    
    ```bash
    sudo mkdir -p /etc/authbind/byport/
    sudo touch /etc/authbind/byport/80
    sudo chown nlp:nlp /etc/authbind/byport/80
    sudo chmod 600 /etc/authbind/byport/80
    ```

4. Copy the startup script from the source jar at path `edu/stanford/nlp/pipeline/demo/corenlp` to `/etc/init.d/corenlp`. An easy way to get this is:

    ```bash
	sudo wget https://raw.githubusercontent.com/stanfordnlp/CoreNLP/master/src/edu/stanford/nlp/pipeline/demo/corenlp -O /etc/init.d/corenlp
    ```

5. Link the script to `/etc/rc.d/`:  ```ln -s /etc/init.d/corenlp /etc/rc.d/rc2.d/S75corenlp```

The CoreNLP server will now start on startup, running on port 80 under the user `nlp`. To manually start/stop/restart the server, you can use:

```bash
sudo service corenlp [start|stop|restart]
```
