package net.mythofy.mythofySlashHub.send;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class SlashSendCommand implements SimpleCommand {
    private final ProxyServer server;
    // Confirmation: source -> action ("all:<server>" or "ss:<from>:<to>")
    private final Map<CommandSource, String> pendingConfirmations = new HashMap<>();

    public SlashSendCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendUsage(source);
            return;
        }

        // Confirmation logic
        if (args.length == 1 && (args[0].equalsIgnoreCase("confirm") || args[0].equalsIgnoreCase("deny"))) {
            if (!pendingConfirmations.containsKey(source)) {
                source.sendMessage(Component.text("No pending send-all operation to confirm or deny.", NamedTextColor.RED));
                return;
            }
            if (args[0].equalsIgnoreCase("deny")) {
                pendingConfirmations.remove(source);
                source.sendMessage(Component.text("Send-all operation cancelled.", NamedTextColor.YELLOW));
                return;
            }
            // Confirm
            String action = pendingConfirmations.remove(source);
            if (action.startsWith("all:")) {
                String toServer = action.substring(4);
                performSendAll(source, toServer);
            } else if (action.startsWith("ss:")) {
                String[] parts = action.split(":");
                if (parts.length == 3) {
                    performSendServerToServer(source, parts[1], parts[2]);
                }
            }
            return;
        }

        // /send all <server>
        if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
            if (!source.hasPermission("MythofySlashHub.Sendall")) {
                source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }
            String toServer = args[1];
            Optional<RegisteredServer> to = server.getServer(toServer);
            if (to.isEmpty()) {
                source.sendMessage(Component.text("Target server not found: " + toServer, NamedTextColor.RED));
                return;
            }
            Component confirmMsg = Component.text("Are you sure you want to send ALL players to ", NamedTextColor.YELLOW)
                    .append(Component.text(toServer, NamedTextColor.AQUA))
                    .append(Component.text("? ", NamedTextColor.YELLOW))
                    .append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/send confirm")))
                    .append(Component.text(" "))
                    .append(Component.text("[DENY]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/send deny")));
            source.sendMessage(confirmMsg);
            source.sendMessage(Component.text("Or type /send confirm or /send deny.", NamedTextColor.GRAY));
            pendingConfirmations.put(source, "all:" + toServer);
            return;
        }

        // /send <server1> <server2> (server-to-server)
        if (args.length == 2) {
            Optional<RegisteredServer> from = server.getServer(args[0]);
            Optional<RegisteredServer> to = server.getServer(args[1]);
            if (from.isPresent() && to.isPresent()) {
                if (!source.hasPermission("MythofySlashHub.Sendall")) {
                    source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                    return;
                }
                Component confirmMsg = Component.text("Are you sure you want to send all players from ", NamedTextColor.YELLOW)
                        .append(Component.text(args[0], NamedTextColor.AQUA))
                        .append(Component.text(" to ", NamedTextColor.YELLOW))
                        .append(Component.text(args[1], NamedTextColor.AQUA))
                        .append(Component.text("? ", NamedTextColor.YELLOW))
                        .append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/send confirm")))
                        .append(Component.text(" "))
                        .append(Component.text("[DENY]", NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/send deny")));
                source.sendMessage(confirmMsg);
                source.sendMessage(Component.text("Or type /send confirm or /send deny.", NamedTextColor.GRAY));
                pendingConfirmations.put(source, "ss:" + args[0] + ":" + args[1]);
                return;
            }
        }

        // /send <player> <server>
        if (args.length == 2) {
            if (!source.hasPermission("MythofySlashHub.Send")) {
                source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }
            String playerName = args[0];
            String serverName = args[1];
            Optional<Player> targetPlayer = server.getPlayer(playerName);
            if (targetPlayer.isEmpty()) {
                source.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
                return;
            }
            Optional<RegisteredServer> targetServer = server.getServer(serverName);
            if (targetServer.isEmpty()) {
                source.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
                return;
            }
            Player player = targetPlayer.get();
            player.createConnectionRequest(targetServer.get()).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    source.sendMessage(Component.text("Sent " + playerName + " to " + serverName + ".", NamedTextColor.GREEN));
                    player.sendMessage(Component.text("You have been sent to " + serverName + ".", NamedTextColor.YELLOW));
                } else {
                    source.sendMessage(Component.text("Failed to send player: " + result.getReasonComponent().map(Component::examinableName).orElse("Unknown error"), NamedTextColor.RED));
                }
            });
            return;
        }

        // /send <player1> <player2> ... <server> (multi-player send)
        if (args.length > 2) {
            if (!source.hasPermission("MythofySlashHub.MultiSend")) {
                source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }
            String serverName = args[args.length - 1];
            Optional<RegisteredServer> targetServer = server.getServer(serverName);
            if (targetServer.isEmpty()) {
                source.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
                return;
            }
            List<String> notFound = new ArrayList<>();
            int sentCount = 0;
            for (int i = 0; i < args.length - 1; i++) {
                String playerName = args[i];
                Optional<Player> targetPlayer = server.getPlayer(playerName);
                if (targetPlayer.isEmpty()) {
                    notFound.add(playerName);
                    continue;
                }
                Player player = targetPlayer.get();
                player.createConnectionRequest(targetServer.get()).connect().thenAccept(result -> {
                    if (result.isSuccessful()) {
                        player.sendMessage(Component.text("You have been sent to " + serverName + ".", NamedTextColor.YELLOW));
                    }
                });
                sentCount++;
            }
            if (sentCount > 0) {
                source.sendMessage(Component.text("Sent " + sentCount + " player(s) to " + serverName + ".", NamedTextColor.GREEN));
            }
            if (!notFound.isEmpty()) {
                source.sendMessage(Component.text("Players not found: " + String.join(", ", notFound), NamedTextColor.RED));
            }
            return;
        }

        sendUsage(source);
    }

    private void performSendAll(CommandSource source, String toServer) {
        Optional<RegisteredServer> to = server.getServer(toServer);
        if (to.isEmpty()) {
            source.sendMessage(Component.text("Target server is no longer available.", NamedTextColor.RED));
            return;
        }
        Collection<Player> playersToSend = new ArrayList<>(server.getAllPlayers());
        if (playersToSend.isEmpty()) {
            source.sendMessage(Component.text("No players found on the network.", NamedTextColor.YELLOW));
            return;
        }
        for (Player player : playersToSend) {
            player.createConnectionRequest(to.get()).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    player.sendMessage(Component.text("You have been sent to " + toServer + ".", NamedTextColor.YELLOW));
                }
            });
        }
        source.sendMessage(Component.text("Sent " + playersToSend.size() + " player(s) to " + toServer + ".", NamedTextColor.GREEN));
    }

    private void performSendServerToServer(CommandSource source, String fromServer, String toServer) {
        Optional<RegisteredServer> from = server.getServer(fromServer);
        Optional<RegisteredServer> to = server.getServer(toServer);
        if (from.isEmpty() || to.isEmpty()) {
            source.sendMessage(Component.text("One or both servers are no longer available.", NamedTextColor.RED));
            return;
        }
        Collection<Player> playersToSend = new ArrayList<>();
        for (Player player : server.getAllPlayers()) {
            if (player.getCurrentServer().isPresent() &&
                player.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(fromServer)) {
                playersToSend.add(player);
            }
        }
        if (playersToSend.isEmpty()) {
            source.sendMessage(Component.text("No players found on server: " + fromServer, NamedTextColor.YELLOW));
            return;
        }
        for (Player player : playersToSend) {
            player.createConnectionRequest(to.get()).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    player.sendMessage(Component.text("You have been sent to " + toServer + ".", NamedTextColor.YELLOW));
                }
            });
        }
        source.sendMessage(Component.text("Sent " + playersToSend.size() + " player(s) from " + fromServer + " to " + toServer + ".", NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSource source) {
        source.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
        source.sendMessage(Component.text("/send <player> <server> (Send one player)", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/send <player1> <player2> ... <server> (Send multiple players)", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/send all <server> (Send all players, requires confirmation)", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/send <server1> <server2> (Send all from one server to another, requires confirmation)", NamedTextColor.GRAY));
        source.sendMessage(Component.text("/send confirm | /send deny (Confirm/cancel)", NamedTextColor.GRAY));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        List<String> completions = new ArrayList<>();

        List<String> playerNames = server.getAllPlayers().stream().map(Player::getUsername).toList();
        List<String> serverNames = server.getAllServers().stream().map(s -> s.getServerInfo().getName()).toList();
        boolean hasPending = pendingConfirmations.containsKey(source);

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
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            if ("all".startsWith(arg)) completions.add("all");
            completions.addAll(playerNames.stream().filter(name -> name.toLowerCase().startsWith(arg)).toList());
            completions.addAll(serverNames.stream().filter(name -> name.toLowerCase().startsWith(arg)).toList());
            if (hasPending) {
                if ("confirm".startsWith(arg)) completions.add("confirm");
                if ("deny".startsWith(arg)) completions.add("deny");
            }
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("all")) {
            String partial = args[1].toLowerCase();
            return serverNames.stream().filter(name -> name.toLowerCase().startsWith(partial)).toList();
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("confirm") || args[0].equalsIgnoreCase("deny"))) {
            return Collections.emptyList();
        }
        if (args.length >= 2) {
            boolean allPlayers = true;
            for (int i = 0; i < args.length - 1; i++) {
                if (!playerNames.contains(args[i])) {
                    allPlayers = false;
                    break;
                }
            }
            if (allPlayers) {
                Set<String> used = new HashSet<>(Arrays.asList(args).subList(0, args.length - 1));
                String partial = args[args.length - 1].toLowerCase();
                if (playerNames.contains(args[args.length - 1])) {
                    return serverNames;
                }
                completions.addAll(playerNames.stream().filter(name -> !used.contains(name) && name.toLowerCase().startsWith(partial)).toList());
                completions.addAll(serverNames.stream().filter(name -> name.toLowerCase().startsWith(partial)).toList());
                return completions;
            }
        }
        if (args.length == 2) {
            String fromServer = args[0];
            if (serverNames.contains(fromServer)) {
                String partial = args[1].toLowerCase();
                return serverNames.stream().filter(name -> name.toLowerCase().startsWith(partial)).toList();
            }
        }
        return Collections.emptyList();
    }

    // Expose the confirmation map for tab completion
    public Map<CommandSource, String> getPendingConfirmations() {
        return pendingConfirmations;
    }
}
