package com.cardinalstar.cubicchunks.worldgen.config.gui;

import net.minecraft.client.gui.GuiTextField;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Helpers for text fields that only accept integers. */
@SideOnly(Side.CLIENT)
public final class IntTextFields {

    public static final int VALID_COLOR = 0xE0E0E0;
    public static final int INVALID_COLOR = 0xFF5555;

    private IntTextFields() {}

    /** Passes a key to {@code field} if it's a digit, a leading '-', or an editing/navigation key. */
    public static void keyTyped(GuiTextField field, char eventChar, int eventKey) {
        boolean digit = Character.isDigit(eventChar);
        boolean sign = eventChar == '-' && field.getCursorPosition() == 0
            && !field.getText()
                .startsWith("-");
        boolean nav = eventKey == Keyboard.KEY_BACK || eventKey == Keyboard.KEY_DELETE
            || eventKey == Keyboard.KEY_LEFT
            || eventKey == Keyboard.KEY_RIGHT
            || eventKey == Keyboard.KEY_HOME
            || eventKey == Keyboard.KEY_END;
        if (digit || sign || nav) field.textboxKeyTyped(eventChar, eventKey);
    }

    /** The field's value, or null if it isn't an integer. */
    public static Integer parse(GuiTextField field) {
        try {
            return Integer.parseInt(
                field.getText()
                    .trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Sets the text and scrolls back to the start. setText scrolls to the end using the field's current width, which
     * is 0 until the field is first drawn, so the text would otherwise stay hidden until clicked.
     */
    public static void setText(GuiTextField field, String text) {
        field.setText(text);
        field.setCursorPositionZero();
    }
}
