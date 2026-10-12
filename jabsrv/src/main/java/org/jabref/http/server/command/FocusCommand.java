package org.jabref.http.server.command;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.ws.rs.core.Response;
import org.jabref.logic.UiCommand;

@JsonTypeName("focus")
public class FocusCommand extends Command {
    @Override
    public Response execute() {
        return execute(new UiCommand.Focus());
    }
}
