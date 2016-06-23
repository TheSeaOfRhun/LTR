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
            System.out.println(usage);
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
        for(int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                ltrSettings.indexPath = args[i+1];
                i++;
            } else if ("-field".equals(args[i])) {
                ltrSettings.searchField = args[i+1];
                i++;
            } else if ("-queries".equals(args[i])) {
                ltrSettings.queryFile = args[i+1];
                i++;
            } else if ("-similarity".equals(args[i])) {
                ltrSettings.similarity = args[i+1];
                i++;
            } else if ("-stop".equals(args[i])) {
                ltrSettings.stopFile = args[i+1];
                i++;
            } else if ("-stem".equals(args[i])) {
                ltrSettings.stemmer = args[i+1];
                i++;
            }
        }

        if (ltrSettings.similarity == null) {
            System.out.println("BatchSearch: Similarity not specified\n");
            System.exit(0);
        }

        if (ltrSettings.queryFile == null) {
            System.out.println("BatchSearch: Query file not specified\n");
            System.exit(0);
        }

        String pkg = "org.apache.lucene.search.similarities.";
        Similarity similarity = null;

        if (ltrSettings.similarity.equals("DFRSimilarity")) {
            similarity = (Similarity)Class
                .forName(pkg + ltrSettings.similarity)
                .getConstructor(BasicModel.class,
                                AfterEffect.class,
                                Normalization.class)
                .newInstance(new BasicModelP(),
                             new AfterEffectL(),
                             new NormalizationH2());
        }
        else if (ltrSettings.similarity.equals("IBSimilarity")) {
            similarity = (Similarity)Class
                .forName(pkg + ltrSettings.similarity)
                .getConstructor(Distribution.class,
                                Lambda.class,
                                Normalization.class)
                .newInstance(new DistributionSPL(),
                             new LambdaDF(),
                             new NormalizationH2());
        }
        else if (ltrSettings.similarity.endsWith("Similarity")) {
            similarity = (Similarity)Class
                .forName(pkg + ltrSettings.similarity)
                .getConstructor()
                .newInstance();
        }
        else {
            similarity = (Similarity)Class
                .forName(ltrSettings.similarity)
                .getConstructor()
                .newInstance();
        }

        if (similarity == null) {
            System.out.println("BatchSearch: Unsupported similarity class: "+ 
            similarity +"\n");
            System.exit(0);
        }
                
        IndexReader reader = DirectoryReader.open(FSDirectory.open(
            Paths.get(ltrSettings.indexPath)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        TrecAnalyzer analyzer = new TrecAnalyzer(ltrSettings);
        SimpleQueryParser parser = new SimpleQueryParser(analyzer, 
            ltrSettings.searchField);
                
        org.jsoup.nodes.Document soup;
        String str, qid, txt;
        str = FileUtils.readFileToString(new File(ltrSettings.queryFile));
        soup = Jsoup.parse(str);
        for (Element elm : soup.select("TOP")) {
            qid = elm.child(0).text().trim();
            txt = elm.child(1).text();
            Query query = parser.parse(txt);
            doBatchSearch(ltrSettings, searcher, qid, query, 
                ltrSettings.similarity, analyzer);
        }
        reader.close();
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
