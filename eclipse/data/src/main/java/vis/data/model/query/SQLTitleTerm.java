package vis.data.model.query;
import java.sql.SQLException;

import vis.data.model.RawDoc;

public class SQLTitleTerm extends SQLTerm {
	public static class Parameters extends SQLTerm.Parameters {
		public String title_;
		
		@Override
		public int hashCode() {
			int hashCode = title_.hashCode();
			hashCode ^= Parameters.class.hashCode();
			return hashCode;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(!Parameters.class.isInstance(obj))
				return false;
			Parameters p = (Parameters)obj;
			if(!title_.equals(p.title_)) {
				return false;
			}
			return true;
		}

		@Override
		public void validate() {
			if(title_ == null)
				throw new RuntimeException("missing title");
		}
	}
	
	public final Parameters parameters_;
	public SQLTitleTerm(Parameters p) throws SQLException {
		super(buildQuery(p));
		parameters_ = p;
	}

	private static String buildQuery(Parameters p) {
		//the order by is critical!
		return "SELECT " + RawDoc.ID + " FROM " + RawDoc.TABLE + 
			" WHERE " + RawDoc.TITLE + " LIKE '%" + SQLTerm.clean(p.title_) + "%'" +
			" ORDER BY " + RawDoc.ID;
	}

	@Override
	public Parameters parameters() {
		return parameters_;
	}	
}
