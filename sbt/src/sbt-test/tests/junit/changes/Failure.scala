package com.foo.junit.test.blah

import org.junit._

class Failure
{
	@Test def fail() { error("Fail!") }
}
