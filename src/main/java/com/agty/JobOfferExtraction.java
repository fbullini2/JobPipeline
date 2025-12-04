package com.agty;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Represents a job offer extraction from an email with all relevant information
 * including application details and source folder
 */
public class JobOfferExtraction {

    // Original email metadata
    @JsonProperty("from")
    private String from;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("sentDate")
    private Long sentDate;

    @JsonProperty("sentDateHumanReadable")
    private String sentDateHumanReadable;  // e.g., "2024-01-15 14:30:00"

    @JsonProperty("source_folder")
    private String sourceFolder;  // e.g., "JobOffers_APEC", "JobOffers_CadreEmploi"

    // Extracted job information
    @JsonProperty("company")
    private String company;

    @JsonProperty("position_title")
    private String positionTitle;

    @JsonProperty("location")
    private String location;

    @JsonProperty("contract_type")
    private String contractType;  // CDI, CDD, Freelance, etc.

    @JsonProperty("salary_range")
    private String salaryRange;

    @JsonProperty("description")
    private String description;

    @JsonProperty("required_skills")
    private List<String> requiredSkills;

    @JsonProperty("experience_level")
    private String experienceLevel;  // Junior, Mid, Senior, etc.

    // Application information
    @JsonProperty("application_url")
    private String applicationUrl;

    @JsonProperty("inPortalJobOfferLink")
    private String inPortalJobOfferLink;  // Clean link to job offer in portal (without tracking params)

    @JsonProperty("application_email")
    private String applicationEmail;

    @JsonProperty("application_instructions")
    private String applicationInstructions;

    @JsonProperty("application_deadline")
    private String applicationDeadline;

    // Multiple positions flag
    @JsonProperty("is_multiple_positions")
    private Boolean isMultiplePositions;

    @JsonProperty("number_of_positions")
    private Integer numberOfPositions;

    @JsonProperty("positions_list")
    private List<String> positionsList;  // If email contains multiple positions

    // Additional metadata
    @JsonProperty("contact_person")
    private String contactPerson;

    @JsonProperty("contact_phone")
    private String contactPhone;

    @JsonProperty("reference_number")
    private String referenceNumber;

    @JsonProperty("extraction_timestamp")
    private Long extractionTimestamp;

    @JsonProperty("extraction_confidence")
    private Double extractionConfidence;  // 0.0 to 1.0

    // Original content for reference
    @JsonProperty("email_content_preview")
    private String emailContentPreview;  // First 500 chars

    // Constructors
    public JobOfferExtraction() {
        this.extractionTimestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Long getSentDate() {
        return sentDate;
    }

    public void setSentDate(Long sentDate) {
        this.sentDate = sentDate;
    }

    public String getSentDateHumanReadable() {
        return sentDateHumanReadable;
    }

    public void setSentDateHumanReadable(String sentDateHumanReadable) {
        this.sentDateHumanReadable = sentDateHumanReadable;
    }

    public String getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(String sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public String getSalaryRange() {
        return salaryRange;
    }

    public void setSalaryRange(String salaryRange) {
        this.salaryRange = salaryRange;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getApplicationUrl() {
        return applicationUrl;
    }

    public void setApplicationUrl(String applicationUrl) {
        this.applicationUrl = applicationUrl;
    }

    public String getInPortalJobOfferLink() {
        return inPortalJobOfferLink;
    }

    public void setInPortalJobOfferLink(String inPortalJobOfferLink) {
        this.inPortalJobOfferLink = inPortalJobOfferLink;
    }

    public String getApplicationEmail() {
        return applicationEmail;
    }

    public void setApplicationEmail(String applicationEmail) {
        this.applicationEmail = applicationEmail;
    }

    public String getApplicationInstructions() {
        return applicationInstructions;
    }

    public void setApplicationInstructions(String applicationInstructions) {
        this.applicationInstructions = applicationInstructions;
    }

    public String getApplicationDeadline() {
        return applicationDeadline;
    }

    public void setApplicationDeadline(String applicationDeadline) {
        this.applicationDeadline = applicationDeadline;
    }

    public Boolean getIsMultiplePositions() {
        return isMultiplePositions;
    }

    public void setIsMultiplePositions(Boolean isMultiplePositions) {
        this.isMultiplePositions = isMultiplePositions;
    }

    public Integer getNumberOfPositions() {
        return numberOfPositions;
    }

    public void setNumberOfPositions(Integer numberOfPositions) {
        this.numberOfPositions = numberOfPositions;
    }

    public List<String> getPositionsList() {
        return positionsList;
    }

    public void setPositionsList(List<String> positionsList) {
        this.positionsList = positionsList;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public Long getExtractionTimestamp() {
        return extractionTimestamp;
    }

    public void setExtractionTimestamp(Long extractionTimestamp) {
        this.extractionTimestamp = extractionTimestamp;
    }

    public Double getExtractionConfidence() {
        return extractionConfidence;
    }

    public void setExtractionConfidence(Double extractionConfidence) {
        this.extractionConfidence = extractionConfidence;
    }

    public String getEmailContentPreview() {
        return emailContentPreview;
    }

    public void setEmailContentPreview(String emailContentPreview) {
        this.emailContentPreview = emailContentPreview;
    }

    @Override
    public String toString() {
        return "JobOfferExtraction{" +
                "sourceFolder='" + sourceFolder + '\'' +
                ", positionTitle='" + positionTitle + '\'' +
                ", company='" + company + '\'' +
                ", location='" + location + '\'' +
                ", applicationUrl='" + applicationUrl + '\'' +
                '}';
    }
}
