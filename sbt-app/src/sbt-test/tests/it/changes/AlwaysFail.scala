package org.example

// this tests that normal tests do not
//  interfere with integration tests (#539)

import org.specs2.mutable._

class B extends Specification
{
	"this" should {
 		"not work" in { 1 must_== 2 }
	}
}

object A extends Specification
{
	"this" should {
		"not work" in { 1 must_== 2 }
	}
}
