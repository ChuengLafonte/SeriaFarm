package id.seria.farm.hooks;

import id.seria.farm.SeriaFarmPlugin;
import io.lumine.mythic.lib.script.condition.Condition;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import org.bukkit.entity.Player;

public class SeriaFarmCollectionCondition extends Condition {

    private final String crop;
    private final int tier;

    public SeriaFarmCollectionCondition(ConfigObject config) {
        super(config);
        
        config.validateKeys("crop", "tier");
        this.crop = config.getString("crop").toLowerCase();
        this.tier = config.getInt("tier");
    }

    @Override
    public boolean isMet(SkillMetadata meta) {
        if (!meta.getCaster().getEntity().isValid() || !(meta.getCaster().getEntity() instanceof Player)) {
            return false;
        }

        Player player = (Player) meta.getCaster().getEntity();
        int playerTier = SeriaFarmPlugin.getInstance().getCollectionManager().getTier(player.getUniqueId(), crop);
        
        return playerTier >= tier;
    }
}
