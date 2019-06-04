package test

class Foo extends Serializable {
  private[this] var value: Int = 0
  def getValue(): Int = value
  def setValue(newValue: Int): Unit = value = newValue
  override def equals(o: Any): Boolean = o match {
    case that: Foo => this.getValue() == that.getValue()
    case _ => false
  }
  override def hashCode: Int = value
}