package com.promorunner.controller;

import com.promorunner.dto.AnalyticsSummaryDto;
import com.promorunner.dto.AssetUploadResponse;
import com.promorunner.dto.LeadExportDto;
import com.promorunner.model.GameConfig;
import com.promorunner.service.AdminService;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/api")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/assets")
    public GameConfig assets() {
        return adminService.getAssetsConfig();
    }

    @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetUploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "subdirectory", required = false, defaultValue = "ui") String subdirectory)
            throws IOException {
        return adminService.uploadImage(file, subdirectory);
    }

    @GetMapping("/leads")
    public ResponseEntity<?> leads(@RequestParam(value = "format", required = false) String format) {
        if ("csv".equalsIgnoreCase(format)) {
            String csv = adminService.exportLeadsCsv();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leads.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        }
        List<LeadExportDto> leads = adminService.exportLeads();
        return ResponseEntity.ok(leads);
    }

    @GetMapping("/analytics")
    public AnalyticsSummaryDto analytics() {
        return adminService.analytics();
    }
}
