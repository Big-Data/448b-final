package vis.data.model.meta;

import gnu.trove.list.linked.TIntLinkedList;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import vis.data.model.EntityDoc;
import vis.data.model.RawEntity;
import vis.data.model.RawLemma;
import vis.data.util.SQL;
import vis.data.util.SQL.NullForLastRowProcessor;

public class EntityAccessor {
	PreparedStatement query_, queryByEntity_, queryByBoth_, queryByType_, queryList_, queryListScore_, queryIdsByType_;
	public EntityAccessor() throws SQLException {
		this(SQL.forThread());
	}
	public EntityAccessor(Connection conn) throws SQLException {
		query_ = conn.prepareStatement("SELECT " + RawEntity.ENTITY + "," + RawEntity.TYPE + " FROM " + RawEntity.TABLE + " WHERE " + RawEntity.ID + " = ?");
		queryList_ = conn.prepareStatement("SELECT " + RawEntity.ID + "," + RawEntity.ENTITY + "," + RawEntity.TYPE + " FROM " + RawEntity.TABLE);
		queryList_.setFetchSize(Integer.MIN_VALUE); //streaming
		queryByEntity_ = conn.prepareStatement("SELECT " + RawEntity.ID + "," + RawEntity.TYPE + " FROM " + RawEntity.TABLE + " WHERE " + RawEntity.ENTITY + " = ?");
		queryByBoth_ = conn.prepareStatement("SELECT " + RawEntity.ID + " FROM " + RawEntity.TABLE + " WHERE " + RawEntity.ENTITY + " = ? AND " + RawEntity.TYPE + " = ?");
		queryByType_ = conn.prepareStatement("SELECT " + RawEntity.ID + "," + RawEntity.ENTITY + " FROM " + RawEntity.TABLE + " WHERE " + RawEntity.TYPE + " = ?");
		queryListScore_ = conn.prepareStatement("SELECT " + 
				RawEntity.ID + "," + RawEntity.ENTITY + "," + RawEntity.TYPE + ", LENGTH(" + EntityDoc.DOC_LIST + ")/8" + 
				" FROM " + RawEntity.TABLE +
				" JOIN " + EntityDoc.TABLE + " ON " + RawEntity.ID + "=" + EntityDoc.ENTITY_ID);
		queryListScore_.setFetchSize(Integer.MIN_VALUE); //streaming
		queryIdsByType_ = conn.prepareStatement("SELECT " + RawEntity.ID + " FROM " + RawEntity.TABLE + " WHERE " + RawEntity.TYPE + " = ? ORDER BY " + RawEntity.ID);
	}
	public RawEntity getEntity(int entity_id) throws SQLException {
		query_.setInt(1, entity_id);
		ResultSet rs = query_.executeQuery();
		try {
			if(!rs.next())
				throw new RuntimeException("failed to find entity_id " + entity_id);
			
			RawEntity re = new RawEntity();
			re.id_ = entity_id;
			re.entity_ = rs.getString(1);
			re.type_ = rs.getString(2);
			return re;
		} finally {
			rs.close();
		}
	}
	public RawEntity[] lookupEntityByName(String entity) throws SQLException {
		entity = entity.toLowerCase();
		queryByEntity_.setString(1, entity);
		ResultSet rs = queryByEntity_.executeQuery();
		try {
			if(!rs.next())
				return new RawEntity[0];
			
			ArrayList<RawEntity> hits = new ArrayList<RawEntity>(32);
			do {
				RawEntity re = new RawEntity();
				re.id_ =  rs.getInt(1);
				re.entity_ = entity;
				re.type_ = rs.getString(2);
				hits.add(re);
			} while(rs.next());
			return hits.toArray(new RawEntity[hits.size()]);
		} finally {
			rs.close();
		}
	}
	public RawEntity lookupEntity(String entity, String type) throws SQLException {
		entity = entity.toLowerCase();
		type = type.toUpperCase();
		queryByBoth_.setString(1, entity);
		queryByBoth_.setString(2, type);
		ResultSet rs = queryByBoth_.executeQuery();
		try {
			if(!rs.next())
				return null;
			
			RawEntity re = new RawEntity();
			re.id_ =  rs.getInt(1);
			re.entity_ = entity;
			re.type_ = type;
			return re;
		} finally {
			rs.close();
		}
	}
	public RawEntity[] lookupEntityByType(String type) throws SQLException {
		type = type.toUpperCase();
		queryByType_.setString(1, type);
		ResultSet rs = queryByType_.executeQuery();
		try {
			if(!rs.next())
				return new RawEntity[0];
			
			ArrayList<RawEntity> hits = new ArrayList<RawEntity>(32);
			do {
				RawEntity re = new RawEntity();
				re.id_ =  rs.getInt(1);
				re.entity_ = rs.getString(2);
				re.type_ = type;
				hits.add(re);
			} while(rs.next());
			return hits.toArray(new RawEntity[hits.size()]);
		} finally {
			rs.close();
		}
	}
	public static class ResultSetIterator extends org.apache.commons.dbutils.ResultSetIterator {
		public ResultSetIterator(ResultSet rs) {
			super(rs, new NullForLastRowProcessor());
		}
		
		public RawEntity nextEntity() {
			RawEntity re = new RawEntity();
			Object fields[] = super.next();
			if(fields == null)
				return null;
			re.id_ = (Integer)fields[0];
			re.entity_ = (String)fields[1];
			re.type_ = (String)fields[2];
			return re;
		}
	}
	public ResultSetIterator entityIterator() throws SQLException {
		ResultSet rs = queryList_.executeQuery();
		return new ResultSetIterator(rs);
	}
	public static class ScoredResultSetIterator extends org.apache.commons.dbutils.ResultSetIterator {
		public ScoredResultSetIterator(ResultSet rs) {
			super(rs, new NullForLastRowProcessor());
		}
		
		public ScoredEntity nextEntity() {
			ScoredEntity se = new ScoredEntity();
			Object fields[] = super.next();
			if(fields == null)
				return null;
			se.id_ = (Integer)fields[0];
			se.entity_ = (String)fields[1];
			se.type_ = (String)fields[2];
			se.score_ = ((BigDecimal)fields[3]).toBigInteger().intValue();
			return se;
		}
	}
	public static class ScoredEntity extends RawEntity {
		public int score_;
	}
	public ScoredResultSetIterator entityIteratorWithScore() throws SQLException {
		ResultSet rs = queryListScore_.executeQuery();
		return new ScoredResultSetIterator(rs);
	}
	public TIntObjectHashMap<RawEntity> getEntities(int[] id) throws SQLException {
		TIntObjectHashMap<RawEntity> res = new TIntObjectHashMap<RawEntity>();
		if(id.length == 0)
			return res;
		StringBuilder sb = new StringBuilder(id.length * 16);
		sb.append("SELECT " + RawLemma.ID + "," + RawEntity.ENTITY + "," + RawEntity.TYPE + " FROM " + RawEntity.TABLE + " WHERE " + RawEntity.ID + " IN ");
		sb.append("(");
		sb.append(id[0]);
		for(int i = 1; i < id.length; ++i) {
			sb.append(",");
			sb.append(id[i]);
		}
		sb.append(")");
		Statement st = SQL.forThread().createStatement();
		ResultSet rs = st.executeQuery(sb.toString());
		try {
			while(rs.next()) {
				RawEntity rl = new RawEntity();
				rl.id_ =  rs.getInt(1);
				rl.entity_ = rs.getString(2);
				rl.type_ = rs.getString(3);
				res.put(rl.id_, rl);
			}
	
			return res;
		} finally {
			rs.close();
		}
	}
	public int[] lookupEntityIdsByType(String type) throws SQLException {
		type = type.toUpperCase();
		queryIdsByType_.setString(1, type);
		ResultSet rs = queryIdsByType_.executeQuery();
		try {
			TIntLinkedList hits = new TIntLinkedList();
			while(rs.next())
				hits.add(rs.getInt(1));
			
			return hits.toArray();
		} finally {
			rs.close();
		}
	}
}
