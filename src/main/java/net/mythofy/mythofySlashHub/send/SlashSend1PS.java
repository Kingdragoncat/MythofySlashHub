package net.mythofy.mythofySlashHub.send;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Optional;

public class SlashSend1PS implements SimpleCommand {
    private final ProxyServer server;
    private static final String PERMISSION = "MythofySlashHub.send";

    public SlashSend1PS(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (!source.hasPermission(PERMISSION)) {
            source.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        if (args.length != 2) {
            source.sendMessage(Component.text("Usage: /send <player> <server>", NamedTextColor.RED));
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
    }
}