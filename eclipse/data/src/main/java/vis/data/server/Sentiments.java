package vis.data.server;

import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import vis.data.model.RawSentiment;
import vis.data.model.meta.SentimentAccessor;

public class Sentiments {
	@Path("/api/sentiments")
	public static class All {
		@GET
		@Produces("application/json")
		public Map<Integer, String> listAll() throws SQLException {
			SentimentAccessor sr = new SentimentAccessor();
			Map<Integer, String> m = new TreeMap<Integer, String>();
			for(RawSentiment rs : sr.listSentiments()) {
				m.put(rs.id_, rs.sentiment_);
			}
			return m;
		}
	}
}
