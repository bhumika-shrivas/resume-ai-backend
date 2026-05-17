package com.resumeai.template.controller;

import com.resumeai.template.dto.TemplateCreateRequest;
import com.resumeai.template.dto.TemplateResponseDTO;
import com.resumeai.template.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
@Tag(name = "Template Controller", description = "Endpoints for managing Resume Templates (Metadata only)")
public class TemplateController {

    @Autowired
    private TemplateService templateService;

    @Operation(summary = "Get all active templates")
    @GetMapping
    public ResponseEntity<List<TemplateResponseDTO>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    @Operation(summary = "Get single template by ID")
    @GetMapping("/{id}")
    public ResponseEntity<TemplateResponseDTO> getTemplateById(@PathVariable String id) {
        return templateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get templates by category")
    @GetMapping("/category/{category}")
    public ResponseEntity<List<TemplateResponseDTO>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(templateService.getByCategory(category));
    }

    @Operation(summary = "Get free templates")
    @GetMapping("/free")
    public ResponseEntity<List<TemplateResponseDTO>> getFreeTemplates() {
        return ResponseEntity.ok(templateService.getFreeTemplates());
    }

    @Operation(summary = "Get premium templates")
    @GetMapping("/premium")
    public ResponseEntity<List<TemplateResponseDTO>> getPremiumTemplates() {
        return ResponseEntity.ok(templateService.getPremiumTemplates());
    }

    @Operation(summary = "Get popular templates")
    @GetMapping("/popular")
    public ResponseEntity<List<TemplateResponseDTO>> getPopularTemplates() {
        return ResponseEntity.ok(templateService.getPopularTemplates());
    }

    @Operation(summary = "Create a new template")
    @PostMapping
    public ResponseEntity<TemplateResponseDTO> createTemplate(@RequestBody TemplateCreateRequest request) {
        return ResponseEntity.ok(templateService.createTemplate(request));
    }

    @Operation(summary = "Update an existing template")
    @PutMapping("/{id}")
    public ResponseEntity<TemplateResponseDTO> updateTemplate(
            @PathVariable String id,
            @RequestBody TemplateCreateRequest request) {
        try {
            return ResponseEntity.ok(templateService.updateTemplate(id, request));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Deactivate a template")
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateTemplate(@PathVariable String id) {
        templateService.deactivateTemplate(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Increment usage count for a template")
    @PutMapping("/{id}/increment-usage")
    public ResponseEntity<Void> incrementUsage(@PathVariable String id) {
        templateService.incrementUsage(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Increment usage count for a template (alias)")
    @PostMapping("/{id}/usage")
    public ResponseEntity<Void> incrementUsageAlias(@PathVariable String id) {
        templateService.incrementUsage(id);
        return ResponseEntity.ok().build();
    }
}
