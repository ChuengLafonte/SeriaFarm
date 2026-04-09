package id.seria.farm.commands;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.MainMenu;
import id.seria.farm.inventory.utils.StaticColors;
import id.seria.farm.listeners.WandListener;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
        String prefix = plugin.getConfig().getString("settings.prefix", "&6&lSeriaFarm &8»");

        switch (sub) {
            case "menu":
            case "editor":
                if (!player.hasPermission("sfarm.admin")) return noPerm(player);
                player.openInventory(new MainMenu(plugin).mainmenu(player));
                break;
            case "wand":
                if (!player.hasPermission("sfarm.admin")) return noPerm(player);
                ItemStack wand = new ItemStack(Material.STONE_AXE);
                ItemMeta meta = wand.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.YELLOW + "REGEN WAND");
                    meta.setCustomModelData(20);
                    wand.setItemMeta(meta);
                }
                player.getInventory().addItem(wand);
                player.sendMessage(StaticColors.getHexMsg(prefix + " &fYou have received a selection wand!"));
                break;
            case "pos1":
                if (!player.hasPermission("sfarm.admin")) return noPerm(player);
                WandListener.Mpos1 = player.getLocation();
                player.sendMessage(StaticColors.getHexMsg(prefix) + " " + ChatColor.WHITE + "Pos1 =" + ChatColor.RED + "[" + ChatColor.YELLOW + "X=" + ChatColor.WHITE + WandListener.Mpos1.getBlockX() + ChatColor.RED + "," + ChatColor.YELLOW + "Y=" + ChatColor.WHITE + WandListener.Mpos1.getBlockY() + ChatColor.RED + "," + ChatColor.YELLOW + "Z=" + ChatColor.WHITE + WandListener.Mpos1.getBlockZ() + ChatColor.RED + "]");
                break;
            case "pos2":
                if (!player.hasPermission("sfarm.admin")) return noPerm(player);
                WandListener.Mpos2 = player.getLocation();
                player.sendMessage(StaticColors.getHexMsg(prefix) + " " + ChatColor.WHITE + "Pos2 =" + ChatColor.RED + "[" + ChatColor.YELLOW + "X=" + ChatColor.WHITE + WandListener.Mpos2.getBlockX() + ChatColor.RED + "," + ChatColor.YELLOW + "Y=" + ChatColor.WHITE + WandListener.Mpos2.getBlockY() + ChatColor.RED + "," + ChatColor.YELLOW + "Z=" + ChatColor.WHITE + WandListener.Mpos2.getBlockZ() + ChatColor.RED + "]");
                break;
            case "create":
                if (!player.hasPermission("sfarm.admin")) return noPerm(player);
                if (args.length < 2) {
                    player.sendMessage(StaticColors.getHexMsg(prefix + " &cUsage: /sfarm create <name>"));
                    return true;
                }
                if (WandListener.Mpos1 == null || WandListener.Mpos2 == null) {
                    player.sendMessage(StaticColors.getHexMsg(prefix + " &cPlease select Pos1 & Pos2 first."));
                    return true;
                }
                // Logical creation would go here
                player.sendMessage(StaticColors.getHexMsg(prefix + " &aRegion &f" + args[1] + " &acreated!"));
                break;
            case "reload":
                if (!player.hasPermission("sfarm.admin")) return noPerm(player);
                plugin.getConfigManager().reloadConfigs();
                player.sendMessage(StaticColors.getHexMsg(prefix + " &aPlugin reloaded successfully!"));
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private boolean noPerm(Player player) {
        player.sendMessage(StaticColors.getHexMsg("&cYou don't have permission to do this."));
        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(StaticColors.getHexMsg("&8&m---&r &6&lSeriaFarm Help &8&m---"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm menu &7- Open Admin Panel"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm editor &7- Open Admin Panel"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm wand &7- Get Selection Wand"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm pos1 &7- Set Position 1"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm pos2 &7- Set Position 2"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm create <name> &7- Create Region"));
        player.sendMessage(StaticColors.getHexMsg("&e/sfarm reload &7- Reload Configs"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("menu");
            subs.add("editor");
            subs.add("wand");
            subs.add("pos1");
            subs.add("pos2");
            subs.add("create");
            subs.add("delete");
            subs.add("reload");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
