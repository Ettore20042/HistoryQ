package com.example.historyq.dto;

public class OcrJobResultResponse {
    private String jobId;
    private String status;
    private String result;
    private String error;

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
}