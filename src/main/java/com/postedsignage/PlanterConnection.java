package com.postedsignage;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** The neighboring half joined to this planter, or {@link #SINGLE}. */
public enum PlanterConnection implements StringRepresentable {
    SINGLE("single", null),
    NORTH("north", Direction.NORTH),
    EAST("east", Direction.EAST),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST);

    private final String serializedName;
    @Nullable
    private final Direction direction;

    PlanterConnection(String serializedName, @Nullable Direction direction) {
        this.serializedName = serializedName;
        this.direction = direction;
    }

    @Nullable
    public Direction direction() {
        return direction;
    }

    public static PlanterConnection fromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Planter connections must be horizontal");
        };
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
