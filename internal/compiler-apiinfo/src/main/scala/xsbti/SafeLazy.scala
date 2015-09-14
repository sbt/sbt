// needs to be in xsbti package (or a subpackage) to pass through the filter in DualLoader
//  and be accessible to the compiler-side interface
package xsbti

object SafeLazy {
  def apply[T <: AnyRef](eval: xsbti.F0[T]): xsbti.api.Lazy[T] =
    apply(eval())
  def apply[T <: AnyRef](eval: => T): xsbti.api.Lazy[T] =
    fromFunction0(eval _)
  def fromFunction0[T <: AnyRef](eval: () => T): xsbti.api.Lazy[T] =
    new Impl(eval)

  def strict[T <: AnyRef](value: T): xsbti.api.Lazy[T] = apply(value)

  private[this] final class Impl[T <: AnyRef](private[this] var eval: () => T) extends xsbti.api.AbstractLazy[T] {
    private[this] lazy val _t =
      {
        val t = eval()
        eval = null // clear the reference, ensuring the only memory we hold onto is the result
        t
      }
    def get: T = _t
  }
}