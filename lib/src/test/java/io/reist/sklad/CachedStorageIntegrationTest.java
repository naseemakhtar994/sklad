/*
 * Copyright (C) 2017 Renat Sarymsakov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.reist.sklad;

import android.os.Build;
import android.support.annotation.NonNull;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

import static io.reist.sklad.TestUtils.TEST_NAME;
import static io.reist.sklad.TestUtils.assertTestObject;
import static io.reist.sklad.TestUtils.saveTestObject;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by Reist on 26.06.16.
 */
@RunWith(RobolectricTestRunner.class)
@Config(
        constants = BuildConfig.class,
        sdk = Build.VERSION_CODES.LOLLIPOP,
        shadows = ShadowNetworkSecurityPolicy.class
)
public class CachedStorageIntegrationTest extends BaseStorageTest<CachedStorage> {

    private static final String LOCAL_TEST_NAME = "123";
    private static final byte[] LOCAL_TEST_DATA = new byte[] {1, 2, 3};
    private static final String LOCAL_INVALID_TEST_NAME = LOCAL_TEST_NAME + "q";

    private static final String REMOTE_TEST_NAME = "qwe";
    private static final byte[] REMOTE_TEST_DATA = new byte[] {7, 5, 3};
    private static final String REMOTE_INVALID_TEST_NAME = REMOTE_TEST_NAME + "q";

    private HttpUrl baseUrl;

    @Override
    @NonNull
    protected CachedStorage createStorage() throws IOException {
        return new CachedStorage(
                NetworkStorageTest.createNetworkStorage(baseUrl),
                EncryptedStorageTest.createEncryptedStorage(FileStorageTest.createFileStorage())
        );
    }

    @Test
    @Override
    public void testContains() throws Exception {

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404)); // remote doesn't contain TEST_NAME (for super test)
        server.enqueue(new MockResponse().setResponseCode(404)); // remote doesn't contain LOCAL_INVALID_TEST_NAME
        server.enqueue(new MockResponse()); // remote contains REMOTE_TEST_NAME
        server.enqueue(new MockResponse().setResponseCode(404)); // remote doesn't contain REMOTE_INVALID_TEST_NAME
        server.start();

        baseUrl = server.url("/");

        super.testContains();

        CachedStorage storage = createStorage();

        OutputStream localStream = storage.getLocalStorage().openOutputStream(LOCAL_TEST_NAME);
        localStream.write(LOCAL_TEST_DATA);
        localStream.flush();
        localStream.close();

        assertTrue(storage.contains(LOCAL_TEST_NAME));
        assertFalse(storage.contains(LOCAL_INVALID_TEST_NAME));

        OutputStream remoteStream = storage.getRemoteStorage().openOutputStream(REMOTE_TEST_NAME);
        remoteStream.write(REMOTE_TEST_DATA);
        remoteStream.flush();
        remoteStream.close();

        assertTrue(storage.contains(REMOTE_TEST_NAME));
        assertFalse(storage.contains(REMOTE_INVALID_TEST_NAME));

        server.shutdown();

    }

    @Test
    @Override
    public void testStreams() throws Exception {

        Buffer buffer = new Buffer();
        buffer.readFrom(new ByteArrayInputStream(TestUtils.TEST_DATA));

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(buffer));
        server.start();

        baseUrl = server.url("/");

        super.testStreams();

        CachedStorage storage = createStorage();
        saveTestObject(storage);
        assertTestObject(storage.getLocalStorage());
        assertTestObject(storage.getRemoteStorage());

        server.shutdown();

    }

    @Test
    public void testContainsInRemoteStorage() throws Exception {

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse()); // remote contains REMOTE_TEST_NAME
        server.start();

        baseUrl = server.url("/");

        CachedStorage storage = createStorage();

        OutputStream remoteStream = storage.getRemoteStorage().openOutputStream(REMOTE_TEST_NAME);
        remoteStream.write(REMOTE_TEST_DATA);
        remoteStream.flush();
        remoteStream.close();

        assertTrue(storage.containsInRemoteStorage(REMOTE_TEST_NAME));
        assertFalse(storage.containsInLocalStorage(REMOTE_TEST_NAME));

        server.shutdown();

    }

    @Test
    public void testContainsInLocalStorage() throws Exception {

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404)); // remote doesn't contain LOCAL_TEST_NAME
        server.start();

        baseUrl = server.url("/");

        CachedStorage storage = createStorage();

        OutputStream localStream = storage.getLocalStorage().openOutputStream(LOCAL_TEST_NAME);
        localStream.write(LOCAL_TEST_DATA);
        localStream.flush();
        localStream.close();

        assertTrue(storage.containsInLocalStorage(LOCAL_TEST_NAME));
        assertFalse(storage.containsInRemoteStorage(LOCAL_TEST_NAME));

        server.shutdown();

    }

    @Test
    public void testDownload() throws Exception {

        Buffer buffer = new Buffer();
        buffer.readFrom(new ByteArrayInputStream(TestUtils.TEST_DATA));

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setBody(buffer));
        server.start();

        baseUrl = server.url("/");

        CachedStorage storage = createStorage();
        storage.download(TEST_NAME);
        assertTestObject(storage.getLocalStorage());

        server.shutdown();

    }

    @Test
    public void testPartialCaching() throws Exception {

        Buffer buffer = new Buffer();
        buffer.readFrom(new ByteArrayInputStream(TestUtils.TEST_DATA));

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse());                     // contains data
        server.enqueue(new MockResponse().setBody(buffer));     // respond with actual data
        server.start();

        baseUrl = server.url("/");

        CachedStorage storage = createStorage();
        InputStream inputStream = storage.openInputStream(TestUtils.TEST_NAME);
        assertNotNull(inputStream);
        int b = inputStream.read();
        assertNotEquals(-1, b);
        inputStream.close();
        InputStream localStream = storage.getLocalStorage().openInputStream(TestUtils.TEST_NAME);
        assertNull(localStream);

        server.shutdown();

    }

    @Before
    public void setUp() throws Exception {
        baseUrl = null;
    }

    @Override
    public void testDeleteAll() throws Exception {

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404)); // force no files for cache
        server.start();

        baseUrl = server.url("/");

        super.testDeleteAll();

        server.shutdown();

    }

    @Override
    public void testDelete() throws Exception {

        MockWebServer server = new MockWebServer();
        server.enqueue(new MockResponse().setResponseCode(404)); // force no files for cache
        server.start();

        baseUrl = server.url("/");

        super.testDelete();

        server.shutdown();

    }

}