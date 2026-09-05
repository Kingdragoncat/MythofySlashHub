package net.mythofy.mythofySlashHub;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

public class FindPlayerCommand implements SimpleCommand {
    private final ProxyServer server;

    public FindPlayerCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendMessage(Component.text("Usage: /find <player>", NamedTextColor.RED));
            return;
        }

        String targetName = args[0];
        Optional<Player> targetOpt = server.getPlayer(targetName);

        if (targetOpt.isEmpty()) {
            invocation.source().sendMessage(Component.text("Player '" + targetName + "' is not online.", NamedTextColor.RED));
            return;
        }

        Player target = targetOpt.get();
        target.getCurrentServer().ifPresentOrElse(
            serverConnection -> {
                String serverName = serverConnection.getServerInfo().getName();
                Component msg = Component.text()
                    .append(Component.text(target.getUsername(), NamedTextColor.AQUA))
                    .append(Component.text(" is currently on ", NamedTextColor.YELLOW))
                    .append(Component.text(serverName, NamedTextColor.GOLD))
                    .build();
                invocation.source().sendMessage(msg);
            },
            () -> {
                invocation.source().sendMessage(Component.text("Player '" + targetName + "' is online but not connected to a server.", NamedTextColor.RED));
            }
        );
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();
        if (args.length <= 1) {
            String prefix = args.length == 1 ? args[0].toLowerCase() : "";
            for (Player player : server.getAllPlayers()) {
                if (player.getUsername().toLowerCase().startsWith(prefix)) {
                    suggestions.add(player.getUsername());
                }
            }
        }
        return suggestions;
    }
}
