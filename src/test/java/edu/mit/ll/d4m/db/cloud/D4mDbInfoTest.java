package edu.mit.ll.d4m.db.cloud;

import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

public class D4mDbInfoTest {
	
	D4mDbInfo sut;
	
	@Before
	public void setUp() throws Exception {
		sut = new D4mDbInfo("uno",
				"localhost:2181", "haz", "haz");
	}

	@Test
	public void testGetTableList() {
		try {
			assertNotNull(sut);
			String s = sut.getTableList();
			assertNotNull(sut.getTableList());
			System.out.println(s);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
