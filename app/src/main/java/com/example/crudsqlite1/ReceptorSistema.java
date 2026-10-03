package com.example.crudsqlite1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

/**
 * Semana 7, Actividad 01: escucha avisos del SISTEMA.
 *
 * Se registra de forma DINAMICA (registerReceiver en onStart, unregisterReceiver
 * en onStop). No se declara en el manifest a proposito: desde Android 8 la
 * mayoria de los avisos implicitos ya no llegan a los receivers declarados ahi,
 * y ademas un receiver dinamico solo vive mientras la pantalla esta visible, que
 * es justo lo que se quiere.
 *
 * Si no se llama a unregisterReceiver, Android lanza
 * "Activity has leaked IntentReceiver" y la Activity queda retenida en memoria.
 */
public class ReceptorSistema extends BroadcastReceiver {

    /** La Activity implementa esto para enterarse de los cambios. */
    public interface Escucha {
        void alCambiarRed(boolean hayRed);
        void alCambiarBateria(boolean bateriaBaja);
    }

    private final Escucha escucha;

    public ReceptorSistema(Escucha escucha) {
        this.escucha = escucha;
    }

    /** Los avisos que se quieren recibir. */
    public static IntentFilter filtro() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_BATTERY_LOW);     // el sistema avisa al 15 %
        f.addAction(Intent.ACTION_BATTERY_OKAY);    // y cuando vuelve a subir
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        return f;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String accion = intent.getAction();
        if (accion == null) {
            return;
        }

        switch (accion) {
            case Intent.ACTION_BATTERY_LOW:
                escucha.alCambiarBateria(true);
                break;
            case Intent.ACTION_BATTERY_OKAY:
                escucha.alCambiarBateria(false);
                break;
            case ConnectivityManager.CONNECTIVITY_ACTION:
                /* CONNECTIVITY_ACTION esta obsoleto desde Android 9 y en versiones
                   nuevas no siempre llega. Por eso la app usa ADEMAS un
                   NetworkCallback (ver MainActivity), que es la forma vigente.
                   Se deja este caso porque es el que pide la practica. */
                escucha.alCambiarRed(hayInternet(context));
                break;
        }
    }

    /** Revisa si la red activa realmente tiene salida a internet. */
    public static boolean hayInternet(Context context) {
        ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
        if (cm == null || cm.getActiveNetwork() == null) {
            return false;
        }
        android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        // NET_CAPABILITY_VALIDATED: el sistema ya comprobo que hay salida real,
        // no solo que el Wi-Fi esta conectado (un portal cautivo no vale).
        return caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }
}
