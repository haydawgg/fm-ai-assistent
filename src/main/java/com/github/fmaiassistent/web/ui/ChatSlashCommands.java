package com.github.fmaiassistent.web.ui;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class ChatSlashCommands {
    private ChatSlashCommands() {
    }

    record Command(String name, String hint, String prompt) {
    }

    static final List<Command> COMMANDS = List.of(
            new Command("/xi", "Best XI from the live formation", "Build my best XI from the live formation"),
            new Command("/buy", "Affordable signings", "Find affordable signings for my club"),
            new Command("/sell", "Who to sell or loan", "Who should I sell or loan out?"),
            new Command("/wonderkids", "Wonderkids for my club", "Find affordable wonderkids for my club"),
            new Command("/tactic", "Explain the live tactic", "Explain the live tactic and where the squad fits it"));

    static Optional<String> expand(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = raw.strip();
        if (!text.startsWith("/")) {
            return Optional.empty();
        }
        int space = text.indexOf(' ');
        String name = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
        String rest = space < 0 ? "" : text.substring(space + 1).strip();
        if ("/buy".equals(name) && !rest.isBlank()) {
            return Optional.of("For my club, find affordable " + rest + " signings using the save data.");
        }
        for (Command command : COMMANDS) {
            if (command.name().equals(name)) {
                return Optional.of(command.prompt());
            }
        }
        return Optional.empty();
    }
}
