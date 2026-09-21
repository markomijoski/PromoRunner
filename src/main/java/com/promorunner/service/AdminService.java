package com.promorunner.service;

import com.promorunner.dto.AnalyticsSummaryDto;
import com.promorunner.dto.AssetUploadResponse;
import com.promorunner.dto.LeadExportDto;
import com.promorunner.model.GameConfig;
import com.promorunner.repository.ScoreRepository;
import com.promorunner.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminService {

    private final GameConfigService gameConfigService;
    private final UserRepository userRepository;
    private final ScoreRepository scoreRepository;
    private final Path uploadRoot;

    public AdminService(
            GameConfigService gameConfigService,
            UserRepository userRepository,
            ScoreRepository scoreRepository,
            @Value("${app.upload-dir:src/main/resources/static/images}") String uploadDir) {
        this.gameConfigService = gameConfigService;
        this.userRepository = userRepository;
        this.scoreRepository = scoreRepository;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public GameConfig getAssetsConfig() {
        return gameConfigService.getConfig();
    }

    public AssetUploadResponse uploadImage(MultipartFile file, String subdirectory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String sub = (subdirectory == null || subdirectory.isBlank()) ? "ui" : subdirectory;
        Path targetDir = uploadRoot.resolve(sub).normalize();
        if (!targetDir.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("Invalid subdirectory");
        }
        Files.createDirectories(targetDir);
        String stored = UUID.randomUUID() + "_" + safeName;
        Path target = targetDir.resolve(stored);
        file.transferTo(target);

        return new AssetUploadResponse("/images/" + sub + "/" + stored);
    }

    @Transactional(readOnly = true)
    public List<LeadExportDto> exportLeads() {
        return userRepository.findByMarketingConsentTrue().stream()
                .map(user -> new LeadExportDto(
                        user.getId(),
                        user.getEmail(),
                        user.getDisplayName()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportLeadsCsv() {
        StringBuilder sb = new StringBuilder("userId,email,displayName\n");
        for (LeadExportDto lead : exportLeads()) {
            sb.append(lead.userId()).append(',')
                    .append(escapeCsv(lead.email())).append(',')
                    .append(escapeCsv(lead.displayName() == null ? "" : lead.displayName())).append('\n');
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDto analytics() {
        return new AnalyticsSummaryDto(
                userRepository.count(),
                userRepository.countByMarketingConsentTrue(),
                scoreRepository.averageBestScore(),
                scoreRepository.sumTotalPlays()
        );
    }

    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
