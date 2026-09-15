package com.faa.faadl;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Scanner QR built-in: preview kamera + decode otomatis (seperti versi web),
 * plus fallback scan dari gambar galeri.
 */
public class QrScanActivity extends Activity implements SurfaceHolder.Callback,
        Camera.PreviewCallback {

    private static final int REQ_GALLERY = 2001;

    private SurfaceView surface;
    private SurfaceHolder holder;
    private Camera camera;
    private TextView hint;
    private Button btnFlash;
    private Button btnGallery;
    private Button btnCancel;
    private Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean decoding = false;
    private volatile boolean done = false;
    private volatile boolean previewOn = false;
    private volatile long lastTry = 0;
    private boolean flashOn = false;
    private QRCodeReader reader = new QRCodeReader();
    private Map<DecodeHintType, Object> hints = new EnumMap<DecodeHintType, Object>(DecodeHintType.class);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        List<com.google.zxing.BarcodeFormat> fmts = new ArrayList<com.google.zxing.BarcodeFormat>();
        fmts.add(com.google.zxing.BarcodeFormat.QR_CODE);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, fmts);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surface = new SurfaceView(this);
        root.addView(surface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        holder = surface.getHolder();
        holder.addCallback(this);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(24);
        hint = new TextView(this);
        hint.setText("Arahkan QR ke kamera");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(15);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(Color.parseColor("#800284C7"));
        hint.setPadding(dp(16), dp(10), dp(16), dp(10));
        top.addView(hint);
        root.addView(top, tlp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        blp.bottomMargin = dp(32);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        btnFlash = new Button(this);
        btnFlash.setText("Flash");
        btnFlash.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleFlash(); }
        });
        btnCancel = new Button(this);
        btnCancel.setText("Batal");
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        row.addView(btnFlash);
        row.addView(btnCancel);
        bottom.addView(row);

        btnGallery = new Button(this);
        btnGallery.setText("atau: Scan dari gambar galeri");
        btnGallery.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickImage(); }
        });
        bottom.addView(btnGallery);
        root.addView(bottom, blp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // surface biasanya sudah siap; kalau belum, surfaceCreated yang buka
        try {
            if (holder != null && holder.getSurface() != null
                    && holder.getSurface().isValid() && camera == null) {
                openCameraAndStart();
            }
        } catch (Exception e) {
            toast("Kamera: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    // ---- SurfaceHolder.Callback (pola kanonik: buka di surfaceCreated) ----
    @Override
    public void surfaceCreated(SurfaceHolder h) {
        if (camera == null && !done) {
            openCameraAndStart();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        if (camera == null) return;
        try {
            camera.stopPreview();
        } catch (Exception ignored) {}
        previewOn = false;
        try {
            camera.setPreviewDisplay(h);
            camera.setPreviewCallback(this);
            camera.startPreview();
            previewOn = true;
        } catch (Exception e) {
            toast("Preview gagal: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        closeCamera();
    }

    private void openCameraAndStart() {
        closeCamera();
        try {
            camera = Camera.open();
        } catch (Exception e) {
            toast("Kamera tidak bisa dibuka (dipakai aplikasi lain?)");
            finish();
            return;
        }
        try {
            Camera.Parameters p = camera.getParameters();
            List<Camera.Size> sizes = p.getSupportedPreviewSizes();
            Camera.Size best = null;
            if (sizes != null && !sizes.isEmpty()) {
                for (int i = 0; i < sizes.size(); i++) {
                    Camera.Size s = sizes.get(i);
                    if (s.width == 1280 && s.height == 720) { best = s; break; }
                    if (best == null && s.width >= 640 && s.width <= 1920) best = s;
                }
                if (best == null) best = sizes.get(0);
                p.setPreviewSize(best.width, best.height);
            }
            List<String> focus = p.getSupportedFocusModes();
            if (focus != null) {
                if (focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focus.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                }
            }
            camera.setParameters(p);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
            previewOn = true;
            setHint("Arahkan QR ke kamera");
        } catch (Exception e) {
            toast("Gagal mulai kamera: " + e.getMessage());
            closeCamera();
            finish();
        }
    }

    private void closeCamera() {
        previewOn = false;
        try {
            if (camera != null) {
                camera.setPreviewCallback(null);
                camera.stopPreview();
                camera.release();
            }
        } catch (Exception ignored) {}
        camera = null;
    }

    private void toggleFlash() {
        if (camera == null) return;
        try {
            Camera.Parameters p = camera.getParameters();
            List<String> modes = p.getSupportedFlashModes();
            if (modes == null || !modes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                toast("Flash tidak didukung");
                return;
            }
            flashOn = !flashOn;
            p.setFlashMode(flashOn ? Camera.Parameters.FLASH_MODE_TORCH
                    : Camera.Parameters.FLASH_MODE_OFF);
            camera.setParameters(p);
        } catch (Exception e) {
            toast("Flash gagal: " + e.getMessage());
        }
    }

    private void setHint(final String s) {
        ui.post(new Runnable() {
            @Override public void run() { hint.setText(s); }
        });
    }

    // ---- Camera.PreviewCallback (NV21 landscape) ----
    @Override
    public void onPreviewFrame(final byte[] data, final Camera cam) {
        if (done || decoding || !previewOn) return;
        long now = System.currentTimeMillis();
        if (now - lastTry < 350) return;
        lastTry = now;
        decoding = true;
        Camera.Size s = null;
        try { s = cam.getParameters().getPreviewSize(); } catch (Exception ignored) {}
        if (s == null || data == null) { decoding = false; return; }
        final int w = s.width;
        final int h = s.height;
        new Thread(new Runnable() {
            @Override public void run() {
                String text = decodeQr(data, w, h);
                decoding = false;
                if (text != null && !done) {
                    finishWith(text);
                }
            }
        }).start();
    }

    private void finishWith(final String res) {
        done = true;
        ui.post(new Runnable() {
            @Override public void run() {
                Intent out = new Intent();
                out.putExtra("SCAN_RESULT", res);
                setResult(RESULT_OK, out);
                finish();
            }
        });
    }

    /** Putar plane-Y 90 derajat (portrait) lalu decode area tengah. */
    private String decodeQr(byte[] nv21, int w, int h) {
        try {
            int yLen = w * h;
            if (nv21.length < yLen) return null;
            byte[] rot = new byte[yLen];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    rot[x * h + (h - y - 1)] = nv21[y * w + x];
                }
            }
            int rw = h;
            int rh = w;
            int side = Math.min(rw, rh) * 3 / 4;
            if (side < 200) side = Math.min(rw, rh);
            int left = (rw - side) / 2;
            int top = (rh - side) / 2;
            PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(
                    rot, rw, rh, left, top, side, side, false);
            BinaryBitmap bmp = new BinaryBitmap(new HybridBinarizer(src));
            Result r = reader.decode(bmp, hints);
            return r == null ? null : r.getText();
        } catch (Exception e) {
            return null;
        } finally {
            try { reader.reset(); } catch (Exception ignored) {}
        }
    }

    // ---- Scan dari galeri ----
    private void pickImage() {
        try {
            Intent i = new Intent(Intent.ACTION_PICK);
            i.setType("image/*");
            startActivityForResult(i, REQ_GALLERY);
        } catch (Exception e) {
            toast("Galeri tidak tersedia");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_GALLERY && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            setHint("Membaca gambar...");
            final Uri fu = uri;
            new Thread(new Runnable() {
                @Override public void run() {
                    final String t = decodeImage(fu);
                    ui.post(new Runnable() {
                        @Override public void run() {
                            if (t != null) {
                                finishWith(t);
                            } else {
                                setHint("QR tidak ketemu di gambar");
                                toast("Tidak ada QR terdeteksi");
                            }
                        }
                    });
                }
            }).start();
        }
    }

    private String decodeImage(Uri uri) {
        InputStream in = null;
        try {
            in = getContentResolver().openInputStream(uri);
            Bitmap bm = BitmapFactory.decodeStream(in);
            if (in != null) in.close();
            if (bm == null) return null;
            int w = bm.getWidth();
            int h = bm.getHeight();
            int max = Math.max(w, h);
            if (max > 1200) {
                float sc = 1200f / max;
                Bitmap small = Bitmap.createScaledBitmap(bm,
                        Math.round(w * sc), Math.round(h * sc), true);
                if (small != bm) bm.recycle();
                bm = small;
                w = bm.getWidth();
                h = bm.getHeight();
            }
            int[] px = new int[w * h];
            bm.getPixels(px, 0, w, 0, 0, w, h);
            RGBLuminanceSource src = new RGBLuminanceSource(w, h, px);
            Result r = reader.decode(new BinaryBitmap(new HybridBinarizer(src)), hints);
            return r == null ? null : r.getText();
        } catch (Exception e) {
            return null;
        } finally {
            try { reader.reset(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}
