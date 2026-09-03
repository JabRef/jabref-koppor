module org.jabref.jabsrv.cli {
    opens org.jabref.http.server.cli to info.picocli;

    requires org.jabref.jablib;
    requires org.jabref.jabsrv;

    requires org.slf4j;
    requires jul.to.slf4j;

    requires static jakarta.annotation;

    requires javafx.base;

    requires org.glassfish.grizzly.http.server;

    requires info.picocli;

    requires transitive org.jspecify;
    requires java.logging;
}
