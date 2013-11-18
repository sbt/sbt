package sbt;

public class OutputStringBuilder implements OutputListener {

	private final StringBuffer outBuilder = new StringBuffer();
	private final StringBuffer errBuilder = new StringBuffer();

	public void out(String text) {
		outBuilder.append(text);
	}

	public void err(String text) {
		errBuilder.append(text);
	}

	public String getOut() { return outBuilder.toString(); }
	public String getErr() { return errBuilder.toString(); }

	public String getOutAndReset() {
		synchronized (outBuilder) {
			String out = getOut();
			outBuilder.setLength(0); // reset
			return out;
		}
	}
}
