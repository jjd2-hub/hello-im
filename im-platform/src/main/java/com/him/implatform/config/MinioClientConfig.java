package com.him.implatform.config;

import com.him.implatform.config.props.MinioProperties;
import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioClientConfig {
    @Bean
    public MinioClient minioClient(MinioProperties minioProps) {
        // 注入minio 客户端
        return MinioClient.builder()
                .endpoint(minioProps.getEndpoint())
                .credentials(minioProps.getAccessKey(), minioProps.getSecretKey())
                .build();
    }
}
