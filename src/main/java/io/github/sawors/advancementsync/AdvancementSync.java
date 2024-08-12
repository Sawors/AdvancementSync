package io.github.sawors.advancementsync;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import com.fasterxml.jackson.jr.ob.JSON;
import io.github.sawors.advancementsync.progress.AdvancementUnlocks;
import io.github.sawors.advancementsync.progress.CriterionUnlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public final class AdvancementSync extends JavaPlugin implements Listener {

    private static Plugin instance;
    private static String version;
    // null while not loaded
    //private static Map<NamespacedKey, Set<String>> progress = null;
    private static Set<AdvancementUnlocks> progress = null;
    private static File saveFile = null;
    // related to the unlocking logging yaml file
    private static final String dateTitle = "date";
    private static final String playerTitle = "player";
    //
    private static final Map<Advancement,String> syncBuffer = new HashMap<>();
    private static BukkitTask syncJob = null;
    private static Runnable syncTask = null;
    
    @Override
    public void onEnable() {
        instance = this;
        saveFile = new File(instance.getDataFolder(),"progress.yml");
        if (!saveFile.getParentFile().exists()) {
            saveFile.getParentFile().mkdir();
        }
        if (!saveFile.exists()) {
            try {
                saveFile.createNewFile();
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.SEVERE, "[AdvancementSync] Could not load progress file! Disabling plugin...");
                Bukkit.getPluginManager().disablePlugin(instance);
            }
        }
        saveDefaultConfig();
        
        Bukkit.getPluginManager().registerEvents(this,instance);
        
        Objects.requireNonNull(Bukkit.getServer().getPluginCommand("adsync")).setExecutor(new SyncCommand());
        Objects.requireNonNull(Bukkit.getServer().getPluginCommand("adload")).setExecutor(new LoadCommand());
        Objects.requireNonNull(Bukkit.getServer().getPluginCommand("adprint")).setExecutor(new PrintCommand());
        
        progress = loadAdvancementProgress();
        
        File saveFileOld = new File(instance.getDataFolder(),"progress.json");
        if (saveFileOld.exists()) {
            Bukkit.getLogger().log(Level.INFO,"[Advancement Sync] Migrating old progress data to the new file...");
            progress = new HashSet<>();
            Map<NamespacedKey,Set<String>> oldData = loadOldAdvancementProgress(saveFileOld);
            
            for (Map.Entry<NamespacedKey,Set<String>> entry : oldData.entrySet()) {
                AdvancementUnlocks adv = new AdvancementUnlocks(entry.getKey());
                for (String c : entry.getValue()) {
                    adv.getCriteria().add(new CriterionUnlock(UUID.fromString("f96b1fab-2391-4c41-b6aa-56e6e91950fd"),LocalDateTime.now(),c));
                }
                progress.add(adv);
            }
            File saveFileArchive = new File(saveFileOld.getParentFile(), "progress_old.json");
            try {
                saveFileArchive.createNewFile();
            } catch (IOException ignored) {}
            try (FileInputStream in = new FileInputStream(saveFileOld); FileOutputStream out = new FileOutputStream(saveFileArchive)) {
                out.write(in.readAllBytes());
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.INFO,"[Advancement Sync] Could not move old progress data to an archive file!");
            } finally {
                saveFileOld.delete();
            }
            Bukkit.getLogger().log(Level.INFO,"[Advancement Sync] Migration done!");
        }
        
        saveAdvancementProgress();
        if (instance.getConfig().getBoolean("enforce-advancement-gamerule",true)) {
            for (World w : Bukkit.getServer().getWorlds()) {
                w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS,false);
            }
        }
    }

    @Override
    public void onDisable() {
        
    }
    
    public static Plugin getPlugin() {
        return instance;
    }
    
	public static String getVersion() {
        if(version == null){
            try(InputStream stream = getPlugin().getClass().getClassLoader().getResourceAsStream("plugin.yml"); Reader reader = new InputStreamReader(Objects.requireNonNull(stream))){
                YamlConfiguration pluginData = YamlConfiguration.loadConfiguration(reader);
                version = pluginData.getString("version","1.0");
            } catch (
                    IOException | NullPointerException e){
                Bukkit.getLogger().log(Level.WARNING,"["+getPlugin().getName()+"] plugin.yml not found !");
            }
        }
        return version;
    }
	    
    public static Component getVersionMessage() {
        return
                Component.text("[").color(NamedTextColor.YELLOW)
                        .append(Component.text(getPlugin().getName()).color(NamedTextColor.GOLD))
                        .append(Component.text("]").color(NamedTextColor.YELLOW))
                        .append(Component.text(" Plugin Version ").color(NamedTextColor.AQUA))
                        .append(Component.text(getVersion()).color(NamedTextColor.GREEN))
                ;
    }
    
    public static boolean isAdvancementIgnored(NamespacedKey advancement, String listname) {
        final List<String> configIgnore = instance.getConfig().getStringList(listname);
        final String key = advancement.asString();
        for (String s : configIgnore) {
            try {
                boolean r = Pattern.compile(s, Pattern.CASE_INSENSITIVE).matcher(key).find();
                if (r) {
                    return true;
                }
            } catch (PatternSyntaxException ignored) {}
        }
        return false;
    }
    
    public static boolean isAdvancementIgnored(NamespacedKey advancement) {
        return isAdvancementIgnored(advancement,"ignore-progress");
        //return configIgnore.stream().anyMatch(s -> Pattern.compile(s, Pattern.CASE_INSENSITIVE).matcher(key).find());
    }
    
    public static boolean isCriterionIgnored(String criterion, String listname) {
        final List<String> configIgnore = instance.getConfig().getStringList(listname);
        for (String s : configIgnore) {
            try {
                boolean r = Pattern.compile(s, Pattern.CASE_INSENSITIVE).matcher(criterion).find();
                if (r) {
                    return true;
                }
            } catch (PatternSyntaxException ignored) {}
        }
        return false;
    }
    
    public static boolean isCriterionIgnored(String criterion) {
        return isCriterionIgnored(criterion,"criteria-name-ignore");
        //return configIgnore.stream().anyMatch(s -> Pattern.compile(s, Pattern.CASE_INSENSITIVE).matcher(criterion).find());
        //return instance.getConfig().getList("criteria-name-ignore", new ArrayList<>()).contains(criterion);
    }
    
    private static int getSyncTimeout() {
        return instance.getConfig().getInt("cascade-buffer-timeout",20);
    }
    
    private static void refreshSyncJob() {
        if (syncJob != null) {
            syncJob.cancel();
        }
        if (syncTask == null){
            syncTask = () -> {
                for (Map.Entry<Advancement, String> pair : syncBuffer.entrySet()) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.getAdvancementProgress(pair.getKey()).awardCriteria(pair.getValue());
                    }
                }
                syncBuffer.clear();
            };
        }
        syncJob = Bukkit.getScheduler().runTaskLater(instance, syncTask, getSyncTimeout());
    }
    
    @EventHandler
    public static void onAdvancementGrant(PlayerAdvancementCriterionGrantEvent event) {
        final Advancement advancement = event.getAdvancement();
        final String criterion = event.getCriterion();
        
        if (isAdvancementIgnored(advancement.getKey()) || isRecipe(advancement) || isCriterionIgnored(criterion)) {
            return;
        }
        
        // replace if the criteria has not been granted before
        boolean first_time = progress.stream()
                .noneMatch(
                        u -> u.getAdvancementKey().equals(advancement.getKey())
                        && u.getCriteria().stream().anyMatch(c -> c.criterion().equals(criterion))
                );
        
        if (first_time && event.getAdvancementProgress().isDone() && getPlugin().getConfig().getBoolean("announce-progress", true) && !isAdvancementIgnored(advancement.getKey(),"ignore-progress-announce")) {
            Style style = Style.style(NamedTextColor.GOLD);
            Style nameStyle = Style.style(NamedTextColor.AQUA);
            Bukkit.getServer().sendMessage(
                    Component.text("Advancement ",style)
                            .append(advancement.displayName())
                            .append(Component.text(" has been unlocked by ",style))
                            .append(event.getPlayer().displayName().style(nameStyle))
                            .append(Component.text(" !",style)
                    )
            );
        }
        
        addUnlock(advancement.getKey(),new CriterionUnlock(event.getPlayer().getUniqueId(),LocalDateTime.now(),criterion),first_time);
        
        if (first_time) {
            syncBuffer.put(advancement,criterion);
            refreshSyncJob();
            saveAdvancementProgress();
        }
    }
    
    // TODO : find a way to make this async !
    // for player joining a session with many
    // advancements this causes a server freeze !
    @EventHandler
    public static void connectionSync(PlayerJoinEvent event) {
        syncPlayer(event.getPlayer());
    }
    
    /**
     * This method synchronises ALL the advancements of a player
     * with the currently loaded progress.
     * @param p The player to synchronise
     */
    public static void syncPlayer(@NotNull Player p) {
        // Sadly this seems impossible to do async unless we find a way to
        // effectively get all the advancements of a player in a collection
        // without iterating through the whole stack.
        
        if (!p.isOnline()) {
            throw new IllegalArgumentException("Player "+p.getName()+" is not online!");
        }
        
        for (@NotNull Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement advancement = it.next();
            if (isAdvancementIgnored(advancement.getKey()) || isRecipe(advancement)) {
                continue;
            }
            AdvancementProgress playerProg = p.getAdvancementProgress(advancement);
            AdvancementUnlocks ref = progress.stream()
                    .filter(a -> a.getAdvancementKey().equals(advancement.getKey()))
                    .findFirst()
                    .orElse(null);
            
            Set<String> progRef = ref != null ? ref.getCriteria().stream().map(CriterionUnlock::criterion).collect(Collectors.toSet()) : new HashSet<>();
            
            for (String granted : playerProg.getAwardedCriteria()) {
                if (!progRef.contains(granted)) {
                    playerProg.revokeCriteria(granted);
                }
            }
            
            for (String missing : playerProg.getRemainingCriteria()) {
                if (progRef.contains(missing)) {
                    playerProg.awardCriteria(missing);
                }
            }
        }
    }
    
    public static boolean isRecipe(Advancement advancement) {
        return advancement.getRoot().getKey().value().contains("recipes");
    }
    
    public static Map<NamespacedKey,Set<String>> loadOldAdvancementProgress(File svf) {
        Map<NamespacedKey,Set<String>> progress = new HashMap<>();

        String json = "{}";
        try (FileInputStream in = new FileInputStream(svf)) {
            json = new String(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (!(json.startsWith("{") && json.endsWith("}"))) {
            json = "{}";
        }

        try {
            for (Object obj : JSON.std.with(JSON.Feature.PRETTY_PRINT_OUTPUT).beanFrom(progress.getClass(),json).entrySet()) {
                if (obj instanceof Map.Entry<?,?> entry1) {
                    Object key = entry1.getKey();
                    Object value = entry1.getValue();
                    if (key instanceof String nmk && value instanceof List<?> l){
                        progress.put(NamespacedKey.fromString(nmk),l.stream().map(Object::toString).collect(Collectors.toSet()));
                    }
                }
            }
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.WARNING,"Could not load advancement progress from file!");
        }

        return progress;
    }
    
    public static Set<AdvancementUnlocks> loadAdvancementProgressFromPlayer(Player p) {
        final Set<AdvancementUnlocks> progress = new HashSet<>();
        for (@NotNull Iterator<Advancement> it = Bukkit.advancementIterator(); it.hasNext(); ) {
            Advancement a = it.next();
            AdvancementUnlocks unlock = new AdvancementUnlocks(a.getKey());
            if (isAdvancementIgnored(a.getKey()) || isRecipe(a)) {
                continue;
            }
            for (String crit : p.getAdvancementProgress(a).getAwardedCriteria()) {
                CriterionUnlock c = new CriterionUnlock(
                        p.getUniqueId(),
                        LocalDateTime.now(),
                        crit
                );
                unlock.getCriteria().add(c);
            }
        }
        return progress;
    }
    
    public static void setAdvancementProgress(Set<AdvancementUnlocks> newProgress) {
        progress = newProgress;
    }
    
    public static Set<AdvancementUnlocks> getAdvancementProgress() {
        return progress;
    }
    
//    public static void saveAdvancementProgress() {
//        Map<String,List<String>> sermap = new HashMap<>();
//        YamlConfiguration save = new YamlConfiguration();
//        for (Map.Entry<NamespacedKey,Set<String>> entry : progress.entrySet()) {
//            save.createSection(entry.getKey().asString());
//
//            sermap.put(,entry.getValue().stream().toList());
//        }
//
//
//
//        try {
//            String json = JSON.std
//                    .with(JSON.Feature.PRETTY_PRINT_OUTPUT)
//                    .asString(sermap);
//
//            try (FileOutputStream out = new FileOutputStream(saveFile)) {
//                out.write(json.getBytes(StandardCharsets.UTF_8));
//            }
//        } catch (IOException e) {
//            Bukkit.getLogger().log(Level.SEVERE, "[AdvancementSync] Could not save advancement progress!");
//        }
//    }
    
    public static Set<AdvancementUnlocks> loadAdvancementProgress() {
        YamlConfiguration save = YamlConfiguration.loadConfiguration(saveFile);
        Set<AdvancementUnlocks> set = new HashSet<>();
        for (String advKeyStr : save.getKeys(false)) {
            NamespacedKey advKey = NamespacedKey.fromString(advKeyStr);
            AdvancementUnlocks prog = new AdvancementUnlocks(advKey);
            ConfigurationSection advSection = save.getConfigurationSection(advKeyStr);
            if (advKey == null || advSection == null) {continue;}
            for (String critKey : advSection.getKeys(false)) {
                ConfigurationSection criterion = advSection.getConfigurationSection(critKey);
                if (criterion == null) {continue;}
                UUID playerId = UUID.fromString(Objects.requireNonNull(criterion.getString(playerTitle)));
                LocalDateTime date = LocalDateTime.parse(Objects.requireNonNull(criterion.getString(dateTitle)));
                CriterionUnlock crit = new CriterionUnlock(playerId,date,critKey);
                prog.getCriteria().add(crit);
            }
            set.add(prog);
        }
        return set;
    }
    
    public static void saveAdvancementProgress() {
        YamlConfiguration save = new YamlConfiguration();
        for (AdvancementUnlocks adv : progress) {
            ConfigurationSection advSection = save.createSection(adv.getAdvancementKey().asString());
            for (CriterionUnlock crit : adv.getCriteria()) {
                ConfigurationSection critSection = advSection.createSection(crit.criterion());
                critSection.set(playerTitle, crit.playerId().toString());
                Player p = Bukkit.getPlayer(crit.playerId());
                if (p != null && p.isOnline()) {
                    critSection.set("player-name", p.getName());
                }
                critSection.set(dateTitle, crit.dateTime().toString());
            }
        }
        try {
            save.save(saveFile);
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[AdvancementSync] Could not save advancement progress data!");
        }
    }
    
    public static void addUnlock(NamespacedKey advancementKey, CriterionUnlock criterion, boolean replace) {
        AdvancementUnlocks adv = progress.stream()
                .filter(a -> a.getAdvancementKey().equals(advancementKey))
                .findFirst()
                .orElse(new AdvancementUnlocks(advancementKey, new HashSet<>()));
        
        if (adv.getCriteria().stream().anyMatch(c -> c.criterion().equals(criterion.criterion()))) {
            if (replace) {
                adv.getCriteria().removeIf(c -> c.criterion().equals(criterion.criterion()));
                adv.getCriteria().add(criterion);
            }
        } else {
            adv.getCriteria().add(criterion);
        }
        
        progress.removeIf(p -> p.getAdvancementKey().equals(advancementKey));
        progress.add(adv);
        saveAdvancementProgress();
    }
}
