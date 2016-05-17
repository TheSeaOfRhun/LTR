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

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.lang.StringBuilder;

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
     * @param settings The global settings (used by some parsers to determine
     *                 fields to index, etc.)
     * @param writer The index to write extracted documents to.
     * @param file The file to process.
     * @param extension The extension of the file.
     */
    public static void processFile(LTRSettings settings, IndexWriter writer, 
        File file)
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
                parseWARCFile(settings, writer, inputStream);
                break;
            default:
                System.err.println("Found TREC document!");
                parseTRECFile(settings, writer, inputStream);
        } 

        inputStream.close();
    } 
  
    /**
     * Parses a TREC styled file (with &lt;DOC&gt; tags) and adds each document
     * to the given index.
     *
     * @param settings The global settings.
     * @param writer The index to write the document to.
     * @param input  The input stream to parse.
     */
    public static void parseTRECFile(LTRSettings settings, IndexWriter writer,
        InputStream input) 
    throws IOException {
        org.jsoup.nodes.Document soup;
        String docno, txt;        
        Field.Store storeField = Field.Store.NO;
        boolean addContentsField = settings.trecFieldsToIndex.size() == 0;
        StringBuilder documentContent = null;
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(input));
        String line;

        // Determine whether non-id fields should be stored.
        if(settings.storeFields)
            storeField = Field.Store.YES;

        while((line = reader.readLine()) != null){
            // Found the start of a new document.
            if(line.equals("<DOC>") && documentContent == null ){
                documentContent = new StringBuilder();
                documentContent.append(line+"\n");

            // Found the end of the current document.
            } else if(line.equals("</DOC>") && documentContent != null) {
                documentContent.append(line);
                soup = Jsoup.parse(documentContent.toString());
                docno = soup.getElementsByTag("DOCNO").first().text().trim();
                Document doc = new Document();
                doc.add(new StringField("docno", docno, Field.Store.YES));

                // Get all of the requested fields.
                for(String field : settings.trecFieldsToIndex)
                    if(field == "contents")
                        addContentsField = true;
                    else
                        for(Element elm : soup.getElementsByTag(field))
                            doc.add(new TextField(field, elm.text(), 
                                storeField));
    
                // If no field is specified, index the whole thing.
                if(addContentsField)
                    doc.add(new TextField("contents", 
                        documentContent.toString(), storeField));
    
                writer.addDocument(doc);
                documentContent = null;

            // Found the next line of the document.
            } else if(documentContent != null) {
                documentContent.append(line+"\n");
            }
        }
    }

    /**
     * Parses a WARC formatted file and adds each document to the given index.
     * This code is loosely based on lemur.nopol.ResponseIterator
     * (see https://github.com/lemurproject/nopol). The following settings
     * may be set in the LTRSettings instance passed in:
     *
     *  warcFieldsToIndex -- a list of field names.
     *  includeSnippets   -- if true, then all fields will be stored in addition
     *                       to being indexed; if false, only the docno field
     *                       is stored.
     *
     * @param settings The global settings. This includes what fields should
     *                 be indexed and stored.
     * @param writer The index to write the document to.
     * @param input  The input stream to parse.
     */
    public static void parseWARCFile(LTRSettings settings, IndexWriter writer, 
        InputStream input) 
    throws IOException {
        // WarcReader will iterate through each WARC document in the given
        // input stream in a streaming fashion, so we don't have to worry
        // about loading the whole thing in memory at once.
        WarcReader warcReader = WarcReaderFactory.getReader(input);
        Iterator<WarcRecord> records = warcReader.iterator();
        WarcRecord record;
        HeaderLine typeHeader, trecIDHeader;
        Document doc;
        Field.Store storeField = Field.Store.NO;
        org.jsoup.nodes.Document soup;
        boolean addContentsField = settings.warcFieldsToIndex.size() == 0;

        // Determine whether non-id fields should be stored.
        if(settings.storeFields)
            storeField = Field.Store.YES;
        
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

            // Process the document content. This allows us to extract fields
            // and get rid of things like JavaScript.
            soup = Jsoup.parse(IOUtils.toString(
                record.getPayloadContent(), (String) null));

            // Get all of the requested fields.
            for(String field : settings.warcFieldsToIndex)
                if(field == "contents")
                    addContentsField = true;
                else
                    for(Element elm : soup.getElementsByTag(field))
                        doc.add(new TextField(field, elm.text(), storeField));


            // If no field is specified, index the whole thing.
            if(addContentsField)
                doc.add(new TextField("contents", soup.outerHtml(), 
                    storeField));

            writer.addDocument(doc);
        }
    }

}
