package com.resumeai.web.controller;

import com.resumeai.web.client.AiClient;
import com.resumeai.web.client.JobMatchClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

@Controller
@RequestMapping("/web/builder")
public class BuilderController {

    @Autowired
    private AiClient aiClient;

    @Autowired
    private JobMatchClient jobMatchClient;

    @GetMapping("/{resumeId}")
    public ModelAndView openBuilder(@PathVariable Long resumeId) {
        ModelAndView mav = new ModelAndView("builder");
        mav.addObject("resumeId", resumeId);
        return mav;
    }

    @PostMapping("/analyze-fit")
    @ResponseBody
    public Map<String, Object> analyzeJobFit(@RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> payload) {
        return jobMatchClient.analyzeFit(userId, payload);
    }

    @PostMapping("/generate-content")
    @ResponseBody
    public String generateAiContent(@RequestHeader("X-Auth-User") String userId, @RequestBody Map<String, Object> payload) {
        return aiClient.generateSummary(userId, payload);
    }
}
