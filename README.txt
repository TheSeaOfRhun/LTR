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

Type "ant" in a shell.

----------------------------------------------------------------------
INDEXING

java -cp LTR/lib/* IndexTREC -index AP
               	   	     -docs  ap/AP
			     -stop  ap/ser17.txt
			     -stem  PorterStemFilter

AP - The string passed as -index is a directory where Lucene will
write the index.

ap/AP - This is a directory. In the sample test-collection ap.txt is
the only file in the corpus and it has been placed inside a directory
named 'AP' because Lucene expects a path to a directory to look for a
corpus in.

----------------------------------------------------------------------
RETRIEVAL

java -cp /x/LTR/lib/* BatchSearch -index      AP
                      		  -queries    ap/query-l.txt
				  -similarity BM25Similarity
				  -stop       ap/ser17.txt
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
