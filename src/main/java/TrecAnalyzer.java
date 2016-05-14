import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Scanner;
import java.util.ArrayList;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.core.*;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;

public class TrecAnalyzer extends Analyzer
{
    String       stemmer   = null;
    CharArraySet stopwords = null;
    
    public TrecAnalyzer(LTRSettings settings)
    {
        super();

        if (settings.stopFile.equals("None")) {
            try {
                Scanner s = new Scanner(new File(settings.stopFile));
                ArrayList<String> list = new ArrayList<String>();
                while (s.hasNext())
                    list.add(s.next());
                s.close();
                stopwords = StopFilter.makeStopSet(list);
            } catch (IOException e) {
                System.out.println(" caught a " + e.getClass() + 
                                   "\n with message: " + e.getMessage());
            }
        }
            
        if (!settings.stemmer.equals("None"))
            stemmer = settings.stemmer;
    }
    
    @Override
    protected TokenStreamComponents createComponents(String fieldName)
    {
        Tokenizer   source = new WhitespaceTokenizer();
        TokenStream filter = new LowerCaseFilter(source); 
        // all the stemmers need lower case tokens
        String      pkg    = "org.apache.lucene.analysis.en.";
         
        if (stopwords != null)
            filter = new StopFilter(filter, stopwords);

        if (stemmer != null) {
            try {
                if (stemmer.equals("SnowballFilter")) {
                    filter = (TokenStream)Class
                        .forName(pkg + stemmer)
                        .getConstructor(TokenStream.class, String.class)
                        .newInstance(filter, "English");
                }
                else {
                    filter = (TokenStream)Class
                        .forName(pkg + stemmer)
                        .getConstructor(TokenStream.class)
                        .newInstance(filter);
                }
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return new TokenStreamComponents(source, filter);
    }
}
