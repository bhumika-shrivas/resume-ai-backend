package com.resumeai.export.dto;

import java.io.Serializable;
import java.util.Map;

public class ExportRequest implements Serializable {
    private String jobId;
    private Long resumeId;
    private String userId;
    private String format;
    private Map<String, Object> data;

    public ExportRequest() {}

    public ExportRequest(String jobId, Long resumeId, String userId, String format, Map<String, Object> data) {
        this.jobId = jobId;
        this.resumeId = resumeId;
        this.userId = userId;
        this.format = format;
        this.data = data;
    }

    // Getters and Setters
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
