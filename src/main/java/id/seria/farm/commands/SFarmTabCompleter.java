package id.seria.farm.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SFarmTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>();
            if (sender.hasPermission("seriafarm.admin")) {
                subCommands.add("editor");
                subCommands.add("reload");
                subCommands.add("wand");
                subCommands.add("give");
            }
            subCommands.add("stats");
            
            String current = args[0].toLowerCase();
            return subCommands.stream()
                    .filter(s -> s.startsWith(current))
                    .collect(Collectors.toList());
        }

        return completions;
    }
}
