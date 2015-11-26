import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;
import java.io.IOException;
import java.util.Collections;

public class BM25 extends Similarity {

    private final float k1;
    private final float b;
    private static final float[] NORM = new float[256];    
    static {
	for (int i = 0; i < 256; i++) {
	    NORM[i] = SmallFloat.byte315ToFloat((byte)i);
	}
    }
    
    public BM25()
    {
	this.k1 = 1.2f;
	this.b  = 0.75f;
    }

    public BM25(float k1, float b)
    {
	this.k1 = k1;
	this.b  = b;
    }

    public double log(double x)
    {
	return Math.log(x) / Math.log(2.0D);
    }

    public float coord(int overlap, int maxOverlap)
    {
	return 1f;
    }

    public float queryNorm(float valueForNormalization)
    {
	return 1f;
    }

    @Override
    public final SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats,
					 TermStatistics... termStats)
    {
	float N, n, idf, adl, dl;

	idf = 1.0f;
	
	N = collectionStats.maxDoc();

	if (termStats.length == 1) {
	    n = termStats[0].docFreq();
	    idf = (float)log(1.0D + (N - n + 0.5D) / (n + 0.5D));
	}
	else {
	    for (final TermStatistics stat : termStats) {
		n = stat.docFreq();
		idf += (float)log(1.0D + (N - n + 0.5D) / (n + 0.5D));
	    }
	}
	
	adl = collectionStats.sumTotalTermFreq() / N;

	float K[] = new float[256];
	for (int i = 0; i < K.length; i++) {
	    dl = decodeNorm((byte)i);
	    K[i] = k1 * (1- b + b * (dl / adl));
	}

	return new BM25Weight(collectionStats.field(), idf, adl, K);
    }

    @Override
    public final SimScorer simScorer(SimWeight sw, LeafReaderContext context)
	throws IOException
    {
	BM25Weight bw = (BM25Weight) sw;
	return new BM25Scorer(bw, context.reader().getNormValues(bw.field));
    }

    public class BM25Scorer extends SimScorer
    {
	private final BM25Weight bw;
	private final NumericDocValues norms;
	private final float[] K;
    
	BM25Scorer(BM25Weight bw, NumericDocValues norms)
	    throws IOException
	{
	    this.bw    = bw;
	    this.K     = bw.K;
	    this.norms = norms;
	}

	@Override
	public float score(int doc, float tf)
	{
	    return ((k1 + 1) * tf) / ((K[(byte)norms.get(doc) & 0xFF]) + tf) * bw.idf; 
	}

	@Override
	public float computeSlopFactor(int distance)
	{
	    return 1.0f / (distance + 1);
	}

	@Override
	public float computePayloadFactor(int doc, int start, int end, BytesRef payload)
	{
	    return 1.0f;
	}
    }
  
    public static class BM25Weight extends SimWeight
    {
	private final String field;
	private final float idf;
	private final float adl;
	private final float K[];
	
	public BM25Weight(String field, float idf, float adl, float K[])
	{
	    this.field = field;
	    this.idf   = idf;
	    this.adl   = adl;
	    this.K     = K;
	}

	@Override
	public float getValueForNormalization()
	{
	    return 1.0f;
	}

	@Override
	public void normalize(float queryNorm, float topLevelBoost) {}
    }    

    protected byte encodeNorm(int dl)
    {
	return SmallFloat.floatToByte315(dl);
    }

    protected float decodeNorm(byte b)
    {
	return NORM[b & 0xFF];
    }
    
    @Override
    public final long computeNorm(FieldInvertState state)
    {
	final int numterms = state.getLength();
	return numterms;
    }
}
