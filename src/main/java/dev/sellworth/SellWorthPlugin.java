package dev.sellworth;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class SellWorthPlugin extends JavaPlugin {
    private PriceManager prices;

    @Override
    public void onEnable() {
        prices = new PriceManager(this);
        prices.load();

        WorthLore lore = new WorthLore(this, prices);
        lore.registerPackets(this);
        getServer().getPluginManager().registerEvents(lore, this);

        HistoryManager historyManager = new HistoryManager(this);
        SellGui sell = new SellGui(this, prices, lore, historyManager);
        HistoryGui historyGui = new HistoryGui(this, historyManager, lore, sell);
        sell.setHistoryGui(historyGui);
        getServer().getPluginManager().registerEvents(sell, this);
        getServer().getPluginManager().registerEvents(historyGui, this);
        getCommand("sell").setExecutor(sell);
        getCommand("sellhistory").setExecutor(historyGui);

        WorthGui worth = new WorthGui(prices, lore);
        getServer().getPluginManager().registerEvents(worth, this);
        getCommand("worth").setExecutor(worth);
        getCommand("worth").setTabCompleter(worth);

        getCommand("sellreload").setExecutor((sender, cmd, label, args) -> {
            prices.load();
            sender.sendMessage("§aprices.yml reloaded (" + prices.all().size() + " items).");
            return true;
        });
    }

    /** Looked up lazily so the economy plugin can register after us. */
    public Economy economy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }
}
