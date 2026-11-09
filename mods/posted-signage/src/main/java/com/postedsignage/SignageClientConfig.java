package com.postedsignage;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Preferencias locales; no alteran datos ni distancias de los bloques. */
public final class SignageClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue TEXT_DISTANCE;
    static {
        var builder=new ModConfigSpec.Builder();
        TEXT_DISTANCE=builder.comment("Distancia máxima en bloques para las letras editables CIT. 0 conserva la distancia original.")
                .defineInRange("textDistance",0,0,96);
        SPEC=builder.build();
    }
    private SignageClientConfig(){}
    // El valor cero conserva el comportamiento anterior.
    public static boolean textVisible(double squaredDistance){
        int distance=TEXT_DISTANCE.get();return distance==0||squaredDistance<=distance*distance;
    }
}
