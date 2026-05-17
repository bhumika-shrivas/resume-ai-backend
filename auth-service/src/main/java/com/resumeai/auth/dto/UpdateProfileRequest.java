package com.resumeai.auth.dto;

public class UpdateProfileRequest {
    private String fullName;
    private String phone;
    private String headline;
    private String about;

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getAbout() { return about; }
    public void setAbout(String about) { this.about = about; }
}
