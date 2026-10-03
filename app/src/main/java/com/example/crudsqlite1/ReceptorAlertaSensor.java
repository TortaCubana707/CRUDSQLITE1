package com.example.crudsqlite1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/**
 * Semana 7, Actividad 02: recibe la alerta PROPIA de la aplicacion.
 *
 * La pantalla principal emite este aviso cuando una lectura de la ESP32 pasa el
 * umbral critico. Quien quiera enterarse solo se suscribe; el que emite no sabe
 * quien escucha, y por eso las partes quedan desacopladas: manana se puede
 * agregar otra pantalla que reaccione sin tocar MainActivity.
 */
public class ReceptorAlertaSensor extends BroadcastReceiver {

    /** Solo la propia app envia y recibe este aviso. */
    public static final String ACCION_ALERTA = "com.example.crudsqlite1.ALERTA_SENSOR";
    public static final String EXTRA_SENSOR = "sensor";
    public static final String EXTRA_VALOR = "valor";

    public interface Escucha {
        void alRecibirAlerta(String sensor, double valor);
    }

    private final Escucha escucha;

    public ReceptorAlertaSensor(Escucha escucha) {
        this.escucha = escucha;
    }

    public static IntentFilter filtro() {
        return new IntentFilter(ACCION_ALERTA);
    }

    /** Arma y manda el aviso. Lo llama la pantalla principal. */
    public static void emitir(Context context, String sensor, double valor) {
        Intent intent = new Intent(ACCION_ALERTA);
        // setPackage limita el aviso a esta app: sin esto cualquier otra
        // aplicacion del telefono podria escucharlo.
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_SENSOR, sensor);
        intent.putExtra(EXTRA_VALOR, valor);
        context.sendBroadcast(intent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        escucha.alRecibirAlerta(
                intent.getStringExtra(EXTRA_SENSOR),
                intent.getDoubleExtra(EXTRA_VALOR, 0));
    }
}
