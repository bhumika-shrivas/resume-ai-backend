package com.resumeai.jobmatch.service;

import com.resumeai.jobmatch.client.AiClient;
import com.resumeai.jobmatch.client.NotificationClient;
import com.resumeai.jobmatch.entity.JobMatch;
import com.resumeai.jobmatch.repository.JobMatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobMatchServiceImpl implements JobMatchService {

    @Autowired
    private JobMatchRepository jobMatchRepository;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private NotificationClient notificationClient;

    @Value("${linkedin.rapidapi.key:}")
    private String linkedinRapidApiKey;

    @Value("${RAPIDAPI_NAUKRI_KEY:}")
    private String naukriRapidApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @Transactional
    public JobMatch analyzeJobFit(Long resumeId, String userId, String jobTitle, String jobDescription, String source) {
        return analyzeJobFitFull(resumeId, userId, jobTitle, jobDescription, source, null, null, null);
    }

    @Override
    @Transactional
    public JobMatch analyzeJobFitFull(Long resumeId, String userId, String jobTitle, String jobDescription,
                                       String source, String companyName, String location, String jobUrl) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("resumeId", resumeId);
        payload.put("jobDescription", jobDescription);

        Map<String, Object> aiResult;
        try {
            aiResult = aiClient.checkAtsCompatibility(userId, payload);
        } catch (Exception e) {
            aiResult = new HashMap<>();
            System.err.println("AI compatibility check failed: " + e.getMessage());
        }

        JobMatch match = new JobMatch();
        match.setResumeId(resumeId);
        match.setUserId(userId);
        match.setJobTitle(jobTitle);
        match.setJobDescription(jobDescription);
        match.setSource(source != null ? source : "MANUAL");
        match.setCompanyName(companyName);
        match.setLocation(location);
        match.setJobUrl(jobUrl);

        int score = 0;
        Object rawScore = aiResult.get("score");
        if (rawScore instanceof Number) score = ((Number) rawScore).intValue();
        match.setMatchScore(score);

        @SuppressWarnings("unchecked")
        List<String> missingSkills = (List<String>) aiResult.get("missingKeywords");
        if (missingSkills != null) match.setMissingSkills(String.join(",", missingSkills));

        @SuppressWarnings("unchecked")
        List<String> recs = (List<String>) aiResult.get("recommendations");
        if (recs != null) match.setRecommendations(String.join("\n", recs));

        JobMatch savedMatch = jobMatchRepository.save(match);

        try {
            Map<String, Object> notif = new HashMap<>();
            notif.put("recipientId", userId);
            notif.put("title", "Job Match Saved");
            notif.put("message", "\"" + jobTitle + "\" at " + companyName + " was saved to your matches.");
            notif.put("type", "JOB_MATCH");
            notif.put("relatedId", String.valueOf(savedMatch.getMatchId()));
            notif.put("actionUrl", "/app/jobs");
            notificationClient.sendNotification(notif);
        } catch (Exception e) {
            System.err.println("Notification failed: " + e.getMessage());
        }

        return savedMatch;
    }

    @Override
    public List<JobMatch> getMatchesByResume(Long resumeId) {
        return jobMatchRepository.findByResumeId(resumeId);
    }

    @Override
    public List<JobMatch> getMatchesByUser(String userId) {
        return jobMatchRepository.findByUserId(userId);
    }

    @Override
    public Optional<JobMatch> getMatchById(Long matchId) {
        return jobMatchRepository.findByMatchId(matchId);
    }

    @Override
    @Transactional
    public JobMatch bookmarkMatch(Long matchId, boolean isBookmarked) {
        return jobMatchRepository.findById(matchId).map(match -> {
            match.setBookmarked(isBookmarked);
            return jobMatchRepository.save(match);
        }).orElseThrow(() -> new RuntimeException("Match not found: " + matchId));
    }

    @Override
    public List<JobMatch> getBookmarkedMatches(String userId) {
        return jobMatchRepository.findByIsBookmarkedTrueAndUserId(userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LinkedIn Job Search API  (fantastic-jobs on RapidAPI)
    //  Host: linkedin-job-search-api.p.rapidapi.com
    //  Endpoint: GET /active-jb-1h
    //  Response fields: id, title, organization, url, description_text,
    //                   locations_derived (array), date_posted, seniority
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public List<Map<String, Object>> fetchJobsFromLinkedIn(String query, String location) {
        if (linkedinRapidApiKey != null && !linkedinRapidApiKey.isBlank()) {
            try {
                String loc = (location != null && !location.isBlank()) ? location : "India";

                URI uri = UriComponentsBuilder
                        .fromUriString("https://linkedin-job-search-api.p.rapidapi.com/active-jb-1h")
                        .queryParam("title_filter", query)
                        .queryParam("location_filter", loc)
                        .queryParam("description_type", "text")
                        .build().toUri();

                HttpHeaders headers = new HttpHeaders();
                headers.set("x-rapidapi-key", linkedinRapidApiKey);
                headers.set("x-rapidapi-host", "linkedin-job-search-api.p.rapidapi.com");

                System.out.println("Calling LinkedIn API: " + uri);

                ResponseEntity<List> response = restTemplate.exchange(
                        uri, HttpMethod.GET, new HttpEntity<>(headers), List.class);

                System.out.println("LinkedIn API status: " + response.getStatusCode()
                        + " | body size: " + (response.getBody() != null ? response.getBody().size() : 0));

                if (response.getStatusCode() == HttpStatus.OK
                        && response.getBody() != null
                        && !response.getBody().isEmpty()) {
                    List<Map<String, Object>> jobs = parseLinkedInJobs(response.getBody());
                    System.out.println("Parsed " + jobs.size() + " LinkedIn jobs");
                    return jobs;
                }
            } catch (Exception e) {
                System.err.println("LinkedIn API error: " + e.getMessage());
            }
        } else {
            System.out.println("No LinkedIn API key configured — using demo data");
        }

        return buildLinkedInDemoData(query, location);
    }

    @Override
    @Transactional
    public JobMatch saveJobDirectly(String userId, String jobTitle, String jobDescription, String source,
                                     String companyName, String location, String jobUrl, String salary, String postedAt) {
        JobMatch job = new JobMatch();
        job.setUserId(userId);
        job.setResumeId(0L); // no resume required for direct bookmarks
        job.setJobTitle(jobTitle);
        job.setJobDescription(jobDescription != null ? jobDescription : "");
        job.setSource(source != null ? source : "LINKEDIN");
        job.setCompanyName(companyName);
        job.setLocation(location);
        job.setJobUrl(jobUrl);
        job.setSalary(salary);
        job.setPostedAt(postedAt);
        job.setMatchScore(0);
        job.setBookmarked(true); // saved directly as bookmark
        return jobMatchRepository.save(job);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Naukri — since the Naukri Job Market Intelligence API returns analytics
    //  (not job listings), we use the LinkedIn API results for both tabs but
    //  filter/label them as Naukri-style. For production, subscribe to a
    //  real Naukri job listing API.
    // ─────────────────────────────────────────────────────────────────────────
   
    public List<Map<String, Object>> fetchJobsFromNaukri(String query, String location) {
        // Try a different Naukri job search API if key is set
        if (naukriRapidApiKey != null && !naukriRapidApiKey.isBlank()) {
            try {
                // Naukri Job Market Intelligence API (ajayapradhan210 version)
                URI uri = URI.create("https://naukri-job-market-intelligence-api.p.rapidapi.com/naukri/jobs/search");

                HttpHeaders headers = new HttpHeaders();
                headers.set("x-rapidapi-key", naukriRapidApiKey);
                headers.set("x-rapidapi-host", "naukri-job-market-intelligence-api.p.rapidapi.com");
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> body = new HashMap<>();
                body.put("keyword", query);
                body.put("location", location != null && !location.isBlank() ? location : null);

                System.out.println("Calling Naukri API for: " + query + " in " + location);

                ResponseEntity<Map> response = restTemplate.exchange(
                        uri, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);

                System.out.println("Naukri API status: " + response.getStatusCode());

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    // Naukri API returns market intelligence — convert to job listings format
                    // buildNaukriFromMarketData(response.getBody(), query, location);
                }
            } catch (Exception e) {
                System.err.println("Naukri API error: " + e.getMessage());
            }
        }

        // return buildNaukriDemoData(query, location);
        return Collections.emptyList();
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseLinkedInJobs(List<?> rawList) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> raw = (Map<String, Object>) item;

            // organization = company name (plain string in this API)
            String company = safeStr(raw.get("organization"), "Unknown Company");

            // locations_derived = array of location strings
            String location = "Location not specified";
            Object locObj = raw.get("locations_derived");
            if (locObj instanceof List && !((List<?>) locObj).isEmpty()) {
                location = String.valueOf(((List<?>) locObj).get(0));
            }

            // description_text (not description)
            String description = safeStr(raw.get("description_text"),
                    safeStr(raw.get("description"), "No description available."));

            // url = specific job posting URL on LinkedIn
            String url = safeStr(raw.get("url"), "https://www.linkedin.com/jobs/");

            // date_posted
            String postedAt = safeStr(raw.get("date_posted"), "Recently posted");
            if (postedAt.contains("T")) postedAt = postedAt.substring(0, 10); // trim time

            // employment_type
            String type = "";
            Object empType = raw.get("employment_type");
            if (empType instanceof List && !((List<?>) empType).isEmpty()) {
                type = String.valueOf(((List<?>) empType).get(0)).replace("_", " ");
            }

            Map<String, Object> job = new LinkedHashMap<>();
            job.put("title", safeStr(raw.get("title"), "N/A"));
            job.put("company", company);
            job.put("location", location);
            job.put("description", description);
            job.put("url", url);
            job.put("postedAt", postedAt);
            job.put("employmentType", type);
            job.put("seniority", safeStr(raw.get("seniority"), ""));
            job.put("source", "LINKEDIN");
            result.add(job);
        }
        return result;
    }


    // ── Live Public APIs (No API Key Required) Fallbacks ─────────────────────

    private String resolveLocation(String loc) {
        if (loc == null || loc.trim().isEmpty()) return "India";
        String lower = loc.toLowerCase().trim();
        if (lower.equals("banglore") || lower.equals("bengaluru") || lower.equals("bangalore")) return "Bengaluru, India";
        if (lower.equals("delhi") || lower.equals("new delhi") || lower.equals("delhi ncr")) return "Delhi, India";
        if (!loc.contains(",")) {
             List<String> indianCities = Arrays.asList("mumbai", "pune", "hyderabad", "chennai", "kolkata", "gurgaon", "noida", "ahmedabad", "bhopal", "indore", "chandigarh", "jaipur");
             if (indianCities.contains(lower)) return loc + ", India";
        }
        return loc;
    }

    private List<Map<String, Object>> buildLinkedInDemoData(String query, String location) {
        // Scrape real LinkedIn jobs from the public Guest API
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            String q = (query != null && !query.trim().isEmpty()) ? query.trim() : "Software";
            String loc = resolveLocation(location);
            String url = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?keywords=" 
                + java.net.URLEncoder.encode(q, "UTF-8") 
                + "&location=" + java.net.URLEncoder.encode(loc, "UTF-8") + "&start=0";
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            String html = response.getBody();
            
            if (html != null && !html.isBlank()) {
                // Split by job card block to parse each job individually
                String[] cards = html.split("<div class=\"base-card ");
                for (int i = 1; i < cards.length && result.size() < 12; i++) {
                    String card = cards[i];
                    
                    String jobUrl = extractRegex(card, "href=\"([^\"]+)\"");
                    if (jobUrl.contains("?")) jobUrl = jobUrl.substring(0, jobUrl.indexOf("?")); // clean tracking params
                    
                    String title = extractRegex(card, "<h3 class=\"base-search-card__title\">\\s*(.*?)\\s*</h3>");
                    
                    // Company might be in an <a> tag or just text
                    String company = extractRegex(card, "<h4 class=\"base-search-card__subtitle\">\\s*<a[^>]*>\\s*(.*?)\\s*</a>");
                    if (company.isEmpty()) {
                        company = extractRegex(card, "<h4 class=\"base-search-card__subtitle\">\\s*(.*?)\\s*</h4>");
                    }
                    
                    String jobLoc = extractRegex(card, "<span class=\"job-search-card__location\">\\s*(.*?)\\s*</span>");
                    String posted = extractRegex(card, "<time class=\"job-search-card__listdate\"[^>]*>\\s*(.*?)\\s*</time>");
                    if (posted.isEmpty()) {
                        posted = extractRegex(card, "<time class=\"job-search-card__listdate--new\"[^>]*>\\s*(.*?)\\s*</time>");
                    }
                    
                    if (!title.isEmpty() && !company.isEmpty()) {
                        Map<String, Object> job = new LinkedHashMap<>();
                        job.put("title", stripHtml(title));
                        job.put("company", stripHtml(company));
                        job.put("location", stripHtml(jobLoc));
                        job.put("source", "LINKEDIN");
                        job.put("description", "Click View to see the full job description and apply directly on LinkedIn.");
                        job.put("url", jobUrl);
                        job.put("postedAt", posted.isEmpty() ? "Recently" : stripHtml(posted));
                        job.put("employmentType", "Full-time");
                        job.put("salary", "Competitive");
                        result.add(job);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("LinkedIn Guest API Scrape failed: " + e.getMessage());
        }
        
        if (!result.isEmpty()) return result;
        return buildStaticMock(query, location);
    }

    private String extractRegex(String text, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL).matcher(text);
        if (m.find()) return m.group(1).trim();
        return "";
    }


    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }

    private List<Map<String, Object>> buildStaticMock(String query, String location) {
        String q = (query != null && !query.isBlank()) ? query.trim() : "Software Engineer";
        String loc = (location != null && !location.isBlank()) ? location.trim() : "India";

        String searchUrl = "https://www.linkedin.com/jobs/search/?keywords=" + q.replace(" ", "%20") + "&location=" + loc.replace(" ", "%20");

        String title1 = q;
        String title2 = q + " (Remote)";
        String title3 = q.contains("Intern") ? q : "Senior " + q;
        String title4 = q.contains("Intern") ? q : "Lead " + q;

        return Arrays.asList(
            demoJob(title1, "TechCorp", loc, "LINKEDIN", "We are looking for a skilled " + q + " to join our growing team in " + loc + ".", searchUrl, "Full-time", "Competitive"),
            demoJob(title2, "Innovate Solutions", loc, "LINKEDIN", "Join us as a " + q + ". Flexible hours, great benefits, and strong growth opportunities.", searchUrl, "Full-time", "Competitive"),
            demoJob(title3, "Global Systems Inc.", loc, "LINKEDIN", "Hiring a " + title3 + " to work on high-scale distributed systems.", searchUrl, "Full-time", "Competitive"),
            demoJob(title4, "Fintech Next", loc, "LINKEDIN", "We are seeking a " + title4 + " for our core payments infrastructure team.", searchUrl, "Full-time", "Competitive"),
            demoJob(title1, "HealthAI", loc, "LINKEDIN", "Join our healthcare AI startup as a " + q + " to build next-generation predictive models.", searchUrl, "Full-time", "Competitive")
        );
    }

    private Map<String, Object> demoJob(String title, String company, String location, String source,
                                         String description, String url, String type, String salary) {
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("title", title);
        job.put("company", company);
        job.put("location", location);
        job.put("source", source);
        job.put("description", description);
        job.put("url", url);  // real search URL for the query
        job.put("postedAt", "Recently posted");
        job.put("employmentType", type);
        job.put("salary", salary);
        return job;
    }

    private String safeStr(Object obj, String fallback) {
        if (obj == null) return fallback;
        String s = String.valueOf(obj).trim();
        return s.isEmpty() ? fallback : s;
    }

    // ── Other service methods ──────────────────────────────────────────────────

    @Override
    public String getTailoringRecommendations(Long matchId) {
        return jobMatchRepository.findById(matchId).map(match -> {
            if (match.getRecommendations() != null && !match.getRecommendations().isEmpty()) {
                return match.getRecommendations();
            }
            return "No tailoring recommendations available for this match.";
        }).orElse("Match not found.");
    }

    @Override
    @Transactional
    public void deleteMatch(Long matchId) {
        jobMatchRepository.deleteById(matchId);
    }

    @Override
    public List<JobMatch> getTopMatches(String userId, int limit) {
        return jobMatchRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparingInt(JobMatch::getMatchScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
