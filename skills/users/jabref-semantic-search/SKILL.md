---
name: jabref-semantic-search
category: users
description: Semantic search in the PDFs of a JabRef library - asks the running JabRef for the passages of the linked files that are most similar in meaning to a question and returns citation key, page, score, and text. Use to find which papers of a library say something about a topic, or to find a passage to cite.
license: MIT
---

# Semantic search in the PDFs of a JabRef library

JabRef generates embeddings for the files linked to the entries of a library.
The running JabRef offers a search over them at its HTTP server: "which of my PDFs say something about X?"

In contrast to `jabkit search` (see the `jabkit` skill), which matches the fields of the entries, this searches the *full text* of the linked files by meaning.

## Prerequisites

JabRef (the desktop application) has to run, with

- the library opened,
- the HTTP server enabled: *File > Preferences > General > Enable HTTP server* (port 23119),
- the AI features enabled: *File > Preferences > AI*.

Nothing has to be installed; the examples use `curl`.
There is no command-line variant: the embeddings belong to the running JabRef.

Check whether the server runs and which libraries are open:

```bash
curl -s http://localhost:23119/libraries
```

```json
[
  "literature.bib-17d0d20e",
  "demo"
]
```

The strings are the library ids for the next call.
`current` works as id, too, and denotes the library focused in JabRef.
If the connection is refused, ask the user to start JabRef and to enable the HTTP server.

## Search

```bash
curl -s http://localhost:23119/libraries/literature.bib-17d0d20e/ai/search \
  -H "Content-Type: application/json" \
  -d '{"query": "How do deployment models handle heterogeneous infrastructure?", "maxResults": 10, "minScore": 0.5}'
```

- `query` (required): a question or statement in natural language. Full sentences work better than keywords.
- `maxResults` (optional): number of passages returned at most. Default: JabRef's AI preferences.
- `minScore` (optional): passages with a lower score (0 to 1) are left out. Default: JabRef's AI preferences.

Percent-encode special characters in the library id (a space is `%20`).

```json
{
  "matches": [
    {
      "citationKey": "Wurster2019",
      "file": "Wurster2019 - The Essential Deployment Metamodel.pdf",
      "page": 4,
      "score": 0.83,
      "text": "… the passage of the PDF …"
    }
  ],
  "pendingFiles": [
    "Bergmayr2018 - A Systematic Review of Cloud Modeling Languages.pdf"
  ],
  "notFoundFiles": [
    "H:/papers/Endres2017.pdf"
  ]
}
```

- `matches`: the most similar passages, best first. `citationKey` is `{NONE}` if the entry has no citation key; `page` is absent for files without pages.
- `pendingFiles`: linked files JabRef has not ingested yet. They were **not** searched. The call started their ingestion, which can take minutes; repeat the search later.
- `notFoundFiles`: linked files JabRef could not read. They were **not** searched.

## Notes for agents

- An empty `matches` does not mean "nothing in the library": check `pendingFiles` and `notFoundFiles` and tell the user which files were not searched.
- A passage is a chunk of a page and can start or end in the middle of a sentence. Before quoting, read the page in the PDF. The path of the file is available at `GET http://localhost:23119/libraries/{id}/entries/{citationKey}/files`.
- A high score means "similar in meaning", not "supports the claim". Read the passage.
- Ask several differently worded queries for one topic; the results differ.
- Author, title, and year of a hit: `curl -s http://localhost:23119/libraries/{id}/entries/{citationKey}`.
- PowerShell: call `curl.exe` (not the alias `curl`) and put the JSON into a file to avoid quoting issues: `curl.exe -s ... -H "Content-Type: application/json" -d "@query.json"`.

## Errors

| Status | Meaning |
|---|---|
| 400 | `query` is missing or blank |
| 403 | The request carries an `Origin` header. The endpoint does not answer web pages; call it without that header |
| 404 | No open library has this id. List the ids at `/libraries` |
| 503 | Semantic search is not available. The body tells why: the AI features are disabled, the embedding model is still loading, or the server runs without the JabRef GUI |
