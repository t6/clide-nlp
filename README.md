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

[<img width="40%" src="https://raw.githubusercontent.com/t6/snippets/master/docs/screenshots/semantic-graph.png">](https://raw.githubusercontent.com/t6/snippets/master/docs/screenshots/semantic-graph.png)
[<img width="40%" src="https://raw.githubusercontent.com/t6/snippets/master/docs/screenshots/draw.png">](https://raw.githubusercontent.com/t6/snippets/master/docs/screenshots/draw.png)

See my bachelor report [An NLP Assistant for
Clide](http://arxiv.org/abs/1409.2073v1) for more information.

`clide-nlp`'s triple mining algorithm is implemented in a
standalone library called
[`snippets`](https://github.com/t6/snippets).

## Usage

Install [Leiningen](http://leiningen.org/) and then run

```bash
lein run
```

Startup might take a while, make sure you have > 2.5 GiB RAM.  Clide
with `clide-nlp` will be available on http://localhost:14000
afterwards (User: `clide-nlp` / Password: `clide-nlp`).

To get a REPL run

```bash
lein repl
```

## License

Copyright © 2014 Tobias Kortkamp

Distributed under the GNU Lesser General Public License either version
3 or (at your option) any later version.
