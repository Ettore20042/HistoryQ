package com.example.historyq.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(MinioBucketInitializer.class);

    private final MinioClient minioClient;
    private final String bucket;

    public MinioBucketInitializer(
            MinioClient minioClient,
            @Value("${minio.bucket}") String bucket) {

        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            if (bucketExists) {
                logger.info("Silo bucket '{}' already exists.", bucket);
                return;
            }

            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket)
                            .build()
            );

            logger.info("Silo bucket '{}' created successfully.", bucket);

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to initialize Silo bucket '" + bucket + "'.",
                    e
            );
        }
    }
}
