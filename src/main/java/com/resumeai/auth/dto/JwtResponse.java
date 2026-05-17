package com.resumeai.auth.dto;

public class JwtResponse {
    private String accessToken;
    private String refreshToken;
    private String role;
    private String plan;

    public JwtResponse() {}

    public JwtResponse(String accessToken, String refreshToken, String role, String plan) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.role = role;
        this.plan = plan;
    }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPlan() { return plan; }
    public void setPlan(String plan) { this.plan = plan; }

    public static JwtResponseBuilder builder() {
        return new JwtResponseBuilder();
    }

    public static class JwtResponseBuilder {
        private String accessToken;
        private String refreshToken;
        private String role;
        private String plan;

        public JwtResponseBuilder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public JwtResponseBuilder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public JwtResponseBuilder role(String role) {
            this.role = role;
            return this;
        }

        public JwtResponseBuilder plan(String plan) {
            this.plan = plan;
            return this;
        }

        public JwtResponse build() {
            return new JwtResponse(accessToken, refreshToken, role, plan);
        }
    }
}
