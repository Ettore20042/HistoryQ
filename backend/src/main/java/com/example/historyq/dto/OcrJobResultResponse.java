package com.example.historyq.dto;

import java.util.List;

public class OcrJobResultResponse {
    private String jobId;
    private String status;
    private String result;
    private String error;
    private List<PageResult> pages;

    public static class PageResult {
        private String filename;
        private String text;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public String getJobId() {
        return jobId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public List<PageResult> getPages() {
        return pages;
    }

    public void setPages(List<PageResult> pages) {
        this.pages = pages;
    }
}