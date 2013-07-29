from docutils import nodes
from docutils.parsers.rst import Directive, directives
from sphinx.util.nodes import set_source_info

# node that will be replaced by an index of howto topics
# along with brief examples
class howtoindex(nodes.General, nodes.Element): pass

# The howtoindex directive takes no arguments, options, or content.
# It will be replaced by an index of howto topics.
# All howto directives will be indexed and linked to along with
# brief examples.  The howto sections are grouped by the page in which
# they occur.
class HowtoindexDirective(Directive):

    def run(self):
        return [howtoindex('')]

# Defines a howto section, which takes no arguments.
# The content of the howto directive is the example that is given in the index.
# The content may be omitted, in which case the index will not show an example.
# The howto section itself is replaced by a heading with :title: as the text.
#
# Required options:
#  :id:      Identifier used for linking.  Must be unique within the enclosing document.
#  :title:   The title of the section.
# Optional options:
#  :reftext: The text of the link to the section in the index.  :title: is used if unspecified.
#  :type:    The kind of example that the content represents.  Possible values are:
#     text      The content is included in a literal block with lang=text.  This is the default.
#     setting   The content is included in a literal block with lang=scala.
#     batch     The content is prefixed with '$ ' and is included in a literal block with lang=console.
#     command   The content is prefixed with '> ' and is included in a literal block with lang=console.
#     commands  The content is included in a literal block with lang=console.
#
class HowtoDirective(Directive):

    has_content = True
    option_spec = {
        'id': directives.unchanged_required,
        'title': directives.unchanged_required,
        'type': directives.unchanged,
        'reftext': directives.unchanged,
    }

    def run(self):
        doc = self.state.document
        env = doc.settings.env
        # env.titles doesn't contain the title for env.docname yet
        # and doc.title nor doc['title'] exist yet either
        doctitle = get_title(doc, env)
        
        # needs to be normalized here so that the reference and target agree
        targetid = nodes.fully_normalize_name(self.options['id'])
        titleText = self.options['title']
        # default link text is the title
        reftext = titleText
        # default type is 'text'
        tpe = 'text'
        if 'reftext' in self.options:
            reftext = self.options['reftext']
        if 'type' in self.options:
            tpe = self.options['type']

        # a section+title make the howto have a prominent heading, a link, and entry in a toc
        newsection = make_section(targetid, 'full-howto', doc)
        # title nodes should be the child of a section to work properly
        titlenode = nodes.title(targetid, titleText)
        newsection += titlenode
        # wrap the necessary information for this howto ...
        howto_info = {
            'docname': env.docname,
            'doctitle': doctitle,
            'target': targetid,
            'reftext': reftext,
            'type': tpe,
        }
        if self.content:
            howto_info['content'] = self.content

        # ... and store it in the shared howto list
        if not hasattr(env, 'howto_all_howtos'):
            env.howto_all_howtos = []
        env.howto_all_howtos.append(howto_info)

        # replace the howto directive with the new section, which includes a title/heading
        return [newsection]

# register the howtoindex node and the howto and howtoindex directives,
# process_howto_nodes will replace howtoindex nodes with the actual index of howtos
# purge_howtos drops data for changed documents
def setup(app):
    app.add_node(howtoindex)
    app.add_directive('howto', HowtoDirective)
    app.add_directive('howtoindex', HowtoindexDirective)
    app.connect('doctree-resolved', process_howto_nodes)
    app.connect('env-purge-doc', purge_howtos)

# remove the howto data that came from `docname`
def purge_howtos(app, env, docname):
    if not hasattr(env, 'howto_all_howtos'):
        return
    env.howto_all_howtos = [howto for howto in env.howto_all_howtos
                          if howto['docname'] != docname]

# constructs a reference to `link` with content `txt`
def make_reference(txt, link):
    ref = nodes.reference('', '')
    ref['refuri'] = link
    ref.append( nodes.Text(txt, txt) )
    return ref

# Generates the actual index of howtos and replaces howtoindex nodes with it
def process_howto_nodes(app, doctree, fromdocname):
    env = app.builder.env

    # gets all howtoindex nodes
    for node in doctree.traverse(howtoindex):
        # dictionary that groups howtos by document name
        howtos_by_doc = {}

        # iterates over all howtos 
        for howto_info in env.howto_all_howtos:
            docname = howto_info['docname']
            if docname in howtos_by_doc:
                doc = howtos_by_doc[docname]
            else:
                doc = {
                    'name': docname,
                    'link': app.builder.get_relative_uri(fromdocname, docname),
                    'title': howto_info['doctitle'],
                    'infos': [],
                }
                howtos_by_doc[docname] = doc
            
            doc['infos'].append(howto_info)

        content = []
        sorted_names = sorted(howtos_by_doc.keys())
        for docname in sorted_names:
            doc = howtos_by_doc[docname]
            doc_link = doc['link']
            section = howto_section(doc['title'], doc_link, doctree)
            for howto_info in doc['infos']:
                section_link = doc_link + '#' + (howto_info['target'])
                section += howto_summary(howto_info, section_link, doctree)
            content.append( section )

        node.replace_self(content)

# constructs a section with a title `title_text` with a `(details)` link to `link` 
def howto_section(title_text, link, document):
    name = nodes.fully_normalize_name(title_text)
    section = make_section(name, 'doc-howto-summary', document)
    section += make_title_ref(title_text, link)
    return section

# Constructs a title node with text `title_text`
#  followed by a link to `link` with label "(details)".
def make_title_ref(title_text, link):
    ref = make_reference(' (details)', link)
    t = nodes.Text(title_text)
    # A title can only contain text and inline elements.
    # Therefore, the text and reference elements need to be passed directly
    # as children and not combined in a paragraph or other element.
    return nodes.title('', '', t, ref)

# Constructs a new section with the given `name` and in `document`.
# This properly sets up the id so that sphinx doesn't generate an
# error in later processing.
def make_section(name, classname, document):
    section = nodes.section()
    section.document = document
    section['classes'].append(classname)
    # setting the name might not be necessary
    if not document.has_name(name):
        section['names'].append(name)
    # this avoids an error from sphinx
    document.note_implicit_target(section)
    return section

# Constructs a literal block that joins `content` lines by `sep` and prefixes `prefix`.
# The highlighting language is set to `lang`
def new_code_block(prefix, lang, content, sep):
    code = prefix + sep.join(content)
    block = nodes.literal_block(code, code)
    block['language'] = lang
    return block

# Generates the section including the title and short example for a howto.
# The title contains a (details) link to the full howto.
def howto_summary(howto_info, section_link, document):
    para = nodes.paragraph()
    para += howto_summary_content(howto_info)

    title_text = howto_info['reftext']
    section = make_section(title_text, 'howto-summary', document)
    section += make_title_ref(title_text, section_link)
    section += para
    return section

# Generates the short example content from the howto dictionary
def howto_summary_content(howto):
    if not ('content' in howto):
        return nodes.Text('')

    tpe = howto['type']
    content = howto['content']
    if tpe == 'setting':
        return new_code_block('', 'scala', content, '\n')
    elif tpe == 'batch':
        return new_code_block('$ sbt ', 'console', content, ' ')
    elif tpe == 'command':
        return new_code_block('> ', 'console', content, ' ')
    elif tpe == 'commands':
        return new_code_block('', 'console', content, '\n')
    else:
        return new_code_block('', tpe, content, '\n')

# Obtains the title for a document `doc`.
# This is necessary because at the time the title is needed, the 'title' attribute hasn't
# been set on the document or registered in the environment.
# `docname` is used for generating an error title when finding the title is unsuccessful
def get_title(doc,docname):
    if doc.hasattr('title'):
        doctitle = doc['title']
    elif doc.settings.title is not None:
        doctitle = doc.settings.title
    elif len(doc):
        if isinstance(doc[0], nodes.title):
            doctitle = doc[0][0].astext()
        elif len(doc[0]) and isinstance(doc[0][0], nodes.title):
            doctitle = doc[0][0].astext()
        else:
            doctitle = no_title(docname)
    else:
        doctitle = no_title(docname)
    return doctitle

# string to use when the title for a document cannot be determined
def no_title(docname):
    return "no title for " + docname
