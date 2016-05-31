LTR

Rup Palchowdhury
rup.palchowdhury [at] gmail [dot] com

----------------------------------------------------------------------
DESCRIPTION

LTR is Lucene (5.4.0) with some modifications for processing TREC
test-collections. The way to use it is to pass to 'IndexTREC', at the
command-line, a TREC document corpus to index, and then retrieve
documents with 'BatchSearch' and a set of queries as input.

To run the commands described below you will need the sample TREC data
from: http://kak.tx0.org/IR/

----------------------------------------------------------------------
COMPILING

Type "mvn package" in a shell. Tested with Maven 3.0.5.

----------------------------------------------------------------------
INDEXING

java -cp "/x/LTR/lib/*" IndexTREC -settings settings.hjson \
                                  -index AP                \
                                  -docs  ap/AP             \
                                  -stop  ap/ser17.txt      \
                                  -stem  PorterStemFilter

AP - The string passed as -index is a directory where Lucene will
write the index.

ap/AP - This is a directory. In the sample test-collection ap.txt is
the only file in the corpus and it has been placed inside a directory
named 'AP' because Lucene expects a path to a directory to look for a
corpus in.

----------------------------------------------------------------------
RETRIEVAL

java -cp "/x/LTR/lib/*" BatchSearch -settings   settings.hjson  \
                                    -index      AP              \
                                    -queries    ap/query-l.txt  \
                                    -similarity BM25Similarity  \
                                    -stop       ap/ser17.txt    \
                                    -stem       PorterStemFilter

ap/query-l.txt - A plain text file containing formatted TREC
queries. Each query is enclosed in a <TOP> tag and the text is placed
within a <TEXT> tag. It was necessary to normalize the formatting
because the older (early 1990's) TREC queries used a different
structure and building this intelligence into Lucene would require
more work.

The query is pre-processed to this format:

    <TOP>
        <NUM>301</NUM>
        <TEXT>
            hello world
        </TEXT>
    <TOP>

Section E, 'PRE-PROCESSING TREC QUERIES', in TRECBOX's documentation
shows how to covert TREC queries to this format:

http://kak.tx0.org/IR/.trecbox/README.txt

----------------------------------------------------------------------
SETTINGS FILE

All settings can be provided in a settings file following HJSON, which is
a more human-friendly version of JSON. See example/settings.hjson for an 
example. The available settings are listed below.

Note: all paths can be absolute or relative to where LTR will be invoked.

Indexing + retrieval options:

    indexPath   --  The path to where the index is located (when querying) or
                    placed (when indexing).

    stopFile    --  The path to the stop word list file to use during indexing
                    or retrieval. Use "None" if no stopping should be performed
                    (default).
    
    stemmer     --  The name of the stemmer to use during indexing or retrieval.
                    See NOTES.txt for a list of available stemmers. Set to 
                    "None" to turn stemming off (default).

Indexing only options:

    docsPath    --  Indexing only. The path to the directory containing the 
                    corpus to index. This may contain uncompressed or compressed
                    (gzip or bzip2) files. Extensions will determine the parser
                    to use. See NOTES.txt for more information about document
                    formats.

    storeFields --  If set to false (default), fields other than docno will be
                    indexed, but not stored. Set to true in order to have the 
                    option of including snippets in retrieval results.
       
    warcFieldsToIndex
                --  A list of fields to index from WARC documents. Use 
                    "contents" to specify all document text (excluding tags).
                    If the list is empty, "contents" is assumed.

    trecFieldsToIndex
                --  Similar to warcFieldsToIndex, but for TREC text and web
                    documents.

Retrieval only options:

    searchField --  The field to search. Defaults to "contents".

    similarity  --  The retrieval model to use. See NOTES.txt for a full
                    listing of available retrieval models.

    queryFile   --  The path to the file containing all the queries to run in
                    batch search. See RETRIEVAL above for a description of
                    the format of this file.

    returnedResultCount
                --  Defaults to 1000. Set this to the maximum number of 
                    documents that should be returned for a query.
    
    includeSnippets
                --  Default is false. If true, snippets will be included
                    with results. In order to use this, the index must have
                    been indexed with the storeFields option set to true.

    maxSnippetFragments
                --  The number of sentence fragments to include in the snippets.
                    Defaults to 4.

----------------------------------------------------------------------
EXAMPLES

The examples/ directory contains an example setup, including:

    corpus/         --  A directory containing two simple example documents,
                        one each in the TREC and WARC formats.
    queries.txt     --  An example query file.
    settings.hjson  --  A settings file for indexing and retrieval.
    stop.txt        --  An example stop file (don't use this for anything real;
                        it only contains three stop words).

To index the example, do:

    cd example
    java -cp "../lib/*" IndexTREC -settings settings.hjson

This will create a directory called index/, which contains the Lucene index. If
you would like to see statistics about the index, check out the CLue command
line tool on GitHub (https://github.com/javasoze/clue).

To run the example retrieval, do the following from the example directory:

    java -cp "../lib/*" BatchSearch -settings settings.hjson

Try playing with the values in the settings file, or overriding values on the
command line to see how things work.
