# -*- coding: utf-8 -*-

import sys, os

sys.path.append(os.path.abspath('_sphinx/exts'))
extensions = ['sphinxcontrib.issuetracker', 'sphinx.ext.extlinks', 'howto', 'codeliteral', 'key', 'srcref']

# Project variables

project = 'sbt'
version = os.environ['sbt.partial.version']
site_version = os.environ['sbt.site.version']
release = os.environ['sbt.full.version']
scalaVersion = os.environ['scala.binary.version']
scalaRelease = os.environ['scala.full.version']

# General settings

needs_sphinx = '1.1'
nitpicky = True
default_role = 'codeliteral'
master_doc = 'home'
highlight_language = 'scala'
add_function_parentheses = False

# TODO: make this an argument
#  pdf_index should be excluded when generating html
#  index.rst should be excluded when generating a pdf
exclude_patterns = [ 'pdf_index.rst' ]

# HTML

html_theme = 'sbt'
html_theme_path = ['_sphinx/themes']
html_title = 'sbt Documentation'
html_domain_indices = False
html_use_index = False
html_show_sphinx = False
htmlhelp_basename = 'sbtdoc'
html_use_smartypants = False
html_copy_source = False

# if true:
#  the Home link is to scala-sbt.org
# if false:
#  the Home link is to home.html for the current documentation version
# TODO: pass this as an argument to sphinx
home_site = True

# Passed to Google as site:<site_search_base>
# If empty, no search box is included
site_search_base = 'http://www.scala-sbt.org/' + site_version + '/docs'

# passes variables to the template
html_context = {'home_site': home_site, 'site_search_base': site_search_base}

# Latex (PDF)

#latex_documents = [
#  ('pdf_index', 'sbt.tex', html_title, '', 'manual', True),
#  ('Getting-Started/index', 'sbt-Getting-Started.tex', html_title, '', 'manual', True),
#]

# Issues role

issuetracker = 'github'
issuetracker_project = 'sbt/sbt'
issuetracker_plaintext_issues = True
issuetracker_issue_pattern = r'\bgh-(\d+)\b'
issuetracker_title_template = '#{issue.id}'

# links, substitutions

typesafe_base = 'http://repo.typesafe.com/typesafe/'
typesafe_ivy_snapshots = typesafe_base + 'ivy-snapshots/'
typesafe_ivy_releases = typesafe_base + 'ivy-releases/'
launcher_release_base = typesafe_ivy_releases + 'org.scala-sbt/sbt-launch/'
launcher_snapshots_base = typesafe_ivy_snapshots + 'org.scala-sbt/sbt-launch/'
sbt_native_package_base = 'http://repo.scala-sbt.org/scalasbt/sbt-native-packages/org/scala-sbt/sbt/'

rst_epilog = """
.. |scalaVersion| replace:: %(scalaVersion)s
.. |scalaRelease| replace:: %(scalaRelease)s
.. _typesafe-snapshots: %(typesafe_ivy_snapshots)s
.. |typesafe-snapshots| replace:: Typesafe Snapshots
.. _sbt-launch.jar: %(launcher_release_base)s%(release)s/sbt-launch.jar
.. _MSI: %(sbt_native_package_base)s%(release)s/sbt.msi
.. _TGZ: %(sbt_native_package_base)s%(release)s/sbt.tgz
.. _ZIP: %(sbt_native_package_base)s%(release)s/sbt.zip
.. _DEB: %(sbt_native_package_base)s%(release)s/sbt.deb
.. _RPM: %(sbt_native_package_base)s%(release)s/sbt.rpm
.. |nightly-launcher| replace:: %(launcher_snapshots_base)s
.. _sbt-dev mailing list: https://groups.google.com/forum/#!forum/sbt-dev
.. _adept: https://groups.google.com/group/adept-dev/topics
.. _sbt-launcher-package: https://github.com/sbt/sbt-launcher-package
.. _Stack Overflow: http://stackoverflow.com/tags/sbt
.. _source code: http://github.com/sbt/sbt

:srcref:`ignored`
""" % {
   'launcher_release_base': launcher_release_base,
   'launcher_snapshots_base': launcher_snapshots_base,
   'typesafe_ivy_snapshots': typesafe_ivy_snapshots,
   'sbt_native_package_base': sbt_native_package_base,
   'scalaRelease': scalaRelease,
   'scalaVersion': scalaVersion,
   'release': release
}
