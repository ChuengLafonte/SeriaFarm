package id.seria.farm.listeners;
 
import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.maintree.ToggleMenu;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.inventory.utils.InvUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import java.util.List;
import java.util.Random;
 
public class BlockBreakListener implements Listener {
 
    private final SeriaFarmPlugin plugin;
    private final Random random = new Random();

    public BlockBreakListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }
 
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        // 0. Check if plugin is globally enabled
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.enabled", true)) return;
 
        Block block = event.getBlock();
        Player player = event.getPlayer();
 
        // 1. Check if it's a Custom Garden Plant (Outside RegenManager)
        if (plugin.getCustomPlantManager().isCustomPlant(block.getLocation())) {
            id.seria.farm.models.CustomPlantState state = plugin.getCustomPlantManager().getState(block.getLocation());
            if (state != null) {
                if (!state.isRotten() && !isFullyGrown(block)) {
                    event.setCancelled(true);
                    return; // Prevent breaking immature plants (unless completely rotten)
                }

                // Allow break!
                event.setCancelled(true); // Take over drops
                block.setType(Material.AIR);

                // Plant Cleanup (Slot is NOT freed here, because soil is still placed)
                plugin.getCustomPlantManager().removePlant(block.getLocation());
                if (player != null) plugin.getHologramManager().hide(player);

                // Rewards if NOT ROTTEN
                if (!state.isRotten() && isFullyGrown(block)) {
                    ConfigurationSection gardenCfg = plugin.getConfigManager().getConfig("crops.yml")
                            .getConfigurationSection("crops.garden." + state.getCropKey());
                    if (gardenCfg != null) {
                        distributeRewards(player, block, gardenCfg);
                        plugin.getAuraSkillsManager().giveXP(player, block.getType());
                    }
                }
                return;
            }
        }

        // 1.5 Check if they broke an actual Composted Soil Block
        java.util.UUID soilOwner = plugin.getSoilSlotManager().getOwner(block.getLocation());
        if (soilOwner != null) {
            if (!soilOwner.equals(player.getUniqueId()) && !player.hasPermission("seriafarm.admin")) {
                event.setCancelled(true);
                plugin.getConfigManager().sendPrefixedMessage(player, "&cIni bukan ladang milikmu!");
                return;
            }

            // Refund the slot
            plugin.getSoilSlotManager().breakSoil(block.getLocation());
            int usedNow = plugin.getSoilSlotManager().getUsedSlotsByUUID(soilOwner);
            plugin.getConfigManager().sendPrefixedMessage(player, "&aLadang dibongkar, Slot kembali: &f" + usedNow);

            // Also explicitly destroy the plant above it if it exists, so holograms don't linger
            Block above = block.getRelative(0, 1, 0);
            if (plugin.getCustomPlantManager().isCustomPlant(above.getLocation())) {
                plugin.getCustomPlantManager().removePlant(above.getLocation());
                above.setType(Material.AIR);
                if (player != null) plugin.getHologramManager().hide(player);
            }
            // Standard drops handle the soil item
        }

        // 2. Check if this block is managed by SeriaFarm (Global or Regional)
        String blockKey = findBlockKey(block);
        if (blockKey == null) return;
        
        ConfigurationSection config = getBlockConfig(block, blockKey);
        if (config == null) return;
 
        // 2.1 Strict Growth Check for Crops (Apply to Global and Regional)
        if (!isFullyGrown(block)) {
            event.setCancelled(true);
            return;
        }

        // 2. REFINED GLOBAL HANDLING (Left-Click / Break)
        if (blockKey.startsWith("global.")) {
            // Cancel any active growth timer at this location upon break
            plugin.getRegenManager().cancelRegeneration(block.getLocation());
            
            String matStr = config.getString("material", "");
            
            // 1.2 NEW: Requirement Check for Global Crops (Engine handles messaging)
            if (!plugin.getRequirementEngine().canBreak(player, config)) {
                event.setCancelled(true);
                return;
            }

            // If it's a Hooked/Custom material (MMOItems, ItemsAdder, etc.)
            if (isCustomMaterial(matStr)) {
                // Intercept and drop the custom source item
                event.setCancelled(true);
                ItemStack source = plugin.getHookManager().getItem(matStr);
                if (source != null && source.getType() != org.bukkit.Material.STONE) {
                    block.getWorld().dropItemNaturally(block.getLocation(), source);
                }
                
                // RESPECT GLOBAL REPLANT
                boolean replant = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.global-replant", true);
                if (replant) {
                    int delay = calculateDelayForPlayer(player, config.getInt("regen-delay", 10));
                    plugin.getRegenManager().scheduleRegeneration(block, delay, null, null, org.bukkit.Material.AIR, blockKey);
                } else {
                    block.setType(org.bukkit.Material.AIR);
                }
                return;
            } else {
                // Standard vanilla block break
                // If it's a crop and we want replant:
                boolean replant = plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.global-replant", true);
                if (replant && isGrowthCapable(block)) {
                    // NEW: Requirement Check for Vanilla Global Crops (Engine handles messaging)
                    if (!plugin.getRequirementEngine().canBreak(player, config)) {
                        event.setCancelled(true);
                        return;
                    }
                    
                    event.setCancelled(true);
                    distributeRewards(player, block, config);
                    int delay = calculateDelayForPlayer(player, config.getInt("regen-delay", 10));
                    plugin.getRegenManager().scheduleRegeneration(block, delay, null, null, block.getType(), blockKey);
                }
                return;
            }
        }
 
        // --- REGIONAL BLOCK LOGIC ONLY FROM HERE ---
 
        // 1. CANCEL IF REGENERATING (Protection)
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            event.setCancelled(true);
            return;
        }

        // AGGRESSIVE PROTECTION FOR BAMBOO STALKS
        // If child block of bamboo is broken, check if root is regenerating
        if (block.getType() == org.bukkit.Material.BAMBOO) {
            org.bukkit.block.Block root = block;
            while (root.getRelative(0, -1, 0).getType() == org.bukkit.Material.BAMBOO) {
                root = root.getRelative(0, -1, 0);
            }
            if (plugin.getRegenManager().isRegenerating(root.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
 
        // 2.2 GROWTH STAGE VALIDATION (Now handled globally at Step 1.1)
 
        // 3. REQUIREMENT CHECK (Engine handles messaging)
        if (!plugin.getRequirementEngine().canBreak(player, config)) {
            event.setCancelled(true);
            return;
        }
 
        // 4. CANCEL EVENT - Plugin takes full control for Regional blocks
        event.setCancelled(true);
 
        // 5. DISTRIBUTE REWARDS & XP
        distributeRewards(player, block, config);
        plugin.getAuraSkillsManager().giveXP(player, block.getType());
        
        // --- AOE SWEEP LOGIC ---
        handleSweep(player, block, config, blockKey);
        
        // 6. MANAGE TOOL DURABILITY
        handleDurability(player);
        
        // 7. HANDLE DROPS (Smart logic for custom materials in regions)
        String matStr = config.getString("material", "");
        if (isCustomMaterial(matStr)) {
            ItemStack source = plugin.getHookManager().getItem(matStr);
            if (source != null && source.getType() != org.bukkit.Material.STONE) {
                block.getWorld().dropItemNaturally(block.getLocation(), source);
            }
        }
        
        // 8. REGENERATION PARAMETERS (Regional: raw delay, no GrowthAura buff)
        int delay = config.getInt("regen-delay", 10);
        java.util.List<String> replaceBlocks = config.getStringList("replace-blocks");
        java.util.List<String> delayBlocks = config.getStringList("delay-blocks");
        
        String fallbackMatStr = config.getString("material", block.getType().name());
        org.bukkit.Material fallbackMat = org.bukkit.Material.matchMaterial(fallbackMatStr);
        if (fallbackMat == null) fallbackMat = org.bukkit.Material.WHEAT;

        // 9. VERTICAL CROP SPECIAL HANDLING (Bamboo, Sugarcane, Cactus)
        if (block.getType() == Material.BAMBOO || block.getType() == Material.BAMBOO_SAPLING || 
            block.getType() == Material.SUGAR_CANE || block.getType() == Material.CACTUS) {
            org.bukkit.block.Block root = plugin.getRegenManager().getVerticalRoot(block);
            
            if (plugin.getRegenManager().isRegenerating(root.getLocation())) {
                event.setCancelled(true);
                return;
            }

            // Clear stalk upwards (excluding root for now)
            org.bukkit.block.Block stalk = root.getRelative(0, 1, 0);
            Material stalkMat = block.getType();
            while (stalk.getType() == stalkMat) {
                stalk.getWorld().dropItemNaturally(stalk.getLocation(), new ItemStack(stalkMat));
                stalk.setType(org.bukkit.Material.AIR);
                stalk = stalk.getRelative(0, 1, 0);
            }
            
            // Re-fetch config and key for the ROOT (Important for Regional isolation)
            String rootKey = plugin.getRegenManager().findBlockKey(root, player);
            if (rootKey == null) rootKey = blockKey; // Fallback
            
            plugin.getRegenManager().scheduleRegeneration(root, delay, replaceBlocks, delayBlocks, stalkMat, rootKey);
            return;
        }

        // 10. REGIONAL REGENERATION
        plugin.getRegenManager().scheduleRegeneration(block, delay, replaceBlocks, delayBlocks, fallbackMat, blockKey);
    }
 
    private boolean isCustomMaterial(String matStr) {
        if (matStr == null) return false;
        String lower = matStr.toLowerCase();
        return lower.startsWith("mi:") || lower.startsWith("ia:") || lower.startsWith("ox:") || lower.startsWith("nx:");
    }
 
    private void handleDurability(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType() == org.bukkit.Material.AIR) return;
        
        if (tool.getType().getMaxDurability() <= 0) return;
 
        org.bukkit.inventory.meta.ItemMeta meta = tool.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.Damageable) {
            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
            int unbreakingLevel = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);
            if (unbreakingLevel > 0) {
                if (new Random().nextInt(unbreakingLevel + 1) != 0) return;
            }
            
            damageable.setDamage(damageable.getDamage() + 1);
            tool.setItemMeta(meta);
            
            if (damageable.getDamage() >= tool.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }
 
    private void distributeRewards(Player player, Block block, ConfigurationSection config) {

        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards != null) {
            int xp = rewards.getInt("xp", 0);
            if (xp > 0) player.giveExp(xp);
            
            for (String cmd : rewards.getStringList("commands")) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }
  
            List<?> drops = rewards.getList("drops");
            if (drops != null) {
                boolean dropToInv = ToggleMenu.isDropToInvEnabled();
                ItemStack tool = player.getInventory().getItemInMainHand();
                int fortune = 0;
                if (tool != null && tool.hasItemMeta()) {
                    fortune = tool.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE);
                }
                
                int playerLevel = plugin.getAuraSkillsManager().getFarmingLevel(player);
                double globalScaling = plugin.getConfigManager().getConfig("config.yml").getDouble("settings.global-level-scaling", 0.1);

                boolean hasCommonDrop = false;
                java.util.List<java.util.Map<?, ?>> weightedPool = new java.util.ArrayList<>();
                
                for (Object obj : drops) {
                    if (obj instanceof java.util.Map<?, ?> map) {
                        try {
                            ItemStack item = (ItemStack) map.get("item");
                            if (item == null) continue;

                            // 1. Level Requirement Check
                            int reqLevel = map.containsKey("farming-level") ? ((Number) map.get("farming-level")).intValue() : 0;
                            if (playerLevel < reqLevel) continue;

                            // 2. Chance Scaling
                            double baseChance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                            double scaling = map.containsKey("level-scaling") ? ((Number) map.get("level-scaling")).doubleValue() : globalScaling;
                            double finalChance = baseChance + (playerLevel - reqLevel) * scaling;

                            if ((random.nextDouble() * 100.0) > finalChance) continue;

                            if (baseChance >= 10.0) hasCommonDrop = true;

                            // If it has a weight (numeric or range), add to weighted pool competition
                            if (map.containsKey("weight")) {
                                weightedPool.add(map);
                            } else {
                                // Static drop (Independent)
                                giveReward(player, block, item.clone(), map, dropToInv, fortune);
                            }
                        } catch (Exception ignored) {}
                    }
                }

                // 3. Automatic Vanilla Drop (Trash Item)
                if (!hasCommonDrop && !config.getBoolean("suppress-vanilla-drop", false)) {
                    Material vanillaMat = block.getType();
                    vanillaMat = id.seria.farm.inventory.utils.InvUtils.getSingleMaterial(vanillaMat);
                    giveReward(player, block, new ItemStack(vanillaMat), new java.util.HashMap<>(), dropToInv, fortune);
                }

                // Handle the weighted pool competition (Pick only ONE)
                if (!weightedPool.isEmpty()) {
                    double totalWeight = 0;
                    for (java.util.Map<?, ?> map : weightedPool) {
                        totalWeight += getSelectionWeight(map);
                    }

                    if (totalWeight > 0) {
                        double roll = random.nextDouble() * totalWeight;
                        double count = 0;
                        for (java.util.Map<?, ?> map : weightedPool) {
                            count += getSelectionWeight(map);
                            if (roll <= count) {
                                ItemStack item = (ItemStack) map.get("item");
                                giveReward(player, block, item.clone(), map, dropToInv, fortune);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private double getSelectionWeight(java.util.Map<?, ?> map) {
        Object w = map.get("weight");
        if (w instanceof Number n) return n.doubleValue();
        String ws = String.valueOf(w);
        if (ws.contains("-")) {
            // Use mid-point of range for selection probability
            try {
                String[] parts = ws.split("-");
                return (Double.parseDouble(parts[0]) + Double.parseDouble(parts[1])) / 2.0;
            } catch (Exception e) { return 1.0; }
        }
        try { return Double.parseDouble(ws); } catch (Exception e) { return 1.0; }
    }

    private void giveReward(Player player, Block block, ItemStack dropItem, java.util.Map<?, ?> map, boolean dropToInv, int fortune) {
        // Vanilla Fortune Multiplier
        if (fortune > 0) {
            int bonus = 0;
            for (int i = 0; i < fortune; i++) {
                if (random.nextBoolean()) bonus++;
            }
            dropItem.setAmount(dropItem.getAmount() + bonus);
        }

        // AuraSkills Farming Fortune (Farmer Ability, etc)
        // 100 Fortune = +1 guaranteed drop, 50 Fortune = 50% chance for +1
        double auraFortune = plugin.getAuraSkillsManager().getFarmingFortune(player);
        if (auraFortune > 0) {
            int extraDrops = (int) (auraFortune / 100);
            double leftOverChance = auraFortune % 100;
            if ((random.nextDouble() * 100.0) < leftOverChance) {
                extraDrops++;
            }
            dropItem.setAmount(dropItem.getAmount() + extraDrops);
        }

        // --- WEIGHT / RARITY SYSTEM ---
        handleWeightAndRarity(player, dropItem, map);
        
        // Always strip technical lore
        InvUtils.stripTechnicalLore(dropItem);

        if (dropToInv) {
            java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(dropItem);
            if (!remaining.isEmpty()) {
                for (ItemStack left : remaining.values()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), left);
                }
            }
        } else {
            block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
        }
    }
 
    private boolean isFullyGrown(org.bukkit.block.Block block) {
        org.bukkit.block.data.BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        if (block.getType().name().contains("AMETHYST")) {
            return block.getType() == org.bukkit.Material.AMETHYST_CLUSTER;
        }
        
        // Vertical Crops (Sugar Cane, Cactus, Bamboo)
        if (block.getType() == org.bukkit.Material.BAMBOO || 
            block.getType() == org.bukkit.Material.SUGAR_CANE || 
            block.getType() == org.bukkit.Material.CACTUS) {
            
            String key = plugin.getRegenManager().findBlockKey(block, null);
            if (key != null) {
                org.bukkit.block.Block root = plugin.getRegenManager().getVerticalRoot(block);
                int currentHeight = plugin.getRegenManager().getVerticalHeight(root, block.getType());
                
                String configKey = block.getType() == org.bukkit.Material.BAMBOO ? "bamboo-max-height" : "growth-max-height";
                int def = block.getType() == org.bukkit.Material.BAMBOO ? 12 : 3;
                int max = plugin.getConfigManager().getConfig("crops.yml").getInt("crops." + key + "." + configKey, def);
                
                return currentHeight >= max;
            }
        }
        return true;
    }
 
    private boolean isGrowthCapable(Block block) {
        org.bukkit.block.data.BlockData data = block.getBlockData();
        return data instanceof org.bukkit.block.data.Ageable || block.getType().name().contains("AMETHYST");
    }
 
    private void handleWeightAndRarity(Player player, ItemStack item, java.util.Map<?, ?> dropConfig) {
        if (!dropConfig.containsKey("weight")) return;
        
        String weightRange = String.valueOf(dropConfig.get("weight"));
        if (weightRange == null || !weightRange.contains("-")) return;

        try {
            String[] parts = weightRange.split("-");
            double min = Double.parseDouble(parts[0]);
            double max = Double.parseDouble(parts[1]);

            // Fetch true native Farming Luck from AuraSkills (combines Bountiful Harvest, Geneticist, equipment stats, etc.)
            // Convert Farming Luck to roll bonus (e.g. 100 Luck = +0.1 to roll, giving +10% higher rarity)
            double luckBonus = plugin.getAuraSkillsManager().getFarmingLuck(player) * 0.001;

            Random random = new Random();
            double roll = min + (random.nextDouble() * (max - min)) + luckBonus;
            roll = Math.min(roll, max); // Cap at max
            roll = Math.round(roll * 1000.0) / 1000.0; // 3 decimal places

            // Determine Rarity
            double percent = (max - min) > 0 ? (roll - min) / (max - min) : 1.0;
            String rarity;
            String color;

            if (percent >= 0.99) { rarity = "MYTHIC"; color = "&e&l"; }
            else if (percent >= 0.95) { rarity = "LEGENDARY"; color = "&d"; }
            else if (percent >= 0.85) { rarity = "EPIC"; color = "&6"; }
            else if (percent >= 0.65) { rarity = "RARE"; color = "&b"; }
            else if (percent >= 0.40) { rarity = "UNCOMMON"; color = "&a"; }
            else { rarity = "COMMON"; color = "&8"; }

            // Name Transformation (Potato -> Epic Potato)
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            String originalName = meta.hasDisplayName() ? 
                SeriaFarmPlugin.MINI_MESSAGE.serialize(meta.displayName()) : 
                InvUtils.getFriendlyName(item.getType());
            
            // Re-apply rarity prefix to the name
            meta.displayName(StaticColors.getHexMsg(color + rarity + " " + originalName));

            // Remove Chance/Weight Lore to allow stacking
            meta.lore(new java.util.ArrayList<>());

            // Inject NBT (Only rarity name to allow items of same rarity to stack)
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(new org.bukkit.NamespacedKey(plugin, "rarity"), org.bukkit.persistence.PersistentDataType.STRING, rarity);
            
            item.setItemMeta(meta);
        } catch (Exception ignored) {}
    }

    private void handleSweep(Player player, Block center, ConfigurationSection config, String blockKey) {
        // Only run if sweep is explicitly enabled in config.yml
        if (!plugin.getConfigManager().getConfig("config.yml").getBoolean("settings.sweep.enabled", false)) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || !tool.hasItemMeta()) return;

        // 1. Check NBT (seria_sweep)
        boolean hasSweepTag = false;
        try {
            org.bukkit.persistence.PersistentDataContainer pdc = tool.getItemMeta().getPersistentDataContainer();
            hasSweepTag = pdc.has(new org.bukkit.NamespacedKey("mmoitems", "seria_sweep"), org.bukkit.persistence.PersistentDataType.STRING) ||
                          pdc.has(new org.bukkit.NamespacedKey("seriafarm", "sweep"), org.bukkit.persistence.PersistentDataType.BYTE);
        } catch (Exception ignored) {}

        // 2. Check AuraSkills Scythe Master level
        int scytheLevel = plugin.getAuraSkillsManager().getAbilityLevel(player, "scythe_master");
        
        if (!hasSweepTag && scytheLevel <= 0) return;

        // Radius Calculation
        int radius = 0;
        if (hasSweepTag) radius = 1; // Default radius for tag
        if (scytheLevel > 0) radius = Math.max(radius, scytheLevel / 20 + 1); // 1 extra radius every 20 levels
        
        if (radius <= 0) return;
        radius = Math.min(radius, 3); // Max logic safety

        Material targetMat = center.getType();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) continue;
                Block b = center.getRelative(x, 0, z);
                if (b.getType() == targetMat && isFullyGrown(b)) {
                    // Quick check if allowed to break (region/manager)
                    if (findBlockKey(b) != null) {
                        // Process virtual break
                        distributeRewards(player, b, config);
                        plugin.getAuraSkillsManager().giveXP(player, b.getType());
                        
                        // Regen
                        int delay = calculateDelayForPlayer(player, config.getInt("regen-delay", 10));
                        List<String> rb = config.getStringList("replace-blocks");
                        List<String> db = config.getStringList("delay-blocks");
                        plugin.getRegenManager().scheduleRegeneration(b, delay, rb, db, targetMat, blockKey);
                    }
                }
            }
        }
    }

    private String findBlockKey(Block block) {
        return plugin.getRegenManager().findBlockKey(block, null);
    }
 
    private ConfigurationSection getBlockConfig(Block block, String blockKey) {
        return plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops." + blockKey);
    }

    private int calculateDelayForPlayer(Player player, int baseDelay) {
        if (plugin.getAuraSkillsManager().isReplenishActive(player)) {
            return 0; // Instant replant (Replenish Ability)
        }
        
        // Apply Growth Aura reduction
        double growthAuraReduction = plugin.getAuraSkillsManager().getGrowthAuraReduction(player);
        if (growthAuraReduction > 0) {
            double multiplier = 1.0 - (growthAuraReduction / 100.0);
            if (multiplier < 0) multiplier = 0; // Prevent negative delays
            return (int) Math.round(baseDelay * multiplier);
        }
        
        return baseDelay;
    }
}
