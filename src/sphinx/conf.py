# -*- coding: utf-8 -*-

extensions = ['sphinxcontrib.issuetracker', 'sphinx.ext.extlinks']

# Project variables

project = 'sbt'
version = '0.12'
release = '0.12.1'

# General settings

needs_sphinx = '1.1'
nitpicky = True
default_role = 'literal'
master_doc = 'index'
highlight_language = 'scala'
add_function_parentheses = False

# HTML

html_title = 'sbt Documentation'
html_domain_indices = False
html_use_index = False
html_show_sphinx = False
htmlhelp_basename = 'sbtdoc'
html_use_smartypants = False

# Issues role

issuetracker = 'github'
issuetracker_project = 'harrah/xsbt'
issuetracker_plaintext_issues = True
issuetracker_issue_pattern = r'\bgh-(\d+)\b'
issuetracker_title_template = '#{issue.id}'

# links, substitutions

typesafe_base = 'http://repo.typesafe.com/typesafe/'
typesafe_ivy_snapshots = typesafe_base + 'ivy-snapshots/'
typesafe_ivy_releases = typesafe_base + 'ivy-releases/'
launcher_release_base = typesafe_ivy_releases + 'org.scala-sbt/sbt-launch/'
launcher_snapshots_base = typesafe_ivy_snapshots + 'org.scala-sbt/sbt-launch/'


rst_epilog = """
.. _typesafe-snapshots: %(typesafe_ivy_snapshots)s
.. |typesafe-snapshots| replace:: Typesafe Snapshots
.. _sbt-launch.jar: %(launcher_release_base)s/%(version)s/sbt-launch.jar
.. _msi: %(launcher_release_base)s/%(version)s/sbt.msi
.. |nightly-launcher| replace:: <%(launcher_snapshots_base)s
.. _mailing list: http://groups.google.com/group/simple-build-tool/topics
.. _source code: http://github.com/harrah/xsbt
""" % {
   'launcher_release_base': launcher_release_base,
   'launcher_snapshots_base': launcher_snapshots_base,
   'version': release,
   'typesafe_ivy_snapshots': typesafe_ivy_snapshots
}
