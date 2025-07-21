package net.mythofy.mythofySlashHub;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerListCommand implements SimpleCommand {
    private final ProxyServer server;

    public PlayerListCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        invocation.source().sendMessage(Component.text("=== Player List by Server ===", NamedTextColor.GOLD));
        for (RegisteredServer regServer : server.getAllServers()) {
            Collection<Player> players = regServer.getPlayersConnected();
            String playerNames = players.isEmpty()
                    ? "(none)"
                    : players.stream().map(Player::getUsername).collect(Collectors.joining(", "));
            Component line = Component.text()
                    .append(Component.text(regServer.getServerInfo().getName(), NamedTextColor.AQUA))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(Component.text(playerNames, NamedTextColor.YELLOW))
                    .build();
            invocation.source().sendMessage(line);
        }
    }
}