package xsbti.compile;

/**
 * An API for reporting when files are being compiled.
 *
 * Note; This is tied VERY SPECIFICALLY to scala.
 */
public interface CompileProgress {
	void startUnit(String phase, String unitPath);

	boolean advance(int current, int total);
}