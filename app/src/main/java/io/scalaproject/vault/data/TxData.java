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

package io.scalaproject.vault.data;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import io.scalaproject.vault.model.PendingTransaction;

// https://stackoverflow.com/questions/2139134/how-to-send-an-object-from-one-android-activity-to-another-using-intents
public class TxData implements Parcelable {

    public TxData() {
    }

    public TxData(TxData txData) {
        this.dstAddr = txData.dstAddr;
        this.amount = txData.amount;
        this.mixin = txData.mixin;
        this.priority = txData.priority;
    }

    public TxData(String dstAddr,
                  long amount,
                  int mixin,
                  PendingTransaction.Priority priority) {
        this.dstAddr = dstAddr;
        this.amount = amount;
        this.mixin = mixin;
        this.priority = priority;
    }

    public String getDestinationAddress() {
        return dstAddr;
    }

    public long getAmount() {
        return amount;
    }

    public int getMixin() {
        return mixin;
    }

    public PendingTransaction.Priority getPriority() {
        return priority;
    }

    public void setDestinationAddress(String dstAddr) {
        this.dstAddr = dstAddr;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public void setMixin(int mixin) {
        this.mixin = mixin;
    }

    public void setPriority(PendingTransaction.Priority priority) {
        this.priority = priority;
    }

    public UserNotes getUserNotes() {
        return userNotes;
    }

    public void setUserNotes(UserNotes userNotes) {
        this.userNotes = userNotes;
    }

    private String dstAddr;
    private long amount;
    private int mixin;
    private PendingTransaction.Priority priority;

    private UserNotes userNotes;

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(dstAddr);
        out.writeLong(amount);
        out.writeInt(mixin);
        out.writeInt(priority.getValue());
    }

    // this is used to regenerate your object. All Parcelables must have a CREATOR that implements these two methods
    public static final Parcelable.Creator<TxData> CREATOR = new Parcelable.Creator<TxData>() {
        public TxData createFromParcel(Parcel in) {
            return new TxData(in);
        }

        public TxData[] newArray(int size) {
            return new TxData[size];
        }
    };

    protected TxData(Parcel in) {
        dstAddr = in.readString();
        amount = in.readLong();
        mixin = in.readInt();
        priority = PendingTransaction.Priority.fromInteger(in.readInt());

    }

    @Override
    public int describeContents() {
        return 0;
    }

    @NonNull
    @Override
    public String toString() {
        return "dstAddr:" +
                dstAddr +
                ",amount:" +
                amount +
                ",mixin:" +
                mixin +
                ",priority:" +
                priority;
    }
}
