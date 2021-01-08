package edu.mit.ll.cloud.connection;

import java.util.Properties;

import org.apache.accumulo.core.conf.ClientProperty;

public class ConnectionProperties extends Properties {

	private static final long serialVersionUID = -4776516646245732900L;

	private String[] authorizations = null;
	public static final int MAX_NUM_THREADS = 25;
	private int maxNumThreads = MAX_NUM_THREADS; // 50
	private int sessionTimeOut = 100000; // millisec

	public ConnectionProperties() {
	}

	public ConnectionProperties(String host, String user, String pass, String instanceName, String[] authorizations) {
		this.setProperty(ClientProperty.INSTANCE_ZOOKEEPERS.getKey(), host);
		this.setProperty(ClientProperty.INSTANCE_NAME.getKey(), instanceName);
		this.setProperty(ClientProperty.AUTH_TOKEN.getKey(), pass);
		this.setProperty(ClientProperty.AUTH_PRINCIPAL.getKey(), user);
		this.setProperty(ClientProperty.AUTH_TYPE.getKey(), "password");
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
		return this.getProperty(ClientProperty.INSTANCE_ZOOKEEPERS.getKey());
	}

	/**
	 * @param host the host to set
	 */
	public void setHost(String host) {
		this.setProperty(ClientProperty.INSTANCE_ZOOKEEPERS.getKey(), host);
	}

	/**
	 * @return the user
	 */
	public String getUser() {
		return this.getProperty(ClientProperty.AUTH_PRINCIPAL.getKey());
	}

	/**
	 * @param user the user to set
	 */
	public void setUser(String user) {
		this.setProperty(ClientProperty.AUTH_PRINCIPAL.getKey(), user);
	}

	/**
	 * @return the pass
	 */
	public String getPass() {
		return this.getProperty(ClientProperty.AUTH_TOKEN.getKey());
	}

	/**
	 * @param pass the pass to set
	 */
	public void setPass(String pass) {
		this.setProperty(ClientProperty.AUTH_TOKEN.getKey(), pass);
	}

	/**
	 * @return the instanceName
	 */
	public String getInstanceName() {
		return this.getProperty(ClientProperty.INSTANCE_NAME.getKey());
	}

	/**
	 * @param instanceName the instanceName to set
	 */
	public void setInstanceName(String instanceName) {
		this.setProperty(ClientProperty.INSTANCE_NAME.getKey(), instanceName);
	}

	public String getType() {
		return this.getProperty(ClientProperty.AUTH_TYPE.getKey());
	}
	
	public void setType(String type) {
		this.setProperty(ClientProperty.AUTH_TYPE.getKey(), type);
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
