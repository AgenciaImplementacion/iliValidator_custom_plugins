package org.interlis2.validator.plugins;

import java.util.HashMap;
import java.util.Iterator;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Polygon;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.CoordType;
import ch.interlis.ili2c.metamodel.LineType;
import ch.interlis.ili2c.metamodel.LocalAttribute;
import ch.interlis.ili2c.metamodel.NumericType;
import ch.interlis.ili2c.metamodel.NumericalType;
import ch.interlis.ili2c.metamodel.PolylineType;
import ch.interlis.ili2c.metamodel.SurfaceOrAreaType;
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

public class no_overlaps_2_2_1_IoxPlugin implements InterlisFunction {

    public static final String POLYLINE_TYPE = "PolylineType";
    public static final String SURFACE_OR_AREA_TYPE = "SurfaceOrAreaType";
    public static final String COORD_TYPE = "CoordType";

    private LogEventFactory logger = null;
    private ObjectPool objectPool = null;
    /**
     * mappings from xml-tags to Viewable|AttributeDef
     */
    private HashMap tag2class = null;

    @Override
    public void init(TransferDescription td, Settings settings, IoxValidationConfig validationConfig,
            ObjectPool objectPool, LogEventFactory logEventFactory) {

        tag2class = ch.interlis.iom_j.itf.ModelUtilities.getTagMap(td);

        logger = logEventFactory;
        this.objectPool = objectPool;
    }

    @Override
    public Value evaluate(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments) {
        /*
        String currentObjectTag = mainObj.getobjecttag();
        String geomAttr = actualArguments[1].getValue();
        String geomType = getGeometryType(currentObjectTag, geomAttr);
        double p = getPSurfaceOrAreaType(currentObjectTag, geomAttr);

        if (geomType != null) {

            Geometry currentObjectGeometry = geometry2JTS(mainObj.getattrobj(geomAttr, 0), geomType, p);
            logger.addEvent(logger.logInfoMsg("evaluate: " + getQualifiedIliName() + " Over: " + mainObj.getobjecttag()
                    + " tid: " + mainObj.getobjectoid()));

            Boolean isValid = true;
            for (String basketId : objectPool.getBasketIds()) {
                // iterate through iomObjects
                Iterator<IomObject> objectIterator = (objectPool.getObjectsOfBasketId(basketId)).valueIterator();
                while (objectIterator.hasNext()) {
                    IomObject iomObj = objectIterator.next();
                    if (iomObj != null) {
                        // do not evaluate itself
                        if (iomObj.getobjecttag().equals(currentObjectTag) && !mainObj.equals(iomObj)) {
                            Geometry iteratedPolygon = geometry2JTS(iomObj.getattrobj(geomAttr, 0), geomType, p);

                            // check if mainObj overlaps any other object
                            isValid &= !(currentObjectGeometry.overlaps(iteratedPolygon));
                        }
                    }
                }
            }
            return new Value(isValid);
        }
        return new Value(false);
        */
        return  new Value(true);
    }

    public String getGeometryType(String classTag, String geomAttribute) {
        // get "actualArguments[1]" attribute geometry type
        String geomType = null;
        Object modelele = tag2class.get(classTag);
        Viewable aclass1 = (Viewable) modelele;
        Iterator iter = aclass1.getAttributes();
        while (iter.hasNext()) {
            LocalAttribute obj = (LocalAttribute) iter.next();

            if (obj.getName().equals(geomAttribute)) {
                Type type = obj.getDomainResolvingAliases();
                System.out.println("********* " + type);
                if (type instanceof PolylineType) {
                    geomType = POLYLINE_TYPE;
                } else if (type instanceof SurfaceOrAreaType) {
                    geomType = SURFACE_OR_AREA_TYPE;
                } else if (type instanceof CoordType) {
                    geomType = COORD_TYPE;
                } else {
                    System.out.println("--------- " + obj.getMetaValues());
                    logger.addEvent(
                            logger.logErrorMsg("Given attribute " + geomAttribute + " is not a valid geometry type"));
                    return null;
                }
            } else {
                continue;
            }
        }
        return geomType;
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
                    if (typeCache.containsKey(type)) {
                        return ((Double) typeCache.get((SurfaceOrAreaType) type)).doubleValue();
                    }
                    double p;
                    CoordType coordType = (CoordType) ((SurfaceOrAreaType) type).getControlPointDomain().getType();
                    NumericalType dimv[] = coordType.getDimensions();
                    int accuracy = ((NumericType) dimv[0]).getMaximum().getAccuracy();
                    if (accuracy == 0) {
                        p = 0.5;
                    } else {
                        p = Math.pow(10.0, -accuracy);
                    }
                    typeCache.put((SurfaceOrAreaType) type, new Double(p));
                    return p;

                } else {
                    return 0;
                }
            } else {
                continue;
            }
        }
        return 0;
    }

    public Geometry geometry2JTS(IomObject object, String geometryType, double p) {
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
            }
        } catch (Iox2jtsException e1) {
            logger.addEvent(logger.logErrorMsg(e1.getMessage()));
            e1.printStackTrace();
        }
        return geometry;
    }

    private HashMap<LineType, Double> typeCache = new HashMap<LineType, Double>();

    public double getP(LineType type) {
        if (typeCache.containsKey(type)) {
            return ((Double) typeCache.get(type)).doubleValue();
        }
        double p;
        CoordType coordType = (CoordType) type.getControlPointDomain().getType();
        NumericalType dimv[] = coordType.getDimensions();
        int accuracy = ((NumericType) dimv[0]).getMaximum().getAccuracy();
        if (accuracy == 0) {
            p = 0.5;
        } else {
            p = Math.pow(10.0, -accuracy);
            // EhiLogger.debug("accuracy "+accuracy+", p "+p);
        }
        typeCache.put(type, new Double(p));
        return p;
    }

    @Override
    public String getQualifiedIliName() {
        return "Catastro_Registro_Nucleo_V2_2_1.no_overlaps";
    }

}
