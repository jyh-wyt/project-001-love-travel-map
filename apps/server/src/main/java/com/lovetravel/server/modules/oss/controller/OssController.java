package com.lovetravel.server.modules.oss.controller;

import com.lovetravel.server.modules.auth.service.AuthSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.lovetravel.server.modules.oss.service.OssService;
import com.lovetravel.server.modules.oss.vo.OssUploadResponse;

@RestController
@RequestMapping("/api/oss")
public class OssController {

    private final OssService ossService;
    private final AuthSessionService authSessionService;

    public OssController(OssService ossService, AuthSessionService authSessionService) {
        this.ossService = ossService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/images")
    public OssUploadResponse uploadImage(
            HttpServletRequest servletRequest,
            @RequestParam("file") MultipartFile file) {
        Long userId = authSessionService.requireCurrentUserId(servletRequest);
        return ossService.uploadImage(userId, file);
    }

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        return ossService.getStatus();
    }
}
