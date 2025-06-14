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

    @Inject
    public SlashSendEntrypoint(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Register /send with tab completion
        CommandMeta sendMeta = server.getCommandManager()
            .metaBuilder("send")
            .tabCompleter(new SlashSendTabCompletion(server))
            .permission("MythofySlashHub.Send")
            .build();

        server.getCommandManager().register(sendMeta, new SlashSend1PS(server));

        server.getCommandManager().register("sendmp", new SlashSendMPS(server));
        server.getCommandManager().register("sendss", new SlashSendSS(server));
        server.getCommandManager().register("sendas", new SlashSendAS(server));
        logger.info("Registered /send, /sendmp, /sendss, and /sendas commands.");
    }
}
