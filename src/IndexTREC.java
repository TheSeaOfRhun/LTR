import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.commons.io.FileUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class IndexTREC {

    private IndexTREC() {}

    public static void main(String[] args) {
	String usage = "java -cp lib/trec.jar:bin IndexTREC"
	    + " [-index INDEX_PATH] [-docs DOCS_PATH] "
	    + " [-stop STOP_FILE] [-stem STEMMER_NAME]\n";

	String   indexPath = "index";
	String   docsPath  = null;
	String[] opt       = new String[2];
		
	for(int i=0;i<args.length;i++) {
	    if ("-index".equals(args[i])) {
		indexPath = args[i+1];
		i++;
	    } else if ("-docs".equals(args[i])) {
		docsPath = args[i+1];
		i++;
	    } else if ("-stop".equals(args[i])) {
		opt[0] = args[i+1];
		i++;
	    } else if ("-stem".equals(args[i])) {
		opt[1] = args[i+1];
		i++;
	    }
	}

	if (docsPath == null) {
	    System.err.println("Usage: " + usage);
	    System.exit(1);
	}

	final Path docDir = Paths.get(docsPath);
	if (!Files.isReadable(docDir)) {
	    System.out.println("Document directory '" +docDir.toAbsolutePath()+ "' does not exist or is not readable, please check the path");
	    System.exit(1);
	}

	try {
	    System.out.println("Indexing to directory '" + indexPath + "'...");

	    Directory dir = FSDirectory.open(Paths.get(indexPath));
	    TrecAnalyzer analyzer = new TrecAnalyzer(opt);
	    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
	    iwc.setOpenMode(OpenMode.CREATE);
	    // iwc.setRAMBufferSizeMB(256.0);
	    IndexWriter writer = new IndexWriter(dir, iwc);
	    indexDocs(writer, docDir);
	    // writer.forceMerge(1);
	    writer.close();
	} catch (IOException e) {
	    System.out.println(" caught a " + e.getClass() +
			       "\n with message: " + e.getMessage());
	}
    }

    public static EnumSet<FileVisitOption> visitor_opts = EnumSet.of(FileVisitOption.FOLLOW_LINKS);
    
    public static class DocVisitor extends SimpleFileVisitor<Path> {
	IndexWriter writer;
	DocVisitor(IndexWriter writer_) {
	    writer = writer_;
	}
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
	    throws IOException {
	    try {
		parseDoc(writer, file);
	    } catch (IOException ignore) {
		// don't index files that can't be read.
	    }
	    return FileVisitResult.CONTINUE;
	}
    }
    
    static void indexDocs(final IndexWriter writer, Path path)
	throws IOException {
	DocVisitor docVisitor = new DocVisitor(writer);
	if (Files.isDirectory(path)) {
	    Files.walkFileTree(path, visitor_opts, Integer.MAX_VALUE, docVisitor);
	} else {
	    parseDoc(writer, path);
	}
    }

    static void parseDoc(IndexWriter writer, Path file)
	throws IOException {
	org.jsoup.nodes.Document soup;
	String str, docno, txt;	
	str  = FileUtils.readFileToString(new File(file.toString()));
	soup = Jsoup.parse(str);
	for (Element elm : soup.select("DOC")) {
	    docno = "x";
	    txt   = "x";
	    // docno = elm.child(0).text().trim();
	    for (Element elm_ : elm.children()) {
		if(elm_.tagName().equals("docno")) {
		    docno = elm_.text().trim();
		    elm_.remove();
		}
	    }
	    txt = elm.text();
	    Document doc = new Document();
	    doc.add(new StringField("docno", docno, Field.Store.YES));
	    doc.add(new TextField("contents", txt, Field.Store.NO));
	    writer.addDocument(doc);
	}
    }
}
