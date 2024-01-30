package io.openliberty;

import org.eclipse.lemminx.XMLAssert;
import org.eclipse.lemminx.commons.BadLocationException;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.openliberty.tools.langserver.lemminx.LibertyDiagnosticParticipant;
import io.openliberty.tools.langserver.lemminx.data.FeatureListGraph;
import io.openliberty.tools.langserver.lemminx.models.feature.Feature;
import io.openliberty.tools.langserver.lemminx.services.FeatureService;
import io.openliberty.tools.langserver.lemminx.services.LibertyProjectsManager;
import io.openliberty.tools.langserver.lemminx.services.LibertyWorkspace;
import jakarta.xml.bind.JAXBException;

import static org.eclipse.lemminx.XMLAssert.r;
import static org.eclipse.lemminx.XMLAssert.ca;
import static org.eclipse.lemminx.XMLAssert.te;
import static org.eclipse.lemminx.XMLAssert.tde;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class LibertyDiagnosticTest {

    static String newLine = System.lineSeparator();

    static File srcResourcesDir = new File("src/test/resources/sample");
    static File featureList = new File("src/test/resources/featurelist-ol-23.0.0.1-beta.xml");
    static String serverXMLURI = new File(srcResourcesDir, "test/server.xml").toURI().toString();
    static String sampleserverXMLURI = new File(srcResourcesDir, "sample-server.xml").toURI().toString();
    static List<WorkspaceFolder> initList = new ArrayList<WorkspaceFolder>();
    LibertyProjectsManager libPM;
    LibertyWorkspace libWorkspace;

    @BeforeEach
    public void setupWorkspace() {
        initList.add(new WorkspaceFolder(srcResourcesDir.toURI().toString()));
        libPM = LibertyProjectsManager.getInstance();
        libPM.setWorkspaceFolders(initList);
        libWorkspace = libPM.getLibertyWorkspaceFolders().iterator().next();
    }

    @Test
    public void testFeatureDuplicateDiagnostic() {
        String serverXML = String.join(newLine, //
                "<server description=\"Sample Liberty server\">", //
                "       <featureManager>", //
                "               <feature>jaxrs-2.1</feature>", //
                "               <feature>jaxrs-2.1</feature>", //
                "               <feature>jsonp-1.1</feature>", //
                "               <!-- <feature>comment</feature> -->", //
                "               <feature>jsonp-1.1</feature>", //
                "       </featureManager>", //
                "</server>" //
        );

        Diagnostic dup1 = new Diagnostic();
        dup1.setRange(r(3, 24, 3, 33));
        dup1.setMessage("ERROR: jaxrs-2.1 is already included.");

        Diagnostic dup2 = new Diagnostic();
        dup2.setRange(r(6, 24, 6, 33));
        dup2.setMessage("ERROR: jsonp-1.1 is already included.");

        XMLAssert.testDiagnosticsFor(serverXML, null, null, serverXMLURI, dup1, dup2);
    }

    @Test
    public void testAnotherVersionOfFeatureDuplicateDiagnostic() {
        String serverXML = String.join(newLine, //
                "<server description=\"Sample Liberty server\">", //
                "       <featureManager>", //
                "               <feature>jaxrs-2.0</feature>", //
                "               <feature>jaxrs-2.1</feature>", //
                "               <feature>jsonp-1.1</feature>", //
                "       </featureManager>", //
                "</server>" //
        );

        Diagnostic dup1 = new Diagnostic();
        dup1.setRange(r(3, 24, 3, 33));
        dup1.setMessage("ERROR: More than one version of feature jaxrs is included. Only one version of a feature may be specified.");

        XMLAssert.testDiagnosticsFor(serverXML, null, null, serverXMLURI, dup1);
    }

    @Test
    public void testInvalidFeatureDiagnostic() throws BadLocationException{
        String serverXML = String.join(newLine, //
                "<server description=\"Sample Liberty server\">", //
                "       <featureManager>", //
                "               <feature>jaxrs-2.1</feature>", //
                "               <feature>jaX</feature>", //
                "               <feature>jsonp-1.1</feature>", //
                "               <!-- <feature>comment</feature> -->", //
                "               <feature>invalid</feature>", //
                "       </featureManager>", //
                "</server>" //
        );
        Diagnostic invalid1 = new Diagnostic();
        invalid1.setRange(r(3, 24, 3, 27));
        invalid1.setCode(LibertyDiagnosticParticipant.INCORRECT_FEATURE_CODE);
        invalid1.setMessage("ERROR: The feature \"jaX\" does not exist.");

        Diagnostic invalid2 = new Diagnostic();
        invalid2.setRange(r(6, 24, 6, 31));
        invalid2.setCode(LibertyDiagnosticParticipant.INCORRECT_FEATURE_CODE);
        invalid2.setMessage("ERROR: The feature \"invalid\" does not exist.");

        XMLAssert.testDiagnosticsFor(serverXML, null, null, serverXMLURI, invalid1, invalid2);

        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        diagnostics.add(invalid1);

        List<String> featuresStartWithJAX = new ArrayList<String>();
        featuresStartWithJAX.add("jaxb-2.2");
        //featuresStartWithJAX.add("jaxrs-2.0"); excluded because it matches an existing feature with a different version
        //featuresStartWithJAX.add("jaxrs-2.1"); excluded because it matches an existing feature
        featuresStartWithJAX.add("jaxrsClient-2.0");
        featuresStartWithJAX.add("jaxrsClient-2.1");
        featuresStartWithJAX.add("jaxws-2.2");
        Collections.sort(featuresStartWithJAX);

        List<CodeAction> codeActions = new ArrayList<CodeAction>();
        for (String nextFeature: featuresStartWithJAX) {
            TextEdit texted = te(invalid1.getRange().getStart().getLine(), invalid1.getRange().getStart().getCharacter(),
                                invalid1.getRange().getEnd().getLine(), invalid1.getRange().getEnd().getCharacter(), nextFeature);
            CodeAction invalidCodeAction = ca(invalid1, texted);

            codeActions.add(invalidCodeAction);
        }

        XMLAssert.testCodeActionsFor(serverXML, invalid1, codeActions.get(0), codeActions.get(1), 
                                    codeActions.get(2), codeActions.get(3)); 

    }

    @Test
    public void testTrimmedFeatureDiagnostic() {
        String serverXML = String.join(newLine, //
                "<server description=\"Sample Liberty server\">", //
                "       <featureManager>", //
                "               <feature>jaxrs-2.1 </feature>",
                "       </featureManager>", //
                "</server>" //
        );

        XMLAssert.testDiagnosticsFor(serverXML, null, null, serverXMLURI, (Diagnostic[]) null);
    }

    @Test
    public void testDiagnosticsForInclude() throws IOException, BadLocationException {
        // LibertyWorkspace must be initialized
        List<WorkspaceFolder> initList = new ArrayList<WorkspaceFolder>();
        initList.add(new WorkspaceFolder(new File("src/test/resources").toURI().toString()));
        LibertyProjectsManager.getInstance().setWorkspaceFolders(initList);

        String serverXML = String.join(newLine, //
                "<server description=\"default server\">", //
                "    <include optional=\"true\" location=\"./empty_server.xml\"/>", //
                "    <include optional=\"true\" location=\"/empty_server.xml\"/>", //
                "    <include optional=\"true\" location=\"MISSING FILE\"/>", //
                "    <include", //
                "            optional=\"true\" location=\"MULTI LINER\"/>", //
                "    <include optional=\"false\" location=\"MISSING FILE.xml\"/>", //
                "    <include location=\"MISSING FILE.xml\"/>", //
                "    <include location=\"/empty_server.xml/\"/>", //
                "    <include location=\"/testDir.xml\"/>", //
                "</server>"
        );
        
        // Diagnostic location1 = new Diagnostic();
        File serverXMLFile = new File("src/test/resources/server.xml");
        assertFalse(serverXMLFile.exists());
        // Diagnostic will not be made if found
        assertTrue(new File("src/test/resources/empty_server.xml").exists());

        Diagnostic not_xml = new Diagnostic();
        not_xml.setRange(r(3, 29, 3, 52));
        not_xml.setMessage("The specified resource is not an XML file. If it is a directory, it must end with a trailing slash.");

        Diagnostic multi_liner = new Diagnostic();
        multi_liner.setRange(r(5, 28, 5, 50));
        multi_liner.setMessage("The specified resource is not an XML file. If it is a directory, it must end with a trailing slash.");

        Diagnostic not_optional = new Diagnostic();
        not_optional.setRange(r(6, 13, 6, 29));
        not_optional.setCode("not_optional");
        not_optional.setMessage("The specified resource cannot be skipped. Check location value or set optional to true.");

        Diagnostic missing_xml = new Diagnostic();
        missing_xml.setRange(r(6, 30, 6, 57));
        missing_xml.setCode("missing_file");
        missing_xml.setMessage("The resource at the specified location could not be found.");

        Diagnostic optional_not_defined = new Diagnostic();
        optional_not_defined.setRange(r(7, 13, 7, 40));
        optional_not_defined.setCode("implicit_not_optional");
        optional_not_defined.setMessage("The specified resource cannot be skipped. Check location value or add optional attribute.");

        Diagnostic missing_xml2 = new Diagnostic();
        missing_xml2.setRange(r(7, 13, 7, 40));
        missing_xml2.setCode("missing_file");
        missing_xml2.setMessage("The resource at the specified location could not be found.");

        Diagnostic dirIsFile = new Diagnostic();
        dirIsFile.setRange(r(8, 13, 8, 42));
        dirIsFile.setCode("is_file_not_dir");
        dirIsFile.setMessage("Path specified a directory, but resource exists as a file. Please remove the trailing slash.");

        Diagnostic fileIsDir = new Diagnostic();
        fileIsDir.setRange(r(9, 13, 9, 36));
        fileIsDir.setCode("is_dir_not_file");
        fileIsDir.setMessage("Path specified a file, but resource exists as a directory. Please add a trailing slash.");

        XMLAssert.testDiagnosticsFor(serverXML, null, null, serverXMLFile.toURI().toString(), 
                not_xml, multi_liner, not_optional, missing_xml, optional_not_defined, missing_xml2,
                dirIsFile, fileIsDir);

        // Check code actions for add/remove trailing slashes
        String fixedFilePath = "location=\"/empty_server.xml\"";
        TextEdit dirIsFileTextEdit = te(dirIsFile.getRange().getStart().getLine(), dirIsFile.getRange().getStart().getCharacter(),
                            dirIsFile.getRange().getEnd().getLine(), dirIsFile.getRange().getEnd().getCharacter(), fixedFilePath);
        CodeAction dirIsFileCodeAction = ca(dirIsFile, dirIsFileTextEdit);

        String fixedDirPath = "location=\"/testDir.xml/\"";
        TextEdit fileIsDirTextEdit = te(fileIsDir.getRange().getStart().getLine(), fileIsDir.getRange().getStart().getCharacter(),
                            fileIsDir.getRange().getEnd().getLine(), fileIsDir.getRange().getEnd().getCharacter(), fixedDirPath);
        CodeAction fileIsDirCodeAction = ca(fileIsDir, fileIsDirTextEdit);


        XMLAssert.testCodeActionsFor(serverXML, dirIsFile, dirIsFileCodeAction); 

        XMLAssert.testCodeActionsFor(serverXML, fileIsDir, fileIsDirCodeAction);
    }

    @Test
    public void testDiagnosticsForIncludeWindows() throws BadLocationException {
        if (!File.separator.equals("\\")) { // skip test if not Windows
            return;
        }
        // LibertyWorkspace must be initialized
        List<WorkspaceFolder> initList = new ArrayList<WorkspaceFolder>();
        initList.add(new WorkspaceFolder(new File("src/test/resources").toURI().toString()));
        LibertyProjectsManager.getInstance().setWorkspaceFolders(initList);

        String serverXML = String.join(newLine, //
                "<server description=\"default server\">", //
                "    <include location=\"\\empty_server.xml\\\"/>", //
                "    <include location=\"\\testDir.xml\"/>", //
                "</server>"
        );
        
        // Diagnostic location1 = new Diagnostic();
        File serverXMLFile = new File("src/test/resources/server.xml");
        assertFalse(serverXMLFile.exists());
        // Diagnostic will not be made if found
        assertTrue(new File("src/test/resources/empty_server.xml").exists());

        Diagnostic dirIsFile = new Diagnostic();
        dirIsFile.setRange(r(1, 13, 1, 42));
        dirIsFile.setCode("is_file_not_dir");
        dirIsFile.setMessage("Path specified a directory, but resource exists as a file. Please remove the trailing slash.");

        Diagnostic fileIsDir = new Diagnostic();
        fileIsDir.setRange(r(2, 13, 2, 36));
        fileIsDir.setCode("is_dir_not_file");
        fileIsDir.setMessage("Path specified a file, but resource exists as a directory. Please add a trailing slash.");

        XMLAssert.testDiagnosticsFor(serverXML, null, null, serverXMLFile.toURI().toString(), 
                dirIsFile, fileIsDir);

        String fixedFilePath = "location=\"\\empty_server.xml\"";
        TextEdit dirIsFileTextEdit = te(dirIsFile.getRange().getStart().getLine(), dirIsFile.getRange().getStart().getCharacter(),
                            dirIsFile.getRange().getEnd().getLine(), dirIsFile.getRange().getEnd().getCharacter(), fixedFilePath);
        CodeAction dirIsFileCodeAction = ca(dirIsFile, dirIsFileTextEdit);

        String fixedDirPath = "location=\"\\testDir.xml\\\"";
        TextEdit fileIsDirTextEdit = te(fileIsDir.getRange().getStart().getLine(), fileIsDir.getRange().getStart().getCharacter(),
                            fileIsDir.getRange().getEnd().getLine(), fileIsDir.getRange().getEnd().getCharacter(), fixedDirPath);
        CodeAction fileIsDirCodeAction = ca(fileIsDir, fileIsDirTextEdit);

        XMLAssert.testCodeActionsFor(serverXML, dirIsFile, dirIsFileCodeAction); 

        XMLAssert.testCodeActionsFor(serverXML, fileIsDir, fileIsDirCodeAction);
    }

    @Test
    public void testConfigElementMissingFeatureManager() throws JAXBException {
        assertTrue(featureList.exists());
        FeatureService.getInstance().readFeaturesFromFeatureListFile(new ArrayList<Feature>(), libWorkspace, featureList);
        
        String serverXml = "<server><ssl id=\"\"/></server>";
        // Temporarily disabling config element diagnostics if featureManager element is missing (until issue 230 is addressed)
        // Diagnostic config_for_missing_feature = new Diagnostic();
        // config_for_missing_feature.setRange(r(0, serverXml.indexOf("<ssl"), 0, serverXml.length()-"</server>".length()));
        // config_for_missing_feature.setCode(LibertyDiagnosticParticipant.MISSING_CONFIGURED_FEATURE_CODE);
        // config_for_missing_feature.setMessage(LibertyDiagnosticParticipant.MISSING_CONFIGURED_FEATURE_MESSAGE);

        // XMLAssert.testDiagnosticsFor(serverXml, null, null, serverXMLURI, config_for_missing_feature);
        XMLAssert.testDiagnosticsFor(serverXml, null, null, serverXMLURI); // expect no diagnostic for this scenario right now
    }

    @Test
    public void testConfigElementMissingFeatureUsingCachedFeaturelist() throws JAXBException, BadLocationException {
        LibertyWorkspace ws = libPM.getWorkspaceFolder(sampleserverXMLURI);
        ws.setFeatureListGraph(new FeatureListGraph()); // need to clear out the already loaded featureList from other test methods
        FeatureService.getInstance().getDefaultFeatureList();

        String correctFeature   = "        <feature>%s</feature>";
        String incorrectFeature = "        <feature>jaxrs-2.0</feature>";
        String configElement    = "    <springBootApplication location=\"\"/>";
        int diagnosticStart = configElement.indexOf("<");
        int diagnosticLength = configElement.trim().length();

        String serverXML = String.join(newLine,
                "<server description=\"Sample Liberty server\">",
                "    <featureManager>",
                    incorrectFeature,
                "    </featureManager>",
                    configElement,
                "</server>"
        );

        Diagnostic config_for_missing_feature = new Diagnostic();
        config_for_missing_feature.setRange(r(4, diagnosticStart, 4, diagnosticStart + diagnosticLength));
        config_for_missing_feature.setCode(LibertyDiagnosticParticipant.MISSING_CONFIGURED_FEATURE_CODE);
        config_for_missing_feature.setMessage(LibertyDiagnosticParticipant.MISSING_CONFIGURED_FEATURE_MESSAGE);

        XMLAssert.testDiagnosticsFor(serverXML, null, null, sampleserverXMLURI, config_for_missing_feature);

        // TODO: Add code to check the CodeActions also.
        config_for_missing_feature.setSource("springBootApplication");

        List<String> featuresToAdd = new ArrayList<String>();
        featuresToAdd.add("springBoot-1.5");
        featuresToAdd.add("springBoot-2.0");
        featuresToAdd.add("springBoot-3.0");
        Collections.sort(featuresToAdd);

        List<CodeAction> codeActions = new ArrayList<CodeAction>();
        for (String nextFeature: featuresToAdd) {
            String addFeature = System.lineSeparator()+String.format(correctFeature, nextFeature);
            TextEdit texted = te(2, 36, 2, 36, addFeature);
            CodeAction invalidCodeAction = ca(config_for_missing_feature, texted);

            TextDocumentEdit textDoc = tde(sampleserverXMLURI, 0, texted);
            WorkspaceEdit workspaceEdit = new WorkspaceEdit(Collections.singletonList(Either.forLeft(textDoc)));
            
            invalidCodeAction.setEdit(workspaceEdit);
            codeActions.add(invalidCodeAction);
        }

        XMLAssert.testCodeActionsFor(serverXML, sampleserverXMLURI, config_for_missing_feature, (String) null, 
                                    codeActions.get(0), codeActions.get(1), codeActions.get(2)); 

    }


    @Test
    public void testConfigElementDirect() throws JAXBException {
        assertTrue(featureList.exists());
        FeatureService.getInstance().readFeaturesFromFeatureListFile(new ArrayList<Feature>(), libWorkspace, featureList);

        String correctFeature   = "           <feature>Ssl-1.0</feature>";
        String incorrectFeature = "           <feature>jaxrs-2.0</feature>";
        String configElement    = "   <ssl id=\"\"/>";
        int diagnosticStart = configElement.indexOf("<");
        int diagnosticLength = configElement.trim().length();

        String serverXML1 = String.join(newLine,
                "<server description=\"Sample Liberty server\">",
                "   <featureManager>",
                        correctFeature,
                "   </featureManager>",
                    configElement,
                "</server>"
        );
        XMLAssert.testDiagnosticsFor(serverXML1, null, null, serverXMLURI);

        String serverXML2 = String.join(newLine,
                "<server description=\"Sample Liberty server\">",
                "   <featureManager>",
                        incorrectFeature,
                "   </featureManager>",
                    configElement,
                "</server>"
        );

        Diagnostic config_for_missing_feature = new Diagnostic();
        config_for_missing_feature.setRange(r(4, diagnosticStart, 4, diagnosticStart + diagnosticLength));
        config_for_missing_feature.setCode(LibertyDiagnosticParticipant.MISSING_CONFIGURED_FEATURE_CODE);
        config_for_missing_feature.setMessage(LibertyDiagnosticParticipant.MISSING_CONFIGURED_FEATURE_MESSAGE);

        XMLAssert.testDiagnosticsFor(serverXML2, null, null, serverXMLURI, config_for_missing_feature);
    }

    @Test
    public void testConfigElementTransitive() throws JAXBException {
        assertTrue(featureList.exists());
        FeatureService.getInstance().readFeaturesFromFeatureListFile(new ArrayList<Feature>(), libWorkspace, featureList);
        String serverXML1 = String.join(newLine,
                "<server description=\"Sample Liberty server\">",
                "   <featureManager>",
                "       <feature>microProfile-5.0</feature>",
                "   </featureManager>",
                "   <ssl id=\"\"/>",
                "</server>"
        );
        XMLAssert.testDiagnosticsFor(serverXML1, null, null, serverXMLURI);
    }
}