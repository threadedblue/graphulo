/**
 * 
 */
package edu.mit.ll.d4m.db.cloud;

import edu.mit.ll.d4m.db.cloud.accumulo.D4mDbQueryAccumulo;
import edu.mit.ll.d4m.db.cloud.util.D4mDataObj;

/**
 * @author cyee
 *
 */
public class D4mDataSearch extends D4mParentQuery {

	protected D4mDataObj result = new D4mDataObj();
	private D4mParentQuery d4m=null;

	public String rowReturnString = "";
	public String columnReturnString = "";
	public String valueReturnString = "";


	/**
	 * 
	 */
	public D4mDataSearch() {
		super();
	}

	public D4mDataSearch(String instanceName, String host, String table, String username, String password) {
		super();
		super.tableName = table;
		super.connProps.setHost(host);
		super.connProps.setInstanceName(instanceName);
		super.connProps.setUser(username);
		super.connProps.setPass(password);
		super.connProps.setType("password");
	}

	/* (non-Javadoc)
	 * @see edu.mit.ll.d4m.db.cloud.D4mParentQuery#doMatlabQuery(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public D4mDbResultSet doMatlabQuery(String rows, String cols,
			String family, String authorizations) throws D4mException {
		reset();

		if(this.d4m == null) {
			this.d4m = new D4mDbQueryAccumulo();
			this.d4m.setTableName(super.tableName);
			this.d4m.setConnProps(super.connProps);
		}
		this.d4m.setLimit(super.limit);
		this.d4m.setTableName(super.tableName);
		D4mDbResultSet testResult = d4m.doMatlabQuery(rows, cols, family, authorizations);
		setResults();
		return testResult;
	}

	/* (non-Javadoc)
	 * @see edu.mit.ll.d4m.db.cloud.D4mParentQuery#next()
	 */
	@Override
	public void next() {
		this.d4m.setLimit(super.limit);
		d4m.next();
		setResults();
	}

	@Override
	public D4mDataObj getResults() {

		return this.d4m.getResults();
	}

	private void setResults() {
		D4mDataObj data = d4m.getResults();
		this.rowReturnString = data.getRow();
		this.columnReturnString =  data.getColQualifier();
		this.valueReturnString = data.getValue();

	}

	@Override
	public void reset() {
		if(d4m != null)
			d4m.reset();

	}

	public String getRowReturnString() {
		return rowReturnString;
	}

	public void setRowReturnString(String rowReturnString) {
		this.rowReturnString = rowReturnString;
	}

	public String getColumnReturnString() {
		return columnReturnString;
	}

	public void setColumnReturnString(String columnReturnString) {
		this.columnReturnString = columnReturnString;
	}

	public String getValueReturnString() {
		return valueReturnString;
	}

	public void setValueReturnString(String valueReturnString) {
		this.valueReturnString = valueReturnString;
	}

	@Override
	public boolean hasNext() {
		return this.d4m.hasNext();
	}
	
	@Override
	public void setLimit(int limit) {
		super.setLimit(limit);
		if(this.d4m != null)
		this.d4m.setLimit(limit);
	}
	
	public void setSecurity(String authorizations) {
		this.d4m.setSecurity(authorizations);
	}
	
	public String getSecurity() {
		String s= this.d4m.getSecurity();
		return s;
	}
	
	public String getColumnFamily() {
		
		String cf = this.d4m.getColumnFamily();
		return cf;
	}
	public void putColumnFamily(String columnFamily) {
		this.setColumnFamily(columnFamily);
	}
	public void setColumnFamily(String columnFamily) {
		this.d4m.setColumnFamily(columnFamily);
	}
}

/*
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% D4M: Dynamic Distributed Dimensional Data Model
% MIT Lincoln Laboratory
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% (c) <2010> Massachusetts Institute of Technology
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
*/

