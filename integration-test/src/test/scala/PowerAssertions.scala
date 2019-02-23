package example.test

import com.eed3si9n.expecty.Expecty

trait PowerAssertions {
  lazy val assert: Expecty = new Expecty()
}
