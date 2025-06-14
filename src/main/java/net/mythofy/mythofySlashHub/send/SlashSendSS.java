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

public class SlashSendSS implements SimpleCommand {
    private final ProxyServer server;
    // Track pending confirmations: source -> [fromServer, toServer]
    private final Map<CommandSource, String[]> pendingConfirmations = new HashMap<>();
    private static final String PERMISSION = "MythofySlashHub.sendall";

    public SlashSendSS(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        // Handle confirmation/denial
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
            String[] servers = pendingConfirmations.remove(source);
            performSendAll(source, servers[0], servers[1]);
            return;
        }

        // Command: /send <fromServer> <toServer>
        if (args.length == 2) {
            if (!source.hasPermission(PERMISSION)) {
                source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }
            String fromServer = args[0];
            String toServer = args[1];

            Optional<RegisteredServer> from = server.getServer(fromServer);
            Optional<RegisteredServer> to = server.getServer(toServer);

            if (from.isEmpty()) {
                source.sendMessage(Component.text("Source server not found: " + fromServer, NamedTextColor.RED));
                return;
            }
            if (to.isEmpty()) {
                source.sendMessage(Component.text("Target server not found: " + toServer, NamedTextColor.RED));
                return;
            }

            // Prepare confirmation message with clickable options
            Component confirmMsg = Component.text("Are you sure you want to send all players from ", NamedTextColor.YELLOW)
                    .append(Component.text(fromServer, NamedTextColor.AQUA))
                    .append(Component.text(" to ", NamedTextColor.YELLOW))
                    .append(Component.text(toServer, NamedTextColor.AQUA))
                    .append(Component.text("? ", NamedTextColor.YELLOW))
                    .append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/send confirm")))
                    .append(Component.text(" "))
                    .append(Component.text("[DENY]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/send deny")));

            source.sendMessage(confirmMsg);
            source.sendMessage(Component.text("Or type /send confirm or /send deny.", NamedTextColor.GRAY));
            pendingConfirmations.put(source, new String[]{fromServer, toServer});
            return;
        }

        // Usage
        source.sendMessage(Component.text("Usage: /send <fromServer> <toServer>", NamedTextColor.RED));
        source.sendMessage(Component.text("Or: /send confirm | /send deny", NamedTextColor.GRAY));
    }

    private void performSendAll(CommandSource source, String fromServer, String toServer) {
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
}