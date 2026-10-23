package com.athiramk.lambda;

//import static org.junit.jupiter.api.Assertions.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3Entity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.S3ObjectEntity;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification.UserIdentityEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
//import org.mockito.Mockito;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageOptimizerHandlerTest {

    private S3Client mockS3Client;
    private Context mockContext;
    private LambdaLogger mockLogger;
    private ImageOptimizerHandler handler;

    // A valid 1x1 base64 transparent PNG byte array to feed into Thumbnailator safely
    private final byte[] sampleImageBytes = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    @BeforeEach
    void setUp() {
        mockS3Client = mock(S3Client.class);
        mockContext = mock(Context.class);
        mockLogger = mock(LambdaLogger.class);

        // Instruct the context to return our mock logger when called
        when(mockContext.getLogger()).thenReturn(mockLogger);

        // Inject the mocked client into our handler
        handler = new ImageOptimizerHandler(mockS3Client);
    }

    @Test
    void testHandleRequest_SuccessfulOptimization() {
        // 1. Mock the S3 Download Response
        GetObjectResponse getResponse = GetObjectResponse.builder().contentType("image/png").build();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(sampleImageBytes);

        // Wrap our stream inside the special ResponseInputStream that AWS SDK v2 expects
        ResponseInputStream<GetObjectResponse> s3ResponseStream = new ResponseInputStream<>(getResponse, inputStream);

        when(mockS3Client.getObject(any(GetObjectRequest.class))).thenReturn(s3ResponseStream);

        // 2. Build a Mock S3 Event payload simulating an upload
        S3ObjectEntity s3Object = new S3ObjectEntity("accident-claim-001.png", 100L, null, null, null);
//        S3EventNotification.BucketEntity s3Bucket = new S3EventNotification.BucketEntity("my-insurance-claims-raw", null, null);
        S3EventNotification.S3BucketEntity s3Bucket = new S3EventNotification.S3BucketEntity("my-insurance-claims-raw", null, null);
        S3Entity s3Entity = new S3Entity(null, s3Bucket, s3Object, null);

        S3EventNotificationRecord record = new S3EventNotificationRecord(
                "us-east-1",
                "ObjectCreated:Put",
                "aws:s3",
                "1970-01-01T00:00:00.000Z", // <-- The missing argument (eventTime)
                "2.1",
                null, // requestParameters
                null, // responseElements
                s3Entity,
                new UserIdentityEntity("principalId")
        );
        S3Event s3Event = new S3Event(Collections.singletonList(record));

        // 3. Execute the Lambda handler
        String result = handler.handleRequest(s3Event, mockContext);

        // 4. Assertions
        assertEquals("SUCCESS", result);

        // Verify that getObject was called exactly once with our raw bucket parameters
        ArgumentCaptor<GetObjectRequest> getCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(mockS3Client, times(1)).getObject(getCaptor.capture());
        assertEquals("my-insurance-claims-raw", getCaptor.getValue().bucket());
        assertEquals("accident-claim-001.png", getCaptor.getValue().key());

        // Verify that putObject was called to save the optimized file back to the destination bucket
        ArgumentCaptor<PutObjectRequest> putCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(mockS3Client, times(1)).putObject(putCaptor.capture(), any(RequestBody.class));

        // Default target bucket format logic check (sourceBucket + "-optimized")
        assertEquals("my-insurance-claims-raw-optimized", putCaptor.getValue().bucket());
        assertEquals("optimized-accident-claim-001.png", putCaptor.getValue().key());
    }
}
