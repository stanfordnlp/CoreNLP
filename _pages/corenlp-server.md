---
title: CoreNLP Server 
keywords: server
permalink: '/corenlp-server.html'
---

CoreNLP includes a simple web API server for servicing your human language understanding needs (starting with version 3.6.0). This page describes how to set it up.  CoreNLP server provides both a convenient graphical way to interface with your installation of CoreNLP and an API with which to call CoreNLP using any programming language. If you're writing a new wrapper of CoreNLP for using it in another language, you're advised to do it using the CoreNLP Server.

## Getting Started

Stanford CoreNLP ships with a built-in server, which requires only the CoreNLP dependencies. To run this server, simply run:

```bash
# Run the server using all jars in the current directory (e.g., the CoreNLP home directory)
java -mx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -port 9000 -timeout 15000
```

Note the the timeout is in milliseconds.

If you want to process non-English languages, use this command with the appropriate language properties:

```bash
# Run a server using Chinese properties
java -Xmx4g -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLPServer -serverProperties StanfordCoreNLP-chinese.properties -port 9000 -timeout 15000
``` 

Each language has a models jar which must also be on the CLASSPATH.  The most recently models jars for each language can be found [here](http://stanfordnlp.github.io/CoreNLP/download.html).  

If no value for `port` is provided, port 9000 will be used by default. You can then test your server by visiting

    http://localhost:9000/

You should see a website similar to [corenlp.run](http://corenlp.run/), with an input box for text and a list of annotators you can run. From this interface, you can test out each of the annotators by adding/removing them from this list. (Note: *The first use will be slow*  to respond while models are loaded – it might take 30 seconds or so, but after that the server should run quite quickly.) You can test out the API by sending a `POST` request to the server with the appropriate properties. An easy way to do this is with [wget](https://www.gnu.org/software/wget/). The following will annotate the sentence "*the quick brown fox jumped over the lazy dog*" with part of speech tags:

```bash
wget --post-data 'The quick brown fox jumped over the lazy dog.' 'localhost:9000/?properties={"annotators":"tokenize,ssplit,pos","outputFormat":"json"}' -O -
```

Or if you only have or prefer [curl](https://curl.haxx.se/):

```bash
curl --data 'The quick brown fox jumped over the lazy dog.' 'http://localhost:9000/?properties={{ "{%" }}22annotators%22%3A%22tokenize%2Cssplit%2Cpos%22%2C%22outputFormat%22%3A%22json%22}' -o -
```

The rest of this document: describes the API in more detail, describes a Java client to the API as a drop-in replacement for the `StanfordCoreNLP` annotator pipeline, and talks about administering the server.


## API Documentation

The greatest strength of the server is the ability to make API calls against it. 

> **NOTE**: Please do **not** make API calls against [corenlp.run](http://corenlp.run). It is not set up to handle a large volume of requests. Instructions for setting up your own server can be found in the [Dedicated Server](#dedicated-server) section.

There are three endpoints provided by the server, which we'll describe in more detail below. Each of them takes as input a series of url parameters, as well as `POST` data consisting of the serialized document or raw text to be annotated. The endpoints are:

*  `/` Provides an interface to annotate documents with CoreNLP.
* `/tokensregex` Provides an interface for querying text for TokensRegex patterns, once it has been annotated with CoreNLP (using the enpoint above).
* `/semgrex` Similar to `/tokensregex` above, this endpoint matches text against semgrex patterns.

### Annotate with CoreNLP: `/`

This endpoint takes as input a JSON-formatted properties string under the key `properties=<properties>`, and as `POST`data text to annotate. The properties should mirror the properties file passed into the CoreNLP command line, except formatted as a JSON object. For example, the following will tokenize the input text, run part of speech tagging, and output it as JSON to standard out:

```bash
wget --post-data 'the quick brown fox jumped over the lazy dog' 'localhost:9000/?properties={"annotators": "tokenize,ssplit,pos", "outputFormat": "json"}' -O -
```

A common property to set is the output format of the API. The server supports all output formats provided by CoreNLP. These are listed below, along with their relevant properties:

* **JSON**: Print the annotations in JSON format. This corresponds to the property: `{"outputFormat": "json"}`.
* **XML**: Print the annotations in XML format. This corresponds to the property: `{"outputFormat": "xml"}`.
* **Text**: Print the annotations in a human-readable text format. This is the default format for the CoreNLP command-line interface. This corresponds to the property: `{"outputFormat": "text"}`.  
* **Serialized**: Print the annotations in a losslessly serialized format. This is the recommended option when calling the API programmatically from a language that supports one of the serialized formats. In addition to setting the output format flag, you must also provide a valid serializer class. For example, for protocol buffers, this would be: 
  ```
{"outputFormat": "serialized", 
 "serializer": "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer"}
  ```
  The serializers currently supported are:
  - `edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer` Writes the output to a protocol buffer, as defined in the definition file `edu.stanford.nlp.pipeline.CoreNLP.proto`.
  -  `edu.stanford.nlp.pipeline.GenericAnnotationSerializer` Writes the output to a Java serialized object. This is only suitable for transferring data between Java programs. This also produces relatively large serialized objects.
  - `edu.stanford.nlp.pipeline.CustomAnnotationSerializer` Writes the output to a (lossy!) textual representation, which is much smaller than the `GenericAnnotationSerializer` but does not include all the relevant information.

The server also accepts input in a variety of formats. By default, it takes input as raw text sent as `POST` data to the server. However, it can also be configured to read the `POST` data using one of the CoreNLP serializers. This can be set up by setting the properties `inputFormat` and `inputSerializer`. For example, to read the data as a protocol buffer (useful if, e.g., it is already partially annotated), simply include the following in your url parameter `properties={...}`:

```json
{"inputFormat": "serialized",
 "inputSerializer": "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer"}
```

A complete call to the server, taking as input a protobuf serialized document at path `/path/to/file.proto` and returning as a response a protobuf for the document annotated for part of speech and named entity tags (to the file `/path/to/annotated_file.proto` could be:

```bash
wget --post-file /path/to/file.proto 'localhost:9000/?properties={"inputFormat": "serialized", "inputSerializer", "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer", "annotators": "tokenize,ssplit,pos,lemma,ner", "outputFormat": "serialized", "serializer", "edu.stanford.nlp.pipeline.ProtobufAnnotationSerializer"}' -O /path/to/annotated_file.proto
```

### Query TokensRegex: `/tokensregex`

Similar to the CoreNLP target, `/tokensregex` takes a block of data (e.g., text) as `POST` data, and a series of url parameters. Currently, only plain-text `POST` data is supported. The two relevant url parameters are:

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

Similar to the CoreNLP target, and nearly identical to TokensRegex, `/semgrex` takes a block of data (e.g., text) as `POST` data, and a series of url parameters. Currently, only plain-text `POST` data is supported. The two relevant url parameters are:

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

CoreNLP includes a Java client to the server – `StanfordCoreNLPClient` – which mirrors the interface of the annotation pipeline (`StanfordCoreNLP.java`) as closely as possible. The primary motivating use cases for using this class and not the local pipeline are:

* The models are not re-loaded every time your program runs. This is useful when debugging a block of code which runs CoreNLP annotations, as the CoreNLP models often take on the order of minutes to load from disk.
* The machine running the server has more compute and more memory than your local machine. Never again must Chrome and CoreNLP compete for the same memory.

The constructors to `StanfordCoreNLPClient` take the following 3 required arguments, and a fourth optional argument:

1. `Properties props`: Mirroring the local pipeline exactly, these are the properties to use when annotating text with the pipeline. Minimally, this specifies the annotators to run.
2. `String host`: The hostname of the server.
3. `int port`: The port that the server is running on.
4. `int threads`: Optionally, the number of threads to hit the server with. If, for example, the server is running on an 8 core machine, you can specify this to be 8, and the client will allow you to make 8 simultaneous requests to the server. Note that there is nothing that ensures that you have these threads reserved on the server: two clients can both hit the server with 8 threads, and the server will just respond half as fast.

An example programmatic usage of the client, hitting a server at localhost:9000 with up to 2 threads, is as follows. Note that this exactly mirrors the usage of the conventional pipeline.

```java
// creates a StanfordCoreNLP object with POS tagging, lemmatization, NER, parsing, and coreference resolution
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
java -cp "*" -Xmx1g edu.stanford.nlp.pipeline.StanfordCoreNLPClient -annotators tokenize,ssplit,pos,lemma,ner,parse,dcoref -file input.txt
```

> **NOTE**: Again, please do **not** make API calls against `http://corenlp.run`. It is not set up to handle a large volume of requests. Instructions for setting up your own server can be found in the [Dedicated Server](#dedicated-server) section.

Once you have your own server(s) set up, you can run against them with a command like this:

```bash
java edu.stanford.nlp.pipeline.StanfordCoreNLPClient -cp "*" -annotators tokenize,ssplit,pos,lemma,ner,parse,dcoref -file input.txt  -backends localhost:9000
```

You specify one or more back-end servers in a comma-separated list as the arguments of the `-backends` option. Each is specified as `host:port`.

Providing that the server has foreign language models available on its
classpath, you can ask for it to work with texts in other languages.
If you have the French properties file and a file called `french.txt`
in your current directory, then you should be able to successfully give a command like this:

```bash
java -cp "*" edu.stanford.nlp.pipeline.StanfordCoreNLPClient -props StanfordCoreNLP-french.properties -annotators tokenize,ssplit,pos,depparse ile french.txt -outputFormat conllu -backends localhost:9000
```

## Usage via other programming languages

There are now modules for several programming languages, including
Python and JavaScript, which work by
talking to a Stanford CoreNLP server instance. Indeed, this is now normally the best
way to implement an interface to CoreNLP in other languages.

Check out what is available on the
[Other programming languages and packages page](other-languages.html).

## Server Administration

This section describes how to administer the server, including starting and stopping the server, as well as setting it up as a startup task 

### Starting the Server

The server is started directly though calling it with `java`. For example, the following will start the server in the background on port 1337, assuming your classpath is set properly:

```bash
nohup java -mx4g edu.stanford.nlp.pipeline.StanfordCoreNLPServer 1337 &
```

The classpath must include all of the CoreNLP dependencies. The memory requirements of the server are the same as that of CoreNLP, though it will grow as you load more models (e.g., memory increases if you load both the PCFG and Shift-Reduce constituency parser models). A safe minimum is 4gb; 8gb is recommended if you can spare it.

### Docker

If running the server under docker, the container’s port 9000 has to be published to the host. Give a command like:
`docker run -p 9000:9000 --name coreNLP --rm -i -t motiz88/corenlp`. If, when going to `localhost:9000/`, you see the error 
`This site can’t be reached. localhost refused to connect`, then this is what you failed to do!

### Stopping the Server

The server can be stopped programmatically by making a call to the `/shutdown` endpoint with an appropriate shutdown key. This key is saved to the file `corenlp.shutdown` in the directory specified by `System.getProperty("java.io.tmpdir");` when the server starts.  Typically this will be `/tmp/corenlp.shutdown`, though it can vary, especially on macOS.  An example command to shut down the server would be:

```bash
wget "localhost:9000/shutdown?key=`cat /tmp/corenlp.shutdown`" -O -
```

If you start the server with `-server_id SERVER_NAME` it will store the shutdown key in a file called `corenlp.shutdown.SERVER_NAME`.

### Command line flags

The server can take a number of command-line flags, documented below:

| Flag | Argument type | Default | Description |
| --- | --- | --- | --- |
| `-port`        | Integer | 9000    | The port to run the server on. |
| `-status_port` | Integer | `-port` | The port to run the liveness and readiness server on. Defaults to running on the main server (i.e., also on port 9000). |
| `-timeout`     | Integer | 15000   | The maximum amount of time, in milliseconds, to wait for an annotation to finish before cancelling it. |
| `-strict`      | Boolean | false   | If true, follow HTTP standards strictly -- this means not returning in UTF unless it's explicitly requested! |
| `-ssl`         | Boolean | false   | If true, run an SSL server, with the *.jks key in `-key`. By default, this loads the (very insecure!) key included in the CoreNLP distribution. |
| `-key`         | String  | edu/stanford/nlp/pipeline/corenlp.jks   | The classpath or filepath to the *.jks key to use for creating an SSL connection |
| `-username`    | String  | ""      | Along with `-password`, if set this enables basic auth with the given username. |
| `-password`    | String  | ""      | Along with `-username`, if set this enables basic auth with the given password. |
| `-annotators` | String  | tokenize,ssplit,pos,lemma,ner,parse,depparse,mention,coref,natlog,openie,regexner,kbp | If no annotators are specified with the annotation request, these annotators are run by default. |
| `-preload` | String  | "" | A set of annotators to warm up in the cache when the server boots up. The `/ready` endpoint won't respond with a success until all of these annotators have been loaded into memory. |
| `-serverProperties` | String  | "" | A file with the default properties the server should use if no properties are set in the actual annotation request. Useful for, e.g., changing the default language of the server. |


### Dedicated Server

This section describes how to set up a dedicated CoreNLP server on a
fresh Linux install. These instructions are definitely okay on a
CentOS 6 system, which is what our demo server runs on. We include a
couple of notes of variations below.
As always, make sure you understand the commands being run below, as they largely require root permissions:

1. Place all of the CoreNLP jars (code, models, and library dependencies) in a directory `/opt/corenlp`. The code will be in a jar named `stanford-corenlp-<version>.jar`. The models will be in a jar named `stanford-corenlp-<version>-models.jar`; other language, caseless or shift-reduce models can also be added here. The minimal library dependencies, included in the CoreNLP release, are:
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

5. Give executable permissions to the startup script:  ```sudo chmod a+x /etc/init.d/corenlp```

6. Link the script to `/etc/rc.d/`:
    ```ln -s /etc/init.d/corenlp /etc/rc.d/rc2.d/S75corenlp```

    On Ubuntu, there is no intervening `rc.d` directory, so the equivalent is to do:
    ```ln -s /etc/init.d/corenlp /etc/rc2.d/S75corenlp```

The above steps work using traditional SysVinit scripts. The other
   alternative on Ubuntu is to use Upstart instead. We haven't tried
   that but believe that the corresponding thing to do is:
   ```bash
   sudo wget https://raw.githubusercontent.com/stanfordnlp/CoreNLP/master/src/edu/stanford/nlp/pipeline/demo/corenlp -O /etc/init/corenlp
   initctl reload-configuration
   ```

The CoreNLP server will now start on startup, running on port 80 under the user `nlp`. To manually start/stop/restart the server, you can use:

```bash
sudo service corenlp [start|stop|restart]
```


## Quirks and Subtleties

This section documents some of the subtle quirks of the server, and the motivations behind them.

### Character Encoding

The official HTTP 1.1 specification [recommends ISO-8859-1](https://www.w3.org/International/O-HTTP-charset) as the encoding of a request, unless a different `encoding` is explicitly set by using the `Content-Type` header. However, for most NLP applications this is an unintuitive default, and so the server instead defaults to UTF-8. To enable the ISO-8859-1 default, pass in the `-strict` flag to the server at startup.

### Default Properties

The server has different default properties than the regular CoreNLP pipeline. These are:

  * The default output format is `json` rather than `text` (`-outputFormat json`). This is more natural for most cases when you would be making API calls against a server.
  * By default, the server will not pretty print the output, opting instead for a minified output. This is the same as setting the property `-prettyPrint false`.
  * The default annotators do not include the `parse` annotator. This is primarily for efficiency. The annotators enabled by default are: `-annotators tokenize, ssplit, pos, lemma, ner, depparse, coref, natlog, openie`.
  * As a necessary consequence of not having the `parse` annotator, the default coref mention detector is changed to use dependency parsers: `-coref.md.type dep`.

### Undocumented Features

Well, I guess they're documented now:

  * Hitting `Shift+Enter` on any input field in the web demo (e.g., the main text input) is equivalent to clicking the `Submit` (or `Match`) button. Furthermore, if the input is empty, it will fill itself with a default input. Useful if – to take a purely hypothetical example – you're developing the web server and don't want to re-type the same sentence everytime you re-load the website.

### Server readiness

When booting up an instance of the server for a shell script, make sure you wait for the server to be available before interacting with it. An example using the `netcat` tool on linux:

```bash
#!/bin/bash
java -mx4g edu.stanford.nlp.pipeline.StanfordCoreNLPServer &
# Wait until server starts
while ! nc -z localhost 9000; do
    sleep 0.1 # wait for 1/10 of the second before check again
done
# Rest of script
# ...
```

If you're in a production environment, you can also wait for liveness (`/live`) and readiness (`/ready`) endpoints to check if the server is online (liveness) and ready to accept connections (readiness) respectively. These mirror the semantics of the Kubernetes liveness and readiness probes, and can double as health checks for the server.
