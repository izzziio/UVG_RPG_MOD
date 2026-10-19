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

    // Asocia el nombre guardado con la dirección del vecino.
    PlanterConnection(String serializedName, @Nullable Direction direction) {
        this.serializedName = serializedName;
        this.direction = direction;
    }

    /** SINGLE has no partner direction; callers must handle null before moving to a neighbor. */
    @Nullable
    public Direction direction() {
        return direction;
    }

    /** Reject vertical connections because planter pairing is limited to the horizontal plane. */
    public static PlanterConnection fromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Planter connections must be horizontal");
        };
    }

    /** These names must match the connection values used by the generated blockstate JSON. */
    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
