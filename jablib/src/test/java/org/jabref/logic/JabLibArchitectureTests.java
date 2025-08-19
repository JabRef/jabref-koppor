package org.jabref.logic;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import org.jabref.support.CommonArchitectureTest;

@AnalyzeClasses(
    packages = "org.jabref",
    importOptions = ImportOption.DoNotIncludeTests.class
)
public class JabLibArchitectureTests extends CommonArchitectureTest {}
