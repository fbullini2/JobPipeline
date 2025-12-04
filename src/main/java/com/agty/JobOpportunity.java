package com.agty;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Entity representing a structured job opportunity extracted from an email.
 * This is the final structured representation of a job offer.
 */
public class JobOpportunity {
    private String title;
    private String link;
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

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
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
        return String.format("JobOpportunity{title='%s', company='%s', location='%s', fitScore=%.2f}",
                title, company, location, fitScore);
    }
}
