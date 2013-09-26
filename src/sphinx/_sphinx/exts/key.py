from docutils import nodes
from sphinx.util.nodes import set_source_info
from sphinx.util.osutil import relative_uri

# node that will have its link that is relative to root be made relative to
#  the referencing document
class sibling_ref(nodes.General, nodes.Inline, nodes.TextElement): pass

# This role interprets its argument as the name of a `val` in `sbt.Keys`.
# The description of the key is used as the title and the name is linked to
# the sxr documentation for the key.
def sbt_key(name, rawtext, text, lineno, inliner, options={}, content=[]):
    refNode = sibling_ref('', '')
    refNode.keyName = text
    refNode.description = 'faked'
    return [refNode], []

# register the role
# process_sibling_ref_nodes will replace sibling_ref nodes with proper links
# purge_siblings drops data for changed documents
def setup(app):
    app.add_role('key', sbt_key)
    app.add_node(sibling_ref)
    app.connect('doctree-resolved', process_sibling_ref_nodes)


# Resolves sibling_ref links relative to the referencing document
def process_sibling_ref_nodes(app, doctree, fromdocname):
    fromTargetURI = app.builder.get_target_uri(fromdocname)
    # resolves links for all sibling_ref nodes
    for node in doctree.traverse(sibling_ref):
        rellink = '../sxr/sbt/Keys.scala.html#sbt.Keys.%s' % node.keyName
        newuri = relative_uri(fromTargetURI, rellink)
        refNode = nodes.reference('', node.keyName, internal=False, refuri=newuri)
        refNode['classes'].append('pre')
        refNode['title'] = node.description
        node.replace_self(refNode)
