## Deploy the ImageOptimiser in AWS Lambda

**Step A: Build the package**

Run the following Maven command in your terminal. This generates a `s3-image-optimizer-1.0-SNAPSHOT.jar` inside your `target/` directory.

**bash**

```
mvn clean package
```

Use code with caution.

**Step B: Set up S3 Buckets**

Create **two separate buckets** inside your AWS Console (using the same AWS Region):

1. `my-insurance-claims-raw`
2. `my-insurance-claims-optimized`

**Step C: Create the Lambda Function**

1. Go to the AWS Lambda Console and click **Create Function**.
2. Select **Author from scratch**, set the runtime to **Java 17**, and give it a name (e.g., `ImageOptimizer`).
3. Click **Upload from** -> **.zip or .jar file** and upload your compiled `s3-image-optimizer-1.0-SNAPSHOT.jar`.
4. Under **Runtime settings**, change the **Handler** text box to your package path:`com.insurance.lambda.ImageOptimizerHandler::handleRequest`
5. Go to Configuration -> **Environment variables** and add a variable named `DESTINATION_BUCKET` with your optimized bucket name as the value (`my-insurance-claims-optimized`).
6. Give the Lambda function's IAM execution role permissions to `s3:GetObject` on the raw bucket and `s3:PutObject` on the optimized bucket.

**Step D: Connect the S3 Trigger**

1. In the Lambda visual designer panel, click **Add trigger**.
2. Select **S3** from the dropdown menu.
3. Choose your source bucket (`my-insurance-claims-raw`).
4. Set the event type to **All object create events**.
5. *(Optional)* Add a suffix filter like `.jpg` or `.png` so it only runs for images.
6. Click **Add**.

**Step E: Increase Allocated Memory**

Because Java uses a significant amount of memory just to load the JVM and the image bytes into an array, increasing the memory allocation will automatically grant your function more CPU power.

1. In that same **General configuration** edit screen, look at the **Memory** slider.
2. Increase the memory from `512 MB` to **`1024 MB` or `2048 MB`**.
3. Click **Save**.

---

**🧪 How to Test It**

Upload any high-resolution `.jpg` image into `my-insurance-claims-raw`. Within seconds, look into `my-insurance-claims-optimized`—you will see a drastically smaller, optimized version prefixed with `optimized-`. Check the Lambda **CloudWatch Logs** to view your print statements and see how the execution functions
