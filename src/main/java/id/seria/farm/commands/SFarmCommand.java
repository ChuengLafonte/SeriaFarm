package id.seria.farm.commands;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.listeners.WandListener;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SFarmCommand implements CommandExecutor, TabCompleter {

    private final SeriaFarmPlugin plugin;

    public SFarmCommand(SeriaFarmPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "editor":
                if (!player.isOp()) return noPerm(player);
                player.openInventory(new MainMenu(plugin).mainmenu(player));
                break;
            case "wand":
                if (!player.isOp()) return noPerm(player);
                ItemStack wand = new ItemStack(Material.STONE_AXE);
                ItemMeta meta = wand.getItemMeta();
                if (meta != null) {
                    meta.displayName(StaticColors.getHexMsg("&eREGEN WAND"));
                    meta.setCustomModelData(20);
                    wand.setItemMeta(meta);
                }
                player.getInventory().addItem(id.seria.farm.utils.WandUtils.getWand());
                plugin.getConfigManager().sendPrefixedMessage(player, "&fYou have received a selection wand!");
                return true;
            case "pos1":
                if (!player.isOp()) return noPerm(player);
                Location p1 = player.getLocation();
                WandListener.Mpos1.put(player.getUniqueId(), p1);
                plugin.getConfigManager().sendPrefixedMessage(player, "&fPos1 = &c[&eX=&f" + p1.getBlockX() + "&c,&eY=&f" + p1.getBlockY() + "&c,&eZ=&f" + p1.getBlockZ() + "&c]");
                return true;
            case "pos2":
                if (!player.isOp()) return noPerm(player);
                Location p2 = player.getLocation();
                WandListener.Mpos2.put(player.getUniqueId(), p2);
                plugin.getConfigManager().sendPrefixedMessage(player, "&fPos2 = &c[&eX=&f" + p2.getBlockX() + "&c,&eY=&f" + p2.getBlockY() + "&c,&eZ=&f" + p2.getBlockZ() + "&c]");
                return true;
            case "create":
                if (!player.isOp()) return noPerm(player);
                if (args.length < 2) {
                    plugin.getConfigManager().sendPrefixedMessage(player, " &cUsage: /sfarm create <name>");
                    return true;
                }
                Location pos1 = WandListener.Mpos1.get(player.getUniqueId());
                Location pos2 = WandListener.Mpos2.get(player.getUniqueId());
                if (pos1 == null || pos2 == null) {
                    plugin.getConfigManager().sendPrefixedMessage(player, " &cPlease select Pos1 & Pos2 first.");
                    return true;
                }
                
                String regionName = args[1];
                FileConfiguration regionConfig = plugin.getConfigManager().getConfig("regions.yml");
                String path = "regions." + regionName + ".";
                
                regionConfig.set(path + "display-name", "&#ffa500" + regionName);
                regionConfig.set(path + "enabled", true);
                regionConfig.set(path + "per-region-regen", true);
                regionConfig.set(path + "require-permission", false);
                regionConfig.set(path + "allow-block-place", false);
                regionConfig.set(path + "teleport-location", serializeLoc(player.getLocation()));
                regionConfig.set(path + "pos1", serializeLoc(pos1));
                regionConfig.set(path + "pos2", serializeLoc(pos2));
                
                plugin.getConfigManager().saveConfig("regions.yml");
                plugin.getConfigManager().sendPrefixedMessage(player, " &aRegion &f" + regionName + " &acreated and saved!");
                break;
            case "reload":
                if (!player.isOp()) return noPerm(player);
                plugin.getConfigManager().reloadConfigs();
                plugin.getConfigManager().sendPrefixedMessage(player, " &aPlugin reloaded successfully!");
                break;
            case "clear":
                if (!player.isOp()) return noPerm(player);
                WandListener.Mpos1.remove(player.getUniqueId());
                WandListener.Mpos2.remove(player.getUniqueId());
                plugin.getConfigManager().sendPrefixedMessage(player, " &aSelection and particles cleared!");
                break;
            case "givesoil":
                if (!player.isOp()) return noPerm(player);
                if (args.length < 3) {
                    plugin.getConfigManager().sendPrefixedMessage(player, " &cUsage: /sfarm givesoil <soil_id> <player> [amount]");
                    return true;
                }
                String soilId = args[1];
                Player target = org.bukkit.Bukkit.getPlayer(args[2]);
                if (target == null) {
                    plugin.getConfigManager().sendPrefixedMessage(player, " &cPlayer not found: " + args[2]);
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
                        plugin.getConfigManager().sendPrefixedMessage(player, " &cInvalid amount: " + args[3]);
                        return true;
                    }
                }
                
                ItemStack soilItem = plugin.getHookManager().getItem(soilId);
                if (soilItem == null || soilItem.getType() == Material.AIR) {
                    plugin.getConfigManager().sendPrefixedMessage(player, " &cInvalid soil identifier: " + soilId);
                    return true;
                }
                
                soilItem.setAmount(amount);
                target.getInventory().addItem(soilItem);
                plugin.getConfigManager().sendPrefixedMessage(player, " &aGave &f" + amount + "x " + soilId + " &ato &f" + target.getName());
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private boolean noPerm(Player player) {
        player.sendMessage(plugin.getConfigManager().getMessage("no-permission"));
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(StaticColors.getHexMsg("&8&m---&r &6&lSeriaFarm Help &8&m---"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm editor &7- Open Admin Panel"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm wand &7- Get Selection Wand"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm pos1 &7- Set Position 1"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm pos2 &7- Set Position 2"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm create <name> &7- Create Region"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm givesoil <soil> <player> [amount] &7- Give soil item"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm clear &7- Clear Selection"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm help &7- Page 1/1"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm reload &7- Reload Configs"));
    }

    private String serializeLoc(Location loc) {
        if (loc == null) return "world;0;0;0;0;0";
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("editor");
            subs.add("wand");
            subs.add("pos1");
            subs.add("pos2");
            subs.add("create");
            subs.add("givesoil");
            subs.add("clear");
            subs.add("reload");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("givesoil")) {
            return plugin.getCustomPlantManager().getAllSoilIdentifiers().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("givesoil")) {
            return org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
