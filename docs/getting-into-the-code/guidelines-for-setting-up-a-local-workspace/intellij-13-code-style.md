---
parent: Set up a local workspace
grand_parent: Getting into the code
nav_order: 13
---

# Step 3: Set up JabRef's code style

Contributions to JabRef's source code need to have a code formatting that is consistent with existing source code. For that purpose, JabRef provides code-style and check-style definitions.

Install the [CheckStyle-IDEA plugin](http://plugins.jetbrains.com/plugin/1065?pr=idea), it can be found via the plug-in repository:
Navigate to **File > Settings... > Plugins"**.
On the top, click on "Marketplace".
Then, search for "Checkstyle".
Click on "Install" choose "CheckStyle-IDEA".<br>
**Note:** In some MacBooks, `Settings` can be found at the "IntelliJ" button of the app menu instead of at "File".

{% figure caption:"Install CheckStyle" %}
![Install CheckStyle](13-01-install-checkstyle.png)
{% endfigure %}

After clicking, IntelliJ asks for confirmation:

{% figure caption:"Third Party Plugin Privacy Notice" %}
![Third Party Plugin Privacy Notice](13-02-checkstyle-confirmation.png)
{% endfigure %}

If you agree, click on "Agree" and you can continue.

Afterward, use the "Restart IDE" button to restart IntelliJ.

{% figure caption:"IntelliJ restart IDE" %}
![IntelliJ restart IDE](13-03-checkstyle-restart-ide.png)
{% endfigure %}

Click on "Restart" to finally restart.

Wait for IntelliJ coming up again.

## Install the open-java-format plugin

JabRef's Java sources are formatted with [open-java-format](https://openjavaformat.dev/).
The CI checks every pull request with it.
With its IntelliJ plugin, "Reformat Code" (<kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>L</kbd>) produces exactly what the CI expects.

1. Go to **File > Settings... > Plugins**, switch to "Marketplace", search for "open-java-format", and click "Install". Restart IntelliJ.
2. Go to **File > Settings... > Tools > open-java-format Settings** and tick "Enable open-java-format".
3. Optional, but much faster: tick "Use native binary" and click "Download".

Details are on the [IntelliJ page of open-java-format](https://openjavaformat.dev/get-started/intellij-idea/).

## Enable auto formatting of your code

IntelliJ can automatically reformat your code on save.
Please make use of this functionality.

Go to **File > Settings... > Tools > Actions on Save**.

1. Check "Reformat code"
2. Click on "All file types" (on the right)
3. Uncheck "All file types"
4. Check "Java"
5. Choose "Whole file"
6. Check "Optimize imports"

## Enable proper import cleanup

To enable "magic" creation and auto cleanup of imports, go to **File > Settings... > Editor > General > Auto Import**.
There, enable both "Add unambiguous imports on the fly" and "Optimize imports on the fly"
(Source: [JetBrains help](https://www.jetbrains.com/help/idea/creating-and-optimizing-imports.html#automatically-add-import-statements)).

{% figure caption:"Auto import enabled" %}
![Enable auto import](13-13-editor-auto-import.png)
{% endfigure %}

Press "OK".

## Disable too advanced code folding

Go to **File > Settings... > Editor > General > Code Folding**.
At section "General", disable "File header" and "Imports".
At section "Java", disable "One-line methods".

{% figure caption:"Code foldings disabled" %}
![Code foldings disabled](13-14-code-foldings.png)
{% endfigure %}

Press "OK".

## Run checkstyle

In the lower part of IntelliJ's window, click on "Checkstyle".
In "Rules", change to "JabRef".
Then, you can run a check on all modified files.

{% figure caption:"JabRef's style is active - and we are ready to run a check on all modified files" %}
![JabRef's style is active - and we are ready to run a check on all modified files](13-11-checkstyle-window.png)
{% endfigure %}

## Final comments

{: .highlight }
> Now you have configured IntelliJ completely.
> You can run the main application using Gradle and the test cases using IntelliJ.
> The code formatting rules are imported - and the most common styling issue at imports is automatically resolved by IntelliJ.
> Finally, you have Checkstyle running locally so that you can check for styling errors before submitting the pull request.

Got it running? GREAT! You are ready to lurk the code and contribute to JabRef. Please make sure to also read our [contribution guide](https://devdocs.jabref.org/contributing#contribute-code).

<!-- markdownlint-disable-file MD033 -->
