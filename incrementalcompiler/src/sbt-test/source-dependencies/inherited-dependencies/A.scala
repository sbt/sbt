// T is a type constructor [x]C
// C extends D
// E verifies the core type gets pulled out
trait A extends B.T[Int] with (E[Int] @unchecked)
