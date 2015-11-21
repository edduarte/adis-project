# Argus

[![Build Status](https://travis-ci.org/edduarte/argus.svg?branch=master)](https://travis-ci.org/edduarte/argus)
[![Coverage Status](https://img.shields.io/coveralls/edduarte/argus.svg)](https://coveralls.io/r/edduarte/argus)
[![GitHub release](https://img.shields.io/github/release/edduarte/argus.svg)](https://github.com/edduarte/argus/releases)

- [Getting Started](#getting-started)
    + [Installation](#installation)
    + [Usage](#usage)
- [Dependencies](#dependencies)
- [Architecture](#architecture)
    + [Job Management](#job-management)
        * [Difference Detection](#difference-detection)
        * [Difference Matching](#difference-matching)
        * [Clustering](#clustering)
        * [Scaling](#scaling)
    + [Persistence](#persistence)
    + [Reading](#reading)
    + [Indexing](#indexing)
- [Caveats](#caveats)
- [License](#license)

Argus is a high-performance, scalable web service that provides web-page monitoring, triggering notifications when specified keywords were either added or removed from a web document. It supports multi-language parsing and supports reading the most popular web document formats like HTML, JSON, XML, Plain-text and other variations of these.

This service implements a information retrieval system that fetches, indexes and performs queries over web documents on a periodic basis. Difference detection is implemented by comparing occurrences between two snapshots of the same document.

# Getting Started

## Installation

Argus uses the Publish/Subscribe model, where <u>**an additional Client web service with a REST API must be implemented to request and consume Argus web service**</u>. This allows for an asynchronous operation, where the client does not have to lock its threads while waiting for page changes notifications nor implement a busy-waiting condition checking of Argus status.

Once you have a client web service running, follow the steps below:

1. Download [MongoDB](https://www.mongodb.org/downloads)
2. Run MongoDB with
```
mongod
```
3. Download the [latest release](https://github.com/edduarte/argus/releases) of Argus
4. Run Argus with
```
java -jar argus-core.jar

Optional arguments:
 -h,--help               Shows this help prompt.
 -p,--port <arg>         Core server port. Defaults to 9000.
 -dbh,--db-host <arg>    Database host. Defaults to localhost.
 -dbp,--db-port <arg>    Database port. Defaults to 27017.
 -case,--preserve-case   Keyword matching with case sensitivity.
 -stop,--stopwords       Keyword matching with stopword filtering.
 -stem,--stemming        Keyword matching with stemming (lexical
                         variants).
 -t,--threads <arg>      Number of threads to be used for computation and
                         indexing processes. Defaults to the number of
                         available cores.
```
5. If Argus was successfully deployed, opening 'localhost:9000' on a browser will launch a landing page with usage instructions.



## Usage

To watch for content, a POST call must be sent to ** http://localhost:9000/argus/v1/watch ** with the following JSON body:
```javascript
{
    "documentUrl": "http://www.example.com", // the page to be watched (mandatory field)
    "receiverUrl": "http://your.site/client-rest-api", // the client web service that will receive detected differences (mandatory field)
    "keywords": // the keywords to watch for (mandatory field)
    [
        "argus", // looks for changes with this word (if stemming is enabled, looks for changes in lexical variants)
        "panoptes", // looks for changes with this word (if stemming is enabled, looks for changes in lexical variants)
        "argus panoptes" // looks for an exact match of this phrase (if stemming is enabled, looks for changes in lexical variants)
    ],
    "interval": 600, // the elapsed duration (in seconds) between page checks (optional field, defaults to 600)
    "ignoreAdded": false, // if 'true', ignore events where the keyword was added to the page (optional field, defaults to 'false')
    "ignoreRemoved": false // if 'true', ignore events where the keyword was removed from the page (optional field, defaults to 'false')
}
```

When detected differences are matched with keywords, notifications are asynchronously sent to the provided response URL in POST with the following JSON body:
```javascript
{
    "status": "ok",
    "url": "http://www.example.com",
    "diffs": [
        {
            "action": "added",
            "keyword": "argus",
            "snippet": "In the 5th century and later, Argus' wakeful alertness ..."
        },
        {
            "action": "removed",
            "keyword": "argus",
            "snippet": "... sacrifice of Argus liberated Io and allowed ..."
        }
    ]
}
```

Argus is capable of managing a high number of concurrent watch jobs, as it is implemented to save resources and free up database and memory space whenever possible. To this effect, Argus automatically expires jobs when it fails to fetch a web document after 10 consecutive tries. When that happens, the following JSON body is sent to the response URL:
```javascript
{
    "status": "timeout",
    "url": "http://www.example.com",
    "diffs": []
}
```

Finally, to manually cancel a watch job, a POST call must be sent to ** http://localhost:9000/argus/v1/cancel ** with the following JSON body:
```javascript
{
    "documentUrl": "http://www.example.com", // the page that was being watched (mandatory field)
    "receiverUrl": "http://your.site/client-rest-api" // the client web service (mandatory field)
}
```

Immediate responses are returned for every watch or cancel request, showing if the request was successful or not with the following JSON body:
```javascript
{
    "code": "ok"/"error",
    "message": "..."
}
```

# Dependencies

Jersey RESTful framework: https://jersey.java.net  
Jetty Embedded server: http://eclipse.org/jetty/  
Snowball stop-words and stemmers: http://snowball.tartarus.org  
Language Detector: https://github.com/optimaize/language-detector  
Cache2K: http://cache2k.org  
MongoDB Java driver: http://docs.mongodb.org/ecosystem/drivers/java/  
Quartz Scheduler: http://quartz-scheduler.org  
Quartz MongoDB-based store: https://github.com/michaelklishin/quartz-mongodb  
DiffMatchPatch: https://code.google.com/p/google-diff-match-patch/  
Gson: https://code.google.com/p/google-gson/  
Jsonic: http://jsonic.sourceforge.jp  
Jsoup: http://jsoup.org  
Jackson: http://jackson.codehaus.org  
DSI Utilities: http://dsiutils.di.unimi.it  
Commons-IO: http://jackson.codehaus.org  
Commons-Codec: http://commons.apache.org/proper/commons-codec/  
Commons-CLI: http://commons.apache.org/proper/commons-cli/  
Commons-Lang: http://commons.apache.org/proper/commons-lang/  
Commons-Validator: http://commons.apache.org/proper/commons-validator/  

# Architecture

## Job Management

There are 2 types of jobs, concurrently executed and scheduled periodically (using Quartz Scheduler) with an interval of 600 seconds by default: difference detection jobs and difference matching jobs.

### Difference Detection

The detection job is responsible for fetching a new document and comparing it with the previous document, detecting textual differences between the two. To do that, the robust DiffMatchPatch algorithm is used.

### Difference Matching

The matching job is responsible for querying the list of detected differences with specific requested keywords.

Harmonization of keywords-to-differences is performed passing the differences through a Bloom filter, to remove differences that do not have the specified keywords, and a character-by-character comparator on the remaining differences, to ensure that the difference contains any of the keywords.

### Clustering

Since the logic of difference retrieval is spread between two jobs, one that is agnostic of requests and one that is specific to the request and its keywords, Argus reduces workload by scheduling only one difference detection job per watched web-page. For this effect, jobs are grouped into clusters, where its unique identifier is the document URL. Each cluster contains, imperatively, a single scheduled detection job and one or more matching jobs.

### Scaling

Argus was conceived to be able scale and to be future-proof, and to this effect it was implemented to deal with a high number of jobs in terms of batching / persistence and of real-time / concurrency.

The clustering design mentioned above implies that, as the number of clients grows linearly, the number of jobs will grow semi-linearly because the first call for a URL will spawn two jobs and the remaining calls for the same URL will spawn only one.

In terms of orchestration, there are two mechanisms created to reduce redundant resource-consumption, both in memory as well as in the database:

1. if the difference detection job fails to fetch content from a specific URL after 10 consecutive attempts, the entire cluster for that URL is expired. When expiring a cluster, all of the associated client REST APIs receive a time-out call.
2. every time a matching job is cancelled by its client, Argus checks if there are still matching-jobs in its cluster, and if not, the cluster is cleared from the workspace.

## Persistence

Documents, indexing results, found differences are all stored in MongoDB. To avoid multiple bulk operations on the database, every query (document, tokens, occurrences and differences) is covered by memory cache with an expiry duration between 20 seconds and 1 minute.

Persistence of difference-detection jobs and difference-matching jobs is also covered, using a custom MongoDB Job Store by Michael Klishin and Alex Petrov.

## Reading

Once a request has been received or a difference-detection job was triggered, the raw content and the Content-Type of the specified document are fetched. With the Content-Type, an appropriate Reader class that supports the conversion of the raw content into a string (filtered of non-informative data or XML tags) is collected and used.

Reader classes follow the plugin paradigm, which means that compiled Reader classes can be added to or removed from the 'argus-readers' folder during runtime, and Argus will be able to dynamically load a suitable Reader class that supports the obtained Content-Type.

When Reader classes are instanced, they are stored in on-heap memory cache temporarily (5 seconds). This reduces the elapsed duration of discovering available Reader classes and instancing one for consecutive stems of documents with the same language (for example, English).

## Indexing

The string of text that represents the document snapshot that was captured during the Reading phase is passed through a parser that tokenizes, filters stop-words and stems text. For every token found, its occurrences (positional index, starting character index and ending character index) in the document are stored. When a detected difference affected a token, the character indexes of its occurrences can be used to retrieve snippets of text. With this, Argus can instantly show to user, along with the notifications of differences detected, the added text in the new snapshot or the removed text in the previous snapshot.

Because different documents can have different languages, which require specialized stemmers and stop-word filters to be used, the language must be obtained. Unlike the Content-Type, which is often provided as a HTTP header when fetching the document, the Accept-Language is not for the most part. Instead, Argus infers the language from the document content using a language detector algorithm based on Bayesian probabilistic models and N-Grams, developed by Nakatani Shuyo, Fabian Kessler, Francois Roland and Robert Theis.

Stemmer classes and stop-word files, both from the Snowball project, follow the plugin paradigm, similarly to the Reader classes. This means that both can be changed during runtime and Argus will be updated without requiring a restart. Moreover, like the Reader classes, Stemmer classes are stored in on-heap memory cache for 5 seconds before being invalidated.

To ensure a concurrent architecture, where multiple parsing calls should be performed in parallel, Argus will instance multiple parsers when deployed and store them in a blocking queue. The number of parsers corresponds to the number of cores available in the machine where Argus was deployed to.

# Caveats

- Argus has only been used in a production environment for academic projects, and has not been battle-tested or integrated in consumer software;
- the intervals for difference-matching jobs can be set on the watch request, but difference-detection occurs independently of difference-matching so it can accommodate to all matching jobs for the same document. This means that difference-detection job needs to use an internal interval (420 seconds), and that matching jobs that are configured to run more frequently than that interval will look for matches on the same detected differences two times or more. The architecture should be revised so that:
    + intervals cannot be configured;
    + matching jobs are not scheduled but rather invoked once a detection job is completed.

# License

    Copyright 2015 Ed Duarte

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

