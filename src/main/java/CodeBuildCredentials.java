/*
 *     Copyright 2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
 *     Portions copyright Copyright (c) 2015, CloudBees, Inc.
 *     This program is made available under the terms of the MIT License.
 *
 *     Permission is hereby granted, free of charge, to any person obtaining a copy
 *     of this software and associated documentation files (the "Software"), to deal
 *     in the Software without restriction, including without limitation the rights
 *     to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *     copies of the Software, and to permit persons to whom the Software is
 *     furnished to do so, subject to the following conditions:
 *
 *     The above copyright notice and this permission notice shall be included in all
 *     copies or substantial portions of the Software.
 *
 *     THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *     IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *     FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *     AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *     LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *     OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *     SOFTWARE.
 */

import com.amazonaws.codebuild.jenkinsplugin.CodeBuildBaseCredentials;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

// Legacy credentials class, kept around for backward compatibility.
public class CodeBuildCredentials extends CodeBuildBaseCredentials implements AWSCredentialsProvider {

    @DataBoundConstructor
    public CodeBuildCredentials(CredentialsScope scope, String id, String description, String accessKey, String secretKey,
                                String proxyHost, String proxyPort, String iamRoleArn, String externalId) {
        super(scope, id, description, accessKey, secretKey, proxyHost, proxyPort, iamRoleArn, externalId);
    }

    @Override
    public AWSCredentials getCredentials() {
        return super.getCredentials();
    }

    @Override
    public void refresh() {
        super.refresh();
    }

    @Extension
    public static class DescriptorImpl extends CodeBuildBaseCredentials.DescriptorImpl {
        public String getDisplayName() {
            return "CodeBuild Credentials";
        }
    }

}
