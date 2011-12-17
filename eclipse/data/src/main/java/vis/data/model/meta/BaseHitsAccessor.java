package vis.data.model.meta;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.commons.lang3.tuple.Pair;

import vis.data.util.CountAggregator;
import vis.data.util.SQL;
import vis.data.util.SetAggregator;

public abstract class BaseHitsAccessor {
	final static int BATCH_SIZE = 1024;
	abstract String bulkCountsQueryBase();
	abstract PreparedStatement countsQuery();
	abstract PreparedStatement updateQuery();
	abstract int maxItemId();
	abstract int maxCountedItemId();
	public int[] getItems(int doc_id) throws SQLException {
		PreparedStatement query_ = countsQuery();
		query_.setInt(1, doc_id);
		ResultSet rs = query_.executeQuery();
		try {
			if(!rs.next()) {
				return new int[0];
			}
			
			byte[] data = rs.getBytes(1);
			int[] item_ids = new int[data.length / (Integer.SIZE / 8) / 2];
			ByteBuffer bb = ByteBuffer.wrap(data);
			for(int i = 0; i < item_ids.length; ++i) {
				item_ids[i] = bb.getInt();
				/*int count =*/ bb.getInt();
			}
			return item_ids;
		} finally {
			rs.close();
		}
	}
	
	//TODO: batching? maybe not for these ones cause the result sets can be large
	public int[] getItems(int docs[]) throws SQLException {
		if(docs.length == 0)
			return new int[0];
		int[] partial = getItems(docs[0]);
		for(int i = 1; i < docs.length; ++i) {
			int[] res = getItems(docs[i]);
			partial = SetAggregator.or(res, partial);
		}
		return partial;

	}
	public Pair<int[], int[]> getCounts(int doc_id) throws SQLException {
		PreparedStatement query_ = countsQuery();
		int items[], counts[];
		query_.setInt(1, doc_id);
		ResultSet rs = query_.executeQuery();
		try {
			if(!rs.next()) {
				items = new int[0];
				counts = new int[0];
				return Pair.of(items, counts);
			}
			return processRow(rs);
		} finally {
			rs.close();
		}
	}
	private Pair<int[], int[]> processRow(ResultSet rs) throws SQLException {
		byte[] data = rs.getBytes(1);
		int num = data.length / (Integer.SIZE / 8) / 2;
		int[] items = new int[num];
		int[] counts = new int[num];
		ByteBuffer bb = ByteBuffer.wrap(data);
		for(int i = 0; i < num; ++i) {
			items[i] = bb.getInt();
			counts[i] = bb.getInt();
		}
		return Pair.of(items, counts);
	}
	static final int COUNT_TRADEOFF = 8192;
	public Pair<int[], int[]> getCounts(int docs[]) throws SQLException {
		if(docs.length == 0)
			return Pair.of(new int[0], new int[0]);
		StringBuilder sb = new StringBuilder(docs.length * 16);
		sb.append(bulkCountsQueryBase());
		sb.append("(");
		sb.append(docs[0]);
		for(int i = 1; i < docs.length; ++i) {
			sb.append(",");
			sb.append(docs[i]);
		}
		sb.append(")");
		Connection conn = SQL.open();
		try {
			Statement st = conn.createStatement();
			st.setFetchSize(Integer.MIN_VALUE);
			ResultSet rs = st.executeQuery(sb.toString());
			try {
				if(!rs.next())
					return Pair.of(new int[0], new int[0]);
				Pair<int[], int[]> partial = processRow(rs);
				Pair<int[], int[]> res;
				while(rs.next()) {
					res = processRow(rs);
					if(res.getKey().length > COUNT_TRADEOFF || partial.getKey().length > COUNT_TRADEOFF)
						break;
					partial = CountAggregator.or(res.getKey(), res.getValue(), partial.getKey(), partial.getValue());
				}
		
				//bail if we finished in short mode
				if(rs.isAfterLast()) {
					return partial;
				}		
				int[] all = new int[maxItemId() + 1];
				explodeItems(all, partial.getKey(), partial.getValue());
				while(rs.next()) {
					res = processRow(rs);
					explodeItems(all, res.getKey(), res.getValue());
				}
				return squishItems(all);
			} finally {
				rs.close();
				st.close();
			}
		} finally {
			conn.close();
		}
	}
	public int updateHitList(int item, int items[], int counts[]) throws SQLException {
		PreparedStatement st =updateQuery();
		st.setBytes(1, pack(items, counts));
		st.setInt(2, item);
		return st.executeUpdate();
	}
	public static byte[] pack(int items[], int counts[]) {
		int num = items.length;
		ByteBuffer bb = ByteBuffer.allocate(num * 2 * Integer.SIZE / 8);
		for(int i = 0; i < num; ++i) {
			bb.putInt(items[i]);
			bb.putInt(counts[i]);
		}
		return bb.array();
	}
	static Pair<int[], int[]> squishItems(int all[]) {
		int t = 0;
		for(int i = 0; i < all.length; ++i) {
			if(all[i] > 0)
				++t;
		}
		int id[] = new int[t];
		int count[] = new int[t];
		t = 0;
		for(int i = 0; t < id.length; ++i) {
			if(all[i] > 0) {
				id[t] = i;
				count[t] = all[i];
				++t;
			}
		}
		return Pair.of(id, count);
	}
	static void explodeItems(int all[], int id[], int count[]) {
		for(int i = 0; i < id.length; ++i) {
			all[id[i]] += count[i];
		}
	}
	
}
