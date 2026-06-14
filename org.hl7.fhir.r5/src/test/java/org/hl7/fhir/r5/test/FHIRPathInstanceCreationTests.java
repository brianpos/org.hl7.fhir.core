package org.hl7.fhir.r5.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r5.context.SimpleWorkerContext;
import org.hl7.fhir.r5.fhirpath.ExpressionNode;
import org.hl7.fhir.r5.fhirpath.ExpressionNode.Kind;
import org.hl7.fhir.r5.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r5.model.Base;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.CodeableConcept;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.Patient;
import org.hl7.fhir.r5.model.Period;
import org.hl7.fhir.r5.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r5.test.utils.TestingUtilities;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tests for the FHIRPath instance selector / object construction (STU) feature, e.g.
 * <pre>Coding { system : 'http://example.org/demo', code : 'c1' }</pre>
 * This feature is only implemented for R5.
 */
public class FHIRPathInstanceCreationTests {

  private static FHIRPathEngine fp;
  private static SimpleWorkerContext context;

  @BeforeAll
  public static void setUp() throws FileNotFoundException, FHIRException, IOException {
    // Build a context from just the R5 core package. The instance-construction feature
    // only needs the structural model (ResourceFactory / setProperty), so we avoid the
    // shared worker context which additionally downloads terminology/extensions packages.
    context = new SimpleWorkerContext.SimpleWorkerContextBuilder().withAllowLoadingDuplicates(true).fromPackage(TestingUtilities.loadR5CorePackage());
    if (fp == null) {
      fp = new FHIRPathEngine(context);
    }
  }

  @Test
  public void testParseSimpleConstruction() {
    ExpressionNode node = fp.parse("Coding { system : 'http://example.org/demo', code : 'c1' }");
    Assertions.assertEquals(Kind.Structure, node.getKind());
    Assertions.assertEquals("Coding", node.getName());
    Assertions.assertEquals(2, node.getConstructorParams().size());
    Assertions.assertEquals("system", node.getConstructorParams().get(0).getName());
    Assertions.assertEquals("code", node.getConstructorParams().get(1).getName());
  }

  @Test
  public void testEvaluateSimpleConstruction() {
    Patient input = new Patient();
    List<Base> result = fp.evaluate(input, "Coding { system : 'http://example.org/demo', code : 'c1' }");
    Assertions.assertEquals(1, result.size());
    Assertions.assertTrue(result.get(0) instanceof Coding);
    Coding c = (Coding) result.get(0);
    Assertions.assertEquals("http://example.org/demo", c.getSystem());
    Assertions.assertEquals("c1", c.getCode());
  }

  @Test
  public void testEvaluateNestedConstruction() {
    List<Base> result = fp.evaluate(new Patient(),
        "CodeableConcept { coding : Coding { system : 'http://terminology.hl7.org/CodeSystem/v2-0203', code : 'MR' } }");
    Assertions.assertEquals(1, result.size());
    Assertions.assertTrue(result.get(0) instanceof CodeableConcept);
    CodeableConcept cc = (CodeableConcept) result.get(0);
    Assertions.assertEquals(1, cc.getCoding().size());
    Assertions.assertEquals("MR", cc.getCodingFirstRep().getCode());
  }

  @Test
  public void testEvaluatePrimitiveConstruction() {
    List<Base> result = fp.evaluate(new Patient(), "code { value : 'final' }");
    Assertions.assertEquals(1, result.size());
    Assertions.assertTrue(result.get(0) instanceof CodeType);
    Assertions.assertEquals("final", ((CodeType) result.get(0)).getValue());
  }

  @Test
  public void testEvaluateNamespaceQualifiedConstruction() {
    List<Base> result = fp.evaluate(new Patient(),
        "FHIR.Identifier { system : 'http://example.org/demo', value : 'N0001231' }");
    Assertions.assertEquals(1, result.size());
    Assertions.assertTrue(result.get(0) instanceof Identifier);
    Identifier id = (Identifier) result.get(0);
    Assertions.assertEquals("http://example.org/demo", id.getSystem());
    Assertions.assertEquals("N0001231", id.getValue());
  }

  @Test
  public void testEvaluateEmptyConstruction() {
    List<Base> result = fp.evaluate(new Patient(), "Period {:}");
    Assertions.assertEquals(1, result.size());
    Assertions.assertTrue(result.get(0) instanceof Period);
    Assertions.assertTrue(((Period) result.get(0)).isEmpty());
  }

  @Test
  public void testEvaluateEmptyValueOmitsElement() {
    List<Base> result = fp.evaluate(new Patient(), "Coding { system : 'http://example.org/demo', code : {} }");
    Assertions.assertEquals(1, result.size());
    Coding c = (Coding) result.get(0);
    Assertions.assertEquals("http://example.org/demo", c.getSystem());
    Assertions.assertFalse(c.hasCode());
  }

  @Test
  public void testEvaluateCollectionElementViaUnion() {
    List<Base> result = fp.evaluate(new Patient(),
        "CodeableConcept { coding : Coding { system : 'a', code : '1' } | Coding { system : 'b', code : '2' } }");
    Assertions.assertEquals(1, result.size());
    CodeableConcept cc = (CodeableConcept) result.get(0);
    Assertions.assertEquals(2, cc.getCoding().size());
  }

  @Test
  public void testEvaluateConstructionUsesInputItem() {
    Patient input = new Patient();
    input.setGender(AdministrativeGender.MALE);
    List<Base> result = fp.evaluate(input,
        "Patient.select(Coding { system : 'http://terminology.hl7.org/CodeSystem/v2-0203', code : gender })");
    Assertions.assertEquals(1, result.size());
    Coding c = (Coding) result.get(0);
    Assertions.assertEquals("male", c.getCode());
  }

  @Test
  public void testEvaluateEmptyInputYieldsEmpty() {
    // Per the spec, constructing against an empty input collection yields an empty result.
    ExpressionNode node = fp.parse("Coding { system : 'http://example.org/demo', code : 'c1' }");
    List<Base> result = fp.evaluate((Base) null, node);
    Assertions.assertEquals(0, result.size());
  }

  @Test
  public void testRoundTripToString() {
    ExpressionNode node = fp.parse("Coding { system : 'http://example.org/demo', code : 'c1' }");
    Assertions.assertEquals("Coding { system : 'http://example.org/demo', code : 'c1' }", node.toString());
  }
}
