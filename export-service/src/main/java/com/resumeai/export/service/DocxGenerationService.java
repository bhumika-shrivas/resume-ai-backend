package com.resumeai.export.service;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Service
public class DocxGenerationService {

    public byte[] generateDocx(Map<String, Object> data) throws Exception {
        String templateKey = getString(data, "templateKey", "modern-template");
        
        switch (templateKey) {
            case "executive-template":
                return generateExecutiveDocx(data);
            case "ats-pro-template":
                return generateAtsProDocx(data);
            case "modern-template":
            default:
                return generateModernDocx(data);
        }
    }

    // ========================================================================
    // ATS PRO TEMPLATE (Strict Linear)
    // ========================================================================
    private byte[] generateAtsProDocx(Map<String, Object> data) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Map<String, Object> personalInfo = (Map<String, Object>) data.get("personalInfo");
            if (personalInfo != null) {
                XWPFParagraph namePara = document.createParagraph();
                namePara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun nameRun = namePara.createRun();
                nameRun.setText(getString(personalInfo, "fullName", "Your Name"));
                nameRun.setBold(true);
                nameRun.setFontSize(20);

                String jobTitle = getString(personalInfo, "jobTitle", "");
                if (!jobTitle.isEmpty()) {
                    XWPFParagraph titlePara = document.createParagraph();
                    titlePara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun titleRun = titlePara.createRun();
                    titleRun.setText(jobTitle);
                    titleRun.setFontSize(14);
                }

                XWPFParagraph contactPara = document.createParagraph();
                contactPara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun contactRun = contactPara.createRun();
                String email = getString(personalInfo, "email", "");
                String phone = getString(personalInfo, "phone", "");
                String location = getString(personalInfo, "location", "");
                String contactStr = String.join(" | ", List.of(email, phone, location).stream().filter(s -> !s.isEmpty()).toList());
                contactRun.setText(contactStr);
                contactRun.setFontSize(11);
                
                document.createParagraph(); // Spacing
            }

            renderAtsLinearSections(document, data);

            document.write(baos);
            return baos.toByteArray();
        }
    }

    private void renderAtsLinearSections(XWPFDocument document, Map<String, Object> data) {
        String summary = getString(data, "summary", "");
        if (!summary.isEmpty()) {
            addAtsHeading(document, "SUMMARY");
            addAtsText(document, summary);
        }

        List<Map<String, Object>> experience = (List<Map<String, Object>>) data.get("experience");
        if (experience != null && !experience.isEmpty()) {
            addAtsHeading(document, "EXPERIENCE");
            for (Map<String, Object> exp : experience) {
                XWPFParagraph header = document.createParagraph();
                XWPFRun titleRun = header.createRun();
                titleRun.setText(getString(exp, "jobTitle", ""));
                titleRun.setBold(true);

                String company = getString(exp, "company", "");
                if (!company.isEmpty()) {
                    XWPFRun compRun = header.createRun();
                    compRun.setText(" - " + company);
                }

                String startDate = getString(exp, "startDate", "");
                String endDate = getString(exp, "endDate", "Present");
                String loc = getString(exp, "location", "");
                XWPFRun dateRun = header.createRun();
                dateRun.setText("  (" + startDate + " - " + endDate + (loc.isEmpty() ? "" : ", " + loc) + ")");
                dateRun.setItalic(true);

                addAtsText(document, getString(exp, "description", ""));
                document.createParagraph();
            }
        }

        List<Map<String, Object>> education = (List<Map<String, Object>>) data.get("education");
        if (education != null && !education.isEmpty()) {
            addAtsHeading(document, "EDUCATION");
            for (Map<String, Object> edu : education) {
                XWPFParagraph header = document.createParagraph();
                XWPFRun degreeRun = header.createRun();
                degreeRun.setText(getString(edu, "degree", ""));
                degreeRun.setBold(true);

                String field = getString(edu, "fieldOfStudy", "");
                if (!field.isEmpty()) {
                    XWPFRun fieldRun = header.createRun();
                    fieldRun.setText(" in " + field);
                }

                String inst = getString(edu, "institution", "");
                String year = getString(edu, "year", "");
                XWPFRun metaRun = header.createRun();
                metaRun.setText(" - " + inst + (year.isEmpty() ? "" : ", " + year));
            }
            document.createParagraph();
        }

        List<String> skills = (List<String>) data.get("skills");
        if (skills != null && !skills.isEmpty()) {
            addAtsHeading(document, "SKILLS");
            XWPFParagraph para = document.createParagraph();
            XWPFRun run = para.createRun();
            run.setText(String.join(", ", skills));
            document.createParagraph();
        }
    }

    private void addAtsHeading(XWPFDocument document, String title) {
        XWPFParagraph para = document.createParagraph();
        para.setBorderBottom(Borders.SINGLE);
        XWPFRun run = para.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(12);
    }

    private void addAtsText(XWPFDocument document, String text) {
        if (text == null || text.trim().isEmpty()) return;
        for (String line : stripHtml(text).split("\n")) {
            if (line.trim().isEmpty()) continue;
            XWPFParagraph para = document.createParagraph();
            XWPFRun run = para.createRun();
            if (line.trim().startsWith("•") || line.trim().startsWith("-")) {
                run.setText("• " + line.trim().substring(1).trim());
            } else {
                run.setText(line.trim());
            }
            run.setFontSize(11);
        }
    }


    // ========================================================================
    // EXECUTIVE TEMPLATE (Header Banner + Single Column Body)
    // ========================================================================
    private byte[] generateExecutiveDocx(Map<String, Object> data) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Map<String, Object> custom = (Map<String, Object>) data.get("customization");
            String accent = getString(custom, "primaryColor", "#3B82F6").replace("#", "");
            
            // Header Banner using a 1-cell table
            XWPFTable banner = document.createTable(1, 1);
            banner.setWidth("100%");
            setTableBordersNone(banner);
            XWPFTableCell bannerCell = banner.getRow(0).getCell(0);
            setCellBackground(bannerCell, accent);
            bannerCell.removeParagraph(0);

            Map<String, Object> personalInfo = (Map<String, Object>) data.get("personalInfo");
            if (personalInfo != null) {
                XWPFParagraph namePara = bannerCell.addParagraph();
                namePara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun nameRun = namePara.createRun();
                nameRun.setText(getString(personalInfo, "fullName", "Your Name"));
                nameRun.setBold(true);
                nameRun.setFontSize(24);
                nameRun.setColor("FFFFFF");

                String jobTitle = getString(personalInfo, "jobTitle", "");
                if (!jobTitle.isEmpty()) {
                    XWPFParagraph titlePara = bannerCell.addParagraph();
                    titlePara.setAlignment(ParagraphAlignment.CENTER);
                    XWPFRun titleRun = titlePara.createRun();
                    titleRun.setText(jobTitle);
                    titleRun.setFontSize(14);
                    titleRun.setColor("FFFFFF");
                }

                XWPFParagraph contactPara = bannerCell.addParagraph();
                contactPara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun contactRun = contactPara.createRun();
                String email = getString(personalInfo, "email", "");
                String phone = getString(personalInfo, "phone", "");
                String location = getString(personalInfo, "location", "");
                String contactStr = String.join(" | ", List.of(email, phone, location).stream().filter(s -> !s.isEmpty()).toList());
                contactRun.setText(contactStr);
                contactRun.setFontSize(11);
                contactRun.setColor("FFFFFF");
            }
            
            document.createParagraph(); // Spacer below banner

            // Summary
            String summary = getString(data, "summary", "");
            if (!summary.isEmpty()) {
                addExecHeading(document, "Executive Summary", accent);
                addAtsText(document, summary);
                document.createParagraph();
            }

            // Experience
            List<Map<String, Object>> experience = (List<Map<String, Object>>) data.get("experience");
            if (experience != null && !experience.isEmpty()) {
                addExecHeading(document, "Professional Experience", accent);
                for (Map<String, Object> exp : experience) {
                    XWPFParagraph header = document.createParagraph();
                    XWPFRun titleRun = header.createRun();
                    titleRun.setText(getString(exp, "jobTitle", ""));
                    titleRun.setBold(true);
                    titleRun.setFontSize(12);

                    String startDate = getString(exp, "startDate", "");
                    String endDate = getString(exp, "endDate", "Present");
                    XWPFRun dateRun = header.createRun();
                    dateRun.setText(" (" + startDate + " - " + endDate + ")");
                    dateRun.setItalic(true);
                    dateRun.setFontSize(10);

                    XWPFParagraph sub = document.createParagraph();
                    XWPFRun compRun = sub.createRun();
                    compRun.setText(getString(exp, "company", ""));
                    compRun.setColor(accent);
                    compRun.setBold(true);
                    
                    String loc = getString(exp, "location", "");
                    if (!loc.isEmpty()) {
                        XWPFRun locRun = sub.createRun();
                        locRun.setText(" · " + loc);
                        locRun.setColor(accent);
                    }

                    addAtsText(document, getString(exp, "description", ""));
                    document.createParagraph();
                }
            }

            // 2-column for Skills and Certifications
            List<String> skills = (List<String>) data.get("skills");
            List<Map<String, Object>> certs = (List<Map<String, Object>>) data.get("certifications");
            
            if ((skills != null && !skills.isEmpty()) || (certs != null && !certs.isEmpty())) {
                XWPFTable bottomTable = document.createTable(1, 2);
                bottomTable.setWidth("100%");
                setTableBordersNone(bottomTable);
                
                XWPFTableCell skillCell = bottomTable.getRow(0).getCell(0);
                skillCell.setWidth("50%");
                skillCell.removeParagraph(0);
                
                XWPFTableCell certCell = bottomTable.getRow(0).getCell(1);
                certCell.setWidth("50%");
                certCell.removeParagraph(0);

                if (skills != null && !skills.isEmpty()) {
                    addExecHeadingToCell(skillCell, "Key Competencies", accent);
                    for (String skill : skills) {
                        XWPFParagraph p = skillCell.addParagraph();
                        XWPFRun r = p.createRun();
                        r.setText("• " + skill);
                    }
                }
                
                if (certs != null && !certs.isEmpty()) {
                    addExecHeadingToCell(certCell, "Certifications", accent);
                    for (Map<String, Object> cert : certs) {
                        XWPFParagraph p = certCell.addParagraph();
                        XWPFRun r = p.createRun();
                        r.setText("• " + getString(cert, "name", ""));
                        r.setBold(true);
                        
                        String issuer = getString(cert, "issuer", "");
                        if (!issuer.isEmpty()) {
                            XWPFRun r2 = p.createRun();
                            r2.setText(" (" + issuer + ")");
                            r2.setFontSize(10);
                        }
                    }
                }
            }

            document.write(baos);
            return baos.toByteArray();
        }
    }

    private void addExecHeading(XWPFDocument document, String title, String accentColor) {
        XWPFParagraph para = document.createParagraph();
        para.setBorderBottom(Borders.SINGLE);
        XWPFRun run = para.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(14);
        run.setColor(accentColor);
    }
    
    private void addExecHeadingToCell(XWPFTableCell cell, String title, String accentColor) {
        XWPFParagraph para = cell.addParagraph();
        para.setBorderBottom(Borders.SINGLE);
        XWPFRun run = para.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(12);
        run.setColor(accentColor);
    }


    // ========================================================================
    // MODERN TEMPLATE (2-Column Layout)
    // ========================================================================
    private byte[] generateModernDocx(Map<String, Object> data) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Map<String, Object> custom = (Map<String, Object>) data.get("customization");
            String accent = getString(custom, "primaryColor", "#2563EB").replace("#", "");
            String sidebarColor = getString(custom, "secondaryColor", "#1E293B").replace("#", "");

            XWPFTable table = document.createTable(1, 2);
            table.setWidth("100%");
            setTableBordersNone(table);

            XWPFTableRow row = table.getRow(0);
            XWPFTableCell leftCell = row.getCell(0);
            XWPFTableCell rightCell = row.getCell(1);

            leftCell.setWidth("33%");
            setCellBackground(leftCell, sidebarColor);
            leftCell.removeParagraph(0);

            rightCell.setWidth("67%");
            rightCell.removeParagraph(0);

            // -- LEFT CELL --
            Map<String, Object> personalInfo = (Map<String, Object>) data.get("personalInfo");
            if (personalInfo != null) {
                XWPFParagraph namePara = leftCell.addParagraph();
                XWPFRun nameRun = namePara.createRun();
                nameRun.setText(getString(personalInfo, "fullName", "Your Name"));
                nameRun.setBold(true);
                nameRun.setFontSize(22);
                nameRun.setColor("FFFFFF");

                String jobTitle = getString(personalInfo, "jobTitle", "");
                if (!jobTitle.isEmpty()) {
                    XWPFParagraph titlePara = leftCell.addParagraph();
                    XWPFRun titleRun = titlePara.createRun();
                    titleRun.setText(jobTitle);
                    titleRun.setFontSize(14);
                    titleRun.setColor("818CF8"); 
                }
                leftCell.addParagraph(); 

                addLeftHeading(leftCell, "Contact");
                addLeftText(leftCell, getString(personalInfo, "email", ""));
                addLeftText(leftCell, getString(personalInfo, "phone", ""));
                addLeftText(leftCell, getString(personalInfo, "location", ""));
                addLeftText(leftCell, getString(personalInfo, "linkedin", ""));
                leftCell.addParagraph(); 
            }

            List<String> skills = (List<String>) data.get("skills");
            if (skills != null && !skills.isEmpty()) {
                addLeftHeading(leftCell, "Skills");
                for (String skill : skills) {
                    addLeftText(leftCell, "• " + skill);
                }
                leftCell.addParagraph(); 
            }

            List<Map<String, Object>> certs = (List<Map<String, Object>>) data.get("certifications");
            if (certs != null && !certs.isEmpty()) {
                addLeftHeading(leftCell, "Certifications");
                for (Map<String, Object> cert : certs) {
                    XWPFParagraph p = leftCell.addParagraph();
                    XWPFRun r1 = p.createRun();
                    r1.setText(getString(cert, "name", ""));
                    r1.setBold(true);
                    r1.setColor("E2E8F0");
                    r1.setFontSize(10);
                }
                leftCell.addParagraph(); 
            }

            // -- RIGHT CELL --
            String summary = getString(data, "summary", "");
            if (!summary.isEmpty()) {
                addRightHeading(rightCell, "Summary", accent);
                XWPFParagraph para = rightCell.addParagraph();
                XWPFRun run = para.createRun();
                run.setText(stripHtml(summary));
                run.setFontSize(11);
                run.setColor("334155");
                rightCell.addParagraph(); 
            }

            List<Map<String, Object>> experience = (List<Map<String, Object>>) data.get("experience");
            if (experience != null && !experience.isEmpty()) {
                addRightHeading(rightCell, "Experience", accent);
                for (Map<String, Object> exp : experience) {
                    XWPFParagraph header = rightCell.addParagraph();
                    XWPFRun titleRun = header.createRun();
                    titleRun.setText(getString(exp, "jobTitle", ""));
                    titleRun.setBold(true);
                    titleRun.setFontSize(13);
                    titleRun.setColor("1E293B");

                    String company = getString(exp, "company", "");
                    if (!company.isEmpty()) {
                        XWPFRun compRun = header.createRun();
                        compRun.setText(" — " + company);
                        compRun.setBold(true);
                        compRun.setFontSize(12);
                        compRun.setColor("475569");
                    }

                    XWPFParagraph subHeader = rightCell.addParagraph();
                    XWPFRun dateRun = subHeader.createRun();
                    dateRun.setText(getString(exp, "startDate", "") + " – " + getString(exp, "endDate", "Present"));
                    dateRun.setItalic(true);
                    dateRun.setFontSize(10);
                    dateRun.setColor("64748B");

                    for (String line : stripHtml(getString(exp, "description", "")).split("\n")) {
                        if (line.trim().isEmpty()) continue;
                        XWPFParagraph descPara = rightCell.addParagraph();
                        XWPFRun descRun = descPara.createRun();
                        descRun.setText(line.trim().startsWith("•") ? line.trim() : "• " + line.trim());
                        descRun.setFontSize(11);
                        descRun.setColor("334155");
                    }
                    rightCell.addParagraph();
                }
            }

            List<Map<String, Object>> education = (List<Map<String, Object>>) data.get("education");
            if (education != null && !education.isEmpty()) {
                addRightHeading(rightCell, "Education", accent);
                for (Map<String, Object> edu : education) {
                    XWPFParagraph header = rightCell.addParagraph();
                    XWPFRun degreeRun = header.createRun();
                    degreeRun.setText(getString(edu, "degree", ""));
                    degreeRun.setBold(true);
                    degreeRun.setFontSize(12);
                    degreeRun.setColor("1E293B");

                    String field = getString(edu, "fieldOfStudy", "");
                    if (!field.isEmpty()) {
                        XWPFRun fieldRun = header.createRun();
                        fieldRun.setText(" in " + field);
                        fieldRun.setFontSize(12);
                    }

                    XWPFParagraph subHeader = rightCell.addParagraph();
                    XWPFRun metaRun = subHeader.createRun();
                    metaRun.setText(getString(edu, "institution", ""));
                    metaRun.setItalic(true);
                    metaRun.setFontSize(10);
                    metaRun.setColor("64748B");
                    rightCell.addParagraph();
                }
            }

            document.write(baos);
            return baos.toByteArray();
        }
    }

    private void addLeftHeading(XWPFTableCell cell, String title) {
        XWPFParagraph para = cell.addParagraph();
        XWPFRun run = para.createRun();
        run.setText(title.toUpperCase());
        run.setBold(true);
        run.setFontSize(11);
        run.setColor("94A3B8"); 
    }

    private void addLeftText(XWPFTableCell cell, String text) {
        if (text == null || text.trim().isEmpty()) return;
        XWPFParagraph para = cell.addParagraph();
        XWPFRun run = para.createRun();
        run.setText(text.trim());
        run.setFontSize(10);
        run.setColor("FFFFFF"); 
    }

    private void addRightHeading(XWPFTableCell cell, String title, String accentColor) {
        XWPFParagraph para = cell.addParagraph();
        para.setBorderBottom(Borders.SINGLE);
        XWPFRun run = para.createRun();
        run.setText(title.toUpperCase());
        run.setBold(true);
        run.setFontSize(14);
        run.setColor(accentColor);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    private void setTableBordersNone(XWPFTable table) {
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto");
    }

    private void setCellBackground(XWPFTableCell cell, String hexColor) {
        CTTcPr tcPr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setFill(hexColor);
    }

    private String getString(Map<String, Object> map, String key, String def) {
        if (map == null) return def;
        Object val = map.get(key);
        return val == null ? def : val.toString();
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").replaceAll("&nbsp;", " ").replaceAll("&amp;", "&");
    }
}
