package com.resumeai.export.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Synchronous PDF generation service using Flying Saucer + OpenPDF.
 *
 * Flow:
 *  1. Fetch aggregated ResumeData from section-service
 *  2. Fetch rendered HTML from template-service (render-data endpoint)
 *  3. Clean HTML for XHTML compliance
 *  4. Generate PDF bytes with Flying Saucer
 */
@Service
public class PdfGenerationService {

    private static final String SECTION_SERVICE_URL =
            "http://localhost:8083/api/v1/sections/resume/%d/aggregate";

    private static final String TEMPLATE_RENDER_URL =
            "http://localhost:8084/api/v1/templates/%s/render-data";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate a PDF byte array for the given resumeId and templateId.
     *
     * @param resumeId   ID of the resume in section-service
     * @param templateId ID of the template in template-service
     * @return PDF bytes
     */
    public byte[] generatePdf(Long resumeId, String templateId) {
        // Step 1: Fetch resume data
        Map<String, Object> resumeData = fetchResumeData(resumeId);

        // Step 2: Render HTML via template-service
        String html = fetchRenderedHtml(templateId, resumeData);

        // Step 3: Generate PDF
        return htmlToPdf(html);
    }

    /**
     * Generate PDF from an already-rendered HTML string.
     * Useful when the caller already has the HTML (e.g., async listener).
     */
    public byte[] htmlToPdf(String html) {
        try {
            String xhtml = prepareXhtml(html);
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml, "");
            renderer.layout();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private static final String RESUME_SERVICE_URL =
            "http://localhost:8082/api/v1/resumes/%d";

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchResumeData(Long resumeId) {
        try {
            // 1. Get sections data (Experience, Education, Skills, etc.)
            String sectionUrl = String.format(SECTION_SERVICE_URL, resumeId);
            Map<String, Object> aggregatedData = restTemplate.getForObject(sectionUrl, Map.class);
            if (aggregatedData == null) {
                aggregatedData = new java.util.HashMap<>();
            }

            // 2. Get personal info from resume-service (Resume entity)
            String resumeUrl = String.format(RESUME_SERVICE_URL, resumeId);
            Map<String, Object> resumeEntity = null;
            try {
                // Pass a dummy user email header, as resume-service might require it for auth/access,
                // or preferably, we should expose an internal endpoint or use a service token.
                // For now, we'll try to fetch it.
                org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                headers.set("X-Auth-User", "internal-export-service");
                org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                
                org.springframework.http.ResponseEntity<Map> response = restTemplate.exchange(
                    resumeUrl, org.springframework.http.HttpMethod.GET, entity, Map.class);
                resumeEntity = response.getBody();
            } catch (Exception ignored) {
                // Ignore if resume-service call fails (e.g., auth issues during internal call)
            }

            // 3. Merge personal info
            Map<String, Object> personalInfo = (Map<String, Object>) aggregatedData.get("personalInfo");
            if (personalInfo == null) {
                personalInfo = new java.util.HashMap<>();
                aggregatedData.put("personalInfo", personalInfo);
            }

            if (resumeEntity != null) {
                if (resumeEntity.get("fullName") != null) personalInfo.put("fullName", resumeEntity.get("fullName"));
                if (resumeEntity.get("email") != null) personalInfo.put("email", resumeEntity.get("email"));
                if (resumeEntity.get("phone") != null) personalInfo.put("phone", resumeEntity.get("phone"));
                if (resumeEntity.get("location") != null) personalInfo.put("location", resumeEntity.get("location"));
                if (resumeEntity.get("linkedin") != null) personalInfo.put("linkedin", resumeEntity.get("linkedin"));
                if (resumeEntity.get("website") != null) personalInfo.put("website", resumeEntity.get("website"));
                if (resumeEntity.get("targetJobTitle") != null && personalInfo.get("jobTitle") == null) {
                    personalInfo.put("jobTitle", resumeEntity.get("targetJobTitle"));
                }
                if (resumeEntity.get("summary") != null && aggregatedData.get("summary") == null) {
                    aggregatedData.put("summary", resumeEntity.get("summary"));
                }
            }

            return aggregatedData;
        } catch (Exception e) {
            throw new RuntimeException("Resume data unavailable: " + e.getMessage(), e);
        }
    }

    private String fetchRenderedHtml(String templateId, Object resumeData) {
        try {
            String url = String.format(TEMPLATE_RENDER_URL, templateId);
            return restTemplate.postForObject(url, resumeData, String.class);
        } catch (Exception e) {
            throw new RuntimeException(
                "Template unavailable (template-service): " + e.getMessage(), e);
        }
    }

    /**
     * Cleans raw HTML from the template engine into valid XHTML suitable for Flying Saucer.
     * Flying Saucer is strict: it requires valid XML / XHTML 1.0.
     */
    private String prepareXhtml(String html) {
        if (html == null || html.isBlank()) {
            return buildMinimalXhtml("<p>No content</p>");
        }

        // If it already starts with a proper doctype/xml declaration, use as-is
        if (html.trim().startsWith("<?xml") || html.trim().startsWith("<!DOCTYPE")) {
            return ensureXmlDeclaration(html);
        }

        // Wrap bare HTML body content in a full XHTML document
        if (!html.toLowerCase().contains("<html")) {
            return buildMinimalXhtml(html);
        }

        return ensureXmlDeclaration(html);
    }

    private String buildMinimalXhtml(String body) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                    "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                  <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
                  <style type="text/css">
                    body { font-family: Arial, sans-serif; font-size: 11pt; margin: 0; padding: 20px; }
                  </style>
                </head>
                <body>
                """ + body + """
                </body>
                </html>
                """;
    }

    private String ensureXmlDeclaration(String html) {
        if (!html.trim().startsWith("<?xml")) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + html;
        }
        return html;
    }
}
