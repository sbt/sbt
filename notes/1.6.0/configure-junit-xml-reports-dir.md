[@ashleymercer]: https://github.com/ashleymercer

[#2853]: https://github.com/sbt/sbt/issues/2853

### Fixes with compatibility implications

- Add a new setting `testReportsDirectory` to allow output directory for JUnitXmlTestsListener to be configured,
  and expose `testReportSettings` which provides defaults for new configurations [#2853][] by [@ashleymercer][]
  - output directory now uses the configuration name as a prefix so `target/test-reports` for the `Test` configuration
    but `target/it-reports` for the `IntegrationTest` configuration (previously this was hardcoded to always use
    `target/test-reports`). To override this set e.g. `Test / testReportsDirectory := target.value / "my-custom-dir"`
  - the `JunitXmlTestsListener` is now only attached to the `Test` and `IntegrationTest` configurations by default
    (previously it was added to the global configuration object). Any configurations which inherit from one of these
    will therefore continue to have the listener attached; but completely custom configurations will need to re-add
    it with `.settings(testReportSettings)`