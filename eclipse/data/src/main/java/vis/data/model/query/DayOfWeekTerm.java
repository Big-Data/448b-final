package vis.data.model.query;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.tuple.Pair;

import vis.data.model.meta.TimeSortedDocCache;

public class DayOfWeekTerm extends Term {
	public static class Parameters implements Term.Parameters {
		public Integer dayOfWeek_;

		@Override
		public int hashCode() {
			int hashCode = 0;
			hashCode ^= Parameters.class.hashCode();
			if(dayOfWeek_ != null)
				hashCode ^= dayOfWeek_.hashCode();
			return hashCode;
		}
		
		@Override
		public boolean equals(Object obj) {
			if(!Parameters.class.isInstance(obj))
				return false;
			Parameters p = (Parameters)obj;
			if(dayOfWeek_ != null ^ p.dayOfWeek_ != null) {
				return false;
			}
			if(dayOfWeek_ != null && !dayOfWeek_.equals(p.dayOfWeek_)) {
				return false;
			}
			return true;
		}

		@Override
		public void validate() {
			if(dayOfWeek_ == null)
				throw new RuntimeException("missing the actual day");
		}

		@Override
		public ResultType resultType() {
			return ResultType.DOC_HITS;
		}
		@Override
		public Collection<Term.Parameters> withChildren() {
			return Arrays.asList((Term.Parameters)this);
		}
		@Override
		public void setFilterOnly() {
			//always is
		}
	}
	
	public final Parameters parameters_;
	public final int docs_[];
	public DayOfWeekTerm(Parameters p) throws SQLException {
		parameters_ = p;
		TimeSortedDocCache tsd = new TimeSortedDocCache();
		docs_ = tsd.getDocsForDay(p.dayOfWeek_);
		//must be in doc id order
		Arrays.sort(docs_);
	}

	public Term.Parameters parameters() {
		return parameters_;
	}	
	@Override
	public Pair<int[], int[]> compute() throws SQLException {
		return Pair.of(docs_, new int[docs_.length]);
	}
}
