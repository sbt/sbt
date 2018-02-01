# CLI Documentation

## Json Report

When invoking coursier cli with
```
fetch -t <modules...> --json-output-file <report.json>
```

The report will contain the info about resolved modules and their relationships.

## Format and Version Change Log

### 0.0.1

Add 'version' field to the report.

```
{
  "version": "0.0.1",
  "conflict_resolution": {
    ...
  },
  "dependencies": [
    ...
  ]
}
```

### Initial version (before we add the version to the report)

```
{
  "conflict_resolution": {
    "org:name:version" (requested): "org:name:version" (reconciled)
  },
  "dependencies": [
    {
      "coord": "orgA:nameA:versionA",
      "files": [
        [
          <classifier>,
          <path>
        ]
      ],
      "dependencies": [ // coodinates for its transitive dependencies
        <orgX:nameX:versionX>,
        <orgY:nameY:versionY>,
      ]
    },
    {
      "coord": "orgB:nameB:versionB",
      "files": [
        [
          <classifier>,
          <path>
        ]
      ],
      "dependencies": [ // coodinates for its transitive dependencies
        <orgX:nameX:versionX>,
        <orgZ:nameZ:versionZ>,
      ]
    },
  ]
}
```
For example:
```
fetch -t org.apache.avro:trevni-avro:1.8.2  org.slf4j:slf4j-api:1.7.6 --json-output-file x.out
  Result:
├─ org.apache.avro:trevni-avro:1.8.2
│  ├─ org.apache.avro:trevni-core:1.8.2
│  │  ├─ org.apache.commons:commons-compress:1.8.1
│  │  ├─ org.slf4j:slf4j-api:1.7.7
│  │  └─ org.xerial.snappy:snappy-java:1.1.1.3
│  └─ org.slf4j:slf4j-api:1.7.7
└─ org.slf4j:slf4j-api:1.7.6 -> 1.7.7
```
would produce the following json file:
```
$ jq < x.out
{
  "conflict_resolution": {
    "org.slf4j:slf4j-api:1.7.6": "org.slf4j:slf4j-api:1.7.7"
  },
  "dependencies": [
    {
      "coord": "org.apache.avro:trevni-core:1.8.2",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/apache/avro/trevni-core/1.8.2/trevni-core-1.8.2.jar"
        ]
      ],
      "dependencies": [
        "org.slf4j:slf4j-api:1.7.7",
        "org.xerial.snappy:snappy-java:1.1.1.3",
        "org.apache.commons:commons-compress:1.8.1"
      ]
    },
    {
      "coord": "org.apache.avro:trevni-avro:1.8.2",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/apache/avro/trevni-avro/1.8.2/trevni-avro-1.8.2.jar"
        ]
      ],
      "dependencies": [
        "org.apache.avro:trevni-core:1.8.2",
        "org.slf4j:slf4j-api:1.7.7",
        "org.xerial.snappy:snappy-java:1.1.1.3",
        "org.apache.commons:commons-compress:1.8.1"
      ]
    },
    {
      "coord": "org.slf4j:slf4j-api:1.7.7",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.7/slf4j-api-1.7.7.jar"
        ]
      ],
      "dependencies": []
    },
    {
      "coord": "org.apache.commons:commons-compress:1.8.1",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-compress/1.8.1/commons-compress-1.8.1.jar"
        ]
      ],
      "dependencies": []
    },
    {
      "coord": "org.xerial.snappy:snappy-java:1.1.1.3",
      "files": [
        [
          "",
          "<coursier_cache>/Library/Caches/Coursier/v1/https/repo1.maven.org/maven2/org/xerial/snappy/snappy-java/1.1.1.3/snappy-java-1.1.1.3.jar"
        ]
      ],
      "dependencies": []
    }
  ]
}
```