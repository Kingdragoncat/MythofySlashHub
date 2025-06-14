package net.mythofy.mythofySlashHub;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.command.SimpleCommand.Invocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class reload implements SimpleCommand {
    private final MythofySlashHub plugin;
    private static final String PERMISSION = "MythofySlashHub.Reload";

    public reload(MythofySlashHub plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!source.hasPermission(PERMISSION)) {
            source.sendMessage(Component.text("You do not have permission to reload the plugin.", NamedTextColor.RED));
            return;
        }

        plugin.reloadConfig();
        source.sendMessage(Component.text("MythofySlashHub configuration reloaded.", NamedTextColor.GREEN));
    }
}