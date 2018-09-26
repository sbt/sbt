object Count {
  private var count = 0
  def get: Int = count
  def increment(): Unit = count += 1
  def reset(): Unit = count = 0
}