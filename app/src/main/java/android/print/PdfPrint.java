package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;

/**
 * Direct WebView -> PDF writer.
 *
 * It intentionally lives in android.print so it can instantiate the framework's
 * package-visible PrintDocumentAdapter callback classes on Android versions where
 * their constructors are package-private.
 */
public final class PdfPrint {
    private final PrintAttributes attributes;

    public PdfPrint(PrintAttributes attributes) {
        this.attributes = attributes;
    }

    public void print(
            final PrintDocumentAdapter adapter,
            final File outputFile
    ) {
        try {
            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (outputFile.exists()) {
                outputFile.delete();
            }

            final ParcelFileDescriptor destination =
                    ParcelFileDescriptor.open(
                            outputFile,
                            ParcelFileDescriptor.MODE_CREATE
                                    | ParcelFileDescriptor.MODE_READ_WRITE
                                    | ParcelFileDescriptor.MODE_TRUNCATE
                    );

            adapter.onLayout(
                    null,
                    attributes,
                    new CancellationSignal(),
                    new PrintDocumentAdapter.LayoutResultCallback() {
                        @Override
                        public void onLayoutFinished(
                                PrintDocumentInfo info,
                                boolean changed
                        ) {
                            try {
                                adapter.onWrite(
                                        new PageRange[]{PageRange.ALL_PAGES},
                                        destination,
                                        new CancellationSignal(),
                                        new PrintDocumentAdapter.WriteResultCallback() {
                                            @Override
                                            public void onWriteFinished(PageRange[] pages) {
                                                try {
                                                    destination.close();
                                                } catch (Exception ignored) {}
                                            }

                                            @Override
                                            public void onWriteFailed(CharSequence error) {
                                                try {
                                                    destination.close();
                                                } catch (Exception ignored) {}
                                                android.util.Log.e(
                                                        "GeminiExamPDF",
                                                        "Write failed: " + error
                                                );
                                            }

                                            @Override
                                            public void onWriteCancelled() {
                                                try {
                                                    destination.close();
                                                } catch (Exception ignored) {}
                                            }
                                        }
                                );
                            } catch (Exception e) {
                                try {
                                    destination.close();
                                } catch (Exception ignored) {}
                                android.util.Log.e(
                                        "GeminiExamPDF",
                                        "onWrite failed",
                                        e
                                );
                            }
                        }

                        @Override
                        public void onLayoutFailed(CharSequence error) {
                            try {
                                destination.close();
                            } catch (Exception ignored) {}
                            android.util.Log.e(
                                    "GeminiExamPDF",
                                    "Layout failed: " + error
                            );
                        }
                    },
                    null
            );
        } catch (Exception e) {
            android.util.Log.e("GeminiExamPDF", "Print setup failed", e);
        }
    }
}
