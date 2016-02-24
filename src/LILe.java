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

public class LILe extends Similarity {
    private static final float[] NORM = new float[256];
    static {
	for (int i = 0; i < 256; i++) {
	    NORM[i] = SmallFloat.byte315ToFloat((byte)i);
	}
    }
    
    public LILe() {}

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
	N   = collectionStats.maxDoc();
	adl = collectionStats.sumTotalTermFreq() / N;
	
	if (termStats.length == 1) {
	    n = termStats[0].docFreq();
	    idf = log((N - n + 0.5f) / (n + 0.5f));
	}
	else {
	    for (final TermStatistics stat : termStats) {
		n = stat.docFreq();
		idf += log((N - n + 0.5f) / (n + 0.5f));
	    }
	}
	
	return new TFIDFWeight(collectionStats.field(), idf, adl);
    }

    @Override
    public final SimScorer simScorer(SimWeight sw, LeafReaderContext context)
	throws IOException
    {
	TFIDFWeight tw = (TFIDFWeight) sw;
	return new TFIDFScorer(tw, context.reader().getNormValues(tw.field));
    }

    public class TFIDFScorer extends SimScorer
    {
	private final TFIDFWeight tw;
	private final NumericDocValues norms;
	
	TFIDFScorer(TFIDFWeight tw, NumericDocValues norms)
	    throws IOException
	{
	    this.tw    = tw;
	    this.norms = norms;
	}

	@Override
	public float score(int doc, float tf)
	{
	    float idf, dl, adl, K, w;
	    idf = tw.idf;
	    adl = tw.adl;
	    dl = decodeNorm((byte)((byte)norms.get(doc) & 0xFF));
	    K = log(dl);
	    w = log(tf) / K * idf;
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
  
    public static class TFIDFWeight extends SimWeight
    {
	private final String field;
	private final float idf;
	private final float adl;
	
	public TFIDFWeight(String field, float idf, float adl)
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
