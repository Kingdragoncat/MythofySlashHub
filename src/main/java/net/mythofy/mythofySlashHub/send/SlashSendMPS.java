package net.mythofy.mythofySlashHub.send;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class SlashSendMPS implements SimpleCommand {
    private final ProxyServer server;
    private static final String PERMISSION = "MythofySlashHub.send";

    public SlashSendMPS(ProxyServer server) {
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

        if (args.length < 3) {
            source.sendMessage(Component.text("Usage: /send [<player1> <player2> ...] <server>", NamedTextColor.RED));
            return;
        }

        // Find the opening and closing brackets
        if (!args[0].startsWith("[") || !args[args.length - 2].endsWith("]")) {
            source.sendMessage(Component.text("Usage: /send [<player1> <player2> ...] <server>", NamedTextColor.RED));
            return;
        }

        // Collect player names between brackets
        List<String> playerNames = new ArrayList<>();
        boolean inBracket = false;
        for (int i = 0; i < args.length - 1; i++) {
            String arg = args[i];
            if (i == 0 && arg.startsWith("[")) {
                inBracket = true;
                arg = arg.substring(1);
            }
            if (i == args.length - 2 && arg.endsWith("]")) {
                arg = arg.substring(0, arg.length() - 1);
                inBracket = false;
            }
            if (!arg.isEmpty()) {
                playerNames.add(arg);
            }
        }

        String serverName = args[args.length - 1];

        Optional<RegisteredServer> targetServer = server.getServer(serverName);
        if (targetServer.isEmpty()) {
            source.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
            return;
        }

        List<String> notFound = new ArrayList<>();
        int sentCount = 0;
        for (String playerName : playerNames) {
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
    }
}