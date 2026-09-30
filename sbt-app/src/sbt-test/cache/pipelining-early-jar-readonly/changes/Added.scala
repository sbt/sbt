package core

final case class Added[T](others: List[Other[T]], n: Int)
