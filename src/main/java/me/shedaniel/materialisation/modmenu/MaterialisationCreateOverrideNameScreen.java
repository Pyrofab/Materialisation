package me.shedaniel.materialisation.modmenu;

import com.mojang.blaze3d.platform.GlStateManager;
import me.shedaniel.materialisation.api.PartMaterial;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.TranslatableText;

import java.util.Locale;
import java.util.UUID;

import static me.shedaniel.materialisation.modmenu.MaterialisationMaterialsScreen.overlayBackground;

public class MaterialisationCreateOverrideNameScreen extends Screen {
    private MaterialisationMaterialsScreen og;
    private Screen parent;
    private PartMaterial partMaterial;
    private TextFieldWidget fileName, priority;
    private String randomFileName;
    private ButtonWidget continueButton;

    public MaterialisationCreateOverrideNameScreen(MaterialisationMaterialsScreen og, Screen parent, PartMaterial partMaterial) {
        super(new TranslatableText("config.title.materialisation.new_override"));
        this.og = og;
        this.parent = parent;
        this.partMaterial = partMaterial;
        String s = UUID.randomUUID().toString();
        this.randomFileName = partMaterial.getIdentifier().getPath() + "+" + s.substring(s.lastIndexOf('-') + 1, s.length()) + ".json";
    }

    @Override
    public boolean keyPressed(int int_1, int int_2, int int_3) {
        if (int_1 == 256 && this.shouldCloseOnEsc()) {
            minecraft.openScreen(parent);
            return true;
        }
        return super.keyPressed(int_1, int_2, int_3);
    }

    @Override
    protected void init() {
        super.init();
        addButton(new ButtonWidget(4, 4, 75, 20, I18n.translate("gui.back"), var1 -> {
            minecraft.openScreen(parent);
        }));
        addButton(continueButton = new ButtonWidget(width - 79, 4, 75, 20, I18n.translate("config.button.materialisation.continue"), var1 -> {
            minecraft.openScreen(new MaterialisationCreateOverrideScreen(og, this, partMaterial, fileName.getText().isEmpty() ? randomFileName : fileName.getText(), priority.getText().isEmpty() ? 0 : Double.parseDouble(priority.getText())));
        }));
        addButton(fileName = new TextFieldWidget(minecraft.textRenderer, width / 4, 50, width / 2, 18, fileName, "") {
            @Override
            public void render(int int_1, int int_2, float float_1) {
                if (getText().isEmpty())
                    setSuggestion(randomFileName);
                else setSuggestion(null);
                setEditableColor(!getText().isEmpty() && !getText().toLowerCase(Locale.ROOT).endsWith(".json") ? 16733525 : 14737632);
                super.render(int_1, int_2, float_1);
            }
        });
        addButton(priority = new TextFieldWidget(minecraft.textRenderer, width / 4, 118, width / 2, 18, priority, "") {
            @Override
            public void render(int int_1, int int_2, float float_1) {
                if (getText().isEmpty())
                    setSuggestion("0");
                else setSuggestion(null);
                if (!priority.getText().isEmpty())
                    try {
                        Double.parseDouble(getText());
                        setEditableColor(14737632);
                    } catch (NumberFormatException e) {
                        setEditableColor(16733525);
                    }
                super.render(int_1, int_2, float_1);
            }
        });
    }

    @Override
    public void render(int mouseX, int mouseY, float delta) {
        continueButton.active = fileName.getText().isEmpty() || fileName.getText().toLowerCase(Locale.ROOT).endsWith(".json");
        if (continueButton.active && !priority.getText().isEmpty()) {
            try {
                Double.parseDouble(priority.getText());
            } catch (NumberFormatException e) {
                continueButton.active = false;
            }
        }
        overlayBackground(0, 0, width, 28, 64, 64, 64, 255, 255);
        overlayBackground(0, 28, width, height, 32, 32, 32, 255, 255);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        GlStateManager.disableAlphaTest();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableTexture();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBufferBuilder();
        buffer.begin(7, VertexFormats.POSITION_UV_COLOR);
        buffer.vertex(0, 28 + 4, 0.0D).texture(0.0D, 1.0D).color(0, 0, 0, 0).next();
        buffer.vertex(this.width, 28 + 4, 0.0D).texture(1.0D, 1.0D).color(0, 0, 0, 0).next();
        buffer.vertex(this.width, 28, 0.0D).texture(1.0D, 0.0D).color(0, 0, 0, 255).next();
        buffer.vertex(0, 28, 0.0D).texture(0.0D, 0.0D).color(0, 0, 0, 255).next();
        tessellator.draw();
        GlStateManager.enableTexture();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableAlphaTest();
        GlStateManager.disableBlend();
        drawCenteredString(font, title.asFormattedString(), width / 2, 10, 16777215);
        drawString(font, I18n.translate("config.text.materialisation.override_json_file_name"), width / 4, 36, -6250336);
        drawString(font, I18n.translate("config.text.materialisation.override_json_file_saved"), width / 4, 74, -6250336);
        drawString(font, I18n.translate("config.text.materialisation.priority"), width / 4, 104, -6250336);
        super.render(mouseX, mouseY, delta);
    }
}
