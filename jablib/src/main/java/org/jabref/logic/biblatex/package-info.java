/// Reading biblatex style files (`.bbx` bibliography styles and `.cbx` citation styles).
///
/// [org.jabref.logic.biblatex.BiblatexStyleScanner] finds the fields a style reads without running TeX:
/// it scans the field-access macros of the style, its sibling style of the other kind, and the styles it requires.

package org.jabref.logic.biblatex;
