package com.coara.browser2.util;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.coara.browser2.R;
import com.coara.browser2.gecko.BrowserClient;
import com.coara.browser2.gecko.BrowserView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mozilla.geckoview.WebRequestError;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class SafeBrowsingSupport {
    private static final Map<BrowserView, Boolean> PROMPTS = Collections.synchronizedMap(new WeakHashMap<BrowserView, Boolean>());

    private SafeBrowsingSupport() {
    }

    public static boolean handles(BrowserClient.LoadFailure failure) {
        if (failure == null) {
            return false;
        }
        if (failure.category == WebRequestError.ERROR_CATEGORY_SAFEBROWSING) {
            return true;
        }
        return failure.category == WebRequestError.ERROR_CATEGORY_SECURITY
                && failure.code != WebRequestError.ERROR_BAD_HSTS_CERT;
    }

    public static void clear(BrowserView view) {
        if (view != null) {
            PROMPTS.remove(view);
        }
    }

    public static void show(final Activity activity, final BrowserView view,
                            final BrowserClient.LoadFailure failure, final Runnable goBack) {
        if (activity == null || view == null || failure == null || !handles(failure)) {
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        synchronized (PROMPTS) {
            if (PROMPTS.containsKey(view)) {
                return;
            }
            PROMPTS.put(view, Boolean.TRUE);
        }
        final boolean threat = failure.category == WebRequestError.ERROR_CATEGORY_SAFEBROWSING;
        final String url = failure.url == null ? "" : failure.url;
        String host = "";
        try {
            Uri uri = Uri.parse(url);
            if (uri.getHost() != null) {
                host = uri.getHost();
            }
        } catch (Throwable ignored) {
        }
        final String targetHost = host.isEmpty() ? "確認できませんでした" : host;
        final String reason;
        final String detail;
        final String summaryText;
        final String subText;
        final String advice;
        if (threat) {
            reason = reasonFor(failure.code);
            detail = detailFor(failure.code);
            summaryText = "危険なページとして検出されました";
            subText = "ブラウザの安全確認サービスが、このページを危険と判定しました。";
            advice = "このページを開くと、マルウェア感染やフィッシングなどの被害につながる可能性があります。パスワード、認証コード、カード情報などの入力は避けてください。\n\n「前に戻る」では安全なページへ戻ります。「このページを開く」では安全確認を無視して表示を続行します。";
        } else {
            reason = failure.detail == null || failure.detail.isEmpty() ? "HTTPS証明書の検証に失敗しました。" : failure.detail;
            detail = "";
            summaryText = "この接続ではプライバシーが保護されません";
            subText = "HTTPS証明書を確認できないため、安全な接続を確立できませんでした。";
            advice = "このまま開くと、通信内容が第三者に見られたり改ざんされたりする可能性があります。パスワード、認証コード、クレジットカード情報などは入力しないでください。\n\n「前に戻る」では安全なページへ戻ります。「このページを開く」では証明書エラーを無視して続行します。";
        }
        final boolean[] decided = {false};
        try {
            View content = activity.getLayoutInflater().inflate(R.layout.dialog_security_warning, null, false);
            ((TextView) content.findViewById(R.id.securityWarningSummary)).setText(summaryText);
            ((TextView) content.findViewById(R.id.securityWarningSubSummary)).setText(subText);
            ((TextView) content.findViewById(R.id.securityWarningReason)).setText(detail.isEmpty() ? reason : reason + "\n\n" + detail);
            ((TextView) content.findViewById(R.id.securityWarningTarget)).setText(targetHost);
            ((TextView) content.findViewById(R.id.securityWarningUrl)).setText(url.isEmpty() ? "URLを取得できませんでした" : url);
            ((TextView) content.findViewById(R.id.securityWarningAdvice)).setText(advice);
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                    .setView(content)
                    .setNegativeButton("前に戻る", (d, which) -> {
                        if (decided[0]) {
                            return;
                        }
                        decided[0] = true;
                        PROMPTS.remove(view);
                        if (goBack != null) {
                            goBack.run();
                        }
                    })
                    .setPositiveButton("このページを開く", (d, which) -> {
                        if (decided[0]) {
                            return;
                        }
                        decided[0] = true;
                        PROMPTS.remove(view);
                        if (threat) {
                            view.loadUrlBypassClassifier(url);
                        } else {
                            view.proceedAfterCertificateError(url);
                        }
                    })
                    .setOnCancelListener(d -> {
                        if (decided[0]) {
                            return;
                        }
                        decided[0] = true;
                        PROMPTS.remove(view);
                    })
                    .show();
            styleButtons(dialog);
        } catch (Throwable error) {
            PROMPTS.remove(view);
            try {
                Toast.makeText(activity, "危険なページをブロックしました", Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void styleButtons(AlertDialog dialog) {
        if (dialog == null) {
            return;
        }
        try {
            android.widget.Button negative = dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE);
            android.widget.Button positive = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE);
            if (negative == null || positive == null) {
                return;
            }
            android.view.ViewParent parent = negative.getParent();
            if (parent instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout bar = (android.widget.LinearLayout) parent;
                bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                bar.setGravity(android.view.Gravity.CENTER);
                android.view.ViewGroup.LayoutParams np = negative.getLayoutParams();
                android.view.ViewGroup.LayoutParams pp = positive.getLayoutParams();
                if (np instanceof android.widget.LinearLayout.LayoutParams && pp instanceof android.widget.LinearLayout.LayoutParams) {
                    android.widget.LinearLayout.LayoutParams nl = (android.widget.LinearLayout.LayoutParams) np;
                    android.widget.LinearLayout.LayoutParams pl = (android.widget.LinearLayout.LayoutParams) pp;
                    nl.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                    nl.weight = 0f;
                    nl.gravity = android.view.Gravity.CENTER_VERTICAL;
                    nl.setMargins(8, 0, 8, 0);
                    pl.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
                    pl.weight = 0f;
                    pl.gravity = android.view.Gravity.CENTER_VERTICAL;
                    pl.setMargins(8, 0, 8, 0);
                    negative.setLayoutParams(nl);
                    positive.setLayoutParams(pl);
                }
                negative.setAllCaps(false);
                positive.setAllCaps(false);
            }
            if (dialog.getWindow() != null) {
                android.util.DisplayMetrics metrics = dialog.getContext().getResources().getDisplayMetrics();
                int width = (int) (metrics.widthPixels * 0.92f);
                width = Math.min(width, (int) (560 * metrics.density));
                width = Math.max(width, (int) (300 * metrics.density));
                dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String reasonFor(int code) {
        switch (code) {
            case WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI:
                return "マルウェアの可能性";
            case WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI:
                return "フィッシングの可能性";
            case WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI:
                return "望ましくないソフトウェアの可能性";
            case WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI:
                return "有害または欺瞞的なコンテンツの可能性";
            default:
                return "既知の危険なコンテンツとして検出";
        }
    }

    private static String detailFor(int code) {
        switch (code) {
            case WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI:
                return "このサイトでは、端末やデータに被害を与えるマルウェアの配布が確認されている可能性があります。不要なアプリやファイルのインストールを求められる場合があります。";
            case WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI:
                return "正規サイトを装って、ID、パスワード、認証コード、カード情報などを入力させるフィッシングの危険がある可能性があります。";
            case WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI:
                return "ブラウザや端末の設定を望ましくない形に変更したり、不要なソフトウェアをインストールさせたりする危険がある可能性があります。";
            case WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI:
                return "不透明な請求、意図しない定期購入、欺瞞的な誘導などにつながる危険がある可能性があります。";
            default:
                return "ブラウザの安全確認サービスが、このURLを安全に表示し続けることが望ましくないと判定しました。";
        }
    }
}
