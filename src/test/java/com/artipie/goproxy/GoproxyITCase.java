/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.goproxy;

import com.artipie.asto.Storage;
import com.artipie.asto.fs.FileStorage;
import io.vertx.reactivex.core.Vertx;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.cactoos.text.Joined;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration case for {@link Goproxy}.
 *
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
@Testcontainers
public final class GoproxyITCase {
    /**
     * Path to repo (will be used both in test and inside golang container).
     */
    private static Path repo;

    /**
     * GoLang container to verify Go repository layout.
     */
    private static GoContainer golang;

    /**
     * Test GoProxy works.
     * @throws Exception If some problem inside
     */
    @Test
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    public void savesAndLoads() throws Exception {
        final Vertx vertx = Vertx.vertx();
        final Storage storage = new FileStorage(GoproxyITCase.repo, vertx.fileSystem());
        final Goproxy goproxy = new Goproxy(storage, vertx);
        goproxy.update("example.com/foo/bar", "0.0.123").blockingAwait();
        goproxy.update("example.com/foo/bar", "0.0.124").blockingAwait();
        this.validateResult(
            golang.execInContainer("go", "install", "-v"),
            "go: downloading example.com/foo/bar v0.0.124"
        );
        this.validateResult(
            golang.execInContainer("go", "run", "test.go"),
            "Hey, you!",
            "Works!!!"
        );
    }

    /**
     * Start GoLang container and make sure go is available.
     * @throws Exception If fails
     */
    @BeforeAll
    static void goExists() throws Exception {
        GoproxyITCase.repo = Paths.get(
            Thread.currentThread()
            .getContextClassLoader()
            .getResource("repo")
            .toURI()
        );
        GoproxyITCase.golang = new GoContainer()
            .withClasspathResourceMapping("repo", "/opt/repo", BindMode.READ_WRITE)
            .withClasspathResourceMapping("work", "/opt/work", BindMode.READ_WRITE)
            .withEnv("GOPROXY", "file:///opt/repo")
            .withEnv("GOSUMDB", "off")
            .withWorkingDirectory("/opt/work")
            .withCommand("tail", "-f", "/dev/null");
        GoproxyITCase.golang.start();
        final Container.ExecResult result = golang.execInContainer("which", "go");
        MatcherAssert.assertThat(
            "Go is NOT present at the build machine",
            result.getExitCode(),
            Matchers.equalTo(0)
        );
    }

    /**
     * Stop GoLang container after all tests.
     */
    @AfterAll
    static void stopContainer() {
        GoproxyITCase.golang.stop();
    }

    /**
     * Validate results of running command inside docker.
     * @param result Result of execution command inside docker
     * @param substrings Templates to be found in the process output
     * @throws Exception
     */
    private void validateResult(final Container.ExecResult result,
        final String... substrings) throws Exception {
        MatcherAssert.assertThat(0, Matchers.equalTo(result.getExitCode()));
        MatcherAssert.assertThat(
            new Joined("\n", result.getStdout(), result.getStderr()).asString(),
            Matchers.allOf(
                Arrays.stream(substrings)
                    .map(Matchers::containsString)
                    .collect(Collectors.toList())
            )
        );
    }

    /**
     * Inner subclass to instantiate GoLang container.
     * @since 0.3
     */
    private static class GoContainer extends GenericContainer<GoContainer> {
        GoContainer() {
            super("golang:latest");
        }
    }
}
