package net.mythofy.mythofySlashHub.send;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.command.CommandMeta;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.nio.file.Path;

public class SlashSendEntrypoint {

    private final ProxyServer server;
    private final Logger logger;
    private final Object plugin; // Reference to the plugin instance

    @Inject
    public SlashSendEntrypoint(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Object plugin) {
        this.server = server;
        this.logger = logger;
        this.plugin = plugin;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Register /send with the unified command executor (tab completion is now in the same class)
        SlashSendCommand sendCommand = new SlashSendCommand(server);
        CommandMeta sendMeta = server.getCommandManager()
            .metaBuilder("send")
            .plugin(plugin)
            .build();
        server.getCommandManager().register(sendMeta, sendCommand);
        logger.info("Registered /send command with all subcommands and tab completion.");
    }
}
