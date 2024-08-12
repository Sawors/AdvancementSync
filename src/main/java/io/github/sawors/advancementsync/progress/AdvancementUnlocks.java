package io.github.sawors.advancementsync.progress;

import org.bukkit.NamespacedKey;

import java.util.HashSet;
import java.util.Set;

public class AdvancementUnlocks {
    private final NamespacedKey advancementKey;
    private final Set<CriterionUnlock> criteria;
    
    public AdvancementUnlocks(NamespacedKey advancementKey, Set<CriterionUnlock> criteria) {
        this.advancementKey = advancementKey;
        this.criteria = criteria;
    }
    
    public AdvancementUnlocks(NamespacedKey advancementKey) {
        this(advancementKey,new HashSet<>());
    }
    
    public NamespacedKey getAdvancementKey() {
        return advancementKey;
    }
    
    public Set<CriterionUnlock> getCriteria() {
        return criteria;
    }
}
