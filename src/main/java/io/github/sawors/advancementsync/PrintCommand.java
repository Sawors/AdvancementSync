package io.github.sawors.advancementsync;

import io.github.sawors.advancementsync.progress.AdvancementUnlocks;
import io.github.sawors.advancementsync.progress.CriterionUnlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PrintCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        Component message = Component
                .text("Advancement Progress :")
                .style(Style.style(NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.newline().style(Style.style()));
        if (strings.length > 0) {
            for (String str : strings) {
                NamespacedKey key = NamespacedKey.fromString(str);
                if (key == null) {
                    commandSender.sendMessage(Component.text("Key "+str+" is not valid!",NamedTextColor.RED));
                    continue;
                }
                Advancement adv = Bukkit.getAdvancement(key);
                if (adv == null) {
                    commandSender.sendMessage(Component.text("Advancement "+str+" does not exist!",NamedTextColor.RED));
                    continue;
                }
                message = message.append(getAdvancementDisplay(adv));
            }
        } else {
            for (NamespacedKey key : AdvancementSync.getAdvancementProgress().stream().map(AdvancementUnlocks::getAdvancementKey).toList()) {
                Advancement advancement = Bukkit.getAdvancement(key);
                if (advancement != null) {
                    message = message.append(getAdvancementDisplay(advancement));
                }
            }
        }
        commandSender.sendMessage(message);
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s, @NotNull String[] strings) {
        List<String> keys = new ArrayList<>();
        for (@NotNull Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement advancement = it.next();
            keys.add(advancement.getKey().asString());
        }
        return keys;
    }
    
    private static Component getAdvancementDisplay(Advancement advancement) {
        Component comp = advancement.displayName().color(NamedTextColor.GOLD).append(Component.newline());
        AdvancementUnlocks unlock = AdvancementSync.getAdvancementProgress().stream().filter(p -> p.getAdvancementKey().equals(advancement.getKey())).findFirst().orElse(null);
        Set<String> refProgress = unlock != null ? unlock.getCriteria().stream().map(CriterionUnlock::criterion).collect(Collectors.toSet()) : new HashSet<>();
        List<String> criterion = advancement.getCriteria().stream().toList();
        for (String c : criterion) {
            boolean present = refProgress.contains(c);
            Style color = present ? Style.style(NamedTextColor.WHITE,TextDecoration.BOLD) : Style.style(NamedTextColor.DARK_GRAY,TextDecoration.ITALIC);
            String prefix = present ? "✔" : "✖";
            comp = comp.append(Component.text("    "+prefix+" "+c+"\n",color));
        }
        
        return comp;
    }
}
