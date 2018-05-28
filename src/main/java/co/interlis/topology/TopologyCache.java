/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package co.interlis.topology;

import ch.ehi.ili2db.mapping.MultiSurfaceMappings;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.LocalAttribute;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxException;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;
import ch.interlis.iox_j.validator.ObjectPool;
import ch.interlis.iox_j.wkb.Iox2wkb;
import ch.interlis.iox_j.wkb.Iox2wkbException;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 * @author user
 */
public class TopologyCache {

    public static final String POLYLINE_TYPE = "PolylineType";
    public static final String SURFACE_OR_AREA_TYPE = "SurfaceOrAreaType";
    public static final String MULTI_SURFACE_TYPE = "MultiSurfaceType";
    public static final String COORD_TYPE = "CoordType";

    public static final String METAATTR_MAPPING = "ili2db.mapping";
    public static final String METAATTR_MAPPING_MULTISURFACE = "MultiSurface";
    public static final String METAATTR_MAPPING_MULTILINE = "MultiLine";
    public static final String METAATTR_MAPPING_MULTIPOINT = "MultiPoint";
    public static final String METAATTR_MAPPING_ARRAY = "ARRAY";

    private static TopologyCache instance = null;

    private Map<String, STRtree> topologyCatalog;

    private ObjectPool objectPool;

    protected TopologyCache() {
    }

    public static TopologyCache getInstance(ObjectPool objectPool) {
        if (instance == null) {
            instance = new TopologyCache();
            instance.topologyCatalog = new LinkedHashMap<>();

            instance.objectPool = objectPool;
        }
        return instance;
    }

    public void addCatalog(String name, String currentObjectTag, LocalAttribute geometryAttribute, String geometryType, Double p) throws IoxException {
        if (!topologyCatalog.containsKey(name)) {
            STRtree strTree = new STRtree();
            objectPool.getBasketIds().stream().map((basketId) -> (objectPool.getObjectsOfBasketId(basketId)).valueIterator()).forEach((Iterator objectIterator) -> {
                while (objectIterator.hasNext()) {
                    IomObject iomObj = (IomObject) objectIterator.next();
                    if (iomObj != null) {
                        if (iomObj.getobjecttag().equals(currentObjectTag)) {
                            try {
                                //Get current object geometry as JTS Geometry
                                Geometry iteratedGeometry = geometry2JTS(iomObj.getattrobj(geometryAttribute.getName(), 0), geometryAttribute, geometryType, p);
                                String iteratedOID = iomObj.getobjectoid();
                                Map<String, Object> item = new LinkedHashMap<>();
                                item.put("id", iteratedOID);
                                item.put("geometry", iteratedGeometry);
                                
                                //Insert iteratedGeometry into strTree Index
                                strTree.insert(iteratedGeometry.getEnvelopeInternal(), item);
                            } catch (IoxException ex) {
                                System.out.println("ERROR: " + ex.getMessage());
                            }
                        }
                    }
                }
            });

            strTree.build();
            topologyCatalog.put(name, strTree);
        }
    }

    public STRtree getCatalog(String name) {
        return topologyCatalog.get(name);
    }

    private Geometry geometry2JTS(IomObject object, LocalAttribute attr, String geometryType, double p) throws IoxException {
        Geometry geometry = null;
        try {
            switch (geometryType) {
                case POLYLINE_TYPE:
                    geometry = new GeometryFactory()
                            .createLineString(Iox2jts.polyline2JTS(object, false, 0).toCoordinateArray());
                    break;
                case SURFACE_OR_AREA_TYPE:
                    geometry = Iox2jts.surface2JTS(object, p);
                    break;
                case COORD_TYPE:
                    geometry = new GeometryFactory().createPoint(Iox2jts.coord2JTS(object));
                    break;
                case MULTI_SURFACE_TYPE:
                    Type type = attr.getDomain();
                    if (isMultiSurfaceAttr(attr)) {
                        MultiSurfaceMappings multiSurfaceAttrs = new MultiSurfaceMappings();
                        multiSurfaceAttrs.addMultiSurfaceAttr(attr);
                        ch.ehi.ili2db.mapping.MultiSurfaceMapping attrMapping = multiSurfaceAttrs.getMapping(attr);

                        IomObject iomMultisurface = null;
                        if (object != null) {

                            int surfacec = object.getattrvaluecount(attrMapping.getBagOfSurfacesAttrName());
                            for (int surfacei = 0; surfacei < surfacec; surfacei++) {

                                IomObject iomSurfaceStructure = object.getattrobj(attrMapping.getBagOfSurfacesAttrName(), surfacei);
                                IomObject iomPoly = iomSurfaceStructure.getattrobj(attrMapping.getSurfaceAttrName(), 0);
                                IomObject iomSurface = iomPoly.getattrobj("surface", 0);
                                if (iomMultisurface == null) {
                                    iomMultisurface = new ch.interlis.iom_j.Iom_jObject("MULTISURFACE", null);
                                }
                                iomMultisurface.addattrobj("surface", iomSurface);

                                try {
                                    Geometry g = Iox2jts.surface2JTS(iomSurface, 0);
                                } catch (Iox2jtsException ex) {
                                    throw new IoxException(ex.getMessage());
                                }
                            }
                        }

                        if (iomMultisurface != null) {
                            try {
                                AttributeDef surfaceAttr = getMultiSurfaceAttrDef(type, attrMapping);
                                SurfaceType surface = ((SurfaceType) surfaceAttr.getDomainResolvingAliases());
                                CoordType coord = (CoordType) surface.getControlPointDomain().getType();
                                boolean is3D = coord.getDimensions().length == 3;

                                Iox2wkb conv = new Iox2wkb(is3D ? 3 : 2);
                                Object geomObj = conv.multisurface2wkb(iomMultisurface, surface.getLineAttributeStructure() != null, p);
                                byte bv[] = (byte[]) geomObj;
                                geometry = new WKBReader().read(bv);

                            } catch (Iox2wkbException | ParseException ex) {
                                throw new IoxException(ex.getMessage());
                            }
                        } else {
                            throw new IoxException("Given attribute is not a valid multisurface type");
                        }
                    }
                    break;
            }
        } catch (Iox2jtsException e1) {
            throw new IoxException(e1.getMessage());
        }
        return geometry;
    }

    private AttributeDef getMultiSurfaceAttrDef(Type type, ch.ehi.ili2db.mapping.MultiSurfaceMapping attrMapping) {
        Table multiSurfaceType = ((CompositionType) type).getComponentType();
        Table surfaceStructureType = ((CompositionType) ((AttributeDef) multiSurfaceType.getElement(AttributeDef.class, attrMapping.getBagOfSurfacesAttrName())).getDomain()).getComponentType();
        AttributeDef surfaceAttr = (AttributeDef) surfaceStructureType.getElement(AttributeDef.class, attrMapping.getSurfaceAttrName());
        return surfaceAttr;
    }

    private boolean isMultiSurfaceAttr(AttributeDef attr) {
        Type typeo = attr.getDomain();
        if (typeo instanceof CompositionType) {
            CompositionType type = (CompositionType) attr.getDomain();
            if (type.getCardinality().getMaximum() == 1) {
                Table struct = type.getComponentType();
                if (METAATTR_MAPPING_MULTISURFACE.equals(struct.getMetaValue(METAATTR_MAPPING))) {
                    return true;
                }
            }
        }
        return false;
    }

}
