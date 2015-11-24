import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

public class BatchSearch {

    private BatchSearch() {};

    public static void main(String[] args)
	throws Exception
    {
	String usage = "Usage:\tjava BatchSearch"
	    + " [-index dir] [-simfn similarity]"
	    + " [-field f] [-queries file]"
	    + " [-stop STOP_FILE] [-stem STEMMER_NAME]\n";
		
	if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
	    System.out.println(usage);
	    System.exit(0);
	}

	int    NUMRET    = 1000;
	String index     = "index";
	String field     = "contents";
	String queries   = null;
	String simstr    = "default";
	String[] opt     = new String[2];

	for(int i = 0; i < args.length; i++) {
	    if ("-index".equals(args[i])) {
		index = args[i+1];
		i++;
	    } else if ("-field".equals(args[i])) {
		field = args[i+1];
		i++;
	    } else if ("-queries".equals(args[i])) {
		queries = args[i+1];
		i++;
	    } else if ("-simfn".equals(args[i])) {
		simstr = args[i+1];
		i++;
	    } else if ("-stop".equals(args[i])) {
		opt[0] = args[i+1];
		i++;
	    } else if ("-stem".equals(args[i])) {
		opt[1] = args[i+1];
		i++;
	    }
	}

	HashMap<String, Object> model_map = new HashMap<String, Object>();
	String pkg = "org.apache.lucene.search.similarities.";
	model_map.put("default",
		      Class
		      .forName(pkg + "DefaultSimilarity")
		      .getConstructor()
		      .newInstance()
		      );
	model_map.put("tmpl",
		      Class
		      .forName("TMPL")
		      .getConstructor()
		      .newInstance()
		      );
	model_map.put("bm25",
		      Class
		      .forName("BM25Sim")
		      .getConstructor()
		      .newInstance()
		      );
	model_map.put("bm25L",
		      Class
		      .forName(pkg + "BM25Similarity")
		      .getConstructor()
		      .newInstance()
		      );
	model_map.put("dfr",
		      Class
		      .forName(pkg + "DFRSimilarity")
		      .getConstructor(BasicModel.class,
				      AfterEffect.class,
				      Normalization.class)
		      .newInstance(new BasicModelP(),
				   new AfterEffectL(),
				   new NormalizationH2())
		      );
	model_map.put("lm",
		      Class
		      .forName(pkg + "LMDirichletSimilarity")
		      .getConstructor()
		      .newInstance()
		      );

	Similarity simfn = (Similarity)model_map.get(simstr);

	if (simfn == null) {
	    System.out.println("Unsupported: " + simfn + "\n");
	    System.exit(0);
	}

	if (queries == null) {
	    System.out.println("Query file not specified\n");
	    System.exit(0);
	}
		
	IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
	IndexSearcher searcher = new IndexSearcher(reader);
	searcher.setSimilarity(simfn);
	TrecAnalyzer analyzer = new TrecAnalyzer(opt);
	SimpleQueryParser parser = new SimpleQueryParser(analyzer, field);
		
	org.jsoup.nodes.Document soup;
	String str, qid, txt;
	str = FileUtils.readFileToString(new File(queries));
	soup = Jsoup.parse(str);
	for (Element elm : soup.select("TOP")) {
	    qid = elm.child(0).text().trim();
	    txt = elm.child(1).text();
	    Query query = parser.parse(txt);
	    doBatchSearch(searcher, qid, query, simstr, NUMRET);
	}
	reader.close();
    }

    public static void doBatchSearch(IndexSearcher searcher,
				     String qid, Query query,
				     String runtag, int numret)	 
	throws IOException {
	TopDocs results = searcher.search(query, numret);
	ScoreDoc[] hits = results.scoreDocs;
	HashMap<String, String> seen = new HashMap<String, String>(numret);
	int numTotalHits = results.totalHits;
	int start = 0;
	int end = numTotalHits < numret ? numTotalHits : numret;

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
	}
    }
}
