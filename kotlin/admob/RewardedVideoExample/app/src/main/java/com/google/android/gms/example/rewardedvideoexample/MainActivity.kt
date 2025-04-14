package com.google.android.gms.example.rewardedvideoexample

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.example.rewardedvideoexample.databinding.ActivityMainBinding
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

  private val isMobileAdsInitializeCalled = AtomicBoolean(false)
  private lateinit var binding: ActivityMainBinding
  private lateinit var googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
  private var coinCount: Int = 0
  private var canPlayGame = false;
  private var countdownTimer: CountDownTimer? = null
  private var playingGame = false
  private var gameOver = true
  private var gamePaused = false
  private var isLoading = false
  private var rewardedAd: RewardedAd? = null
  private var timeRemaining: Long = 0L

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // Log the Mobile Ads SDK version.
    Log.d(TAG, "Google Mobile Ads SDK Version: " + MobileAds.getVersion())

    googleMobileAdsConsentManager = GoogleMobileAdsConsentManager.getInstance(this)
    googleMobileAdsConsentManager.gatherConsent(this) { error ->
      if (error != null) {
        // Consent not obtained in current session.
        Log.d(TAG, "${error.errorCode}: ${error.message}")
      }

      if (googleMobileAdsConsentManager.canRequestAds) {
        initializeMobileAdsSdk()
      }

      if (googleMobileAdsConsentManager.isPrivacyOptionsRequired) {
        // Regenerate the options menu to include a privacy setting.
        invalidateOptionsMenu()
      }

      updateUI()
    }

    // This sample attempts to load ads using consent obtained in the previous session.
    if (googleMobileAdsConsentManager.canRequestAds) {
      initializeMobileAdsSdk()
    }

    // Create the "retry" button, which tries to show a rewarded video ad between game plays.
    binding.playGameButton.visibility = View.INVISIBLE
    binding.playGameButton.setOnClickListener {
      startGame()
      if (!isLoading && googleMobileAdsConsentManager.canRequestAds) {
        loadRewardedAd()
      }
    }

    // Create the "show" button, which shows a rewarded video if one is loaded.
    //binding.showVideoButton.visibility = View.INVISIBLE
    binding.showVideoButton.setOnClickListener {
      showRewardedVideo()
    }

    updateUI()
  }

  public override fun onPause() {
    super.onPause()
    pauseGame()
  }

  public override fun onResume() {
    super.onResume()
    resumeGame()
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.action_menu, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    val menuItemView = findViewById<View>(item.itemId)
    val activity = this
    PopupMenu(this, menuItemView).apply {
      menuInflater.inflate(R.menu.popup_menu, menu)
      menu
        .findItem(R.id.privacy_settings)
        .setVisible(googleMobileAdsConsentManager.isPrivacyOptionsRequired)
      show()
      setOnMenuItemClickListener { popupMenuItem ->
        when (popupMenuItem.itemId) {
          R.id.privacy_settings -> {
            pauseGame()
            // Handle changes to user consent.
            googleMobileAdsConsentManager.showPrivacyOptionsForm(activity) { formError ->
              if (formError != null) {
                Toast.makeText(activity, formError.message, Toast.LENGTH_SHORT).show()
              }
              resumeGame()
            }
            true
          }
          R.id.ad_inspector -> {
            MobileAds.openAdInspector(activity) { error ->
              // Error will be non-null if ad inspector closed due to an error.
              error?.let { Toast.makeText(activity, it.message, Toast.LENGTH_SHORT).show() }
            }
            true
          }
          // Handle other branches here.
          else -> false
        }
      }
      return super.onOptionsItemSelected(item)
    }
  }

  private fun pauseGame() {
    if (gameOver || gamePaused) {
      return
    }
    countdownTimer?.cancel()
    gamePaused = true

    updateUI()
  }

  private fun resumeGame() {
    if (gameOver || !gamePaused) {
      return
    }
    PlayGameSimulationTimer(timeRemaining)
    gamePaused = false

    updateUI()
  }

  private fun loadRewardedAd() {
    if (rewardedAd == null) {
      isLoading = true

      RewardedAd.load(
        this,
        AD_UNIT_ID,
        AdRequest.Builder().build(),
        object : RewardedAdLoadCallback() {

          override fun onAdLoaded(ad: RewardedAd) {
            Log.d(TAG, "REWARDED ADD WAS LOADED.")
            rewardedAd = ad
            isLoading = false
          }

          override fun onAdFailedToLoad(adError: LoadAdError) {
            Log.d(TAG, "REWARDED ADD WAS NOT LOADED.")

            Log.d(TAG, adError.message)
            isLoading = false
            rewardedAd = null
          }
        },
      )
    }
  }

  private fun updateUI() {
    binding.coinCountText.text = "Coins: $coinCount"

    canPlayGame = if (coinCount >= GAME_PLAY_COST) true else false
    binding.playGameButton.visibility = if (canPlayGame == true) (View.VISIBLE) else View.INVISIBLE
    binding.showVideoButton.visibility = View.VISIBLE

    if (playingGame == true) {
      binding.gameTitle.text = getString(R.string.playing_game)
    }
    else {
      binding.gameTitle.text = if (canPlayGame) (getString(R.string.possible_game)) else (getString(R.string.impossible_game))
    }
  }

  private fun addCoins(coins: Int) {
    coinCount += coins
    updateUI()
  }

  private fun consumeCoins(coins: Int) {
    coinCount -= coins
    updateUI()
  }

  private fun startGame() {
    updateUI()

    if (canPlayGame) {
      consumeCoins(GAME_PLAY_COST)

      gamePaused = false
      gameOver = false

      PlayGameSimulationTimer(COUNTER_TIME)
    }
  }

  // Create the game timer, which counts down to the end of the level
  // and shows the "retry" button.
  private fun PlayGameSimulationTimer(time: Long) {
    countdownTimer?.cancel()

    playingGame = true
    updateUI()

    countdownTimer =
      object : CountDownTimer(time * 1000, 50) {
        override fun onTick(millisUnitFinished: Long) {
          timeRemaining = millisUnitFinished / 1000 + 1
          binding.timer.text = "seconds remaining: $timeRemaining"
        }

        override fun onFinish() {
          binding.showVideoButton.visibility = View.VISIBLE
          binding.timer.text = "The game has ended!"

          if (timeRemaining == 1L) {
            addCoins(GAME_OVER_REWARD)
          }
          else  updateUI()

          playingGame = false
          gameOver = true

        }
      }

    countdownTimer?.start()
  }

  private fun showRewardedVideo() {
    if (rewardedAd == null) {
      Log.d(TAG, "showRewardedVideo, Ad was not actually loaded.")

      if (isLoading){
        Log.d(TAG, "showRewardedVideo, ad is currently loading.")
      }

      if (!isLoading && googleMobileAdsConsentManager.canRequestAds) {
        Log.d(TAG, "showRewardedVideo, try to reload it.")
        loadRewardedAd()
      }
    }

    if (rewardedAd != null) {
      Log.d(TAG, "showRewardedVideo, ad was loadded")

      binding.showVideoButton.visibility = View.INVISIBLE

      rewardedAd?.fullScreenContentCallback =
        object : FullScreenContentCallback() {
          override fun onAdDismissedFullScreenContent() {
            Log.d(TAG, "FullScreenContentCallback, Ad was dismissed.")
            // Don't forget to set the ad reference to null so you
            // don't show the ad a second time.
            rewardedAd = null
            if (googleMobileAdsConsentManager.canRequestAds) {
              loadRewardedAd()
            }
          }

          override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            Log.d(TAG, "FullScreenContentCallback, Ad failed to show.")
            // Don't forget to set the ad reference to null so you
            // don't show the ad a second time.
            rewardedAd = null
          }

          override fun onAdShowedFullScreenContent() {
            Log.d(TAG, "FullScreenContentCallback, Ad showed fullscreen content.")
            // Called when ad is dismissed.
          }
        }

      rewardedAd?.show(
        this,
        OnUserEarnedRewardListener { rewardItem ->
          // Handle the reward.
          val rewardAmount = rewardItem.amount
          val rewardType = rewardItem.type

          addCoins(rewardAmount)
          Log.d("TAG", "FullScreenContentCallback, User earned the reward.")
        },
      )
    }
  }

  private fun initializeMobileAdsSdk() {
    if (isMobileAdsInitializeCalled.getAndSet(true)) {
      return
    }

    // Set your test devices.
    MobileAds.setRequestConfiguration(
      RequestConfiguration.Builder().setTestDeviceIds(listOf(TEST_DEVICE_HASHED_ID)).build()
    )

    val backgroundScope = CoroutineScope(Dispatchers.IO)
    backgroundScope.launch {
      // Initialize the Google Mobile Ads SDK on a background thread.
      MobileAds.initialize(this@MainActivity) {}
      runOnUiThread {
        // Load an ad on the main thread.
        loadRewardedAd()
      }
    }
  }

  companion object {
    // This is an ad unit ID for a test ad. Replace with your own rewarded ad unit ID.
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val COUNTER_TIME = 10L
    private const val GAME_OVER_REWARD = 1
    private const val GAME_PLAY_COST = 5
    private const val TAG = "MainActivity"

    // Check your logcat output for the test device hashed ID e.g.
    // "Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("ABCDEF012345"))
    // to get test ads on this device" or
    // "Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("ABCDEF012345") to set this as
    // a debug device".
    const val TEST_DEVICE_HASHED_ID = "ABCDEF012345"
  }
}
