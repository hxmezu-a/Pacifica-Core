package com.districtx.pacificacore.bank;

import com.districtx.pacificacore.PacificaCore;
import com.districtx.pacificacore.api.BankingService;
import com.districtx.pacificacore.api.EconomyService;
import com.districtx.pacificacore.economy.MoneyFormatter;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class BankingGuiManager implements Listener, CommandExecutor {
    private static final List<Integer> LIGHT_PANES = List.of(0, 9, 18, 27, 36, 45, 8, 17, 26, 35, 44, 53);
    private static final List<Integer> WHITE_PANES = List.of(1, 10, 19, 28, 37, 46, 7, 16, 25, 34, 43, 52);
    private static final List<Integer> BLACK_PANES = List.of(2, 3, 4, 5, 6);
    private static final List<Integer> AMOUNT_SLOTS = List.of(12, 13, 14, 21, 22, 23, 30, 31, 32, 39, 40, 41);
    private static final List<Integer> RECIPIENT_SLOTS = List.of(11, 12, 13, 14, 15, 20, 21, 22, 23, 24,
            29, 30, 31, 32, 33, 38, 39, 40, 41, 42);
    private static final List<Integer> FIXED_SLOTS = List.of(12, 13, 14, 21, 22, 23, 30, 31, 32);
    private static final List<BigDecimal> FIXED_AMOUNTS = List.of(
            new BigDecimal("200"), new BigDecimal("500"), new BigDecimal("1000"),
            new BigDecimal("2500"), new BigDecimal("5000"), new BigDecimal("10000"),
            new BigDecimal("20000"), new BigDecimal("50000"), new BigDecimal("100000"));
    private static final Pattern AMOUNT_INPUT = Pattern.compile("[0-9]+(?:\\.[0-9]{1,8})?");

    private final PacificaCore plugin;
    private final EconomyService economy;
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, Flow> customInputFlows = new HashMap<>();
    private BankingService banking;

    public BankingGuiManager(PacificaCore plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public void setBankingService(BankingService banking) {
        this.banking = banking;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color("&cOnly players can use banking."));
            return true;
        }
        banking.openBankingMenu(player);
        return true;
    }

    public void openMainMenu(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player));
            return;
        }
        banking.getBankBalance(player.getUniqueId()).whenComplete((balance, error) -> runMain(() -> {
            if (!player.isOnline()) return;
            if (error != null) {
                player.sendMessage(message("bank-error", "&cBanking is temporarily unavailable."));
                return;
            }
            Inventory inventory = create(BankingMenuHolder.Screen.MAIN, player, null, null, 0, List.of(),
                    "&9&lBank Account");
            decorate(inventory);
            inventory.setItem(13, item("PLAYER_HEAD", "&aTransfer Money", "&7Send money to another player!"));
            inventory.setItem(29, item("RED_DYE", "&c&lWithdraw Money", "&7Take it all!"));
            inventory.setItem(31, item("BOOK", "&9&lBank Balance: &a&l$" + MoneyFormatter.format(balance),
                    "&7You a rich fool!"));
            inventory.setItem(33, item("SLIME_BALL", "&a&lDeposit Money", "&7Trusy me! Your money is safe here!"));
            inventory.setItem(49, item("PAPER", "&9&lBanking", "&7Your financial buddy!"));
            player.openInventory(inventory);
        }));
    }

    public void openAmountMenu(Player player, Flow flow) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> openAmountMenu(player, flow));
            return;
        }
        available(player.getUniqueId(), flow).whenComplete((balance, error) -> runMain(() -> {
            if (!player.isOnline()) return;
            if (error != null) {
                player.sendMessage(message("bank-error", "&cBanking is temporarily unavailable."));
                return;
            }
            String title = switch (flow) {
                case WITHDRAW -> "&9&lBanking: Withdraw";
                case TRANSFER -> "&9&lBanking: Transfer Money";
                case DEPOSIT -> "&9&lBanking: Deposit";
            };
            Inventory inventory = create(BankingMenuHolder.Screen.AMOUNT, player, flow, null, 0, List.of(), title);
            decorate(inventory);
            for (int index = 0; index < FIXED_SLOTS.size(); index++) {
                BigDecimal amount = FIXED_AMOUNTS.get(index);
                String name = (index <= 3 ? "&a&l$" : "&c&l$") + amount.toPlainString();
                inventory.setItem(FIXED_SLOTS.get(index), item("PAPER", name, amountLore(flow).toArray(String[]::new)));
            }
            // Rounding down at the supported precision prevents half of an available balance from overdrawing it.
            BigDecimal half = balance.divide(BigDecimal.valueOf(2), 8, RoundingMode.DOWN).stripTrailingZeros();
            inventory.setItem(39, item("PAPER", "&9&lHalf: &a&l$" + MoneyFormatter.format(half), amountLore(flow).toArray(String[]::new)));
            inventory.setItem(40, item("PAPER", "&9All: &a&l$" + MoneyFormatter.format(balance), amountLore(flow).toArray(String[]::new)));
            inventory.setItem(41, item("WRITABLE_BOOK", "&a&lCustom Amount", "&7Click to choose an amount!"));
            inventory.setItem(47, item("REDSTONE", "&c&lBack", "&7Return to the banking page!"));
            player.openInventory(inventory);
        }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory().getHolder() instanceof BankingMenuHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || !holder.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        switch (holder.screen) {
            case MAIN -> handleMainClick(player, slot);
            case AMOUNT -> handleAmountClick(player, holder, slot);
            case RECIPIENTS -> handleRecipientClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BankingMenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || event.getView().getTopInventory().getType() != InventoryType.ANVIL
                || !customInputFlows.containsKey(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Flow flow = customInputFlows.remove(player.getUniqueId());
            if (flow != null && player.isOnline()) openAmountMenu(player, flow);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        pending.remove(id);
        customInputFlows.remove(id);
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 13 -> openAmountMenu(player, Flow.TRANSFER);
            case 29 -> openAmountMenu(player, Flow.WITHDRAW);
            case 33 -> openAmountMenu(player, Flow.DEPOSIT);
            default -> { }
        }
    }

    private void handleAmountClick(Player player, BankingMenuHolder holder, int slot) {
        if (slot == 47) {
            banking.openBankingMenu(player);
            return;
        }
        if (slot == 41) {
            openCustomAmount(player, holder.flow);
            return;
        }
        if (slot == 39 || slot == 40) {
            available(player.getUniqueId(), holder.flow).whenComplete((balance, error) -> runMain(() -> {
                if (!player.isOnline()) return;
                if (error != null) send(player, "bank-error", "&cBanking is temporarily unavailable.");
                else selectAmount(player, holder.flow, slot == 39 ? half(balance) : balance);
            }));
            return;
        }
        int amountIndex = FIXED_SLOTS.indexOf(slot);
        if (amountIndex >= 0) selectAmount(player, holder.flow, FIXED_AMOUNTS.get(amountIndex));
    }

    private void handleRecipientClick(Player player, BankingMenuHolder holder, int slot) {
        if (slot == 47) {
            openAmountMenu(player, Flow.TRANSFER);
            return;
        }
        int pageSlot = RECIPIENT_SLOTS.indexOf(slot);
        if (slot == 48 && holder.page > 0) {
            openRecipientMenu(player, holder.amount, holder.players, holder.page - 1);
            return;
        }
        int lastPage = Math.max(0, (holder.players.size() - 1) / RECIPIENT_SLOTS.size());
        if (slot == 50 && holder.page < lastPage) {
            openRecipientMenu(player, holder.amount, holder.players, holder.page + 1);
            return;
        }
        if (pageSlot < 0) return;
        int index = holder.page * RECIPIENT_SLOTS.size() + pageSlot;
        if (index < 0 || index >= holder.players.size()) return;
        PlayerEntry target = holder.players.get(index);
        if (target.uuid().equals(player.getUniqueId())) {
            send(player, "bank-self-transfer", "&cYou cannot transfer money to yourself.");
            return;
        }
        if (!pending.add(player.getUniqueId())) return;
        banking.transfer(player.getUniqueId(), target.uuid(), holder.amount).whenComplete((success, error) -> runMain(() -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (error != null || !Boolean.TRUE.equals(success)) {
                send(player, "bank-transaction-failed", "&cThe transfer failed. Check your bank balance and try again.");
                return;
            }
            player.closeInventory();
            send(player, "bank-transfer-sent", "&aTransferred $%amount% to %player%.",
                    "amount", MoneyFormatter.format(holder.amount), "player", target.name());
            Player onlineRecipient = Bukkit.getPlayer(target.uuid());
            if (onlineRecipient != null) {
                send(onlineRecipient, "bank-transfer-received", "&aReceived $%amount% from %player%.",
                        "amount", MoneyFormatter.format(holder.amount), "player", player.getName());
            }
        }));
    }

    private void selectAmount(Player player, Flow flow, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            send(player, "bank-invalid-amount", "&cThe available amount must be greater than zero.");
            openAmountMenu(player, flow);
            return;
        }
        available(player.getUniqueId(), flow).whenComplete((available, error) -> runMain(() -> {
            if (!player.isOnline()) return;
            if (error != null) {
                send(player, "bank-error", "&cBanking is temporarily unavailable.");
                openAmountMenu(player, flow);
                return;
            }
            if (amount.compareTo(available) > 0) {
                send(player, "bank-insufficient-funds", "&cYou do not have enough available funds.");
                openAmountMenu(player, flow);
                return;
            }
            if (flow == Flow.TRANSFER) {
                openRecipientMenu(player, amount, knownPlayers(player.getUniqueId()), 0);
            } else {
                transact(player, flow, amount);
            }
        }));
    }

    private void transact(Player player, Flow flow, BigDecimal amount) {
        if (!pending.add(player.getUniqueId())) return;
        CompletableFuture<Boolean> transaction = flow == Flow.DEPOSIT
                ? banking.deposit(player.getUniqueId(), amount)
                : banking.withdraw(player.getUniqueId(), amount);
        transaction.whenComplete((success, error) -> runMain(() -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) return;
            if (error != null || !Boolean.TRUE.equals(success)) {
                send(player, "bank-transaction-failed", "&cThe transaction failed. Check your available funds and try again.");
                openAmountMenu(player, flow);
                return;
            }
            player.closeInventory();
            String key = flow == Flow.DEPOSIT ? "bank-deposit-success" : "bank-withdraw-success";
            String fallback = flow == Flow.DEPOSIT ? "&aDeposited $%amount% into your bank." : "&aWithdrew $%amount% from your bank.";
            send(player, key, fallback, "amount", MoneyFormatter.format(amount));
        }));
    }

    private void openCustomAmount(Player player, Flow flow) {
        customInputFlows.put(player.getUniqueId(), flow);
        new AnvilGUI.Builder().plugin(plugin).title(color("&7&lEnter amount")).text("§7")
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return List.of();
                    String input = snapshot.getText();
                    if (input == null || !AMOUNT_INPUT.matcher(input).matches()) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("Enter a positive amount"));
                    }
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(input);
                    } catch (NumberFormatException exception) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("Invalid amount"));
                    }
                    if (!validAmount(amount)) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText("Invalid amount"));
                    }
                    customInputFlows.remove(player.getUniqueId());
                    selectAmount(player, flow, amount);
                    return List.of(AnvilGUI.ResponseAction.close());
                }).open(player);
    }

    private void openRecipientMenu(Player player, BigDecimal amount, List<PlayerEntry> knownPlayers, int page) {
        List<PlayerEntry> players = List.copyOf(knownPlayers);
        Inventory inventory = create(BankingMenuHolder.Screen.RECIPIENTS, player, Flow.TRANSFER, amount,
                page, players, "&9&lSelect to Transfer Money");
        decorate(inventory);
        inventory.setItem(47, item("REDSTONE", "&c&lBack", "&7Return to the amount menu!"));
        int lastPage = Math.max(0, (players.size() - 1) / RECIPIENT_SLOTS.size());
        inventory.setItem(48, page > 0 ? item("ARROW", "&e&lPrevious Page") : item("GRAY_STAINED_GLASS_PANE", "&7"));
        inventory.setItem(49, item("GRAY_STAINED_GLASS_PANE", "&7"));
        inventory.setItem(50, page < lastPage ? item("ARROW", "&e&lNext Page") : item("GRAY_STAINED_GLASS_PANE", "&7"));
        inventory.setItem(51, item("GRAY_STAINED_GLASS_PANE", "&7"));
        int first = page * RECIPIENT_SLOTS.size();
        for (int slotIndex = 0; slotIndex < RECIPIENT_SLOTS.size() && first + slotIndex < players.size(); slotIndex++) {
            PlayerEntry entry = players.get(first + slotIndex);
            inventory.setItem(RECIPIENT_SLOTS.get(slotIndex), playerHead(entry));
        }
        if (players.isEmpty()) inventory.setItem(22, item("BARRIER", "&cNo known players"));
        player.openInventory(inventory);
    }

    private ItemStack playerHead(PlayerEntry entry) {
        ItemStack head = item("PLAYER_HEAD", "&e" + entry.name());
        if (head.getItemMeta() instanceof SkullMeta meta) {
            try {
                OfflinePlayer profile = Bukkit.getOfflinePlayer(entry.uuid());
                meta.setOwningPlayer(profile);
                head.setItemMeta(meta);
            } catch (RuntimeException ignored) {
            }
        }
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(color("&7Click to select this recipient.")));
            head.setItemMeta(meta);
        }
        return head;
    }

    private List<PlayerEntry> knownPlayers(UUID senderId) {
        Map<UUID, PlayerEntry> players = new HashMap<>();
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            UUID id = offline.getUniqueId();
            if (id.equals(senderId)) continue;
            String name = offline.getName();
            if (name == null || name.isBlank()) name = id.toString().substring(0, 8);
            players.put(id, new PlayerEntry(id, name));
        }
        return players.values().stream().sorted(Comparator.comparing(entry -> entry.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private CompletableFuture<BigDecimal> available(UUID playerId, Flow flow) {
        return flow == Flow.DEPOSIT
                ? CompletableFuture.completedFuture(economy.getBalance(playerId))
                : banking.getBankBalance(playerId);
    }

    private BigDecimal half(BigDecimal amount) {
        return amount.divide(BigDecimal.valueOf(2), 8, RoundingMode.DOWN).stripTrailingZeros();
    }

    private List<String> amountLore(Flow flow) {
        return List.of(color(switch (flow) {
            case WITHDRAW -> "&7Click to withdraw!";
            case TRANSFER -> "&7Click to transfer!";
            case DEPOSIT -> "&7Click to deposit!";
        }));
    }

    private String actionLore(Flow flow) {
        return switch (flow) {
            case WITHDRAW -> "&7Click to withdraw!";
            case TRANSFER -> "&7Click to transfer!";
            case DEPOSIT -> "&7Click to deposit!";
        };
    }

    private Inventory create(BankingMenuHolder.Screen screen, Player player, Flow flow, BigDecimal amount,
                             int page, List<PlayerEntry> players, String title) {
        BankingMenuHolder holder = new BankingMenuHolder(screen, player.getUniqueId(), flow, amount, page, players);
        Inventory inventory = Bukkit.createInventory(holder, 54, color(title));
        holder.setInventory(inventory);
        return inventory;
    }

    private void decorate(Inventory inventory) {
        ItemStack gray = item("GRAY_STAINED_GLASS_PANE", "&7");
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, gray.clone());
        for (int slot : LIGHT_PANES) inventory.setItem(slot, item("LIGHT_GRAY_STAINED_GLASS_PANE", "&7"));
        for (int slot : WHITE_PANES) inventory.setItem(slot, item("WHITE_STAINED_GLASS_PANE", "&7"));
        for (int slot : BLACK_PANES) inventory.setItem(slot, item("BLACK_STAINED_GLASS_PANE", "&7"));
    }

    private ItemStack item(String materialName, String name, String... lore) {
        Material material = Material.matchMaterial(materialName);
        ItemStack stack = new ItemStack(material == null ? Material.STONE : material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore.length > 0) meta.setLore(Arrays.stream(lore).map(this::color).toList());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private boolean validAmount(BigDecimal amount) {
        if (amount.signum() <= 0) return false;
        BigDecimal normalized = amount.stripTrailingZeros();
        return normalized.scale() <= 8 && normalized.precision() - normalized.scale() <= 38;
    }

    private String message(String key, String fallback) {
        return color(plugin.getMessage(key, fallback));
    }

    private void send(Player player, String key, String fallback, String... replacements) {
        String text = plugin.getMessage(key, fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            text = text.replace("%" + replacements[index] + "%", replacements[index + 1]);
        }
        player.sendMessage(color(text));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void runMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    public enum Flow { DEPOSIT, WITHDRAW, TRANSFER }
    public record PlayerEntry(UUID uuid, String name) { }
}