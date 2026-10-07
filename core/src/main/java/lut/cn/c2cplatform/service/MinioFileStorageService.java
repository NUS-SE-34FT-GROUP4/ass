package lut.cn.c2cplatform.service;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import lut.cn.c2cplatform.config.MinioProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Service
public class MinioFileStorageService implements FileStorageService {
    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioProperties minioProperties;

    @PostConstruct
    public void init() {
        try {
            String bucketName = minioProperties.getBucketName();
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                // Create the bucket
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

                // Make the bucket publicly readable (anonymous access)
                String policy = "{\n" +
                        "  \"Version\": \"2012-10-17\",\n" +
                        "  \"Statement\": [\n" +
                        "    {\n" +
                        "      \"Effect\": \"Allow\",\n" +
                        "      \"Principal\": {\"AWS\": \"*\"},\n" +
                        "      \"Action\": [\"s3:GetObject\"],\n" +
                        "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(bucketName)
                                .config(policy)
                                .build()
                );
                System.out.println("MinIO bucket '" + bucketName + "' created and set to public read access.");
            } else {
                // Bucket exists; make sure the public access policy is set
                try {
                    String policy = "{\n" +
                            "  \"Version\": \"2012-10-17\",\n" +
                            "  \"Statement\": [\n" +
                            "    {\n" +
                            "      \"Effect\": \"Allow\",\n" +
                            "      \"Principal\": {\"AWS\": \"*\"},\n" +
                            "      \"Action\": [\"s3:GetObject\"],\n" +
                            "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                            "    }\n" +
                            "  ]\n" +
                            "}";
                    minioClient.setBucketPolicy(
                            SetBucketPolicyArgs.builder()
                                    .bucket(bucketName)
                                    .config(policy)
                                    .build()
                    );
                    System.out.println("MinIO bucket '" + bucketName + "' policy updated to public read access.");
                } catch (Exception e) {
                    System.err.println("Warning: Could not set bucket policy: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error while initializing MinIO bucket", e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd/");
        String objectName = sdf.format(new Date()) + UUID.randomUUID() + fileExtension;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        // Build an accessible URL
        // If the endpoint uses the Docker-internal host (minio), replace it with localhost for the browser
        String endpoint = minioProperties.getEndpoint();
        if (endpoint.contains("minio:")) {
            endpoint = endpoint.replace("minio:", "localhost:");
        }

        // Normalise the URL and remove any double slashes
        String url = endpoint + "/" + minioProperties.getBucketName() + "/" + objectName;
        return url.replace("//", "/").replace(":/", "://");
    }

    @Override
    public void deleteFile(String objectName) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(objectName)
                        .build()
        );
    }
}
