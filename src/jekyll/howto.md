---
layout: content
title: How to...
description: How to do common tasks.
---

{% capture table %}
{% for page in site.pages %}
  {% if page.sections %}
### {{page.title}} [(details)]({{page.url}}) ###
    {% for section in page.sections %}
... {{section.name}} [(details)]({{page.url | append: "#" | append: section.id}})
  {% if section.short %} {{section.short | strip_newlines | markdownify}}
  {% elsif section.batch %} {% highlight console %} $ sbt {{section.batch | strip_newlines}} {% endhighlight %}
  {% elsif section.setting %} {% highlight scala %} {{section.setting | strip_newlines}} {% endhighlight %}
  {% elsif section.command %} {% highlight console %} > {{section.command | strip_newlines}} {% endhighlight %}
  {% elsif section.commands %} {% highlight console %} {{section.commands}} {% endhighlight %}
  {% endif %}


    {% endfor %}
  {% endif %}
{% endfor %}
{% endcapture %}

{{ table | unindent | markdownify }}