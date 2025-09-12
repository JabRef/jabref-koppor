package org.jabref.http.server.cayw.format;

import jakarta.ws.rs.core.MediaType;
import java.util.List;
import org.jabref.http.server.cayw.CAYWQueryParams;
import org.jabref.http.server.cayw.gui.CAYWEntry;

public interface CAYWFormatter {
    MediaType getMediaType();

    String format(CAYWQueryParams caywQueryParams, List<CAYWEntry> caywEntries);
}
