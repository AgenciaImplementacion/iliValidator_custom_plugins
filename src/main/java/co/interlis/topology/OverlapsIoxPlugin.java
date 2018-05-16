package co.interlis.topology;

import java.util.HashMap;
import java.util.Iterator;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

import ch.ehi.basics.settings.Settings;
import ch.ehi.ili2db.mapping.MultiSurfaceMappings;
import ch.interlis.ili2c.metamodel.AttributeDef;
import ch.interlis.ili2c.metamodel.CompositionType;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.LineType;
import ch.interlis.ili2c.metamodel.LocalAttribute;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
import ch.interlis.ili2c.metamodel.SurfaceType;
import ch.interlis.ili2c.metamodel.Table;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.ili2c.metamodel.Type;
import ch.interlis.ili2c.metamodel.Viewable;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxValidationConfig;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.iox_j.validator.ObjectPool;
import ch.interlis.iox_j.validator.Value;
import ch.interlis.iox_j.wkb.Iox2wkb;
import ch.interlis.iox_j.wkb.Iox2wkbException;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.io.ParseException;
import com.vividsolutions.jts.io.WKBReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OverlapsIoxPlugin implements InterlisFunction {
    public static final String POLYLINE_TYPE = "PolylineType";
    public static final String SURFACE_OR_AREA_TYPE = "SurfaceOrAreaType";
    public static final String MULTI_SURFACE_TYPE = "MultiSurfaceType";
    public static final String COORD_TYPE = "CoordType";

    public static final String METAATTR_MAPPING = "ili2db.mapping";
    public static final String METAATTR_MAPPING_MULTISURFACE = "MultiSurface";
    public static final String METAATTR_MAPPING_MULTILINE = "MultiLine";
    public static final String METAATTR_MAPPING_MULTIPOINT = "MultiPoint";
    public static final String METAATTR_MAPPING_ARRAY = "ARRAY";
    public static final String METAATTR_DISPNAME = "ili2db.dispName";

    /**
     * mappings from xml-tags to Viewable|AttributeDef
     */
    private HashMap tag2class = null;
    private LogEventFactory logger = null;
    private ObjectPool objectPool = null;
    private HashMap<Type, Double> typeCache = null;
    private TransferDescription transferDescription = null;

    @Override
    public void init(TransferDescription td, Settings settings, IoxValidationConfig validationConfig,
            ObjectPool objectPool, LogEventFactory logEventFactory) {

        typeCache = new HashMap<>();
        pTypeCache = new HashMap<>();

        this.logger = logEventFactory;
        this.tag2class = ch.interlis.iom_j.itf.ModelUtilities.getTagMap(td);
        this.objectPool = objectPool;
        this.transferDescription = td;
    }

    public String getGeometryType(IomObject xtfGeom, IomObject mainObj) {
        String geomType = null;

        Boolean isPolygon = xtfGeom.getattrvaluecount("surface") > 0;
        Boolean isLine = xtfGeom.getattrvaluecount("sequence") > 0;

        if (isPolygon) {
            geomType = SURFACE_OR_AREA_TYPE;
        } else if (isLine) {
            geomType = POLYLINE_TYPE;
        } else {
            String c1 = xtfGeom.getattrvalue("C1");
            String c2 = xtfGeom.getattrvalue("C2");
            if (c1 != null && c2 != null) {
                geomType = COORD_TYPE;
            } else {
                String currentObjectTag = mainObj.getobjecttag();
                Object modelele2 = tag2class.get(currentObjectTag);
                Viewable aclass13 = (Viewable) modelele2;
                Iterator iter4 = aclass13.getAttributes();
                while (iter4.hasNext()) {
                    LocalAttribute attr = (LocalAttribute) iter4.next();
                    String attrName = attr.getName();
                    IomObject attVal = mainObj.getattrobj(attrName, 0);
                    if (attVal != null && attVal.equals(xtfGeom)) {
                        Type type = attr.getDomain();
                        if (isMultiSurfaceAttr(transferDescription, attr)) {
                            geomType = MULTI_SURFACE_TYPE;
                            break;
                        }
                    }
                }
                if (geomType == null) {
                    logger.addEvent(logger.logErrorMsg("Given attribute is not a valid geometry type"));
                    return null;
                }
            }
        }

        return geomType;
    }

    public Geometry geometry2JTS(IomObject object, LocalAttribute attr, String geometryType, double p) {
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
                    if (isMultiSurfaceAttr(transferDescription, attr)) {
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
                                    Logger.getLogger(OverlapsIoxPlugin.class.getName()).log(Level.SEVERE, null, ex);
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
                                Object geomObj = conv.multisurface2wkb(iomMultisurface, surface.getLineAttributeStructure() != null, getP(surface));
                                byte bv[] = (byte[]) geomObj;
                                geometry = new WKBReader().read(bv);

                            } catch (Iox2wkbException | ParseException ex) {
                                Logger.getLogger(OverlapsIoxPlugin.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        } else {
                            logger.addEvent(logger.logErrorMsg("Given attribute is not a valid multisurface type"));
                        }
                    }
                    break;
            }
        } catch (Iox2jtsException e1) {
            logger.addEvent(logger.logErrorMsg(e1.getMessage()));
        }
        return geometry;
    }

    @Override
    public Value evaluate(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments) {

        IomObject xtfGeom = (IomObject) actualArguments[1].getComplexObjects().toArray()[0];
        String currentObjectTag = mainObj.getobjecttag();
        String geomAttr = null;
        String geomType = getGeometryType(xtfGeom, mainObj);
        LocalAttribute localAttr = null;

        if (geomType != null) {
            logger.addEvent(logger.logInfoMsg("evaluate: " + getQualifiedIliName() + " Over: " + mainObj.getobjecttag()
                    + " tid: " + mainObj.getobjectoid()));

            Boolean spatialResult = false;

            // Find geometry attribute name
            Object modelele = tag2class.get(currentObjectTag);
            Viewable aclass1 = (Viewable) modelele;
            Iterator iter = aclass1.getAttributes();
            while (iter.hasNext()) {
                LocalAttribute attr = (LocalAttribute) iter.next();
                String attrName = attr.getName();
                IomObject attVal = mainObj.getattrobj(attrName, 0);
                if (attVal != null && attVal.equals(xtfGeom)) {
                    geomAttr = attrName;
                    localAttr = attr;
                    break;
                }
            }

            double p = getPSurfaceOrAreaType(currentObjectTag, geomAttr);
            Geometry currentObjectGeometry = geometry2JTS(xtfGeom, localAttr, geomType, p);

            // check for self overlaps
            if (geomType.equals(MULTI_SURFACE_TYPE)) {
                MultiPolygon mp = (MultiPolygon) currentObjectGeometry;
                for (int i = 0; i < mp.getNumGeometries(); i++) {
                    Geometry geometryA = mp.getGeometryN(i);
                    for (int j = i + 1; j < mp.getNumGeometries(); j++) {
                        Geometry geometryB = mp.getGeometryN(j);

                        spatialResult |= (geometryA.overlaps(geometryB));

                    }
                }
            }

            // iterate through iomObjects
            for (String basketId : objectPool.getBasketIds()) {
                Iterator<IomObject> objectIterator = (objectPool.getObjectsOfBasketId(basketId)).valueIterator();
                while (objectIterator.hasNext()) {
                    IomObject iomObj = objectIterator.next();
                    if (iomObj != null) {
                        // do not evaluate itself
                        if (iomObj.getobjecttag().equals(currentObjectTag) && !mainObj.equals(iomObj)) {
                            Geometry iteratedPolygon = geometry2JTS(iomObj.getattrobj(geomAttr, 0), localAttr, geomType, p);

                            // check if mainObj overlaps any other object
                            spatialResult |= (currentObjectGeometry.overlaps(iteratedPolygon));
                        }
                    }
                }
            }
            return new Value(spatialResult);
        }
        return null;
    }

    @Override
    public String getQualifiedIliName() {
        return "INTERLIS_TOPOLOGY.overlaps";
    }

    public AttributeDef getMultiSurfaceAttrDef(Type type, ch.ehi.ili2db.mapping.MultiSurfaceMapping attrMapping) {
        Table multiSurfaceType = ((CompositionType) type).getComponentType();
        Table surfaceStructureType = ((CompositionType) ((AttributeDef) multiSurfaceType.getElement(AttributeDef.class, attrMapping.getBagOfSurfacesAttrName())).getDomain()).getComponentType();
        AttributeDef surfaceAttr = (AttributeDef) surfaceStructureType.getElement(AttributeDef.class, attrMapping.getSurfaceAttrName());
        return surfaceAttr;
    }

    public double getPSurfaceOrAreaType(String classTag, String geomAttribute) {
        // get "actualArguments[1]" attribute geometry type
        Object modelele = tag2class.get(classTag);
        Viewable aclass1 = (Viewable) modelele;
        Iterator iter = aclass1.getAttributes();
        while (iter.hasNext()) {
            LocalAttribute obj = (LocalAttribute) iter.next();

            if (obj.getName().equals(geomAttribute)) {
                Type type = obj.getDomainResolvingAliases();

                if (type instanceof SurfaceOrAreaType) {
                    if (!typeCache.containsKey(type)) {
                        double p;
                        CoordType coordType = (CoordType) ((SurfaceOrAreaType) type).getControlPointDomain().getType();
                        NumericalType dimv[] = coordType.getDimensions();
                        int accuracy = ((NumericType) dimv[0]).getMaximum().getAccuracy();
                        if (accuracy == 0) {
                            p = 0.5;
                        } else {
                            p = Math.pow(10.0, -accuracy);
                        }
                        typeCache.put((SurfaceOrAreaType) type, p);
                        return p;
                    } else {
                        return (typeCache.get((SurfaceOrAreaType) type));
                    }

                } else {
                    return 0;
                }
            } else {
                
            }
        }
        return 0;
    }

    public static boolean isMultiSurfaceAttr(TransferDescription td,
            AttributeDef attr) {
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
    private HashMap<LineType, Double> pTypeCache = null;

    public double getP(LineType type) {
        if (pTypeCache.containsKey(type)) {
            return (pTypeCache.get(type));
        }
        double p;
        CoordType coordType = (CoordType) type.getControlPointDomain().getType();
        NumericalType dimv[] = coordType.getDimensions();
        int accuracy = ((NumericType) dimv[0]).getMaximum().getAccuracy();
        if (accuracy == 0) {
            p = 0.5;
        } else {
            p = Math.pow(10.0, -accuracy);
            //EhiLogger.debug("accuracy "+accuracy+", p "+p);
        }
        pTypeCache.put(type, p);
        return p;
    }

}
