/*
 * Copyright (c) 2016, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3;

import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.SkuDetails;
import com.psiphon3.billing.BillingRepository;
import com.psiphon3.billing.PaymentChooserBillingViewModel;
import com.psiphon3.psiphonlibrary.LocalizedActivities;
import com.psiphon3.psiphonlibrary.Utils;
import com.psiphon3.subscription.R;

import java.text.NumberFormat;
import java.util.Currency;

import io.reactivex.disposables.Disposable;

public class PaymentChooserActivity extends LocalizedActivities.AppCompatActivity {
    private static final String TAG = "PaymentChooserActivity";
    public static final String SKU_DETAILS_EXTRA = "SKU_DETAILS_EXTRA";

    private Disposable skuDetailsDisposable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PaymentChooserBillingViewModel billingViewModel = ViewModelProviders.of(this).get(PaymentChooserBillingViewModel.class);

        setContentView(R.layout.payment_chooser);

        skuDetailsDisposable = billingViewModel.getAllSkuDetails()
                .subscribe(skuDetailsList -> {
                    for (SkuDetails skuDetails : skuDetailsList) {
                        int buttonResId = 0;
                        float pricePerDay = 0f;
                        // Calculate life time in days for subscriptions
                        if (skuDetails.getType().equals(BillingClient.SkuType.SUBS)) {
                            if (skuDetails.getSubscriptionPeriod().equals("P1W")) {
                                pricePerDay = skuDetails.getPriceAmountMicros() / 1000000.0f / 7f;
                            } else if (skuDetails.getSubscriptionPeriod().equals("P1M")) {
                                pricePerDay = skuDetails.getPriceAmountMicros() / 1000000.0f / (365f / 12);
                            } else if (skuDetails.getSubscriptionPeriod().equals("P3M")) {
                                pricePerDay = skuDetails.getPriceAmountMicros() / 1000000.0f / (365f / 4);
                            } else if (skuDetails.getSubscriptionPeriod().equals("P6M")) {
                                pricePerDay = skuDetails.getPriceAmountMicros() / 1000000.0f / (365f / 2);
                            } else if (skuDetails.getSubscriptionPeriod().equals("P1Y")) {
                                pricePerDay = skuDetails.getPriceAmountMicros() / 1000000.0f / 365f;
                            }
                            if (pricePerDay == 0f) {
                                Utils.MyLog.g("PaymentChooserActivity error: bad subscription period for sku: " + skuDetails);
                                return;
                            }

                            // Get button resource ID
                            if (skuDetails.getSku().equals(BillingRepository.IAB_LIMITED_MONTHLY_SUBSCRIPTION_SKU)) {
                                buttonResId = R.id.limitedSubscription;
                            } else if (skuDetails.getSku().equals(BillingRepository.IAB_UNLIMITED_MONTHLY_SUBSCRIPTION_SKU)) {
                                buttonResId = R.id.unlimitedSubscription;
                            }
                        } else {
                            // Get pre-calculated life time in days for time passes
                            String timepassSku = skuDetails.getSku();
                            long lifetimeInDays = BillingRepository.IAB_TIMEPASS_SKUS_TO_DAYS.get(timepassSku);
                            if (lifetimeInDays == 0L) {
                                Utils.MyLog.g("PaymentChooserActivity error: unknown timepass period for sku: " + skuDetails);
                                return;
                            }
                            // Get button resource ID
                            buttonResId = getResources().getIdentifier("timepass" + lifetimeInDays, "id", getPackageName());
                            pricePerDay = skuDetails.getPriceAmountMicros() / 1000000.0f / lifetimeInDays;
                        }

                        if (buttonResId == 0) {
                            Utils.MyLog.g("PaymentChooserActivity error: no button resource for sku: " + skuDetails);
                            return;
                        }

                        setUpButton(buttonResId, skuDetails, pricePerDay);
                    }
                }, __ -> {
                    // The error should be already logged upstream, just send and empty sku back,
                    // it will be handled in StatusActivity::onActivityResult
                    Intent intent = getIntent();
                    intent.putExtra(SKU_DETAILS_EXTRA, "");
                    setResult(RESULT_OK, intent);
                    finish();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        skuDetailsDisposable.dispose();
    }

    /**
     * Sets up the payment chooser buttons. This includes adding a tag with the product SKU and
     * putting the price-per-day value into the label.
     *
     * @param buttonId   ID of the button to set up.
     * @param skuDetails Info about the SKU.
     */
    private void setUpButton(int buttonId, SkuDetails skuDetails, float pricePerDay) {
        Button button = findViewById(buttonId);

        // If the formatting for pricePerDayText fails below, use this as a default.
        String pricePerDayText = skuDetails.getPriceCurrencyCode() + " " + pricePerDay;

        try {
            Currency currency = Currency.getInstance(skuDetails.getPriceCurrencyCode());
            NumberFormat priceFormatter = NumberFormat.getCurrencyInstance();
            priceFormatter.setCurrency(currency);
            pricePerDayText = priceFormatter.format(pricePerDay);
        } catch (IllegalArgumentException e) {
            // do nothing
        }

        String formatString = button.getText().toString();
        String buttonText = String.format(formatString, skuDetails.getPrice(), pricePerDayText);
        button.setText(buttonText);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> {
            Utils.MyLog.g("PaymentChooserActivity purchase button clicked.");
            Intent intent = getIntent();
            intent.putExtra(SKU_DETAILS_EXTRA, skuDetails.getOriginalJson());
            setResult(RESULT_OK, intent);
            finish();
        });
    }
}
