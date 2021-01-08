package edu.mit.ll.cloud.connection;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class GraphuloTest {

	@Test
	public void testConnect() {
		Graphulo app = new Graphulo("uno",
				"localhost:2181", "haz", "haz");
		assertNotNull(app);
		assertTrue(app.connect());
	}
}
