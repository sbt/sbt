package xsbti;

	import java.net.URL;

public interface IvyRepository extends Repository
{
	String id();
	URL url();
	String ivyPattern();
	String artifactPattern();
}
