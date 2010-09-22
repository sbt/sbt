import java.util.Iterator;
class Anon
{
	public static final Iterator<String> it = new Iterator<String>() {
		public boolean hasNext() { return false; }
		public String next() { return ""; }
		public void remove() {}
	};
}
