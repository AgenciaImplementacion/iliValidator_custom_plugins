package org.interlis2.validator.plugins;

import java.util.Iterator;

import com.vividsolutions.jts.geom.Polygon;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxValidationConfig;
import ch.interlis.iox_j.jts.Iox2jts;
import ch.interlis.iox_j.jts.Iox2jtsException;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.iox_j.validator.ObjectPool;
import ch.interlis.iox_j.validator.Value;

public class no_overlapsIoxPlugin implements InterlisFunction {
	private LogEventFactory logger = null;
	private ObjectPool objectPool = null;

	@Override
	public void init(TransferDescription td, Settings settings, IoxValidationConfig validationConfig,
			ObjectPool objectPool, LogEventFactory logEventFactory) {
		logger = logEventFactory;
		this.objectPool = objectPool;
	}

	@Override
	public Value evaluate(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments) {

		String currentObjectTag = mainObj.getobjecttag();
		try {
			Polygon currentObjectPolygon = Iox2jts.surface2JTS(mainObj.getattrobj(actualArguments[1].getValue(), 0), 0);
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

							Polygon iteratedPolygon = Iox2jts
									.surface2JTS(iomObj.getattrobj(actualArguments[1].getValue(), 0), 0);

							isValid &= !(currentObjectPolygon.overlaps(iteratedPolygon));
						}
					}
				}
			}
			return new Value(isValid);

		} catch (Iox2jtsException e1) {
			logger.addEvent(logger.logErrorMsg(e1.getMessage()));
			e1.printStackTrace();
		}
		return new Value(false);
	}

	@Override
	public String getQualifiedIliName() {
		return "Catastro_COL_ES_V2_1_6.no_overlaps";
	}

}
