---
parent: Requirements
---

# Export

## Export BIBFRAME 2.0 RDF/XML
`req~export.bibframe.rdfxml~1`

JabRef exports each entry as a linked BIBFRAME 2.0 Work and Instance in striped RDF/XML. Resource identifiers are deterministic for unchanged bibliographic content, unique for duplicate entries within one file, and independent of citation keys.

See [ADR 0076](../decisions/0076-support-bibframe-alongside-marc21.md) for the decision to support BIBFRAME alongside MARC 21.

Needs: impl, utest

## Exports can be defined as Apache Velocity templates
`req~logic.exporter.velocity-template~1`

An export format can be defined as a single [Apache Velocity](https://velocity.apache.org/) template
(instead of a set of per-entry-type `.layout` files).
The template iterates over the sorted entries (`$entries`); each entry offers its type, citation key,
and field values (with field aliases, BibTeX strings, and `crossref` inheritance resolved) as plain strings,
where an unset field is the empty string.
See [ADR 39](../decisions/0039-use-apache-velocity-as-template-engine.md) for the engine choice.

Needs: impl

<!-- markdownlint-disable-file MD013 MD033 MD041 -->
<!-- markdownlint-disable-file MD022 -->
