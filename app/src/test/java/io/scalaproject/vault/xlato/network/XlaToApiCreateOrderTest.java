/*
 * Copyright (c) 2017 m2049r et al.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ////////////////
 *
 * Copyright (c) 2020 Scala
 *
 * Please see the included LICENSE file for more information.*/

package io.scalaproject.vault.xlato.network;

import io.scalaproject.vault.xlato.api.XlaToCallback;
import io.scalaproject.vault.xlato.XlaToError;
import io.scalaproject.vault.xlato.XlaToException;
import io.scalaproject.vault.xlato.api.CreateOrder;
import io.scalaproject.vault.xlato.api.XlaToApi;

import net.jodah.concurrentunit.Waiter;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeoutException;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.assertEquals;

public class XlaToApiCreateOrderTest {

    private MockWebServer mockWebServer;

    private XlaToApi xlaToApi;

    private final OkHttpClient okHttpClient = new OkHttpClient();
    private Waiter waiter;

    @Mock
    XlaToCallback<CreateOrder> mockOrderXlaToCallback;

    @Before
    public void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        waiter = new Waiter();

        MockitoAnnotations.initMocks(this);

        xlaToApi = new XlaToApiImpl(okHttpClient, mockWebServer.url("/"));
    }

    @After
    public void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    public void createOrder_shouldBePostMethod()
            throws InterruptedException {

        xlaToApi.createOrder(0.5, "btcsomething", mockOrderXlaToCallback);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("POST", request.getMethod());
    }

    @Test
    public void createOrder_shouldBeContentTypeJson()
            throws InterruptedException {

        xlaToApi.createOrder(0.5, "19y91nJyzXsLEuR7Nj9pc3o5SeHNc8A9RW", mockOrderXlaToCallback);

        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"));
    }

    @Test
    public void createOrder_shouldContainValidBody()
            throws InterruptedException {

        final String validBody = "{\"amount\":0.1,\"amount_currency\":\"BTC\",\"btc_dest_address\":\"19y91nJyzXsLEuR7Nj9pc3o5SeHNc8A9RW\"}";

        xlaToApi.createOrder(0.1, "19y91nJyzXsLEuR7Nj9pc3o5SeHNc8A9RW", mockOrderXlaToCallback);

        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertEquals(validBody, body);
    }

    @Test
    public void createOrder_wasSuccessfulShouldRespondWithOrder()
            throws TimeoutException, InterruptedException {
        final double amount = 1.23456789;
        final String address = "19y91nJyzXsLEuR7Nj9pc3o5SeHNc8A9RW";
        final String uuid = "xlato-abcdef";
        final String state = "TO_BE_CREATED";
        MockResponse jsonMockResponse = new MockResponse().setBody(
                createMockCreateOrderResponse(amount, address, uuid, state));
        mockWebServer.enqueue(jsonMockResponse);

        xlaToApi.createOrder(amount, address, new XlaToCallback<CreateOrder>() {
            @Override
            public void onSuccess(final CreateOrder order) {
                waiter.assertEquals(order.getBtcAmount(), amount);
                waiter.assertEquals(order.getBtcDestAddress(), address);
                waiter.assertEquals(order.getState(), state);
                waiter.assertEquals(order.getUuid(), uuid);
                waiter.resume();
            }

            @Override
            public void onError(final Exception e) {
                waiter.fail(e);
                waiter.resume();
            }
        });
        waiter.await();
    }

    @Test
    public void createOrder_wasNotSuccessfulShouldCallOnError()
            throws TimeoutException, InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        xlaToApi.createOrder(0.5, "19y91nJyzXsLEuR7Nj9pc3o5SeHNc8A9RW", new XlaToCallback<CreateOrder>() {
            @Override
            public void onSuccess(final CreateOrder order) {
                waiter.fail();
                waiter.resume();
            }

            @Override
            public void onError(final Exception e) {
                waiter.assertTrue(e instanceof XlaToException);
                waiter.assertTrue(((XlaToException) e).getCode() == 500);
                waiter.resume();
            }

        });
        waiter.await();
    }

    @Test
    public void createOrder_malformedAddressShouldCallOnError()
            throws TimeoutException, InterruptedException {
        mockWebServer.enqueue(new MockResponse().
                setResponseCode(400).
                setBody("{\"error_msg\":\"malformed bitcoin address\",\"error\":\"XLATO-ERROR-002\"}"));
        xlaToApi.createOrder(0.5, "xxx", new XlaToCallback<CreateOrder>() {
            @Override
            public void onSuccess(final CreateOrder order) {
                waiter.fail();
                waiter.resume();
            }

            @Override
            public void onError(final Exception e) {
                waiter.assertTrue(e instanceof XlaToException);
                XlaToException xlaEx = (XlaToException) e;
                waiter.assertTrue(xlaEx.getCode() == 400);
                waiter.assertNotNull(xlaEx.getError());
                waiter.assertEquals(xlaEx.getError().getErrorId(), XlaToError.Error.XLATO_ERROR_002);
                waiter.assertEquals(xlaEx.getError().getErrorMsg(), "malformed bitcoin address");
                waiter.resume();
            }

        });
        waiter.await();
    }

    private String createMockCreateOrderResponse(final double amount, final String address,
                                                 final String uuid, final String state) {
        return "{\n"
                + "    \"state\": \"" + state + "\",\n"
                + "    \"btc_amount\": \"" + amount + "\",\n"
                + "    \"btc_dest_address\": \"" + address + "\",\n"
                + "    \"uuid\": \"" + uuid + "\"\n"
                + "}";
    }
}