/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package co.interlis.commons.String;

import ch.ehi.basics.settings.Settings;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iox.IoxValidationConfig;
import ch.interlis.iox_j.logging.LogEventFactory;
import ch.interlis.iox_j.validator.InterlisFunction;
import ch.interlis.iox_j.validator.ObjectPool;
import ch.interlis.iox_j.validator.Value;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author USER
 */
public class MatchesIoxPlugin implements InterlisFunction {

    private LogEventFactory logger = null;

    @Override
    public void init(TransferDescription td, Settings settings,
            IoxValidationConfig validationConfig, ObjectPool objectPool,
            LogEventFactory logEventFactory) {
        logger = logEventFactory;

    }

    @Override
    public Value evaluate(String validationKind, String usageScope, IomObject mainObj, Value[] actualArguments) {
        String value = actualArguments[0].getValue();
        String patternString = actualArguments[1].getValue();
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(value);
        return new Value(matcher.matches());
    }

    @Override
    public String getQualifiedIliName() {
        return "INTERLIS_COMMONS.matches";
    }

}
