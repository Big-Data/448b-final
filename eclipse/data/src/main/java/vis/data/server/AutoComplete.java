package vis.data.server;

import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import vis.data.model.AutoCompleteEntry;
import vis.data.model.meta.AutoCompleteAccessor;

public class AutoComplete {
	@Path("/api/autocomplete/types")
	public static class ListAutoCompleteTypes {
		@GET
		@Produces("application/json")
		public Map<Integer, String> get() {
			Map<Integer, String> mapping = new TreeMap<Integer, String>();
			for(AutoCompleteEntry.Type t : AutoCompleteEntry.Type.values()) {
				mapping.put(t.ordinal(), t.toString());
			}
			return mapping;
		}
	}

	@Path("/api/autocomplete/{term}")
	public static class AutoCompleteFull {
		@GET
		@Produces("application/json")
		public AutoCompleteAccessor.NamedAutoComplete[] get(
			@PathParam("term") String term) 
			throws SQLException 
		{
			AutoCompleteAccessor aca = new AutoCompleteAccessor();
			return aca.lookup(term);
		}
	}
	@Path("/api/autocomplete/{term}/limit/{limit}")
	public static class AutoCompleteLimited {
		@GET
		@Produces("application/json")
		public AutoCompleteAccessor.NamedAutoComplete[] get(
			@PathParam("term") String term, 
			@PathParam("limit") int limit) 
			throws SQLException 
		{
			AutoCompleteAccessor aca = new AutoCompleteAccessor();
			return aca.lookup(term, limit);
		}
	}
	@Path("/api/autocomplete/{term}/type/{type}")
	public static class AutoCompleteTypedFull {
		@GET
		@Produces("application/json")
		public AutoCompleteAccessor.NamedAutoComplete[] get(
			@PathParam("term") String term, 
			@PathParam("type") int type)
			throws SQLException 
		{
			AutoCompleteAccessor aca = new AutoCompleteAccessor();
			return aca.lookup(term, AutoCompleteEntry.Type.values()[type]);
		}
	}	
	@Path("/api/autocomplete/{term}/type/{type}/limit/{limit}")
	public static class AutoCompleteTypedLimited {
		@GET
		@Produces("application/json")
		public AutoCompleteAccessor.NamedAutoComplete[] get(
			@PathParam("term") String term, 
			@PathParam("type") int type,
			@PathParam("limit") int limit) 
			throws SQLException 
		{
			AutoCompleteAccessor aca = new AutoCompleteAccessor();
			return aca.lookup(term, AutoCompleteEntry.Type.values()[type], limit);
		}
	}

}