# iXML

This project contains experiments with
[S. Pemberton's "invisible XML"](http://homepages.cwi.nl/~steven/Talks/2013/08-07-invisible-xml/invisible-xml-3.html) idea.
Parsing Expression Grammars (PEG) are used to parse anything into XML.
Specifically, we use a [modified version of Waxeye](https://github.com/nverwer/waxeye)
integrated into [Apache Cocoon 2.1](http://cocoon.apache.org/2.1/).
The integration code is not yet open sourced by the Dutch government, but code for a Cocoon 2.1 generator is included.

The first experiment is a parser for [M. Kay's FtanML](http://www.balisage.net/Proceedings/vol10/html/Kay01/BalisageVol10-Kay01.html).
The parser can handle all FtanML markup, except cells. This is caused by a limitation of Waxeye.

The "Anything To XML.pptx" presentation explains the ideas behind all this.
 