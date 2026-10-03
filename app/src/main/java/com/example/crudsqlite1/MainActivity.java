package com.example.crudsqlite1;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity
        implements ReceptorSistema.Escucha, ReceptorAlertaSensor.Escucha {

    // 1. Declarar variables para la Interfaz (UI)
    private SwitchCompat swLed, swRelay;
    private TextView txtLdr, txtTemp, txtHum, txtIr, txtDist;
    private EditText etMensajeLcd;
    private Button btnEnviarLcd;

    // 2. Declarar variables para Firebase
    private FirebaseDatabase database;
    private DatabaseReference refSensores, refLed, refRelay, refLcd;

    // 3. Semana 6: historial local en Room
    private Button btnHistorial;
    private RegistroSensorDAO registroDao;

    /* Un solo hilo de fondo para escribir en la base. Room prohibe tocar el
       disco desde el hilo principal para que la pantalla no se congele. */
    private final ExecutorService hiloBaseDatos = Executors.newSingleThreadExecutor();

    /* Ultimo valor guardado de cada sensor. La ESP32 escribe los valores uno
       por uno, asi que onDataChange se dispara varias veces por ciclo; sin
       esta comparacion la misma lectura quedaria repetida en la tabla. */
    private final Map<String, Double> ultimosValores = new HashMap<>();

    /* Cuando se guardo por ultima vez cada sensor. Si un valor no cambia (la
       temperatura puede quedarse en 26.9 durante minutos) igual se guarda una
       vez por minuto, para que el historial no tenga huecos. */
    private final Map<String, Long> ultimoGuardado = new HashMap<>();
    private static final long LATIDO_MS = 60000;   // 1 minuto

    /* Limite superior que ASHRAE recomienda para el aire de entrada de equipo
       de computo. Arriba de eso la lectura se marca en alerta (version 2). */
    private static final double TEMPERATURA_CRITICA = 27.0;

    // 4. Semana 7: avisos del sistema y alerta propia
    private TextView txtEstadoSistema;
    private ReceptorSistema receptorSistema;
    private ReceptorAlertaSensor receptorAlerta;
    private ConnectivityManager.NetworkCallback vigilanteRed;

    private boolean hayRed = true;
    private boolean bateriaBaja = false;
    private String ultimaAlerta = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 3. Vincular variables con los IDs del XML
        swLed = findViewById(R.id.sw_led);
        swRelay = findViewById(R.id.sw_relay);
        txtLdr = findViewById(R.id.txt_ldr);
        txtTemp = findViewById(R.id.txt_temp);
        txtHum = findViewById(R.id.txt_hum);
        txtIr = findViewById(R.id.txt_ir);
        txtDist = findViewById(R.id.txt_dist);
        etMensajeLcd = findViewById(R.id.et_mensaje_lcd);
        btnEnviarLcd = findViewById(R.id.btn_enviar_lcd);
        btnHistorial = findViewById(R.id.btn_historial);

        /* Proyecto integrador: el panel de enfriamiento vive en su propia
           pantalla porque lee otra rama de la base (tarjeta/refrigeracion) y
           mezclarlo aqui haria de esta Activity un cajon de sastre. */
        findViewById(R.id.btn_refrigeracion).setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RefrigeracionActivity.class)));
        txtEstadoSistema = findViewById(R.id.txt_estado_sistema);

        receptorSistema = new ReceptorSistema(this);
        receptorAlerta = new ReceptorAlertaSensor(this);
        hayRed = ReceptorSistema.hayInternet(this);
        pintarEstadoSistema();

        // DAO del historial (Semana 6)
        registroDao = HistorialDatabase.getInstance(this).registroSensorDao();

        btnHistorial.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, HistorialActivity.class)));

        // 4. Inicializar Firebase (Automático gracias al plugin google-services)
        database = FirebaseDatabase.getInstance();
        
        // 5. Apuntar a las rutas exactas del ESP32
        refSensores = database.getReference("tarjeta/sensores");
        refLed = database.getReference("tarjeta/dispositivos/LED/estado");
        refRelay = database.getReference("tarjeta/dispositivos/relevador/estado");
        refLcd = database.getReference("tarjeta/dispositivos/LCD/mensaje");

        // Autenticación para tener permisos
        FirebaseAuth.getInstance().signInWithEmailAndPassword("esp32@correo.com", "tortacubana")
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(MainActivity.this, "Conectado a Firebase", Toast.LENGTH_SHORT).show();
                        /* El listener se engancha AQUI, no antes: las reglas de la
                           base exigen sesion iniciada y un listener registrado
                           durante el login muere con "Permission denied". */
                        escucharSensores();
                    } else {
                        String errorMsg = task.getException() != null ? task.getException().getMessage() : "Error desconocido";
                        Toast.makeText(MainActivity.this, "Error de Login: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });

        // ------------------------------------------------------------
        // LÓGICA DE ACTUADORES (Escribir en Firebase)
        // ------------------------------------------------------------

        // Control del LED
        swLed.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                refLed.setValue("ON");
            } else {
                refLed.setValue("OFF");
            }
        });

        // Control del Relevador
        swRelay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                refRelay.setValue("ON");
            } else {
                refRelay.setValue("OFF");
            }
        });

        // Enviar mensaje a la pantalla LCD
        btnEnviarLcd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String mensaje = etMensajeLcd.getText().toString();
                if (!mensaje.isEmpty()) {
                    refLcd.setValue(mensaje);
                    Toast.makeText(MainActivity.this, "Mensaje enviado", Toast.LENGTH_SHORT).show();
                    etMensajeLcd.setText(""); // Limpiar el cuadro de texto
                } else {
                    Toast.makeText(MainActivity.this, "Escribe un mensaje primero", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    // ------------------------------------------------------------
    // LÓGICA DE SENSORES (Leer desde Firebase en Tiempo Real)
    // ------------------------------------------------------------

    /**
     * Escucha en vivo el nodo tarjeta/sensores. Se llama una sola vez, cuando
     * FirebaseAuth confirmo la sesion.
     */
    private void escucharSensores() {
        refSensores.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Leer Temperatura y Humedad
                    if (snapshot.child("DHT/temperatura").exists()) {
                        String temp = String.valueOf(snapshot.child("DHT/temperatura").getValue());
                        txtTemp.setText("Temperatura: " + temp + " °C");
                    }
                    if (snapshot.child("DHT/humedad").exists()) {
                        String hum = String.valueOf(snapshot.child("DHT/humedad").getValue());
                        txtHum.setText("Humedad: " + hum + " %");
                    }

                    // Leer Luz (LDR)
                    if (snapshot.child("LDR/analogico").exists()) {
                        String luz = String.valueOf(snapshot.child("LDR/analogico").getValue());
                        txtLdr.setText("Luz Ambiental: " + luz + " %");
                    }

                    // Leer Proximidad (IR y Ultrasónico)
                    if (snapshot.child("proximidad/estado").exists()) {
                        String estadoIr = snapshot.child("proximidad/estado").getValue(String.class);
                        txtIr.setText("Estado IR: " + estadoIr);
                    }
                    if (snapshot.child("proximidad/distancia").exists()) {
                        String distancia = String.valueOf(snapshot.child("proximidad/distancia").getValue());
                        txtDist.setText("Distancia: " + distancia + " cm");
                    }

                    // Semana 6: guardar estas lecturas en la base local
                    guardarEnHistorial(snapshot);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------
    // SEMANA 7: CICLO DE VIDA DE LOS BROADCAST RECEIVERS
    // ------------------------------------------------------------

    /*
     * Se registran en onStart y se quitan en onStop: viven solo mientras la
     * pantalla esta visible. Asi la app no sigue despertando en segundo plano ni
     * deja receivers colgados (Android avisa con "Activity has leaked
     * IntentReceiver" si uno se olvida de unregisterReceiver).
     *
     * Desde Android 14 hay que decir si el receiver acepta avisos de otras
     * aplicaciones. RECEIVER_NOT_EXPORTED significa que no: estos avisos vienen
     * del sistema o de la propia app.
     */
    @Override
    protected void onStart() {
        super.onStart();

        ContextCompat.registerReceiver(this, receptorSistema,
                ReceptorSistema.filtro(), ContextCompat.RECEIVER_NOT_EXPORTED);

        ContextCompat.registerReceiver(this, receptorAlerta,
                ReceptorAlertaSensor.filtro(), ContextCompat.RECEIVER_NOT_EXPORTED);

        /* CONNECTIVITY_ACTION esta obsoleto y en Android moderno puede no
           llegar. NetworkCallback es la via vigente y avisa en el momento
           exacto en que se pierde o vuelve la red. */
        vigilanteRed = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network red) {
                runOnUiThread(() -> alCambiarRed(true));
            }

            @Override
            public void onLost(@NonNull Network red) {
                runOnUiThread(() -> alCambiarRed(false));
            }
        };
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(vigilanteRed);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receptorSistema);
        unregisterReceiver(receptorAlerta);

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null && vigilanteRed != null) {
            cm.unregisterNetworkCallback(vigilanteRed);
        }
    }

    /** Llega cuando cambia la red (por NetworkCallback o CONNECTIVITY_ACTION). */
    @Override
    public void alCambiarRed(boolean conectado) {
        hayRed = conectado;
        pintarEstadoSistema();
    }

    /** Llega con ACTION_BATTERY_LOW y ACTION_BATTERY_OKAY. */
    @Override
    public void alCambiarBateria(boolean baja) {
        bateriaBaja = baja;
        pintarEstadoSistema();
    }

    /** Llega con el aviso propio que emite esta misma pantalla. */
    @Override
    public void alRecibirAlerta(String sensor, double valor) {
        ultimaAlerta = sensor + " = " + valor;
        pintarEstadoSistema();
    }

    /** Una sola linea que resume red, bateria y ultima alerta. */
    private void pintarEstadoSistema() {
        String texto;
        int color;
        if (!hayRed) {
            texto = "Sin conexion: mostrando el historial guardado";
            color = 0xFFD32F2F;
        } else if (bateriaBaja) {
            texto = "Bateria baja: se pauso el guardado en la base local";
            color = 0xFFF57C00;
        } else if (ultimaAlerta != null) {
            texto = "ALERTA: " + ultimaAlerta;
            color = 0xFFD32F2F;
        } else {
            texto = "Conectado - guardando lecturas";
            color = 0xFF388E3C;
        }
        txtEstadoSistema.setText(texto);
        txtEstadoSistema.setTextColor(color);
    }

    // ------------------------------------------------------------
    // SEMANA 6: PERSISTENCIA LOCAL EN ROOM
    // ------------------------------------------------------------

    /**
     * Convierte lo que llego de Firebase en filas y las inserta en Room.
     *
     * Solo se guardan los sensores cuyo valor CAMBIO respecto a la ultima vez:
     * la ESP32 escribe cada valor por separado y Firebase avisa en cada
     * escritura, asi que sin este filtro la tabla se llenaria de repetidos.
     */
    private void guardarEnHistorial(DataSnapshot snapshot) {
        long ahora = System.currentTimeMillis();
        List<RegistroSensor> nuevos = new ArrayList<>();

        agregarSiCambio(nuevos, "luz", snapshot.child("LDR/analogico"), ahora);
        agregarSiCambio(nuevos, "temperatura", snapshot.child("DHT/temperatura"), ahora);
        agregarSiCambio(nuevos, "humedad", snapshot.child("DHT/humedad"), ahora);
        agregarSiCambio(nuevos, "distancia", snapshot.child("proximidad/distancia"), ahora);

        if (nuevos.isEmpty()) {
            return;
        }

        /* Semana 7: con la bateria baja se suspende lo costoso. Escribir en
           disco y mantener la base despierta gasta, y el historial puede
           esperar; los valores se siguen viendo en pantalla. */
        if (bateriaBaja) {
            return;
        }

        // Si alguna lectura viene en alerta, se avisa al resto de la app
        for (RegistroSensor r : nuevos) {
            if (r.isEstadoAlerta()) {
                ReceptorAlertaSensor.emitir(this, r.getTipoSensor(), r.getValorLectura());
            } else if (ultimaAlerta != null
                    && ultimaAlerta.startsWith(r.getTipoSensor() + " ")) {
                // ese sensor volvio a su rango: el aviso deja de tener sentido
                ultimaAlerta = null;
                pintarEstadoSistema();
            }
        }

        // La escritura ocurre en el hilo de fondo; la pantalla no espera al disco.
        hiloBaseDatos.execute(() -> registroDao.insertarTodos(nuevos));
    }

    /** Agrega la lectura a la lista solo si el valor es nuevo o distinto. */
    private void agregarSiCambio(List<RegistroSensor> destino, String tipo,
                                 DataSnapshot nodo, long ahora) {
        if (!nodo.exists() || nodo.getValue() == null) {
            return;
        }
        double valor;
        try {
            valor = Double.parseDouble(String.valueOf(nodo.getValue()));
        } catch (NumberFormatException e) {
            return;   // el nodo trae texto, no un numero: se ignora
        }

        Double anterior = ultimosValores.get(tipo);
        Long guardadoEn = ultimoGuardado.get(tipo);
        boolean cambio = anterior == null || anterior != valor;
        boolean tocaLatido = guardadoEn == null || (ahora - guardadoEn) >= LATIDO_MS;

        if (!cambio && !tocaLatido) {
            return;
        }
        ultimosValores.put(tipo, valor);
        ultimoGuardado.put(tipo, ahora);

        // Solo la temperatura tiene umbral; los demas sensores se guardan sin alerta
        boolean enAlerta = "temperatura".equals(tipo) && valor > TEMPERATURA_CRITICA;
        destino.add(new RegistroSensor(tipo, valor, ahora, enAlerta));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hiloBaseDatos.shutdown();   // no dejar el hilo vivo tras cerrar la pantalla
    }
}