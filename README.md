# clide-nlp

`clide-nlp` is an assistant for the collaborative development
environment `Clide`, that supports the development of NLP applications
by providing easy access to some common NLP data structures. The
assistant visualizes text fragments and their dependencies by
displaying the semantic graph of a sentence, the coreference chain of
a paragraph and mined triples that are extracted from a paragraph's
semantic graphs and linked using its coreference chain. Using this
information and a logic programming library, we create an NLP database
which is used by a series of queries to mine the triples. The
algorithm is tested by translating a natural language text describing
a graph to an actual graph that is shown as an annotation in the text
editor.

TODO: Screenshot

Also see http://arxiv.org/abs/1409.2073v1

## Usage

```bash
# lein run
```

Startup might take a while, make sure you have > 2.5 GiB RAM.  Clide
with `clide-nlp` will be available on http://localhost:14000
afterwards (User: `clide-nlp` / Password: `clide-nlp`).

### WARNING

`clide-nlp` includes a component that allows live editing of triple
queries. The queries are `eval`ed and thus allow executing arbitrary
Clojure code. I suggest you only run `clide-nlp` on a trusted network.

## License

Copyright © 2014 Tobias Kortkamp

Distributed under the GNU Lesser General Public License either version
3 or (at your option) any later version.
