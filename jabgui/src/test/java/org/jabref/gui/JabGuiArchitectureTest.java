package org.jabref.gui;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import org.jabref.support.CommonArchitectureTest;

@AnalyzeClasses(packages = "org.jabref", importOptions = DoNotIncludeTests.class)
public class JabGuiArchitectureTest extends CommonArchitectureTest {}
