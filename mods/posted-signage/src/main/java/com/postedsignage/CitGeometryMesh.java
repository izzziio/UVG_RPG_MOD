package com.postedsignage;

import java.util.List;

/** Mallas locales sin dependencias de cliente: posición, UV y normal. */
public final class CitGeometryMesh {
    private CitGeometryMesh(){}
    private static final int[][] FACES={{0,3,2,1},{5,6,7,4},{1,2,6,5},{4,7,3,0},{3,7,6,2},{4,0,1,5}};
    private static final float[][] NORMALS={{0,0,-1},{0,0,1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
    // Conserva el orden de las piezas dentro de cada material.
    public static float[] compile(List<CitDoorGeometry.Piece> pieces,boolean glass){
        int count=(int)pieces.stream().filter(p->p.glass()==glass).count();
        float[] mesh=new float[count*(glass?2:6)*4*8];int offset=0;
        for(var piece:pieces){
            if(piece.glass()!=glass)continue;
            var b=piece.box();
            double[][] points={{b.minX,b.minY,b.minZ},{b.maxX,b.minY,b.minZ},{b.maxX,b.maxY,b.minZ},{b.minX,b.maxY,b.minZ},
                    {b.minX,b.minY,b.maxZ},{b.maxX,b.minY,b.maxZ},{b.maxX,b.maxY,b.maxZ},{b.minX,b.maxY,b.maxZ}};
            for(int f=0;f<6;f++){
                if(glass&&(b.getXsize()<b.getZsize()?f!=2&&f!=3:f!=0&&f!=1))continue;
                for(int i=0;i<4;i++){
                    var point=points[FACES[f][i]];
                    mesh[offset++]=(float)point[0];mesh[offset++]=(float)point[1];mesh[offset++]=(float)point[2];
                    mesh[offset++]=i<2?0:1;mesh[offset++]=i==0||i==3?1:0;
                    mesh[offset++]=NORMALS[f][0];mesh[offset++]=NORMALS[f][1];mesh[offset++]=NORMALS[f][2];
                }
            }
        }
        return mesh;
    }
}
