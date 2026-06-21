package com.ichi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class MultipartUploadConfigTest {

    @Test
    void applicationDefinesPracticalMultipartUploadLimits() throws IOException {
        PropertySource<?> source = new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"))
            .getFirst();

        assertThat(source.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("${MAX_UPLOAD_FILE_SIZE:25MB}");
        assertThat(source.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("${MAX_UPLOAD_REQUEST_SIZE:30MB}");
    }

    @Test
    void nginxEdgeAcceptsSameUploadEnvelope() throws IOException {
        String nginx = new String(
            java.nio.file.Files.readAllBytes(java.nio.file.Path.of("../deploy/nginx/api-edge.conf")));

        assertThat(nginx).contains("client_max_body_size 30m;");
    }
}
