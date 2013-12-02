from docutils import nodes
from sphinx.util.nodes import set_source_info
from sphinx.util.osutil import relative_uri
import os

# This role inserts a link to the source rST on GitHub.
def srcref(name, rawtext, text, lineno, inliner, options={}, content=[]):
    srcPath = inliner.document.settings.env.docname
    srcUri = "%s%s.rst" % (os.environ['sbt.site.source.base'], srcPath)
    refNode = nodes.reference('', 'Source for this page', internal=False, refuri=srcUri)
    spanNode = nodes.paragraph()
    spanNode.append(refNode)
    spanNode['classes'].append('page-source')
    return [spanNode], []

# register the role
def setup(app):
    app.add_role('srcref', srcref)

