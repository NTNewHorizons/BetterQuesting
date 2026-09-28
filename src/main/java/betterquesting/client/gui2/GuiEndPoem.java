package betterquesting.client.gui2;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.Charsets;
import org.lwjgl.opengl.GL11;

import betterquesting.api2.client.gui.misc.TextureSizeHelper;
import betterquesting.api2.client.gui.resources.textures.IGuiTexture;
import betterquesting.api2.client.gui.resources.textures.SimpleNoUVTexture;
import betterquesting.core.BetterQuesting;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Vanilla {@code GuiWinGame} (the end poem) with the poem, music and logo pulled from the final
 * quest's {@code poem_*} properties instead of being hard coded.
 * <p>
 * The poem follows the vanilla format: one line per paragraph, {@code §} formatting, {@code PLAYERNAME}
 * replaced with the player's name and the {@code §f§k§a§b} marker sequence replaced with scrambled letters
 * (use it for words that should look glitched/decrypted). Lines starting with {@code #} are ignored.
 * <p>
 * A file named after the client's language next to the poem wins, so "yourmod:texts/end_poem" reads
 * {@code texts/end_poem/ru_RU.txt} for a Russian client and falls back to {@code texts/end_poem.txt}.
 */
@SideOnly(Side.CLIENT)
public class GuiEndPoem extends GuiScreen {

    private static final int LINE_WIDTH = 274;
    private static final int LINE_HEIGHT = 12;
    private static final float SCROLL_SPEED = 0.5F;
    private static final int LOGO_WIDTH = 310;
    private static final int LOGO_HEIGHT = 44;
    private static final int LOGO_GAP = 200; // Vanilla keeps the text this far below the logo
    private static final int HOLD_TICKS = 20 * 20; // Rest 20s on the last line before fading out
    private static final int FADE_TICKS = 40;

    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    private static final String SECRET = "" + EnumChatFormatting.WHITE
        + EnumChatFormatting.OBFUSCATED
        + EnumChatFormatting.GREEN
        + EnumChatFormatting.AQUA;
    private static final String OBFUSCATED = "" + EnumChatFormatting.WHITE + EnumChatFormatting.OBFUSCATED;

    private final ResourceLocation poemText;
    private final ResourceLocation music;
    private final ResourceLocation logo;

    private final List<String> lines = new ArrayList<>();

    private int contentHeight = 0;
    private int endTick = 0; // Tick at which the last line has scrolled into the middle
    private int tick = 0;
    private IGuiTexture logoTexture;
    private PositionedSound playing;
    private boolean rangOut = false; // Poem finished on its own, let the track play to its end

    public GuiEndPoem(ResourceLocation poemText, ResourceLocation music, ResourceLocation logo) {
        this.poemText = poemText;
        this.music = music;
        this.logo = logo;
    }

    @Override
    public void initGui() {
        if (lines.isEmpty()) {
            readPoem(poemText);
            contentHeight = lines.size() * LINE_HEIGHT;
        }

        if (logo != null && logoTexture == null) {
            // Vanilla's drawTexturedModalRect assumes a 256px wide texture, so use the 0..1 UV version instead
            logoTexture = new SimpleNoUVTexture(logo, TextureSizeHelper.getDimension(logo)).maintainAspect(true);
        }

        // The last line rests in the middle of the screen, same place the drawing pins it to
        endTick = (int) (2 * (contentHeight - LINE_HEIGHT + 56.0F + height / 2.0F));

        if (playing == null && music != null) {
            playing = new PoemSound(music);
            mc.getSoundHandler()
                .playSound(playing);
        }
    }

    @Override
    public void onGuiClosed() {
        // A poem that ran its course leaves the track alone so it can finish on its own
        if (playing != null && !rangOut) {
            mc.getSoundHandler()
                .stopSound(playing);
            playing = null;
        }
    }

    private void close(boolean poemFinished) {
        rangOut = poemFinished;
        mc.displayGuiScreen((GuiScreen) null);
    }

    @Override
    public void updateScreen() {
        if (lines.isEmpty()) { // Nothing to read, i.e. a missing or empty text file
            close(false);
            return;
        }

        tick++;

        if (tick >= endTick + HOLD_TICKS + FADE_TICKS) {
            close(true);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 /* ESC */ || keyCode == 57 /* SPACE */) {
            close(false);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            close(false);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    // region Text

    @SuppressWarnings("unchecked")
    private void readPoem(ResourceLocation base) {
        ResourceLocation localized = localized(base);

        if (localized != null && readText(localized)) {
            return;
        }

        if (!readText(base)) {
            BetterQuesting.logger.warn("End poem text file not found: {}", base);
        }
    }

    /** {@code yourmod:texts/end_poem} -> {@code yourmod:texts/end_poem/ru_RU.txt} for a ru_RU client */
    @Nullable
    private static ResourceLocation localized(ResourceLocation base) {
        String lang = Minecraft.getMinecraft()
            .getLanguageManager()
            .getCurrentLanguage()
            .getLanguageCode();
        return lang == null || lang.isEmpty() ? null
            : new ResourceLocation(base.getResourceDomain(), base.getResourcePath() + "/" + lang + ".txt");
    }

    /** @return true if the file was there and has been read */
    private boolean readText(ResourceLocation location) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                mc.getResourceManager()
                    .getResource(location)
                    .getInputStream(),
                Charsets.UTF_8))) {
            Random random = new Random(8124371L);
            int i;
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }

                line = line.replace(
                    "PLAYERNAME",
                    mc.getSession()
                        .getUsername());

                String head, tail;
                for (; line.contains(
                    SECRET); line = head + OBFUSCATED + "XXXXXXXX".substring(0, random.nextInt(4) + 3) + tail) {
                    i = line.indexOf(SECRET);
                    head = line.substring(0, i);
                    tail = line.substring(i + SECRET.length());
                }

                lines.addAll(fontRendererObj.listFormattedStringToWidth(line, LINE_WIDTH));
                lines.add("");
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // endregion

    // region Rendering

    private void drawBackground(float partialTicks) {
        Tessellator tessellator = Tessellator.instance;

        mc.getTextureManager()
            .bindTexture(Gui.optionsBackground);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, 1.0F);

        int scrollX = this.width;
        float y0 = 0.0F - (tick + partialTicks) * 0.5F * SCROLL_SPEED;
        float y1 = this.height - (tick + partialTicks) * 0.5F * SCROLL_SPEED;
        float bright = (tick + partialTicks) * 0.02F;
        float target = ((contentHeight + height + height + 24) / SCROLL_SPEED - 20.0F - (tick + partialTicks)) * 0.005F;

        if (target < bright) {
            bright = target;
        }

        if (bright > 1.0F) {
            bright = 1.0F;
        }

        bright = Math.min(bright * bright * 96.0F / 255.0F, 1.0F);

        tessellator.setColorOpaque_F(bright, bright, bright);
        tessellator.addVertexWithUV(0.0D, this.height, this.zLevel, 0.0D, y0 * 0.015625F);
        tessellator.addVertexWithUV(scrollX, this.height, this.zLevel, scrollX * 0.015625F, y0 * 0.015625F);
        tessellator.addVertexWithUV(scrollX, 0.0D, this.zLevel, scrollX * 0.015625F, y1 * 0.015625F);
        tessellator.addVertexWithUV(0.0D, 0.0D, this.zLevel, 0.0D, y1 * 0.015625F);
        tessellator.draw();
    }

    private void drawPoem(float partialTicks) {
        float offset = -(tick + partialTicks) * SCROLL_SPEED;
        int x = this.width / 2 - LINE_WIDTH / 2;
        int logoY = this.height + 50;
        int y = logoY + LOGO_GAP;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, offset, 0.0F);

        if (logoTexture != null) {
            logoTexture.drawTexture(x, logoY, LOGO_WIDTH, LOGO_HEIGHT, this.zLevel, partialTicks);
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (i == lines.size() - 1) {
                float last = y + offset - (this.height / 2 - 6);
                if (last < 0.0F) {
                    GL11.glTranslatef(0.0F, -last, 0.0F);
                }
            }

            if (y + offset + 12.0F + 8.0F > 0.0F && y + offset < this.height) {
                if (line.startsWith("[C]")) {
                    String centered = line.substring(3);
                    this.fontRendererObj.drawStringWithShadow(
                        centered,
                        x + (LINE_WIDTH - this.fontRendererObj.getStringWidth(centered)) / 2,
                        y,
                        0xFFFFFF);
                } else {
                    this.fontRendererObj.fontRandom.setSeed(i * 4238972211L + tick / 4);
                    this.fontRendererObj.drawStringWithShadow(line, x, y, 0xFFFFFF);
                }
            }

            y += LINE_HEIGHT;
        }

        GL11.glPopMatrix();
    }

    private void drawVignette() {
        Tessellator tessellator = Tessellator.instance;

        mc.getTextureManager()
            .bindTexture(VIGNETTE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0F, 1.0F, 1.0F, 1.0F);
        tessellator.addVertexWithUV(0.0D, this.height, this.zLevel, 0.0D, 1.0D);
        tessellator.addVertexWithUV(this.width, this.height, this.zLevel, 1.0D, 1.0D);
        tessellator.addVertexWithUV(this.width, 0.0D, this.zLevel, 1.0D, 0.0D);
        tessellator.addVertexWithUV(0.0D, 0.0D, this.zLevel, 0.0D, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
    }

    private void drawFade(int fadeTick) {
        int alpha = (int) (Math.min(fadeTick / (float) FADE_TICKS, 1.0F) * 255.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        Gui.drawRect(0, 0, this.width, this.height, alpha << 24);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawBackground(partialTicks);
        drawPoem(partialTicks);
        drawVignette();

        int fadeTick = tick - (endTick + HOLD_TICKS);
        if (fadeTick > 0) {
            drawFade(fadeTick);
        }
    }

    // endregion

    /**
     * Plays once through. {@code repeat} and the repeat delay are protected, so a subclass is the only way to
     * hand a custom sound to the vanilla sound handler.
     */
    public static class PoemSound extends PositionedSound {

        public PoemSound(ResourceLocation sound) {
            super(sound);
            this.volume = 1.0F;
            this.field_147663_c = 1.0F;
            this.xPosF = 0.0F;
            this.yPosF = 0.0F;
            this.zPosF = 0.0F;
            this.repeat = false;
            this.field_147665_h = 0;
            this.field_147666_i = AttenuationType.NONE;
        }
    }
}
