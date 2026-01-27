package com.matchimban.matchimban_api.global.health;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api")
@Tag(name = "Health", description = "헬스 체크 API")
public class HealthController {

    @GetMapping("/ping")
    @Operation(summary = "헬스 체크", description = "서버가 살아있는지 확인한다.")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }
}
