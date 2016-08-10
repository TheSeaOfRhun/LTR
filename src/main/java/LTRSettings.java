import com.google.gson.Gson;
import org.hjson.JsonValue; 
import java.util.ArrayList;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Stores settings for use across LTR components.
 *
 * @author hafeild
 */
public class LTRSettings {
    public static final int     DEFAULT_RESULT_COUNT     = 1000;
    public static final int     DEFAULT_SNIPPET_FRAGS    = 4;
    public static final boolean DEFAULT_STORE_FIELDS     = false;
    public static final boolean DEFAULT_INCLUDE_SNIPPETS = false;
    public static final String  DEFAULT_INDEX_PATH       = "index";
    public static final String  DEFAULT_DOCS_PATH        = null;
    public static final String  DEFAULT_TOKENIZER        ="WhitespaceTokenizer";
    public static final String  DEFAULT_CUSTOM_TOKENIZER = null;
    public static final String  DEFAULT_STOP_FILE        = "None";
    public static final String  DEFAULT_STEMMER          = "None";
    public static final String  DEFAULT_QUERY_FILE       = null;
    public static final String  DEFAULT_SIMILARITY       = null;
    public static final String  DEFAULT_SEARCH_FIELD     = "contents";
    public static final String  DEFAULT_PARSER           = "auto";

    public ArrayList<String> warcFieldsToIndex;
    public ArrayList<String> trecFieldsToIndex;
    public int      returnedResultCount;
    public int      maxSnippetFragments;
    public boolean  storeFields;
    public boolean  includeSnippets;
    public String   indexPath;
    public String   docsPath;
    public String   tokenizer;
    public String   customTokenizer;
    public String   stopFile;
    public String   stemmer;
    public String   similarity;
    public String   queryFile;
    public String   searchField; 
    public String   parser;

    /**
     * Creates an LTRSettings file from an HJSON file.
     *
     * @param filename The name of the HJSON file to process.
     * @return An LTRSettings instance created using the settings in filename.
     */
    public static LTRSettings generateFromFile(String filename)
    throws FileNotFoundException, IOException {
        Gson gson = new Gson();
        return gson.fromJson(
            JsonValue.readHjson(new FileReader(filename)).toString(),
            LTRSettings.class);
    }

    /**
     * Default constructor. Sets all public fields to their default values.
     */
    public LTRSettings(){
        indexPath           = DEFAULT_INDEX_PATH;
        docsPath            = DEFAULT_DOCS_PATH;
        tokenizer           = DEFAULT_TOKENIZER;
        customTokenizer     = DEFAULT_CUSTOM_TOKENIZER;
        stemmer             = DEFAULT_STEMMER;
        stopFile            = DEFAULT_STOP_FILE;
        returnedResultCount = DEFAULT_RESULT_COUNT;
        warcFieldsToIndex   = new ArrayList<String>();
        trecFieldsToIndex   = new ArrayList<String>();
        includeSnippets     = DEFAULT_INCLUDE_SNIPPETS;
        storeFields         = DEFAULT_STORE_FIELDS;
        similarity          = DEFAULT_SIMILARITY;
        queryFile           = DEFAULT_QUERY_FILE;
        searchField         = DEFAULT_SEARCH_FIELD;
        maxSnippetFragments = DEFAULT_SNIPPET_FRAGS;
        parser              = DEFAULT_PARSER;
    }

    /**
     * Extracts settings from command line arguments.
     *
     * @param args A list of command line arguments to parse. 
     */
    public void parseCommandLineArguments(String[] args){
        for(int i = 0; i < args.length; i+=2) {
            if ("-index".equals(args[i]) || "-indexPath".equals(args[i]))
                indexPath = args[i+1];
            else if ("-docs".equals(args[i]) || "-docsPath".equals(args[i]))
                docsPath = args[i+1];
            else if ("-stop".equals(args[i]) || "-stopFile".equals(args[i]))
                stopFile = args[i+1];
            else if ("-stem".equals(args[i]) || "-stemmer".equals(args[i])) 
                stemmer = args[i+1];
            else if ("-field".equals(args[i]) || "-searchField".equals(args[i])) 
                searchField = args[i+1];
            else if ("-queries".equals(args[i]) || "-queryFile".equals(args[i])) 
                queryFile = args[i+1];
            else if ("-similarity".equals(args[i])) 
                similarity = args[i+1];
            else if ("-tokenizer".equals(args[i])) 
                tokenizer = args[i+1];
            else if ("-customTokenizer".equals(args[i])) 
                customTokenizer = args[i+1];
            else if ("-returnedResultCount".equals(args[i])) 
                returnedResultCount = Integer.parseInt(args[i+1]);
            else if ("-parser".equals(args[i])) 
                parser = args[i+1];
            else if ("-includeSnippets".equals(args[i])) 
                includeSnippets = "true".equals(args[i+1]);
            else if ("-storeFields".equals(args[i])) 
                storeFields = "true".equals(args[i+1]);
            else if ("-maxSnippetFragments".equals(args[i])) 
                maxSnippetFragments = Integer.parseInt(args[i+1]);
            else if ("-warcFieldsToIndex".equals(args[i])) 
                warcFieldsToIndex = csvToArrayList(args[i+1]);
            else if ("-trecFieldsToIndex".equals(args[i])) 
                trecFieldsToIndex = csvToArrayList(args[i+1]);
            else
                i--; 
        } 
    }

    public static ArrayList<String> csvToArrayList(String csvString){
        ArrayList<String> list = new ArrayList<String>();
        String[] columns = csvString.split(",");
        for(int i = 0; i < columns.length; i++)
            list.add(columns[i]);
        return list;
    }

    //@override
    public String toString(){
        return (new Gson()).toJson(this);
    }
}
