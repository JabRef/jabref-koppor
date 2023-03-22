package org.jabref.logic.sharelatex;

import java.security.GeneralSecurityException;
import java.util.Objects;

import org.jabref.logic.shared.security.Password;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharelatexConnectionProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(SharelatexConnectionProperties.class);

    private String user;
    private String password;
    private String url;
    private String project;

    public SharelatexConnectionProperties() {
        // no data
    }

    public SharelatexConnectionProperties(ShareLatexPreferences prefs) {
        setFromPreferences(prefs);
    }

    public SharelatexConnectionProperties(String url, String user, String password, String project) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.project = project;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getUrl() {
        return url;
    }

    public String getProject() {
        return project;
    }

    public boolean isValid() {
        return Objects.nonNull(url)
                && Objects.nonNull(user)
                && Objects.nonNull(password)
                && Objects.nonNull(project);
    }

    private void setFromPreferences(ShareLatexPreferences prefs) {
        this.url = prefs.getSharelatexUrl();
        prefs.getDefaultProject().ifPresent(proj -> this.project = proj);

        if (prefs.getUser().isPresent()) {
            this.user = prefs.getUser().get();
            if (prefs.getPassword().isPresent()) {
                try {
                    this.password = new Password(prefs.getPassword().get().toCharArray(), prefs.getUser().get()).decrypt();
                } catch (GeneralSecurityException e) {
                    LOGGER.error("Could not decrypt password", e);
                }
            }
        }
    }
}
