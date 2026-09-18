package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;

public class PdfPrint {
    private static final String TAG = "PdfPrint";
    private final PrintAttributes printAttributes;

    public interface Callback {
        void onSuccess(File file);
        void onFailure(String error);
    }

    public PdfPrint(PrintAttributes printAttributes) {
        this.printAttributes = printAttributes;
    }

    public void print(final PrintDocumentAdapter adapter, final File path, final Callback callback) {
        if (path.exists()) {
            path.delete();
        }

        adapter.onLayout(null, printAttributes, null, new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                try {
                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(
                            path, ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
                    );

                    PageRange[] pages = new PageRange[]{PageRange.ALL_PAGES};

                    adapter.onWrite(pages, pfd, new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                        @Override
                        public void onWriteFinished(PageRange[] pages) {
                            try {
                                pfd.close();
                                if (path.exists() && path.length() > 0) {
                                    if (callback != null) callback.onSuccess(path);
                                } else {
                                    if (callback != null) callback.onFailure("File created but output stream was empty.");
                                }
                            } catch (Exception e) {
                                if (callback != null) callback.onFailure("Failed to close file descriptor: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onWriteFailed(CharSequence error) {
                            try { pfd.close(); } catch (Exception ignored) {}
                            if (callback != null) callback.onFailure("Write failed: " + error);
                        }
                    });
                } catch (Exception e) {
                    if (callback != null) callback.onFailure("Failed to create ParcelFileDescriptor: " + e.getMessage());
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                if (callback != null) callback.onFailure("Layout failed: " + error);
            }
        }, new Bundle());
    }
}
