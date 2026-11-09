package com.postedsignage;

/** Geometría generada por build_interiors.py; medidas en píxeles. */
public final class InteriorGeometry {
    private InteriorGeometry(){}
    public enum Kind { TV, ELEVATOR, BUTTONS, CABINET, RULES }
    // Cada estado reutiliza las cajas del modelo editable.
    public static double[][] boxes(Kind kind,boolean active){
        return switch(kind){
            case TV -> new double[][]{{12.5,12.8,15.2,19.5,20.2,16.0},{14.0,14.0,12.0,18.0,19.0,15.2},{0.5,7.0,10.3,31.5,25.0,12.0},{1.1,7.65,10.2,30.9,24.4,10.32},{1.0,7.1,10.16,31.0,7.65,10.4}};
            case ELEVATOR -> active?new double[][]{{0.0,0.0,6.7,1.0,48.0,9.4},{31.0,0.0,6.7,32.0,48.0,9.4},{1.0,45.5,6.7,31.0,48.0,9.4},{1.0,0.0,6.4,31.0,0.4,9.7},{0.95,0.4,7.3,2.0,45.5,8.8},{30.0,0.4,7.3,31.05,45.5,8.8}}:new double[][]{{0.0,0.0,6.7,1.0,48.0,9.4},{31.0,0.0,6.7,32.0,48.0,9.4},{1.0,45.5,6.7,31.0,48.0,9.4},{1.0,0.0,6.4,31.0,0.4,9.7},{0.95,0.4,7.3,15.96,45.5,8.8},{16.04,0.4,7.3,31.05,45.5,8.8}};
            case BUTTONS -> active?new double[][]{{5.0,1.5,14.8,11.0,14.5,16.0}}:new double[][]{{5.0,1.5,14.8,11.0,14.5,16.0}};
            case CABINET -> new double[][]{{1.0,0.5,13.0,15.0,15.5,16.0},{1.7,1.2,12.9,14.3,14.8,13.1},{1.0,0.5,12.0,1.8,15.5,13.5},{14.2,0.5,12.0,15.0,15.5,13.5},{1.8,14.7,12.0,14.2,15.5,13.5},{1.8,0.5,12.0,14.2,1.3,13.5},{1.25,7.3,11.8,1.8,8.8,12.1}};
            case RULES -> new double[][]{{1.0,1.5,15.55,15.0,30.5,16.0}};
        };
    }
}
