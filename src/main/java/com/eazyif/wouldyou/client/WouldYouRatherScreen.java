package com.eazyif.wouldyou.client;

import com.eazyif.wouldyou.network.ChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

/**
 * Modal popup displaying a Would-You-Rather question with two option buttons.
 * Pause and game-input are blocked until the player picks one.
 */
public class WouldYouRatherScreen extends Screen {

    private final String question;
    private final String optionA;
    private final String optionB;
    private boolean answered = false;

    public WouldYouRatherScreen(String question, String optionA, String optionB) {
        super(Text.translatable("wouldyou.title"));
        this.question = question;
        this.optionA = optionA;
        this.optionB = optionB;
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int boxWidth = Math.min(this.width - 40, 360);
        int btnWidth = boxWidth;
        int btnHeight = 40;

        int top = this.height / 2 - 40;

        // Question text widget
        MultilineTextWidget questionWidget = new MultilineTextWidget(
                centerX - boxWidth / 2,
                top - 60,
                Text.literal(question),
                this.textRenderer);
        questionWidget.setMaxWidth(boxWidth);
        questionWidget.setCentered(true);
        this.addDrawableChild(questionWidget);

        ButtonWidget aButton = ButtonWidget.builder(
                Text.literal(optionA),
                btn -> choose(0))
                .dimensions(centerX - btnWidth / 2, top, btnWidth, btnHeight)
                .build();

        ButtonWidget bButton = ButtonWidget.builder(
                Text.literal(optionB),
                btn -> choose(1))
                .dimensions(centerX - btnWidth / 2, top + btnHeight + 8, btnWidth, btnHeight)
                .build();

        this.addDrawableChild(aButton);
        this.addDrawableChild(bButton);
    }

    private void choose(int index) {
        if (answered) return;
        answered = true;
        ClientPlayNetworking.send(new ChoicePayload(index));
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        // Title above the question text
        context.drawCenteredTextWithShadow(this.textRenderer,
                this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFFAA);
        super.render(context, mouseX, mouseY, delta);
    }
}
