package edu.mit.ll.d4m.db.cloud;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class D4mDataSearchTest {

	static D4mDataSearch sut;
	
	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		sut = new D4mDataSearch("uno",
				"localhost:2181", "ccd", "haz", "haz");
	}

	@Test
	void testDoMatlabQuery() {
		try {
			D4mDbResultSet rs = sut.doMatlabQuery(":", "Encounters,:,Encounters,", "", "");
			assertNotNull(rs);
			D4mDbResultSet rs1 = sut.doMatlabQuery(":", "Allergies_and_Adverse_Reactions.Observation.code:ASSERTION,", "", "");
			assertNotNull(rs1);
		} catch (D4mException e) {
			e.printStackTrace();
		}
	}

}
