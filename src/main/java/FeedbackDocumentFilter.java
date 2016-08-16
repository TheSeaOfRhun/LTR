import java.util.HashSet;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

/**
 * Provides basic methods that all query post-processors should implement.
 * These run after a query is run. They get the global settings, query settings,
 * the query text run, and the retrieved results. Their only output is results.
 *
 * @author hafeild
 */
public class FeedbackDocumentFilter implements QueryPostProcessor {
    HashSet<String> feedbackDocnos;

    /**
     * Extracts the docno attribute from all doc elements in the feedback
     * element of the given xmlSettings. These will be filtered out from 
     * results passed to getResults(). The feedback element should be in the
     * same formas is is used in ExplicitFeedbackM1PreProcessor.
     *
     * @param xmlSettings The XML settings. Should contain a feedback element
     *                    doc subelements. Only doc elements with a docno
     *                    attribute are considered. Relevance and doc text are
     *                    ignored.
     * @param globalSettings The LTR settings used for retrieval. Not currently 
     *                       used.
     * @param queryText The text of the run query. Not currently used.
     *
     * @throws Exception if the feedback element is missing from xmlSettings.
     */
    public void initialize(String xmlSettings, LTRSettings globalSettings,
            String queryText) throws Exception {

        org.jsoup.nodes.Document soup;
        Element feedbackElm;        

        feedbackDocnos = new HashSet<String>();

        // Parse the settings.
        soup = Jsoup.parse(xmlSettings);
        feedbackElm = soup.select("feedback").first();

        // Make sure there's a feedback section.
        if(feedbackElm == null)
            throw new Exception("Query XML missing <feedback> element:\n"+
                xmlSettings);

        // Extract all the docnos.
        for(Element feedbackDocElm : feedbackElm.select("doc"))
            if(feedbackDocElm.hasAttr("docno"))
                feedbackDocnos.add(feedbackDocElm.attr("docno"));

    }

    /**
     * Filters all results with a docno field that matches one of the feedback
     * documents extracted from the xmlSettings passed to initialize().
     * 
     * @param searcher The IndexSearcher to use to get information about the
     *                 retrieved documents.
     * @param results The result set of the query.
     *
     * @throws Exception if initialize was not invoked first.
     */
    public TopDocs getResults(IndexSearcher searcher, TopDocs results) 
            throws Exception {
        ArrayList<ScoreDoc> filteredDocs = new ArrayList<ScoreDoc>();
        float maxScore = Float.MIN_VALUE;
        String docno;

        // Make sure this post processor was initialized.
        if(feedbackDocnos == null)
            throw new Exception("initialize method not called in query "+
                "post processor.");

        // Filter the results, only keeping docs with a docno that isn't part
        // of the feedback set.
        for(int i = 0; i < results.totalHits; i++) {
            docno = searcher.doc(results.scoreDocs[i].doc).get("docno");
            if(!feedbackDocnos.contains(docno)) {
                filteredDocs.add(results.scoreDocs[i]);

                // Update the max score.
                maxScore = results.scoreDocs[i].score > maxScore ?
                    results.scoreDocs[i].score : maxScore;
            }
        }

        return new TopDocs(filteredDocs.size(), 
            filteredDocs.toArray(results.scoreDocs), maxScore); 
    }
}
