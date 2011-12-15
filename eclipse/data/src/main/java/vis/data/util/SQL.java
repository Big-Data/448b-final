package vis.data.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.sql.DataSource;

import net.sf.log4jdbc.Log4jdbcProxyDataSource;

import org.apache.commons.dbutils.BasicRowProcessor;
import org.apache.commons.dbutils.DbUtils;

import vis.data.model.annotations.Index;
import vis.data.model.annotations.NonUniqueIndexes;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

public class SQL {

	public static <T> java.util.Collection<String> getNonGenerated(Class<T> cls) {
		Set<String> fields = new TreeSet<String>();
		Field model_fields[] = cls.getFields();
		for(Field f : model_fields) {
			Column col = f.getAnnotation(Column.class);
			if(col == null)
				continue;
			GeneratedValue gen = f.getAnnotation(GeneratedValue.class);
			if(gen != null)
				continue;
			fields.add(col.name());
		}
		return fields;
	}

	public static <T> void createTable(Connection conn, Class<T> cls) throws SQLException {
		createTable(conn, cls, true);
	}
	public static <T> void createTable(Connection conn, Class<T> cls, boolean indexes) throws SQLException {
		String TABLE_NAME = cls.getAnnotation(Table.class).name();

		TreeMap<String, String> col_def = new TreeMap<String, String>();
		Field model_fields[] = cls.getFields();
		for(Field f : model_fields) {
			Column col = f.getAnnotation(Column.class);
			if(col == null)
				continue;
			col_def.put(col.name(), col.columnDefinition());
		}		

		//also create the table... die if you run this without deleting the old table
		Iterator<String> i_name = col_def.keySet().iterator();
		Iterator<String> i_type = col_def.values().iterator();
		StringBuilder table_spec = new StringBuilder();
		table_spec.append(i_name.next());
		table_spec.append(' ');
		table_spec.append(i_type.next());
		for(;i_name.hasNext();) {
			table_spec.append(',');
			table_spec.append(i_name.next());
			table_spec.append(' ');
			table_spec.append(i_type.next());
			
		}
		System.out.println(table_spec);
		Statement st = conn.createStatement();
		try {
			st.execute("CREATE TABLE " + TABLE_NAME + "(" + table_spec  + ")");
		} finally {
			st.close();
		}
		if(indexes) {
			createAllIndexes(conn, cls);
		}
	}
	public static <T> void createAllIndexes(Connection conn, Class<T> cls) throws SQLException {
		createUniqueIndexes(conn, cls);
		createNonUniqueIndexes(conn, cls);
	}
	public static <T> void createUniqueIndexes(Connection conn, Class<T> cls) throws SQLException {
		String TABLE_NAME = cls.getAnnotation(Table.class).name();
		UniqueConstraint uniq[] = cls.getAnnotation(Table.class).uniqueConstraints();
		for(UniqueConstraint u : uniq) {
			String index_name = TABLE_NAME + "_by";
			String index_fields = "";
			for(String s : u.columnNames()) {
				if(!index_fields.isEmpty())
					index_fields += ", ";
				index_fields += s;
				index_name += "_" + s.replaceAll("[^_a-zA-Z0-9]", "_");
			}
			Statement st = conn.createStatement();
			try {
				st.execute("CREATE UNIQUE INDEX " + index_name + " ON " + TABLE_NAME + " (" + index_fields + ")");
			} finally {
				st.close();
			}
		}
	}
	public static <T> void createNonUniqueIndexes(Connection conn, Class<T> cls) throws SQLException {
		String TABLE_NAME = cls.getAnnotation(Table.class).name();
		NonUniqueIndexes nui = cls.getAnnotation(NonUniqueIndexes.class);
		if(nui == null)
			return;
		Index uniq[] = nui.indexes();
		for(Index u : uniq) {
			String index_name = TABLE_NAME + "_by";
			String index_fields = "";
			for(String s : u.columnNames()) {
				if(!index_fields.isEmpty())
					index_fields += ", ";
				index_fields += s;
				index_name += "_" + s.replaceAll("[^_a-zA-Z0-9]", "_");
			}
			Statement st = conn.createStatement();
			try {
				st.execute("CREATE INDEX " + index_name + " ON " + TABLE_NAME + " (" + index_fields + ")");
			} finally {
				st.close();
			}
		}
	}
	final static MysqlConnectionPoolDataSource base = new MysqlConnectionPoolDataSource();
	final static DataSource cpds;
	final static boolean TRACE_SQL = false;
	static {
		base.setUser("vis");
		base.setPassword("vis");
		base.setUrl("jdbc:mysql://127.0.0.1/vis?useServerPrepStmts=true");
//		base.setUrl("jdbc:mysql://127.0.0.1/vis");
		if(TRACE_SQL) {
			Log4jdbcProxyDataSource logger = new Log4jdbcProxyDataSource(base);
			cpds = logger;
		} else {
			cpds = base;
		}
	}

	public static Connection open() {
		try {
			Connection conn = cpds.getConnection();
			return conn;
		} catch (SQLException e) {
			System.err.println ("Cannot connect to database server");
			throw new RuntimeException("Sql connection failed", e);
		}
	}
	final static ThreadLocal<Connection> tlc = new ThreadLocal<Connection>();
	public static Connection forThread() {
		Connection for_thread = tlc.get();
		if(for_thread == null) {
			for_thread = open();
			tlc.set(for_thread);
		}
		return for_thread;
	}
	public static class SQLCloseFilter implements Filter {

		@Override
		public void destroy() {
		}

		@Override
		public void doFilter(ServletRequest req, ServletResponse resp,
				FilterChain chain) throws IOException, ServletException {
			try {
				chain.doFilter(req, resp);
			} finally {
				Connection conn = tlc.get();
				tlc.remove();
				if(conn != null)
					DbUtils.closeQuietly(conn);
			}
		}

		@Override
		public void init(FilterConfig arg0) throws ServletException {
		}
		
	}
	public static void importMysqlDump(File f, String... options)  {
		System.out.println("loading mysql dump " + f.getName());
		try {
			List<String> args = new ArrayList<String>();
			args.addAll(Arrays.asList(
				"mysql",
				"--user=vis",
				"--password=vis",
				"vis"
			));
			args.addAll(Arrays.asList(options));
			ProcessBuilder pb = new ProcessBuilder(args);
			pb.redirectInput(f);
			Process pr = pb.start();
			int res = pr.waitFor();
			if(res != 0)
				throw new RuntimeException("failed to import database code " + res);
		} catch (Exception e) {
			throw new RuntimeException("failed to import database", e);
		}
	}
	//check and swallow exceptions
	public static boolean tableExists(String table) {
		try {
			DatabaseMetaData dbm = SQL.forThread().getMetaData();
			// check if "employee" table is there
			ResultSet tables = dbm.getTables(null, null, table, null);
			try {
				return tables.next();
			} finally {
				tables.close();
			}
		} catch(SQLException e) {
			return false;
		}
	}
	public static class NullForLastRowProcessor extends BasicRowProcessor {
		@Override
		public Object[] toArray(ResultSet rs) throws SQLException {
			if(rs.isAfterLast())
				return null;
			return super.toArray(rs);
		}
	}
}
