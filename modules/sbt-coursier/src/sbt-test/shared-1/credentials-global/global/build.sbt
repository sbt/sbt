coursierExtraCredentials += lmcoursier.credentials.Credentials(
  uri(sys.env("TEST_REPOSITORY")).getHost,
  sys.env("TEST_REPOSITORY_USER"),
  sys.env("TEST_REPOSITORY_PASSWORD")
).withHttpsOnly(false).withMatchHost(true)
