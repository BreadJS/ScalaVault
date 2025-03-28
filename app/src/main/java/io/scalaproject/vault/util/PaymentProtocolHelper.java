/*
 * Copyright (c) 2018 m2049r
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

// Specs from https://openalias.org/

package io.scalaproject.vault.util;

import io.scalaproject.vault.data.BarcodeData;
import io.scalaproject.vault.xlato.api.CreateOrder;
import io.scalaproject.vault.xlato.api.XlaToApi;
import io.scalaproject.vault.xlato.api.XlaToCallback;
import io.scalaproject.vault.xlato.network.XlaToApiImpl;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import timber.log.Timber;

public class PaymentProtocolHelper {

    //https://bitpay.com/i/xxx
    public static boolean isHttp(String string) {
        if ((string == null) || (string.isEmpty())) return false;
        try {
            // new URL(string).toURI() checks if its a real url
            return new URL(string).toURI().getScheme().startsWith("http");
        } catch (MalformedURLException | URISyntaxException ex) {
            return false;
        }
    }

    // guess if the string may be a valid payment uri
    //https://bitpay.com/i/xxx
    //bitcoin:?r=https://bitpay.com/i/xxx
    public static String getBip70(String bip72Or70) {
        if ((bip72Or70 == null) || (bip72Or70.isEmpty())) return null;
        if (isHttp(bip72Or70)) return bip72Or70;
        BarcodeData bc = BarcodeData.parseBitcoinUri(bip72Or70);
        if (bc == null) return null;
        return bc.bip70;
    }

    public interface OnResolvedListener {
        void onResolved(BarcodeData.Asset asset, String address, double amount, String bip70);

        void onFailure(Exception ex);

    }

    static public boolean resolve(final String bip70, final OnResolvedListener resolvedListener) {
        if ((bip70 == null) || (bip70.isEmpty()))
            return false; //pointless trying to lookup nothing
        Timber.d("Resolving %s", bip70);
        getxlaToApi().createOrder(bip70, new XlaToCallback<CreateOrder>() {
            @Override
            public void onSuccess(CreateOrder createOrder) {
                if (resolvedListener != null) {
                    resolvedListener.onResolved(BarcodeData.Asset.BTC,
                            createOrder.getBtcDestAddress(),
                            createOrder.getBtcAmount(),
                            createOrder.getBtcBip70());
                }
            }

            @Override
            public void onError(Exception ex) {
                if (resolvedListener != null) {
                    resolvedListener.onFailure(ex);
                }
            }
        });
        return true;
    }

    static private XlaToApi xlaToApi = null;

    static private XlaToApi getxlaToApi() {
        if (xlaToApi == null) {
            synchronized (PaymentProtocolHelper.class) {
                if (xlaToApi == null) {
                    xlaToApi = new XlaToApiImpl(OkHttpHelper.getOkHttpClient(),
                            Helper.getxlaToBaseUrl());
                }
            }
        }
        return xlaToApi;
    }
}