import org.apache.lucene.analysis.Analyzer;

class PorterStemAnalyzer extends Analyzer {
     @Override
     protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
	  Tokenizer source = new LowerCaseTokenizer(version, reader);
	  return new TokenStreamComponents(source, new PorterStemFilter(source));
     }
}

class SStemAnalyzer extends Analyzer {
     @Override
     protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
	  Tokenizer source = new LowerCaseTokenizer(version, reader);
	  return new TokenStreamComponents(source, new EnglishMinimalStemFilter(source));
     }
}

class KStemAnalyzer extends Analyzer {
     @Override
     protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
	  Tokenizer source = new LowerCaseTokenizer(version, reader);
	  return new TokenStreamComponents(source, new KStemFilter(source));
     }
}
