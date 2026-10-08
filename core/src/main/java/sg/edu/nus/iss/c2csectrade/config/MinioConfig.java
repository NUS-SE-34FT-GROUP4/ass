package sg.edu.nus.iss.c2csectrade.config;

import io.minio.MinioClient;
import io.minio.credentials.IamAwsProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MinioConfig {
    @Autowired
    private MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        MinioClient.Builder builder = MinioClient.builder().endpoint(minioProperties.getEndpoint());
        if (StringUtils.hasText(minioProperties.getRegion())) {
            builder.region(minioProperties.getRegion());
        }
        if (StringUtils.hasText(minioProperties.getAccessKey())) {
            // Local MinIO: static keys from configuration
            return builder.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey()).build();
        }
        // On AWS (S3 behind the same client): no keys are configured, so use the
        // ECS task role's temporary credentials.
        return builder.credentialsProvider(new IamAwsProvider(null, null)).build();
    }
}
