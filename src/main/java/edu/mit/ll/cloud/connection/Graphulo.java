package edu.mit.ll.cloud.connection;

import java.util.Properties;

import org.apache.accumulo.core.client.Accumulo;
import org.apache.accumulo.core.client.AccumuloClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.ll.graphulo.util.D4MTableWriter;

public class Graphulo {

	private static final Logger LOG = LoggerFactory.getLogger(Graphulo.class);

	private String accumuloInstance;
	private String zookeeperURI;
	private String userName;
	private String pword;

	AccumuloClient conn;
	D4MTableWriter writer;

	public Graphulo(String accumuloInstance,
			String zookeeperURI, String userName, String pword) {
		super();
		this.accumuloInstance = accumuloInstance;
		this.zookeeperURI = zookeeperURI;
		this.userName = userName;
		this.pword = pword;
	}

	public boolean connect() {
		Properties props = new Properties();
		props.put("instance.name", accumuloInstance);
		props.put("instance.zookeepers", zookeeperURI);
		props.put("auth.type", "password");
		props.put("auth.principal", userName);
		props.put("auth.token", pword);
		conn = Accumulo.newClient().from(props).build();
		return (conn != null);
	}

	public AccumuloClient getConnection() {
		return conn;
	}
}
