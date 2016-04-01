Here is a set of tools to run Lucene (5.4.0) on TREC
test-collections. To build Lucene run `ant` from inside the `LTR`
directory. The Lucene libraries live in `lib`, the classes in `bin`
and the tool-set is bundled into `lib/LTR.jar`. The usual way to use
it on TREC test-collections is to pass `IndexTREC`, at the
command-line, a TREC-like document corpus to index, and then retrieve
documents with `BatchSearch` and a set of queries as input.

###### Indexing

```
java -cp /x/LTR/lib/*
     IndexTREC -index /x/index/CD45
               -docs  /x/doc/CD45
               -stop  /x/misc/ser17
               -stem  PorterStemFilter
```

###### Retrieval

```
java -cp /x/LTR/lib/*
      BatchSearch -index      /x/index/CD45
                  -queries    /x/query/301-350
                  -similarity BM25Similarity
                  -stop       /x/misc/ser17
                  -stem       PorterStemFilter
```

###### LTR

```
/x/LTR
├── README.md
├── bin
├── build.xml
├── lib
│   └── LTR.jar
└── src
```

###### Data; input and output locations.

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

- Command-line invocations (shown above) have been formatted for
  readability, everything should go in one line.

- `/x` is a imaginary directory name, the layout does not have to be
  this. It is only to show out how to point the tool to the data.

- `/x/index/CD45.017.s` is an empty directory that was created before
  passing it to `IndexTREC`. The naming is arbitrary, but, if you use
  [trecbox](https://github.com/sauparna/trecbox) to drive `LTR`, it
  will have a meaning.

- `/x/misc/ser17` is a plain text file containing a list of stop words,
  one on each line.

- `/x/query/301-350` is a plain text file containing TREC queries
  formatted in a particular style. Each query is enclosed in a <TOP>
  tag and the text is placed within a <TEXT> tag.

  ```
  <TOP>
    <NUM>301</NUM>
    <TEXT>
     hello world
    </TEXT>
  <TOP>
  ```
  
  It was necessary to normalize the formatting because the older
  (early 1990's) TREC queries used a different structure. A snippet of
  code shows how to use trecbox's (query parser)[http://kak.tx0.org/IR/trecbox/Doc/Query-Parser] to pre-process the TREC query files. The parts of the query like 'title', 'description' and 'narrative', if specified, is packed in a single block of text within the <TEXT> tag.

- `PorterStemFilter` is a name of a Java class that implements the
  Porter stemming algorithm and this string tells Lucene to use that
  stemmer.
