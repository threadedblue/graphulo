package edu.mit.ll.cloud.connection;

import java.util.Properties;

public class ConnectionProperties extends Properties {

	private static final long serialVersionUID = -4776516646245732900L;

	public enum CONN_PROPS {
		HOST, USER, PASSWORD, INSTANCE
	};

	private String[] authorizations = null;
	public static final int MAX_NUM_THREADS = 25;
	private int maxNumThreads = MAX_NUM_THREADS; // 50
	private int sessionTimeOut = 100000; // millisec

	public ConnectionProperties() {
	}

	public ConnectionProperties(String host, String user, String pass, String instanceName, String[] authorizations) {
		this.setProperty(CONN_PROPS.HOST.name(), host);
		this.setProperty(CONN_PROPS.INSTANCE.name(), instanceName);
		this.setProperty(CONN_PROPS.PASSWORD.name(), pass);
		this.setProperty(CONN_PROPS.USER.name(), user);
		this.authorizations = authorizations;
	}

	public String[] getAuthorizations() {
		return authorizations;
	}

	public void setAuthorizations(String[] authorizations) {
		this.authorizations = authorizations;
	}

	/**
	 * @return the host
	 */
	public String getHost() {
		return this.getProperty(CONN_PROPS.HOST.name());
	}

	/**
	 * @param host the host to set
	 */
	public void setHost(String host) {
		this.setProperty(CONN_PROPS.HOST.name(), host);
	}

	/**
	 * @return the user
	 */
	public String getUser() {
		return this.getProperty(CONN_PROPS.USER.name());
	}

	/**
	 * @param user the user to set
	 */
	public void setUser(String user) {
		this.setProperty(CONN_PROPS.USER.name(), user);
	}

	/**
	 * @return the pass
	 */
	public String getPass() {
		return this.getProperty(CONN_PROPS.PASSWORD.name());
	}

	/**
	 * @param pass the pass to set
	 */
	public void setPass(String pass) {
		this.setProperty(CONN_PROPS.PASSWORD.name(), pass);
	}

	/**
	 * @return the instanceName
	 */
	public String getInstanceName() {
		return this.getProperty(CONN_PROPS.INSTANCE.name());
	}

	/**
	 * @param instanceName the instanceName to set
	 */
	public void setInstanceName(String instanceName) {
		this.setProperty(CONN_PROPS.INSTANCE.name(), instanceName);
	}

	public void setMaxNumThreads(int num) {
		this.maxNumThreads = num;
	}

	public int getMaxNumThreads() {
		return this.maxNumThreads;
	}

//	public String toString() {
//		String s = "INSTANCE_NAME=" + this.instanceName + ", HOST=" + this.host + ", USER=" + this.user;
//		return s;
//	}

	public int getSessionTimeOut() {
		return sessionTimeOut;
	}

	public void setSessionTimeOut(int sessionTimeOut) {
		this.sessionTimeOut = sessionTimeOut;
	}
}
/*
 * %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% % D4M: Dynamic
 * Distributed Dimensional Data Model % MIT Lincoln Laboratory
 * %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%% % (c) <2010>
 * Massachusetts Institute of Technology
 * %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
 */
