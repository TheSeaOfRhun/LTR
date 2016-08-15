import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

// For highlighting.
import java.lang.StringBuilder;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.search.highlight.TokenSources;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.TextFragment;
import org.apache.lucene.search.highlight.QueryScorer;

public class BatchSearch
{
    public static final int FRAGMENT_SIZE_CHARS = 15;
    private BatchSearch() {};

    public static void main(String[] args)
        throws Exception
    {
        LTRSettings ltrSettings = null;
        String usage = "Usage:\tjava BatchSearch"
            + "[-settings SETTINGS_FILE] [-index dir]\n"
            + "\t[-similarity similarity] [-field f] [-queries file]\n"
            + "\t[-stop STOP_FILE] [-stem STEMMER_NAME]\n";
                
        if (args.length == 0 || (args.length > 0 && 
                ("-h".equals(args[0]) || "-help".equals(args[0])))) {
            System.err.println(usage);
            System.exit(0);
        }

 
        // Make two passes through the arguments; the first time, look only for
        // a settings file. All command line settings will then override the
        // values in this file if it exists.
        for(int i=0;i<args.length;i++){
            if ("-settings".equals(args[i])) {
                try {
                    ltrSettings = LTRSettings.generateFromFile(args[i+1]);
                } catch (FileNotFoundException e) {
                    System.err.println("LTR settings file not found ("+
                        args[i+1] +").");
                    System.exit(1);
                } catch (IOException e) {
                    System.err.println("Error reading file ("+
                        args[i+1] +"): "+ e); 
                    System.exit(1);
                }   
                continue;
            }   
        }   
 
        // Use default settings if a settings file wasn't provided.
        if(ltrSettings == null)
            ltrSettings = new LTRSettings();

        // Read in command line settings -- these will override the settings
        // file entries.
        ltrSettings.parseCommandLineArguments(args);

        if (ltrSettings.similarity == null) {
            System.out.println("BatchSearch: Similarity not specified\n");
            System.exit(0);
        }

        if (ltrSettings.queryFile == null) {
            System.out.println("BatchSearch: Query file not specified\n");
            System.exit(0);
        }

        try {
            processQueryFile(ltrSettings);
        } catch(Exception e){
            System.out.println("BatchSearch error: "+ e.getMessage() +"\n");
            e.printStackTrace();
            System.exit(0);
        }
    }


    /**
     * Concatenates the values across all instances of a field within the
     * given document. E.g., if the field name is "a", the value of all "a"
     * fields within the document will be concatenated into one string and 
     * returned.
     *
     * @param doc The document to extract field values from.
     * @param fieldName The field to extract.
     * @return A concatenation of the field values.
     */
    public static String concatenateFieldValues(Document doc, String fieldName){
        StringBuilder concatenatedValue = new StringBuilder();
        String[] fieldValues = doc.getValues(fieldName);

        // A little shortcut in the event that this field only occurs once.
        if(fieldValues.length == 1)
            return fieldValues[0];

        // In all other cases, perform the concatenation.
        for(int i = 0; i < fieldValues.length; i++){
            concatenatedValue.append(fieldValues[i]);
            if(i < fieldValues.length-1)
                concatenatedValue.append(" ");
        }
        return concatenatedValue.toString();
    }


    /**
     * Creates a Similarity instance from the given model string. It is assumed
     * that model corresponds to a Similarity implementation in the
     * org.apache.lucene.search.similarities package, or that it is fully
     * qualified.
     *
     * @param model Either a fully qualified Similarity implementation or the 
     *              name of a Similarity implementation in the
     *              org.apache.lucene.search.similarities package.
     * @return An instance of the specified model.
     */
    public static Similarity getSimilarityModel(String model)
    throws Exception {
        String pkg = "org.apache.lucene.search.similarities.";
        Similarity similarity = null;

        try {
            if (model.equals("DFRSimilarity")) {
                similarity = (Similarity)Class
                    .forName(pkg + model)
                    .getConstructor(BasicModel.class,
                                    AfterEffect.class,
                                    Normalization.class)
                    .newInstance(new BasicModelP(),
                                 new AfterEffectL(),
                                 new NormalizationH2());
            }
            else if (model.equals("IBSimilarity")) {
                similarity = (Similarity)Class
                    .forName(pkg + model)
                    .getConstructor(Distribution.class,
                                    Lambda.class,
                                    Normalization.class)
                    .newInstance(new DistributionSPL(),
                                 new LambdaDF(),
                                 new NormalizationH2());
            }
            else if (model.endsWith("Similarity")) {
                similarity = (Similarity)Class
                    .forName(pkg + model)
                    .getConstructor()
                    .newInstance();
            }
            else {
                similarity = (Similarity)Class
                    .forName(model)
                    .getConstructor()
                    .newInstance();
            }
        } catch(Exception e) {
            throw new Exception("Similarity class not found: "+ model);
        }

        return similarity;
    }

    /**
     * Extracts queries from the query file specified in the given settings.
     * Each query is run through the specified pre-processors if supplied,
     * then to doBatchSearch.
     *
     * @param settings The settings to use.
     */
    public static void processQueryFile(LTRSettings ltrSettings)
    throws Exception {
        LTRSettings modifiedSettings;
        Query query;
        String str, qid, queryText;
        QueryPreProcessor preProcessor;
        Element preProcessorElm;
        TrecAnalyzer analyzer;
        SimpleQueryParser parser;

        Similarity similarity = getSimilarityModel(ltrSettings.similarity);
                
        IndexReader reader = DirectoryReader.open(FSDirectory.open(
            Paths.get(ltrSettings.indexPath)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);

        // The analyzer and query parser may change based on the query
        // preprocessor.
        TrecAnalyzer originalAnalyzer = new TrecAnalyzer(ltrSettings);
        SimpleQueryParser originalParser = new SimpleQueryParser(
            originalAnalyzer, ltrSettings.searchField);
                

        org.jsoup.nodes.Document soup;
        str = FileUtils.readFileToString(new File(ltrSettings.queryFile));
        soup = Jsoup.parse(str);
        for (Element elm : soup.select("top")) {
            qid = elm.select("num").first().text().trim();

            analyzer = originalAnalyzer;
            parser = originalParser;

            // Check if there is any preprocessing that needs to happen.
            preProcessorElm = elm.select("preprocessor").first();
            if(preProcessorElm != null && preProcessorElm.hasAttr("class")){
                try{
                    preProcessor =  (QueryPreProcessor) Class
                        .forName(preProcessorElm.attr("class"))
                        .getConstructor()
                        .newInstance();
                } catch(Exception e){
                    throw new Exception("Could not find query preprocessor "+
                        "class: "+ preProcessorElm.attr("class"));
                }

                preProcessor.initialize(preProcessorElm.html(), ltrSettings);
                queryText = preProcessor.getQuery();
                if(preProcessor.modifiesSettings()){
                    modifiedSettings = preProcessor.getModifiedSettings();
                    analyzer = new TrecAnalyzer(modifiedSettings);
                    parser = new SimpleQueryParser(analyzer, 
                        modifiedSettings.searchField);
                }
            } else {
                queryText = elm.select("text").first().text();
            }

            query = parser.parse(queryText);
            doBatchSearch(ltrSettings, searcher, qid, query, 
                ltrSettings.similarity, analyzer);
        }
        reader.close();       
    }

    
    /**
     * Runs a query and displays the restults in TREC format.
     *
     * @param settings The settings to use (e.g., specifying how many results
     *                 to display, whether to include snippets, etc.).
     * @param searcher The Lucene IndexSearcher to issue the query to.
     * @param qid The query id to include in the results output.
     * @param query The query to issue.
     * @param runtag The tag to include in the results output.
     * @param analyzer The Lucene Analyzer to use in processing the query.
     */
    public static void doBatchSearch(LTRSettings settings,
                                     IndexSearcher searcher,
                                     String qid, Query query,
                                     String runtag, Analyzer analyzer)
    throws IOException {
        TopDocs results = searcher.search(query, settings.returnedResultCount);
        ScoreDoc[] hits = results.scoreDocs;
        HashMap<String, String> seen = 
            new HashMap<String, String>(settings.returnedResultCount);
        int numTotalHits = results.totalHits;
        int start = 0;
        int end = numTotalHits < settings.returnedResultCount ? 
            numTotalHits : settings.returnedResultCount;

        for (int i = start; i < end; i++) {
            Document doc = searcher.doc(hits[i].doc);
            String docno = doc.get("docno");
            // There are duplicate document numbers in the
            // FR collection, so only output a given docno
            // once.
            if (seen.containsKey(docno))
                continue;
            seen.put(docno, docno);
            System.out.println(qid + " " + "Q0" + " " + docno
                                   + " " + i    + " " + hits[i].score
                                   + " " + runtag);

            // Extract and display a snippet for each result if asked to
            // do so.
            if(settings.includeSnippets){
                try {
                    String textToHighlight = 
                        concatenateFieldValues(doc, settings.searchField);
                    SimpleHTMLFormatter htmlFormatter = 
                        new SimpleHTMLFormatter();
                    Highlighter highlighter = new Highlighter(htmlFormatter, 
                        new QueryScorer(query));
                    highlighter.setTextFragmenter(new SimpleFragmenter(
                        FRAGMENT_SIZE_CHARS));

                    TokenStream tokenStream = TokenSources.getTokenStream(
                        settings.searchField, null, textToHighlight,
                        analyzer, -1);

                    String fragment = highlighter.getBestFragments(
                        tokenStream,
                        textToHighlight,
                        settings.maxSnippetFragments, "...") +"...";
                    if(!Character.isUpperCase(fragment.charAt(0)))
                        fragment = "..."+ fragment;
                    System.out.println(fragment);
                    
                } catch(Exception e) {
                    System.err.println("Problem extracting snippet: "+
                        e.getMessage());
                }
            }
        }
    }


}
