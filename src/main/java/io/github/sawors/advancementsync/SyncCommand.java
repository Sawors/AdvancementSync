package io.github.sawors.advancementsync;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SyncCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (strings.length >= 1) {
            for (final String arg : strings) {
                Player p = Bukkit.getPlayer(arg);
                if (p == null || !p.isOnline()) {
                    commandSender.sendMessage(Component.text("The player " + arg + " is not online!", NamedTextColor.RED));
                    continue;
                }
                AdvancementSync.syncPlayer(p);
            }
            commandSender.sendMessage(Component.text("Advancement progress synchronized",NamedTextColor.GREEN));
            return true;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            AdvancementSync.syncPlayer(p);
        }
        commandSender.sendMessage(Component.text("Advancement progress synchronized",NamedTextColor.GREEN));
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
