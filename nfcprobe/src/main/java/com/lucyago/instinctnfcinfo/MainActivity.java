package com.lucyago.instinctnfcinfo;

import android.app.Activity;
import android.app.AlertDialog;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.NfcV;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public class MainActivity extends Activity {
    private NfcAdapter adapter;
    private TextView result;
    private boolean scanning = false;
    private final NfcAdapter.ReaderCallback reader = this::readTag;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = NfcAdapter.getDefaultAdapter(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(28, 32, 28, 24);
        content.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(this);
        title.setText("Instinct Go — NFC información");
        title.setTextSize(21);
        title.setTextColor(Color.BLACK);
        content.addView(title);
        TextView note = new TextView(this);
        note.setText("\nPrueba experimental: envía únicamente el comando de información A1 observado en Juggluco. NO envía A0 (activación) ni A8 (cambio de receptor). No está comprobado que A1 sea inocuo en Instinct Go. Evita probarlo con el sensor activo de Yago hasta valorar el riesgo.\n");
        note.setTextSize(15);
        note.setTextColor(Color.DKGRAY);
        content.addView(note);
        Button button = new Button(this);
        button.setText("Preparar UNA lectura NFC");
        content.addView(button);
        result = new TextView(this);
        result.setTextSize(16);
        result.setTextColor(Color.BLACK);
        result.setText("\nTodavía no se ha leído ningún sensor.");
        content.addView(result);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        button.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Confirmar prueba")
            .setMessage("La aplicación solo enviará A1. Aun así, desconocemos su efecto exacto sobre Instinct Go. ¿Preparar la lectura?")
            .setNegativeButton("Cancelar", (d,w) -> {})
            .setPositiveButton("Preparar", (d,w) -> beginRead())
            .show());
    }

    private void beginRead() {
        if (adapter == null) { result.setText("Este teléfono no tiene NFC."); return; }
        if (!adapter.isEnabled()) { result.setText("Activa NFC en Ajustes."); return; }
        scanning = true;
        result.setText("Esperando sensor NFC. Acerca la parte trasera del teléfono SOLO al sensor elegido para la prueba.");
        adapter.enableReaderMode(this, reader,
            NfcAdapter.FLAG_READER_NFC_V | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
    }

    private synchronized void readTag(Tag tag) {
        if (!scanning) return;
        scanning = false;
        NfcV nfc = NfcV.get(tag);
        if (nfc == null) {
            finishRead("El objeto NFC no ofrece tecnología NfcV (ISO 15693).");
            return;
        }
        String message;
        try {
            nfc.connect();
            // Libre3.firstnfc() en Juggluco: exclusivamente comando informativo A1.
            byte[] response = nfc.transceive(new byte[]{0x02, (byte)0xA1, 0x7A});
            message = parseResponse(response);
        } catch (Exception ex) {
            message = "No se obtuvo respuesta A1: " + ex.getClass().getSimpleName()
                    + " — " + ex.getMessage();
        } finally {
            try { nfc.close(); } catch (Exception ignored) {}
        }
        finishRead(message);
    }

    private void finishRead(String message) {
        runOnUiThread(() -> {
            if (adapter != null) adapter.disableReaderMode(this);
            result.setText(message);
        });
    }

    private static int u16(byte[] data, int offset) {
        return (data[offset] & 255) | ((data[offset + 1] & 255) << 8);
    }
    private static String parseResponse(byte[] d) {
        if (d == null) return "Respuesta nula.";
        StringBuilder sb = new StringBuilder();
        sb.append("Respuesta recibida (").append(d.length).append(" bytes).\n");
        // Juggluco's Libre3 NFC parser expects 00 A5 00 + 26-byte patch info.
        if (d.length >= 29 && (d[0] & 255) == 0 && (d[1] & 255) == 0xA5) {
            int p = 3;
            sb.append("Versión de seguridad: ").append(u16(d,p)).append("\n");
            sb.append("Región/localización: ").append(u16(d,p+2)).append("\n");
            sb.append("Generación: ").append(u16(d,p+4)).append("\n");
            sb.append("Duración nominal (min): ").append(u16(d,p+6)).append("\n");
            sb.append("Tipo de producto: ").append(d[p+12] & 255).append("\n");
            sb.append("Calentamiento (bloques de 5 min): ").append(d[p+13] & 255).append("\n");
            sb.append("Estado: ").append(d[p+14] & 255).append("\n");
            sb.append("\nSi producto=10, coincide con el identificador Instinct que documenta DiaBLE.\n");
        } else {
            sb.append("No coincide con la estructura de información Libre 3 esperada.\n");
        }
        sb.append("\nDatos hexadecimales (pueden contener identificadores del sensor):\n");
        for (byte b : d) sb.append(String.format(Locale.US, "%02X", b & 255));
        sb.append("\n\nNo se ha solicitado activación ni cambio de receptor.");
        return sb.toString();
    }

    @Override public void onPause() {
        super.onPause();
        scanning = false;
        if (adapter != null) {
            try { adapter.disableReaderMode(this); } catch (Exception ignored) {}
        }
    }
}
