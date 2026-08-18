package com.geoscanner.app.ui.splash;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.geoscanner.app.R;
import com.geoscanner.app.ui.main.MainActivity;
import com.geoscanner.app.utils.LocaleHelper;

public class SplashActivity extends AppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView ivLogo;
    private android.view.View fireGlow;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private ProgressBar progressBar;
    private MediaPlayer mediaPlayer;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        getWindow().setStatusBarColor(0xFF000000);
        getWindow().setNavigationBarColor(0xFF000000);

        ivLogo = findViewById(R.id.ivSplashLogo);
        fireGlow = findViewById(R.id.fireGlow);
        tvTitle = findViewById(R.id.tvSplashTitle);
        tvSubtitle = findViewById(R.id.tvSplashSubtitle);
        progressBar = findViewById(R.id.progressSplash);

        startAnimations();
    }

    private void startAnimations() {
        handler.postDelayed(this::animateGlow, 200L);
        handler.postDelayed(this::animateLogoIn, 500L);
        handler.postDelayed(this::animateLogoBreathe, 1700L);
        handler.postDelayed(this::animateTitle, 1500L);
        handler.postDelayed(this::animateSubtitle, 2000L);
        handler.postDelayed(this::animateProgress, 2300L);
        handler.postDelayed(this::goToMain, 3500L);
    }

    private void animateGlow() {
        ObjectAnimator glowFadeIn = ObjectAnimator.ofFloat(fireGlow, "alpha", 0.0f, 0.8f);
        glowFadeIn.setDuration(800L);
        glowFadeIn.start();

        ObjectAnimator glowPulse = ObjectAnimator.ofFloat(fireGlow, "alpha", 0.8f, 0.4f);
        glowPulse.setDuration(1000L);
        glowPulse.setRepeatCount(ObjectAnimator.INFINITE);
        glowPulse.setRepeatMode(ObjectAnimator.REVERSE);
        glowPulse.setStartDelay(800L);
        glowPulse.start();

        ObjectAnimator glowScaleX = ObjectAnimator.ofFloat(fireGlow, "scaleX", 0.8f, 1.1f);
        ObjectAnimator glowScaleY = ObjectAnimator.ofFloat(fireGlow, "scaleY", 0.8f, 1.1f);
        for (ObjectAnimator a : new ObjectAnimator[]{glowScaleX, glowScaleY}) {
            a.setDuration(1200L);
            a.setRepeatCount(ObjectAnimator.INFINITE);
            a.setRepeatMode(ObjectAnimator.REVERSE);
            a.start();
        }
    }

    private void animateLogoIn() {
        ivLogo.setScaleX(0.1f);
        ivLogo.setScaleY(0.1f);
        ivLogo.setRotation(-180f);

        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(ivLogo, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(ivLogo, "scaleX", 0.1f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(ivLogo, "scaleY", 0.1f, 1f);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(ivLogo, "rotation", -180f, 0f);

        AnimatorSet logoAnim = new AnimatorSet();
        logoAnim.playTogether(fadeIn, scaleX, scaleY, rotation);
        logoAnim.setDuration(1200L);
        logoAnim.setInterpolator(new OvershootInterpolator(1.2f));
        logoAnim.start();

        playSoundEffect();
    }

    private void animateLogoBreathe() {
        ObjectAnimator slowRotate = ObjectAnimator.ofFloat(ivLogo, "rotation", 0f, 360f);
        slowRotate.setDuration(8000L);
        slowRotate.setRepeatCount(ObjectAnimator.INFINITE);
        slowRotate.setInterpolator(new AccelerateDecelerateInterpolator());
        slowRotate.start();

        ObjectAnimator breatheX = ObjectAnimator.ofFloat(ivLogo, "scaleX", 1f, 1.08f);
        ObjectAnimator breatheY = ObjectAnimator.ofFloat(ivLogo, "scaleY", 1f, 1.08f);
        for (ObjectAnimator a : new ObjectAnimator[]{breatheX, breatheY}) {
            a.setDuration(1500L);
            a.setRepeatCount(ObjectAnimator.INFINITE);
            a.setRepeatMode(ObjectAnimator.REVERSE);
            a.start();
        }
    }

    private void animateTitle() {
        ObjectAnimator titleFade = ObjectAnimator.ofFloat(tvTitle, "alpha", 0f, 1f);
        ObjectAnimator titleSlide = ObjectAnimator.ofFloat(tvTitle, "translationY", 50f, 0f);
        AnimatorSet titleAnim = new AnimatorSet();
        titleAnim.playTogether(titleFade, titleSlide);
        titleAnim.setDuration(600L);
        titleAnim.start();
    }

    private void animateSubtitle() {
        ObjectAnimator subFade = ObjectAnimator.ofFloat(tvSubtitle, "alpha", 0f, 1f);
        subFade.setDuration(500L);
        subFade.start();
    }

    private void animateProgress() {
        ObjectAnimator progFade = ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f);
        progFade.setDuration(400L);
        progFade.start();
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void playSoundEffect() {
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.fire_whoosh);
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(0.7f, 0.7f);
                mediaPlayer.setOnCompletionListener(mp -> {
                    mp.release();
                    mediaPlayer = null;
                });
                mediaPlayer.start();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }
}
