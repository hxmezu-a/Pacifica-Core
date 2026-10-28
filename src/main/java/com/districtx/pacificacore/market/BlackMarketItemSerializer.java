package com.districtx.pacificacore.market;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

public final class BlackMarketItemSerializer {
    private BlackMarketItemSerializer() { }

    public static String serialize(ItemStack item) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static ItemStack deserialize(String value) throws IOException, ClassNotFoundException {
        byte[] bytes = Base64.getDecoder().decode(value);
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            Object object = input.readObject();
            if (!(object instanceof ItemStack)) throw new IOException("Serialized offer is not an ItemStack");
            return ((ItemStack) object).clone();
        }
    }
}