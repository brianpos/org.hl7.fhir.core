package org.hl7.fhir.validation.special;

import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import org.hl7.fhir.r5.formats.JsonParser;
import org.hl7.fhir.r5.context.IWorkerContext;
import org.hl7.fhir.r5.formats.IParser;
import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.terminologies.utilities.TerminologyServiceErrorClass;
import org.hl7.fhir.r5.terminologies.utilities.ValidationResult;
import org.hl7.fhir.r5.test.utils.CompareUtilities;
import org.hl7.fhir.r5.test.utils.TestingUtilities;
import org.hl7.fhir.utilities.FhirPublication;
import org.hl7.fhir.utilities.TextFile;
import org.hl7.fhir.utilities.Utilities;
import org.hl7.fhir.utilities.i18n.I18nConstants;
import org.hl7.fhir.utilities.json.model.JsonObject;
import org.hl7.fhir.utilities.validation.ValidationOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class TxServiceTestHelper {


  public static String getDiffForValidation(IWorkerContext context, String name, Resource requestParameters, String expectedResponse, String lang, String fp, JsonObject externals, boolean isCodeSystem) throws JsonSyntaxException, FileNotFoundException, IOException {
    org.hl7.fhir.r5.model.Parameters p = (org.hl7.fhir.r5.model.Parameters) requestParameters;
    ValueSet vs = null;
    String vsurl = null;
    if (!isCodeSystem) {
      if (p.hasParameter("valueSetVersion")) {
        vsurl = p.getParameterValue("url").primitiveValue()+"|"+p.getParameterValue("valueSetVersion").primitiveValue();
        vs = context.fetchResource(ValueSet.class, p.getParameterValue("url").primitiveValue(), p.getParameterValue("valueSetVersion").primitiveValue());
      } else {
        vsurl = p.getParameterValue("url").primitiveValue();
        vs = context.fetchResource(ValueSet.class, p.getParameterValue("url").primitiveValue());
      }
    }
    ValidationResult validationResult = null;
    String code = null;
    String system = null;
    String version = null;
    String display = null;
    CodeableConcept cc = null;
    org.hl7.fhir.r5.model.Parameters res = null;
    OperationOutcome operationOutcome = null;

    if (vs == null && vsurl != null) {
      String msg = context.formatMessage(I18nConstants.UNABLE_TO_RESOLVE_VALUE_SET_, vsurl);
      operationOutcome = new OperationOutcome();
      CodeableConcept codeableConcept = operationOutcome.addIssue().setSeverity(OperationOutcome.IssueSeverity.ERROR).setCode(OperationOutcome.IssueType.NOTFOUND).getDetails();
      codeableConcept.addCoding("http://hl7.org/fhir/tools/CodeSystem/tx-issue-type", "not-found", null);
      codeableConcept.setText(msg);
    } else {
      ValidationOptions options = new ValidationOptions(FhirPublication.R5);
      if (p.hasParameter("displayLanguage")) {
        options = options.withLanguage(p.getParameterString("displayLanguage"));
      } else if (lang != null ) {
        options = options.withLanguage(lang);
      }
      if (p.hasParameter("valueset-membership-only") && "true".equals(p.getParameterString("valueset-membership-only"))) {
        options = options.withCheckValueSetOnly();
      }
      if (p.hasParameter("lenient-display-validation") && "true".equals(p.getParameterString("lenient-display-validation"))) {
        options = options.setDisplayWarningMode(true);
      }
      if (p.hasParameter("activeOnly") && "true".equals(p.getParameterString("activeOnly"))) {
        options = options.setActiveOnly(true);
      }
      context.getExpansionParameters().clearParameters("includeAlternateCodes");
      for (Parameters.ParametersParameterComponent pp : p.getParameter()) {
        if ("includeAlternateCodes".equals(pp.getName())) {
          context.getExpansionParameters().addParameter(pp.copy());
        }
      }
      if (p.hasParameter("code")) {
        code = p.getParameterString("code");
        system = p.getParameterString(isCodeSystem ? "url" : "system");
        version = p.getParameterString(isCodeSystem ? "version" : "systemVersion");
        display = p.getParameterString("display");
        validationResult = context.validateCode(options.withGuessSystem(),
          p.getParameterString(isCodeSystem ? "url" : "system"), p.getParameterString(isCodeSystem ? "version" : "systemVersion"),
          p.getParameterString("code"), p.getParameterString("display"), vs);
      } else if (p.hasParameter("coding")) {
        Coding coding = (Coding) p.getParameterValue("coding");
        code = coding.getCode();
        system = coding.getSystem();
        version = coding.getVersion();
        display = coding.getDisplay();
        validationResult = context.validateCode(options, coding, vs);
      } else if (p.hasParameter("codeableConcept")) {
        cc = (CodeableConcept) p.getParameterValue("codeableConcept");
        validationResult = context.validateCode(options, cc, vs);
      } else {
        throw new Error("validate not done yet for this steup");
      }
    }
    if (operationOutcome == null && validationResult != null && validationResult.getSeverity() == org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity.FATAL) {
      operationOutcome = new OperationOutcome();
      operationOutcome.getIssue().addAll(validationResult.getIssues());
    }
    if (operationOutcome != null) {
      TxTesterSorters.sortOperationOutcome(operationOutcome);
      TxTesterScrubbers.scrubOO(operationOutcome, false);

      String actualResponse = new JsonParser().setOutputStyle(IParser.OutputStyle.PRETTY).composeString(operationOutcome);
      String diff = CompareUtilities.checkJsonSrcIsSame(expectedResponse, actualResponse, externals);
      if (diff != null) {
        Utilities.createDirectory(Utilities.getDirectoryForFile(fp));
        TextFile.stringToFile(actualResponse, fp);
        System.out.println("Test "+name+"failed: "+diff);
      }
      return diff;
    } else {
      if (res == null) {
        res = new org.hl7.fhir.r5.model.Parameters();
        if (validationResult.getSystem() != null) {
          res.addParameter("system", new UriType(validationResult.getSystem()));
        } else if (system != null) {
          res.addParameter("system", new UriType(system));
        }
        if (validationResult.getCode() != null) {
          if (code != null && !code.equals(validationResult.getCode())) {
            res.addParameter("code", new CodeType(code));
            res.addParameter("normalized-code", new CodeType(validationResult.getCode()));
          } else {
            res.addParameter("code", new CodeType(validationResult.getCode()));
          }
        } else if (code != null) {
          res.addParameter("code", new CodeType(code));
        }
        if (validationResult.getSeverity() == org.hl7.fhir.utilities.validation.ValidationMessage.IssueSeverity.ERROR) {
          res.addParameter("result", false);
        } else {
          res.addParameter("result", true);
        }
        if (validationResult.getMessage() != null) {
          res.addParameter("message", validationResult.getMessage());
        }
        if (validationResult.getVersion() != null) {
          res.addParameter("version", validationResult.getVersion());
        } else if (version != null) {
          res.addParameter("version", new StringType(version));
        }
        if (validationResult.getDisplay() != null) {
          res.addParameter("display", validationResult.getDisplay());
        } else if (display != null) {
          res.addParameter("display", new StringType(display));
        }
        //      if (vm.getCodeableConcept() != null) {
        //        res.addParameter("codeableConcept", vm.getCodeableConcept());
        //      } else
        if (cc != null) {
          res.addParameter("codeableConcept", cc);
        }
        if (validationResult.isInactive()) {
          res.addParameter("inactive", true);
        }
        if (validationResult.getStatus() != null) {
          res.addParameter("status", validationResult.getStatus());
        }
        if (validationResult.getUnknownSystems() != null) {
          for (String s : validationResult.getUnknownSystems()) {
            res.addParameter(validationResult.getErrorClass() == TerminologyServiceErrorClass.CODESYSTEM_UNSUPPORTED ? "x-caused-by-unknown-system" :  "x-unknown-system", new CanonicalType(s));
          }
        }
        if (validationResult.getIssues().size() > 0) {
          operationOutcome = new OperationOutcome();
          operationOutcome.getIssue().addAll(validationResult.getIssues());
          res.addParameter().setName("issues").setResource(operationOutcome);
        }
      }

      TxTesterSorters.sortParameters(res);
      TxTesterScrubbers.scrubParams(res);

      String actualResponse = new JsonParser().setOutputStyle(IParser.OutputStyle.PRETTY).composeString(res);



      String diff = CompareUtilities.checkJsonSrcIsSame(expectedResponse, actualResponse, externals);
      if (diff != null) {
        dumparoo("/Users/david.otasek/IN/2024-02-05-hapi-core-bump-6-2.16/core-test", name, expectedResponse, actualResponse);
        Utilities.createDirectory(Utilities.getDirectoryForFile(fp));
        TextFile.stringToFile(actualResponse, fp);
        System.out.println("Test "+name+"failed: "+diff);
      }
      return diff;
    }
  }

  public static void dumparoo(String rootDirectory, String testName, String expected, String actual) throws IOException {
    String fullDirectory = rootDirectory + "/" + testName;
    File directory = new File(fullDirectory);
    if (!directory.exists()) {
      directory.mkdirs();
    }
TextFile.stringToFile(expected, fullDirectory + "/expected.json");
    TextFile.stringToFile(actual, fullDirectory + "/actual.json");

  }
}
