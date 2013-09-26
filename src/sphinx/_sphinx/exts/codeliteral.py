from docutils import nodes
from sphinx.util.nodes import set_source_info


class Struct:
    def __init__(self, **keywordargs):
        self.__dict__.update(keywordargs)

def process_node(node):
    if isinstance(node, nodes.Text):
        node = nodes.inline('', node.astext())
    else:
        node = nodes.inline('', '', node)
    node['classes'].append('pre')
    return node

# This role formats a string to be in a fixed width font.
# The string is taken as a literal and is not processed for further inline formatting.
def code_literal(name, rawtext, text, lineno, inliner, options={}, content=[]):
    node = nodes.inline('', text)
    node['classes'].append('pre')
    return [node], []

# This role formats a string to be in a fixed width font.
# It processes nested inline formatting, substitutions in particular.
def sub_literal(name, rawtext, text, lineno, inliner, options={}, content=[]):
    memo = Struct(document=inliner.document,
                           reporter=inliner.reporter,
                           language=inliner.language,
                           inliner=inliner)

    nested_parse, problems = inliner.parse(text, lineno, memo, inliner.parent)
    nodes = [process_node(node) for node in nested_parse ]
    return nodes, problems

# register the role
def setup(app):
    app.add_role('sublit', sub_literal)
    app.add_role('codeliteral', code_literal)
