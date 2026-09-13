package com.postedsignage;

import net.minecraft.util.StringRepresentable;

/** One occupied square of the ten-wide, four-high T-shaped statue relief. */
public enum StatuePart implements StringRepresentable {
    STEM_BOTTOM_0("stem_bottom_0", 0, 0),
    STEM_BOTTOM_1("stem_bottom_1", 1, 0),
    STEM_BOTTOM_2("stem_bottom_2", 2, 0),
    STEM_BOTTOM_3("stem_bottom_3", 3, 0),
    STEM_TOP_0("stem_top_0", 0, 1),
    STEM_TOP_1("stem_top_1", 1, 1),
    STEM_TOP_2("stem_top_2", 2, 1),
    STEM_TOP_3("stem_top_3", 3, 1),
    BAR_BOTTOM_0("bar_bottom_0", -3, 2),
    BAR_BOTTOM_1("bar_bottom_1", -2, 2),
    BAR_BOTTOM_2("bar_bottom_2", -1, 2),
    BAR_BOTTOM_3("bar_bottom_3", 0, 2),
    BAR_BOTTOM_4("bar_bottom_4", 1, 2),
    BAR_BOTTOM_5("bar_bottom_5", 2, 2),
    BAR_BOTTOM_6("bar_bottom_6", 3, 2),
    BAR_BOTTOM_7("bar_bottom_7", 4, 2),
    BAR_BOTTOM_8("bar_bottom_8", 5, 2),
    BAR_BOTTOM_9("bar_bottom_9", 6, 2),
    BAR_TOP_0("bar_top_0", -3, 3),
    BAR_TOP_1("bar_top_1", -2, 3),
    BAR_TOP_2("bar_top_2", -1, 3),
    BAR_TOP_3("bar_top_3", 0, 3),
    BAR_TOP_4("bar_top_4", 1, 3),
    BAR_TOP_5("bar_top_5", 2, 3),
    BAR_TOP_6("bar_top_6", 3, 3),
    BAR_TOP_7("bar_top_7", 4, 3),
    BAR_TOP_8("bar_top_8", 5, 3),
    BAR_TOP_9("bar_top_9", 6, 3);

    private final String serializedName;
    private final int column;
    private final int row;

    StatuePart(String serializedName, int column, int row) {
        this.serializedName = serializedName;
        this.column = column;
        this.row = row;
    }

    public int column() {
        return column;
    }

    public int row() {
        return row;
    }

    public static StatuePart at(int column, int row) {
        StatuePart part = findAt(column, row);
        if (part != null) {
            return part;
        }
        throw new IllegalArgumentException("No statue part at " + column + ", " + row);
    }

    public static StatuePart findAt(int column, int row) {
        for (StatuePart part : values()) {
            if (part.column == column && part.row == row) {
                return part;
            }
        }
        return null;
    }

    public StatuePart mirrored() {
        return at(3 - column, row);
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
