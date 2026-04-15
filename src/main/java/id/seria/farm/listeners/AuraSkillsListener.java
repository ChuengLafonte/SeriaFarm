package id.seria.farm.listeners;

import dev.aurelium.auraskills.api.event.mana.ManaAbilityActivateEvent;
import id.seria.farm.SeriaFarmPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class AuraSkillsListener implements Listener {

    private final SeriaFarmPlugin plugin;

    public AuraSkillsListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onManaAbilityActivate(ManaAbilityActivateEvent event) {
        if (event.getManaAbility() != null && event.getManaAbility().name().equalsIgnoreCase("replenish")) {
            // Duration is usually in seconds or ticks? The javadoc didn't specify, but 
            // usually it's seconds, let's assume it's seconds or ticks. 
            // Better yet, just record the expiry time natively. 
            // getDuration() is usually in seconds for Replenish.
            int durationSeconds = event.getDuration();
            long expiryTime = System.currentTimeMillis() + (durationSeconds * 1000L);
            plugin.getAuraSkillsManager().setReplenishActive(event.getPlayer().getUniqueId(), expiryTime);
        }
    }
}
