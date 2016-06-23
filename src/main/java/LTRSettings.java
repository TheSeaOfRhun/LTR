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

    //@override
    public String toString(){
        return (new Gson()).toJson(this);
    }
}
