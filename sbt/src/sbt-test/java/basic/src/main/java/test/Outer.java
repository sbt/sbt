package test;

public class Outer {
	public class Inner extends Outer {
		public class Inner2 extends Inner {}
		public class InnerB extends Outer {}
	}
}
