package com.resumeai.export.service;

import com.itextpdf.html2pdf.HtmlConverter;
import com.resumeai.export.dto.ExportRequest;
import com.resumeai.export.entity.ExportJob;
import com.resumeai.export.repository.ExportRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

@Service
public class ExportListener {

    @Autowired
    private ExportRepository exportRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private com.resumeai.export.client.NotificationClient notificationClient;

    @RabbitListener(queues = "${export.queue.name}")
    public void processExport(ExportRequest request) {
        ExportJob job = exportRepository.findById(request.getJobId()).orElse(null);
        if (job == null) return;

        try {
            job.setStatus("PROCESSING");
            exportRepository.save(job);

            byte[] content;
            String contentType;
            String extension;

            switch (request.getFormat().toUpperCase()) {
                case "PDF":
                    content = generatePdf(request.getData());
                    contentType = "application/pdf";
                    extension = "pdf";
                    break;
                case "DOCX":
                    content = generateDocx(request.getData());
                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    extension = "docx";
                    break;
                case "JSON":
                    content = generateJson(request.getData());
                    contentType = "application/json";
                    extension = "json";
                    break;
                default:
                    throw new RuntimeException("Unsupported format: " + request.getFormat());
            }

            String subPath = "resumes/" + job.getUserId();
            String fileName = job.getJobId() + "." + extension;
            String relativePath = fileStorageService.saveFile(subPath, fileName, content);

            job.setStatus("COMPLETED");
            job.setFileUrl(relativePath);
            job.setFileSizeKb((long) (content.length / 1024));
            job.setCompletedAt(LocalDateTime.now());
            job.setExpiresAt(LocalDateTime.now().plusDays(7));
            exportRepository.save(job);

            // Send notification (non-blocking, don't fail the job if notification fails)
            try {
                java.util.Map<String, Object> notif = new java.util.HashMap<>();
                notif.put("recipientId", job.getUserId());
                notif.put("title", "Export Ready");
                notif.put("message", "Your resume export (" + job.getFormat() + ") is ready for download.");
                notif.put("type", "EXPORT_READY");
                notif.put("relatedId", job.getJobId());
                notif.put("actionUrl", "/app/exports");
                notificationClient.sendNotification(notif);
            } catch (Exception ne) {
                System.err.println("Failed to send notification for job " + job.getJobId() + ": " + ne.getMessage());
                // Job is still COMPLETED even if notification fails
            }

        } catch (Exception e) {
            System.err.println("Export processing failed for job " + request.getJobId());
            e.printStackTrace();
            job.setStatus("FAILED");
            exportRepository.save(job);
        }
    }

    private byte[] generatePdf(java.util.Map<String, Object> data) throws Exception {
        String html = (String) data.getOrDefault("html", "<html><body>No Content</body></html>");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            HtmlConverter.convertToPdf(html, baos);
            return baos.toByteArray();
        }
    }

    private byte[] generateDocx(java.util.Map<String, Object> data) throws Exception {
        String content = (String) data.getOrDefault("text", "No Content");
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(content);
            document.write(baos);
            return baos.toByteArray();
        }
    }

    private byte[] generateJson(java.util.Map<String, Object> data) throws Exception {
        // Simple serialization - in a real app use Jackson ObjectMapper
        return data.toString().getBytes();
    }
}
