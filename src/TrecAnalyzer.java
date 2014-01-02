import java.io.Reader;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.ArrayList;

import org.apache.lucene.util.Version;
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

class TrecAnalyzer extends Analyzer {

     String stop = null;
     String stemmer = null;
     CharArraySet stopwords = null;
     Version v = Version.LUCENE_46;
    
     public TrecAnalyzer(Version v_, String[] opt) {
	  super();
	  v = v_;
	  if (!opt[0].equals("None")) {
	       try {
		    Scanner s = new Scanner(new File(opt[0]));
		    ArrayList<String> list = new ArrayList<String>();
		    while (s.hasNext())
			 list.add(s.next());
		    s.close();
		    stopwords = StopFilter.makeStopSet(v, list);
	       } catch (IOException e) {
		    System.out.println(" caught a " + e.getClass() +
				       "\n with message: " + e.getMessage());
	       }
	  }
	    
	  if (!opt[1].equals("None"))
	       stemmer = opt[1];
     }
	   
    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
	 Tokenizer source = new WhitespaceTokenizer(v, reader);
	 TokenStream filter = new LowerCaseFilter(v, source); // all the stemmers need lower case tokens

	 if (stopwords != null)
	      filter = new StopFilter(v, filter, stopwords);
	
	 if (stemmer.equals("porter"))
	      filter = new PorterStemFilter(filter);
	 else if (stemmer.equals("krovetz"))
	      filter = new KStemFilter(filter);
	 else if (stemmer.equals("snowball"))
	      filter = new SnowballFilter(filter, "English");
	 else if (stemmer.equals("sstemmer"))
	      filter = new EnglishMinimalStemFilter(filter);

	 return new TokenStreamComponents(source, filter);
    }
}
