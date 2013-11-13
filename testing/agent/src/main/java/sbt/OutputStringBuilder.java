package sbt;

public class OutputStringBuilder implements OutputListener {
	private StringBuilder outBuilder = new StringBuilder();
	private StringBuilder errBuilder = new StringBuilder();

	public void out(String text) {
		outBuilder.append(text);
	}

	public void err(String text) {
		errBuilder.append(text);
	}

	public String getOut() { return outBuilder.toString(); }
	public String getErr() { return errBuilder.toString(); }

	public String getOutAndReset() {
		String out = getOut();
		outBuilder.setLength(0); // reset
		return out;
	}
}
