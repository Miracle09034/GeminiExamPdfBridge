package com.examexporter.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.URLDecoder;

public class MainActivity extends Activity {
    private WebView webView;
    private String htmlPath;
    private String pdfPath;
    private boolean started;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final int REQ_STORAGE = 1001;
    private static final String TAG = "GeminiExamPDF";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        TextView status = new TextView(this);
        status.setText("Preparing PDF…");
        status.setTextSize(16);
        status.setTextColor(Color.DKGRAY);
        status.setPadding(40, 40, 40, 40);
        setContentView(status);

        parseIntent(getIntent());

        if (htmlPath == null || pdfPath == null) {
            finishWithError("Missing html_path or pdf_path parameters.");
            return;
        }

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQ_STORAGE);
        } else {
            startConversion();
        }
    }

    private void parseIntent(Intent intent) {
        if (intent == null) return;

        Uri uri = intent.getData();
        if (uri != null && "gemini-pdf".equalsIgnoreCase(uri.getScheme())) {
            try {
                // Safely decode percent-encoded paths (e.g. spaces in "exam folder")
                String rawHtml = uri.getQueryParameter("html");
                String rawPdf = uri.getQueryParameter("pdf");

                if (rawHtml != null) htmlPath = URLDecoder.decode(rawHtml, "UTF-8");
                if (rawPdf != null) pdfPath = URLDecoder.decode(rawPdf, "UTF-8");
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode URI parameters", e);
            }
            return;
        }

        htmlPath = intent.getStringExtra("html_path");
        pdfPath = intent.getStringExtra("pdf_path");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_STORAGE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                startConversion();
            } else {
                finishWithError("Storage permission denied by user.");
            }
        }
    }

    private void startConversion() {
        if (started) return;
        started = true;

        File html = new File(htmlPath);
        if (!html.exists()) {
            finishWithError("HTML file not found at: " + htmlPath);
            return;
        }

        File out = new File(pdfPath);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        webView = new WebView(this);
        webView.setVisibility(View.INVISIBLE);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setDefaultTextEncodingName("UTF-8");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                handler.postDelayed(() -> exportPdf(), 1000);
            }
        });

        setContentView(webView);

        Uri uri = Uri.fromFile(html);
        webView.loadUrl(uri.toString());
    }

    private void exportPdf() {
        if (webView == null) return;

        try {
            android.print.PrintAttributes attrs =
                    new android.print.PrintAttributes.Builder()
                            .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                            .build();

            android.print.PrintDocumentAdapter adapter =
                    webView.createPrintDocumentAdapter(new File(pdfPath).getName());

            new android.print.PdfPrint(attrs).print(
                    adapter,
                    new File(pdfPath)
            );

            // Wait 1.5 seconds for PDF file output stream to flush, then finish
            handler.postDelayed(this::finish, 1500);

        } catch (Exception e) {
            finishWithError("PDF export failed: " + e.getMessage());
        }
    }

    private void finishWithError(String message) {
        Log.e(TAG, message);
        // Show a brief Toast before closing so the failure reason is visible on screen
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        handler.postDelayed(this::finish, 2000);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
