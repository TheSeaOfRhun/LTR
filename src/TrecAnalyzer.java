import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilter;
import org.apache.lucene.analysis.en.KStemFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.*;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
// TODO: fix imports

class TrecAnalyzer extends Analyzer {

    String stop = null;
    String stemmer = null;
    CharArraySet stopwords = null;
    
    public NewAnalyzer(String[] opt) {
	super();
	if (!opt[0].equals("None")) {
	    Scanner s = new Scanner(new File(opt[0]));
	    ArrayList<String> list = new ArrayList<String>();
	    while (s.hasNext())
		list.add(s.next());
	    s.close();
	    stopwords = StopFilter.makeStopSet(list);
        }
	    
	if (!opt[1].equals("None"))
	    stemmer = opt[1];
	   
    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
	Tokenizer source = new WhitespaceTokenizer(reader);
	Tokenstream filter = new LowerCaseFilter(source); // all the stemmers need lower case tokens

	if (stopwords)
	    filter = new StopFilter(filter, stopwords);
	
	if (stemmer.equals("porter"))
	    filter = new PorterStemFilter(filter);
	else if (stemmer.equals("krovetz"))
	    filter = new KStemFilter(filter);
	else if (stemmer.equals("snowball"))
	    filter = new SnowballFilter(filter);
	else if (stemmer.equals("sstemmer"))
	    filter = new EnglishMinimalStemFilter(filter);

	return new TokenStreamComponents(source, filter);
    }
}

/*
tokenizers:
LetterTokenizer
WhitespaceTokeniser

filters:

 stemmers:
 PorterStemFilter
 SnowballFilter
 EnglishMinimalStemFilter
 KStemFilter

 LowerCaseFilter
 StopFilter
*/

/*
Analyzer analyzer = new Analyzer() {
  @Override
      protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
      Tokenizer source = new FooTokenizer(reader);
      TokenStream filter = new FooFilter(source);
      filter = new BarFilter(filter);
      return new TokenStreamComponents(source, filter);
  }
    };
*/