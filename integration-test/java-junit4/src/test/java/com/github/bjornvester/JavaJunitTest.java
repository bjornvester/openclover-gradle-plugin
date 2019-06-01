package com.github.bjornvester;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class JavaJunitTest {
	@Test
	public void testMyJavaClass() {
		MyJavaClass myJavaClass = new MyJavaClass();
		assertTrue(myJavaClass.doStuff());
	}
}
