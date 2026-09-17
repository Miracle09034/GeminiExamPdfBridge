"""
Pydroid 3 launcher for Gemini Exam PDF Bridge.

After the companion APK is installed once, this Python script gives you
fully automated conversion:

    python convert_exam.py "/storage/emulated/0/ExamPapers/paper.html"

It launches the Android WebView PDF bridge, waits for the PDF to appear,
and returns control to Pydroid.

The APK package name is:
    com.examexporter.bridge
"""

import os
import subprocess
import sys
import time

PACKAGE = "com.examexporter.bridge"
ACTION = "com.examexporter.bridge.CONVERT"


def run_am(*args):
    candidates = ["/system/bin/am", "am"]
    last = None
    for exe in candidates:
        try:
            return subprocess.run(
                [exe, *args],
                capture_output=True,
                text=True,
                timeout=20,
            )
        except Exception as e:
            last = e
    raise RuntimeError(f"Could not execute Android Activity Manager: {last}")


def convert(html_path, pdf_path=None, timeout=90):
    html_path = os.path.abspath(html_path)
    if not os.path.isfile(html_path):
        raise FileNotFoundError(html_path)

    if pdf_path is None:
        pdf_path = os.path.splitext(html_path)[0] + ".pdf"
    pdf_path = os.path.abspath(pdf_path)

    # Remove an old output so completion can be detected reliably.
    if os.path.exists(pdf_path):
        os.remove(pdf_path)

    result = run_am(
        "start",
        "-a", ACTION,
        "-n", f"{PACKAGE}/.MainActivity",
        "--es", "html_path", html_path,
        "--es", "pdf_path", pdf_path,
    )

    if result.returncode != 0:
        raise RuntimeError(
            "Could not launch PDF bridge.\n"
            + result.stdout + "\n" + result.stderr
        )

    print("Rendering:", html_path)
    print("Waiting for:", pdf_path)

    started = time.time()
    while time.time() - started < timeout:
        if os.path.isfile(pdf_path):
            size = os.path.getsize(pdf_path)
            if size > 1000:
                # Wait briefly for the last write to settle.
                time.sleep(0.5)
                print("SUCCESS:", pdf_path)
                print("Size:", os.path.getsize(pdf_path), "bytes")
                return pdf_path
        time.sleep(0.25)

    raise TimeoutError(
        "Timed out waiting for PDF.\n"
        "If Android showed a storage permission request, allow it once and retry."
    )


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage:")
        print('  python convert_exam.py "/storage/emulated/0/ExamPapers/paper.html"')
        print('  python convert_exam.py "/storage/emulated/0/ExamPapers/paper.html" "/storage/emulated/0/ExamPapers/output.pdf"')
        sys.exit(2)

    convert(sys.argv[1], sys.argv[2] if len(sys.argv) >= 3 else None)
