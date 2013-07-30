from docutils import nodes
from sphinx.util.nodes import set_source_info


class Struct:

    """Stores data attributes for dotted-attribute access."""

    def __init__(self, **keywordargs):
        self.__dict__.update(keywordargs)

def process_node(node):
    if isinstance(node, nodes.Text):
        node = nodes.inline('', node.astext())
    else:
        node = nodes.inline('', '', node)
    node['classes'].append('pre')
    print ("NODE: %s" % node)
    return node

# This directive formats a string to be in a fixed width font.
# Only substitions in the string are processed.  

def code_literal(name, rawtext, text, lineno, inliner, options={}, content=[]):
    memo = Struct(document=inliner.document,
                           reporter=inliner.reporter,
                           language=inliner.language,
                           inliner=inliner)

    nested_parse, problems = inliner.parse(text, lineno, memo, inliner.parent)
    nodes = [process_node(node) for node in nested_parse ]
    return nodes, problems

# register the role
def setup(app):
    app.add_role('codeliteral', code_literal)
