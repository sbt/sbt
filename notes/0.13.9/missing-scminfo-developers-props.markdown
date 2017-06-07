  [@pdalpra]: http://github.com/pdalpra
  

### Fixes with compatibility implications
### Improvements

 -  Adds to ScmInfo and Developer classes all the properties that are defined in [Maven's POM Reference](https://maven.apache.org/pom.html) but were missing. By [@pdalpra][@pdalpra]
    
    In ScmInfo:
    - Adds an optional `tag`
	
	In Developer:
	- Adds an optional `organization`
	- Adds an optional `organizationUrl`
    - Adds an optional `roles` list
    - Adds an optional `timezone`
    - Adds an optional `properties` map

### Bug fixes
