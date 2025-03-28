/*
 * Copyright (c) 2017 m2049r
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

package io.scalaproject.vault.service.exchange.api;

public class ExchangeException extends Exception {
    private final int code;
    private final String errorMsg;

    public String getErrorMsg() {
        return errorMsg;
    }

    public ExchangeException(final int code) {
        super();
        this.code = code;
        this.errorMsg = null;
    }

    public ExchangeException(final String errorMsg) {
        super();
        this.code = 0;
        this.errorMsg = errorMsg;
    }

    public ExchangeException(final int code, final String errorMsg) {
        super();
        this.code = code;
        this.errorMsg = errorMsg;
    }

    public int getCode() {
        return code;
    }
}