/**
 * Provides basic methods that all query pre-processors should implement.
 * These run before a query is run and only shape the query itself, not
 * the model used to execute the query.
 *
 * @author hafeild
 */
public interface QueryPreProcessor {

    public void initialize(String xmlSettings, LTRSettings globalSettings) 
        throws Exception;

    public String getQuery();
}
