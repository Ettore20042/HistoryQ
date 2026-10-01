package com.example.historyq.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.messages.Item;

@Service
public class DocumentStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public DocumentStorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }
    /**
     * Uploads a file to the Minio storage.
     *
     * @param file The file to upload.
     * @param objectName The name of the object in the storage.
     * @throws Exception If an error occurs during the upload.
     * MultipartFile is a Spring Framework interface that represents an uploaded file received in a multipart request. It provides methods to access the file's content, name, size, and other metadata. In this context, it is used to handle the file being uploaded to the Minio storage.
     *
     */
    public void upload(MultipartFile file, String objectName) throws Exception {


            try (InputStream inputStream = file.getInputStream()) {

                minioClient.putObject(
                                PutObjectArgs.builder()
                                        .bucket(bucket)
                                        .object(objectName)
                                        .stream(
                                            inputStream, // InputStream representing the file's content
                                            file.getSize(), // The size of the file in bytes
                                              -1 // The part size for multipart uploads. -1 means the default part size will be used.
                            )
                            .contentType(file.getContentType())
                            .build()
            );

        }
    }

    public void delete(String objectName) throws Exception {

        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );
    }
    public void listObjects() throws Exception {

        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .recursive(true)
                        .build()
        );

        System.out.println("=== OGGETTI NEL BUCKET ===");

        for (Result<Item> result : results) {
            Item item = result.get();
            System.out.println(item.objectName());
        }

        System.out.println("=== FINE ===");
    }
}