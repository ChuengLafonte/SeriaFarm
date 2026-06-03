package id.seria.farm.listeners;
 
import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.InvUtils;
import id.seria.farm.managers.WateringToolManager;
import id.seria.farm.models.CustomPlantState;
import id.seria.farm.models.RegenBlock;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class InteractListener implements Listener {

    private final SeriaFarmPlugin plugin;
    private final Random random = new Random();
    
    // Cache for hologram optimization
    private final java.util.Map<java.util.UUID, String> lastTargetLocation = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Integer> lastWaterLevel = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> hologramCooldown = new java.util.HashMap<>();
 
    public InteractListener(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }
 
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 0. Global enable check + off-hand guard
        if (!plugin.getConfigManager().getSettings().enabled) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // ─── X. ENCHANTED BONE MEAL HANDLING ────────────────────────────────────
        if (isEnchantedBoneMeal(itemInHand)) {
            if (handleEnchantedBoneMeal(player, block, itemInHand)) {
                event.setCancelled(true);
                return;
            }
        }

        // ─── A. CUSTOM PLANT INTERACTION ────────────────────────────────────────
        if (plugin.getCustomPlantManager().isCustomPlant(block.getLocation())) {
            event.setCancelled(true); // Always cancel — prevents vanilla regen timer showing
            CustomPlantState state = plugin.getCustomPlantManager().getState(block.getLocation());
            if (state == null) return;

            WateringToolManager.WateringTool toolCfg = plugin.getWateringToolManager().getToolConfig(itemInHand);
            if (toolCfg != null) {
                if (state.isRotten()) {
                    player.sendMessage(plugin.getConfigManager().getMessage("garden-rot-warning"));
                    return;
                }
                int capacity = plugin.getWateringToolManager().getCapacity(itemInHand);
                if (capacity <= 0) {
                    player.sendMessage(plugin.getConfigManager().getMessage("watering-tool-empty"));
                    return;
                }
                int added = plugin.getCustomPlantManager().water(block.getLocation(), toolCfg.perUse());
                ItemStack newItem = plugin.getWateringToolManager().consume(itemInHand);
                player.getInventory().setItemInMainHand(newItem);
                if (state != null) plugin.getHologramManager().show(player, block.getLocation(), state);
                if (added == 0) player.sendMessage(plugin.getConfigManager().getMessage("garden-water-full"));
                return;
            }

            // Right click without a watering tool -> show timer
            if (state.isRotten()) {
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&c&lTanaman sudah mati kekeringan!"));
                return;
            }
            
            int waterLevel = state.getWateringLevel();
            if (waterLevel <= 0) {
                net.kyori.adventure.text.Component title = id.seria.farm.inventory.utils.StaticColors.getHexMsg("&#FF5555Paused");
                net.kyori.adventure.text.Component sub = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&c(Kurang Air)");
                player.showTitle(net.kyori.adventure.title.Title.title(title, sub, net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(250))));
                return;
            }

            // Calculate exact time remaining chronologically
            String cropKey = state.getCropKey();
            String path = plugin.getConfigManager().getConfig("crops.yml").contains("crops.garden." + cropKey) 
                    ? "crops.garden." + cropKey : "crops.global." + cropKey;
            
            int delaySecs = plugin.getConfigManager().getConfig("crops.yml").getInt(path + ".regen-delay", 300);
            
            long wateredMs = System.currentTimeMillis() - state.getPlantedAt();
            long timeRemainingMs = (delaySecs * 1000L) - wateredMs;
            int timeRemaining = (int) Math.max(0, Math.ceil(timeRemainingMs / 1000.0));
            
            BlockData blockData = block.getBlockData();
            if (blockData instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
                timeRemaining = 0;
            }
            
            if (timeRemaining > 0) {
                long minutes = timeRemaining / 60;
                long seconds = timeRemaining % 60;
                String timeStr = String.format("%02dm:%02ds", minutes, seconds);
                
                String dName = plugin.getConfigManager().getConfig("crops.yml").getString(path + ".display-name", cropKey);
                net.kyori.adventure.text.Component title = id.seria.farm.inventory.utils.StaticColors.getHexMsg("&#54F47FTime " + timeStr);
                net.kyori.adventure.text.Component sub = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(dName);
                
                player.showTitle(net.kyori.adventure.title.Title.title(title, sub, net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofSeconds(1), java.time.Duration.ofMillis(250))));
            } else {
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&aTanaman sudah siap dipanen!"));
            }
            return; // Stop here — do not fall through to vanilla regen check
        }

        // ─── B. PLANTING a seed on composted soil ────────────────────────────
        if (!itemInHand.getType().isAir() && plugin.getCustomPlantManager().isValidSoil(block)) {
            String seedId = plugin.getHookManager().getItemIdentifier(itemInHand);
            String cropKey = plugin.getCustomPlantManager().findCropBySeed(seedId);
            if (cropKey != null) {
                event.setCancelled(true);
                Block above = block.getRelative(0, 1, 0);
                if (above.getType() != Material.AIR && above.getType() != Material.CAVE_AIR) {
                    player.sendMessage(plugin.getConfigManager().getMessage("garden-no-space"));
                    return;
                }
                
                // Verify soil ownership instead of checking slot capacity
                if (!plugin.getSoilSlotManager().isSoilOwner(block.getLocation(), player.getUniqueId()) && !player.hasPermission("seriafarm.admin")) {
                    player.sendMessage(plugin.getConfigManager().getMessage("garden-not-yours"));
                    return;
                }

                // Consume seed from hand
                if (itemInHand.getAmount() > 1) itemInHand.setAmount(itemInHand.getAmount() - 1);
                else player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                // Place sprout — read material from crops.garden section
                String materialStr = plugin.getConfigManager().getConfig("crops.yml")
                        .getString("crops.garden." + cropKey + ".material", "WHEAT").toUpperCase();
                Material sproutMat = Material.matchMaterial(materialStr);
                if (sproutMat == null || !sproutMat.isItem()) sproutMat = Material.WHEAT;
                above.setType(sproutMat, true);
                if (above.getBlockData() instanceof org.bukkit.block.data.Ageable ag) {
                    ag.setAge(0);
                    above.setBlockData(ag, true);
                }
                plugin.getCustomPlantManager().plant(above.getLocation(), player, cropKey);
                // NOTE: Do NOT call startAdHocTracking — garden crops are managed by
                // the watering engine, NOT the vanilla regen system.
                player.sendMessage(plugin.getConfigManager().getMessage("garden-plant-success"));
                return;
            }
        }

        // REDIRECT TO ROOT FOR VERTICAL CROPS
        if (block.getType() == Material.BAMBOO || block.getType() == Material.SUGAR_CANE ||
                block.getType() == Material.CACTUS || block.getType() == Material.WHEAT) {
            block = plugin.getRegenManager().getVerticalRoot(block);
        }
 
        // 1. REGENERATION CHECK
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            RegenBlock regen = plugin.getRegenManager().getRegenBlock(block.getLocation());
            
            // ADMIN DEBUG & BYPASS
            if (player.hasPermission("seriafarm.admin") || player.isOp()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.BONE_MEAL) return;
            }
 
            if (regen != null && regen.isGrowth()) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    plugin.getVisualManager().showGrowthInfo(player, regen);
                }
            }
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand != null && hand.getType() == Material.BONE_MEAL) {
                    if (applyVanillaBoneMeal(player, block, regen)) {
                        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                        block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3);
                        block.getWorld().playSound(block.getLocation(), org.bukkit.Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
                    }
                    event.setCancelled(true);
                } else {
                    event.setCancelled(true);
                }
            }
            return;
        }
 
        // 1.5 ADMIN HARVEST DEBUG
        
        // 2. Check if this is a managed crop/block
        String blockKey = findBlockKey(player, block);
        
        // 2.0. AD-HOC TRACKING (Handle growing blocks not in map)
        if (blockKey != null && !isFullyGrown(block)) {
            Block root = block;
            if (isVerticalCrop(block.getType())) {
                root = plugin.getRegenManager().getVerticalRoot(block);
            }
            
            if (!plugin.getRegenManager().isRegenerating(root.getLocation())) {
                plugin.getRegenManager().startAdHocTracking(root, blockKey, player);
            }
            
            RegenBlock regen = plugin.getRegenManager().getRegenBlock(root.getLocation());
            if (regen != null && regen.isGrowth()) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    plugin.getVisualManager().showGrowthInfo(player, regen);
                }
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand != null && hand.getType() == Material.BONE_MEAL) {
                        if (applyVanillaBoneMeal(player, block, regen)) {
                            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
                            block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3);
                            block.getWorld().playSound(block.getLocation(), org.bukkit.Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
                        }
                        event.setCancelled(true);
                    } else {
                        event.setCancelled(true);
                    }
                }
                return;
            }
        }
 
        if (blockKey == null) return;
        
        // 2.1 Regional blocks: Disable right-click harvesting (Enforce even for global fallback)
        if (plugin.getRegenManager().getRegionAt(block.getLocation()) != null) return;
        
        // 2.2 Global blocks: Check harvest toggle
        if (!blockKey.startsWith("global.")) return;
        
        // 2.2 Global blocks: Check harvest toggle
        if (!plugin.getConfigManager().getSettings().globalRightClickHarvest) {
            return;
        }
 
        // 2.3 Check growth stage
        ConfigurationSection config = getBlockConfig(block, blockKey);
        if (config == null) return;
        
        if (!isFullyGrown(block)) {
            event.setCancelled(true);
            return;
        }
 
        // 3. Right-Click Harvest
        event.setCancelled(true);
        handleHarvest(player, block, config, blockKey);
    }

    // ─── Hologram trigger via PlayerMoveEvent ────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        
        // Optimized frequency: Check every 250ms per player instead of shared ticks
        Long lastCheck = hologramCooldown.get(player.getUniqueId());
        if (lastCheck != null && now - lastCheck < 250) return;
        hologramCooldown.put(player.getUniqueId(), now);
        
        ItemStack hand = player.getInventory().getItemInMainHand();
        Material handType = hand.getType();
        
        // CHECK: Is the player holding a Bucket OR a configured Watering Tool?
        boolean isWateringTool = plugin.getWateringToolManager().getToolConfig(hand) != null;
        boolean isBucket = handType == Material.BUCKET || handType == Material.WATER_BUCKET || handType == Material.LAVA_BUCKET;
        
        // Fallback catch-all for buckets (case-insensitive)
        if (!isBucket && handType.name().contains("BUCKET")) isBucket = true;
        
        if (!isWateringTool && !isBucket) {
            String removed = lastTargetLocation.remove(player.getUniqueId());
            if (removed != null) {
                plugin.getHologramManager().hide(player);
            }
            lastWaterLevel.remove(player.getUniqueId());
            return;
        }

        // RAYTRACE: Limited distance (5 blocks)
        Block target = player.getTargetBlockExact(5);
        
        // OPTIMIZATION: Check if looking at nothing or a non-custom plant
        if (target == null || !plugin.getCustomPlantManager().isCustomPlant(target.getLocation())) {
            String removed = lastTargetLocation.remove(player.getUniqueId());
            if (removed != null) {
                plugin.getHologramManager().hide(player);
            }
            lastWaterLevel.remove(player.getUniqueId());
            return;
        }

        // CACHE CHECK: If same block and same water level, skip update
        String locKey = plugin.getRegenManager().toKey(target.getLocation());
        CustomPlantState state = plugin.getCustomPlantManager().getState(target.getLocation());
        
        if (state != null) {
            String prevLoc = lastTargetLocation.get(player.getUniqueId());
            Integer prevLevel = lastWaterLevel.get(player.getUniqueId());
            
            if (locKey.equals(prevLoc) && prevLevel != null && prevLevel == state.getWateringLevel()) {
                return; // Nothing changed, avoid packet/teleport overhead
            }
            
            lastTargetLocation.put(player.getUniqueId(), locKey);
            lastWaterLevel.put(player.getUniqueId(), state.getWateringLevel());
            plugin.getHologramManager().show(player, target.getLocation(), state);
        }
    }

    // ─── Sync SeriaFarm with Vanilla Bonemeal ────────────────────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFertilize(org.bukkit.event.block.BlockFertilizeEvent event) {
        Block block = event.getBlock();
        if (plugin.getRegenManager().isRegenerating(block.getLocation())) {
            id.seria.farm.models.RegenBlock regen = plugin.getRegenManager().getRegenBlock(block.getLocation());
            if (regen != null && regen.isGrowth()) {
                // Sync stage next tick after vanilla finishes updating the block
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                        int newStage = ageable.getAge();
                        if (newStage > regen.getCurrentStage()) {
                            regen.setCurrentStage(newStage);
                            // Adjust start time to logically match the new stage
                            long offset = (long) (newStage * regen.getStepDuration());
                            regen.setStartTime(System.currentTimeMillis() - offset);
                        }
                    } else {
                        // Check if it grew into a full plant or tree (max stage)
                        regen.setCurrentStage(regen.getMaxStage());
                        long offset = (long) (regen.getMaxStage() * regen.getStepDuration());
                        regen.setStartTime(System.currentTimeMillis() - offset);
                    }
                });
            }
        }
    }

    private boolean isFullyGrown(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() == ageable.getMaximumAge();
        }
        if (block.getType().name().contains("AMETHYST")) {
            return block.getType() == Material.AMETHYST_CLUSTER;
        }
        if (block.getType() == Material.BAMBOO || block.getType() == Material.SUGAR_CANE || block.getType() == Material.CACTUS) {
            String key = plugin.getRegenManager().findBlockKey(block, null);
            if (key != null) {
                Block root = plugin.getRegenManager().getVerticalRoot(block);
                int current = plugin.getRegenManager().getVerticalHeight(root, block.getType());
                
                String configKey = block.getType() == Material.BAMBOO ? "bamboo-max-height" : "growth-max-height";
                int def = block.getType() == Material.BAMBOO ? 12 : 3;
                int max = plugin.getConfigManager().getConfig("crops.yml").getInt("crops." + key + "." + configKey, def);
                
                return current >= max;
            }
        }
        return true;
    }
 
    private void handleHarvest(Player player, Block block, ConfigurationSection config, String blockKey) {
        distributeRewards(player, block, config);
        int delay = config.getInt("regen-delay", 10);
        List<String> replaceBlocks = config.getStringList("replace-blocks");
        List<String> delayBlocks = config.getStringList("delay-blocks");
        Material fallbackMat = block.getType();
        plugin.getRegenManager().scheduleRegeneration(block, delay, replaceBlocks, delayBlocks, fallbackMat, blockKey);
    }
 
    private void distributeRewards(Player player, Block block, ConfigurationSection config) {
        ConfigurationSection rewards = config.getConfigurationSection("rewards");
        if (rewards == null) return;
 
        int xp = rewards.getInt("xp", 0);
        if (xp > 0) plugin.getExperienceManager().giveXP(player, xp);
 
        for (String cmd : rewards.getStringList("commands")) {
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }
 
        List<?> drops = rewards.getList("drops");
        if (drops != null) {
            boolean dropToInv = plugin.getConfigManager().getSettings().dropToInventory;
            int playerLevel = plugin.getAuraSkillsManager().getFarmingLevel(player);
            double globalScaling = plugin.getConfigManager().getSettings().globalLevelScaling;

            boolean hasCommonDrop = false;
            java.util.List<java.util.Map<?, ?>> weightedPool = new java.util.ArrayList<>();

            for (Object obj : drops) {
                if (obj instanceof java.util.Map<?, ?> map) {
                    try {
                        ItemStack item = (ItemStack) map.get("item");
                        if (item == null) continue;

                        int reqLevel = map.containsKey("farming-level") ? ((Number) map.get("farming-level")).intValue() : 0;
                        if (playerLevel < reqLevel) continue;

                        double baseChance = map.containsKey("chance") ? ((Number) map.get("chance")).doubleValue() : 100.0;
                        double scaling = map.containsKey("level-scaling") ? ((Number) map.get("level-scaling")).doubleValue() : globalScaling;
                        double finalChance = baseChance + (playerLevel - reqLevel) * scaling;

                        if ((random.nextDouble() * 100.0) > finalChance) continue;

                        if (baseChance >= 10.0) hasCommonDrop = true;

                        if (map.containsKey("weight")) {
                            weightedPool.add(map);
                        } else {
                            giveReward(player, block, item.clone(), map, dropToInv);
                        }
                    } catch (Exception e) {}
                }
            }

            if (!hasCommonDrop && !config.getBoolean("suppress-vanilla-drop", false)) {
                Material vanillaMat = block.getType();
                vanillaMat = id.seria.farm.inventory.utils.InvUtils.getSingleMaterial(vanillaMat);
                giveReward(player, block, new ItemStack(vanillaMat), new java.util.HashMap<>(), dropToInv);
            }

            if (!weightedPool.isEmpty()) {
                double totalWeight = 0;
                for (java.util.Map<?, ?> map : weightedPool) totalWeight += getSelectionWeight(map);

                if (totalWeight > 0) {
                    double roll = random.nextDouble() * totalWeight;
                    double count = 0;
                    for (java.util.Map<?, ?> map : weightedPool) {
                        count += getSelectionWeight(map);
                        if (roll <= count) {
                            ItemStack item = (ItemStack) map.get("item");
                            giveReward(player, block, item.clone(), map, dropToInv);
                            break;
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
            try {
                String[] parts = ws.split("-");
                return (Double.parseDouble(parts[0]) + Double.parseDouble(parts[1])) / 2.0;
            } catch (Exception e) { return 1.0; }
        }
        try { return Double.parseDouble(ws); } catch (Exception e) { return 1.0; }
    }

    private void giveReward(Player player, Block block, ItemStack dropItem, java.util.Map<?, ?> map, boolean dropToInv) {
        InvUtils.stripTechnicalLore(dropItem);
        if (dropToInv) {
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(dropItem);
            if (!remaining.isEmpty()) {
                for (ItemStack left : remaining.values()) {
                    block.getWorld().dropItemNaturally(block.getLocation(), left);
                }
            }
        } else {
            block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
        }
    }

    private boolean applyVanillaBoneMeal(Player player, Block block, id.seria.farm.models.RegenBlock regen) {
        if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ag) {
            if (ag.getAge() < ag.getMaximumAge()) {
                int growth = java.util.concurrent.ThreadLocalRandom.current().nextInt(2, 6);
                int newAge = Math.min(ag.getMaximumAge(), ag.getAge() + growth);
                ag.setAge(newAge);
                block.setBlockData(ag, true);
                if (regen != null) {
                    regen.setCurrentStage(newAge);
                    long offset = (long) (newAge * regen.getStepDuration());
                    regen.setStartTime(System.currentTimeMillis() - offset);
                }
                return true;
            }
        }
        return false;
    }
 
    private String findBlockKey(Player player, Block block) {
        return plugin.getRegenManager().findBlockKey(block, player);
    }

    private ConfigurationSection getBlockConfig(Block block, String blockKey) {
        return plugin.getConfigManager().getConfig("crops.yml").getConfigurationSection("crops." + blockKey);
    }

    private boolean isEnchantedBoneMeal(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String id = plugin.getHookManager().getItemIdentifier(item);
        if (id == null) return false;
        
        // Handle format mi:TYPE:ENCHANTED_BONE_MEAL or just ENCHANTED_BONE_MEAL
        return id.equalsIgnoreCase("ENCHANTED_BONE_MEAL") || id.endsWith(":ENCHANTED_BONE_MEAL");
    }

    private boolean handleEnchantedBoneMeal(Player player, Block block, ItemStack item) {
        Material type = block.getType();
        boolean success = false;

        // 1. SKIP Garden Plants (Mekanik berbeda)
        if (plugin.getCustomPlantManager().isCustomPlant(block.getLocation())) {
            return false;
        }

        // 2. Check for Vanilla Ageable (Wheat, Carrots, etc.)
        if (block.getBlockData() instanceof Ageable ag) {
            if (ag.getAge() < ag.getMaximumAge()) {
                ag.setAge(ag.getMaximumAge());
                block.setBlockData(ag, true);
                success = true;
            }
        }
        // 3. Check for Saplings & Azaleas (Instant Tree)
        else if (type.name().contains("SAPLING") || type.name().contains("AZALEA")) {
            org.bukkit.TreeType treeType = getTreeType(type);
            if (treeType != null) {
                block.setType(Material.AIR);
                if (!block.getWorld().generateTree(block.getLocation(), treeType)) {
                    block.setType(type);
                } else {
                    success = true;
                }
            }
        }
        // 4. Check for Nether Fungi (Giant Fungi)
        else if (type == Material.CRIMSON_FUNGUS || type == Material.WARPED_FUNGUS) {
            org.bukkit.TreeType treeType = (type == Material.CRIMSON_FUNGUS) ? org.bukkit.TreeType.CRIMSON_FUNGUS : org.bukkit.TreeType.WARPED_FUNGUS;
            block.setType(Material.AIR);
            if (!block.getWorld().generateTree(block.getLocation(), treeType)) {
                block.setType(type);
            } else {
                success = true;
            }
        }
        // 5. Check for Mushrooms (Giant Mushrooms)
        else if (type == Material.BROWN_MUSHROOM || type == Material.RED_MUSHROOM) {
            org.bukkit.TreeType treeType = (type == Material.BROWN_MUSHROOM) ? org.bukkit.TreeType.BROWN_MUSHROOM : org.bukkit.TreeType.RED_MUSHROOM;
            block.setType(Material.AIR);
            if (!block.getWorld().generateTree(block.getLocation(), treeType)) {
                block.setType(type);
            } else {
                success = true;
            }
        }
        // 6. Check for Vertical Crops (Sugar Cane, Cactus, Bamboo)
        else if (isVerticalCrop(type)) {
            Block root = plugin.getRegenManager().getVerticalRoot(block);
            String blockKey = findBlockKey(player, root);
            int max = 3; // Default
            if (blockKey != null) {
                String configKey = type == Material.BAMBOO ? "bamboo-max-height" : "growth-max-height";
                int def = type == Material.BAMBOO ? 12 : 3;
                max = plugin.getConfigManager().getConfig("crops.yml").getInt("crops." + blockKey + "." + configKey, def);
            }
            
            int currentHeight = plugin.getRegenManager().getVerticalHeight(root, type);
            if (currentHeight < max) {
                for (int i = currentHeight; i < max; i++) {
                    Block above = root.getRelative(0, i, 0);
                    if (above.getType() == Material.AIR || above.getType() == Material.CAVE_AIR || above.getType() == type) {
                        above.setType(type, true);
                        success = true;
                    } else break;
                }
            }
        }
        // 7. Sea Pickles (Max Cluster)
        else if (type == Material.SEA_PICKLE) {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.SeaPickle pickle) {
                if (pickle.getPickles() < pickle.getMaximumPickles()) {
                    pickle.setPickles(pickle.getMaximumPickles());
                    block.setBlockData(pickle, true);
                    success = true;
                }
            }
        }

        if (success) {
            // Particle & Sound
            block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3);
            block.getWorld().playSound(block.getLocation(), org.bukkit.Sound.ITEM_BONE_MEAL_USE, 1.0f, 1.0f);
            
            // Consume item
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
            return true;
        }

        return false;
    }

    private org.bukkit.TreeType getTreeType(Material mat) {
        return switch (mat) {
            case OAK_SAPLING -> org.bukkit.TreeType.TREE;
            case SPRUCE_SAPLING -> org.bukkit.TreeType.REDWOOD;
            case BIRCH_SAPLING -> org.bukkit.TreeType.BIRCH;
            case JUNGLE_SAPLING -> org.bukkit.TreeType.JUNGLE;
            case ACACIA_SAPLING -> org.bukkit.TreeType.ACACIA;
            case DARK_OAK_SAPLING -> org.bukkit.TreeType.DARK_OAK;
            case MANGROVE_PROPAGULE -> org.bukkit.TreeType.MANGROVE;
            case CHERRY_SAPLING -> org.bukkit.TreeType.CHERRY;
            case AZALEA -> org.bukkit.TreeType.AZALEA;
            case FLOWERING_AZALEA -> org.bukkit.TreeType.AZALEA;
            default -> null;
        };
    }

    private boolean isVerticalCrop(Material mat) {
        return mat == Material.BAMBOO || mat == Material.BAMBOO_SAPLING || 
               mat == Material.SUGAR_CANE || mat == Material.WHEAT || 
               mat == Material.CACTUS;
    }
}
