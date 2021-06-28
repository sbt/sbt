package test3

trait B { self: A with C => 
  class Inner {
    def b = B.this
  }

  def ref: Impl = throw new RuntimeException
}
