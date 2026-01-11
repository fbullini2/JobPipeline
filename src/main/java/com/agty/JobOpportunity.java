package com.agty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.logging.Logger;

/**
 * Entity representing a structured job opportunity extracted from an email.
 * This is the final structured representation of a job offer.
 */
public class JobOpportunity {
    private static final Logger logger = Logger.getLogger(JobOpportunity.class.getName());

    private String title;

    // Job portal information
    @JsonProperty("job_portal_name")
    private String jobPortalName;  // "Cadremploi", "LinkedIn", "Indeed", "Apec", "HelloWork", etc.

    // URL fields - categorized by purpose and source
    @JsonProperty("job_offer_url_apply_portal")
    private String jobOfferURLForApplyOnJobPortal;

    @JsonProperty("job_offer_url_apply_company")
    private String jobOfferURLForApplyOnCompanySite;

    @JsonProperty("job_offer_url_description_portal")
    private String jobOfferURLForDescriptionOnJobPortal;

    @JsonProperty("job_offer_url_description_company")
    private String jobOfferURLForDescriptionOnCompanySite;

    // URL reference type - indicates if URL is a final direct link or expired job reference
    @JsonProperty("url_reference_type")
    private String urlReferenceType;  // "DIRECT" (default) or "NOT_FINAL_REFERENCE" (expired job)

    private String company;

    @JsonProperty("fit_score")
    private Double fitScore;

    private String location;
    private String salary;
    private String responsibilities;

    @JsonProperty("skills_required")
    private String skillsRequired;

    private String compensation;

    @JsonProperty("employment_type")
    private String employmentType;  // 'freelance' or 'employee'

    @JsonProperty("contract_type")
    private String contractType;    // 'permanent' or 'temporary'

    @JsonProperty("is_startup")
    private Boolean isStartup;

    @JsonProperty("company_size")
    private String companySize;     // '1-10', '11-50', '51-200', '201-1000', '1000+', etc.

    @JsonProperty("team_size_to_manage")
    private String teamSizeToManage;

    @JsonProperty("additional_experience")
    private String additionalExperience;

    @JsonProperty("work_languages")
    private String workLanguages;

    // Reference to original email
    @JsonProperty("source_email_subject")
    private String sourceEmailSubject;

    @JsonProperty("source_email_from")
    private String sourceEmailFrom;

    @JsonProperty("source_email_date")
    private String sourceEmailDate;

    // Constructors
    public JobOpportunity() {}

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * DEPRECATED: For backward compatibility only.
     * Returns the first available URL in order of preference.
     * This method logs a warning when called.
     *
     * @deprecated Use specific URL getters instead:
     *             getJobOfferURLForApplyOnJobPortal(),
     *             getJobOfferURLForApplyOnCompanySite(),
     *             getJobOfferURLForDescriptionOnJobPortal(),
     *             getJobOfferURLForDescriptionOnCompanySite()
     */
    @Deprecated
    @JsonIgnore  // Prevent Jackson from serializing this deprecated field
    public String getLink() {
        logger.warning("getLink() is DEPRECATED - using backward compatibility mode. " +
                       "Please use specific URL getters instead.");
        // Return first non-null URL in priority order
        if (jobOfferURLForApplyOnJobPortal != null) return jobOfferURLForApplyOnJobPortal;
        if (jobOfferURLForApplyOnCompanySite != null) return jobOfferURLForApplyOnCompanySite;
        if (jobOfferURLForDescriptionOnJobPortal != null) return jobOfferURLForDescriptionOnJobPortal;
        if (jobOfferURLForDescriptionOnCompanySite != null) return jobOfferURLForDescriptionOnCompanySite;
        return null;
    }

    /**
     * DEPRECATED: For backward compatibility only.
     * Sets the description URL on portal by default.
     *
     * @deprecated Use specific URL setters instead
     */
    @Deprecated
    @JsonIgnore  // Prevent Jackson from deserializing this deprecated field
    public void setLink(String link) {
        logger.warning("setLink() is DEPRECATED - using backward compatibility mode. " +
                       "Setting jobOfferURLForDescriptionOnJobPortal instead.");
        this.jobOfferURLForDescriptionOnJobPortal = link;
    }

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

    public String getUrlReferenceType() {
        return urlReferenceType;
    }

    public void setUrlReferenceType(String urlReferenceType) {
        this.urlReferenceType = urlReferenceType;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public Double getFitScore() {
        return fitScore;
    }

    public void setFitScore(Double fitScore) {
        this.fitScore = fitScore;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public String getResponsibilities() {
        return responsibilities;
    }

    public void setResponsibilities(String responsibilities) {
        this.responsibilities = responsibilities;
    }

    public String getSkillsRequired() {
        return skillsRequired;
    }

    public void setSkillsRequired(String skillsRequired) {
        this.skillsRequired = skillsRequired;
    }

    public String getCompensation() {
        return compensation;
    }

    public void setCompensation(String compensation) {
        this.compensation = compensation;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(String contractType) {
        this.contractType = contractType;
    }

    public Boolean getIsStartup() {
        return isStartup;
    }

    public void setIsStartup(Boolean isStartup) {
        this.isStartup = isStartup;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public String getTeamSizeToManage() {
        return teamSizeToManage;
    }

    public void setTeamSizeToManage(String teamSizeToManage) {
        this.teamSizeToManage = teamSizeToManage;
    }

    public String getAdditionalExperience() {
        return additionalExperience;
    }

    public void setAdditionalExperience(String additionalExperience) {
        this.additionalExperience = additionalExperience;
    }

    public String getWorkLanguages() {
        return workLanguages;
    }

    public void setWorkLanguages(String workLanguages) {
        this.workLanguages = workLanguages;
    }

    public String getSourceEmailSubject() {
        return sourceEmailSubject;
    }

    public void setSourceEmailSubject(String sourceEmailSubject) {
        this.sourceEmailSubject = sourceEmailSubject;
    }

    public String getSourceEmailFrom() {
        return sourceEmailFrom;
    }

    public void setSourceEmailFrom(String sourceEmailFrom) {
        this.sourceEmailFrom = sourceEmailFrom;
    }

    public String getSourceEmailDate() {
        return sourceEmailDate;
    }

    public void setSourceEmailDate(String sourceEmailDate) {
        this.sourceEmailDate = sourceEmailDate;
    }

    @Override
    public String toString() {
        return String.format("JobOpportunity{title='%s', company='%s', location='%s', portal='%s', fitScore=%.2f}",
                title, company, location, jobPortalName, fitScore);
    }
}
