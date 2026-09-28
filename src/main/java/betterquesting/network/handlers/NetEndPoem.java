package betterquesting.network.handlers;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import betterquesting.api.network.QuestingPacket;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.client.QuestNotification;
import betterquesting.client.gui2.GuiEndPoem;
import betterquesting.core.BetterQuesting;
import betterquesting.network.PacketSender;
import betterquesting.network.PacketTypeRegistry;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Fired when a quest flagged as {@code is_final} is completed. Only the quest ID travels over the wire, the
 * client reads the poem assets from its own (already synced) copy of the quest book.
 */
public class NetEndPoem {

    private static final ResourceLocation ID_NAME = new ResourceLocation("betterquesting:end_poem");

    public static void registerHandler() {
        if (BetterQuesting.proxy.isClient()) {
            PacketTypeRegistry.INSTANCE.registerClientHandler(ID_NAME, NetEndPoem::onClient);
        }
    }

    public static void sendEndPoem(IQuest quest, EntityPlayer player) {
        if (!quest.getProperty(NativeProps.IS_FINAL) || !(player instanceof EntityPlayerMP)) {
            return;
        }

        UUID questId = QuestDatabase.INSTANCE.lookupKey(quest);
        if (questId == null) {
            return;
        }

        NBTTagCompound payload = new NBTTagCompound();
        payload.setString("questId", questId.toString());
        PacketSender.INSTANCE
            .sendToPlayers(new QuestingPacket(ID_NAME, payload), new EntityPlayerMP[] { (EntityPlayerMP) player });
    }

    @SideOnly(Side.CLIENT)
    private static void onClient(NBTTagCompound message) {
        IQuest quest;

        try {
            quest = QuestDatabase.INSTANCE.get(UUID.fromString(message.getString("questId")));
        } catch (IllegalArgumentException e) {
            return;
        }

        if (quest == null || !quest.getProperty(NativeProps.IS_FINAL)) {
            return;
        }

        ResourceLocation text = toLocation(quest.getProperty(NativeProps.POEM_TEXT));
        if (text == null) {
            BetterQuesting.logger.warn("Final quest has no end poem text file set: {}", message.getString("questId"));
            return;
        }

        GuiScreen poem = new GuiEndPoem(
            text,
            toLocation(quest.getProperty(NativeProps.POEM_MUSIC)),
            toLocation(quest.getProperty(NativeProps.POEM_LOGO)));

        // Let the completion notification play out first, then take over with the poem
        QuestNotification.setPendingScreen(poem);
        QuestNotification.flushPendingScreen();
    }

    @Nullable
    private static ResourceLocation toLocation(String value) {
        return value == null || value.trim()
            .isEmpty() ? null : new ResourceLocation(value.trim());
    }
}
