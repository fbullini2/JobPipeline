package com.agty.urlextractor;

/**
 * Container class for URL extraction results.
 * Holds the extracted URLs categorized by type and source.
 */
public class URLExtractionResult {
    private String jobPortalName;
    private String jobOfferURLForApplyOnJobPortal;
    private String jobOfferURLForApplyOnCompanySite;
    private String jobOfferURLForDescriptionOnJobPortal;
    private String jobOfferURLForDescriptionOnCompanySite;
    private boolean extractionSuccess;
    private String extractionMethod;  // "REGEX" or "LLM"
    private String errorMessage;

    public URLExtractionResult() {
        this.extractionSuccess = false;
    }

    // Getters and Setters
    public String getJobPortalName() {
        return jobPortalName;
    }

    public void setJobPortalName(String jobPortalName) {
        this.jobPortalName = jobPortalName;
    }

    public String getJobOfferURLForApplyOnJobPortal() {
        return jobOfferURLForApplyOnJobPortal;
    }

    public void setJobOfferURLForApplyOnJobPortal(String jobOfferURLForApplyOnJobPortal) {
        this.jobOfferURLForApplyOnJobPortal = jobOfferURLForApplyOnJobPortal;
    }

    public String getJobOfferURLForApplyOnCompanySite() {
        return jobOfferURLForApplyOnCompanySite;
    }

    public void setJobOfferURLForApplyOnCompanySite(String jobOfferURLForApplyOnCompanySite) {
        this.jobOfferURLForApplyOnCompanySite = jobOfferURLForApplyOnCompanySite;
    }

    public String getJobOfferURLForDescriptionOnJobPortal() {
        return jobOfferURLForDescriptionOnJobPortal;
    }

    public void setJobOfferURLForDescriptionOnJobPortal(String jobOfferURLForDescriptionOnJobPortal) {
        this.jobOfferURLForDescriptionOnJobPortal = jobOfferURLForDescriptionOnJobPortal;
    }

    public String getJobOfferURLForDescriptionOnCompanySite() {
        return jobOfferURLForDescriptionOnCompanySite;
    }

    public void setJobOfferURLForDescriptionOnCompanySite(String jobOfferURLForDescriptionOnCompanySite) {
        this.jobOfferURLForDescriptionOnCompanySite = jobOfferURLForDescriptionOnCompanySite;
    }

    public boolean isExtractionSuccess() {
        return extractionSuccess;
    }

    public void setExtractionSuccess(boolean extractionSuccess) {
        this.extractionSuccess = extractionSuccess;
    }

    public String getExtractionMethod() {
        return extractionMethod;
    }

    public void setExtractionMethod(String extractionMethod) {
        this.extractionMethod = extractionMethod;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Check if this result has any URLs extracted
     */
    public boolean hasAnyURL() {
        return jobOfferURLForApplyOnJobPortal != null ||
               jobOfferURLForApplyOnCompanySite != null ||
               jobOfferURLForDescriptionOnJobPortal != null ||
               jobOfferURLForDescriptionOnCompanySite != null;
    }

    @Override
    public String toString() {
        return String.format("URLExtractionResult{portal='%s', method='%s', success=%s, hasURLs=%s}",
                jobPortalName, extractionMethod, extractionSuccess, hasAnyURL());
    }
}
