package example

// These implementations have no error checking: they will throw
// exceptions if the input is unexpected, e.g. an empty list has no
// penultimate member.
object Exercises {
  def penultimate[T](xs: List[T]): T = xs.reverse.tail.head
  // Other possible implementations:
  // def penultimate[T](xs: List[T]): T = xs.init.last  
  // def penultimate[T](xs: List[T]): T = xs(xs.length - 2)
  // def penultimate[T](xs: List[T]): T = xs takeRight 2 head
  
  // Test if the argument is a palindrome.
  def isPalindrome[T](xs: List[T]) = xs == xs.reverse
  
  // Remove the Kth element from a list, returning the list and
  // the removed element as a tuple.
  def removeAt[T](index: Int, xs: List[T]): (List[T], T) = {
    val (front, back) = xs splitAt index
    (front ++ back.tail, back.head)
  }
}