package com.enjoy.recorder.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {

    // =========================================================================
    // GOOGLE ADMOB TEST AD UNIT IDS (Replace with production IDs before release)
    // =========================================================================
    const val ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    private var appOpenAd: AppOpenAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        MobileAds.initialize(context) {
            isInitialized = true
            loadAppOpenAd(context)
            loadInterstitialAd(context)
            loadRewardedAd(context)
        }
    }

    fun loadAppOpenAd(context: Context) {
        val request = AdRequest.Builder().build()
        AppOpenAd.load(context, APP_OPEN_AD_UNIT_ID, request, object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenAd = ad
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                appOpenAd = null
            }
        })
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onDismissed: () -> Unit) {
        val ad = appOpenAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    loadAppOpenAd(activity)
                    onDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    onDismissed()
                }
            }
            ad.show(activity)
        } else {
            loadAppOpenAd(activity)
            onDismissed()
        }
    }

    fun loadInterstitialAd(context: Context) {
        val request = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, request, object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialAd = ad
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                interstitialAd = null
            }
        })
    }

    fun showInterstitial(activity: Activity, onFinished: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitialAd(activity)
                    onFinished()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    onFinished()
                }
            }
            ad.show(activity)
        } else {
            loadInterstitialAd(activity)
            onFinished()
        }
    }

    fun loadRewardedAd(context: Context) {
        val request = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_AD_UNIT_ID, request, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
            }
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                rewardedAd = null
            }
        })
    }

    fun showRewarded(activity: Activity, onUserEarnedReward: () -> Unit, onClosed: () -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    loadRewardedAd(activity)
                    onClosed()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    onClosed()
                }
            }
            ad.show(activity) { onUserEarnedReward() }
        } else {
            loadRewardedAd(activity)
            onClosed()
        }
    }
}
