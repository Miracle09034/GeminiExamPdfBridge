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
    private TextView statusTextView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final int REQ_STORAGE = 1001;
    private static final String TAG = "GeminiExamPDF";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        statusTextView = new TextView(this);
        statusTextView.setText("Initializing Gemini Exam PDF Bridge...");
        statusTextView.setTextSize(18);
        statusTextView.setTextColor(Color.BLACK);
        statusTextView.setPadding(50, 50, 50, 50);
        setContentView(statusTextView);

        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        started = false; // Reset flag for new conversion request
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        parseIntent(intent);

        if (htmlPath == null || pdfPath == null) {
            finishWithError("Error: Deep link missing html or pdf parameter.");
            return;
        }

        statusTextView.setText("HTML: " + htmlPath + "\nPDF: " + pdfPath);

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
                String rawHtml = uri.getQueryParameter("html");
                String rawPdf = uri.getQueryParameter("pdf");

                if (rawHtml != null) {
                    rawHtml = rawHtml.replace("+", " ");
                    htmlPath = URLDecoder.decode(rawHtml, "UTF-8");
                }
                if (rawPdf != null) {
                    rawPdf = rawPdf.replace("+", " ");
                    pdfPath = URLDecoder.decode(rawPdf, "UTF-8");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode URI", e);
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
                finishWithError("Error: Storage permission denied by user.");
            }
        }
    }

    private void startConversion() {
        if (started) return;
        started = true;

        File html = new File(htmlPath);
        if (!html.exists()) {
            finishWithError("Error: HTML file does NOT exist at:\n" + htmlPath);
            return;
        }

        File out = new File(pdfPath);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        webView = new WebView(this);
        webView.setVisibility(View.VISIBLE);

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
                statusTextView.setText("Rendering complete. Generating PDF...");
                handler.postDelayed(() -> exportPdf(), 1200);
            }
        });

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

            File outputFile = new File(pdfPath);

            new android.print.PdfPrint(attrs).print(
                    adapter,
                    outputFile
            );

            statusTextView.setText("Writing PDF to disk...");

            // Poll the file on disk until it has a valid size (> 1KB) and stops growing
            waitForPdfAndFinish(outputFile, 0, 0);

        } catch (Throwable t) {
            finishWithError("PDF Export Exception: " + t.getMessage());
        }
    }

    private void waitForPdfAndFinish(File file, long lastSize, int attempts) {
        if (attempts > 40) {
            finishWithError("Error: PDF writing timed out on disk.");
            return;
        }

        handler.postDelayed(() -> {
            if (file.exists()) {
                long currentSize = file.length();
                
                // If file is > 1KB and size hasn't changed since last check (flush complete)
                if (currentSize > 1024 && currentSize == lastSize) {
                    statusTextView.setText("PDF complete! Size: " + currentSize + " bytes");
                    handler.postDelayed(this::finish, 500);
                    return;
                }
                
                waitForPdfAndFinish(file, currentSize, attempts + 1);
            } else {
                waitForPdfAndFinish(file, 0, attempts + 1);
            }
        }, 500);
    }

    private void finishWithError(String message) {
        Log.e(TAG, message);
        if (statusTextView != null) {
            statusTextView.setTextColor(Color.RED);
            statusTextView.setText(message);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        handler.postDelayed(this::finish, 6000);
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
