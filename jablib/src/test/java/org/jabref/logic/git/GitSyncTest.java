package org.jabref.logic.git;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GitSyncTest {

    // These are setup by alieBobSetting
    private RevCommit base;
    private String local;
    private String remote;

    private final PersonIdent alice = new PersonIdent("Alice", "alice@example.org");
    private final PersonIdent bob = new PersonIdent("Bob", "bob@example.org");

    /**
     * Creates a commit graph with a base commit, one modification by Alice and one modification by Bob
     */
    @Test
    void aliceBobSimple(@TempDir Path tempDir) throws Exception {
        // Create empty repository
        try (Git git = Git.init()
                          .setDirectory(tempDir.toFile())
                          .setInitialBranch("main")
                          .call()) {
            Path library = tempDir.resolve("library.bib");

            String initialContent = """
                    @article{a,
                      author = {don't know the author}
                      doi = {xya},
                    }

                    @article{b,
                      author = {author-b}
                      doi = {xyz},
                    }
                    """;

            RevCommit baseCommit = writeAndCommit(initialContent, "Inital commit", alice, library, git);

            // Alice modifies a
            String aliceUpdatedContent = """
                    @article{a,
                      author = {author-a}
                      doi = {xya},
                    }

                    @article{b,
                      author = {author-b}
                      doi = {xyz},
                    }
                    """;

            RevCommit aliceCommit = writeAndCommit(aliceUpdatedContent, "Fix author of a", alice, library, git);

            git.checkout().setStartPoint(baseCommit).setCreateBranch(true).setName("bob-branch").call();

            // Bob reorders a and b
            String bobUpdatedContent = """
                    @article{b,
                      author = {author-b}
                      doi = {xyz},
                    }

                    @article{a,
                      author = {lala}
                      doi = {xya},
                    }
                    """;

            RevCommit bobCommit = writeAndCommit(bobUpdatedContent, "Exchange a with b", bob, library, git);

            // ToDo: Replace by call to GitSyncService crafting a merge commit
            git.merge().include(aliceCommit).include(bobCommit).call(); // Will throw exception bc of merge conflict

            // Debug hint: Show the created git graph on the command line
            //   git log --graph --oneline --decorate --all --reflog
        }
    }

    private RevCommit writeAndCommit(String content, String message, PersonIdent author, Path library, Git git) throws Exception {
        Files.writeString(library, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        git.add().addFilepattern(library.getFileName().toString()).call();
        return git.commit().setAuthor(author).setMessage(message).call();
    }
}
