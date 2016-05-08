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

public class BM25e extends Similarity
{
    private static float k1;
    private static float b;
    private static final float[] NORM = new float[256];
    static {
	for (int i = 0; i < 256; i++) {
	    NORM[i] = SmallFloat.byte315ToFloat((byte)i);
	}
    }
    
    public BM25e()
    {
	k1 = 1.2f;
	b  = 0.75f;
    }

    public BM25e(float k1, float b)
    {
	k1 = k1;
	b  = b;
    }

    public float log(double x)
    {
	return (float)(Math.log(x) / Math.log(2.0D));
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
    public final SimWeight computeWeight(CollectionStatistics collectionStats,
					 TermStatistics... termStats)
    {
	float N, n, idf, adl;

	idf = 1.0f;
	
	N = collectionStats.maxDoc();

	if (termStats.length == 1) {
	    n = termStats[0].docFreq();
	    idf = log(1.0f + (N - n + 0.5f) / (n + 0.5f));
	}
	else {
	    for (final TermStatistics stat : termStats) {
		n = stat.docFreq();
		idf += log(1.0f + (N - n + 0.5f) / (n + 0.5f));
	    }
	}
	
	adl = collectionStats.sumTotalTermFreq() / N;

	return new BM25Weight(collectionStats.field(), idf, adl);
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

    
	BM25Scorer(BM25Weight bw, NumericDocValues norms)
	    throws IOException
	{
	    this.bw    = bw;
	    this.norms = norms;
	}

	@Override
	public float score(int doc, float tf)
	{
	    float idf, dl, adl, K, w;
	    idf = bw.idf;
	    adl = bw.adl;
	    dl  = decodeNorm((byte)((byte)norms.get(doc) & 0xFF));
	    K   = k1 * (1.0f - b + b * (dl / adl));
	    w   = ((k1 + 1.0f) * tf) / (K + tf) * idf;
	    return w;
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
	
	public BM25Weight(String field, float idf, float adl)
	{
	    this.field = field;
	    this.idf   = idf;
	    this.adl   = adl;
	}

	@Override
	public float getValueForNormalization()
	{
	    return 1.0f;
	}

	@Override
	public void normalize(float queryNorm, float boost) {}
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
	return encodeNorm(state.getLength());
    }
}
