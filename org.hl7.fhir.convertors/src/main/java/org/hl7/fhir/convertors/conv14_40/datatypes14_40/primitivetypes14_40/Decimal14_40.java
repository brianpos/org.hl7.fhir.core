package org.hl7.fhir.convertors.conv14_40.datatypes14_40.primitivetypes14_40;

import org.hl7.fhir.convertors.context.ConversionContext14_40;
import org.hl7.fhir.exceptions.FHIRException;

public class Decimal14_40 {
  public static org.hl7.fhir.r4.model.DecimalType convertDecimal(org.hl7.fhir.dstu2016may.model.DecimalType src) throws FHIRException {
    org.hl7.fhir.r4.model.DecimalType tgt = src.hasValue() ? new org.hl7.fhir.r4.model.DecimalType(src.getValueAsString()) : new org.hl7.fhir.r4.model.DecimalType();
    ConversionContext14_40.INSTANCE.getVersionConvertor_14_40().copyElement(src, tgt);
    return tgt;
  }

  public static org.hl7.fhir.dstu2016may.model.DecimalType convertDecimal(org.hl7.fhir.r4.model.DecimalType src) throws FHIRException {
    org.hl7.fhir.dstu2016may.model.DecimalType tgt = src.hasValue() ? new org.hl7.fhir.dstu2016may.model.DecimalType(src.getValueAsString()) : new org.hl7.fhir.dstu2016may.model.DecimalType();
    ConversionContext14_40.INSTANCE.getVersionConvertor_14_40().copyElement(src, tgt);
    return tgt;
  }
}
