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

public class LoadCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (strings.length > 0) {
            Player p = Bukkit.getPlayer(strings[0]);
            if (p == null || !p.isOnline()) {
                commandSender.sendMessage(Component.text("The player " + strings[0] + " is not online!", NamedTextColor.RED));
                return true;
            }
            AdvancementSync.setAdvancementProgress(AdvancementSync.loadAdvancementProgressFromPlayer(p));
            commandSender.sendMessage(Component.text("Advancement progress reloaded from "+p.getName(),NamedTextColor.GREEN));
            return true;
        }
        AdvancementSync.setAdvancementProgress(AdvancementSync.loadAdvancementProgress());
        commandSender.sendMessage(Component.text("Advancement progress reloaded",NamedTextColor.GREEN));
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        if (strings.length == 0) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
