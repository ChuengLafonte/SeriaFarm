package id.seria.farm.listeners;

import id.seria.farm.SeriaFarmPlugin;
import id.seria.farm.inventory.utils.StaticColors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.Listener;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChatInputListener implements Listener {

    private static final Map<UUID, InputContext> inputQueue = new ConcurrentHashMap<>();

    public static class InputContext {
        public final String title;
        public final Consumer<String> callback;
        public final Runnable onCancel;

        public InputContext(String title, Consumer<String> callback, Runnable onCancel) {
            this.title = title;
            this.callback = callback;
            this.onCancel = onCancel;
        }
    }

    public static void requestInput(Player player, String title, String format, Consumer<String> callback, Runnable onCancel) {
        player.closeInventory();
        
        player.sendMessage(StaticColors.getHexMsg("\n&e&l" + title));
        player.sendMessage(StaticColors.getHexMsg("&7Type &c&lCancel &7to exit."));
        if (format != null) {
            player.sendMessage(StaticColors.getHexMsg("&8&m--------------------------------"));
            player.sendMessage(StaticColors.getHexMsg("&6Format: &f" + format));
            player.sendMessage(StaticColors.getHexMsg("&8&m--------------------------------"));
        }
        
        inputQueue.put(player.getUniqueId(), new InputContext(title, callback, onCancel));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (inputQueue.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
            
            if (message.equalsIgnoreCase("cancel")) {
                InputContext ctx = inputQueue.remove(player.getUniqueId());
                player.sendMessage(StaticColors.getHexMsg("&cInput cancelled."));
                if (ctx.onCancel != null) {
                    Bukkit.getScheduler().runTask(SeriaFarmPlugin.getInstance(), ctx.onCancel);
                }
                return;
            }
 
            InputContext ctx = inputQueue.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(SeriaFarmPlugin.getInstance(), () -> ctx.callback.accept(message));
        }
    }

    // Helper for List inputs (Porting UBR ListInput logic)
    public static void requestListInput(Player player, String title, List<String> currentList, String format, Consumer<List<String>> onDone, Runnable onCancel) {
        printList(player, title, currentList);
        
        requestInput(player, "Enter " + title, format, input -> {
            if (input.equalsIgnoreCase("done")) {
                onDone.accept(currentList);
            } else if (input.equalsIgnoreCase("reset")) {
                currentList.clear();
                requestListInput(player, title, currentList, format, onDone, onCancel);
            } else if (input.toLowerCase().startsWith("removeline ")) {
                try {
                    int index = Integer.parseInt(input.split(" ")[1]);
                    if (index >= 0 && index < currentList.size()) {
                        currentList.remove(index);
                    }
                } catch (Exception e) {}
                requestListInput(player, title, currentList, format, onDone, onCancel);
            } else {
                currentList.add(input);
                requestListInput(player, title, currentList, format, onDone, onCancel);
            }
        }, onCancel);
    }

    private static void printList(Player player, String title, List<String> list) {
        String prefix = SeriaFarmPlugin.getInstance().getConfig().getString("settings.prefix", "&6&lSeriaFarm &8»");
        player.sendMessage(StaticColors.getHexMsg(prefix + " &a&lCurrent " + title + " list"));
        player.sendMessage(StaticColors.getHexMsg(prefix + " &7---------------------"));
        int i = 0;
        for (String line : list) {
            player.sendMessage(StaticColors.getHexMsg(prefix + " &f[" + i + "] " + line));
            i++;
        }
        player.sendMessage(StaticColors.getHexMsg(prefix + " &7--------------------"));
        player.sendMessage(StaticColors.getHexMsg("&7Type &c&lDone &7to finish, &c&lReset &7to clear, or &c&lRemoveLine <#>&7."));
    }
}
