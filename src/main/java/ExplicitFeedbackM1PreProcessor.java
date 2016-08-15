import java.util.ArrayList;
import java.util.TreeSet;
import java.util.HashMap;
import java.lang.StringBuilder;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Models a set of relevant (Rel) and non-relevant (NRel) documents in the
 * index and creates a query with terms from Rel weighted by:
 *
 *  weight(w) = p(w|Rel) / p(w|NRel)
 *
 * where p(w|X) is defined as:
 *
 *  p(w|X) = 1/|X| * ((sum_{D \in X} (tf(w,D) + OOV) / |D|) + OOV)
 *  
 * Essentially, weight(w) is the ratio of w's micro average occurrence in the
 * relevant and non-relevant document sets. 
 *
 * Terms will be stemmed and stopped (if specified in the LTR Settings) before
 * calculating statistics.
 * 
 * @author hafeild
 */
public class ExplicitFeedbackM1PreProcessor implements QueryPreProcessor {
    private ArrayList<FeedbackDocument> relevantDocs, nonRelevantDocs;
    private IndexSearcher searcher;
    private Analyzer analyzer;
    private LTRSettings globalSettings;
    private int topTermsToKeep;
    // Out-of-vocabulary smoothing factor.
    public static final double OOV = 0.001;

    public class FeedbackDocument {
        boolean isRelevant;
        String docno, content;

        public FeedbackDocument(){
            isRelevant = true;
            docno = null;
            content = null;
        }

        public FeedbackDocument(boolean isRelevant, String docno, 
        String content){
            this.isRelevant = isRelevant;
            this.docno = docno;
            this.content = content;
        }
    }

    /**
     * A class for keeping track of a term's stats in relevant and non-relevant
     * documents.
     */
    public class TermStats {
        double relTermLikelihoodSum, nonRelTermLikelihoodSum;

        public TermStats(){
            relTermLikelihoodSum = 0.0;
            nonRelTermLikelihoodSum = 0.0;
        }

        public void addRelTermLikelihood(double likelihood) {
            relTermLikelihoodSum += likelihood;
        }

        public void addNonRelTermLikelihood(double likelihood) {
            nonRelTermLikelihoodSum += likelihood;
        }

        public double getNormalizedProb(int relDocCount, int nonRelDocCount){
            return (nonRelDocCount * (relTermLikelihoodSum+OOV)) /
                   (relDocCount * (nonRelTermLikelihoodSum+OOV));
        }
    }

    /**
     * A class for keeping track of a docuemnt's terms and frequencies.
     */
    public class DocStats {
        HashMap<String, Integer> termCounts;
        int length;

        public DocStats(){
            termCounts = new HashMap<String, Integer>();
            length = 0;
        }

        public void addTerm(String term, int count){
            if(!termCounts.containsKey(term))
                termCounts.put(term, 0);

            termCounts.put(term, termCounts.get(term)+count);
            length += count;
        }
    }

    /**
     * A wrapper for query terms and their weights. This helps with sorting.
     */
    public class WeightedTerm implements Comparable<WeightedTerm> {
        public String term;
        public double weight;
        
        public WeightedTerm(){ 
            this(null, 0.0);
        }

        public WeightedTerm(String term, double weight) {
            this.term = term;
            this.weight = weight;
        }

        /**
         * Returns a reverse-natural ordering (higher first).
         */
        public int compareTo(WeightedTerm other){
            double diff = weight - other.weight;
            if(diff == 0){
                return -1*term.compareTo(other.term);
            } else if(diff < 0) {
                return 1;
            } else {
                return -1;
            }
        }

    }

    /**
     * Reads in the settings and index reader. The settings should include
     * the following fields:
     *
     *    feedback -- denotes the feedback section.
     *
     *    topTermsToKeep -- an optional child of feedback; if present, this
     *                denotes the number of top ranking terms that will be
     *                returned by getQuery(). Leave out or set to -1 to use all
     *                non-stopped terms from relevant documents. Otherwise,
     *                this number should be greater than 1 (0 will cause an
     *                exception to be thrown).
     *
     *    doc      -- a child of feedback; can occur more than once;
     *                Attributes:
     *                  relevant -- REQUIRED. One of "true" or "false".
     *                  docno    -- OPTIONAL. A valid document id contained
     *                              within the index specified in the retrieval
     *                              settings.
     *                Text:
     *                              Text is ignored if docno is provided;
     *                              otherwise, the node text is processed as the
     *                              document content.
     *
     * @param xmlSettings An XML string with the above settings.
     * @throws Exception if require settings are not present.
     */
    public void initialize(String xmlSettings, LTRSettings globalSettings) 
    throws Exception {
        org.jsoup.nodes.Document soup;
        Element feedbackElm, topTermsToKeepElm;

        // Make a copy of the global settings. We need to ensure that the
        // classic tokenizer so terms can be extracted.
        this.globalSettings = globalSettings.deepCopy();
        this.globalSettings.tokenizer = "ClassicTokenizer";

        // Initialize the index reader, etc.
        IndexReader reader = DirectoryReader.open(FSDirectory.open(
            Paths.get(globalSettings.indexPath)));
        searcher = new IndexSearcher(reader);
        analyzer = new TrecAnalyzer(globalSettings);
        relevantDocs = new ArrayList<FeedbackDocument>();
        nonRelevantDocs = new ArrayList<FeedbackDocument>();


        // Parse the settings.
        soup = Jsoup.parse(xmlSettings);
        feedbackElm = soup.select("feedback").first();

        // Make sure there's a feedback section.
        if(feedbackElm == null)
            throw new Exception("Query XML missing <feedback> element:\n"+
                xmlSettings);
            
        for(Element feedbackDocElm : feedbackElm.select("doc")){
            FeedbackDocument feedbackDoc = new FeedbackDocument();
            String relValue;

            // Verify and save the relevance of the document.
            if(!feedbackDocElm.hasAttr("relevant"))
                throw new Exception("Feedback <doc> missing 'relevant' "+
                    "attribute: "+ feedbackDocElm);
            
            relValue = feedbackDocElm.attr("relevant");
            if(!relValue.equals("true") && !relValue.equals("false"))
                throw new Exception("Feedback <doc> 'relevant' attribute "+
                    "value invalid: "+ relValue);

            feedbackDoc.isRelevant = relValue.equals("true");

            // Ensure there is a docno or document content associated with the
            // document.
            if(!feedbackDocElm.hasAttr("docno") && !feedbackDocElm.hasText())
                throw new Exception("Feedback <doc>s must have either a "+
                    "'docno' attribute or contain text to use as the document "+
                    "contents: "+ feedbackDocElm);

            if(feedbackDocElm.hasAttr("docno"))
                feedbackDoc.docno = feedbackDocElm.attr("docno");
            else
                feedbackDoc.content = feedbackDocElm.text();

            if(feedbackDoc.isRelevant)
                relevantDocs.add(feedbackDoc);
            else
                nonRelevantDocs.add(feedbackDoc);
        }

        // Extract topTermsToKeep from XML.
        topTermsToKeep = -1;
        topTermsToKeepElm = feedbackElm.select("topTermsToKeep").first();
        if(topTermsToKeepElm != null && topTermsToKeepElm.hasText())
            topTermsToKeep = Integer.parseInt(topTermsToKeepElm.text());
        if(topTermsToKeep == 0)
            throw new Exception("In <feedback>, <topTermsToKeep> must be -1 "+
                "(to use all feedback terms) or greater than 0."); 
        
    }


    /**
     * Computes a relevance model -- a set of terms extracted from relevant
     * documents weighted by p(w|R)/p(w/NR) (R and NR are the sets of relevant
     * and nonrelevant documents, respectively and p(w|X) is defined as: 
     * 
     *  p(w|X) = 1/|X| * ((sum_{D \in X} (tf(w,D) + OOV) / |D|) + OOV)
     *
     * This is returned as a string query in the format:
     *
     *  w1^weight1 w2^weight2 ...
     *
     * Currently, no pre-processing is done to terms (stemming, stopping, etc.).
     * Terms are used exactly as they appear in the index in the event that
     * docnos are provided in the settings or the content provided in the
     * settings.
     *
     * @return A query representation of the relavance model in the form:
     *          w1^weight1 w2^weight2 ...
     */
    public String getQuery() {
        StringBuilder queryString;
        HashMap<String,TermStats> relevanceModel = 
            new HashMap<String,TermStats>();
        TreeSet<WeightedTerm> finalTerms = new TreeSet<WeightedTerm>();

        // Extract term stats from relevant documents.
        for(FeedbackDocument relDoc : relevantDocs){
            DocStats docStats;
            if(relDoc.docno != null)
                docStats = getDocTermsFromIndex(
                    relDoc.docno, analyzer, searcher);
            else
                docStats = getDocTermsFromContent(relDoc.content, analyzer);
                
            for(String term : docStats.termCounts.keySet()){

                if(!relevanceModel.containsKey(term))
                    relevanceModel.put(term, new TermStats());

                relevanceModel.get(term).addRelTermLikelihood(
                    docStats.termCounts.get(term) / (1.0*docStats.length)
                );
            }
        }

        // Extract term stats from non-relevant documents.
        for(FeedbackDocument nonRelDoc : nonRelevantDocs){
            DocStats docStats;
            if(nonRelDoc.docno != null)
                docStats = getDocTermsFromIndex(
                    nonRelDoc.docno, analyzer, searcher);
            else
                docStats = getDocTermsFromContent(nonRelDoc.content,analyzer);

            for(String term : docStats.termCounts.keySet()){
                if(!relevanceModel.containsKey(term))
                    continue;

                relevanceModel.get(term).addNonRelTermLikelihood(
                    docStats.termCounts.get(term) / (1.0*docStats.length)
                );
            }
        }

        // Normalize terms.
        queryString = new StringBuilder();

        // If we're keeping all terms, there's no reason to sort them.
        if(topTermsToKeep < 0) {
            for(String term : relevanceModel.keySet()){
                queryString.append(term).append("^").append( 
                    relevanceModel.get(term).getNormalizedProb(
                        relevantDocs.size()+1, nonRelevantDocs.size()+1)).
                        append(" ");
            }
        // Otherwise, we need to normalize and then sort.
        } else {
            int termsUsed = 0;

            // Normalize each term.
            for(String term : relevanceModel.keySet())
                finalTerms.add(new WeightedTerm(
                    term, relevanceModel.get(term).getNormalizedProb(
                        relevantDocs.size()+1, nonRelevantDocs.size()+1)));

            // Pull out the top terms and add them to the queryString.
            for(WeightedTerm term : finalTerms){
                if(termsUsed < topTermsToKeep)
                    queryString.append(term.term).append("^").
                        append(term.weight).append(" ");
                else
                    break;
                termsUsed++;
            }
        }

        return queryString.toString();
    }

    /**
     * Extracts terms and their frequency from the document identified by
     * docno via the given searcher instance. These are returned in a DocStats 
     * instance. Terms are passed through the given analyzer, which may
     * stop and stem terms.
     *
     * @return A DocStats instance containing all terms and their counts
     *         extracted from the document with the given docno.
     */
    public DocStats getDocTermsFromIndex(String docno, 
                                         Analyzer analyzer, 
                                         IndexSearcher searcher) {
        TopDocs results;
        DocStats docStats = new DocStats();

        // This query will search the docno field for an exact match.
        BooleanQuery query = (new BooleanQuery.Builder()).add(
            new TermQuery(new Term("docno", docno)),
                BooleanClause.Occur.MUST).build();

        try {
            results = searcher.search(query, 1);
            // Make sure we get a hit (there should be exactly one).
            if(results.totalHits > 0){
                Document doc = searcher.doc(results.scoreDocs[0].doc);
                TokenStream contentStream = doc.getField(
                    globalSettings.searchField).tokenStream(analyzer, null);
  
                CharTermAttribute charTermAttr =
                    contentStream.addAttribute(CharTermAttribute.class);
                contentStream.reset();
    
                // Extract the tokens.
                while(contentStream.incrementToken())
                    docStats.addTerm(charTermAttr.toString(), 1);
            
                contentStream.close();
            }
        } catch(Exception e){
            System.err.println("Error finding feedback documents:  "+ 
                e.getMessage());
        }

        return docStats;
    }


    /**
     * Extracts terms and their frequency from the given document content.
     * These are returned in a DocStats instance. Terms are passed through the 
     * given analyzer, which may stop and stem terms.
     *
     * @return A DocStats instance containing all terms and their counts
     *         extracted from the given content.
     */
    public DocStats getDocTermsFromContent(String content, Analyzer analyzer) {
        DocStats docStats = new DocStats();

        try {
            TokenStream contentStream = analyzer.tokenStream(null, content);
    
            CharTermAttribute charTermAttr =
                contentStream.addAttribute(CharTermAttribute.class);
            contentStream.reset();
    
            // Extract the tokens.
            while(contentStream.incrementToken())
                docStats.addTerm(charTermAttr.toString(), 1);

            contentStream.close();
        } catch(Exception e){
            System.err.println("Error finding feedback documents:  "+ 
                e.getMessage());
        }

        return docStats;
    }


    /**
     * This query preprocessor require changes to the settings if stopping or
     * stemming are requested.
     *
     * @return Whether the settings need to change when running the query.
     */
    public boolean modifiesSettings(){
        return !globalSettings.stopFile.equals("None") ||
               !globalSettings.stemmer.equals("None");
    }

    /**
     * @returns A deep copy of the LTRSettings with the stopper and stemmer 
     * removed.
     */
    public LTRSettings getModifiedSettings() {
        LTRSettings newSettings = globalSettings.deepCopy();
        newSettings.stopFile = "None";
        newSettings.stemmer = "None";
        return newSettings;
    }

}
