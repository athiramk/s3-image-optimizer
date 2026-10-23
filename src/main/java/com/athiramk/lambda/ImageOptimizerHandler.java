package com.athiramk.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import net.coobird.thumbnailator.Thumbnails;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ImageOptimizerHandler implements RequestHandler<S3Event, String> {

    // Initialize S3 Client outside the handleRequest method to enable connection reuse across warm invocations
    private final S3Client s3Client;
    
    // In a real environment, configure this via Lambda Environment Variables
    private final String DESTINATION_BUCKET = System.getenv("DESTINATION_BUCKET");

    // Standard constructor used by AWS Lambda at runtime
    public ImageOptimizerHandler() {
        this.s3Client = S3Client.builder().build();
    }

    // Overloaded constructor used exclusively by our JUnit test to inject a mock
    ImageOptimizerHandler(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public String handleRequest(S3Event s3Event, Context context) {
        var logger = context.getLogger();

        try {
            // 1. Extract bucket name and object key from the event payload
            S3EventNotificationRecord record = s3Event.getRecords().get(0);
            String sourceBucket = record.getS3().getBucket().getName();
            
            // S3 object keys can contain spaces or special characters encoded as URL characters
            String sourceKey = URLDecoder.decode(record.getS3().getObject().getUrlDecodedKey(), StandardCharsets.UTF_8);
            
            logger.log("Processing file: " + sourceKey + " from bucket: " + sourceBucket);

            // 2. Download the original image from the source S3 bucket
            InputStream s3ObjectStream = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(sourceBucket)
                    .key(sourceKey)
                    .build());

            // 3. Compress and resize the image using Thumbnailator in memory
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(s3ObjectStream)
                    .size(800, 800)       // Resize limit bounding box to 800x800 maximum dimensions
                    .outputQuality(0.75)  // Reduce quality to 75% for massive storage optimization
                    .toOutputStream(outputStream);
            
            byte[] optimizedData = outputStream.toByteArray();
            logger.log("Optimization successful. New size in bytes: " + optimizedData.length);

            // 4. Upload the optimized version to the destination bucket
            String targetBucket = (DESTINATION_BUCKET != null) ? DESTINATION_BUCKET : sourceBucket + "-optimized";
            
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key("optimized-" + sourceKey)
                    .contentType("image/jpeg")
                    .build(), 
                    RequestBody.fromBytes(optimizedData));

            logger.log("Successfully saved optimized image to: " + targetBucket);
            return "SUCCESS";

        } catch (Exception e) {
            logger.log("ERROR processing image: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
