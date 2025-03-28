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

package io.scalaproject.vault.fragment.send;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.text.InputType;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import io.scalaproject.vault.Config;
import io.scalaproject.vault.OnBackPressedListener;
import io.scalaproject.vault.OnUriScannedListener;
import io.scalaproject.vault.R;
import io.scalaproject.vault.WalletActivity;
import io.scalaproject.vault.data.BarcodeData;
import io.scalaproject.vault.data.PendingTx;
import io.scalaproject.vault.data.TxData;
import io.scalaproject.vault.data.TxDataBtc;
import io.scalaproject.vault.data.UserNotes;
import io.scalaproject.vault.layout.SpendViewPager;
import io.scalaproject.vault.model.PendingTransaction;
import io.scalaproject.vault.util.Helper;
import io.scalaproject.vault.util.Notice;
import io.scalaproject.vault.widget.DotBar;
import io.scalaproject.vault.widget.Toolbar;

import java.lang.ref.WeakReference;
import java.util.Objects;

import timber.log.Timber;

public class SendFragment extends Fragment
        implements SendAddressWizardFragment.Listener,
        SendAmountWizardFragment.Listener,
        SendConfirmWizardFragment.Listener,
        SendSuccessWizardFragment.Listener,
        OnBackPressedListener, OnUriScannedListener {

    final static public int MIXIN = 11;

    private Listener activityCallback;

    public interface Listener {
        SharedPreferences getPrefs();

        long getTotalFunds();

        boolean isStealthMode();

        void onPrepareSend(String tag, TxData data);

        boolean verifyWalletPassword(String password);

        void onSend(UserNotes notes);

        void onSaveContact(String name, String address);

        void onDisposeRequest();

        void onFragmentDone();

        void setToolbarButton(int type);

        void setTitle(String title);

        void setSubtitle(String subtitle);

        void setOnUriScannedListener(OnUriScannedListener onUriScannedListener);
    }

    private EditText etDummy;
    private Drawable arrowPrev;
    private Drawable arrowNext;

    private View llNavBar;
    private DotBar dotBar;
    private Button bPrev;
    private Button bNext;

    private Button bDone;

    static private final int MAX_FALLBACK = Integer.MAX_VALUE;

    public static SendFragment newInstance(String uri) {
        SendFragment f = new SendFragment();
        Bundle args = new Bundle();
        args.putString(WalletActivity.REQUEST_URI, uri);
        f.setArguments(args);
        return f;
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final View view = inflater.inflate(R.layout.fragment_send, container, false);

        llNavBar = view.findViewById(R.id.llNavBar);
        bDone = view.findViewById(R.id.bDone);

        dotBar = view.findViewById(R.id.dotBar);
        bPrev = view.findViewById(R.id.bPrev);
        bNext = view.findViewById(R.id.bNext);
        arrowPrev = getResources().getDrawable(R.drawable.ic_navigate_prev_white_24dp);
        arrowNext = getResources().getDrawable(R.drawable.ic_navigate_next_white_24dp);

        ViewGroup llNotice = view.findViewById(R.id.llNotice);
        Notice.showAll(llNotice, ".*_send");

        spendViewPager = view.findViewById(R.id.pager);
        pagerAdapter = new SpendPagerAdapter(getChildFragmentManager());
        spendViewPager.setOffscreenPageLimit(pagerAdapter.getCount()); // load & keep all pages in cache
        spendViewPager.setAdapter(pagerAdapter);

        spendViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            private int fallbackPosition = MAX_FALLBACK;
            private int currentPosition = 0;

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int newPosition) {
                Timber.d("onPageSelected=%d/%d", newPosition, fallbackPosition);
                if (fallbackPosition < newPosition) {
                    spendViewPager.setCurrentItem(fallbackPosition);
                } else {
                    pagerAdapter.getFragment(currentPosition).onPauseFragment();
                    pagerAdapter.getFragment(newPosition).onResumeFragment();
                    updatePosition(newPosition);
                    currentPosition = newPosition;
                    fallbackPosition = MAX_FALLBACK;
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_DRAGGING) {
                    if (!spendViewPager.validateFields(spendViewPager.getCurrentItem())) {
                        fallbackPosition = spendViewPager.getCurrentItem();
                    } else {
                        fallbackPosition = spendViewPager.getCurrentItem() + 1;
                    }
                }
            }
        });

        bPrev.setOnClickListener(v -> spendViewPager.previous());

        bNext.setOnClickListener(v -> spendViewPager.next());

        bDone.setOnClickListener(v -> {
            Timber.d("bDone.onClick");
            activityCallback.onFragmentDone();
        });

        updatePosition(0);

        etDummy = view.findViewById(R.id.etDummy);
        etDummy.setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etDummy.requestFocus();
        Helper.hideKeyboard(getActivity());

        Bundle args = getArguments();
        if (args != null) {
            String uri = args.getString(WalletActivity.REQUEST_URI);
            Timber.d("URI: %s", uri);
            if (uri != null) {
                BarcodeData.fromString(uri, (data) -> {
                    barcodeData = data;
                    Timber.d("barcodeData: %s", barcodeData != null ? barcodeData.toString() : "null");
                });
            }
        }

        return view;
    }

    void updatePosition(int position) {
        dotBar.setActiveDot(position);
        CharSequence nextLabel = pagerAdapter.getPageTitle(position + 1);
        bNext.setText(nextLabel);
        if (nextLabel != null) {
            bNext.setCompoundDrawablesWithIntrinsicBounds(null, null, arrowNext, null);
        } else {
            bNext.setCompoundDrawables(null, null, null, null);
        }
        CharSequence prevLabel = pagerAdapter.getPageTitle(position - 1);
        bPrev.setText(prevLabel);
        if (prevLabel != null) {
            bPrev.setCompoundDrawablesWithIntrinsicBounds(arrowPrev, null, null, null);
        } else {
            bPrev.setCompoundDrawables(null, null, null, null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume");
        activityCallback.setSubtitle(getString(R.string.send_title));
        if (spendViewPager.getCurrentItem() == SpendPagerAdapter.POS_SUCCESS) {
            activityCallback.setToolbarButton(Toolbar.BUTTON_NONE);
        } else {
            activityCallback.setToolbarButton(Toolbar.BUTTON_CANCEL);
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        Timber.d("onAttach %s", context);
        super.onAttach(context);
        if (context instanceof Listener) {
            activityCallback = (Listener) context;
            activityCallback.setOnUriScannedListener(this);
        } else {
            throw new ClassCastException(context.toString() + " must implement Listener");
        }
    }

    @Override
    public void onDetach() {
        activityCallback.setOnUriScannedListener(null);
        super.onDetach();
    }

    private SpendViewPager spendViewPager;
    private SpendPagerAdapter pagerAdapter;

    @Override
    public boolean onBackPressed() {
        if (isComitted()) return true; // no going back
        if (spendViewPager.getCurrentItem() == 0) {
            return false;
        } else {
            spendViewPager.previous();
            return true;
        }
    }

    @Override
    public void onUriScanned(BarcodeData barcodeData) {
        if (spendViewPager.getCurrentItem() == SpendPagerAdapter.POS_ADDRESS) {
            final SendWizardFragment fragment = pagerAdapter.getFragment(SpendPagerAdapter.POS_ADDRESS);
            if (fragment instanceof SendAddressWizardFragment) {
                ((SendAddressWizardFragment) fragment).processScannedData(barcodeData);
            }
        }
    }

    enum Mode {
        XLA, BTC
    }

    Mode mode = Mode.XLA;

    @Override
    public void setMode(Mode aMode) {
        if (mode != aMode) {
            mode = aMode;
            switch (aMode) {
                case XLA:
                    txData = new TxData();
                    break;
                case BTC:
                    txData = new TxDataBtc();
                    break;
                default:
                    throw new IllegalArgumentException("Mode " + String.valueOf(aMode) + " unknown!");
            }
            requireView().post(() -> pagerAdapter.notifyDataSetChanged());
            Timber.d("New Mode = %s", mode.toString());
        }
    }

    @Override
    public Mode getMode() {
        return mode;
    }

    public class SpendPagerAdapter extends FragmentStatePagerAdapter {
        private static final int POS_ADDRESS = 0;
        private static final int POS_AMOUNT = 1;
        private static final int POS_CONFIRM = 2;
        private static final int POS_SUCCESS = 3;
        private int numPages = 3;

        SparseArray<WeakReference<SendWizardFragment>> myFragments = new SparseArray<>();

        public SpendPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        public void addSuccess() {
            numPages++;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return numPages;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Timber.d("instantiateItem %d", position);
            SendWizardFragment fragment = (SendWizardFragment) super.instantiateItem(container, position);
            myFragments.put(position, new WeakReference<>(fragment));
            return fragment;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            Timber.d("destroyItem %d", position);
            myFragments.remove(position);
            super.destroyItem(container, position, object);
        }

        public SendWizardFragment getFragment(int position) {
            WeakReference<SendWizardFragment> ref = myFragments.get(position);
            if (ref != null)
                return myFragments.get(position).get();
            else
                return null;
        }

        @NonNull
        @Override
        public SendWizardFragment getItem(int position) {
            Timber.d("getItem(%d) CREATE", position);
            Timber.d("Mode=%s", mode.toString());
            if (mode == Mode.XLA) {
                return switch (position) {
                    case POS_ADDRESS -> SendAddressWizardFragment.newInstance(SendFragment.this);
                    case POS_AMOUNT -> SendAmountWizardFragment.newInstance(SendFragment.this);
                    case POS_CONFIRM -> SendConfirmWizardFragment.newInstance(SendFragment.this);
                    case POS_SUCCESS -> SendSuccessWizardFragment.newInstance(SendFragment.this);
                    default ->
                            throw new IllegalArgumentException("no such send position(" + position + ")");
                };
            } else if (mode == Mode.BTC) {
                return switch (position) {
                    case POS_ADDRESS -> SendAddressWizardFragment.newInstance(SendFragment.this);
                    case POS_AMOUNT -> SendBtcAmountWizardFragment.newInstance(SendFragment.this);
                    case POS_CONFIRM -> SendBtcConfirmWizardFragment.newInstance(SendFragment.this);
                    case POS_SUCCESS -> SendBtcSuccessWizardFragment.newInstance(SendFragment.this);
                    default ->
                            throw new IllegalArgumentException("no such send position(" + position + ")");
                };
            } else {
                throw new IllegalStateException("Unknown mode!");
            }
        }

        @Override
        public CharSequence getPageTitle(int position) {
            Timber.d("getPageTitle(%d)", position);
            if (position >= numPages) return null;
            return switch (position) {
                case POS_ADDRESS -> getString(R.string.send_address_title);
                case POS_AMOUNT -> getString(R.string.send_amount_title);
                case POS_CONFIRM -> getString(R.string.send_confirm_title);
                case POS_SUCCESS -> getString(R.string.send_success_title);
                default -> null;
            };
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            Timber.d("getItemPosition %s", String.valueOf(object));
            if (object instanceof SendAddressWizardFragment) {
                // keep these pages
                return POSITION_UNCHANGED;
            } else {
                return POSITION_NONE;
            }
        }
    }

    @Override
    public TxData getTxData() {
        return txData;
    }

    private TxData txData = new TxData();

    private BarcodeData barcodeData;

    @Override
    public void saveContact(String name, String address) {
        activityCallback.onSaveContact(name, address);
    }

    // Listeners
    @Override
    public void setBarcodeData(BarcodeData data) {
        barcodeData = data;
    }

    @Override
    public BarcodeData getBarcodeData() {
        return barcodeData;
    }

    @Override
    public BarcodeData popBarcodeData() {
        Timber.d("POPPED");
        BarcodeData data = barcodeData;
        barcodeData = null;
        return data;
    }

    boolean isComitted() {
        return committedTx != null;
    }

    PendingTx committedTx;

    @Override
    public PendingTx getCommittedTx() {
        return committedTx;
    }


    @Override
    public void commitTransaction() {
        Timber.d("REALLY SEND");
        disableNavigation(); // committed - disable all navigation
        activityCallback.onSend(txData.getUserNotes());
        committedTx = pendingTx;
    }

    void disableNavigation() {
        spendViewPager.allowSwipe(false);
    }

    void enableNavigation() {
        spendViewPager.allowSwipe(true);
    }

    @Override
    public void enableDone() {
        llNavBar.setVisibility(View.INVISIBLE);
        bDone.setVisibility(View.VISIBLE);
    }

    public Listener getActivityCallback() {
        return activityCallback;
    }


    // callbacks from send service

    public void onTransactionCreated(final String txTag, final PendingTransaction pendingTransaction) {
        final SendConfirm confirm = getSendConfirm();
        if (confirm != null) {
            pendingTx = new PendingTx(pendingTransaction);
            confirm.transactionCreated(txTag, pendingTransaction);
        } else {
            // not in confirm fragment => dispose & move on
            disposeTransaction();
        }
    }

    @Override
    public void disposeTransaction() {
        pendingTx = null;
        activityCallback.onDisposeRequest();
    }

    PendingTx pendingTx;

    public PendingTx getPendingTx() {
        return pendingTx;
    }

    public void onCreateTransactionFailed(String errorText) {
        final SendConfirm confirm = getSendConfirm();
        if (confirm != null) {
            confirm.createTransactionFailed(errorText);
        }
    }

    SendConfirm getSendConfirm() {
        final SendWizardFragment fragment = pagerAdapter.getFragment(SpendPagerAdapter.POS_CONFIRM);
        if (fragment instanceof SendConfirm) {
            return (SendConfirm) fragment;
        } else {
            return null;
        }
    }

    public void onTransactionSent(final String txId) {
        Timber.d("txid=%s", txId);
        pagerAdapter.addSuccess();
        Timber.d("numPages=%d", Objects.requireNonNull(spendViewPager.getAdapter()).getCount());
        activityCallback.setToolbarButton(Toolbar.BUTTON_NONE);
        spendViewPager.setCurrentItem(SpendPagerAdapter.POS_SUCCESS);
    }

    public void onSendTransactionFailed(final String error) {
        Timber.d("error=%s", error);
        committedTx = null;
        final SendConfirm confirm = getSendConfirm();
        if (confirm != null) {
            confirm.sendFailed(getString(R.string.status_transaction_failed, error));
        }
        enableNavigation();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.send_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    // xla.to info box
    private static final String PREF_SHOW_XLATO_ENABLED = "info_xlato_enabled_send";

    boolean showxlatoEnabled = true;

    void loadPrefs() {
        String enabled = Config.read("PREF_SHOW_XLATO_ENABLED");
        showxlatoEnabled = !(enabled.isEmpty() || enabled.equals("0"));

    }

    void savexlaToPrefs() {
        Config.write("PREF_SHOW_XLATO_ENABLED", showxlatoEnabled ? "1" : "0");
    }

}
