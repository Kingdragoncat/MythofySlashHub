package net.mythofy.mythofySlashHub;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class BroadcastCommand implements SimpleCommand {
    private final ProxyServer server;

    public BroadcastCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("mythofyslashhub.command.broadcast")) {
            invocation.source().sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            invocation.source().sendMessage(Component.text("Usage: /broadcast <message>", NamedTextColor.RED));
            return;
        }

        String message = String.join(" ", args);
        Component formattedMessage;
        
        // Support MiniMessage and legacy formatting in broadcast
        if (message.contains("<") && message.contains(">")) {
            formattedMessage = MiniMessage.miniMessage().deserialize(message);
        } else {
            formattedMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        }

        Component prefix = Component.text("[Broadcast] ", NamedTextColor.RED);
        Component finalMessage = prefix.append(formattedMessage);

        server.getAllPlayers().forEach(player -> player.sendMessage(finalMessage));
        server.getConsoleCommandSource().sendMessage(finalMessage);
    }
}
