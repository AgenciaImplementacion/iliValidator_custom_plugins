/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.ehi.ili2db.mapping;

/**
 *
 * @author taken from https://github.com/claeis/ili2db - ili2db/src/ch/ehi/ili2db/mapping/MultiSurfaceMapping.java 
 */
public class MultiSurfaceMapping {

    private String bagOfSurfacesAttrName;
    private String surfaceAttrName;

    public MultiSurfaceMapping(String bagOfSurfacesAttrName,
            String surfaceAttrName) {
        this.bagOfSurfacesAttrName = bagOfSurfacesAttrName;
        this.surfaceAttrName = surfaceAttrName;
    }

    public String getBagOfSurfacesAttrName() {
        return bagOfSurfacesAttrName;
    }

    public String getSurfaceAttrName() {
        return surfaceAttrName;
    }

}
