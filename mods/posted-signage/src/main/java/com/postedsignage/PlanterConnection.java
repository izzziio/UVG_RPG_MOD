package com.postedsignage;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

/** Mitad vecina unida a esta maceta, o {@link #SINGLE}. */
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

    /** Rechaza conexiones verticales entre macetas. */
    public static PlanterConnection fromDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Planter connections must be horizontal");
        };
    }

    /** Estos nombres coinciden con las conexiones del JSON generado. */
    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
