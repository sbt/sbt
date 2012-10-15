# -*- coding: utf-8 -*-

import sys, os

sys.path.append(os.path.abspath('_sphinx/exts'))
extensions = ['sphinxcontrib.issuetracker', 'sphinx.ext.extlinks', 'howto']

# Project variables

project = 'sbt'
version = '0.13'
release = '0.13.0-SNAPSHOT'

# General settings

needs_sphinx = '1.1'
nitpicky = True
default_role = 'literal'
master_doc = 'home'
highlight_language = 'scala'
add_function_parentheses = False

# HTML

html_theme = 'sbt'
html_theme_path = ['_sphinx/themes']
html_title = 'sbt Documentation'
html_domain_indices = False
html_use_index = False
html_show_sphinx = False
htmlhelp_basename = 'sbtdoc'
html_use_smartypants = False

# if true:
#  the Home link is to scala-sbt.org
# if false:
#  the Home link is to home.html for the current documentation version
# TODO: pass this as an argument to sphinx
home_site = True

# Passed to Google as site:<site_search_base>
# If empty, no search box is included
# TODO: pass this as an argument to sphinx, use actual version instead of release 
site_search_base = 'http://www.scala-sbt.org/release/docs'

# passes variables to the template
html_context = {'home_site': home_site, 'site_search_base': site_search_base}

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
sbt_native_package_base = 'http://scalasbt.artifactoryonline.com/scalasbt/sbt-native-packages/org/scala-sbt/sbt/'


rst_epilog = """
.. _typesafe-snapshots: %(typesafe_ivy_snapshots)s
.. |typesafe-snapshots| replace:: Typesafe Snapshots
.. _sbt-launch.jar: %(launcher_release_base)s/%(version)s/sbt-launch.jar
.. _MSI: %(sbt_native_package_base)s/%(version)s/sbt.msi
.. _TGZ: %(sbt_native_package_base)s/%(version)s/sbt.tgz
.. _ZIP: %(sbt_native_package_base)s/%(version)s/sbt.zip
.. _DEB: %(sbt_native_package_base)s/%(version)s/sbt.deb
.. _RPM: %(sbt_native_package_base)s/%(version)s/sbt.rpm
.. |nightly-launcher| replace:: <%(launcher_snapshots_base)s
.. _mailing list: http://groups.google.com/group/simple-build-tool/topics
.. _source code: http://github.com/harrah/xsbt
""" % {
   'launcher_release_base': launcher_release_base,
   'launcher_snapshots_base': launcher_snapshots_base,
   'version': release,
   'typesafe_ivy_snapshots': typesafe_ivy_snapshots,
   'sbt_native_package_base': sbt_native_package_base
}

