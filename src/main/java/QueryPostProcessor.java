import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;

/**
 * Provides basic methods that all query post-processors should implement.
 * These run after a query is run. They get the global settings, query settings,
 * the query text run, and the retrieved results. Their only output is results.
 *
 * @author hafeild
 */
public interface QueryPostProcessor {

    public void initialize(String xmlSettings, LTRSettings globalSettings,
        String queryText) throws Exception;

    public TopDocs getResults(IndexSearcher searcher, TopDocs results)
        throws Exception;
}
