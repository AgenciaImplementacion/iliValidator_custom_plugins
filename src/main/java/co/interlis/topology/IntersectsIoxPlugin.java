package co.interlis.topology;

import java.util.HashMap;
import java.util.Iterator;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;

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

public class IntersectsIoxPlugin implements InterlisFunction {

	public static final String POLYLINE_TYPE = "PolylineType";
	public static final String SURFACE_OR_AREA_TYPE = "SurfaceOrAreaType";
	public static final String COORD_TYPE = "CoordType";

	/**
	 * mappings from xml-tags to Viewable|AttributeDef
	 */
	private HashMap tag2class = null;
	private LogEventFactory logger = null;
	private ObjectPool objectPool = null;
	private HashMap<LineType, Double> typeCache = new HashMap<LineType, Double>();

	@Override
	public void init(TransferDescription td, Settings settings, IoxValidationConfig validationConfig,
			ObjectPool objectPool, LogEventFactory logEventFactory) {
		logger = logEventFactory;

		tag2class = ch.interlis.iom_j.itf.ModelUtilities.getTagMap(td);
		this.objectPool = objectPool;
	}

	public String getGeometryType(IomObject xtfGeom) {
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
				logger.addEvent(logger.logErrorMsg("Given attribute is not a valid geometry type"));
				return null;
			}
		}

		return geomType;
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

	@Override
	public Value evaluate(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments) {

		// System.out.println(actualArguments[1].getValue());
		//System.out.println(mainObj);

		IomObject xtfGeom = (IomObject) actualArguments[1].getComplexObjects().toArray()[0];
		String currentObjectTag = mainObj.getobjecttag();
		String geomType = getGeometryType(xtfGeom);
		String geomAttr = null;

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
				if (attVal.equals(xtfGeom)) {
					geomAttr = attrName;
				}
			}

			double p = getPSurfaceOrAreaType(currentObjectTag, geomAttr);
			Geometry currentObjectGeometry = geometry2JTS(xtfGeom, geomType, p);

			// iterate through iomObjects
			for (String basketId : objectPool.getBasketIds()) {
				Iterator<IomObject> objectIterator = (objectPool.getObjectsOfBasketId(basketId)).valueIterator();
				while (objectIterator.hasNext()) {
					IomObject iomObj = objectIterator.next();
					if (iomObj != null) {
						// do not evaluate itself
						if (iomObj.getobjecttag().equals(currentObjectTag) && !mainObj.equals(iomObj)) {
							Geometry iteratedPolygon = geometry2JTS(iomObj.getattrobj(geomAttr, 0), geomType, p);

							// check if mainObj overlaps any other object
							spatialResult |= (currentObjectGeometry.intersects(iteratedPolygon));
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
		return "INTERLIS_TOPOLOGY.intersects";
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

}
