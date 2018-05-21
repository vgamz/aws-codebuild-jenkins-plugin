/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
 *  Portions copyright Copyright 2002-2016 JUnit. All Rights Reserved. Copyright (c) 2007 Mockito contributors. Copyright 2004-2011 Oracle Corporation.
 *  Please see LICENSE.txt for applicable license terms and NOTICE.txt for applicable notices.
 */

import com.amazonaws.services.codebuild.model.ArtifactsType;
import com.amazonaws.services.codebuild.model.BatchGetBuildsRequest;
import com.amazonaws.services.codebuild.model.InvalidInputException;
import com.amazonaws.services.codebuild.model.StartBuildRequest;
import enums.SourceControlType;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import lombok.Getter;
import lombok.Setter;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@PowerMockIgnore("javax.management.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest(CodeBuilder.class)
public class CodeBuilderPerformTest extends CodeBuilderTest {

    @Test
    public void testConfigAllNull() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder(null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null);

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
    }

    @Test
    public void testConfigAllNullPipeline() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder(null, null, null, null, null,
                null, null,null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null);

        test.setIsPipelineBuild(true);
        test.perform(build, ws, launcher, listener);

        assert(log.toString().contains(CodeBuilder.configuredImproperlyError));
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
    }

    @Test
    public void testConfigAllBlank() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("", "", "", "", "",
                "", "", "", "", "", "", "", "", "",
                "","","","","",     "", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
    }

    @Test
    public void testConfigAllBlankPipeline() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("", "", "", "", "",
                "", "", "","", "", "", "", "", "",
                "","","","","",     "", "", "", "");

        test.setIsPipelineBuild(true);
        test.perform(build, ws, launcher, listener);

        assert(log.toString().contains(CodeBuilder.configuredImproperlyError));
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
    }

    @Test
    public void testNoProjectName() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "","", "", "", "","",
                "us-east-1", "", "", "", SourceControlType.ProjectSource.toString(),
                "", "", "", "", "", "",
                "","", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.projectRequiredError), true);
    }

    @Test
    public void testNoProjectNamePipeline() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "","", "", "", "","",
                "us-east-1", "", "", "", SourceControlType.ProjectSource.toString(),
                "", "", "", "", "", "",
                "","", "", "", "");

        test.setIsPipelineBuild(true);
        test.perform(build, ws, launcher, listener);


        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.projectRequiredError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(result.getErrorMessage().contains(Validation.projectRequiredError));
    }

    @Test
    public void testNoSourceType() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "","", "", "", "","",
                "us-east-1", "project", "", "", "",
                "", "", "", "", "", "",
                "","", "", "", "");

        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);
        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        verify(listener, times(1)).getLogger();

        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.sourceControlTypeRequiredError), true);
    }

    @Test
    public void testNoSourceTypePipeline() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = new CodeBuilder("keys", "","", "", "", "","",
                "us-east-1", "project", "", "", "",
                "", "", "", "", "", "",
                "","", "", "", "");

        test.setIsPipelineBuild(true);
        test.perform(build, ws, launcher, listener);

        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(CodeBuilder.configuredImproperlyError), true);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(Validation.sourceControlTypeRequiredError), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(CodeBuilder.configuredImproperlyError));
        assertTrue(result.getErrorMessage().contains(Validation.sourceControlTypeRequiredError));
    }

    @Test
    public void testStartBuildExcepts() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "StartBuild exception";
        doThrow(new InvalidInputException(error)).when(mockClient).startBuild(any(StartBuildRequest.class));
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
    }

    @Test
    public void testStartBuildExceptsPipeline() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        test.setIsPipelineBuild(true);
        String error = "submit build exception.";
        doThrow(new InvalidInputException(error)).when(mockClient).startBuild(any(StartBuildRequest.class));

        test.perform(build, ws, launcher, listener);

        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }


    @Test
    public void testGetCBClientExcepts() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        String error = "failed to instantiate cb client.";
        doThrow(new InvalidInputException(error)).when(mockFactory).getCodeBuildClient();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
    }

    @Test
    public void testGetCBClientExceptsPipeline() throws Exception {
        setUpBuildEnvironment();
        CodeBuilder test = createDefaultCodeBuilder();
        test.setIsPipelineBuild(true);
        String error = "failed to instantiate cb client.";
        doThrow(new InvalidInputException(error)).when(mockFactory).getCodeBuildClient();

        test.perform(build, ws, launcher, listener);

        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }


    @Test
    public void testGetBuildExcepts() throws Exception {
        setUpBuildEnvironment();
        String error = "cannot get build";
        doThrow(new InvalidInputException(error)).when(mockClient).batchGetBuilds(any(BatchGetBuildsRequest.class));
        CodeBuilder test = createDefaultCodeBuilder();
        ArgumentCaptor<Result> savedResult = ArgumentCaptor.forClass(Result.class);

        test.perform(build, ws, launcher, listener);

        verify(build).setResult(savedResult.capture());
        assertEquals(savedResult.getValue(), Result.FAILURE);
        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
    }

    @Test
    public void testGetBuildExceptsPipeline() throws Exception {
        setUpBuildEnvironment();
        String error = "cannot get build";
        doThrow(new InvalidInputException(error)).when(mockClient).batchGetBuilds(any(BatchGetBuildsRequest.class));
        CodeBuilder test = createDefaultCodeBuilder();
        test.setIsPipelineBuild(true);

        test.perform(build, ws, launcher, listener);

        assertEquals("Invalid log contents: " + log.toString(), log.toString().contains(error), true);
        CodeBuildResult result = test.getCodeBuildResult();
        assertEquals(CodeBuildResult.FAILURE, result.getStatus());
        assertTrue(result.getErrorMessage().contains(error));
    }

    @Test
    public void testBuildParameters() throws Exception {
        setUpBuildEnvironment();

        envVars.put("foo", "bar");
        envVars.put("foo2", "bar2");
        envVars.put("foo3", "bar3");

        CodeBuilder cb = new CodeBuilder("keys", "id123","host", "60", "a", "s", "",
                "us-east-1", "$foo", "$foo2-$foo3", "", SourceControlType.ProjectSource.toString(), "1",
                ArtifactsType.NO_ARTIFACTS.toString(), "", "", "", "",
                "","[{k, v}]", "", "buildspec.yml", "5");

        cb.perform(build, ws, launcher, listener);

        assertEquals(envVars.get("foo"), cb.getParameterized(cb.getProjectName()));
        assertEquals(envVars.get("foo2") + "-" + envVars.get("foo3"), cb.getParameterized(cb.getSourceVersion()));
    }

    private class Parameter extends ParameterValue {
        @Getter @Setter String value;

        protected Parameter(String name, String description) {
            super(name, description);
        }
    }
}
