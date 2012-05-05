package xsbti;

public enum Predefined 
{
	Local("local"),
	MavenLocal("maven-local"),
	MavenCentral("maven-central"),
	ScalaToolsReleases("sonatype-oss-releases"),
	ScalaToolsSnapshots("sonatype-oss-snapshots");

	private final String label;
	private Predefined(String label) { this.label = label; }
	public String toString() { return label; }

	public static Predefined toValue(String s)
	{
		for(Predefined p : values())
			if(s.equals(p.toString()))
				return p;
		
		StringBuilder msg = new StringBuilder("Expected one of ");
		for(Predefined p : values())
			msg.append(p.toString()).append(", ");
		msg.append("got '").append(s).append("'.");
		throw new RuntimeException(msg.toString());
	}
}