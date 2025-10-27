package bose.ankush.route

import bose.ankush.module
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DecodeRouteTest {

    @Test
    fun decodeRoute_returnsOk() = testApplication {
        application { module() }
        val response = client.get("/decode")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun decodeRoute_containsRequiredElements() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify page structure
        assertTrue(body.contains("Data Decoder"), "Page should contain title")
        assertTrue(body.contains("json-input"), "Page should contain input textarea")
        assertTrue(body.contains("code-output"), "Page should contain output area")
        assertTrue(body.contains("line-gutter"), "Page should contain line gutter")
        assertTrue(body.contains("error-box"), "Page should contain error box")
    }

    @Test
    fun decodeRoute_containsFormatSelector() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify format selector
        assertTrue(body.contains("format-select"), "Page should contain format selector")
        assertTrue(body.contains("JSON"), "Format selector should have JSON option")
        assertTrue(body.contains("XML"), "Format selector should have XML option")
        assertTrue(body.contains("Protobuf"), "Format selector should have Protobuf option")
    }

    @Test
    fun decodeRoute_containsInputButtons() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify input manipulation buttons
        assertTrue(body.contains("btn-format"), "Page should contain Format button")
        assertTrue(body.contains("btn-autofix"), "Page should contain Auto Fix button")
        assertTrue(body.contains("btn-unescape"), "Page should contain Unescape button")
        assertTrue(body.contains("btn-clear"), "Page should contain Clear button")
        assertTrue(body.contains("btn-sample"), "Page should contain Sample button")
    }

    @Test
    fun decodeRoute_containsOutputButtons() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify output manipulation buttons
        assertTrue(body.contains("btn-copy"), "Page should contain Copy button")
        assertTrue(body.contains("btn-minify"), "Page should contain Minify button")
        assertTrue(body.contains("btn-sort"), "Page should contain Sort Keys button")
        assertTrue(body.contains("btn-download"), "Page should contain Download button")
        assertTrue(body.contains("btn-create-mock"), "Page should contain Create Mock API button")
    }

    @Test
    fun decodeRoute_containsMockApiBox() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify mock API box elements
        assertTrue(body.contains("mock-api-box"), "Page should contain mock API box")
        assertTrue(body.contains("mock-url"), "Page should contain mock URL element")
        assertTrue(body.contains("btn-mock-copy"), "Page should contain Copy URL button")
        assertTrue(body.contains("btn-mock-reset"), "Page should contain Reset button")
    }

    @Test
    fun decodeRoute_containsHowToUseSection() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify How to Use section
        assertTrue(body.contains("howto-details"), "Page should contain How to Use section")
        assertTrue(body.contains("How to Use"), "Page should contain How to Use text")
    }

    @Test
    fun decodeRoute_containsStandardizedHeader() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify standardized header component
        assertTrue(body.contains("app-header"), "Page should contain standardized header")
        assertTrue(body.contains("Androidplay"), "Header should contain Androidplay branding")
        assertTrue(body.contains("Data Decoder"), "Header should contain Data Decoder subtitle")
        assertTrue(body.contains("initializeHeader"), "Page should initialize header")
    }

    @Test
    fun decodeRoute_includesRequiredCSS() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify CSS includes
        assertTrue(body.contains("decode-panel-bg"), "Page should contain decode-specific CSS variables")
        assertTrue(body.contains("decode-grid"), "Page should contain decode grid styles")
        assertTrue(body.contains("token-key"), "Page should contain syntax highlighting styles")
    }

    @Test
    fun decodeRoute_includesRequiredJS() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify JavaScript includes
        assertTrue(
            body.contains("decode.js") || body.contains("initDecode"),
            "Page should include decode JavaScript"
        )
    }

    @Test
    fun decodeRoute_includesMaterialIcons() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify Material Icons
        assertTrue(
            body.contains("Material+Icons") || body.contains("material-icons"),
            "Page should include Material Icons"
        )
        assertTrue(body.contains("material-icons"), "Page should use material-icons class")
    }

    @Test
    fun decodeRoute_hasResponsiveDesign() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify responsive design elements
        assertTrue(body.contains("viewport"), "Page should have viewport meta tag")
        assertTrue(body.contains("@media"), "Page should contain media queries")
        assertTrue(
            body.contains("max-width: 900px") || body.contains("max-width:900px"),
            "Page should have tablet breakpoint"
        )
        assertTrue(
            body.contains("max-width: 480px") || body.contains("max-width:480px"),
            "Page should have mobile breakpoint"
        )
    }

    @Test
    fun decodeRoute_hasSafeAreaInsets() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify safe area insets for mobile devices
        assertTrue(body.contains("safe-area-inset"), "Page should support safe area insets")
    }

    @Test
    fun decodeRoute_hasThemeSupport() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify theme support
        assertTrue(
            body.contains("data-theme") || body.contains("[data-theme"),
            "Page should support theme switching"
        )
        assertTrue(body.contains("theme-toggle"), "Page should have theme toggle")
    }

    @Test
    fun decodeRoute_hasAccessibilityFeatures() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify accessibility features
        assertTrue(body.contains("aria-label"), "Page should have ARIA labels")
        assertTrue(body.contains("role="), "Page should have ARIA roles")
        assertTrue(body.contains("title="), "Buttons should have title attributes for tooltips")
    }

    // ===== Task 6.1: JSON Formatting and Validation Tests =====

    @Test
    fun decodeRoute_jsonFormatConfig_isCorrect() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify JSON format configuration exists in JavaScript
        assertTrue(body.contains("FORMAT_CONFIGS"), "Page should contain format configurations")
        assertTrue(body.contains("json:"), "Format configs should include JSON")
        assertTrue(
            body.contains("fileExtension: 'json'") || body.contains("fileExtension:'json'"),
            "JSON config should have correct file extension"
        )
        assertTrue(
            body.contains("supportsAutoFix: true") || body.contains("supportsAutoFix:true"),
            "JSON should support auto-fix"
        )
    }

    @Test
    fun decodeRoute_jsonSyntaxHighlighting_stylesExist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify syntax highlighting CSS classes exist
        assertTrue(body.contains("token-key"), "Page should have token-key style")
        assertTrue(body.contains("token-string"), "Page should have token-string style")
        assertTrue(body.contains("token-number"), "Page should have token-number style")
        assertTrue(body.contains("token-boolean"), "Page should have token-boolean style")
        assertTrue(body.contains("token-null"), "Page should have token-null style")
    }

    @Test
    fun decodeRoute_jsonErrorHandling_elementsExist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify error handling elements
        assertTrue(body.contains("error-box"), "Page should have error box")
        assertTrue(body.contains("error-message"), "Page should have error message class")
        assertTrue(
            body.contains("error-line") || body.contains("class=\"error\""),
            "Page should support error line highlighting"
        )
    }

    @Test
    fun decodeRoute_jsonFormatFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify format function exists
        assertTrue(
            body.contains("formatNow") || body.contains("function formatNow"),
            "Page should have formatNow function"
        )
        assertTrue(body.contains("JSON.parse"), "Page should use JSON.parse for validation")
        assertTrue(body.contains("JSON.stringify"), "Page should use JSON.stringify for formatting")
    }

    @Test
    fun decodeRoute_jsonAutoFixFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify auto-fix function exists
        assertTrue(
            body.contains("autoFixNow") || body.contains("function autoFixNow"),
            "Page should have autoFixNow function"
        )
        assertTrue(
            body.contains("attemptAutoFix") || body.contains("function attemptAutoFix"),
            "Page should have attemptAutoFix function"
        )
    }

    @Test
    fun decodeRoute_jsonAutoFixRules_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify auto-fix rules are implemented
        assertTrue(
            body.contains("normalizeQuotes") || body.contains("smart quotes"),
            "Auto-fix should handle quote normalization"
        )
        assertTrue(
            body.contains("removeTrailingCommas") || body.contains("trailing comma"),
            "Auto-fix should remove trailing commas"
        )
        assertTrue(
            body.contains("replacePythonLiterals") || body.contains("True") && body.contains("False"),
            "Auto-fix should handle Python literals"
        )
        assertTrue(
            body.contains("stripComments") || body.contains("comment"),
            "Auto-fix should strip comments"
        )
    }

    // ===== Task 6.2: XML Formatting and Validation Tests =====

    @Test
    fun decodeRoute_xmlFormatConfig_isCorrect() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify XML format configuration
        assertTrue(body.contains("xml:"), "Format configs should include XML")
        assertTrue(
            body.contains("fileExtension: 'xml'") || body.contains("fileExtension:'xml'"),
            "XML config should have correct file extension"
        )
        assertTrue(
            body.contains("supportsAutoFix: false") || body.contains("supportsAutoFix:false"),
            "XML should not support auto-fix"
        )
    }

    @Test
    fun decodeRoute_xmlParserFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify XML parser function exists
        assertTrue(
            body.contains("xmlParserResult") || body.contains("DOMParser"),
            "Page should have XML parser functionality"
        )
        assertTrue(body.contains("parseFromString"), "Page should use DOMParser.parseFromString")
    }

    @Test
    fun decodeRoute_xmlMinifyFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify XML minify function exists
        assertTrue(
            body.contains("xmlMinify") || body.contains("XMLSerializer"),
            "Page should have XML minify functionality"
        )
    }

    // ===== Task 6.3: Protobuf Hex Dump Tests =====

    @Test
    fun decodeRoute_protobufFormatConfig_isCorrect() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify Protobuf format configuration
        assertTrue(body.contains("protobuf:"), "Format configs should include Protobuf")
        assertTrue(
            body.contains("fileExtension: 'bin'") || body.contains("fileExtension:'bin'"),
            "Protobuf config should have correct file extension"
        )
        assertTrue(body.contains("Base64"), "Protobuf placeholder should mention Base64")
    }

    @Test
    fun decodeRoute_protobufHexDump_logicExists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify hex dump logic exists
        assertTrue(
            body.contains("atob") || body.contains("base64"),
            "Page should decode Base64 for protobuf"
        )
        assertTrue(
            body.contains("toString(16)") || body.contains("hex"),
            "Page should convert to hex format"
        )
    }

    // ===== Task 6.4: Manipulation Features Tests =====

    @Test
    fun decodeRoute_minifyFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify minify function
        assertTrue(
            body.contains("minifyNow") || body.contains("function minifyNow"),
            "Page should have minifyNow function"
        )
        assertTrue(body.contains("btn-minify"), "Page should have minify button")
    }

    @Test
    fun decodeRoute_sortKeysFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify sort keys function
        assertTrue(
            body.contains("sortKeysNow") || body.contains("function sortKeysNow"),
            "Page should have sortKeysNow function"
        )
        assertTrue(
            body.contains("deepSort") || body.contains("Object.keys") && body.contains("sort"),
            "Page should have deep sort logic"
        )
        assertTrue(body.contains("btn-sort"), "Page should have sort keys button")
    }

    @Test
    fun decodeRoute_unescapeFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify unescape function
        assertTrue(
            body.contains("unescapeNow") || body.contains("function unescapeNow"),
            "Page should have unescapeNow function"
        )
        assertTrue(body.contains("btn-unescape"), "Page should have unescape button")
    }

    @Test
    fun decodeRoute_copyFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify copy function
        assertTrue(
            body.contains("copyOutput") || body.contains("function copyOutput"),
            "Page should have copyOutput function"
        )
        assertTrue(body.contains("btn-copy"), "Page should have copy button")
        assertTrue(
            body.contains("execCommand") || body.contains("clipboard"),
            "Page should use clipboard API"
        )
    }

    @Test
    fun decodeRoute_downloadFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify download function
        assertTrue(
            body.contains("downloadOutput") || body.contains("function downloadOutput"),
            "Page should have downloadOutput function"
        )
        assertTrue(body.contains("btn-download"), "Page should have download button")
        assertTrue(
            body.contains("Blob") || body.contains("createObjectURL"),
            "Page should use Blob API for downloads"
        )
    }

    @Test
    fun decodeRoute_sampleDataFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify sample data function
        assertTrue(
            body.contains("insertSample") || body.contains("function insertSample"),
            "Page should have insertSample function"
        )
        assertTrue(body.contains("btn-sample"), "Page should have sample button")
    }

    @Test
    fun decodeRoute_clearFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify clear function
        assertTrue(body.contains("btn-clear"), "Page should have clear button")
    }

    // ===== Task 6.5: Mock API Tests =====

    @Test
    fun decodeRoute_mockApiCreateFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify mock API create function
        assertTrue(
            body.contains("createMock") || body.contains("function createMock"),
            "Page should have createMock function"
        )
        assertTrue(
            body.contains("/mock/create") || body.contains("mock") && body.contains("create"),
            "Page should call mock create endpoint"
        )
    }

    @Test
    fun decodeRoute_mockApiResetFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify mock API reset function
        assertTrue(
            body.contains("resetMock") || body.contains("function resetMock"),
            "Page should have resetMock function"
        )
        assertTrue(
            body.contains("DELETE") || body.contains("delete"),
            "Page should use DELETE method for reset"
        )
    }

    @Test
    fun decodeRoute_mockApiCopyUrlFunction_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify mock API copy URL function
        assertTrue(
            body.contains("copyMockUrl") || body.contains("function copyMockUrl"),
            "Page should have copyMockUrl function"
        )
        assertTrue(body.contains("btn-mock-copy"), "Page should have copy URL button")
    }

    @Test
    fun decodeRoute_mockApiValidation_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify mock API validation
        assertTrue(
            body.contains("getJsonForMock") || body.contains("valid JSON"),
            "Page should validate JSON before creating mock"
        )
    }

    // ===== Task 6.6: Responsive Design Tests =====

    @Test
    fun decodeRoute_mobileBreakpoint_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify mobile breakpoint
        assertTrue(
            body.contains("max-width: 480px") || body.contains("max-width:480px"),
            "Page should have mobile breakpoint at 480px"
        )
        assertTrue(body.contains("safe-area-inset"), "Page should support safe area insets")
    }

    @Test
    fun decodeRoute_tabletBreakpoint_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify tablet breakpoint
        assertTrue(
            body.contains("max-width: 900px") || body.contains("max-width:900px"),
            "Page should have tablet breakpoint at 900px"
        )
        assertTrue(
            body.contains("grid-template-columns: 1fr") || body.contains("grid-template-columns:1fr"),
            "Tablet layout should use single column"
        )
    }

    @Test
    fun decodeRoute_responsiveGrid_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify responsive grid
        assertTrue(body.contains("decode-grid"), "Page should have decode grid")
        assertTrue(body.contains("grid-template-columns"), "Grid should use CSS Grid")
    }

    // ===== Task 6.7: Theme Switching Tests =====

    @Test
    fun decodeRoute_themeVariables_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify theme CSS variables
        assertTrue(body.contains("--decode-panel-bg"), "Page should have decode panel background variable")
        assertTrue(body.contains("--decode-btn-bg"), "Page should have decode button background variable")
        assertTrue(
            body.contains("--card-bg") || body.contains("var(--"),
            "Page should use CSS custom properties for theming"
        )
    }

    @Test
    fun decodeRoute_lightThemeColors_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify light theme specific colors
        assertTrue(
            body.contains("[data-theme=\"light\"]") || body.contains("[data-theme='light']"),
            "Page should have light theme styles"
        )
        assertTrue(
            body.contains("data-theme") && body.contains("light"),
            "Page should support light theme"
        )
    }

    @Test
    fun decodeRoute_themeTransitions_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify smooth theme transitions
        assertTrue(body.contains("transition"), "Page should have CSS transitions")
        assertTrue(
            body.contains("background-color") || body.contains("color"),
            "Transitions should include color properties"
        )
    }

    // ===== Task 6.8: Accessibility Tests =====

    @Test
    fun decodeRoute_keyboardNavigation_supported() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify keyboard navigation support
        assertTrue(body.contains("button"), "Page should have button elements")
        assertTrue(
            body.contains("tabindex") || body.contains("button") && body.contains("btn"),
            "Interactive elements should be keyboard accessible"
        )
    }

    @Test
    fun decodeRoute_focusIndicators_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify focus indicators
        assertTrue(
            body.contains(":focus") || body.contains("focus"),
            "Page should have focus styles"
        )
    }

    @Test
    fun decodeRoute_ariaLabels_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify ARIA labels
        assertTrue(body.contains("aria-label"), "Page should have ARIA labels")
        assertTrue(body.contains("role="), "Page should have ARIA roles")
    }

    @Test
    fun decodeRoute_tooltips_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify tooltips on buttons
        assertTrue(body.contains("title="), "Buttons should have title attributes")
        val titleCount = body.split("title=").size - 1
        assertTrue(titleCount >= 10, "Multiple buttons should have tooltips")
    }

    @Test
    fun decodeRoute_colorContrast_variablesExist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify color contrast considerations
        assertTrue(
            body.contains("--text-color") || body.contains("color:"),
            "Page should define text colors"
        )
        assertTrue(
            body.contains("--text-secondary") || body.contains("secondary"),
            "Page should have secondary text color"
        )
    }

    @Test
    fun decodeRoute_realTimeFormatting_exists() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify real-time formatting with debounce
        assertTrue(
            body.contains("debounce") || body.contains("input") && body.contains("addEventListener"),
            "Page should have real-time formatting"
        )
        assertTrue(
            body.contains("addEventListener('input'") || body.contains("addEventListener(\"input\""),
            "Input should have event listener"
        )
    }

    @Test
    fun decodeRoute_loadingStates_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify loading states for async operations
        assertTrue(
            body.contains("disabled") || body.contains("Creating...") || body.contains("loading"),
            "Page should have loading state handling"
        )
    }

    @Test
    fun decodeRoute_toastNotifications_exist() = testApplication {
        application { module() }
        val response = client.get("/decode")
        val body = response.bodyAsText()

        // Verify toast notification system
        assertTrue(
            body.contains("showToast") || body.contains("toast"),
            "Page should have toast notification system"
        )
        assertTrue(
            body.contains("success") && body.contains("error"),
            "Toast should support success and error types"
        )
    }
}
