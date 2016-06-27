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
import java.io.FileNotFoundException;
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
        String usage = "java -cp lib/trec.jar:bin IndexTREC\n"
            + "\t[-settings SETTINGS_FILE] [-index INDEX_PATH]\n"
            + "\t[-docs DOCS_PATH] [-stop STOP_FILE] [-stem STEMMER_NAME] ]\n"
            + "\nCommand line options will override values in SETTINGS_FILE\n"
            + "if a settings file is provided. Use 'None' in place of "
            + "STOP_FILE\nor STEMMER_NAME to use no stoplist or stemmer (this "
            + "is the default).\n";

        LTRSettings ltrSettings = null;
        //String   indexPath = "index";
        //String   docsPath  = null;
        //String[] opt       = new String[2];

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
     
        // Second pass: override defaults/file settings with command line 
        // settings.
        ltrSettings.parseCommandLineArguments(args); 

        if (ltrSettings.docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(ltrSettings.docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" +docDir.toAbsolutePath()+ 
                "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        try {
            System.out.println("Indexing to directory '"+ 
                ltrSettings.indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(ltrSettings.indexPath));
            TrecAnalyzer analyzer = new TrecAnalyzer(ltrSettings);
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(OpenMode.CREATE);
            // iwc.setRAMBufferSizeMB(256.0);
            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(ltrSettings, writer, docDir);
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
        LTRSettings ltrSettings;

        DocVisitor(LTRSettings ltrSettings, IndexWriter writer) {
            this.writer = writer;
            this.ltrSettings = ltrSettings;
        }
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
            throws IOException {
            try {
                FileParser.processFile(
                    ltrSettings, writer, new File(file.toString()));
            } catch (IOException ignore) {
                // don't index files that can't be read.
            }
            return FileVisitResult.CONTINUE;
        }
    }
    
    static void indexDocs(LTRSettings ltrSettings, final IndexWriter writer, 
        Path path)
    throws IOException {
        DocVisitor docVisitor = new DocVisitor(ltrSettings, writer);
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, visitor_opts, Integer.MAX_VALUE, docVisitor);
        } else {
            FileParser.processFile(
                ltrSettings, writer, new File(path.toString()));
        }
    }
}
