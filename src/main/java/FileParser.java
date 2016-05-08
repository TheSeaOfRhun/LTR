import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.util.zip.GZIPInputStream;
import java.util.Iterator;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;

// For classic TREC format processing.
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

// For WARC processing.
import org.jwat.warc.WarcRecord;
import org.jwat.warc.WarcReader;
import org.jwat.warc.WarcReaderFactory;
import org.jwat.common.HeaderLine;

/**
 * Handles parsing various TREC document formats. Currently supported formats
 * include:
 *
 *   - WARC
 *   - classic TREC (e.g., from TREC Vol 1)
 *
 * It is safe for files to be gzipped (*.gz or *.gzip) or bzipped (*.bz2 or 
 * *.bzip2)
 *
 * @author hafeild
 */
public class FileParser {
 
    /**
     * Strips the given extension from the filename. If the extension is not
     * at the end of the filename, the filename is unchanged.
     *
     * @param filename The name of the file.
     * @param extension The extension to remove. 
     * @return The filename with the given extension removed.
     */
    public static String removeExtension(String filename, String extension) {
        if(filename.endsWith(extension))
            return filename.substring(0, filename.length()-extension.length());
        return filename;
    }

    /**
     * Processes the content of a file. It is parsed based on the file's
     * extension:
     *
     *  Compressed formats:
     *      - .gzip / .gz
     *      - .bzip2 / .bz2
     *  Document formats:
     *      - .warc: WARC
     *      - anything else: TREC
     *
     * @param writer The index to write extracted documents to.
     * @param file The file to process.
     * @param extension The extension of the file.
     */
    public static void processFile(IndexWriter writer, File file)
    throws IOException {
        String extension = FilenameUtils.getExtension(file.getName());
        InputStream inputStream = FileUtils.openInputStream(file);
        boolean removePrevExtension = false;

        // Check for compression extensions.
        switch(extension){
            case "gz":
            case "gzip":
                // Handle gzip files.
                inputStream = new GZIPInputStream(inputStream);
                removePrevExtension = true;
                break;
            case "bzip2":
            case "bz2":
                // Handle bzip2 files...
                inputStream = new BZip2CompressorInputStream(inputStream);
                removePrevExtension = true;
                break;
        }

        // Get rid of any compression extensions and get the next extension.
        if(removePrevExtension)
            extension = FilenameUtils.getExtension(
                removeExtension(file.getName(), "."+extension));

        // Check
        switch(extension){
            case "warc":
                System.err.println("Found WARC document!");
                parseWARCFile(writer, inputStream);
                break;
            default:
                System.err.println("Found TREC document!");
                parseTRECFile(writer, inputStream);
        } 

        inputStream.close();
    } 


    /**
     * Parses a TREC styled file (with &lt;DOC&gt; tags) and adds each document
     * to the given index.
     *
     * @param writer The index to write the document to.
     * @param input  The input stream to parse.
     */
    public static void parseTRECFile(IndexWriter writer, InputStream input) 
    throws IOException {
        org.jsoup.nodes.Document soup;
        String docno, txt;        
        soup = Jsoup.parse(input, null, "");
        for (Element elm : soup.select("DOC")) {
            docno = elm.child(0).text().trim();
            txt   = elm.text();
            Document doc = new Document();
            doc.add(new StringField("docno", docno, Field.Store.YES));
            doc.add(new TextField("contents", txt, Field.Store.NO));
            writer.addDocument(doc);
        }
    }

    /**
     * Parses a WARC formatted file and adds each document to the given index.
     * This code is loosely based on lemur.nopol.ResponseIterator
     * (see https://github.com/lemurproject/nopol).
     *
     * @param writer The index to write the document to.
     * @param input  The input stream to parse.
     */
    public static void parseWARCFile(IndexWriter writer, InputStream input) 
    throws IOException {
        // WarcReader will iterate through each WARC document in the given
        // input stream in a streaming fashion, so we don't have to worry
        // about loading the whole thing in memory at once.
        WarcReader warcReader = WarcReaderFactory.getReader(input);
        Iterator<WarcRecord> records = warcReader.iterator();
        WarcRecord record;
        HeaderLine typeHeader, trecIDHeader;
        Document doc;

        while(records.hasNext()){
            record = records.next();
            typeHeader = record.getHeader("WARC-TYPE");
            trecIDHeader = record.getHeader("WARC-TREC-ID");

            // Skip non-response entries or entries without headers.
            if(typeHeader == null || !typeHeader.value.equals("response"))
                continue;

            // Skip entries that do not have a "WARC-TREC-ID" field in the
            // header.
            if(trecIDHeader == null)
                continue;

            doc = new Document();
            doc.add(new StringField("docno", trecIDHeader.value, 
                Field.Store.YES));
            doc.add(new TextField("contents", IOUtils.toString(
                    record.getPayloadContent(), (String) null), Field.Store.NO));
            writer.addDocument(doc);
        }
    }

}
