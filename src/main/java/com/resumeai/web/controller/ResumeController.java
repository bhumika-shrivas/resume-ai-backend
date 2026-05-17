package com.resumeai.web.controller;

import com.resumeai.web.client.ResumeClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/web/resumes")
public class ResumeController {

    @Autowired
    private ResumeClient resumeClient;

    @GetMapping("/home")
    public String home() {
        return "home";
    }

    @GetMapping("/dashboard")
    public ModelAndView viewDashboard(@RequestHeader("X-Auth-User") String userId) {
        ModelAndView mav = new ModelAndView("dashboard");
        List<Map<String, Object>> resumes = resumeClient.getResumes(userId);
        mav.addObject("resumes", resumes);
        return mav;
    }

    @GetMapping("/create")
    public String createResumePage(Model model) {
        return "create-resume";
    }

    @PostMapping("/create")
    public String createResume(@RequestHeader("X-Auth-User") String userId, @RequestParam String title) {
        // Implementation logic
        return "redirect:/web/resumes/dashboard";
    }

    @GetMapping("/edit/{id}")
    public String editResume(@PathVariable Long id, Model model) {
        Map<String, Object> resume = resumeClient.getResume(id);
        model.addAttribute("resume", resume);
        return "builder";
    }
}
