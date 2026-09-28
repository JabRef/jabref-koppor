# Plan: the glossary drives the domain model

Two decisions, each its own ADR plus one mechanical PR. This file is the review object; nothing else is in this PR.
Numbers are from `main` at 046d7063bd (2026-09-28).

**Ground rule.** `docs/glossary/` is the ubiquitous language (DDD). A name in code is a glossary term or a compound of glossary terms.
A term the code needs and the glossary lacks is added to the glossary first, in the same PR.

## 1. Merge `org.jabref.model` and `org.jabref.logic`

### Why merge

- ADR-0017 chose "model may use logic in defined cases" to avoid an anemic domain model. Today 28 of 268 model classes carry `@AllowedToUseLogic`; 17 of them read "Uses StringUtil temporarily". The exception is a ritual, not a boundary.
- The wall still produces the anemic model it wanted to prevent: behaviour lives in transaction scripts next to the data. `DatabaseMerger.merge(BibDatabase, BibDatabase)`, `BibDatabaseModeDetection.inferMode(BibDatabase)`, `DuplicateCheck`, `BibDatabaseDiff`; even `BibDatabases.purgeEmptyEntries` is a static helper inside `model`.
- Twelve packages exist on both sides (`ai`, `biblog`, `database`, `groups`, `icore`, `ocr`, `openoffice`, `pdf`, `search`, `texparser`, `undo`, `util`). The tree is split by "is it data?", not by feature: low cohesion, and every new class needs a placement decision.
- The boundary that matters (no `org.jabref.gui` in the library) is enforced elsewhere already: the Gradle module `jablib` cannot see `jabgui`, and ArchUnit `doNotUseGuiInLogic` double-checks.

### Options

| Option | Cost | Result |
| --- | --- | --- |
| A. Move `org.jabref.model.*` into `org.jabref.logic.*`; twin packages merge, model-only packages (`entry`, `metadata`, `study`, …) move as they are | 268 files, one OpenRewrite recipe | One package tree, no exception list |
| B. Package by feature under new roots (`org.jabref.library`, `org.jabref.entry`, …) | 1,243 files, a design decision per package | Best cohesion, months of conflicts |
| C. Keep packages, delete the ArchUnit rule and the annotation | 30 files | `model` keeps a name it does not honour; twins stay |

**Recommendation: A.** B is done later, package by package, when a package is being reworked anyway. C leaves the misleading name.

Merging removes the excuse, not the anemia. The rich model then follows a rule of thumb applied at every touch, not as a project:

- Needs only its own state → method on the object. `library.merge(other)` (the keyword separator already sits in the library's `MetaData`), `content.inferMode()`, `entries.purgeEmpty()`.
- Needs I/O, preferences of other objects, or several aggregates → service class in the same package (`BibtexParser`, `BibDatabaseWriter`, fetchers). Preferences stay parameters.

### Steps to merge

1. ADR (next free number) "One package tree for the domain": supersedes ADR-0017, records options A–C and the rule of thumb above. Sets ADR-0017 `status: superseded`.
2. Delete `AllowedToUseLogic` and its 28 usages; delete `doNotUseLogicInModel`; fold `restrictUsagesInModel` into `restrictUsagesInLogic` (its three `model.search.rules.*` exceptions name classes that no longer exist). Update `AGENTS.md` (lines 44–45, 228, 231), `CHECKLIST.md` (model/logic test rule), `docs/code-howtos/faq.md` (the `doNotUseLogicInModel` entry).
3. OpenRewrite recipe: `ChangePackage` for each of the 20 model packages, `ChangeType` for the five classes at the `model` root (`ChainNode`, `FieldChange`, `TreeNode`, `TransferInformation`, `TransferMode` → `logic.util`). Non-Java references: 41 `exports` lines in `jablib/module-info.java`, two `reachability-metadata.json`, `tinylog-test.properties`, six pages under `docs/`. Tests move with their classes; no behaviour change.

## 2. Rename `database` to `library`

### Why rename

The glossary reserves *database* for SQL storage and defines *library* as the `.bib` file with its entries and metadata. The code says both: `LibraryTab.getBibDatabaseContext()`, `StateManager.getActiveDatabase()` in 107 files next to `getActiveLibrary()` in 3. `BibDatabaseContext` has 2,292 usages, `BibDatabase` 1,139, `BibDatabaseMode` 619.

### Mapping

| Today | Proposed | Note |
| --- | --- | --- |
| `BibDatabaseContext` | `Library` | Content, `MetaData`, path, location: exactly what a `LibraryTab` shows. `LibraryTab.getLibrary()` |
| `BibDatabase` | `LibraryFile` (as requested) | See open question 2 |
| `BibDatabaseMode`, `BibDatabaseModeDetection` | `LibraryMode`, `LibraryModeDetection` | |
| `BibDatabases`, `BibDatabaseContextChangedEvent` | fold into the object; `LibraryChangedEvent` | |
| `DatabaseLocation {LOCAL, SHARED}` | `LibraryLocation` | Follow-up below |
| `logic`: `DatabaseMerger`, `BibDatabaseDiff`, `BibDatabaseWriter`, `OpenDatabase`, `DatabaseChecker`, `DatabaseFileLookup`, `DatabaseCitationKeyPatterns`, `*AiDatabaseListener` (3), `PerformLoadDatabaseMigrations` | `Library…` | 11 classes |
| `gui`: `SaveDatabaseAction`, `OpenDatabaseAction`, `NewDatabaseAction`, `collab.DatabaseChange*` (9) | `Library…` | 12 classes |
| `getActiveDatabase()`, `getOpenDatabases()`, `getDatabase()`, fields `bibDatabaseContext`/`databaseContext` | `getActiveLibrary()`, `getOpenLibraries()`, `getContent()`, `library` | IntelliJ rename, not a recipe |
| `logic.shared.*` (`DatabaseConnection`, `DatabaseSynchronizer`, `SharedDatabase*`), `MSBibDatabase`, `OOCalcDatabase` | unchanged | These are SQL databases or foreign formats |
| l10n: "No database is open", "Database:", the pre-3.6 migration texts | "library" | 8 of 18 `database` strings; the shared/online ones stay. Translations of the 8 are lost and redone on Crowdin |

**Follow-up, not in the rename PR.** `BibDatabaseContext` keeps a nullable `path`, a nullable `dbmsSynchronizer` and a `location` flag that `convertToSharedDatabase`/`convertToLocalDatabase` must keep consistent. A sealed `LibraryLocation` (`Unsaved`, `File(path)`, `SharedDatabase(synchronizer)`) makes the glossary distinction a type instead of three fields.

### Steps to rename

1. Glossary: add `database.md` (SQL storage behind a shared library; JabRef term "shared database") so the reservation is citable. Fix `library.md` (broken `[citation key]` link) and `references.md` ("a bibliography corresponds to a .bib file" contradicts `library.md`).
2. ADR (next free number) "Code names follow the glossary: library, not database". One paragraph, the mapping table, the exclusions.
3. OpenRewrite `ChangeType` recipe for the 29 class renames, then IntelliJ rename for methods and fields, then the 8 l10n strings. Runs after step 1.3 so files move once.

## 3. Mechanics shared by both PRs

- **Reviewable by re-running.** Each PR = recipe file + `module-info.java` + docs. The reviewer runs the recipe on `main` and expects an empty diff against the PR.
- **Ship the recipe.** The recipe stays in the repository for one release: `org.jabref:jablib` is on Maven Central, and its users migrate with the same recipe instead of a changelog paragraph.
- **Open PRs.** 85 today. Announce the date; the PR body gives the one command (`./gradlew rewriteRun -Drewrite.activeRecipes=…`) that migrates a branch. The conflict routine can run it for stale PRs.
- **Timing.** Both are breaking changes for jablib users; they land before 6.0 final, back to back in one quiet window.
- **No mixed PRs.** No behaviour change, no test rewrites, no "while I am here" cleanups. Rich-model moves come afterwards, per touch.

## 4. Out of scope

Redesigning `BibEntry` or `MetaData`; renaming anything under `logic.shared`; option B (package by feature).

## 5. Questions for review

1. Option A, or B right away?
2. `BibDatabase` → `LibraryFile` names the in-memory entry collection after a file that a shared library does not have; the path lives in `BibDatabaseContext`. Alternative: `LibraryContent`, with `Library.getEntries()` (exists today as a delegate) hiding it from most callers. Which?
3. One combined PR (one conflict event for open PRs) or two PRs as planned (two reviewable steps)?
4. Who announces the window and when?
