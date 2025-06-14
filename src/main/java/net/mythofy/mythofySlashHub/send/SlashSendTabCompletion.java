package net.mythofy.mythofySlashHub.send;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.*;
import java.util.stream.Collectors;

public class SlashSendTabCompletion implements SimpleCommand {
    private final ProxyServer server;
    // Reference to the confirmation map in SlashSendCommand
    private static Map<CommandSource, String> pendingConfirmations;

    public SlashSendTabCompletion(ProxyServer server) {
        this.server = server;
    }

    // Allow SlashSendCommand to set the confirmation map reference
    public static void setPendingConfirmations(Map<CommandSource, String> map) {
        pendingConfirmations = map;
    }

    @Override
    public void execute(Invocation invocation) {
        // No-op: This class is only for tab completion
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> completions = new ArrayList<>();

        // Helper lists
        List<String> playerNames = server.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList());
        List<String> serverNames = server.getAllServers().stream().map(s -> s.getServerInfo().getName()).collect(Collectors.toList());
        boolean hasPending = pendingConfirmations != null && pendingConfirmations.containsKey(source);

        // /send <TAB>
        if (args.length == 0) {
            completions.add("all");
            completions.addAll(playerNames);
            completions.addAll(serverNames);
            if (hasPending) {
                completions.add("confirm");
                completions.add("deny");
            }
            return completions;
        }

        // /send <firstArg> <TAB>
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            if ("all".startsWith(arg)) completions.add("all");
            completions.addAll(playerNames.stream().filter(name -> name.toLowerCase().startsWith(arg)).collect(Collectors.toList()));
            completions.addAll(serverNames.stream().filter(name -> name.toLowerCase().startsWith(arg)).collect(Collectors.toList()));
            if (hasPending) {
                if ("confirm".startsWith(arg)) completions.add("confirm");
                if ("deny".startsWith(arg)) completions.add("deny");
            }
            return completions;
        }

        // /send all <TAB> (only server names)
        if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
            String partial = args[1].toLowerCase();
            return serverNames.stream().filter(name -> name.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }

        // /send confirm or /send deny (no further completion)
        if (args.length == 1 && (args[0].equalsIgnoreCase("confirm") || args[0].equalsIgnoreCase("deny"))) {
            return Collections.emptyList();
        }

        // /send <player1> <player2> ... <TAB> (suggest more player names, then server names at last arg)
        if (args.length >= 2) {
            // If first arg is a player, and not a server or 'all', treat as player list
            boolean allPlayers = true;
            for (int i = 0; i < args.length - 1; i++) {
                if (!playerNames.contains(args[i])) {
                    allPlayers = false;
                    break;
                }
            }
            if (allPlayers) {
                // If not at last arg, suggest more player names not already used
                if (args.length < serverNames.size() + 2) {
                    Set<String> used = new HashSet<>(Arrays.asList(args).subList(0, args.length - 1));
                    String partial = args[args.length - 1].toLowerCase();
                    // If last arg matches a player, suggest server names
                    if (playerNames.contains(args[args.length - 1])) {
                        return serverNames;
                    }
                    // Otherwise, suggest player names not already used
                    completions.addAll(playerNames.stream()
                        .filter(name -> !used.contains(name) && name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList()));
                    // If enough players, also suggest server names
                    completions.addAll(serverNames.stream().filter(name -> name.toLowerCase().startsWith(partial)).collect(Collectors.toList()));
                    return completions;
                }
            }
        }

        // /send <server1> <server2> (server-to-server)
        if (args.length == 2) {
            String fromServer = args[0];
            if (serverNames.contains(fromServer)) {
                String partial = args[1].toLowerCase();
                return serverNames.stream().filter(name -> name.toLowerCase().startsWith(partial)).collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}