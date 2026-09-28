package betterquesting.client.gui2;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.client.audio.PositionedSound;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.Charsets;
import org.lwjgl.opengl.GL11;

import betterquesting.core.BetterQuesting;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Vanilla {@code GuiWinGame} (the end poem) with the poem, credits, music and logo pulled from the final
 * quest's {@code poem_*} properties instead of being hard coded.
 * <p>
 * Text files follow the vanilla format: one line per paragraph, {@code §} formatting, {@code PLAYERNAME}
 * replaced with the player's name and the {@code §f§k§a§b} marker sequence replaced with scrambled letters
 * (use it for words that should look glitched/decrypted). Lines starting with {@code #} are ignored.
 */
@SideOnly(Side.CLIENT)
public class GuiEndPoem extends GuiScreen {

    private static final int LINE_WIDTH = 274;
    private static final int LINE_HEIGHT = 12;
    private static final float SCROLL_SPEED = 0.5F;
    private static final int FADE_TICKS = 40;
    private static final int CREDITS_GAP = 8;

    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    private static final String SECRET = "" + EnumChatFormatting.WHITE
        + EnumChatFormatting.OBFUSCATED
        + EnumChatFormatting.GREEN
        + EnumChatFormatting.AQUA;
    private static final String OBFUSCATED = "" + EnumChatFormatting.WHITE + EnumChatFormatting.OBFUSCATED;

    private final ResourceLocation poemText;
    private final ResourceLocation creditsText;
    private final ResourceLocation music;
    private final ResourceLocation logo;

    private final List<String> lines = new ArrayList<>();

    private int contentHeight = 0;
    private int tick = 0;
    private int fade = -1; // Counts up once the poem ran out, then the screen closes itself
    private PositionedSound playing;

    public GuiEndPoem(ResourceLocation poemText, ResourceLocation creditsText, ResourceLocation music,
        ResourceLocation logo) {
        this.poemText = poemText;
        this.creditsText = creditsText;
        this.music = music;
        this.logo = logo;
    }

    @Override
    public void initGui() {
        if (lines.isEmpty()) {
            readPoem(poemText, false);
            readPoem(creditsText, true);
            contentHeight = lines.size() * LINE_HEIGHT;
        }

        if (playing == null && music != null) {
            playing = new PoemSound(music);
            mc.getSoundHandler()
                .playSound(playing);
        }
    }

    @Override
    public void onGuiClosed() {
        if (playing != null) {
            mc.getSoundHandler()
                .stopSound(playing);
            playing = null;
        }
    }

    @Override
    public void updateScreen() {
        if (lines.isEmpty()) { // Nothing to read, i.e. a missing or empty text file
            mc.displayGuiScreen((GuiScreen) null);
            return;
        }

        tick++;

        if (fade < 0 && (float) tick > (contentHeight + height + height + 24) / SCROLL_SPEED) {
            fade = 0;
        }

        if (fade >= 0 && ++fade > FADE_TICKS) {
            mc.displayGuiScreen((GuiScreen) null);
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1 /* ESC */ || keyCode == 57 /* SPACE */) {
            mc.displayGuiScreen((GuiScreen) null);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            mc.displayGuiScreen((GuiScreen) null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    // region Text

    @SuppressWarnings("unchecked")
    private void readPoem(ResourceLocation location, boolean credits) {
        if (location == null) {
            return;
        }

        Random random = new Random(8124371L);
        int i;

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(
                mc.getResourceManager()
                    .getResource(location)
                    .getInputStream(),
                Charsets.UTF_8))) {
            if (credits) {
                for (i = 0; i < CREDITS_GAP; i++) {
                    lines.add("");
                }
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }

                line = line.replace(
                    "PLAYERNAME",
                    mc.getSession()
                        .getUsername());

                if (credits) {
                    line = line.replace("\t", "    ");
                }

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
        } catch (FileNotFoundException e) {
            BetterQuesting.logger.warn("End poem text file not found: {}", location);
        } catch (Exception e) {
            BetterQuesting.logger.error("Couldn't load end poem text " + location, e);
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
        int y = this.height + 50;

        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, offset, 0.0F);

        if (logo != null) {
            mc.getTextureManager()
                .bindTexture(logo);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.drawTexturedModalRect(x, y, 0, 0, 310, 44);
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

    private void drawFade() {
        int alpha = (int) (Math.min(fade / (float) FADE_TICKS, 1.0F) * 255.0F);
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
        if (fade >= 0) {
            drawFade();
        }
    }

    // endregion

    /**
     * Loops until stopped. {@code repeat} and the repeat delay are protected, so a subclass is the only way
     * to get a looping sound out of the vanilla sound handler.
     */
    public static class PoemSound extends PositionedSound {

        public PoemSound(ResourceLocation sound) {
            super(sound);
            this.volume = 1.0F;
            this.field_147663_c = 1.0F;
            this.xPosF = 0.0F;
            this.yPosF = 0.0F;
            this.zPosF = 0.0F;
            this.repeat = true;
            this.field_147665_h = 0;
            this.field_147666_i = AttenuationType.NONE;
        }
    }
}
