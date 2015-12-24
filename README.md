Here is a set of tools to run Lucene (5.3.1) on TREC collections. To
build it run `ant` from inside the `lucene.TREC` directory. The Lucene
libraries live in `lib`, the classes in `bin` and the tool-set is
bundled into `lib/lucene.TREC.jar`. The usual way to use it on TREC
test collections is to pass `IndexTREC`, at the command-line, a
TREC-like document corpus to index, and then retrieve documents using
the index with `BatchSearch`, with a set of queries as input.

###### Indexing

```
java -cp /x/lucene.TREC/lib/*
     IndexTREC -index /x/index/CD45
               -docs  /x/doc/CD45
               -stop  /x/misc/ser17
               -stem  sstemmer
```

###### Retrieval

```
java -cp /x/lucene.TREC/lib/*
      BatchSearch -index   /x/index/CD45
                  -queries /x/query/301-350
                  -simfn   bm25
                  -stop    /x/misc/ser17
                  -stem    sstemmer
```

###### lucene.TREC

```
/x/lucene.TREC
├── README.md
├── bin
├── build.xml
├── lib
│   └── lucene.TREC.jar
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

- `/x` is a imaginary, the layout does not have to be this. It is only
  to point out how to point the tool to the data.

- `/x/index/CD45.017.s` is an empty directory that was created before
  passing it on to `IndexTREC`. The naming is arbitrary, but, if you use
  [trecbox](https://github.com/sauparna/trecbox) to drive `lucene.TREC`,
  it will have a meaning.

- `/x/misc/ser17` is a plain text file containing a list of stop-words,
  one on each line.

- `/x/query/301-350` is a plain text file containing TREC queries
  where each query is enclosed in a <TOP> tag. There are as many <TOP>
  tags as there are
  queries. [trecbox](https://github.com/sauparna/trecbox) takes care
  of creating this structure. Otherwise you have to write a program to
  do so. This pre-processing of query files was necessary because the
  older (early 1990's) TREC queries used a different structure, and
  the interntion is to have backward compatibility.

  ```
  <TOP>
    <NUM>301</NUM>
    <TEXT>
     hello world
    </TEXT>
  <TOP>
  ```
  
- `sstemmer` tells Lucene to use the *S-Stemmer*.
