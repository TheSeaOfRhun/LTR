**LTR** is a mod of Lucene (5.4.0) for doing Information Retrieval
(IR) experiments on TREC test-collections. The way to use it on TREC
data is to pass to `IndexTREC`, at the command-line, a TREC document
corpus to index, and then retrieve documents with `BatchSearch` and a
set of queries as input.

[Documentation][ltrd]

###### Build

To build Lucene run `ant` from inside the `LTR` directory. The Lucene
libraries live in `lib`, the classes in `bin` and the tool-set is
bundled into `lib/LTR.jar`.

```
/x/LTR
├── README.md
├── bin
├── build.xml
├── lib
│   ├── ...
│   └── LTR.jar
└── src
```

```
cd /x/LTR
ant
```

###### Index

```
java -cp /x/LTR/lib/*
     IndexTREC -index /x/index/CD45
               -docs  /x/doc/CD45
               -stop  /x/misc/ser17
               -stem  PorterStemFilter
```

###### Retrieve

```
java -cp /x/LTR/lib/*
      BatchSearch -index      /x/index/CD45
                  -queries    /x/query/301-350
                  -similarity BM25Similarity
                  -stop       /x/misc/ser17
                  -stem       PorterStemFilter
```

###### Locations

```
/x
├── doc
│   └── CD45
│       ├── fbis -> /x/ir/docs/cd5/fbis
│       ├── fr94 -> /x/ir/docs/cd4/fr94
│       ├── ft -> /x/ir/docs/cd4/ft
│       └── latimes -> /x/ir/docs/cd5/latimes
├── evals
├── index
│   └── CD45.017.s
├── misc
│   └── ser17
├── qrel
├── query
│   └── 301-350
└── runs
```

###### Note

A few points to note, and help clarify meaning:

- Command-line invocations have been formatted for readability.

- `/x` is a imaginary directory name, the layout does not have to be
  this.

- `/x/index/CD45.017.s` is an empty directory that was created before
  passing it to `IndexTREC`. The naming is arbitrary, but, if you use
  [trecbox][trb] to drive `LTR`, it will have a meaning.

- `/x/misc/ser17` is a plain text file containing a list of stop words
  -- one on each line.

- `/x/query/301-350` is a plain text file containing formatted TREC
  queries. Each query is enclosed in a <TOP> tag and the text is
  placed within a <TEXT> tag. It was necessary to normalize the
  formatting because the older (early 1990's) TREC queries used a
  different structure. A [snippet of code][trbq] shows how to use
  _trecbox's_ query parser to convert them to this format.

  ```
  <TOP>
    <NUM>301</NUM>
    <TEXT>
     hello world
    </TEXT>
  <TOP>
  ```

- `PorterStemFilter` is a name of a Java class that implements the
  Porter stemming algorithm and this string tells Lucene to use that
  stemmer.

[ltr]:  http://kak.tx0.org/IR/LTR/
[ltrd]: http://kak.tx0.org/IR/LTR/Doc/
[trb]:  http://kak.tx0.org/IR/trecbox/
[trbq]: http://kak.tx0.org/IR/trecbox/Doc/Query-Parser
