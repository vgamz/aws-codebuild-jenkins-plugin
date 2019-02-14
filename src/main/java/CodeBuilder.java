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
 *  Portions copyright Copyright 2004-2011 Oracle Corporation. Copyright (c) 2009-, Kohsuke Kawaguchi and other contributors.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.AWSCodeBuildClient;
import com.amazonaws.services.codebuild.model.*;
import com.amazonaws.services.codebuild.model.Build;
import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import enums.*;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CodeBuilder extends Builder implements SimpleBuildStep {

    @Getter private String credentialsType;
    @Getter private String credentialsId;
    @Getter private String proxyHost;
    @Getter private String proxyPort;
    @Getter private String awsAccessKey;
    @Getter private Secret awsSecretKey;
    @Getter private String awsSessionToken;
    @Getter private String region;
    @Getter private String projectName;
    @Getter private String sourceControlType;
    @Getter private String localSourcePath;
    @Getter private String workspaceSubdir;
    @Getter private String sourceVersion;
    @Getter private String sseAlgorithm;
    @Getter private String gitCloneDepthOverride;
    @Getter private String reportBuildStatusOverride;

    @Getter private String artifactTypeOverride;
    @Getter private String artifactLocationOverride;
    @Getter private String artifactNameOverride;
    @Getter private String artifactNamespaceOverride;
    @Getter private String artifactPackagingOverride;
    @Getter private String artifactPathOverride;
    @Getter private String artifactEncryptionDisabledOverride;
    @Getter private String overrideArtifactName;

    @Getter private String environmentTypeOverride;
    @Getter private String imageOverride;
    @Getter private String computeTypeOverride;
    @Getter private String certificateOverride;
    @Getter private String cacheTypeOverride;
    @Getter private String cacheLocationOverride;
    @Getter private String cloudWatchLogsStatusOverride;
    @Getter private String cloudWatchLogsGroupNameOverride;
    @Getter private String cloudWatchLogsStreamNameOverride;
    @Getter private String s3LogsStatusOverride;
    @Getter private String s3LogsLocationOverride;
    @Getter private String serviceRoleOverride;
    @Getter private String privilegedModeOverride;
    @Getter private String sourceTypeOverride;
    @Getter private String sourceLocationOverride;
    @Getter private String insecureSslOverride;
    @Getter private String envVariables;
    @Getter private String envParameters;
    @Getter private String buildSpecFile;
    @Getter private String buildTimeoutOverride;

    @Getter private final CodeBuildResult codeBuildResult;
    private EnvVars envVars;

    @Getter@Setter String artifactLocation;
    @Getter@Setter String artifactType;
    @Getter@Setter String projectSourceLocation;
    @Getter@Setter String projectSourceType;

    private StepContext stepContext;

    //These messages are used in the Jenkins console log.
    public static final String authorizationError = "Authorization error";
    public static final String configuredImproperlyError = "CodeBuild configured improperly in project settings";
    public static final String s3BucketBaseURL = "https://console.aws.amazon.com/s3/buckets/";
    public static final String envVariableSyntaxError = "CodeBuild environment variable keys and values cannot be empty and the string must be of the form [{key, value}, {key2, value2}]";
    public static final String envVariableNameSpaceError = "CodeBuild environment variable keys cannot start with CODEBUILD_";
    public static final String invalidProjectError = "Please select a project with S3 source type";
    public static final String notVersionsedS3BucketError = "A versioned S3 bucket is required.\n";

    public static final String httpTimeoutMessage = "Unable to execute HTTP request";

    private static final int MIN_SLEEP_TIME = 2500;
    private static final int MAX_SLEEP_TIME = 60000;
    private static final int SLEEP_JITTER = 5000;
    private int batchGetBuildsCalls;


    @DataBoundConstructor
    public CodeBuilder(String credentialsType, String credentialsId, String proxyHost, String proxyPort, String awsAccessKey, Secret awsSecretKey, String awsSessionToken,
                       String region, String projectName, String sourceVersion, String sseAlgorithm, String sourceControlType, String localSourcePath, String workspaceSubdir, String gitCloneDepthOverride,
                       String reportBuildStatusOverride, String artifactTypeOverride, String artifactLocationOverride, String artifactNameOverride, String artifactNamespaceOverride,
                       String artifactPackagingOverride, String artifactPathOverride, String artifactEncryptionDisabledOverride, String overrideArtifactName,
                       String envVariables, String envParameters, String buildSpecFile, String buildTimeoutOverride, String sourceTypeOverride,
                       String sourceLocationOverride, String environmentTypeOverride, String imageOverride, String computeTypeOverride,
                       String cacheTypeOverride, String cacheLocationOverride, String cloudWatchLogsStatusOverride, String cloudWatchLogsGroupNameOverride, String cloudWatchLogsStreamNameOverride,
                       String s3LogsStatusOverride, String s3LogsLocationOverride, String certificateOverride, String serviceRoleOverride,
                       String insecureSslOverride, String privilegedModeOverride) {

        this.credentialsType = Validation.sanitize(credentialsType);
        this.credentialsId = Validation.sanitize(credentialsId);
        this.proxyHost = Validation.sanitize(proxyHost);
        this.proxyPort = Validation.sanitize(proxyPort);
        this.awsAccessKey = Validation.sanitize(awsAccessKey);
        this.awsSecretKey = awsSecretKey;
        this.awsSessionToken = Validation.sanitize(awsSessionToken);
        this.region = Validation.sanitize(region);
        this.projectName = Validation.sanitize(projectName);
        this.sourceControlType = Validation.sanitize(sourceControlType);
        this.localSourcePath = Validation.sanitize(localSourcePath);
        this.sourceVersion = Validation.sanitize(sourceVersion);
        this.sseAlgorithm = Validation.sanitize(sseAlgorithm);
        this.gitCloneDepthOverride = Validation.sanitize(gitCloneDepthOverride);
        this.reportBuildStatusOverride = Validation.sanitize(reportBuildStatusOverride);
        this.artifactTypeOverride = Validation.sanitize(artifactTypeOverride);
        this.artifactLocationOverride = Validation.sanitize(artifactLocationOverride);
        this.artifactNameOverride = Validation.sanitize(artifactNameOverride);
        this.artifactNamespaceOverride = Validation.sanitize(artifactNamespaceOverride);
        this.artifactPackagingOverride = Validation.sanitize(artifactPackagingOverride);
        this.artifactPathOverride = Validation.sanitize(artifactPathOverride);
        this.artifactEncryptionDisabledOverride = Validation.sanitize(artifactEncryptionDisabledOverride);
        this.overrideArtifactName = Validation.sanitize(overrideArtifactName);
        this.sourceTypeOverride = Validation.sanitize(sourceTypeOverride);
        this.sourceLocationOverride = Validation.sanitize(sourceLocationOverride);
        this.environmentTypeOverride = Validation.sanitize(environmentTypeOverride);
        this.imageOverride = Validation.sanitize(imageOverride);
        this.computeTypeOverride = Validation.sanitize(computeTypeOverride);
        this.cacheTypeOverride = Validation.sanitize(cacheTypeOverride);
        this.cacheLocationOverride = Validation.sanitize(cacheLocationOverride);
        this.cloudWatchLogsStatusOverride = Validation.sanitize(cloudWatchLogsStatusOverride);
        this.cloudWatchLogsGroupNameOverride = Validation.sanitize(cloudWatchLogsGroupNameOverride);
        this.cloudWatchLogsStreamNameOverride = Validation.sanitize(cloudWatchLogsStreamNameOverride);
        this.s3LogsStatusOverride = Validation.sanitize(s3LogsStatusOverride);
        this.s3LogsLocationOverride = Validation.sanitize(s3LogsLocationOverride);
        this.certificateOverride = Validation.sanitize(certificateOverride);
        this.serviceRoleOverride = Validation.sanitize(serviceRoleOverride);
        this.envVariables = Validation.sanitize(envVariables);
        this.envParameters = Validation.sanitize(envParameters);
        this.buildSpecFile = Validation.sanitize(buildSpecFile);
        this.buildTimeoutOverride = Validation.sanitize(buildTimeoutOverride);
        this.insecureSslOverride = Validation.sanitize(insecureSslOverride);
        this.privilegedModeOverride = Validation.sanitize(privilegedModeOverride);
        this.codeBuildResult = new CodeBuildResult();
        this.batchGetBuildsCalls = 0;
    }

    protected Object readResolve() {
        credentialsType = Validation.sanitize(credentialsType);
        credentialsId = Validation.sanitize(credentialsId);
        proxyHost = Validation.sanitize(proxyHost);
        proxyPort = Validation.sanitize(proxyPort);
        awsAccessKey = Validation.sanitize(awsAccessKey);
        awsSessionToken = Validation.sanitize(awsSessionToken);
        region = Validation.sanitize(region);
        projectName = Validation.sanitize(projectName);
        sourceControlType = Validation.sanitize(sourceControlType);
        localSourcePath = Validation.sanitize(localSourcePath);
        workspaceSubdir = Validation.sanitize(workspaceSubdir);
        sourceVersion = Validation.sanitize(sourceVersion);
        sseAlgorithm = Validation.sanitize(sseAlgorithm);
        gitCloneDepthOverride = Validation.sanitize(gitCloneDepthOverride);
        reportBuildStatusOverride = Validation.sanitize(reportBuildStatusOverride);
        artifactTypeOverride = Validation.sanitize(artifactTypeOverride);
        artifactLocationOverride = Validation.sanitize(artifactLocationOverride);
        artifactNameOverride = Validation.sanitize(artifactNameOverride);
        artifactNamespaceOverride = Validation.sanitize(artifactNamespaceOverride);
        artifactPackagingOverride = Validation.sanitize(artifactPackagingOverride);
        artifactPathOverride = Validation.sanitize(artifactPathOverride);
        artifactEncryptionDisabledOverride = Validation.sanitize(artifactEncryptionDisabledOverride);
        overrideArtifactName = Validation.sanitize(overrideArtifactName);
        sourceTypeOverride = Validation.sanitize(sourceTypeOverride);
        sourceLocationOverride = Validation.sanitize(sourceLocationOverride);
        environmentTypeOverride = Validation.sanitize(environmentTypeOverride);
        imageOverride = Validation.sanitize(imageOverride);
        computeTypeOverride = Validation.sanitize(computeTypeOverride);
        cacheTypeOverride = Validation.sanitize(cacheTypeOverride);
        cacheLocationOverride = Validation.sanitize(cacheLocationOverride);
        cloudWatchLogsStatusOverride = Validation.sanitize(cloudWatchLogsStatusOverride);
        cloudWatchLogsGroupNameOverride = Validation.sanitize(cloudWatchLogsGroupNameOverride);
        cloudWatchLogsStreamNameOverride = Validation.sanitize(cloudWatchLogsStreamNameOverride);
        s3LogsStatusOverride = Validation.sanitize(s3LogsStatusOverride);
        s3LogsLocationOverride = Validation.sanitize(s3LogsLocationOverride);
        certificateOverride = Validation.sanitize(certificateOverride);
        serviceRoleOverride = Validation.sanitize(serviceRoleOverride);
        envVariables = Validation.sanitize(envVariables);
        envParameters = Validation.sanitize(envParameters);
        buildSpecFile = Validation.sanitizeYAML(buildSpecFile);
        buildTimeoutOverride = Validation.sanitize(buildTimeoutOverride);
        insecureSslOverride = Validation.sanitize(insecureSslOverride);
        privilegedModeOverride = Validation.sanitize(privilegedModeOverride);
        return this;
    }

    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath ws, @Nonnull Launcher launcher, @Nonnull TaskListener listener, StepContext stepContext) throws InterruptedException, IOException {
        this.stepContext = stepContext;
        perform(build, ws, launcher, listener);
    }

    /*
     * This is the Jenkins method that executes the actual build.
     */
    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath ws, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {

        envVars = build.getEnvironment(listener);

        AWSClientFactory awsClientFactory;
        try {
            awsClientFactory = new AWSClientFactory(
                    getParameterized(this.credentialsType),
                    getParameterized(this.credentialsId),
                    getParameterized(this.proxyHost),
                    getParameterized(this.proxyPort),
                    getParameterized(this.awsAccessKey),
                    this.awsSecretKey,
                    getParameterized(this.awsSessionToken),
                    getParameterized(this.region),
                    build,
                    this.stepContext);
        } catch (Exception e) {
            failBuild(build, listener, authorizationError, e.getMessage());
            return;
        }

        String projectConfigError = Validation.checkCodeBuilderConfig(this);
        if(!projectConfigError.isEmpty()) {
            failBuild(build, listener, configuredImproperlyError, projectConfigError);
            return;
        }

        String overridesErrorMessage = Validation.checkCodeBuilderStartBuildOverridesConfig(this);
        if(!overridesErrorMessage.isEmpty()) {
            failBuild(build, listener, configuredImproperlyError, overridesErrorMessage);
            return;
        }

        Collection<EnvironmentVariable> codeBuildEnvVars = null;
        try {
            codeBuildEnvVars = mapEnvVariables(getParameterized(envVariables), EnvironmentVariableType.PLAINTEXT);
            codeBuildEnvVars.addAll(mapEnvVariables(getParameterized(envParameters), EnvironmentVariableType.PARAMETER_STORE));
        } catch(InvalidInputException e) {
            failBuild(build, listener, configuredImproperlyError, e.getMessage());
            return;
        }
        if(Validation.envVariablesHaveRestrictedPrefix(codeBuildEnvVars)) {
            failBuild(build, listener, configuredImproperlyError, envVariableNameSpaceError);
            return;
        }

        LoggingHelper.log(listener, awsClientFactory.getCredentialsDescriptor());

        final AWSCodeBuildClient cbClient;
        try {
            cbClient = awsClientFactory.getCodeBuildClient();
        } catch (Exception e) {
            failBuild(build, listener, e.getMessage(), "");
            return;
        }

        try {
            retrieveArtifactAndSourceInfo(cbClient);
        } catch (Exception e) {
            failBuild(build, listener, e.getMessage(), "");
            return;
        }

        StartBuildRequest startBuildRequest = new StartBuildRequest().withProjectName(getParameterized(projectName)).
                withEnvironmentVariablesOverride(codeBuildEnvVars).withBuildspecOverride(getParameterized(buildSpecFile)).
                withTimeoutInMinutesOverride(Validation.parseInt(getParameterized(buildTimeoutOverride)));

        ProjectArtifacts artifactsOverride = generateStartBuildArtifactOverride();
        if(artifactsOverride != null) {
            startBuildRequest.setArtifactsOverride(artifactsOverride);
        }

        ProjectCache cacheOverride = generateStartBuildCacheOverride();
        if(cacheOverride != null) {
            startBuildRequest.setCacheOverride(cacheOverride);
        }

        LogsConfig logsConfigOverride = generateStartBuildLogsConfigOverride();
        if(logsConfigOverride != null) {
            startBuildRequest.setLogsConfigOverride(logsConfigOverride);
        }

        if(!getParameterized(environmentTypeOverride).isEmpty()) {
            startBuildRequest.setEnvironmentTypeOverride(getParameterized(environmentTypeOverride));
        }

        if(!getParameterized(imageOverride).isEmpty()) {
            startBuildRequest.setImageOverride(getParameterized(imageOverride));
        }

        if(!getParameterized(computeTypeOverride).isEmpty()) {
            startBuildRequest.setComputeTypeOverride(getParameterized(computeTypeOverride));
        }

        if(!getParameterized(certificateOverride).isEmpty()) {
            startBuildRequest.setCertificateOverride(getParameterized(certificateOverride));
        }

        if(!getParameterized(serviceRoleOverride).isEmpty()) {
            startBuildRequest.setServiceRoleOverride(getParameterized(serviceRoleOverride));
        }

        if(!getParameterized(insecureSslOverride).isEmpty()) {
            startBuildRequest.setInsecureSslOverride(Boolean.parseBoolean(getParameterized(insecureSslOverride)));
        }

        if(!getParameterized(privilegedModeOverride).isEmpty()) {
            startBuildRequest.setPrivilegedModeOverride(Boolean.parseBoolean(getParameterized(privilegedModeOverride)));
        }

        if(SourceControlType.JenkinsSource.toString().equals(getParameterized(sourceControlType))) {
            if(!Validation.checkSourceTypeS3(this.projectSourceType)) {
                failBuild(build, listener, invalidProjectError, "");
                return;
            }

            String sourceS3Bucket = Utils.getS3BucketFromObjectArn(this.projectSourceLocation);
            String sourceS3Key = Utils.getS3KeyFromObjectArn(this.projectSourceLocation);
            if(!Validation.checkBucketIsVersioned(sourceS3Bucket, awsClientFactory)) {
                failBuild(build, listener, notVersionsedS3BucketError, "");
                return;
            }

            S3DataManager s3DataManager = new S3DataManager(awsClientFactory.getS3Client(), sourceS3Bucket, sourceS3Key, getParameterized(sseAlgorithm), getParameterized(localSourcePath), getParameterized(workspaceSubdir));
            String uploadedSourceVersion = "";

            try {
                UploadToS3Output uploadToS3Output = s3DataManager.uploadSourceToS3(listener, ws);
                // Override source version to object version id returned by S3
                if(uploadToS3Output.getObjectVersionId() != null) {
                    uploadedSourceVersion = uploadToS3Output.getObjectVersionId();
                } else {
                    failBuild(build, listener, notVersionsedS3BucketError, "");
                    return;
                }
                LoggingHelper.log(listener, "S3 object version id for uploaded source is " + uploadedSourceVersion);
            } catch (Exception e) {
                failBuild(build, listener, e.getMessage(), "");
                return;
            }

            startBuildRequest.setSourceVersion(uploadedSourceVersion);
            logStartBuildMessage(listener, uploadedSourceVersion);

        } else {
            if(!getParameterized(sourceTypeOverride).isEmpty()) {
                startBuildRequest.setSourceTypeOverride(getParameterized(sourceTypeOverride));
                SourceAuth auth = generateStartBuildSourceAuthOverride(getParameterized(sourceTypeOverride));
                if(auth != null) {
                    startBuildRequest.setSourceAuthOverride(auth);
                }
            }
            if(!getParameterized(sourceLocationOverride).isEmpty()) {
                startBuildRequest.setSourceLocationOverride(getParameterized(sourceLocationOverride));
            }
            startBuildRequest.setSourceVersion(getParameterized(sourceVersion));
            startBuildRequest.setGitCloneDepthOverride(generateStartBuildGitCloneDepthOverride());
            if(!getParameterized(reportBuildStatusOverride).isEmpty()) {
                startBuildRequest.setReportBuildStatusOverride(Boolean.parseBoolean(getParameterized(reportBuildStatusOverride)));
            }

            logStartBuildMessage(listener, getParameterized(sourceVersion));
        }

        final StartBuildResult sbResult;
        try {
            sbResult = cbClient.startBuild(startBuildRequest);
        } catch (Exception e) {
            failBuild(build, listener, e.getMessage(), "");
            return;
        }

        Build currentBuild = new Build().withBuildStatus(StatusType.IN_PROGRESS);
        String buildId = sbResult.getBuild().getId();
        LoggingHelper.log(listener, "Build id: " + buildId);
        LoggingHelper.log(listener, "CodeBuild dashboard: " + generateDashboardURL(buildId));

        boolean haveInitializedAction = false;
        CodeBuildAction action = null;
        CloudWatchMonitor logMonitor = null;

        //poll buildResult for build status until it's complete.
        do {
            try {
                List<Build> buildsForId = cbClient.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds();

                if(buildsForId.size() != 1) {
                    throw new Exception("Multiple builds mapped to this build id.");
                }

                currentBuild = buildsForId.get(0);
                if(!haveInitializedAction) {
                    logMonitor = new CloudWatchMonitor(awsClientFactory.getCloudWatchLogsClient());
                    action = new CodeBuildAction(build);

                    //only need to set these once, the others will need to be updated below as the build progresses.
                    String buildARN = currentBuild.getArn();
                    codeBuildResult.setBuildInformation(currentBuild.getId(), buildARN);

                    action.setBuildId(buildId);
                    action.setBuildARN(buildARN);
                    action.setStartTime(currentBuild.getStartTime().toString());

                    ProjectSource source = currentBuild.getSource();
                    if(source != null) {
                        action.setSourceType(source.getType());
                        action.setSourceLocation(source.getLocation());

                        if(currentBuild.getSourceVersion() == null) {
                            action.setSourceVersion("");
                        } else {
                            action.setSourceVersion(currentBuild.getSourceVersion());
                        }

                        Integer depth = source.getGitCloneDepth();
                        if(depth == null || depth == 0) {
                            action.setGitCloneDepth("Full");
                        } else {
                            action.setGitCloneDepth(String.valueOf(depth));
                        }

                        Boolean status = source.getReportBuildStatus();
                        if(status != null) {
                            action.setReportBuildStatus(String.valueOf(status));
                        }
                    }

                    action.setS3ArtifactURL(generateS3ArtifactURL(this.s3BucketBaseURL, artifactLocation, artifactType));
                    action.setArtifactTypeOverride(getParameterized(artifactTypeOverride));
                    action.setCodeBuildDashboardURL(generateDashboardURL(buildId));
                    action.setS3BucketName(artifactLocation);
                    action.setLogs(new ArrayList());
                    action.setCloudWatchLogsURL("");
                    action.setS3LogsURL("");

                    build.addAction(action);
                    haveInitializedAction = true;
                }

                updateDashboard(currentBuild, action, logMonitor, listener);
                Thread.sleep(getSleepTime());

            } catch(Exception e) {
                if(e.getClass().equals(InterruptedException.class)) {
                    //Request to stop Jenkins build has been made. First make sure the build is stoppable
                    List<Build> buildsForId = cbClient.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds();
                    currentBuild = buildsForId.get(0);
                    if(!currentBuild.getCurrentPhase().equals(BuildPhaseType.COMPLETED.toString())) {
                        cbClient.stopBuild(new StopBuildRequest().withId(buildId));
                        //Wait for the build to actually stop
                        do {
                            buildsForId = cbClient.batchGetBuilds(new BatchGetBuildsRequest().withIds(buildId)).getBuilds();
                            currentBuild = buildsForId.get(0);
                            Thread.sleep(5000L);
                            logMonitor.pollForLogs(listener);
                            updateDashboard(currentBuild, action, logMonitor, listener);
                        } while (!currentBuild.getCurrentPhase().equals(BuildPhaseType.COMPLETED.toString()));
                    }
                    if (action != null) {
                        action.setJenkinsBuildSucceeds(false);
                    }
                    this.codeBuildResult.setStopped();
                    build.setResult(Result.ABORTED);
                    return;
                } else if(e.getMessage().contains(httpTimeoutMessage)) {
                    Thread.sleep(getSleepTime());
                    continue;
                } else {
                    if (action != null) {
                        action.setJenkinsBuildSucceeds(false);
                    }
                    failBuild(build, listener, e.getMessage(), "");
                    return;
                }
            }
        } while(currentBuild.getBuildStatus().equals(StatusType.IN_PROGRESS.toString()));

        // Read artifacts location once the build is complete and artifact name finalized
        codeBuildResult.setArtifactsLocation(currentBuild.getArtifacts() != null ? currentBuild.getArtifacts().getLocation() : null);

        if(currentBuild.getBuildStatus().equals(StatusType.SUCCEEDED.toString().toUpperCase(Locale.ENGLISH))) {
            action.setJenkinsBuildSucceeds(true);
            this.codeBuildResult.setSuccess();
            build.setResult(Result.SUCCESS);
        } else {
            action.setJenkinsBuildSucceeds(false);
            String errorMessage = "Build " + currentBuild.getId() + " failed" + "\n\t> " + action.getPhaseErrorMessage();
            failBuild(build, listener, errorMessage, "");
        }
        return;
    }

    private int getSleepTime() {
        // 5s + 1s per BatchGetBuilds call already made + jitter for concurrent builds
        int sleepTime = MIN_SLEEP_TIME + batchGetBuildsCalls++*1000 + ThreadLocalRandom.current().nextInt(SLEEP_JITTER);
        return Math.min(sleepTime, MAX_SLEEP_TIME);
    }

    // finds the name of the artifact S3 bucket associated with this project.
    // Sets this.artifactLocation equal to it and updates this.action.
    // @param cbClient: the CodeBuild client used by this build.
    private void retrieveArtifactAndSourceInfo(AWSCodeBuildClient cbClient) throws Exception {
        BatchGetProjectsResult bgpResult = cbClient.batchGetProjects(
                new BatchGetProjectsRequest().withNames(getParameterized(projectName)));

        if(bgpResult.getProjects().isEmpty()) {
            throw new RuntimeException("Project " + getParameterized(projectName) + " does not exist.");
        } else {
            this.artifactLocation = bgpResult.getProjects().get(0).getArtifacts().getLocation();
            this.artifactType = bgpResult.getProjects().get(0).getArtifacts().getType();

            this.projectSourceLocation = bgpResult.getProjects().get(0).getSource().getLocation();
            this.projectSourceType = bgpResult.getProjects().get(0).getSource().getType();
        }
    }

    // Performs an update of build data to the codebuild dashboard.
    // @param action: the entity representing the dashboard.
    private void updateDashboard(Build b, CodeBuildAction action, CloudWatchMonitor logMonitor, TaskListener listener) {
        if(action != null) {
            action.setCurrentStatus(b.getBuildStatus());
            logMonitor.setLogsLocation(b.getLogs());

            logMonitor.pollForLogs(listener);
            action.updateLogs(logMonitor.getLatestLogs());

            action.setPhases(b.getPhases());
            if(logMonitor.getLogsLocation() != null) {
                LogsLocation logsLocation = logMonitor.getLogsLocation();

                if(logsLocation.getGroupName() != null && logsLocation.getStreamName() != null && logsLocation.getDeepLink() != null
                    && action.getCloudWatchLogsURL().equals("")) {
                    String cloudWatchLogsURL = logsLocation.getDeepLink();
                    action.setCloudWatchLogsURL(cloudWatchLogsURL);
                    LoggingHelper.log(listener, "CloudWatch dashboard: " + cloudWatchLogsURL);
                }

                if(logsLocation.getS3DeepLink() != null && b.getPhases() != null && action.getS3LogsURL().equals("")) {
                    List<BuildPhase> phases = b.getPhases();
                    for(BuildPhase phase : phases) {
                        if(phase.getPhaseType() != null && phase.getPhaseType().equals(BuildPhaseType.UPLOAD_ARTIFACTS.toString())) {
                            if(phase.getContexts() != null && phase.getContexts().get(0) != null && phase.getContexts().get(0).getMessage() != null
                                && !phase.getContexts().get(0).getMessage().contains("Error uploading logs:")) {
                                String s3LogsURL = logsLocation.getS3DeepLink();
                                action.setS3LogsURL(s3LogsURL);
                                LoggingHelper.log(listener, "S3 logs location: " + s3LogsURL);
                            }
                        }
                    }
                }
            }
        }
    }

    // @param baseURL: a link to the S3 dashboard for the user running the build.
    // @param buildARN: the ARN for this build.
    // @return: a URL to the S3 artifacts for this build.
    public String generateS3ArtifactURL(String baseURL, String artifactLocation, String artifactType) throws UnsupportedEncodingException {
        if(artifactLocation == null || artifactLocation.isEmpty() ||
                artifactType == null || !artifactType.equals(ArtifactsType.S3.toString())) {
            return "";
        } else {
            return new StringBuilder()
                .append(baseURL)
                .append(URLEncoder.encode(artifactLocation, "UTF-8")).toString();
        }
    }

    private String generateDashboardURL(String buildId) {
        return new StringBuilder()
            .append("https://")
            .append(getParameterized(region))
            .append(".console.aws.amazon.com/codesuite/codebuild/projects/")
            .append(getParameterized(projectName))
            .append("/build/")
            .append(getParameterized(buildId))
            .append("/log?region=")
            .append(region)
            .toString();
    }

    private void logStartBuildMessage(TaskListener listener, String sourceVersion) {
        StringBuilder message = new StringBuilder().append("Starting build with \n\t> project name: " + getParameterized(projectName));
        if(!SourceControlType.JenkinsSource.toString().equals(getParameterized(sourceControlType))) {
            if(!sourceTypeOverride.isEmpty()) {
                message.append("\n\t> source type: " + getParameterized(sourceTypeOverride));
            }
            if(!sourceLocationOverride.isEmpty()) {
                message.append("\n\t> source location: " + getParameterized(sourceLocationOverride));
            }
            if(!gitCloneDepthOverride.isEmpty()) {
                message.append("\n\t> git clone depth: " + getParameterized(gitCloneDepthOverride) + " (git clone depth is omitted when source provider is Amazon S3)");
            }
            if(!reportBuildStatusOverride.isEmpty()) {
                message.append("\n\t> report build status: " + getParameterized(reportBuildStatusOverride) + " (report build status is valid when source provider is GitHub)");
            }
        }
        if(!sourceVersion.isEmpty()) {
            message.append("\n\t> source version: " + sourceVersion);
        }
        if(!artifactTypeOverride.isEmpty()) {
            message.append("\n\t> artifact type: " + getParameterized(artifactTypeOverride));
        }
        if(!artifactLocationOverride.isEmpty()) {
            message.append("\n\t> artifact location: " + getParameterized(artifactLocationOverride));
        }
        if(!artifactNameOverride.isEmpty()) {
            message.append("\n\t> artifact name: " + getParameterized(artifactNameOverride));
        }
        if(!overrideArtifactName.isEmpty()) {
            message.append("\n\t> overrideArtifactName: " + getParameterized(overrideArtifactName));
        }
        if(!artifactNamespaceOverride.isEmpty()) {
            message.append("\n\t> artifact namespace: " + getParameterized(artifactNamespaceOverride));
        }
        if(!artifactPackagingOverride.isEmpty()) {
            message.append("\n\t> artifact packaging: " + getParameterized(artifactPackagingOverride));
        }
        if(!artifactPathOverride.isEmpty()) {
            message.append("\n\t> artifact path: " + getParameterized(artifactPathOverride));
        }
        if(!artifactEncryptionDisabledOverride.isEmpty()) {
            message.append("\n\t> artifact encryption disabled: " + getParameterized(artifactEncryptionDisabledOverride));
        }
        if(!envVariables.isEmpty()) {
            message.append("\n\t> environment variables: " + getParameterized(envVariables));
        }
        if(!buildTimeoutOverride.isEmpty()) {
            message.append("\n\t> build timeout: " + getParameterized(buildTimeoutOverride));
        }
        if(!cacheTypeOverride.isEmpty()) {
            message.append("\n\t> cache type: " + getParameterized(cacheTypeOverride));
        }
        if(!cacheLocationOverride.isEmpty()) {
            message.append("\n\t> cache location: " + getParameterized(cacheLocationOverride));
        }
        if(!cloudWatchLogsStatusOverride.isEmpty()) {
            message.append("\n\t> cloudwatch logs status: " + getParameterized(cloudWatchLogsStatusOverride));
        }
        if(!cloudWatchLogsGroupNameOverride.isEmpty()) {
            message.append("\n\t> cloudwatch logs group name: " + getParameterized(cloudWatchLogsGroupNameOverride));
        }
        if(!cloudWatchLogsStreamNameOverride.isEmpty()) {
            message.append("\n\t> cloudwatch logs stream name: " + getParameterized(cloudWatchLogsStreamNameOverride));
        }
        if(!s3LogsStatusOverride.isEmpty()) {
            message.append("\n\t> s3 logs status: " + getParameterized(s3LogsStatusOverride));
        }
        if(!s3LogsLocationOverride.isEmpty()) {
            message.append("\n\t> s3 logs location: " + getParameterized(s3LogsLocationOverride));
        }
        if(!environmentTypeOverride.isEmpty()) {
            message.append("\n\t> environment type: " + getParameterized(environmentTypeOverride));
        }
        if(!imageOverride.isEmpty()) {
            message.append("\n\t> image: " + getParameterized(imageOverride));
        }
        if(!privilegedModeOverride.isEmpty()) {
            message.append("\n\t> privileged mode override: " + getParameterized(privilegedModeOverride));
        }
        if(!computeTypeOverride.isEmpty()) {
            message.append("\n\t> compute type: " + getParameterized(computeTypeOverride));
        }
        if(!insecureSslOverride.isEmpty()) {
            message.append("\n\t> insecure ssl override: " + getParameterized(insecureSslOverride));
        }
        if(!certificateOverride.isEmpty()) {
            message.append("\n\t> certificate: " + getParameterized(certificateOverride));
        }
        if(!serviceRoleOverride.isEmpty()) {
            message.append("\n\t> service role: " + getParameterized(serviceRoleOverride));
        }
        if(!buildSpecFile.isEmpty()) {
            message.append("\n\t> build spec: \n" + getParameterized(buildSpecFile));
        }

        LoggingHelper.log(listener, message.toString());
    }

    private Integer generateStartBuildGitCloneDepthOverride() {
        String depth = getParameterized(gitCloneDepthOverride);
        if(depth.isEmpty() || depth.equals("Full")) {
            return 0;
        }

        return Integer.parseInt(depth);
    }

    private ProjectArtifacts generateStartBuildArtifactOverride() {
        ProjectArtifacts artifacts = new ProjectArtifacts();
        boolean overridesSpecified = false;
        if(!getParameterized(artifactTypeOverride).isEmpty()) {
            artifacts.setType(getParameterized(artifactTypeOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactLocationOverride).isEmpty()) {
            artifacts.setLocation(getParameterized(artifactLocationOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactNameOverride).isEmpty()) {
            artifacts.setName(getParameterized(artifactNameOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactNamespaceOverride).isEmpty()) {
            artifacts.setNamespaceType(getParameterized(artifactNamespaceOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactPackagingOverride).isEmpty()) {
            artifacts.setPackaging(getParameterized(artifactPackagingOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactPathOverride).isEmpty()) {
            artifacts.setPath(getParameterized(artifactPathOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(artifactEncryptionDisabledOverride).isEmpty()) {
            artifacts.setEncryptionDisabled(Boolean.parseBoolean(artifactEncryptionDisabledOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(overrideArtifactName).isEmpty()) {
            artifacts.setOverrideArtifactName(Boolean.parseBoolean(overrideArtifactName));
            overridesSpecified = true;
        }

        return overridesSpecified ? artifacts : null;
    }

    private ProjectCache generateStartBuildCacheOverride() {
        ProjectCache cache = new ProjectCache();
        boolean overridesSpecified = false;
        if(!getParameterized(cacheTypeOverride).isEmpty()) {
            cache.setType(getParameterized(cacheTypeOverride));
            overridesSpecified = true;
        }
        if(!getParameterized(cacheLocationOverride).isEmpty()) {
            cache.setLocation(getParameterized(cacheLocationOverride));
            overridesSpecified = true;
        }
        return overridesSpecified ? cache : null;
    }

    private LogsConfig generateStartBuildLogsConfigOverride() {
        LogsConfig logsConfig = new LogsConfig();
        CloudWatchLogsConfig cloudWatchLogsConfig = new CloudWatchLogsConfig();
        S3LogsConfig s3LogsConfig = new S3LogsConfig();

        boolean overridesCloudWatchLogsSpecified = false;
        boolean overridesS3LogsSpecified = false;

        if(!getParameterized(cloudWatchLogsStatusOverride).isEmpty()) {
            cloudWatchLogsConfig.setStatus(getParameterized(cloudWatchLogsStatusOverride));
            overridesCloudWatchLogsSpecified = true;
        }
        if(!getParameterized(cloudWatchLogsGroupNameOverride).isEmpty()) {
            cloudWatchLogsConfig.setGroupName(getParameterized(cloudWatchLogsGroupNameOverride));
            overridesCloudWatchLogsSpecified = true;
        }
        if(!getParameterized(cloudWatchLogsStreamNameOverride).isEmpty()) {
            cloudWatchLogsConfig.setStreamName(getParameterized(cloudWatchLogsStreamNameOverride));
            overridesCloudWatchLogsSpecified = true;
        }

        if(!getParameterized(s3LogsStatusOverride).isEmpty()) {
            s3LogsConfig.setStatus(getParameterized(s3LogsStatusOverride));
            overridesS3LogsSpecified = true;
        }
        if(!getParameterized(s3LogsLocationOverride).isEmpty()) {
            s3LogsConfig.setLocation(getParameterized(s3LogsLocationOverride));
            overridesS3LogsSpecified = true;
        }

        if(overridesCloudWatchLogsSpecified) {
            logsConfig.setCloudWatchLogs(cloudWatchLogsConfig);
        }
        if(overridesS3LogsSpecified) {
            logsConfig.setS3Logs(s3LogsConfig);
        }

        return overridesCloudWatchLogsSpecified || overridesS3LogsSpecified ? logsConfig : null;
    }

    private SourceAuth generateStartBuildSourceAuthOverride(String sourceType) {
        SourceAuth auth = null;
        if(sourceType.equals(SourceType.GITHUB.toString()) || sourceType.equals(SourceType.BITBUCKET.toString())) {
            auth = new SourceAuth().withType(SourceAuthType.OAUTH.toString());
        }
        return auth;
    }

    // Given a String representing environment variables, returns a list of com.amazonaws.services.codebuild.model.EnvironmentVariable
    // objects with the same data. The input string must be in the form [{Key, value}, {k2, v2}] or else null is returned
    public static Collection<EnvironmentVariable> mapEnvVariables(String envVars, EnvironmentVariableType envVarType) throws InvalidInputException {
        Collection<EnvironmentVariable> result = new HashSet<EnvironmentVariable>();
        if(envVars == null || envVars.isEmpty()) {
            return result;
        }

        envVars = envVars.replaceAll("\\}\\s*,\\s*\\{", "},{");
        envVars = envVars.replaceAll("\\[\\s*\\{", "[{");
        envVars = envVars.replaceAll("\\}\\s*\\]", "}]");
        envVars = envVars.replaceAll("[\\n|\\t]", "").trim();
        if(envVars.length() < 4 || envVars.charAt(0) != '[' || envVars.charAt(envVars.length()-1) != ']' ||
           envVars.charAt(1) != '{' || envVars.charAt(envVars.length()-2) != '}') {
            throw new InvalidInputException(envVariableSyntaxError);
        } else {
            envVars = envVars.substring(2, envVars.length()-2);
        }

        int numCommas = envVars.replaceAll("[^,]", "").length();
        if(numCommas == 0) {
            throw new InvalidInputException(envVariableSyntaxError);
        }
        //single environment variable case vs multiple
        if(numCommas == 1) {
            result.add(deserializeCodeBuildEnvVar(envVars, envVarType));
        } else {
            String[] evs = envVars.split("\\},\\{");
            for(int i = 0; i < evs.length; i++) {
                result.add(deserializeCodeBuildEnvVar(evs[i], envVarType));
            }
        }
        return result;
    }

    // Given a string of the form "key,value", returns a CodeBuild Environment Variable with that data.
    // Throws an InvalidInputException when the input string doesn't match the form described in mapEnvVariables
    private static EnvironmentVariable deserializeCodeBuildEnvVar(String ev, EnvironmentVariableType envVarType) throws InvalidInputException {
        if(ev.replaceAll("[^,]", "").length() != 1) {
            throw new InvalidInputException(envVariableSyntaxError);
        }
        String[] keyAndValue = ev.split(",");
        if(keyAndValue.length != 2 || keyAndValue[0].isEmpty() || keyAndValue[1].isEmpty()) {
            throw new InvalidInputException(envVariableSyntaxError);
        }
        return new EnvironmentVariable().withName(keyAndValue[0].trim()).withValue(keyAndValue[1].trim()).withType(envVarType);
    }

    private void failBuild(Run<?, ?> build, TaskListener listener, String errorMessage, String secondaryError) throws AbortException {
        this.codeBuildResult.setFailure(errorMessage, secondaryError);
        build.setResult(Result.FAILURE);
        LoggingHelper.log(listener, errorMessage, secondaryError);
    }

    //Given a CodeBuild build parameter, checks if it contains any Jenkins parameters and if so, evaluates and returns the
    //value.
    public String getParameterized(String codeBuildParam) {
        String result = envVars.expand(codeBuildParam);
        if(result == null) {
          return "";
        }
        return result;
    }

    //// Jenkins-specific functions ////
    // all for CodeBuilder/config.jelly
    public String sourceControlTypeEquals(String given) {
        return String.valueOf((sourceControlType != null) && (sourceControlType.equals(given)));
    }

    public String credentialsTypeEquals(String given) {
        return String.valueOf((credentialsType != null) && (credentialsType.equals(given)));
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }


    /**
     * Descriptor for CodeBuilder. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return super.configure(req, formData);
        }

        public ListBoxModel doFillGitCloneDepthOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(GitCloneDepth t: GitCloneDepth.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillReportBuildStatusOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillPrivilegedModeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillInsecureSslOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillArtifactTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(ArtifactsType t: ArtifactsType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactNamespaceOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(ArtifactNamespace t: ArtifactNamespace.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactPackagingOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (ArtifactPackaging t : ArtifactPackaging.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillArtifactEncryptionDisabledOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillOverrideArtifactNameItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(BooleanValue t: BooleanValue.values()) {
                selections.add(t.toString());
            }

            return selections;
        }

        public ListBoxModel doFillSourceTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (SourceType t : SourceType.values()) {
                if(!t.equals(SourceType.CODEPIPELINE)) {
                    selections.add(t.toString());
                }
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillComputeTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (ComputeType t : ComputeType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillCacheTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (CacheType t : CacheType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillCloudWatchLogsStatusOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(LogsConfigStatusType t : LogsConfigStatusType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillS3LogsStatusOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(LogsConfigStatusType t : LogsConfigStatusType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillEnvironmentTypeOverrideItems() {
            final ListBoxModel selections = new ListBoxModel();

            for (EnvironmentType t : EnvironmentType.values()) {
                selections.add(t.toString());
            }
            selections.add("");
            return selections;
        }

        public ListBoxModel doFillRegionItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(CodeBuildRegions r: CodeBuildRegions.values()) {
                selections.add(r.toString());
            }
            return selections;
        }

        public ListBoxModel doFillCredentialsIdItems() {
            final ListBoxModel selections = new ListBoxModel();

            SystemCredentialsProvider s = SystemCredentialsProvider.getInstance();
            Set<String> displayCredentials = new HashSet();

            for (Credentials c: s.getCredentials()) {
                if (c instanceof CodeBuildCredentials) {
                    displayCredentials.add(((CodeBuildCredentials) c).getId());
                }
            }

            Jenkins instance = Jenkins.getInstance();
            if(instance != null) {
                List<Folder> folders = instance.getAllItems(Folder.class);
                for(Folder folder: folders) {
                    List<Credentials> creds = CredentialsProvider.lookupCredentials(Credentials.class, (Item) folder);
                    for(Credentials cred: creds) {
                        if (cred instanceof CodeBuildCredentials) {
                            displayCredentials.add(((CodeBuildCredentials) cred).getId());
                        }
                    }
                }
            }


            for(String credString: displayCredentials) {
                selections.add(credString);
            }

            return selections;
        }

        public ListBoxModel doFillSseAlgorithmItems() {
            final ListBoxModel selections = new ListBoxModel();

            for(EncryptionAlgorithm e: EncryptionAlgorithm.values()) {
                selections.add(e.toString());
            }

            return selections;
        }

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        public String getDisplayName() {
            return "AWS CodeBuild";
        }
    }
}

