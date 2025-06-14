package net.mythofy.mythofySlashHub.send;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.*;
import java.util.stream.Collectors;

public class SlashSendTabCompletion implements SimpleCommand {
    private final ProxyServer server;

    public SlashSendTabCompletion(ProxyServer server) {
        this.server = server;
    }
    
    @Override
    public void execute(Invocation invocation) {
        // No-op: This class is only for tab completion
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // /send <tab>
        if (args.length == 0) {
            List<String> completions = new ArrayList<>();
            completions.add("all");
            completions.add("confirm");
            completions.add("deny");
            completions.add("[");
            completions.addAll(server.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList()));
            completions.addAll(server.getAllServers().stream().map(s -> s.getServerInfo().getName()).collect(Collectors.toList()));
            return completions;
        }

        // /send <firstArg> ...
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String arg = args[0].toLowerCase();
            if ("all".startsWith(arg)) completions.add("all");
            if ("confirm".startsWith(arg)) completions.add("confirm");
            if ("deny".startsWith(arg)) completions.add("deny");
            if ("[".startsWith(arg)) completions.add("[");
            completions.addAll(server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(arg))
                    .collect(Collectors.toList()));
            completions.addAll(server.getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .filter(name -> name.toLowerCase().startsWith(arg))
                    .collect(Collectors.toList()));
            return completions;
        }

        // /send all <server>
        if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
            String partial = args[1].toLowerCase();
            return server.getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        // /send confirm or /send deny (no further completion)
        if (args.length == 1 && (args[0].equalsIgnoreCase("confirm") || args[0].equalsIgnoreCase("deny"))) {
            return Collections.emptyList();
        }

        // /send [<player1> <player2> ...] <server>
        if (args[0].startsWith("[")) {
            // Find if we're still in player list or at server name
            int closingBracketIdx = -1;
            for (int i = 0; i < args.length; i++) {
                if (args[i].endsWith("]")) {
                    closingBracketIdx = i;
                    break;
                }
            }
            if (closingBracketIdx == -1) {
                // Still in player list, suggest player names (excluding already typed)
                Set<String> already = Arrays.stream(args).map(a -> a.replace("[", "").replace("]", "")).collect(Collectors.toSet());
                return server.getAllPlayers().stream()
                        .map(Player::getUsername)
                        .filter(name -> !already.contains(name))
                        .collect(Collectors.toList());
            } else if (args.length == closingBracketIdx + 2) {
                // Next arg is server name
                String partial = args[args.length - 1].toLowerCase();
                return server.getAllServers().stream()
                        .map(s -> s.getServerInfo().getName())
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        // /send <player> <server>
        if (args.length == 2) {
            String playerName = args[0];
            // Only suggest servers for valid player
            Optional<Player> player = server.getPlayer(playerName);
            if (player.isPresent()) {
                String partial = args[1].toLowerCase();
                return server.getAllServers().stream()
                        .map(s -> s.getServerInfo().getName())
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        // /send <fromServer> <toServer>
        if (args.length == 2) {
            String fromServer = args[0];
            Optional<RegisteredServer> from = server.getServer(fromServer);
            if (from.isPresent()) {
                String partial = args[1].toLowerCase();
                return server.getAllServers().stream()
                        .map(s -> s.getServerInfo().getName())
                        .filter(name -> name.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}