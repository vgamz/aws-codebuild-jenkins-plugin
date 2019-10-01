/*
 *  Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with the License.
 *     A copy of the License is located at
 *
 *         http://aws.amazon.com/apache2.0/
 *
 *     or in the "license" file accompanying this file.
 *     This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and limitations under the License.
 *
 *  Portions copyright Copyright 2004-2011 Oracle Corporation. Copyright (C) 2015 The Project Lombok Authors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import hudson.FilePath;
import hudson.model.TaskListener;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.Charsets;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.apache.commons.codec.binary.Base64.encodeBase64;

@RequiredArgsConstructor
public class S3DataManager {

    private final AmazonS3Client s3Client;
    private final String s3InputBucket;
    private final String s3InputKey;
    private final String sseAlgorithm;
    private final String localSourcePath;
    private final String workspaceSubdir;

    // if localSourcePath is empty, clones, zips, and uploads the given workspace. Otherwise, uploads the file referred to by localSourcePath.
    // The upload bucket used is this.s3InputBucket and the name of the zip file is this.s3InputKey.
    public UploadToS3Output uploadSourceToS3(TaskListener listener, FilePath workspace) throws Exception {
        CodeBuilderValidation.checkS3SourceUploaderConfig(workspace, s3Client, localSourcePath, workspaceSubdir);

        FilePath localFile;
        String zipFileMD5;
        ObjectMetadata objectMetadata = new ObjectMetadata();

        if(localSourcePath != null && !localSourcePath.isEmpty()) {
            String sourcePath = workspace.child(localSourcePath).getRemote();
            LoggingHelper.log(listener, "Local file to be uploaded to S3: " + sourcePath);

            localFile = new FilePath(workspace, getTempFilePath(sourcePath));
            zipFileMD5 = localFile.act(new LocalSourceCallable(workspace, localSourcePath));
        } else {
            if(workspaceSubdir != null && !workspaceSubdir.isEmpty()) {
                workspace = workspace.child(workspaceSubdir);
            }
            String sourcePath = workspace.getRemote();
            LoggingHelper.log(listener, "Zipping directory to upload to S3: " + sourcePath);

            localFile = new FilePath(workspace, getTempFilePath(sourcePath));
            zipFileMD5 = localFile.act(new ZipSourceCallable(workspace));
        }

        // Add MD5 checksum as S3 Object metadata
        objectMetadata.setContentMD5(zipFileMD5);
        objectMetadata.setContentLength(localFile.length());
        if(sseAlgorithm != null && !sseAlgorithm.isEmpty()) {
            objectMetadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        }

        PutObjectRequest putObjectRequest;
        PutObjectResult putObjectResult;

        try(InputStream zipFileInputStream = localFile.read()) {
            putObjectRequest = new PutObjectRequest(s3InputBucket, s3InputKey, zipFileInputStream, objectMetadata);
            LoggingHelper.log(listener, "Uploading to S3 at location " + putObjectRequest.getBucketName() + "/" + putObjectRequest.getKey() + ". MD5 checksum is " + zipFileMD5);
            putObjectResult = s3Client.putObject(putObjectRequest);
        } finally {
            localFile.delete();
        }

        return new UploadToS3Output(putObjectRequest.getBucketName() + "/" + putObjectRequest.getKey(), putObjectResult.getVersionId());
    }

    private String getTempFilePath(String filePath) {
        return filePath.substring(0, filePath.lastIndexOf(File.separator)+1) + UUID.randomUUID().toString() + "-" + s3InputKey;
    }

    public static String getZipMD5(File zipFile) throws IOException {
        return new String(encodeBase64(DigestUtils.md5(new FileInputStream(zipFile))), Charsets.UTF_8);
    }
}
