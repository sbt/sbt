import org.scalacheck._

class SimpleTest extends Properties("Simple")
{
	specify("increment scala", (i: Int) => (new a.b.ScalaA).increment(i) == i+1)
	specify("increment java", (i: Int) => (new JavaA).inc(i) == i+1)
	
	specify("decrement scala", (i: Int) => (new b.ScalaB).decrement(i) == i+1)
	specify("decrement java", (i: Int) => (new a.JavaB).dec(i) == i+1)
}