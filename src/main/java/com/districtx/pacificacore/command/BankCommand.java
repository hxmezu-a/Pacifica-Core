package com.districtx.pacificacore.command;

import com.districtx.pacificacore.api.BankingService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BankCommand implements CommandExecutor {
    private final BankingService banking;

    public BankCommand(BankingService banking) {
        this.banking = banking;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use banking.");
            return true;
        }
        banking.openBankingMenu(player);
        return true;
    }
}