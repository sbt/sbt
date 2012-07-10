package xsbti.compile;

public interface CompileProgress {
	void startUnit(String phase, String unitPath);

	boolean advance(int current, int total);
}